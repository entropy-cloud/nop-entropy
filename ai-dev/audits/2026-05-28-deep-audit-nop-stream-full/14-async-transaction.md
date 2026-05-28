# 维度 14：异步与事务模式

## 第 1 轮（初审）

### [维度14-01] GraphModelCheckpointExecutor 未关闭 TaskExecutor，线程池泄漏

- **文件**: `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java:81-94,537-542`
- **证据片段**:
  ```java
  // L81-94
  TaskExecutor executor = new TaskExecutor();
  try {
      submitAndRun(execPlan, tasks, executor);
  } finally {
      shutdown(barrierScheduler, coordinator);  // executor 未被关闭!
  }
  // L537-542
  private static void shutdown(ScheduledExecutorService barrierScheduler,
          CheckpointCoordinator coordinator) {
      if (barrierScheduler != null) { barrierScheduler.shutdownNow(); }
      coordinator.shutdown();
      // 缺少: executor.shutdown()
  }
  ```
- **严重程度**: P1
- **现状**: 每个 execute 方法创建 new TaskExecutor() 但 shutdown() 未关闭它。TaskExecutor 内部使用 Executors.newFixedThreadPool 且非 daemon 线程。
- **风险**: 每次调用 executeWithCheckpoint/triggerSavepoint/executeWithSavepoint 都创建线程池且永不关闭。多次调用导致线程和内存累积泄漏。JVM 可能因非 daemon 线程无法退出。
- **建议**: 将 executor 传入 shutdown 方法，或在 finally 块中直接调用 executor.shutdown()。
- **误报排除**: 不是误报。资源泄漏路径已通过代码分析确认。
- **复核状态**: 未复核

### [维度14-02] CheckpointCoordinator.startCheckpointScheduler() 竞态条件

- **文件**: `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:94-125`
- **证据片段**:
  ```java
  public void startCheckpointScheduler() {
      if (isSchedulerStarted) { return; }  // volatile boolean，非原子检查
      // ...
      scheduler = Executors.newSingleThreadScheduledExecutor(...);
      // ...
      isSchedulerStarted = true;
  }
  ```
- **严重程度**: P1
- **现状**: 使用 volatile boolean 做 check-then-act，但检查和赋值之间无原子性保证。两个线程并发调用可能创建两个 scheduler。
- **风险**: 第一个 scheduler 被覆盖后泄漏，调度任务重复执行。
- **建议**: 使用 synchronized 方法或 AtomicBoolean.compareAndSet() 保护整个操作。
- **误报排除**: 不是误报。并发调用场景在实际部署中可能出现。
- **复核状态**: 未复核

### [维度14-03] PendingCheckpoint.acknowledgeTask() CompletableFuture 完成竞态

- **文件**: `nop-stream-runtime/.../checkpoint/PendingCheckpoint.java:104-118`
- **证据片段**:
  ```java
  public void acknowledgeTask(TaskLocation taskLocation, TaskStateSnapshot state) {
      if (isDisposed) { return; }
      notYetAcknowledgedTasks.remove(taskLocation);
      if (state != null) { taskStates.put(taskLocation, state); }
      if (isFullyAcknowledged() && !completableFuture.isDone()) {
          CompletedCheckpoint completed = toCompletedCheckpoint(); // 可能被两个线程都执行
          completableFuture.complete(completed);
      }
  }
  ```
- **严重程度**: P1
- **现状**: 两个线程可能同时看到 isFullyAcknowledged() == true 并都执行 toCompletedCheckpoint()。
- **风险**: toCompletedCheckpoint() 构建 new HashMap<>(taskStates)，可能导致快照不一致。
- **建议**: 将 acknowledgeTask 方法加 synchronized，或使用 CAS 模式在完成后再构建 CompletedCheckpoint。
- **误报排除**: 实际风险较低因为所有任务都已 ACK，但代码缺乏明确的同步保证。
- **复核状态**: 未复核

### [维度14-04] CheckpointCoordinator.completePendingCheckpoint() 与超时 abort 非互斥

- **文件**: `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:196-246`
- **证据片段**:
  ```java
  public void completePendingCheckpoint(CompletedCheckpoint completed) {
      try {
          checkpointStorage.storeCheckPoint(completed);  // 耗时操作
      } catch (Exception e) { abortPendingCheckpoint(...); return; }
      // 此处超时可能触发 abortPendingCheckpoint
      try {
          checkpointStorage.storeEpochManifest(...);  // 又一个耗时操作
      } catch (Exception e) { abortPendingCheckpoint(...); return; }
      if (!pendingCheckpoints.remove(checkpointId, pending)) { return; }
      latestCompletedCheckpoint = completed;
      // 已持久化的数据不会被 abort 回滚
  }
  ```
- **严重程度**: P1
- **现状**: storeCheckPoint 成功后、pendingCheckpoints.remove 之前可能发生超时 abort。已持久化的数据不会被回滚。
- **风险**: abort 和 complete 同时执行时，checkpoint 数据可能处于不一致状态。
- **建议**: 引入显式状态机（AtomicReference<Status>），确保 abort 和 complete 互斥。abort 时应尝试清理已持久化的数据。
- **误报排除**: 不是误报。两步持久化之间有时间窗口。
- **复核状态**: 未复核

### [维度14-05] TaskExecutor 线程池未设置 daemon 线程

- **文件**: `nop-stream-core/.../execution/TaskExecutor.java:112`
- **证据片段**:
  ```java
  this.executorService = Executors.newFixedThreadPool(poolSize);
  ```
- **严重程度**: P2
- **现状**: 使用默认 ThreadFactory，创建的线程非 daemon。对比 CheckpointCoordinator、JobCoordinator、TaskManager 中都正确设置了 t.setDaemon(true)。
- **风险**: 若 TaskExecutor 未被显式 shutdown（如异常路径），JVM 因非守护线程无法退出。
- **建议**: 使用自定义 ThreadFactory，设置 t.setDaemon(true)。
- **误报排除**: 与发现 14-01 关联但独立问题（daemon 属性 vs 未关闭）。
- **复核状态**: 未复核

### [维度14-06] MemoryKeyedStateBackend 完全非线程安全

- **文件**: `nop-stream-core/.../state/backend/memory/MemoryKeyedStateBackend.java:85-91,96`
- **证据片段**:
  ```java
  private transient K currentKey;              // 非 volatile
  private transient Object currentNamespace;   // 非 volatile
  private final Map<String, Object> states = new HashMap<>();  // 非 concurrent
  ```
- **严重程度**: P2
- **现状**: currentKey/currentNamespace 是 transient 非 volatile 字段，states 使用普通 HashMap。无同步机制。
- **风险**: 当前使用模式（单任务单线程 + checkpoint 在 barrier 后执行）下风险有限。但未来引入异步快照将直接导致数据损坏。
- **建议**: 在类 Javadoc 中明确标注 "Not thread-safe. Designed for single-threaded access per task."
- **误报排除**: 当前使用模式下不会触发。P2 因为维护风险存在。
- **复核状态**: 未复核

### [维度14-07] CompletedCheckpoint.getTaskStates() 返回可变内部 Map

- **文件**: `nop-stream-core/.../checkpoint/CompletedCheckpoint.java:73-75`
- **证据片段**:
  ```java
  public Map<TaskLocation, TaskStateSnapshot> getTaskStates() {
      return taskStates;  // 直接返回可变引用
  }
  ```
- **严重程度**: P2
- **现状**: 返回内部 Map 的可变引用，调用方可以修改内部状态。CompletedCheckpoint 标记为 @DataBean。
- **风险**: 缺少防御性保护。如调用方修改 Map 影响后续操作。
- **建议**: 返回 Collections.unmodifiableMap(taskStates)。
- **误报排除**: 当前代码中无调用方修改此 Map，但缺少保护。
- **复核状态**: 未复核

## 信息性确认

- CheckpointBarrierTracker 混用 synchronized + AtomicInteger：冗余但正确
- BarrierAligner 锁外检查 closed：快速路径优化，微小效率问题
- TaskExecutor.submitJobVertex isShutdown 检查非原子：当前单线程使用，风险有限
- InputGate 无同步保护：单消费者设计，需文档标注
- JobCoordinator start/stop 竞态：实际单次启动，但缺少防御
- CheckpointCoordinator.currentFingerprint 非 volatile：当前使用模式下安全
