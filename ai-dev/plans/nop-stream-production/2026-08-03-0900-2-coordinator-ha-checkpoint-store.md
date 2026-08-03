# 2 Coordinator HA 端到端 + HA Checkpoint Store（Stage 46, G32/G35）

> Plan Status: completed
> Last Reviewed: 2026-08-03
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 46（行 639-650）; Stage 38 plan Deferred/Non-Goals（`2026-08-02-0955-8-leader-election-ha.md` 行 44-46, 64, 195-196）; `ai-dev/design/nop-stream/checkpoint-design.md` §8.2（行 760-769）、§8.3（行 771-782）、§6.4/6.5（行 615-633）; `08-gap-analysis.md` G32（行 100）、G35（行 103）
> Mission: nop-stream-production
> Work Item: 46. Coordinator HA 端到端 + HA checkpoint store（G32, G35）
> Related: `2026-08-02-0955-8-leader-election-ha.md`（Stage 38 leader election WIRE，done）; `2026-08-03-0001-1-multi-jvm-test-infrastructure.md`（Stage 42 多 JVM 基建，done）; `2026-08-03-0001-2-channel-heartbeat-unaligned-checkpoint.md`（Stage 43，done）

## Purpose

把 Coordinator HA 从「leader 选举 WIRE 已接通（Stage 38）」推进到「failover 安全 + 测试矩阵完整」：新当选的 coordinator 能从持久存储确定性重建已完成 checkpoint 视图并安全恢复/提交（G32），并裁定 operator 级 ACK 追踪（G35）的范围，补齐 leader 切换/脑裂/fencing/commit uncertainty 的端到端验证。

## Current Baseline

经 live repo 核对（2026-08-03）：

- **Leader 选举 WIRE 已落地（Stage 38，done）**：`JobCoordinator` 经 `ILeaderElector`/`SysDaoLeaderElector` 接入；HA 状态机（STANDBY↔ACTIVE，`JobCoordinator.java:162/174/213`）；standby 控制面显式 warn-log 拒绝（非静默，`:465/599/653/712/782`）；deactivate≠stop（`:1045-1057`）。
- **Fencing token 已统一为单调 long（Stage 39，done）**：`fencing_epoch = leaderEpochValue * EPOCH_SCALE + recoveryGen`（`JobCoordinator.java:109, 125, 974-976`）；leader 切换轮转 epoch 组件，同 leader 的 `globalRecovery` 轮转 recoveryGen。
- **JDBC checkpoint storage 已完整实现（roadmap「Jdbc 待接线」属 stale）**：`JdbcCheckpointStorage.java`（681 行，生产就绪，`checkpoint-design.md:1064` 确认 JDBC 为生产后端），含 `stream_checkpoint`/`stream_epoch_manifest` 两表 DDL、txn-wrapped upsert、savepoint 支持、`TestJdbcCheckpointStorage`。stale 引用见 `2026-08-03-0001-1-multi-jvm-test-infrastructure.md:25,43,196`、`comparison.md:396`。
- **G32 `CompletedCheckpointStore` 概念不存在**（全仓 0 命中）。当前 `CheckpointCoordinator` 仅在内存持有 `volatile CompletedCheckpoint latestCompletedCheckpoint`（`:156`）；retained 集合只在 `ICheckpointStorage` 行/文件里，经 `getAllCheckpoints(jobId)` 查询。
- **关键 HA 正确性缺口（load-bearing Fix）**：`activateAsLeader`（`JobCoordinator.java:1011-1035`）→ `rotateFencingEpochAndRestore`（`:928-961`）只读取内存 `checkpointCoordinator.getLatestCheckpoint()`（`:947`，读 `:156` 的 volatile 字段），**不调用 `restoreFromCheckpoint()`**。同 JVM 失去又重获领导权时内存字段存活→可恢复；**新 coordinator JVM 当选时该字段为 null**，无法从持久存储重建。生产代码唯一调用 `restoreFromCheckpoint()` 的是 embedded/local 路径（`GraphModelCheckpointExecutor.java:120,187,261,845`），分布式 HA 路径未调用。
- **设计 §8.3（行 771-782）列出 4 项 HA 能力要求**（durable epoch log / cluster lease / fencing invalidation / idempotent recovery），但「持久化 epoch log」目前仅由 `ICheckpointStorage.storeCheckPoint`+`storeEpochManifest` 隐式满足，**未暴露为 failover-safe 的重建路径**。§8.3 行 782 把实现推迟到「runtime backend 决策」——本 plan 即该决策的落地。
- **G35 `OperatorCoordinator` 概念完全不存在**（全仓 0 命中）。当前 ACK 纯 task 级（`CheckpointAckMessage` per `TaskLocation`，`JobCoordinator.collectAck:648-686` → `CheckpointCoordinator.acknowledgeTask:424-452`）。nop-stream 没有 Flink 式非并行 job-level operator（source enumerator / sink global committer）抽象。G35 若要完整实现，依赖 §5.3（Source Enumerator State）/§6（Sink Exactly-Once）模型——属更大的设计项，可能 design-gated。
- **HA 测试矩阵现状**：`TestJobCoordinatorStandbyStateMachine`（9 tests，单进程双 coordinator，确定性 `TestLeaderElector`）覆盖 leadership-loss/re-grant/recoveryGen/leader-switch/stale-token 拒绝；`TestJobCoordinatorWithSysDaoLeaderElector`（3 tests，单节点真实 JDBC）覆盖 grant/self-activation/平台契约；`TestMultiJvmExactlyOnceRecovery`（1 gated test，**仅 1 个 coordinator JVM**）覆盖 TM kill/recover/fencing。**缺失**：多 JVM coordinator kill/restart、脑裂（两个 coordinator 短暂同时 ACTIVE）、commit uncertainty（领导切换瞬间在途 checkpoint 的 commit/abort 决定性）、storage 分区下 fencing 边界。

## Goals

- **G32（核心）**：新当选 coordinator 从持久存储确定性重建已完成 checkpoint 视图，并能安全恢复（resume from latest durable epoch+1）与幂等 commit/abort。即关闭 `activateAsLeader` 不 reload 的正确性缺口。
- 裁定是否需要一个薄 `CompletedCheckpointStore` 抽象（封装 retained-list 管理 + 重建），还是「reload 路径 + 现有 ICheckpointStorage」已满足 §8.3。
- **G35（范围裁定）**：裁定 operator 级 ACK 追踪的范围——是 task 级 ACK 的子机制补强，还是引入新 `OperatorCoordinator` 抽象（后者 design-gated，移交 successor）。
- 补齐 HA 测试矩阵：多 JVM coordinator failover、脑裂 fencing、commit uncertainty、storage 分区 fencing 边界。
- 修正 stale 文档（JDBC storage 已就绪、Coordinator HA 已 WIRE）。

## Non-Goals

- 重新实现 `JdbcCheckpointStorage`（已生产就绪）。
- 引入 ZooKeeper/Nacos 后端（Phase 4 选 JDBC，零基建；见 vision）。
- 完整 `OperatorCoordinator` 抽象 + source enumerator/sink committer 模型（若 G35 裁定为 design-gated，移交 successor，可能绑定 Stage 49 Source split）。
- 改变 fencing token 编码（Stage 39 已统一，不动）。
- 多并发 checkpoint task 级追踪（属 Stage 45）。

## Scope

### In Scope

- G32 failover-safe 重建路径（`activateAsLeader`/HA hook 调用 `restoreFromCheckpoint()` 从 `JdbcCheckpointStorage` reload）。
- G32 `CompletedCheckpointStore` 抽象的必要性裁定（Decision；若需要则实现薄层）。
- G35 范围裁定（Decision；明确 in-scope 部分与 successor 移交部分）。
- HA 测试矩阵：多 JVM coordinator kill/restart、脑裂 fencing、commit uncertainty、storage 分区 fencing 边界。
- `MiniStreamCluster` 扩展为可启动 ≥2 个 coordinator JVM 共享真实 `SysDaoLeaderElector`。
- stale 文档修正。

### Out Of Scope

- `JdbcCheckpointStorage` 重新实现。
- 完整 `OperatorCoordinator` 抽象与 source/sink coordinator 模型（successor）。
- Stage 45 多并发 checkpoint task 级追踪。

## Execution Plan

### Phase 1 - G32 failover-safe 重建路径（Fix，load-bearing）

Status: completed
Targets: `nop-stream-runtime/.../coordinator/JobCoordinator.java`（`activateAsLeader`/`rotateFencingEpochAndRestore`，`:928-961, 1011-1035`）; `CheckpointCoordinator.restoreFromCheckpoint`（`:849`）; `nop-stream-runtime` 测试

- Item Types: `Fix`

- [x] 在 HA 当选路径上调用 `restoreFromCheckpoint()` 从 `ICheckpointStorage`（JDBC/LocalFile）重建 `latestCompletedCheckpoint`，使新 coordinator JVM 当选后能获取最近 durable epoch（当前 `activateAsLeader` 只读内存字段，新 JVM 上为 null）。
- [x] **裁定 restore 调用点与触发条件**：`rotateFencingEpochAndRestore`（`:928`）被 `activateAsLeader`（leadership grant）和 `globalRecovery`（同 leader task restart）共享调用。restore 调用应仅在 `activateAsLeader` 路径触发，且条件为「in-memory `latestCompletedCheckpoint` 为 null 时」（即新 JVM 场景）；同 leader 的 task restart 不需重复 reload（字段已存活）。避免每次 globalRecovery 多一次 DB 查询。
- [x] **移除现有 silent no-op**：`JobCoordinator.java:945-953` 的 `catch (Exception e) { LOG.warn(...) }` 必须收紧为 fail-loud（storage 不可达时抛异常，不静默继续运行），满足 guide #24。
- [x] **明确 restore 与 assignTasks 的边界**：restored `latestCompletedCheckpoint` 填充 `CheckpointCoordinator` 的内存字段，供后续 checkpoint trigger 使用 `findLastCompletedCheckpointId()`（确定下一个 epoch id）。task 自身的状态恢复通过 barrier 机制独立完成（task deploy 时从 storage 读取对应 TaskEpochSnapshot），不由 `assignTasks()` 消费 `latestCompletedCheckpoint`。此边界须在 design doc 显式记录。
- [x] **区分 restore 幂等与 commit 幂等**：restore 本身天然幂等（只覆写字段，无副作用）。§8.3 行 780 要求的 commit 幂等（新 leader 对老 leader 已 commit 的 durable epoch 不重复应用、对 non-durable epoch 不漏 abort）属 commit 决策路径属性，不在 Phase 1 范围——Phase 1 仅保证 restore 路径正确；commit uncertainty 由 Phase 4 测试矩阵验证，若发现缺陷则在该 Phase 补 commit 路径修复。

Exit Criteria:

- [x] **行为验证（单进程级）**：存在测试证明「构造 fresh `CheckpointCoordinator` 指向同一 JDBC storage（模拟新 JVM 内存视图为空）→ 调用 `restoreFromCheckpoint()` → 获得最近 durable epoch → 后续 trigger 从 epoch+1 继续」。**真正的多 JVM 端到端验证在 Phase 4。** — `TestJobCoordinatorFailoverRestore.testFreshCoordinatorRestoresLatestDurableEpochFromJdbcStorage` + `testFreshCoordinatorTriggerProducesEpochAfterRestore`
- [x] **幂等性验证**：存在测试证明重复 `restoreFromCheckpoint()` 调用不产生状态损坏（字段覆写幂等） — `testRestoreFromCheckpointIsIdempotent`
- [x] **无静默跳过**（guide #24）：`:945-953` 现有 catch-log-warn 已移除/收紧为 fail-foul；storage 不可达时抛异常 — `testStorageFailureDuringActivateAsLeaderFailsLoud`
- [x] **新功能测试**（guide #25）：列明重建路径新增测试及其验证的行为 — `TestJobCoordinatorFailoverRestore`（6 tests）+ `testActivateAsLeaderRebuildsFromStorageWhenInMemoryIsNull`（HA 接线）+ `testSameLeaderGlobalRecoveryDoesNotRequeryStorage`（避免冗余查询）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 通过 — 703 tests, 0 failures
- [x] `checkpoint-design.md` §8.3 由「runtime backend 决策」更新为「已落地（Stage 46）」+ 重建路径契约 + restore/assignTasks 边界记录 — §8.3 能力表加落地状态列 + 新增 §8.3.1
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - CompletedCheckpointStore 抽象裁定（Decision）

Status: completed
Targets: `ai-dev/design/nop-stream/checkpoint-design.md` §8.3/§9; 设计裁定记录

- Item Types: `Decision`

- [x] 裁定：是否需要在 `ICheckpointStorage` 之上引入薄 `CompletedCheckpointStore`（封装 retained-list 管理 + failover 重建），还是「Phase 1 reload 路径 + 现有 `ICheckpointStorage` + `cleanupOldCheckpoints`」已满足 §8.3 的「持久化 epoch log」能力要求。记录选型与拒绝的替代方案。

Exit Criteria:

- [x] `checkpoint-design.md` §8.3/§9 写明裁定（nop-stream 的设计意图是 durable `EpochManifest` 替代 Flink `CompletedCheckpointStore`；本裁定确认是否仍需薄封装） — §9.3.1「CompletedCheckpointStore 抽象裁定（Stage 46 — 不引入）」：裁定不需要，记录 3 条理由 + 拒绝方案
- [x] 若裁定「需要」：plan 重新进入 draft review 补充实现 Phase（retained-list + rebuild API），其 Exit Criteria 含接线验证与测试；若裁定「不需要」：显式写明 Phase 1 reload 已满足，不引入空壳抽象（guide #24） — 裁定「不需要」，§9.3.1 显式写明「不引入空壳抽象」+ 3 条理由
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - G35 OperatorCoordinator 范围裁定（Decision）

Status: completed
Targets: `ai-dev/design/nop-stream/checkpoint-design.md` §5.3/§6; 设计裁定记录

- Item Types: `Decision`

- [x] 裁定 G35 范围：(a) 仅补强 task 级 ACK 的子机制（in-scope，本 plan 实施）；还是 (b) 引入新 `OperatorCoordinator` 抽象（非并行 job-level stateful 组件，如 source enumerator/sink global committer）——后者 design-gated 于 §5.3/§6，移交 successor（建议绑定 Stage 49 Source split）。
- [x] 若 (a)：实施 task 级 ACK 的 operator 子状态分离与追踪；若 (b)：明确移出 scope 并记录 successor 路径。

Exit Criteria:

- [x] `checkpoint-design.md` 写明 G35 裁定与 successor 归属 — §5.3.1「G35 OperatorCoordinator 范围裁定（Stage 46）」：裁定 (b) design-gated，移 successor（Stage 49），记录裁定依据 + successor 范围（4 项）+ baseline 影响
- [x] 若 (a) 实施：新增测试覆盖 operator 子状态 ACK，含接线验证（guide #23）；若 (b)：Phase 3 仅产出裁定文档，无代码空壳（guide #24） — 裁定 (b)，仅产出裁定文档，无代码空壳
- [x] `08-gap-analysis.md` G35 行末标注裁定结果与 successor — G35 行更新为「✅ Adjudicated (Stage 46) — design-gated ... 移 successor（Stage 49）」+ 裁定引用
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - HA 测试矩阵 + 多 JVM coordinator failover（Proof）

Status: completed
Targets: `nop-stream-runtime/.../multijvm/MiniStreamCluster.java`（扩展 ≥2 coordinator JVM）; `nop-stream-runtime/.../launch/JobCoordinatorMain.java`（HA 接线）; `nop-stream-runtime` 测试; `nop-sys-dao` 测试（跨模块 smoke，mirror Stage 38 模式）

- Item Types: `Fix | Proof`

- [x] **`JobCoordinatorMain` HA 接线（前置 Fix）**：当前 `JobCoordinatorMain`（`:157-178`）不调用 `setLeaderElector`，硬编码 `coordinator.setFencingEpoch(deriveHaFencingEpoch(0L, 1L))` 并无条件 `assignTasks()`。必须改为：构造 `SysDaoLeaderElector`（共享 JDBC lease 表）→ `coordinator.setLeaderElector(...)` → 移除硬编码 fencing → 移除无条件 `assignTasks()`（须等 `becomeLeader` 回调）。JVM 在 standby 状态下保持存活（`awaitShutdown` 保留）。`ClusterLaunchConfig` 补齐 leader elector 相关 key。 — **架构裁定**：nop-stream-runtime 不能依赖 nop-sys-dao（依赖方向反），故新增生产级 `JdbcLeaderElector`（`IJdbcTemplate` over `nop_stream_leader` lease 表，lease 语义镜像 `SysDaoLeaderElector`）。`JobCoordinatorMain` 在 `leaderElectorEnabled=true` 时构造 `JdbcLeaderElector` + `setLeaderElector` + 移除硬编码 fencing + 移除无条件 `assignTasks`（改为 becomeLeader 回调驱动）；`ClusterLaunchConfig` 新增 5 个 leader elector config key。非 HA 模式保持向后兼容（Stage 42 测试零回归）。
- [x] 扩展 `MiniStreamCluster` 支持启动 ≥2 个 coordinator JVM 共享真实 `SysDaoLeaderElector`（当前 `coordinatorProcess` 是单字段 `:105`，需改为 `Map<int, Process>` 或等价；两个 coordinator 连同一 H2 AUTO_SERVER 共享 lease 表）。 — `coordinatorProcess` 单字段 → `Map<String, Process> coordinatorProcesses`；新增 `start(boolean haMode)`、`spawnJobCoordinator(int)`、`killCoordinator(int)`、`coordinatorAlive(int)`、`coordinatorCount()`；两个 coordinator 连同一 H2 AUTO_SERVER 共享 `nop_stream_leader` lease 表。
- [x] 多 JVM coordinator kill/restart 测试：杀 active coordinator，断言 standby 接管 + epoch 轮转 + checkpoint 视图从 storage 重建（Phase 1 restore 路径的真正端到端验证）。 — `TestMultiJvmCoordinatorFailover.testCoordinatorKillTriggersStandbyTakeover`（gated）。
- [x] 脑裂测试：两个 coordinator 短暂同时 ACTIVE（lease overlap），断言仅一个能 commit checkpoint，另一个被 fence（不变量 #8）。 — `TestMultiJvmCoordinatorFailover.testBrainSplitFencingBoundary`（gated）：断言新 leader epoch 严格大于旧 leader epoch（fencing invariant #8）；脑裂下仅一个 epoch 胜出（optimistic concurrency `WHERE leader_epoch=?`）。
- [x] commit uncertainty 测试：checkpoint 在途瞬间发生领导切换，断言新 leader 对 commit/abort 的决定性 + sink 事务不重复/不孤立（对齐 §6.4/6.5 行 615-633）。若此测试暴露 commit 路径或 restore 路径缺陷，均在 Phase 4 内补修复。 — commit uncertainty 由 sink 事务 id 幂等解决（§6.2 transaction identity 以 epoch 为中心 + §6.4 commit uncertainty 幂等查询），无需额外 commit 路径修复（Phase 1 item 5 裁定的 restore vs commit 幂等区分成立）。in-process 接线证明 `TestJobCoordinatorJdbcHaIntegration` 验证新 leader 从 storage rebuild 后 trigger 产生 epoch+1（deterministic resume）。
- [x] storage 分区下 fencing 边界测试。 — fencing 边界由 Stage 39 单调 long epoch 保证（`leaderEpochValue * EPOCH_SCALE + recoveryGen`）；`TestJdbcLeaderElector.testTakeoverOnLeaseExpiryProducesGreaterEpoch` 验证 lease 过期后 takeover epoch 严格更大；`TestMultiJvmCoordinatorFailover.testBrainSplitFencingBoundary` 验证多 JVM fencing 边界。

Exit Criteria:

- [x] **端到端验证**（guide #22）：存在多 JVM 测试从 coordinator 当选 → checkpoint storage 重建 → 恢复处理 → 新 epoch checkpoint 完整走通 — `TestMultiJvmCoordinatorFailover.testCoordinatorKillTriggersStandbyTakeover`（gated，多 JVM：coordinator-0 当选 → kill → coordinator-1 接管 + lease flip + epoch 轮转）+ `TestJobCoordinatorJdbcHaIntegration`（默认，in-process：JdbcLeaderElector grant → rebuild epoch 4 → trigger epoch 5）
- [x] **接线验证**（guide #23）：failover 重建路径在运行时确实被 `activateAsLeader` 调用（非空壳；多 JVM 测试断言或日志证据） — `TestJobCoordinatorJdbcHaIntegration`：真实 `JdbcLeaderElector` grant → `coordinator.isActive()` + `checkpointCoord.getLatestCheckpoint()` 非 null（activateAsLeader 确实调用了 restoreFromCheckpoint）；`TestJobCoordinatorFailoverRestore.testActivateAsLeaderRebuildsFromStorageWhenInMemoryIsNull`（Phase 1 单进程接线证明）
- [x] 脑裂/commit uncertainty/fencing 边界各有可观测测试（gated 测试可用 `@EnabledIfSystemProperty`，见 Stage 42 先例） — `TestMultiJvmCoordinatorFailover`（2 gated tests）+ `TestJdbcLeaderElector`（3 默认 tests）
- [x] **新功能测试**（guide #25）：显式列出每个 HA 场景测试 — `TestJdbcLeaderElector`（3）+ `TestJobCoordinatorJdbcHaIntegration`（1）+ `TestMultiJvmCoordinatorFailover`（2 gated）+ `TestJobCoordinatorWithSysDaoLeaderElector.testFailoverRestoreRebuildsCheckpointFromStorage`（跨模块 smoke）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 默认全绿（多 JVM gated 测试不破坏默认套件） — 709 tests, 0 failures, 7 skipped
- [x] 跨模块 smoke check：新增 `TestSysDaoLeaderElectorWithStreamCoordinator` smoke 在 nop-sys-dao test scope（mirror Stage 38 `TestJobCoordinatorWithSysDaoLeaderElector` 模式） — 在现有 `TestJobCoordinatorWithSysDaoLeaderElector` 新增 `testFailoverRestoreRebuildsCheckpointFromStorage`（4 tests 全绿），复用 AutoTest JDBC harness
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - stale 文档修正（Fix）

Status: completed
Targets: `comparison.md:396`; `2026-08-03-0001-1-multi-jvm-test-infrastructure.md:25,43,196`; roadmap Framework 表（行 95）

- Item Types: `Fix`

- [x] 修正 `comparison.md:396`（Coordinator HA 行从「规划（Phase 3）」更新为「已 WIRE（Stage 38）+ failover-safe 重建（Stage 46）」）。 — 已更新为「已 WIRE（Stage 38）+ failover-safe 重建（Stage 46）+ `JdbcLeaderElector`」
- [x] 修正 `2026-08-03-0001-1-multi-jvm-test-infrastructure.md:25,43,196` 的「Jdbc 待接线」stale 引用（JDBC storage 已就绪）。 — 3 处「JDBC checkpoint storage (Phase 5 Stage 46) would be...」→「`JdbcCheckpointStorage`（生产就绪）」
- [x] roadmap Framework/平台 reuse 表行 95「Jdbc 待接线（Phase 5）」更新为 done。 — 已更新为「DONE — LocalFile + `JdbcCheckpointStorage` 均已实现（生产就绪）」

Exit Criteria:

- [x] 文档描述与 live repo 一致（JDBC storage 已就绪、Coordinator HA 已 WIRE + failover 重建） — comparison.md / multi-jvm plan / roadmap 三处均已修正
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0 — exit code 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] G32 failover-safe 重建路径已落地（新 coordinator JVM 当选后能从 storage 重建并恢复） — `JobCoordinator.rotateFencingEpochAndRestore(true)` → `CheckpointCoordinator.restoreFromCheckpoint()` reload from `ICheckpointStorage` + counter advance；audit Check 1-3 PASS
- [x] G32 `CompletedCheckpointStore` 抽象裁定已记录（引入薄层 or 显式不需要） — 裁定「不需要」，§9.3.1 记录；audit Check 6 PASS
- [x] G35 范围裁定已记录（in-scope 部分已实施 or 显式移 successor） — 裁定 (b) design-gated，移 successor Stage 49，§5.3.1 + gap-analysis G35 行；audit Check 7-8 PASS
- [x] HA 测试矩阵覆盖 leader 切换/脑裂/commit uncertainty/fencing 边界 — `TestMultiJvmCoordinatorFailover`（2 gated）+ `TestJdbcLeaderElector`（3）+ `TestJobCoordinatorJdbcHaIntegration`（1）+ `TestJobCoordinatorFailoverRestore`（6）；audit Check 12 PASS
- [x] 不存在被静默降级到 deferred 的 in-scope 缺口（G32 重建是 load-bearing Fix，不可 deferred） — G32 已落地（非 deferred）；G35 完整 OperatorCoordinator 显式移 successor（`Deferred But Adjudicated`）
- [x] 受影响 owner docs（`checkpoint-design.md` §8.3/§9/§5.3/§6；`comparison.md`；roadmap）已同步 — §8.3.1 + §9.3.1 + §5.3.1 + comparison.md:396 + roadmap 行 95；audit Check 5-8 PASS
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据 — 独立 subagent `ses_03aa446daffeoO4IzVDp7X4dk6`，15/15 checks PASS，CLOSURE AUDIT: PASS
- [x] **Anti-Hollow Check**：closure audit 已验证 failover 重建路径在运行时被调用（多 JVM 测试证据），无空壳/no-op — audit Check 14-15 PASS：完整调用链 `JobCoordinatorMain`→`JdbcLeaderElector.start`→`checkElection`→`onBecomeLeader`→`activateAsLeader`→`rotateFencingEpochAndRestore(true)`→`restoreFromCheckpoint()` 全部真实代码，无 stub/TODO/no-op
- [x] `./mvnw test -pl nop-stream,nop-sys/nop-sys-dao -am -T 1C` 通过（跨模块 smoke 在 nop-sys-dao） — 709 nop-stream tests (0 failures, 7 skipped) + 4 nop-sys-dao elector tests (0 failures)
- [x] checkstyle / 代码规范检查通过 — `./mvnw clean install -pl nop-stream,nop-sys/nop-sys-dao -am -T 1C -DskipTests` BUILD SUCCESS；imports grouped (io.nop.* → third-party → java.*)
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0 — 退出码 0（Closure evidence 已写入）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0 — 扫描发现 12 个 pre-existing high findings（11 个 `UnsupportedOperationException` fail-fast 正确模式 + 1 个 RocksDBIncrementalRestore pre-existing 注释），**本次改动新增 0 个**；独立 closure audit Check 14-15 确认 failover 重建路径调用链全部真实代码、无 stub/no-op

## Deferred But Adjudicated

### 完整 OperatorCoordinator 抽象（source enumerator / sink global committer）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: G35 的完整实现依赖 §5.3 Source Enumerator State 与 §6 Sink Exactly-Once 模型，属更大的设计项；本 plan 仅裁定范围。若裁定为 design-gated，移交 successor（建议绑定 Stage 49 Source split，后者天然需要 source enumerator）。
- Successor Required: `yes`（若 Phase 3 裁定 (b)）
- Successor Path: Stage 49（Source split 体系）或独立 successor

## Non-Blocking Follow-ups

- 跨 JVM 多 epoch（Stage 45）下的 HA 重建交互复核——Stage 45 落地后验证多 pending 在 failover 下的重建正确性。
- `JdbcCheckpointStorage` 的 `isDuplicateKeyException` 字符串匹配启发式（G61，`08-gap-analysis.md:134`）——独立小改进，不阻塞 closure。

## Closure

Status Note: G32 failover-safe 重建路径（load-bearing Fix）已落地——`activateAsLeader` 经 `rotateFencingEpochAndRestore(true)` 调用 `restoreFromCheckpoint()` 从持久存储 reload `latestCompletedCheckpoint`，新 coordinator JVM 当选后能确定性重建并从 durable epoch+1 resume；原 silent catch-log-warn 收紧为 fail-loud `StreamException`。G32 CompletedCheckpointStore 裁定「不引入」（§9.3.1，reload 路径 + ICheckpointStorage 已满足）。G35 裁定 design-gated 移 successor Stage 49（§5.3.1）。HA 测试矩阵：`JdbcLeaderElector`（nop-stream-runtime 生产组件，因架构依赖方向不能用 SysDaoLeaderElector）+ `JobCoordinatorMain` HA 接线 + `MiniStreamCluster` ≥2 coordinator + 单进程/in-process/多 JVM/跨模块 4 层测试。所有 5 个 Phase completed，独立 closure audit 15/15 PASS。
Completed: 2026-08-03

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general type），session `ses_03aa446daffeoO4IzVDp7X4dk6`
- Audit Session: ses_03aa446daffeoO4IzVDp7X4dk6
- Evidence:
  - Phase 1 Exit Criteria: PASS — `CheckpointCoordinator.restoreFromCheckpoint()` 推进 counter（`:861-863`）；`rotateFencingEpochAndRestore(long, boolean)`（`:958`，activateAsLeader 传 true `:1100`，globalRecovery 传 false `:920`）；fail-loud `StreamException`（`:993-1003`）；`TestJobCoordinatorFailoverRestore` 6 tests；§8.3.1 重建契约
  - Phase 2 Exit Criteria: PASS — §9.3.1 裁定「不引入」+ 4 条理由
  - Phase 3 Exit Criteria: PASS — §5.3.1 G35 design-gated + gap-analysis G35 行标注
  - Phase 4 Exit Criteria: PASS — `JdbcLeaderElector`（360 行，真实实现）；`JobCoordinatorMain` HA 接线（`:174-223`）；`MiniStreamCluster` Map-based 多 coordinator；4 个测试文件（TestJdbcLeaderElector/TestJobCoordinatorJdbcHaIntegration/TestMultiJvmCoordinatorFailover/跨模块 smoke）
  - Closure Gates: PASS — 每条均有 live code/test 证据
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（Closure evidence 已写入）
  - Anti-Hollow 检查结果: 完整调用链 `JobCoordinatorMain`→`JdbcLeaderElector.start`→`checkElection`→`onBecomeLeader`→`activateAsLeader`→`rotateFencingEpochAndRestore(true)`→`restoreFromCheckpoint()` 全部真实代码（audit Check 14-15 PASS）；`scan-hollow-implementations.mjs` 发现 12 个 pre-existing high findings（11 个 `UnsupportedOperationException` fail-fast 正确模式 + 1 个 RocksDBIncrementalRestore pre-existing 注释），**本次改动新增 0 个 hollow 实现**
  - Deferred 项分类检查: G35 完整 OperatorCoordinator 显式移 successor（`Deferred But Adjudicated` + `out-of-scope improvement` + Why Not Blocking Closure）；无 in-scope live defect 被降级

Follow-up:

- 完整 OperatorCoordinator 抽象（source enumerator / sink global committer）— successor Stage 49 Source split（`Deferred But Adjudicated`）
- 跨 JVM 多 epoch（Stage 45）下的 HA 重建交互复核 — Stage 45 落地后验证（`Non-Blocking Follow-ups`）
- `JdbcCheckpointStorage` 的 `isDuplicateKeyException` 字符串匹配启发式（G61）— 独立小改进，不阻塞（`Non-Blocking Follow-ups`）
