# 87 nop-stream InputGate 重叠 Checkpoint Barrier 丢弃修复

> Plan Status: completed
> Last Reviewed: 2026-05-31
> Source: ai-dev/audits/2026-05-30-adversarial-review-nop-stream-r13/01-open-findings.md [AR-4] + live repo 验证
> Related: 82-nop-stream-round13-audit-remediation (completed, 本发现误标为已修复)

## Purpose

将 R13-AR-4（InputGate 重叠 checkpoint 场景下 barrier 静默丢弃）修复到可验证状态。该发现在 Plan 82 Current Baseline 中被标注为"已在 Plan 81 中修复"，但 live repo 独立验证确认代码结构未变，仍为活跃 P1 发现。

## Current Baseline

- Plans 81-86 全部 completed，`./mvnw test -pl nop-stream -am` 全量通过（360 tests）
- R13-AR-4 在 Plan 82 baseline 中标注为"已修复"，但 live code `InputGate.java:272-303` 方法体与 R13 审计时完全一致
- `CheckpointBarrierTracker` 的 `operatorsToAck > 0` 仅在 checkpoint **触发**时拒绝重叠（对单输入 Source 算子生效），不管控 barrier 在 InputGate 中的传递。多输入算子的 barrier 对齐完全在 InputGate 内完成，CheckpointBarrierTracker 不参与
- `barrierAlignment=true` 模式下，已收到 barrier 的 channel 被 `continue` 跳过（InputGate.java:219），新 barrier 不会被读到——重叠路径仅在 `barrierAlignment=false` 模式下可达
- 现有测试 `testNonAlignedMode_multipleCheckpoints`（TestInputGateProcessingGuarantee.java:197-238）和 `testHighBarrierEventCountNoStackOverflow`（TestInputGate.java:136-161）在 `barrierAlignment=false` 模式下写入多个不同 ID 的 barrier，需验证兼容性
- R13 审计信心水平：很可能（非确定，因为本地运行时有缓解机制）

## Problem

`InputGate.handleBarrierNonRecursive(int channelIndex, CheckpointBarrier barrier)` 使用 per-channel boolean 数组 `barrierReceived[]` 追踪当前 checkpoint 的 barrier 接收状态。当 checkpoint N 的 barrier 对齐尚未完成（`barrierReceived[channelIndex]=true`）时，如果 checkpoint N+1 的 barrier 到达同一 channel，`!barrierReceived[channelIndex]` 为 false，整个 if 块被跳过，方法返回 `null`。checkpoint N+1 的 barrier 被静默丢弃，该 channel 永远不会确认 checkpoint N+1。

## Goals

- 修复 `handleBarrierNonRecursive` 的重叠 barrier 丢弃问题
- 在 barrier 对齐过程中，对属于不同 checkpoint ID 的 barrier 采取明确的拒绝或排队策略（而非静默丢弃）
- 添加回归测试验证修复行为
- `./mvnw test -pl nop-stream -am` 全部通过

## Non-Goals

- 实现完整的 unaligned checkpoint 支持
- InputGate 的全面重构（如改为按 checkpoint ID 追踪状态的完整状态机）
- 其他 deferred items from Plans 82-86
- fraud-example 端到端验证

## Scope

### In Scope

- `nop-stream-core/.../execution/InputGate.java`: `handleBarrierNonRecursive` 方法
- `nop-stream-core/.../execution/InputGate.java`: `resetBarrierState` 方法（可能需要扩展）
- 新增测试

### Out Of Scope

- InputGate 全面重构
- 其他 Plan 82-86 deferred items
- P3 发现

## Execution Plan

### Phase 1 - InputGate 重叠 Barrier 修复

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java`

- Item Types: `Fix`

- [x] **修复 handleBarrierNonRecursive**：在 `if (!barrierReceived[channelIndex])` 的 else 分支中（即 `barrierReceived[channelIndex] == true` 时），添加 checkpoint ID 比较逻辑：当 `pendingBarrier != null && barrier.getId() != pendingBarrier.getId()` 时，抛出 `StreamException(ERR_STREAM_CHECKPOINT_ABORTED).param(ARG_REASON, "Overlapping checkpoint barrier: expected " + pendingBarrier.getId() + " but got " + barrier.getId() + " on channel " + channelIndex)`。使用已有错误码 `ERR_STREAM_CHECKPOINT_ABORTED`（NopStreamErrors.java:157-158）。当 checkpoint ID 相同时（重复 barrier），保持当前静默忽略行为（返回 null）。当 `pendingBarrier == null` 且 `barrierReceived[channelIndex]=true` 时（理论不一致状态），自然落入静默 `return null`，与当前行为一致（防御性安全）
- [x] **验证现有测试兼容性**：实现修复后立即运行 `./mvnw test -pl nop-stream -am -Dtest=TestInputGate,TestInputGateProcessingGuarantee` 确认现有测试不受影响。`testNonAlignedMode_multipleCheckpoints` 中两个 channel 的 barrier 按 round-robin 交替到达，checkpoint 30 的两个 barrier 应先到齐触发 `resetBarrierState()` 再收到 checkpoint 31 的 barrier，不应触发重叠检测。`testHighBarrierEventCountNoStackOverflow` 中 500 个连续 ID 的 barrier 交替到达，同样不应触发。如果现有测试失败，分析是否为预期行为变更并相应调整
- [x] **添加回归测试**：在 TestInputGate.java 中新增 `testBarrierOverlapRejectedInNonAlignedMode`：(1) 构造 2 channel + `barrierAlignment=false` 的 InputGate，(2) 写入 checkpoint 1 的 barrier 到 channel 0（`barrierReceived[0]=true`，checkpoint 1 尚未完成因为 channel 1 还没收到），(3) 写入 checkpoint 2 的 barrier 到 channel 0，(4) 验证抛出 `StreamException` 且消息包含 "Overlapping" 或包含两个 checkpoint ID

Exit Criteria:

- [x] `handleBarrierNonRecursive` 在 `barrierReceived[channelIndex]=true` 时，对属于不同 checkpoint ID 的 barrier 抛出 `StreamException`，对相同 checkpoint ID 的重复 barrier 保持静默忽略
- [x] 修改仅影响 `barrierAlignment=false` 路径（`barrierAlignment=true` 模式下已通过 `continue` 跳过已知 channel，新 barrier 不会被读到）
- [x] 新增测试 `testBarrierOverlapRejectedInNonAlignedMode` 验证重叠 checkpoint 被明确拒绝
- [x] 现有测试 `testHighBarrierEventCountNoStackOverflow` 和 `testNonAlignedMode_multipleCheckpoints` 不受影响（运行结果确认）
- [x] **无静默跳过**：不同 checkpoint ID 的 barrier 在重叠场景下抛异常而非静默返回 null
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required（InputGate 是 internal 实现类，无公共 API 文档）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] R13-AR-4 (P1) 已修复：InputGate 不再静默丢弃重叠 checkpoint barrier
- [x] 新增回归测试通过
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：修复有实际行为代码，无空方法体/静默跳过
- [x] `./mvnw compile -pl nop-stream -am`
- [x] `./mvnw test -pl nop-stream -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（无——仅 1 个 in-scope 发现）

## Non-Blocking Follow-ups

- InputGate 按 checkpoint ID 追踪 barrier 状态的完整实现（支持真正的重叠 checkpoint）
- Plan 82-86 其他 deferred items（GraphExecutionPlan OperatorChain 隔离、GraphModelCheckpointExecutor 类拆分、CEP ClosureCleaner 等）

## Closure

Status Note: R13-AR-4 (P1) 已修复。InputGate.handleBarrierNonRecursive 在 barrierReceived[channel]=true 时，对来自不同 checkpoint ID 的 barrier 抛出 StreamException(ERR_STREAM_CHECKPOINT_ABORTED) 而非静默丢弃。重复 barrier（相同 checkpoint ID）保持原有静默忽略。barrierAlignment=true 模式不受影响（已通过 continue 跳过已知 channel）。新增回归测试 testBarrierOverlapRejectedInNonAlignedMode 验证。全量 nop-stream 测试通过。

Closure Audit Evidence:

- Commit: 4a5275f80 fix(stream): InputGate重叠checkpoint barrier不再静默丢弃
- 代码变更: InputGate.java handleBarrierNonRecursive 添加 else 分支，检测 pendingBarrier.getId() != barrier.getId() 时抛 StreamException
- 新增测试: TestInputGate.testBarrierOverlapRejectedInNonAlignedMode — 验证 2 channel + barrierAlignment=false 场景下重叠 barrier 抛异常
- 现有测试兼容性: TestInputGate (8/8 pass), TestInputGateProcessingGuarantee (9/9 pass)
- 全量测试: ./mvnw test -pl nop-stream -am BUILD SUCCESS (所有 nop-stream 模块通过)
- lint: ast-grep Java lint check passed
- Anti-Hollow: 修复包含实际条件检查和异常抛出代码，非空壳

Follow-up:

- InputGate 完整重叠 checkpoint 支持（按 checkpoint ID 状态追踪）
