# 226 nop-ai-agent Orphan Session 恢复模式策略（resume / abort）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-8-P4-RecoveryStrategy

> Last Reviewed: 2026-06-17
> Source: carry-over from `ai-dev/plans/222-nop-ai-agent-recovery-manager-daemon.md`（Non-Blocking Follow-ups 第二条：`恢复模式策略（resume/retry/abort）（vision §6.3 第 2 步）：恢复 orphaned session 时裁定恢复策略。Classification: successor plan required`）+ `ai-dev/plans/221-nop-ai-agent-cross-process-session-takeover-lock.md`（Non-Blocking Follow-ups 第三条：`恢复模式策略（resume/retry/abort）... Classification: successor plan required`）；`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md` §6.3（RecoveryManager 工作流第 2 步：处理 orphaned Actor）+ §6.1（恢复策略表：JVM 进程崩溃重启 → RecoveryManager 扫描，根据策略恢复）
> Related: `222`（交付定时扫描 daemon，本计划为其恢复策略 successor）、`221`（交付跨进程接管锁，本计划 RESUME 模式依赖其 tryAcquire 防止 double-execution）、`183`（交付 `restoreSession` 单 session 恢复原语，RESUME 模式委托给它）、`184`（交付 `restorePendingSessions` 启动期批量恢复）

## Purpose

把 nop-ai-agent 的 orphan session 处理从"RecoveryManager daemon 仅检测并 LOG.warn 记录、不采取任何恢复行动"扩展为"按可插拔 `IOrphanRecoveryHandler` 决策恢复模式——RESUME 自动恢复、ABORT 标记失败、SKIP 仅观测"。本计划只负责这一件事：让无人值守多实例部署中 crash 后遗留的 orphaned session（`status='running'/'pending'` 但无活跃接管锁）能被自动恢复或显式标记失败，而非无限期驻留 DB 等待人工干预。

> **关键裁定**：vision §6.3 列出 3 种恢复模式（resume / retry / abort）。本计划裁定首版交付 **RESUME + ABORT + SKIP**，RETRY（清空 session 状态从头重试）为独立 successor。理由：(1) RESUME 委托给已存在的 `restoreSession`（plan 183，从 checkpoint 恢复续跑），无需新基础设施；(2) ABORT 是简单 raw JDBC UPDATE（status=failed），无需 checkpoint；(3) RETRY 需要 session 状态重置语义（清空消息列表、重置迭代计数、保留初始 request），涉及 `ISessionStore` 契约变更，是独立产品策略决策。SKIP 保留为 shipped 默认（零回归）。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-17）：

- **RecoveryManager 定时扫描 daemon ✅**（plan 222）：`IRecoveryManager` 接口（`start`/`stop` 幂等 + `scanOnce → RecoveryScanResult`）+ `NoOpRecoveryManager` shipped 默认 + `ScheduledRecoveryManager` 功能实现（`IScheduledExecutor` 周期调度默认 60s）已落地于 `io.nop.ai.agent.runtime.recovery` 包。`scanOnce` 执行 stale lock cleanup（DELETE 过期锁）+ orphan session detection（SELECT `STATUS IN ('running','pending')` 无活跃锁的 session → LOG.warn 记录到 `RecoveryScanResult.orphanSessionIds`）。**daemon 检测到 orphan 后仅 LOG.warn，不触发任何恢复动作。**
- **跨进程接管锁 ✅**（plan 221）：`ISessionTakeoverLock` 接口 + `NoOpSessionTakeoverLock` shipped 默认 + `DbSessionTakeoverLock` 功能实现（`ai_agent_session_lock` 表 + lease/TTL CAS acquire + stale-lock 抢占）。`DefaultAgentEngine` 三个执行入口点（`doExecute`/`resumeSession`/`restoreSession`）在 `putIfAbsent` 前经 `tryAcquire` 加锁。**RESUME 模式的安全性依赖此锁**：daemon 触发 `restoreSession` → 内部 `tryAcquire` → 若另一实例已恢复该 session 则 fail-fast（防止 double-execution）。
- **`restoreSession` ✅**（plan 183）：从 FileBackedSessionStore/DBSessionStore 加载 session 快照 → 恢复 ReAct 循环续跑。返回 `CompletableFuture<AgentExecutionResult>`。**RESUME 模式直接委托给此方法。**
- **`restorePendingSessions` ✅**（plan 184）：启动期批量 auto-restore。**仅在引擎启动时调用一次**，非持续运行。
- **零恢复策略代码**：grep `RecoveryMode|RecoveryStrategy|IOrphanRecoveryHandler|OrphanRecovery|resumeOrRetry` 在 `nop-ai/nop-ai-agent/src/main` 返回 0 类定义命中。`ScheduledRecoveryManager.scanOnce` 的 orphan detection 路径仅 `LOG.warn` 每个 orphan session ID（`ScheduledRecoveryManager.java` 约 :196-197）。
- **`AgentExecStatus` 枚举**：`pending` / `running` / `completed` / `failed` / `cancelled` / `forced_stopped` / `escalated` / `paused`。ABORT 模式将 session 标记为 `failed`。
- **引擎无 `orphanRecoveryHandler` 字段**：`DefaultAgentEngine` 当前无 orphan recovery handler 字段/setter。`DefaultAgentEngine` 有 `recoveryManager` 字段 + `setRecoveryManager` setter（plan 222），本计划不新增引擎层 handler 字段——handler 是 `ScheduledRecoveryManager` 的子组件，经其自己的 `setOrphanRecoveryHandler` setter 注入（见 Goals § Wiring 裁定）。

## Goals

> **Wiring 裁定**：`IOrphanRecoveryHandler` 仅 live 在 `ScheduledRecoveryManager` 上（经其 `setOrphanRecoveryHandler` setter 注入）。`DefaultAgentEngine` 不新增 handler 字段——handler 是 recovery manager 的内部策略，不是引擎层配置点。集成商配置路径：`new DefaultOrphanRecoveryHandler(mode, engine, dataSource)` → `manager.setOrphanRecoveryHandler(handler)` → `engine.setRecoveryManager(manager)`。这避免了 engine↔manager 双重 handler 配置点的歧义。

- **`RecoveryMode` 枚举**：`RESUME`（自动恢复——委托给 `engine.restoreSession`，fire-and-forget 异步触发）/ `ABORT`（标记失败——raw JDBC UPDATE session status=failed）/ `SKIP`（仅观测——LOG.warn，shipped 默认行为）。
- **`IOrphanRecoveryHandler` 接口**：`handleOrphan(String sessionId) → RecoveryOutcome`，handler 根据配置的 mode 决定动作。`RecoveryOutcome` 数据对象记录动作结果（`mode` / `succeeded` / `message`）。
- **`NoOpOrphanRecoveryHandler` shipped 默认**：singleton；`handleOrphan` 返回 SKIP outcome（LOG.warn orphan session ID），**零行为回归**（与 plan 222 daemon 的 shipped 默认行为一致）。
- **`DefaultOrphanRecoveryHandler` 功能实现**：按配置的 `RecoveryMode` 分发：
  - `RESUME`：调用 `engine.restoreSession(sessionId, "recovery-daemon", "orphan auto-recovery")`（fire-and-forget，不阻塞 scan 循环）。takeover 锁防 double-execution（restoreSession 内部 tryAcquire，若锁已被占则 fail-fast，handler 捕获并记录 SKIPPED）。
  - `ABORT`：raw JDBC `UPDATE ai_agent_session SET STATUS='failed' WHERE SESSION_ID=? AND STATUS IN ('running','pending')`（条件 WHERE 防止 abort 已转换的 session）。
  - `SKIP`：LOG.warn（当前行为）。
- **`ScheduledRecoveryManager` 集成**：新增 `setOrphanRecoveryHandler` setter（默认 `NoOpOrphanRecoveryHandler`）。`scanOnce` 在 orphan detection 后，对每个 `orphanSessionId` 调 `handler.handleOrphan(sessionId)`，汇总结果到 `RecoveryScanResult`（新增 `recoveryActions` 字段记录每 session 的动作摘要）。集成商经 `manager.setOrphanRecoveryHandler(handler)` 直接配置，**引擎不持有 orphan recovery handler 字段**——handler 是 recovery manager 的子组件，不是引擎层配置点（与 `setRecoveryManager` 是引擎层配置点、handler 是 manager 内部策略的分层一致）。
- **`ScheduledRecoveryManager` 集成**：新增 `setOrphanRecoveryHandler` setter（默认 `NoOpOrphanRecoveryHandler`）。`scanOnce` 在 orphan detection 后，对每个 `orphanSessionId` 调 `handler.handleOrphan(sessionId)`，汇总结果到 `RecoveryScanResult`（新增 `recoveryActions` 字段记录每 session 的动作摘要）。集成商经 `manager.setOrphanRecoveryHandler(handler)` 直接配置，**引擎不持有 orphan recovery handler 字段**——handler 是 recovery manager 的子组件，不是引擎层配置点（与 `setRecoveryManager` 是引擎层配置点、handler 是 manager 内部策略的分层一致）。
- **focused 测试**：RESUME 模式（orphan → restoreSession 被调用 + takeover 锁冲突时 SKIPPED）/ ABORT 模式（orphan → DB status=failed）/ SKIP 模式（orphan → LOG.warn 无副作用）/ shipped 默认零回归 各有覆盖。
- **端到端验证**：H2 DB + `ScheduledRecoveryManager` + `DefaultOrphanRecoveryHandler`（mode=ABORT）→ 手动插入 orphan session 行 → `scanOnce` → 断言 session status 被更新为 failed + `RecoveryScanResult` 动作摘要正确。
- **设计文档更新**：`nop-ai-agent-actor-runtime-vision.md` §6.3 标注恢复模式策略（第 2 步）已落地（RESUME/ABORT/SKIP，RETRY 为 successor）。

## Non-Goals

- **RETRY 恢复模式**（vision §6.3"从头重试整个请求"）：需要 session 状态重置语义（清空消息列表、重置迭代计数、保留初始 request），涉及 `ISessionStore` 契约变更 + 产品策略裁定（哪些 session 可安全 retry？工具副作用如何处理？）。Classification: successor plan required。
- **orphan 进程主动 liveness 检测**（vision §6.3"检查进程 ID 是否存活"）：主动检测锁持有者进程存活状态需要进程注册/心跳机制。本计划仅依赖 DB 可观测信号（status + 锁过期），不检测运行时进程存活。Classification: successor plan required。
- **超时强制中止**（vision §6.3 第 3 步）：`status=running` 且超时的 session 经 `ICancelToken.cancel()` 强制中止。需要 cancel 接入 + 超时策略裁定。Classification: successor plan required。
- **归档清理**（vision §6.3 第 4 步）：terminal session 归档到历史表。Classification: optimization candidate。
- **心跳自动续约**：长时间 ReAct 执行自动续约 lease。Classification: optimization candidate（plan 221 已裁定为有意 fail-safe 设计）。
- **XDSL 配置化**：`agent.xdef` 增加 `<recovery>` 元素配置 recovery mode。当前通过编程 API 配置。Classification: optimization candidate。
- **多实例恢复协调**：多个实例同时运行 daemon 时的恢复去重。takeover 锁已防 double-execution（restoreSession tryAcquire fail-fast），但多实例可能重复尝试恢复同一 session。Classification: optimization candidate。
- **TeamManager + ResourceGuard + Fencing Token**（vision §10 Phase 3/5）。Classification: successor plan required。
- **nop-job `IJobScheduler` 集成**：Classification: successor plan required（plan 222 已裁定 `IScheduledExecutor` 足够）。

## Scope

### In Scope

- 新增 `RecoveryMode` 枚举 + `RecoveryOutcome` 数据对象于 `io.nop.ai.agent.runtime.recovery` 包
- 新增 `IOrphanRecoveryHandler` 接口 + `NoOpOrphanRecoveryHandler` 默认 + `DefaultOrphanRecoveryHandler` 功能实现
- `DefaultOrphanRecoveryHandler` RESUME 模式（委托 `engine.restoreSession`，fire-and-forget）+ ABORT 模式（raw JDBC UPDATE status=failed）+ SKIP 模式（LOG.warn）
- `ScheduledRecoveryManager` 新增 `setOrphanRecoveryHandler` setter（默认 NoOp）+ scanOnce orphan handler 集成（detection 后调 handler.handleOrphan）
- `RecoveryScanResult` 扩展 `recoveryActions` 字段（`List<RecoveryOutcome>`）：新增 6-arg 构造器（原 5-arg + `List<RecoveryOutcome> recoveryActions`）；`empty()` 工厂方法返回 `recoveryActions = Collections.emptyList()`；现有调用方 `ScheduledRecoveryManager.scanOnce`（6-arg 构造器调用）+ `NoOpRecoveryManager.scanOnce`（`empty()` 工厂）自动适配
- focused 测试 + 端到端测试（H2 场景）
- 设计文档更新（`nop-ai-agent-actor-runtime-vision.md` §6.3/§6.1）

### Out Of Scope

- 见 Non-Goals（RETRY 模式 / orphan liveness / 超时中止 / 归档 / 心跳续约 / XDSL / 多实例协调 / TeamManager / ResourceGuard / nop-job 集成均为显式 successor）

### 设计裁定

**裁定 1：RESUME 委托给 `engine.restoreSession`，fire-and-forget**——RESUME 模式调用 `engine.restoreSession(sessionId, approver, reason)` 后立即返回（不等 `CompletableFuture` 完成）。理由：(1) daemon scan 循环不应阻塞在单个 session 的恢复上（恢复可能持续数分钟 LLM 调用）；(2) takeover 锁保证同一 session 不会被 double-execute（restoreSession 内部 tryAcquire）；(3) 下次 scan 若 session 仍为 orphan（恢复失败），handler 再次尝试或切换策略；(4) fire-and-forget 与 `supplyAsync` 异步执行模式一致。

**裁定 2：ABORT 用 raw JDBC UPDATE 而非 sessionStore.saveSession**——ABORT 模式直接 `UPDATE ai_agent_session SET STATUS='failed' WHERE SESSION_ID=? AND STATUS IN ('running','pending')`。理由：(1) 与 `DbSessionTakeoverLock` / `ScheduledRecoveryManager` 的 raw JDBC 模式一致（不依赖 ORM entity 加载）；(2) 条件 WHERE 防止 abort 已转换的 session（如另一实例已恢复它）；(3) 轻量（单行 UPDATE，无需加载完整 session 对象）。

**裁定 3：首版不实现 RETRY 模式**——RETRY 需要清空 session 对话历史并重新执行原始 request，涉及 `ISessionStore` 契约扩展（reset 方法）+ 工具副作用 idempotency 裁定（retry 时已执行的工具调用副作用如何处理）。是独立产品策略决策。首版 RESUME（从 checkpoint 续跑）已覆盖主要无人值守恢复场景。

## Execution Plan

### Phase 1 - 契约表面 + NoOp 默认 + 设计裁定落档

Status: completed
Targets: `io.nop.ai.agent.runtime.recovery` 包（枚举 + 数据对象 + 接口 + NoOp 默认）、`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md`

- Item Types: `Decision` | `Proof`

- [x] **裁定并落档** 恢复模式清单：首版 RESUME + ABORT + SKIP，RETRY 为 successor。落档到 `nop-ai-agent-actor-runtime-vision.md` §6.3 + §6.1 恢复策略表，标注裁定理由
- [x] 定义 `RecoveryMode` 枚举：`RESUME` / `ABORT` / `SKIP`，Javadoc 明确每种模式的语义
- [x] 定义 `RecoveryOutcome` 数据对象：`sessionId`（String）+ `mode`（RecoveryMode）+ `succeeded`（boolean）+ `message`（String）
- [x] 定义 `IOrphanRecoveryHandler` 接口：`handleOrphan(String sessionId) → RecoveryOutcome`。Javadoc 明确线程安全契约（可被 daemon 扫描线程并发调用）、NoOp 语义
- [x] 实现 `NoOpOrphanRecoveryHandler`（singleton）：`handleOrphan` 返回 SKIP outcome（LOG.warn orphan session ID），零回归
- [x] `nop-ai-agent-actor-runtime-vision.md` §6.3 标注恢复模式策略（第 2 步）部分落地（RESUME/ABORT/SKIP，RETRY 为 successor）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `RecoveryMode.java` + `RecoveryOutcome.java` + `IOrphanRecoveryHandler.java` + `NoOpOrphanRecoveryHandler.java` 文件存在于 `io.nop.ai.agent.runtime.recovery` 包
- [x] 恢复模式清单已裁定并落档（RESUME/ABORT/SKIP 首版，RETRY successor，设计文档标注裁定理由）
- [x] `NoOpOrphanRecoveryHandler.handleOrphan` 返回 SKIP outcome（LOG.warn，非静默跳过）
- [x] **No new test required for Phase 1**：Phase 1 只交付契约表面（枚举 + 接口 + NoOp 默认），无行为逻辑需测试；NoOp 行为验证在 Phase 2 focused 测试中覆盖（Minimum Rules #25）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - DefaultOrphanRecoveryHandler 功能实现 + daemon 集成 + RecoveryScanResult 扩展 + 测试

Status: completed
Targets: `io.nop.ai.agent.runtime.recovery.DefaultOrphanRecoveryHandler`、`ScheduledRecoveryManager`（`setOrphanRecoveryHandler` setter + scanOnce orphan handler 集成）、`RecoveryScanResult`（recoveryActions 字段扩展 + 6-arg 构造器 + empty() 适配）、`NoOpRecoveryManager`（empty() 适配）、测试

- Item Types: `Proof`

- [x] 实现 `DefaultOrphanRecoveryHandler`：
  - 构造期接收 `RecoveryMode mode` + `IAgentEngine engine`（RESUME 用，RESUME 模式下 engine=null 抛 `NullPointerException`——fail-fast 防止 silent misuse）+ `DataSource dataSource`（ABORT 用，ABORT 模式下 dataSource=null 抛 `NullPointerException`——fail-fast）
  - `RESUME`：调 `engine.restoreSession(sessionId, "recovery-daemon", "orphan auto-recovery")`（fire-and-forget，不 `.join()`），返回 succeeded=true outcome。`restoreSession` 同步抛异常时（tryAcquire 失败 / session 不存在 / 终态 session 等，均在 CompletableFuture 创建前同步抛出）捕获并返回 succeeded=false + message 含异常摘要
  - `ABORT`：raw JDBC `UPDATE ai_agent_session SET STATUS=? WHERE SESSION_ID=? AND STATUS IN ('running','pending')`（STATUS 参数用 `AgentExecStatus.failed.name()`，表名/列名用 `AiAgentSessionTable` 常量，与 `ScheduledRecoveryManager` / `DbSessionTakeoverLock` 的 SQL 构建模式一致），affected rows=1 → succeeded=true，affected rows=0（已转换）→ succeeded=false + message="session already transitioned"
  - `SKIP`：LOG.warn orphan session ID，返回 SKIP outcome
- [x] `RecoveryScanResult` 扩展：新增 `private final List<RecoveryOutcome> recoveryActions` 字段 + 6-arg 构造器（原 5 args + `List<RecoveryOutcome> recoveryActions`）；`empty()` 工厂方法返回 `recoveryActions = Collections.emptyList()`；新增 `getRecoveryActions()` getter。现有调用方 `ScheduledRecoveryManager.scanOnce`（原 5-arg 构造器调用）更新为 6-arg（传入 handler 产出的 outcomes 列表）
- [x] `ScheduledRecoveryManager` 新增 `orphanRecoveryHandler` 字段（默认 `NoOpOrphanRecoveryHandler`）+ `setOrphanRecoveryHandler` setter。`scanOnce` 在 orphan detection 循环中对每个 `orphanSessionId` 调 `handler.handleOrphan(sessionId)`，收集 `RecoveryOutcome` 列表，传入 `RecoveryScanResult` 6-arg 构造器
- [x] 编写 `DefaultOrphanRecoveryHandler` focused 测试：
  - RESUME 模式：mock engine → handler.handleOrphan(orphanId) → 断言 `engine.restoreSession` 被调用 + outcome.succeeded=true
  - RESUME 模式 takeover 冲突：mock engine.restoreSession 抛异常（模拟 tryAcquire 失败）→ outcome.succeeded=false + message 含 "takeover"
  - ABORT 模式：H2 插入 running session → handler.handleOrphan → 断言 DB status=failed + outcome.succeeded=true
  - ABORT 模式已转换：H2 插入 completed session → handler.handleOrphan → affected rows=0 + outcome.succeeded=false
  - SKIP 模式：handler.handleOrphan → LOG.warn + outcome.mode=SKIP
- [x] 编写 daemon 集成测试：H2 + `ScheduledRecoveryManager` + `DefaultOrphanRecoveryHandler`(ABORT) → 插入 orphan session → scanOnce → 断言 session status=failed + `RecoveryScanResult.recoveryActions` 含 succeeded=true outcome
- [x] 编写 NoOp 默认零回归测试：`NoOpOrphanRecoveryHandler` 下 scanOnce 行为与 plan 222 shipped 默认一致（LOG.warn orphan，无副作用）+ `RecoveryScanResult.empty()` 的 `recoveryActions` 为 `emptyList()`
- [x] 编写 `ScheduledRecoveryManager.setOrphanRecoveryHandler` setter 注入测试：默认 handler instanceof NoOp → `manager.setOrphanRecoveryHandler(handler)` → 断言 scanOnce 调用注入的 handler（非 NoOp）
- [x] 编写端到端测试：H2 DB + `ScheduledRecoveryManager` + `DefaultOrphanRecoveryHandler`(ABORT) → 手动插入 orphan session 行 → `scanOnce` → 断言 session status=failed + `RecoveryScanResult` 动作摘要正确

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `DefaultOrphanRecoveryHandler` 正确实现 RESUME（fire-and-forget restoreSession，同步异常捕获）/ ABORT（raw JDBC UPDATE status=failed，用 `AiAgentSessionTable` 常量 + `AgentExecStatus.failed.name()`）/ SKIP（LOG.warn）三种模式
- [x] `RecoveryScanResult` 6-arg 构造器 + `recoveryActions` 字段 + `getRecoveryActions()` getter + `empty()` 返回 `recoveryActions=emptyList()` 已落地；现有调用方（`ScheduledRecoveryManager.scanOnce` / `NoOpRecoveryManager.scanOnce`）已适配
- [x] `ScheduledRecoveryManager` 新增 `setOrphanRecoveryHandler` setter（默认 NoOp）；`scanOnce` 在 orphan detection 后调用 `handler.handleOrphan`，结果汇总到 `RecoveryScanResult.recoveryActions`
- [x] **接线验证**（Minimum Rules #23）：`ScheduledRecoveryManager` 持有 `IOrphanRecoveryHandler` 引用；`scanOnce` 运行时确实调用 `handler.handleOrphan`（测试断言 handler 被调用 + DB 状态变化）；`setOrphanRecoveryHandler` setter 正确赋值（注入非 NoOp handler 后 scanOnce 调用注入的 handler）
- [x] **端到端验证**（Minimum Rules #22）：H2 + orphan session + ABORT handler → scanOnce → session status=failed + RecoveryScanResult 动作摘要正确
- [x] **无静默跳过**（Minimum Rules #24）：RESUME 冲突时返回 succeeded=false outcome（非静默忽略）；ABORT affected rows=0 时返回 succeeded=false（非静默）；SKIP 模式 LOG.warn（非静默）；所有异常路径有 LOG.warn 或 outcome 记录
- [x] shipped 默认（`NoOpOrphanRecoveryHandler`）下既有测试零回归
- [x] 新增功能各有对应 focused 测试覆盖（RESUME / RESUME-conflict / ABORT / ABORT-transitioned / SKIP / daemon-integration / NoOp-regression / setter-injection / E2E 各有测试）
- [x] `nop-ai-agent-actor-runtime-vision.md` §6.3/§6.1 已更新（标注恢复模式策略部分落地 + RETRY 为 successor）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `RecoveryMode` 枚举 + `RecoveryOutcome` 数据对象 + `IOrphanRecoveryHandler` 接口 + `NoOpOrphanRecoveryHandler` shipped 默认已落地
- [x] `DefaultOrphanRecoveryHandler` 功能实现（RESUME/ABORT/SKIP 三模式）已落地
- [x] `ScheduledRecoveryManager.scanOnce` orphan handler 集成已落地（detection 后调 handler）+ `setOrphanRecoveryHandler` setter 已落地
- [x] `RecoveryScanResult` 6-arg 构造器 + `recoveryActions` 字段 + `empty()` 适配已落地（现有调用方无回归）
- [x] 端到端：H2 + orphan session + handler → scanOnce → 恢复/abort/观测完整路径跑通
- [x] shipped 默认（NoOp）下既有测试零回归
- [x] 必要 focused verification 已完成（RESUME / RESUME-conflict / ABORT / ABORT-transitioned / SKIP / daemon-integration / NoOp-regression / setter-injection / E2E 各有测试）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（RETRY / orphan liveness / 超时中止 / 归档 / 心跳续约 / XDSL / 多实例协调 / TeamManager / ResourceGuard / nop-job 集成均显式在 Non-Goals 切出）
- [x] 受影响 owner docs（`nop-ai-agent-actor-runtime-vision.md` §6.3/§6.1）已同步到 live baseline
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`handler.handleOrphan` 在运行时被 `ScheduledRecoveryManager.scanOnce` 调用（不只类型存在），（b）RESUME/ABORT 路径在运行时确实执行（不只方法存在），（c）无空方法体/静默跳过作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；RETRY 模式 / orphan liveness / 超时中止 / 归档 / 心跳续约 / XDSL / 多实例协调 / TeamManager / ResourceGuard / nop-job 集成均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **RETRY 恢复模式**（vision §6.3"从头重试整个请求"）：需要 session 状态重置语义 + 工具副作用 idempotency 裁定。Classification: successor plan required。
- **orphan 进程主动 liveness 检测**（vision §6.3"检查进程 ID 是否存活"）：主动检测锁持有者进程存活状态。Classification: successor plan required。
- **超时强制中止**（vision §6.3 第 3 步）：超时 session 经 cancel 强制中止。Classification: successor plan required。
- **归档清理**（vision §6.3 第 4 步）：terminal session 归档。Classification: optimization candidate。
- **心跳自动续约**：长时间执行自动续约 lease。Classification: optimization candidate。
- **XDSL 配置化**：`agent.xdef` 增加 `<recovery>` 元素配置 recovery mode。Classification: optimization candidate。
- **多实例恢复协调**：多实例同时运行 daemon 时的恢复去重。Classification: optimization candidate。
- **TeamManager + ResourceGuard + Fencing Token**（vision §10 Phase 3/5）。Classification: successor plan required。
- **nop-job `IJobScheduler` 集成**。Classification: successor plan required。

## Closure

Status Note: 恢复模式策略（RESUME/ABORT/SKIP）已完整落地。`IOrphanRecoveryHandler` 可插拔策略契约 + `NoOpOrphanRecoveryHandler` shipped 默认（SKIP，零回归）+ `DefaultOrphanRecoveryHandler` 功能实现（RESUME 委托 restoreSession fire-and-forget + takeover 锁防 double-execution / ABORT raw JDBC UPDATE status=failed 条件 WHERE / SKIP LOG.warn）+ `ScheduledRecoveryManager` 集成（scanOnce orphan detection 后调 handler，汇总到 RecoveryScanResult.recoveryActions）+ RecoveryScanResult 6-arg 扩展。33 focused/E2E 测试全绿。RETRY 及其余 RecoveryManager 能力（liveness/超时中止/归档/心跳续约）均为显式 Non-Goals successor。
Completed: 2026-06-17

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (task ses_12e18b422ffeMAt8MpmCNkkfac, general agent, fresh session)
- Audit Session: ses_12e18b422ffeMAt8MpmCNkkfac
- Evidence:
  - Phase 1 Items 1-4: PASS — `RecoveryMode.java:43-70` (RESUME/ABORT/SKIP) + `RecoveryOutcome.java:38-66` (4 fields + getters) + `IOrphanRecoveryHandler.java:38-49` (handleOrphan) + `NoOpOrphanRecoveryHandler.java:28-51` (singleton + LOG.warn SKIP outcome, non-silent)
  - Phase 2 Items 5-7: PASS — `DefaultOrphanRecoveryHandler.java` RESUME(fire-and-forget restoreSession L140 + sync exception catch L143→succeeded=false) / ABORT(raw JDBC UPDATE L160-163 + AiAgentSessionTable constants + AgentExecStatus.failed.name() L166 + rows=0→succeeded=false L176) / SKIP(LOG.warn L194) + constructor fail-fast(L96-103 NPE); `RecoveryScanResult.java:56-67` 6-arg constructor + `getRecoveryActions()` L107 + `empty()` emptyList L75; `ScheduledRecoveryManager.java:101` default NoOp field + `setOrphanRecoveryHandler` L151(null rejected) + scanOnce L251 runtime `handleOrphan` call
  - Anti-Hollow Item 8 (Wiring): PASS — `ScheduledRecoveryManager.scanOnce()` L251 `orphanRecoveryHandler.handleOrphan(orphanId)` is a runtime dispatch inside the orphan loop, confirmed by `setOrphanRecoveryHandlerInjectsNonNoOpHandler` test asserting `handler.callCount == 1`
  - Anti-Hollow Item 9 (No Silent No-Op): PASS — RESUME conflict→succeeded=false (L149), ABORT rows=0→succeeded=false (L176), SKIP→LOG.warn (L194), SQLException→succeeded=false (L183); no swallowed exceptions
  - Anti-Hollow Item 10 (No empty bodies): PASS — every method body contains substantive logic
  - Tests Items 11-12: PASS — `TestDefaultOrphanRecoveryHandler` (9 tests) covers RESUME/RESUME-conflict/ABORT/ABORT-transitioned/SKIP/fail-fast; `TestScheduledRecoveryManager` (24 tests, +8 new) covers default-NoOp/setter-injection/NoOp-regression/E2E-ABORT
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`: BUILD SUCCESS (33 recovery tests + full module suite green)
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high`: Total=0 findings
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict`: exit code 0 (after Closure evidence written)
  - Deferred 项分类检查: RETRY / orphan liveness / 超时中止 / 归档 / 心跳续约 / XDSL / 多实例协调 / TeamManager / ResourceGuard / nop-job 集成均显式在 Non-Goals 切出为独立 successor，无 in-scope live defect 被降级

Follow-up:

- no remaining plan-owned work（RETRY 模式及其他 RecoveryManager 能力均为显式 Non-Goals successor，见 Non-Blocking Follow-ups）

## Follow-up handled by 229-nop-ai-agent-session-timeout-forced-abort.md

超时强制中止（Non-Blocking Follow-ups 第三条，标 `successor plan required`）已由 successor plan `ai-dev/plans/229-nop-ai-agent-session-timeout-forced-abort.md` 接管：交付 `ISessionTimeoutHandler` 可插拔策略 + `TimeoutAction` 枚举 + `TimeoutOutcome` 数据对象 + `NoOpSessionTimeoutHandler` shipped 默认（SKIPPED，零回归）+ `DefaultSessionTimeoutHandler` 三分裁定（LOCAL_CANCELLED 经 engine.cancelSession forced / FORCE_FAILED raw JDBC UPDATE status=failed / SKIPPED_REMOTE 不干预）+ `ScheduledRecoveryManager` scanOnce 集成（timeout 检测先于 orphan detection + `setSessionTimeoutHandler` setter + `RecoveryScanResult.timeoutActions` 7-arg 扩展）。此段为事实性交叉引用追加，不修改本计划已关闭的 closure 内容。
