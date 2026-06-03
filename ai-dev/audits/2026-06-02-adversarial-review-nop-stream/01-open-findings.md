# 对抗性审查 — nop-stream (2026-06-02)

## 基本信息

- **审核模块**: nop-stream（流处理引擎）
- **审核日期**: 2026-06-02
- **审查类型**: 开放式对抗性审查
- **审查范围**: 全模块，重点为 state backend、window operator、CEP engine、graph generation、execution engine
- **去重范围**: 16 轮对抗性审查 + 5 轮深度审计（截至 2026-05-31 R16 + 2026-06-02 深度审计）
- **切入视角**: 异常路径侦探 + 代码生成受害者 + 未来破坏者

## 先前已知但未修复的关键问题

以下 13 个 P0/P1 级别问题在本次审查中确认为**仍未修复**（仅 HeapInternalTimerService timer deletion 和 Lockable double release 已修复）：

| 编号 | 问题 | 原报告 |
|------|------|--------|
| K-1 | TwoPhaseCommitSinkFunction.saveState() ConcurrentModificationException | R16 AR-1 |
| K-2 | CheckpointBarrierTracker.triggerCheckpoint() 永久死锁 | R15 AR-1 |
| K-3 | forceNonParallel() 静默空操作 | 深度审计 07-03 |
| K-4 | AbstractStreamOperator.processBarrier 静默吞异常 | 深度审计 09-01 |
| K-5 | WindowedStreamImpl.allowedLateness() setter 无效 | R16 AR-2 |
| K-6 | WindowAggregationOperator.merge 双重计数元素 | R16 AR-4 |
| K-7 | CheckpointIDCounter 恢复后不更新 | R16 AR-5 |
| K-8 | CepOperator.onEventTime() 清空有效状态 | R16 AR-6 |
| K-9 | DeweyNumber.increase() int 溢出 | R16 AR-7 |
| K-10 | JdbcClusterRegistry.registerNode() 节点不可见 | R16 AR-9 |
| K-11 | CheckpointCoordinator.checkpointSuccessMap 无限增长 | R16 AR-10 |
| K-12 | ChainingOutput 静默丢弃 side-output | R15 AR-4 |
| K-13 | WindowAggregationOperator 从不调用 trigger.onMerge() | R15 AR-3 |

以下仅报告**新发现**。

---

## 新发现

### [AR-1] MemoryInternalAppendingState 共享单例 Accumulator 导致跨 Key 状态损坏

- **文件**: `nop-stream-core/.../state/backend/memory/MemoryInternalAppendingState.java:28,82-97`
- **证据片段**:
  ```java
  private transient SimpleAccumulator<IN> accumulator;  // 单一共享实例

  public void add(IN value) throws IOException {
      TypedNamespaceAndKey key = getStorageKey();
      ACC current = storage.get(key);
      accumulator.resetLocal();          // 清除共享实例内部状态
      if (current != null) {
          accumulator.add((IN) current); // 重新加入当前值
      }
      accumulator.add(value);
      storage.put(key, (ACC) accumulator.getLocalValue()); // 存入共享引用
  }
  ```
- **严重程度**: P0
- **现状**: `accumulator` 是所有 (namespace, key) 组合共享的单例。对于 `ListAccumulator`、`Histogram` 等可变累加器，`getLocalValue()` 返回内部可变对象（如 `ArrayList`），导致：
  1. **引用别名**：`storage.put()` 存入的是累加器内部可变对象的直接引用。当 `add()` 被不同 key 调用时，`resetLocal()` 清空了之前 key 存储的同一个对象。
  2. **数据丢失**：对 key A 调用 `add(10)` 存储 `[10]`，然后对 key B 调用 `add(20)`，此时 key A 的存储也被改为 `[20]`（因为是同一个 ArrayList 对象）。
- **风险**: 所有使用 `InternalAppendingState` 的窗口算子在可变累加器场景下静默丢失数据。`ListAccumulator`（窗口元素收集）和 `Histogram` 直接受影响。
- **建议**: 每次 `add()` 创建新累加器实例，或对 `getLocalValue()` 返回值做深拷贝后再存入 storage。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-2] StateSnapshot + MemoryStateSerDe 快照存储直接引用导致异步检查点损坏

- **文件**: `nop-stream-core/.../state/backend/StateSnapshot.java:25-27` + `MemoryStateSerDe.java:423-441`
- **证据片段**:
  ```java
  // StateSnapshot.java
  public Map<String, Object> getStateData() {
      return stateData;  // 返回可变引用
  }

  // MemoryStateSerDe.java snapshotValueState
  for (Map.Entry<TypedNamespaceAndKey, ?> e : state.storage.entrySet()) {
      entry.put("value", e.getValue());  // 直接引用，无深拷贝
  }
  ```
- **严重程度**: P1
- **现状**: 快照过程从活跃 state storage 中取出值后直接放入快照，不做深拷贝。`ListState` 的快照做了 `new ArrayList<>(e.getValue())` 浅拷贝，但 `ValueState`、`AppendingState`、`AggregatingState`、`InternalAggregatingState` 均存储原始引用。如果检查点是异步的（先快照后继续处理），后续状态变更会静默修改已保存的快照。
- **风险**: 对于可变状态值（Map、自定义 POJO、累加器对象），异步检查点场景下恢复时读到的是被后续处理修改过的数据，而非快照时刻的数据。检查点语义被破坏。
- **建议**: 所有快照方法对可变值做深拷贝，或将 `getStateData()` 改为 `Collections.unmodifiableMap()`。
- **信心水平**: 很可能（取决于异步检查点是否为当前使用模式）
- **发现来源视角**: 异常路径侦探

---

### [AR-3] WindowOperator Evictor 路径对所有元素使用 Long.MIN_VALUE 时间戳

- **文件**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java:685-708`
- **证据片段**:
  ```java
  if (evictor != null) {
      Iterable<IN> elements = (Iterable<IN>) contents;
      List<TimestampedValue<IN>> wrapped = new ArrayList<>();
      for (IN element : elements) {
          wrapped.add(new TimestampedValue<>(element, Long.MIN_VALUE));
      }
      evictor.evictBefore(wrapped, wrapped.size(), window, evictorContext);
  ```
- **严重程度**: P0
- **现状**: 当 evictor 非空时，所有元素被包装为 `TimestampedValue(element, Long.MIN_VALUE)`。`TimeEvictor` 依赖 `getTimestamp()` 判断是否过期——所有元素时间戳相同意味着 `TimeEvictor` 要么驱逐全部元素，要么不驱逐，完全失去基于时间的驱逐能力。
- **风险**: `TimeEvictor` 在生产环境中完全失效。使用 `TimeEvictor` 的窗口算子产生错误结果或空结果。
- **建议**: 在元素入窗时记录其时间戳（`StreamRecord.getTimestamp()`），在 evictor 路径使用真实时间戳。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-4] InputGate.checkBarrierAlignmentComplete() 丢弃已对齐的 Barrier

- **文件**: `nop-stream-core/.../execution/InputGate.java:345-349`
- **证据片段**:
  ```java
  private void checkBarrierAlignmentComplete() {
      if (barriersRemaining <= 0 && pendingBarrier != null) {
          resetBarrierState();  // 清除状态，但 barrier 对象丢失
      }
  }
  ```
- **严重程度**: P1
- **现状**: 当最后一个 channel 通过 `isFinished()` 检测确认 barrier 到达时，`checkBarrierAlignmentComplete()` 重置 barrier 状态但不向下游转发 barrier。正常 channel 的 barrier 通过 `handleBarrierNonRecursive()` 转发，但 finished channel 的自动确认路径绕过了这个逻辑。导致检查点 barrier 被静默丢弃。
- **风险**: 上游 task 在 barrier 对齐期间完成（最后 barrier 来自 finished channel 检测）时，该检查点永远不会被下游看到。检查点协调失败但无错误信号。
- **建议**: 在 `checkBarrierAlignmentComplete()` 中调用 barrier 转发逻辑，或在 finished channel 路径中返回 barrier 给调用者。
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

---

### [AR-5] JobGraphGenerator.createJobVertex 对每个节点调用 createOperatorFromFactory 两次

- **文件**: `nop-stream-core/.../jobgraph/JobGraphGenerator.java:352-384`
- **证据片段**:
  ```java
  // createJobVertex 中第一次调用
  for (StreamNode node : chain) {
      StreamOperator<?> operator = createOperatorFromFactory(node);  // 第 1 次
      if (operator != null) operators.add(operator);
  }

  // filterKeySelectorsForOperators 中第二次调用
  private List<KeySelector<?,?>> filterKeySelectorsForOperators(
          List<StreamNode> chain, List<StreamOperator<?>> operators) {
      // operators 参数被传入但从未使用
      for (StreamNode node : chain) {
          StreamOperator<?> operator = createOperatorFromFactory(node);  // 第 2 次，结果丢弃
          if (operator != null) result.add(node.getKeySelector());
      }
  }
  ```
- **严重程度**: P1
- **现状**: `filterKeySelectorsForOperators` 接受 `operators` 参数但完全忽略它，重新调用 `createOperatorFromFactory` 来判断哪些节点有 operator。如果 factory 有副作用（打开连接、分配资源），会产生资源泄漏。同时 `opIndex` 变量递增但从未被读取（死代码）。
- **建议**: 直接使用已有的 `operators` 列表和 `chain` 的对应关系过滤 key selector，不再重复调用 factory。
- **信心水平**: 确定
- **发现来源视角**: 代码生成受害者

---

### [AR-6] MemoryKeyedStateBackend.routeKey() 中 Math.abs(Integer.MIN_VALUE) 返回负数

- **文件**: `nop-stream-core/.../state/backend/memory/MemoryKeyedStateBackend.java:237-243`
- **证据片段**:
  ```java
  Object routeKey(Object key) {
      if (shardCount <= 1) return key;
      int shardId = Math.abs(StateShard.stableHash(key)) % shardCount;
      return new ShardPrefixedKey(shardId, key);
  }
  ```
- **严重程度**: P1
- **现状**: `Math.abs(Integer.MIN_VALUE)` 返回 `Integer.MIN_VALUE`（仍为负），导致 `shardId` 为负数。`ShardPrefixedKey` 的 `equals()` 比较包含 shardId，负数 shardId 的 key 永远无法被正常路由找到。对应 key 的状态变为孤立。
- **风险**: 约 1/2^32 概率的 key 丢失状态。大规模部署下必然出现。检查点恢复后因 hash 一致性，相同的 key 始终丢失。
- **建议**: 使用 `(hash & 0x7FFFFFFF) % shardCount` 或 `Math.floorMod(hash, shardCount)`。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-7] SimpleKeyedStateStore 完全忽略 Key 分区

- **文件**: `nop-stream-core/.../state/simple/SimpleKeyedStateStore.java:37-58`
- **证据片段**:
  ```java
  public <T> ValueState<T> getState(ValueStateDescriptor<T> stateProperties) {
      return new ValueState<T>() {
          private T value;  // 单一值，无 key 分区
          public T value() { return value; }
          public void update(T value) { this.value = value; }
      };
  }
  ```
- **严重程度**: P1
- **现状**: 每次调用 `getState()` 都创建一个全新的匿名实例，无共享存储。不同 key 的状态写入互相不可见。同一个 key 的多次 `getState()` 调用也返回独立实例。
- **风险**: 如果作为 fallback state store 使用，所有 keyed stream 的状态语义被破坏。这可能不是默认路径，但作为 public API 存在，任何误用都会导致数据损坏。
- **建议**: 要么移除这个类并替换为抛出 `UnsupportedOperationException`，要么正确实现 key 分区。
- **信心水平**: 确定
- **发现来源视角**: 新人开发者

---

### [AR-8] WindowOperator.windowNamespace() 对非 TimeWindow 使用 identityHashCode

- **文件**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java:1138`
- **证据片段**:
  ```java
  private String windowNamespace(W window) {
      if (window instanceof TimeWindow) {
          return "TW:" + tw.getStart() + "," + tw.getEnd();
      }
      return window.getClass().getName() + "@" + System.identityHashCode(window);
  }
  ```
- **严重程度**: P1
- **现状**: `System.identityHashCode()` 在序列化/反序列化后会改变。对于 `GlobalWindow`（单例），同一 JVM 内可能一致，但跨 JVM 恢复（分布式检查点）后，namespace 字符串不同，导致状态无法找回。
- **风险**: 使用 `GlobalWindows` 或自定义窗口类型的场景下，检查点恢复后窗口状态丢失。
- **建议**: 使用 `window.toString()` 或要求 `Window` 子类实现稳定的 namespace key 方法。
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

---

### [AR-9] CepOperator 仅为全局 windowTime 注册定时器，per-state windowTimes 从不触发

- **文件**: `nop-stream-cep/.../operator/CepOperator.java:455-457`
- **证据片段**:
  ```java
  if (nfa.getWindowTime() > 0 && nfaState.isNewStartPartialMatch()) {
      registerTimer(timestamp + nfa.getWindowTime());
  }
  ```
- **严重程度**: P1
- **现状**: `NFA` 有全局 `windowTime` 和 per-state `windowTimes`（`Map<String, Long>`）。`advanceTime()` 中检查 per-state 超时，但 `processEvent()` 仅注册全局 `windowTime` 的定时器。如果没有新事件到达触发 `advanceTime`，per-state 超时永远不会被检测。
- **风险**: 使用 `within(Time, WithinType.PREVIOUS_AND_CURRENT)` 的模式在无事件期间永远不会超时。SharedBuffer 中的 partial match 条目永不释放，造成内存泄漏。超时 match 永远不会输出。
- **建议**: 在 `processEvent()` 中遍历 `nfa.getWindowTimes()` 并为每个 per-state windowTime 注册定时器。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-10] PartitionedPlanGenerator.inferPartitionPolicy 用类名子串匹配分区策略

- **文件**: `nop-stream-core/.../graph/PartitionedPlanGenerator.java:59-72`
- **证据片段**:
  ```java
  String partitionerName = edge.getPartitioner().getClass().getSimpleName().toLowerCase();
  if (partitionerName.contains("hash")) return PartitionPolicy.HASH;
  else if (partitionerName.contains("rebalance")) return PartitionPolicy.REBALANCE;
  else if (partitionerName.contains("broadcast")) return PartitionPolicy.BROADCAST;
  return PartitionPolicy.FORWARD;  // 未知分区器默认 FORWARD
  ```
- **严重程度**: P1
- **现状**: `KeySelectorPartitioner`（`DataStreamImpl` 内部类）的简单名不包含 "hash"，因此 `keyBy()` 产生的分区被推断为 `FORWARD`（1:1 映射）而非 `HASH`。任何自定义分区器（名称不含 hash/rebalance/broadcast）也被降级为 FORWARD。
- **风险**: `keyBy()` 后的数据不按 key 分发，而是按 subtask index 直接转发。Keyed state 在错误的 subtask 上读写。数据丢失和计算错误。
- **建议**: 使用类型检查（`instanceof`）而非字符串匹配，或让 Partitioner 实现一个 `getPartitionPolicy()` 方法。
- **信心水平**: 很可能（取决于 KeySelectorPartitioner 是否为 keyBy 的实际路径）
- **发现来源视角**: 未来破坏者

---

### [AR-11] CheckpointCoordinator registerTask/unregisterTask TOCTOU 竞态

- **文件**: `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:354-371`
- **证据片段**:
  ```java
  public void registerTask(TaskLocation taskLocation) {
      Set<TaskLocation> current = this.tasksToAcknowledge;  // 读
      if (!current.contains(taskLocation)) {                 // 检查
          Set<TaskLocation> newSet = ConcurrentHashMap.newKeySet();
          newSet.addAll(current);
          newSet.add(taskLocation);
          this.tasksToAcknowledge = newSet;                  // 写
      }
  }
  ```
- **严重程度**: P1
- **现状**: 非原子的读-检查-写模式。两个并发 `registerTask` 调用可能都读到同一 `current`，各自创建新集合并写入，导致其中一个丢失。`unregisterTask` 存在相同问题。虽然 `tasksToAcknowledge` 是 volatile，但 volatile 只保证可见性，不保证复合操作的原子性。
- **风险**: 并行 task 启动场景下（分布式部署常见），task 可能被遗漏出 ack 集合，导致检查点永远无法完成或在不完整状态下完成。
- **建议**: 使用 `ConcurrentHashMap.newKeySet()` 并直接操作（`add`/`remove`），而非 copy-on-write 模式。或使用 `synchronized` 保护复合操作。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-12] SharedBuffer.registerEvent() int 溢出导致 EventId 重复

- **文件**: `nop-stream-cep/.../nfa/sharedbuffer/SharedBuffer.java:184-198`
- **证据片段**:
  ```java
  EventId registerEvent(V value, long timestamp) {
      Integer id = eventsCount.get(timestamp);
      if (id == null) id = 0;
      EventId eventId = new EventId(id, timestamp);
      eventsCount.put(timestamp, id + 1);  // int 溢出无检查
      eventsBufferCache.put(eventId, lockableValue);
      eventsBuffer.put(eventId, lockableValue);
  }
  ```
- **严重程度**: P2
- **现状**: 同一 timestamp 超过 2^31 个事件时，`id + 1` 静默溢出为 `Integer.MIN_VALUE`，产生重复 EventId。`eventsBuffer` 和 `eventsBufferCache` 中的旧条目被覆盖。
- **风险**: 高吞吐量场景下 CEP 引擎静默丢失事件。实践中极低概率，但无错误信号。
- **建议**: 使用 `AtomicInteger` 或 `long` 代替 `Integer` 计数器，或在溢出时抛异常。
- **信心水平**: 很可能（理论正确性风险）
- **发现来源视角**: 10x 规模运维者

---

### [AR-13] SharedBuffer 缓存先于状态写入，无回滚机制

- **文件**: `nop-stream-cep/.../nfa/sharedbuffer/SharedBuffer.java:192-198`
- **证据片段**:
  ```java
  eventsBufferCache.put(eventId, lockableValue);  // 先写缓存
  try {
      eventsBuffer.put(eventId, lockableValue);    // 再写状态
  } catch (Exception e) {
      throw new StreamException(...);               // 缓存未回滚
  }
  ```
- **严重程度**: P2
- **现状**: 如果 `eventsBuffer.put()` 抛出异常，缓存中已存在该条目但状态后端中没有。后续 `getEvent()` 从缓存返回该事件，但检查点恢复后事件丢失。相同模式存在于 `upsertEvent`/`upsertEntry`。
- **风险**: 状态后端 I/O 错误时产生缓存-状态不一致。恢复后数据丢失。
- **建议**: 在 catch 块中调用 `eventsBufferCache.remove(eventId)` 回滚缓存。
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

---

### [AR-14] RebalancePartitionRouter 计数器溢出导致负分区索引

- **文件**: `nop-stream-core/.../execution/RebalancePartitionRouter.java:30-31`
- **证据片段**:
  ```java
  public int selectChannel(StreamRecord<?> record) {
      return Math.abs(roundRobinCounter.getAndIncrement() % numPartitions);
  }
  ```
- **严重程度**: P2
- **现状**: `AtomicInteger` 溢出为 `Integer.MIN_VALUE` 时，`Math.abs(Integer.MIN_VALUE)` 返回负数，`% numPartitions` 产生负索引。
- **风险**: 长时间运行的流作业（~21 亿次记录后）触发 `ArrayIndexOutOfBoundsException`，导致 task 崩溃。
- **建议**: 使用 `(counter.getAndIncrement() & 0x7FFFFFFF) % numPartitions`。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者

---

### [AR-15] TaskExecutor.submitTask(SubtaskTask) 不跟踪 submittedTasks

- **文件**: `nop-stream-core/.../execution/TaskExecutor.java:203-217`
- **证据片段**:
  ```java
  public void submitTask(SubtaskTask subtaskTask) {
      String taskId = subtaskTask.getTaskName();
      Future<?> future = executorService.submit(subtaskTask);
      taskFutures.put(taskId, future);
      // 注意：没有 submittedTasks.put(taskId, subtaskTask)
  }
  ```
- **严重程度**: P2
- **现状**: `Task` 重载版本同时写入 `submittedTasks` 和 `taskFutures`，但 `SubtaskTask` 重载只写入 `taskFutures`。导致 `getAllTasks()`、`getTaskCount()`、`getRunningTaskCount()` 等 API 返回不正确的结果。`GraphModelCheckpointExecutor` 的实际执行路径使用 `SubtaskTask`，因此所有 task 查询 API 在生产路径上失效。
- **建议**: 在 `SubtaskTask` 重载中加入 `submittedTasks.put(taskId, subtaskTask)`。
- **信心水平**: 确定
- **发现来源视角**: 新人开发者

---

## 总评

nop-stream 作为一个流计算引擎，**架构方向合理**，但在实现层面存在大量**静默数据损坏**风险。本轮审查最值得关注的 3 个方向：

1. **State Backend 可变性安全**（AR-1、AR-2）：`MemoryInternalAppendingState` 的共享累加器别名问题是最危险的新发现——它在可变累加器（`ListAccumulator`、`Histogram`）场景下对每个窗口操作产生数据损坏。这不是边界条件，而是正常使用路径。StateSnapshot 的引用别名问题使异步检查点机制失去意义。

2. **Window Operator 语义完整性**（AR-3）：evictor 路径的 `Long.MIN_VALUE` 时间戳是一个实现性 bug，使 `TimeEvictor` 完全失效。这是一个容易被测试遗漏的问题（因为 `CountEvictor` 不受影响）。

3. **已报告问题的持续积累**：16 轮对抗性审查报告了 2 个 P0 + 12 个 P1 问题，**仅 2 个已修复**。问题积累速度远超修复速度，表明该模块需要系统性修复投入而非继续发现新问题。

## 本次审查盲区自评

1. **分布式集成测试**：所有并发问题仅通过代码审查发现，未在真实分布式环境中验证
2. **性能与吞吐量**：未评估高负载下的性能表现和背压行为
3. **Operator Chain 完整生命周期**：仅审查了 open/close 的基础路径，未深入测试 failover 场景
4. **Connector 实现**：`nop-stream-connector` 的 15 个文件仅做了浅层审查
5. **Flink 兼容层**：`nop-stream-flink` 为空占位模块，无法审查
6. **fraud-example 端到端正确性**：示例模块未运行验证

## 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 2    | 状态后端可变性损坏(AR-1), 窗口 evictor 语义失效(AR-3) |
| P1      | 9    | 检查点完整性(AR-2,AR-4), 图生成(AR-5,AR-10), 状态路由(AR-6,AR-7), CEP 定时器(AR-9), 窗口命名空间(AR-8), 并发安全(AR-11) |
| P2      | 4    | CEP 溢出(AR-12), 缓存一致性(AR-13), 分区溢出(AR-14), 任务跟踪(AR-15) |
| P3      | 0    | — |

<ADVERSARIAL_RESULT>issues</ADVERSARIAL_RESULT>
