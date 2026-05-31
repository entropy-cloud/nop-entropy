# 维度 09：错误处理与错误码

## 第 1 轮（初审）

### [维度09-01] 分布式运行时关键路径使用原生 IllegalStateException 而非 StreamException + ErrorCode

- **文件**:
  - `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:324-327`
  - `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:219-223, 283-285`
- **证据片段**:
  ```java
  // TaskManager.java:324-327
  if (coordinatorRpcService != null) {
      coordinatorRpcService.receiveCheckpointAck(ack);
  } else {
      throw new IllegalStateException(
              "No coordinator RPC service available. "
              + "All checkpoint ACKs require IStreamCoordinatorRpcService.");
  }

  // JobCoordinator.java:219-223
  throw new IllegalStateException(
          "No RPC service for node " + targetNode.getNodeId()
          + ". All control plane operations require IStreamTaskRpcService.");
  ```
- **严重程度**: P2
- **现状**: 3 处位于分布式运行时控制面关键路径，使用 `IllegalStateException` 而非 `StreamException(ERR_XXX).param(...)` 模式。同文件其他位置已正确使用 ErrorCode 模式。
- **风险**: 无法被框架统一错误处理管线识别和分类，无法通过 .param() 附加结构化上下文。
- **建议**: 替换为 `StreamException(ERR_STREAM_INVALID_STATE).param(ARG_NODE_ID, ...)` 等模式。
- **信心水平**: 高
- **误报排除**: 同文件其他位置已使用正确模式，说明是遗漏而非设计选择。
- **复核状态**: 未复核

### [维度09-02] WindowOperator 核心数据处理路径使用原生 UnsupportedOperationException/IllegalStateException

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:375-394, 420-423, 817-820`
- **证据片段**:
  ```java
  // WindowOperator.java:375-382
  throw new UnsupportedOperationException(
          "The end timestamp of an event-time window cannot become earlier than the current watermark by merging. "
          + "Current watermark: " + internalTimerService.currentWatermark()
          + " window: " + mergeResult);

  // WindowOperator.java:420-423
  if (stateWindow == null) {
      throw new IllegalStateException("Window " + window + " is not in in-flight window set.");
  }
  ```
- **严重程度**: P2
- **现状**: 核心窗口算子中 4 处使用原生 JDK 异常，丢失了 watermark、window、targetType 等关键诊断信息的结构化附加能力。
- **风险**: 生产环境中可能因数据异常或并发问题触发，但无法被错误处理管线分类。
- **建议**: 替换为 `StreamException(ERR_STREAM_WINDOW_AGGREGATOR_INVALID_STATE).param(...)` 模式。
- **信心水平**: 高
- **误报排除**: 同文件其他位置已正确使用 ErrorCode 模式（733、751、919 行），说明不一致是局部遗漏。
- **复核状态**: 未复核

### [维度09-03] MergingWindowSet 和 NoSkipStrategy 使用原生 IllegalStateException

- **文件**:
  - `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/MergingWindowSet.java:131-135`
  - `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/aftermatch/NoSkipStrategy.java:42-49`
- **证据片段**:
  ```java
  // MergingWindowSet.java:131-135
  public void retireWindow(W window) {
      W removed = this.mapping.remove(window);
      if (removed == null) {
          throw new IllegalStateException("Window " + window + " is not in in-flight window set.");
      }
  }

  // NoSkipStrategy.java:42-49
  @Override
  protected boolean shouldPrune(EventId startEventID, EventId pruningId) {
      throw new IllegalStateException("This should never happen. Please file a bug.");
  }
  ```
- **严重程度**: P2
- **现状**: 3 处使用原生异常，缺少结构化错误信息。
- **风险**: 无法通过 ErrorCode 区分"设计上不可能的路径被触发"与"其他运行时错误"。
- **建议**: 替换为模块级异常 + ErrorCode。
- **信心水平**: 高
- **误报排除**: 窗口不在集合中可能是并发恢复或状态损坏的表现，需要结构化错误信息帮助定位。
- **复核状态**: 未复核

### [维度09-04] StreamException/StreamRuntimeException 暴露 String 构造器，允许绕过 ErrorCode 模式

- **文件**:
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/exceptions/StreamException.java:13-18`
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/exceptions/StreamRuntimeException.java:16-21`
- **证据片段**:
  ```java
  // StreamException.java:13-18
  public class StreamException extends StreamRuntimeException {
      public StreamException(String message) {
          super(message);
      }
  }
  ```
- **严重程度**: P3
- **现状**: String 构造器作为 public API 暴露。当前生产代码中未误用（仅在测试中使用），但存在绕过入口。
- **风险**: 后续开发者可直接 `throw new StreamException("some message")` 绕过 ErrorCode 模式。
- **建议**: 考虑将 String 构造器标记为 @Deprecated 或减少可见性。
- **信心水平**: 高
- **误报排除**: 当前零实例误用，但作为 public API 存在绕过可用性。
- **复核状态**: 未复核

### [维度09-05] MemoryInternalAppendingState.get() 用原生 IOException 包装，与同类方法不一致

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryInternalAppendingState.java:73-78`
- **证据片段**:
  ```java
  @Override
  public ACC get() throws IOException {
      try {
          return getAccumulator();
      } catch (Exception e) {
          throw new IOException("Failed to get accumulator", e);
      }
  }
  ```
- **严重程度**: P3
- **现状**: 同一个类中 `createAccumulator()` 使用 StreamException + ErrorCode，但 `get()` 使用原生 IOException。
- **风险**: 增加维护者的认知负担。
- **建议**: 统一使用 StreamException(ERR_XXX) 模式。
- **信心水平**: 高
- **误报排除**: 方法签名受限于父接口的 `throws IOException`，但可在 catch 中包装为运行时异常。
- **复核状态**: 未复核

## 合规确认（无问题）

- **硬编码中文错误消息**: 无
- **吞掉异常**: 无空 catch 块
- **异常链保留**: 所有 catch-then-rethrow 均传入 cause
- **System.out/System.err 用于日志**: PrintSinkFunction 是功能需求，非日志反模式
- **Checkpoint 失败处理**: 正确 abort 并通知 listener
- **连接器失败传播**: 使用 StreamException(ERR_XXX) 模式
- **序列化异常处理**: 使用 NopStreamErrors.ERR_STREAM_SERIALIZATION
