# 维度 16：测试覆盖与质量

## 第 1 轮（初审）

### [维度16-01] CheckpointSerDe 缺少直接单元测试

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/CheckpointSerDe.java` (294行)
- **严重程度**: P1
- **现状**: 294 行工具类负责 JSON 序列化与反序列化，无直接测试文件。间接被存储层测试覆盖 happy path，但未覆盖损坏数据、缺失字段、边界条件。
- **风险**: 序列化格式回归可能导致恢复失败。
- **建议**: 添加 TestCheckpointSerDe 覆盖完整往返、null/empty、缺失字段、损坏数据。
- **信心水平**: 确定
- **误报排除**: 无 TestCheckpointSerDe 文件存在。
- **复核状态**: 未复核

### [维度16-02] DeploymentPlanGenerator 缺少测试

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/DeploymentPlanGenerator.java` (45行)
- **严重程度**: P2
- **现状**: 无直接测试。被集成测试间接覆盖但 null 参数边界未测试。
- **建议**: 添加轻量级单元测试。
- **信心水平**: 确定
- **误报排除**: 逻辑简单，间接覆盖。
- **复核状态**: 未复核

### [维度16-03] 分布式测试使用同步消息传递，不验证并发语义

- **文件**: `nop-stream-runtime/src/test/.../TestEmbeddedDistributedExecution.java` (74行)
- **严重程度**: P1
- **证据片段**:
  ```java
  /**
   * ... Despite setting parallelism=2 and DeploymentMode.DISTRIBUTED, execution is
   * effectively single-threaded and synchronous. This test validates the wiring and
   * data flow but does NOT verify true concurrent execution semantics.
   */
  ```
- **现状**: 所有分布式测试使用 InProcessMessageService 同步传递消息，不验证多线程竞态、barrier 对齐异步正确性。
- **风险**: RemoteResultPartition/RemoteInputChannel 的并发 bug 无法被当前测试发现。
- **建议**: 添加 1-2 个使用异步消息传递的测试。
- **信心水平**: 确定
- **误报排除**: TestDistributedE2EIntegration 同样使用同步消息。
- **复核状态**: 未复核

### [维度16-04] RemoteGraphExecutionPlanBuilder 无直接测试

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteGraphExecutionPlanBuilder.java` (332行)
- **严重程度**: P1
- **现状**: 分布式执行核心构建器，332 行复杂逻辑。仅通过 E2E 集成测试间接覆盖，分区矩阵索引计算（idx = s * tgtP + taskIndex）等关键逻辑未直接测试。
- **风险**: 索引计算错误会导致并行度 > 1 时数据丢失或重复。
- **建议**: 添加 TestRemoteGraphExecutionPlanBuilder 单元测试。
- **信心水平**: 确定
- **误报排除**: 无测试文件直接引用此类。
- **复核状态**: 未复核

### [维度16-05] WatermarkOutputMultiplexer 边界覆盖不足

- **文件**: `nop-stream-core/src/test/.../TestWatermarkOutputMultiplexer.java` (91行，仅 2 个 @Test)
- **严重程度**: P2
- **现状**: DeferredOutput 延迟 emit、unregisterOutput、全 idle 等关键场景未测试。
- **建议**: 补充 3-4 个测试用例。
- **信心水平**: 确定
- **误报排除**: DeferredOutput 是生产环境最常用模式。
- **复核状态**: 未复核

## 正面发现

- CEP 模块错误路径测试覆盖良好（TestPatternValidation 347行~30个@Test）
- CheckpointCoordinator 测试质量极高（480行~17个@Test）
- 测试总量 1514 个 @Test 方法，数量健康

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 16-01 | P1 | CheckpointSerDe.java | 缺少直接单元测试 |
| 16-02 | P2 | DeploymentPlanGenerator.java | 缺少测试 |
| 16-03 | P1 | TestEmbeddedDistributedExecution.java | 分布式测试不验证并发 |
| 16-04 | P1 | RemoteGraphExecutionPlanBuilder.java | 核心路由逻辑无测试 |
| 16-05 | P2 | TestWatermarkOutputMultiplexer.java | 边界覆盖不足 |
