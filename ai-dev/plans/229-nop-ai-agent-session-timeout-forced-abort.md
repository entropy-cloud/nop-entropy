# 229 nop-ai-agent Session 超时强制中止（RecoveryManager 第 3 步）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-8-P4-TimeoutAbort

> Last Reviewed: 2026-06-17
> Source: carry-over from `ai-dev/plans/226-nop-ai-agent-orphan-recovery-strategy.md`（Non-Blocking Follow-ups：`超时强制中止（vision §6.3 第 3 步）：超时 session 经 cancel 强制中止。Classification: successor plan required`）+ `ai-dev/plans/222-nop-ai-agent-recovery-manager-daemon.md`（Non-Blocking Follow-ups：`超时强制中止（vision §6.3 第 3 步）：status=running 且超时的 session 经 ICancelToken.cancel() 强制中止。需要 cancel 接入 + 超时策略裁定。Classification: successor plan required`）；`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md` §6.3（RecoveryManager 工作流第 3 步：处理超时 Actor）+ §6.1（恢复策略表：Agent 循环超过最大迭代 → 强制中止）
> Related: `222`（交付定时扫描 daemon，本计划在其 scanOnce 上新增超时检测步骤）、`226`（交付恢复模式策略 + IOrphanRecoveryHandler 可插拔模式，本计划镜像其设计模式：可插拔 handler + NoOp shipped 默认 + 功能实现 + daemon 集成 + RecoveryScanResult 扩展）、`221`（交付跨进程接管锁 ISessionTakeoverLock + isHeld，本计划的"本地 / 孤儿 / 远端"三分裁定依赖锁持有状态）、`197`（交付 cancelSession 两级取消语义 + CancelHandle 预注册 + forced 线程中断，本地超时 session 经此路径取消）

## Purpose

把 nop-ai-agent 的超时处理从"RecoveryManager daemon 仅处理孤儿 session（无活跃锁的 running/pending），对仍持有活跃锁但运行时间超过上限的 session 无任何机制——一个卡死/挂起的 session（如 LLM 调用永久阻塞、工具死循环、逻辑死锁）会无限期占用资源且永不进入 orphan 检测的视野"扩展为"RecoveryManager daemon 的 `scanOnce` 在 stale-lock cleanup 之后、orphan detection 之前，增加一步超时检测：对 `STATUS IN ('running','pending')` 且活动时间戳超过配置的 wall-clock 上限的 session，经可插拔 `ISessionTimeoutHandler` 裁定动作"。本计划只负责这一件事：闭合 vision §6.3 RecoveryManager 工作流第 3 步（超时强制中止），使无人值守多实例部署中卡死/挂起的 session 能被自动取消或强制标记失败，而非无限期占用执行槽。

## Current Baseline

基于 live repo 核对（来源：plan 222 / plan 226 closure audit evidence，均已对照 live code path 验证）：

- **RecoveryManager 定时扫描 daemon ✅**（plan 222）：`IRecoveryManager` 接口（`start`/`stop` 幂等 + `scanOnce → RecoveryScanResult`）+ `NoOpRecoveryManager` shipped 默认 + `ScheduledRecoveryManager` 功能实现（`IScheduledExecutor` 周期调度默认 60s）位于 `io.nop.ai.agent.runtime.recovery` 包。`scanOnce` 当前执行：stale lock cleanup（DELETE `ai_agent_session_lock` WHERE `LOCK_EXPIRES_AT <= now`）→ orphan session detection（SELECT `ai_agent_session` 中 `STATUS IN ('running','pending')` 且无活跃锁的 session → LOG.warn）。**当前 scanOnce 无超时检测步骤。**
- **orphan 恢复模式策略 ✅**（plan 226）：`IOrphanRecoveryHandler` 接口（`handleOrphan(sessionId) → RecoveryOutcome`）+ `NoOpOrphanRecoveryHandler` shipped 默认（SKIP，零回归）+ `DefaultOrphanRecoveryHandler`（RESUME/ABORT/SKIP）+ `ScheduledRecoveryManager` `setOrphanRecoveryHandler` setter + scanOnce orphan handler 集成。**orphan handler 仅处理无活跃锁的 session；持有活跃锁（即使是远端实例 / 本实例）的 running session 不在 orphan 路径内。** `RecoveryScanResult` 当前为 6-arg 构造器（`staleLocksCleaned` / `orphanSessionsDetected` / `orphanSessionIds` / `scanDurationMs` / `scannedAt` / `recoveryActions: List<RecoveryOutcome>`）+ `empty()` 工厂。
- **跨进程接管锁 ✅**（plan 221）：`ISessionTakeoverLock`（`tryAcquire`/`release`/`isHeld`/`tryRenew`，CAS 语义 + lease/TTL）+ `DbSessionTakeoverLock`（`ai_agent_session_lock` 表：`SESSION_ID` / `LOCK_OWNER` / `LOCK_ACQUIRED_AT` / `LOCK_EXPIRES_AT`）+ `DefaultAgentEngine` 三入口点接线 + `instanceId` 字段。**本计划"本地 / 孤儿 / 远端"三分裁定依赖锁持有者与 `instanceId` 的比较。**
- **cancelSession 两级取消 ✅**（plan 197 / `02-execution-model.md` §取消语义）：`IAgentEngine.cancelSession(sessionId, reason, forced)` 实现 graceful（完成当前 tool 后停止）+ forced（在 graceful 基础上 interrupt 执行线程）两级语义；cancel 信号经 `AgentExecutionContext.cancelRequested`（volatile）传递；终态置 `cancelled`/`forced_stopped`；对不存在的 sessionId fail-fast 抛 `NopAiAgentException`。**本地活跃 session 的超时取消经此路径。**
- **`AgentExecStatus` 枚举**：`pending` / `running` / `completed` / `failed` / `cancelled` / `forced_stopped` / `escalated` / `paused`。超时强制中止的目标终态为 `failed`（孤儿/远端强制标记）或 `cancelled`/`forced_stopped`（本地经 cancelSession）。
- **`AiAgentSessionTable` 常量类**：模块内 DB 持久化（`DbSessionTakeoverLock` / `ScheduledRecoveryManager` / `DefaultOrphanRecoveryHandler`）统一经此常量类引用 `ai_agent_session` 表名与列名（如 STATUS）。raw JDBC + 常量类是模块约定，无 ORM entity 加载路径。
- **零超时处理代码**：grep `ISessionTimeoutHandler|SessionTimeoutHandler|TimeoutOutcome|handleTimeout|超时强制中止` 在 `nop-ai/nop-ai-agent/src/main` 返回 0 类定义命中。vision §6.3 工作流第 3 步显式标注 `successor：超时强制中止`。

## Goals

- **`ISessionTimeoutHandler` 接口**：`handleTimeout(String sessionId) → TimeoutOutcome`，handler 根据锁持有状态裁定动作。线程安全契约（可被 daemon 扫描线程调用）同 `IOrphanRecoveryHandler`。
- **`TimeoutOutcome` 数据对象**：`sessionId`（String）+ `action`（TimeoutAction 枚举）+ `succeeded`（boolean）+ `message`（String）。镜像 `RecoveryOutcome` 结构。
- **`TimeoutAction` 枚举**：`LOCAL_CANCELLED`（本实例持有活跃锁 → 经 `engine.cancelSession(sessionId, "timeout", true)` 取消，终态 cancelled/forced_stopped）/ `FORCE_FAILED`（无活跃锁的孤儿 session → raw JDBC UPDATE status=failed）/ `SKIPPED_REMOTE`（活跃锁被远端实例持有，不干预）/ `SKIPPED`（NoOp shipped 默认，LOG.warn 观测）。
- **`NoOpSessionTimeoutHandler` shipped 默认**：singleton；`handleTimeout` 返回 `SKIPPED` outcome（LOG.warn 超时 session ID），**零行为回归**（与 plan 222/226 shipped 默认一致，超时检测仅观测不动作）。
- **`DefaultSessionTimeoutHandler` 功能实现**：构造期接收 `timeoutSeconds`（long，超时阈值）+ `IAgentEngine engine`（本地取消用）+ `DataSource dataSource`（既用于 FORCE_FAILED 的 `ai_agent_session` UPDATE，**也**用于读取 `ai_agent_session_lock` 的锁持有者——见裁定 1）+ `String instanceId`（本实例标识，与锁持有者比较）。`handleTimeout` 执行三分裁定（见设计裁定 1）。**不注入 `ISessionTakeoverLock`**：其 `isHeld(sessionId)` 仅返回 boolean、不区分持有者（接口 Javadoc 明确"Does not distinguish owners"），无法支撑本地/远端区分；故 handler 经 raw JDBC 直读 `ai_agent_session_lock` 表（与 `ScheduledRecoveryManager` stale-lock-cleanup 直访锁表的既有模式一致）。
- **`ScheduledRecoveryManager` 集成**：新增 `sessionTimeoutHandler` 字段（默认 `NoOpSessionTimeoutHandler`）+ `setSessionTimeoutHandler` setter。`scanOnce` 在 stale lock cleanup **之后**、orphan detection **之前**，增加超时检测步骤：SELECT 超时 session → 对每个调 `handler.handleTimeout`，汇总到 `RecoveryScanResult`。**timeout 步骤先于 orphan**，使被强制标记 failed（terminal）的超时 session 自动被后续 orphan detection（过滤 `STATUS IN ('running','pending')`）排除，避免与 orphan handler 冲突。
- **`RecoveryScanResult` 扩展**：新增 `timeoutActions: List<TimeoutOutcome>` 字段（7-arg 构造器 = 原 6 args + `List<TimeoutOutcome> timeoutActions`）；`getTimeoutActions()` getter；`empty()` 工厂返回 `timeoutActions = Collections.emptyList()`；现有调用方（`ScheduledRecoveryManager.scanOnce` / `NoOpRecoveryManager.scanOnce`）适配。镜像 plan 226 的 6-arg 扩展模式。
- **focused 测试**：LOCAL_CANCELLED（mock engine + 本实例锁 → cancelSession 被调用）/ FORCE_FAILED（H2 孤儿超时 session → DB status=failed）/ SKIPPED_REMOTE（远端锁持有 → 不动 DB，LOG.warn）/ NoOp SKIPPED（零回归）/ shipped 默认零回归 各有覆盖。
- **端到端验证**：H2 DB + `ScheduledRecoveryManager` + `DefaultSessionTimeoutHandler` → 手动插入超时孤儿 session 行（活动时间戳早于 now-threshold）→ `scanOnce` → 断言 session status=failed + `RecoveryScanResult.timeoutActions` 含 succeeded=true FORCE_FAILED outcome + 后续 orphan detection 不再检测到该 session（已 terminal）。
- **设计文档更新**：`nop-ai-agent-actor-runtime-vision.md` §6.3 工作流第 3 步从 `successor` 标注为已落地（三分裁定 + 可插拔 handler + daemon 集成）。

## Non-Goals

- **orphan 进程主动 liveness 检测**（vision §6.3"检查进程 ID 是否存活"）：主动检测锁持有者进程存活状态需要进程注册/心跳机制。本计划三分裁定的"远端实例"判定仅依赖锁持有者标识（`LOCK_OWNER` vs `instanceId`）+ lease TTL，不检测进程存活。Classification: successor plan required。
- **RETRY 恢复模式**（vision §6.3 第 2 步）：从头重试整个请求。需 `ISessionStore` reset 语义 + 工具副作用 idempotency 裁定。Classification: successor plan required（plan 226 已切出）。
- **归档清理**（vision §6.3 第 4 步）：terminal session 归档到历史表。Classification: optimization candidate。
- **心跳自动续约**：长时间执行自动续约 lease。Classification: optimization candidate（plan 221 已裁定为有意 fail-safe 设计）。
- **per-agent / per-session 超时配置**：`AgentModel.maxWallClockMinutes` 或 session 级超时 override。本计划超时阈值为全局可配置 `timeoutSeconds`（注入 handler）。per-agent/per-session 维度是 successor（依赖 AgentModel 字段扩展 + 配置 precedence 裁定）。Classification: optimization candidate。
- **跨进程取消传播**：对远端实例持有的活跃超时 session 发起跨进程 cancel（经 `IMessageService` 投递 cancel 指令）。本计划对远端活跃 session 仅 LOG.warn + SKIPPED_REMOTE（不干预，等其自身 daemon 处理或 lease 过期转 orphan）。Classification: successor plan required（依赖跨进程消息通路）。
- **优雅等待 + 超时升级**：vision §6.3 描述的"cancel → 等待优雅停止 → 强制标记"三段式。本计划本地路径经 `cancelSession(forced=true)`（已封装 graceful + forced interrupt，见 `02-execution-model.md`）一次调用完成，不在 daemon scan 循环中阻塞等待。分立的 graceful-wait-then-force 序列是 successor（需 bounded-wait 调度裁定）。Classification: optimization candidate。
- **XDSL 配置化**：`agent.xdef` 增加 `<timeout-policy>` 元素。当前经编程 API 配置。Classification: optimization candidate。
- **`RecoveryScanResult` 构造器重构**：7-arg 构造器持续膨胀。若后续仍有扩展，重构为 builder / action-log Map 是治理项。Classification: optimization candidate。
- **TeamManager + ResourceGuard + Fencing Token**（vision §10 Phase 3/5）。Classification: successor plan required。
- **nop-job `IJobScheduler` 集成**：Classification: successor plan required（plan 222 已裁定 `IScheduledExecutor` 足够）。

## Scope

### In Scope

- 新增 `TimeoutAction` 枚举 + `TimeoutOutcome` 数据对象 + `ISessionTimeoutHandler` 接口 + `NoOpSessionTimeoutHandler` 默认 + `DefaultSessionTimeoutHandler` 功能实现于 `io.nop.ai.agent.runtime.recovery` 包
- `DefaultSessionTimeoutHandler` 三分裁定：LOCAL_CANCELLED（本实例锁 → cancelSession forced）/ FORCE_FAILED（孤儿 → raw JDBC UPDATE status=failed 条件 WHERE）/ SKIPPED_REMOTE（远端锁 → LOG.warn 不干预）。三分判定经 raw JDBC `SELECT LOCK_OWNER, LOCK_EXPIRES_AT FROM ai_agent_session_lock` 直读锁表（用既有 `AiAgentSessionLockTable` 常量，不注入 `ISessionTakeoverLock`）
- `ScheduledRecoveryManager` 新增 `setSessionTimeoutHandler` setter（默认 NoOp）+ scanOnce 超时检测步骤（stale lock cleanup 后、orphan detection 前执行）
- `RecoveryScanResult` 扩展 `timeoutActions` 字段（7-arg 构造器 + getter + `empty()` 适配）；现有调用方适配
- 超时检测 SQL + 活动时间戳列裁定（见设计裁定 2）
- focused 测试 + 端到端测试（H2 场景）
- 设计文档更新（`nop-ai-agent-actor-runtime-vision.md` §6.3）

### Out Of Scope

- 见 Non-Goals（orphan liveness / RETRY / 归档 / 心跳续约 / per-agent 超时 / 跨进程取消 / 优雅等待升级 / XDSL / 构造器重构 / TeamManager / nop-job 均为显式 successor）

### 设计裁定

**裁定 1：三分裁定（本地 / 孤儿 / 远端）—— 经 raw JDBC 直读锁表**——对每个超时 running/pending session，先经 `DataSource` 对 `ai_agent_session_lock` 执行单条 `SELECT LOCK_OWNER, LOCK_EXPIRES_AT WHERE SESSION_ID=?`（表名/列名用既有 `AiAgentSessionLockTable` 常量，与 `ScheduledRecoveryManager` stale-lock-cleanup 直访锁表的既有模式一致），按结果三分：

> **为什么不注入 `ISessionTakeoverLock`**：其 `isHeld(sessionId)` 仅返回 boolean、Javadoc 明确"Does not distinguish owners"。LOCAL vs REMOTE 的区分必须知道 `LOCK_OWNER`，而接口无任何方法返回持有者标识。直读锁表是唯一不扩张 `ISessionTakeoverLock` public API（避免波及 `NoOpSessionTakeoverLock` 及所有集成商）的方式，且与 recovery 包既有的 raw-JDBC-on-lock-table 模式一致。部署假设：功能性 recovery 栈部署时 `ai_agent_session_lock` 表已存在（由 `DbSessionTakeoverLock` 构造期 `initSchema()` 建表，同 `ScheduledRecoveryManager` stale-lock-cleanup 的既有假设）。

- **无活跃锁 / 无锁行 / `LOCK_EXPIRES_AT <= now`**（orphaned，已被 stale lock cleanup 清理或从未持有）：session 不被任何进程执行 → raw JDBC `UPDATE ai_agent_session SET STATUS='failed' WHERE SESSION_ID=? AND STATUS IN ('running','pending')`（条件 WHERE 防止标记已转换的 session，与 plan 226 ABORT 一致）。返回 `FORCE_FAILED` outcome。
- **`LOCK_OWNER == instanceId` 且 `LOCK_EXPIRES_AT > now`**（本实例持有活跃锁）：session 在本进程有活跃执行线程 → `engine.cancelSession(sessionId, "timeout", true)`（forced=true 封装 graceful + 线程 interrupt，终态 cancelled/forced_stopped）。返回 `LOCAL_CANCELLED` outcome。
- **`LOCK_OWNER != instanceId` 且 `LOCK_EXPIRES_AT > now`**（远端实例持有活跃锁）：不属本实例，不可取消其线程，不应与其争 DB status → LOG.warn（含 LOCK_OWNER）+ 返回 `SKIPPED_REMOTE` outcome（等其自身 daemon 处理，或 lease 过期后转 orphan 由后续 scan 的孤儿路径处理）。

理由：(1) 本地 cancelSession 是唯一能真正中断活跃线程的路径（经 CancelHandle + interrupt）；(2) 孤儿 raw UPDATE 与 plan 226 ABORT 模式一致，幂等安全；(3) 远端干预会引发跨实例 status 竞争（另一实例可能覆写 failed 回 running），必须让出；(4) 单条 SELECT 一次取回 (owner, expires)，三分判定全靠该结果，无需多次锁查询。

**裁定 2：活动时间戳列 = session 最近状态更新时间**——超时检测 SQL 为 `SELECT SESSION_ID FROM ai_agent_session WHERE STATUS IN ('running','pending') AND {activityCol} < ?`（`?` = `now - timeoutSeconds`）。`{activityCol}` 为 `ai_agent_session` 表中记录最近状态变更/持久化的时间戳列。Phase 1 须核对 `AiAgentSessionTable` 常量确认该列存在并选定（候选：UPDATED_AT / LAST_ACTIVE_AT / CREATED_AT，按 Nop `useStdFields` 约定 CREATED_AT/UPDATED_AT 为标准列）。语义：活动时间戳 = lastActiveAt 的代理——一个仍在迭代（每轮 ReAct 持久化）的 session 会持续刷新该列，不会误判超时；只有真正卡死/挂起（停止迭代）的 session 才会超时，这正是目标。理由：(1) 与 vision §6.3"lastActiveAt 超过 maxWallClockMinutes"语义一致；(2) 不误杀合法长任务（仍在迭代的会被刷新）；(3) 避免引入新 DDL（若列已存在则零 schema 变更）。若核对发现无合适时间戳列，Phase 1 升级为 DDL 新增 `LAST_ACTIVE_AT` 列（`AiAgentSessionTable` 常量 + 构造期 `initSchema()` 自动建表，镜像 `DbSessionTakeoverLock` 的 `ai_agent_session_lock` 建表模式）。

**裁定 3：timeout 检测先于 orphan 检测**——`scanOnce` 步骤顺序为：stale lock cleanup → **timeout detection** → orphan detection。理由：timeout handler 会把超时孤儿 session 强制标记为 failed（terminal），使后续 orphan detection（过滤 `STATUS IN ('running','pending')`）自动排除它，避免同一 session 被 timeout handler 标记 failed 后又被 orphan handler RESUME（fire-and-forget restoreSession）产生冲突。顺序依赖无新基础设施，只是 scanOnce 内部步骤重排。

**裁定 4：NoOpSessionTimeoutHandler 为 shipped 默认**——与 plan 222/226 的 NoOp 模式一致。`handleTimeout` 返回 SKIPPED outcome（LOG.warn），引擎默认零行为回归。集成商经 `manager.setSessionTimeoutHandler(handler)` 注入功能性实现。

## Execution Plan

### Phase 1 - 契约表面 + NoOp 默认 + 超时裁定落档

Status: completed
Targets: `io.nop.ai.agent.runtime.recovery` 包（枚举 + 数据对象 + 接口 + NoOp 默认）、`AiAgentSessionTable`（时间戳列核对）、`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md`

- Item Types: `Decision` | `Proof`

- [x] **裁定并落档** 超时三分裁定（本地 cancel / 孤儿 force-fail / 远端 skip）+ 活动时间戳列 + timeout-先于-orphan 步骤顺序。落档到 `nop-ai-agent-actor-runtime-vision.md` §6.3 工作流第 3 步 + 设计裁定理由
- [x] 核对 `AiAgentSessionTable` 常量：确认 `ai_agent_session` 存在记录最近状态/持久化的时间戳列并选定（裁定 2）。若不存在则新增 `LAST_ACTIVE_AT` 列定义 + 构造期 `initSchema()` 自动建表（镜像 `DbSessionTakeoverLock`）
- [x] 定义 `TimeoutAction` 枚举：`LOCAL_CANCELLED` / `FORCE_FAILED` / `SKIPPED_REMOTE` / `SKIPPED`，Javadoc 明确每种动作的语义与触发条件
- [x] 定义 `TimeoutOutcome` 数据对象：`sessionId`（String）+ `action`（TimeoutAction）+ `succeeded`（boolean）+ `message`（String），不可变（全参构造 + getter）
- [x] 定义 `ISessionTimeoutHandler` 接口：`handleTimeout(String sessionId) → TimeoutOutcome`。Javadoc 明确线程安全契约（可被 daemon 扫描线程调用）、NoOp 语义、三分裁定责任在实现侧
- [x] 实现 `NoOpSessionTimeoutHandler`（singleton）：`handleTimeout` 返回 SKIPPED outcome（LOG.warn 超时 session ID），零回归
- [x] `nop-ai-agent-actor-runtime-vision.md` §6.3 工作流第 3 步从 `successor：超时强制中止` 标注为已落地（三分裁定 + 可插拔 handler + daemon 集成 + NoOp shipped 默认）；标注 per-agent 超时 / 跨进程取消 / 优雅等待升级 / orphan liveness 仍为 successor

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `TimeoutAction.java` + `TimeoutOutcome.java` + `ISessionTimeoutHandler.java` + `NoOpSessionTimeoutHandler.java` 文件存在于 `io.nop.ai.agent.runtime.recovery` 包
- [x] 时间戳列已核对/选定（或新增 `LAST_ACTIVE_AT` + initSchema），`AiAgentSessionTable` 常量含该列引用
- [x] 超时三分裁定 + 时间戳列 + 步骤顺序已裁定并落档（设计文档标注裁定理由）
- [x] `NoOpSessionTimeoutHandler.handleTimeout` 返回 SKIPPED outcome（LOG.warn，非静默跳过）
- [x] **No new test required for Phase 1**：Phase 1 只交付契约表面（枚举 + 接口 + NoOp 默认）+ 时间戳列裁定，无行为逻辑需测试；NoOp 行为验证在 Phase 2 focused 测试中覆盖（Minimum Rules #25）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - DefaultSessionTimeoutHandler 功能实现 + daemon 集成 + RecoveryScanResult 扩展 + 测试

Status: completed
Targets: `io.nop.ai.agent.runtime.recovery.DefaultSessionTimeoutHandler`、`ScheduledRecoveryManager`（`setSessionTimeoutHandler` setter + scanOnce timeout 检测步骤 + 步骤顺序重排）、`RecoveryScanResult`（`timeoutActions` 字段扩展 + 7-arg 构造器 + `empty()` 适配）、`NoOpRecoveryManager`（`empty()` 适配）、测试

- Item Types: `Proof`

- [x] 实现 `DefaultSessionTimeoutHandler`：
  - 构造期接收 `long timeoutSeconds` + `IAgentEngine engine`（LOCAL_CANCELLED 用）+ `DataSource dataSource`（既用于 FORCE_FAILED 的 `ai_agent_session` UPDATE，**也**用于 LOCAL/REMOTE 判定的 `ai_agent_session_lock` 锁持有者 SELECT）+ `String instanceId`（与 `LOCK_OWNER` 比较）。依赖项为 null 时 fail-fast 抛 `NullPointerException`（防止 silent misuse）。**不注入 `ISessionTakeoverLock`**（`isHeld` 不区分持有者，见裁定 1）。
  - **三分判定**：先对 `ai_agent_session_lock` 执行 `SELECT LOCK_OWNER, LOCK_EXPIRES_AT WHERE SESSION_ID=?`（表名/列名用既有 `AiAgentSessionLockTable` 常量），按结果分发：
  - **LOCAL_CANCELLED**：结果行 `LOCK_OWNER == instanceId` 且 `LOCK_EXPIRES_AT > now` → 调 `engine.cancelSession(sessionId, "timeout", true)`（fire-and-forget，不阻塞 scan 循环），返回 `LOCAL_CANCELLED` outcome。cancelSession 同步抛异常（session 不存在等）时捕获返回 succeeded=false + message
  - **FORCE_FAILED**：无锁行 / `LOCK_EXPIRES_AT <= now`（orphaned）→ raw JDBC `UPDATE ai_agent_session SET STATUS=? WHERE SESSION_ID=? AND STATUS IN ('running','pending')`（STATUS 参数用 `AgentExecStatus.failed.name()`，表名/列名用 `AiAgentSessionTable` 常量，与 plan 226 ABORT 一致），affected rows=1 → succeeded=true，affected rows=0（已转换）→ succeeded=false
  - **SKIPPED_REMOTE**：结果行 `LOCK_OWNER != instanceId` 且 `LOCK_EXPIRES_AT > now` → LOG.warn（含 LOCK_OWNER），返回 succeeded=true SKIPPED_REMOTE outcome（不干预 DB）
- [x] `RecoveryScanResult` 扩展：新增 `private final List<TimeoutOutcome> timeoutActions` 字段 + 7-arg 构造器（原 6 args + `List<TimeoutOutcome> timeoutActions`）；`empty()` 工厂返回 `timeoutActions = Collections.emptyList()`；新增 `getTimeoutActions()` getter。现有调用方 `ScheduledRecoveryManager.scanOnce`（原 6-arg）更新为 7-arg；`NoOpRecoveryManager.scanOnce`（`empty()` 工厂）自动适配
- [x] `ScheduledRecoveryManager` 新增 `sessionTimeoutHandler` 字段（默认 `NoOpSessionTimeoutHandler`）+ `setSessionTimeoutHandler` setter（null 拒绝，保持 NoOp）。`scanOnce` 步骤顺序重排为：stale lock cleanup → **timeout detection（SELECT 超时 session → 对每个调 `handler.handleTimeout`，收集 `TimeoutOutcome` 列表）** → orphan detection（不变）。timeout outcomes 传入 `RecoveryScanResult` 7-arg 构造器
- [x] 编写 `DefaultSessionTimeoutHandler` focused 测试：
  - LOCAL_CANCELLED：H2 在 `ai_agent_session_lock` 插入活跃锁行（`LOCK_OWNER=instanceId`）+ mock engine → handler.handleTimeout → 断言锁表 SELECT 被执行 + `engine.cancelSession(forced=true)` 被调用 + outcome.action=LOCAL_CANCELLED + succeeded=true
  - LOCAL_CANCELLED 异常：mock engine.cancelSession 抛异常 → outcome.succeeded=false + message 含异常摘要
  - FORCE_FAILED：H2 插入 running 孤儿超时 session（无锁行）→ handler.handleTimeout → 断言锁表 SELECT 返回无行 + DB session status=failed + outcome.action=FORCE_FAILED + succeeded=true
  - FORCE_FAILED 已转换：H2 插入 completed session → handler.handleTimeout → affected rows=0 + succeeded=false
  - SKIPPED_REMOTE：H2 在 `ai_agent_session_lock` 插入活跃锁行（`LOCK_OWNER=other-instance`）→ handler.handleTimeout → 断言锁表 SELECT 返回 other-instance + DB session 未变 + outcome.action=SKIPPED_REMOTE + LOG.warn 含 LOCK_OWNER
  - 过期锁转 FORCE_FAILED：H2 插入锁行 `LOCK_EXPIRES_AT <= now`（stale）+ running session → handler.handleTimeout → 走 FORCE_FAILED 分支（过期锁等同无活跃锁）
- [x] 编写 daemon 集成测试：H2 + `ScheduledRecoveryManager` + `DefaultSessionTimeoutHandler` → 插入超时孤儿 session → scanOnce → 断言 session status=failed + `RecoveryScanResult.timeoutActions` 含 succeeded=true FORCE_FAILED outcome
- [x] 编写 timeout-先于-orphan 顺序测试：H2 + 一个超时孤儿 session + `DefaultSessionTimeoutHandler`(force-fail) + `DefaultOrphanRecoveryHandler`(RESUME) → scanOnce → 断言该 session 经 timeout 路径标记 failed（terminal），`orphanSessionIds` 不含该 session（未被 orphan handler RESUME，无冲突）
- [x] 编写 NoOp 默认零回归测试：`NoOpSessionTimeoutHandler` 下 scanOnce 行为与 plan 226 shipped 默认一致（LOG.warn 超时 session，无副作用）+ `RecoveryScanResult.empty()` 的 `timeoutActions` 为 `emptyList()`
- [x] 编写 `ScheduledRecoveryManager.setSessionTimeoutHandler` setter 注入测试：默认 handler instanceof NoOp → `manager.setSessionTimeoutHandler(handler)` → 断言 scanOnce 调用注入的 handler（非 NoOp）
- [x] 编写端到端测试：H2 DB + `ScheduledRecoveryManager` + `DefaultSessionTimeoutHandler` → 手动插入超时孤儿 session 行（活动时间戳早于 now-threshold）→ `scanOnce` → 断言 session status=failed + `RecoveryScanResult.timeoutActions` 含 succeeded=true FORCE_FAILED outcome + 后续 orphan detection 不再检测到该 session（已 terminal）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `DefaultSessionTimeoutHandler` 正确实现三分裁定（经锁表 SELECT LOCK_OWNER/LOCK_EXPIRES_AT 判定：LOCAL_CANCELLED 委托 cancelSession forced / FORCE_FAILED raw JDBC UPDATE status=failed 条件 WHERE / SKIPPED_REMOTE LOG.warn 不干预）
- [x] `RecoveryScanResult` 7-arg 构造器 + `timeoutActions` 字段 + `getTimeoutActions()` getter + `empty()` 返回 `timeoutActions=emptyList()` 已落地；现有调用方（`ScheduledRecoveryManager.scanOnce` / `NoOpRecoveryManager.scanOnce`）已适配
- [x] `ScheduledRecoveryManager` scanOnce 步骤顺序为 stale lock cleanup → timeout detection → orphan detection；新增 `setSessionTimeoutHandler` setter（默认 NoOp）
- [x] **接线验证**（Minimum Rules #23）：`ScheduledRecoveryManager` 持有 `ISessionTimeoutHandler` 引用；`scanOnce` 运行时确实调用 `handler.handleTimeout`（测试断言 handler 被调用 + DB 状态变化/cancelSession 被调用）；`setSessionTimeoutHandler` setter 正确赋值（注入非 NoOp handler 后 scanOnce 调用注入的 handler）
- [x] **端到端验证**（Minimum Rules #22）：H2 + 超时孤儿 session + handler → scanOnce → session status=failed + timeoutActions 正确 + orphan detection 不再检测到该 session
- [x] **无静默跳过**（Minimum Rules #24）：LOCAL_CANCELLED 异常时返回 succeeded=false（非静默）；FORCE_FAILED affected rows=0 时返回 succeeded=false（非静默）；SKIPPED_REMOTE/NoOp SKIPPED LOG.warn（非静默）；所有异常路径有 LOG.warn 或 outcome 记录
- [x] shipped 默认（`NoOpSessionTimeoutHandler`）下既有测试零回归
- [x] 新增功能各有对应 focused 测试覆盖（LOCAL_CANCELLED / LOCAL_CANCELLED-exception / FORCE_FAILED / FORCE_FAILED-transitioned / SKIPPED_REMOTE / expired-lock-to-FORCE_FAILED / daemon-integration / timeout-before-orphan / NoOp-regression / setter-injection / E2E 各有测试）
- [x] `nop-ai-agent-actor-runtime-vision.md` §6.3 已更新（工作流第 3 步标注已落地 + 三分裁定 + successor 标注）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `TimeoutAction` 枚举 + `TimeoutOutcome` 数据对象 + `ISessionTimeoutHandler` 接口 + `NoOpSessionTimeoutHandler` shipped 默认已落地
- [x] `DefaultSessionTimeoutHandler` 三分裁定（LOCAL_CANCELLED / FORCE_FAILED / SKIPPED_REMOTE）已落地
- [x] `ScheduledRecoveryManager.scanOnce` timeout 检测步骤（先于 orphan）+ `setSessionTimeoutHandler` setter 已落地
- [x] `RecoveryScanResult` 7-arg 构造器 + `timeoutActions` 字段 + `empty()` 适配已落地（现有调用方无回归）
- [x] 端到端：H2 + 超时孤儿 session + handler → scanOnce → 强制标记 failed + timeoutActions 正确 + 不与 orphan handler 冲突 完整路径跑通
- [x] shipped 默认（NoOp）下既有测试零回归
- [x] 必要 focused verification 已完成（LOCAL_CANCELLED / LOCAL_CANCELLED-exception / FORCE_FAILED / FORCE_FAILED-transitioned / SKIPPED_REMOTE / expired-lock-to-FORCE_FAILED / daemon-integration / timeout-before-orphan / NoOp-regression / setter-injection / E2E 各有测试）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（orphan liveness / RETRY / 归档 / 心跳续约 / per-agent 超时 / 跨进程取消 / 优雅等待升级 / XDSL / 构造器重构 / TeamManager / nop-job 均显式在 Non-Goals 切出）
- [x] 受影响 owner docs（`nop-ai-agent-actor-runtime-vision.md` §6.3）已同步到 live baseline
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`handler.handleTimeout` 在运行时被 `ScheduledRecoveryManager.scanOnce` 调用（不只类型存在），（b）三分裁定路径在运行时确实执行（不只方法存在），（c）无空方法体/静默跳过作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；orphan liveness / RETRY / 归档 / 心跳续约 / per-agent 超时 / 跨进程取消 / 优雅等待升级 / XDSL / 构造器重构 / TeamManager / nop-job 集成均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **orphan 进程主动 liveness 检测**（vision §6.3"检查进程 ID 是否存活"）：主动检测锁持有者进程存活状态。Classification: successor plan required。
- **RETRY 恢复模式**（vision §6.3 第 2 步）：从头重试整个请求。Classification: successor plan required（plan 226 已切出）。
- **per-agent / per-session 超时配置**：`AgentModel.maxWallClockMinutes` 或 session 级 override。Classification: optimization candidate。
- **跨进程取消传播**：对远端实例持有的超时 session 经 `IMessageService` 投递 cancel。Classification: successor plan required。
- **优雅等待 + 超时升级序列**：graceful cancel → bounded wait → force（vision §6.3 三段式）。Classification: optimization candidate。
- **归档清理**（vision §6.3 第 4 步）。Classification: optimization candidate。
- **心跳自动续约**。Classification: optimization candidate。
- **XDSL 配置化**：`agent.xdef` 增加 `<timeout-policy>` 元素。Classification: optimization candidate。
- **`RecoveryScanResult` 构造器重构**（builder / action-log Map）。Classification: optimization candidate。
- **TeamManager + ResourceGuard + Fencing Token**（vision §10 Phase 3/5）。Classification: successor plan required。
- **nop-job `IJobScheduler` 集成**。Classification: successor plan required。

## Closure

Status Note: plan 229 闭合 vision §6.3 RecoveryManager 工作流第 3 步（超时强制中止）。nop-ai-agent 的超时处理从"RecoveryManager daemon 仅处理孤儿 session（无活跃锁的 running/pending），对仍持有活跃锁但运行时间超过上限的 session 无任何机制"扩展为"scanOnce 在 stale-lock cleanup 之后、orphan detection 之前增加超时检测步骤：对 STATUS IN ('running','pending') 且 UPDATED_AT 超过配置 timeoutSeconds 的 session，经可插拔 ISessionTimeoutHandler 裁定动作"。三分裁定（LOCAL_CANCELLED / FORCE_FAILED / SKIPPED_REMOTE）经 raw JDBC 直读锁表实现，不波及 ISessionTakeoverLock public API。NoOp shipped 默认零回归。所有 in-scope 项已落地，所有 successor 显式在 Non-Goals 切出。
Completed: 2026-06-17

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（explore subagent，task `ses_12cbe0990ffetD2z1YoqZ7M3DP`）
- Audit Session: `ses_12cbe0990ffetD2z1YoqZ7M3DP`
- Evidence:
  - **Phase 1 Exit Criteria**（7/7 PASS）：
    - 4 个契约文件存在（`TimeoutAction.java` / `TimeoutOutcome.java` / `ISessionTimeoutHandler.java` / `NoOpSessionTimeoutHandler.java` in `io.nop.ai.agent.runtime.recovery`）— PASS
    - `AiAgentSessionTable.java:31` `COL_UPDATED_AT = "UPDATED_AT"`（`BIGINT NOT NULL`，line 42），零 schema 变更 — PASS
    - 三分裁定 + 时间戳列 + 步骤顺序已落档（`nop-ai-agent-actor-runtime-vision.md:321` plan 229 落地 callout + `:337-341` 工作流第 3 步已落地）— PASS
    - `NoOpSessionTimeoutHandler.java:50-53` LOG.warn + SKIPPED outcome — PASS
    - No new test required for Phase 1（NoOp 验证在 Phase 2 `noOpTimeoutHandlerDefaultProducesSkippedOutcomes` 覆盖）— PASS
    - `./mvnw compile -pl nop-ai/nop-ai-agent -am` → BUILD SUCCESS（executor 验证）— PASS
    - `ai-dev/logs/2026/06-17.md` plan-229 条目已添加 — PASS
  - **Phase 2 Exit Criteria**（11/11 PASS）：
    - `DefaultSessionTimeoutHandler` 三分裁定（LOCAL_CANCELLED `:211-228` 调 cancelSession forced / FORCE_FAILED `:237-264` raw JDBC UPDATE 条件 WHERE / SKIPPED_REMOTE `:271-277` LOG.warn 不干预）— PASS
    - `RecoveryScanResult` 7-arg 构造器 `:65-79` + `timeoutActions` 字段 `:63` + `getTimeoutActions()` `:133-135` + `empty()` `:87-90` 返回 emptyList；现有调用方适配 — PASS
    - `ScheduledRecoveryManager` scanOnce 步骤顺序 stale lock cleanup `:341` → timeout detection `:352-363` → orphan detection `:365`；`setSessionTimeoutHandler` setter `:230-236` — PASS
    - **接线验证 #23**：`ScheduledRecoveryManager.java:361` 运行时调用 `sessionTimeoutHandler.handleTimeout(timedOutId)`；`setSessionTimeoutHandlerInjectsNonNoOpHandler` 断言 handler callCount=1；`daemonTimeoutLocalCancelledWiringWithMockEngine` 断言 engine.cancelSession forced=true — PASS
    - **端到端验证 #22**：`endToEndTimeoutForceFailedCompleteScanResult`（3 session 完整路径，timed-out orphan → failed，healthy 不动，terminal 排除）+ `timeoutBeforeOrphanOrderingAvoidsConflict`（orphan handler callCount=0 无冲突）— PASS
    - **无静默跳过 #24**：LOCAL_CANCELLED 异常 `:225-226` succeeded=false；FORCE_FAILED affected=0 `:254-255` succeeded=false；SKIPPED_REMOTE/NoOp SKIPPED LOG.warn；所有 SQLException/RuntimeException 路径 LOG.warn + succeeded=false outcome；recovery 包 0 个 `continue` 语句 — PASS
    - shipped 默认 NoOp 既有测试零回归（`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` BUILD SUCCESS）— PASS
    - 11/11 focused 测试类别覆盖（见 Phase 2 focused 测试表）— PASS
    - `nop-ai-agent-actor-runtime-vision.md` §6.3 已更新 — PASS
    - `./mvnw test -pl nop-ai/nop-ai-agent -am` → BUILD SUCCESS（executor 验证：43 recovery tests + 全模块零回归）— PASS
    - `ai-dev/logs/2026/06-17.md` plan-229 条目已添加 — PASS
  - **Closure Gates**（14/14 PASS，含本 audit 满足 gate 10）：
    - Gate 1-9: substantive checks PASS（见 audit report）
    - Gate 10: 本 audit 段落即 closure-audit 证据记录 — PASS
    - Gate 11: Anti-Hollow Check PASS（见下方）
    - Gate 12: `./mvnw compile -pl nop-ai/nop-ai-agent -am` → BUILD SUCCESS — PASS
    - Gate 13: `./mvnw test -pl nop-ai/nop-ai-agent -am` → BUILD SUCCESS — PASS
    - Gate 14: checkstyle / 代码规范（imports grouped java.* → javax.* → org.slf4j → io.nop.*，4-space indent，PascalCase/camelCase，无 private field injection，无 Spring-only patterns）— PASS
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/229-nop-ai-agent-session-timeout-forced-abort.md --strict` 退出码 0（确认无未勾选项 + Closure Evidence 已写入）
  - **Anti-Hollow 检查结果**：
    - (a) `handler.handleTimeout` 在运行时被 `ScheduledRecoveryManager.scanOnce:361` 调用（不只类型存在）— recording handler callCount=1 断言验证
    - (b) 三分裁定路径在运行时确实执行（不只方法存在）— LOCAL_CANCELLED `daemonTimeoutLocalCancelledWiringWithMockEngine` 断言 engine.cancelSession called forced=true；FORCE_FAILED `daemonTimeoutDetectionForceFailsOrphanedTimedOutSession` + `forceFailedUpdatesStatusForOrphanedSession` 断言 DB status=failed；SKIPPED_REMOTE `skippedRemoteLeavesDbUnchanged` 断言 DB 不变 + engine 未调用
    - (c) 无空方法体/静默跳过/no-op 作为正常实现 — `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0（0 critical/high/medium/low findings）
  - **Deferred 项分类检查**：orphan liveness / RETRY / 归档 / 心跳续约 / per-agent 超时 / 跨进程取消 / 优雅等待升级 / XDSL / 构造器重构 / TeamManager / nop-job 均显式在 Non-Goals（lines 40-53）切出为独立 successor，无 in-scope live defect 被降级

Follow-up:

- no remaining plan-owned work（所有 in-scope 项已落地；per-agent 超时 / 跨进程取消传播 / 优雅等待升级序列 / orphan liveness / RETRY / 归档清理 / 心跳续约 / XDSL 配置化 / 构造器重构 / TeamManager / nop-job 集成均为显式 Non-Goals 独立 successor，各自在 Non-Goals + Non-Blocking Follow-ups 标注 Classification）
