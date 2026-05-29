# 维度 03：API 表面积与契约一致性

## 第 1 轮（初审）

nop-stream 的公共 API 面通过 Java 接口暴露（无 GraphQL/BizModel），共 89 个公共接口。

### [维度03-01] TwoPhaseCommitSinkFunction is an interface with substantial default logic and Logger field

- **文件**: `nop-stream/nop-stream-core/.../functions/sink/TwoPhaseCommitSinkFunction.java`
- **证据片段**:
  ```java
  public interface TwoPhaseCommitSinkFunction<IN, TXN> extends SinkFunction<IN> {
      Logger LOG = LoggerFactory.getLogger(TwoPhaseCommitSinkFunction.class);
      // ~50 lines of default methods managing pending commits, transaction lifecycle
  }
  ```
- **严重程度**: P2
- **现状**: 声明为 interface 但包含 Logger 静态字段和约50行默认方法实现（事务生命周期管理、错误处理）。
- **风险**: 接口持有状态不寻常；测试困难；实现者被迫实现 getPendingCommits/setPendingCommits 等状态管理方法。
- **建议**: 改为 abstract class，或将默认逻辑提取到辅助类。
- **误报排除**: 不是纯粹的风格偏好。interface 持有可变状态（通过 accessor 方法）违反了 Java 接口设计原则。
- **复核状态**: 未复核

### [维度03-02] 37 stale org.apache.flink.* references in Javadoc

- **文件**: 20+ files in nop-stream-core/src/main/java/
- **严重程度**: P3
- **现状**: 多处 Javadoc 引用 org.apache.flink.* 类（Flink fork 遗留），导致 IDE 中 @link 渲染为断链。
- **建议**: 替换所有 org.apache.flink.* 引用为 io.nop.stream.core.* 等价物。
- **误报排除**: Flink fork 的文档遗留，需清理。
- **复核状态**: 未复核

### [维度03-03] 5 unused connector interfaces (dead code / API placeholders)

- **文件**: `io.nop.stream.core.connector` 包中的 DynamicSplitRequest, DynamicSplitResponse, RestrictionTracker, WatermarkEstimator, SourceWorkUnit
- **严重程度**: P3
- **现状**: 5个接口/类零外部引用，是 FLIP-27 风格连接器框架的前瞻性 API 预留。
- **建议**: 如有意保留，添加文档说明；否则删除。
- **误报排除**: 前瞻性 API 预留可接受，但应有文档说明。
- **复核状态**: 未复核

### [维度03-04] Mixed Chinese/English Javadoc on StreamOperator default methods

- **文件**: `nop-stream-core/.../operators/StreamOperator.java:127-146`
- **严重程度**: P3
- **现状**: snapshotState 和 initializeState 默认方法使用中文 Javadoc，与文件其余部分的英文不一致。
- **建议**: 统一为英文。
- **误报排除**: 项目规范要求错误消息用英文，Javadoc 也应一致。
- **复核状态**: 未复核

### 正面观察

- DataStream API 层次结构清晰且类型安全
- 函数接口模式一致（SourceFunction, SinkFunction, MapFunction 等）
- 状态类型层次完整（ValueState, ListState, MapState 等）
- SPI 解耦正确（IDeploymentPlanProvider 使用 ServiceLoader）
- WatermarkStrategy 提供丰富的构建器模式
