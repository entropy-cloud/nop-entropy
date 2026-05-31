# nop-stream 对抗性审查 — Round 6（第6轮全模块审查）

> 审查日期：2026-05-31
> 审查范围：nop-stream 全模块（10 个子模块），聚焦之前审查未覆盖的 checkpoint 管线正确性、并发安全、恢复语义
> 审查方法：开放式发现导向，4 个并行探索 agent 分别聚焦 core operators、runtime 执行引擎、CEP/windowing、connector/API
> 去重：已阅读以下已有报告，本报告不重复其中内容：
> - `ai-dev/audits/2026-05-31-adversarial-review-nop-stream/`（Round 5，AR-1~AR-16）
> - `ai-dev/audits/2026-05-31-deep-audit-nop-stream/`（21 维度深度审核）
> - `ai-dev/audits/2026-05-31-deep-audit-nop-stream-full/`（6 维度深度审核）
> - `ai-dev/audits/2026-05-30-adversarial-review-nop-stream-r13/`（Round 4）
> - `ai-dev/audits/2026-05-30-deep-audit-nop-stream-full/`（全维度深度审核）
> 发现来源视角：事务边界追踪者 + 异常路径侦探 + 10x 规模运维者 + 死代码清道夫

---

## 发现分类

| 类别 | 数量 | 最严重 |
|------|------|--------|
| Checkpoint 管线正确性 | 5 | P0 |
| 恢复语义缺陷 | 3 | P1 |
| 并发安全 | 2 | P1 |
| 资源管理 | 2 | P1 |
| Connector 契约违反 | 2 | P1 |
| 设计缺陷 / 代码健壮性 | 5 | P2 |

---

## P0：Checkpoint 管线正确性

### [AR-17] CheckpointBarrierTracker.triggerCheckpoint 在 offerBarrier 被拒绝后不重置 operatorsToAck — checkpoint 永久卡死

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/execution/CheckpointBarrierTracker.java:55-87`
- **证据片段**:
  ```java
  public synchronized boolean triggerCheckpoint(long checkpointId, long timestamp, CheckpointType type) throws Exception {
      if (operatorsToAck.get() > 0) {
          return false;                          // 已有进行中的 checkpoint
      }
      this.currentCheckpointId = checkpointId;
      this.currentSnapshot = new TaskStateSnapshot(taskLocation, checkpointId);
      int count = 0;
      for (StreamOperator<?> op : operators) {
          if (op instanceof AbstractStreamOperator) { count++; }
      }
      this.operatorsToAck.set(count);            // ← 设置为 > 0

      CheckpointBarrier barrier = new CheckpointBarrier(checkpointId, timestamp, type);
      if (!operators.isEmpty()) {
          StreamOperator<?> head = operators.get(0);
          if (head instanceof StreamSourceOperator) {
              boolean accepted = ((StreamSourceOperator<?>) head).offerBarrier(barrier);
              if (!accepted) {
                  LOG.warn("Checkpoint {} rejected...", checkpointId);
                  return false;                   // ← 返回 false，但 operatorsToAck 仍然是 count！
              }
          }
      }
      return true;
  }
  ```
- **严重程度**: P0
- **现状**: `triggerCheckpoint` 先设置 `operatorsToAck = count > 0`，然后尝试 `offerBarrier`。如果 offer 被拒绝（Source 的 pending queue 已满），直接返回 false，但 `operatorsToAck` 未被重置为 0。下次 `triggerCheckpoint` 检查 `operatorsToAck > 0` 直接拒绝。**所有后续 checkpoint 永久卡死**。
- **风险**: 一次 Source 拒绝 barrier 即可导致整个流处理作业永久失去 checkpoint 能力。在 Source 暂时性繁忙（如 pending barriers 堆积）场景下必然触发。
- **建议**: 在 `offerBarrier` 返回 false 的分支中添加 `operatorsToAck.set(0)` 并清除 `currentSnapshot`。
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者

---

### [AR-18] AbstractStreamOperator.processBarrier 吞掉 snapshot 异常并传播空结果 — checkpoint 以空状态"成功完成"

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/operators/AbstractStreamOperator.java:262-285`
- **证据片段**:
  ```java
  public void processBarrier(CheckpointBarrier barrier) throws Exception {
      OperatorSnapshotResult snapshotResult = null;
      Exception snapshotError = null;
      if (barrier.snapshot()) {
          try {
              StateSnapshotContext context = new StateSnapshotContext(barrier.getId(), barrier.getTimestamp());
              snapshotResult = snapshotState(context);
              this.lastSnapshotResult = snapshotResult;
          } catch (Exception e) {
              snapshotError = e;                   // 捕获异常，仅存局部变量
          }
      }
      if (output != null) {
          output.emitBarrier(barrier);              // barrier 照常向下游传播
      }
      if (snapshotCallback != null) {
          if (snapshotResult != null) {
              snapshotCallback.accept(snapshotResult);
          } else if (snapshotError != null) {
              OperatorSnapshotResult failureResult = new OperatorSnapshotResult();
              snapshotCallback.accept(failureResult); // ← 传空 result，callback 无法区分成功/失败
          }
      }
      // snapshotError 从未被重新抛出或记录到任何持久化位置
  }
  ```
- **严重程度**: P0
- **现状**: 当 `snapshotState()` 抛出异常时：(1) 异常被完全吞掉（无日志、无重抛）；(2) barrier 照常向下游传播，下游所有 operator 继续做自己的 snapshot；(3) 空的 `OperatorSnapshotResult` 被传给 callback。Checkpoint coordinator 收到 ACK 后认为 snapshot 成功，但该 operator 的状态为空。**恢复后丢失该 operator 的全部状态**。
- **风险**: 任何导致 snapshot 失败的 I/O 错误（磁盘满、序列化失败等）都会静默导致状态丢失，且无法通过 checkpoint 成功/失败指标感知。与 AR-9（Source/Sink 不通知 callback）是同一缺陷模式的不同表现。
- **建议**: (1) 添加 `LOG.error("Snapshot failed for operator", snapshotError)`；(2) 空 result 中应包含错误信息，或在 callback 接口中增加 error 参数；(3) 考虑将异常重新抛出以 fail-fast。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-19] CheckpointCoordinator.restoreFromCheckpoint 不推进 checkpointIdCounter — 恢复后 checkpoint ID 冲突

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:312-320`
- **证据片段**:
  ```java
  public CompletedCheckpoint restoreFromCheckpoint() throws Exception {
      CompletedCheckpoint checkpoint = checkpointStorage.getLatestCheckpoint(jobId, pipelineId);
      if (checkpoint != null) {
          checkpoint.setRestored(true);
          latestCompletedCheckpoint = checkpoint;
          LOG.info("Restored checkpoint {} for job {}", checkpoint.getCheckpointId(), jobId);
          // ← 未调用 checkpointIdCounter.setAtLeast(checkpoint.getCheckpointId() + 1)
      }
      return checkpoint;
  }
  ```
- **严重程度**: P0
- **现状**: 恢复了 `latestCompletedCheckpoint`（如 checkpointId=5），但 `checkpointIdCounter` 仍从初始值开始。新触发的 checkpoint 会使用 id=0、1、2...，与已存储的 checkpoint ID 重叠。`JdbcCheckpointStorage` 的 INSERT 会因唯一约束冲突而失败；`LocalFileCheckpointStorage` 会覆盖旧文件。**两种情况都破坏 exactly-once 语义**。
- **风险**: Job 重启后恢复 checkpoint 但后续 checkpoint 全部失败（JDBC）或覆盖历史数据（LocalFile），等于 checkpoint 管线不可用。
- **建议**: 在 `restoreFromCheckpoint` 中添加 `checkpointIdCounter.setAtLeast(checkpoint.getCheckpointId() + 1)`。
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者

---

### [AR-20] InputGate.handleBarrierNonRecursive 不检查 barrier ID — 不同 checkpoint 的 barrier 被混合对齐

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:272-303`
- **证据片段**:
  ```java
  private Optional<StreamElement> handleBarrierNonRecursive(int channelIndex, CheckpointBarrier barrier) {
      if (!barrierReceived[channelIndex]) {
          barrierReceived[channelIndex] = true;
          if (pendingBarrier == null) {
              pendingBarrier = barrier;             // 记录第一个 barrier 作为"当前对齐的 barrier"
              barriersRemaining = channels.size();
          }
          barriersRemaining--;
          // ← 从不检查 barrier.getId() 是否与 pendingBarrier.getId() 相同
      }
      return null;
  }
  ```
- **严重程度**: P0
- **现状**: `handleBarrierNonRecursive` 接受来自不同 channel 的 barrier，但从不验证它们的 checkpoint ID 是否一致。第一个到达的 barrier 被记录为 `pendingBarrier`，后续 channel 到达的 barrier 即使 ID 不同也被计入 `barriersRemaining`。如果上游 subtask 重启后重发旧 barrier，不同 checkpoint 的 barrier 会被混合对齐。
- **风险**: 混合对齐导致：(1) 提前触发对齐完成（barriersRemaining 降到 0），触发一个使用了错误 barrier 的 snapshot；(2) 真正的 barrier 到达时被当作重复 barrier 忽略（`barrierReceived` 已为 true）；(3) 两个 checkpoint 的状态被混合，恢复后数据不一致。
- **建议**: 在 `handleBarrierNonRecursive` 中添加 `barrier.getId() == pendingBarrier.getId()` 检查，不匹配时记录警告并重置对齐状态。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-21] CepOperator 事件时间定时器不被 checkpoint 持久化 — 恢复后 CEP 超时检测失效

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:214-261`
- **证据片段**:
  ```java
  // open() 方法内部:
  final Set<Long> registeredEventTimeTimers = new TreeSet<>();  // 局部变量，被匿名类捕获

  timerService = new InternalTimerService<VoidNamespace>() {
      @Override
      public void registerEventTimeTimer(VoidNamespace namespace, long time) {
          registeredEventTimeTimers.add(time);     // 仅存于堆内存
      }
      @Override
      public void deleteEventTimeTimer(VoidNamespace namespace, long time) {
          registeredEventTimeTimers.remove(time);
      }
      // ...
  };
  ```
- **严重程度**: P0
- **现状**: `registeredEventTimeTimers` 是 `open()` 方法内的局部变量（被匿名类闭包捕获）。它不是 managed state，不会被 `snapshotState()`/`restoreState()` 持久化。checkpoint 恢复后所有已注册的 event time timer 全部丢失。对比 `WindowOperator` 使用 `WindowOperatorTimerService` 作为实例字段，可以被 checkpoint。
- **风险**: CEP 模式中的 `within(timeWindow)` 超时检测在恢复后完全失效。如果 CEP job 在 checkpoint 后 failover 恢复，所有正在进行中的 pattern match 的超时定时器丢失，partial match 会无限等待。配合 AR-7（SharedBuffer 内存泄漏），长时间运行后内存会持续增长。
- **建议**: 将 `registeredEventTimeTimers` 提升为实例字段，并在 `snapshotState()`/`restoreState()` 中正确持久化和恢复。或者使用 `WindowOperator` 相同的 managed timer service 模式。
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者

---

## P1：恢复语义缺陷

### [AR-22] GraphModelCheckpointExecutor.triggerSavepoint 双重存储 checkpoint

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:203-211`
- **证据片段**:
  ```java
  PendingCheckpoint savepointPending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.SAVEPOINT);
  if (savepointPending != null) {
      triggerBarrierOnAllInvokables(allInvokables, savepointPending);
      CompletedCheckpoint completed = (CompletedCheckpoint) savepointPending
              .getCompletableFuture().get(checkpointConfig.getCheckpointTimeout(), TimeUnit.MILLISECONDS);
      if (completed != null) {
          savepointPath = storage.storeCheckPoint(completed);  // ← 第二次存储
      }
  }
  // CheckpointCoordinator.completePendingCheckpoint() 第 226 行已经调用过一次:
  checkpointStorage.storeCheckPoint(completed);
  ```
- **严重程度**: P1
- **现状**: `completePendingCheckpoint()` 在所有 ACK 到齐后自动调用 `storage.storeCheckPoint()` 存储 checkpoint。之后 `triggerSavepoint` 又手动调用一次。每次 savepoint 被写入两次。
- **风险**: 对于 JDBC storage 是两次 INSERT（第二次会冲突异常）；对于 LocalFile storage 是两次覆盖写入（浪费 I/O）。可能导致 savepoint 看似失败（JDBC 唯一约束冲突），但实际数据已正确存储。
- **建议**: 移除 `triggerSavepoint` 中的手动 `storeCheckPoint` 调用，或者让 `completePendingCheckpoint` 在 SAVEPOINT 类型时跳过自动存储。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-23] CheckpointConfig.jobId 默认随机 UUID — 重启后无法恢复历史 checkpoint

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/CheckpointConfig.java:36`
- **证据片段**:
  ```java
  private String jobId = java.util.UUID.randomUUID().toString();
  ```
- **严重程度**: P1
- **现状**: `jobId` 在字段初始化时赋值为 `UUID.randomUUID()`。每次创建 `CheckpointConfig` 实例都产生不同的 jobId。Job 重启时如果不显式设置 jobId，新的 coordinator 无法通过 `checkpointStorage.getLatestCheckpoint(jobId, pipelineId)` 找到之前保存的 checkpoint。
- **风险**: 默认配置下，流处理作业**永远无法从 checkpoint 恢复**——这是流处理引擎最核心的能力之一。用户必须记住手动设置 jobId 才能启用恢复。
- **建议**: (1) 不提供默认随机值，要求显式设置 jobId（fail-fast）；(2) 或基于作业拓扑计算稳定的 hash 作为默认值；(3) 至少在文档中明确警告此行为。
- **信心水平**: 确定
- **发现来源视角**: 新人开发者

---

### [AR-24] DebeziumCdcSourceFunction 声称 REPLAYABLE 但无 checkpoint 实现 — failover 后 CDC offset 丢失

- **文件**: `nop-stream-connector/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:25,92-94`
- **证据片段**:
  ```java
  // 行25: 只实现 DrainableSource，未实现 CheckpointedSourceFunction 或 ReplayableSourceFunction
  public class DebeziumCdcSourceFunction implements DrainableSource<ChangeEvent> {
      // ...
      @Override
      public SourceConsistencyCapability getSourceConsistency() {
          return SourceConsistencyCapability.REPLAYABLE;  // 声称可重放
      }
  }
  ```
- **严重程度**: P1
- **现状**: `getSourceConsistency()` 返回 `REPLAYABLE`，暗示该 source 支持 checkpoint/replay。但类未实现 `CheckpointedSourceFunction` 或 `ReplayableSourceFunction`，没有 `snapshotState`/`initializeState` 方法。`StreamSourceOperator.snapshotState()` 只对 `instanceof ReplayableSourceFunction` 的 source 做 checkpoint。因此 CDC source 的 offset 从不被保存。
- **风险**: Failover 后 CDC source 从默认位置（最早/最新）重新开始消费，导致数据重复或遗漏。这是 CDC 场景（通常要求 exactly-once）中最严重的正确性问题。
- **建议**: 实现 `ReplayableSourceFunction` 接口，保存/恢复 Debezium 的 offset；或将 `getSourceConsistency()` 改为 `AT_LEAST_ONCE`。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

## P1：并发安全

### [AR-25] CheckpointCoordinator.tryTriggerPendingCheckpoint TOCTOU — 可能突破 maxConcurrentCheckpoints 约束

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:165-186`
- **证据片段**:
  ```java
  public PendingCheckpoint tryTriggerPendingCheckpoint(CheckpointType type) {
      if (numPendingCheckpoints.get() >= config.getMaxConcurrentCheckpoints()) {
          return null;                              // 检查
      }
      // ← 窗口：另一个线程也通过了检查
      long checkpointId = checkpointIdCounter.getAndIncrement();
      PendingCheckpoint pending = new PendingCheckpoint(...);
      pendingCheckpoints.put(checkpointId, pending);
      numPendingCheckpoints.incrementAndGet();       // 操作
      return pending;
  }
  ```
- **严重程度**: P1
- **现状**: `numPendingCheckpoints.get()` 检查和 `numPendingCheckpoints.incrementAndGet()` 之间存在竞态窗口。当 `maxConcurrentCheckpoints=1` 时，两个并发触发都可能通过检查，导致同时存在 2 个 pending checkpoint。
- **风险**: 多个 concurrent checkpoint 同时运行可能导致 barrier 对齐混乱（同一个 operator 收到两个不同 ID 的 barrier），与 AR-20 叠加加剧问题。
- **建议**: 使用 `compareAndSet` 原子操作替代 get + incrementAndGet 的两步操作。
- **信心水平**: 很可能
- **发现来源视角**: 10x 规模运维者

---

### [AR-26] BarrierAligner.close() 不唤醒等待线程 — 线程阻塞至超时

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/barrier/BarrierAligner.java:180-191`
- **证据片段**:
  ```java
  public void close() {
      lock.lock();
      try {
          closed = true;
          for (TreeMap<Long, CheckpointBarrier> barriers : inputBarriers) {
              barriers.clear();
          }
          alignedBarriers.clear();
          // ← 缺少 alignedBarrierAvailable.signalAll()
      } finally {
          lock.unlock();
      }
  }
  // pollAlignedBarrier 中等待的线程:
  while (alignedBarriers.isEmpty()) {
      if (remainingNanos <= 0) return null;
      remainingNanos = alignedBarrierAvailable.awaitNanos(remainingNanos);  // 只能等超时
  }
  ```
- **严重程度**: P1
- **现状**: `close()` 设置 `closed=true` 并清空所有 barrier，但不调用 `alignedBarrierAvailable.signalAll()`。在 `pollAlignedBarrier(timeout)` 中等待的线程必须等到超时才能感知到关闭。等待循环也不检查 `closed` 标志。
- **风险**: 作业停止/恢复时，消费线程被阻塞在 `pollAlignedBarrier` 中直到超时（可能是数秒到数十秒），延长了停止时间。
- **建议**: 在 `alignedBarriers.clear()` 后添加 `alignedBarrierAvailable.signalAll()`，并在 `pollAlignedBarrier` 的等待循环中添加 `if (closed) return null` 检查。
- **信心水平**: 确定
- **发现来源视角**: 资源管理

---

## P1：资源管理

### [AR-27] TaskManager.waitForInvokable 超时后任务被标记为成功 — 任务静默跳过执行

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:431-472`
- **证据片段**:
  ```java
  // run() 方法:
  StreamTaskInvokable inv = waitForInvokable();
  if (inv == null || canceled) {
      LOG.info("Task ... canceled while waiting for invokable", ...);  // 日志误导：实际是超时
      return;                        // 正常退出
  }
  // finally 块:
  completedTasks.put(key, new TaskResult(jobId, vertexId, subtaskIndex,
          error == null && !canceled, canceled, error));  // ← success=true！

  // waitForInvokable():
  private StreamTaskInvokable waitForInvokable() throws InterruptedException {
      if (!invokableLatch.await(30, TimeUnit.SECONDS)) {
          LOG.warn("Timed out waiting for invokable...");
          return null;               // 静默返回 null
      }
      return invokable;
  }
  ```
- **严重程度**: P1
- **现状**: `waitForInvokable()` 等待 30 秒后返回 null，`run()` 随即正常退出（`error==null`, `canceled==false`）。`finally` 块中记录 `TaskResult` 的 `success=true`。任务实际上从未执行任何工作，但被标记为成功。
- **风险**: 分布式执行中，coordinator 看到 task "成功" 后继续下一步，但实际上 task 没有产出任何数据。在 30 秒内未收到 invokable 的场景下（如 coordinator 暂时不可达），整个流处理管线静默跳过关键任务。
- **建议**: 超时后应设置 error 状态并记录为失败，或抛出异常让上层处理。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-28] InMemoryClusterRegistry.getNodeLease 总是返回 active=true — 过期节点被误判为存活

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/InMemoryClusterRegistry.java:63-69`
- **证据片段**:
  ```java
  public LeaseInfo getNodeLease(String nodeId) {
      Long timestamp = leaseTimestamps.get(nodeId);
      if (timestamp == null) return null;
      return new LeaseInfo(nodeId, timestamp, timestamp + LEASE_TIMEOUT_MS, true);
      //                                                        ↑ 永远 true
  }
  // 对比 getActiveNodes():
  if (leaseTime != null && (now - leaseTime) < LEASE_TIMEOUT_MS) {  // 正确检查过期
      active.add(entry.getValue());
  }
  ```
- **严重程度**: P1
- **现状**: `getNodeLease()` 对已过期 lease 仍返回 `active=true`，而 `getActiveNodes()` 正确过滤了过期节点。两个方法对同一节点返回矛盾的活跃状态。
- **风险**: 调用者（如 `SourceEnumerator`）若依赖 `getNodeLease()` 判断节点可用性，会将已失联的 task manager 误判为存活，分配 split 后永远无法消费。
- **建议**: `getNodeLease()` 中添加过期检查：`boolean active = (now - timestamp) < LEASE_TIMEOUT_MS`。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者

---

## P2：Connector 契约违反

### [AR-29] MessageSourceFunction.subscription 非 volatile — cancel() 可能无法取消订阅

- **文件**: `nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:48,107,126-128`
- **证据片段**:
  ```java
  private IMessageSubscription subscription;          // 非 volatile

  // run() 线程写入:
  subscription = messageService.subscribe(...);       // 行 107

  // cancel() 线程读取:
  if (subscription != null) {                          // 行 126
      subscription.cancel();
  }
  ```
- **严重程度**: P2
- **现状**: `subscription` 不是 `volatile`，`run()` 和 `cancel()` 可能在不同线程执行。没有 happens-before 保证，`cancel()` 可能一直看到 null。对比同模块 `DebeziumCdcSourceFunction` 中 `source` 和 `subscription` 都声明为 `volatile`。
- **风险**: 消息订阅无法取消，导致线程泄漏或消息源持续推送数据到已关闭的 consumer。
- **建议**: 将 `subscription` 声明为 `volatile`。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-30] BatchConsumerSinkFunction flush 失败后 buffer 保留 — 重试时整批重复消费

- **文件**: `nop-stream-connector/src/main/java/io/nop/stream/connector/BatchConsumerSinkFunction.java:66-80`
- **证据片段**:
  ```java
  private void flush() {
      if (buffer.isEmpty()) return;
      try {
          consumer.consume(new ArrayList<>(buffer), chunkContext);
          buffer.clear();                              // 只在成功后清除
      } catch (Exception e) {
          throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                  .param(ARG_DETAIL, "Failed to flush batch, data retained for retry");
          // buffer 未清除 → 下一批包含已消费的旧数据
      }
  }
  ```
- **严重程度**: P2
- **现状**: `flush()` 失败后 buffer 被保留（错误消息声称"data retained for retry"），但异常直接抛出，上层没有重试逻辑。如果上层框架捕获异常后再次调用 `consume()`，新记录会追加到未清空的 buffer 中，导致已消费的旧记录被重复消费。
- **风险**: 在 `getSinkConsistency()` 返回 `IDEMPOTENT` 的前提下，整批重复可能导致下游系统压力增大。如果下游不支持幂等，则数据损坏。
- **建议**: (1) 如果要支持重试，应在 `flush()` 内部实现重试而非依赖上层；(2) 如果不重试，应在抛出异常前清除 buffer 以避免后续重复。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

## P2：设计缺陷 / 代码健壮性

### [AR-31] RecordWriter.emitBarrier 广播中断导致部分完成 — 下游 barrier 对齐永久挂起

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/execution/RecordWriter.java:171-179`
- **证据片段**:
  ```java
  public void emitBarrier(CheckpointBarrier barrier) {
      for (ResultPartition partition : partitions) {
          try {
              partition.write(barrier);
          } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new StreamException(ERR_STREAM_INTERRUPTED_WRITE, e).param(ARG_DETAIL, "barrier");
              // ← 异常抛出时，前 K 个 partition 已收到 barrier，后 N-K 个没有
          }
      }
  }
  ```
- **严重程度**: P2
- **现状**: 如果向 partition[K] 写入 barrier 时线程被 interrupt，前 K 个 partition 已收到 barrier，后 N-K 个永远不会收到。K 个下游 subtask 开始 snapshot 并等待 ACK，N-K 个不参与 checkpoint。
- **风险**: 部分 subtask 的 snapshot 永远不会被 ACK → checkpoint 挂死。配合 AR-17（operatorsToAck 不重置），问题会级联。
- **建议**: 在 interrupt 时记录哪些 partition 已收到 barrier，向已收到的 partition 发送 abort 信号；或将 barrier 广播设计为 all-or-nothing 语义。
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

---

### [AR-32] MemoryKeyedStateBackend 同名状态不同类型描述符无报错 — ClassCastException 延迟到使用时

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java:106-114`
- **证据片段**:
  ```java
  public <T> ValueState<T> getState(ValueStateDescriptor<T> stateProperties) {
      @SuppressWarnings("unchecked")
      ValueState<T> state = (ValueState<T>) states.get(stateProperties.getName());
      if (state == null) {
          state = new MemoryValueState<>(this, stateProperties);
          states.put(stateProperties.getName(), state);
      }
      return state;  // 已存在时不检查类型是否匹配
  }
  ```
- **严重程度**: P2
- **现状**: 所有 `getXxxState()` 方法按 name 查找已存在的状态，如果存在就 `@SuppressWarnings("unchecked")` 直接强转返回。不检查描述符类型是否匹配。对比 Flink 在 `AbstractKeyedStateBackend` 中检查类型不匹配时抛出 `IllegalStateException`。
- **风险**: 用户代码先用 `ValueStateDescriptor("s", String.class)` 注册，再用 `ListStateDescriptor("s", Integer.class)` 访问，会得到一个类型错误的 state 对象。后续 `ClassCastException` 发生在使用时（如 `state.add(42)`），而非注册时，增加调试难度。
- **建议**: 在返回已存在的 state 前检查描述符类型是否一致，不一致时抛出异常。
- **信心水平**: 确定

---

### [AR-33] CheckpointCoordinator.failedCommitParticipants 无限增长 — 内存泄漏

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:52,430,437-467`
- **证据片段**:
  ```java
  private final ConcurrentSkipListMap<Long, Set<CheckpointParticipant>> failedCommitParticipants =
          new ConcurrentSkipListMap<>();                    // 无容量限制

  // 行 430: 每次 finishCommit 失败都添加
  failedCommitParticipants.computeIfAbsent(checkpointId, k -> ConcurrentHashMap.newKeySet())
          .add(participant);

  // retryFailedCommits() 只在全部重试成功时移除
  if (stillFailing.isEmpty()) {
      it.remove();        // 全部成功才移除
  } else {
      entry.setValue(stillFailing);  // 部分失败 → 保留
  }
  ```
- **严重程度**: P2
- **现状**: `failedCommitParticipants` 和 `checkpointSuccessMap` 只在 `retryFailedCommits()` 成功时清理。但 `retryFailedCommits()` 只在 `completePendingCheckpoint()` 中被调用——如果 checkpoint 持续失败，重试永远不会被触发。即使被触发，participant 持续失败时条目永远不会清除。没有基于时间的过期或最大条目数限制。
- **风险**: 长时间运行的作业中，如果下游 commit 目标（如 Kafka）永久不可用，这两个 Map 无限增长导致 OOM。
- **建议**: 添加最大条目数限制（如只保留最近 N 个 checkpoint 的失败记录），或基于时间的过期清理。
- **信心水平**: 很可能
- **发现来源视角**: 10x 规模运维者

---

### [AR-34] ChainingOutput side output 被静默丢弃 — 应在 pipeline 构建时报错

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/operators/ChainingOutput.java:82-84`
- **证据片段**:
  ```java
  @Override
  public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
      LOG.warn("Side output '{}' discarded in simplified chaining mode...", outputTag);
      // 数据被静默丢弃
  }
  ```
- **严重程度**: P2
- **现状**: Side output 数据在 chaining 模式下被静默丢弃，只记录 WARN 日志。如果应用依赖 side output（异常数据分流、延迟数据输出），会导致静默数据丢失。生产环境中日志级别可能设为 ERROR，完全无感知。
- **风险**: 依赖 side output 的应用逻辑静默失效。应在 pipeline 构建阶段就检测到不兼容并报错。
- **建议**: (1) 在 pipeline 构建时检测 chaining + side output 的不兼容并抛出异常；(2) 或支持 chaining 模式下的 side output 转发。
- **信心水平**: 确定
- **发现来源视角**: 新人开发者

---

### [AR-35] CheckpointCoordinator.shutdown() 清空重试记录 — 参与者资源泄漏

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:469-488`
- **证据片段**:
  ```java
  public void shutdown() {
      for (PendingCheckpoint pending : pendingCheckpoints.values()) {
          long checkpointId = pending.getCheckpointId();
          notifyParticipantsFinishCommit(checkpointId, false);  // 可能失败
          notifyCheckpointAborted(checkpointId);
          pending.dispose();
      }
      pendingCheckpoints.clear();
      // ...
      failedCommitParticipants.clear();   // 重试信息被清空
  }
  ```
- **严重程度**: P2
- **现状**: `shutdown()` 遍历 pending checkpoints 通知 participants 回滚，但立即清空 `failedCommitParticipants`。如果 commit 失败，参与者持有的资源（如事务句柄、文件锁）无法被追踪和清理。
- **风险**: 作业关闭时，未完成的事务或外部资源泄漏。
- **建议**: 在清空前记录所有未清理的 participants 到日志；或在清空前等待所有 participants 确认回滚。
- **信心水平**: 很可能

---

## 历史问题状态更新

以下之前报告的问题在本轮审查中确认**仍然存在**（简要引用）：

| # | 描述 | 状态 | 变化 |
|---|------|------|------|
| AR-2 | JdbcClusterRegistry.registerCoordinator 无 fencing token 校验 | 仍存在 | 无变化 |
| AR-3 | WindowOperator 非 SimpleAccumulator 路径覆盖写入 | 仍存在 | 无变化 |
| AR-4 | WindowOperator windowNamespace identityHashCode 不稳定 | 仍存在 | 无变化 |
| AR-5 | WindowOperator timer purge 不清除 trigger 状态 | 仍存在 | 无变化 |
| AR-6 | CepPatternBuilder 非起始 pattern where 条件未应用 | 仍存在 | 无变化 |
| AR-7 | SharedBuffer.advanceTime 不清理 backing state | 仍存在 | 无变化 |
| AR-8 | CepOperator 清除 NFA 状态产生孤立 SharedBuffer 条目 | 仍存在 | 无变化 |
| AR-10 | DebeziumCdcSourceFunction draining 模式入口是死代码 | 仍存在 | 无变化（AR-24 从不同角度报告了同一模块的另一个问题） |
| AR-11 | SourceEnumerator.assignSplits 要求顺序调用 | 仍存在 | 无变化 |
| AR-12 | JdbcCheckpointStorage INSERT-UPDATE 不检查 affected rows | 仍存在 | 无变化 |

---

## 总评

### 最值得关注的 3 个方向

1. **Checkpoint 管线正确性是一片"未发现过"的富矿**（AR-17/18/19/20/21/22）：6 个发现中有 5 个 P0，全部集中在 checkpoint 管线的端到端正确性——触发、snapshot、barrier 对齐、恢复、存储。这些不是边界情况，而是 checkpoint 管线的核心路径。之前的审查聚焦在 CEP/windowing 的算法正确性（AR-1~AR-16），而 checkpoint 管线的"从触发到恢复"全链路验证是本轮的主要盲区覆盖。最严重的是 AR-18（snapshot 异常被吞掉）和 AR-19（恢复后 counter 不推进），这两个问题使得"checkpoint 成功但恢复失败"成为默认行为。

2. **CEP operator 的容错能力缺失**（AR-21）：`registeredEventTimeTimers` 是唯一未被 checkpoint 管线管理的核心状态，意味着 CEP 模式的超时检测在 failover 后完全失效。与之前发现的 AR-7（SharedBuffer 内存泄漏）和 AR-8（NFA 状态清除）形成连锁效应：CEP job 在 checkpoint + failover 后，(1) timer 丢失 → partial match 不超时 → (2) SharedBuffer 不释放 → (3) 内存持续增长。

3. **Connector 层的契约声明与实现不匹配**（AR-24/29/30）：`DebeziumCdcSourceFunction` 声称 `REPLAYABLE` 但不实现 checkpoint 接口，`MessageSourceFunction` 的 `subscription` 缺少 `volatile`，`BatchConsumerSinkFunction` 的 flush 重试语义模糊。三个 connector 各有一个独立的契约违反，表明 connector 层缺少统一的集成测试策略。

### 本次审查的盲区自评

1. **之前审查的历史问题修复验证不完整**：虽然确认了 AR-2~AR-12 仍然存在，但没有逐行验证 AR-1（Lockable.release）和 AR-13~AR-16 的修复状态。
2. **序列化兼容性**：没有验证 checkpoint 数据在不同版本间的向前/向后兼容性。
3. **nop-stream-flow 模块**：仍然为空壳模块，无法审查。
4. **性能压测**：所有并发问题（AR-25/26/31）都是理论分析，没有实际压力测试验证。
5. **分布式 transport 层**：`RemoteGraphExecutionPlanBuilder` 和 RPC 传输层的错误恢复路径审查偏浅。

---

## 严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 5    | Checkpoint 管线正确性（触发拒绝、异常吞掉、counter 不推进、barrier ID 混合、CEP timer 丢失） |
| P1      | 5    | 恢复语义（双重存储、jobId 随机、CDC 无 checkpoint）、并发（TOCTOU、barrier aligner）、资源管理（task 假成功、lease 过期） |
| P2      | 5    | Connector 契约违反、设计健壮性（barrier 部分广播、状态类型检查、内存泄漏、side output 丢失、shutdown 资源） |
