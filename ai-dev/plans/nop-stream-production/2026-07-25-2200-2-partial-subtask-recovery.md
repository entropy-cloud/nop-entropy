# Subtask 粒度状态恢复修复（G29, P2）

> Plan Status: completed
> Last Reviewed: 2026-07-25
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 20；`ai-dev/analysis/nop-stream/08-gap-analysis.md` G29
> Mission: nop-stream-production
> Work Item: 20（G29 部分）
> Related: `ai-dev/design/nop-stream/checkpoint-design.md` §2.6（epochId 语义）、§3.2（restoreFromEpoch 契约）、§8.1（全局 recovery 基线）

## Outdated Note（draft review 拆分）

> 初稿曾把 G28（local partial subtask restart）与 G29 合并。经独立 review 核对（`ses_06670768bffeMPvclA4hYTha3I`）：local 单 subtask 重启在当前 pipelined 有界队列（`ResultPartition` `LinkedBlockingQueue`）按引用直连的数据交换模型下**结构上不可行**——下游死则上游 `queue.put()` 永久阻塞、上游死则下游 channel 不 close 永挂；且 `checkTaskFailures`（`GraphModelCheckpointExecutor.java:656-662`）在 `awaitCompletion` 后运行（所有线程已退出），无法承载 mid-execution 重启；非 source subtask 失败无 transport 级 replay buffer，单 subtask 回滚到 durable epoch 会破坏全局一致切点（§12 #4/#6）。这正是 `checkpoint-design.md:637-639` 选择全局 recovery 的理由。G28 需先有 region/drain/reconnect + supervision loop + replay 的设计文档，属 Stage 27/44 范畴，**本 plan 仅收口 G29**。G28 见 Deferred But Adjudicated。

## Purpose

收口 G29：使 nop-stream 的状态恢复真正按 subtask 粒度消费 `EpochManifest.taskSnapshots`，修复 `CheckpointParticipant.restoreFromEpoch` 调用点硬编码 `epochId=0` 的 confirmed bug，并验证多 subtask（parallelism>1）场景下各 subtask 状态独立恢复、无串扰。

## Current Baseline

> 全部为 live repo 核对结果（explore `ses_066775ec5ffek1vB96BcluaOeq`；review `ses_06670768bffeMPvclA4hYTha3I` 复核）。

- **epochId 硬编码为 0（confirmed bug）**：`GraphModelCheckpointExecutor.restoreOperatorsFromState`（`:898-942`）在 `:923` 和 `:933` 调用 `((CheckpointParticipant) op).restoreFromEpoch(0, taskState)`——`epochId` 参数硬编码 `0`，未从 `EpochManifest.epochId` 透传。`CheckpointParticipant.restoreFromEpoch(long epochId, TaskStateSnapshot)`（`CheckpointParticipant.java:20`）签名接收 epochId 但永远收到 0。
- **状态层已按 subtask 切分**：`EpochManifest.taskSnapshots: Map<TaskLocation, TaskStateSnapshot>`（`EpochManifest.java:32`），按 `(vertexId, taskIndex)` 切分。`restoreTaskStatesFromSource`（`GraphModelCheckpointExecutor.java:861-877`）已按 per-vertex、per-subtask 迭代，`TaskStateSnapshot` 按 `TaskLocation` 查找（line 763）。
- **per-operator restore API 存在**：`AbstractStreamOperator.restoreState(OperatorSnapshotResult)`（`:131-150`）支持 deferred keyed-state restore（`pendingRestoreState`，restore-before-open 模式）。
- **owner-doc 契约 drift**：`checkpoint-design.md` §3.2（`:265-267`）文档 `void restoreFromEpoch(long checkpointId)`——**一参数**；live 接口（`CheckpointParticipant.java:20`）是 `void restoreFromEpoch(long epochId, TaskStateSnapshot state)`——**两参数**。Phase 1 须顺带对齐此 drift。
- **恢复触发仍全局**：`GraphModelCheckpointExecutor.checkTaskFailures`（`:656-662`）任意 subtask FAILED → 抛 `StreamException`（local 路径）；`JobCoordinator.globalRecovery`（`:415-460`）重启整个 pipeline（distributed 路径）。**G28（局部重启）不在本 plan**（见 Outdated Note）。

## Goals

- G29：`restoreFromEpoch` 调用点透传 `EpochManifest.epochId`（而非硬编码 0），使 participant 恢复时能感知真实 epoch。
- G29：验证多 subtask（parallelism>1）场景下各 subtask 仅恢复自身 `TaskLocation` 的状态、无串扰。
- owner-doc 对齐：`checkpoint-design.md` §3.2 `restoreFromEpoch` 契约（一参→两参 drift 修正）。

## Non-Goals

- G28（local partial subtask restart）——结构上需 region/drain/reconnect 设计，属 Stage 27/44（见 Outdated Note + Deferred）。
- region-aware scheduling / pipelined region 识别（Stage 27/44）。
- 分布式 partial recovery RPC（Stage 25/27）。
- `RestartStrategy` / attempt 计数 / backoff（Stage 25）。
- Flink ExecutionGraph 三层抽象（vision §十排除）。

## Scope

### In Scope

- 修复 `restoreFromEpoch` epochId 硬编码：`GraphModelCheckpointExecutor.restoreOperatorsFromState`（`:923,933`）透传 `EpochManifest.epochId`。
- 验证/强化 per-subtask restore 路径：多 subtask 场景各 subtask 独立恢复。
- `checkpoint-design.md` §3.2 `restoreFromEpoch` 契约 drift 对齐。

### Out Of Scope

- G28 local partial restart（design-gated，见 Deferred）。
- region 识别 / 分布式 partial recovery / RestartStrategy（Stage 25/27/44）。

## Execution Plan

### Phase 1 - G29: epochId 透传修复 + per-subtask restore 验证

Status: completed
Targets: `GraphModelCheckpointExecutor.java:898-942`（`restoreOperatorsFromState`）、`GraphModelCheckpointExecutor.java:923,933`（epochId 硬编码）、`ai-dev/design/nop-stream/checkpoint-design.md` §3.2

- Item Types: `Fix | Proof`

- [x] 把 `EpochManifest.epochId` 透传至 `restoreOperatorsFromState`（`GraphModelCheckpointExecutor.java:898`）的方法签名/参数，使 `:923`/`:933` 的 `restoreFromEpoch(epochId, taskState)` 收到真实 epoch 而非 `0`。需追溯 **三个调用入口**确保 epochId 一路透传：(1) `restoreFromCheckpoint`（`:748→761`，EpochManifest 路径，epochId 源自 `epochManifest.getEpochId()`）；(2) `restoreTaskStatesFromCheckpoint`（`:786`，CompletedCheckpoint 路径，epochId 源自 `latestCheckpoint.getCheckpointId()`）；(3) `restoreFromSavepointPath`（`:820→853`，savepoint 路径，epochId 源自 `savepointCheckpoint.getCheckpointId()`）。三者皆汇入 `restoreTaskStatesFromSource`（`:861-877`）→ `restoreOperatorsFromState`（`:898`）。
- [x] 验证 per-subtask restore 无串扰：多 subtask（parallelism>1）作业 checkpoint 后 restore，每个 subtask 仅消费自身 `TaskLocation` 对应的 `TaskStateSnapshot`（`restoreTaskStatesFromSource` `:865-874` 已按 subtask 迭动，补断言确认无跨 subtask 读取）。
- [x] fail-fast 行为验证（**已存在，补 focused 覆盖**）：`findTaskLocationInPlan`（`:568`）查找失败已抛 `StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_JOB_GRAPH_INVALID)`；state-lookup lambda（`:765`/`:887`）已抛异常。补一条断言验证既有 fail-fast 行为（非新增代码）。

Exit Criteria:

- [x] grep 确认 `restoreFromEpoch` 调用点不再硬编码 0（repo-observable：`GraphModelCheckpointExecutor.java:923,933` 传入真实 epochId；三个调用入口的 epochId 源均已透传）。
- [x] **接线验证**：新 test 断言 `CheckpointParticipant.restoreFromEpoch` 收到的 epochId == durable epoch（非 0），且该断言覆盖从 `execute`/`executeWithSavepoint` 入口到 `restoreFromEpoch` 接收的完整透传路径（推荐扩展现有 E2E restore 测试如 `TestSavepointEndToEnd` 或 `TestE2ECheckpointAndRecovery`，而非仅单测 helper）。
- [x] 多 subtask restore 测试（新增，parallelism>1，可基于 `TestParallelCheckpoint` 已有的 parallelism=2 plan）：断言每个 subtask 的状态独立恢复（subtask A 的 restore 不影响 subtask B 的状态内容）；断言各 subtask 仅读取自身 TaskLocation 的 TaskStateSnapshot。
- [x] fail-fast 行为（已存在的 `:568`/`:765`/`:887`）有断言覆盖（验证既有行为，非新增代码）。
- [x] **无静默跳过**：restore 路径无空方法体/吞异常；缺失状态显式失败。
- [x] owner-doc 更新：`checkpoint-design.md` §3.2 `restoreFromEpoch` 契约对齐为两参数 `(long epochId, TaskStateSnapshot)`，并注明 epochId 从 EpochManifest/CompletedCheckpoint/savepoint 透传（Phase 1 修改了契约调用语义）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] G29 收敛：epochId 硬编码修复，participant 恢复时收到真实 epoch；多 subtask 状态独立恢复有测试覆盖。
- [x] 必要 focused verification（epochId 断言、per-subtask 独立恢复断言、fail-fast 断言）已完成。
- [x] epochId=0 硬编码是 confirmed bug，已 Fix（不得降级为 Follow-up）。
- [x] 受影响 owner docs（`checkpoint-design.md` §3.2）已同步。
- [x] 独立子 agent closure-audit 已完成并记录证据。（本 pass 由 mission-driver 在独立 closure-audit session 中执行，fresh task_id，证据见 Closure 段。）
- [x] **Anti-Hollow Check**：epochId 透传在运行时确实生效（restoreFromEpoch 收到非 0 值，断言覆盖）；非仅改注释。
- [x] `./mvnw compile -pl nop-stream -am` 通过（方法签名变更）。
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过。
- [x] checkstyle / 代码规范检查通过。

## Deferred But Adjudicated

### G28: Local Partial Subtask Restart

- Classification: `out-of-scope improvement`（需 design-gated）
- Why Not Blocking Closure: 经独立 review（`ses_06670768bffeMPvclA4hYTha3I`）核对，local 单 subtask 重启在当前 pipelined 有界队列按引用直连的数据交换下结构上不可行（上游/下游阻塞）、`checkTaskFailures` 运行时机在 `awaitCompletion` 后无法承载 mid-execution 重启、非 source subtask 无 replay。G28 需先有 region/drain/reconnect + supervision loop + replay 的设计文档，与 Stage 27/44 的 region 机制重叠。G29（本 plan）是 G28 的状态层前置。
- Successor Required: `yes`
- Successor Path: 需先在 `ai-dev/design/nop-stream/` 起草 region/drain/reconnect + supervision 设计文档，随后进入 Stage 27（targeted failover）/ Stage 44（region-based failover）

### Region-aware scheduling / 分布式 partial recovery RPC / RestartStrategy

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 属 Stage 25/27/44。
- Successor Required: `yes`
- Successor Path: Stage 25/27/44

## Non-Blocking Follow-ups

- G28 设计文档起草后，`DeploymentPlan` subtask→node 映射（Stage 24）+ supervision loop 可作为 G28 successor plan 的前置。

## Closure

Status Note: G29 收口完成。`GraphModelCheckpointExecutor.restoreOperatorsFromState` 与 `restoreTaskStatesFromSource` 新增 `epochId` 参数；三个调用入口（EpochManifest / CompletedCheckpoint / savepoint）的 epochId 源均已透传至 `restoreFromEpoch`，不再硬编码 0。新增 3 个测试覆盖完整透传路径、per-subtask 独立恢复（parallelism=2）、fail-fast。owner-doc §3.2 契约对齐为两参数。
Completed: 2026-07-25

Closure Audit Evidence:

- Reviewer / Agent: mission-driver 独立 closure-audit session（fresh task_id，非执行 session 复用）
- Audit Session: mission-driver closure-audit round（独立子 agent，独立核对 live repo）
- Evidence:
  - Exit Criterion 1 (无硬编码 0): PASS — `GraphModelCheckpointExecutor.java:926,936` 传入 `epochId`；入口 `:761` (`epochManifest.getEpochId()`)、`:884` (`checkpoint.getCheckpointId()`)、`:853` savepoint 路径汇入 `:884`。
  - Exit Criterion 2 (接线验证 epochId 非 0): PASS — `TestSavepointEndToEnd#testRestoreFromEpochReceivesRealEpochIdNotZero` 断言 `participant.lastEpoch == 9L`（staged durable savepoint id），覆盖 `executeWithSavepoint → restoreFromSavepointPath → restoreTaskStatesFromCheckpoint → restoreTaskStatesFromSource → restoreOperatorsFromState → restoreFromEpoch`。
  - Exit Criterion 3 (多 subtask 独立恢复): PASS — `TestParallelCheckpoint#testParallelRestoreEachSubtaskReceivesOwnState`（parallelism=2）断言 subtask0→"state-0"、subtask1→"state-1"、无 taskIndex=2，且 `lastEpoch == 13L`。
  - Exit Criterion 4 (fail-fast 覆盖): PASS — `TestSavepointEndToEnd#testRestoreFailsFastOnMissingTaskState` 断言缺失 TaskLocation 抛 `StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED)`。
  - Exit Criterion 5 (无静默跳过): PASS — restore 路径无空方法体；缺失状态经 state-lookup lambda 显式抛异常（同上测试）。
  - Exit Criterion 6 (owner-doc): PASS — `checkpoint-design.md` §3.2 line 273 已对齐为 `(long epochId, TaskStateSnapshot)` 两参数签名。
  - Exit Criterion 7 (日志): PASS — `ai-dev/logs/2026/07-25.md` 已更新 G29 条目。
  - `./mvnw test -pl nop-stream -am -T 1C`: PASS（490 tests, 0 failures, 0 errors）。
  - `./mvnw clean install -pl nop-stream -am -T 1C -DskipTests`: PASS。
  - Anti-Hollow: PASS — restoreFromEpoch 在运行时收到非 0 值（断言覆盖）。
  - Deferred 项分类检查: G28 维持 `out-of-scope improvement`（design-gated，Stage 27/44）；无 in-scope live defect 被降级。

Follow-up:

- G28 local partial restart → design-gated，需先起草 region/drain/reconnect 设计文档，随后 Stage 27/44
- Region / 分布式 partial / RestartStrategy → Stage 25/27/44
