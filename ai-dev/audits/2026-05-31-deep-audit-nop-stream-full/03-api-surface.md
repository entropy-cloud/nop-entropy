# 维度 03：API 表面积与契约一致性

## 第 1 轮（初审）

### [维度03-01] nop-stream-api 为空壳模块，API 与实现未分离

- **文件**: `nop-stream/nop-stream-api/pom.xml:13-14`
- **证据片段**:
  ```xml
  <!-- placeholder, planned but not implemented -->
  <!-- interfaces are in nop-stream-core; this module is reserved for future API extraction -->
  ```
- **严重程度**: P3
- **现状**: 所有公共接口（SourceFunction, SinkFunction, DataStream 等）直接定义在 nop-stream-core 中。外部消费者无法仅依赖 API 模块。
- **风险**: 模块边界不清晰，但这已经是已知且受控的技术债。
- **建议**: 按计划执行 API 提取，或更新设计文档反映现状。
- **信心水平**: 确定
- **误报排除**: 与维度01-01是同一问题的不同角度，此处关注 API 表面积而非依赖方向。
- **复核状态**: 未复核

### [维度03-02] CheckpointParticipant 接口缺少 @Internal 标注

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/participant/CheckpointParticipant.java:12-21`
- **证据片段**:
  ```java
  // 无 @Internal
  public interface CheckpointParticipant {
      TaskStateSnapshot saveState(long epochId) throws Exception;
      void prepareCommit(long epochId) throws Exception;
      void finishCommit(long epochId, boolean success) throws Exception;
      void restoreFromEpoch(long epochId, TaskStateSnapshot state) throws Exception;
  }
  ```
- **严重程度**: P3
- **现状**: 公共接口无 @Internal 标注，但实现类 TwoPhaseCommitSinkFunction 已标注 @Internal。
- **风险**: 外部用户可能误用此接口。
- **建议**: 添加 @Internal 标注或明确文档化其对外暴露意图。
- **信心水平**: 确定
- **误报排除**: 不是误报——同模块其他内部类已标注 @Internal。
- **复核状态**: 未复核

### [维度03-03] nop-stream-runtime 中多个 public 类缺少 @Internal 标注

- **文件**: PendingCheckpoint, CheckpointPlanBuilder, CheckpointMetrics, InMemoryClusterRegistry, ClusterRegistry, EmbeddedDistributedExecutor, DeploymentPlanGenerator, CheckpointAckMessage
- **严重程度**: P3
- **现状**: 同模块的 CheckpointCoordinator 和 JdbcCheckpointStorage 已标注 @Internal，但上述类未标注，标准不一致。
- **建议**: 统一添加 @Internal 标注。
- **信心水平**: 确定
- **误报排除**: 不是误报——同模块内标准不一致。
- **复核状态**: 未复核

## 合规亮点

- 核心 API 接口（SourceFunction, SinkFunction, StreamOperator, DataStream 等）设计合理
- @Internal 标注广泛覆盖（core 中 75 个类/接口，runtime 中 14 个）
- 用户面向的扩展点（SourceFunction, SinkFunction, MapFunction 等）未标注 @Internal，设计合理
