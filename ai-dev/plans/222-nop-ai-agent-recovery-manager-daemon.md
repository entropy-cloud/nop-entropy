# 222 nop-ai-agent RecoveryManager 定时扫描 Daemon

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-8-P4-RecoveryDaemon

> Last Reviewed: 2026-06-16
> Source: carry-over from `ai-dev/plans/221-nop-ai-agent-cross-process-session-takeover-lock.md`（Non-Blocking Follow-ups 第一条：`nop-job 定时扫描集成（vision §6.3 第 1 步）：RecoveryManager 作为后台定时扫描 daemon（每 60s 扫描 ai_agent_session_lock + ai_agent_session 状态），依赖 nop-job 调度基础设施集成。Classification: successor plan required`）；`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md` §6.3（RecoveryManager 工作流第 1 步：定时任务，每 60 秒）+ §10 Phase 4
> Related: `221`（交付跨进程接管锁 `ISessionTakeoverLock` + `DbSessionTakeoverLock` + 引擎接线，本计划是其 #1 successor）、`218`（交付 Actor Runtime 基础原语）、`183`/`184`/`185`（L3-4b/c/d session restore 链）

## Purpose

把 nop-ai-agent 的 session 恢复从"仅启动期 `restorePendingSessions` 一次性扫描"扩展为"持续运行的 RecoveryManager 定时扫描 daemon"。本计划只负责这一件事：交付经 `IScheduledExecutor`（nop-commons，已传递可用）调度的后台扫描循环，使多实例无人值守部署中 crash 后遗留的 stale lock + orphaned session 能被持续检测并清理，而非仅依赖被动 lease TTL 过期或下次实例启动。

> **调度机制裁定**：plan 221 carry-over 原文为"nop-job 定时扫描集成"。经 Phase 1 核实，nop-job 的 `IJobScheduler`（`nop-job-api`）是重量级作业管理框架（`JobSpec` + `ITriggerSpec` + `IJobInvoker` resolver + 自有 `activate()`/`deactivate()` 生命周期），且 `nop-ai/nop-ai-agent/pom.xml` 无 nop-job 依赖。本计划改用 nop-commons 的 `IScheduledExecutor`（`scheduleWithFixedDelay(Runnable, initialDelay, delay, unit)`，已传递可用）作为调度抽象——对于"每 60s 跑一次 `scanOnce()`"的简单周期任务，`IScheduledExecutor` 足够且更轻量。nop-job 集成（含 DB-backed job persistence / cluster coordination / cron expression）是后续 successor 增强方向。

RecoveryManager 的其余能力（orphan 进程 liveness 检测、恢复模式策略 resume/retry/abort、超时强制中止、归档清理）均为显式 Non-Goal 独立 successor——它们在本 daemon 扫描循环基础上扩展。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-16）：

- **跨进程接管锁 ✅**（plan 221）：`ISessionTakeoverLock` 接口 + `NoOpSessionTakeoverLock` shipped 默认 + `DbSessionTakeoverLock` 功能实现已落地于 `io.nop.ai.agent.runtime.lock` 包。`DbSessionTakeoverLock` 基于 `ai_agent_session_lock` 表（`SESSION_ID` PK + `LOCK_OWNER` + `LOCK_ACQUIRED_AT` + `LOCK_EXPIRES_AT`），经 SQL CAS acquire + conditional DELETE release + stale-lock 抢占（`WHERE LOCK_EXPIRES_AT <= ?`）。`DefaultAgentEngine` 三个执行入口点（`doExecute`/`resumeSession`/`restoreSession`）在 `putIfAbsent` 前经 `tryAcquire` 加锁，三路径清理时 `releaseLockQuietly` 释放锁。
- **`restorePendingSessions` ✅**（plan 184）：批量 auto-restore-on-startup——`sessionStore.listAllSessions()` → 过滤 `running`/`pending` → `sessionTakeoverLock.isHeld` 跳过 → 逐个 `restoreSession` → 返回 `SessionRestoreSummary`。**此方法仅在引擎启动时被调用一次，非持续运行。**
- **RecoveryManager 零实现**：grep `RecoveryManager|ScheduledRecovery|RecoveryDaemon` 在 `nop-ai/nop-ai-agent/src/main` 返回 0 类定义命中。`AgentActorStatus.java` 的 Javadoc（约第 39、91 行）提及 `RecoveryManager` 作为 Phase 4 successor，非实现。
- **调度基础设施**：`nop-job/` 目录存在于仓库根（nop-job 是 Nop 平台的重量级定时任务调度模块，含 `IJobScheduler` / `JobSpec` / `ITriggerSpec` / `IJobInvoker`），但 `nop-ai/nop-ai-agent/pom.xml` **无 nop-job 依赖**。nop-commons 的 `IScheduledExecutor`（`scheduleWithFixedDelay` 语义）经 nop-core 等传递依赖**已可用**于 classpath。本计划采用 `IScheduledExecutor` 作为调度抽象（轻量，无需新增 Maven 依赖），nop-job 集成是后续 successor 方向。
- **引擎无生命周期方法**：`IAgentEngine` / `DefaultAgentEngine` **无** `start()` / `stop()` / `init()` / `close()` / `@PostConstruct` / `@PreDestroy` 等生命周期回调。`IAgentEngine` 的 `restorePendingSessions` Javadoc（约 `IAgentEngine.java:166-171`）明确记载生命周期管理是部署层决策而非引擎层契约（"Opt-in, not lifecycle-coupled... when to run auto-restore is a deployment-layer decision, not an engine-layer contract"）。本计划遵循此设计：不向引擎添加生命周期方法，RecoveryManager 的 `start()`/`stop()` 由集成商/部署层调用。
- **接管锁的被动过期已可用但不足够**：`DbSessionTakeoverLock.tryAcquire` 已支持 stale lock CAS 抢占（`LOCK_EXPIRES_AT <= ?`）。被动过期 = 锁持有者 crash 后，等待 TTL 到期，下次有人 `tryAcquire` 即可抢占。**但若无实例主动 `tryAcquire` 该 session**（如唯一会恢复它的实例已 crash 且无其他实例的 `restorePendingSessions` 覆盖它），stale lock + orphaned session 将无限期驻留 DB。
- **引擎无 RecoveryManager 集成点**：`DefaultAgentEngine` 当前无 RecoveryManager 字段/setter。本计划新增 `setRecoveryManager` setter（setter 注入模式，与 `setSessionTakeoverLock` / `setAuditLogger` 等一致），但**不**添加引擎生命周期方法（见 Non-Goals）。

## Goals

- **`IRecoveryManager` 接口**：`start()` / `stop()` 生命周期方法（幂等——重复 start/stop 无副作用）+ `scanOnce() → RecoveryScanResult` 手动触发单次扫描（用于测试和按需触发）。`start()` 经 `IScheduledExecutor` 注册周期任务（默认 60s fixed-delay），`stop()` 注销任务。
- **`NoOpRecoveryManager` shipped 默认**：singleton；`start`/`stop`/`scanOnce` 全 no-op；`scanOnce` 返回全零值 `RecoveryScanResult`。引擎默认使用它，**零行为回归**。
- **`ScheduledRecoveryManager` 功能实现**：经 `IScheduledExecutor` 注册周期性扫描任务。每次 `scanOnce` 执行两个具体操作：
  - (a) **stale lock cleanup**：DELETE `ai_agent_session_lock` 表中 `LOCK_EXPIRES_AT <= now` 的行（幂等清理，释放 stale lock 使对应 session 可被恢复），记录 affected rows 数
  - (b) **orphan session detection**：SELECT `ai_agent_session` 表中 `STATUS IN ('running','pending')` 且在 lock 表中无活跃锁（`LOCK_EXPIRES_AT > now`）的 session，LOG.warn 记录每个 orphan session ID（为后续恢复策略 successor 提供观测基础）
  - 返回 `RecoveryScanResult`（`staleLocksCleaned` / `orphanSessionsDetected` / `orphanSessionIds` / `scanDurationMs` / `scannedAt`）供可观测性
- **引擎 setter 注入**：`DefaultAgentEngine` 新增 `recoveryManager` 字段（默认 `NoOpRecoveryManager`）+ `setRecoveryManager` setter，使集成商可注入功能性 `ScheduledRecoveryManager`。**引擎不调用 `recoveryManager.start()`/`stop()`**——遵循 `IAgentEngine` 的"生命周期管理是部署层决策"设计契约（`IAgentEngine.java:166-171`）。集成商在部署层负责调用 `start()`（如应用启动后）和 `stop()`（如应用关闭前）。
- **`IScheduledExecutor` 调度集成**：`ScheduledRecoveryManager` 构造期接收 `IScheduledExecutor`（使测试可注入 mock 调度器），`start()` 注册周期任务，`stop()` 注销。扫描间隔可配置（默认 60s）。
- **focused 测试**：`ScheduledRecoveryManager.scanOnce` 的 stale lock cleanup / orphan detection / active-lock-preserved / terminal-session-excluded 各有覆盖。
- **端到端验证**：H2 DB + `ScheduledRecoveryManager` → 手动插入 stale lock 行 + orphan session 行 → `scanOnce` → 断言 stale lock 行被 DELETE + orphan session 被 LOG.warn 记录 + `RecoveryScanResult` 字段正确。
- **设计文档更新**：`nop-ai-agent-actor-runtime-vision.md` §6.3 标注 RecoveryManager daemon（第 1 步定时扫描 + stale lock cleanup）已落地；§10 Phase 4 标注 successor 状态变化。

## Non-Goals

- **orphan 进程 liveness 检测**（vision §6.3"检查进程 ID 是否存活"）：主动检测锁持有者进程存活状态（而非仅依赖 stale lock 过期 + orphan session 检测），需要进程注册/心跳机制。本计划只检测 stale lock + orphan session（DB 可观测信号），不检测进程存活（运行时信号）。Classification: successor plan required。
- **恢复模式策略（resume/retry/abort）**（vision §6.3 第 2 步）：恢复 orphaned session 时选择从 checkpoint 恢复、从头重试、还是标记失败是产品策略决策。本计划只检测并清理 stale lock，不裁定恢复策略（orphan session 仅 LOG.warn 被记录，不自动触发恢复）。Classification: successor plan required。
- **超时强制中止**（vision §6.3 第 3 步）：`status=running` 且超时的 session 经 `ICancelToken.cancel()` 强制中止。需要 cancel 接入 + 超时策略裁定。Classification: successor plan required。
- **归档清理**（vision §6.3 第 4 步）：terminal session 归档到历史表。Classification: optimization candidate。
- **心跳自动续约**：长时间 ReAct 执行自动续约 lease（避免被抢占）。Classification: optimization candidate（plan 221 已裁定为有意 fail-safe 设计）。
- **自动触发 `restorePendingSessions`**：daemon 检测到 orphan session 后是否自动调用 `restorePendingSessions` 是恢复策略的一部分（Non-Goal 上述第 2 条）。本计划只检测 + 清理 stale lock + LOG.warn，不自动触发恢复。Classification: successor plan required（依赖恢复策略裁定）。
- **XDSL 配置化**：`agent.xdef` 增加 `<recovery-manager>` 元素。当前通过编程 API 配置。Classification: optimization candidate。
- **多实例扫描协调**：多个实例同时运行 RecoveryManager daemon 时的扫描去重。stale lock DELETE 是幂等的（DELETE 不存在的行无副作用），但 orphan session LOG.warn 可能重复。首版接受重复 LOG.warn（可观测性噪音，非正确性问题）。Classification: optimization candidate。
- **向 `IAgentEngine` / `DefaultAgentEngine` 添加生命周期方法**（`start`/`stop`/`init`/`close`/`@PostConstruct`/`@PreDestroy`）：`IAgentEngine` 的现有设计契约明确生命周期管理是部署层决策（`IAgentEngine.java:166-171`："Opt-in, not lifecycle-coupled"）。向引擎添加生命周期方法将改变 public API 契约并影响所有集成商，超出本计划 scope。RecoveryManager 的 `start()`/`stop()` 由集成商/部署层调用。Classification: out-of-scope improvement（public API 变更，需独立 plan-first 决策）。
- **nop-job `IJobScheduler` 集成**：nop-job 是重量级作业管理框架（`JobSpec` + `ITriggerSpec` + `IJobInvoker`），适合需要 DB-backed job persistence / cluster coordination / cron expression 的场景。对于本计划的简单周期扫描（"每 60s 跑一次 `scanOnce()`"），nop-commons 的 `IScheduledExecutor` 已足够。nop-job 集成是后续 successor 增强方向。Classification: successor plan required。
- **TeamManager + ResourceGuard + Fencing Token**（vision §10 Phase 3/5）。Classification: successor plan required。

## Scope

### In Scope

- 新增 `io.nop.ai.agent.runtime.recovery` 包：`IRecoveryManager` 接口 + `NoOpRecoveryManager` 默认 + `ScheduledRecoveryManager` 功能实现 + `RecoveryScanResult` 数据对象
- `ScheduledRecoveryManager.scanOnce` 实现：stale lock cleanup（DELETE 幂等清理）+ orphan session detection（SELECT + LOG.warn）+ `RecoveryScanResult` 返回
- `IScheduledExecutor` 调度集成：`start()` 注册周期任务 + `stop()` 注销
- `DefaultAgentEngine` 新增 `recoveryManager` 字段（默认 NoOp）+ `setRecoveryManager` setter（集成商注入 + 部署层管理 start/stop 生命周期，引擎自身不调用 start/stop）
- focused 测试 + 端到端测试（H2 场景）
- 设计文档更新（`nop-ai-agent-actor-runtime-vision.md` §6.3/§10 Phase 4）

### Out Of Scope

- 见 Non-Goals（orphan liveness / 恢复策略 / 超时中止 / 归档 / 心跳续约 / 自动触发恢复 / XDSL / 多实例扫描协调 / TeamManager / ResourceGuard 均为显式 successor）

### 设计裁定

**裁定 1：scanOnce 做 stale lock cleanup + orphan detection，不做恢复策略**——每次扫描执行两个具体操作：(a) DELETE `ai_agent_session_lock` 中 `LOCK_EXPIRES_AT <= now` 的行（stale lock cleanup，幂等——DELETE 不存在的行无副作用，多实例并发清理安全）；(b) SELECT `ai_agent_session` 中 `STATUS IN ('running','pending')` 且对应 session_id 在 lock 表中无活跃锁（`LOCK_EXPIRES_AT > now`）的行，LOG.warn 记录。不做 resume/retry/abort 策略裁定（orphan session 仅被观测，不被恢复）。理由：(1) stale lock cleanup 是安全的幂等操作，不改变 session 业务状态（只清理临时锁）；(2) orphan detection 为下游恢复策略 successor 提供观测基础（当前仅 LOG.warn）；(3) 恢复策略涉及产品决策（从 checkpoint 恢复 vs 从头重试 vs 标记失败），超出本计划 scope。

**裁定 2：NoOpRecoveryManager 为 shipped 默认**——与 plan 218/221 的 `NoOpActorRuntime`/`NoOpSessionTakeoverLock` 模式一致。`start`/`stop`/`scanOnce` 全 no-op，引擎默认使用它，零行为回归。集成商显式 `engine.setRecoveryManager(new ScheduledRecoveryManager(dataSource, scheduledExecutor))` 注入后，由部署层调用 `start()` 启用。

**裁定 3：采用 `IScheduledExecutor`（nop-commons）而非 nop-job `IJobScheduler`**——`ScheduledRecoveryManager` 构造期接收 `IScheduledExecutor`（不硬编码 nop-job 具体类），使测试可注入 mock 调度器。`start()` 经 `scheduleWithFixedDelay(this::scanOnce, initialDelay, scanIntervalSec, TimeUnit.SECONDS)` 注册周期任务。理由：(1) nop-job 的 `IJobScheduler` 是重量级框架（`JobSpec` + `ITriggerSpec` + `IJobInvoker` resolver + 自有 `activate()`/`deactivate()` 生命周期），对于"每 60s 跑一次 scanOnce"的简单周期任务过度复杂；(2) `nop-ai/nop-ai-agent/pom.xml` 无 nop-job 依赖，引入需新增 Maven 依赖 + 理解 nop-job 的 job 注册流程；(3) `IScheduledExecutor` 经 nop-commons → nop-core 传递依赖**已可用**于 classpath，无需新增 Maven 依赖；(4) plan 221 carry-over 原文"nop-job 定时扫描集成"是 vision 层面的方向描述，实施层面 `IScheduledExecutor` 是更精确的集成点。nop-job 集成（DB-backed job persistence / cluster coordination / cron expression）是后续 successor 增强方向。

**裁定 4：scanOnce 作为公共方法暴露**——除经 nop-job 自动调度外，`scanOnce` 作为公共方法暴露，使测试可手动触发单次扫描（不依赖调度器时序），且支持集成商按需触发。

## Execution Plan

### Phase 1 - 契约表面 + NoOp 默认 + 调度机制裁定

Status: completed
Targets: `io.nop.ai.agent.runtime.recovery` 包（新增接口 + NoOp 默认 + 数据对象）、`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md`

- Item Types: `Decision` | `Proof`

- [x] **裁定并落档** 调度机制：采用 `IScheduledExecutor`（nop-commons，`scheduleWithFixedDelay` 语义，已传递可用）而非 nop-job `IJobScheduler`（重量级，`nop-ai/nop-ai-agent/pom.xml` 无依赖）。落档到 `nop-ai-agent-actor-runtime-vision.md` §6.3，标注 nop-job 集成为 successor 增强方向
- [x] 定义 `IRecoveryManager` 接口：`start()` / `stop()` / `scanOnce() → RecoveryScanResult`。Javadoc 明确：start/stop 生命周期语义（幂等——重复 start/stop 无副作用）、scanOnce 手动触发语义、线程安全契约
- [x] 定义 `RecoveryScanResult` 数据对象：`staleLocksCleaned`（int）+ `orphanSessionsDetected`（int）+ `orphanSessionIds`（List<String>）+ `scanDurationMs`（long）+ `scannedAt`（long）
- [x] 实现 `NoOpRecoveryManager`（singleton）：`start`/`stop`/`scanOnce` 全 no-op；`scanOnce` 返回全零值 `RecoveryScanResult`
- [x] `nop-ai-agent-actor-runtime-vision.md` §6.3 标注 RecoveryManager daemon（第 1 步定时扫描 + stale lock cleanup）已落地；§10 Phase 4 标注 successor 状态变化（orphan liveness / 恢复策略 / 超时中止 / 归档仍为 successor）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `IRecoveryManager.java` + `NoOpRecoveryManager.java` + `RecoveryScanResult.java` 文件存在于 `io.nop.ai.agent.runtime.recovery` 包
- [x] 调度机制已裁定并落档（`IScheduledExecutor` 作为调度抽象，设计文档标注裁定理由 + nop-job 为 successor 方向）
- [x] `NoOpRecoveryManager.start`/`stop`/`scanOnce` 全 no-op，`scanOnce` 返回全零值 `RecoveryScanResult`
- [x] **No new test required for Phase 1**：Phase 1 只交付契约表面（接口 + NoOp 默认 + 数据对象），无行为逻辑需测试；NoOp 行为验证在 Phase 2 focused 测试中覆盖（Minimum Rules #25）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - ScheduledRecoveryManager 功能实现 + 调度集成 + 引擎 setter + 测试

Status: completed
Targets: `io.nop.ai.agent.runtime.recovery.ScheduledRecoveryManager`、`DefaultAgentEngine`（`recoveryManager` 字段 + `setRecoveryManager` setter）、`io.nop.ai.agent.runtime.recovery` 测试

- Item Types: `Proof`

- [x] 实现 `ScheduledRecoveryManager.scanOnce`：
  - (a) stale lock cleanup：DELETE FROM `ai_agent_session_lock` WHERE `LOCK_EXPIRES_AT <= ?`（now 参数），记录 affected rows 数为 `staleLocksCleaned`
  - (b) orphan session detection：SELECT `s.SESSION_ID` FROM `ai_agent_session` s WHERE `s.STATUS IN ('running','pending')` AND NOT EXISTS (SELECT 1 FROM `ai_agent_session_lock` l WHERE `l.SESSION_ID = s.SESSION_ID` AND `l.LOCK_EXPIRES_AT > ?`)，对每个结果行 LOG.warn 记录 session ID
  - (c) 组装并返回 `RecoveryScanResult`（含 `staleLocksCleaned` / `orphanSessionsDetected` / `orphanSessionIds` / `scanDurationMs` / `scannedAt`）
- [x] 实现 `ScheduledRecoveryManager.start` / `stop`：经 `IScheduledExecutor.scheduleWithFixedDelay(this::scanOnce, initialDelay, scanIntervalSec, TimeUnit.SECONDS)` 注册周期任务（默认 60s fixed-delay），记录 `ScheduledFuture` 句柄以支持 stop（`cancel(false)`）；start/stop 幂等（重复调用无副作用）
- [x] `DefaultAgentEngine` 新增 `recoveryManager` 字段（默认 `NoOpRecoveryManager`）+ `setRecoveryManager` setter。**引擎不调用 `recoveryManager.start()`/`stop()`**——遵循 `IAgentEngine` 的部署层生命周期管理设计契约。集成商经 `engine.setRecoveryManager(mgr)` 注入后在部署层自行调用 `mgr.start()` / `mgr.stop()`
- [x] 编写 `ScheduledRecoveryManager.scanOnce` focused 测试：stale lock cleanup（插入过期锁行 → scanOnce → 断言行被 DELETE + `staleLocksCleaned` 计数正确）/ orphan detection（插入 running session 无活跃锁 → scanOnce → 断言 `orphanSessionsDetected` = 1 + LOG.warn 被调用）/ 活跃锁不被清理（插入未过期锁 → scanOnce → 断言行仍在 + `staleLocksCleaned` = 0）/ terminal session 不被检测为 orphan（completed session → scanOnce → `orphanSessionsDetected` = 0）
- [x] 编写 NoOp 默认零回归测试：`NoOpRecoveryManager` 下既有引擎测试全部通过（shipped 默认不改变行为）
- [x] 编写 `start`/`stop` 调度测试：配置 `ScheduledRecoveryManager`（H2 DataSource + mock `IScheduledExecutor`）→ `start()` → 断言 `scheduleWithFixedDelay` 被调用（参数：scanOnce Runnable + 60s delay）→ `stop()` → 断言 `ScheduledFuture.cancel` 被调用
- [x] 编写 setter 注入测试：`new DefaultAgentEngine(...)` 默认 `recoveryManager` 为 `NoOpRecoveryManager` 实例 → `engine.setRecoveryManager(mgr)` → 断言 `engine.getRecoveryManager() == mgr`
- [x] 编写端到端测试：H2 DB + `ScheduledRecoveryManager` → 手动插入 stale lock 行（`LOCK_EXPIRES_AT` 设为过去时间）+ orphan session 行（`STATUS='running'`，无锁行）→ `scanOnce` → 断言 stale lock 行被 DELETE + orphan session 被 LOG.warn 记录 + `RecoveryScanResult` 各字段正确

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ScheduledRecoveryManager.scanOnce` 正确实现 stale lock cleanup（DELETE 幂等）+ orphan session detection（SELECT + LOG.warn）
- [x] `ScheduledRecoveryManager.start` 经 `IScheduledExecutor.scheduleWithFixedDelay` 注册周期任务，`stop` 经 `ScheduledFuture.cancel` 注销任务
- [x] **接线验证**（Minimum Rules #23）：`DefaultAgentEngine.setRecoveryManager` setter 存在且接受 `IRecoveryManager`；默认值为 `NoOpRecoveryManager` 实例（断言 `engine` 的 `recoveryManager` 字段 instanceof `NoOpRecoveryManager`）；注入 `ScheduledRecoveryManager` 后 `engine` 持有该实例（断言 setter 正确赋值）
- [x] **端到端验证**（Minimum Rules #22）：H2 DB + stale lock + orphan session → scanOnce → stale lock 行被 DELETE + orphan session 被 LOG.warn + `RecoveryScanResult` 正确
- [x] **无静默跳过**（Minimum Rules #24）：`scanOnce` 中 orphan session 被 LOG.warn（非静默忽略）；stale lock DELETE 失败时抛异常（非吞没）；NoOp `scanOnce` 返回全零值（显式 "no recovery scanning" 语义，非静默跳过）
- [x] shipped 默认（`NoOpRecoveryManager`）下既有测试零回归
- [x] 新增功能各有对应 focused 测试覆盖（stale-lock-cleanup / orphan-detection / active-lock-preserved / terminal-session-excluded / start-stop-scheduling / setter-injection / E2E 各有测试）
- [x] `nop-ai-agent-actor-runtime-vision.md` §6.3/§10 Phase 4 已更新（标注 daemon 已落地 + successor 状态变化）
- [x] roadmap §4 Layer 4 验收标准已更新（如适用）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `IRecoveryManager` 契约表面（接口 + NoOp 默认 + 数据对象）已落地
- [x] `ScheduledRecoveryManager` 功能实现（stale lock cleanup + orphan detection + `IScheduledExecutor` 调度）已落地
- [x] `DefaultAgentEngine` `recoveryManager` 字段 + `setRecoveryManager` setter 已落地（引擎不调用 start/stop，遵循部署层生命周期管理设计契约）
- [x] 端到端：H2 + stale lock + orphan session → scanOnce → cleanup + detection 完整路径跑通
- [x] shipped 默认（NoOp）下既有测试零回归
- [x] 必要 focused verification 已完成（stale-lock-cleanup / orphan-detection / active-lock-preserved / terminal-session-excluded / start-stop-scheduling / setter-injection / E2E 各有测试）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（orphan liveness / 恢复策略 / 超时中止 / 归档 / 心跳续约 / 自动触发恢复 / XDSL / 多实例扫描协调 / 引擎生命周期方法 / nop-job IJobScheduler 集成 / TeamManager / ResourceGuard 均显式在 Non-Goals 切出）
- [x] 受影响 owner docs（`nop-ai-agent-actor-runtime-vision.md` + roadmap §4）已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`scanOnce` 在运行时被 `IScheduledExecutor` 调度器或手动调用执行（不只类型存在），（b）stale lock cleanup + orphan detection 路径在运行时确实执行（不只方法存在），（c）无空方法体/静默跳过作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；orphan liveness / 恢复策略 / 超时中止 / 归档 / 心跳续约 / 自动触发恢复 / XDSL / 多实例扫描协调 / 引擎生命周期方法 / nop-job IJobScheduler 集成 / TeamManager / ResourceGuard 均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **orphan 进程 liveness 检测**（vision §6.3"检查进程 ID 是否存活"）：主动检测锁持有者进程存活状态。Classification: successor plan required。
- **恢复模式策略（resume/retry/abort）**（vision §6.3 第 2 步）：恢复 orphaned session 时裁定恢复策略。Classification: successor plan required。
- **超时强制中止**（vision §6.3 第 3 步）：超时 session 经 cancel 强制中止。Classification: successor plan required。
- **归档清理**（vision §6.3 第 4 步）：terminal session 归档。Classification: optimization candidate。
- **心跳自动续约**：长时间执行自动续约 lease。Classification: optimization candidate。
- **自动触发 `restorePendingSessions`**：daemon 检测到 orphan session 后自动触发恢复。Classification: successor plan required（依赖恢复策略裁定）。
- **XDSL 配置化**：`agent.xdef` 增加 `<recovery-manager>` 元素。Classification: optimization candidate。
- **多实例扫描协调**：多实例同时运行 daemon 时的扫描去重。Classification: optimization candidate。
- **TeamManager + ResourceGuard + Fencing Token**（vision §10 Phase 3/5）。Classification: successor plan required。

## Closure

Status Note: RecoveryManager 定时扫描 daemon 全量交付——`IRecoveryManager` 契约（`start`/`stop` 幂等 + `scanOnce → RecoveryScanResult`）+ `NoOpRecoveryManager` shipped 默认（零回归）+ `ScheduledRecoveryManager` 功能实现（`IScheduledExecutor` 周期调度默认 60s，`scanOnce` = stale lock cleanup 幂等 DELETE + orphan session detection LOG.warn）+ `DefaultAgentEngine` `recoveryManager` 字段 + `setRecoveryManager` setter（部署层管理 start/stop）。所有 in-scope 项落地为真实（非空壳）代码，21 个新增测试（17 + 4）经真实 H2 DB 验证，既有 2104 测试零回归。其余 RecoveryManager 能力（orphan liveness / 恢复策略 / 超时中止 / 归档 / 心跳续约 / 自动触发恢复 / XDSL / 多实例扫描协调 / 引擎生命周期方法 / nop-job IJobScheduler 集成 / TeamManager / ResourceGuard）均为显式 Non-Goals 独立 successor，无 in-scope live defect 被静默降级。
Completed: 2026-06-17

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent（general agent, task_id `ses_12ec956b3ffeFw1Mw9tF0tzlX1`，fresh session，非实现 session）
- Audit Session: `ses_12ec956b3ffeFw1Mw9tF0tzlX1`
- Evidence:
  - **Exit Criteria 验证**（逐条 PASS）：
    - Phase 1：`IRecoveryManager.java:51,59,66,80` + `RecoveryScanResult.java:42-46`（5 字段）+ `NoOpRecoveryManager.java:31-37`（singleton，`scanOnce` 返回 `empty()` 非 null）—— PASS
    - Phase 2 scanOnce 非空壳：`ScheduledRecoveryManager.java:216-227`（DELETE `LOCK_EXPIRES_AT <= ?` + affected rows 计数）+ `:235-257`（SELECT `STATUS IN ('running','pending')` NOT EXISTS + 逐个 LOG.warn `:196-197`）+ `:201-206` 组装 `RecoveryScanResult`；SQLException 包裹 `NopAiAgentException`（`:224-225` / `:253-254`，非吞没）—— PASS
    - Phase 2 start/stop 接线：`:144-145` `scheduleWithFixedDelay(this::scanOnceSafe,...)` + 存储 handle；`:155` `cancel(false)`；`:84` `volatile Future<?>` + `:139,150` `synchronized` + null guard 幂等 —— PASS
    - Phase 2 引擎接线：`DefaultAgentEngine.java:318` 字段默认 `NoOpRecoveryManager.noOp()` + `:1238-1242` setter null-safe + `:1248-1250` getter；引擎不调用 start/stop（grep 确认）—— PASS
    - 端到端验证（#22）：`TestScheduledRecoveryManager.endToEndStaleLockPlusOrphan:288-315` 真实 H2 DB 断言 `staleLocksCleaned=1` / `orphanSessionsDetected=1` / 存活锁行数 / orphan id —— PASS
    - 接线验证（#23）：`scheduledTaskRunsScanOnce:405-422` 取 `start()` 真实注册到 `IScheduledExecutor` 的 Runnable 调 `run()` → 断言 stale lock 行被 DELETE（真实 H2）；`TestRecoveryManagerEngineWiring:113` 默认 instanceof NoOp + `:126` setter 注入 —— PASS
    - 无静默跳过（#24）：orphan LOG.warn（非静默）+ SQL 失败抛 `NopAiAgentException`（非吞没）+ NoOp `scanOnce` 返回全零值（显式语义）—— PASS
    - shipped 默认零回归：`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → 2104 tests, 0 failures —— PASS
  - **Closure Gates 验证**（逐条 PASS）：契约表面 / 功能实现 / 引擎 setter / 端到端 / 零回归 / focused verification（7 类各有测试）/ 无静默降级 / owner docs 同步 / 独立 audit / Anti-Hollow / compile / test / checkstyle —— 全 PASS
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/222-nop-ai-agent-recovery-manager-daemon.md --strict` → 退出码 0（"All plans passed checklist verification"）
  - **Anti-Hollow 检查结果**：`scanOnce` 调度链在运行时确实连通——`scheduledTaskRunsScanOnce` 验证 `start()` 注册的 Runnable 被 `run()` 后真实执行 stale-lock DELETE（H2 行数 0）；`endToEndStaleLockPlusOrphan` 验证 cleanup + detection 双路径运行时执行；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high` → 退出码 0，新 `recovery/` 包零 high 发现（18 个 high 均为既有无关文件）
  - **Deferred 项分类检查**：Non-Goals 12 项（orphan liveness / 恢复策略 / 超时中止 / 归档 / 心跳续约 / 自动触发恢复 / XDSL / 多实例扫描协调 / 引擎生命周期方法 / nop-job IJobScheduler 集成 / TeamManager / ResourceGuard）经 grep 确认 `recovery/` 包无任何实现命中（仅 Javadoc 文本提及）—— 无 in-scope live defect 被降级
  - **Owner-doc drift 检查**：`nop-ai-agent-actor-runtime-vision.md` §6.3（`:315,318,320,323`）+ §10 Phase 4（`:435`）/ `nop-ai-agent-roadmap.md` §4（`:253`）/ `01-architecture-baseline.md`（`:160`）/ `nop-ai-agent-reliability.md`（`:86`）均标注 plan 222 定时扫描 daemon 已落地 —— 无 drift

Follow-up:

- successor plans required（均为显式 Non-Goals，非本计划 debt）：orphan 进程 liveness 检测 / 恢复模式策略 resume-retry-abort / 超时强制中止 / 自动触发 `restorePendingSessions`（依赖恢复策略裁定）/ nop-job `IJobScheduler` 集成 / TeamManager + ResourceGuard + Fencing Token
- optimization candidates：归档清理 / 心跳自动续约 / XDSL 配置化 / 多实例扫描协调
- 无剩余 plan-owned work

## Follow-up handled by 226-nop-ai-agent-orphan-recovery-strategy.md

恢复模式策略 resume-retry-abort（Non-Blocking Follow-ups 第二条，标 `successor plan required`）已由 successor plan `ai-dev/plans/226-nop-ai-agent-orphan-recovery-strategy.md` 接管：交付 `RecoveryMode` 枚举 + `IOrphanRecoveryHandler` 接口 + `NoOpOrphanRecoveryHandler` shipped 默认（SKIP = LOG.warn 零回归）+ `DefaultOrphanRecoveryHandler` 功能实现（RESUME → `engine.restoreSession` fire-and-forget / ABORT → raw JDBC UPDATE status=failed / SKIP → LOG.warn）+ `ScheduledRecoveryManager` scanOnce 集成（detection 后调 handler）+ `DefaultAgentEngine` `setOrphanRecoveryHandler` setter。此段为事实性交叉引用追加，不修改本计划已关闭的 closure 内容。
