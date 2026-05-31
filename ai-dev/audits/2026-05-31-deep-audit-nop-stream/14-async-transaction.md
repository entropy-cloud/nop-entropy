# 维度 14：异步与事务模式

## 第 1 轮（初审）

### [维度14-01] CheckpointCoordinator registerTask/unregisterTask 非原子"复制后写入"竞态

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:354-372`
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
- **现状**: volatile Set 用"读取-检查-复制-写入"实现，非原子。两线程同时调用可能丢失注册。
- **风险**: checkpoint 永远无法完成（等不到已丢失任务的 ACK）。
- **建议**: 使用 synchronized 保护 registerTask/unregisterTask，或改用 ConcurrentHashMap 直接 putIfAbsent。
- **信心水平**: 很可能
- **误报排除**: 代码已暴露这些方法为公共 API，分布式模式下 TaskManager 上下线是可能的。
- **复核状态**: 未复核

### [维度14-02] PendingCheckpoint 混合同步策略

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/PendingCheckpoint.java:39-40, 113-129, 163-168`
- **证据片段**:
  ```java
  private final AtomicReference<Status> status;
  private volatile boolean isDisposed = false;
  public synchronized void acknowledgeTask(...) { ... }
  public boolean forceComplete() { ... }  // NOT synchronized
  ```
- **严重程度**: P1
- **现状**: acknowledgeTask 和 abort 是 synchronized，但 forceComplete 不是。CheckpointCoordinator 通过外部 compareAndSet 绕过 PendingCheckpoint 内部锁。
- **风险**: forceComplete 可能与 abort 并发执行，导致 CompletableFuture 在已 abort 后被 complete。
- **建议**: 统一为 synchronized 方法或完全使用 AtomicReference + CAS 一致策略。
- **信心水平**: 很可能
- **误报排除**: timeout scheduler 的 abort 和正常 ACK 路径的 complete 可能并发。
- **复核状态**: 未复核

### [维度14-03] TwoPhaseCommitSinkFunction finishCommit 锁定可被外部替换的 Map 引用

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:61-63, 80-108`
- **证据片段**:
  ```java
  public void setPendingCommits(Map<Long, Object> pending) {
      this.pendingCommits = pending;
  }
  public void finishCommit(long epochId, boolean success) throws Exception {
      Map<Long, Object> pending = getPendingCommits();
      synchronized (pending) { ... }
  }
  ```
- **严重程度**: P1
- **现状**: finishCommit 先读取 pendingCommits 引用再对其加锁，但 setPendingCommits 可替换引用。
- **风险**: 提交的事务可能丢失或双重提交。
- **建议**: synchronized 加在 this 上，或使用 volatile + 不可变 Map。
- **信心水平**: 很可能
- **误报排除**: 代码未保证 setPendingCommits 和 finishCommit 不并发。
- **复核状态**: 未复核

### [维度14-04] TaskManager 线程池非 daemon 线程，无命名前缀

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:105`
- **证据片段**:
  ```java
  this.taskExecutor = Executors.newFixedThreadPool(Math.max(1, capacity));
  ```
- **严重程度**: P2
- **现状**: 使用默认 ThreadFactory，非 daemon 线程，无意义线程名。同文件 heartbeatExecutor 正确使用自定义 ThreadFactory。
- **风险**: JVM 退出阻塞，调试困难。
- **建议**: 与 heartbeatExecutor 一致使用自定义 ThreadFactory。
- **信心水平**: 确定
- **误报排除**: stop() 调用了 shutdownNow 但超时后不强制终止。
- **复核状态**: 未复核

### [维度14-05] MessageSourceFunction transient CountDownLatch 反序列化后竞态

- **文件**: `nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:50, 104-107, 130-133`
- **严重程度**: P2
- **现状**: shutdownLatch 标记为 transient，反序列化后为 null。cancel() 在 run() 之前调用时 countDown 不会执行。
- **风险**: 恢复场景中 source 线程可能永远阻塞。
- **建议**: 在 cancel() 中也加 null 检查并重新初始化。
- **信心水平**: 很可能
- **误报排除**: 典型使用模式是先 run 再 cancel。
- **复核状态**: 未复核

### [维度14-06] Lockable equals/hashCode 使用可变 AtomicInteger

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/Lockable.java:44-46, 54-63, 79-93`
- **严重程度**: P2
- **现状**: hashCode 和 equals 依赖 refCounter.get()，但 lock()/release() 会修改 refCounter。Lockable 被存储在 ConcurrentHashMap 中作为 value。
- **风险**: 当前 Lockable 是 value 而非 key，不影响 map 查找。但如果用作 Set 元素或 key 会违反 hash 不变量。
- **建议**: 从 hashCode 中移除 refCounter，只基于 element 计算。
- **信心水平**: 很可能
- **误报排除**: 当前代码中未发现将 Lockable 用作 key 的场景。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 14-01 | P1 | CheckpointCoordinator.java | registerTask 非原子竞态 |
| 14-02 | P1 | PendingCheckpoint.java | 混合同步策略 |
| 14-03 | P1 | TwoPhaseCommitSinkFunction.java | finishCommit 锁可被替换引用 |
| 14-04 | P2 | TaskManager.java | 线程池非 daemon 无命名 |
| 14-05 | P2 | MessageSourceFunction.java | transient CountDownLatch 竞态 |
| 14-06 | P2 | Lockable.java | equals/hashCode 依赖可变状态 |
