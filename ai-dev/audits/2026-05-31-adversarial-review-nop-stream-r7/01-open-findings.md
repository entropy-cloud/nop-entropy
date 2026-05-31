# nop-stream 对抗性审查 — Round 7（第7轮全模块审查）

> 审查日期：2026-05-31
> 审查范围：nop-stream 全模块（5 个活跃子模块），聚焦代码变更验证 + 未覆盖区域深挖
> 审查方法：开放式发现导向，4 个并行探索 agent 分别聚焦 core/operators/state、runtime/checkpoint/cluster、CEP/NFA/SharedBuffer、connector/API
> 去重：已阅读以下已有报告，本报告不重复其中内容：
> - `ai-dev/audits/2026-05-31-adversarial-review-nop-stream/`（Round 5，AR-1~AR-16）
> - `ai-dev/audits/2026-05-31-adversarial-review-nop-stream-r6/`（Round 6，AR-17~AR-35）
> - `ai-dev/audits/2026-05-30-adversarial-review-nop-stream-r13/`（Round 13，AR-1~AR-17）
> - `ai-dev/audits/2026-05-30-deep-audit-nop-stream-full/`（全维度深度审核）
> 发现来源视角：异常路径侦探 + 10x 规模运维者 + 新人开发者

---

## 代码变更验证

### 已修复

| 旧编号 | 问题 | 状态 |
|--------|------|------|
| R5-AR-6 | CepPatternBuilder 非起始 pattern where 条件未应用 | ✅ 已修复（`buildWhere` 现在对每个非起始 pattern 调用） |
| R5-AR-4 | WindowOperator.windowNamespace 使用 identityHashCode | ✅ 已修复（改为 `toString()`，但见 R13-AR-8 关于脆弱性） |

### 关键 P0 仍未修复

| 旧编号 | 问题 | 状态 |
|--------|------|------|
| R5-AR-1 | Lockable.release() refCounter<=0 返回 true | ⚠️ 未修复（CAS 改进，但语义 bug 保留） |
| R5-AR-2 | JdbcClusterRegistry.registerCoordinator 无 fencing token 校验 | ⚠️ 未修复 |
| R6-AR-17 | CheckpointBarrierTracker.operatorsToAck 不重置 | ⚠️ 未修复 |
| R6-AR-18 | AbstractStreamOperator 吞掉 snapshot 异常 | ⚠️ 未修复 |
| R6-AR-19 | CheckpointCoordinator.restoreFromCheckpoint counter 不推进 | ⚠️ 未修复 |
| R6-AR-20 | InputGate 不检查 barrier ID | ⚠️ 未修复 |
| R6-AR-21 | CepOperator timer 不被 checkpoint 持久化 | ⚠️ 未修复 |

---

## 新发现

### [AR-36] SharedBuffer.flushCache() 在写入 backing state 之前清除 cache — 状态写入失败导致数据静默丢失

- **文件**: `nop-stream-cep/.../nfa/sharedbuffer/SharedBuffer.java:303-321`
- **证据片段**:
  ```java
  void flushCache() {
      if (!entryCache.isEmpty()) {
          Map<NodeId, Lockable<SharedBufferNode>> snapshot1 = new java.util.HashMap<>(entryCache);
          entryCache.clear();       // ← cache 先清空
          try {
              entries.putAll(snapshot1);  // ← state 写入后执行
          } catch (Exception e) {
              throw NopException.adapt(e);  // snapshot1 丢失，cache 已空
          }
      }
      if (!eventsBufferCache.isEmpty()) {
          Map<EventId, Lockable<V>> snapshot2 = new java.util.HashMap<>(eventsBufferCache);
          eventsBufferCache.clear();  // ← 同样先清空
          try {
              eventsBuffer.putAll(snapshot2);
          } catch (Exception e) {
              throw NopException.adapt(e);
          }
      }
  }
  ```
- **严重程度**: P1
- **现状**: `flushCache()` 先复制 cache 到局部变量，然后**立即清空 cache**，最后才写入 backing state。如果 `entries.putAll()` 或 `eventsBuffer.putAll()` 抛出异常（I/O 错误、序列化失败、OOM），局部变量随异常栈帧丢失，cache 已清空。数据永远无法恢复。
- **风险**: 状态后端写入失败时，SharedBuffer 中的 CEP 事件数据和节点关系静默丢失。后续 `materializeMatch` 将找不到已注册的事件，导致 NPE 或错误的匹配结果。
- **建议**: 将 `clear()` 移到 `putAll()` 成功之后：`entries.putAll(snapshot1); entryCache.clear();`。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-37] NFAState.STATE_COMPARATOR 使用 DeweyNumber.hashCode() 而非值比较 — 哈希碰撞导致 NFA 状态相等性判断错误

- **文件**: `nop-stream-cep/.../nfa/NFAState.java:115-119`
- **证据片段**:
  ```java
  private static final Comparator<ComputationState> STATE_COMPARATOR =
      Comparator.<ComputationState, String>comparing(ComputationState::getCurrentStateName)
          .thenComparing(c -> c.getVersion() == null ? 0 : c.getVersion().hashCode())
          .thenComparingLong(ComputationState::getStartTimestamp)
          .thenComparingLong(ComputationState::getPreviousTimestamp);
  ```
- **严重程度**: P1
- **现状**: `STATE_COMPARATOR` 使用 `DeweyNumber.hashCode()`（内部为 `Arrays.hashCode(int[])`）作为排序键。两个不同的 `DeweyNumber` 可能产生相同的 `hashCode`（32 位哈希函数的固有碰撞率），此时 comparator 返回 0，导致 `ComputationState` 被视为"相等"。此 comparator 用于 `sortedCopy()`，进而被 `NFAState.equals()` 和 `NFAState.hashCode()` 使用。
- **风险**: `NFAState` 的相等性判断不可靠——两个包含不同 DeweyNumber 版本的 NFAState 可能被判定为相等。影响 checkpoint 状态比较、去重逻辑等依赖 equals 的场景。在极端情况下（大量 partial match 导致版本号多样化），碰撞概率上升。
- **建议**: 使用 `DeweyNumber.compareTo()` 替代 `hashCode()`，实现真正的全序比较。
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

---

### [AR-38] TwoPhaseCommitSinkFunction.saveState() 在未同步状态下遍历 synchronized map — 并发快照不一致

- **文件**: `nop-stream-core/.../functions/sink/TwoPhaseCommitSinkFunction.java:34,66-70`
- **证据片段**:
  ```java
  // 行 34: synchronized map
  this.pendingCommits = Collections.synchronizedMap(new TreeMap<>());

  // 行 66-70: 无同步保护地遍历
  @Override
  public TaskStateSnapshot saveState(long epochId) throws Exception {
      TaskStateSnapshot snapshot = new TaskStateSnapshot(new TaskLocation(), epochId);
      Map<Long, Object> copy = new TreeMap<>(pendingCommits);  // ← 无 synchronized(pendingCommits)
      snapshot.putOperatorState(PENDING_COMMITS_KEY, copy);
      return snapshot;
  }
  ```
- **严重程度**: P1
- **现状**: `pendingCommits` 是 `Collections.synchronizedMap`，但 `new TreeMap<>(pendingCommits)` 触发的迭代未在 `synchronized (pendingCommits)` 块中执行（违反 `Collections.synchronizedMap` 契约）。与此同时，`finishCommit()` 方法（行 86/96/101）正确使用了 `synchronized (pending)` 块。如果 `finishCommit()` 和 `saveState()` 并发执行，`TreeMap` 复制构造函数可能抛出 `ConcurrentModificationException` 或产生不一致的快照。
- **风险**: Checkpoint 时 2PC sink 的 pendingCommits 映射可能不一致——部分事务被快照、部分被跳过，恢复后事务状态损坏（已 commit 的事务被重复 commit，或未 commit 的事务被遗漏）。
- **建议**: 将 `new TreeMap<>(pendingCommits)` 包裹在 `synchronized (pendingCommits)` 中。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-39] TwoPhaseCommitSinkFunction.setPendingCommits() 允许替换为非同步 Map — 线程安全丧失

- **文件**: `nop-stream-core/.../functions/sink/TwoPhaseCommitSinkFunction.java:61-63`
- **证据片段**:
  ```java
  public void setPendingCommits(Map<Long, Object> pending) {
      this.pendingCommits = pending;  // 可替换为任意 Map 实现
  }
  ```
  与 `finishCommit()` 中的同步块配合：
  ```java
  synchronized (pending) {  // 行 86, 96, 101 — 锁对象随 Map 替换而变
      // ...
  }
  ```
- **严重程度**: P2
- **现状**: 公开的 `setPendingCommits()` 方法可将内部的 `synchronizedMap` 替换为任意 `Map` 实现。如果替换为非同步 Map（如 `HashMap`），`finishCommit()` 中的 `synchronized(pending)` 块虽然仍加锁，但其他通过 `pendingCommits` 字段直接访问 Map 的代码路径（如 `saveState()`）没有同步保护。
- **风险**: 恢复后（`restoreFromEpoch` 通过 `setPendingCommits` 设置恢复的数据），如果恢复数据是 `Collections.synchronizedMap` 包装的则安全，但接口契约无此保证。
- **建议**: `setPendingCommits()` 应验证或包装输入为 `synchronizedMap`，或将字段改为 `private` 并通过方法控制访问。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-40] TwoPhaseCommitSinkFunction.finishCommit() commit() 调用在同步块外 — 并发双重 commit

- **文件**: `nop-stream-core/.../functions/sink/TwoPhaseCommitSinkFunction.java:94-103`
- **证据片段**:
  ```java
  for (Map.Entry<Long, Object> entry : toCommit.entrySet()) {
      Long eid = entry.getKey();
      synchronized (pending) {
          if (!pending.containsKey(eid))
              continue;
      }
      commit(eid);                    // ← 不在任何同步块内
      synchronized (pending) {
          pending.remove(eid);
      }
  }
  ```
- **严重程度**: P2
- **现状**: `commit(eid)` 调用位于两个 `synchronized(pending)` 块之间。如果两个线程同时调用 `finishCommit()` 且 epoch 范围重叠，两个线程都可以通过 `containsKey` 检查，然后对同一个 `eid` 调用 `commit()`。
- **风险**: 非幂等的 `commit()` 实现（如数据库事务提交）被双重调用，导致数据重复或异常。
- **建议**: 将 `containsKey` + `commit` + `remove` 放在同一个 `synchronized` 块内，或在 `commit` 前先 `remove` 再 commit。
- **信心水平**: 很可能

---

### [AR-41] CepOperator.processElement() 与 onProcessingTime() 无同步保护 — 处理时间模式下共享状态竞争

- **文件**: `nop-stream-cep/.../operator/CepOperator.java:304-330,387-414`
- **证据片段**:
  ```java
  // processElement — 流处理线程
  public void processElement(StreamRecord<IN> element) throws Exception {
      if (isProcessingTime) {
          if (comparator == null) {
              // ...
          } else {
              long currentTime = timerService.currentProcessingTime();
              bufferEvent(element.getValue(), currentTime); // 修改 elementQueueState
          }
      }
      // ... 修改 computationStates, nfaState
  }

  // onProcessingTime — 定时器线程
  public void onProcessingTime(long time) throws Exception {
      PriorityQueue<Long> sortedTimestamps = getSortedTimestamps(); // 读取 elementQueueState
      // ... 移除 elementQueueState 条目，修改 computationStates, nfaState
  }
  ```
- **严重程度**: P1
- **现状**: 在 processing time + comparator 模式下，`processElement()` 调用 `bufferEvent()` 读写 `elementQueueState` 并注册定时器。定时器触发 `onProcessingTime()`，从另一个线程读写同一个 `elementQueueState`。整个 `CepOperator` 没有任何 `synchronized` 块或 `Lock`。共享的可变状态包括 `computationStates`、`nfaState`、`elementQueueState`。
- **风险**: 如果运行时不保证 operator 的单线程调用模型（即 timer callback 在独立线程），processing time + comparator 模式下存在数据竞争：`ConcurrentModificationException`、丢失事件、重复处理或状态损坏。Flink 的 mailbox 模型保证单线程访问，但 nop-stream 当前没有等效机制。
- **建议**: 在 `processElement` 和 `onProcessingTime` 中添加 `synchronized(this)` 或使用 `ReentrantLock`；或在运行时文档中明确要求单线程调用保证。
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

---

### [AR-42] BatchConsumerSinkFunction 在构造函数中急切创建 IBatchConsumer — 序列化时 NotSerializableException

- **文件**: `nop-stream-connector/.../connector/BatchConsumerSinkFunction.java:31,35,53-54`
- **证据片段**:
  ```java
  // 行 31: 实现 SinkFunction → StreamFunction → Serializable
  public class BatchConsumerSinkFunction<R> implements SinkFunction<R> {

      // 行 35: 非 transient
      private final IBatchConsumerProvider.IBatchConsumer<R> consumer;

      // 行 53-54: 构造函数中急切创建
      this.taskContext = new BatchTaskContextImpl();
      this.consumer = consumerProvider.setup(taskContext);
  }
  ```
- **严重程度**: P1
- **现状**: `BatchConsumerSinkFunction` 通过继承链是 `Serializable`。构造函数中急切调用 `consumerProvider.setup()` 创建 `IBatchConsumer` 实例，该实例几乎必然不可序列化（持有 DB 连接、文件句柄等运行时资源）。`consumer` 字段非 `transient`。对比同模块 `BatchLoaderSourceFunction` 正确地在 `run()` 中延迟创建 loader。
- **风险**: 分布式模式下序列化 task 时抛出 `NotSerializableException`。当前仅在嵌入式/本地模式可用。
- **建议**: (1) 将 `consumer` 标记为 `transient`，在 `open()` 或 `consume()` 首次调用时延迟创建；(2) 保存 `consumerProvider`（应可序列化）而非 `consumer`。
- **信心水平**: 确定
- **发现来源视角**: 新人开发者

---

### [AR-43] CheckpointCoordinator.completePendingCheckpoint() 存储失败时直接 set(ABORTED) 而不调用 pending.abort() — CompletableFuture 永远不会 complete

- **文件**: `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:225-236,244-254`
- **证据片段**:
  ```java
  // 存储失败路径（行 229）:
  pending.getStatus().set(PendingCheckpoint.Status.ABORTED);  // 直接设置状态
  pendingCheckpoints.remove(checkpointId, pending);
  // ... 但 pending.abort() 从未被调用 → completableFuture 不会被 complete

  // 对比 abortPendingCheckpoint() 方法（正确实现）:
  removed.abort(reason);  // 会完成 future
  ```
- **严重程度**: P1
- **现状**: 当 `checkpointStorage.storeCheckPoint()` 或 `storeEpochManifest()` 抛出异常时，代码直接设置 `pending.getStatus()` 为 `ABORTED` 但不调用 `pending.abort()`。`abort()` 方法负责完成 `CompletableFuture`（exceptionally）。结果：任何等待 `pending.getCompletableFuture().get()` 的线程会永远阻塞。
- **风险**: `GraphModelCheckpointExecutor.triggerSavepoint()` 在行 202 调用 `savepointPending.getCompletableFuture().get(timeout)`，如果存储失败，该调用会阻塞到超时而非立即返回错误。Savepoint 操作的"失败"被延迟为"超时"。
- **建议**: 将 `pending.getStatus().set(ABORTED)` 替换为调用 `abortPendingCheckpoint()` 或直接调用 `pending.abort(reason)`。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-44] JobCoordinator.assignTasks() 硬编码 "pipeline-0" 作为 TaskLocation pipelineId — 与 CheckpointCoordinator pipelineId 不匹配

- **文件**: `nop-stream-runtime/.../coordinator/JobCoordinator.java:226`
- **证据片段**:
  ```java
  locations.add(new TaskLocation(jobId, "pipeline-0", vertexId, subtaskIndex));
  ```
- **严重程度**: P1
- **现状**: `assignTasks()` 中创建的 `TaskLocation` 使用硬编码的 `"pipeline-0"` 作为 pipelineId。但 `CheckpointCoordinator` 是使用 `deploymentPlan.getPartitionedPlan().getPipelineId()` 创建的（可能不是 `"pipeline-0"`）。如果实际 pipelineId 不同，checkpoint ACK 处理时的 `TaskLocation` 匹配会失败——所有 ACK 被静默拒绝，checkpoint 永远无法完成。
- **风险**: 非默认 pipelineId 的流处理作业无法完成 checkpoint。当前可能因默认 pipelineId 恰好为 `"pipeline-0"` 而未暴露。
- **建议**: 使用 `deploymentPlan.getPartitionedPlan().getPipelineId()` 替代硬编码值。
- **信心水平**: 很可能
- **发现来源视角**: 10x 规模运维者

---

### [AR-45] GraphModelCheckpointExecutor.createStorage() 对 null storageType 抛出模糊异常 — 常见执行路径崩溃

- **文件**: `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java:568-579`
- **证据片段**:
  ```java
  static ICheckpointStorage createStorage(CheckpointConfig config) {
      String storageType = config.getStorageType();
      if ("jdbc".equalsIgnoreCase(storageType)) {
          throw new StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_FAILED)...;
      }
      if (storageType == null || !"local".equalsIgnoreCase(storageType)) {
          throw new StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_FAILED)
                  .param(ARG_DETAIL, "Unknown storage type: " + storageType);
          // ← 当 storageType==null 时，消息是 "Unknown storage type: null"
      }
  }
  ```
- **严重程度**: P2
- **现状**: `CheckpointConfig` 的 `storageType` 字段无默认值（为 null）。如果用户通过 `executeWithCheckpoint()` 调用但未显式设置 storageType，`createStorage()` 会抛出 "Unknown storage type: null" 异常。这是一个非常常见的使用路径——大多数用户不会设置 storageType。
- **风险**: 首次使用 checkpoint 功能的用户遇到不直觉的错误消息。应默认为 "local"。
- **建议**: 当 `storageType == null` 时默认使用 "local"，而非抛出异常。
- **信心水平**: 确定
- **发现来源视角**: 新人开发者

---

### [AR-46] InMemoryClusterRegistry.renewLease() 忽略 leaseTimeoutMs 参数 — 与 JDBC 实现行为不一致

- **文件**: `nop-stream-runtime/.../cluster/InMemoryClusterRegistry.java:54-59`
- **证据片段**:
  ```java
  @Override
  public boolean renewLease(String nodeId, long leaseTimeoutMs) {
      if (!nodes.containsKey(nodeId)) {
          return false;
      }
      leaseTimestamps.put(nodeId, System.currentTimeMillis());
      return true;  // leaseTimeoutMs 被完全忽略
  }
  ```
  对比 `JdbcClusterRegistry.renewLease()`（正确使用参数）:
  ```java
  // compute expireAt = now + leaseTimeoutMs
  ```
- **严重程度**: P2
- **现状**: `renewLease()` 的 `leaseTimeoutMs` 参数被完全忽略。`getNodeLease()` 总是使用静态常量 `LEASE_TIMEOUT_MS = 15000L`。而 `JdbcClusterRegistry` 正确地将参数用于计算过期时间。这意味着在内存实现中测试通过的租约超时行为，切换到 JDBC 后会使用不同的超时值。
- **风险**: 测试与生产的行为不一致。自定义 leaseTimeout 的功能在内存实现中不可验证。
- **建议**: 使用参数计算过期时间，与 JDBC 实现保持一致。
- **信心水平**: 确定
- **发现来源视角**: 新人开发者

---

### [AR-47] SourceEnumerator.restoreState() 重算 nextSubtaskIndex 公式错误 — 恢复后 split 分配偏移

- **文件**: `nop-stream-runtime/.../source/SourceEnumerator.java:258-259`
- **证据片段**:
  ```java
  // Recalculate nextSubtaskIndex based on assigned splits
  this.nextSubtaskIndex = discoveredSplits.size() - unassignedSplits.size();
  ```
- **严重程度**: P2
- **现状**: `discoveredSplits.size() - unassignedSplits.size()` 计算的是"已分配 + 已完成"的 split 数。但 `nextSubtaskIndex` 的目的是继续 round-robin 分配的位置。如果部分 split 已完成（finishedSplits），公式会高估 index，跳过某些 subtask。
  - 例：10 discovered, 3 unassigned, 5 finished, 2 assigned → 公式给 7，但正确值应基于 2 个已分配 split 的 round-robin 位置。
- **风险**: checkpoint 恢复后 split 分配不均匀，部分 subtask 被跳过。
- **建议**: 使用 `assignedSplits.size() % totalParallelism` 计算，排除已完成的 split。
- **信心水平**: 很可能
- **发现来源视角**: 10x 规模运维者

---

### [AR-48] Lockable.equals()/hashCode() 依赖可变的 refCounter — 违反 Java Object 契约

- **文件**: `nop-stream-cep/.../nfa/sharedbuffer/Lockable.java:79-93`
- **证据片段**:
  ```java
  @Override
  public boolean equals(Object o) {
      // ...
      return refCounter.get() == lockable.refCounter.get()
             && Objects.equals(element, lockable.element);
  }

  @Override
  public int hashCode() {
      return Objects.hash(refCounter.get(), element);
  }
  ```
- **严重程度**: P2
- **现状**: `equals()` 和 `hashCode()` 都依赖 `refCounter.get()`——一个每次 `lock()`/`release()` 调用都会改变的 `AtomicInteger`。虽然 `Lockable` 主要作为 Map 的 value（非 key）使用，但 `SharedBufferNode.equals()` 通过 `Objects.equals(edges, that.edges)` 间接调用 `Lockable.equals()`。当边的锁计数变化时，两个结构相同的 node 可能比较为不等。
- **风险**: 依赖 `SharedBufferNode` 相等性的逻辑（如状态比较、去重）结果不可预测。
- **建议**: `equals()`/`hashCode()` 应只比较 `element`，不包括 `refCounter`（`refCounter` 是运行时锁管理状态，不是值的一部分）。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-49] SharedBuffer.registerEvent() 将同一 Lockable 引用存入 cache 和 state — 状态后端切换时行为异常

- **文件**: `nop-stream-cep/.../nfa/sharedbuffer/SharedBuffer.java:181-196`
- **证据片段**:
  ```java
  EventId registerEvent(V value, long timestamp) {
      // ...
      Lockable<V> lockableValue = new Lockable<>(value, 1);
      eventsCount.put(timestamp, id + 1);
      eventsBufferCache.put(eventId, lockableValue);  // 同一对象
      try {
          eventsBuffer.put(eventId, lockableValue);     // 同一对象
      } catch (Exception e) {
          throw NopException.adapt(e);
      }
      return eventId;
  }
  ```
- **严重程度**: P2
- **现状**: 同一个 `Lockable<V>` 对象引用同时存入 `eventsBufferCache`（ConcurrentHashMap）和 `eventsBuffer`（MapState）。由于 `Lockable` 是可变的（refCounter），cache 中的修改会直接影响 state 中的引用。当前使用 `MemoryKeyedStateBackend`（不序列化，保持引用），但切换到持久化状态后端时，序列化/反序列化会打破引用共享，导致 cache 和 state 不同步。
- **风险**: 当前无实际问题，但是未来引入持久化状态后端时的隐含缺陷。设计上应将 cache 和 state 视为独立副本。
- **建议**: 存入 state 时使用深拷贝，或在 flushCache 时才写入 state（避免双写）。
- **信心水平**: 很可能

---

### [AR-50] MemoryInternalAppendingState.add() 共享 accumulator 实例跨 key — 可变状态可能交叉污染

- **文件**: `nop-stream-core/.../state/backend/memory/MemoryInternalAppendingState.java:82-96`
- **证据片段**:
  ```java
  @Override
  public void add(IN value) throws IOException {
      TypedNamespaceAndKey key = getStorageKey();
      ACC current = storage.get(key);
      accumulator.resetLocal();
      if (current != null) {
          accumulator.add((IN) current);
      }
      accumulator.add(value);
      storage.put(key, (ACC) accumulator.getLocalValue());
  }
  ```
- **严重程度**: P2
- **现状**: 单个 `accumulator` 实例跨所有 key 共享。`getLocalValue()` 可能返回可变的内部状态引用（取决于 accumulator 类型）。如果 `storage.put(key, value)` 存储的是可变引用而非深拷贝，后续 key 的 `accumulator.resetLocal()` 可能修改已存储的值。对于不可变返回类型（如 `Long`、`Double`）安全，但对于返回可变集合或对象的 accumulator 不安全。
- **风险**: 使用返回可变对象的 accumulator 时，不同 key 的状态交叉污染。
- **建议**: 确保所有 `SimpleAccumulator.getLocalValue()` 返回不可变值或深拷贝，或在 `add()` 中显式深拷贝。
- **信心水平**: 很可能

---

### [AR-51] HashPartitionRouter 对 null record value 抛出 NPE — 整个 task 崩溃

- **文件**: `nop-stream-core/.../execution/HashPartitionRouter.java:30-35`
- **证据片段**:
  ```java
  @Override
  @SuppressWarnings("unchecked")
  public int selectChannel(StreamRecord<?> record) {
      if (partitioner != null) {
          int channel = ((IPartitioner<Object>) partitioner).partition(record.getValue(), numPartitions);
          return Math.floorMod(channel, numPartitions);
      }
      return Math.floorMod(record.getValue().hashCode(), numPartitions);  // NPE if getValue()==null
  }
  ```
- **严重程度**: P2
- **现状**: 无 partitioner 的回退路径直接调用 `record.getValue().hashCode()`。如果 record value 为 null（在流处理中合法——如 tombstone 事件、null 值过滤遗漏），立即 NPE 导致整个 task 崩溃。
- **风险**: 上游发送 null 值的记录时，整个 partition router 崩溃而非将 null 路由到特定分区。
- **建议**: 添加 null 检查，null 值路由到固定分区（如分区 0 或随机分区）。
- **信心水平**: 确定

---

### [AR-52] SubtaskTask.isFinished() 将 CANCELING 视为已完成 — 过早资源清理

- **文件**: `nop-stream-core/.../execution/SubtaskTask.java:127-129`
- **证据片段**:
  ```java
  public boolean isFinished() {
      State s = state.get();
      return s == State.COMPLETED || s == State.FAILED || s == State.CANCELED || s == State.CANCELING;
  }
  ```
- **严重程度**: P2
- **现状**: `CANCELING` 是过渡状态——任务线程仍在运行（invokable 仍在执行）。将 `CANCELING` 视为"已完成"可能导致调用者跳过等待、释放资源、或继续后续步骤。对比 `Task.isFinished()` 只检查终止状态。
- **风险**: 任务仍在执行时被当作已完成处理，资源被过早释放。
- **建议**: 移除 `s == State.CANCELING` 条件。
- **信心水平**: 确定

---

### [AR-53] EvalFunctionCondition 将 IterativeCondition.Context 传入 IEvalFunction — XPL 表达式无法使用 scope 功能

- **文件**: `nop-stream-cep/.../model/builder/EvalFunctionCondition.java:27-29`
- **证据片段**:
  ```java
  @Override
  public boolean filter(Object value, Context ctx) {
      return ConvertHelper.toTruthy(action.call2(null, value, ctx, null));
  }
  ```
- **严重程度**: P2
- **现状**: `call2(null, value, ctx, null)` 的第三个参数是 `IterativeCondition.Context`，不是 `IEvalScope`。XPL 函数如果尝试使用 scope 级别的功能（变量解析、表达式求值），会收到类型不匹配的对象。函数只能将参数作为不透明的位置参数使用。
- **风险**: 通过 XLang 模型定义的 CEP 条件无法使用完整的 XPL 功能（如访问上下文变量），限制了模型驱动 CEP 的表达能力。
- **建议**: 创建适配器将 `IterativeCondition.Context` 包装为 `IEvalScope`，或修改 `call2` 的调用约定。
- **信心水平**: 很可能
- **发现来源视角**: 新人开发者

---

### [AR-54] MessageSourceFunction.run() 无 try/finally 保护订阅清理 — 异常退出时订阅泄漏

- **文件**: `nop-stream-connector/.../connector/MessageSourceFunction.java:104-126`
- **证据片段**:
  ```java
  @Override
  public void run(SourceContext<T> ctx) throws Exception {
      // ...
      subscription = messageService.subscribe(effectiveTopic, ...);  // 行 110

      while (running) {
          shutdownLatch.await(1, TimeUnit.SECONDS);  // 可能抛 InterruptedException
      }
      // 无 finally 块取消 subscription
  }
  ```
- **严重程度**: P2
- **现状**: `run()` 在订阅成功后进入等待循环。如果 `shutdownLatch.await()` 抛出 `InterruptedException`（或任何其他异常），方法直接退出而不取消 `subscription`。配合 AR-29（`subscription` 非 volatile），cancel() 也可能看不到订阅。
- **风险**: 消息订阅泄漏，消息服务继续推送数据到已关闭的 consumer。
- **建议**: 用 try/finally 包裹订阅后的逻辑，在 finally 中取消 subscription。
- **信心水平**: 确定

---

## 历史问题状态更新

以下之前报告的问题在本轮审查中确认**仍然存在**（简要引用，不重复分析）：

| # | 描述 | 状态 | 变化 |
|---|------|------|------|
| R5-AR-1 | Lockable.release() refCounter<=0 返回 true | 仍存在 | CAS 改进并发，语义 bug 未修 |
| R5-AR-2 | JdbcClusterRegistry 无 fencing token 校验 | 仍存在 | 无变化 |
| R5-AR-3 | WindowOperator addWindowElement 非累加器覆盖 | 仍存在 | 无变化 |
| R5-AR-5 | WindowOperator timer purge 不清除 trigger 状态 | 仍存在 | 无变化 |
| R5-AR-7 | SharedBuffer.advanceTime 不清理 backing state | 仍存在 | 无变化 |
| R5-AR-8 | CepOperator 清除 NFA 状态产生孤立条目 | 仍存在 | 无变化 |
| R5-AR-9 | Source/Sink 快照失败不通知 callback | 仍存在 | 无变化 |
| R5-AR-10 | DebeziumCdcSourceFunction draining 死代码 | 仍存在 | 无变化 |
| R5-AR-11 | SourceEnumerator.assignSplits 要求顺序调用 | 仍存在 | 无变化 |
| R5-AR-12 | JdbcCheckpointStorage 不检查 affected rows | 仍存在 | 无变化 |
| R6-AR-17 | CheckpointBarrierTracker operatorsToAck 不重置 | 仍存在 | 无变化 |
| R6-AR-18 | AbstractStreamOperator 吞掉 snapshot 异常 | 仍存在 | 无变化 |
| R6-AR-19 | CheckpointCoordinator counter 不推进 | 仍存在 | 无变化 |
| R6-AR-20 | InputGate 不检查 barrier ID | 仍存在 | 无变化 |
| R6-AR-21 | CepOperator timer 不被 checkpoint 持久化 | 仍存在 | 无变化 |
| R6-AR-24 | DebeziumCdcSourceFunction REPLAYABLE 无实现 | 仍存在 | 无变化 |
| R6-AR-29 | MessageSourceFunction subscription 非 volatile | 仍存在 | 无变化 |
| R13-AR-1 | SubtaskTask.cancel() 只能从 CREATED 取消 | 仍存在 | 无变化 |
| R13-AR-3 | CheckpointCoordinator 参与者索引失效 | 仍存在 | 无变化 |
| R13-AR-5 | WindowAggregationOperator `#` 分隔符 | 仍存在 | 无变化 |
| R13-AR-7 | WindowOperator triggerAccumulators `_` 分隔符 | 仍存在 | 无变化 |

---

## 总评

### 最值得关注的 3 个方向

1. **SharedBuffer 的数据完整性保护不足**（AR-36/48/49）是一个新发现的高价值方向。`flushCache()` 的 clear-before-write 模式在状态写入失败时导致数据静默丢失，这是一个在任何异常场景下都可能触发的路径。配合 `Lockable` 的可变 `equals/hashCode`（AR-48）和 cache-state 共享引用（AR-49），SharedBuffer 的"数据丢失"风险不仅仅来自引用计数（AR-1），也来自写入顺序和对象共享。

2. **TwoPhaseCommitSinkFunction 的线程安全是一个未被发现的设计缺陷**（AR-38/39/40）。三个独立但相关的并发问题：`saveState()` 的无同步迭代、`setPendingCommits()` 的类型不安全替换、`finishCommit()` 的双重 commit 窗口。2PC 是端到端 exactly-once 语义的关键组件，其内部并发正确性直接影响事务完整性。

3. **分布式协调的 pipeline ID 一致性是新发现的系统性风险**（AR-44）。`JobCoordinator.assignTasks()` 硬编码 `"pipeline-0"` 与 `CheckpointCoordinator` 使用的实际 pipelineId 可能不匹配。这是一个"在简单场景下隐藏、在复杂场景下爆发"的典型 bug——单 pipeline 作业不会触发，但多 pipeline 或自定义 pipelineId 的作业会静默失败。

### 本次审查的盲区自评

1. **性能压测**：所有并发问题（AR-40/41）都是理论分析，没有实际压力测试验证。
2. **nop-stream-flow 模块**：仍然为空壳模块，无法审查。
3. **Flink 集成路径**：nop-stream-flink 仍为空，未审查。
4. **序列化兼容性**：没有验证 checkpoint 数据在不同版本间的兼容性。
5. **fraud-example 端到端正确性**：未审查示例应用的端到端流程。
6. **Timer 服务的回调重入**：未分析 `HeapInternalTimerService.advanceWatermark` 中 `onEventTime` 回调注册/删除 timer 的重入问题。

---

## 严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 6    | SharedBuffer 数据丢失(1) + NFA 相等性(1) + 2PC 线程安全(1) + CEP 并发(1) + Connector 序列化(1) + Checkpoint future 悬挂(1) + pipeline ID 不匹配(1) |
| P2      | 11   | 2PC 线程安全(2) + Checkpoint 配置(1) + 集群语义(1) + 源恢复(1) + Lockable 契约(1) + SharedBuffer 隔离(1) + 状态后端(1) + 路由(1) + 任务状态(1) + XPL 限制(1) + 资源泄漏(1) |
