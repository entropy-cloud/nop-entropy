# 维度 14：异步与事务模式

## 第 1 轮（初审）

### [维度14-01] PendingCheckpoint.acknowledgeTask 竞态导致快照数据不完整

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/PendingCheckpoint.java:103-117`
- **证据片段**:
  ```java
  public void acknowledgeTask(TaskLocation taskLocation, TaskStateSnapshot state) {
      if (isDisposed) { return; }
      notYetAcknowledgedTasks.remove(taskLocation);       // 步骤A: 先移除
      if (state != null) {
          taskStates.put(taskLocation, state);              // 步骤B: 后写入
      }
      if (isFullyAcknowledged() && !completableFuture.isDone()) {
          CompletedCheckpoint completed = toCompletedCheckpoint();  // 步骤C: 构造快照
          completableFuture.complete(completed);
      }
  }
  ```
- **严重程度**: P0
- **现状**: `acknowledgeTask` 中步骤 A（remove）先于步骤 B（put）执行。当多个 task 并发 ACK 时，一个线程的 remove 可使 `notYetAcknowledgedTasks` 变为空，而其他线程的 `taskStates.put()` 尚未完成，导致 `toCompletedCheckpoint()` 拷贝到一个缺少部分 task 状态的 Map。
- **风险**: 持久化的 checkpoint 快照可能缺少一个或多个 task 的状态。恢复时使用不完整的快照会导致数据丢失，在 exactly-once 语义下这是正确性缺陷。
- **建议**: 将 `notYetAcknowledgedTasks.remove(taskLocation)` 移至 `taskStates.put()` 之后，或对整个方法加 `synchronized`。
- **误报排除**: 已有对抗性审查列表中无此发现。可构造并发场景导致快照丢数据。
- **复核状态**: 未复核

---

### [维度14-02] CheckpointCoordinator.failedCommitParticipants 使用非线程安全 TreeMap

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:50,369-419`
- **证据片段**:
  ```java
  private final TreeMap<Long, Set<Integer>> failedCommitParticipants = new TreeMap<>();
  
  // 写入路径：
  failedCommitParticipants.computeIfAbsent(checkpointId, k -> new TreeSet<>()).add(i);
  
  // 遍历+删除路径：
  Iterator<Map.Entry<Long, Set<Integer>>> it = failedCommitParticipants.entrySet().iterator();
  ```
- **严重程度**: P1
- **现状**: `failedCommitParticipants` 是普通 `TreeMap`，可由不同 checkpoint 的 CompletableFuture 回调在不同线程并发访问。
- **风险**: 高并发 checkpoint 完成时可能抛出 `ConcurrentModificationException`。
- **建议**: 改为 `ConcurrentSkipListMap` 或使用 `synchronized` 块。
- **误报排除**: `TreeMap` 的 `computeIfAbsent` + 迭代器并发访问是经典的并发安全缺陷。
- **复核状态**: 未复核

---

### [维度14-03] completePendingCheckpoint 与 abortPendingCheckpoint 的竞态导致已持久化快照被标记为失败

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:195-266`
- **证据片段**:
  ```java
  // completePendingCheckpoint：
  checkpointStorage.storeCheckPoint(completed);   // ← 已持久化
  if (!pendingCheckpoints.remove(checkpointId, pending)) { return; }
  
  // abortPendingCheckpoint：
  PendingCheckpoint removed = pendingCheckpoints.remove(checkpointId);  // 无条件移除
  ```
- **严重程度**: P1
- **现状**: 超时调度器线程可在 `completePendingCheckpoint` 执行期间调用 `abortPendingCheckpoint`，导致存储中存在"孤立的"已完成 checkpoint，其关联的两阶段提交事务已被回滚。
- **风险**: 恢复时可能从孤立 checkpoint 恢复，但参与者的事务已回滚，导致 exactly-once 语义被破坏。
- **建议**: 在 `storeCheckPoint` 之前先通过 `pendingCheckpoints.remove` 原子占位，或将 complete/abort 入口用锁串行化。
- **误报排除**: 两个异步路径之间的结构性竞态，可构造具体时序复现。
- **复核状态**: 未复核

---

### [维度14-04] TaskManager.stop() 不等待任务线程终止

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:133-149`
- **证据片段**:
  ```java
  public void stop() {
      running = false;
      heartbeatExecutor.shutdownNow();
      taskExecutor.shutdownNow();             // 仅发送中断信号
      for (Map.Entry<String, RunningTask> entry : runningTasks.entrySet()) {
          entry.getValue().cancel();
      }
      runningTasks.clear();                   // 清空追踪，但任务线程仍在运行
  }
  ```
- **严重程度**: P2
- **现状**: `stop()` 调用 `shutdownNow()` 后未调用 `awaitTermination()`。
- **风险**: 在嵌入式场景中，旧任务线程仍在运行，可能导致资源冲突。
- **建议**: 添加 `awaitTermination()` 调用（如 5 秒超时）。
- **误报排除**: N58 是关于 core 层 TaskExecutor，本发现是关于 runtime 层 TaskManager 的 `stop()` 缺少 `awaitTermination()`。
- **复核状态**: 未复核

---

### [维度14-05] TaskManager.RunningTask.waitForInvokable 使用忙等待轮询

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:432-442`
- **证据片段**:
  ```java
  private StreamTaskInvokable waitForInvokable() throws InterruptedException {
      long deadline = System.currentTimeMillis() + 30_000;
      while (invokable == null && !canceled) {
          if (System.currentTimeMillis() > deadline) { return null; }
          Thread.sleep(100);   // 每 100ms 轮询一次
      }
      return invokable;
  }
  ```
- **严重程度**: P2
- **现状**: 使用 `Thread.sleep(100)` 轮询等待 `invokable` 被设置，最长等待 30 秒。
- **风险**: 在高并发任务分配场景下，线程池中多个线程同时忙等待，浪费 CPU 周期和线程资源。
- **建议**: 使用 `CountDownLatch` 或 `CompletableFuture` 替代忙等待。
- **误报排除**: 线程池是稀缺资源，每个线程被无意义占用 30 秒会直接影响系统吞吐。
- **复核状态**: 未复核

---

### [维度14-06] CheckpointBarrierTracker.acknowledgeOperator 缺少同步保护

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/execution/CheckpointBarrierTracker.java:88-114`
- **证据片段**:
  ```java
  // triggerCheckpoint 是 synchronized 的
  public synchronized boolean triggerCheckpoint(...) { ... }
  
  // acknowledgeOperator 不是 synchronized 的
  public void acknowledgeOperator(int operatorIndex, OperatorSnapshotResult snapshot) {
      TaskStateSnapshot snap = this.currentSnapshot;
      snap.putOperatorState(...);   // 修改 HashMap（非线程安全）
      if (operatorsToAck.decrementAndGet() <= 0) {
          completionCallback.accept(snap);
      }
  }
  ```
- **严重程度**: P2
- **现状**: `triggerCheckpoint` 是 `synchronized` 的，但 `acknowledgeOperator` 不是。当前单线程执行安全，但如果未来引入并发快照会导致 HashMap 并发修改。
- **风险**: 框架级别的不变量违反——两个方法操作同一组共享状态却使用不同的同步策略。
- **建议**: 将 `acknowledgeOperator` 也标记为 `synchronized`。
- **误报排除**: 不是已有问题 N42-N103 中的任何一项。
- **复核状态**: 未复核

---

### [维度14-07] RemoteInputChannel.close() 与 EnvelopeConsumer.onMessage 之间的竞态

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteInputChannel.java:149-158,196-204`
- **证据片段**:
  ```java
  public void close() {
      subscription.cancel();
      if (!finished) {
          finished = true;
          queue.offer(END_OF_STREAM);
      }
  }
  
  // onMessage 也放入 END_OF_STREAM，竞态时可能产生两个
  ```
- **严重程度**: P2
- **现状**: `close()` 和 `onMessage` 可能同时放入 `END_OF_STREAM`。
- **风险**: 竞态的结果是良性的（多余的 END_OF_STREAM），但不确定的关闭行为会导致测试不稳定。
- **建议**: 在 `onMessage` 中加入 `finished` 检查作为快速失败路径。
- **误报排除**: 通道关闭的确定性是测试可靠性的前提。
- **复核状态**: 未复核

---

## 深挖第 2 轮追加

### [维度14-08] GraphModelCheckpointExecutor 在并行模式下仅注册第一个子任务的 BarrierTracker (P0)
- registerTasksAndTrackers 使用遗留的 getInvokables() 而非 getSubtasks()
- parallelism > 1 时只有 subtask-0 参与 checkpoint，其余 subtask 从未注册

### [维度14-09] InputGate.readMultiChannel finished channel 导致 barriersRemaining 负值 (P1)
- 上游 task 提前关闭时 barrier 对齐逻辑进入不可恢复状态

### [维度14-10] ResultPartition.write() 与 close() 竞态导致数据丢失 (P1)
- write() 的 finished 检查与 close() 的 finished 设置之间无同步

### [维度14-11] GraphModelCheckpointExecutor TaskExecutor 线程池资源未在异常路径关闭 (P2)
- 每次 executeWithCheckpoint 都创建但未 shutdown TaskExecutor 线程池

### [维度14-12] failedCommitParticipants TreeMap 并发访问（与14-02同源，补充调用链证据）(P2)
- acknowledgeTask 的 CompletableFuture 回调可从多线程触发

### [维度14-13] TaskStateSnapshot 使用非线程安全 HashMap (P2)
- CheckpointBarrierTracker.acknowledgeOperator 可并发修改同一个 TaskStateSnapshot

深挖结束，共新增 6 个发现（P0×1, P1×2, P2×3）。

---

## 维度复核结论

| 编号 | 复核结论 | 理由 |
|------|---------|------|
| 14-01 | **驳回** | ConcurrentHashMap 支持 + volatile + CompletableFuture 原子性，无可利用竞态 |
| 14-02 | **保留 P1** | TreeMap 从多线程访问，真实并发错误 |
| 14-03 | **降级至 P3** | ConcurrentHashMap.remove 原子性实际序列化操作，轻微设计异味 |
| 14-04 | **保留 P2** | shutdownNow() 无 awaitTermination() |
| 14-05 | **保留 P2** | 忙等待应改为 CountDownLatch |
| 14-06 | **保留 P2** | 非同步方法写入共享 TaskStateSnapshot |
| 14-07 | **保留 P2** | close/onMessage 双重 END_OF_STREAM 竞态 |
| 14-08 | **保留 P0** | getInvokables() 仅含第一个 subtask，并行模式 checkpoint 完全损坏 |
| 14-09 | **降级至 P2** | 仅影响短生命周期通道，P1 过高 |
| 14-10 | **降级至 P3** | 单生产者契约下 write/close 应同线程调用 |
| 14-11 | **降级至 P3** | 仅影响长时间嵌入式场景 |
| 14-12 | **驳回** | 与 14-02 完全重复 |
| 14-13 | **保留 P2** | HashMap 并发写入风险 |
