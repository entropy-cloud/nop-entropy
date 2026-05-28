# nop-stream 对抗性审查 — Round 5

> 审查日期：2026-05-28
> 审查范围：nop-stream 全模块（10 个子模块），独立代码审查
> 审查方法：开放式发现导向，从 live code 出发，4 个并行 agent 深入代码
> 去重：已阅读以下已有报告，本报告不重复其中内容：
> - `ai-dev/audits/2026-05-20-adversarial-review-nop-stream/`（Round 1+2，N1-N41）
> - `ai-dev/audits/2026-05-22-adversarial-review-nop-stream/`（Round 1，N42-N72）
> - `ai-dev/audits/2026-05-22-adversarial-review-nop-stream-r2/`（Round 3，N73-N93）
> - `ai-dev/audits/2026-05-24-adversarial-review-nop-stream-r3/`（Round 4，N94-N105）
> - `ai-dev/audits/2026-05-28-adversarial-review-nop-stream/report.md`（12 个发现，Checkpoint/WindowOperator/CEP SharedBuffer/TaskManager/BarrierTracker 等）
> - `ai-dev/audits/2026-05-25-deep-audit-nop-stream-full/`（21 维度系统审计）
> - `ai-dev/audits/2026-05-27-deep-audit-nop-stream-r1/`（7 维度复审）
> - `ai-dev/audits/2026-05-28-deep-audit-nop-stream-full/`（21 维度全量审计）
> 发现来源视角：异常路径侦探 + 10x 规模运维者 + 死代码清道夫 + 代码生成受害者

---

## 发现分类

| 类别 | 数量 | 最严重 |
|------|------|--------|
| 状态后端序列化 | 2 | P0 |
| 执行引擎正确性 | 3 | P0 |
| 窗口算子正确性 | 2 | P0 |
| CEP NFA 边界条件 | 3 | P1 |
| Connector 资源管理 | 3 | P1 |
| 代码质量 | 2 | P2 |

---

## P0：状态丢失 / 执行崩溃

### N106：MemoryKeyedStateBackend.snapshotState 静默丢弃 ReducingState 和 AggregatingState — checkpoint 后状态丢失

**在哪里：** `nop-stream-core/.../state/backend/memory/MemoryKeyedStateBackend.java` L290-318（snapshotState）、L197-217（getReducingState/getAggregatingState）

**是什么：**

`snapshotState()` 用 `instanceof` 链分发状态类型，只处理 5 种：

```java
if (stateObj instanceof MemoryValueState) { ... }
else if (stateObj instanceof MemoryMapState) { ... }
else if (stateObj instanceof MemoryListState) { ... }
else if (stateObj instanceof MemoryInternalAppendingState) { ... }
else if (stateObj instanceof MemoryInternalListState) { ... }
// MemoryReducingState 和 MemoryAggregatingState — 无匹配分支，静默跳过
```

但 `getReducingState()`（L197）和 `getAggregatingState()`（L208）创建了 `MemoryReducingState` 和 `MemoryAggregatingState` 两种状态对象，它们存储在同一个 `states` map 中。`snapshotState()` 遍历 `states` 时遇到这两种类型不匹配任何 `instanceof` 分支，直接跳过——无警告、无错误。

`restoreState()`（L340-358）的 switch 同样只有 "ValueState"、"MapState"、"AppendingState"、"ListState"、"InternalListState" 五个分支，没有对应的恢复路径。

**为什么不修会怎样：** 任何使用 `ReducingState` 或 `AggregatingState` 的流应用（如 `keyBy().reduce()` 通过 `getReducingState` 获取内部状态）在 checkpoint 恢复后状态完全丢失。这是静默数据丢失——无异常、无日志。

**信心水平：** 确定

---

### N107：WindowOperatorTimerService.deleteEventTimeTimer/deleteProcessingTimeTimer 不过滤 key — 跨 key 误删 timer

**在哪里：** `nop-stream-runtime/.../WindowOperatorTimerService.java` L90-98

**是什么：**

Timer 注册（L74-84）正确包含 key：

```java
public void registerEventTimeTimer(N namespace, long time) {
    K key = currentKeySupplier != null ? currentKeySupplier.get() : null;
    InternalTimer<K, N> timer = new SimpleInternalTimer<>(time, key, namespace);
    if (!eventTimeTimers.contains(timer)) { eventTimeTimers.add(timer); }
}
```

但删除（L90-93）只匹配 namespace+timestamp，忽略 key：

```java
public void deleteEventTimeTimer(N namespace, long time) {
    eventTimeTimers.removeIf(timer ->
            timer.getNamespace().equals(namespace) && timer.getTimestamp() == time);
}
```

**为什么不修会怎样：** 在多 key 场景下，key K1 的窗口清理调用 `deleteCleanupTimer` 时，如果 key K2 有相同 namespace（同一窗口）和相同 timestamp 的 timer，K2 的 timer 也会被删除。K2 的窗口状态永远不会被清理——无限内存泄漏。这在 session 窗口合并的 `WindowOperator.java:394`（merge callback 中调用 `deleteCleanupTimer`）触发。

对比：`WindowAggregationOperator` 用 `WindowKey<K, W>` 作为 timer key，包含了 key 信息，不受此 bug 影响。

**信心水平：** 确定

---

### N108：InputGate.readMultiChannel 超时返回 Optional.empty — 多通道管道提前终止

**在哪里：** `nop-stream-core/.../execution/InputGate.java` L204-260

**是什么：**

`readMultiChannel()` 做一轮 round-robin（每通道 50ms 超时），如果所有通道都没有数据：

```java
// L254-259
if (isAllFinished()) {
    return Optional.empty();
}
return Optional.empty();  // 即使通道未全部完成，也返回 empty！
```

调用方 `StreamTaskInvokable.processInputGate()`（L250-253）把 `Optional.empty()` 当作 end-of-stream 并 break 循环：

```java
Optional<StreamElement> elementOpt = inputGate.read();
if (!elementOpt.isPresent()) {
    break;  // 永久退出处理循环
}
```

对比：单通道的 `readSingleChannel()` 使用 `queue.take()` 无限等待，正确行为。

**为什么不修会怎样：** 任何 MIDDLE 或 SINK 角色、有多个 InputChannel 的 task，在上游 producer 生产间隔 > 50ms/通道数 时就会提前终止。多输入管道静默丢失数据。

**信心水平：** 确定

---

### N109：SubtaskTask.openOperatorChains() 打开原始 chain 而非 deep copy — parallelism > 1 时算子未初始化

**在哪里：** `nop-stream-core/.../SubtaskTask.java` L102-118、`nop-stream-core/.../GraphExecutionPlan.java` L186-189

**是什么：**

`GraphExecutionPlan.build()` 对 taskIndex > 0 创建 deep copy 的 OperatorChain：

```java
// GraphExecutionPlan L186-189
OperatorChain chain = taskIndex == 0
        ? original.getOperatorChains().get(0)
        : original.getOperatorChains().get(0).deepCopy();
```

deep copy 用于创建 `StreamTaskInvokable`。但 `SubtaskTask.run()` 调用 `openOperatorChains()` 时打开的是 `jobVertex.getOperatorChains()`——原始 chain：

```java
// SubtaskTask L102-106
private void openOperatorChains() {
    List<OperatorChain> chains = jobVertex.getOperatorChains();
    for (int i = 0; i < chains.size(); i++) {
        chains.get(i).open();
    }
}
```

taskIndex > 0 的 subtask，其 invokable 使用的 deep-copied chain 从未被 `open()`。算子的 `open()` 方法（初始化 state backend、创建 timer、注册 callback 等）不会执行。

**为什么不修会怎样：** parallelism > 1 时，所有 subtask > 0 的算子未初始化。`WindowAggregationOperator`、`StreamReduceOperator` 等依赖 `open()` 初始化字段的算子会 NPE 或行为完全错误。

**信心水平：** 确定

---

### N110：WindowOperator 非合并路径不调用 trigger.clear() — trigger 状态无限增长

**在哪里：** `nop-stream-runtime/.../WindowOperator.java` L440-465（processElement 非合并路径）、L522-530（onEventTime cleanup）、L578-586（onProcessingTime cleanup）

**是什么：**

非合并窗口路径中，当 trigger 返回 FIRE_AND_PURGE（L454-463）：

```java
if (triggerResult.isFire()) {
    ACC contents = getWindowContents(key, window);
    if (contents != null) { emitWindowContents(key, window, contents); }
}
if (triggerResult.isPurge()) {
    clearWindowContents(key, window);  // 只清理窗口内容
    // trigger.clear() 从未被调用！
}
```

cleanup timer 触发时（L522-530）同样只调用 `clearWindowContents`，不调用 `trigger.clear(window, triggerCtx)`。

对比：合并路径（L393）正确调用了 `triggerContext.clear()`。`WindowAggregationOperator.purgeWindow()`（L331-357）也正确清理了 trigger state。

**为什么不修会怎样：** 非合并窗口的 trigger state（如 CountTrigger 的计数器、EventTimeTrigger 的 timer entries）在窗口 fire+purge 或 cleanup 后仍残留在 `triggerAccumulators` map 中。长时间运行的流作业中，trigger state 无限增长——内存泄漏。

**信心水平：** 确定

---

### N111：NFA.startTimestamp > 0 检查遗漏 timestamp=0 的事件 — 窗口化 CEP 模式的 timer 永不注册

**在哪里：** `nop-stream-cep/.../nfa/NFA.java` L379

**是什么：**

```java
if (isStartState(computationState) && newComputationState.getStartTimestamp() > 0) {
    nfaState.setNewStartPartiailMatch();
}
```

当 start state 处理 TAKE 转换时，如果事件的 timestamp 恰好为 0（epoch 起始），`getStartTimestamp() > 0` 为 false，`setNewStartPartialMatch()` 不被调用。后续 `CepOperator.processEvent()`（约 L436）：

```java
if (nfa.getWindowTime() > 0 && nfaState.isNewStartPartialMatch()) {
    registerTimer(timestamp + nfa.getWindowTime());
}
```

不会注册 cleanup timer。partial match 永远不会被超时清理。

**为什么不修会怎样：** 使用自定义时间域（timestamp 可以从 0 开始）或 epoch 起始时间附近的 CEP 模式，partial match 无限积累，导致内存泄漏。

**信心水平：** 很可能（timestamp=0 在实际流处理中不常见但合法，特别是自定义时间域）

---

## P1：正确性问题

### N112：CheckpointBarrierTracker.acknowledgeOperator operator state key 格式不匹配 — trigger accumulators 恢复丢失

**在哪里：** `nop-stream-core/.../CheckpointBarrierTracker.java` L92-96、`nop-stream-runtime/.../GraphModelCheckpointExecutor.java` ~L770

**是什么：**

保存时，`acknowledgeOperator()` 用 `operatorIndex` 前缀拼接 state key：

```java
String opStateKey = getOperatorStateKey(operatorIndex); // "operator-0"
for (Map.Entry<String, Object> entry : snapshot.getOperatorStates().entrySet()) {
    snap.putOperatorState(opStateKey + "-" + entry.getKey(), entry.getValue());
    // 存为 "operator-0-trigger-accumulators"
}
```

恢复时，`buildSnapshotFromTaskState()` 查找 key 时用裸 `mapping.getOperatorStateKey()`（即 `"operator-0"`），而非带后缀的复合 key。查找返回 null。

**为什么不修会怎样：** WindowOperator 的 trigger accumulators（`putOperatorState("trigger-accumulators", ...)`）在 checkpoint 恢复后静默丢失。CountTrigger 的计数器归零，可能导致窗口提前或延迟触发。keyed state 恢复不受影响（使用前缀匹配逻辑）。

**信心水平：** 很可能

---

### N113：GraphExecutionPlan 多出边合并到单个 RecordWriter — fan-out 拓扑数据路由错误

**在哪里：** `nop-stream-core/.../GraphExecutionPlan.java` L196-221

**是什么：**

当 vertex 有多个出边时，所有边的 partition 被合并到同一个 `writerPartitions` 列表。只使用第一条边的 `PartitionPolicy` 和 `IPartitioner` 创建 `RecordWriter`：

```java
PartitionPolicy policy = resolvePartitionPolicy(outEdges.get(0), deploymentPlan);
IPartitioner<?> partitioner = outEdges.get(0).getPartitioner();
```

如果 edge1 使用 FORWARD（2 partition），edge2 使用 HASH（3 partition），writer 有 5 个 partition，但路由策略只考虑 edge1 的 FORWARD（总是发到 channel 0）。edge2 的 partition 永远收不到数据。

**为什么不修会怎样：** 任何 fan-out 拓扑（一个算子同时连接两个下游算子）数据路由完全错误。只有第一条边的第一个 partition 收到数据。

**信心水平：** 确定

---

### N114：SharedBufferAccessor.extractPatterns DFS 遍历中间节点无 null 检查 — NPE

**在哪里：** `nop-stream-cep/.../sharedbuffer/SharedBufferAccessor.java` ~L185-194

**是什么：**

顶层入口有 null 检查（`if (entryLock != null)`），但 DFS while 循环中遍历 edge 到中间 target node 时：

```java
sharedBuffer.getEntry(target)   // 可返回 null
        .getElement()           // NPE
```

如果 target node 已被其他 computation state 释放（refCounter 降到 0），`getEntry()` 返回 null。这与 N47 报告的**顶层** null 检查是不同的代码路径——此处是 DFS 内部的中间节点。

**为什么不修会怎样：** 并发 buffer 释放场景下（短窗口 + 大量 partial match），pattern 提取时 NPE 崩溃。

**信心水平：** 很可能

---

### N115：SkipPastLastStrategy/SkipToNextStrategy 无空列表保护 — IndexOutOfBoundsException

**在哪里：** `nop-stream-cep/.../aftermatch/SkipPastLastStrategy.java` L41、`SkipToNextStrategy.java` L41

**是什么：**

```java
// SkipPastLastStrategy:
pruningId = max(pruningId, eventList.get(eventList.size() - 1)); // size=0 时 get(-1)

// SkipToNextStrategy:
pruningId = min(pruningId, eventList.get(0));  // size=0 时 get(0) → IOOBE
```

对比 `SkipToElementStrategy.getPruningId()` 有空列表保护。optional/singleton pattern 在特定边界条件下可能产生空 event list。

**信心水平：** 很可能

---

## P1：Connector 问题

### N116：DebeziumCdcSourceFunction 包含非序列化 CountDownLatch 字段 — 分布式执行序列化失败

**在哪里：** `nop-stream-connector/.../DebeziumCdcSourceFunction.java` L41

**是什么：**

```java
private final CountDownLatch completionLatch = new CountDownLatch(1);
```

`CountDownLatch` 不实现 `Serializable`。`DebeziumCdcSourceFunction` 通过 `DrainableSource → SourceFunction → StreamFunction → Serializable` 继承链声明为可序列化。在分布式执行模式下，`StreamElementCodec.encode()` 或 task 序列化时会抛 `NotSerializableException`。

嵌入式模式不受影响（对象不跨 JVM），但任何分布式部署（包括 `EmbeddedDistributedExecutor` 的 task 分发）会立即失败。

**信心水平：** 确定

---

### N117：StreamSinkOperator.close() 不调用 super.close() — RichFunction.close() 永不执行

**在哪里：** `nop-stream-core/.../StreamSinkOperator.java` L84-89

**是什么：**

```java
@Override
public void close() throws Exception {
    if (userFunction instanceof AutoCloseable) {
        ((AutoCloseable) userFunction).close();
    }
    // super.close() 未调用！
}
```

`AbstractUdfStreamOperator.close()` 会调用 `FunctionUtils.closeFunction(userFunction)`（处理 `RichFunction` 生命周期）。跳过 `super.close()` 意味着实现 `RichFunction` 的 sink function 的 `close()` 方法永不执行。

**信心水平：** 确定

---

### N118：BatchConsumerSinkFunction.flush() 传 null IBatchChunkContext — 使用 chunk context 的 consumer 会 NPE

**在哪里：** `nop-stream-connector/.../BatchConsumerSinkFunction.java` L47（构造器）、L66（flush）

**是什么：**

构造器创建了 `BatchTaskContextImpl` 但只作为局部变量传给 `consumerProvider.setup()`，未保存为字段。`flush()` 调用 `consumer.consume(buffer, null)`——第二个参数 `IBatchChunkContext` 为 null。

任何使用 chunk context 的 `IBatchConsumer`（如获取 task name、retry count 等）会 NPE。对比 `BatchLoaderSourceFunction` 正确使用 `taskContext.newChunkContext()`。

**信心水平：** 很可能

---

## P2：代码质量

### N119：SharedBuffer 创建 java.util.Timer 但从不使用 — 浪费 daemon thread

**在哪里：** `nop-stream-cep/.../sharedbuffer/SharedBuffer.java` ~L119

**是什么：**

```java
cacheStatisticsTimer = new Timer(true); // daemon thread 创建
// 从不 schedule 任何 TimerTask
```

每个 SharedBuffer 实例创建一个 Timer daemon thread 但从不使用。资源浪费（线程栈内存 + scheduler 开销）。

**信心水平：** 确定

---

### N120：NFACompiler.copyWithoutTransitiveNots 递归无环检测 — StackOverflow

**在哪里：** `nop-stream-cep/.../compiler/NFACompiler.java` ~L515

**是什么：**

递归遍历 PROCEED 边复制 state，但不维护 visited set。与 N49（运行时 `createDecisionGraph`/`findFinalStateAfterProceed`）是同类问题，但这是编译时路径（NFA 构建），是独立的代码路径。

**信心水平：** 很可能

---

## 总评

### 最值得关注的 3 个方向

1. **MemoryKeyedStateBackend 序列化盲区（N106）是最实际的状态丢失 bug**。`ReducingState` 和 `AggregatingState` 在 `snapshotState()` 的 `instanceof` 链中被静默跳过。这意味着使用这些状态类型的流应用（如 `keyBy().reduce()`、`keyBy().aggregate()`）在 checkpoint 恢复后状态完全丢失。在所有已有审计中，这个盲区从未被报告过，可能是最广泛的实际影响问题。

2. **InputGate 多通道超时即退出（N108）是 Graph Model 执行路径的又一个根本性缺陷**。与已知问题 N42（递归 CME）、N43（barriersRemaining 下溢）叠加，InputGate 的多通道实现有至少三个独立 bug。N108 是最严重的——任何有多个 InputChannel 的 task 都可能在正常运行中提前终止。之前审查都聚焦在 barrier 对齐和递归问题上，遗漏了超时行为。

3. **窗口算子的 trigger state 泄漏（N107+N110）是长时间运行作业的隐患**。`WindowOperatorTimerService.deleteEventTimeTimer` 不过滤 key 导致跨 key 误删 timer（N107），加上 `WindowOperator` 非合并路径不清理 trigger state（N110），长时间运行的流作业中 trigger state 无限增长。这两个问题在之前的密集审查中都没有被发现，说明窗口算子的清理路径是一个被忽视的区域。

### 历史问题修复确认

| # | 描述 | 状态 |
|---|------|------|
| N73 | HeapInternalTimerService CME | 已修复 |
| N74 | HeapInternalTimer.getKey() null | 已修复 |
| N75 | Graph Model 缺少 KeyExtractingOutput | 已修复 |
| N76 | StreamReduceOperator transient HashMap | 已修复 |
| N83 | invokeMiddle/invokeSink 不发 MAX_WATERMARK | 已修复 |
| N84 | processInputGate 不处理 WatermarkStatus | 已修复 |

### 本次审查的盲区自评

1. **没有验证 GraphExecutionPlan.deepCopy() 的完整性**：N109 确认了 deep copy chain 未被 open，但没有验证 deepCopy() 本身是否正确复制了所有状态（是否是 shallow copy 伪装为 deep copy）。
2. **没有审查 nop-stream-flink 适配层**：Flink 集成模块的正确性影响迁移路径，但本次未涉及。
3. **没有运行测试验证任何发现**：所有发现基于代码静态分析。特别是 N108（InputGate 超时退出）和 N106（ReducingState 序列化盲区）应该可以通过简单的单元测试确认。
4. **性能影响未量化**：N119（Timer 浪费线程）的实际资源开销没有测量。
5. **CheckpointBarrierTracker 的竞态条件**（N112 报告的 key 格式不匹配 + 之前 N89 报告的初始化竞态）需要通过压力测试验证实际触发频率。
