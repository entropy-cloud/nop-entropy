# 维度 09：错误处理与错误码

## 审计范围概览

| 检查项 | 结果 |
|--------|------|
| ErrorCode 定义类 | 2个：`NopStreamErrors`（21个错误码）、`NopCepErrors`（8个错误码） |
| 模块级异常类 | 2个：`StreamException extends StreamRuntimeException extends NopException` |
| ErrorCode 命名格式 | 全部遵循 `nop.err.{模块}.{错误描述}`，格式正确 |
| 硬编码中文错误消息 | 无 |
| 空 catch 块 | 无 |

### [维度09-01] Connector 公共 API 使用字符串构造器，未使用 ErrorCode

- **文件**: `nop-stream-connector/src/main/java/io/nop/stream/connector/DebeziumCdcSourceFunction.java:37`, `BatchConsumerSinkFunction.java:44-47`, `MessageSinkFunction.java:30-33`, `BatchLoaderSourceFunction.java:42-45`, `MessageSourceFunction.java:63-72`
- **证据片段**:
  ```java
  throw new StreamException("config must not be null");
  throw new StreamException("consumerProvider must not be null");
  throw new StreamException("batchSize must be at least 1");
  throw new StreamException("messageService must not be null");
  throw new StreamException("topic must not be null or empty");
  ```
- **严重程度**: P2
- **现状**: 5个 Connector 类共12处使用 `new StreamException("string")` 抛出参数校验异常。`NopStreamErrors` 已定义 `ERR_STREAM_NULL_ARG` 和 `ERR_STREAM_INVALID_ARG` 但未被使用。
- **风险**: Connector 是应用层直接使用的公共 API。字符串构造器缺少结构化错误码，调用方无法通过 error code 做程序化处理。
- **建议**: 替换为 `new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "config")` 模式。
- **信心水平**: 确定
- **误报排除**: `NopStreamErrors` 已定义对应错误码却未使用，属于公共 API 契约漂移。
- **复核状态**: 未复核

### [维度09-02] Runtime 公共 API 使用字符串构造器

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointPlanBuilder.java:61-67`, `execution/DeploymentPlanGenerator.java:24`, `source/SourceEnumerator.java:63`, `coordinator/JobCoordinator.java:456`, `transport/RemoteResultPartition.java:84`
- **证据片段**:
  ```java
  throw new StreamException("executionPlan must not be null");
  throw new StreamException("jobId must not be null or empty");
  throw new StreamException("Unknown termination mode: " + mode);
  ```
- **严重程度**: P2
- **现状**: 5个 Runtime 公共类共9处在参数校验和业务逻辑异常中使用字符串构造器。
- **风险**: 缺少结构化错误码影响上层调用方的错误处理能力。
- **建议**: 对参数校验使用 `ERR_STREAM_NULL_ARG`/`ERR_STREAM_INVALID_ARG`；对业务逻辑异常新增专用 ErrorCode。
- **信心水平**: 确定
- **误报排除**: 涉及跨模块公共 API（CheckpointPlanBuilder 是静态工厂）。
- **复核状态**: 未复核

### [维度09-03] WindowOperator 相同场景使用字符串，而 WindowAggregationOperator 使用 ErrorCode（不一致）

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:1010` vs `nop-stream-core/src/main/java/io/nop/stream/core/operators/WindowAggregationOperator.java:668`
- **证据片段**:
  ```java
  // WindowOperator.java:1010 - 使用字符串
  throw new StreamException("Failed to create trigger state accumulator", e);

  // WindowAggregationOperator.java:668 - 使用 ErrorCode
  throw new StreamException(ERR_STREAM_WINDOW_TRIGGER_STATE_ACCUMULATOR_FAILED, e)
          .param(ARG_DESCRIPTOR_NAME, rsd.getName());
  ```
- **严重程度**: P2
- **现状**: 两个 Operator 类在完全相同的场景中，一个使用字符串，另一个使用已定义的 ErrorCode。
- **风险**: 同一语义的错误产生不同格式的异常，影响统一的错误监控和报警。
- **建议**: 将 WindowOperator 改为使用 `ERR_STREAM_WINDOW_TRIGGER_STATE_ACCUMULATOR_FAILED`。
- **信心水平**: 确定
- **误报排除**: 语义完全相同的错误场景在两个文件中使用了不同的错误表达方式。
- **复核状态**: 未复核

### [维度09-04] ClassNameValidator 使用 SecurityException 而非模块级异常

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/util/ClassNameValidator.java:32,39`
- **证据片段**:
  ```java
  throw new IllegalArgumentException("Class name must not be null or empty");
  throw new SecurityException("Class not allowed for dynamic loading: " + className +
          ". Allowed prefixes: " + ALLOWED_PREFIXES);
  ```
- **严重程度**: P1
- **现状**: 安全敏感的类名校验器使用了 JDK 原生的 `SecurityException` 和 `IllegalArgumentException`，而非 `StreamException` + ErrorCode。`NopStreamErrors` 已定义 `ERR_STREAM_CLASS_NOT_ALLOWED` 可直接使用。
- **风险**: `SecurityException` 无法被 `catch (StreamException)` / `catch (NopException)` 捕获，可能绕过上层统一错误处理。安全相关错误绕过 NopException 体系是结构性风险。
- **建议**: 改为 `throw new StreamException(ERR_STREAM_CLASS_NOT_ALLOWED).param(ARG_CLASS_NAME, className)`。
- **信心水平**: 确定
- **误报排除**: ClassNameValidator 标注为 `@Internal` 但被 `StreamElementCodec`（公共编码器）直接调用。
- **复核状态**: 已保留（独立复核确认成立，P1 维持）

### [维度09-05] RemoteInputChannel 吞掉反序列化异常，可能导致静默数据丢失

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteInputChannel.java:218-223`
- **证据片段**:
  ```java
  } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Interrupted while enqueueing decoded element", e);
  } catch (Exception e) {
      LOG.error("Failed to decode envelope on topic={}", topic, e);
  }

  return null;
  ```
- **严重程度**: P1
- **现状**: `RemoteInputChannel.onMessage()` 的 lambda 在反序列化失败时仅记录错误日志，不抛出异常、不设置错误状态。方法返回 null。
- **风险**: 在生产环境中，反序列化失败时数据会静默丢失，不触发任何告警或恢复机制。Checkpoint 机制依赖完整的数据流，数据丢失可能导致 checkpoint 状态不完整。
- **建议**: 至少设置一个错误标志使后续读取操作抛出异常；或在无法解码时抛出 `StreamException(ERR_STREAM_SERIALIZATION)` 以触发 failover。
- **信心水平**: 很可能
- **误报排除**: 与 LocalFileCheckpointStorage 中 LOG.warn 的 catch 不同——那些是遍历多文件时对单文件的容错，这里每条消息都是唯一的流数据，丢失不可恢复。
- **复核状态**: 未复核

### [维度09-06] OperatorChain 混用 ErrorCode 和字符串构造器

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/jobgraph/OperatorChain.java:81,112-113,116-117,157,191,238`
- **证据片段**:
  ```java
  // Line 81 - ErrorCode
  throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "operators");

  // Line 112-113 - 字符串
  throw new StreamException("Failed to process element in operator: " + operator.getClass().getName(), e);

  // Line 116-117 - IllegalStateException
  throw new IllegalStateException("Operator does not implement Input interface: " + ...);
  ```
- **严重程度**: P2
- **现状**: 同一个类中混用三种异常风格：ErrorCode、字符串构造器、IllegalStateException。
- **风险**: 同一类中异常风格不一致增加维护成本；`IllegalStateException` 不在 `StreamException` 体系内。
- **建议**: 统一使用 ErrorCode 模式。
- **信心水平**: 确定
- **误报排除**: OperatorChain 是核心算子链执行组件，被 StreamTaskInvokable 和 checkpoint 机制广泛使用。
- **复核状态**: 未复核

### [维度09-07] 非测试主代码中直接使用 IllegalStateException/IllegalArgumentException（~20处）

- **文件**: `JobCoordinator.java:214,277`, `TaskManager.java:313`, `WindowOperator.java:414,816`, `CheckpointPlanBuilder.java:77,88`, `MergingWindowSet.java:133`, `ResultPartition.java:79`, `RemoteResultPartition.java:87`, `MemoryInternalListState.java:85`, `MemoryInternalAppendingState.java:99`, `AbstractRichFunction.java:52,59,63`, `StreamReduceOperator.java:63`, `RichIterativeCondition.java:56`, `KeyedStreamImpl.java:177,200,222,291`
- **证据片段**:
  ```java
  throw new IllegalStateException("No RPC service for node " + targetNode.getNodeId());
  throw new IllegalStateException("Window " + window + " is not in in-flight window set.");
  throw new IllegalStateException("Vertex not found in execution plan: " + vertexId);
  ```
- **严重程度**: P2
- **现状**: 约20处非测试代码使用 JDK 原生 `IllegalStateException`/`IllegalArgumentException`，绕过 `StreamException`/`NopException` 体系。
- **风险**: 如果上层有统一的 `catch (NopException)` 处理，这些异常会被遗漏。
- **建议**: 对关键路径（JobCoordinator、CheckpointPlanBuilder、WindowOperator）中的 JDK 异常替换为 `StreamException(ERR_STREAM_INVALID_STATE)`。
- **信心水平**: 确定
- **误报排除**: 批量问题但每个实例都有独立的结构性原因——它们使同一模块的异常处理出现两条路径。
- **复核状态**: 已保留（独立复核确认成立，P1 维持。补充：StreamElementCodec.decode() 的 try-catch 仅捕获 ClassNotFoundException，SecurityException 会直接穿透。建议增加 volatile Throwable decodeError 字段）

## 维度复核结论 PrintSinkFunction / PrintSink 使用 System.out 而非 SLF4J

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/PrintSinkFunction.java:57,59`, `nop-stream-core/src/main/java/io/nop/stream/core/operators/PrintSink.java:40`
- **证据片段**:
  ```java
  System.out.println(prefix + ": " + value);
  ```
- **严重程度**: P3
- **现状**: 调试用 Sink 类使用 `System.out.println` 输出流数据。这是 Flink PrintSinkFunction 的标准行为模式。
- **风险**: 在生产环境中如果误用了这些 Sink，输出会绕过日志框架。
- **建议**: 在类 Javadoc 中标注 "This sink is for debugging only."。
- **信心水平**: 确定
- **误报排除**: 这是 Sink 的设计意图（打印到标准输出）。
- **复核状态**: 未复核

### [维度09-09] FraudDetectionDemo 使用 System.out（Demo 类主方法）

- **文件**: `nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/FraudDetectionDemo.java:55,58-59,72-73,78-79,87-88,95,202-209`
- **证据片段**:
  ```java
  System.out.println("=== Fraud Detection Demo ===\n");
  ```
- **严重程度**: P3
- **现状**: Demo 类的 `main()` 方法使用 `System.out.println` 输出示例结果。
- **风险**: 无。Demo 代码的 main() 方法使用 stdout 是标准做法。
- **建议**: 无需修改。
- **信心水平**: 确定
- **误报排除**: Demo 类的 main() 方法使用 stdout 是标准做法。
- **复核状态**: 未复核
