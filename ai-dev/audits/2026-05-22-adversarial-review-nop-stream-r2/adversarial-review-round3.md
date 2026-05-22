# nop-stream 对抗性审查 — Round 3（深度验证轮）

> 审查日期：2026-05-22
> 审查范围：nop-stream 全模块（10 个子模块），聚焦之前审查的盲区和未覆盖区域
> 审查方法：开放式发现导向，4 个并行探索 agent 分别聚焦 timer 生命周期、Watermark 传播路径、CEP NFA 正确性边界、connector + 测试质量
> 去重：已阅读以下已有报告，本报告不重复其中内容：
> - `ai-dev/audits/2026-05-20-adversarial-review-nop-stream/`（Round 1+2，N1-N41 共 41 个 bug 级发现 + 已知问题确认 K4-K24）
> - `ai-dev/audits/2026-05-21-adversarial-review-nop-stream-design/`（13 个设计级发现 D1-D13）
> - `ai-dev/audits/2026-05-22-adversarial-review-nop-stream/`（Round 1，N42-N72 共 31 个发现）
> - `ai-dev/analysis/2026-04-02-nop-stream-review.md`（557 行综合分析，25 个已知问题）
> - `ai-dev/analysis/2026-05-20-nop-stream-duplicate-code-audit.md`（705 行重复代码审计）
> 发现来源视角：异常路径侦探 + IoC 侦探 + 死代码清道夫 + 组合爆炸测试者

---

## 发现分类

| 类别 | 数量 | 最严重 |
|------|------|--------|
| Timer 生命周期正确性 | 3 | P0 |
| Graph Model 执行路径缺陷 | 4 | P0 |
| CEP NFA 边界条件 | 4 | P1 |
| 算子正确性 | 3 | P1 |
| Connector 资源管理 | 3 | P2 |
| 测试质量 | 4 | P2 |
| 代码质量 | 3 | P2 |

---

## P0：Timer 生命周期 + Graph Model 正确性

### N73：HeapInternalTimerService.advanceWatermark 在 timer callback 删除 timer 时触发 ConcurrentModificationException

**在哪里：** `nop-stream-core/.../operators/HeapInternalTimerService.java` L106-111

**是什么：**

`advanceWatermark` 的实现：

```java
for (Map.Entry<Long, Set<TimerEntry<N>>> entry : toFire) {
    for (TimerEntry<N> timer : entry.getValue()) {          // 遍历 Set<TimerEntry>
        triggerable.onEventTime(new HeapInternalTimer<>(timer.timestamp, timer.namespace));
    }
    eventTimeTimers.remove(entry.getKey());
}
```

虽然 `toFire` 是从 `headMap` 收集到的新 ArrayList（避免了 TreeMap 的 CME），但 `entry.getValue()` 返回的是 `eventTimeTimers` 中**原始的 HashSet 引用**。如果 `triggerable.onEventTime()` 回调中调用了 `deleteEventTimeTimer()`，后者会从同一个 HashSet 中删除元素，触发 `ConcurrentModificationException`。

**触发路径：** `EventTimeTrigger.onEventTime()` → 返回 `FIRE_AND_PURGE` → `WindowOperator` 的 `onEventTime()` 调用 `triggerContext.clear()` → `Trigger.clear()` 调用 `deleteEventTimeTimer()` → CME。

**为什么值得关心：** 这是 EventTimeTrigger 的标准用法。任何使用 EventTimeTrigger 的窗口操作在 timer 触发时都会崩溃。这不是边界情况——是正常执行路径上的必然失败。

**信心水平：** 确定

---

### N74：HeapInternalTimer.getKey() 永远返回 null — keyed timer 回调无法确定当前 key

**在哪里：** `nop-stream-core/.../operators/HeapInternalTimerService.java` L156-158

**是什么：**

```java
@Override
public Object getKey() {
    return null;
}
```

`HeapInternalTimer` 内部类只存储 `(timestamp, namespace)`，不存储 key。当 timer 通过 `triggerable.onEventTime(timer)` 触发时，`timer.getKey()` 返回 null。

对比 `WindowOperatorTimerService`（runtime 模块），其 `SimpleInternalTimer` 通过 `Supplier<K>` 在注册时捕获当前 key，timer 正确携带 key。

**影响链：** `WindowOperator.onEventTime()` 从 timer 获取 key 的路径：
1. 通过 `triggerContext.key`（隐式状态）而非 timer 的 key
2. 但如果有人试图从 timer 本身获取 key（如自定义算子），得到 null

**为什么值得关心：** `HeapInternalTimerService` 是 core 模块提供的"标准" timer 实现，但它的 timer 没有 key 信息。这意味着使用 `TimerServiceManager` + `HeapInternalTimerService` 的算子（graph model 路径中的算子）的 keyed timer 回调存在缺陷。与 runtime 的 `WindowOperatorTimerService`（有 key）不一致——两个 timer 服务对同一个 `InternalTimer` 接口提供了不同的语义。

**信心水平：** 确定

---

### N75：Graph Model 路径缺少 KeyExtractingOutput — keyed state 在 graph 执行中完全失效

**在哪里：** `nop-stream-core/.../execution/StreamTaskInvokable.java` L87-105 vs `nop-stream-core/.../environment/StreamExecutionEnvironment.java` L461-481

**是什么：**

**Fast Path** 的 `wireOperatorChain()` 正确地为 keyed 算子包装了 `KeyExtractingOutput`：
```java
if (keySelectors.containsKey(i + 1)) {
    currentOp.setOutput(new KeyExtractingOutput(keySelectors.get(i + 1), nextInput));
}
```
`KeyExtractingOutput` 在转发元素前调用 `keyContext.setCurrentKey(key)`，确保 keyed state backend 的当前 key 正确。

**Graph Model Path** 的 `StreamTaskInvokable.wireOperators()` 只用 `ChainingOutput` 连接算子：
```java
currentOp.setOutput(new ChainingOutput(nextInput));
```

没有 `KeyExtractingOutput`，`setCurrentKey()` 永远不被调用。所有 keyed 算子（`StreamReduceOperator`、`WindowAggregationOperator`、`WindowOperator`）在 graph model 路径中：
- 所有数据写入同一个 key（null 或上一个 key）
- 多 key 数据互相覆盖

**为什么值得关心：** 这意味着 `executeWithGraphModel()` 路径完全不支持 keyed 操作。任何使用 `keyBy().reduce()` 或 `keyBy().window()` 的管道在 graph model 路径中产出错误结果。与 N42-N50（之前报告的 InputGate/RecordWriter 问题）叠加，graph model 路径的正确性问题更加系统性。

**信心水平：** 确定

---

### N76：StreamReduceOperator 使用 transient HashMap 存储状态 — checkpoint 恢复后状态完全丢失

**在哪里：** `nop-stream-core/.../operators/StreamReduceOperator.java` L23, L46

**是什么：**

```java
private transient Map<Object, T> values;  // transient — 序列化时跳过

@Override
public void open() throws Exception {
    super.open();
    values = new HashMap<>();  // 每次重新创建空 map
}
```

`StreamReduceOperator` 用 `transient HashMap` 存储每个 key 的 reduce 结果。它不使用 `keyedStateBackend`，也没有 `snapshotState()`/`restoreState()` 方法。Checkpoint 时这个 map 被跳过（transient），恢复后创建新空 map——所有已 reduce 的状态丢失。

对比 `WindowAggregationOperator`（用 `LinkedHashMap` 存 window state，也有类似问题但至少有 timer）。

**为什么值得关心：** `DataStreamImpl.reduce()` 创建 `StreamReduceOperator`。如果用户调用 `keyBy(...).reduce(...)` 然后触发 checkpoint，恢复后所有 reduce 结果重置为空——相当于数据丢失。

**信心水平：** 确定

---

### N77：WindowAggregationOperator 的 trigger state key 存在碰撞风险

**在哪里：** `nop-stream-core/.../operators/WindowAggregationOperator.java` L285, L191

**是什么：**

```java
String stateKey = String.valueOf(key) + "#" + window + "#" + descriptor.getName();
```

Trigger state 使用 `key#window#descriptorName` 作为 Map key。如果 key 或 window 的 `toString()` 产生包含 `#` 的字符串，不同 key/window 组合可能映射到相同的 state key。

例如：
- key=`"a#b"`, window=`"c"` → `"a#b#c#triggerState"`
- key=`"a"`, window=`"b#c"` → `"a#b#c#triggerState"`

这会导致不同 key/window 的 trigger state 互相覆盖。

**为什么值得关心：** 当 key 类型是 String 且用户使用包含 `#` 的值时，trigger state（如 CountTrigger 的计数器）会错误地跨窗口共享。

**信心水平：** 很可能

---

### N78：WindowAggregationOperator 维护独立于 TimerServiceManager 的计时器系统 — 双 timer 系统不协调

**在哪里：** `nop-stream-core/.../operators/WindowAggregationOperator.java` L29-30

**是什么：**

`WindowAggregationOperator` 有自己的 timer 注册/触发机制：

```java
private transient TreeMap<Long, Set<WindowKey<K, W>>> eventTimeTimers;       // 自己的 timer 存储
private transient Map<WindowKey<K, W>, Set<Long>> windowTimerLookup;         // 反向查找
```

而 `AbstractStreamOperator` 通过 `timeServiceManager` 管理 `HeapInternalTimerService`。两者完全独立：
- `WindowAggregationOperator.processWatermark()` 不调用 `super.processWatermark()`，所以 `timeServiceManager` 永远不被推进
- WindowAggregationOperator 的 timer 在自己的 `processWatermark()` 中直接触发

**为什么值得关心：** 这是 nop-stream 中出现的**第三套** timer 实现：
1. `HeapInternalTimerService`（core，无 key、无 checkpoint）
2. `WindowOperatorTimerService`（runtime，有 key、PriorityQueue、O(n) 操作）
3. `WindowAggregationOperator` 的 TreeMap timer（core，有 key、per-window 反向查找）

三套 timer 服务对同一个 `Trigger` 接口提供不同实现，`Trigger.onEventTime()` 的行为取决于它被哪个 timer 系统调用——这是一种隐式的"运行时多态"但没有任何文档或类型系统约束。

**信心水平：** 确定

---

## P1：CEP NFA 边界条件

### N79：CepOperator 每个 processEvent 调用都创建并关闭 SharedBufferAccessor — 高吞吐场景下性能灾难

**在哪里：** `nop-stream-cep/.../operator/CepOperator.java` L407-421

**是什么：**

```java
private void processEvent(NFAState nfaState, IN event, long timestamp) throws Exception {
    try (SharedBufferAccessor<IN> sharedBufferAccessor = partialMatches.getAccessor()) {
        // ... process event ...
    }  // close() → flushCache() → 写整个 Guava cache 到 state backend
}
```

每个事件处理都打开/关闭一个 `SharedBufferAccessor`。`close()` 调用 `flushCache()`，后者将 Guava cache 中的所有条目写回底层 `MapState`。在高吞吐场景（每秒 10K+ 事件）下，这意味着每秒 10K+ 次完整的 cache 写回操作。

Flink 的 CEP 实现在 checkpoint 时才 flush cache，不是每个事件。

**为什么值得关心：** 性能瓶颈。`SharedBuffer.flushCache()` 遍历整个 cache 调用 `entries.putAll()` + `eventsBuffer.putAll()`，这是 O(cache_size) 操作。每秒 10K 次 O(cache_size) 操作在 cache 有数百条目时就会成为严重瓶颈。

**信心水平：** 确定

---

### N80：SharedBuffer.advanceTime 只清除 eventsCount 不清除对应的事件数据 — EventId 碰撞风险

**在哪里：** `nop-stream-cep/.../sharedbuffer/SharedBuffer.java` L258-266

**是什么：**

```java
void advanceTime(long timestamp) {
    Iterator<Long> iterator = eventsCount.keys().iterator();
    while (iterator.hasNext()) {
        Long next = iterator.next();
        if (next < timestamp) {
            iterator.remove();  // 只删除 eventsCount 条目
        }
    }
}
```

`advanceTime` 清除 `eventsCount` 中旧时间戳的计数器，但不清除 `eventsBuffer` / `eventsBufferCache` 中对应的事件数据。

`eventsCount` 的语义是"每个时间戳的事件数量"，用于为 `registerEvent()` 生成递增的 EventId。如果 `eventsCount` 被清除但事件数据仍在，同一时间戳的后续事件会从 id=0 重新开始，产生与已存在事件相同的 `EventId(timestamp, 0)`。

**为什么值得关心：** 当旧事件数据仍被 SharedBuffer 引用计数保持时（`Lockable.refCounter > 0`），新注册的事件可能获得重复的 `EventId`，导致 `NodeId` 碰撞和 SharedBuffer 图结构损坏。

**信心水平：** 很可能（需要特定时序：advanceTime 后同一时间戳有新事件注册，且旧事件仍被引用）

---

### N81：NFACompiler 在非终止位置静默丢弃 NOT_FOLLOW 约束 — 用户定义的排除条件不生效

**在哪里：** `nop-stream-cep/.../compiler/NFACompiler.java` L333-346

**是什么：**

NFACompiler 处理 NOT_FOLLOW 模式时：
```java
if ((currentPattern.getWindowSize(PREVIOUS_AND_CURRENT).isPresent() || getWindowTime() > 0) && lastSink.isFinal()) {
    // 创建 Pending state（NOT 约束生效）
} else {
    // 静默跳过 — NOT_FOLLOW 约束完全不生效
}
```

只有当 NOT_FOLLOW 是**最后一个模式**且**有窗口时间约束**时，NOT 约束才会被编译进 NFA。在其他位置（如中间位置的 `notFollowedBy`），约束被静默丢弃。

**为什么值得关心：** 用户定义了 `begin("a").next("b").notFollowedBy("c").next("d")` 这样的 pattern，期望"c 不出现"，但 NFA 编译后 NOT 约束不存在——pattern 等价于没有 NOT 约束的版本。无错误、无警告。

**信心水平：** 确定

---

### N82：SkipToFirst/SkipToLast 在 pattern name 不存在时静默退化为 NoSkip — 用户不知道 skip 策略失效

**在哪里：** `nop-stream-cep/.../aftermatch/SkipToElementStrategy.java` L53-85

**是什么：**

`SkipToFirstStrategy.getPruningId()` 在 pattern name 不存在于 match 中时：
- `shouldThrowException = false`（默认）→ `pruningId = null`
- `AfterMatchSkipStrategy.prune()` 检查 `if (pruningId != null)` → 跳过 prune
- 效果：skip 策略完全等同于 NoSkip

**为什么值得关心：** 用户设置了 `skipToFirst("nonexistent")` 但不会得到任何错误。在 pattern name 拼写错误时，策略静默退化。

**信心水平：** 确定

---

## P2：算子 + Connector + 测试质量

### N83：StreamTaskInvokable.invokeMiddle/invokeSink 不发送 MAX_WATERMARK — 下游 timer 不触发最终清理

**在哪里：** `nop-stream-core/.../execution/StreamTaskInvokable.java` L197-221

**是什么：**

`invokeSource()` 在 source 完成后正确发送 `Watermark.MAX_WATERMARK`（L188）。但 `invokeMiddle()` 和 `invokeSink()` 在 InputGate 耗尽后直接结束，不发送 `MAX_WATERMARK`。

**影响：** 在 graph model 路径中，如果 MIDDLE 或 SINK 角色的 task 包含 timer-based 算子（如窗口操作），这些算子的最终 timer（如窗口的 cleanup timer）不会被触发。

**为什么值得关心：** 这是 source 到 sink 的 watermark 传播链断裂。Source 发出了 MAX_WATERMARK，但如果中间有 task boundary（通过 ResultPartition/InputGate），MAX_WATERMARK 不会跨越。

**信心水平：** 确定

---

### N84：StreamTaskInvokable.processInputGate 不处理 WatermarkStatus — idle 检测在 graph model 路径失效

**在哪里：** `nop-stream-core/.../execution/StreamTaskInvokable.java` L237-253

**是什么：**

`processInputGate` 只处理 `isRecord()`、`isWatermark()`、`isCheckpointBarrier()`。`isWatermarkStatus()` 没有处理分支。

`WatermarkStatus` 是 source 标记 idle/active 的机制（对应 N46 发现的 `markIdle()` 空操作问题）。即使 `markIdle()` 被修复，`WatermarkStatus` 元素通过 InputGate 传递到下游 task 时也会被静默丢弃。

**为什么值得关心：** 与 N46 叠加：markIdle 是空操作 → 即使修复了 markIdle → WatermarkStatus 通过 InputGate 也会被丢弃 → idle 检测在 graph model 路径双倍失效。

**信心水平：** 确定

---

### N85：BatchConsumerSinkFunction 的 IBatchConsumer 从不关闭 — 资源泄漏

**在哪里：** `nop-stream-connector/.../BatchConsumerSinkFunction.java` 构造器 + close()

**是什么：**

构造器调用 `consumerProvider.setup(taskContext)` 获取 `IBatchConsumer`，但 `close()` 只调用 `flush()`。`IBatchConsumer` 如果持有数据库连接等资源，这些资源永远不会被释放。

对比 `BatchLoaderSourceFunction`（已有 `if (loader instanceof AutoCloseable)` 清理逻辑），sink 端缺少对应处理。

**信心水平：** 很可能

---

### N86：MessageSourceFunction.onMessage 中 unchecked cast 导致静默数据丢失

**在哪里：** `nop-stream-connector/.../MessageSourceFunction.java` — onMessage callback

**是什么：**

```java
ctx.collect((T) msg);  // unchecked cast
```

如果 `IMessageService` 投递的消息类型与 `T` 不匹配，抛出 `ClassCastException`。由于 `LocalMessageService.invokeMessageListener` 捕获并仅记录异常，数据被静默丢弃。

**信心水平：** 很可能

---

### N87：TestE2ECheckpointAndRecovery.testFullPipelineCheckpointAndRecovery 不是真正的恢复测试

**在哪里：** `nop-stream-runtime/src/test/.../TestE2ECheckpointAndRecovery.java`

**是什么：**

测试声称测试"完整管道的 checkpoint + 恢复"，但实际上：
1. 运行 source(1-5) → 收集结果 → 获取 snapshot
2. 创建新 source(6-10) → 运行 → 收集结果
3. 断言第二次结果是 6-10 的 map 结果

**没有**将第一次的 snapshot 恢复到新的 operator 中。它测试的是"两个独立管道顺序运行"，不是"checkpoint + restore"。

**信心水平：** 确定

---

### N88：TestCepOperatorStateRecovery.testSnapshotRestoreAndContinue 复用同一 operator 实例 — 不是真正的跨实例恢复

**在哪里：** `nop-stream-cep/src/test/.../TestCepOperatorStateRecovery.java`

**是什么：**

测试流程：process events → capture state → `updateNFAStateForTesting(capturedState)` → continue processing。

`updateNFAStateForTesting()` 在**同一个** operator 实例上设置状态。真正的恢复测试应创建**新** operator 实例，将 captured state 恢复进去，然后继续处理。当前测试验证的是"设置已有的状态不影响后续处理"，不是"状态可以从一个实例迁移到另一个"。

**信心水平：** 确定

---

### N89：TestEndToEndPipeline 的 TestMapOperator/TestFilterOperator 是 no-op stub — 即使 source 发数据也不处理

**在哪里：** `nop-stream-core/src/test/.../TestEndToEndPipeline.java` — TestMapOperator, TestFilterOperator

**是什么：**

这些 stub 实现 `StreamOperator` 接口但不实现 `OneInputStreamOperator`（没有 `processElement` 方法）。即使 `TestSourceFunction.run()` 发射了数据（当前不发射），map 和 filter 也不会处理——因为它们没有处理元素的入口点。

**信心水平：** 确定

---

### N90：CEP 模块零测试覆盖的关键功能

**在哪里：** `nop-stream-cep/src/test/` 全目录

**是什么：**

以下 CEP 功能完全没有任何测试：

| 功能 | 影响 |
|------|------|
| timeout handling（`TimedOutPartialMatchHandler`） | 有 timeout 的 pattern 未验证 |
| NOT pattern（`notFollowedBy`/`notNext`） | N81 的静默丢弃无法被测试发现 |
| optional pattern（`.optional()`） | 无验证 |
| looping pattern（`oneOrMore()`, `times()`） | 无验证 |
| greedy pattern（`.greedy()`） | 无验证 |
| after-match skip strategies | N82 的退化无法被测试发现 |
| SharedBuffer eviction/cache | 高吞吐下的行为未验证 |
| EventComparator secondary sort | 同一时间戳的排序未验证 |
| CepPatternBuilder（model → Pattern） | 声明式 pattern 构建路径未验证 |
| watermark-driven timeout | CEP + event-time 集成未验证 |

**为什么值得关心：** CEP 是 nop-stream 中完成度最高的模块（设计审查 D10 证实了 XDEF 代码生成管线存在），但其测试覆盖只包括最基本的 pattern 匹配。N81 和 N82 这样的静默降级问题正是因为缺少对应测试而未被早期发现。

**信心水平：** 确定

---

## 代码质量

### N91：三套独立的 timer 实现对同一 Trigger 接口提供不同语义 — 维护负担 + 行为不一致

**在哪里：**
- `nop-stream-core/.../HeapInternalTimerService.java`（无 key、无 checkpoint、TreeMap）
- `nop-stream-runtime/.../WindowOperatorTimerService.java`（有 key、PriorityQueue、O(n) contains）
- `nop-stream-core/.../WindowAggregationOperator.java` L29-30（有 key、TreeMap + 反向查找）

**是什么：** 见 N78 的详细分析。三个实现的行为差异：

| 行为 | HeapInternalTimerService | WindowOperatorTimerService | WindowAggregationOperator |
|------|--------------------------|---------------------------|---------------------------|
| Timer 携带 key | 否（null） | 是 | 是 |
| Checkpoint 支持 | 无 | 无 | 无 |
| Processing time | 空操作 | 有（O(n) removeIf） | 空操作 |
| 重复注册检查 | 无（HashSet 自然去重） | 有（contains O(n)） | 无（LinkedHashSet 自然去重） |
| 删除性能 | O(1) HashSet.remove | O(n) removeIf | O(1) Set.remove + 反向查找 |

**为什么值得关心：** 自定义 Trigger 的行为取决于它被哪个 timer 系统调用。例如，`Trigger.onEventTime()` 中调用 `ctx.registerEventTimeTimer()` 的去重行为在三个实现中不同。

**信心水平：** 确定

---

### N92：CepPatternBuilder.buildCondition 创建的匿名 IterativeCondition 不支持生命周期管理

**在哪里：** `nop-stream-cep/.../builder/CepPatternBuilder.java` L134-141

**是什么：**

```java
return new IterativeCondition<T>() {
    @Override
    public boolean filter(T value, Context<T> ctx) throws Exception {
        return action.call2(value, ctx);
    }
};
```

匿名类不实现 `RichFunction` 生命周期（open/close/setRuntimeContext）。如果 `IEvalFunction` 需要运行时上下文（如访问状态），会在调用时静默失败或抛异常。

**信心水平：** 很可能

---

### N93：SharedBufferCacheConfig 不验证参数 — zero/negative cache size 导致崩溃或性能归零

**在哪里：** `nop-stream-cep/.../configuration/SharedBufferCacheConfig.java` L37-41

**是什么：** 构造函数直接使用传入的 cache size 值传给 `CacheBuilder.maximumSize()`。零值导致每次访问立即驱逐（性能归零），负值在 cache 构建时抛异常。

**信心水平：** 确定

---

## 总评

### 最值得关注的 3 个方向

1. **Timer 生命周期是系统性缺陷**（N73+N74+N78+N91）：nop-stream 有三套独立的 timer 实现，都不支持 checkpoint/restore，且核心实现 `HeapInternalTimerService` 在正常使用路径上会崩溃（CME）且不携带 key。这意味着所有依赖 timer 的操作（event-time 窗口、CEP timeout、cleanup timer）在 checkpoint 恢复后都会丢失状态，而 `HeapInternalTimerService` 在 timer 触发 + 删除的标准模式下会直接崩溃。

2. **Graph Model 执行路径有根本性缺陷**（N75+N83+N84）：之前报告的 N42-N50 已经覆盖了 InputGate/RecordWriter 的问题，本次发现 graph model 路径还缺少 `KeyExtractingOutput`（keyed state 完全失效）和 `MAX_WATERMARK` 传播（最终 timer 不触发）。结合设计审查 D1（双执行模型），graph model 路径在 keyed 操作、watermark 传播、timer 触发三个核心能力上都有缺陷。

3. **CEP 的测试覆盖严重不足**（N90+N81+N82）：CEP 模块的 API 表面积极大（Pattern DSL 有 15+ 方法、NFACompiler 有复杂的条件分支、SharedBuffer 有多层 cache），但测试只覆盖了最基本的 3-step pattern 匹配。NOT pattern 静默丢弃（N81）、skip 策略静默退化（N82）等问题正是因为缺少测试而长期存在。

### 本次审查的盲区自评

1. **WindowOperator + WindowAggregationOperator 的交互**：两者都提供窗口聚合功能但实现完全不同。没有分析用户应该使用哪个、是否有迁移路径。
2. **性能基准**：所有性能相关发现（N79 per-event cache flush、N91 三套 timer）都是基于代码结构的理论分析，没有实际的性能测试数据。
3. **序列化兼容性**：没有验证 checkpoint 数据在不同版本间的兼容性。
4. **nop-stream-flow 模块**：空壳模块，仍然无法审查。
5. **多 job 并行执行**：没有分析多个 StreamExecutionEnvironment 实例并行运行时的线程安全和资源隔离。
