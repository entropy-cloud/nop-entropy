# 维度 09：错误处理与错误码

## 第 1 轮（初审）

### [维度09-01] 生产代码中约 20 处 IllegalStateException 裸抛

- **文件**: 多个文件，典型示例：
  - `nop-stream-runtime/.../transport/RemoteResultPartition.java:87`
  - `nop-stream-runtime/.../coordinator/JobCoordinator.java:213,276`
  - `nop-stream-core/.../execution/ResultPartition.java:79`
  - `nop-stream-core/.../operators/WindowAggregationOperator.java:135,144,148`
  - `nop-stream-runtime/.../operators/windowing/WindowOperator.java:414,813`
- **证据片段**:
  ```java
  // RemoteResultPartition.java:87
  throw new IllegalStateException("Cannot write to a finished RemoteResultPartition");
  // JobCoordinator.java:213
  throw new IllegalStateException("No RPC service for node...");
  ```
- **严重程度**: P2
- **现状**: 在生产代码中有约 20 处直接抛出 IllegalStateException，未使用 StreamException 或 ErrorCode。部分位于公共 API 路径（ResultPartition.write(), JobCoordinator, TaskManager）。
- **风险**: 这些异常不会被 StreamException 的 catch 块捕获，可能在框架统一异常处理层丢失上下文。
- **建议**: 跨模块公共 API 路径上的改用 StreamException + ERR_STREAM_INVALID_STATE。纯内部防御性断言可保留但建议统一。AbstractRichFunction 中的 IllegalStateException 是 Java 惯用法，可保留。
- **误报排除**: AbstractRichFunction 和 RichIterativeCondition 中的 IllegalStateException 是从 Flink 移植的骨架代码，属于"未初始化即调用"的编程错误，用 IllegalStateException 是 Java 社区惯例。
- **复核状态**: 未复核

### [维度09-02] MemoryKeyedStateBackend 中 IOException 裸抛

- **文件**: `nop-stream-core/.../common/state/backend/memory/MemoryKeyedStateBackend.java:357,1056`
- **证据片段**:
  ```java
  // Line 357
  throw new IOException("Unknown state type: " + stateType);
  // Line 1056
  throw new IOException("Failed to get accumulator", e);
  ```
- **严重程度**: P2
- **现状**: 在状态恢复和访问路径中直接抛出 IOException，未被 StreamException 包装。
- **风险**: IOException 不被 StreamException 的 catch 捕获。如果调用方按 StreamException 捕获则会漏掉。
- **建议**: 改为 `throw new StreamException(ERR_STREAM_STATE_ERROR).param(ARG_DETAIL, "Unknown state type: " + stateType)` 或至少包装为 StreamException。
- **误报排除**: 无。
- **复核状态**: 未复核

### [维度09-03] GraphModelCheckpointExecutor.checkTaskFailures() 只报告首个失败

- **文件**: `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java:529-534`
- **证据片段**:
  ```java
  private static void checkTaskFailures(Map<String, SubtaskTask> tasks) {
      for (SubtaskTask task : tasks.values()) {
          if (task.getState() == SubtaskTask.State.FAILED) {
              throw new StreamException("Task failed", task.getError());
          }
      }
  }
  ```
- **严重程度**: P2
- **现状**: 当多个 task 同时失败时，只抛出第一个遇到的失败任务异常，其余被静默忽略。
- **风险**: 并行执行中多个 task 同时失败时丢失其他失败原因，影响运维排查。
- **建议**: 参考 EmbeddedDistributedExecutor.checkTaskResults() 的做法（使用 addSuppressed），收集所有失败。同样问题存在于 StreamExecutionEnvironment.java:278-281。
- **误报排除**: 无。这是真实的异常信息丢失风险。
- **复核状态**: 未复核

### [维度09-04] CheckpointType.fromName() 使用 IllegalArgumentException

- **文件**: `nop-stream-core/.../checkpoint/CheckpointType.java:98`
- **证据片段**:
  ```java
  throw new IllegalArgumentException("Unknown CheckpointType name: " + name);
  ```
- **严重程度**: P2
- **现状**: `CheckpointType.fromName()` 是跨模块可调用的公共枚举解析方法，使用了 IllegalArgumentException 而非 ErrorCode。
- **风险**: 不符合公共 API 应使用 ErrorCode 的两档策略要求。
- **建议**: 改用 `throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "name").param(ARG_DETAIL, "...")`
- **误报排除**: 无。
- **复核状态**: 未复核

### [维度09-05] NopCepErrors 缺少英文消息测试覆盖

- **文件**: `nop-stream-core/src/test/java/io/nop/stream/core/exceptions/TestErrorCodeMessagesEnglish.java:16`
- **严重程度**: P3
- **现状**: TestErrorCodeMessagesEnglish 只测试了 NopStreamErrors.class，未测试 NopCepErrors.class。
- **风险**: NopCepErrors 中的错误码消息可能混入非英文内容而不被发现。
- **建议**: 在 nop-stream-cep 测试目录中添加对应的英文消息测试。
- **误报排除**: 经目视检查 NopCepErrors 的 3 条消息均为英文，当前无实际问题。
- **复核状态**: 未复核

### [维度09-06] fraud-example 使用 System.out.println 而非 SLF4J

- **文件**: `nop-stream-fraud-example/.../FraudDetectionDemo.java:55,58,59,72,73,78,79,87,88,95,202-209`
- **证据片段**:
  ```java
  // FraudDetectionDemo.java:202-209
  System.out.println("   *** FRAUD ALERT ***");
  System.out.println("   Alert ID: " + alert.getAlertId());
  ```
- **严重程度**: P3
- **现状**: FraudDetectionDemo 全部使用 System.out.println 输出（约 15 处）。PrintSinkFunction 和 PrintSink 的 System.out.println 是功能需求（向控制台输出的 Sink）。
- **风险**: 示例代码质量问题，无生产影响。
- **建议**: 示例代码可保持现状。PrintSinkFunction/PrintSink 保持现状（功能需求）。
- **误报排除**: PrintSinkFunction 和 PrintSink 的 System.out.println 是正确的功能实现，不是日志替代品。
- **复核状态**: 未复核

## 已验证合规项

- 异常类体系设计优秀：StreamRuntimeException -> NopException，同时支持 ErrorCode 和 String 构造器
- ErrorCode 定义规范：NopStreamErrors（10 个错误码）和 NopCepErrors（3 个错误码）均使用 ErrorCode.define()
- 异常链保留优秀：几乎所有 catch-rethrow 都正确传入 cause
- 无吞异常的空 catch 块
- 所有生产代码使用 SLF4J，无 printStackTrace
- StreamException 字符串构造器用于内部实现，符合两档策略
- OperatorChain 正确使用 addSuppressed 处理 cleanup 异常
