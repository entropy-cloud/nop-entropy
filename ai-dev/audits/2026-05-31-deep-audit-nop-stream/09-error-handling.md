# 维度 09：错误处理与错误码

## 第 1 轮（初审）

### [维度09-01] 生产代码中使用裸 UnsupportedOperationException 代替 StreamException

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/execution/RecordWriter.java:128`
- **证据片段**:
  ```java
  throw new UnsupportedOperationException(
      "Flow control policy " + policy + " is not supported in local runtime");
  ```
- **严重程度**: P3
- **现状**: 使用裸 JDK 异常，不经过模块异常体系。
- **风险**: 调用方无法通过 StreamRuntimeException 统一捕获。
- **建议**: 改为 throw new StreamException(ERR_STREAM_UNSUPPORTED).param(...)
- **信心水平**: 确定
- **误报排除**: 模块其他位置已正确使用 ERR_STREAM_UNSUPPORTED。
- **复核状态**: 未复核

### [维度09-02] SkipToElementStrategy 使用裸 IllegalStateException

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/aftermatch/SkipToElementStrategy.java:77-78`
- **证据片段**:
  ```java
  .orElseThrow(
      () -> new IllegalStateException("Cannot prune based on empty match"));
  ```
- **严重程度**: P3
- **现状**: 同文件第 81 行已正确使用 StreamException(ERR_STREAM_SKIP_NO_MATCH)。
- **风险**: 不经过模块异常体系。
- **建议**: 改为 StreamException。
- **信心水平**: 确定
- **误报排除**: 同文件已有正确模式。
- **复核状态**: 未复核

### [维度09-03] API 契约性默认方法使用裸 UnsupportedOperationException（5 处）

- **文件**: Trigger.java:104, WindowAggregationFunction.java:16, FunctionUtils.java:69, RichIterativeCondition.java:62, RichFunction.java
- **严重程度**: P3
- **现状**: 接口/抽象类默认方法使用裸 UnsupportedOperationException 表示"子类必须覆盖"。这是标准 Java 惯用法（模板方法模式）。
- **风险**: 极低。标准 Java 惯用法。
- **建议**: 维持现状。
- **信心水平**: 确定
- **误报排除**: 模板方法模式的标准实现。
- **复核状态**: 未复核

### [维度09-04] fraud-example 模块大量使用裸 IllegalArgumentException

- **文件**: AccountTakeoverPattern.java:143,153,158,161,164; UnusualAmountPattern.java:135; GeographicAnomalyPattern.java:120; DemoKeyedStateStore.java:63,69
- **严重程度**: P3
- **现状**: demo/示例代码中使用裸 JDK 异常。
- **风险**: 作为示例代码，用户可能复制这些模式。
- **建议**: fraud-example 是面向用户的参考代码，建议统一使用 StreamException。
- **信心水平**: 确定
- **误报排除**: 这是 nop-stream-fraud-example 示例模块，非核心生产代码。
- **复核状态**: 未复核

## 正面发现

- 异常层次清晰：StreamRuntimeException -> NopException, StreamException 和 CheckpointStorageException 分层合理
- ErrorCode 定义完整：NopStreamErrors 定义 38 个 ErrorCode，NopCepErrors 定义 10 个
- 异常链保留良好
- 异常参数化规范
- 无中文错误消息
- InterruptedException 处理规范（先 interrupt() 再包装）
- 日志规范（全部 SLF4J，无生产代码 System.out）

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 09-01 | P3 | RecordWriter.java | 裸 UnsupportedOperationException |
| 09-02 | P3 | SkipToElementStrategy.java | 裸 IllegalStateException |
| 09-03 | P3 | 5 处 | API 契约性裸异常（标准惯用法） |
| 09-04 | P3 | fraud-example | 示例代码裸异常 |
