# 维度 09：错误处理与错误码

## 第 1 轮（初审）

### [维度09-01] MalformedPatternException 全部 13 处调用未传递 .param(ARG_PATTERN_DETAIL)，错误消息无诊断价值

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/pattern/Pattern.java:245,250,309,337,641,647,660,667` 及 `NFACompiler.java:191,216,232` 及 `NFAStateNameHandler.java:60` 及 `Quantifier.java:87`
- **证据片段**:
  ```java
  // Pattern.java:244-246
  if (this.untilCondition != null) {
      throw new MalformedPatternException(ERR_CEP_MALFORMED_PATTERN);
      // 缺少 .param(ARG_PATTERN_DETAIL, "untilCondition already set")
  }
  ```
  ```java
  // NFACompiler.java:190-191
  throw new MalformedPatternException(ERR_CEP_MALFORMED_PATTERN); // 缺少 .param
  ```
  ```java
  // Quantifier.java:85-87
  public Quantifier combinationsReached() {
      throw new MalformedPatternException(ERR_CEP_MALFORMED_PATTERN); // 缺少 .param
  }
  ```
- **严重程度**: P2
- **现状**: 错误码 `ERR_CEP_MALFORMED_PATTERN` 定义了模板消息 `"Malformed CEP pattern: {patternDetail}"`，但全部 13 处调用均未填充 `ARG_PATTERN_DETAIL`，导致异常消息中 `{patternDetail}` 为 null。
- **风险**: 用户遇到 CEP 模式定义错误时无法判断具体是哪个约束被违反，排查困难。
- **建议**: 为每处调用添加 `.param(ARG_PATTERN_DETAIL, "具体原因描述")`，如 `.param(ARG_PATTERN_DETAIL, "until() requires LOOPING or TIMES quantifier")`。
- **信心水平**: 确定
- **误报排除**: 不是误报——错误码模板明确要求 patternDetail 参数，13 处全部缺失是系统性问题。
- **复核状态**: 未复核

### [维度09-02] CEP 子模块核心类使用原生 IllegalStateException/UnsupportedOperationException（共 7 处）

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/aftermatch/NoSkipStrategy.java:43,48` 及 `RichIterativeCondition.java:56,62` 及 `GroupPattern.java:46,51,56`
- **证据片段**:
  ```java
  // NoSkipStrategy.java:42-49
  @Override
  protected boolean shouldPrune(EventId startEventID, EventId pruningId) {
      throw new IllegalStateException("This should never happen. Please file a bug.");
  }
  @Override
  protected EventId getPruningId(Collection<Map<String, List<EventId>>> match) {
      throw new IllegalStateException("This should never happen. Please file a bug.");
  }
  ```
  ```java
  // GroupPattern.java:44-57
  @Override
  public Pattern<T, F> where(IterativeCondition<F> condition) {
      throw new UnsupportedOperationException("GroupPattern does not support where clause.");
  }
  @Override
  public Pattern<T, F> or(IterativeCondition<F> condition) {
      throw new UnsupportedOperationException("GroupPattern does not support or clause.");
  }
  @Override
  public <S extends F> Pattern<T, S> subtype(final Class<S> subtypeClass) {
      throw new UnsupportedOperationException("GroupPattern does not support subtype clause.");
  }
  ```
  ```java
  // RichIterativeCondition.java:52-64
  @Override
  public RuntimeContext getRuntimeContext() {
      if (this.runtimeContext != null) {
          return this.runtimeContext;
      } else {
          throw new IllegalStateException("The runtime context has not been initialized.");
      }
  }
  @Override
  public IterationRuntimeContext getIterationRuntimeContext() {
      throw new UnsupportedOperationException(
              "Not support to get the IterationRuntimeContext in IterativeCondition.");
  }
  ```
- **严重程度**: P2
- **现状**: nop-stream 作为框架层模块，CEP 子模块的核心类使用了 JDK 原生异常而非 ErrorCode 模式。
- **风险**: 异常不会被 Nop 平台的统一异常处理机制捕获和格式化，无法提供错误码和结构化上下文。
- **建议**: 改用 `StreamRuntimeException(ERR_CEP_INVALID_STATE).param(ARG_DETAIL, "...")` 或 `StreamRuntimeException(ERR_CEP_UNSUPPORTED).param(ARG_OPERATION, "...")`。
- **信心水平**: 很可能
- **误报排除**: 不是误报——这是框架核心代码，应遵循 Nop 平台的 ErrorCode 两档策略。同模块的 NFA.java 和 SharedBuffer.java 都正确使用了 ErrorCode 模式，说明 CEP 子模块内部存在不一致。
- **复核状态**: 未复核

### [维度09-03] nop-stream-core 接口默认方法使用原生 UnsupportedOperationException（共 5 处）

- **文件**: `nop-stream-core/.../RecordWriter.java:128` 及 `Trigger.java:104` 及 `WindowAggregationFunction.java:16` 及 `ICheckpointExecutorFactory.java:81` 及 `FunctionUtils.java:69`
- **证据片段**:
  ```java
  // RecordWriter.java:127-130
  if (policy != FlowControlPolicy.BLOCKING_QUEUE) {
      throw new UnsupportedOperationException(
              "Flow control policy " + policy + " is not supported in local runtime");
  }
  ```
  ```java
  // Trigger.java:103-105
  public void onMerge(W window, OnMergeContext ctx) {
      throw new UnsupportedOperationException("This trigger does not support merging.");
  }
  ```
- **严重程度**: P3
- **现状**: 接口默认方法和工具类构造器中使用 JDK 原生异常。
- **风险**: 影响较小——这些是"不支持的操作"的标准 Java 模式，异常消息清晰。但从 Nop 平台规范性角度，应使用 ErrorCode 模式。
- **建议**: 长期统一为 ErrorCode 模式，短期可接受。
- **信心水平**: 确定
- **误报排除**: 不是误报，但影响较小。
- **复核状态**: 未复核

### [维度09-04] fraud-example demo 模块使用裸 IllegalArgumentException/UnsupportedOperationException（共 11 处）

- **文件**: `nop-stream-fraud-example/.../UnusualAmountPattern.java:135` 及 `GeographicAnomalyPattern.java:120` 及 `AccountTakeoverPattern.java:143,153,158,161,164` 及 `RapidTransactionPattern.java:120` 及 `DemoKeyedStateStore.java:63,69`
- **证据片段**:
  ```java
  // UnusualAmountPattern.java:134-136
  if (transactionEvents == null || transactionEvents.isEmpty()) {
      throw new IllegalArgumentException("Match must contain 'transaction' event");
  }
  ```
  ```java
  // AccountTakeoverPattern.java:140-164
  throw new IllegalArgumentException("Match must contain 'login', 'change', and 'withdraw' events");
  throw new IllegalArgumentException("All events must be from the same user");
  ```
- **严重程度**: P3
- **现状**: fraud-example 是 demo 模块，但代码位于 src/main/java 下，使用了裸异常。
- **风险**: Demo 模块影响范围有限。
- **建议**: 作为代码质量改进，统一使用 StreamRuntimeException + ErrorCode。
- **信心水平**: 确定
- **误报排除**: 不是误报，但作为 demo 模块优先级较低。
- **复核状态**: 未复核

### [维度09-05] CepPatternBuilder 使用 NopException.adapt(e) 缺少业务上下文

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java:155-161`
- **证据片段**:
  ```java
  // CepPatternBuilder.java:155-161
  private Pattern addQualifier(Pattern pattern, CepPatternPartModel partModel) {
      if (partModel.getSubType() != null) {
          try {
              pattern.subtype(ClassHelper.safeLoadClass(partModel.getSubType()));
          } catch (Exception e) {
              throw NopException.adapt(e); // 缺少上下文：应附加 subType 类名
          }
      }
  ```
- **严重程度**: P3
- **现状**: `NopException.adapt(e)` 保留了异常链但不附加业务上下文（如哪个 subType 加载失败）。
- **风险**: 排查时无法确定是哪个类加载失败。
- **建议**: 改为 `throw NopException.adapt(e).param(ARG_SUBTYPE, partModel.getSubType())`。
- **信心水平**: 确定
- **误报排除**: 不是误报——NopException.adapt() 的设计就是为了在保留异常链的同时附加上下文参数。
- **复核状态**: 未复核

### [维度09-06] PrintSink / PrintSinkFunction 使用 System.out 而非 SLF4J

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/operators/PrintSink.java:39-41` 及 `PrintSinkFunction.java:55-60`
- **证据片段**:
  ```java
  // PrintSink.java:39-41
  public void consume(T value) throws Exception {
      System.out.println(prefix + value);
  }
  ```
  ```java
  // PrintSinkFunction.java:55-60
  public void consume(T value) throws Exception {
      if (prefix != null) {
          System.out.println(prefix + ": " + value);
      } else {
          System.out.println(value);
      }
  }
  ```
- **严重程度**: P3
- **现状**: 调试用 Sink 使用 System.out 输出，而非 SLF4J。
- **风险**: 生产环境无法控制日志级别和输出目标。但 PrintSink 的设计意图就是 "print to stdout"（类似 Flink 的 PrintSinkFunction），功能正确。
- **建议**: 保持现状即可，或在 Javadoc 中明确标注"This sink writes to stdout for debugging purposes, not for production use"。
- **信心水平**: 确定
- **误报排除**: 边缘情况——PrintSink 是 Flink 兼容的调试 Sink，System.out 在此场景下是功能需求而非日志误用。
- **复核状态**: 未复核

## 合规亮点

1. 错误码定义规范：NopStreamErrors 和 NopCepErrors 共定义 40+ 错误码
2. 异常继承体系正确：StreamRuntimeException → NopException
3. .param() 使用广泛（如 ChainingOutput.java 的 5 处 catch 块）
4. InterruptedException 处理规范（4 处均调用 Thread.currentThread().interrupt()）
5. 异常链保留良好
6. 无空 catch 块、无 e.printStackTrace()、无中文错误消息
