# 维度 09 审计报告：nop-stream 错误处理与错误码

> 审计日期: 2026-05-27

## 总结

nop-stream 模块整体错误处理框架结构良好。以下列出真实问题。

## 发现

### [09-01] MalformedPatternException 继承裸 RuntimeException
- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/pattern/MalformedPatternException.java:25`
- **严重程度**: P1
- **现状**: 继承 JDK RuntimeException 而非 StreamRuntimeException 或 NopException。被 NFACompiler、NFAStateNameHandler 广泛使用。
- **风险**: 绕过 Nop 异常体系，上层框架无法统一处理。
- **建议**: 改为 extends StreamRuntimeException。

### [09-02] 生产代码中大量使用 IllegalStateException 替代 StreamException + ErrorCode
- **文件**: StreamExecutionEnvironment.java (9处), TaskExecutor.java (3处), JobCoordinator.java (2处), TaskManager.java, CheckpointPlanBuilder.java
- **严重程度**: P2
- **建议**: 统一改为 StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, ...)

### [09-03] TwoPhaseCommitSinkFunction.restoreFromEpoch 吞掉 rollback 异常
- **文件**: `nop-stream-core/.../functions/sink/TwoPhaseCommitSinkFunction.java:88-93`
- **严重程度**: P1
- **证据代码**:
  ```java
  for (Object tx : pending.values()) {
      try {
          rollback();
      } catch (Exception e) {
          // best effort
      }
  }
  ```
- **风险**: exactly-once 语义关键路径——rollback 失败导致脏事务残留，完全无法诊断。
- **建议**: 至少增加 LOG.warn 记录 rollback 失败。

### [09-04] CheckpointCoordinator 定时触发失败后静默跳过
- **文件**: `CheckpointCoordinator.java:115-117`, `GraphModelCheckpointExecutor.java:470-472`
- **严重程度**: P2
- **建议**: 引入连续失败计数器和告警阈值。

### [09-05] GraphModelCheckpointExecutor 终端 savepoint 失败被静默吞掉
- **文件**: `GraphModelCheckpointExecutor.java:322-324`
- **严重程度**: P1
- **风险**: 终端 savepoint 失败意味着作业状态未被持久化，恢复时可能数据丢失。静默失败使运维无感知。
- **建议**: 让 triggerTerminalSavepoint() 抛出受检异常或返回 boolean。

### [09-06] Task.closeOperatorChains 吞掉关闭异常
- **文件**: `Task.java:207-233`, `SubtaskTask.java:121-138`
- **严重程度**: P2
- **建议**: 至少检查 firstException 并附加到 error 字段。

### [09-07] NopStreamErrors / NopCepErrors 错误消息使用中文
- **文件**: `NopStreamErrors.java:24-53`, `NopCepErrors.java`
- **严重程度**: P2
- **建议**: 将所有 ErrorCode 消息模板改为英文。

### [09-08] PendingCheckpoint.acknowledgePrecedingCheckpoint 未实现但已暴露为 public API
- **文件**: `PendingCheckpoint.java:120-122`
- **严重程度**: P2
- **建议**: 确认无用则删除，否则补全实现。

### [09-09] ChainingOutput 包装异常丢失算子诊断上下文
- **文件**: `ChainingOutput.java:39-87`
- **严重程度**: P3
- **建议**: 附带算子名称等上下文信息。

### [09-10] connector 模块参数校验使用 StreamException(String) 而非 ErrorCode
- **文件**: MessageSourceFunction.java, MessageSinkFunction.java, BatchLoaderSourceFunction.java 等
- **严重程度**: P3
- **建议**: 为 connector 模块新增 NopConnectorErrors ErrorCode 接口。

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 09-01 | P1 | MalformedPatternException.java | 继承裸 RuntimeException |
| 09-02 | P2 | StreamExecutionEnvironment 等 | 大量 IllegalStateException |
| 09-03 | P1 | TwoPhaseCommitSinkFunction.java | 吞掉 rollback 异常 |
| 09-04 | P2 | CheckpointCoordinator.java | 触发失败静默跳过 |
| 09-05 | P1 | GraphModelCheckpointExecutor.java | 终端 savepoint 静默吞掉 |
| 09-06 | P2 | Task.java | 关闭异常被吞掉 |
| 09-07 | P2 | NopStreamErrors.java | ErrorCode 消息用中文 |
| 09-08 | P2 | PendingCheckpoint.java | 未实现的 public API |
| 09-09 | P3 | ChainingOutput.java | 异常丢失上下文 |
| 09-10 | P3 | connector 模块 | 参数校验不用 ErrorCode |
