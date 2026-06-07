# 维度 14+19+20：异步/事务 + 命名一致性 + 跨模块契约

## 第 1 轮（初审）

### [维度14-01] CheckpointCoordinator.stopCheckpointScheduler() 的 isSchedulerStarted 非原子读写

- **文件**: `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:146-163`
- **证据片段**:
  ```java
  // start 是 synchronized
  public synchronized void startCheckpointScheduler() { ... isSchedulerStarted = true; }
  // stop 不是 synchronized
  public void stopCheckpointScheduler() {
      if (!isSchedulerStarted || scheduler == null) { return; }
      ... isSchedulerStarted = false;
  }
  ```
- **严重程度**: P3
- **现状**: start/stop 不对称：start 是 synchronized，stop 不是。isSchedulerStarted 是 volatile 保证可见性，但 scheduler 引用和 isSchedulerStarted 的一致性不是原子的。
- **风险**: 并发 start/stop 可能出现 scheduler 在 start 过程中被 shutdown。
- **建议**: stop 也加 synchronized 或用 AtomicBoolean + CAS。
- **信心水平**: 很可能 (80%)
- **误报排除**: volatile 可见性已部分缓解，scheduler.shutdown() 是幂等的。
- **复核状态**: 未复核

### [维度14-05] TaskManager 的 taskExecutor 线程未设为 daemon

- **文件**: `nop-stream-runtime/.../taskmanager/TaskManager.java:105`
- **证据片段**:
  ```java
  this.taskExecutor = Executors.newFixedThreadPool(Math.max(1, capacity));
  // 对比 TaskExecutor.java 使用 daemon 线程
  ```
- **严重程度**: P2
- **现状**: 同模块其他线程池（TaskExecutor, CheckpointCoordinator, JobCoordinator）都显式设了 daemon，TaskManager 遗漏。
- **风险**: JVM 关闭时如果未显式调用 TaskManager.stop()，非 daemon 线程阻止 JVM 退出。
- **建议**: 提供自定义 ThreadFactory 设置 t.setDaemon(true)。
- **信心水平**: 确定 (95%)
- **误报排除**: 无。同模块其他线程池都设了 daemon，此处是明确遗漏。
- **复核状态**: 未复核

### [维度14-08] GraphModelCheckpointExecutor shutdown 顺序：先停任务再停 barrier

- **文件**: `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java:543-551`
- **证据片段**:
  ```java
  private static void shutdown(ScheduledExecutorService barrierScheduler,
          CheckpointCoordinator coordinator, TaskExecutor executor) {
      if (executor != null) { executor.shutdownNow(); }    // 先停任务
      if (barrierScheduler != null) { barrierScheduler.shutdownNow(); }  // 再停 barrier
  }
  ```
- **严重程度**: P3
- **现状**: 先停任务再停 barrier scheduler，中间可能有一个 barrier 注入周期。
- **风险**: 低。仅产生 WARN 日志，不会造成数据损坏。
- **建议**: 先停 barrier scheduler 再停 executor。
- **信心水平**: 很可能 (90%)
- **复核状态**: 未复核

### [维度20-03] BatchConsumerSinkFunction 声称 IDEMPOTENT 但无幂等实现

- **文件**: `nop-stream-connector/.../BatchConsumerSinkFunction.java:111-113`
- **证据片段**:
  ```java
  @Override
  public SinkConsistencyCapability getSinkConsistency() {
      return SinkConsistencyCapability.IDEMPOTENT;
  }
  ```
- **严重程度**: P2
- **现状**: 该类声明 IDEMPOTENT，但实际只是批量转发到 IBatchConsumerProvider。幂等性取决于底层 consumer 实现。
- **风险**: 如果框架基于 IDEMPOTENT 做优化决策（跳过两阶段提交、允许重放），而底层不幂等，会导致数据不一致。
- **建议**: 默认改为 AT_LEAST_ONCE，让具体实现声明其一致性能力。
- **信心水平**: 很可能 (85%)
- **误报排除**: 如果框架当前不基于此枚举做决策则风险降低，但公共 API 契约应准确。
- **复核状态**: 未复核

### [维度20-04] DrainableSource 接口已定义但运行时未使用

- **文件**: `nop-stream-core/.../connector/DrainableSource.java`
- **严重程度**: P3
- **现状**: DRAIN 模式下未调用 DrainableSource.truncateForDrain()。
- **风险**: DRAIN 模式下 CDC source 可能丢失数据。
- **建议**: 在 DRAIN 模式处理流程中识别并调用 DrainableSource。
- **信心水平**: 有趣的猜测 (75%)
- **误报排除**: 如果 invokable 内部已处理 drain 逻辑则不成立。
- **复核状态**: 未复核

### [维度20-05] CheckpointParticipant.restoreFromEpoch() 恢复路径缺失

- **文件**: `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java:397-435`
- **严重程度**: P2
- **现状**: restoreOperatorsFromState() 只调用 AbstractStreamOperator.restoreState()，未检查 CheckpointParticipant 并调用 restoreFromEpoch()。
- **风险**: TwoPhaseCommitSinkFunction 等实现了 CheckpointParticipant 的 operator 在恢复时不收到回调，pending transactions 不被回滚/恢复。
- **建议**: 在 restoreOperatorsFromState() 中添加 CheckpointParticipant 检查和 restoreFromEpoch() 调用。
- **信心水平**: 很可能 (80%)
- **误报排除**: 需确认 AbstractStreamOperator.restoreState() 是否内部处理了 participant 恢复。
- **复核状态**: 未复核

## 已排除项

- 14-02 (tasksToAcknowledge 竞态): setTasksToAcknowledge 主要在初始化时调用一次，运行时竞态风险低。
- 14-03 (TOCTOU 竞态): CAS 保护已防止重复完成。
- 14-04 (newFixedThreadPool 无界队列): 流处理任务数固定，不会触发。
- 14-06 (LRU 清理非线程安全): ConcurrentHashMap 弱一致性在此场景可接受。
- 14-07 (TwoPhaseCommit 锁排序): 恢复和提交生命周期不重叠。
- 19-01 (错误码前缀): ERR_STREAM_* vs ERR_CEP_* 分离清晰，无问题。
- 19-04 (一致性能力枚举): Source/Sink 一致性模型本质上不同，设计合理。

## 维度复核结论

待复核。

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 14-01 | P3 | CheckpointCoordinator.java | start/stop 不对称 |
| 14-05 | P2 | TaskManager.java | taskExecutor 非 daemon 线程 |
| 14-08 | P3 | GraphModelCheckpointExecutor.java | shutdown 顺序 |
| 20-03 | P2 | BatchConsumerSinkFunction.java | IDEMPOTENT 声明不准确 |
| 20-04 | P3 | DrainableSource + GraphModelCheckpointExecutor | DRAIN 模式未使用接口 |
| 20-05 | P2 | GraphModelCheckpointExecutor.java | restoreFromEpoch 路径缺失 |
