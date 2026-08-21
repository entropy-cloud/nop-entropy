# stream-cep 实现代码检查报告

- 检查日期: 2026-08-21
- 模块路径: nop-stream/nop-stream-cep
- 文件数: 实际清点 77（src/main/java，其中 `_gen/` 生成文件 4 个，非生成 73 个；与任务描述的"约 131"不符，以实际清点为准）
- 覆盖范围声明:
  - 全部 73 个非生成主代码文件均已浏览；其中约 25 个核心文件逐行深读（NFA、SharedBuffer、SharedBufferAccessor、Lockable/SharedBufferNode/SharedBufferEdge/NodeId/EventId、ComputationState、DeweyNumber、NFAState、State/StateTransition、NFACompiler、NFAStateNameHandler、CepOperator、Pattern、Quantifier、GroupPattern、CepPatternBuilder、AfterMatchSkipStrategy 全家族、conditions 全家族、PatternStream/Builder、CepRuntimeContext 等）。
  - 为验证资源管理与并发问题，追读了模块外依赖的关键实现：`nop-stream-core` 的 MapState/MemoryMapState/KeyExtractingOutput/ChainingOutput/HeapInternalTimerService，`nop-stream-rocksdb` 的 RocksDBMapState/RocksDBKeyedStateBackend。
  - 本模块为 Apache Flink CEP（约 1.16/1.17 版本语义）的移植。原计划与 Flink 原版逐行 diff，但环境中 GitHub/raw 网络不可达（404/-500），D8 对照改为基于 DeweyNumber 版本语义的引用计数平衡推演与逻辑自洽性验证，未做逐行 diff。
  - 未审计：`src/test`、`_gen/` 生成文件内部、`target/`。
  - 方法：先模块结构梳理，再 grep 扫描（空 catch / new RuntimeException / printStackTrace / synchronized / retain/release 等，均无命中），每个候选发现均 Read 上下文验证；对引用计数模型做了完整的加/减平衡推演（见各条"误报排除"）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 1 |
| P2 | 2 |
| P3 | 5 |

## 发现列表

### [P0] CepOperator 的 watermark/定时器回调使用错误的 key 上下文，多 key 流下事件滞留与状态泄漏

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:465-474, 533-606, 608-666`
- **维度**: D1 / D2 / D3
- **证据**:
```java
// processWatermark: 直接调用 onEventTime，没有任何 key 上下文切换
public void processWatermark(Watermark mark) throws Exception {
    long newWatermark = mark.getTimestamp();
    if (newWatermark > currentWatermark) {
        currentWatermark = newWatermark;
        if (!isProcessingTime) {
            onEventTime(currentWatermark);   // 无 setCurrentKey
        }
    }
    super.processWatermark(mark);
}

// onEventTime STEP 2: 只排"当前 key"的队列
while (!sortedTimestamps.isEmpty()
        && sortedTimestamps.peek() <= timerService.currentWatermark()) {
    long timestamp = sortedTimestamps.poll();
    advanceTime(nfaState, timestamp);        // 也只作用于当前 key
    ...
}

// timer 注册也是无 key 维度的纯时间戳集合
@Override
public void registerEventTimeTimer(VoidNamespace namespace, long time) {
    registeredEventTimeTimers.add(time);     // Set<Long>，无 key
}
```
- **现状**: `elementQueueState`、`computationStates`、`partialMatches`(SharedBuffer) 全部是 keyed state；key 上下文只由上游 `KeyExtractingOutput.processElement` 设置（其 `processWatermark` 不设 key，见 `nop-stream-core/.../KeyExtractingOutput.java:47-49`）。因此 watermark 到达时 `onEventTime`/`onProcessingTime` 排水、超时清理（`NFA.advanceTime`）只作用于"最后一个被处理元素所属的 key"。事件本身从不直接处理（event-time 模式 `processElement` 仅 `bufferEvent` 入队），唯一处理路径就是 onEventTime。
- **风险**: 对 `KeyedStream` 输入（多 key，`PatternStreamBuilder.build` line 157-160 走 `keyedStream.transform`）且 event-time 模式：非当前 key 的 buffered 事件在 watermark 越过其时间戳后仍滞留 `elementQueueState` 永不处理，直至该 key 再次有新事件且恰逢下一个 watermark；若该 key 不再活跃则事件永丢。同时其他 key 的 partial match 永不超时清理 → SharedBuffer 条目与 computation states 无限泄漏，超时回调（`processTimedOutMatch`）不触发。单 key 场景（非 keyed 输入走 `NullByteKeySelector`）不受影响。对照同仓库 `WindowOperator`（`nop-stream-runtime/.../windowing/WindowOperator.java:794`）用 `HeapInternalTimerService`（timer 携带 key，触发时 `setCurrentKey(timer.getKey())`）的正确做法，本 operator 的 timer/排水模型缺少 key 维度。
- **建议**: 仿照 WindowOperator 接入 `HeapInternalTimerService<K, VoidNamespace>`：timer 按 (key, timestamp) 注册，watermark 推进时逐 key 恢复上下文后排水；或在本 operator 内维护 key 集合，`onEventTime` 前对每个活跃 key 迭代 `setCurrentKey` + 排水。
- **误报排除**: 已确认 `ChainingOutput.processWatermark` 与 `KeyExtractingOutput.processWatermark` 均不设置 key；`AbstractStreamOperator.setCurrentKey` 仅委托 keyedStateBackend，watermark 路径无其他 key 切换点。非 keyed 输入统一 keyBy 常量 key，单 key 下行为正常——故风险限定于 keyed 多 key 场景，但该场景是 CEP 的主流用法（类 Javadoc 自述 "keeps one NFA per key, for keyed input streams"）。

### [P1] SharedBuffer.advanceTime 依赖 keys() 迭代器 remove，RocksDB 后端下静默失效导致 eventsCount 状态无限增长

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBuffer.java:224-234`
- **维度**: D2 / D6
- **证据**:
```java
void advanceTime(long timestamp) {
    Iterator<Long> iterator = eventsCount.keys().iterator();
    while (iterator.hasNext()) {
        Long next = iterator.next();
        if (next < timestamp) {
            iterator.remove();     // 依赖实现支持"通过视图删除底层条目"
        }
    }
    eventsBufferCache.asMap().keySet().removeIf(eventId ->
            eventId != null && eventId.getTimestamp() < timestamp);
}
```
- **现状**: `MapState.keys()` 的契约只承诺返回 `Iterable<UK>`（`nop-stream-core/.../state/MapState.java:98`），不承诺迭代器可删除。实际实现：`MemoryMapState.keys()` 返回底层 HashMap 的 `keySet()` 视图，`iterator.remove()` 有效；但 `RocksDBMapState.keys()` 返回 `collectMap().keySet()`（`nop-stream-rocksdb/.../RocksDBMapState.java:267-270`）——`collectMap()` 是把 RocksDB 数据全量读入的**本地副本**，对副本迭代器的 `remove()` 不会写回 RocksDB。
- **风险**: RocksDB state backend 下（`NopCepConfigs.COMMON_HINT` 明确将 rocksdb 列为支持的生产配置，且本模块的 cache 设计正是面向 rocksdb），`sharedBuffer-events-count` 中 timestamp 小于 watermark 的条目永远不被删除：该 keyed map 随每个事件时间戳增长一条，无限膨胀，checkpoint 快照与存储空间持续增长（慢性资源泄漏）。Memory 后端不受影响，故问题具有后端相关性、易在内存测试下漏检。
- **建议**: 改为先收集待删 key 再逐个 `eventsCount.remove(key)`（与 `MapState.remove(UK)` 契约一致），不依赖迭代器 remove 语义。
- **误报排除**: 已读取两处 `keys()` 实现源码确认行为差异；`advanceTime` 由 `NFA.advanceTime` → `CepOperator.advanceTime`（onEventTime STEP 2/3、onProcessingTime STEP 2/3）在每个 watermark/timer 上调用，触发路径现实。

### [P2] CepOperator 缓存统计定时器 close 与 re-arm 存在竞态，close 后定时器链可无限延续

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:395-417`
- **维度**: D2 / D3
- **证据**:
```java
void onCacheStatisticsTimer(long timestamp) {
    if (partialMatches != null) {
        partialMatches.logCacheStatistics();
    }
    if (cacheStatsIntervalMs > 0) {
        // Re-arm anchored to the fire time (not current time) to avoid drift.
        cacheStatsTimerFuture = getProcessingTimeService().registerTimer(
                timestamp + cacheStatsIntervalMs, this::onCacheStatisticsTimer);
    }
}

void releaseCacheStatisticsTimer() {
    if (cacheStatsTimerFuture != null) {
        cacheStatsTimerFuture.cancel(false);   // 不中断正在执行的任务
        cacheStatsTimerFuture = null;
    }
}
```
- **现状**: `onCacheStatisticsTimer` 运行在 ProcessingTimeService 的 timer 线程，`close()`（调用 `releaseCacheStatisticsTimer`）运行在另一线程。`cancel(false)` 不中断执行中的回调；若 close 恰好发生回调执行期间，回调随后 `registerTimer` 重新赋值 `cacheStatsTimerFuture`，而 cancel 已作用于旧 future 并将字段置 null——新 future 永远不会被取消。
- **风险**: close 之后周期性 cache 统计任务无限自我重注册（定时器链泄漏），并在 operator 已 teardown 后继续访问 `partialMatches`（字段未置 null，仅读统计，暂无 NPE，但生命周期违约）。窗口窄但确定存在；本平台对该 timer 的分层设计（与 CEP 事件处理 timer 分离）说明它是新增功能而非 Flink 移植。
- **建议**: 引入 `volatile boolean closed` 标志，re-arm 前检查；或 `close()` 中先置标志再 cancel，回调检测到标志后不再 re-arm；同时 `close()` 中将 `partialMatches` 置 null 前需保证回调不再运行（例如 `cancel(true)` 或同步点）。
- **误报排除**: 已确认该 timer 与 `timerService`/`cepTimerService` 分离（类注释 line 178-190 明确说明），确实由独立 ProcessingTimeCallback 驱动；close 与 timer 线程之间无任何同步原语。

### [P2] Lockable.releaseOrDetach 把 refCounter==0 视为"可回收"，掩盖过度释放并可能提前删除仍在使用的条目

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/Lockable.java:56-81`
- **维度**: D4 / D2
- **证据**:
```java
boolean release() {
    int old;
    do {
        old = refCounter.get();
        if (old <= 0) {
            refCounter.set(0);
            throw new StreamRuntimeException("Lockable over-release: refCounter went negative");
        }
    } while (!refCounter.compareAndSet(old, old - 1));
    return old == 1;
}

boolean releaseOrDetach() {
    int old;
    do {
        old = refCounter.get();
        if (old < 0) { ... throw ... }
        if (old == 0) {
            return true;          // 0 引用 → 视为"已脱离"，直接判可回收
        }
    } while (!refCounter.compareAndSet(old, old - 1));
    return old == 1;
}
```
- **现状**: Flink 原版 `Lockable.release()` 在引用计数归零后再释放是 assert 失败（尽早暴露计数 bug）。本版本的 `releaseOrDetach()`（平台新增，被 `SharedBufferAccessor.releaseNode/releaseEvent` 全部使用）在 `refCounter==0` 时返回 true → 调用方执行 `removeEntry` + `releaseEvent`，把条目从 buffer 中删除。
- **风险**: 引用计数一旦出现过度释放（多 release 一次），Flink 会在第一次越界处立刻失败暴露；本版本则静默删除条目，破坏向后传播——后续仍需该 node 的 `extractPatterns`/`materializeMatch` 将取到 null（`getEntry(target)` 为 null 时 `Tuple2.of(target, null)`，后续 `currentEntry.f1.getEdges()` NPE；`getEvent` 为 null 时 NPE 被 catch 转为泛化 StreamException "match materialization failed"），错误从"计数违约的快速失败"退化为"延迟的、无因果关系的 NPE"。同时 `release()` 在抛异常前先 `refCounter.set(0)`，异常路径仍修改了共享状态。
- **建议**: `releaseOrDetach` 对 0 引用应与负引用同样抛出（或至少记录 WARN 并返回 false 保守处理），让计数缺陷在第一现场暴露；`release()` 中先抛后修（去掉 set(0)）。
- **误报排除**: 已通读全部 lock/release 调用点（put/lockNode/lockEvent/releaseNode/releaseEvent/EventWrapper.close），确认正常路径下被 release 的对象计数均 >0（put 新建 node 计数为 0 后紧跟 addComputationState 的 lockNode+1，瞬态 0 窗口内不会发生 release）；本条针对的是异常场景下的失败模式劣化与防御语义，非断言当前必现。

### [P3] 多处 MalformedPatternException 抛出时丢弃具体错误消息，仅剩泛化错误码

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/pattern/Quantifier.java:85-89`; `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java:192, 217, 233`; `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFAStateNameHandler.java:58-63`
- **维度**: D4 / D7
- **证据**:
```java
// Quantifier.checkPattern: errorMessage 参数被完全丢弃
private static void checkPattern(boolean condition, Object errorMessage) {
    if (!condition) {
        throw new MalformedPatternException(ERR_CEP_MALFORMED_PATTERN);
    }
}
// 调用方: checkPattern(!hasProperty(OPTIONAL), "Optional already applied!") 等
```
- **现状**: `NopCepErrors.ERR_CEP_MALFORMED_PATTERN` 定义了 `ARG_PATTERN_DETAIL` 参数，`Pattern.java` 中多处正确使用 `.param(ARG_PATTERN_DETAIL, ...)`；但 `Quantifier.checkPattern` 把调用方传入的具体原因（"Optional already applied!"、"You can apply either combinations or consecutive, not both!" 等）整个丢弃，`NFACompiler` 的 `checkPatternWindowTimes`/`checkPatternSkipStrategy`/NOT_FOLLOW 无窗口校验以及 `NFAStateNameHandler.checkNameUniqueness` 也不带任何 detail。
- **风险**: 用户配置错误的诊断体验显著劣化（只看到 "Malformed CEP pattern: {patternDetail}" 且 patternDetail 为空），违背平台错误处理"带上下文参数"的约定精神；Flink 原版这些位置都携带消息。
- **建议**: 统一 `.param(ARG_PATTERN_DETAIL, String.valueOf(errorMessage))` 补齐。
- **误报排除**: 已核对 `NopCepErrors` 的参数定义与 `Pattern.java` 中的正确用法，确认是局部遗漏而非全局约定。

### [P3] CepOperator STEP 5 自研"悬挂 partial match 清理"语义漂移且在 onEventTime/onProcessingTime 重复 30 行

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:563-592, 636-665`
- **维度**: D1 / D6
- **证据**:
```java
// 两处几乎逐字相同的代码（onEventTime / onProcessingTime）
if (nfaState.getPartialMatches().size() == 1 && nfaState.getCompletedMatches().isEmpty()) {
    boolean allTimedOut = true;
    for (Object pm : nfaState.getPartialMatches()) {
        ...
        Map<String, Long> windowTimes = nfa.getWindowTimes();
        long wt = windowTimes != null && windowTimes.containsKey(stateName)
                ? windowTimes.get(stateName) : nfa.getWindowTime();
        if (wt <= 0 || timerService.currentWatermark() < cs.getStartTimestamp() + wt) {
            allTimedOut = false;
            ...
        }
    }
    if (allTimedOut) {
        try (SharedBufferAccessor<IN> accessor = partialMatches.getAccessor()) { ... releaseNode ... }
        computationStates.clear();   // 绕过 updateNFA 的 stateChanged 机制直接清持久状态
    }
}
```
- **现状**: Flink 原版 CepOperator 无此段（平台自研兜底）。两个问题：(1) `windowTimes` 里存的是 `WithinType.PREVIOUS_AND_CURRENT`（相邻事件间隔）窗口，`NFA.advanceTime` 对其用 `previousTimestamp` 判超时；STEP 5 却用 `startTimestamp + wt`（首尾间隔语义）判定，窗口语义被混用；(2) `computationStates.clear()` 绕过 `updateNFA`/`stateChanged` 状态机直接清 keyed state。
- **风险**: 当前实际可触达面很窄——已验证 `size()==1` 时唯一元素必为 start state（start state 不超时、不被 prune、由 doProcess/advanceTime 保底保留），start state 的 `previousBufferEntry` 为 null 使 releaseNode 空转、startTimestamp=-1 使 clear 等价于"重置为初始 NFA"（下次 `getNFAState()` 自动重建），故现状近似死代码级冗余。但一旦 partialMatches 不变式变化（如 future 修改保留策略），按错误的窗口语义清除会把未超时的匹配提前丢弃（漏报）；重复的 30 行也必然产生修一处漏一处的维护风险。
- **建议**: 删除该兜底段（advanceTime 已按正确语义清理超时）；若保留，抽公共方法并改用与 `NFA.advanceTime` 一致的双窗口判定（previousTimestamp + per-state wt / startTimestamp + 全局 wt）。
- **误报排除**: 对"size()==1 时必为 start state"做了完整推演（start state 不参与超时/stop/prune 移除路径），确认现状无直接数据错误，故定 P3 而非 P1。

### [P3] 热点路径性能杂项：DeweyNumber 比较走字符串解析、extractPatterns 重复查询、registerEvent 双重查找

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFAState.java:128-137`; `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBufferAccessor.java:186-194`; `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBuffer.java:242-249`
- **维度**: D6
- **证据**:
```java
// NFAState.compareDeweyNumber: 每次比较 2 次 toString + 2 次 split + 2N 次 parseInt
private static int compareDeweyNumber(DeweyNumber a, DeweyNumber b) {
    int minLen = Math.min(a.length(), b.length());
    String[] da = a.toString().split("\\.");
    String[] db = b.toString().split("\\.");
    ...
}

// SharedBufferAccessor.extractPatterns: 同一 target 查两次 getEntry
target != null
        ? Tuple2.of(target,
                sharedBuffer.getEntry(target) != null
                        ? sharedBuffer.getEntry(target).getElement()
                        : null)
        : null

// SharedBuffer.registerEvent: 每个事件注册先查 cache 再查 backing state
while (eventsBufferCache.asMap().containsKey(eventId) || hasEventInBuffer(eventId)) { ... }
```
- **现状**: `DeweyNumber` 本身持有 `int[]`，可直接按位比较，`NFAState` 却经字符串往返（仅影响 equals/hashCode/测试排序路径）；`extractPatterns` 是每事件 DFS 的最内层循环，双查询在 RocksDB 后端下意味着双倍 cache/state 读；`registerEvent` 的 `hasEventInBuffer` 正常情况（eventsCount 计数正确）必然 miss，却每事件都打一次底层 state 读。
- **风险**: 高吞吐流上放大状态后端读放大；均不改变正确性。
- **建议**: `DeweyNumber` 增加 `compareTo`；`Lockable<SharedBufferNode> n = getEntry(target); Tuple2.of(target, n != null ? n.getElement() : null)`；`registerEvent` 以 eventsCount 为准分配 id、仅防御性冲突时再查。
- **误报排除**: 三处均已读上下文确认调用频率（extractPatterns 在每次匹配提取/条件求值 `getEventsForPattern` 时进入 DFS；registerEvent 每事件一次）。

### [P3] PatternStreamBuilder 恒传 null inputSerializer，SharedBuffer 序列化参数形同虚设

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/PatternStreamBuilder.java:139`; `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBuffer.java:100-118`
- **维度**: D8 / D7
- **证据**:
```java
final TypeSerializer<IN> inputSerializer = null;   // PatternStreamBuilder.build
...
partialMatches = new SharedBuffer<>(keyedStateStore, inputSerializer, new SharedBufferCacheConfig());
// SharedBuffer 构造器签名要求 TypeSerializer<V> valueSerializer，但函数体从未使用该参数
```
- **现状**: Flink 原版此处用 `inputStream.getType().createSerializer(...)`；本平台唯一构造调用点恒传 null，而 `SharedBuffer` 构造器接受 `valueSerializer` 却完全忽略（MapStateDescriptor 用原始 Class 提示）。实际值序列化完全取决于 state backend 的通用 serde。
- **风险**: API 契约欺骗（签名承诺序列化器、实现忽略）；不同 backend 下 `Lockable`/事件值的编码一致性只能依赖 backend 默认行为，未来接入要求显式 serializer 的 backend 时将静默偏离。
- **建议**: 要么删除该参数（如实反映契约），要么从 `TypeInformation` 创建并传入。
- **误报排除**: 已检查 `SharedBuffer` 构造器全文确认 valueSerializer 无任何使用；已检查 `RocksDBMapState` 使用 backend 通用 serde，当前两后端可运行。

### [P3] SkipToElementStrategy 混用 JDK 异常与无错误码的 StreamException，违背平台错误处理两档约定

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/aftermatch/SkipToElementStrategy.java:60-78`
- **维度**: D4 / D7
- **证据**:
```java
if (shouldThrowException) {
    throw new StreamException(
            String.format(
                    "Could not skip to %s. No such element in the found match %s",
                    patternName, resultMap));          // String message，无 ErrorCode
}
...
EventId startEvent = ... .orElseThrow(
        () -> new IllegalStateException("Cannot prune based on empty match"));  // JDK 异常
```
- **现状**: 该类是面向用户的公共校验路径（`throwExceptionOnMiss()` 启用后）。同文件/同家族其他位置（`SkipToFirstStrategy`、`NoSkipStrategy`）均已 Nop 化为 `ErrorCode + .param(...)`，唯独此处遗留 `StreamException(String)` 与裸 `IllegalStateException`。模块其余部分的错误处理（NopCepErrors、StreamException(ErrorCode, cause).param）总体规范，无 bare RuntimeException、无空 catch、无 printStackTrace（grep 零命中）。
- **风险**: 异常分流/错误码追踪（两档策略）在这两个分支断链；维护一致性风险。
- **建议**: 定义/复用 ErrorCode 并 `.param` 携带 patternName 与 match 内容。
- **误报排除**: 已全模块 grep `new RuntimeException|printStackTrace|catch.*{}`，确认无其他同类命中；本条是仅有的两处遗留。

## 总结

模块整体是 Flink CEP 的高保真移植，算法核心（DeweyNumber 版本分配、NFA 主循环、skip 策略、Pattern API）经推演与 Flink 语义一致，未发现匹配组合语义（begin/next/followedBy/notFollowedBy、贪婪/非贪婪）的移植错误；引用计数加/减平衡经完整推演成立（包括对 `releaseNode` 中新增 `visited` 剪枝的可达性分析：单次调用的版本兼容性保证路径唯一，剪枝不可达、无害）。真正的风险集中在平台适配层：keyed 语义下的 watermark/timer 模型（P0）、跨后端的 MapState 迭代器删除语义（P1）、自研缓存统计定时器的生命周期（P2）与 `releaseOrDetach` 的失败模式弱化（P2）。
