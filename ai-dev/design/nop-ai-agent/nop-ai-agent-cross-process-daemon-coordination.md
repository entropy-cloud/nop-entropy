# 跨进程 Daemon 扫描协调设计（team 级 scan lease：多实例 daemon 降冗余扫描优化层）

> Status: landed
> Last Reviewed: 2026-06-18
> Owner plan: `ai-dev/plans/242-nop-ai-agent-cross-process-daemon-coordination.md`（Work Item: `L4-cross-process-daemon-coordination`）
> Related: `nop-ai-agent-task-scheduler-daemon.md`（plan 236 交付 `TeamTaskSchedulerDaemon`——本设计为其接线 team 级 scan lease 协调）、`nop-ai-agent-actor-runtime-vision.md` §6.3 / §10 Phase 4（`DbSessionTakeoverLock` lease-table CAS 范式——本设计镜像其模式）、`nop-ai-agent-async-team-task-orchestration.md`（plan 241 切出本 cross-process 半部 successor）

## 1. 定位

把 nop-ai-agent 的多实例 daemon 部署从"**N 个实例无协调地重复扫描全部团队**——每个 `TeamTaskSchedulerDaemon` 实例每 5s 扫描所有 active team，`claimTask` CAS 保证同一 task 不被 double-dispatch（正确性地板已存在），但 N 个实例全部执行相同的 DB 读 + topology build + ready query，冗余扫描负载随实例数线性增长"扩展为"**team 级 scan lease 协调**：daemon 在扫描每个 team 前获取该 team 的短时 scan lease，若另一实例持有该 team 的活跃 lease 则跳过该 team（降冗余扫描），N 个实例自然分担不同 team 的扫描+派发工作"。

**关键定位（roadmap §4 Layer 4 + plan 241 设计裁定 4）**：cross-process daemon 协调是**降冗余扫描的优化层**，非正确性前置。`claimTask` DB 级 CAS（affected-row-count==1，plan 227/240）已提供多实例 double-dispatch 正确性地板——多个 daemon 实例并发 claim 同一 task 只有 1 个胜出。本设计交付的是在此正确性地板之上的**扫描负载优化**：减少冗余 DB 读 + topology build + claim CAS 竞争。

## 2. 设计决策

### 决策 1：机制 = DB lease-table（镜像 `DbSessionTakeoverLock` 范式），非 DB advisory lock / fencing token

独立表 `ai_agent_daemon_coord`（PK `TEAM_ID` + `OWNER_ID` + `ACQUIRED_AT` + `EXPIRES_AT` + `TENANT_ID`），INSERT 乐观 + duplicate-key conditional UPDATE CAS。理由：
- `DbSessionTakeoverLock`（plan 221）已证明此范式在本 codebase 可行且 portable（标准 SQL，无 vendor-specific advisory lock）。
- fencing token（plan 235）是 write-ordering 原语（防 stale writer 覆盖最新状态），非 scan-coordination 原语（协调谁扫哪个 team）。
- 独立表（非 `ai_agent_session_lock` 扩展）因为不同域（scan coordination vs session takeover）+ 不同 key space（teamId vs sessionId）+ 不同 lease 语义（scan lease = scan 周期级数十秒，session lease = 30min crash recovery 窗口）。

### 决策 2：协调粒度 = per-team scan lease，非 per-scan-cycle leader election / 全局 scan lock

per-team lease 使 N 个实例自然分担不同 team 的扫描+派发（一个实例扫 team A 时另一实例 gravitate 到 team B），非"一个 leader 扫全部"的单点负载。理由：
- per-scan-cycle leader 选举使 leader 实例承担全部扫描+派发负载，N-1 个实例空闲——对 SchedulerDaemon（每 5s 派发 agent 执行）是反负载分布。
- per-team lease 更细粒度，team 多时协调效果更明显（实例自然 gravitate 到未被扫描的 team）。
- 全局 scan lock 会序列化整个扫描周期，丧失并行性，且单 lock 竞争成为瓶颈。

### 决策 3：`IDaemonCoordinator` 是独立新接口，非复用 `ISessionTakeoverLock`

理由：
- 不同域（scan coordination vs session takeover）。
- 不同 key space（teamId vs sessionId）。
- 允许独立演进（future partitioning / work stealing）不污染 session lock 契约。
- 语义对称但命名清晰隔离（`tryAcquireScanLease` / `releaseScanLease` / `isScanLeaseActive` vs `tryAcquire` / `release` / `isHeld`）。

### 决策 4：只接线 `TeamTaskSchedulerDaemon`，不接线 `ScheduledRecoveryManager`

SchedulerDaemon 5s 高频 + agent 派发（昂贵），冗余成本最高；RecoveryManager 60s 低频 + 纯幂等检测/清理（无 agent 派发，DELETE absent rows = no-op），冗余成本低。不同结果面可独立收口（RecoveryManager 协调为显式 successor）。本设计结果面（SchedulerDaemon 降冗余扫描）的成立不依赖 RecoveryManager 协调。

**已知限制（诚实记录）**：RecoveryManager 仍无协调（60s 全量冗余扫描），为显式 successor（见 §5）。

### 决策 5：`NoOpDaemonCoordinator.tryAcquireScanLease` 恒返回 `true`（永不阻止扫描 = 单实例行为不变）

零回归硬约束——NoOp 接线后 daemon 行为与无协调器逐行一致（全量扫描全部 team）。显式"无跨进程协调"语义（Minimum Rules #24），非静默跳过。与 `NoOpSessionTakeoverLock`（session 域）/ `NoOpResourceGuard`（quota 域）shipped 默认范式一致。

注意 `releaseScanLease` 恒返回 `false`（与 `NoOpSessionTakeoverLock.release` 返回 `true` 不同）：scan lease no-op release 诚实报告"无 lease 可释放"（lease 从未记录），而非 takeover lock 的"consider it released"acknowledgement。daemon 对 `false` 返回 LOG.warn（防御性）并继续——scan 结果不受影响。

### 决策 6：正确性地板不受协调层影响

即使 `IDaemonCoordinator` 完全失效（lease 表删除 / 全部 fail / NoOp wired），`claimTask` CAS 仍保证无 double-dispatch。协调层只影响扫描负载（冗余 vs 分担），不影响正确性。roadmap §4 Layer 4 + plan 241 设计裁定 4 明确定位 cross-process 为优化层非正确性前置。

### 决策 7：`daemonOwnerId` 默认 `"scheduler-daemon-"+UUID`，不复用 `daemonSessionId`

`daemonSessionId`（`TeamTaskSchedulerDaemon.DEFAULT_DAEMON_SESSION_ID = "team-task-scheduler-daemon"`）是全部实例共享的固定值，用于 task `claimedBy`/`completedBy` 审计标记。若复用则所有实例 ownerId 相同，`tryAcquireScanLease` 全部命中"同 owner renew"路径，lease 永不阻止任何人，协调完全失效（hollow）。

`daemonOwnerId` 必须每实例唯一：默认 `"scheduler-daemon-"+UUID.randomUUID()`（类比 `DefaultAgentEngine.instanceId` UUID 唯一标识范式，加前缀提升可观测性），可经构造器/setter 覆盖支持测试确定性（固定 ownerId）+ 部署自定义（hostname/Pod 名）。

### 决策 8：`scanLeaseMs` = `scanIntervalSec * 1000 * 6`（默认 `DEFAULT_SCAN_LEASE_MS = 30_000L`）

lease 必须显著大于单次 scan 周期（`scanIntervalSec * 1000`），保证持有 lease 的实例在当前周期内完成该 team 的扫描+派发（topology build + claim CAS + agent execute join + complete）不被另一实例中途抢占。6× scan interval 给 worst-case 单 team 扫描（含一次同步 agent 执行 join）留充裕窗口，同时保持 failover 敏捷度（实例崩溃后 lease 在 30s 内过期，另一实例下周期抢占）。经 setter 可调，部署按 team 规模 / agent 执行耗时 tune（大 team / 慢 agent → 增大 leaseMs）。

scanOnce 扫描完成后 `releaseScanLease` 主动释放（非仅依赖 TTL 过期），正常路径无 lease 残留。

**已知边界（诚实记录）**：若单 team 扫描（含 agent execute join）实际耗时超过 `scanLeaseMs`，lease 中途过期，另一实例可抢占 → 两实例短暂并发扫描同一 team（降冗余优化减弱，非正确性问题——claimTask CAS 仍保证无 double-dispatch）。

## 3. CAS 真值表（tryAcquireScanLease）

| 前置状态 | 当前请求 | 结果 |
|---------|---------|------|
| 无前 lease（表无该 teamId 行） | 任意 owner | INSERT 成功 → `true` |
| 前 lease 持有者 = 当前 owner（未过期） | 同 owner | conditional UPDATE `OWNER_ID=?` 命中 → `true`（renew） |
| 前 lease 已过期（`EXPIRES_AT <= now`） | 任意 owner | conditional UPDATE `EXPIRES_AT <= ?` 命中 → `true`（preempt） |
| 前 lease 持有者 ≠ 当前 owner 且未过期 | 异 owner | conditional UPDATE 两谓词均不命中 → 0 rows → `false`（协调信号，非异常） |

## 4. 失败语义（No Silent No-Op #24）

- **lease 获取失败** = 跳过该 team + `skippedCoordinatedTeams` 计数 + LOG.debug（显式协调信号，非静默跳过）。
- **DB 异常** = `NopAiAgentException` 传播（不吞异常）。
- **NoOp** = 恒获取成功（显式无协调语义）。
- **`releaseScanLease` 返回 false**（lease 已被抢占/过期）= LOG.warn，不影响 scan 结果（scan 已执行，claimTask CAS 是正确性地板）。
- **正确性地板**：协调完全失效时 claimTask CAS 仍保证无 double-dispatch。

## 5. 拒绝的替代方案

| 被拒绝方案 | 理由 |
|-----------|------|
| DB advisory lock（vendor-specific，如 Postgres `pg_advisory_lock`） | vendor-specific 不可 portable；本设计 lease-table 用标准 SQL（INSERT + conditional UPDATE），H2/Postgres/MySQL/Oracle 均支持。镜像已落地的 `DbSessionTakeoverLock` 范式。 |
| fencing token（plan 235） | fencing token 是 write-ordering 原语（防 stale writer 覆盖最新状态，单调计数器 + 高水位校验），非 scan-coordination 原语。不同问题域。 |
| per-scan-cycle leader election（每周期选一个 leader 扫全部） | leader 实例承担全部扫描+派发负载，N-1 实例空闲——反负载分布。对 SchedulerDaemon（5s 派发 agent）尤其不合理。 |
| 复用 `ISessionTakeoverLock`（扩其契约加 teamId key） | 域混淆（scan coordination vs session takeover）+ key space 混淆（teamId vs sessionId）+ lease 语义混淆（数十秒 scan lease vs 30min session lease）。污染既有契约。决策 3。 |
| 静态 team 分区（hash-based partitioning，`hash(teamId) % instanceCount`） | 需实例数发现 + 实例增减时重平衡，是不同结果面。lease-based 动态协调更简单（实例自然 gravitate 到未被扫描的 team）。切出 successor。 |
| 接线 `ScheduledRecoveryManager` 协调 | 60s 低频 + 纯幂等检测/清理（无 agent 派发），冗余成本远低于 SchedulerDaemon。不同结果面，切出 successor（决策 4）。 |

## 6. 边界（Non-Goals，均为独立 successor）

- **`ScheduledRecoveryManager` 跨进程协调** — successor plan required（60s 低频幂等，冗余成本低）。
- **静态 team 分区（hash-based partitioning）** — successor plan required（需实例数发现 + 重平衡）。
- **load-aware work stealing / 动态重平衡** — optimization candidate（基于实例负载动态迁移 team 归属）。
- **per-task 跨进程协调** — out-of-scope（`claimTask` CAS 已提供 per-task 正确性地板，无需变更）。
- **nop-job cluster 协调集成**（DB job persistence / cluster election 替代 `IScheduledExecutor`）— successor plan required（独立基础设施层）。
- **修改 `ITeamTaskSchedulerDaemon` / `ITeamManager` / `ITeamTaskStore` / `IAgentEngine` 契约** — out-of-scope（消费原样契约，仅在 daemon 内部新增 coordinator 字段 + scanOnce guard）。
- **修改 `ISessionTakeoverLock` 契约** — out-of-scope（本设计新建独立 `IDaemonCoordinator`，决策 3）。

## 7. 落地证据

- 协调原语组件：`io.nop.ai.agent.runtime.coordination` 包（`IDaemonCoordinator` team 级 scan lease 契约 + `NoOpDaemonCoordinator` shipped 默认零回归 + `DbDaemonCoordinator` DB 功能实现 + `AiAgentDaemonCoordTable` DDL/列常量）。
- DB lease-table 范式镜像 plan 221 `DbSessionTakeoverLock`：INSERT 乐观 + duplicate-key conditional UPDATE CAS（portable `isDuplicateKey` 检测 SQLState 23505 / MySQL 1062 / `SQLIntegrityConstraintViolationException`）+ 条件 DELETE release + SELECT COUNT(*) isScanLeaseActive + 构造期 `initSchema` 自动建 `ai_agent_daemon_coord` 表。
- 多租户守卫：`ITenantResolver` opt-in 默认 `NullTenantResolver`（null tenant = SQL byte-identical 零回归）；非空 tenant 注入 `TenantSql.whereTenant` WHERE + INSERT 写入 TENANT_ID。
- `TeamTaskSchedulerDaemon` 接线：`daemonCoordinator` 字段（默认 `NoOpDaemonCoordinator.noOp()`）+ `setDaemonCoordinator`/`getDaemonCoordinator`（null-safe 回退 NoOp）+ `daemonOwnerId` 字段（默认 `"scheduler-daemon-"+UUID`，决策 7 不复用 `daemonSessionId`）+ `scanLeaseMs` 字段（默认 `DEFAULT_SCAN_LEASE_MS = 30_000L`，决策 8）+ scanOnce 逐 team lease guard（acquire→扫描→finally release / acquire fail→`skippedCoordinatedTeams++`+LOG.debug）+ `SchedulerScanResult` 扩展 `skippedCoordinatedTeams` 计数。
- 端到端降冗余验证（Anti-Hollow #22）：两实例（不同 ownerId）+ 同一 `DbDaemonCoordinator`（同一 H2 DataSource）+ 同一 team 集合 → 同一 scan 周期内不重复扫描同一 team（per-team lease 获取/跳过计数可观测证据，非仅最终 task 状态）。
- 接线验证（#23）：scanOnce 运行时确实调用 `tryAcquireScanLease`/`releaseScanLease`（spy/mock 断言，非仅字段存在）。
- 正确性地板零回归：协调完全失效时（NoOp / lease 表空）claimTask CAS 仍保证无 double-dispatch（既有 plan 236/240 测试零回归）。
- 全量回归：`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿。
