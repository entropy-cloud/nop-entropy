# 242 nop-ai-agent Cross-Process Daemon Coordination（多实例 daemon 扫描协调：team 级 scan lease + 降冗余扫描优化层）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-cross-process-daemon-coordination
> Last Reviewed: 2026-06-18
> Source: carry-over from `ai-dev/plans/241-nop-ai-agent-async-team-task-orchestration.md`（Deferred But Adjudicated `cross-process daemon 协调` → successor `L4-cross-process-daemon-coordination`）；roadmap §4 Layer 4 row `L4-cross-process-daemon-coordination` ❌（line 265）
> Related: `221`（交付 `ISessionTakeoverLock` / `DbSessionTakeoverLock` —— 本计划复用其 DB lease-table CAS 范式）、`222`（交付 `ScheduledRecoveryManager` daemon —— 本计划为其 successor 协调留接口但不接线）、`236`（交付 `TeamTaskSchedulerDaemon` —— 本计划接线其 scanOnce team 级协调）、`227`/`240`（交付 `claimTask` DB CAS 正确性地板）

## Purpose

把 nop-ai-agent 的多实例 daemon 部署从"**N 个实例无协调地重复扫描全部团队**——每个 `TeamTaskSchedulerDaemon` 实例每 5s 扫描所有 active team，`claimTask` CAS 保证同一 task 不被 double-dispatch（正确性地板已存在），但 N 个实例全部执行相同的 DB 读 + topology build + ready query，冗余扫描负载随实例数线性增长"扩展为"**team 级 scan lease 协调**：daemon 在扫描每个 team 前经 `IDaemonCoordinator` 获取该 team 的短时 scan lease，若另一实例持有该 team 的活跃 lease 则跳过该 team（降冗余扫描），N 个实例自然分担不同 team 的扫描+派发工作"。

**关键定位（roadmap line 265 + plan 241 设计裁定 4）**：cross-process daemon 协调是**降冗余扫描的优化层**，非正确性前置。`claimTask` DB 级 CAS（affected-row-count==1，plan 227/240）已提供多实例 double-dispatch 正确性地板——多个 daemon 实例并发 claim 同一 task 只有 1 个胜出。本计划交付的是在此正确性地板之上的**扫描负载优化**：减少冗余 DB 读 + topology build + claim CAS 竞争。

## Current Baseline

基于 live repo 核对（来源：plan 221 / 222 / 236 / 241 closure audit evidence 已对照 live code path 验证；本段描述现状）：

- **`TeamTaskSchedulerDaemon.scanOnce()` 无跨进程协调 ✅**（plan 236）：`TeamTaskSchedulerDaemon.java:387` scanOnce 经 `resolveTeamIdsToScan()` 获取全部 active team ID（`ITeamManager.getActiveTeams()`，无 targetTeamIds 配置时扫描全部），逐 team 加载 tasks → build `TeamTaskTopology` → `getReadyTasks()` → filter CREATED → `claimTask` CAS。N 个实例 = N 份全量 team 扫描，每 5s 一轮。`claimTask` CAS（`DbTeamTaskStore.claimTask` 条件 `UPDATE ... WHERE STATUS='CREATED'`，affected-row-count==1）保证正确性，但冗余扫描负载随实例数线性增长。
- **`ScheduledRecoveryManager.scanOnce()` 无跨进程协调 ✅**（plan 222）：每 60s 全量扫描 stale lock cleanup + timeout + orphan detection + recovery。SQL 操作幂等（DELETE absent rows = no-op），多实例并发安全但冗余。60s 低频 + 纯检测/清理（无 agent 派发），冗余成本远低于 SchedulerDaemon。
- **`ISessionTakeoverLock` / `DbSessionTakeoverLock` ✅**（plan 221）：per-session lease lock（`tryAcquire(sessionId, ownerId, leaseMs)` / `release(sessionId, ownerId)` / `isHeld(sessionId)` / `tryRenew`），backed by `ai_agent_session_lock` 表（PK SESSION_ID + LOCK_OWNER + LOCK_ACQUIRED_AT + LOCK_EXPIRES_AT + TENANT_ID），INSERT 乐观 + duplicate-key conditional UPDATE CAS 范式。这是本计划 DB lease-table 范式的**直接参照模板**。
- **`DefaultAgentEngine.instanceId` ✅**：`UUID.randomUUID().toString()`（`DefaultAgentEngine.java:363`），用作 session takeover lock 的 ownerId。本计划 daemon 协调需类似 ownerId 标识 daemon 实例。
- **`claimTask` DB CAS 正确性地板 ✅**（plan 227 / 240）：`DbTeamTaskStore.claimTask` 条件 UPDATE + affected-row-count==1。多实例并发 claim 同一 task 只有 1 个胜出——本计划优化层不触及此正确性地板。
- **零 daemon 协调代码**：grep `IDaemonCoordinator|DbDaemonCoordinator|CrossProcessDaemonCoordinator|DistributedDaemonLock|scanLease|daemonCoordination` 在 `nop-ai/nop-ai-agent/src/main/` 返回 0 命中（NEXT_ITEM 已确认 carry-over 未落地）。

## Goals

- **`IDaemonCoordinator` team 级 scan lease 契约**：opt-in 跨进程 daemon 扫描协调扩展点。核心契约 `tryAcquireScanLease(teamId, ownerId, leaseMs) → boolean`（原子 CAS：无活跃 lease 或前 lease 已过期 → 获取成功 true；另一实例持有活跃 lease → false，调用方跳过该 team）+ `releaseScanLease(teamId, ownerId) → boolean`（条件释放，只释放自己持有的 lease）+ `isScanLeaseActive(teamId) → boolean`（查询活跃 lease）。语义对称 `ISessionTakeoverLock` 的 lease CAS 范式，但 keyed by teamId、面向 scan 协调域。
- **`NoOpDaemonCoordinator` shipped 默认（零回归）**：`tryAcquireScanLease` 恒返回 `true`（永不阻止扫描 = 单实例行为不变），`releaseScanLease` 返回 `false`（无 lease 可释放），`isScanLeaseActive` 返回 `false`。显式"无跨进程协调"语义，非静默 no-op（Minimum Rules #24）。
- **`DbDaemonCoordinator` DB 功能实现**：backed by 新表 `ai_agent_daemon_coord`（PK (TEAM_ID) + OWNER_ID + ACQUIRED_AT + EXPIRES_AT + TENANT_ID），INSERT 乐观 + duplicate-key conditional UPDATE CAS（镜像 `DbSessionTakeoverLock` 范式），构造期 `initSchema` 自动建表。多租户守卫（`ITenantResolver` opt-in 默认 `NullTenantResolver`）。多个 `DbDaemonCoordinator` 实例指向同一 DB 即协调互相可见。
- **`TeamTaskSchedulerDaemon` 接线 team 级协调**：scanOnce 逐 team 扫描前经 `coordinator.tryAcquireScanLease(teamId, daemonOwnerId, leaseMs)` 获取 lease——成功则扫描该 team 并在扫描完成后 `releaseScanLease`；失败（另一实例持有活跃 lease）则跳过该 team（`skippedCoordinatedTeams` 计数 + LOG.debug，非静默跳过）。NoOp 默认 = 全部 lease 获取成功 = 零回归。
- **降冗余扫描验证（Anti-Hollow #22）**：两个 `TeamTaskSchedulerDaemon` 实例（不同 ownerId）指向同一 DB + 同一 team 集合，同一 scan 周期内**不重复扫描同一 team**（一个实例获取 lease 扫描，另一个跳过）。验证方式：可观测的 per-team lease 获取/跳过计数断言，非仅最终 task 状态。
- **诚实失败语义（No Silent No-Op #24）**：lease 获取失败 = 跳过该 team（显式协调语义，非静默跳过——`skippedCoordinatedTeams` 计数记录）；DB 异常 = `NopAiAgentException`（不吞异常）；NoOp = 恒获取成功（显式无协调语义）。**正确性地板不受影响**：即使协调完全失效（lease 表删除），claimTask CAS 仍保证无 double-dispatch。
- **设计文档**：新建 `ai-dev/design/nop-ai-agent/nop-ai-agent-cross-process-daemon-coordination.md`（记录核心裁定：DB lease-table 范式 / team 级 scan lease / SchedulerDaemon 优先 RecoveryManager successor / 优化层定位非正确性前置 / NoOp 零回归 / 拒绝替代方案）+ 更新 roadmap §4 Layer 4（`L4-cross-process-daemon-coordination` ❌→✅）。

## Non-Goals

- **`ScheduledRecoveryManager` 跨进程协调**：RecoveryManager 60s 低频 + 纯幂等检测/清理（无 agent 派发），冗余扫描成本远低于 SchedulerDaemon（5s + agent 派发）。Classification: successor plan required。
- **静态 team 分区（hash-based partitioning）**：按 `hash(teamId) % instanceCount` 静态分配 team 到实例。需实例数发现 + 实例增减时重平衡，是不同结果面。本计划 lease-based 协调是更简单的动态协调（实例自然 gravitate 到未被扫描的 team）。Classification: successor plan required。
- **Load-aware work stealing / 动态重平衡**：基于实例负载动态迁移 team 归属。Classification: optimization candidate。
- **Per-task 跨进程协调**：`claimTask` CAS 已提供 per-task 正确性地板。Classification: out-of-scope（无需变更）。
- **nop-job cluster 协调集成**：`ScheduledRecoveryManager` 用 `IScheduledExecutor`（nop-commons）而非 nop-job；nop-job cluster 协调（DB job persistence / cluster election）是独立基础设施层。Classification: successor plan required。
- **`SpawnMemberAgentTaskStep` async 化**（plan 241 carry-over `L4-spawn-step-async`）。Classification: successor plan required。
- **多成员 per-task 路由 / spawn session 复用池化 / nop-task decorator / 运行时动态增删图节点**（各 plan carry-over）。Classification: 各自 successor。
- **修改 `ITeamTaskSchedulerDaemon` / `ITeamManager` / `ITeamTaskStore` / `IAgentEngine` 契约**：消费原样契约，仅在 daemon 内部新增 coordinator 字段 + scanOnce guard。
- **修改 `ISessionTakeoverLock` 契约**：本计划新建独立 `IDaemonCoordinator`（不同域 + 不同 key space + 不同 lease 语义），不扩展现有 session takeover lock。

## Scope

### In Scope

- `io.nop.ai.agent.runtime.coordination` 包（新）：
  - `IDaemonCoordinator.java` — team 级 scan lease 契约（`tryAcquireScanLease` / `releaseScanLease` / `isScanLeaseActive`）
  - `NoOpDaemonCoordinator.java` — shipped 默认（恒获取成功，零回归）
  - `DbDaemonCoordinator.java` — DB 功能实现（lease-table CAS，镜像 `DbSessionTakeoverLock` 范式）
  - `AiAgentDaemonCoordTable.java` — `ai_agent_daemon_coord` 表 DDL + 列常量（PK TEAM_ID + OWNER_ID + ACQUIRED_AT + EXPIRES_AT + TENANT_ID）
- `io.nop.ai.agent.team.scheduler` 包：
  - `TeamTaskSchedulerDaemon.java` — 新增 `daemonCoordinator` 字段（默认 `NoOpDaemonCoordinator`）+ `setDaemonCoordinator`/`getDaemonCoordinator`（null-safe 回退 NoOp）+ `daemonOwnerId` 字段（默认 `"scheduler-daemon-"+UUID`）+ scanOnce 逐 team lease guard（获取→扫描→释放 / 获取失败→跳过计数）+ `SchedulerScanResult` 扩展 `skippedCoordinatedTeams` 计数
- 测试文件（新）：
  - `TestDbDaemonCoordinator.java`（lease CAS：无前 lease / 同 owner renew / 异 owner 活跃 lease fail / 过期 lease 抢占 / release 条件 / isScanLeaseActive / 多租户守卫）
  - `TestNoOpDaemonCoordinator.java`（恒获取成功 / 零回归语义）
  - `TestSchedulerDaemonCoordination.java`（NoOp 零回归全量扫描 + DB 协调器 team 级 lease guard 接线 + 跳过计数）
  - `TestMultiInstanceScanCoordination.java`（两实例同 DB 同 team 集合 → 同周期不重复扫描同一 team，Anti-Hollow 降冗余断言）
  - `TestSchedulerDaemonCoordinationHonestFailure.java`（lease fail 跳过非静默 + DB 异常 NopAiAgentException + NoOp 恒成功）
- 设计文档：`ai-dev/design/nop-ai-agent/nop-ai-agent-cross-process-daemon-coordination.md`（新）+ `nop-ai-agent-roadmap.md` §4 Layer 4（`L4-cross-process-daemon-coordination` ❌→✅）+ `nop-ai-agent-task-scheduler-daemon.md`（既有 SchedulerDaemon owner doc 更新：lines 30/64/73 的"跨进程协调为 successor"改为"SchedulerDaemon team 级 scan lease 已落地（plan 242），RecoveryManager 协调仍 successor"）

### Out Of Scope

- 见 Non-Goals（RecoveryManager 协调 / 静态分区 / work stealing / per-task 协调 / nop-job 集成 / spawn-step async / 多成员路由 / spawn 池化 / decorator / 动态改图 / 既有契约变更 / ISessionTakeoverLock 扩展 均为显式 successor 或 out-of-scope）

### 设计裁定（Pre-Adjudicated）

以下裁定在 plan 撰写阶段已确定，执行时直接遵循：

1. **机制 = DB lease-table（镜像 `DbSessionTakeoverLock` 范式），非 DB advisory lock / fencing token**。`ai_agent_daemon_coord` 表（PK TEAM_ID + OWNER_ID + ACQUIRED_AT + EXPIRES_AT + TENANT_ID），INSERT 乐观 + duplicate-key conditional UPDATE CAS。理由：(1) `DbSessionTakeoverLock`（plan 221）已证明此范式在本 codebase 可行且 portable（标准 SQL，无 vendor-specific advisory lock）；(2) fencing token（plan 235）是 write-ordering 原语，非 scan-coordination 原语；(3) 独立表（非 ai_agent_session_lock 扩展）因为不同域（scan coordination vs session takeover）+ 不同 key space（teamId vs sessionId）+ 不同 lease 语义（scan lease = scan 周期级，session lease = 30min）。

2. **协调粒度 = per-team scan lease，非 per-scan-cycle leader election / 全局 scan lock**。理由：(1) per-team lease 使 N 个实例自然分担不同 team 的扫描+派发（一个实例扫 team A 时另一实例 gravitate 到 team B），非"一个 leader 扫全部"的单点负载；(2) per-scan-cycle leader 选举使 leader 实例承担全部扫描+派发负载，N-1 个实例空闲——对 SchedulerDaemon（每 5s 派发 agent 执行）是反负载分布；(3) per-team lease 更细粒度，team 多时协调效果更明显。

3. **`IDaemonCoordinator` 是独立新接口，非复用 `ISessionTakeoverLock`**。理由：(1) 不同域（scan coordination vs session takeover）；(2) 不同 key space（teamId vs sessionId）；(3) 允许独立演进（future partitioning / work stealing）不污染 session lock 契约；(4) 语义对称但命名清晰隔离。

4. **只接线 `TeamTaskSchedulerDaemon`，不接线 `ScheduledRecoveryManager`**。理由：(1) SchedulerDaemon 5s 高频 + agent 派发（昂贵），冗余成本最高；(2) RecoveryManager 60s 低频 + 纯幂等检测/清理（无 agent 派发），冗余成本低；(3) 不同结果面可独立收口（RecoveryManager 协调为 successor）；(4) 本计划结果面（SchedulerDaemon 降冗余扫描）的成立不依赖 RecoveryManager 协调。**已知限制（诚实记录）**：RecoveryManager 仍无协调（60s 全量冗余扫描），为显式 successor。

5. **`NoOpDaemonCoordinator.tryAcquireScanLease` 恒返回 `true`（永不阻止扫描 = 单实例行为不变）**。理由：(1) 零回归硬约束——NoOp 接线后 daemon 行为与无协调器逐行一致（全量扫描全部 team）；(2) 显式"无跨进程协调"语义（Minimum Rules #24），非静默跳过；(3) 与 `NoOpSessionTakeoverLock`（session 域）/ `NoOpResourceGuard`（quota 域）shipped 默认范式一致。

6. **正确性地板不受协调层影响**：即使 `IDaemonCoordinator` 完全失效（lease 表删除 / 全部 fail），`claimTask` CAS 仍保证无 double-dispatch。协调层只影响扫描负载（冗余 vs 分担），不影响正确性。理由：roadmap line 265 + plan 241 设计裁定 4 明确定位 cross-process 为优化层非正确性前置。

7. **`daemonOwnerId` 默认 `"scheduler-daemon-"+UUID`**，可经构造器/setter 覆盖。理由：(1) 每个 daemon 实例需唯一 ownerId 标识其 lease 归属；(2) 类比 `DefaultAgentEngine.instanceId`（`UUID.randomUUID().toString()`，`DefaultAgentEngine.java:363`）唯一标识范式（本字段加 `"scheduler-daemon-"` 前缀提升可观测性，非完全镜像 engine 裸 UUID）；(3) 可覆盖支持测试确定性（固定 ownerId）+ 部署自定义（hostname/Pod 名）。**关键约束**：`daemonOwnerId` 必须每实例唯一，**不可复用既有 `daemonSessionId`**（`TeamTaskSchedulerDaemon.java:151` 默认 `"team-task-scheduler-daemon"` 是全部实例共享的固定值，用于 task `claimedBy`/`completedBy` 审计标记）——若复用则所有实例 ownerId 相同，`tryAcquireScanLease` 全部命中"同 owner renew"路径，lease 永不阻止任何人，协调完全失效（hollow）。

8. **`scanLeaseMs` = `scanIntervalSec * 1000 * 6`（默认 `DEFAULT_SCAN_LEASE_MS = 30_000L`，即默认 5s scan interval × 6 = 30s lease）**，经 `TeamTaskSchedulerDaemon` 字段 + `setScanLeaseMs` setter 配置。理由：(1) lease 必须显著大于单次 scan 周期（`scanIntervalSec * 1000`），保证持有 lease 的实例在当前周期内完成该 team 的扫描+派发（topology build + claim CAS + agent execute join + complete）不被另一实例中途抢占；(2) 6× scan interval 给 worst-case 单 team 扫描（含一次同步 agent 执行 join）留充裕窗口，同时保持 failover 敏捷度（实例崩溃后 lease 在 30s 内过期，另一实例下周期抢占）；(3) 经 setter 可调，部署按 team 规模 / agent 执行耗时 tune（大 team / 慢 agent → 增大 leaseMs）；(4) **已知边界（诚实记录）**：若单 team 扫描（含 agent execute join）实际耗时超过 `scanLeaseMs`，lease 中途过期，另一实例可抢占 → 两实例短暂并发扫描同一 team（降冗余优化减弱，非正确性问题——claimTask CAS 仍保证无 double-dispatch）。scanOnce 扫描完成后 `releaseScanLease` 主动释放（非仅依赖 TTL 过期），正常路径无 lease 残留。

## Execution Plan

### Phase 1 - 协调原语（IDaemonCoordinator 契约 + NoOp 默认 + DbDaemonCoordinator DB 实现 + 设计裁定落档）

Status: completed
Targets: `io.nop.ai.agent.runtime.coordination`（IDaemonCoordinator / NoOpDaemonCoordinator / DbDaemonCoordinator / AiAgentDaemonCoordTable）、`ai-dev/design/nop-ai-agent/nop-ai-agent-cross-process-daemon-coordination.md`

- Item Types: `Decision`（DB lease-table 范式 / per-team scan lease 粒度 / 独立新接口 / SchedulerDaemon 优先 / NoOp 零回归 / 正确性地板不受影响 / daemonOwnerId 不复用 daemonSessionId / scanLeaseMs 默认 6× scan interval）、`Proof`

- [x] 新建 `io.nop.ai.agent.runtime.coordination.IDaemonCoordinator` 接口：`tryAcquireScanLease(String teamId, String ownerId, long leaseMs) → boolean`（原子 CAS 获取/renew/抢占）+ `releaseScanLease(String teamId, String ownerId) → boolean`（条件释放只释放自己持有的）+ `isScanLeaseActive(String teamId) → boolean`（查询活跃 lease，不区分 owner）。Javadoc 含完整 CAS 真值表（无前 lease → true / 同 owner renew → true / 过期 lease 抢占 → true / 异 owner 活跃 lease → false）+ lease/TTL 语义 + 线程安全契约。语义对称 `ISessionTakeoverLock` lease 范式，命名清晰隔离 scan-coordination 域。
- [x] 新建 `NoOpDaemonCoordinator`（singleton，`noOp()` 工厂）：`tryAcquireScanLease` 恒 `true`（永不阻止扫描）+ `releaseScanLease` 恒 `false`（无 lease 可释放）+ `isScanLeaseActive` 恒 `false`。Javadoc 自述显式"无跨进程协调"语义（非静默 no-op）。
- [x] 新建 `AiAgentDaemonCoordTable`：`TABLE_NAME = "ai_agent_daemon_coord"` + 列常量（`TEAM_ID` PK / `OWNER_ID` / `ACQUIRED_AT` / `EXPIRES_AT` / `TENANT_ID` nullable）+ `DDL_CREATE_TABLE`（`CREATE TABLE IF NOT EXISTS`）。镜像 `AiAgentSessionLockTable` 结构（独立表 + PK 保证 at most one lease per team）。
- [x] 新建 `DbDaemonCoordinator` implements `IDaemonCoordinator`：`DataSource` + `ITenantResolver`（opt-in 默认 `NullTenantResolver`）+ 构造期 `initSchema`（镜像 `DbSessionTakeoverLock`）。`tryAcquireScanLease`：INSERT 乐观 → duplicate-key conditional UPDATE（`WHERE TEAM_ID=? AND (OWNER_ID=? OR EXPIRES_AT <= ?)` affected-row==1）。`releaseScanLease`：`DELETE WHERE TEAM_ID=? AND OWNER_ID=?` affected-row==1。`isScanLeaseActive`：`SELECT COUNT(*) WHERE TEAM_ID=? AND EXPIRES_AT > ?`。多租户非空时注入 `TenantSql.whereTenant`。`isDuplicateKey` portable 检测（SQLState 23505 / MySQL 1062 / `SQLIntegrityConstraintViolationException`）。全部 SQLException 包裹 `NopAiAgentException`（不吞异常）。
- [x] 新建 `ai-dev/design/nop-ai-agent/nop-ai-agent-cross-process-daemon-coordination.md`：记录核心裁定（DB lease-table / per-team scan lease / 独立接口 / SchedulerDaemon 优先 / 优化层定位 / NoOp 零回归 / 正确性地板不受影响）+ 拒绝替代方案（DB advisory lock vendor-specific / fencing token write-ordering 非扫描协调 / per-scan-cycle leader 单点负载 / 复用 ISessionTakeoverLock 域混淆）。遵循 design doc 规范（只记最终设计状态与决策，不放类签名/代码）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `IDaemonCoordinator` 接口存在，含 `tryAcquireScanLease` / `releaseScanLease` / `isScanLeaseActive` 三方法 + Javadoc CAS 真值表
- [x] `NoOpDaemonCoordinator` 存在，`tryAcquireScanLease` 恒 `true` + `releaseScanLease` 恒 `false` + `isScanLeaseActive` 恒 `false`（显式语义非静默 no-op）
- [x] `DbDaemonCoordinator` 存在，`tryAcquireScanLease` 经 INSERT 乐观 + conditional UPDATE CAS（镜像 `DbSessionTakeoverLock` 范式），构造期 `initSchema` 自动建 `ai_agent_daemon_coord` 表
- [x] **无静默跳过**（#24）：`DbDaemonCoordinator` 全部 SQLException 包裹 `NopAiAgentException`（不吞异常）；`tryAcquireScanLease` false = 显式协调信号（非静默）；NoOp = 显式无协调语义
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] **新功能各有对应测试在 Phase 2**（#25）；Phase 1 compile 通过即可
- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-cross-process-daemon-coordination.md` 存在，含核心裁定 + 拒绝替代方案，无类签名/代码
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - SchedulerDaemon 接线 + focused/E2E 测试 + roadmap 同步 + 全量回归

Status: completed
Targets: `io.nop.ai.agent.team.scheduler.TeamTaskSchedulerDaemon`、`io.nop.ai.agent.team.scheduler.SchedulerScanResult`、`nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/`（新测试）、`nop-ai-agent-roadmap.md` §4、`nop-ai-agent-task-scheduler-daemon.md`

- Item Types: `Proof`、`Fix`（SchedulerDaemon 无协调冗余扫描 = carry-over gap）

- [x] 编写 `TestDbDaemonCoordinator`：lease CAS 全路径——无前 lease INSERT 成功 / 同 owner renew 成功 / 异 owner 活跃 lease fail（zero rows）/ 过期 lease 抢占成功 / `releaseScanLease` 条件（只释放自己的，不释放他人的）/ `isScanLeaseActive`（活跃 true / 过期 false / 无 lease false）/ 多租户守卫（非空 tenant WHERE 隔离）/ 并发 tryAcquire（两线程同一 teamId 只有 1 个成功）/ portable duplicate-key 检测
- [x] 编写 `TestNoOpDaemonCoordinator`：`tryAcquireScanLease` 恒 true / `releaseScanLease` 恒 false / `isScanLeaseActive` 恒 false（零回归语义验证）
- [x] `TeamTaskSchedulerDaemon` 接线：新增 `daemonCoordinator` 字段（默认 `NoOpDaemonCoordinator.noOp()`）+ `setDaemonCoordinator`/`getDaemonCoordinator`（null-safe 回退 NoOp）+ `daemonOwnerId` 字段（默认 `"scheduler-daemon-"+UUID.randomUUID()`，构造器/setter 可覆盖；**不复用 `daemonSessionId`**——见设计裁定 7）+ `scanLeaseMs` 字段（默认 `DEFAULT_SCAN_LEASE_MS = 30_000L` = 6× 默认 5s scan interval，`setScanLeaseMs` 可调；见设计裁定 8）+ scanOnce 逐 team lease guard（`resolveTeamIdsToScan` 后逐 team：`tryAcquireScanLease(teamId, daemonOwnerId, scanLeaseMs)` → 成功则扫描该 team + finally `releaseScanLease`；失败则 `skippedCoordinatedTeams++` + LOG.debug 跳过）+ `SchedulerScanResult` 扩展 `skippedCoordinatedTeams` 字段（12-arg 构造器 + getter + 向后兼容适配）
- [x] 编写 `TestSchedulerDaemonCoordination`：NoOp 协调器全量扫描零回归（skippedCoordinatedTeams==0 全部 team 扫描）+ DB 协调器 lease guard 接线（spy/mock 协调器 verify tryAcquireScanLease 被 scanOnce 逐 team 调用 + 获取失败的 team 被跳过 + skippedCoordinatedTeams 计数正确 + 获取成功的 team 扫描后 releaseScanLease 被调用）
- [x] 编写 `TestMultiInstanceScanCoordination`（端到端 Anti-Hollow #22）：两个 `TeamTaskSchedulerDaemon` 实例（不同 `daemonOwnerId`）+ 同一 `DbDaemonCoordinator`（同一内存 H2 DataSource）+ 同一 team 集合（含多 team）→ 两实例并发 `scanOnce` → **同一 team 不被两实例同时扫描**（可观测证据：per-team lease 获取/跳过计数 + claim CAS 竞争计数，非仅最终 task 状态）；NoOp 对比：两实例全量重复扫描（零协调基线）
- [x] 编写 `TestSchedulerDaemonCoordinationHonestFailure`：lease fail（异 owner 活跃）→ 跳过该 team + skippedCoordinatedTeams 计数（非静默跳过）/ `DbDaemonCoordinator` DB 异常 → `NopAiAgentException` 传播（不吞异常）/ NoOp 恒成功（零回归）/ `releaseScanLease` CAS 失败（lease 已被抢占）→ LOG.warn 不影响 scan 结果
- [x] 更新 `nop-ai-agent-roadmap.md` §4 Layer 4：`L4-cross-process-daemon-coordination` ❌→✅（标注 SchedulerDaemon 接线落地 + RecoveryManager 协调仍 successor）
- [x] 更新 `nop-ai-agent-task-scheduler-daemon.md`（既有 SchedulerDaemon owner doc）：lines 30/64/73 的"跨进程协调为 successor"改为"SchedulerDaemon team 级 scan lease 已落地（plan 242），RecoveryManager 协调仍 successor"（owner-doc drift 修复，Rule #17）
- [x] 验证全量测试：`./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict`（退出码 0）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `TestDbDaemonCoordinator` 全绿（lease CAS 全路径：获取/renew/抢占/fail/release/isActive/多租户/并发）
- [x] `TestNoOpDaemonCoordinator` 全绿（恒成功零回归语义）
- [x] `TeamTaskSchedulerDaemon` 含 `daemonCoordinator` 字段（默认 NoOp）+ scanOnce 逐 team lease guard + `SchedulerScanResult.skippedCoordinatedTeams`
- [x] **接线验证**（#23）：scanOnce 运行时确实调用 `tryAcquireScanLease`/`releaseScanLease`（spy/mock 断言，非仅字段存在）
- [x] **端到端降冗余验证**（#22）：两实例同 DB 同 team 集合 → 同周期不重复扫描同一 team（per-team lease 获取/跳过计数可观测证据，非仅最终 task 状态）——闭合"N 实例无协调冗余扫描"gap
- [x] **无静默跳过**（#24）：lease fail 跳过 = skippedCoordinatedTeams 计数（非静默）；DB 异常 = NopAiAgentException（不吞）；NoOp = 显式无协调
- [x] **正确性地板验证**：协调完全失效时（NoOp / lease 表空）claimTask CAS 仍保证无 double-dispatch（既有 plan 236/240 测试零回归）
- [x] 新增功能各有对应 focused 测试（coordinator CAS 全路径 / NoOp 语义 / daemon 接线 / 多实例降冗余 / honest failure 各有测试）
- [x] `TestSchedulerDaemonCoordinationHonestFailure` 全绿（lease fail 跳过计数 / DB 异常传播 / NoOp 恒成功 / release CAS fail LOG.warn）
- [x] roadmap §4 `L4-cross-process-daemon-coordination` 已标 ✅ + RecoveryManager 协调 successor 标注
- [x] `nop-ai-agent-task-scheduler-daemon.md` owner doc 已更新（SchedulerDaemon team 级 scan lease 落地 + RecoveryManager 协调仍 successor）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿（零回归）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `IDaemonCoordinator` team 级 scan lease 契约落地（tryAcquireScanLease / releaseScanLease / isScanLeaseActive + CAS 真值表 Javadoc）
- [x] `NoOpDaemonCoordinator` shipped 默认落地（恒成功零回归）
- [x] `DbDaemonCoordinator` DB 功能实现落地（lease-table CAS，镜像 DbSessionTakeoverLock 范式，非空壳代码）
- [x] `TeamTaskSchedulerDaemon` 接线 team 级协调落地（scanOnce 逐 team lease guard，NoOp 默认零回归）
- [x] 端到端降冗余：两实例同 DB 同 team 集合同周期不重复扫描同一 team（可观测证据）
- [x] 正确性地板不受影响（claimTask CAS 仍保证无 double-dispatch，既有测试零回归）
- [x] 既有测试零回归（plan 236 SchedulerDaemon + plan 222 RecoveryManager 行为不变）
- [x] 必要 focused verification 已完成（coordinator CAS / NoOp 语义 / daemon 接线 / 多实例降冗余 / honest failure 各有测试）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（RecoveryManager 协调 / 静态分区 / work stealing / nop-job 集成 均显式在 Non-Goals 切出）
- [x] 受影响 owner docs（新 design doc + roadmap §4 + `nop-ai-agent-task-scheduler-daemon.md` owner doc 更新）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）scanOnce 运行时确实调用 coordinator（非仅字段存在），（b）两实例降冗余协调真实发生（可观测证据），（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/242-nop-ai-agent-cross-process-daemon-coordination.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0

## Deferred But Adjudicated

### ScheduledRecoveryManager 跨进程协调

- Classification: `out-of-scope improvement`（切出为独立 successor）
- Why Not Blocking Closure: RecoveryManager 60s 低频 + 纯幂等检测/清理（无 agent 派发），冗余扫描成本远低于 SchedulerDaemon（5s + agent 派发）。SQL 操作幂等（DELETE absent rows = no-op），多实例并发安全。本计划结果面（SchedulerDaemon 降冗余扫描）的成立不依赖 RecoveryManager 协调。
- Successor Required: yes
- Successor Path: `ai-dev/plans/{NNN}-nop-ai-agent-recovery-manager-coordination.md`（待创建）

### 静态 team 分区（hash-based partitioning）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 静态分区需实例数发现 + 实例增减时重平衡，是不同结果面。本计划 lease-based 动态协调是更简单的等价优化（实例自然 gravitate 到未被扫描的 team），成立不依赖静态分区。
- Successor Required: yes
- Successor Path: 随 RecoveryManager 协调 successor 一并裁定

## Non-Blocking Follow-ups

- **`ScheduledRecoveryManager` 跨进程协调**：Classification: successor plan required（60s 低频幂等，冗余成本低）。
- **静态 team 分区 / load-aware work stealing / 动态重平衡**：Classification: optimization candidate / successor plan required。
- **nop-job cluster 协调集成**（DB job persistence / cluster election 替代 IScheduledExecutor）：Classification: successor plan required。
- **`SpawnMemberAgentTaskStep` async 化 / 多成员 per-task 路由 / spawn session 复用池化 / nop-task decorator / 运行时动态增删图节点**：各 plan carry-over，各自 successor。

## Closure

Status Note: Plan 242 交付 team 级 scan lease 跨进程 daemon 扫描协调（降冗余扫描优化层，非正确性前置——claimTask CAS 仍是正确性地板）。`IDaemonCoordinator` 契约 + `NoOpDaemonCoordinator` shipped 默认零回归 + `DbDaemonCoordinator` DB 功能实现（镜像 plan 221 `DbSessionTakeoverLock` lease-table CAS 范式）+ `TeamTaskSchedulerDaemon` scanOnce 逐 team lease guard 接线 + `SchedulerScanResult.skippedCoordinatedTeams` 计数。两实例同 DB 同 team 集合同周期不重复扫描同一 team 经 Anti-Hollow #22 端到端验证（per-team lease 获取/跳过可观测证据）。NoOp 默认 = 全量扫描零回归（既有 plan 236/240 测试全绿）。RecoveryManager 协调 / 静态 team 分区 / load-aware work stealing / nop-job cluster 协调 均为显式 Non-Goals successor。
Completed: 2026-06-18

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session `ses_128675833fferb2jIXSukQsfkf`，非 implementation session）执行 read-only closure audit
- Audit Session: ses_128675833fferb2jIXSukQsfkf
- Evidence:
  - **Section 1 Phase 1 code PASS**：`IDaemonCoordinator.java`（162 行，3 方法 + CAS 真值表 Javadoc L39-53）/ `NoOpDaemonCoordinator.java`（61 行，singleton + 显式无协调语义）/ `DbDaemonCoordinator.java`（327 行，INSERT + conditional UPDATE CAS L160-226 + initSchema + isDuplicateKey portable 检测 L310-326 + 全部 SQLException 包裹 NopAiAgentException L141/187/222/249/278）/ `AiAgentDaemonCoordTable.java`（63 行，TABLE_NAME + PK TEAM_ID + DDL_CREATE_TABLE）。grep TODO/FIXME 0 命中，无空方法体。
  - **Section 2 Daemon wiring PASS**：`TeamTaskSchedulerDaemon.java` daemonCoordinator 字段 L191（默认 NoOpDaemonCoordinator.noOp()）+ get/setDaemonCoordinator L402-429 null-safe + daemonOwnerId L213 默认 "scheduler-daemon-"+UUID（显式不复用 daemonSessionId L207-212）+ DEFAULT_SCAN_LEASE_MS=30_000L L166 + scanLeaseMs L220 + scanOnce lease guard L570 tryAcquire BEFORE topology build（L589 new TeamTaskTopology）+ skippedCoordinated++/LOG.debug L571-576 + finally releaseScanLease L647 + LOG.warn on false L648-651 + 12-arg SchedulerScanResult 构造 L657-660。`SchedulerScanResult.java` skippedCoordinatedTeams L70 + getter L160 + 11-arg 向后兼容 L76-84 + 12-arg 全参 L95-115。
  - **Section 3 Test existence PASS**：TestDbDaemonCoordinator（10 tests：INSERT/blocked/renew/preempt/release 条件/isActive/多租户/并发 CAS/参数校验）/ TestNoOpDaemonCoordinator（4 tests）/ TestSchedulerDaemonCoordination（7 tests 含 RecordingCoordinator 接线断言）/ TestMultiInstanceScanCoordination（3 tests Anti-Hollow #22）/ TestSchedulerDaemonCoordinationHonestFailure（5 tests 含 DB 异常 NopAiAgentException）。全部 @Test 真实断言非空壳。
  - **Section 4 Doc artifacts PASS**：设计文档 115 行含 8 设计决策 + 6 拒绝替代方案 / roadmap §4 line 265 ✅ / task-scheduler-daemon.md L30+L64+L74 landed+successor 标注 / daily log 06-18.md plan-242 条目。
  - **Section 5 Anti-Hollow PASS**：#22 TestMultiInstanceScanCoordination.twoDbCoordinatedInstancesDoNotRedundantlyScanSameTeam 两实例 + 共享 DbDaemonCoordinator + 共享 H2 → pre-acquire 模拟 A mid-scan → B scanOnce 断言 skippedCoordinatedTeams==2 + engineB.totalExecutions==0 + teamsExecuted.isEmpty()（per-team lease 证据非最终 task 状态）+ A release 后 B 干净 handoff。#23 TestSchedulerDaemonCoordination.coordinatorAcquirePerTeamAndReleaseOnCompletion RecordingCoordinator 断言 acquireTeamIds.size()==2 + releaseTeamIds.size()==2 + ownerId/leaseMs 透传（call-count 断言非字段存在）。
  - **Section 6 Deferred honesty PASS**：ScheduledRecoveryManager 协调 + 静态 team 分区 均分类为 out-of-scope improvement + 明确 non-blocking 理由（RecoveryManager 60s 低频幂等无 agent 派发冗余成本低 / 静态分区需实例数发现+重平衡不同结果面）+ successor path 指定。非静默降级 live defect。
  - **工具验证**：`node ai-dev/tools/check-plan-checklist.mjs 242-...md --strict` 退出码 0（全 checklist 勾选 + Closure Evidence 已写入）；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0（0 high/critical 空壳发现）；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors）。
  - **测试验证**：`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS（2660 tests，0 failures，零回归：plan 241 baseline 2630 + 本次新增 29 + 1 测试数量微调）。

Follow-up:

- `ScheduledRecoveryManager` 跨进程协调 — successor plan required（60s 低频幂等，冗余成本低）。
- 静态 team 分区 / load-aware work stealing / 动态重平衡 — optimization candidate / successor plan required。
- nop-job cluster 协调集成（DB job persistence / cluster election 替代 IScheduledExecutor）— successor plan required。
- 无其他 plan-owned 剩余工作。
