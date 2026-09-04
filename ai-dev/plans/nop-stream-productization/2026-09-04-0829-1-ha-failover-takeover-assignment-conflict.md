# 1 HA failover 新 leader 接管中止修复（task_assignment 唯一键冲突 → attempt 计数从注册表历史种子化）

> Plan Status: completed
> Mission: nop-stream-productization
> Work Item: item 34
> Last Reviewed: 2026-09-04
> Draft Review: 两轮独立 subagent 对抗性审查达成共识——第一轮（`ses_f962cd7a7ffemHvOAmPs70kB13`）1 Blocker（异常吞点实为 `AbstractLeaderElector.onBecomeLeader` 平台吞掉、无 standby 降级/重选举——机制叙事与 Phase 1 #24 裁定项已据此重写）+ 1 Major（Phase 2 缺陷窗口前置轮询与断言轮询语义、fencing_token 口径——已补）+ 5 Minor 全部吸收；第二轮（`ses_f96228ed6ffebe5EwXryX8DeSk`）0 Blocker / 0 Major，5 Minor（inventory diff 口径、per-subtask 比较口径、选择性 stash、Goals 措辞、行号）全部吸收后达成 consensus
> Source: roadmap `ai-dev/backlog/nop-stream-productization-roadmap.md` item 34（来源 items 28+31 plan `2026-09-03-1951-3-remote-deploy-dataplane-stability.md` Phase 4 CHAOS-2 复验发现并确认 pre-existing，2026-09-04，runId `1788457688118-1` 与 item 15 演练产物 `1788385016277-1` 同签名；缺陷机制与复现命令记录 = `ai-dev/design/nop-stream/distributed-runbook.md` §7 第 153 行）
> Related: `ai-dev/plans/nop-stream-productization/2026-09-03-1951-2-control-plane-fencing-cancel-task.md`（控制面 fencing 族前序修复）、`ai-dev/plans/nop-stream-productization/2026-09-01-2217-3-composite-scenario-distributed-verification.md`（C1/S2 租约到期路径 gated 断言面 = 本 plan Phase 2 补全对象）

## Purpose

使 HA failover 的新 leader 接管真正完成：旧 leader 的 task_assignment 行存留于共享注册表时，新 coordinator JVM 的 leadership 激活不再因 attempt_number 唯一键冲突中止，重部署与 checkpoint 推进在接管后继续，CHAOS-2 演练格达到终态收敛。

## Current Baseline

（live 核对于 2026-09-04，行号为当日基线；引运行时模块根 `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/`）

**缺陷机制（runbook §7:153 已留档，代码侧逐点核实）**：

- attempt 编号唯一真值是 coordinator 进程内 `JobCoordinator.attemptCounters`（`coordinator/JobCoordinator.java:194`，`ConcurrentHashMap<String,Integer>`，key = `vertexId/subtaskIndex`）。`prepareAssignmentsLocked`（:754）对每个 subtask 取 `attemptCounters + 1` 作 attemptNumber（:799-800），随后调 `clusterRegistry.assignTask(...)`（:807-809）落库。
- `JdbcClusterRegistry.assignTask`（`cluster/JdbcClusterRegistry.java:202-229`）对 `nop_stream_task_assignment` 执行纯 INSERT；表 PK = `(job_id, vertex_id, subtask_index, attempt_number)`（建表 DDL :400-410，G56 要求 attempt 历史 append-only，接口 javadoc `cluster/ClusterRegistry.java:71-95`）。
- HA 接管路径 `JobCoordinator.activateAsLeader`（:1860-1900）：先置 `active=true`（:1880）→ 置 `recoveryGen=0` → `rotateFencingEpochCoreLocked(token, true)`（G32 从存储重建 checkpoint 视图；清 `taskAssignmentMap`/`allTaskLocations` 但**不清也不种子化 `attemptCounters`**）→ `prepareAssignmentsLocked()`。新 coordinator JVM 的 `attemptCounters` 为空 → attemptNumber 从 1 重发 → 与旧 leader 存留行（attempt_number=1）PK 冲突 → `nop.err.dao.sql.duplicate-key`。
- **异常吞点与终态（审查轮核实，勿沿用 runbook 外推）**：该异常经 `becomeLeader` listener 传播到平台基类 `AbstractLeaderElector.onBecomeLeader`（`nop-cluster/nop-cluster-core/.../elector/AbstractLeaderElector.java:182-190`）被**逐 listener try/catch 吞掉**（仅 `LOG.error("nop.cluster.invoke-become-leader-listener-error")`）——**无 standby 降级、无重选举、无重试**。`JdbcLeaderElector.java:108-112` 的 catch 只接 elector 自身 SQL 异常，不在本失败路径上。又因 `active=true` 在失败点之前已置位且无复位路径，终态 = **冻结的半激活 leader**：持有租约、epoch 已轮转、checkpoint 视图已恢复、active=true，但无 assignment、无重部署、无后续 checkpoint，输出冻结（进程存活）。演练产物 `1788457688118-1/logs/coordinator-2.log` 实证全链（became LEADER epoch=2 → duplicate-key → invoke-become-leader-listener-error → elector 按 leader 身份继续刷租约；leaseEpoch 每 kill 恰 +1、无失败重试循环的连续 bump，与该机制吻合）。
- 演练证据：CHAOS-2 复验 `1788457688118-1`（leaseEpoch 1→2→3 严格递增 PASS + 终态不收敛）与 item 15 `1788385016277-1` 同签名；item 15 报告原归因「jam 抑制重部署触发」已在 runbook §7 修正为本缺陷。

**为什么既有 gated 断言面没有抓住（断言缺口）**：

- `TestMultiJvmCoordinatorFailover.testCoordinatorKillTriggersStandbyTakeover`（`multijvm/TestMultiJvmCoordinatorFailover.java:53-99`）只断言：coordinator-1 存活 + 租约行 leader 翻转 + epoch 严格递增——这三者都在 listener 异常**之前**或与之无关节立，因此缺陷存在时测试仍绿（item 27 记录 gated 13/13）。`testBrainSplitFencingBoundary`（:107-143）同形态。测试**不**断言新 leader 的 assignment 行、不断言接管后推进。辅助方法 `waitForLeaderAndAssignments`（:258-273）名不副实——只轮询租约行，不查 assignment 表（kill 时刻若早于 coordinator-0 的 assignment 落库，表可能为空——Phase 2 前置轮询须避开该窗口）。
- `MiniStreamCluster` 每次启动 drop 全部 stream 表（`multijvm/MiniStreamCluster.java:463-473` `dropStreamTablesQuietly`，经 `start()` :240 调用）——只有「运行中旧 leader 行已落库 + 新鲜 coordinator JVM 接管」这一窗口触发冲突；C0-C3 场景与 TM kill/恢复路径不经过该窗口。
- 单 JVM HA 拓扑（`TestJobCoordinatorFailoverRestore`/`TestJobCoordinatorJdbcHaIntegration`）中共识 registry 为 InMemory 或同 JVM JDBC 且 `attemptCounters` 常驻（同 JVM leader 切换计数不清零），不触发冲突；`InMemoryClusterRegistry.assignTask`（`cluster/InMemoryClusterRegistry.java:142-168`）对 attempt 回退仅 WARN 后 append（"caller bug"），单 JVM 形态静默容忍。注意口径：lease 表 `leader_epoch` 为裸整数，assignment 表 `fencing_token` = `leaderEpoch × EPOCH_SCALE(1_000_000) + recoveryGen`（`JobCoordinator.java:115`）——跨两表断言须用 `fencing_token` 列换算。

**修复原料（已存在，无需新建抽象）**：

- `ClusterRegistry.getAttemptHistory(jobId, vertexId, subtaskIndex)`（接口 :118；JDBC 实现 `JdbcClusterRegistry.java:250-271` 按 attempt_number ASC 返回全历史；InMemory 实现 :180-187）——种子化读源现成。
- `prepareAssignmentsLocked` 遍历的 `(vertexId, subtaskIndex)` 枚举源 = `deploymentPlan.getPartitionedPlan().getVertexPlans()`（:772-776），种子化可复用同一枚举。
- 该方法内 `clusterRegistry.assignTask` 本就是 recoveryLock 下的逐 subtask 存储 I/O（既有结构先例），种子化读不引入新类别的锁内开销。

**测试与门禁现状**：

- 无任何测试覆盖「注册表已有同 jobId attempt 行 + 新鲜 coordinator JVM 激活」组合（Phase 1 focused 测试填补）。
- `check-nop-stream-invariants.mjs` 无 attemptCounters/assignTask 相关 pin，注册表（wiring-registry/mjs-pins/gate-inventory/output-contract 四 JSON）中也**无任何 JobCoordinator pin**（rg 零命中）——本改动预期不触发 re-pin；若执行中出现意外失配，按既有 re-pin 流程同步（item 26 先例 `:751→:712` 系 `GraphModelCheckpointExecutor` 的 pin，非 JobCoordinator）。
- CHAOS-2 演练入口 = `TestStabilityExerciseMultiJvm.chaosJcHaFailover`（`nop-stream-fraud-example/.../scenario/TestStabilityExerciseMultiJvm.java:369-447`，gated + `preserve-artifacts` 强制），当前按 runbook §7 判定 = fail（终态不收敛）——修复后的端到端复验载体。

**设计契约（owner doc 现状）**：

- `ai-dev/design/nop-stream/01-architecture-baseline.md` 已裁定 ClusterRegistry = runtime source of truth（task assignment + attempt history（n），G56 append-only）；JobCoordinator 职责行含「per-subtask attempt 编号（n）」。**跨 leader 接管的 attempt 连续性语义未落档**——本 plan Phase 1 补裁定记录。
- `ai-dev/design/nop-stream/failover-design.md` 是 targeted failover 可行性裁定（no-go 结论），与本 plan 无重叠。

**真正剩余的 gap**：`activateAsLeader` 路径 attempt 编号从内存空计数器重启，与注册表持久化历史脱节；断言面无接管完成性检查；CHAOS-2 终态不收敛。

## Goals

- 新 leader 接管完成性：共享注册表存有旧 leader 的 `(jobId, vertex, subtask, attempt_number)` 行时，新鲜 coordinator JVM 的 `activateAsLeader` 重发 assignment 使用**严格大于持久化历史最大值**的 attempt_number，INSERT 成功、无 duplicate-key、listener 不中止，重部署与 checkpoint 推进继续。
- G56 不变量保持：attempt 历史 append-only（不覆盖、不删除旧行）；per-subtask attemptNumber 跨 leader 接管单调递增；upsert/删旧插新等破坏历史语义的方案显式拒绝并落档。
- 无回归：fresh-job 首次分配（注册表无行）attempt 仍从 1 起；同 JVM leader 切换 / globalRecovery / TM kill 恢复路径行为不变；新鲜 coordinator 实例接管**共享 InMemory registry** 时不触发「Attempt number regression」caller-bug WARN（既有同 JVM 切换本就不触发——该目标针对 Phase 1 新造的聚焦场景形态）。
- 断言面补全：多 JVM 接管测试从「租约翻转」升级为「接管完成」（新 leader 的 assignment 行存在且 attempt_number 接续旧最大值 + coordinator 日志无 duplicate-key 中止签名）。
- 端到端证明：CHAOS-2（`chaosJcHaFailover`）gated 复验通过——租约/epoch 翻转子判据 + 终态收敛（期望集完整、checkpoint 恢复推进、输出不冻结），产物留档 `_tmp/mini-stream-cluster/`。

## Non-Goals

- 不做 targeted/region failover（`failover-design.md` no-go 裁定不变）。
- 不改 `nop_stream_task_assignment` 表结构/PK/DDL，不做跨版本存储迁移。
- 不改平台 elector 的 listener 异常处置（`AbstractLeaderElector.onBecomeLeader` 逐 listener 吞异常是 nop-cluster 平台行为，本 plan 不动）。**诚实披露**：这意味着任何激活路径异常的终态都是「冻结半激活 leader」（无重试、无降级）——本 plan 消除已知异常源（duplicate-key）并把种子化读失败行为显式裁定落档（Phase 1 Decision），「任意激活异常的通用恢复机制」登记为 Non-Blocking Follow-up 候选，不在本 scope。
- 不处理非 HA 同 jobId 的 JC 整进程重启（该形态不是受支持的恢复路径——恢复语义由 HA standby 承担）；如执行中发现其为受支持路径上的 confirmed live defect，路由 successor plan 或 ask-first，**不得**按 Deferred 分类处理。
- 不动 recovery 预算分池（items 28+31 D3 已落）、不动 HA 租约参数。

## Scope

### In Scope

- `nop-stream-runtime` `coordinator/JobCoordinator.java`：leadership 激活路径的 attempt 计数种子化（读源 = `ClusterRegistry.getAttemptHistory`；枚举源 = deployment plan vertex/subtask）。
- （裁定依赖项，closure 时必须显式落「需要/不需要」结论，不得悬空）`cluster/ClusterRegistry.java` 批量读便捷面：仅当逐 subtask 读被证明不可接受时新增，default 方法形式（items 28/31 `loadRetainedEpochManifests` 先例），不强制替身迁移。
- focused 单测（JDBC/H2 + InMemory 双实现）+ `TestMultiJvmCoordinatorFailover` 断言升级 + gated 复验（含 CHAOS-2）。
- owner docs：`01-architecture-baseline.md`（接管 attempt 连续性裁定 + 拒绝方案）、`distributed-runbook.md` §7 item-34 条目 → 已修复（附复验 runId）。
- roadmap item 34 状态写回。

### Out Of Scope

- `nop-stream-core` 及其它模块（缺陷与修复完全在 runtime 模块内）。
- 演练装置改造（`ExerciseSampler`/负载生成零变更，CHAOS-2 判据已含终态收敛）。
- 观测面/指标新增（接管事件已在 jobEventBus RECOVERY_* 既有面内）。

## Execution Plan

### Phase 1 - 修复方案裁定 + coordinator 种子化实现 + focused 测试

Status: completed
Targets: `nop-stream-runtime/.../coordinator/JobCoordinator.java`、（如需）`cluster/ClusterRegistry.java`、`nop-stream-runtime/src/test/.../coordinator/`、`ai-dev/design/nop-stream/01-architecture-baseline.md`

- Item Types: `Decision | Fix | Proof`

- [x] 裁定并落档：种子化方案（读源/枚举源/放置点/批量与否——批量读便捷面的「需要/不需要」结论显式落档；**若裁定需要**：`gate-inventory.json` 追踪的 `ClusterRegistry` 方法集须同步再生成，属 inventory diff 口径而非行号 re-pin 口径）+ 显式拒绝 upsert（PK 冲突覆盖旧行 = G56 历史丢失 + attempt-1 语义歧义）与删旧插新（append-only 破坏）——写入 `01-architecture-baseline.md` ClusterRegistry 契约段（G56 段落邻近处），含拒绝理由
- [x] 裁定并落档：种子化的 warm 路径 gating 策略（每次激活全量读 vs 仅 attemptCounters 缺失 key 时读）——两选项的额外读开销与语义等价性在裁定中对比后择一落档；fresh-job 与同 JVM 既有路径的行为与开销不得劣化
- [x] 裁定并落档：种子化读失败（注册表异常）时的行为——在「listener 异常被平台 elector 吞掉、无降级无重试」的真实语义下（见 Current Baseline），显式裁定传播前状态复位（如 de-active 后 rethrow，使冻结面不留 active=true 半激活态）或维持传播并在 owner doc 如实记录冻结后果；「吞掉后静默保持 active=true 且无 assignment」不得作为未记录的默认结果
- [x] 实现：`activateAsLeader` 路径在物化 assignment 前，按 deployment plan 枚举对每个 `(vertexId, subtaskIndex)` 从注册表 attempt 历史取 max(attemptNumber) 种子化 `attemptCounters`（无历史行 → 种子 0 → 行为与现状逐字节等价；已有更大内存值时不回退），失败行为按上一条裁定执行
- [x] focused 测试（JDBC/H2，单 JVM，可仿 `TestJobCoordinatorFailoverRestore` 的 elector harness 形态）：同 jobId 预置 attempt_number=1..k 行 → 第二个新鲜 `JobCoordinator` 实例激活 → 断言①新 assignment 行落库且 attempt_number = k+1..（严格接续）②无 duplicate-key 异常③`getAttemptHistory` 全历史单调且旧行原样保留
- [x] focused 测试（回归钉定）：fresh-job 路径（注册表无行）attempt 从 1 起；同 JVM 二次激活（内存计数器已在）不回退不重复
- [x] focused 测试（InMemory 对等）：同场景经 `InMemoryClusterRegistry` → 历史单调 + 无「Attempt number regression」WARN 触发路径

Exit Criteria:

- [x] 种子化后：预置行场景下新 leader 激活成功完成（assignment 行 + 接管日志无 listener 异常），上述 focused 测试全绿且以具体断言（行内容/attempt_number 序列/无 regression WARN 触发）钉定，测试文件与用例名在 log 中可查
- [x] G56 保持证据：测试断言旧行（attempt 1..k）在接管后仍原样可读（append-only 未破坏）
- [x] 回归证据：fresh-job/同 JVM 用例证明既有行为零变更；`./mvnw test -pl nop-stream -am -T 1C` 全绿
- [x] **接线验证**（Minimum Rules #23）：种子化读真实发生——以可观察效果断言（接管后 attempt_number 接续 k 而非从 1 重启），非仅类型存在
- [x] **无静默跳过**（Minimum Rules #24）：种子化读失败的行为按 Phase 1 显式裁定执行并有 focused 测试钉定（若裁定为 de-active 后 rethrow：断言失败后 `active=false`；若裁定为维持传播：owner doc 已如实记录「吞掉 → 冻结半激活」后果且测试断言异常确实抛出）——不允许未裁定的吞掉后静默保持 active=true
- [x] 三项裁定（方案与拒绝面 / warm 路径 gating / 读失败行为）全部落档 `01-architecture-baseline.md`；`ai-dev/logs/` 当日条目更新
- [x] 若 invariants 注册表 pin 因行号漂移失配：按既有 re-pin 流程同步并在 log 记录（无漂移则显式记「无 pin 受影响」）

### Phase 2 - 多 JVM 接管断言面补全 + gated 接管证明

Status: completed
Targets: `nop-stream-runtime/src/test/.../multijvm/TestMultiJvmCoordinatorFailover.java`

- Item Types: `Fix | Proof`

- [x] 升级 `testCoordinatorKillTriggersStandbyTakeover`：**kill 前先带 deadline 轮询至 `nop_stream_task_assignment` 存在 attempt_number≥1 的行**（`waitForLeaderAndAssignments` 只查租约行，不足以行使缺陷窗口），再记录 kill 前最大 attempt_number（**per-subtask 口径**比较；本测试 fresh 表 + globalRecovery 全 subtask 统一 +1，历史均匀，全局口径与 per-subtask 等价——采用 per-subtask 口径钉死，避免未来形态演化引入不均匀历史时假失败）；接管租约翻转判定通过后，**带 deadline 轮询**（模式仿同文件 `waitForAssignmentEpoch`）断言①存在 fencing_token 为新 epoch 的 assignment 行（注意 fencing_token = leaderEpoch × 1_000_000 + recoveryGen，断言用 `fencing_token` 列）②其 attempt_number 严格大于 kill 前对应 subtask 最大值③coordinator-1 日志无 `duplicate-key` 且无 `invoke-become-leader-listener-error` 中止签名
- [x] `testBrainSplitFencingBoundary` 同步补①②（其断言主体 epoch 严格递增保持不变）
- [x] 红证（A/B 对照）：在未含修复的代码上运行升级后测试确认必红——**选择性 stash 只回滚 `src/main` 修复文件**（整库 stash 会连升级后的测试一起回滚，红证失效；09-04 log :138 有选择性 diff 先例），含修复代码上绿，两次运行证据记录在案
- [x] gated 运行 `-Dnop.stream.test.multi-jvm.enabled=true` 下两测试绿（含修复代码）

Exit Criteria:

- [x] 升级后的两测试在 gated 模式绿；断言覆盖「接管完成」而非仅「租约翻转」（缺陷窗口前置轮询 + 新 epoch assignment 行 + attempt 接续 + 日志无中止签名齐备）；红证记录在案（未修复代码上必红、含修复代码上绿，两次运行证据）
- [x] **端到端验证**（Minimum Rules #22）：kill 前 assignment 已落库 → 真实 SIGTERM kill → 新 coordinator JVM 真实接管 → 新 assignment 行跨 JVM 可观察——完整路径经真实多 JVM 走通
- [x] 默认套件（非 gated）`./mvnw test -pl nop-stream -am -T 1C` 全绿（断言升级对默认态零影响——该测试类整体 gated）
- [x] `ai-dev/logs/` 当日条目更新（含 gated 运行结果、红证与产物路径如有）

### Phase 3 - CHAOS-2 端到端复验 + runbook/roadmap 收口

Status: completed
Targets: `nop-stream-fraud-example/.../scenario/TestStabilityExerciseMultiJvm.java`（仅运行，不改造——**执行偏差**：harness 存活判据修正一处，见下）、`ai-dev/design/nop-stream/distributed-runbook.md`、`ai-dev/backlog/nop-stream-productization-roadmap.md`

- Item Types: `Proof | Follow-up`

- [x] gated 运行 `TestStabilityExerciseMultiJvm#chaosJcHaFailover`（`preserve-artifacts=true` 强制，参数沿 §5 EX 行默认）：断言终态收敛（期望集完整无丢失无重复 + checkpoint 恢复推进 + 输出不冻结）与既有子判据（租约翻转/epoch 严格递增）同时成立；产物留档 `_tmp/mini-stream-cluster/`
- [x] 从留档产物核对：接管 coordinator 日志无 `nop.err.dao.sql.duplicate-key`、接管后存在新 attempt 接续的 assignment 行（演练装置 samples/DB 面或日志面任一可观察源）
- [x] runbook §7 item-34 条目更新为「已修复」（格式沿 :152 item 28 先例：修复要素 + 复验 runId + 复现命令保留）
- [x] roadmap item 34 写回 `done`（Work Items 行 + Last updated 头，沿 items 28/31 写回先例）
- [x] `docs-for-ai/` 影响裁定：runbook/01-architecture-baseline 属 `ai-dev/design/`；`docs-for-ai/03-modules/nop-stream.md` 无 HA 接管语义条目（rg 核对），如执行中发现需同步则就地更新，否则显式记 No docs-for-ai update required

Exit Criteria:

- [x] CHAOS-2 gated 运行 pass（verdict=pass 留档 run-summary + 本 plan Closure 记录 runId）；修复前红/修复后绿的对照以 runbook §7 既有记录（`1788457688118-1`）为「前」证据
- [x] **端到端验证**（Minimum Rules #22）：HA 接管 → 重部署 → checkpoint 恢复 → 数据面终态收敛，从 kill 到期望集完整的链条经真实多 JVM 演练完整走通
- [x] runbook §7 条目与 roadmap 状态一致反映修复事实；`node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] `ai-dev/logs/` 当日条目记录复验与收口

## Closure Gates

> **关闭条件**：本 section 全部条目及各 Phase Exit Criteria 全部 `[x]` 后，方可将 Plan Status 改为 `completed`（独立 closure audit 前置，见 guide）。

- [x] 预置行场景下新 leader 接管完成（focused + 多 JVM + CHAOS-2 三层证据齐备），无 duplicate-key、无 listener 中止
- [x] G56 attempt 历史 append-only 与跨接管单调性有测试钉定（旧行原样保留断言）
- [x] fresh-job / 同 JVM / globalRecovery / TM kill 既有路径零回归（全模块测试绿）
- [x] 种子化裁定（含拒绝方案）落档 `01-architecture-baseline.md`；runbook §7 已修复条目 + runId 落档；roadmap item 34 写回 `done`
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect
- [x] 独立子 agent closure-audit 完成且 evidence 写入本 plan Closure 节
- [x] **Anti-Hollow Check**：种子化读 → attemptCounters → assignTask INSERT → 新行落库的全链在运行时真实连通（Phase 1 接线断言 + Phase 2 跨 JVM 行观察 + Phase 3 演练产物三层任举其二为证）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-runtime --severity high` exit 0
- [x] `node ai-dev/tools/check-nop-stream-invariants.mjs` exit 0（含如发生的 re-pin）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0
- [x] checkstyle / 代码规范检查通过（全量构建内含）

## Deferred But Adjudicated

（起草时无；执行中如出现按 Allowed classifications 分类并附理由。注意：非 HA 同 jobId JC 重启若被确认为受支持路径上的 live defect，走 successor plan / ask-first，不进本节——见 Non-Goals 裁定行）

## Non-Blocking Follow-ups

- （watch-only residual）平台 elector 吞 listener 异常的通用面：任何激活路径异常当前终态均为「冻结半激活 leader」（无重试/无降级，`AbstractLeaderElector.onBecomeLeader` 平台行为）——本 plan 消除已知异常源并裁定种子化读失败行为后，通用恢复机制（如 activateAsLeader 自降级或 elector 重试策略）留待后续裁定，非本 plan closure 必需
- （观察项）`TestMultiJvmExactlyOnceRecovery` 负载相关恢复检测超时 flake 保持 watch-only（归属 plan 1951-3 既有记录，非本 plan 缺陷）

## Closure

Status Note: 三层证据（focused 5/5 + 多 JVM 接管断言面红绿对照 + CHAOS-2 演练 pass）证明接管完成性缺陷已消除且既有路径零回归；三项裁定与拒绝面落档 owner doc；runbook §7 / roadmap 已写回修复事实。Phase 3 存在唯一已记录执行偏差（harness 存活判据 HA 化，判据强度不降）。独立 closure audit APPROVED。
Completed: 2026-09-04

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent fresh session `ses_f95c2772effeJQlv9Yz0e90MO1`（2026-09-04）
- Evidence:
  - **Phase 1 全部 Exit Criteria PASS**（live 锚点）：`JobCoordinator.java` `seedAttemptCountersFromRegistryLocked()`（:1945，于 :1901 被 `activateAsLeader` 在 recoveryLock 内、rotate（:1894）与 prepareAssignmentsLocked（:1902）之间调用；max-raise 不回退 :1968-1970；读失败 de-active（:1981-1982）+ typed rethrow :1986）；`ClusterRegistry` 接口零扩展；`TestJobCoordinatorAttemptSeedOnTakeover` 5 用例（audit 现场复跑 5/5 绿）；三项裁定 + 拒绝面 = `01-architecture-baseline.md:279-296`；无 pin 受影响（audit 复跑 `check-nop-stream-invariants` exit 0）。
  - **Phase 2 全部 Exit Criteria PASS**：`TestMultiJvmCoordinatorFailover` 两测试接管完成断言（前置轮询 :84/:167、per-subtask max :86/:169、新 fencing band 严格接续轮询 :417-449、日志无中止签名 :130-136）；红证产物 `_tmp/mini-stream-cluster/1788483771855-1`（coordinator-1.log 双中止签名实证，audit grep 命中）；gated 3/3 绿两次 + 默认套件 BUILD SUCCESS（log 留档）。
  - **Phase 3 全部 Exit Criteria PASS**：CHAOS-2 verdict=pass（runId `1788487981277-1`，期望集 43 行完整、epoch 94 次推进 max gap 5007ms、lease 1→2→3、fencing 1M→2M→3M、全 coordinator 日志零 duplicate-key / 零 listener-error、Seeded 0→1 / 0→2 跨两轮接管）；runbook §7:153 已修复条目（plan ref + 复验 runId + 复现命令保留）；roadmap item 34 done（:82 + Last updated :3）。
  - **Anti-Hollow**：全链 live 追踪连通（activateAsLeader → seed（真实 getAttemptHistory 读 :1957）→ attemptCounters 写 → prepareAssignmentsLocked 消费（:799-800）→ assignTask INSERT（:807-809）），无 stub/no-op/空方法体；`history == null || isEmpty → continue` 分支带显式 fresh-job 语义注释（:1958-1963），非静默跳过；三层证据齐备。
  - **工具退出码**：`check-plan-checklist --strict` 0、`scan-hollow-implementations --module nop-stream-runtime --severity high` 0、`check-nop-stream-invariants` 0、`check-doc-links --strict` 0（audit 全部现场复跑）。
  - **Deferred 分类检查**：Deferred But Adjudicated 为空；Non-Blocking Follow-ups 两项（elector 吞 listener 异常平台行为——live 核实 `AbstractLeaderElector.java:182-190` 逐 listener try/catch 无重试，Non-Goals 显式排除 + owner doc :292 落档；`TestMultiJvmExactlyOnceRecovery` flake 归属 plan 1951-3 既有记录）均确认 non-blocking，无 in-scope live defect 降级。
  - **偏差诚实性**：Phase 3 Targets 行 + 当日 log 记录 harness 存活判据偏差（`assertSoakHealth haCoordinatorKills`——仅存活判据 HA 化，收敛/推进/队列判据不变，audit 逐行核对 :953-1001）。
  - Findings：F1 Minor（roadmap APPROVED 表述先于 audit 执行的时序——由本 closure ritual 补齐后回溯成立）、F2/F3 Info（未提交工作按 closure 时提交；全量套件/checkstyle 采信 log 留档 + audit 抽样复跑）。

Follow-up:

- （watch-only residual）平台 elector 吞 listener 异常的通用恢复机制（activateAsLeader 自降级 / elector 重试策略）留待后续裁定，非本 plan 遗留 work
- （watch-only）`TestMultiJvmExactlyOnceRecovery` 负载相关 flake（归属 plan 1951-3 既有记录）
- no remaining plan-owned work
