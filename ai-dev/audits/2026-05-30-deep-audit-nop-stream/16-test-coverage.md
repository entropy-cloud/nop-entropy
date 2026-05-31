# 维度 16：测试覆盖与质量

## 第 1 轮（初审）

### [维度16-01] NFACompiler 的 GroupPattern+Looping 编译路径无端到端测试

- **文件**:
  - 源：`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java:822-842`
  - 测试：`nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/compiler/TestNFACompilerExtended.java:150-195`
- **证据片段**:
  ```java
  // NFACompiler.java:822-842 - 从未被测试触发的方法
  private State<T> createLoopingGroupPatternState(
          final GroupPattern<T, ?> groupPattern, final State<T> sinkState) { ... }

  // TestNFACompilerExtended.java:150-172 - 仅测试 next()，不测试 oneOrMore()/times()
  @Test
  public void testGroupPatternCompilation() {
      Pattern<Event, ?> groupPattern = Pattern.<Event>begin("g1")
              .where(ALWAYS_TRUE)
              .next("g2")    // 仅 STRICT 策略
              .where(ALWAYS_TRUE);
  }
  ```
- **严重程度**: P2
- **现状**: GroupPattern 的测试仅使用 next()（STRICT 策略），从未与 oneOrMore()、times() 或 consecutive() 组合。createLoopingGroupPatternState 从未被测试触发。
- **风险**: GroupPattern+Looping 是 CEP 中实际常见的组合模式（"连续3次失败登录后告警"），状态机结构错误只能在运行时通过 CEP 结果偏差发现。
- **建议**: 添加 GroupPattern 与 oneOrMore()/times() 组合的编译+运行时测试。
- **信心水平**: 很可能
- **误报排除**: 已验证 TestNFACompilerExtended 中所有测试方法均未使用 looping 操作。
- **复核状态**: 未复核

### [维度16-02] TestNFACompiler 全部为浅层断言，仅验证状态存在性而不验证转换正确性

- **文件**: `nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/compiler/TestNFACompiler.java`
- **证据片段**:
  ```java
  @Test
  public void testPatternWithFollowedBy() {
      Pattern<Event, ?> pattern = Pattern.<Event>begin("a").followedBy("b");
      NFA<Event> nfa = NFACompiler.compileFactory(pattern, false).createNFA();
      Collection<State<Event>> states = nfa.getStates();
      assertTrue(states.size() >= 3);  // 为什么 >=3？应该精确是多少？
  }
  ```
- **严重程度**: P2
- **现状**: 5 个测试方法全部仅检查状态存在性和名称，从不验证 StateTransition 的方向、条件或目标状态是否正确。
- **风险**: 状态转换方向错误或条件绑定错误不会被检测到。
- **建议**: 添加验证 transition 结构的测试（TAKE/IGNORE/PROCEED 边及其目标状态）。
- **信心水平**: 确定
- **误报排除**: 编译器是 CEP 引擎正确性的根节点，浅层断言保护力不足。
- **复核状态**: 未复核

### [维度16-03] WindowOperator.onProcessingTime 无直接测试覆盖

- **文件**:
  - 源：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:539-593`
  - 测试：runtime 下 6 个 WindowOperator 测试文件
- **证据片段**:
  ```java
  // WindowOperator.java:539-593 - onProcessingTime 方法
  public void onProcessingTime(InternalTimer<K, W> timer) throws Exception {
      triggerContext.key = timer.getKey();
      triggerContext.window = timer.getNamespace();
      // ... MergingWindowAssigner + processing time cleanup 组合 ...
  }
  ```
- **严重程度**: P2
- **现状**: 所有 runtime WindowOperator 测试均使用 EventTimeTrigger + advanceWatermark，仅覆盖了 onEventTime 路径。onProcessingTime 路径零测试覆盖。
- **风险**: onProcessingTime 中的 isCleanupTime 路径是窗口资源泄漏的关键防线，该路径零测试覆盖。
- **建议**: 添加 ProcessingTimeTrigger + TumblingProcessingTimeWindows 的 WindowOperator 集成测试。
- **信心水平**: 确定
- **误报排除**: 已搜索所有 runtime 测试文件，确认无 ProcessingTime 相关的 WindowOperator 测试。
- **复核状态**: 未复核

### [维度16-04] CheckpointCoordinator.scheduleTimeout 的超时中止路径未测试

- **文件**:
  - 源：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:361-371`
  - 测试：`nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/TestCheckpointCoordinator.java`
- **证据片段**:
  ```java
  // CheckpointCoordinator.java:361-371 - 超时自动中止
  private void scheduleTimeout(PendingCheckpoint pending) {
      timeoutScheduler.schedule(() -> {
          if (!pending.getCompletableFuture().isDone()) {
              abortPendingCheckpoint(pending, "Timeout");
          }
      }, config.getCheckpointTimeout(), TimeUnit.MILLISECONDS);
  }
  ```
- **严重程度**: P2
- **现状**: checkpointTimeout 设置为 5000L，但没有测试故意不 acknowledge 以等待超时触发。
- **风险**: 如果超时中止路径有 bug，生产环境在 checkpoint 超时时会出现 pending checkpoint 堆积，阻塞后续 checkpoint。
- **建议**: 添加测试：故意不 acknowledge → 等待超时 → 验证 pending 被中止 → 验证下一个 checkpoint 可以触发。
- **信心水平**: 确定
- **误报排除**: 超时是生产环境 checkpoint 失败的主要模式之一。
- **复核状态**: 未复核

### [维度16-05] GraphModelCheckpointExecutor.executeWithCheckpoint(StreamModel, ...) 无单元测试

- **文件**:
  - 源：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:104-156`
  - 测试：所有测试均使用重载 1（JobGraph 入口）
- **证据片段**:
  ```java
  // GraphModelCheckpointExecutor.java:104-156 - 无测试的重载
  public static StreamExecutionResult executeWithCheckpoint(
          StreamModel streamModel, PartitionedPlan partitionedPlan,
          DeploymentPlan deploymentPlan) throws Exception {
      StreamModelFingerprint fingerprint = streamModel.computeFingerprint();
      coordinator.setCurrentFingerprint(fingerprint);
      ...
  }
  ```
- **严重程度**: P1
- **现状**: 重载 2 比 重载 1 多了 fingerprint 设置和从 StreamModel 构建 JobGraph 的逻辑，但无任何直接测试。
- **风险**: 这是用户通过 StreamExecutionEnvironment.execute() 启用 checkpointing 时的主路径。如果 buildJobGraphFromStreamModel 的 transformation 过滤逻辑有误，用户管道会静默丢失算子。
- **建议**: 添加使用重载 2 入口的端到端测试。
- **信心水平**: 确定
- **误报排除**: 已搜索所有测试文件，确认无测试使用重载 2。
- **复核状态**: 未复核

### [维度16-06] TestCheckpointCoordinator 使用 Thread.sleep(100) 等待异步完成

- **文件**: `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/TestCheckpointCoordinator.java:95-117`
- **证据片段**:
  ```java
  Thread.sleep(100);  // 魔法数字，等待异步存储完成
  CompletedCheckpoint completed = pending.getCompletableFuture().get();
  ```
- **严重程度**: P3
- **现状**: Thread.sleep 在整个 nop-stream 测试套件中出现 47 次，假设固定等待时间足够完成 I/O。
- **风险**: CI 环境高负载时可能间歇性失败。
- **建议**: 使用 CompletableFuture.get(timeout) 或 Awaitility.await() 替代。
- **信心水平**: 很可能
- **误报排除**: 正常环境下通常通过，但在高负载 CI 节点时可能间歇性失败。
- **复核状态**: 未复核

### [维度16-07] TestWindowOperatorBasic 文件名与实际内容不匹配

- **文件**: `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/operators/windowing/TestWindowOperatorBasic.java`
- **证据片段**:
  ```java
  /**
   * Tests for basic windowing infrastructure (assigners, triggers, window objects).
   * Despite the class name, this does not directly test {@code WindowOperator};
   */
  ```
- **严重程度**: P3
- **现状**: 文件名暗示测试 WindowOperator，但实际仅测试 assigner/trigger/window 基础设施类。这些类在 core 中已有专属测试。
- **风险**: 降低测试套件可维护性，给开发者虚假的测试覆盖印象。
- **建议**: 重命名为 TestWindowingInfrastructure 或合并到对应的 assigner/trigger 测试中。
- **信心水平**: 确定
- **误报排除**: 文件注释已说明不直接测试 WindowOperator，但文件名仍然误导。
- **复核状态**: 未复核

### [维度16-08] CheckpointCoordinator retryFailedCommits 的多 epoch 交叉重试路径未测试

- **文件**:
  - 源：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:436-466`
  - 测试：`nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/TestCheckpointCoordinator.java:240-270`
- **证据片段**:
  ```java
  // CheckpointCoordinator.java:436-466 - retryFailedCommits
  private void retryFailedCommits() {
      for (Integer idx : failedIdx) {
          try {
              participants.get(idx).finishCommit(failedEpoch, originalSuccess);
          } catch (Exception e) {
              stillFailing.add(idx);
          }
      }
  }
  ```
- **严重程度**: P2
- **现状**: 测试仅验证了重试时是否传递正确的 success 值，未测试持续失败的参与者、多 epoch 失败交叉、参与者被移除后重试等边界。
- **风险**: retryFailedCommits 是 exactly-once 语义的关键保证。跨 checkpoint 的 commit 失败累积可能导致数据丢失或重复。
- **建议**: 添加持续失败、多 epoch 交叉的测试场景。
- **信心水平**: 很可能
- **误报排除**: 分布式流处理中 commit 失败是常态，该路径是数据一致性的关键保障。
- **复核状态**: 未复核

## 深挖第 2 轮追加

### [维度16-09] StreamExecutionEnvironment triggerSavepoint/executeWithSavepoint 路径未测试

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/environment/StreamExecutionEnvironment.java:292-344`
- **证据片段**:
  ```java
  public StreamExecutionResult triggerSavepoint(String savepointPath) {
      // 包含非平凡的验证逻辑，但从未通过 StreamExecutionEnvironment 自身被测试
  }
  ```
- **严重程度**: P2
- **现状**: TestSavepointEndToEnd 直接调用 GraphModelCheckpointExecutor 的静态方法，跳过了 StreamExecutionEnvironment 的验证包装层。
- **风险**: 验证逻辑 bug（如 checkpointExecutorFactory 空检查遗漏）不会被测试捕获。
- **建议**: 添加单元测试覆盖三个 savepoint 方法，验证正常路径和异常路径。
- **信心水平**: 确定
- **误报排除**: 已搜索所有测试文件，确认无测试通过 StreamExecutionEnvironment 调用 savepoint 方法。
- **复核状态**: 未复核

### [维度16-10] SharedBuffer.flushCache() 刷写后数据完整性未验证

- **文件**: `nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/sharedbuffer/TestSharedBufferExtended.java:276-287`
- **证据片段**:
  ```java
  // testRegisterEventPersistsToState 验证缓存为空且状态大小 > 0，
  // 但从未从状态中读回数据验证内容正确性
  ```
- **严重程度**: P2
- **现状**: 测试仅验证 close() 后缓存为空且状态非空，从未验证持久化数据的正确性。
- **风险**: CEP 共享缓冲区的数据持久化正确性未验证，checkpoint 恢复后模式匹配结果可能错误。
- **建议**: 在 close() 后通过新 SharedBufferAccessor 从状态读回数据，断言内容和引用计数。
- **信心水平**: 很可能
- **误报排除**: flushCache 是 CEP 数据持久化的关键路径，需要验证数据完整性。
- **复核状态**: 未复核

### [维度16-11] DebeziumCdcSourceFunction.subscribe() 异常恢复路径未测试

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/DebeziumCdcSourceFunction.java:60-67`
- **证据片段**:
  ```java
  try {
      subscription = source.subscribe(ctx::collect);
  } catch (Exception e) {
      source.stop();
      throw e;
  }
  ```
- **严重程度**: P2
- **现状**: subscribe 失败 → stop → 传播异常的路径没有测试覆盖。所有测试使用的 DebeziumMessageSource 都能正常 subscribe。
- **风险**: 如果 subscribe 失败后 source.stop() 也失败，可能导致资源泄漏。
- **建议**: 使用 mock DebeziumMessageSource（subscribe 时抛异常）编写测试。
- **信心水平**: 很可能
- **误报排除**: 该路径是异常恢复的关键路径，需要测试验证。
- **复核状态**: 未复核

### [维度16-12] CheckpointStorage 损坏数据反序列化未测试

- **文件**:
  - `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java:131`
  - `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/LocalFileCheckpointStorage.java:333-335`
- **证据片段**:
  ```java
  // 两种实现都从持久化存储读取字节并反序列化，但测试仅覆盖正常 round-trip
  ```
- **严重程度**: P2
- **现状**: 现有测试仅覆盖正常写入后读回，未测试损坏数据（部分写入、文件截断、序列化版本不兼容）场景。
- **风险**: 生产环境 checkpoint 数据可能因磁盘故障等损坏，反序列化失败时的异常信息不足会导致排查困难。
- **建议**: 添加测试验证损坏数据场景下的异常处理。
- **信心水平**: 很可能
- **误报排除**: checkpoint 损坏是生产环境的实际场景。
- **复核状态**: 未复核

### [维度16-13] SubtaskTask.cancel() 和算子链部分打开失败路径未测试

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/SubtaskTask.java:83-85, 108-124`
- **证据片段**:
  ```java
  // cancel() 方法 — 唯一能阻止已提交任务执行的机制，未测试
  public boolean cancel() {
      if (state == State.CREATED) {
          state = State.CANCELED;
          return true;
      }
      return false;
  }
  ```
- **严重程度**: P2
- **现状**: cancel() 方法、openOperatorChains 部分失败的 rollback 路径、closeOperatorChains 失败时状态不一致路径均未测试。
- **风险**: SubtaskTask 是 StreamExecutionEnvironment.execute() 的实际执行单元，任务生命周期管理的核心路径未覆盖。
- **建议**: 添加 cancel() 测试、部分 open 失败测试、close 失败测试。
- **信心水平**: 确定
- **误报排除**: SubtaskTask 是任务执行的核心类，cancel 和错误恢复是关键路径。
- **复核状态**: 未复核
