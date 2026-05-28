# 维度 09：错误处理与错误码

## 第 1 轮（初审）

### [维度09-01] MalformedPatternException 缺少 ErrorCode 构造器

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/pattern/MalformedPatternException.java:27-34`
- **证据片段**:
```java
public class MalformedPatternException extends StreamRuntimeException {
    public MalformedPatternException(String message) {
        super(message);
    }

    public MalformedPatternException(String message, Throwable cause) {
        super(message, cause);
    }
    // 缺少 ErrorCode 构造器
}
```
- **严重程度**: P3
- **现状**: `MalformedPatternException` 仅有 `(String)` 和 `(String, Throwable)` 构造器，缺少 `(ErrorCode)` 构造器。当 CEP 公共 API（如 `NFACompiler`）需要抛出带 ErrorCode 的模式错误时，无法使用此异常类。
- **风险**: CEP 公共 API 的错误码追踪链断裂。
- **建议**: 为 `MalformedPatternException` 增加 `MalformedPatternException(ErrorCode errorCode)` 和 `MalformedPatternException(ErrorCode errorCode, Throwable cause)` 构造器。
- **误报排除**: 这是两档策略下对公共 API 层异常类的合规性检查，不是泛化的"所有类都需要所有构造器"。
- **复核状态**: 未复核

### [维度09-02] nop-stream-core 的 ErrorCode 使用规范 — 正面确认

- **现状**: `NopStreamErrors` 定义了 11 个标准 ErrorCode。core 主代码中有 105 处 `throw new StreamException(ERR_xxx).param(...)` 调用。所有 ErrorCode 均使用 `.param()` 模式传递上下文参数。完全符合两档策略。

### [维度09-03] NopCepErrors 定义规范 — 正面确认

- **现状**: `NopCepErrors` 定义了 3 个 ErrorCode，在 `CepPatternBuilder` 和 `ICepPatternGroupModel` 中正确使用。

### [维度09-04] nop-stream-runtime 完全没有 ErrorCode 定义

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java` 等多个文件
- **证据片段**:
```java
// GraphModelCheckpointExecutor.java:324
throw new StreamException("Failed to trigger terminal savepoint", e);
// :446
throw new StreamException("No TaskLocation found for vertex " + vertexName);
// :532
throw new StreamException("Task failed");
// CheckpointPlanBuilder.java:61
throw new StreamException("vertexName must not be null");
// SourceEnumerator.java:63
throw new StreamException("totalParallelism must be positive, got: " + totalParallelism);
```
- **严重程度**: P2
- **现状**: nop-stream-runtime 模块的主代码中 **0 处** 使用 `ErrorCode` 模式，所有 15 处 `StreamException` 抛出均使用字符串构造器。其中 `CheckpointPlanBuilder`、`DeploymentPlanGenerator`、`SourceEnumerator` 中的参数校验错误性质上与 `NopStreamErrors` 中已有的 `ERR_STREAM_NULL_ARG` / `ERR_STREAM_INVALID_ARG` 相同。
- **风险**: runtime 模块的错误无法通过错误码追踪和分类，降低可观测性。参数校验等可复用 ErrorCode 的场景被硬编码为英文字符串。
- **建议**: 为 runtime 模块新增 `NopStreamRuntimeErrors`，或复用 `NopStreamErrors` 中已有的 ErrorCode（如 `ERR_STREAM_NULL_ARG`、`ERR_STREAM_INVALID_ARG`、`ERR_STREAM_INVALID_STATE`、`ERR_STREAM_CHECKPOINT_ERROR`），将字符串构造器调用替换为 ErrorCode + `.param()` 调用。
- **误报排除**: runtime 模块包含分布式执行核心路径（Checkpoint、TaskManager、JobCoordinator），这些是跨模块公共 API 级别的代码，两档策略要求使用 ErrorCode。
- **复核状态**: 未复核

### [维度09-05] nop-stream-connector 全部使用字符串构造器

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/` 下多个文件
- **证据片段**:
```java
// BatchConsumerSinkFunction.java:42,45
throw new StreamException("consumerProvider must not be null");
throw new StreamException("batchSize must be at least 1");
// MessageSinkFunction.java:30,33
throw new StreamException("messageService must not be null");
throw new StreamException("topic must not be null or empty");
```
- **严重程度**: P3
- **现状**: nop-stream-connector 的 11 处异常抛出全部使用字符串构造器。这些主要是参数校验，性质上属于公共 API 层面的契约检查。
- **风险**: 错误不可追踪，无法通过错误码分类。
- **建议**: 复用 `NopStreamErrors.ERR_STREAM_NULL_ARG` / `ERR_STREAM_INVALID_ARG` 替换字符串构造器。
- **误报排除**: connector 是对外暴露的连接器 API，参数校验应使用 ErrorCode 模式。
- **复核状态**: 未复核

### [维度09-06] nop-stream-cep 内部实现使用字符串构造器 — 可接受

- **现状**: NFA、SkipToElementStrategy 等内部实现中有 5 处使用字符串构造器，但这是模块内部代码，在两档策略下可接受。错误消息已全部为英文。

### [维度09-07] 主代码中 24 处 IllegalStateException 未通过模块级异常类包装

- **文件**: `nop-stream/nop-stream-core` 和 `nop-stream/nop-stream-runtime` 多个文件
- **证据片段**:
```java
// JobCoordinator.java:213
throw new IllegalStateException("No RPC service for node " + nodeId);
// :276
throw new IllegalStateException("No RPC services available for checkpoint trigger");
// WindowAggregationOperator.java:4处（状态恢复校验）
// MemoryKeyedStateBackend.java:2处（命名空间校验）
// MergingWindowSet.java:1处（窗口状态不一致）
// CheckpointPlanBuilder.java:2处（顶点查找失败）
```
- **严重程度**: P2
- **现状**: core 和 runtime 主代码中共 24 处 `throw new IllegalStateException(...)` 未通过 `StreamException` 包装。其中 `JobCoordinator` 的 RPC 服务不可用错误和 `CheckpointPlanBuilder` 的顶点查找失败属于分布式执行核心错误路径，应使用 `StreamException(ERR_STREAM_INVALID_STATE).param(...)` 以便错误码可追踪。
- **风险**: `IllegalStateException` 无法被框架的异常处理链正确捕获和分类，错误码不可追踪、不可结构化。
- **建议**: 将这些 `IllegalStateException` 替换为 `StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "...")` 或 `StreamException(ERR_STREAM_UNSUPPORTED).param(...)`。
- **误报排除**: 这不是泛化的"不用 JDK 异常"——关键是分布式执行核心路径的错误需要错误码追踪能力。部分位置（如状态校验）确实可以用 ISE，但 24 处中有至少 10 处属于公共 API 或核心执行路径。
- **复核状态**: 未复核

### [维度09-08] 主代码中 15 处 UnsupportedOperationException — 可接受

- **现状**: 15 处 `UnsupportedOperationException` 分布在 Pattern、GroupPattern、KeyedStreamImpl 等文件中，主要用于标记"不支持的操作"。部分可替换为 `StreamException(ERR_STREAM_UNSUPPORTED)` 以保持一致性，但当前可接受。

### [维度09-09] 异常链保留良好 — 正面确认

- **现状**: `StreamExecutionEnvironment.execute()`、`GraphModelCheckpointExecutor`、`NFA.java`、`CepOperator.java` 等关键路径的 catch-and-rethrow 均正确保留了 cause。

### [维度09-10] TaskExecutor.awaitCompletion() 吞没任务异常

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/TaskExecutor.java:291-296`
- **证据片段**:
```java
} catch (Exception e) {
    LOG.debug("Task {} completed with exception", entry.getKey(), e);
}
```
- **严重程度**: P3
- **现状**: `awaitCompletion()` 在 `Future.get()` 抛出异常时仅以 `LOG.debug` 记录。实际不是 bug，因为调用方会在之后遍历 `SubtaskTask` 列表检查 `task.getError()` 并抛出。但此行为容易被误读为吞没异常。
- **风险**: 后续开发者可能误认为异常被吞没。
- **建议**: 在 `awaitCompletion()` 的 Javadoc 中添加说明，解释异常已被记录在 `SubtaskTask.error` 中，由调用方统一检查。
- **误报排除**: 这不是真正的异常吞没——调用方有正确的后续检查。但缺少文档说明。
- **复核状态**: 未复核

### [维度09-11] 日志框架使用规范 — 正面确认

- **现状**: 全模块 54 个文件正确使用 SLF4J。未发现 `e.printStackTrace()` 调用。所有 catch 块中的异常均通过 `LOG.error/warn(..., e)` 记录。`System.out` 仅用于 `PrintSinkFunction`（语义上就是打印）和 `FraudDetectionDemo`（示例代码），完全合理。
