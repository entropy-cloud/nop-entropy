# stream-core 实现代码检查报告

- 检查日期: 2026-08-20
- 模块路径: nop-stream/nop-stream-core
- 文件数: 364（src/main/java 实测；任务描述的 578 与实际不符，以实际为准）
- 覆盖范围声明:
  - 全量 grep 扫描 364 个文件：空 catch、`new RuntimeException`、`printStackTrace`、synchronized、可变 static 集合、ThreadLocal（结果：空 catch / printStackTrace / bare RuntimeException / ThreadLocal 均为 0 命中）。
  - 深读约 45 个高风险文件：eventtime 全部核心类（TimestampsAndWatermarksOperator、WatermarkOutputMultiplexer、CombinedWatermarkStatus、IndexedCombinedWatermarkStatus、WatermarksWithIdleness、BoundedOutOfOrdernessWatermarks 等）；operators 主要类（AbstractStreamOperator、HeapInternalTimerService、TimerServiceManager、ChainingOutput、TimestampsAndWatermarksOperator、StreamSourceOperator、SourceReaderOperator、StreamReduceOperator、StreamSinkOperator、ProcessOperator、KeyExtractingOutput、TimestampedCollector）；状态后端（MemoryKeyedStateBackend、MemoryStateSerDe、MemoryValueState、TtlContext、ContainerValueCodec、KeyGroupAssignment、ShardPrefixedKey、ClassNameValidator）；执行层（StreamTaskInvokable、SubtaskTask、TaskExecutor、InputGate、RecordWriter、ResultPartition、TaskMailbox、TaskProcessingTimeService、ProcessingTimeServiceDriver、CheckpointBarrierTracker、InMemoryMaterializationPoint）；窗口（TimeWindow、Tumbling/Sliding/Session assigners、EventTimeTrigger、CountTrigger、ContinuousProcessingTimeTrigger）；序列化（StreamElementCodec、BasicTypeInfo）；异常（StreamException、StreamRuntimeException）；TwoPhaseCommitSinkFunction、LocalSourceCoordinator（部分）。
  - 为验证误报，只读查阅了 nop-stream-runtime 的 GraphModelCheckpointExecutor / SupervisionLoop / WindowOperator 恢复调用顺序（未修改任何文件；runtime 模块本身不在审计范围）。
  - 未深读（低风险或生成物外围）：jobgraph 图生成器（JobGraphGenerator/StreamGraphGenerator）、graph、checkpoint/storage 与 incremental、connector、model、configuration、typeinfo 其余类、flow/buffer 细节。WindowOperator 实现在 nop-stream-runtime（不在本模块），本报告窗口结论仅覆盖 core 侧 assigner/trigger/TimeWindow/timer 语义。
  - 结论性印象：core 侧大量类为 Flink 忠实移植（eventtime、窗口 assigner/trigger 与 Flink 逐行一致），异常体系整体符合 Nop 两档策略（NopStreamErrors + StreamException + .param），未见 bare RuntimeException。发现集中在：恢复生命周期（restore 先于 open）与自管状态算子的冲突、idleness 传播断链、异常吞噬三处系统性问题。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 4 |
| P2 | 7 |
| P3 | 3 |

## 发现列表

### [P0] StreamReduceOperator.open() 无条件清空 restoreState() 已恢复的 keyed reduce 状态

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamReduceOperator.java:74-77`（open）与 `:119-148`（restoreState）
- **维度**: D1（数据错误）、D4（故障恢复语义）
- **证据**:
```java
@Override
public void open() throws Exception {
    super.open();
    values = new HashMap<>();          // 无条件重建，覆盖 restoreState 写入的 values
}

@Override
public void restoreState(OperatorSnapshotResult snapshotResult) throws Exception {
    super.restoreState(snapshotResult);
    Object stateObj = snapshotResult.getOperatorState(REDUCE_STATE_KEY);
    if (stateObj instanceof List) {
        values = new HashMap<>();      // restore 直接赋值，无 deferred 暂存
        ...                            // 逐条 put 进 values
    }
}
```
- **现状**: 平台恢复生命周期是 restore 先于 open：runtime 侧 `GraphModelCheckpointExecutor.restoreOperatorsFromState`（约 :1613-1660）在 rebuilt invokable 提交前调用 `((AbstractStreamOperator<?>) op).restoreState(opResult)`，随后 SubtaskTask → `invoke()` → `operatorChain.open()`；runtime 的 WindowOperator 源码注释明确写有 "restoreState() (called before open())，This deferred-application pattern is required"。WindowOperator 为此把快照暂存到 open() 再应用；StreamReduceOperator 却在 open() 里直接 `values = new HashMap<>()`，把恢复结果整个丢弃。
- **风险**: `KeyedStream.reduce()/sum()/min()/max()`（KeyedStreamImpl.java:186-260 全部走 StreamReduceOperator）在任何基于 checkpoint 的恢复（region restart / restoreFromCheckpoint）后，所有 key 的聚合状态静默清零：聚从中途重新开始、每个 key 的首个值被当作初始值重新向下游发射（对下游 sink 是重复/错误数据）。无任何报错。
- **建议**: 仿照 WindowOperator 的 deferred 模式——restoreState 只暂存快照，open() 中先建空 map、再应用暂存快照；或 open() 仅在 `values == null` 时初始化。
- **误报排除**: 确认 `GraphModelCheckpointExecutor`（初始恢复）与 `SupervisionLoop.rebuildTask`（region 重启，:655）两条路径均在任务提交/invoke（即 open）之前调用 restoreState；`OperatorChain.open()`（OperatorChain.java）会对每个 operator 调 open()。不存在 restore 在 open 之后的恢复路径。该算子不经 keyedStateBackend 存状态，无其他恢复通道兜底。

### [P1] 水位线空闲状态（WatermarkStatus）不跨任务边界传播，空闲上游将永久钉住下游 watermark

- **文件**:
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/TimestampsAndWatermarksOperator.java:149-170`
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/StreamTaskInvokable.java:908-911`（及 :976-978）
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:920-935`
- **维度**: D1（watermark 传播）、D8（与 Flink 语义漂移）
- **证据**:
```java
// TimestampsAndWatermarksOperator$OperatorWatermarkOutput —— markIdle 只置本地标志
@Override
public void markIdle() {
    idle = true;                 // 未调用 output.emitWatermarkStatus(WatermarkStatus.IDLE)
}

// StreamTaskInvokable$RecordWriterOutput —— 状态不跨任务
@Override
public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus status) {
    // Not forwarded across task boundaries
}

// InputGate.handleWatermarkNonRecursive —— 合并只取 min，无 idle 通道排除
if (watermark.getTimestamp() <= oldWatermark) { return Optional.empty(); }
currentWatermarks[channelIndex] = watermark.getTimestamp();
long oldMin = minWatermarkExcluding(channelIndex, oldWatermark);
```
- **现状**: 链条在三个断点同时失效：(1) 算子层 `markIdle()` 不向下游发 `WatermarkStatus.IDLE`（Flink 的 WatermarkEmitter.markIdle 会 `output.markIdle()` 传播），且 `emitWatermark` 在 idle 时静默丢弃水位线（Flink 不做此检查）；(2) 跨任务边界 `RecordWriterOutput/BroadcastingRecordWriterOutput.emitWatermarkStatus` 为空实现；(3) `InputGate` 的多通道 watermark 合并没有 idle 概念，永远取 min。此外 `StreamSourceOperator.SourceContext.markAsTemporarilyIdle()`（:223-225）虽发出 IDLE，也会被 (2) 拦截。`AbstractStreamOperator.processWatermarkStatus1/2` 与 `ChainingOutput.emitWatermarkStatus` 表明链内传播已实现，唯独跨任务链路断裂。
- **风险**: 下游任务输入并行度 > 1 时（多 InputChannel 是常态），任一上游 subtask 空闲（`WatermarksWithIdleness`、source 暂时无数据 markIdle）后其 channel watermark 停滞，InputGate 的 min 永不前进 → 下游事件时间定时器/窗口全部停摆，作业不报错、吞吐归零。Flink 中空闲通道会被排除出 min 计算。
- **建议**: `OperatorWatermarkOutput.markIdle/markActive` 传播 `output.emitWatermarkStatus`；`RecordWriterOutput.emitWatermarkStatus` 广播到所有 partition；InputGate 增加每 channel idle 状态，min 计算排除 idle 通道（all-idle 时输出 IDLE）。
- **误报排除**: 已核对 InputGate 全部读取路径（readMultiChannel 只处理 barrier/watermark，WatermarkStatus 元素不经合并直接透传，而由于 (2) 它根本到不了 InputGate）；core 的 DataStream API 当前无 union/connect，双输入合并路径不可达，但"多上游 subtask → 单下游任务"经由 InputGate 完全可触发。

### [P1] TimerServiceManager 吞掉定时器服务异常，窗口/定时器计算失败仅记日志不失败任务

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/TimerServiceManager.java:45-63`
- **维度**: D4（异常吞噬）、D1（数据正确性）
- **证据**:
```java
public void advanceWatermark(Watermark mark) throws Exception {
    for (HeapInternalTimerService<?, ?> service : timerServices) {
        try {
            advanceWatermarkUnchecked(service, mark.getTimestamp());
        } catch (Exception e) {
            LOG.error("Failed to advance watermark for timer service: {}", service, e);  // 吞掉
        }
    }
}
// fireProcessingTimeTimers 同样逐 service catch + LOG.error
```
- **现状**: `HeapInternalTimerService.advanceWatermark` 会经 `triggerable.onEventTime(...)` 回调进 WindowOperator/ProcessOperator 的用户代码（ProcessWindowFunction/processFunction.onTimer）。任何用户回调抛错（NPE、下游 sink 失败等）都被此处 catch 后仅 LOG.error，`AbstractStreamOperator.processWatermark` 继续把 watermark 往下游转发，任务照常运行。对比之下 `TaskProcessingTimeService.fireDueTimers` 的 javadoc 明确选择 fail-fast（异常传播并使任务可见失败）；两条定时器路径策略相反。
- **风险**: 事件时间窗口触发时计算失败 → 该窗口数据部分发射或全部丢失，作业继续运行，从日志以外无从察觉；没有重启/恢复兜底，静默数据错误。这正是 Flink 中 onEventTime 异常会使任务进入 FAILED 并触发恢复的场景。
- **建议**: advanceWatermark/fireProcessingTimeTimers 不要吞异常——收集后抛出（或首个异常抛出、其余 addSuppressed），让任务失败进入恢复流程；保留 per-service 日志仅用于诊断。
- **误报排除**: 类 javadoc 承认这是"established robustness contract"（有测试固化），属有意设计而非笔误；但该设计与平台自身 fail-fast 决策（TaskProcessingTimeService）冲突且语义为静默数据丢失，仍判定为问题而非误报。回调链已验证：AbstractStreamOperator.processWatermark → timeServiceManager.advanceWatermark → HeapInternalTimerService.advanceWatermark → triggerable.onEventTime。

### [P1] SourceReaderOperator.restoreState 在 reader 未创建时静默丢弃 FLIP-27 source 的 split 断点状态

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/SourceReaderOperator.java:379-394`
- **维度**: D1（恢复后状态丢失）、D4
- **证据**:
```java
@Override
public void restoreState(OperatorSnapshotResult snapshotResult) throws Exception {
    super.restoreState(snapshotResult);
    if (reader != null && snapshotResult != null) {      // reader 在 open() 中才创建
        Object stored = snapshotResult.getOperatorState(READER_SPLITS_KEY);
        if (stored instanceof java.util.List) {
            ...
            ((SourceReader) reader).restoreState((java.util.List) splits);
        }
    }
}
```
- **现状**: `reader` 只在 `open()`（:211-236）里经 `source.createReader(ctx)` 创建；而平台恢复生命周期是 restoreState 先于 open()（同 P0 条目证据）。因此恢复路径上 `reader` 恒为 null，`READER_SPLITS_KEY` 的 split 列表被静默跳过。该类自己已有 `preOpenSplits` 缓冲机制（处理 coordinator 早于 reader 的 split 投递），却没有为快照恢复做同样的暂存。
- **风险**: 基于 FLIP-27 source 的作业 checkpoint 恢复后，reader 的 per-split cursor 丢失：有界 source 从头重读（下游重复消费），无界 source 丢失消费位点（数据丢失或重复），无任何报错。`snapshotState` 精心写入的 split 状态成为只写不读。
- **建议**: restoreState 将 splits 暂存到字段（类比 `preOpenSplits` / AbstractStreamOperator.pendingRestoreState），open() 创建 reader 后 flush。
- **误报排除**: 已确认 runtime 恢复路径（GraphModelCheckpointExecutor.restoreOperatorsFromState）在 open 之前调用本方法；open() 也不从 operatorStateBackend 回读该 key；唯一疑问是是否存在别的 restore-后-open 调用方——core 内无任何调用方，runtime 仅上述一处（SupervisionLoop 复用同一方法）。

### [P1] ProcessOperator 的用户定时器不参与 checkpoint 快照/恢复，重启后定时器全部丢失

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/ProcessOperator.java:36-49`
- **维度**: D2（状态/定时器持久化）、D4（故障恢复语义）
- **证据**:
```java
@Override
public void open() throws Exception {
    super.open();
    ...
    internalTimerService = new HeapInternalTimerService<>(   // 每次全新创建
            (Triggerable<Object, VoidNamespace>) this,
            () -> getCurrentKey());
    if (timeServiceManager != null) {
        timeServiceManager.registerTimerService(internalTimerService);
    }
    ...
}
```
- **现状**: `HeapInternalTimerService` 提供了 `snapshotTimers()/restoreTimers()`（HeapInternalTimerService.java:255/:280，注释明确为 "G2: timer state survives checkpoint/restore"），但 core 内无任何调用点；grep 确认唯一接线在 runtime 的 WindowOperator（:565 快照 / :496 恢复）。ProcessOperator 经 `userTimerService`（InternalTimerServiceTimerWrapper）注册的事件/处理时间定时器既不被 `snapshotState` 捕获，恢复时也不重建。
- **风险**: 使用 `process()` + `timerService().registerEventTimeTimer/...` 的作业（超时检测、延迟触发等），任何一次 checkpoint 恢复后所有在途定时器静默消失，`onTimer` 永不触发 → 功能性输出缺失，无报错。Flink 中 KeyedProcessFunction 的定时器随 keyed state 一起快照恢复。
- **建议**: 在 ProcessOperator.snapshotState 中附加 `internalTimerService.snapshotTimers()`（参考 WindowOperator 的 "internal-timers" key），open()/恢复路径调用 restoreTimers。
- **误报排除**: 已确认 AbstractStreamOperator.snapshotState 只处理 keyed/operator state backend，不触碰算子私有 timer service；runtime 只为 WindowOperator 接线定时器快照。

### [P2] StreamReduceOperator 快照值/键经 JSON 持久化后无法正确还原（被 P0 掩盖）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamReduceOperator.java:100-148`
- **维度**: D1、D8（与 MemoryStateSerDe 的 AR-01/AR-22 修复不对齐）
- **证据**:
```java
entry.put("key", e.getKey());                       // 原始对象直接入快照
entry.put("value", e.getValue());
...
String expectedType = (String) entry.get("valueTypeName");
if (expectedType != null && !expectedType.equals(value.getClass().getName())) {
    throw new StreamException(ERR_STREAM_TYPE_MISMATCH) ...   // POJO → LinkedHashMap 即抛
}
values.put(key, (T) value);                         // key 无按 keyType 再物化
```
- **现状**: 快照把 key/value 原始对象放进 operator state；JSON 持久化路径（storageType="local"）下 POJO 值回来是 LinkedHashMap，`valueTypeName` 比对直接抛 ERR_STREAM_TYPE_MISMATCH（fail-fast，但 POJO keyed reduce 完全无法恢复）；数字 key（Long）回来变 Integer（MemoryStateSerDe/HeapInternalTimerService 注释中 AR-01/AR-22 已确认的 TextScanner 行为），而本算子无 `deserializeKey` 式的按 keyType 再物化，恢复后 `values.get(Long)` 永远 miss。该模块其他位置都已修复此问题（MemoryStateSerDe.deserializeKey、HeapInternalTimerService.rematerializeEntry），唯独此处遗漏。
- **风险**: 修复 P0 的 open() 清空问题后本问题立即暴露：POJO 值恢复直接失败；Long key 恢复后聚合状态按 key 静默重置。
- **建议**: 快照/恢复走 MemoryStateSerDe 同款处理——值用 `deserializeValue`（JSON 再物化）、key 按声明 keyType 再物化；或直接改用 keyedStateBackend 存 reduce 状态。
- **误报排除**: 当前因 P0（open() 清空）此路径的产物根本活不到 processElement，故降级 P2；AR-01/AR-22 的 Long→Integer 行为有代码库内注释与既有修复佐证，非推测。

### [P2] MemoryStateSerDe.serializeWithSerializer 序列化失败静默回退原始对象，可能产出不可恢复的快照

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java:878-887`
- **维度**: D4（异常吞噬）、D2（状态损坏）
- **证据**:
```java
private <T> Object serializeWithSerializer(Object value, IStreamSerializer<T> serializer) {
    if (serializer == null || value == null) {
        return value;
    }
    try {
        return serializer.serialize((T) value);
    } catch (Exception e) {
        return value;      // 无日志、无标记，直接以原始对象入快照
    }
}
```
- **现状**: 自定义 `IStreamSerializer` 抛错时，快照退化为存原始对象。恢复侧 `deserializeValue`（:895-904）只在 `obj instanceof byte[]` 时走自定义反序列化，原始对象会落入 Container/JSON 兜底路径——非 JSON 可序列化对象在持久化层就会失败，JSON 可序列化对象则绕过了自定义编码器的语义（可能类型漂移）。注释自认 "P2-09-02c tracks its silent degradation separately"，但至少应有日志。
- **风险**: 状态快照在"成功"外表下包含自定义编码器无法还原的数据；恢复失败或状态语义损坏，且零日志可查。
- **建议**: 至少 LOG.warn 携带状态名；更优是 fail-fast（快照期抛错使 checkpoint 失败，符合代码库自身的 No-Silent-No-Op 规则 #24）。
- **误报排除**: 恢复侧 instanceof byte[] 分支已核对，确认退化路径存在；调用点覆盖全部 8 类 state 快照方法。

### [P2] ResultPartition.injectFront 恢复回放可自阻塞（E+I 超容量且消费者未启动）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ResultPartition.java:434-472`
- **维度**: D3/D2（恢复路径死锁风险）
- **证据**:
```java
public void injectFront(List<StreamElement> elements) {
    ...
    List<StreamElement> existing = new ArrayList<>();
    while ((e = queue.poll()) != null) { ... existing.add(e); }  // 先清空队列
    for (StreamElement injected : elements) {
        if (bufferPool != null) { bufferPool.acquire(); }
        queue.put(injected);                                      // 容量满则阻塞
    }
    for (StreamElement old : existing) {
        queue.put(old);                                           // 同上，无界等待消费者
    }
```
- **现状**: unaligned checkpoint 恢复时（InputGate.restoreChannelState → injectElements → injectFront，javadoc 明确 "before the task thread starts processing"）先把队列现有 E 条腾空，再放回 E+I 条。`LinkedBlockingQueue` 容量固定（默认 1024），E+I > capacity 时 `queue.put` 无限阻塞，而此刻消费者任务尚未开始读取，无人释放空间。
- **风险**: 故障时队列较满 + 回放量较大的恢复场景，恢复流程永久挂起（无超时、无报错）。需要 bufferPool 时还可能同时占满全局 permit，放大为跨任务影响。
- **建议**: 恢复注入绕过容量限制（如临时专用构造/反射字段，或改为 poll+offer+忙等上限+失败报错）；或保证恢复注入量与剩余容量的不变式并在超容量时 fail-fast。
- **误报排除**: E ≤ capacity、I ≥ 1 时 E+I 完全可超 capacity（如队列满 1024 时捕获回放 50 条）；调用时序（恢复先于任务启动）由 InputGate.restoreChannelState javadoc 与 GraphModelCheckpointExecutor 恢复流程（operator restore 后、任务提交前）证实。概率性场景，故 P2 而非 P1。

### [P2] SourceReaderOperator.run() 空闲时 Thread.yield() 忙等，烧 CPU

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/SourceReaderOperator.java:303-318`
- **维度**: D6（性能）
- **证据**:
```java
if (next != null && next.isPresent()) {
    output.collect(new StreamRecord<>(next.get()));
} else {
    ...
    if (reader.isFinished()) { break; }
    // Cooperative yield: avoid busy-loop when reader is idle but not finished.
    Thread.yield();
}
```
- **现状**: 无界 source 空闲时 `pollNext()` 返回空，run() 仅 `Thread.yield()` 后立即重试，无任何 park/sleep。每个空闲 source subtask 占满一个核。对比 InputGate 用 `read(50ms)` + `LockSupport.parkNanos` 有界等待。
- **风险**: 多个空闲 source 任务时 CPU 被无谓占满，挤占同机其他 stream task（TaskExecutor 固定线程池大小 = CPU 数，忙等会拖慢真正干活的任务）。
- **建议**: 空转分支改为 `LockSupport.parkNanos(几百微秒~1ms)` 或有界 poll，与 InputGate 的 idle 处理对齐。
- **误报排除**: run() 是 source 任务线程主循环（StreamTaskInvokable.invokeSource 调 readerOp.run()），空闲路径真实可达（无界 source 无数据时）。

### [P2] InMemoryMaterializationPoint 无淘汰的无界内存增长

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/materialization/InMemoryMaterializationPoint.java:61-72`
- **维度**: D2（资源）、D6
- **证据**:
```java
@Override
public synchronized void write(StreamElement element, long epoch) throws InterruptedException {
    ...
    store.add(new MaterializedElement(element, epoch));   // 只增不减
    if (epoch > lastEpoch) { lastEpoch = epoch; }
}
```
- **现状**: materialization 边界上每条数据 dual-write 进 `ArrayList`，成功 checkpoint 后旧 epoch 数据永不清理（无 truncate/evict API），`replay(fromEpoch)` 也只是过滤读取。类 javadoc 自认 "in-memory list grows unboundedly; global memory pressure is expected to be controlled upstream"，但 IBufferPool 只约束主队列，不约束此旁路 store。
- **风险**: 长期运行 + 高吞吐的作业，materialization-enabled 边界内存随数据量线性增长直至 OOM。
- **建议**: 增加按 durable epoch 的截断接口（checkpoint 完成后由协调者调用），或淘汰策略；至少暴露 size 告警。
- **误报排除**: 全文检索确认无任何调用清理 store 的路径；write 由 ResultPartition.write 对每条 record 调用（materialization point 非空时）。

### [P2] StreamSourceOperator 跨 subtask 共享 sourceFunction：并行 > 1 时 ReplayableSourceFunction 恢复互相覆盖 seek

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamSourceOperator.java:184-187、:318-332`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/jobgraph/OperatorChain.java:244-248`
- **维度**: D1（恢复正确性）、D3
- **证据**:
```java
@Override
public StreamSourceOperator<OUT> copyForSubtask() {
    return new StreamSourceOperator<>(sourceFunction);   // 函数对象共享
}
...
if (sourceFunction instanceof ReplayableSourceFunction && snapshotResult != null) {
    Object offsetObj = snapshotResult.getOperatorState(SOURCE_OFFSET_KEY);
    ...
    ((ReplayableSourceFunction<?>) sourceFunction).seek(offset);   // 对共享对象寻址
}
```
- **现状**: `OperatorChain.deepCopy()` 逐 subtask 调 copyForSubtask()，但 sourceFunction 引用共享（javadoc 说明是为保留外部捕获引用的有意契约）。恢复时每个 subtask 各自 `seek(自己的 offset)`，全部落在同一函数实例上，后写覆盖先写；随后 N 个 subtask 的 run() 并发调用同一实例（还隐含要求用户函数线程安全）。
- **风险**: 并行度 > 1 的 replayable source（SOURCE_OFFSET_KEY 机制）在恢复后所有 subtask 从"最后一个 seek 的 offset"读取 → 重复消费或漏读。
- **建议**: copyForSubtask 对 ReplayableSourceFunction/CheckpointedSourceFunction 不共享（深拷贝函数），或在文档/类型层面强制并行 replayable source 自行管理 per-subtask 状态。
- **误报排除**: 共享是文档化契约（可能主要服务于 SELF_CONTAINED 并行度 1 的本地路径），故不定 P1；但 seek 覆盖路径经代码逐行核实存在，条件（并行 > 1 + 恢复 + ReplayableSourceFunction）具体明确。

### [P2] ProcessOperator.onTimer 发射的记录携带陈旧时间戳（未设置 collector 时间戳）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/ProcessOperator.java:76-87`
- **维度**: D1、D8（Flink 语义漂移）
- **证据**:
```java
@Override
public void onEventTime(InternalTimer<Object, VoidNamespace> timer) throws Exception {
    setCurrentKey(timer.getKey());
    onTimerContext.timerTimestamp = timer.getTimestamp();
    userFunction.onTimer(timer.getTimestamp(), onTimerContext, collector);   // collector 未 setTimestamp
}
```
- **现状**: `collector`（TimestampedCollector，复用单条 StreamRecord）只在 processElement 里 `setTimestamp(element)`；onTimer 回调发射的输出沿用上一条元素的旧时间戳（或无时间戳）。Flink 的 ProcessOperator.onEventTime 会 `collector.setAbsoluteTimestamp(timer.getTimestamp())`。
- **风险**: onTimer 产物进入下游事件时间算子（窗口/watermark 跟踪）时用错时间戳：可能被判定为迟到数据丢弃，或污染下游窗口归属。
- **建议**: onEventTime/onProcessingTime 回调前 `collector.setAbsoluteTimestamp(timer.getTimestamp())`。
- **误报排除**: TimestampedCollector 已提供 setAbsoluteTimestamp 但本算子未使用；onEventTime/onProcessingTime 两个回调均无设置，确认非笔误单点。

### [P3] TimestampsAndWatermarksOperator: 死代码空分支 + 每记录分配 Output 包装 + 直接使用系统时钟

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/TimestampsAndWatermarksOperator.java:107-127、:103`
- **维度**: D6、维护性
- **证据**:
```java
if (shouldEmit) {
    if (now == lastEmitTime && watermarkInterval == 0) {
    } else if (now == lastEmitTime && watermarkInterval > 0) {   // 两个空分支，死代码
    }
    watermarkGenerator.onPeriodicEmit(new OperatorWatermarkOutput());  // 每条记录 new
    ...
}
...
watermarkGenerator.onEvent(element.getValue(), extractedTs, new OperatorWatermarkOutput());
```
- **现状**: (1) :118-120 两个 if 分支体为空，疑似重构残留；(2) 每条记录 `new OperatorWatermarkOutput()`（Flink 复用单一 emitter 实例），热路径无谓分配；(3) processElement 用 `System.currentTimeMillis()` 而非注入的 processingTimeService，且在元素路径里做周期发射，与定时器路径（onProcessingTimeCallback）职责重叠。
- **风险**: 低；纯性能/维护性。
- **建议**: 删除空分支；复用单个 OperatorWatermarkOutput 实例；时间统一走 processingTimeService。
- **误报排除**: 行为已核对——空分支无副作用；分配在每条记录路径（processElement 每元素最多 2 次）。

### [P3] BasicTypeInfo.INSTANCES 静态 HashMap 非同步懒注册 + 无界增长

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/typeinfo/BasicTypeInfo.java:18、:44-51`
- **维度**: D3
- **证据**:
```java
private static final Map<Class<?>, BasicTypeInfo<?>> INSTANCES = new HashMap<>();
...
public static <T> BasicTypeInfo<T> of(Class<T> typeClass) {
    BasicTypeInfo<T> info = (BasicTypeInfo<T>) INSTANCES.get(typeClass);
    if (info != null) return info;
    info = new BasicTypeInfo<>(typeClass);
    INSTANCES.put(typeClass, info);      // 无锁 put
    return info;
}
```
- **现状**: 可变 static HashMap，`of()` 从任意线程无同步写入；并发下理论上可致 HashMap 结构损坏/丢条目；对每个新 Class 永久驻留（弱引用缓存才合理）。
- **风险**: 低——典型调用发生在单线程拓扑构建期；但该类是公共 API，无法约束调用方线程。
- **建议**: 换 `ConcurrentHashMap.computeIfAbsent`。
- **误报排除**: 已确认模块内无其他线程调用点；按实际可达性定 P3 而非 D3 高危。

### [P3] 三种 invoke 角色的 finish() 与 MAX_WATERMARK 顺序不一致

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/StreamTaskInvokable.java:644-646+656-667（invokeSource）、:689-693（invokeMiddle）、:752-756（invokeSelfContained）`
- **维度**: D8（契约一致性）、维护性
- **证据**:
```java
// invokeSource: finish() 先于 MAX_WATERMARK
if (sourceError == null) { operatorChain.finish(); }
... finally { ... ((StreamSourceOperator<?>) head).processWatermark(Watermark.MAX_WATERMARK); closeOutputWriters(); }

// invokeMiddle: MAX_WATERMARK 先于 finish()
if (inputError == null) { headInput.processWatermark(Watermark.MAX_WATERMARK); operatorChain.finish(); }

// invokeSelfContained: finish() 先于 MAX_WATERMARK
if (sourceError == null) { operatorChain.finish(); sourceOp.processWatermark(Watermark.MAX_WATERMARK); }
```
- **现状**: SOURCE/SELF_CONTAINED 先 finish() 再发 MAX_WATERMARK；MIDDLE/SINK 相反。对同一算子（如 TwoPhaseCommitSinkFunction：finish 里 flush、MAX_WATERMARK 触发窗口/定时器）两种顺序语义不同——先 finish 后 watermark 意味着"终态水位触发的输出"发生在已 finish 的算子上。
- **风险**: 同一作业图里不同角色行为不一致；有状态 sink/窗口算子在 SOURCE 路径可能漏掉 MAX_WATERMARK 应触发的最后一批输出（或对已 finish 算子发数据）。
- **建议**: 统一为 MIDDLE 的顺序（先 MAX_WATERMARK 传播完毕，再 finish），与 Flink 的 close 流程一致。
- **误报排除**: 三段代码逐行比对确认顺序差异真实存在；注释（P1-5）只解释 finish 需在 close 前，未解释角色间差异。

## 补充观察（不计入统计）

- **D5 总体评价（正面）**: checkpoint/跨 JVM 反序列化的类加载有 `ClassNameValidator` 白名单（仅 io.nop.*/JDK 前缀，accumulator 更严）把关（MemoryStateSerDe、StreamElementCodec、ContainerValueCodec 均调用），未见任意类加载反序列化风险。残余小项：`Class.forName` 仍会执行允许清单内类的静态初始化，以及白名单会拒绝用户自有包（com.mycompany.*）的值类型恢复——属设计取舍而非漏洞。
- **D7**: 无 bare RuntimeException（grep 0 命中）；异常体系普遍为 `StreamException + NopStreamErrors 错误码 + .param(...)`，符合平台两档策略。零散 69 处 JDK 异常（IllegalArgumentException/IllegalStateException/UnsupportedOperationException，均为英文消息，多在参数校验与协调器内部），属模块内部策略允许范围，未单列。本模块无 beans.xml/@Inject 使用，无 IoC 违规可查。
- 已核对无问题的部分：TimeWindow/Tumbling/Sliding/Session assigner、EventTimeTrigger/CountTrigger 与 Flink 语义一致（含 [start,end) 与 maxTimestamp=end-1）；WatermarkOutputMultiplexer/CombinedWatermarkStatus/IndexedCombinedWatermarkStatus 为忠实移植；KeyGroupAssignment 分配/逆分配数学正确（含负数与 mask 处理）；InputGate 多 epoch barrier 对齐、abort 竞态、P1-05 补发队列逻辑经审查未见错误；TaskMailbox 优先级队列与关闭竞态处理正确；CheckpointBarrierTracker 多 epoch ACK 路由正确（abortCallback 为 null 的 back-compat 构造器在快照失败时只 LOG 且不完成快照，生产路径已注入回调）。
