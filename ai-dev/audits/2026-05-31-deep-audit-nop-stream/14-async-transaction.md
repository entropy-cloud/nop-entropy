# 维度 14：异步与事务模式

## 第 1 轮（初审）

### [维度14-01] CheckpointCoordinator.registerTask/unregisterTask 检查-然后-操作竞态

- **文件**: `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:354-372`
- **证据片段**:
  ```java
  public void registerTask(TaskLocation taskLocation) {
      Set<TaskLocation> current = this.tasksToAcknowledge;
      if (!current.contains(taskLocation)) {
          Set<TaskLocation> newSet = ConcurrentHashMap.newKeySet();
          newSet.addAll(current);
          newSet.add(taskLocation);
          this.tasksToAcknowledge = newSet;
      }
  }
  ```
- **严重程度**: P1
- **现状**: Copy-on-write 模式通过 volatile 保证可见性，但 read-check-write 序列不是原子的。两个并发 registerTask() 调用可各自读取相同 current，各自创建只包含自己添加项的新 set。
- **风险**: 任务丢失 ACK 目标，checkpoint 永远无法完成。
- **建议**: 使用 synchronized 或 ReentrantLock 保护 read-check-write 序列。
- **信心水平**: 很可能
- **误报排除**: volatile 写只保证单次写入可见性，不保证复合操作原子性。
- **复核状态**: 未复核

### [维度14-02] CheckpointCoordinator.currentFingerprint 缺少 volatile

- **文件**: `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:520-527`
- **证据片段**:
  ```java
  private StreamModelFingerprint currentFingerprint;
  ```
- **严重程度**: P1
- **现状**: currentFingerprint 无 volatile 修饰，由 setup 阶段写入，checkpoint 调度线程读取，缺少 happens-before 保证。
- **风险**: 调度线程可能永远看到 null fingerprint，影响恢复时的兼容性检查。
- **建议**: 声明为 volatile。同类中 latestCompletedCheckpoint 和 tasksToAcknowledge 都正确使用了 volatile。
- **信心水平**: 确定
- **误报排除**: 明确的跨线程写入和读取，无 happens-before 保证。
- **复核状态**: 未复核

### [维度14-03] PendingCheckpoint 混合同步模型 -- acknowledgeTask vs. abort/dispose 竞态

- **文件**: `nop-stream-runtime/.../checkpoint/PendingCheckpoint.java:113-183`
- **证据片段**:
  ```java
  public synchronized void acknowledgeTask(...) {
      if (isDisposed || status.get() != Status.RUNNING) return;
      // modify state
  }
  public void abort(String reason, Throwable cause) {
      if (status.compareAndSet(Status.RUNNING, Status.ABORTED)) {
          isDisposed = true;  // No synchronization
      }
  }
  ```
- **严重程度**: P1
- **现状**: acknowledgeTask() 是 synchronized，检查 isDisposed 标志。但 abort()/dispose() 修改 isDisposed 时不在 synchronized 块内。
- **风险**: 线程 A 在 acknowledgeTask() 内通过 isDisposed 检查后，被线程 B 的 dispose() 抢先清空 taskStates。线程 A 继续操作已被清空的集合。
- **建议**: 统一同步模型——所有状态修改方法都使用 synchronized 或都用 AtomicReference + CAS。
- **信心水平**: 很可能
- **误报排除**: isDisposed 既不是 volatile 也不在 synchronized 块内写入。
- **复核状态**: 未复核

### [维度14-04] TaskManager.taskExecutor 使用非 daemon 线程

- **文件**: `nop-stream-runtime/.../taskmanager/TaskManager.java:105`
- **证据片段**:
  ```java
  this.taskExecutor = Executors.newFixedThreadPool(Math.max(1, capacity));
  ```
- **严重程度**: P2
- **现状**: 无自定义 ThreadFactory，线程不是 daemon。对比同类 heartbeatExecutor 正确设置了 daemon=true。
- **风险**: 挂起的 task 阻止 JVM 正常退出。
- **建议**: 使用自定义 ThreadFactory 创建 daemon 线程。
- **信心水平**: 确定
- **误报排除**: 同文件 heartbeatExecutor 和 core.TaskExecutor 都使用 daemon 线程。
- **复核状态**: 未复核

### [维度14-05] MessageSourceFunction.subscription 缺少 volatile

- **文件**: `nop-stream-connector/.../connector/MessageSourceFunction.java:49`
- **证据片段**:
  ```java
  private IMessageSubscription subscription;  // NOT volatile
  ```
- **严重程度**: P2
- **现状**: subscription 字段非 volatile。run() 在一个线程写入，cancel() 从另一个线程读取。
- **风险**: cancel() 线程可能看到 null（缓存的旧值），subscription 未被取消。
- **建议**: 声明为 volatile。
- **信心水平**: 很可能
- **误报排除**: streaming 框架中 cancel() 几乎总是在与 run() 不同的线程上调用。
- **复核状态**: 未复核

### [维度14-06] JobCoordinator.globalRecovery() 未同步 -- 双重恢复风险

- **文件**: `nop-stream-runtime/.../coordinator/JobCoordinator.java:393-439`
- **严重程度**: P2
- **现状**: globalRecovery() 没有同步保护，可能被 detectFailures() 和外部 API 并发触发。
- **建议**: 标记 synchronized 或使用 AtomicBoolean guard。
- **信心水平**: 很可能
- **误报排除**: failureDetector 调度线程每 5 秒运行，可能在上一次恢复未完成时再次触发。
- **复核状态**: 未复核

### [维度14-07] TwoPhaseCommitSinkFunction commit-then-remove 间隙

- **文件**: `nop-stream-core/.../functions/sink/TwoPhaseCommitSinkFunction.java:83-108`
- **严重程度**: P2
- **现状**: commit(eid) 在 synchronized 块外执行。JVM 崩溃在 commit 成功后、remove 前会导致重启后重复提交。
- **建议**: 考虑使用事务性外部存储追踪已提交 epoch。
- **信心水平**: 很可能
- **误报排除**: 两阶段提交的经典问题。
- **复核状态**: 未复核

### [维度14-08] Lockable.hashCode/equals 基于可变 AtomicInteger

- **文件**: `nop-stream-cep/.../nfa/sharedbuffer/Lockable.java:79-93`
- **严重程度**: P2
- **现状**: equals/hashCode 依赖 refCounter.get() 当前值。如果用作 HashMap key，lock()/release() 会导致 hashCode 变化。
- **建议**: 从 hashCode() 中排除 refCounter。
- **信心水平**: 很可能
- **误报排除**: 当前 Lockable 未被用作 HashMap key，但是 latent defect。
- **复核状态**: 未复核

### [维度14-09] CheckpointCoordinator.checkpointSuccessMap 无界增长

- **文件**: `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:53`
- **严重程度**: P3
- **现状**: checkpointSuccessMap 不断添加条目，成功的 epoch 永远不被清理。
- **建议**: 在 completePendingCheckpoint() 成功后移除已完成 checkpoint ID。
- **信心水平**: 确定
- **误报排除**: 代码中只有 retryFailedCommits() 会清理，但成功的 epoch 从不进入 failedCommitParticipants。
- **复核状态**: 未复核
