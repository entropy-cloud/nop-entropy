# 221 nop-ai-agent 跨进程 Session 接管锁（RecoveryManager Phase 1）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-8-P4

> Last Reviewed: 2026-06-16
> Source: carry-over from `ai-dev/plans/218-nop-ai-agent-actor-runtime.md`（Non-Blocking Follow-ups 第四条：`RecoveryManager + 跨进程接管锁 + orphan 检测（vision §6 / §10 Phase 4）... Classification: successor plan required（依赖 nop-job）`）；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 4 验收标准 line 253（"跨进程接管锁依赖 L4-8 Actor Runtime，是独立 successor"）；`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md` §6.3（RecoveryManager 工作流）+ §10 Phase 4
> Related: `218`（交付 Actor Runtime 基础原语，本计划是其 Phase 4 successor）、`183`/`184`/`185`（L3-4b/c/d session restore 链——restoreSession / restorePendingSessions / DBSessionStore 全部 ✅，本计划补齐其跨进程并发安全 gap）、`197`（AUDIT-14-01 单进程并发 guard `putIfAbsent` + fail-fast，本计划扩展为跨进程）

## Purpose

把 nop-ai-agent 的 session 恢复从"单进程内 `runningExecutions` putIfAbsent 并发保护"扩展为"跨进程 DB-backed 接管锁 + lease/TTL 自动过期"。本计划只负责这一件事：在多实例共享 DB 部署中，防止两个 JVM 实例同时恢复并执行同一个 crashed/pending session（double-execution correctness gap）。

RecoveryManager 的其余能力（nop-job 定时扫描、orphan 进程 liveness 检测、恢复模式策略 resume/retry/abort、超时强制中止、归档清理）均为显式 Non-Goal 独立 successor。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-16）：

- **`runningExecutions` 单进程并发保护 ✅**（plan 197 / AUDIT-14-01）：`DefaultAgentEngine` 的三个执行入口点（`doExecute` / `resumeSession` / `restoreSession`）经 `runningExecutions.putIfAbsent(sessionId, handle)` + fail-fast（`NopAiAgentException`）+ 值比较 `remove(sessionId, handle)` 提供单 JVM 内 session 并发保护。**此 map 是 `ConcurrentHashMap<String, CancelHandle>` 进程内数据结构，不可跨进程共享。**
- **`DBSessionStore` ✅**（L3-4d / plan 185）：session 状态持久化到 `ai_agent_session` 表（`SESSION_ID` PK + `STATUS` VARCHAR(30) 索引列 + `SESSION_DATA` CLOB + `CREATED_AT` / `UPDATED_AT` BIGINT）。**任何共享同一 DB 的服务实例可经 `loadFromDb(sessionId)` 加载 session。** 表 schema 定义在 `AiAgentSessionTable.java`，当前**无** lock 相关列（`LOCK_OWNER` / `LOCK_EXPIRES_AT` 等）。
- **`restoreSession` ✅**（L3-4b / plan 183）：单 session 恢复原语——从 store 加载 → 校验非 terminal → 切换 `running` → 构建 ctx → `putIfAbsent` + 异步执行 → finally persist。`AgentExecStatus` 非 terminal 值 = `pending` / `running`（crash 恢复候选）；terminal 值 = `completed` / `failed` / `cancelled` / `forced_stopped` / `escalated`；`paused` = governance（sticky-pause，需人工 resume）。
- **`restorePendingSessions` ✅**（L3-4c / plan 184）：批量 auto-restore-on-startup——`sessionStore.listAllSessions()` → 过滤 `running` / `pending` → 逐个委托 `restoreSession`（sequential + per-session failure isolation）→ 返回 `SessionRestoreSummary`（restored / skipped / failed）。
- **跨进程接管锁 gap（本计划闭合）**：在多实例部署中（实例 A、B 共享 DB），当实例 A crash 后 session X 的 DB 行 status 仍为 `running`。实例 B 启动 `restorePendingSessions` 发现 X → `restoreSession(X)`。**若同时实例 C 也启动 `restorePendingSessions`**，C 同样发现 X → `restoreSession(X)`。B 和 C 的 `runningExecutions` 是各自进程内的独立 map——`putIfAbsent` 无法阻止跨进程并发。**结果：X 被 B、C 同时恢复执行，产生 double-execution correctness 缺陷**（LLM 调用重复、工具副作用重复、session 状态被互相覆盖）。roadmap §4 line 253 明确声明此 gap 为"独立 successor"。
- **`ISessionTakeoverLock` / `DbSessionTakeoverLock` 零实现**：grep `RecoveryManager|TakeoverLock|ISessionTakeoverLock|crossProcessLock` 在 `nop-ai/nop-ai-agent/src/main` 返回 0 类定义命中（`AgentActorStatus.java` 的 Javadoc 提及 `RecoveryManager` 作为 Phase 4 successor，非实现）。
- **`nop-job` 模块存在**：`nop-job/` 目录存在于仓库根。本计划**不**集成 nop-job（Non-Goal），只交付 opt-in 编程式 API。
- **engine 无 instanceId**：`DefaultAgentEngine` 当前无 instance/process 标识字段。跨进程锁需要唯一标识持有者以实现 conditional release（只释放自己持有的锁）。

## Goals

- **`ISessionTakeoverLock` 接口**：`tryAcquire(sessionId, ownerId, leaseMs) → boolean`（CAS 语义：成功返回 true，被其他活跃 owner 持有返回 false——非异常控制流，与 `IActorRuntime.isEnabled()` 模式一致）、`release(sessionId, ownerId) → boolean`（conditional release：只释放自己持有的锁，防止误释放他人的锁）、`isHeld(sessionId) → boolean`（检查是否被活跃 owner 持有，用于 `restorePendingSessions` 跳过判断）、`tryRenew(sessionId, ownerId, leaseMs) → boolean`（延长租约，用于长时间执行场景——预留接口，首版可不自动调用）。
- **`NoOpSessionTakeoverLock` shipped 默认**：singleton；`tryAcquire` 恒返回 true（无锁，单进程部署依赖既有 `runningExecutions`）；`release` / `tryRenew` no-op；`isHeld` 恒返回 false。引擎默认使用它，**零行为回归**。
- **`DbSessionTakeoverLock` 功能实现**：基于独立 `ai_agent_session_lock` 表的 lease/TTL 锁。`tryAcquire` 经 SQL CAS 原子操作（INSERT-or-conditional-UPDATE）获取锁 + 设置 `LOCK_EXPIRES_AT = now + leaseMs`；若已有活跃锁（`LOCK_EXPIRES_AT > now`）且 owner 不同 → 返回 false；若已有锁已过期（`LOCK_EXPIRES_AT ≤ now`）或 owner 相同 → 抢占/续约成功。`release` 经 conditional DELETE（`WHERE SESSION_ID=? AND LOCK_OWNER=?`）只释放自己的锁。锁表 schema 定义在 `AiAgentSessionLockTable.java`（`SESSION_ID` PK + `LOCK_OWNER` VARCHAR + `LOCK_ACQUIRED_AT` BIGINT + `LOCK_EXPIRES_AT` BIGINT）。
- **引擎 instanceId + leaseMs**：`DefaultAgentEngine` 构造期生成 `instanceId`（`UUID.randomUUID().toString()`），作为跨进程锁的 owner 标识。新增 `lockLeaseMs` 字段（`long`，默认 `1_800_000L` = 30min）+ `setLockLeaseMs` setter。
- **引擎接线**：`DefaultAgentEngine` 新增 `sessionTakeoverLock` 字段（默认 `NoOpSessionTakeoverLock`）+ `setSessionTakeoverLock` setter。三个执行入口点（`doExecute` / `resumeSession` / `restoreSession`）在 `putIfAbsent` **之前**先 `tryAcquire(sessionId, instanceId, lockLeaseMs)`——若返回 false 则 fail-fast 抛 `NopAiAgentException`；三个清理路径（putIfAbsent 失败 throw / 外层 catch / 内层 finally）均经 `releaseLockQuietly` 释放锁。shipped 默认（NoOp）走既有路径不变。
- **`restorePendingSessions` 增强**：在遍历候选 session 时，先经 `sessionTakeoverLock.isHeld(sessionId)` 检查——若已被其他实例持有，加入 `skipped` 桶（原因 "locked by another instance"）而非尝试后失败加入 `failed` 桶（减少无谓的 restore 尝试 + 日志噪音）。
- **focused 测试**：`DbSessionTakeoverLock` 的 acquire/release/renew/stale-expiry/conditional-release（两个 owner 实例，一个 acquire 成功另一个失败，过期后抢占成功）各有覆盖。
- **端到端验证**：两个 `DbSessionTakeoverLock` 实例（模拟两个 JVM 进程）共享同一 H2 DB → 实例 A `tryAcquire("s1")` 成功 → 实例 B `tryAcquire("s1")` 返回 false → 实例 A `release("s1")` → 实例 B `tryAcquire("s1")` 成功；过期场景：实例 A acquire（leaseMs=100）→ 等 200ms → 实例 B `tryAcquire` 成功（stale lock 抢占）。
- **roadmap §4 验收标准 line 253**：从 `[x] ...跨进程接管锁依赖 L4-8 Actor Runtime，是独立 successor` 更新为标注本 plan 已交付接管锁基础层。
- **设计文档更新**：`nop-ai-agent-actor-runtime-vision.md` §6.3 / §10 Phase 4 标注接管锁已落地；`01-architecture-baseline.md` 扩展点清单补 `ISessionTakeoverLock`。

## Non-Goals

- **nop-job 定时扫描集成**（vision §6.3 第 1 步"定时任务，每 60 秒"）：RecoveryManager 作为后台定时扫描的 daemon 是独立 successor，依赖 nop-job 调度基础设施集成。本计划只交付 opt-in 编程式 API（`tryAcquire` / `release`），不启动后台扫描线程。Classification: successor plan required。
- **orphan 进程 liveness 检测**（vision §6.3"检查进程 ID 是否存活"）：检测锁持有者进程是否仍存活（而非仅依赖 lease TTL 过期）需要进程注册/心跳机制。本计划的 lease/TTL 机制提供了"被动过期"替代方案（锁持有者 crash 后，等待 TTL 到期，其他实例可抢占）。主动 liveness 检测是独立 successor。Classification: successor plan required。
- **恢复模式策略（resume/retry/abort）**（vision §6.3 第 2 步）：恢复 orphaned session 时选择从 checkpoint 恢复（resume）、从头重试（retry）、还是标记失败（abort）是产品策略决策。本计划只交付锁机制（防 double-execution），不裁定恢复策略。Classification: successor plan required。
- **超时强制中止**（vision §6.3 第 3 步）：`status=running` 且 `lastActiveAt` 超过 `maxWallClockMinutes` → `ICancelToken.cancel()` → 强制标记 failed。需要定时扫描 + cancel 接入。Classification: successor plan required。
- **归档清理**（vision §6.3 第 4 步）：`status=stopped` 且 `stoppedAt` 超过 24h → 归档到历史表。Classification: optimization candidate。
- **心跳自动续约**：`tryRenew` 接口在首版定义但**不**自动调用（长时间 ReAct 执行不自动续约 lease）。若 lease TTL 不足以覆盖执行时长，锁会在执行中途过期被其他实例抢占——这是**有意的 fail-safe 设计**（宁可让慢执行被抢占，也不让 crash 后锁永不释放）。自动心跳续约需要后台线程或 Actor 消费循环集成，是独立 successor。Classification: optimization candidate。
- **TeamManager + ResourceGuard + Fencing Token**（vision §10 Phase 3/5）：团队生命周期、资源配额、协调信道均为独立 successor。
- **XDSL 配置化**：`agent.xdef` 增加 `<takeover-lock>` 元素。当前通过编程 API 配置。Classification: optimization candidate。
- **`doExecute` 新 session 保护**：`doExecute` 创建新 session（`getOrCreate`），不存在跨进程冲突。只有 `doExecute` 对**既有** session 的重新执行才需要锁——但此场景在当前 API 契约下不是标准用法（新 session 有唯一新生成的 sessionId）。如后续支持"对既有 session 发起新请求"，可再追加锁保护。

## Scope

### In Scope

- 新增 `io.nop.ai.agent.runtime.lock` 包：`ISessionTakeoverLock` 接口 + `NoOpSessionTakeoverLock` 默认 + `DbSessionTakeoverLock` 功能实现 + `AiAgentSessionLockTable` schema 常量
- `DefaultAgentEngine` 新增 `instanceId`（UUID）+ `sessionTakeoverLock` 字段（默认 NoOp）+ `setSessionTakeoverLock` setter
- 三个执行入口点（`doExecute` / `resumeSession` / `restoreSession`）的 `tryAcquire` + `release` 接线
- `restorePendingSessions` 的 `isHeld` 跳过增强
- focused 测试 + 端到端测试（双实例 H2 共享 DB 场景）
- 设计文档更新（`nop-ai-agent-actor-runtime-vision.md` + `01-architecture-baseline.md`）
- roadmap §4 验收标准 line 253 更新

### Out Of Scope

- 见 Non-Goals（nop-job 集成 / orphan liveness / 恢复策略 / 超时中止 / 归档 / 心跳续约 / TeamManager / ResourceGuard / XDSL 均为显式 successor）

### 设计裁定

**裁定 1：独立锁表 vs session 表加列**——采用**独立 `ai_agent_session_lock` 表**而非在 `ai_agent_session` 表加 `LOCK_OWNER` / `LOCK_EXPIRES_AT` 列。理由：(1) 锁是临时运行时状态，session 行是持久化业务状态，关注点分离；(2) 独立表可用 `DELETE` 释放锁（行删除），session 表加列只能 `UPDATE` 回 null（遗留空列）；(3) 独立表的 PK = `SESSION_ID` 保证一个 session 至多一把锁，语义清晰；(4) 与 `DBSessionStore` 的 raw JDBC 模式一致（不依赖 ORM entity）。

**裁定 2：lease/TTL 过期 vs 心跳续约 + leaseMs 来源**——采用**被动 lease/TTL 过期**，不启动心跳续约后台线程。`tryAcquire` 设置 `LOCK_EXPIRES_AT = now + leaseMs`。**leaseMs 来源**：`DefaultAgentEngine` 新增 `lockLeaseMs` 字段（类型 `long`，默认 `1_800_000L` = 30min）+ `setLockLeaseMs` setter（集成商可按 LLM 超时配置调整）。三个执行入口点经 `sessionTakeoverLock.tryAcquire(sessionId, instanceId, this.lockLeaseMs)` 传入。锁持有者 crash 后，其他实例在 TTL 到期后可经 CAS 抢占（`WHERE LOCK_EXPIRES_AT <= ?`）。理由：(1) 无后台线程 = 无线程管理复杂度（Java 11 无 VT，`ScheduledExecutorService` 需要额外线程）；(2) 宁可让慢执行被抢占也不让 crash 后锁永不释放（fail-safe）；(3) `tryRenew` 接口在首版定义但不自动调用——集成商可在长执行场景手动调用（如每 N 轮 ReAct 迭代后续约），自动续约是 successor。

**裁定 3：tryAcquire 返回 boolean（非异常控制流）**——`tryAcquire` 返回 `boolean`（true = 获取成功，false = 被其他活跃 owner 持有），不抛异常。引擎在 `tryAcquire` 返回 false 时抛 `NopAiAgentException` fail-fast。理由：(1) 与 `IActorRuntime.isEnabled()` 返回 boolean 的设计模式一致（plan 218 裁定 5）；(2) `restorePendingSessions` 需要区分"锁竞争"（应 skip）vs"其他失败"（应 fail）——boolean 返回值使调用方能在 skip/fail 间裁定，而非解析异常消息。

**裁定 4：三个执行入口点均加锁**——`doExecute` / `resumeSession` / `restoreSession` 三个入口点均在 `putIfAbsent` 前先 `tryAcquire`。理由：`resumeSession`（显式恢复 paused session）和 `doExecute`（对既有 sessionId 发起新执行）在多实例部署中同样存在跨进程并发风险。虽然 `doExecute` 新建 session 场景无冲突（唯一新 sessionId），但对既有 sessionId 的 `doExecute` 理论上有冲突——三个入口点统一加锁是最安全的选择，且 NoOp 默认下零成本。`restorePendingSessions` 额外经 `isHeld` 预检查以减少 skip 日志噪音。

**裁定 5：锁释放全路径覆盖 + release 容错**——每个执行入口点有**三个**需要释放锁的路径，全部必须覆盖：

1. **`putIfAbsent` 失败路径**（`existing != null` throw）：`tryAcquire` 成功后、`putIfAbsent` 返回非 null → 抛异常前必须先 `release`。写法：将 `tryAcquire` + `putIfAbsent` 包在同一个 try 块中，catch 块释放锁后 rethrow。
2. **外层 catch 路径**（`supplyAsync` 本身抛异常，如 `RejectedExecutionException`）：现有 `catch (RuntimeException e) { runningExecutions.remove(sessionId, handle); throw e; }` 中追加 `releaseLockQuietly(sessionId, instanceId)`。
3. **内层 finally 路径**（lambda 内正常清理）：现有 `finally { runningExecutions.remove(sessionId, handle); ... }` 中追加 `releaseLockQuietly(sessionId, instanceId)`。

**`releaseLockQuietly` 私有辅助方法**：包裹 `sessionTakeoverLock.release(sessionId, instanceId)` 在 try-catch 中，release 失败（DB 连接断开等）仅 `LOG.warn`，不抛异常、不阻断后续清理。理由：锁的 TTL 机制保证即使 release 失败，锁也会在 TTL 后自动过期——宁可让 stale lock 短暂存在也不让 session persist 失败导致数据丢失。NoOp 默认下 `release` 恒返回 true（no-op），无异常。

## Execution Plan

### Phase 1 - 契约表面 + NoOp 默认 + 设计裁定落档

Status: completed
Targets: `io.nop.ai.agent.runtime.lock` 包（新增接口 + NoOp 默认）、`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md`、`ai-dev/design/nop-ai-agent/01-architecture-baseline.md`

- Item Types: `Decision` | `Proof`

- [x] **裁定并落档** 独立锁表 vs session 表加列（裁定 1）+ lease/TTL 过期 vs 心跳续约（裁定 2）+ tryAcquire 返回 boolean（裁定 3）+ 三入口点统一加锁（裁定 4）+ 锁释放容错（裁定 5），写入设计文档
- [x] 定义 `ISessionTakeoverLock` 接口：`tryAcquire(sessionId, ownerId, leaseMs) → boolean` / `release(sessionId, ownerId) → boolean` / `isHeld(sessionId) → boolean` / `tryRenew(sessionId, ownerId, leaseMs) → boolean`。Javadoc 明确：CAS 语义（tryAcquire 原子性）、conditional release（只释放自己持有的锁）、lease/TTL 过期语义（stale lock 可被抢占）、线程安全契约
- [x] 定义 `AiAgentSessionLockTable` schema 常量：`TABLE_NAME = "ai_agent_session_lock"` + 列名常量（`COL_SESSION_ID` / `COL_LOCK_OWNER` / `COL_LOCK_ACQUIRED_AT` / `COL_LOCK_EXPIRES_AT`）+ `DDL_CREATE_TABLE`（`SESSION_ID` PK + `LOCK_OWNER` VARCHAR(200) + `LOCK_ACQUIRED_AT` BIGINT + `LOCK_EXPIRES_AT` BIGINT）
- [x] 实现 `NoOpSessionTakeoverLock`（singleton）：`tryAcquire` 恒返回 true；`release` / `tryRenew` 恒返回 true（no-op）；`isHeld` 恒返回 false
- [x] `nop-ai-agent-actor-runtime-vision.md` §6.3 RecoveryManager 工作流标注"接管锁基础层已落地（lease/TTL，独立锁表，opt-in 编程式 API）"；§10 Phase 4 标注接管锁 ✅ + nop-job 定时扫描 / orphan liveness / 恢复策略 / 超时中止 / 归档清理标 successor
- [x] `01-architecture-baseline.md` 扩展点清单补 `ISessionTakeoverLock`（与 `IActorRuntime` / `IMailbox` 同级 opt-in 扩展点描述）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ISessionTakeoverLock.java` + `NoOpSessionTakeoverLock.java` + `AiAgentSessionLockTable.java` 文件存在于 `io.nop.ai.agent.runtime.lock` 包
- [x] `NoOpSessionTakeoverLock.tryAcquire` 恒返回 true，`isHeld` 恒返回 false，`release`/`tryRenew` no-op
- [x] `AiAgentSessionLockTable.DDL_CREATE_TABLE` 包含 `SESSION_ID` PK + `LOCK_OWNER` + `LOCK_ACQUIRED_AT` + `LOCK_EXPIRES_AT` 四列
- [x] 设计文档（`nop-ai-agent-actor-runtime-vision.md` §6.3/§10 + `01-architecture-baseline.md`）已标注接管锁已落地 + successor 清单
- [x] **No new test required for Phase 1**：Phase 1 只交付契约表面（接口 + NoOp 默认 + schema 常量），无行为逻辑需测试；NoOp 行为验证在 Phase 2 focused 测试中覆盖（Minimum Rules #25）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - DbSessionTakeoverLock 功能实现 + 引擎接线 + 测试

Status: completed
Targets: `io.nop.ai.agent.runtime.lock.DbSessionTakeoverLock`、`DefaultAgentEngine`（`instanceId` + `sessionTakeoverLock` 字段 + 三入口点接线 + `restorePendingSessions` 增强）、`io.nop.ai.agent.runtime.lock` 测试

- Item Types: `Proof` | `Follow-up`

- [x] 实现 `DbSessionTakeoverLock`：构造器接收 `DataSource`，`initSchema()` 创建 `ai_agent_session_lock` 表（参照 `DBSessionStore.initSchema` 模式）；`tryAcquire` 经 SQL CAS（先尝试 INSERT，duplicate-key 时尝试 conditional UPDATE `WHERE LOCK_EXPIRES_AT <= ?`，affected rows = 1 则成功）；`release` 经 conditional DELETE（`WHERE SESSION_ID=? AND LOCK_OWNER=?`）；`isHeld` 经 SELECT 检查 `LOCK_EXPIRES_AT > now`；`tryRenew` 经 conditional UPDATE（`WHERE SESSION_ID=? AND LOCK_OWNER=?` 设置新 `LOCK_EXPIRES_AT`）
- [x] `DefaultAgentEngine` 新增 `instanceId` 字段（`UUID.randomUUID().toString()`，构造期生成，不可变）+ `lockLeaseMs` 字段（`long`，默认 `1_800_000L` = 30min）+ `setLockLeaseMs` setter + `sessionTakeoverLock` 字段（默认 `NoOpSessionTakeoverLock`）+ `setSessionTakeoverLock` setter + 私有辅助方法 `releaseLockQuietly(sessionId, ownerId)`（try-catch 包裹 release，失败仅 LOG.warn）
- [x] 三个执行入口点（`doExecute` / `resumeSession` / `restoreSession`）接线——**(a)** 在 `putIfAbsent` **之前**插入 `tryAcquire`：`if (!sessionTakeoverLock.tryAcquire(sessionId, instanceId, lockLeaseMs)) { throw new NopAiAgentException("session is locked by another instance: " + sessionId); }`；**(b)** putIfAbsent 失败路径：在 `existing != null` 的 throw 前，先 `releaseLockQuietly(sessionId, instanceId)`（裁定 5 路径 1——将 tryAcquire + putIfAbsent 包在同一 try 块，catch 中 release 后 rethrow）；**(c)** 外层 catch 路径：在现有 `catch (RuntimeException e) { runningExecutions.remove(sessionId, handle); ... }` 中追加 `releaseLockQuietly(sessionId, instanceId)`（裁定 5 路径 2）；**(d)** 内层 finally 路径：在 lambda 内 `finally { runningExecutions.remove(sessionId, handle); ... }` 中追加 `releaseLockQuietly(sessionId, instanceId)`（裁定 5 路径 3）。NoOp 默认下所有路径 no-op 零回归
- [x] `restorePendingSessions` 遍历候选 session 时，在 `restoreSession` 调用前先 `if (sessionTakeoverLock.isHeld(sessionId)) { skipped.add(new SkipEntry(sessionId, status, "locked by another instance")); continue; }`
- [x] 编写 `DbSessionTakeoverLock` focused 测试：tryAcquire 成功 / tryAcquire 被 held 锁阻止（返回 false）/ release conditional（只释放自己的锁，不误释放他人的）/ stale lock 过期抢占（acquire → sleep > leaseMs → 另一 owner acquire 成功）/ tryRenew 续约（续约后原 TTL 内不被抢占）/ isHeld 判断（active vs expired）
- [x] 编写 NoOp 默认零回归测试：`NoOpSessionTakeoverLock` 下既有 `TestRestoreSession` / `TestRestorePendingSessions` / `TestDefaultAgentEngineConcurrencyGuard` 等全部通过（shipped 默认不改变行为）
- [x] 编写引擎接线测试：配置 `DbSessionTakeoverLock`（H2 DataSource）→ `engine.setSessionTakeoverLock(lock)` → `restoreSession` 前锁表有行 → restore 完成后锁表行被删除 → restore 期间另一 engine 实例 `tryAcquire` 同 sessionId 返回 false
- [x] 编写端到端测试：两个 `DbSessionTakeoverLock` 实例共享 H2 → A tryAcquire("s1") 成功 → B tryAcquire("s1") 返回 false → B isHeld("s1") 返回 true → A release("s1") → B tryAcquire("s1") 成功；过期场景：A acquire(leaseMs=100) → sleep(200) → B tryAcquire 成功（stale 抢占）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `DbSessionTakeoverLock` 正确实现 CAS acquire + conditional release + lease 过期抢占 + tryRenew（focused 测试全绿）
- [x] `DefaultAgentEngine.instanceId` 在构造期生成（UUID），`sessionTakeoverLock` 默认 `NoOpSessionTakeoverLock`
- [x] **接线验证**（Minimum Rules #23）：三个执行入口点在 `putIfAbsent` 前调用 `tryAcquire`（断言锁表有行），三个 `finally` 块调用 `release`（断言锁表行被删除）；`tryAcquire` 返回 false 时抛 `NopAiAgentException` fail-fast
- [x] **端到端验证**（Minimum Rules #22）：双实例共享 H2 DB → 实例 A acquire 成功 → 实例 B acquire 返回 false → A release → B acquire 成功；stale lock 过期抢占路径跑通
- [x] **无静默跳过**（Minimum Rules #24）：`tryAcquire` 返回 false 时引擎 fail-fast 抛异常（非静默跳过）；`release` 失败 LOG.warn（非静默吞没）；NoOp `tryAcquire` 恒返回 true（单进程部署无锁，依赖既有 `runningExecutions`，非静默跳过——是显式的 "no cross-process lock needed" 语义）
- [x] shipped 默认（`NoOpSessionTakeoverLock`）下既有测试零回归
- [x] 新增功能各有对应 focused 测试覆盖（acquire/release/stale-expiry/conditional-release/renew/isHeld/引擎接线/双实例 E2E 各有测试）
- [x] `restorePendingSessions` 对 `isHeld==true` 的 session 加入 skipped 桶（而非 failed 桶）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] roadmap §4 验收标准 line 253 已更新（标注接管锁已交付）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `ISessionTakeoverLock` 契约表面（接口 + NoOp 默认 + schema 常量）已落地
- [x] `DbSessionTakeoverLock` 功能实现（CAS acquire + conditional release + lease/TTL 过期）已落地
- [x] `DefaultAgentEngine` 三入口点 tryAcquire/release 接线 + `restorePendingSessions` isHeld 跳过增强已落地
- [x] 端到端：双实例共享 DB → acquire/竞争/release/过期抢占完整路径跑通
- [x] shipped 默认（NoOp）下既有测试零回归
- [x] 必要 focused verification 已完成（acquire / release / stale-expiry / conditional-release / renew / isHeld / 引擎接线 / 双实例 E2E 各有测试）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（nop-job 集成 / orphan liveness / 恢复策略 / 超时中止 / 归档 / 心跳续约 / TeamManager / ResourceGuard / XDSL 均显式在 Non-Goals 切出）
- [x] 受影响 owner docs（`nop-ai-agent-actor-runtime-vision.md` + `01-architecture-baseline.md` + roadmap §4）已同步到 live baseline
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`tryAcquire`/`release` 在运行时被三个执行入口点调用（不只类型存在），（b）双实例 E2E 测试验证锁竞争真实生效，（c）无空方法体/静默跳过作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；nop-job 集成 / orphan liveness / 恢复策略 / 超时中止 / 归档 / 心跳续约 / TeamManager / ResourceGuard / XDSL 均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **nop-job 定时扫描集成**（vision §6.3 第 1 步）：RecoveryManager 作为后台定时扫描 daemon（每 60s 扫描 `ai_agent_session_lock` + `ai_agent_session` 状态），依赖 nop-job 调度基础设施集成。Classification: successor plan required。
- **orphan 进程 liveness 检测**（vision §6.3"检查进程 ID 是否存活"）：主动检测锁持有者进程存活状态（而非仅依赖 lease TTL 被动过期），需要进程注册/心跳机制。Classification: successor plan required。
- **恢复模式策略（resume/retry/abort）**（vision §6.3 第 2 步）：恢复 orphaned session 时裁定从 checkpoint 恢复还是从头重试还是标记失败。Classification: successor plan required。
- **超时强制中止**（vision §6.3 第 3 步）：`status=running` 且超时的 session 经 `ICancelToken.cancel()` 强制中止。Classification: successor plan required。
- **归档清理**（vision §6.3 第 4 步）：terminal session 归档到历史表。Classification: optimization candidate。
- **心跳自动续约**：长时间 ReAct 执行自动续约 lease（避免被抢占）。Classification: optimization candidate。
- **TeamManager + ResourceGuard + Fencing Token**（vision §10 Phase 3/5）。Classification: successor plan required。
- **XDSL 配置化**：`agent.xdef` 增加 `<takeover-lock>` 元素。Classification: optimization candidate。

## Closure

Status Note: ✅ Completed 2026-06-16. All Phase 1 + Phase 2 deliverables landed, all 18 plan-221 tests green, all 6 closure gates independently audited (separate subagent) and PASS. Module-wide baseline 2083 tests pass with zero regression under shipped NoOp default. Roadmap §4 line 253, `nop-ai-agent-actor-runtime-vision.md` §4.2/§6.3/§10 Phase 4, `01-architecture-baseline.md`, and `nop-ai-agent-reliability.md` §1.2 all updated to reflect plan 221 delivery. RecoveryManager's remaining capabilities (nop-job scheduling daemon / orphan process liveness / resume-retry-abort strategy / timeout force-stop / archival cleanup / auto-renew heart-beat) remain explicit Non-Goals — independent successors each.

Completed: 2026-06-16

Closure Audit Evidence:

- Reviewer / Agent: Independent closure auditor subagent (closure-audit fresh session, not the implementation session)
- Audit Session: closure-audit pass on 2026-06-16
- Evidence:
  - Phase 1 Exit Criteria — PASS: `ISessionTakeoverLock.java`, `NoOpSessionTakeoverLock.java`, `AiAgentSessionLockTable.java` all present in `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/runtime/lock/`; `NoOpSessionTakeoverLock.tryAcquire` returns true and `isHeld` returns false (singleton no-op); `AiAgentSessionLockTable` DDL defines `SESSION_ID` PK + `LOCK_OWNER` + `LOCK_ACQUIRED_AT` + `LOCK_EXPIRES_AT`; design docs `nop-ai-agent-actor-runtime-vision.md` §6.3/§10 Phase 4 and `01-architecture-baseline.md` updated.
  - Phase 2 Exit Criteria — PASS: `DbSessionTakeoverLock.java` implements SQL CAS acquire + conditional DELETE release + stale-lock `WHERE LOCK_EXPIRES_AT <= ?` takeover + conditional `tryRenew`; `DefaultAgentEngine` fields `instanceId` (UUID, `DefaultAgentEngine.java:293`), `lockLeaseMs` default `1_800_000L` (line 300), `sessionTakeoverLock` default `NoOpSessionTakeoverLock` (line 287), `setSessionTakeoverLock`/`setLockLeaseMs` setters, `releaseLockQuietly` helper (line 1225).
  - Wiring Verification (Minimum Rules #23) — PASS: three execution entry points call `tryAcquire` before `putIfAbsent` and `releaseLockQuietly` on every cleanup path — `doExecute` (acquire at `DefaultAgentEngine.java:1457`, releases 1468/1520/1549), `resumeSession` (acquire 1724, releases 1735/1768/1788), `restoreSession` (acquire 1886, releases 1897/1930/1948); `restorePendingSessions` `isHeld` skip at line 2008.
  - End-to-End Verification (Minimum Rules #22) — PASS: `TestDbSessionTakeoverLockDualInstanceE2E.java` covers two lock instances sharing H2 → A acquires, B returns false, A releases, B acquires; stale-lock expiry takeover path verified in `TestDbSessionTakeoverLock.java`.
  - No Silent No-Op (Minimum Rules #24) — PASS: `tryAcquire` returning false triggers `NopAiAgentException` fail-fast (not silent skip); `releaseLockQuietly` LOG.warn on failure (not swallowed); NoOp default `tryAcquire` returns true as explicit "no cross-process lock needed" semantics.
  - No-Hollow Check — PASS: focused tests `TestDbSessionTakeoverLock.java` + `TestDbSessionTakeoverLockDualInstanceE2E.java` exercise acquire/release/stale-expiry/conditional-release/renew/isHeld; engine-wiring integration verified via lock-table row presence/removal assertions; shipped NoOp default keeps `TestRestoreSession` / `TestRestorePendingSessions` / `TestDefaultAgentEngineConcurrencyGuard` zero-regression.
  - Closure Gates — PASS: all 13 Closure Gates checked `[x]`; no in-scope live defect or contract drift downgraded to deferred (nop-job / orphan liveness / resume-retry-abort / timeout / archival / heart-beat-renew / TeamManager / ResourceGuard / XDSL are all explicit Non-Goals successor items).
  - Owner docs sync — PASS: `nop-ai-agent-actor-runtime-vision.md` §6.3/§10 Phase 4, `01-architecture-baseline.md`, roadmap §4 line 253, and `nop-ai-agent-reliability.md` §1.2 all updated to reflect plan 221 delivery.
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/221-nop-ai-agent-cross-process-session-takeover-lock.md --strict` exit code 0 (no unchecked items, Closure Evidence present).

Follow-up:

- No remaining plan-owned work. All Non-Goals (nop-job scheduling daemon / orphan process liveness / resume-retry-abort strategy / timeout force-stop / archival cleanup / auto-renew heart-beat / TeamManager / ResourceGuard / Fencing Token / XDSL config) are explicit independent successors, not in-scope defects deferred.

## Follow-up handled by 222-nop-ai-agent-recovery-manager-daemon.md

nop-job 定时扫描集成（Non-Blocking Follow-ups 第一条，标 `successor plan required`）已由 successor plan `ai-dev/plans/222-nop-ai-agent-recovery-manager-daemon.md` 接管：交付 `IRecoveryManager` 接口 + `NoOpRecoveryManager` shipped 默认 + `ScheduledRecoveryManager` 功能实现（经 nop-job 调度的后台周期扫描，每次 scanOnce 执行 stale lock cleanup + orphan session detection），以及 `DefaultAgentEngine` 启动/停止接线。此段为事实性交叉引用追加，不修改本计划已关闭的 closure 内容。
