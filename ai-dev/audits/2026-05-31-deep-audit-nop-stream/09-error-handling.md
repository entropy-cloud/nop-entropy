# 维度 09：错误处理与错误码

## 第 1 轮（初审）

### [维度09-01] SharedBuffer 中 NopException.adapt() 丢失模块异常类型和诊断上下文

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBuffer.java:117-126,139-157,190-194,264-272,282-295,304-319`
- **证据片段**:
  ```java
  private void copyEntries(MapState<NodeId, Lockable<SharedBufferNode>> state) throws Exception {
      state.entries().forEach(e -> {
          try {
              entries.put(e.getKey(), e.getValue());
          } catch (Exception exception) {
              throw NopException.adapt(exception);
          }
      });
  }
  ```
- **严重程度**: P1
- **现状**: SharedBuffer 有 7 处 `NopException.adapt(e)`，将 checked exception 包装为通用 NopException，丢弃了模块特定的 StreamException 类型。`ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED` ErrorCode 已定义但从未使用。
- **风险**: 调用方 catch StreamException 时会漏掉这些异常。状态访问失败未被分类，影响诊断和监控。
- **建议**: 替换为 `throw new StreamException(ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED, e)`。
- **信心水平**: 确定
- **误报排除**: 模块已有专门的 ErrorCode 和正确使用的同类模式（NFA.java:794 用 `new StreamException(ERR_CEP_NFA_FILTER_EXECUTION_FAILED, e)`）。
- **复核状态**: 未复核

### [维度09-02] CepOperator 中 NopException.adapt() 丢失模块异常类型

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:229-234,362-370,396-404`
- **证据片段**:
  ```java
  getProcessingTimeService().registerTimer(time, t -> {
      try {
          onProcessingTime(t);
      } catch (Exception e) {
          throw NopException.adapt(e);
      }
  });
  ```
- **严重程度**: P1
- **现状**: CepOperator 事件处理管道中有 3 处 NopException.adapt(e)。
- **风险**: CEP 核心热路径上的状态访问失败、NFA 处理错误或定时器回调失败被包装为通用 NopException，下游 catch StreamException 会漏掉，可能绕过 checkpoint/recovery 逻辑。
- **建议**: 替换为 `throw new StreamException(ERR_STREAM_OPERATOR_ERROR, e).param(ARG_OPERATOR_NAME, "CepOperator")`。
- **信心水平**: 确定
- **误报排除**: 同 [维度09-01] 的理由。
- **复核状态**: 未复核

### [维度09-03] SharedBufferAccessor 中 NopException.adapt() 丢失模块异常类型

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBufferAccessor.java:216-221`
- **证据片段**:
  ```java
  for (EventId eventId : pattern.getValue()) {
      try {
          V event = sharedBuffer.getEvent(eventId).getElement();
          events.add(event);
      } catch (Exception ex) {
          throw NopException.adapt(ex);
      }
  }
  ```
- **严重程度**: P1
- **现状**: CEP match 物化路径中 1 处 NopException.adapt(ex)。
- **风险**: 物化 CEP 匹配结果时的失败被包装为通用 NopException，用户可见的操作缺少正确的错误分类。
- **建议**: 替换为 `throw new StreamException(ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED, ex)`。
- **信心水平**: 确定
- **误报排除**: ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED 已存在且设计用于此场景。
- **复核状态**: 未复核

### [维度09-04] CepPatternBuilder 中 NopException.adapt() 丢失模块异常类型

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java:156-161`
- **证据片段**:
  ```java
  try {
      pattern.subtype(ClassHelper.safeLoadClass(partModel.getSubType()));
  } catch (Exception e) {
      throw NopException.adapt(e);
  }
  ```
- **严重程度**: P1
- **现状**: 类加载失败被包装为通用 NopException。
- **风险**: CEP 模型引用的类无法加载时，错误消息丢失"模式构建"上下文。
- **建议**: 替换为 `throw new MalformedPatternException(ERR_CEP_MALFORMED_PATTERN, e).param(ARG_PATTERN_DETAIL, "Failed to load subtype: " + partModel.getSubType())`。
- **信心水平**: 确定
- **误报排除**: 类加载失败属于模式配置错误，MalformedPatternException 已存在。
- **复核状态**: 未复核

### [维度09-05] NFACompiler 使用 bare IllegalStateException -- 框架核心应使用模块异常

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java:106-109`
- **证据片段**:
  ```java
  .orElseThrow(() -> new IllegalStateException(
      "Compiler produced no start state. This is a bug in NFAFactoryCompiler."));
  ```
- **严重程度**: P1
- **现状**: NFACompiler 内部一致性检查失败抛出 bare IllegalStateException。
- **风险**: 调用方 catch StreamException 或 MalformedPatternException 会漏掉。JDK 异常在日志/指标中无法与框架错误区分。
- **建议**: 替换为 `throw new MalformedPatternException(ERR_CEP_MALFORMED_PATTERN).param(ARG_PATTERN_DETAIL, "Compiler produced no start state")`。
- **信心水平**: 很可能
- **误报排除**: NFACompiler 是模式编译的公共入口点，应抛出模块类型异常。MalformedPatternException 已存在。
- **复核状态**: 未复核

### [维度09-06] NoSkipStrategy "should never happen" 分支使用 bare IllegalStateException

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/aftermatch/NoSkipStrategy.java:41-49`
- **证据片段**:
  ```java
  @Override
  protected boolean shouldPrune(EventId startEventID, EventId pruningId) {
      throw new IllegalStateException("This should never happen. Please file a bug.");
  }
  ```
- **严重程度**: P1
- **现状**: 两个抽象方法覆写作为 unreachable-code guards 抛出 bare IllegalStateException。
- **风险**: 如果 bug 导致这些代码被触发，JDK 异常无法被 StreamException 处理器捕获。"should never happen" 条件恰恰最需要结构化错误码（用于告警和诊断）。
- **建议**: 替换为 `throw new StreamException(ERR_STREAM_UNSUPPORTED).param(ARG_OPERATION, "NoSkipStrategy.shouldPrune/getPruningId")`。
- **信心水平**: 确定
- **误报排除**: 同一继承体系的 SkipToElementStrategy:81 正确使用了 `StreamException(ERR_STREAM_SKIP_NO_MATCH)`。
- **复核状态**: 未复核

### [维度09-07] SkipToElementStrategy 使用 bare IllegalStateException

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/aftermatch/SkipToElementStrategy.java:70-78`
- **证据片段**:
  ```java
  .orElseThrow(() -> new IllegalStateException("Cannot prune based on empty match"));
  ```
- **严重程度**: P2
- **现状**: 空匹配条件产生 bare IllegalStateException。
- **风险**: CEP 模式产生空匹配时，JDK 异常无法被流引擎错误处理捕获。
- **建议**: 替换为 `throw new StreamException(ERR_STREAM_SKIP_NO_MATCH)`。
- **信心水平**: 很可能
- **误报排除**: 同文件 line 81 已使用 StreamException(ERR_STREAM_SKIP_NO_MATCH)，不一致。
- **复核状态**: 未复核

### [维度09-08] NFACompiler 使用全限定类名代替 import

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java:491-497`
- **证据片段**:
  ```java
  throw new io.nop.stream.core.exceptions.StreamException(
      io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR)
      .param(io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL,
              "Circular PROCEED dependency detected at state: " + sinkState.getName());
  ```
- **严重程度**: P2
- **现状**: 使用全限定类名而非 import。ErrorCode + .param() 使用正确，但 FQN 降低可读性。
- **风险**: 代码可维护性问题。
- **建议**: 添加 import 并使用短名。考虑使用 CEP 专用 ErrorCode。
- **信心水平**: 确定
- **误报排除**: 客观上是代码质量问题，3 行全限定引用不是正常编码模式。
- **复核状态**: 未复核

### [维度09-09] JobCoordinator 终止路径静默吞掉错误

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:501-503,524-526,547-549`
- **证据片段**:
  ```java
  } catch (Exception e) {
      LOG.error("DRAIN: failed to complete final checkpoint for job {}", jobId, e);
  }
  stop();
  ```
- **严重程度**: P2
- **现状**: 三个终止方法（terminateDrain/terminateSuspend/terminateExportSavepoint）catch 所有异常仅 log 后继续 stop()。
- **风险**: savepoint 失败时调用方无法知道保存点丢失。stop() 后状态被清理但保存点未持久化——恢复时可能数据丢失。
- **建议**: 至少将失败存储在字段中（如 getLastTerminationError()），或返回结果对象指示 savepoint 成功/失败。
- **信心水平**: 很可能
- **误报排除**: 终止方法被更高级编排调用，应在清理前知道 savepoint 是否成功。
- **复核状态**: 未复核

### [维度09-10] TaskManager.sendCheckpointAck 静默吞掉 checkpoint ACK 失败

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:317-336`
- **证据片段**:
  ```java
  try {
      if (coordinatorRpcService != null) {
          coordinatorRpcService.receiveCheckpointAck(ack);
      } else {
          throw new StreamException(ERR_STREAM_INVALID_STATE).param(...);
      }
  } catch (Exception e) {
      LOG.error("Failed to send checkpoint ACK for checkpoint {}", checkpointId, e);
  }
  ```
- **严重程度**: P2
- **现状**: 方法 catch 所有异常（包括自身刚抛出的 StreamException），仅 log。ACK 静默丢失。
- **风险**: coordinatorRpcService==null 时 StreamException 被同一个 catch 块捕获——异常无意义。网络瞬态失败被吞掉，checkpoint 将超时而非重试。
- **建议**: 将 coordinatorRpcService==null 的检查移到 try 外部，或传播异常。对瞬态失败考虑重试或指标计数。
- **信心水平**: 确定
- **误报排除**: 自抛自捕是明确的代码缺陷。
- **复核状态**: 未复核

### [维度09-11] MemoryInternalAppendingState.get() 包装内部错误为 IOException

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryInternalAppendingState.java:73-78`
- **证据片段**:
  ```java
  public ACC get() throws IOException {
      try {
          return getAccumulator();
      } catch (Exception e) {
          throw new IOException("Failed to get accumulator", e);
      }
  }
  ```
- **严重程度**: P2
- **现状**: get() 签名声明 throws IOException，将所有异常（包括 StreamException）包装为 IOException。
- **风险**: add() 方法正确抛 StreamException(ERR_STREAM_TYPE_MISMATCH)，但 get() 将一切包装为 IOException。StreamException 会被双重包装。
- **建议**: 检查 `if (e instanceof IOException) throw (IOException) e; else throw new StreamException(ERR_STREAM_STATE_ERROR, e)`。
- **信心水平**: 很可能
- **误报排除**: 这是内存实现，不应产生真正的 IOException。
- **复核状态**: 未复核

### [维度09-12] Fraud example 使用 bare IllegalArgumentException -- 不良示范

- **文件**: `nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/pattern/AccountTakeoverPattern.java:143,153,158,161,164` 等多个文件
- **证据片段**:
  ```java
  throw new IllegalArgumentException("Match must contain 'login', 'change', and 'withdraw' events");
  ```
- **严重程度**: P2
- **现状**: fraud example 模块在 generateAlert() 方法中使用 bare IllegalArgumentException。
- **风险**: 作为 CEP 框架的官方示例，使用 IllegalArgumentException 教会用户错误模式。
- **建议**: 替换为 `throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "...")`。
- **信心水平**: 确定
- **误报排除**: 示例模块是 nop-stream 分发的一部分，作为参考代码。
- **复核状态**: 未复核

## 正面发现

1. **良好的异常层次结构**: StreamRuntimeException → NopException, StreamException → StreamRuntimeException, CheckpointStorageException → StreamException
2. **双构造器模式**: StreamException/StreamRuntimeException 同时提供 (String) 和 (ErrorCode) 构造器
3. **全面的 ErrorCode 定义**: NopStreamErrors (38) + NopCepErrors (10) = 48 个错误码
4. **一致的 .param() 使用**: ERR_STREAM_TYPE_MISMATCH 的每次 throw 都提供 expectedType 和 actualType
5. **正确的 InterruptedException 处理**: 每次 catch 都重置线程中断标志
6. **无 printStackTrace()**: 全代码库无调用
7. **无中文错误消息**: 所有异常描述为英文
8. **StreamException(String) 仅在测试中使用**: 主源码全部使用 ErrorCode 构造器
