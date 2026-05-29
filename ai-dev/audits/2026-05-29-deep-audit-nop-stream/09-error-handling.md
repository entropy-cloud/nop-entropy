# 维度09：错误处理与错误码

## 第 1 轮（初审）

### [维度09-01] connector 公共 API 参数校验使用 String 构造器，丢失 ErrorCode 结构化信息

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:62-72`
- **证据片段**:
  ```java
  if (messageService == null) {
      throw new StreamException("messageService must not be null");
  }
  if (topic == null || topic.isEmpty()) {
      throw new StreamException("topic must not be null or empty");
  }
  ```
- **严重程度**: P2
- **现状**: connector 子模块的 5 个公共构造器共 12 处使用 `StreamException(String)` 做参数校验。`NopStreamErrors` 已定义 `ERR_STREAM_NULL_ARG` 和 `ERR_STREAM_INVALID_ARG` 但未使用。
- **风险**: 调用方无法通过 `ErrorCode` 匹配做程序化处理，丢失 `.param()` 结构化参数。
- **建议**: 统一替换为 `throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "messageService")` 模式。
- **信心水平**: 高
- **误报排除**: 已确认 `NopStreamErrors` 已有对应的通用 ErrorCode 可直接复用。
- **复核状态**: 未复核

---

### [维度09-02] StreamExecutionEnvironment.execute() 核心路径使用 String 构造器

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/environment/StreamExecutionEnvironment.java:278-289`
- **证据片段**:
  ```java
  // Line 280
  throw new StreamException("Task failed", task.getError());
  // Line 288
  throw new StreamException("Failed to execute job: " + jobName, e);
  ```
- **严重程度**: P1
- **现状**: `execute()` 是流处理作业主入口公共 API。同一类中其他校验已使用 ErrorCode，但这两处关键错误路径仍使用 String 构造器。第 280 行丢弃了 task 的结构化错误信息，第 288 行拼接 jobName 到消息中无法通过 `.param()` 结构化传递。
- **风险**: 最高频调用路径，错误最可能暴露给上层框架或监控系统，无法通过 ErrorCode 程序化匹配。
- **建议**: 改为 `StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_EXECUTE_FAILED, e).param(ARG_JOB_ID, jobName)`。
- **信心水平**: 高
- **误报排除**: 同文件中已使用 ErrorCode 的其他 throw 语句已排除。
- **复核状态**: 未复核

---

### [维度09-03] runtime 子模块公共 API 使用 String 构造器做参数校验（系统性）

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointPlanBuilder.java:60-67`
- **证据片段**:
  ```java
  if (executionPlan == null) {
      throw new StreamException("executionPlan must not be null");
  }
  if (jobId == null || jobId.isEmpty()) {
      throw new StreamException("jobId must not be null or empty");
  }
  ```
- **严重程度**: P2
- **现状**: runtime 子模块中 `CheckpointPlanBuilder`(3处)、`DeploymentPlanGenerator`(1处)、`SourceEnumerator`(1处) 等共 8 处使用 String 构造器。
- **风险**: `CheckpointPlanBuilder.build()` 是 checkpoint 管线静态入口，ErrorCode 传播链在此断裂。
- **建议**: 统一替换为 `ERR_STREAM_NULL_ARG`/`ERR_STREAM_INVALID_ARG` + `.param()`。
- **信心水平**: 高
- **误报排除**: 已确认 `NopStreamErrors` 中有可复用的通用 ErrorCode。
- **复核状态**: 未复核

---

### [维度09-04] ClassNameValidator 使用裸 SecurityException，绕过模块异常体系

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/util/ClassNameValidator.java:30-40`
- **证据片段**:
  ```java
  public static void validateClassName(String className) {
      if (className == null || className.isEmpty()) {
          throw new IllegalArgumentException("Class name must not be null or empty");
      }
      for (String prefix : ALLOWED_PREFIXES) {
          if (className.startsWith(prefix)) return;
      }
      throw new SecurityException("Class not allowed for dynamic loading: " + className);
  }
  ```
- **严重程度**: P1
- **现状**: 安全边界在状态恢复反序列化路径中调用。直接抛出 `IllegalArgumentException` 和 `SecurityException`，绕过 `StreamException` 体系。`NopStreamErrors` 已定义 `ERR_STREAM_CLASS_NOT_ALLOWED` 但未使用。
- **风险**: 裸 `SecurityException` 不携带 `ErrorCode`，上层 `try-catch(StreamException)` 无法捕获，异常穿透影响 checkpoint 失败的错误报告和恢复逻辑。
- **建议**: 替换为 `throw new StreamException(ERR_STREAM_CLASS_NOT_ALLOWED).param(ARG_CLASS_NAME, className)`。
- **信心水平**: 高
- **误报排除**: 这是安全边界的异常类型匹配风险，不是"看起来不优雅"。
- **复核状态**: 未复核

---

### [维度09-05] 关键执行路径上裸 JDK 异常（IllegalStateException）

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointPlanBuilder.java:77,88`
- **证据片段**:
  ```java
  if (vertex == null) {
      throw new IllegalStateException("Vertex not found in execution plan: " + vertexId);
  }
  if (chains == null || chains.isEmpty()) {
      throw new IllegalStateException("Vertex has no operator chains: " + vertexId);
  }
  ```
- **严重程度**: P2
- **现状**: 生产代码中共 55 处裸 JDK 异常。关键路径（CheckpointPlanBuilder、JobCoordinator、TaskManager）上的 `IllegalStateException` 与上层 `catch(StreamException)` 不匹配。
- **风险**: 异常穿透，影响错误报告和恢复逻辑。
- **建议**: CheckpointPlanBuilder 第 77、88 行改为 `StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_JOB_GRAPH_INVALID).param(ARG_VERTEX_ID, vertexId)`。
- **信心水平**: 高
- **误报排除**: 已排除 API 桩的 `UnsupportedOperationException`（属于 JDK 惯例，可接受）。
- **复核状态**: 未复核

---

### [维度09-06] MalformedPatternException 仅提供 String 构造器

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/pattern/MalformedPatternException.java:27-33`
- **证据片段**:
  ```java
  public class MalformedPatternException extends StreamRuntimeException {
      public MalformedPatternException(String message) {
          super(message);
      }
  }
  ```
- **严重程度**: P3
- **现状**: CEP Pattern API 的公共异常类只提供 String 构造器，不提供 ErrorCode 构造器。`NopCepErrors` 已定义了对应 ErrorCode。
- **风险**: 调用方无法通过 `getErrorCode()` 区分 Pattern 不同的构造错误类型。
- **建议**: 增加 `MalformedPatternException(ErrorCode)` 构造器。
- **信心水平**: 中高
- **误报排除**: 继承体系正确，问题仅在于构造器不完整。
- **复核状态**: 未复核

---

## 检查范围说明

- 无空 catch 块 ✓
- 无中文硬编码错误消息 ✓
- 日志使用 SLF4J 规范 ✓（PrintSink/PrintSink 中的 System.out 是功能需求）
- 异常链保留良好 ✓
