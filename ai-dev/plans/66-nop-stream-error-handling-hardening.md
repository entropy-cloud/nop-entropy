> Plan Status: completed
> Last Reviewed: 2026-05-27
> Source: ai-dev/audits/2026-05-27-deep-audit-nop-stream-r1/ (维度09、17)

## Purpose

修复 nop-stream 模块中 3 个 P1 级错误处理问题和相关 P2 级问题，确保异常不被静默吞掉、异常继承体系正确、ErrorCode 消息使用英文。

## Current Baseline

- 构建通过、300 测试全部通过
- `StreamRuntimeException`/`StreamException` 继承体系已建立
- `NopStreamErrors` 定义了 10 个 ErrorCode（当前中文消息）
- `NopCepErrors` 定义了 3 个 ErrorCode（当前中文消息）
- `MalformedPatternException` 继承 `RuntimeException`（应为 `StreamRuntimeException`）
- `TwoPhaseCommitSinkFunction.restoreFromEpoch` 吞掉 rollback 异常
- `GraphModelCheckpointExecutor.triggerTerminalSavepoint` 静默吞掉失败
- `Task.closeOperatorChains` 和 `SubtaskTask.closeOperatorChains` 异常仅 LOG.error 不传播

## Goals

1. `MalformedPatternException` 改为继承 `StreamRuntimeException`
2. `TwoPhaseCommitSinkFunction.restoreFromEpoch` rollback 失败时记录日志
3. `GraphModelCheckpointExecutor.triggerTerminalSavepoint` 失败时抛出 StreamException
4. `NopStreamErrors` 和 `NopCepErrors` 全部 ErrorCode 消息改为英文
5. 关键路径的 `IllegalStateException` 改为 `StreamException(ERR_STREAM_INVALID_STATE)`
6. `Task` 和 `SubtaskTask` 的 `closeOperatorChains` 关闭异常附加到调用方错误

## Non-Goals

- 不重构模块边界（core/runtime 职责拆分是长期任务）
- 不修复 import 排序问题（P3，另作处理）
- 不重构 `Map<String, Object>` 弱类型（P2，需要更大的设计变更）
- 不修复 connector 模块的 `StreamException(String)` 问题（P3）

## Scope

### In Scope
- `nop-stream-core` 的 NopStreamErrors.java、TwoPhaseCommitSinkFunction.java、Task.java、SubtaskTask.java、StreamExecutionEnvironment.java、TaskExecutor.java
- `nop-stream-cep` 的 NopCepErrors.java、MalformedPatternException.java
- `nop-stream-runtime` 的 GraphModelCheckpointExecutor.java、CheckpointCoordinator.java

### Out Of Scope
- 模块级架构重构
- connector 模块的 ErrorCode 迁移
- import 排序、通配符导入清理

## Execution Plan

### Phase 1: P1 修复（异常继承+吞异常）
Status: completed

Targets:
- MalformedPatternException 继承修复
- TwoPhaseCommitSinkFunction rollback 日志
- GraphModelCheckpointExecutor savepoint 失败报告

Exit Criteria:
- [x] `MalformedPatternException extends StreamRuntimeException`
- [x] `TwoPhaseCommitSinkFunction.restoreFromEpoch` rollback 失败时 LOG.warn
- [x] `GraphModelCheckpointExecutor.triggerTerminalSavepoint` 失败时抛出 StreamException，调用方可捕获
- [x] 新增测试：验证 `MalformedPatternException` 可被 `StreamRuntimeException` catch 捕获
- [x] 新增测试：验证 `TwoPhaseCommitSinkFunction` rollback 失败时产生 LOG.warn 输出
- [x] 新增测试：验证 `triggerTerminalSavepoint` 失败时抛出异常而非静默返回
- [x] 无静默跳过（catch 块均有处理，无空方法体）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全量测试通过
- [x] No owner-doc update required: 错误处理属于内部实现，不影响 docs-for-ai 中的公开 API 文档

Item Types: bug fix

- [x] 修改 `MalformedPatternException.java` 继承 `StreamRuntimeException`
- [x] 修改 `TwoPhaseCommitSinkFunction.java` rollback catch 块添加 LOG.warn
- [x] 修改 `GraphModelCheckpointExecutor.triggerTerminalSavepoint` 使失败时抛出 StreamException
- [x] 新增 `TestMalformedPatternException.java` 验证继承关系
- [x] 新增/扩展测试验证 rollback 日志和 savepoint 失败抛异常

### Phase 2: ErrorCode 消息英文化
Status: completed
Depends on: none

Targets:
- NopStreamErrors 全部消息英文化
- NopCepErrors 全部消息英文化

Exit Criteria:
- [x] NopStreamErrors 所有 define() 调用的消息参数为英文
- [x] NopCepErrors 所有 define() 调用的消息参数为英文
- [x] 新增测试：验证 ErrorCode 消息不包含中文字符
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全量测试通过
- [x] No owner-doc update required: ErrorCode 消息文本属于内部实现细节

Item Types: code style fix

- [x] 修改 NopStreamErrors.java 10 条消息为英文
- [x] 修改 NopCepErrors.java 3 条消息为英文
- [x] 新增/扩展测试验证消息不含中文

### Phase 3: IllegalStateException 统一
Status: completed
Depends on: Phase 2 (ERR_STREAM_INVALID_STATE 消息英文化)

Targets:
- StreamExecutionEnvironment 中的 IllegalStateException
- TaskExecutor 中的 IllegalStateException
- Task.closeOperatorChains 异常传播

Exit Criteria:
- [x] StreamExecutionEnvironment 无裸 IllegalStateException（改为 StreamException）
- [x] TaskExecutor 无裸 IllegalStateException（改为 StreamException）
- [x] Task.closeOperatorChains 关闭异常附加到调用方错误
- [x] SubtaskTask.closeOperatorChains 同步修改
- [x] 新增测试：验证替换后的异常类型为 StreamException 且包含 ErrorCode
- [x] 新增测试：验证 closeOperatorChains 异常传播到调用方
- [x] 无静默跳过（所有替换点均有正确的异常处理）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全量测试通过
- [x] No owner-doc update required: 异常类型替换属于内部实现

Item Types: code style fix

- [x] 替换 StreamExecutionEnvironment.java 中 IllegalStateException
- [x] 替换 TaskExecutor.java 中 IllegalStateException
- [x] 修改 Task.java closeOperatorChains 异常传播
- [x] 修改 SubtaskTask.java 同步修改
- [x] 新增测试验证异常类型和传播行为

## Closure Gates

- [x] 所有 Phase 的 Exit Criteria 已满足（每个 checkbox 已勾选）
- [x] `./mvnw clean install -pl nop-stream -am -T 1C` 通过
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过（307+ 测试）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] ai-dev/logs/2026/05-27.md 已更新

## Deferred But Adjudicated

### CheckpointCoordinator 触发失败计数器
- Classification: out-of-scope improvement
- Why Not Blocking Closure: 需设计 checkpoint 告警机制（含失败计数器、阈值、backoff），超出错误处理硬化的 scope
- Successor Required: yes
- Successor Path: 待创建（P2）

### ChainingOutput 诊断上下文增强
- Classification: optimization candidate
- Why Not Blocking Closure: 不影响正确性，仅影响诊断效率
- Successor Required: no

### connector 模块 ErrorCode 迁移
- Classification: out-of-scope improvement
- Why Not Blocking Closure: connector 模块的 StreamException(String) 不影响正确性，仅风格一致性问题
- Successor Required: yes
- Successor Path: 待创建（P3）

## Non-Blocking Follow-ups

- CheckpointCoordinator 连续失败计数器设计

## Closure

Status Note: All 3 phases completed. P1 issues fixed, ErrorCode messages in English, IllegalStateException unified.

Closure Audit Evidence:
- Reviewer/Agent: Herschel (independent closure audit sub-agent, session 019e69eb-f026-7932-b057-5689de10a40c)
- All Phase Exit Criteria verified: Phase 1 (8/8 ✅), Phase 2 (5/5 ✅), Phase 3 (9/9 ✅)
- Code spot-check results:
  - A. MalformedPatternException extends StreamRuntimeException ✅
  - B. TwoPhaseCommitSinkFunction rollback LOG.warn ✅
  - C. GraphModelCheckpointExecutor savepoint throws StreamException ✅
  - D. NopStreamErrors all English ✅
  - E. NopCepErrors all English ✅
  - F. StreamExecutionEnvironment no IllegalStateException ✅
  - G. TaskExecutor no IllegalStateException ✅
  - H. Task.closeOperatorChains propagates to this.error ✅
- Build: `./mvnw test -pl nop-stream -am -T 1C` — 307+ tests pass
- Blocking findings: None

Follow-up: Phase 3 deferred items (CheckpointCoordinator counter, ChainingOutput context, connector ErrorCode migration) tracked in Deferred But Adjudicated.
