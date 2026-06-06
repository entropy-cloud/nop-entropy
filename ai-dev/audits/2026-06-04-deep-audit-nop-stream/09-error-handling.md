# 维度 09：错误处理与错误码

## 第 1 轮（初审）

### [维度09-01] MemoryInternalAppendingState 抛裸 IOException

- **文件**: `nop-stream-core/.../memory/MemoryInternalAppendingState.java:79`
- **证据片段**:
  ```java
  throw new IOException("Failed to get accumulator", e);
  ```
  同文件 L89 和 L112 正确使用了 StreamException。
- **严重程度**: P2
- **现状**: catch Exception 后包装为 `new IOException`，违反模块 `StreamException` + ErrorCode 约定。
- **风险**: 调用者需要同时 catch StreamException 和 IOException，破坏异常处理一致性。
- **建议**: 改为 `throw new StreamException(ERR_STREAM_STATE_ERROR, e).param(ARG_DETAIL, "Failed to get accumulator")`
- **信心水平**: 确定
- **误报排除**: 同类其他方法已正确使用 StreamException，此处是遗漏。
- **复核状态**: 未复核

### [维度09-02] MemoryInternalAggregatingState 抛裸 IOException（×2）

- **文件**: `nop-stream-core/.../memory/MemoryInternalAggregatingState.java:68, 85`
- **证据片段**:
  ```java
  throw new IOException("Failed to get aggregated state", e);
  throw new IOException("Failed to add to aggregated state", e);
  ```
- **严重程度**: P2
- **现状**: 同 09-01，裸 IOException 而非 StreamException + ErrorCode。
- **风险**: 同 09-01。
- **建议**: 改为 StreamException + ERR_STREAM_STATE_ERROR。
- **信心水平**: 确定
- **误报排除**: 同 09-01。
- **复核状态**: 未复核

### [维度09-03] SingleOutputStreamOperatorImpl 抛 UnsupportedOperationException

- **文件**: `nop-stream-core/.../datastream/SingleOutputStreamOperatorImpl.java:48`
- **证据片段**:
  ```java
  throw new UnsupportedOperationException("forceNonParallel is not supported in this implementation");
  ```
- **严重程度**: P2
- **现状**: 模块已定义 `ERR_STREAM_UNSUPPORTED` 但未使用。
- **风险**: UnsupportedOperationException 不携带 ErrorCode，无法被统一的错误处理框架识别。
- **建议**: 改为 `throw new StreamException(ERR_STREAM_UNSUPPORTED).param(ARG_OPERATION, "forceNonParallel")`
- **信心水平**: 确定
- **误报排除**: 已有对应 ErrorCode 但未使用，是遗漏。
- **复核状态**: 未复核

### [维度09-04] RecordWriter 抛 UnsupportedOperationException

- **文件**: `nop-stream-core/.../execution/RecordWriter.java:131`
- **证据片段**:
  ```java
  throw new UnsupportedOperationException("Flow control policy " + policy + " is not supported in local runtime");
  ```
- **严重程度**: P3
- **现状**: 已有 `ERR_STREAM_UNSUPPORTED` 可用。
- **建议**: 改为 StreamException + ERR_STREAM_UNSUPPORTED。
- **信心水平**: 确定
- **误报排除**: 同 09-03。
- **复核状态**: 未复核

### [维度09-05] ICheckpointExecutorFactory 默认方法抛 UnsupportedOperationException

- **文件**: `nop-stream-core/.../execution/ICheckpointExecutorFactory.java:81`
- **严重程度**: P3
- **现状**: 接口 default 方法抛 UnsupportedOperationException。
- **建议**: 改为 StreamException + ERR_STREAM_UNSUPPORTED。
- **信心水平**: 确定
- **误报排除**: 公共 API 的默认实现应使用 ErrorCode 模式。
- **复核状态**: 未复核

### [维度09-08] GroupPattern 多个 UnsupportedOperationException

- **文件**: `nop-stream-cep/.../pattern/GroupPattern.java:46, 51, 56`
- **证据片段**:
  ```java
  public GroupPattern<T, F> where(IterativeCondition<F> condition) {
      throw new UnsupportedOperationException();
  }
  ```
- **严重程度**: P3
- **现状**: where(), or(), subtype() 抛 UnsupportedOperationException，无 ErrorCode。
- **建议**: 在 NopCepErrors 新增 ERR_CEP_UNSUPPORTED。
- **信心水平**: 确定
- **误报排除**: CEP 模块有自己的错误码体系。
- **复核状态**: 未复核

### [维度09-10] SharedBuffer.hasEventInBuffer 吞掉异常（静默返回 false）

- **文件**: `nop-stream-cep/.../sharedbuffer/SharedBuffer.java:211-215`
- **证据片段**:
  ```java
  public boolean hasEventInBuffer(Object eventId) {
      try {
          ...
      } catch (Exception e) {
          return false;  // 吞掉异常！
      }
  }
  ```
- **严重程度**: P1
- **现状**: catch Exception 后直接 `return false`。状态后端访问失败（IO 异常、序列化异常）时，调用者无法区分"事件不存在"和"状态访问出错"。此方法在 registerEvent() 事件 ID 冲突检测中被调用。
- **风险**: 异常被吞掉可能导致重复事件 ID 覆盖已有事件，造成数据一致性问题。
- **建议**: 改为 `throw new StreamException(ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED, e).param(ARG_DETAIL, "hasEventInBuffer")`
- **信心水平**: 确定
- **误报排除**: 不是"可接受的降级"——状态访问异常是真实错误，不应被静默处理。
- **复核状态**: 未复核

### [维度09-11] CheckpointCoordinator 多处 catch 只记日志不抛出

- **文件**: `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:128, 227, 244, 391, 400, 410`
- **严重程度**: P3
- **现状**: 多处 catch 块仅 LOG.error/warn 后 return 或继续，不抛出异常。大部分是调度器容错模式（定时触发、后台清理、监听器通知），总体可接受。
- **风险**: 低。调度器模式应容错，但 L128 的 checkpoint 触发失败应有指标暴露。
- **建议**: 将 L128 异常传递给错误回调或指标系统。
- **信心水平**: 很可能
- **误报排除**: 调度器模式吞异常是标准做法，但需配合监控。
- **复核状态**: 未复核

### [维度09-12] TaskExecutor.awaitCompletion 日志级别过低

- **文件**: `nop-stream-core/.../execution/TaskExecutor.java:308, 348`
- **严重程度**: P3
- **现状**: catch (Exception e) 仅 `LOG.debug`。注释说明设计意图（让调用者通过 task.getError() 检查），但 LOG.debug 在生产环境会被忽略。
- **建议**: 提升为 LOG.warn 或 LOG.info。
- **信心水平**: 很可能
- **误报排除**: 设计意图合理，但日志级别选择有改进空间。
- **复核状态**: 未复核

## 已排除项

- ErrorCode 定义质量：NopStreamErrors (~44个) 和 NopCepErrors (9个) 定义规范，英文描述，参数通过 ARG_* 常量传递。
- 异常链保留：主要 catch-rethrow 模式正确保留了 cause。
- 硬编码中文：无，仅出现在 Javadoc/注释中。
- 错误码命名一致性：core 用 ERR_STREAM_*，cep 用 ERR_CEP_*，无混用。
- param() 使用：抽查通过。
- Fraud 示例模块 IllegalArgumentException：demo 代码，不要求严格遵循。
- Trigger.onMerge / WindowAggregationFunction.merge 的 UnsupportedOperationException：API 设计的可选操作模式，严格来说应用 ErrorCode 但优先级低。

## 维度复核结论

待复核。

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 09-01 | P2 | MemoryInternalAppendingState.java:79 | 抛裸 IOException |
| 09-02 | P2 | MemoryInternalAggregatingState.java:68,85 | 抛裸 IOException ×2 |
| 09-03 | P2 | SingleOutputStreamOperatorImpl.java:48 | 抛 UnsupportedOperationException |
| 09-04 | P3 | RecordWriter.java:131 | 抛 UnsupportedOperationException |
| 09-05 | P3 | ICheckpointExecutorFactory.java:81 | 接口 default 抛 UnsupportedOperationException |
| 09-08 | P3 | GroupPattern.java:46,51,56 | 多个 UnsupportedOperationException |
| 09-10 | P1 | SharedBuffer.java:211-215 | 吞掉异常，静默返回 false |
| 09-11 | P3 | CheckpointCoordinator.java | 多处 catch 只记日志 |
| 09-12 | P3 | TaskExecutor.java:308,348 | LOG.debug 级别过低 |
