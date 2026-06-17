# 184 nop-ai-agent Auto Restore-on-Startup

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: auto-restore-on-startup (carry-over from plan 183)

> Last Reviewed: 2026-06-15
> Source: Carry-over from plan 183 (`ai-dev/plans/183-nop-ai-agent-crash-restart-session-restore.md`) — `Deferred But Adjudicated` "自动 restore-on-startup 检测" (`Successor Required: yes, Successor Path: 独立 enhancement plan`)。同一 carry-over 亦 deferred by plans 181 (`Non-Blocking Follow-ups`: "Automatic restore-on-engine-startup") 与 182 (`Non-Blocking Follow-ups`: "Automatic restore-on-engine-startup")。Roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §1（模块定位"面向大规模无人值守自动化执行"）+ §4 Layer 4 验收标准"长任务中断后可以恢复"（`:233`，plan 183 交付显式 `restoreSession`；本计划交付自动扫描+恢复，补齐"无人值守"最后一块）。Design owner doc: `nop-ai-agent-reliability.md` §1.1（恢复模型，`:15-37`）。
> Related: Plan 183 (crash/restart `restoreSession` manual entry point — the per-session primitive this plan orchestrates in a batch), Plan 182 (checkpoint journal — `getLatestCheckpoint` consumed by `restoreSession`), Plan 180 (sticky-pause `resumeSession` — governance recovery, distinct from crash-restart restore)

## Purpose

将可靠性层 `nop-ai-agent-reliability.md` §1.1 的 **自动 restore-on-startup** 收口到"进程重启后，引擎自动扫描持久化的未完成 session 并恢复执行"状态。Plan 183 交付了显式 `restoreSession(sessionId, approver, reason)` 调用入口——但调用方必须事先知道 sessionId 并手动逐个调用。在"无人值守自动化"场景（模块 §1 核心定位）下，进程崩溃重启后没有人可以手动指定哪些 session 需要恢复。本计划交付自动发现 + 批量恢复能力，使 crash/restart restore 从"手动逐个"进化为"自动扫描全部"。

当前状态：`IAgentEngine.restoreSession`（`IAgentEngine.java:114` default UOE；`DefaultAgentEngine.java:780-889` 实现）已完整落地单 session crash-restart restore。`FileBackedSessionStore`（`FileBackedSessionStore.java:62-232`）将 `AgentSession` 持久化到 per-session `session.json`（`{rootDirectory}/{sessionId}/session.json`）。但 `FileBackedSessionStore.getAll()`（`:144-146`）**仅返回 in-memory cache**，不扫描磁盘——引擎无法发现磁盘上有哪些 session。也没有任何批量扫描/恢复方法（`grep -r "restorePending|restoreOnStartup|autoRestore|scanUnfinished|restoreAll" nop-ai/nop-ai-agent/src/main` → 0 命中）。本计划交付自动发现 + 批量恢复。

## Current Baseline

- **Manual `restoreSession` ✅ (plan 183)**: `IAgentEngine.restoreSession(sessionId, approver, reason)`（`IAgentEngine.java:114`）是单 session crash-restart 恢复入口。`DefaultAgentEngine` 实现（`:780-889`）: 检测 session 不在 `runningExecutions` → `sessionStore.get(sessionId)` 加载 → `isTerminalStatus` 校验（completed/failed/cancelled/forced_stopped/escalated 拒绝）→ `checkpointManager.getLatestCheckpoint` 一致性校验 → status 置 running → `SESSION_RESTORED` 事件 → `buildBaseExecutionContext` 重建 → `resolveExecutor` + `executor.execute` 续跑 → `replaceMessages` + `sessionStore.save` 持久化恢复后状态。返回 `CompletableFuture<AgentExecutionResult>`
- **`FileBackedSessionStore` ✅ (plan 183)**: per-session JSON 持久化（`FileBackedSessionStore.java:62`）。`get(sessionId)`（`:110-121`）cache-miss 时 lazy-load 从 `session.json`。`save(session)`（`:157-164`）write-through 到磁盘 + cache。`remove`（`:124-141`）删文件 + cache。`getRootDirectory()`（`:89-91`）暴露根目录。文件布局 `{rootDirectory}/{sessionId}/session.json`——每个子目录名即 sessionId
- **`getAll()` 仅返回 cache ❌**: `FileBackedSessionStore.getAll()`（`:144-146`）返回 `sessions.values()`（in-memory `ConcurrentHashMap` cache），**不扫描磁盘**。进程重启后新 store 实例的 cache 为空，`getAll()` 返回空集合——磁盘上的 session 全部"隐形"。这是 auto-restore 发现能力的 gap
- **`AgentExecStatus` ✅ 8 values**: `pending / running / completed / failed / cancelled / forced_stopped / escalated / paused`（`AgentExecStatus.java:3-27`）。crash-restart 场景下磁盘上的 session status 可能是: `running`（崩溃时正在执行）、`pending`（崩溃前尚未开始）、`completed`/`failed` 等（已完成的终态，无需恢复）、`paused`（denial-ledger sticky-pause，governance 状态，需人类 `resumeSession` 恢复而非 auto-restore）
- **`isTerminalStatus` 私有方法 ✅**: `DefaultAgentEngine.isTerminalStatus`（`:897-903`）判断 completed/failed/cancelled/forced_stopped/escalated。**注意**: `paused` **不在** terminal 集合中——`restoreSession` 会接受 `paused` session 并尝试恢复。但 auto-restore **不应**恢复 `paused` session（governance 契约: sticky-pause 需显式人类 `resumeSession`，plan 180）。Auto-restore orchestrator 必须在调用 `restoreSession` 前过滤掉 `paused`
- **无引擎生命周期方法 ❌**: `DefaultAgentEngine`（`:72-1099`）使用 telescoping constructors，无 `start()` / `init()` / `@PostConstruct` / Builder。引擎构造后立即可用。"引擎启动"是一个概念性时间点（构造后、首次正常操作前），auto-restore 应作为显式 opt-in 方法供调用方在构造后调用
- **Auto-restore ❌ NOT implemented**: `grep -r "restorePending|restoreOnStartup|autoRestore|scanUnfinished|restoreAll|discoverRestorable" nop-ai/nop-ai-agent/src` → 0 命中。无任何代码在引擎启动时自动扫描磁盘 + 批量恢复。这是本计划要收口的 gap

## Goals

- **磁盘 session 发现能力**: 使 `FileBackedSessionStore` 能枚举磁盘上的全部 session（扫描 `rootDirectory` 子目录，加载每个 `session.json`），供 auto-restore orchestrator 筛选可恢复候选。不改变现有 `getAll()` 语义（`getAll()` 返回 cache-only 的现有行为保留，避免对其他调用方的副作用）
- **Auto-restore 批量入口点**: 新增 `IAgentEngine.restorePendingSessions(approver, reason)`（default UOE）+ `DefaultAgentEngine` 实现。语义: 发现磁盘上全部持久化 session → 筛选可恢复候选（status 为 `running` 或 `pending`；`paused`/terminal 跳过）→ 逐个调用 `restoreSession` 恢复 → 逐 session 失败隔离（一个失败不影响其他）→ 返回恢复结果摘要（restored / skipped / failed + 原因）
- **可恢复状态策略裁定（policy adjudication）**: 明确哪些 status 的 session 该自动恢复——`running`（崩溃时正在执行）+ `pending`（崩溃前未开始）= 可恢复候选；`paused` = 跳过（governance: sticky-pause 需人类 `resumeSession`）；terminal（completed/failed/cancelled/forced_stopped/escalated）= 跳过（已完成）。这是 plan 183 deferred 时明确标注"需要策略裁定"的核心决策
- **顺序恢复策略**: 逐个 session 恢复（每个 `restoreSession` 完成后再恢复下一个），而非并行。理由: 并行恢复多个 session 会产生并发 LLM 调用，可能导致 provider 限流；顺序恢复在单进程 scope 下安全且简单。并行/限流恢复是后续增强
- **端到端无人值守存活**: engine A.execute（FileBackedSessionStore + FileBackedCheckpointManager）→ 崩溃（丢弃 engine A）→ 新 engine B（同一 store/checkpoint 根目录）→ `restorePendingSessions` → 全部未完成 session 自动恢复 + ReAct 循环继续 → 无需人工指定 sessionId。证明从崩溃到自动恢复的完整"无人值守"路径
- **Fail-fast 语义**: `restorePendingSessions` 仅在 session store 的发现方法不支持时（抛 UOE / 未覆写 default）快速失败抛 `NopAiAgentException`（明确告知"当前 store 不支持持久化发现"）。`InMemorySessionStore` 的发现覆写返回 `getAll()`，进程重启后 cache 为空→返回空集合→`restorePendingSessions` 返回空摘要（非异常——"没有未完成 session"是合法状态）。磁盘为空时同样返回空摘要（非异常）
- **测试覆盖**: 发现能力测试（新 store 实例发现磁盘 session）+ 端到端 auto-restore 测试 + 状态筛选测试（running/pending 恢复，paused/terminal 跳过）+ 失败隔离测试 + 空磁盘测试 + 向后兼容测试（InMemorySessionStore fail-fast）

## Non-Goals

- **自动检测的触发时机自动化**: 本计划交付显式 `restorePendingSessions()` 方法供调用方在引擎构造后主动调用。**不**在引擎构造器或某个生命周期回调中自动触发——何时恢复（启动时？定时扫描？）由调用方决定。"调用方在构造后自动调用"是部署层决策，非引擎层契约
- **并行 / 限流恢复**: 多 session 并行恢复（线程池 + 并发上限）或基于资源余量的自适应限流。本计划交付顺序恢复（逐个）。并行恢复需要并发控制 + 资源管理策略，是后续增强
- **定时 / 周期性扫描**: 后台线程定时扫描磁盘发现新崩溃 session。本计划交付一次性扫描（调用时扫描当时磁盘上的全部未完成 session）。持续监控（定时扫描）是后续增强
- **跨进程 / 多实例 session 接管锁**: 同 plan 183 Non-Goal。并发接管的锁机制依赖 L4-8 Actor Runtime（roadmap P3，未开始）。本计划交付 **单进程** auto-restore
- **DB-backed session store 的发现**: 本计划的发现能力针对 `FileBackedSessionStore`（文件级，扫描文件系统子目录）。DB-backed session store 的发现（SQL 查询 `WHERE status IN (...)`）是 DB-session-store successor 的职责（参照 plan 179 模式）
- **恢复优先级 / 排序策略**: 多个可恢复 session 之间的恢复顺序（按创建时间？按 token 消耗？按 agent 优先级？）。本计划按磁盘遍历顺序（不保证特定排序）恢复。优先级排序是后续增强
- **`paused` session 的自动恢复**: `paused` 是 denial-ledger sticky-pause（governance 状态，plan 180）。自动恢复 `paused` session 会绕过人类干预契约。`paused` session 始终需要显式 `resumeSession`（人类操作）。本计划将 `paused` 排除在 auto-restore 候选之外

## Scope

### In Scope

- `FileBackedSessionStore` 磁盘 session 发现能力（扫描 `rootDirectory` 子目录，加载全部持久化 `session.json`）
- `IAgentEngine.restorePendingSessions(approver, reason)` 批量 auto-restore 入口点（default UOE + DefaultAgentEngine 实现）
- 可恢复状态策略: `running` + `pending` = 候选；`paused` + terminal = 跳过（含原因）
- 顺序恢复 + 逐 session 失败隔离
- 恢复结果摘要（restored / skipped / failed + 每项原因）
- 发现能力测试 + 端到端 auto-restore 测试 + 状态筛选测试 + 失败隔离测试 + 空磁盘测试 + 向后兼容测试
- 设计文档 §1.1 更新 + roadmap carry-over 状态同步

### Out Of Scope

- 引擎构造器 / 生命周期回调中的自动触发（调用方决定何时调用）
- 并行 / 限流恢复（后续增强）
- 定时 / 周期性后台扫描（后续增强）
- 跨进程 / 多实例 session 接管锁（依赖 L4-8 Actor Runtime）
- DB-backed session store 的发现（DB-session-store successor）
- 恢复优先级 / 排序策略（后续增强）
- `paused` session 自动恢复（governance: 需人类 `resumeSession`）

## Execution Plan

### Phase 1 - Disk session discovery + auto-restore orchestration + tests

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/FileBackedSessionStore.java` (disk-scan discovery); `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/ISessionStore.java` (discovery contract method, default UOE); `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/InMemorySessionStore.java` (discovery override); `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/IAgentEngine.java` (restorePendingSessions default UOE); `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java` (restorePendingSessions impl); `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/` + `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/session/`

- Item Types: `Decision | Proof`

- [x] **Decision（磁盘发现能力——新 `ISessionStore` default UOE 方法，不改 `getAll()` 语义）**: 现 `FileBackedSessionStore.getAll()`（`:144-146`）仅返回 in-memory cache，不扫描磁盘。直接修改 `getAll()` 语义会改变所有现有调用方的行为（副作用风险）。**裁定**: 在 `ISessionStore` 新增一个 default UOE 方法（如 `listAllSessions()` / `discoverSessions()`），语义为"返回此 store 已知的全部 session，包括尚未加载到 cache 的持久化 session"。`InMemorySessionStore` 覆写为返回 `getAll()`（in-memory store 全部 session 都在内存中，无磁盘概念）。`FileBackedSessionStore` 覆写为扫描 `rootDirectory` 子目录 + 加载每个 `session.json`（加载后存入 cache，后续 `get` 命中 cache）。这与 plan 183 的 `save()` default UOE 模式一致（default UOE + 覆写实现，满足 Minimum Rules #24——不支持持久化的 store 快速失败而非静默返回空）。方法命名与确切签名在实现时裁定，plan 只约束行为语义
- [x] **Decision（可恢复状态策略——`running` + `pending` only）**: 磁盘上的 session status 可能是 8 种之一。Auto-restore 候选: 仅 `running`（崩溃时正在执行）+ `pending`（崩溃前未开始）。**跳过**: `paused`（denial-ledger sticky-pause，governance 状态，需人类 `resumeSession` 恢复，plan 180——auto-restore `paused` 会绕过人类干预契约）；terminal（completed/failed/cancelled/forced_stopped/escalated——已完成，无需恢复）。**关键**: `restoreSession`（`DefaultAgentEngine.java:807-813`）的 `isTerminalStatus` 检查 **不包含** `paused`（`isTerminalStatus` 在 `:897-903` 只检查 5 个 terminal 值），所以 `restoreSession` 会接受 `paused` session。Auto-restore orchestrator 必须在调用 `restoreSession` **前** 自行过滤 `paused`（在 orchestrator 层而非 `restoreSession` 层过滤——`restoreSession` 的单 session 契约不变，由调用方决定是否调用）
- [x] **Decision（顺序恢复策略）**: 逐个 session 恢复——`restoreSession(sessionA)` → 等待完成（`.join()` 或等价机制）→ `restoreSession(sessionB)` → ... 。理由: (1) 并行恢复多个 session 产生并发 LLM 调用，可能导致 provider 限流; (2) 顺序恢复在单进程 scope 下无并发竞争; (3) 实现简单，失败隔离天然（一个 session 的恢复完全结束后才开始下一个）。并行/限流恢复是后续增强。恢复顺序按磁盘遍历顺序（`Files.list(rootDirectory)` 返回顺序），不保证特定排序——优先级排序是 Non-Goal
- [x] **Decision（逐 session 失败隔离）**: 恢复某个 session 失败时（如 `session.json` 损坏、agent model 加载失败、`restoreSession` 抛异常），记录失败 + 原因，**继续恢复下一个 session**，不中止整个 batch。每个 session 的恢复是独立的 try-catch。磁盘为空或无可恢复 session 时返回空摘要（非异常——合法状态）
- [x] **Decision（`restorePendingSessions` 入口点契约 + fail-fast）**: `IAgentEngine.restorePendingSessions(approver, reason)` default UOE。`DefaultAgentEngine` 实现: (1) 发现磁盘全部 session（上 Decision 的发现方法）; (2) 筛选可恢复候选（status = running/pending）; (3) 逐个 `restoreSession` + 等待完成; (4) 收集结果摘要。**Fail-fast**: session store 不是 `FileBackedSessionStore`（或更准确地说，发现方法抛 UOE / 返回不支持信号）时，`restorePendingSessions` 抛 `NopAiAgentException`（非静默返回空——明确告知"当前 store 不支持持久化发现"）。`InMemorySessionStore` 的发现覆写返回 `getAll()`，在进程重启后 cache 为空时返回空集合——这是"InMemorySessionStore 不跨进程持久化"的正确语义，`restorePendingSessions` 返回空摘要（非异常，因为没有未完成 session 是合法状态）。Javadoc 声明此语义
- [x] 在 `FileBackedSessionStore` 实现磁盘发现: 扫描 `rootDirectory` 下全部子目录（`Files.list(rootDirectory)`，过滤目录），每个子目录名即 sessionId，加载 `{sessionId}/session.json`（复用 `SessionFileReader`），返回全部 session 集合。加载结果存入 cache（后续 `get` 命中）。损坏/截断 JSON 的 session 跳过（记录警告，不抛异常——一个损坏文件不应阻塞发现全部 session）。`rootDirectory` 不存在或为空时返回空集合
- [x] 在 `ISessionStore` 新增发现方法 default UOE + `InMemorySessionStore` 覆写（返回 `getAll()`）+ `FileBackedSessionStore` 覆写（磁盘扫描）
- [x] 在 `IAgentEngine` 新增 `restorePendingSessions(approver, reason)` default UOE + Javadoc
- [x] 在 `DefaultAgentEngine` 实现 `restorePendingSessions`: 发现 → 筛选 → 逐个 restoreSession（复用现有 `restoreSession` 方法，不重复其内部逻辑）→ 收集结果摘要
- [x] 功能化测试（**磁盘发现——新 store 实例发现磁盘 session**）: store A 写入 N 个 session（各 status）→ 丢弃 store A → 新 store B（同 rootDirectory）→ 发现方法返回全部 N 个 session（含各 status）
- [x] 功能化测试（**端到端 auto-restore——核心价值**）: engine A.execute（FileBackedSessionStore + FileBackedCheckpointManager 指向 temp 目录）→ 至少 1 个工具调用完成 → 持久化 → 丢弃 engine A（模拟崩溃）→ 新 engine B（同一 store/checkpoint 根目录）→ `restorePendingSessions` → 未完成 session 自动恢复 + ReAct 循环继续 → 无需人工指定 sessionId。证明"无人值守"路径完整连通
- [x] 功能化测试（状态筛选）: 磁盘上有 running / pending / paused / completed / failed 各 1 个 session → `restorePendingSessions` → running + pending 被恢复，paused + terminal 被跳过（结果摘要含 skipped 原因）
- [x] 功能化测试（失败隔离）: 磁盘上有 3 个 running session，其中 1 个的 `session.json` 损坏 → `restorePendingSessions` → 损坏的标记 failed，另外 2 个正常恢复，batch 不中止
- [x] 边界测试: 磁盘为空（rootDirectory 不存在或无 session 子目录）→ `restorePendingSessions` 返回空摘要（非异常）
- [x] 向后兼容测试: engine 注入 `InMemorySessionStore` → `restorePendingSessions` 行为明确（返回空摘要，cache 为空时无未完成 session）；`restoreSession`（plan 183）全部现有测试通过；`getAll()` 语义不变（仍返回 cache-only）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `FileBackedSessionStore` 磁盘发现能力存在且实现为扫描 `rootDirectory` 子目录 + 加载 `session.json`；`ISessionStore` 新增发现方法 default UOE；`InMemorySessionStore` 覆写为返回 `getAll()`
- [x] `IAgentEngine.restorePendingSessions` default UOE + `DefaultAgentEngine` 实现存在且非空壳
- [x] 状态筛选经测试证明: `running` + `pending` 被恢复，`paused` 被跳过（governance），terminal 被跳过（已完成）
- [x] **端到端验证**（Minimum Rules #22 Anti-Hollow）: engine A → 崩溃 → engine B → `restorePendingSessions` → 全部未完成 session 自动恢复——从崩溃到自动恢复的完整"无人值守"路径已验证，无需人工指定 sessionId
- [x] **接线验证**（Minimum Rules #23）: `restorePendingSessions` 内部确实调用 `restoreSession`（计数器/mock verify 或代码追踪），而非重复实现 restore 逻辑
- [x] **无静默跳过**（Minimum Rules #24）: 损坏 session.json 在发现阶段跳过并记录警告（非静默吞掉）；InMemorySessionStore 场景返回空摘要（合法语义，Javadoc 声明）
- [x] **新增功能测试**（Minimum Rules #25）: 磁盘发现 + 端到端 auto-restore + 状态筛选 + 失败隔离 + 空磁盘 + 向后兼容——各有对应通过的测试
- [x] `getAll()` 语义不变（仍返回 cache-only），现有 `FileBackedSessionStore` / `InMemorySessionStore` 调用方不受影响
- [x] **向后兼容**: `restoreSession`（plan 183）全部现有测试通过；`InMemorySessionStore` 默认不受影响
- [x] No owner-doc update required（设计文档更新在 Phase 2）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` Phase 1 新增测试通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Design doc §1.1 update + roadmap carry-over sync

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §1.1; `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`

- Item Types: `Follow-up`

- [x] 更新 `nop-ai-agent-reliability.md` §1.1（恢复模型）: auto-restore-on-startup 标记"已落地"；记录架构决策——(1) 磁盘发现能力（新 `ISessionStore` default UOE 方法，不改 `getAll()` 语义）; (2) 可恢复状态策略（running/pending only，paused/terminal 跳过）; (3) 顺序恢复 + 逐 session 失败隔离; (4) 显式 opt-in 方法（不在构造器/生命周期回调中自动触发，调用方决定何时调用）
- [x] 更新 `nop-ai-agent-reliability.md` §5.4 / §5.4a: "自动 restore-on-startup 仍是独立 successor" 注释更新为"已落地（plan 184）"
- [x] 更新 `nop-ai-agent-roadmap.md`: auto-restore-on-startup carry-over（plans 181/182/183）标记已解决；§1 模块定位"面向大规模无人值守自动化执行"与 §4 Layer 4 验收标准对齐（plan 183 交付显式 `restoreSession`；plan 184 交付自动扫描+恢复，补齐"无人值守"最后一块）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop-ai-agent-reliability.md` §1.1 已更新（auto-restore 已落地 + 4 项架构决策记录）
- [x] `nop-ai-agent-reliability.md` §5.4 / §5.4a 已更新（auto-restore 从 successor 标记为已落地）
- [x] `nop-ai-agent-roadmap.md` carry-over 状态同步
- [x] No new test required: Phase 2 is pure design-doc/roadmap update, no code behavior change
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`（文档变更无需重跑测试，但确认编译通过）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] `FileBackedSessionStore` 磁盘发现能力已落地（扫描 `rootDirectory` 子目录 + 加载 `session.json`，`ISessionStore` 新增 default UOE 契约方法）
- [x] `IAgentEngine.restorePendingSessions` + `DefaultAgentEngine` 实现已落地（发现 → 筛选 → 逐个 restoreSession → 结果摘要）
- [x] 可恢复状态策略经测试证明: running/pending 恢复，paused/terminal 跳过
- [x] 端到端验证: engine A → 崩溃 → engine B → `restorePendingSessions` → 全部未完成 session 自动恢复，无需人工指定 sessionId
- [x] 逐 session 失败隔离经测试证明: 单个 session 恢复失败不中止 batch
- [x] `getAll()` 语义不变（现有调用方不受影响）
- [x] 向后兼容: `restoreSession`（plan 183）全部现有测试通过；`InMemorySessionStore` 默认不受影响
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs（reliability §1.1/§5.4/§5.4a、roadmap）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**: closure audit 已验证 (a) `restorePendingSessions` 内部确实调用 `restoreSession`（非重复实现 restore 逻辑），(b) 磁盘发现确实扫描文件系统（非返回空集合/缓存），(c) 端到端 auto-restore 路径完整连通（发现 → 筛选 → 恢复 → 续跑）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/184-nop-ai-agent-auto-restore-on-startup.md --strict` 退出码为 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码为 0
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 并行 / 限流恢复

- Classification: `optimization candidate`
- Why Not Blocking Closure: 本计划交付顺序恢复（逐个 session），在单进程 scope 下安全且正确。并行恢复（线程池 + 并发上限）或自适应限流（基于 LLM provider 余量）是性能优化，不影响 crash/restart auto-restore 契约的正确性。少量 session 的恢复顺序执行延迟可接受
- Successor Required: no

### 定时 / 周期性后台扫描

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本计划交付一次性扫描（调用 `restorePendingSessions` 时扫描当时磁盘上的全部未完成 session）。后台线程定时扫描发现新崩溃 session 需要线程管理 + 生命周期 + 扫描间隔策略，是独立增强。调用方可在需要时重复调用 `restorePendingSessions`
- Successor Required: no

### 恢复优先级 / 排序策略

- Classification: `optimization candidate`
- Why Not Blocking Closure: 本计划按磁盘遍历顺序恢复（不保证特定排序）。多 session 间的恢复优先级（按创建时间 / token 消耗 / agent 优先级）是优化项，不影响恢复正确性
- Successor Required: no

### 自动检测的触发时机自动化

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本计划交付显式 `restorePendingSessions()` 方法。在引擎构造器或生命周期回调中自动触发需要部署层策略裁定（何时恢复——启动时？延迟？后台？），是部署层决策而非引擎层契约。调用方在构造后调用 `restorePendingSessions()` 即可实现"启动时自动恢复"
- Successor Required: no

### DB-backed session store 的发现

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本计划的发现能力针对 `FileBackedSessionStore`（文件系统扫描）。DB-backed session store 的发现（SQL `WHERE status IN (...)`）是 DB-session-store successor 的职责（参照 plan 179 `DBDenialLedger` 模式）。文件级发现在单机部署中完全可用
- Successor Required: yes
- Successor Path: DB-backed session store 独立 plan（参照 plan 179 模式）

## Non-Blocking Follow-ups

- 并行 / 限流恢复（线程池 + 并发上限）
- 定时 / 周期性后台扫描（持续监控发现新崩溃 session）
- 恢复优先级 / 排序策略（按创建时间 / token 消耗 / agent 优先级）
- 自动检测的触发时机自动化（引擎构造器 / 生命周期回调中的自动触发）
- DB-backed session store 的发现（SQL 查询，参照 plan 179 模式）
- 跨进程 / 多实例 session 接管锁（依赖 L4-8 Actor Runtime，同 plan 183 deferred）

## Closure

Status Note: 本计划交付"自动 restore-on-startup"——进程崩溃重启后引擎自动扫描持久化的未完成 session 并批量恢复，补齐"无人值守自动化"最后一块。磁盘发现契约（`ISessionStore.listAllSessions()`）+ 批量入口（`IAgentEngine.restorePendingSessions`）+ 状态筛选（running/pending 恢复，paused/terminal 跳过）+ 顺序恢复 + 逐 session 失败隔离全部落地，并有端到端 crash→restart→auto-restore 测试验证（Minimum Rules #22）+ 接线验证（restorePendingSessions 内部调用 restoreSession，Minimum Rules #23）+ 独立 closure audit 全部 PASS。所有 deferred 项均为 optimization candidate / out-of-scope improvement（non-blocking），无 in-scope live defect 被降级。

Completed: 2026-06-15

Closure Audit Evidence:

- Reviewer / Agent: Independent closure-audit subagent (explore, task_id `ses_1386fd6faffekyxx7Xw6lw8atV`，fresh session 非实现 session 复用)
- Audit Session: 2026-06-15，adversarial verify-against-live-code audit
- Evidence:
  - **每条 Exit Criterion**: PASS
    - Phase 1 全部 Exit Criteria（磁盘发现 / restorePendingSessions 非空壳 / 状态筛选 / 端到端验证 / 接线验证 / 无静默跳过 / 新增功能测试 / getAll 语义不变 / 向后兼容）—— audit 对每条给出 live code path 或 test name
    - Phase 2 全部 Exit Criteria（§1.1 已更新 / §5.4a 已更新 / roadmap 同步 / 纯文档无需新测试 / compile 通过 / 日志已更新）—— audit Claim 8 确认
  - **每条 Closure Gate**: PASS —— audit 逐条覆盖（disk 发现真实扫描文件系统 FileBackedSessionStore.java:192-222；restorePendingSessions 委托 restoreSession DefaultAgentEngine.java:949-950；状态筛选 DefaultAgentEngine.java:943-973；端到端 TestRestorePendingSessions.java:176-271；失败隔离 :948-962 try/catch；getAll 不变 FileBackedSessionStore.java:148-151；向后兼容 InMemorySessionStore + restoreSession 16 tests；owner docs 同步 reliability.md §1.1/§5.4a + roadmap L3-4c ✅）
  - **Anti-Hollow Check 结果**: 全部 PASS
    - (a) `restorePendingSessions` 内部确实调用 `restoreSession`（DefaultAgentEngine.java:949-950 `.toCompletableFuture().join()`；TestRestorePendingSessions.java:551-557 子类计数 verify 仅 running/pending 触发，paused/terminal 不触发）—— 非重复实现 restore 逻辑
    - (b) 磁盘发现确实扫描文件系统（FileBackedSessionStore.java:192-222 `Files.list(rootDirectory)` + 子目录 + `reader.readIfExists`；TestFileBackedSessionStore.java:401-448 跨实例发现 8 个 session）—— 非返回空集合/缓存
    - (c) 端到端 auto-restore 路径完整连通（TestRestorePendingSessions.java:176-271 engine A → crash → engine B → restorePendingSessions → 2 个未完成 session 自动恢复 + ReAct 续跑完成）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/184-nop-ai-agent-auto-restore-on-startup.md --strict` 退出码为 0
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码为 0（Critical/High/Medium/Low 全部 0 findings）
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → 1296 tests, 0 failures, 0 errors（audit 独立运行 TestRestorePendingSessions + TestFileBackedSessionStore → 36 tests, 0 failures）
  - **Deferred 项分类检查**: 全部 deferred 项（并行/限流恢复 / 定时扫描 / 恢复优先级排序 / 自动触发时机自动化 / DB-backed 发现）均为 `optimization candidate` 或 `out-of-scope improvement`，附 `Why Not Blocking Closure` 理由——无 in-scope live defect 被降级，无 owner-doc drift 残留

Follow-up:

- 并行 / 限流恢复（线程池 + 并发上限）—— optimization candidate
- 定时 / 周期性后台扫描（持续监控发现新崩溃 session）—— out-of-scope improvement
- 恢复优先级 / 排序策略（按创建时间 / token 消耗 / agent 优先级）—— optimization candidate
- 自动检测的触发时机自动化（引擎构造器 / 生命周期回调中的自动触发）—— out-of-scope improvement（部署层决策）
- DB-backed session store 的发现（SQL 查询，参照 plan 179 模式）—— out-of-scope improvement，Successor Required: yes
- 跨进程 / 多实例 session 接管锁（依赖 L4-8 Actor Runtime，同 plan 183 deferred）
