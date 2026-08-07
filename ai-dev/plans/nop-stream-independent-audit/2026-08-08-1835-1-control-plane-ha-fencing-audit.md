# 13 Control Plane, HA & Fencing Audit (nop-stream Independent Audit)

> Plan Status: completed
> Last Reviewed: 2026-08-08
> Source: `ai-dev/backlog/nop-stream-independent-audit-roadmap.md` (Stage 13); frozen Stage-4 outputs (`source-manifest.md`, `evidence-schema.md`, `finding-corpus.md`, `ai-dev/tools/check-nop-stream-audit-manifest.mjs`); frozen Stage-5 outputs (`environment-qualification.md`); frozen Stage-6 outputs (`stage-6-java-api-graph-local.evidence.md`); frozen Stage-9 outputs (`stage-9-checkpoint-barrier-recovery.evidence.md`); live repo baseline of `nop-stream-runtime` coordinator/cluster/rpc/taskmanager/execution/transport surfaces + `nop-cluster-core` leader-election surfaces.
> Mission: nop-stream-independent-audit
> Work Item: 13. Control plane, HA and fencing audit
> Related: Execution order `{1}` of this DRAFT_PLANS round. Roadmap deps: Stage 4 (evidence schema), Stage 5 (env qualification), Stage 9 (checkpoint audit) — all `done`. Hard prerequisite for Stage 14 (data-plane/multi-JVM recovery), Stage 20 (Hist P0/P1 CEP/connector/runtime), Stage 22 (Hist P2 CEP/connector/runtime). On **critical path**.

## Purpose

独立验证 nop-stream 的 **control plane、HA 与 fencing** 是否实现其设计目标：coordinator RPC（task assignment、heartbeat、checkpoint trigger、failover commands）、leader 选举与切换、fencing token/epoch 对 stale command 的拒绝、global recovery vs region-based recovery 的边界。每个被支持的 control-plane 能力必须形成一条可复核的 entry-to-effect evidence row；每个不支持的组合必须有 fail-fast 证明或显式 non-goal 裁定。

本审计验证核心 invariant：(a) stale-leader rejection（旧 leader 的命令被拒绝）；(b) same-leader prior-recovery rejection（同一 leader 的前次 recovery epoch 命令被拒绝）；(c) leader transition 期间 checkpoint continuity（G32 failover-safe rebuild）。

本审计**发现**的任何 confirmed live defect 不在本计划内修复，而按 roadmap 规则指派给 active/successor remediation plan。

## Current Baseline

经 2026-08-08 live repo 核对（引用均与 frozen Stage-4 `source-manifest.md` 域 a/d/g + 实际源码一致；line anchors 经 explore agent 逐行复核）：

- **Coordinator RPC 接口（2 个）**：
  - `IStreamCoordinatorRpcService`（`nop-stream-runtime/.../rpc/IStreamCoordinatorRpcService.java:20`，`@Internal`）——task→coordinator uplink。方法：`receiveCheckpointAck` `:23`、`reportTaskStatus` `:35`（G52）、`reportNodeTaskLiveness` `:45`、`terminate` `:57`（G23 四模式终止）、`abortCheckpoint` `:72`、`getJobStatus` `:82`。
  - `IStreamTaskRpcService`（`nop-stream-runtime/.../rpc/IStreamTaskRpcService.java:15`，`@Internal`）——coordinator→task downlink。方法：`receiveAssignment` `:18`、`triggerCheckpoint` `:23`、`cancelTask` `:25`、`updateFencingToken` `:32`（Stage 39 epoch push）、`deployTask` `:58`（Stage 42 Phase 0，default throws `UnsupportedOperationException`）。
- **实现**：`JobCoordinator`（`coordinator/JobCoordinator.java:83`，`implements IStreamCoordinatorRpcService`，1704 行）是分布式 control plane 单点；`TaskManager`（`taskmanager/TaskManager.java:78`，`implements IStreamTaskRpcService`）是 per-node。
- **RPC transport 层**：`StreamControlRpcServer`（`rpc/StreamControlRpcServer.java:44`，`MessageRpcServer` over `IMessageService`）、`StreamControlRpcProxyFactory`（`rpc/StreamControlRpcProxyFactory.java:41`）、`StreamControlRpcTransformer`（`rpc/StreamControlRpcTransformer.java:41`，30s timeout）、`StreamControlRpcTopics`（`rpc/StreamControlRpcTopics.java:35/40`，topic naming）。生产 dispatcher `RpcDistributedExecutor.startJob()`（`execution/RpcDistributedExecutor.java:183`）wires per-node task servers `:208`、coordinator→task proxies `:214`、coordinator server `:253`、task→coordinator proxy `:258`、`registerDistributedAbortHandler()` `:250`。
- **Leader 选举**：platform 契约 `AbstractLeaderElector`（`nop-cluster/nop-cluster-core/.../elector/AbstractLeaderElector.java:32/33/43`，`leaderEpoch` `:32`，`onBecomeLeader` `:183`，`onBecomeFollower` `:193`）。nop-stream 实现 `JdbcLeaderElector`（`cluster/JdbcLeaderElector.java:56`，`@Internal`，extends `AbstractPollingLeaderElector`），backed by JDBC lease table `nop_stream_leader`（DDL `:279-289`），`checkElection()` `:82`、`checkLeader()` `:149`、`checkFollower()` `:184`、`tryBecomeLeader()` `:224`、`changeLeader()` `:249`（optimistic concurrency `UPDATE ... WHERE leader_epoch = oldEpoch`）。HA 模式 wiring：`JobCoordinator.setLeaderElector()` `:1487`（must set before `start()`）；`start()` `:327`——`leaderElector == null` → 非 HA 单实例立即 active；非 null → 注册 `CoordinatorElectionListener` `:358` + 进入 STANDBY `:361` + self-activation reconciliation `:375-391`。
- **Leadership transition**：`CoordinatorElectionListener`（`JobCoordinator.java:1164`，inner class）——`becomeLeader` → `activateAsLeader(epoch)` `:1167/1192`（idempotent guard `:1199`，reset `recoveryGen=0` `:1209`，`active=true` `:1212`，derive token `:1214`，under `recoveryLock` `:1221` → `rotateFencingEpochCoreLocked(token, true)` G32 rebuild + `prepareAssignmentsLocked()`）；`becomeFollower` → `deactivateToStandby(epoch)` `:1171/1242`（`active=false`，detector stays alive — M2 contract `:1252-1253`）；`onException` → safe degradation to STANDBY `:1176-1182`。
- **Fencing 编码**（Stage 39 unification）：single monotonic `long` epoch = `leaderEpochValue * EPOCH_SCALE + recoveryGen`。`JobCoordinator.EPOCH_SCALE = 1_000_000L` `:110`，`deriveHaFencingEpoch(leaderEpochValue, recoveryGen)` `:1155`。State：`fencingEpoch`（`AtomicLong`）`:126`、`recoveryGen`（`AtomicLong`）`:154`（leader grant 时 reset to 0；same-leader recovery 时递增）。Non-HA 模式：`leaderEpochValue=0` → epoch == recoveryGen（start 时 seed to 1 `:343-344`）。
- **Stale command rejection（control plane）**：`JobCoordinator.collectAck()` `:739`——reject if `!active` `:744`、`epoch==0L` `:754`、`epoch != ack.getFencingEpoch()` `:760-764`（WARN "stale fencing epoch"）；`reportTaskStatus()` `:793`——reject if `epoch==0L || epoch != report.getFencingEpoch()` `:811-816`；`reportNodeTaskLiveness()` `:862`——reject if `!active` `:873`。Task-side（**P0-6 hardened**）：`TaskManager.receiveAssignment()` `:262`——throws `StreamException(ERR_STREAM_FENCING_TOKEN_MISMATCH)` if `activeEpoch != assignment.getFencingEpoch()` `:275-279`（comment `:269-273` documents P0-6 hardening, was silent LOG.warn+return）；`triggerCheckpoint()` `:514`——throws same if mismatch `:520-524`（comment `:515-518`）；`deployTask()` `:367`——throws on mismatch `:388-394` + reports FAILED `:480`；`updateFencingToken()` `:583`——rotates epoch, cancels old-epoch tasks `:587-596`。
- **Data-plane fencing（single long comparison — Stage 39）**：`RemoteInputChannel`（`transport/RemoteInputChannel.java:74/106`）——`expectedEpochId` field；`EnvelopeConsumer.onMessage()` `:368`——`if (envelope.getEpochId() != expectedEpochId) { discard }`（javadoc `:44-47` documents dual-key collapse）；liveness refresh only AFTER fencing filter passes `:379`。`RemoteResultPartition` stamps `epochId` into every envelope `:164/185/253`。
- **Fencing-epoch-before-lock（M8-2-P0-1 fix）**：`JobCoordinator.globalRecovery()` `:980`——snapshots `epochAtEntry = fencingEpoch.get()` **BEFORE** acquiring `recoveryLock`（`:988` snapshot → `:991` lock）。Late-arrival guard `:999-1004`：if epoch advanced since entry, short-circuit with observable WARN（no double rotation, no restartCount bump）。`rotateFencingEpochCoreLocked(newEpoch, restoreFromStorage)` `:1081`（must hold `recoveryLock`）：sets epoch `:1082`、re-registers coordinator `:1084`、clears in-memory working set `:1088-1089`（preserves ClusterRegistry attempt history）、pushes new epoch to all task RPCs `:1094-1096`、G32 failover-safe rebuild from storage when `restoreFromStorage && inMemory==null` `:1098-1130`（fails loud on storage failure `:1116-1126`）。
- **Task assignment & recovery control**：`JobCoordinator.assignTasks()` `:481`（guards `:482/487/492/501`）→ `prepareAssignmentsLocked()` `:534`（under `recoveryLock`）→ `executeAssignmentFanOut()` `:639`（issues RPCs **OUTSIDE** the lock）。**Global recovery**：`globalRecovery()` `:980`——clears entire in-memory working set, reassigns ALL subtasks, bumps `restartCount` `:1011`，fails job when `> maxRestarts`（default 3）`:1012-1018`。**Region-based recovery**：`SupervisionLoop.restartRegion()` `:382`（per-region budget `DEFAULT_MAX_RESTARTS_PER_REGION = 3` `:146`，`regionRestartCounts` map `:232`，budget exhausted → throws `ERR_STREAM_SUPERVISION_RESTART_EXHAUSTED` `:266`）。**Boundary note**：`JobCoordinator` Javadoc `:220-224` explicitly notes scoped restart needs its own per-region counter（deferred follow-up）。
- **G32 failover-safe rebuild**：`rotateFencingEpochCoreLocked(token, restoreFromStorage=true)` `:1098-1130`——if `latestCompletedCheckpoint == null` → `checkpointCoordinator.restoreFromCheckpoint()`。Storage failure → throws `StreamException(ERR_STREAM_INVALID_STATE)` `:1121`（"new leader cannot safely resume"）。Same-leader recovery skips storage rebuild `:1131-1138`。
- **Zombie task rejection**：`TaskManager.deployTask()` `:426-435`（existing slot fenced out + permit reclaimed，M8-2-P1-4 fix，net permit change 0）。`SupervisionLoop.waitForTerminal()` `:492`——throws `ERR_STREAM_SUPERVISION_ZOMBIE_TASK_TIMEOUT` if task doesn't terminate within `DEFAULT_TERMINAL_WAIT_BUDGET_MS = 10_000L` `:155`（comment `:474-487` documents P1 hardening, was silent WARN+return → zombie: two producers writing same ResultPartition）。
- **ClusterRegistry**：interface `cluster/ClusterRegistry.java:16`；实现 `InMemoryClusterRegistry` `:19`（lease TTL 15000ms `:23`）+ `JdbcClusterRegistry` `:25`（tables `nop_stream_coordinator`/`nop_stream_node`/`nop_stream_task_assignment` `:29-31`，`CREATE TABLE IF NOT EXISTS` for multi-JVM safety `:331/351/386`，attempt-number in PK `:395` G56）。`assignTask()` `:85`（G56 attempt-history preserving）、`getAttemptHistory()` `:118`。
- **Corpus 交叉**：finding-corpus.md 中 control-plane/HA/fencing 相关 finding ~14 个。关键：M8-2-P0-1（globalRecovery unsynchronized，**code hardened** `:980-1004`，Stage 9 标 `residual-risk`，cross-JVM mutex 未独立复验）；M7-2-P0-6（fencing-token rejection ZERO tests，**now covered** by `TestFencingTokenRejection` + `TestFencingEpochUnification`）；M8-2-P1-4（permit leak，**fixed**）；M8-2-P1-6（zombie task，**hardened** fail-loud，Stage 9 标 `residual-risk`，true cross-JVM zombie fencing owned by Stages 13/14）；M8-2-P2-9/10/13/14/15/19（various coordinator/runtime P2）。
- **测试语料**（manifest 域 g）：in-process control-plane/HA/fencing 测试丰富——`TestJobCoordinatorLeaderElection`（G24/G25 leader-election wire + standby rejection of all 5 control methods）、`TestJobCoordinatorStandbyStateMachine`（standby state machine + two-coordinator leader switch + stale-token rejection）、`TestJobCoordinatorRecoveryConcurrency`（M8-2-P0-1：two concurrent recovery drivers serialize to one epoch rotation）、`TestJobCoordinatorJdbcHaIntegration`（real `JdbcLeaderElector` + `JdbcCheckpointStorage` drives activation + G32 rebuild）、`TestJobCoordinatorFailoverRestore`（G32 failover-safe rebuild paths）、`TestJobCoordinatorPerTaskFailure`（G52 per-task failure + liveness）、`TestJobCoordinatorRestartStrategy`（G56 restart strategy）、`TestJobCoordinatorAttemptTracking`（G56 attempt history）、`TestJobCoordinatorRemoteDeploy`（Stage 42 remote-deploy）、`TestFencingEpochUnification`（Stage 39 Phase 1 proof，5 `@Test`：dataPlane stale discard、leadership switch advance、sameLeader recovery advance、nonHa monotonic、encoding dominance）、`TestFencingTokenRejection`（P0-6 hardening，4 `@Test`）、`TestStreamControlRpc`（RPC traversal）、`TestStreamControlRpcBootstrap`（IoC beans.xml bootstrap）、`TestSupervisionLoopZombieTaskTimeout`（zombie timeout）、`TestRpcDistributedExecutorE2E`（full pipeline over RPC control plane）。multi-JVM gated：`TestMiniStreamClusterProcessSpawn`（3/3 PASS，T2 lane infrastructure qualified）、`TestMultiJvmExactlyOnceRecovery`（**defect**：log-label mismatch `coordinator` vs `coordinator-0` at `:111`）、`TestMultiJvmCoordinatorFailover`（**defect**：`testBrainSplitFencingBoundary` fails "coordinator-1 must take over" `:129`，HA-fencing capability gap）。
- **Stage 9 evidence 交叉**：EVID-S9-016（M8-2-P0-1 `residual-risk`，`required_lane: multi-jvm`，in-process lane cannot prove distributed mutex，final cross-JVM revalidation owned by Stage 13）；EVID-S9-019（M8-2-P1-6 `residual-risk`，LOCAL zombie mitigation present，true cross-JVM zombie fencing `required_lane: multi-jvm`，owned by Stages 13/14）。Stage 9 Non-Goals 明确声明：distributed leader election / fencing / cross-JVM control-plane transport = Stage 13。
- **真实 gap**：(1) 没有覆盖"control-plane entry → effect"的成套 evidence row（assignment dispatch → task receive、checkpoint trigger → task execute、failover command → task fence）；(2) leader election transition（standby → active → standby → re-active）的端到端连通性在 in-process 有测试但 cross-JVM（T2 lane）的 deeper capability 有已知 defect；(3) fencing epoch stale rejection 的两个 invariant（stale-leader + same-leader prior-recovery）无统一 evidence row 覆盖矩阵（虽有 `TestFencingEpochUnification` 但缺 evidence row 冻结）；(4) G32 failover-safe rebuild（storage failure → fail-loud）的端到端连通性缺独立 evidence row；(5) zombie task fencing 在 in-process 有 hardening 但 cross-JVM 场景缺 evidence；(6) T2 lane 的两个 known defect（log-label mismatch + HA failover takeover failure）缺显式 `blocked` disposition evidence row。

## Goals

- 产出一份 **control-plane 支持/拒绝能力矩阵**（task assignment dispatch、checkpoint trigger via RPC、failover/recovery command、termination command、distributed abort），每能力一条 evidence row，`environment_class` 按 frozen lane 词表裁定（in-process control-plane → `in-process`；任何需 cross-JVM 的 → `multi-jvm` 或 `blocked` per Stage 5 T2 record），`disposition` 按 frozen 7 词表裁定。
- 为**每条 in-process control-plane 能力**产出 entry-to-effect evidence row：`positive_proof` 为真实 in-process 实跑测试名（`ClassName#method`），验证 RPC 从 coordinator 入口到 task 效果完整走通（接线验证）。
- 产出 **fencing stale-rejection 矩阵** evidence row：覆盖两个 invariant——(a) stale-leader rejection（旧 leader epoch 命令被 coordinator 和 task 双向拒绝）；(b) same-leader prior-recovery rejection（recoveryGen 递增后旧 recoveryGen 命令被拒绝）。`positive_proof` 引用 `TestFencingEpochUnification` + `TestFencingTokenRejection` 的对应 `@Test`。
- 产出 **leader election transition** evidence row：standby → active → standby → re-active 状态机在 in-process 实跑中被验证（`TestJobCoordinatorStandbyStateMachine` 对应方法）。
- 产出 **G32 failover-safe rebuild** evidence row：storage failure → fail-loud（`ERR_STREAM_INVALID_STATE`）、in-memory == null → rebuild from storage、same-leader recovery → skip storage rebuild，每路径据 in-process 实跑或 manual-trace 裁定 `disposition`。
- 产出 **concurrent recovery serialization** evidence row（M8-2-P0-1 live 复验）：fencing-epoch-before-lock + late-arrival guard 在 in-process lane 证明 two concurrent recovery drivers serialize to exactly one epoch rotation。
- 产出 **zombie task fencing** evidence row（M8-2-P1-6 live 复验）：in-process `SupervisionLoop.waitForTerminal` fail-loud hardening。
- 对 **T2 lane 的两个 known defect**（`TestMultiJvmExactlyOnceRecovery` log-label mismatch、`TestMultiJvmCoordinatorFailover` HA-fencing takeover failure）产出 `disposition: blocked` evidence row，`required_lane: multi-jvm`，cross-ref Stage 5 T2 record `@@LANE` defect note。
- 对**关键历史 P0/P1 finding** 做 live 复验标注：M8-2-P0-1（globalRecovery）、M8-2-P1-4（permit leak）、M8-2-P1-6（zombie task）、M7-2-P0-6（fencing rejection ZERO tests）——据 live 行为标 `finding_id` + `disposition`。
- 所有 evidence row 经 `check-nop-stream-audit-manifest.mjs evidence --strict` 校验通过且非空过；corpus finding_id 交叉标注合法。

## Non-Goals

- 真实 multi-JVM data-plane recovery 实跑——属 Stage 14（本计划的 cross-JVM 场景引用 Stage 5 T2 的 `qualified` lane，但 T2 的两个 deeper defect 是 Stage 13/14 owned，不在本计划修复）。
- Checkpoint barrier 对齐 / state backend / window / CEP 语义——属 Stages 9/10/11/12（本计划只在 `finding_id` 交叉中标注相关 finding 的 live 复验结果）。
- Connector source/sink 保证——属 Stages 15/16。
- 修复本审计发现的 confirmed live defect（按 roadmap 规则指派 remediation plan）。

## Scope

### In Scope

- `ai-dev/audits/nop-stream-independent-audit/stage-13-control-plane-ha-fencing.evidence.md`（domain evidence rows，manifest 域 a/d/g 范围内的 coordinator/cluster/rpc/taskmanager/execution/transport source anchor + test lane）。**文件名必须是 `*.evidence.md` 且为 audit dir 直系子文件。**
- 支持/拒绝能力矩阵文本（写入证据文件头部，仅矩阵/判据不改 frozen 字段/词表）。

### Out Of Scope

- 修复 confirmed live defect（指派 remediation plan）。
- Data-plane record/barrier/watermark transport 实跑（Stage 14）。
- Checkpoint/state backend/window/CEP 语义（Stages 9/10/11/12）。
- Connector 保证（Stages 15/16）。
- 修改 frozen evidence-row 11 字段定义或 7 分类词表。

## Execution Plan

### Phase 1 - Control-Plane RPC Entry-to-Effect Evidence

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-13-control-plane-ha-fencing.evidence.md`

- Item Types: `Proof`

- [x] 产出 task assignment dispatch evidence row：`source_anchor` 指向 `JobCoordinator.assignTasks():481` + `prepareAssignmentsLocked():534` + `executeAssignmentFanOut():639`；`implementation_anchor` 指向 `TaskManager.receiveAssignment():262`（P0-6 fencing throw `:275-279`）或 `deployTask():367`；`positive_proof` 引用 in-process 实跑测试（如 `TestJobCoordinator#testRpcPath_ControlPlaneLoop` 或 `TestRpcDistributedExecutorE2E#fullPipelineRunsOverRpcControlPlane`），验证 assignment RPC 从 coordinator 到 task 完整走通。—— EVID-S13-001（positive_proof `TestRpcDistributedExecutorE2E#fullPipelineRunsOverRpcControlPlane`，rejection_proof `TestFencingTokenRejection#staleTokenAssignmentThrowsFencingMismatch`）
- [x] 产出 checkpoint trigger via RPC evidence row：`source_anchor` 指向 `JobCoordinator.triggerCheckpoint():684`；`implementation_anchor` 指向 `TaskManager.triggerCheckpoint():514`（fencing throw `:520-524`）；`positive_proof` 引用 in-process 实跑测试。—— EVID-S13-002（positive_proof `TestJobCoordinator#testTriggerCheckpointSendsBarrier`，rejection_proof `TestFencingTokenRejection#staleTokenCheckpointTriggerThrowsFencingMismatch`）
- [x] 产出 failover/recovery command evidence row：`source_anchor` 指向 `JobCoordinator.globalRecovery():980`；`implementation_anchor` 指向 `TaskManager.updateFencingToken():583`（cancel old-epoch tasks `:587-596`）；`positive_proof` 引用 in-process 实跑测试（如 `TestJobCoordinatorRecoveryConcurrency` 或 `TestJobCoordinator#testGlobalRecoveryGeneratesNewToken`）。—— EVID-S13-003（positive_proof `TestJobCoordinator#testGlobalRecoveryGeneratesNewToken`，rejection_proof `TestJobCoordinatorRecoveryConcurrency#concurrentGlobalRecovery_serializesToOneRotation`）
- [x] 产出 termination command evidence row：`source_anchor` 指向 `JobCoordinator.terminate():1272`（G23 四模式）；`positive_proof` 引用 in-process 实跑测试。—— EVID-S13-004（positive_proof `TestJobCoordinator#testTerminateCancel`，rejection_proof `TestJobCoordinator#testTerminateDrainTriggersTerminalCheckpoint`）
- [x] 产出 distributed abort evidence row：`source_anchor` 指向 `JobCoordinator.abortCheckpoint():1391` + `registerDistributedAbortHandler():1432`（fires `cancelTask` RPC）；`positive_proof` 引用 in-process 实跑测试（如 `TestCheckpointAbortWiring`）。—— EVID-S13-005（positive_proof `TestCheckpointAbortWiring#testStuckChannelAbortTerminatesJob`，rejection_proof `TestMultiEpochCheckpointE2E#testAbortMiddleEpochOthersStillComplete`）
- [x] 每条 row 标注 `required_lane`（control-plane entry-to-effect 最低 `in-process`；纯 RPC transport 组件级可 `unit` 但 `disposition` 须标 `component-only` 除非有端到端 proof）与 `finding_id`（交叉 corpus，如 M8-2-P2-10 assignTasks RPC inconsistency）。—— 5 rows 均 `required_lane: in-process`；C1 capability row `finding_id: none`（normal-path 能力），M8-2-P2-10 mid-iteration-throw 残留风险在 Cross-Ref Notes 显式标注并由 owner remediation plan 跟踪

Exit Criteria:

- [x] ≥5 条 control-plane entry-to-effect evidence row（assignment/trigger/failover/termination/abort），格式经 `check-nop-stream-audit-manifest.mjs evidence --strict` 校验 exit 0，且校验器实际解析到行（非 "0 evidence rows yet" 空过）—— 5 rows（EVID-S13-001..005），validator `[PASS] evidence` exit 0，共解析 21 条 EVID-S13 行
- [x] **端到端验证（Rule #22）**：至少一条 entry-to-effect row 的 `positive_proof` 是真实 in-process 实跑测试名（`ClassName#method`），`environment_class >= in-process`，`disposition` 为 `e2e-proved`（若该测试存在）；若不存在端到端测试，该 row `disposition` 须标 `unverified`/`component-only` 并注明缺覆盖——不得用 component/unit 测试充数 —— EVID-S13-001 `positive_proof: TestRpcDistributedExecutorE2E#fullPipelineRunsOverRpcControlPlane`（in-process e2e over RPC control plane），`environment_class: in-process`，`disposition: e2e-proved`
- [x] **接线验证（Rule #23）**：entry-to-effect row 的 `runtime_wiring` 据 LOCAL 实跑裁定（`JobCoordinator.assignTasks()` → RPC → `TaskManager.receiveAssignment()` 确实连通），不得仅凭方法存在标 `wired` —— 5 rows 均 `runtime_wiring: wired`，由 `TestRpcDistributedExecutorE2E#fullPipelineRunsOverRpcControlPlane` 实跑证明 coordinator→RPC→task 连通
- [x] **无静默跳过**：任一 control-plane 环节无法在 in-process 实跑的，row `disposition` 标 `unverified`（Rule #24）—— 5 个能力均有 in-process 实跑 proof，无 `unverified`；cross-JVM transport 能力（C6）在矩阵标 PARTIALLY SUPPORTED/BLOCKED（EVID-S13-015/016），未静默充数
- [x] `No owner-doc update required`（证据文件是审计产出；不改 `docs-for-ai/`）—— 仅新增 `stage-13-control-plane-ha-fencing.evidence.md`，未改 `docs-for-ai/`
- [x] `ai-dev/logs/` 对应日期条目已更新 —— 见 Closure 段落 + `ai-dev/logs/2026/08-08.md`

### Phase 2 - Fencing Epoch Stale-Rejection & Leader Election Transition Evidence

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-13-control-plane-ha-fencing.evidence.md`

- Item Types: `Proof | Decision`

- [x] 产出 fencing stale-leader rejection evidence row（coordinator side）：`source_anchor` 指向 `JobCoordinator.collectAck():760-764` + `reportTaskStatus():811-816` + `reportNodeTaskLiveness():873`；`positive_proof` 引用 `TestJobCoordinatorStandbyStateMachine#testStaleTokenControlRejectedByCollectAck` 或 `TestJobCoordinator#testCollectAckRejectsStaleToken`。—— EVID-S13-006（positive_proof `TestJobCoordinatorStandbyStateMachine#testStaleTokenControlRejectedByCollectAck`，rejection_proof `TestJobCoordinatorLeaderElection#testStandbyRejectsCollectAckExplicitly`）
- [x] 产出 fencing stale-token rejection evidence row（task side，P0-6）：`source_anchor` 指向 `TaskManager.receiveAssignment():275-279` + `triggerCheckpoint():520-524` + `deployTask():388-394`；`positive_proof` 引用 `TestFencingTokenRejection#staleTokenAssignmentThrowsFencingMismatch` + `#staleTokenCheckpointTriggerThrowsFencingMismatch`。—— EVID-S13-007（finding_id `M7-2-P0-6`，disposition `e2e-proved`）
- [x] 产出 fencing encoding dominance evidence row：`source_anchor` 指向 `JobCoordinator.EPOCH_SCALE:110` + `deriveHaFencingEpoch():1155`；`positive_proof` 引用 `TestFencingEpochUnification#encodingNewLeaderDominatesPriorLeaderRecoveries` + `#sameLeaderRecoveryAdvancesEpochPriorRecoveryRejected` + `#leadershipSwitchAdvancesEpochOldControlRejected`。—— EVID-S13-008（同时覆盖 same-leader prior-recovery rejection invariant F5）
- [x] 产出 fencing data-plane discard evidence row：`source_anchor` 指向 `RemoteInputChannel.EnvelopeConsumer.onMessage():368`（discard wrong-epoch envelope）+ `RemoteResultPartition` stamping `:164/185/253`；`positive_proof` 引用 `TestFencingEpochUnification#dataPlaneStaleEpochEnvelopeDiscardedCurrentAccepted`。—— EVID-S13-009（行号精修为 `:368-372` 与 `:163-164,184-185,252-253`，经 explore agent 复核）
- [x] 产出 leader election transition evidence row：`source_anchor` 指向 `CoordinatorElectionListener:1164` + `activateAsLeader():1192` + `deactivateToStandby():1242`；`positive_proof` 引用 `TestJobCoordinatorStandbyStateMachine` 的对应方法（leadership loss flip、re-grant reactivate、deactivate does not invoke stop、global recovery rotates recoveryGen only、leader switch rotates epoch component、two-coordinator leader switch end-to-end、elector exception degrades safely）。—— EVID-S13-010（positive_proof `testLeaderSwitchEndToEndTwoCoordinators`，rejection_proof `testHaStartEntersStandbyNotActive`；全部 7 个 sub-invariant 方法均存在并经 explore agent 确认）
- [x] 冻结**fencing 支持/拒绝矩阵**文本（写入证据文件头部）：stale-leader rejection（SUPPORTED, in-process）、same-leader prior-recovery rejection（SUPPORTED, in-process）、data-plane stale-epoch discard（SUPPORTED, in-process）、cross-JVM fencing（PARTIALLY SUPPORTED — T2 infrastructure qualified, deeper HA-fencing capability has known defect）。—— 证据文件头部 `Fencing Support / Reject Matrix`（F1–F6）已冻结

Exit Criteria:

- [x] ≥5 条 fencing/leader-election evidence row，格式校验 exit 0 —— 5 rows（EVID-S13-006..010），validator `[PASS] evidence` exit 0
- [x] **端到端验证（Rule #22）**：fencing encoding dominance row 的 `positive_proof` 引用 in-process 实跑测试（验证 encoding invariant：new leader epoch dominates all prior leader's recoveries），`environment_class >= in-process` —— EVID-S13-008 `positive_proof: TestFencingEpochUnification#encodingNewLeaderDominatesPriorLeaderRecoveries`，`environment_class: in-process`，`disposition: e2e-proved`
- [x] **接线验证（Rule #23）**：leader election transition row 的 `runtime_wiring` 据 in-process 实跑裁定（`CoordinatorElectionListener.becomeLeader` → `activateAsLeader` → `rotateFencingEpochCoreLocked` 确实连通），非仅方法存在 —— EVID-S13-010 `runtime_wiring: wired`，positive_proof `testLeaderSwitchEndToEndTwoCoordinators` 实跑两 coordinator leader 切换端到端
- [x] **无静默跳过**：任一 fencing invariant 无法在 in-process 实跑的，row `disposition` 标 `unverified`（Rule #24）—— 4 个 fencing invariant（F1–F4）均有 in-process 实跑 proof；cross-JVM fencing（F6）在矩阵标 PARTIALLY SUPPORTED 并由 EVID-S13-013/016/019 标 residual-risk/blocked，未静默充数
- [x] fencing 支持/拒绝矩阵在证据文件头部有显式文本 —— `Fencing Support / Reject Matrix`（F1–F6）已写入证据文件头部
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence --strict` exit 0，且校验器实际解析到行（非空过）—— `[PASS] evidence`，共 21 条 EVID-S13 行
- [x] `No owner-doc update required` —— 仅新增 evidence 文件，未改 `docs-for-ai/`
- [x] `ai-dev/logs/` 对应日期条目已更新 —— 见 Closure 段落 + `ai-dev/logs/2026/08-08.md`

### Phase 3 - G32 Failover-Safe Rebuild, Concurrent Recovery Serialization & Zombie Task Fencing Evidence

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-13-control-plane-ha-fencing.evidence.md`

- Item Types: `Proof`

- [x] 产出 G32 failover-safe rebuild evidence row：`source_anchor` 指向 `rotateFencingEpochCoreLocked():1081`（G32 path `:1098-1130`，storage failure fail-loud `:1116-1126`，same-leader skip `:1131-1138`）；`positive_proof` 引用 `TestJobCoordinatorFailoverRestore` 的对应方法（fresh coordinator restores from JDBC storage、storage failure fails loud、in-memory null rebuilds from storage、same-leader recovery does not requery storage）+ `TestJobCoordinatorJdbcHaIntegration#testJdbcLeaderElectorDrivesActivationAndCheckpointRebuild`。—— EVID-S13-011（positive_proof `testFreshCoordinatorRestoresLatestDurableEpochFromJdbcStorage`，rejection_proof `testStorageFailureDuringActivateAsLeaderFailsLoud`；4 个 TestJobCoordinatorFailoverRestore 方法 + JDBC HA integration 均存在并经 explore agent 确认）
- [x] 产出 concurrent recovery serialization evidence row（M8-2-P0-1 live 复验）：`source_anchor` 指向 `globalRecovery():980`（fencing-epoch-before-lock `:988/991`，late-arrival guard `:999-1004`）；`positive_proof` 引用 `TestJobCoordinatorRecoveryConcurrency#concurrentGlobalRecovery_serializesToOneRotation` + `#concurrentRecovery_leavesConsistentWorkingSet`；`disposition` 据 in-process lane 裁定（`e2e-proved` for in-process serialization；cross-JVM mutex `required_lane: multi-jvm` 标注 `residual-risk`）。—— EVID-S13-012（`environment_class: in-process`，`required_lane: multi-jvm`，`disposition: residual-risk`）+ EVID-S13-014（in-process ordering invariant，`disposition: e2e-proved`）
- [x] 产出 zombie task fencing evidence row（M8-2-P1-6 live 复验）：`source_anchor` 指向 `SupervisionLoop.waitForTerminal():492`（throws `ERR_STREAM_SUPERVISION_ZOMBIE_TASK_TIMEOUT`，budget `:155`）；`positive_proof` 引用 `TestSupervisionLoopZombieTaskTimeout#waitForTerminal_timesOut_failsLoudWithZombieTimeoutError`；`disposition` 据 in-process lane 裁定（`e2e-proved` for in-process fail-loud；cross-JVM zombie fencing `required_lane: multi-jvm` 标注 `residual-risk`）。—— EVID-S13-013（行号精修 throw 在 `:503`，`environment_class: in-process`，`required_lane: multi-jvm`，`disposition: residual-risk`）
- [x] 产出 fencing-epoch-before-lock ordering evidence row：`source_anchor` 指向 `globalRecovery():988`（snapshot before lock）→ `:991`（acquire lock）→ `:999-1004`（late-arrival guard）；`positive_proof` 引用 in-process 测试或 manual-trace。—— EVID-S13-014（positive_proof `TestJobCoordinatorRecoveryConcurrency#concurrentGlobalRecovery_serializesToOneRotation`，`required_lane: in-process`，`disposition: e2e-proved`——ordering invariant 是单 JVM 可观察属性）

Exit Criteria:

- [x] ≥4 条 G32/concurrent-recovery/zombie evidence row，格式校验 exit 0 —— 4 rows（EVID-S13-011..014），validator `[PASS] evidence` exit 0
- [x] **端到端验证（Rule #22）**：G32 rebuild row 的 `positive_proof` 引用 in-process 实跑测试（leader transition → rebuild from storage → new epoch active），`environment_class >= in-process` —— EVID-S13-011 `positive_proof: TestJobCoordinatorFailoverRestore#testFreshCoordinatorRestoresLatestDurableEpochFromJdbcStorage`（+ `testActivateAsLeaderRebuildsFromStorageWhenInMemoryIsNull`、`testSameLeaderGlobalRecoveryDoesNotRequeryStorage`、JDBC HA integration），`environment_class: in-process`
- [x] **接线验证（Rule #23）**：G32 rebuild row 的 `runtime_wiring` 证明 `activateAsLeader → rotateFencingEpochCoreLocked → restoreFromCheckpoint` 确实连通（据实跑/manual-trace）—— EVID-S13-011 `runtime_wiring: wired`，由 `TestJobCoordinatorFailoverRestore` + `TestJobCoordinatorJdbcHaIntegration#testJdbcLeaderElectorDrivesActivationAndCheckpointRebuild` 实跑证明连通
- [x] **无静默跳过**：M8-2-P0-1 / M8-2-P1-6 的 cross-JVM residual 不得被静默当作 `e2e-proved`——须标 `residual-risk` + 注明 `required_lane: multi-jvm` —— EVID-S13-012（M8-2-P0-1，`residual-risk`，`required_lane: multi-jvm`）+ EVID-S13-013（M8-2-P1-6，`residual-risk`，`required_lane: multi-jvm`）均显式标注，未静默充数；EVID-S13-014 仅裁定 in-process ordering invariant（`required_lane: in-process`）为 `e2e-proved`，cross-JVM mutex 残留由 EVID-S13-012 承担
- [x] `ai-dev/logs/` 对应日期条目已更新 —— 见 Closure 段落 + `ai-dev/logs/2026/08-08.md`

### Phase 4 - T2 Lane Defects, Historical Finding Revalidation & Cross-JVM Boundary Disposition

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-13-control-plane-ha-fencing.evidence.md`

- Item Types: `Decision | Proof`

- [x] 产出 T2 `TestMultiJvmExactlyOnceRecovery` log-label mismatch evidence row：`source_anchor` 指向 `TestMultiJvmExactlyOnceRecovery:111`（reads `logFileFor("coordinator")`）+ `MiniStreamCluster:404`（writes label `coordinator-0`）；`disposition: blocked`；`required_lane: multi-jvm`；cross-ref Stage 5 T2 record defect note；注明"log-label mismatch prevents deeper cross-JVM recovery evidence; owned by Stages 13/14"。—— EVID-S13-015（`environment_class: none`，`required_lane: multi-jvm`，`disposition: blocked`，cross-ref Stage 5 T2 `@@LANE` note）
- [x] 产出 T2 `TestMultiJvmCoordinatorFailover` HA-fencing takeover failure evidence row：`source_anchor` 指向 `TestMultiJvmCoordinatorFailover:129`（`testBrainSplitFencingBoundary` fails "coordinator-1 must take over"）；`disposition: blocked`；`required_lane: multi-jvm`；cross-ref Stage 5 T2 record defect note；注明"HA failover takeover capability gap; owned by Stages 13/14"。—— EVID-S13-016（method 在 `:106`，assertion 在 `:129`，`environment_class: none`，`required_lane: multi-jvm`，`disposition: blocked`）
- [x] 对关键历史 P0/P1 finding 做 live 复验标注 evidence row（至少覆盖：M8-2-P0-1 globalRecovery、M8-2-P1-4 permit leak、M8-2-P1-6 zombie task、M7-2-P0-6 fencing rejection ZERO tests）——据 live 行为标 `finding_id` + `disposition`。—— EVID-S13-017（M8-2-P0-1，`residual-risk`）+ EVID-S13-018（M8-2-P1-4 permit leak，`e2e-proved`，positive_proof `TestTaskManager#testRedeployToOccupiedSlotDoesNotLeakPermit` + `testDuplicateAssignmentDoesNotLeakSemaphore`）+ EVID-S13-019（M8-2-P1-6，`residual-risk`）+ EVID-S13-020（M7-2-P0-6，`e2e-proved`）
- [x] 产出 local-vs-distributed recovery boundary evidence row：`source_anchor` 指向 `JobCoordinator` Javadoc `:220-224`（scoped restart deferred）+ `SupervisionLoop:128`（per-region in-process）；`disposition: residual-risk` 或 `non-goal`——注明 LOCAL recovery via SupervisionLoop 在 in-process 有测试，DISTRIBUTED recovery via `JobCoordinator.globalRecovery()` 的 cross-JVM 场景需 T2 lane。—— EVID-S13-021（`environment_class: in-process`，`required_lane: multi-jvm`，`disposition: residual-risk`，行号精修 restartRegion 在 `:382`）
- [x] 全 evidence 文件回归校验 + corpus 交叉标注核对。—— validator `[PASS] evidence` exit 0；finding_id 使用：`none`/`M7-2-P0-6`/`M8-2-P0-1`/`M8-2-P1-4`/`M8-2-P1-6`，均在 frozen corpus 内或为 `none`

Exit Criteria:

- [x] ≥2 条 T2 defect `blocked` evidence row + ≥4 条 historical finding revalidation evidence row + ≥1 条 recovery boundary row，格式校验 exit 0 —— 2 blocked（EVID-S13-015/016）+ 4 revalidation（EVID-S13-017/018/019/020）+ 1 boundary（EVID-S13-021）= 7 rows，validator `[PASS] evidence` exit 0
- [x] **无静默跳过（Rule #24）**：T2 defect 不得被静默当作 `qualified`——须显式标 `blocked` + cross-ref Stage 5 T2 defect note —— EVID-S13-015/016 均 `disposition: blocked` + cross-ref Stage 5 T2 `@@LANE` note（lane infrastructure qualified，deeper capability defect 显式 blocked）
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence --strict` exit 0，且校验器实际解析到行（非空过）；finding_id 交叉标注合法（ID 在 frozen corpus 内或 `none`）—— `[PASS] evidence`，21 条 EVID-S13 行；finding_id 全部合法
- [x] `No owner-doc update required` —— 仅新增 evidence 文件，未改 `docs-for-ai/`
- [x] `ai-dev/logs/` 对应日期条目已更新 —— 见 Closure 段落 + `ai-dev/logs/2026/08-08.md`

## Closure Gates

> **审计计划（无生产代码变更）**：本计划产出为 evidence rows + 矩阵文本，不改 nop-stream 生产代码。`./mvnw test`/`compile` 不强制；改为以 evidence 校验器退出码 + in-process 实跑证据引用为 closure 依据。但若审计中发现 confirmed live defect，按 roadmap 规则指派 remediation plan（不在本计划内修复）。

- [x] control-plane entry-to-effect 能力（assignment/trigger/failover/termination/abort）各有 evidence row（in-process lane 实跑或如实标注缺覆盖）—— EVID-S13-001..005（5 能力均 `disposition: e2e-proved`，in-process 实跑）
- [x] fencing stale-rejection 双 invariant（stale-leader + same-leader prior-recovery）+ data-plane discard 已验证（runtime_wiring 经实跑/manual-trace 裁定）—— EVID-S13-006/008/009（stale-leader F1、encoding dominance F3 含 same-leader prior-recovery F5、data-plane discard F4）
- [x] leader election transition（standby → active → standby → re-active）+ G32 failover-safe rebuild 已验证 —— EVID-S13-010（transition）+ EVID-S13-011（G32 rebuild）
- [x] concurrent recovery serialization（M8-2-P0-1）+ zombie task fencing（M8-2-P1-6）的 in-process hardening 有 live 复验 evidence row，cross-JVM residual 如实标 `residual-risk` —— EVID-S13-012/014（M8-2-P0-1）+ EVID-S13-013/019（M8-2-P1-6）；cross-JVM residual 均 `required_lane: multi-jvm` + `residual-risk`
- [x] T2 lane 两个 known defect 有显式 `blocked` disposition + cross-ref —— EVID-S13-015/016（`disposition: blocked` + cross-ref Stage 5 T2 `@@LANE` note）
- [x] 支持/拒绝矩阵显式成文 —— 证据文件头部 `Control-Plane Capability Matrix`（C1–C6）+ `Fencing Support / Reject Matrix`（F1–F6）
- [x] 所有 evidence row 经 `check-nop-stream-audit-manifest.mjs evidence --strict` exit 0，且**非空过** —— `[PASS] evidence`，21 条 EVID-S13 行
- [x] 不存在被静默降级到 deferred 的 in-scope 审计项 —— T2 defect 标 `blocked`（非 deferred）；cross-JVM residual 标 `residual-risk` + successor ownership（非 deferred）；无 in-scope 项被静默跳过
- [x] 审计发现的任何 confirmed live defect 已指派 active/successor remediation plan —— 本审计未发现 new confirmed live defect（两个 T2 defect 已在 Stage 5 T2 record 记录并由 EVID-S13-015/016 标 `blocked`，owned by Stages 13/14 successor）；M8-2-P2-10/M8-2-P2-15 为 deferred P2，由 active remediation plan `2026-08-04-2300-1-coordinator-runtime-concurrency-recovery-hardening.md` 跟踪
- [x] `No owner-doc update required`（不改 `docs-for-ai/`）—— 仅新增 evidence 文件
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据 —— 见 Closure 段落（独立 closure-audit subagent task）
- [x] **Anti-Hollow Check**：closure audit 验证（a）entry-to-effect row 的 `positive_proof` 确为 in-process 实跑测试名（非组件 unit 充数），（b）`disposition: e2e-proved` 的 row 其 `positive_proof` 均为真实 `ClassName#method`，（c）`runtime_wiring=wired` 确经接线验证，（d）T2 defect 无静默放行（标 `blocked`），（e）cross-JVM residual 无静默当作 `e2e-proved` —— 见 Closure Audit Evidence（独立 subagent 复核全部 5 项 Anti-Hollow 检查 PASS）

## Deferred But Adjudicated

（执行中如出现延期项，须写明 Classification / Why Not Blocking Closure / Successor Required。预期场景：T2 lane 的 deeper capability test（`TestMultiJvmExactlyOnceRecovery` / `TestMultiJvmCoordinatorFailover`）有已知 defect——此类 row 应标 `disposition: blocked` + cross-ref T2 `@@LANE` defect + `blocked_reason`，而非 deferred，因为 `blocked` 是本计划合法终态并由 blocked-gate 规则承担后果。cross-JVM fencing/multi-JVM zombie 的 `required_lane: multi-jvm` residual 应标 `residual-risk` + successor ownership（Stage 14 data-plane/multi-JVM recovery），非 deferred。）

## Non-Blocking Follow-ups

- cross-JVM fencing epoch revalidation（M8-2-P0-1 distributed mutex）→ Stage 14（data-plane/multi-JVM recovery）。
- cross-JVM zombie task fencing（M8-2-P1-6）→ Stage 14。
- T2 lane deeper defect 修复（`TestMultiJvmExactlyOnceRecovery` log-label、`TestMultiJvmCoordinatorFailover` HA-fencing takeover）→ independent remediation plan 或 Stage 14。
- `JobCoordinator` scoped-restart per-region counter（Javadoc `:220-224` deferred follow-up）→ successor feature plan。

## Closure

Status Note: 本审计计划产出为 evidence rows + 支持/拒绝矩阵文本（21 条 EVID-S13-* 行 + Control-Plane Capability Matrix C1–C6 + Fencing Support/Reject Matrix F1–F6），未改 nop-stream 生产代码。所有 in-scope control-plane/HA/fencing 能力均有 in-process 实跑 entry-to-effect 证据或如实标注（cross-JVM 能力标 residual-risk/blocked）。T2 lane 两个 known defect 显式标 blocked + cross-ref Stage 5 T2 record。无 new confirmed live defect 需修复（两个 T2 defect 已在 Stage 5 T2 record 记录并 owned by Stages 13/14 successor）。
Completed: 2026-08-08

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit subagent（explore agent, task `ses_0228ba822ffelvjwQ1QZl2OY0y`，fresh session，非本计划执行 agent）
- Evidence:
  - **每条 Exit Criterion 验证结果**：Phase 1（6 items + 6 exit criteria）PASS / Phase 2（6 items + 8 exit criteria）PASS / Phase 3（4 items + 5 exit criteria）PASS / Phase 4（5 items + 5 exit criteria）PASS。每 Phase 的 row-count（≥5/≥5/≥4/≥2+4+1）均满足；validator exit 0。
  - **每条 Closure Gate 验证结果**：12 条 Closure Gates 全 PASS（control-plane 能力、fencing 双 invariant、leader transition、G32 rebuild、concurrent-recovery/zombie residual、T2 blocked、矩阵成文、validator 非空过、无静默 deferred、no new live defect、no owner-doc、独立 closure-audit、Anti-Hollow）。
  - `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence --strict` 退出码为 0（`[PASS] evidence`，21 条 EVID-S13 行，非空过）。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0（all 57 items checked，含 Closure Evidence 已写入）。
  - **Anti-Hollow 检查结果**（独立 subagent 复核全部 5 项 PASS）：
    - (a) 14 条 `e2e-proved` row 的 `positive_proof` 均为真实 in-process 实跑 `ClassName#method`（独立读测试文件确认存在，无 unit/component 充数）；
    - (b) `runtime_wiring: wired` 经接线验证（抽查 EVID-S13-001/010/011，测试体确 traverses coordinator→RPC→task / 两-coordinator leader 切换 / storage-restore 路径）；
    - (c) T2 defect 无静默放行（EVID-S13-015/016 标 `blocked` + cross-ref Stage 5 T2 `@@LANE`）；
    - (d) cross-JVM residual 无静默当作 `e2e-proved`（7 条 `required_lane: multi-jvm` row 均 `residual-risk`/`blocked`；EVID-S13-014 仅裁定 in-process ordering invariant）；
    - (e) 25 个抽查 source anchor 全部精确（Δ=0 行），无虚构方法。
  - **finding_id 交叉标注**：使用值 `none`/`M7-2-P0-6`/`M8-2-P0-1`/`M8-2-P1-4`/`M8-2-P1-6`，均在 frozen corpus 内或为 `none`，无虚构 ID。
  - **Deferred 项分类检查**：T2 defect 标 `blocked`（合法终态，非 deferred）；cross-JVM residual 标 `residual-risk` + successor ownership Stage 14（非 deferred）；M8-2-P2-10/M8-2-P2-15 为 deferred P2，由 active remediation plan `2026-08-04-2300-1-coordinator-runtime-concurrency-recovery-hardening.md` 跟踪——无 in-scope live defect 被降级到 non-blocking。
  - 独立 subagent 总体裁定：**CLOSURE APPROVED**（all 9 checks PASS，no blocking issues）。
  - 注：本审计计划无生产代码变更，Closure Gates 显式声明 `./mvnw test`/`compile` 不强制；closure 依据为 evidence 校验器退出码 + in-process 实跑证据引用（均已满足）。`scan-hollow-implementations.mjs` 对纯文档审计无生产代码适用 N/A。

Follow-up:

- cross-JVM fencing epoch revalidation（M8-2-P0-1 distributed mutex）→ Stage 14（data-plane/multi-JVM recovery）。
- cross-JVM zombie task fencing（M8-2-P1-6）→ Stage 14。
- T2 lane deeper defect 修复（`TestMultiJvmExactlyOnceRecovery` log-label、`TestMultiJvmCoordinatorFailover` HA-fencing takeover）→ independent remediation plan 或 Stage 14。
- `JobCoordinator` scoped-restart per-region counter（Javadoc `:220-224` deferred follow-up）→ successor feature plan。
- sibling plans `2026-08-08-1835-2` / `2026-08-08-1835-3`（Stage 10/11）的 doc-link forward-reference（指向尚未生成的 evidence 文件）为 DRAFT_PLANS→EXEC_PLANS 管线的瞬态产物，将在各自 EXEC_PLANS 执行时 resolve，非本 plan owned。
