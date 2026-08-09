# T2 Multi-JVM Capability-Gap Remediation (Readiness §2b Blockers)

> Plan Status: completed
> Last Reviewed: 2026-08-09
> Draft Review: independent sub-agent adversarial review passed (0 Blockers; Major P2-1 unbounded-investigation resolved via bounded diagnostic sub-step + decision gate; re-review CONSENSUS REACHED). Sessions ses_01afd97a6ffeQZdgw3WoN8GOe6 (review) + ses_01af87ee4ffebQwbMg7VuCPHlx (re-review).
> Mission: nop-stream-independent-audit
> Work Item: T2 multi-JVM capability-gap remediation (discharges the "independent remediation plan" ownership assigned in readiness report §2b)
> Source: `ai-dev/audits/nop-stream-independent-audit/stage-23-readiness-report.md` §2b (4 capability-gap rows: EVID-S13-015, EVID-S13-016, EVID-S14-013, EVID-S14-014); `stage-13-control-plane-ha-fencing.evidence.md`; `stage-14-data-plane-multi-jvm-recovery.evidence.md`; `environment-qualification.md` T2 lane note
> Related: Roadmap items 13 (control-plane/HA audit, done) and 14 (data-plane/multi-JVM audit, done) produced these `blocked` rows; readiness item 23 (done) aggregated them as the sole code-remediation blockers to a blanket `ready` verdict.

## Purpose

收口阻止 nop-stream 达到 blanket production-ready 的两个 T2 multi-JVM capability-gap 缺陷，使 4 条当前 `blocked` 的证据行可被重新证明为 `e2e-proved`。这两个缺陷由 Stage 13/14 审计发现、由 Stage 23 readiness 报告 §2b 明确归属为 "independent code-remediation plan"，但目前没有任何计划承载该归属。本计划即承担该归属。

## Current Baseline

经 2026-08-09 live repo 核对（所有锚点均已二次确认文件路径与行号）：

### T2 lane 已 qualified（基础设施可用）

- `environment-qualification.md` T2 行：`status: qualified`。`TestMiniStreamClusterProcessSpawn` 3/3 PASS（真实跨 JVM ProcessBuilder spawn + TaskManager 注册 + coordinator JVM 启动）。证明 multi-JVM 基础设施本身可用，故这两个缺陷是 honest capability-level `blocked`，非 silent skip。
- T2 lane 调用命令（gate 属性 `nop.stream.test.multi-jvm.enabled=true`）：
  `./mvnw test -pl nop-stream/nop-stream-runtime -am -Dnop.stream.test.multi-jvm.enabled=true -Dtest=<TestClass> -Dsurefire.failIfNoSpecifiedTests=false`
- 这两个测试类带 `@EnabledIfSystemProperty(named = "nop.stream.test.multi-jvm.enabled", matches = "true")`，故**默认 `./mvnw test` suite 中它们被 SKIPPED**——标准 suite 绿色不能证明这两个 capability，也不能捕获它们的回归。

### Defect 1 — TestMultiJvmExactlyOnceRecovery log-label mismatch（EVID-S13-015 / EVID-S14-013）

- `TestMultiJvmExactlyOnceRecovery.java` 在 `:103, :111, :142, :146, :229` 调用 `cluster.logFileFor("coordinator")`（**裸 "coordinator"**）。
- `MiniStreamCluster.spawnJobCoordinator(int index, boolean haMode)` 在 `:404` 使用 `label = "coordinator-" + index`（即 `"coordinator-0"`），`coordinatorKey(int)` 在 `:228-230` 返回 `"coordinator-" + index`；`logFileFor(String)` 在 `:310-312` 返回 `runDir/logs/<nodeId>.log`。
- 因此真实日志文件是 `logs/coordinator-0.log`，而测试读取 `logs/coordinator.log`（不存在）→ `:111` 处 `Files.size(...)` 抛 `NoSuchFileException`，测试在能验证 cross-JVM exactly-once recovery 之前就 ERROR。
- 对比：`TestMultiJvmCoordinatorFailover` 正确使用了 `logFileFor("coordinator-1")`（`:82`）与 `logFileFor("coordinator-" + coordIndex)`（`:144`）。故这是 ExactlyOnceRecovery 测试独有的 label 错误。
- **未知的下游**：label 修复后测试能否 PASS 取决于 coordinator JVM 是否真能在共享注册表中检测到被 kill 的 TaskManager 并触发 `globalRecovery` 轮转 fencing epoch。in-process 证据（`TestJobCoordinatorRecoveryConcurrency`、EVID-S14-020）已证明 `globalRecovery` 串行化在 in-process 成立；cross-JVM 段尚未被证明（正因本 defect 被阻断）。Phase 1 须处理"label 修复后若浮现更深层 cross-JVM recovery 缺陷"的情形。

### Defect 2 — TestMultiJvmCoordinatorFailover HA-fencing takeover gap（EVID-S13-016 / EVID-S14-014）

- `TestMultiJvmCoordinatorFailover.testBrainSplitFencingBoundary()`（`:105-134`）：HA 模式启动 1 个 TaskManager + coordinator-0（`start(true)`）+ coordinator-1（`spawnJobCoordinator(1)`），等 coordinator-0 赢得 leadership，kill coordinator-0，等 coordinator-1 接管（lease `leader_id` 翻转为 coordinator-1 且 `leader_epoch` 严格增大）。
- `:129` `assertTrue(epoch1 > 0L, "coordinator-1 must take over")` 失败——coordinator-1 在 `FAILOVER_TIMEOUT_MS`（30s）内未接管。lease 配置（`spawnJobCoordinator` `:402-403`：`leaderLeaseMs=3000`、`leaderCheckIntervalMs=300`）表明 lease 应在 coordinator-0 被 kill 后约 3s 过期，coordinator-1 每 300ms 轮询应能接管；30s 仍未接管说明是真实 capability gap 而非单纯的超时调参问题。
- 同类的 `testCoordinatorKillTriggersStandbyTakeover()`（`:53-97`）具有相同 failover 结构，预期共享同一根因。
- HA 机制**真实存在**（非空壳）：`JdbcLeaderElector.java`、`JobCoordinator` 引用 leader election；`JobCoordinatorMain` 接收 `leaderElectorEnabled/leaderClusterId/leaderHostId/leaderLeaseMs/leaderCheckIntervalMs` 参数。gap 在 standby→leader 跨 JVM 接管路径（lease 过期检测 / leadership 竞争获取 / 接管后 epoch 推进），根因须 Phase 2 调查确定。
- `dropStreamTablesQuietly()`（`MiniStreamCluster:366-376`）清理 `nop_stream_task_assignment/node/coordinator/msg_queue` 但**不清理 `nop_stream_leader`**；每 run 的 `cluster_id = "job-" + runId` 唯一，故跨 run 的 stale lease 行不匹配 WHERE 子句——不构成已知根因，但 Phase 2 调查时需排除。

### Readiness 影响

- Stage 23 readiness gate（`evidence-schema.md` Rule S5-2）：任何 `required_lane: multi-jvm` 的 `blocked` 行禁止 blanket `ready`。当前判定为 bounded `ready only for enumerated e2e-proved capability/environment pairs`（126 行），正是被这 4 行 + §2a 的 4 行 lane-blocked 阻断。
- §2a 的 4 行（Kafka/Pulsar/PostgreSQL/Debezium 外部 backend 不可用）owner 为 infra provisioning（out of audit scope），**不在本计划范围**。
- §2b 的 4 行 owner 为 "independent code-remediation plan"——即本计划。

## Goals

- Defect 1：消除 `TestMultiJvmExactlyOnceRecovery` 的 log-label mismatch，使测试能在 T2 lane 运行并证明 cross-JVM kill/restart → fencing epoch 轮转 → recovery redeploy 路径（若浮现更深层 cross-JVM recovery 缺陷则一并 root-cause 修复）。
- Defect 2：root-cause 并修复 `TestMultiJvmCoordinatorFailover` 的 HA-fencing takeover gap，使 standby coordinator 能在 active 被 kill 后跨 JVM 接管 leadership（lease 翻转 + epoch 严格递增）。
- 两个 multi-JVM 测试类在 T2 lane（gate on）全部方法 PASS，产生可复核的 cross-JVM capability 证据，使 EVID-S13-015/016 与 EVID-S14-013/014 具备从 `blocked` 重分类为 `e2e-proved` 的客观前置条件。

## Non-Goals

- 重分类 frozen evidence 文件中的 4 行（`blocked` → `e2e-proved`）与重跑 Stage 23 readiness gate / 重决策 blanket-ready。该步骤是独立 successor audit 工作（unfreeze evidence corpus → 重分类 → 重跑 readiness validator），不属本 remediation plan；本计划只产出可使其成立的能力证明。
- §2a 的 4 行 lane-blocked（Kafka/Pulsar/PostgreSQL/Debezium 外部 backend）。需要真实基础设施 provisioning，out of audit scope。
- 新增 source→keyBy→sink 完整 cross-JVM 共享 sink exactly-once 断言（`TestMultiJvmExactlyOnceRecovery` Javadoc 明确此为 "Stage 43+ follow-up"，本测试验证的是 enabling infrastructure：cross-JVM deployTask RPC + recovery redeploy + fencing）。
- 重写 leader election 架构或 coordinator actor 模型（仅 root-cause 修复接管路径，不重构整体设计）。
- 修改 roadmap Work Items 状态（由 engine/executor 管理）。

## Scope

### In Scope

- `nop-stream-runtime/src/test/java/io/nop/stream/runtime/multijvm/TestMultiJvmExactlyOnceRecovery.java`：log-label 修正 + 若浮现更深层 cross-JVM recovery 缺陷的 root-cause 修复。
- `nop-stream-runtime/src/test/java/io/nop/stream/runtime/multijvm/TestMultiJvmCoordinatorFailover.java`：随根因修复同步（若根因在产品代码则测试无需改；若根因在 test harness/setup 则修 harness）。
- HA-fencing takeover 根因所在产品代码（候选：`JdbcLeaderElector`、`JobCoordinator` HA/leader-gated 路径、`JobCoordinatorMain`、`MiniStreamCluster` HA 模式 spawn/lease 参数）——由 Phase 2 root-cause 定位后确定具体文件。
- 针对修复路径的回归测试（若根因为产品代码缺陷，须有针对性回归测试证明 standby→leader 接管在 cross-JVM 成立）。

### Out Of Scope

- §2a lane provisioning（外部 backend）。
- evidence 文件重分类与 readiness 重决策（successor audit）。
- `nop_stream_leader` 表 schema 变更（除非 root-cause 明确要求）。
- in-process HA 测试（`TestJobCoordinatorFailoverRestore`、`TestJobCoordinatorJdbcHaIntegration`）的行为——它们已 PASS 且是默认 suite 的 HA 覆盖来源；本计划不改动其语义，除非 root-cause 表明它们与 cross-JVM 路径共享同一缺陷。

## Execution Plan

### Phase 1 — Remediate TestMultiJvmExactlyOnceRecovery log-label defect + prove cross-JVM exactly-once recovery

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/multijvm/TestMultiJvmExactlyOnceRecovery.java`；若浮现更深层缺陷则延伸至 coordinator recovery 跨 JVM 路径

- Item Types: `Fix | Proof`

- [x] 修正 `TestMultiJvmExactlyOnceRecovery` 中所有裸 `"coordinator"` 的 log-label 读取，对齐 `MiniStreamCluster` 实际 key。**已落地**：测试引入常量 `COORDINATOR_LABEL = "coordinator-0"`（TestMultiJvmExactlyOnceRecovery.java:87），所有 `logFileFor(...)` 调用经该常量（行 115/123/154/158/241），与 `MiniStreamCluster.spawnJobCoordinator(int,boolean)` 的 `label = "coordinator-" + index`（MiniStreamCluster.java:404）及 `coordinatorKey(int)`（:228-230）一致。
- [x] 在 T2 lane（gate on）运行 `TestMultiJvmExactlyOnceRecovery`；PASS。**证据**：`./mvnw test -pl nop-stream/nop-stream-runtime -am -T 1C -Dnop.stream.test.multi-jvm.enabled=true -Dtest=TestMultiJvmExactlyOnceRecovery -Dsurefire.failIfNoSpecifiedTests=false` → `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`（elapsed 67.02s）。无 label 之后更深 cross-JVM recovery 缺陷浮现。
- [x] 修复后该测试 `multiJvmDeployKillRecoverFencing` 方法在 T2 lane PASS：断言 initial epoch > 0（行 113）、kill tm-1 后重启（行 126-138）、recovered epoch 严格 > initial epoch（行 146）、recovered assignment count >= 2（行 151）、coordinator 日志含 recovery 事件（行 159-165）。全部成立。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 仓库中可观察到 `TestMultiJvmExactlyOnceRecovery` 不再读取裸 `"coordinator"` log label（`COORDINATOR_LABEL = "coordinator-0"`，与 `MiniStreamCluster` 的 `"coordinator-"+index` keying 一致）
- [x] `Files.size` 不再因 label mismatch 抛 `NoSuchFileException`（label 缺陷已闭合；T2 lane 运行 1/0/0 证明）
- [x] **端到端验证（Rule #22）**：`./mvnw test -pl nop-stream/nop-stream-runtime -am -T 1C -Dnop.stream.test.multi-jvm.enabled=true -Dtest=TestMultiJvmExactlyOnceRecovery -Dsurefire.failIfNoSpecifiedTests=false` PASS（Tests run: 1, Failures: 0, Errors: 0, elapsed 67.02s）——真实 spawn 2 个 TaskManager JVM + 1 coordinator JVM，kill/restart 一个 TM，断言 fencing epoch 跨 JVM 轮转
- [x] **接线验证（Rule #23）**：测试断言 `nop_stream_task_assignment` 表行存在且 recovery 后 fencing_token 严格递增（`countTaskAssignments` 行 209、`waitForEpochRotation` 行 248、`readLatestFencingEpoch` 行 186 读 `MAX(CAST(fencing_token AS BIGINT))`），证明 coordinator JVM 与 TaskManager JVM 在运行时确实经 RPC 连通
- [x] **无静默跳过（Rule #24）**：测试含真实硬断言（assertTrue/assertFalse），无 `continue`/空体/吞异常；本 Phase 未引入任何静默让测试"通过"的代码
- [x] `No owner-doc update required`：纯 test-harness label 修正（COORDINATOR_LABEL 常量化），不改变 recovery/fencing 契约语义
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/08-09.md`）

### Phase 2 — Root-cause + fix TestMultiJvmCoordinatorFailover HA-fencing takeover gap + prove cross-JVM coordinator failover

Status: completed
Targets: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/elector/AbstractPollingLeaderElector.java`（根因修复）；`nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/multijvm/TestMultiJvmCoordinatorFailover.java`（test harness startup-race 修复）；`nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/cluster/TestJdbcLeaderElector.java`（回归测试）

- Item Types: `Fix | Proof`

- [x] **诊断先行（bounded deliverable）**：双 gate 运行 `TestMultiJvmCoordinatorFailover`（`-Dnop.stream.test.multi-jvm.enabled=true -Dnop.stream.test.multi-jvm.preserve-artifacts=true`），保留 coordinator-0/1.log 与共享 H2 DB。诊断记录（dimension (i)–(iv)）：
  - **(i) coordinator-1 进程存活**：YES。日志含 188,404 次 `refreshLeader` UPDATE，证明 coordinator-1 进程存活且 elector 在轮询。
  - **(ii) coordinator-1 elector 是否在轮询**：YES，但**混乱**：4 个并发 worker 线程（`nop-global-worker-3-1/2/3/4`）同时运行 `checkElection()`，188,409 次 `readLeaseRow` 调用——`MICROSECONDS` 调度导致任务洪泛。
  - **(iii) DB 查询/连接异常**：初始 INSERT 竞态导致 `duplicate-key`（4 线程并发 INSERT 同一 cluster_id 行）；无 H2 AUTO_SERVER backend 中断。
  - **(iv) lease expireAt vs now + leaseSafeGap**：判定方向正确（`now < expireAt - gap`），但因 `MICROSECONDS` 洪泛导致并发执行，in-memory leaderEpoch 在 leader/follower 间振荡，状态机被破坏，takeover 延迟至 ~71s（远超 30s 超时）。
- [x] Root-cause 调查：**根因为产品代码缺陷** — `AbstractPollingLeaderElector.scheduleCheck()` 使用 `TimeUnit.MICROSECONDS` 而非 `TimeUnit.MILLISECONDS`（自 `3c4eec214` 初版即存在的 long-standing bug）。`checkIntervalMs=300` 被当作 300 微秒（0.3ms）调度，使多线程 executor（`GlobalExecutors.globalWorker()`）上 4 个 worker 线程并发执行 `checkElection()`，破坏 leader election 状态机。修复：`MICROSECONDS` → `MILLISECONDS`。**次要根因为 test harness startup-race**：`TestMultiJvmCoordinatorFailover` 在 `cluster.start(true)` 后立即 spawn coordinator-1，两个 coordinator JVM 近乎同时启动并竞争 INSERT lease 行——coordinator-1 可能赢得初始 leader 竞争，导致测试假设（coordinator-0 是初始 leader）不成立。修复：在两个测试方法中，先 `waitForLeaderAndAssignments(cluster, 0)` 确认 coordinator-0 成为 leader，再 `spawnJobCoordinator(1)` spawn standby。
- [x] 实施修复 + 针对性回归测试：
  - 产品代码修复：`AbstractPollingLeaderElector.scheduleCheck()` — `TimeUnit.MICROSECONDS` → `TimeUnit.MILLISECONDS`（nop-cluster-core，1-word fix）。
  - test harness 修复：`TestMultiJvmCoordinatorFailover` — 两个测试方法均改为先确认 coordinator-0 leadership 再 spawn coordinator-1 standby；`testBrainSplitFencingBoundary` 补充 `assertTrue(epoch0 > 0L)` 前置断言。
  - 回归测试：(1) `TestJdbcLeaderElector.testPollingCadenceIsMillisecondsNotMicroseconds` — 通过统计 1.5s 窗口内 `refresh_at` 变化次数（<50 为 MILLISECONDS，MICROSECONDS 会产生数千次），直接守护时间单位回归；(2) `TestJdbcLeaderElector.testMultiThreadedExecutorTakeoverGuardsConcurrentCheckElection` — 在 4 线程 executor 上验证 standby→leader takeover 产生严格更大 epoch。
- [x] 在 T2 lane（gate on）运行 `TestMultiJvmCoordinatorFailover`，两个方法（`testCoordinatorKillTriggersStandbyTakeover` + `testBrainSplitFencingBoundary`）均 PASS。**证据**：`Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`（elapsed 8.293s，修复前 113.7s 全部失败）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] root-cause 已记录（上文 dimension (i)–(iv) 诊断记录 + 最终根因），明确区分"产品代码缺陷"（`AbstractPollingLeaderElector` MICROSECONDS bug）vs "test harness/setup 缺陷"（`TestMultiJvmCoordinatorFailover` startup-race）；诊断有界定终止（4 项核查全部完成才改代码）
- [x] `:129` `assertTrue(epoch1 > 0L)` 不再失败——coordinator-1 在 active 被 kill 后跨 JVM 接管 leadership（lease `leader_id` 翻转为 coordinator-1 且 `leader_epoch` 严格 > 旧值）。T2 lane 2/2 PASS。
- [x] **端到端验证（Rule #22）**：`./mvnw test -pl nop-stream/nop-stream-runtime -Dnop.stream.test.multi-jvm.enabled=true -Dtest=TestMultiJvmCoordinatorFailover -Dsurefire.failIfNoSpecifiedTests=false` PASS（Tests run: 2, Failures: 0, Errors: 0, elapsed 8.293s）——真实 spawn 2 coordinator JVM 共享 JDBC lease 表，kill active，断言 standby 接管 + fencing invariant（新 epoch 严格 > 旧 epoch）
- [x] **接线验证（Rule #23）**：测试断言基于共享 `nop_stream_leader` lease 表的 `leader_id`/`leader_epoch` 翻转（`readLeaseRow` 读 `SELECT leader_id, leader_epoch FROM nop_stream_leader WHERE cluster_id = ?`），证明两个 coordinator JVM 在运行时确实经共享 lease 表协调 leadership
- [x] **新功能必有测试（Rule #25）**：根因含产品代码缺陷，新增 2 个针对性回归测试：(1) `testPollingCadenceIsMillisecondsNotMicroseconds`——验证 MILLISECONDS 调度 cadence（refresh_at 变化 <50/1.5s，MICROSECONDS 会产生数千次）；(2) `testMultiThreadedExecutorTakeoverGuardsConcurrentCheckElection`——在 4 线程 executor 上验证 standby→leader takeover 产生严格更大 epoch（模拟 GlobalExecutors 多线程条件）
- [x] **无静默跳过（Rule #24）**：修复为 1-word 时间单位修正 + test harness 排序调整，无放宽断言/吞失败/延长超时；两个 T2 lane 测试的硬断言（epoch 严格递增、lease leader_id 翻转）均保留
- [x] `No owner-doc update required`：修复不改变 HA/leader-election/fencing 契约语义——`MICROSECONDS` 是 long-standing 实现缺陷（应为 MILLISECONDS），修复使实际行为对齐文档描述的 lease-based leader election 契约（`AbstractPollingLeaderElector` 类注释 + `JdbcLeaderElector` 类注释描述的 lease semantics 均假设 ms 级别轮询）
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/08-09.md`）

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 两个 in-scope capability-gap 缺陷均已修复，而非以放宽断言/静默方式绕过：Defect 1（COORDINATOR_LABEL 常量化）+ Defect 2（MICROSECONDS→MILLISECONDS 产品代码修复 + test harness startup-race 修复）
- [x] 两个 multi-JVM 测试类在 T2 lane（gate on）全部方法 PASS，产生 cross-JVM capability 证据：TestMultiJvmExactlyOnceRecovery 1/1 PASS（67s），TestMultiJvmCoordinatorFailover 2/2 PASS（8.3s）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope capability gap（两个缺陷均已 root-cause 修复 + 回归测试）
- [x] 受影响的 owner docs：`No owner-doc update required`（MICROSECONDS 是实现缺陷，修复使行为对齐已文档化的 lease-based leader election 契约）
- [x] 独立子 agent closure-audit 已完成并记录证据（见下方 Closure Audit Evidence）
- [x] **Anti-Hollow Check**：closure audit 已验证（a）multi-JVM 测试真实 spawn 跨 JVM 进程（`MiniStreamCluster.spawnJobCoordinator` 经 `ProcessBuilder.start()` 真实 OS 进程，非 in-process stub），经共享 H2 DB + JDBC lease 表 + PollingJdbcMessageService RPC 连通；（b）修复在运行时生效（1-word `MICROSECONDS`→`MILLISECONDS` 改变实际调度行为，cadence 从 0.3ms 恢复为 300ms，coordinator 日志从 150MB+ 降至 655KB-1.3MB，takeover 从 >71s 降至 <8s）
- [x] 默认 suite（gate off）无回归：`./mvnw test -pl nop-stream/nop-stream-runtime -T 1C` PASS（BUILD SUCCESS，TestJdbcLeaderElector 5/5 含新增 2 个回归测试，两个 multi-JVM 测试在默认 suite 中 skipped）
- [x] `./mvnw compile -pl nop-stream -am -T 1C` PASS（EXIT 0）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -T 1C`（默认 gate off）PASS（BUILD SUCCESS）。注：`nop-stream-rocksdb` 有 1 个 pre-existing benchmark 测试失败（`incrementalCheckpointIsFasterThanFullScanForLargeState`，性能断言，与本计划改动无关）
- [x] T2 lane gated 运行 PASS：Phase 1 `TestMultiJvmExactlyOnceRecovery` 1/1 PASS；Phase 2 `TestMultiJvmCoordinatorFailover` 2/2 PASS
- [x] checkstyle / 代码规范检查通过：imports 分组正确（io.nop.* → third-party → java.*），4-space 缩进，无注释残留

## Deferred But Adjudicated

（draft 阶段无；执行中若出现经裁定的 non-blocking residual 再记入此处）

## Non-Blocking Follow-ups

- evidence 文件重分类（EVID-S13-015/016、EVID-S14-013/014 从 `blocked` → `e2e-proved`）与 Stage 23 readiness gate 重跑 / blanket-ready 重决策：successor audit 工作，依赖本计划产出能力证明后方可进行。
- `TestMultiJvmExactlyOnceRecovery` Javadoc 标注的 "Stage 43+ follow-up"（完整 source→keyBy→sink cross-JVM 共享 sink exactly-once 断言）：独立功能增强，不阻塞当前 capability 证明。
- §2a 的 4 行 lane-blocked（外部 backend provisioning）：out of audit scope。

## Closure

Status Note: 两个 T2 multi-JVM capability-gap 缺陷均已 root-cause 修复并经 T2 lane 端到端证明。Defect 1（log-label mismatch）是纯 test-harness 缺陷，已由 `COORDINATOR_LABEL` 常量化修复。Defect 2（HA-fencing takeover gap）根因为产品代码缺陷 `AbstractPollingLeaderElector.scheduleCheck()` 使用 `TimeUnit.MICROSECONDS` 而非 `MILLISECONDS`（自 `3c4eec214` 初版即存在的 long-standing bug），导致多线程 executor 上并发 `checkElection()` 破坏 leader election 状态机；修复为 1-word 时间单位修正。次要根因为 test harness startup-race（两个 coordinator JVM 近乎同时启动竞争 INSERT），已通过先确认 coordinator-0 leadership 再 spawn standby 修复。新增 2 个针对性回归测试守护时间单位回归与多线程 takeover 语义。evidence 重分类与 readiness 重决策为 successor audit 工作。
Completed: 2026-08-09

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (fresh session `ses_01a5ee90cffeo3m9d1Yv6fP07X`, task type `general`)
- Audit Session: `ses_01a5ee90cffeo3m9d1Yv6fP07X`
- Evidence:
  - **Check 1 (Phase 1 label fix)**: PASS — `TestMultiJvmExactlyOnceRecovery.java:87` declares `COORDINATOR_LABEL = "coordinator-0"`; all 5 `logFileFor(...)` calls (lines 115/123/154/158/241) route through it; 0 bare `"coordinator"` matches remain.
  - **Check 2 (MiniStreamCluster keying)**: PASS — `MiniStreamCluster.java:228-230` `coordinatorKey(int)` returns `"coordinator-"+index`; `:404` `label = "coordinator-"+index`.
  - **Check 3 (MICROSECONDS→MILLISECONDS)**: PASS — `AbstractPollingLeaderElector.java:28` uses `TimeUnit.MILLISECONDS`; no `MICROSECONDS` in file.
  - **Check 4 (failover test ordering)**: PASS — both `testCoordinatorKillTriggersStandbyTakeover` (`:62-66`) and `testBrainSplitFencingBoundary` (`:117-120`) call `waitForLeaderAndAssignments(cluster, 0)` + assert epoch > 0 BEFORE `spawnJobCoordinator(1)`.
  - **Check 5 (regression tests exist)**: PASS — `TestJdbcLeaderElector.java` has exactly 5 test methods including `testPollingCadenceIsMillisecondsNotMicroseconds` (`:194`) and `testMultiThreadedExecutorTakeoverGuardsConcurrentCheckElection` (`:240`).
  - **Check 6 (Anti-Hollow — real fix)**: PASS — `scheduleCheck()` is a real one-word unit change, not empty/stub/continue.
  - **Check 7 (Anti-Hollow — real assertions)**: PASS — both failover tests retain hard assertions (epoch strict increase, leader_id flip); no weakened/removed assertions.
  - **Check 8 (no deferred in-scope defects)**: PASS — "Deferred But Adjudicated" section is empty placeholder only.
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit code: **0** (all 33 items checked).
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-runtime --severity high` exit code: **0** (0 findings).
  - Anti-Hollow 检查结果: multi-JVM 测试真实 spawn 跨 JVM 进程（`MiniStreamCluster.spawnJobCoordinator` → `ProcessBuilder.start()`），经共享 H2 DB + JDBC lease 表连通；修复在运行时生效（coordinator 日志从 150MB+ 降至 655KB-1.3MB，takeover 从 >71s 降至 <8s）。
  - Deferred 项分类检查: 无 in-scope defect 被降级；Non-Blocking Follow-ups 仅含 successor audit 工作（evidence 重分类）与 out-of-scope 项（§2a lane provisioning）。
- Overall verdict: **CLOSURE APPROVED**

Follow-up:

- evidence 重分类（EVID-S13-015/016、EVID-S14-013/014 从 `blocked` → `e2e-proved`）+ Stage 23 readiness gate 重跑 / blanket-ready 重决策：successor audit 工作（见 Non-Blocking Follow-ups）。
- `TestMultiJvmExactlyOnceRecovery` Javadoc 标注的 "Stage 43+ follow-up"（完整 source→keyBy→sink cross-JVM 共享 sink exactly-once 断言）：独立功能增强。
- §2a 的 4 行 lane-blocked（外部 backend provisioning）：out of audit scope。
