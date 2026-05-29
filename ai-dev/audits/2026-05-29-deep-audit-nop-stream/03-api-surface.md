# 维度03：API 表面积与契约一致性

## 第 1 轮（初审）

### [维度03-01] StreamExecutionEnvironment.execute() 双重包装异常，丢失 ErrorCode

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/environment/StreamExecutionEnvironment.java:207-289`
- **证据片段**:
  ```java
  // 第 209 行：内部使用 ErrorCode
  throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "A streaming job can only be executed once");

  // 第 217 行：内部使用 ErrorCode
  throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "No sinks found in the streaming job");

  // 第 280 行：此处未使用 ErrorCode
  throw new StreamException("Task failed", task.getError());

  // 第 287-289 行：外层 catch 将所有异常（包括已结构化的 StreamException）重新包装
  } catch (Exception e) {
      throw new StreamException("Failed to execute job: " + jobName, e);
  }
  ```
- **严重程度**: P2
- **现状**: `execute(String)` 是流处理引擎主入口 API。try 块中精心构造的 `StreamException(ERR_STREAM_INVALID_STATE)` 会被第 287-288 行的 catch-all 重新包装。调用方捕获后无法直接获取错误码，必须遍历 cause 链。
- **风险**: 所有调用 `env.execute()` 的用户代码均受影响。无法通过 `exception.getErrorCode()` 直接判断失败类型。
- **建议**: catch 块中应区分已结构化的 `StreamException`（直接 re-throw）和其他异常（包装后抛出）。例如：`if (e instanceof StreamException) throw (StreamException) e;`
- **信心水平**: 确定
- **误报排除**: 不是"看起来不优雅"。公共 API 主入口丢失 ErrorCode 信息违反了 Nop 平台的错误处理约定（公共 API 必须使用 ErrorCode 模式），有结构性原因（catch-all 覆盖了精心构造的异常）。
- **复核状态**: 未复核

---

### [维度03-02] ICheckpointExecutorFactory default 方法 Javadoc 与实际行为矛盾

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ICheckpointExecutorFactory.java:63-83`
- **证据片段**:
  ```java
  /**
   * Default implementation delegates to the simpler
   * {@link #executeWithCheckpoint(JobGraph, String, CheckpointConfig)} to maintain
   * backward compatibility.
   */
  default StreamExecutionResult executeWithCheckpoint(
          StreamModel streamModel,
          PartitionedPlan partitionedPlan,
          DeploymentPlan deploymentPlan) throws Exception {
      throw new UnsupportedOperationException(
              "executeWithCheckpoint(StreamModel, PartitionedPlan, DeploymentPlan) not implemented");
  }
  ```
  ```java
  // StreamExecutionEnvironment.java 第 242-246 行：调用此 default 方法
  if (checkpointConfig.isCheckpointEnabled() && checkpointExecutorFactory != null) {
      StreamExecutionResult result = checkpointExecutorFactory.executeWithCheckpoint(
          streamModel, partitionedPlan, deploymentPlan);
  }
  ```
- **严重程度**: P2
- **现状**: 接口 Javadoc 声称"委托给更简单的方法以保持向后兼容"，但实际 default 方法直接抛出 `UnsupportedOperationException`。`StreamExecutionEnvironment.execute()` 在启用 checkpoint 时调用此 default 方法。如果新实现者只覆写了抽象方法而未覆写 default 方法，运行时会抛出异常而非编译期错误。Javadoc 的描述具有误导性。
- **风险**: 潜在契约漂移。当前 `CheckpointExecutorFactoryImpl` 正确覆写了两个方法，不会触发此问题。但新实现者会被 Javadoc 误导。
- **建议**: (1) 修正 Javadoc，明确标注 default 方法必须被覆写；(2) 或者让 default 方法真正委托给 `executeWithCheckpoint(JobGraph, String, CheckpointConfig)`。
- **信心水平**: 确定
- **误报排除**: 不是"看起来不优雅"。Javadoc 说的"delegates"与代码实际 throws UnsupportedOperationException 是功能描述错误，会误导后续开发者。
- **复核状态**: 未复核

---

### [维度03-03] PatternStream Javadoc 引用不存在的 SingleOutputStreamOperator#getSideOutput(OutputTag) 方法

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/PatternStream.java:66-68, 139-141, 202-204`
- **证据片段**:
  ```java
  // 第 66-68 行
  * You can get the stream of late data using {@link
  * SingleOutputStreamOperator#getSideOutput(OutputTag)} on the {@link
  * SingleOutputStreamOperator} resulting from the pattern processing operations.

  // 第 139-141 行
  * You can get the stream of timed-out data resulting from the {@link
  * SingleOutputStreamOperator#getSideOutput(OutputTag)} on the
  * SingleOutputStreamOperator resulting from the select operation.
  ```
- **严重程度**: P3
- **现状**: PatternStream 的 Javadoc 三处引用 `SingleOutputStreamOperator#getSideOutput(OutputTag)`，但该方法在 `SingleOutputStreamOperator` 接口中不存在。这是从 Apache Flink 移植时残留的文档引用。
- **风险**: 用户按 Javadoc 指引调用不存在的方法，导致编译错误或混淆。
- **建议**: 移除或修正三处 Javadoc 引用，替换为 nop-stream 实际的 side output 获取方式说明。
- **信心水平**: 确定
- **误报排除**: 不是"看起来不优雅"。引用了不存在的 API 方法是功能性文档错误。
- **复核状态**: 未复核

---

## 检查范围说明

| 检查项 | 结论 |
|--------|------|
| DataStream/KeyedStream/WindowedStream 接口 | 泛型约束合理，无多余 Map 暴露 |
| CEP Pattern API | fluent builder 模式收敛一致 |
| 连接器工厂 StreamConnectors | 4 个静态方法，参数类型安全 |
| RPC 服务接口 | 参数类型安全，标注 @Internal |
| 核心函数式接口 | 24 个接口均参数化合理 |
| 死 API 检查 | 未发现完全无引用的公共方法 |
| 内部实现暴露检查 | SPI 和 RPC 接口均标注 @Internal |
