# 维度 09：错误处理与错误码

## 审计范围

nop-stream 全部 5 个有代码的子模块（core、runtime、cep、connector、fraud-example）。

## 模块错误处理架构

- **异常层级**: NopException → StreamRuntimeException → StreamException / MalformedPatternException
- **ErrorCode 定义**: NopStreamErrors（30个，core）+ NopCepErrors（9个，cep）
- **ErrorCode 命名规范**: 遵循 nop.err.stream.{description} / nop.err.cep.{description} 格式
- **自动化测试**: TestErrorCodeMessagesEnglish.java + TestNopCepErrorsMessagesEnglish.java

## 第 1 轮（初审）

### [维度09-01] 大量 throw 使用字符串构造器而非 ErrorCode（框架核心模块）

- **文件**: 多个文件，包括 DataStreamImpl.java:344、StreamSourceOperator.java:82,177,188、MemoryReducingState.java:51、MemoryInternalAppendingState.java:45、RecordWriter.java:146,161,177,199、StreamModelFingerprint.java:153、StreamExecutionEnvironment.java:280,288、SkipToElementStrategy.java:79、SkipToFirstStrategy.java:39、SkipToLastStrategy.java:39
- **证据片段**:
```java
// DataStreamImpl.java:344
throw new StreamException("Failed to extract key for partitioning", e);

// RecordWriter.java:146
throw new StreamException("Interrupted while writing record", e);

// SkipToFirstStrategy.java:39
throw new StreamException("null name");
```
- **严重程度**: P2
- **现状**: nop-stream 是框架核心模块，按两档策略应使用 ErrorCode 模式。共发现 16 处在 src/main/java 中使用 `new StreamException(String)` 或 `new StreamException(String, Throwable)` 构造器。
- **风险**: 缺少结构化错误码，不利于下游捕获和国际化。其中 `RecordWriter.java` 的 4 处字符串构造器属于核心 I/O 路径，应优先使用 ErrorCode。
- **建议**: 为高频出现的错误场景（如 "Interrupted while writing *"、"Failed to create accumulator"等）在 NopStreamErrors 中新增对应 ErrorCode，逐步替换字符串构造器。
- **正面**: 所有字符串消息均为英文且语义清晰；异常链均被正确保留。
- **信心水平**: 确定
- **误报排除**: 不是审美问题——作为框架核心模块，ErrorCode 是项目规范要求的（docs-for-ai/02-core-guides/error-handling.md）。
- **复核状态**: 未复核

### [维度09-02] 生产代码中使用 IllegalStateException/UnsupportedOperationException 而非模块异常类

- **文件**: WindowOperator.java:416,818、JobCoordinator.java:220,283、TaskManager.java:320、MergingWindowSet.java:133、RichIterativeCondition.java:56、NoSkipStrategy.java:43,48 等
- **证据片段**:
```java
// WindowOperator.java:416
throw new IllegalStateException("Window " + window + " is not in in-flight window set.");

// JobCoordinator.java:220
throw new IllegalStateException("No RPC service for node " + node);

// NoSkipStrategy.java:43
throw new IllegalStateException("This should never happen. Please file a bug.");
```
- **严重程度**: P2
- **现状**: 10 处 IllegalStateException、22 处 UnsupportedOperationException、8 处 IllegalArgumentException。其中 WindowOperator、JobCoordinator、TaskManager 等属于框架核心运行时组件，应使用 StreamException(ERR_STREAM_*)。
- **风险**: Java 标准异常不携带 ErrorCode，无法被结构化地捕获和处理。
- **建议**: 框架核心路径（WindowOperator、JobCoordinator、TaskManager、MergingWindowSet）中的 IllegalStateException 应替换为 StreamException(ERR_STREAM_INVALID_STATE)。UnsupportedOperationException 中一部分属于 Java API 设计惯例（"有意不支持"），可酌情保留。
- **信心水平**: 确定
- **误报排除**: 部分 UnsupportedOperationException（如 GroupPattern、Trigger 的 "不支持合并"）属于 Java API 设计惯例，但核心运行时路径（WindowOperator、JobCoordinator）中的 IllegalStateException 是真实的不一致。
- **复核状态**: 未复核

### [维度09-03] MalformedPatternException 仅提供 String 构造器，缺少 ErrorCode 构造器

- **文件**: `nop-stream-cep/.../pattern/MalformedPatternException.java`
- **证据片段**:
```java
// MalformedPatternException.java — 只有 String 构造器
public MalformedPatternException(String message) {
    super(message);
}

// Pattern.java:243
throw new MalformedPatternException("Only one until condition can be applied.");
```
- **严重程度**: P3
- **现状**: MalformedPatternException 继承自 StreamRuntimeException，但只暴露了 String 构造器。CEP 模块已有 NopCepErrors 定义了专门的 ErrorCode。11 处 throw 调用均使用英文消息。
- **风险**: CEP 模式构建阶段的客户端错误无法被结构化捕获。影响有限（非运行时错误）。
- **建议**: 低优先级。可为 MalformedPatternException 添加 ErrorCode 构造器，并在 NopCepErrors 中为常见模式错误定义对应 ErrorCode。
- **信心水平**: 很可能
- **误报排除**: 从 Flink 移植的 API，保持一致性有合理性，但缺少 ErrorCode 构造器确实是不完整。
- **复核状态**: 未复核

### [维度09-04] fraud-example 模块使用 IllegalArgumentException 而非模块异常类

- **文件**: AccountTakeoverPattern.java:143,153,158,161,164、GeographicAnomalyPattern.java:120、RapidTransactionPattern.java:120、UnusualAmountPattern.java:135
- **证据片段**:
```java
// AccountTakeoverPattern.java:143
throw new IllegalArgumentException("Match must contain 'login', 'change', and 'withdraw' events");
```
- **严重程度**: P3
- **现状**: fraud-example 是示例模块，8 处 IllegalArgumentException 用于验证模式匹配结果。作为示例代码可接受，但如果被复制为生产起点，可能传播不一致的模式。
- **建议**: 在模块 README 或代码注释中说明这是示例代码的简化写法。
- **信心水平**: 确定
- **误报排除**: 示例代码中使用 IllegalArgumentException 是 Java 惯例，不是严重问题。
- **复核状态**: 未复核

### 正面发现

- **ErrorCode 命名规范完全合规**: 所有 ErrorCode 遵循 nop.err.stream.{description} / nop.err.cep.{description} 格式。
- **异常链保留正确**: 所有 catch 后 re-throw 均正确传入 cause。未发现空 catch 块。
- **InterruptedException 处理规范**: 所有 catch (InterruptedException e) 均调用了 Thread.currentThread().interrupt() 恢复中断状态。
- **日志使用规范**: 58 个文件使用 SLF4J。System.out 仅出现在 PrintSink（设计目的）和 fraud-example（示例代码）。
- **无中文错误消息**: src/main/java 中搜索中文字符返回零结果。已有自动化测试验证。

### 统计摘要

| 指标 | 数量 |
|------|------|
| 生产代码总 throw 语句 | ~264 |
| 使用 ErrorCode 的 throw | ~195 |
| 使用字符串构造器的 StreamException | 16 |
| 使用 MalformedPatternException(String) | 11 |
| 使用 IllegalStateException | 10 |
| 使用 UnsupportedOperationException | 22 |
| 使用 IllegalArgumentException | 8 |
| ErrorCode 定义总数 | 39 |
| 空 catch 块 | 0 |
| 中文错误消息 | 0 |
