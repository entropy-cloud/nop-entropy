# 维度 16：测试覆盖与质量

## 第 1 轮（初审）

### [维度16-01] EmbeddedDistributedExecutor 仅有 1 个单 happy-path 测试

- **文件**: `nop-stream-runtime/src/test/java/io/nop/stream/runtime/execution/TestEmbeddedDistributedExecution.java:全文1-74行`
- **证据片段**:
  ```java
  // lines 19-25 (注释说明)
  // Note: the current test uses InProcessMessageService which delivers messages
  // synchronously within the caller's thread. This test validates the wiring and
  // data flow of the distributed execution path, but does not verify true
  // concurrent/multi-process execution semantics.
  ```
- **严重程度**: P1
- **现状**: 201 行生产代码仅有 1 个 happy-path 测试方法。任务分配失败、超时、checkpoint 恢复等路径未覆盖。
- **风险**: 分布式执行路径的核心异常场景完全无测试保护。
- **建议**: 补充 TaskManager 启动失败、任务超时、部分任务失败等场景测试。
- **信心水平**: 高（95%）
- **误报排除**: 仅有 1 个测试方法无法提供足够保护。
- **复核状态**: 未复核

### [维度16-02] GraphModelCheckpointExecutor 测试仅验证配置分发

- **文件**: `nop-stream-runtime/src/test/java/io/nop/stream/runtime/execution/TestGraphModelCheckpointExecutor.java:全文1-158行`
- **证据片段**:
  ```java
  // lines 80-89
  @Test
  void testHandleJobTerminationDrainModeConfig() {
      CheckpointConfig config = CheckpointConfig.builder()
              .jobTerminationMode(JobTerminationMode.DRAIN)
              .checkpointEnabled(true)
              .build();
      assertEquals(JobTerminationMode.DRAIN, config.getJobTerminationMode());
  }
  ```
- **严重程度**: P2
- **现状**: 8 个测试方法中 5 个测试 CheckpointConfig getter/setter，而非执行路径。
- **风险**: handleJobTermination 和 restoreFromSavepointPath 的实际行为未验证。
- **建议**: 补充通过间接但更有效的方式测试实际执行路径。
- **信心水平**: 高（90%）
- **误报排除**: buildSnapshotFromTaskState 在 TestE2EBuildSnapshotFromTaskState 中有专门测试。
- **复核状态**: 未复核

### [维度16-03] nop-stream-runtime 错误路径测试比例过低

- **文件**: `nop-stream-runtime/src/test/java/` 模块级别
- **证据**: nop-stream-runtime: 362 test methods, 仅 25 个 assertThrows（6.9%）。对比 nop-stream-core: 12.3%。
- **严重程度**: P1
- **现状**: 大部分测试只覆盖 happy path。缺失 JobCoordinator.assignTasks 容量不足、CheckpointCoordinator store 失败重试、TaskManager.receiveAssignment 异常传播等错误路径。
- **风险**: checkpoint 和分布式执行是最容易出错的区域，错误路径未覆盖可能导致生产环境不可预测行为。
- **建议**: 对关键模块补充错误路径测试。
- **信心水平**: 高（90%）
- **误报排除**: CheckpointCoordinator 已有部分错误路径测试。
- **复核状态**: 未复核

### [维度16-04] 测试中广泛使用 Thread.sleep（51 处）导致脆弱性

- **文件**: 多个测试文件（共 51 处调用）
- **证据片段**:
  ```java
  // TestCheckpointRecovery.java:86-92
  coordinator.acknowledgeTask(LOC_1, checkpointId, state1);
  coordinator.acknowledgeTask(LOC_2, checkpointId, state2);
  Thread.sleep(200);   // waiting for async checkpoint completion
  CompletedCheckpoint completed = coordinator.getLatestCheckpoint();
  ```
- **严重程度**: P2
- **现状**: 51 处 Thread.sleep 调用，CI 环境中可能不够导致间歇性失败。
- **风险**: 固定 sleep 时间在慢 CI 环境中可能失败，增加测试执行时间。
- **建议**: 使用 CountDownLatch 或 CompletableFuture.get(timeout) 替代。
- **信心水平**: 高（95%）
- **误报排除**: 部分用于等待非确定性事件，难以完全消除。
- **复核状态**: 未复核

### [维度16-05] CepOperator 状态恢复测试未走完整 snapshot/restore 通道

- **文件**: `nop-stream-cep/src/test/java/io/nop/stream/cep/operator/TestCepOperatorStateRecovery.java:237-290`
- **证据片段**:
  ```java
  // lines 239-241 (注释说明)
  // Note: SharedBuffer state (event data) is not preserved through updateNFAStateForTesting;
  // this is an infrastructure limitation. Full snapshot/restore requires the keyed state
  // backend path via operator.snapshotState()/restoreState().
  ```
- **严重程度**: P2
- **现状**: 所有 CEP 状态恢复测试使用 updateNFAStateForTesting() 而非生产路径 snapshotState()/restoreState()。
- **风险**: 生产环境 checkpoint 恢复路径的 bug 无法被测试发现。
- **建议**: 补充至少 1 个通过 snapshotState() → restoreState() 的端到端测试。
- **信心水平**: 高（90%）
- **误报排除**: TestCepStateRestoreAndContinue 通过 NFA+SharedBuffer 组合测试了恢复，但绕过了序列化层。
- **复核状态**: 未复核

### [维度16-06] connector 模块缺少集成级测试

- **文件**: `nop-stream-connector/src/test/java/` 模块级别
- **证据**: 8 个测试文件均为单元级别。StreamConnectors（53 行）无测试。
- **严重程度**: P2
- **现状**: 所有测试都是单元级别，缺少 source → operator → sink 的端到端测试。
- **风险**: connector 是流式应用与外部系统交互的关键接口，集成问题可能被遗漏。
- **建议**: 补充 BatchLoaderSource → map → BatchConsumerSink 端到端测试。
- **信心水平**: 高（85%）
- **误报排除**: 单元测试覆盖率不错，问题在集成级别。
- **复核状态**: 未复核

### [维度16-07] CheckpointCoordinator 并发测试仅覆盖 PendingCheckpoint 级别

- **文件**: `nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/TestCheckpointConcurrencySafety.java:全文1-148行`
- **证据片段**:
  ```java
  // lines 55-82
  @Test
  void testConcurrentAcknowledgeTask_noCorruption() throws Exception {
      PendingCheckpoint pc = new PendingCheckpoint(...);
      // 直接操作 PendingCheckpoint，不经过 Coordinator
  }
  ```
- **严重程度**: P1
- **现状**: 3 个测试方法中 2 个直接操作 PendingCheckpoint，绕过了 CheckpointCoordinator。
- **风险**: Coordinator 内部状态在并发操作下的损坏无法被发现。Coordinator 是 exactly-once 语义的核心。
- **建议**: 补充通过 Coordinator 进行的多线程 trigger + acknowledge 测试。
- **信心水平**: 高（90%）
- **误报排除**: 当前测试确实只覆盖了 PendingCheckpoint 级别。
- **复核状态**: 未复核

## 正面发现

- Checkpoint 恢复测试覆盖充分（TestCheckpointRecovery 490 行、TestDistributedExactlyOnce 960 行等）
- WindowOperator 测试矩阵完善（9 个专门测试文件，2374 行测试代码覆盖 1099 行生产代码）
- NFA 编译器和执行器测试充分（31 个测试文件，225 个测试方法）
- CEP 模块测试覆盖全面（24 个 assertThrows）

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 16-01 | P1 | runtime/TestEmbeddedDistributedExecution.java | 仅 1 个 happy-path 测试 |
| 16-02 | P2 | runtime/TestGraphModelCheckpointExecutor.java | 仅测试配置分发 |
| 16-03 | P1 | runtime/ 模块级别 | 错误路径测试比例过低（6.9%） |
| 16-04 | P2 | 多文件 51 处 | Thread.sleep 导致测试脆弱性 |
| 16-05 | P2 | cep/TestCepOperatorStateRecovery.java | 未走完整 snapshot/restore 通道 |
| 16-06 | P2 | connector/ 模块级别 | 缺少集成级端到端测试 |
| 16-07 | P1 | runtime/TestCheckpointConcurrencySafety.java | 并发测试仅 PendingCheckpoint 级别 |
