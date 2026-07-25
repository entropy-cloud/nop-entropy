# Partial / Subtask 级恢复基础（G28, G29, P2）

> Plan Status: draft
> Last Reviewed: 2026-07-25
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 20；`ai-dev/analysis/nop-stream/08-gap-analysis.md` G28/G29；`ai-dev/design/nop-stream/checkpoint-design.md` §8.1（全局 epoch recovery 基线，region/local 为后续优化）
> Mission: nop-stream-production
> Work Item: 20
> Related: Plan `2026-07-25-0800-3-multi-input-barrier-alignment`（Stage 16，已完成前置）；`ai-dev/design/nop-stream/checkpoint-design.md` §8.1.1（全局 recovery vs Flink 的有意差异）

## Purpose

把 nop-stream 的故障恢复从「任意失败 → 全局重启整个 pipeline」推进到「支持 subtask 粒度的状态恢复与局部重启基础」。本 plan 落地 G29（subtask 粒度恢复）与 G28（partial failover 基础，local 路径），**不实现 region-aware scheduling 与分布式 partial recovery RPC**（属 Stage 27/44）。全局 epoch recovery 仍是 exactly-once 正确性基线（checkpoint-design §8.1），partial recovery 是可用性优化。

## Current Baseline

> 全部为 live repo 核对结果（explore subagent `ses_066775ec5ffek1vB96BcluaOeq`）。

- **恢复严格全局**：`JobCoordinator.globalRecovery()`（`JobCoordinator.java:415-460`）刷新 fencing token → 清空全部 taskAssignmentMap → 重新 round-robin 分配**每个** subtask（`assignTasks` `JobCoordinator.java:195-255`）。任意节点 lease 过期 → `failureDetected=true` → `globalRecovery()`（`JobCoordinator.java:372-405`）。
- **local 路径无恢复入口**：`GraphModelCheckpointExecutor.checkTaskFailures()`（`GraphModelCheckpointExecutor.java:656-662`）——任意单个 subtask FAILED → 整个 executor 抛 `StreamException`，无恢复。调用点 114/175/243/313/354。
- **无 region 概念**：grep `region|pipelined region|failover|partialRecovery` 跨 `nop-stream/**/*.java` 生产代码 = 0（仅 `TestReplayableSourceRecovery.testPartialRecoveryCycle` 误导命名，实为单算子 snapshot/restore）。无 `recovery` 包。
- **无 RestartStrategy / 无 attempt 计数 / 无 backoff**：grep `restartStrategy|attemptNumber|retryCount|maxAttempts|backoff` = 0。`TaskAssignment.attemptId`（`cluster/TaskAssignment.java:23`）每次恢复重新生成 UUID，无持久化、无计数。
- **状态层已按 subtask 切分**（G29 的基础已具备）：
  - `EpochManifest.taskSnapshots: Map<TaskLocation, TaskStateSnapshot>`（`EpochManifest.java:32`），按 `(vertexId, taskIndex)` 切分。
  - `restoreTaskStatesFromSource()`（`GraphModelCheckpointExecutor.java:861-877`）已按 per-vertex、per-subtask 迭代，`TaskStateSnapshot` 按 `TaskLocation` 查找（line 763）。
  - `AbstractStreamOperator.restoreState(OperatorSnapshotResult)`（`AbstractStreamOperator.java:131-150`）支持 deferred keyed-state restore（`pendingRestoreState`，restore-before-open 模式）。
  - `CheckpointParticipant.restoreFromEpoch(long epochId, TaskStateSnapshot)`（`CheckpointParticipant.java:20`）——**已知缺陷：唯一调用点硬编码 epochId=0**（`GraphModelCheckpointExecutor.java:923,933`），未从 `EpochManifest.epochId` 透传。
- **分布式 restore 当前未接线**：`JobCoordinator.globalRecovery()` 仅调 `getLatestCheckpoint()`（read-only getter），注释（`JobCoordinator.java:451-454`）称 invokable 应在 init 时调 `restoreFromCheckpoint()`，但 `EmbeddedDistributedExecutor` 路径未实际调用 restore（`EmbeddedDistributedExecutor.java:124-151`）。分布式 partial restore 需新 RPC，属 Stage 27/44，不在本 plan。
- **DeploymentPlan 无 subtask→node 映射**：`DeploymentPlan`（`core/execution/plan/DeploymentPlan.java:21-64`）8 字段无拓扑分配；映射在 `JobCoordinator.assignTasks()` 运行时 round-robin 决定，无 partial deployment 概念。
- **Task/SubtaskTask 状态机**：`SubtaskTask`（`core/execution/SubtaskTask.java:31-38`）有 CANCELING 中间态；`Task`（`core/execution/Task.java:70-81`）无 attemptId/retryCount/fencingToken。

## Goals

- **G29（subtask 粒度恢复）**：状态恢复路径真正按 subtask 粒度消费 `EpochManifest.taskSnapshots`，修复 `restoreFromEpoch` epochId 硬编码为 0 的缺陷（透传真实 epochId）。
- **G28（partial failover 基础，local 路径）**：local 执行模式下，单 subtask 失败时可仅重启该 subtask（重新创建 invokable + 从最新 durable epoch 恢复其状态 + 重新提交），而非全局抛异常退出。完整 region failover（分布式）属 Stage 44。
- 全局 epoch recovery 仍为 exactly-once 正确性基线；partial recovery 是可用性优化，不削弱 §12 不变量。
- owner-doc 同步：`checkpoint-design.md` §8.1（partial recovery 基础落地、分布式 region 仍 defer）。

## Non-Goals

- region-aware scheduling / pipelined region 识别——属 Stage 27/44。
- 分布式 partial recovery RPC（`notifyTaskFailed` 等上行 RPC）——属 Stage 25/27。
- `RestartStrategy` / attempt 计数 / backoff 的完整策略体系——本 plan 引入最小化重试计数（G28 基础），完整策略属 Stage 25。
- 跨 JVM partial deployment（`DeploymentPlan` subtask→node 映射）——属 Stage 24。
- Flink ExecutionGraph 三层抽象（vision §十明确排除）。

## Scope

### In Scope

- G29：subtask 粒度状态恢复——修复 `restoreFromEpoch` epochId 硬编码；验证/强化 per-subtask restore 路径（local）。
- G28：local 路径 partial restart——`GraphModelCheckpointExecutor` 在单 subtask FAILED 时，支持「仅重启该 subtask」（从最新 durable epoch 恢复该 subtask 状态、重新提交、新 fencingToken），替代当前「抛异常全局退出」。
- 最小化重试计数（per-subtask attempt counter，local 路径，超过阈值仍全局失败）。
- 端到端验证：单 subtask 失败 → 仅该 subtask 重启恢复 → 状态正确 → 流程继续。

### Out Of Scope

- region 识别与 region 级调度（Stage 27/44）。
- 分布式 partial recovery（跨 JVM RPC，Stage 25/27）。
- `DeploymentPlan` subtask→node 映射（Stage 24）。
- 完整 RestartStrategy 策略体系（Stage 25）。

## Execution Plan

### Phase 1 - G29: Subtask 粒度状态恢复修复

Status: planned
Targets: `GraphModelCheckpointExecutor.java:898-942`（`restoreOperatorsFromState`）、`GraphModelCheckpointExecutor.java:923,933`（epochId 硬编码）、`CheckpointParticipant.restoreFromEpoch`、`core/checkpoint/EpochManifest.java`

- Item Types: `Fix | Proof`

- [ ] 修复 `CheckpointParticipant.restoreFromEpoch` 调用点的 epochId 硬编码：把 `EpochManifest.epochId` 透传至 `restoreOperatorsFromState`（`GraphModelCheckpointExecutor.java:898`）→ `restoreFromEpoch(epochId, taskState)`（line 923/933），不再传 0。
- [ ] 验证 per-subtask restore 路径：`restoreTaskStatesFromSource`（`GraphModelCheckpointExecutor.java:861-877`）按 `TaskLocation` 查找 `taskSnapshots`，确认多 subtask 场景下各 subtask 仅恢复自身状态（无串扰）。
- [ ] 新增 focused test：多 subtask（parallelism>1）作业，checkpoint 后 restore，断言每个 subtask 的状态独立恢复、epochId 正确传入。

Exit Criteria:

- [ ] grep 确认 `restoreFromEpoch` 调用点不再硬编码 0（repo-observable：`GraphModelCheckpointExecutor.java:923,933` 传入 `epochManifest.epochId` 或透传变量）。
- [ ] 新增 test 断言 `CheckpointParticipant.restoreFromEpoch` 收到的 epochId == durable epoch（非 0）。
- [ ] 多 subtask restore 测试：各 subtask 状态独立恢复（断言 subtask A 的 restore 不影响 subtask B 的状态）。
- [ ] **无静默跳过**：无对应 TaskLocation 状态时，恢复路径明确行为（fail-fast 抛异常或显式初始化，不得静默忽略）。
- [ ] No owner-doc update required for Phase 1（bug fix，行为对齐既有文档 §2.6 epochId 语义）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - G28: Local 路径 Partial Subtask Restart

Status: planned
Targets: `GraphModelCheckpointExecutor.java:656-694`（`checkTaskFailures`/`registerLocalAbortHandler`）、`core/execution/SubtaskTask.java`、`core/execution/plan/`、新增 partial restart 路径

- Item Types: `Fix | Decision | Proof`

- [ ] 引入 per-subtask attempt counter（local 路径，最小化）：`SubtaskTask` 或其包装携带 `attemptCount`，每次 partial restart 递增。
- [ ] `GraphModelCheckpointExecutor` 在单 subtask FAILED 且 `attemptCount < maxAttempts`（可配置，默认有限值如 3）时，执行 partial restart：取消该 subtask → 从最新 durable epoch 恢复该 subtask 状态（复用 Phase 1 的 per-subtask restore）→ 重建 invokable → 重新提交。其余 subtask 不重启。
- [ ] 超过 `maxAttempts` 或 partial restart 本身失败时，回退到当前全局失败行为（抛 `StreamException`）——快速失败，不静默降级。
- [ ] partial restart 期间的数据一致性：重启的 subtask 从 durable epoch 之后重放（依赖 source REPLAYABLE，§5.1）；下游 subtask 消费边界由 barrier 语义保证（不破坏 §12 不变量）。
- [ ] 决策点：partial restart 触发时是否触发一次 checkpoint（以固化恢复点）——裁定为「否」（恢复后下一次周期 checkpoint 自然固化），避免引入额外复杂度。

Exit Criteria:

- [ ] local 路径存在 partial subtask restart 代码路径（repo-observable：`GraphModelCheckpointExecutor` 有「重启单 subtask」方法，被 `checkTaskFailures` 在 attemptCount 未超限时调用）。
- [ ] 单 subtask 失败后仅该 subtask 重启：测试注入某 subtask 失败，断言其余 subtask 未被取消/重启（断言其 invokable 未重建）。
- [ ] partial restart 从 durable epoch 恢复状态：断言重启后的 subtask 状态 == durable epoch 该 TaskLocation 的状态。
- [ ] 超过 maxAttempts 时回退全局失败：断言 `attemptCount` 超限后抛 `StreamException`（快速失败，非静默）。
- [ ] **端到端验证**：从 source→multi-subtask operator→sink，注入单 subtask 失败 → 该 subtask partial restart → 恢复后流程继续 → 最终输出 exactly-once（无重复/丢失）。
- [ ] **接线验证**：partial restart 路径在运行时确实被调用（断言 attemptCount 递增、invokable 重建）。
- [ ] **无静默跳过**：partial restart 路径每个分支显式处理（成功/失败/超限），无空方法体或吞异常。
- [ ] owner-doc 更新：`checkpoint-design.md` §8.1（partial recovery 基础落地于 local 路径，region/distributed 仍 defer 至 Stage 27/44）；`00-vision.md` §十一致性确认。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [ ] G29 收敛：subtask 粒度状态恢复正确（epochId 透传、各 subtask 独立恢复）。
- [ ] G28 收敛：local 路径 partial subtask restart 可用，单 subtask 失败不全局退出（attemptCount 内）。
- [ ] 全局 epoch recovery 仍为正确性基线（§12 不变量不破，partial restart 不引入重复/丢失）。
- [ ] 必要 focused verification（epochId 透传断言、单 subtask 重启断言、超限回退断言）已完成。
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect（epochId=0 硬编码是 confirmed bug，必须 Fix 非降级）。
- [ ] 受影响 owner docs（`checkpoint-design.md` §8.1）已同步。
- [ ] 独立子 agent closure-audit 已完成并记录证据。
- [ ] **Anti-Hollow Check**：partial restart 路径在运行时确实被 `checkTaskFailures` 调用（非仅类型存在）；状态恢复确实从 durable epoch 读取（非空壳）。
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 通过。
- [ ] checkstyle / 代码规范检查通过。

## Deferred But Adjudicated

### Region-aware scheduling / region failover

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap Stage 20 明确 Out of scope: region-aware scheduling（Stage 27/44）。本 plan 落地 partial restart 基础（单 subtask 级），region 级故障隔离需 pipelined region 识别（greenfield），属更大范围。
- Successor Required: `yes`
- Successor Path: Stage 27（targeted failover）/ Stage 44（region-based failover）

### 分布式 partial recovery RPC

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 分布式 partial 需 `notifyTaskFailed` 等上行 RPC（`IStreamCoordinatorRpcService` 当前仅 `receiveCheckpointAck`）+ 跨 JVM fencing。属 Phase 1/4 分布式运行时。
- Successor Required: `yes`
- Successor Path: Stage 25（failure detection + state machine）/ Stage 27

### 完整 RestartStrategy 策略体系

- Classification: `optimization candidate`
- Why Not Blocking Closure: 本 plan 引入最小化 attemptCount + 阈值回退；完整 backoff/重启策略属 Stage 25。
- Successor Required: `yes`
- Successor Path: Stage 25

## Non-Blocking Follow-ups

- `DeploymentPlan` subtask→node 映射（Stage 24）落地后，partial restart 可扩展到分布式部署。
- partial restart 是否需要触发 ad-hoc checkpoint 固化恢复点——当前裁定「否」（周期 checkpoint 自然固化），后续若恢复延迟成为问题再评估。

## Closure

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Audit Session: <<session ID>>
- Evidence:
  - 每条 Exit Criterion 验证结果（PASS/FAIL + live code path / test name）
  - 每条 Closure Gate 验证结果
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0
  - Deferred 项分类检查：无 in-scope live defect 被降级

Follow-up:

- Region failover → Stage 27/44
- 分布式 partial recovery RPC → Stage 25/27
- 完整 RestartStrategy → Stage 25
