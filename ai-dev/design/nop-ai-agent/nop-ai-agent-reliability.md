# Nop AI Agent 容错与恢复设计

## 1. 目标

Agent runtime 稳定之后，系统会进入第二类问题：

- 模型调用不稳定
- 工具调用可能失败
- 上下文会膨胀
- Agent 可能循环
- 长执行需要超时和恢复

本篇定义 `nop-ai-agent` 的可靠性增强层，目标是让系统在真实环境中更可控，而不是在设计阶段一次性实现全部复杂能力。

### 1.1 恢复模型

Agent 执行过程中的详细历史自动持久化（消息历史、工具调用及结果、Plan 状态）。崩溃后恢复策略：

- 从持久化的消息历史重建上下文，已完成的工具调用不需要重新执行
- 结合 Plan 系统确定当前进度和待执行步骤，从断点继续
- AgentSession 状态持久化到数据库，任何服务实例都可以接管恢复
- 并发接管的锁机制由 actor 调度系统负责

**实现状态（plan 183 ✅ 已落地 — 单进程 crash/restart restore）**：

- **文件级 session 持久化（必要前提）**：`FileBackedSessionStore implements ISessionStore` 将 `AgentSession`（messages 全量 + status/counters/timestamps/metadata + parentSessionId/planId/compactedAt）持久化到 per-session `session.json`（drop-in 替换 `InMemorySessionStore`，同契约存储后端替换）。`ISessionStore.save(AgentSession)` 是 default UOE 契约桥——`InMemorySessionStore` 显式覆写为 no-op（in-memory 共享引用语义），`FileBackedSessionStore` 覆写为 JSON 全量持久化。session 文件（而非 checkpoint journal）是 **消息历史的 source of truth**。
- **intra-execution 持久化（crash 中途存活的关键）**：`ReActAgentExecutor` dispatch loop 在每次 `saveCheckpoint`（`:918`）后调用 `sessionStore.save(session)`——崩溃发生在执行途中时 session 文件已含已完成工具的 tool_call/tool_response messages。`doExecute`/`resumeSession`/`restoreSession` 末尾的最终 flush 保留 token/iteration/status 等末态字段。intra-execution 与 post-execution 两条同步路径统一为 `AgentSession.replaceMessages(ctx.getMessages())` full-sync replace 语义（等幂，无重复 append）。
- **restore 入口点（restore vs resume 区分）**：`IAgentEngine.restoreSession(sessionId, approver, reason)` 是 crash-restart 恢复入口（session **不在** 活跃执行 map `runningExecutions`，从 `FileBackedSessionStore` 加载）。与 `resumeSession`（sticky-pause 恢复，session **在** 内存 + status=paused，plan 180）明确区分。两者 fail-fast 条件互斥。
- **restore 协议（加载 → 重建 → 校验 → 续跑）**：检测 session 不在 `runningExecutions` → 从 sessionStore 加载 → `checkpointManager.getLatestCheckpoint` 一致性校验（checkpoint.messageCount ≤ session.messageCount；违反时 best-effort 警告，不阻断）→ `buildBaseExecutionContext` 重建（system prompt + 完整历史 replay）→ status 置 running → 发布 `SESSION_RESTORED` 事件 → `resolveExecutor` + `executor.execute` 续跑。
- **checkpoint journal 在 restore 的角色（校验补充，非消息源）**：latest checkpoint 提供 resume-point 元数据 + 一致性校验。消息历史来自 session store（完整），不来自 journal（journal 仅存 tool-execution 摘要）。"已完成工具不重新执行"属性来自 **完整历史 replay**（LLM 看到完整 tool result 不会重发），非 checkpoint 跳过。
- **单进程 scope（跨进程接管锁 deferred）**：并发接管的锁机制（防止多实例同时恢复同一 session）依赖 L4-8 Actor Runtime（roadmap P3，未开始），是独立 successor。本层交付单进程 crash/restart restore（进程重启后单实例恢复），单进程内由 `runningExecutions` map 提供并发保护。DB-backed session store（任何服务实例可接管恢复 session）已落地（plan 185 ✅）；DB-backed checkpoint store（resume-point watermark 跨进程持久化）已落地（plan 186 ✅，实现为 `DBCheckpointManager`，参照 plan 179 `DBDenialLedger` 模式）。restore 协议的 `getLatestCheckpoint` 一致性校验在 DB-backed 场景下跨实例可用。

**实现状态（plan 184 ✅ 已落地 — 自动 restore-on-startup）**：

plan 183 交付了显式 `restoreSession(sessionId, approver, reason)` 单 session 入口——但调用方必须事先知道 sessionId 并手动逐个调用。在"无人值守自动化"场景（模块 §1 核心定位）下，进程崩溃重启后没有人可以手动指定哪些 session 需要恢复。plan 184 交付自动发现 + 批量恢复能力，使 crash/restart restore 从"手动逐个"进化为"自动扫描全部"。

- **磁盘 session 发现能力（`ISessionStore.listAllSessions()`）**：新增 `ISessionStore` default UOE 契约方法，语义为"返回此 store 已知的全部 session，包括尚未加载到 cache 的持久化 session"。`getAll()` 语义不变（仍返回 cache-only，避免对现有调用方的副作用）——发现是新方法而非 `getAll()` 的语义修改。`InMemorySessionStore` 覆写为返回 `getAll()`（in-memory 全部 session 都在内存中，无磁盘概念）；`FileBackedSessionStore` 覆写为扫描 `rootDirectory` 子目录 + 加载每个 `session.json`（加载后存入 cache，后续 `get` 命中）。损坏/截断 JSON 跳过 + LOG.warn（不阻断发现剩余 session；后续 `get(corruptId)` 仍 fail-fast）。这与 plan 183 的 `save()` default UOE 模式一致（default UOE + 覆写实现，Minimum Rules #24）。
- **auto-restore 批量入口点（`IAgentEngine.restorePendingSessions(approver, reason)`）**：default UOE + `DefaultAgentEngine` 实现。语义: 发现磁盘上全部持久化 session → 筛选可恢复候选 → 逐个调用 `restoreSession` 恢复（复用 plan 183 的单 session primitive，不重复 restore 逻辑）→ 返回 `SessionRestoreSummary`（restored / skipped / failed + 每项原因）。
- **可恢复状态策略（running/pending only）**：候选 = `running`（崩溃时正在执行）+ `pending`（崩溃前未开始）。`paused` 跳过（denial-ledger sticky-pause，governance 状态，需人类 `resumeSession` 恢复——plan 180；auto-restore `paused` 会绕过人类干预契约）。terminal（completed/failed/cancelled/forced_stopped/escalated）跳过（已完成）。**关键**: orchestrator 在调用 `restoreSession` **前** 自行过滤 `paused`（`restoreSession` 的 `isTerminalStatus` 检查不包含 `paused`，所以 `restoreSession` 会接受 `paused` session——由调用方决定是否调用，`restoreSession` 单 session 契约不变）。
- **顺序恢复 + 逐 session 失败隔离**：逐个 session 恢复（每个 `restoreSession` 完成后再恢复下一个），非并行——避免并发 LLM 调用触发 provider 限流。每个 session 独立 try-catch，一个失败不中止 batch。磁盘为空或无可恢复 session 时返回空摘要（非异常——合法状态）。
- **显式 opt-in 方法（不在构造器/生命周期回调中自动触发）**：`restorePendingSessions` 是显式 opt-in。引擎**不**在构造器或生命周期回调中自动触发——**何时**恢复（启动时？定时扫描？）由调用方决定。"调用方在构造后调用 `restorePendingSessions()`" 是部署层决策，非引擎层契约。
- **fail-fast 语义**：store 不支持发现（`listAllSessions` 抛 UOE / 未覆写 default）→ `restorePendingSessions` 抛 `NopAiAgentException`（明确告知"当前 store 不支持持久化发现"，非静默返回空摘要——部署配置错误需 surface）。`InMemorySessionStore` 的发现覆写返回 `getAll()`，进程重启后 cache 为空→返回空集合→`restorePendingSessions` 返回空摘要（非异常——"没有未完成 session"是合法状态）。磁盘为空时同样返回空摘要（非异常）。

**架构决策（plan 184 已落地）**：

- **发现能力是新 default UOE 方法，非 `getAll()` 语义修改**：直接修改 `getAll()` 语义会改变所有现有调用方的行为（副作用风险）。新增 `listAllSessions()` default UOE 方法（`InMemorySessionStore` 覆写返回 `getAll()`，`FileBackedSessionStore` 覆写扫描磁盘），与 plan 183 `save()` default UOE 模式一致。`getAll()` 保持 cache-only 语义，现有调用方不受影响。
- **可恢复状态策略 = running/pending only（paused/terminal 跳过）**：`paused` 是 governance sticky-pause，需人类 `resumeSession`（plan 180）；auto-restore `paused` 会绕过人类干预契约。terminal 已完成无需恢复。候选筛选在 orchestrator 层（`restorePendingSessions`）而非 `restoreSession` 层——`restoreSession` 单 session 契约不变。
- **顺序恢复 + 逐 session 失败隔离（非并行）**：并行恢复多个 session 产生并发 LLM 调用，可能触发 provider 限流。顺序恢复在单进程 scope 下无并发竞争，失败隔离天然。并行/限流恢复是后续增强。
- **显式 opt-in 方法（部署层决定何时调用）**：`restorePendingSessions` 是显式 opt-in，不在构造器/生命周期回调中自动触发。何时恢复（启动时？延迟？后台定时扫描？）是部署层决策，非引擎层契约。调用方在构造后调用 `restorePendingSessions()` 即可实现"启动时自动恢复"。

**架构决策（plan 183 已落地）**：

- **session 持久化是 restore 的必要前提**：文件级 `FileBackedSessionStore` 是 restore 协议的存储基础。journal（plan 182）是校验补充而非消息源——单独从 journal 无法重建完整对话（journal 仅存 tool-execution 摘要，不存 LLM reasoning messages）。session store + checkpoint journal 协同：session store 提供 message history，journal 提供 resume-point metadata + consistency verification。
- **restore vs resume 区分（crash-restart vs sticky-pause）**：`restoreSession` 处理进程崩溃/重启后 session 不在活跃内存的场景；`resumeSession` 处理 denial-ledger sticky-pause 后 session 在内存 + status=paused 的场景。两者入口点的语义、前置条件、事件类型、恢复机制完全独立，互斥不重叠。
- **单进程 scope**：本层交付单进程 crash/restart restore。跨进程 takeover lock 依赖 L4-8 Actor Runtime，是独立 successor。

**实现状态（plan 185 ✅ 已落地 — DB-backed session store）**：

plan 183 交付了文件级持久化（`FileBackedSessionStore` per-session JSON），plan 184 交付了基于文件扫描的自动发现。但文件级持久化受限于单文件系统——分布式/云部署中多个服务实例不共享文件系统，崩溃在节点 A 的 session 无法被节点 B 接管恢复。plan 185 交付 `DBSessionStore implements ISessionStore`——`ISessionStore` 的第三个实现（与 `InMemorySessionStore` / `FileBackedSessionStore` 同级），将 `AgentSession` 持久化到 `ai_agent_session` 表，任何共享 DB 的服务实例都可以 load + take over。

- **DB-backed session 持久化（`DBSessionStore`）**：构造器接收外部 `DataSource` + 初始化 schema（`CREATE TABLE IF NOT EXISTS ai_agent_session` + status index）。`save(session)` 使用 `MERGE INTO` 等幂 upsert（同 `FileBackedSessionStore.save` 的 overwrite-on-write 语义）；`get(sessionId)` cache-miss 时从 DB `SELECT SESSION_DATA` + `SessionFileReader.deserialize` lazy-load；`listAllSessions()` 从 DB `SELECT SESSION_DATA FROM ai_agent_session` 全量发现（plan 184 的 discovery 契约的 DB-backed 覆写）；`remove` 删 DB 行 + cache；`forkSession` 同 `FileBackedSessionStore` 逻辑。`InMemorySessionStore` 保持 shipped 默认，`FileBackedSessionStore` 不受影响——DB-backed 是 opt-in。
- **drop-in 替换（零引擎代码变更）**：`DBSessionStore` 通过 `new DefaultAgentEngine(chatService, toolManager, new DBSessionStore(dataSource), ..., checkpointManager)` 构造器注入。`restoreSession`（plan 183）+ `restorePendingSessions`（plan 184）使用 DB-backed store 时自动获得跨实例能力——discovery 经 SQL 而非磁盘扫描，但 orchestrator 逻辑不变。

**架构决策（plan 185 已落地）**：

- **raw JDBC（vs IOrmSession）**：与 plan 179 `DBDenialLedger` + plan 171 `DBMessageService` 同级的兄弟实现模式，保持一致。`ISessionStore` 操作是同步的 save/get/remove，raw JDBC 足够且无额外抽象层。

### 1.2 文件写入 crash-safety（plan 195 已落地）

专为 crash-recovery 设计的 FileBacked 持久化链路（`SessionFileWriter` 写 session.json、`CheckpointSnapshotWriter` 写 snapshot.json）必须保证：写入过程中 JVM/OS 崩溃、kill、磁盘满发生时，目标文件**要么是完整的旧内容、要么是完整的新内容**——不存在截断或部分写入的中间状态。这是 plan 183/184 crash/restart restore 链路的最后一道 crash-safety 缺口（写入路径的非原子性会导致 `listAllSessions` 永久 skip 被截断的 session，使专为 crash-recovery 设计的子系统"crash 时丢失正在恢复的 session"）。

**架构决策（plan 195 已落地）**：

- **write-to-tmp + ATOMIC_MOVE + REPLACE_EXISTING 模式**：目标文件经由同目录 sibling `.tmp` 文件写入后，通过 `Files.move(tmp, target, ATOMIC_MOVE, REPLACE_EXISTING)` 原子替换。POSIX rename(2) 的原子性语义保证目标在任何时刻要么是完整旧内容、要么是完整新内容。这收敛了原 `TRUNCATE_EXISTING` 写法"先截断目标到 0 字节再写入"的非原子窗口。
- **为什么选这个方案**：(1) POSIX rename 原子性是 OS 级保证，覆盖 JVM crash / kill 场景；(2) 仓库内已有先例——`nop-stream` `LocalFileCheckpointStorage` 使用完全相同的 write-to-tmp + `ATOMIC_MOVE` + `REPLACE_EXISTING` + `finally { deleteIfExists(tmp) }` 模式，保持一致；(3) 不引入 fsync（kernel page cache 刷盘的 power-loss hardening 属存储引擎级增强，超出 JVM crash scope，且 nop-stream 先例未使用 fsync）。
- **拒绝的替代方案**："仅把 `TRUNCATE_EXISTING` 改为写新文件不 truncate"——仍非原子：直接覆写目标文件在写入完成前崩溃仍留下部分字节。POSIX 下只有 rename(2) 提供文件级原子替换。
- **tmp 命名与位置**：tmp 文件必须是 target 的 sibling（同目录），命名 `target.getFileName() + ".tmp"`。`ATOMIC_MOVE` 要求源/目标在同一文件系统（同一 mount point），sibling 保证这一点；否则抛 `AtomicMoveNotSupportedException`。与 nop-stream 先例一致。
- **失败路径 tmp 清理**：`finally` 块中 `Files.deleteIfExists(tmp)`，确保即使 `Files.move` 失败（或 JVM 在 move 前被中断但 finally 仍执行）也不留 stale tmp。`deleteIfExists` 的异常不掩盖主异常（IO 异常统一转 `NopAiAgentException` 抛出）。
- **不提取共享 helper（inline 修复）**：两个 writer 的写入逻辑虽是 copy-paste，但裁定各自内联修复而非提取共享 `AtomicFileWriter` helper。理由：(1) 只有两个 writer，无第三个消费者；(2) 两者分属不同包（`session` vs `reliability`），共享 helper 需中性包位置，引入新的模块内依赖方向；(3) 只有 ~5 行 IO 写入段重复，序列化逻辑完全不同；(4) nop-stream 先例在每个调用点内联该模式。保持 per-writer 独立性与现有代码结构一致。
- **`ioLock` 语义不变**：现有 `synchronized(ioLock)` 序列化同一 writer 实例的并发写。crash-safe 改动不改变锁语义——tmp 写 + move 仍在 `synchronized` 块内执行。`Files.move(ATOMIC_MOVE)` 本身在 POSIX 上是原子的；`ioLock` 保护的是"同一 writer 实例的两个并发 write() 不交错"，而非 move 的原子性。

- **混合列布局（scalar queryable columns + full session JSON CLOB）**：`SESSION_ID`(PK) / `AGENT_NAME` / `STATUS` / `CREATED_AT` / `UPDATED_AT` scalar 列支撑未来 status-based SQL 筛选、监控、cleanup；`SESSION_DATA` CLOB 经 `SessionFileWriter.serialize`/`SessionFileReader.deserialize` 序列化（package-private static 方法，同包直接复用），与 `FileBackedSessionStore` 序列化逻辑 100% 一致——零新增序列化代码。
- **write-through cache（同 FileBackedSessionStore 模式）**：`ConcurrentHashMap` mirrors DB state。`save` write-through（更新 cache + DB）；`get` cache-miss 时 DB lazy-load；`remove` 删 cache + DB 行。cache 避免 ReAct dispatch loop intra-execution `save` 的每次 `get` DB round-trip。
- **MERGE INTO upsert（等幂覆写）**：`save(session)` 使用 `MERGE INTO ... KEY (SESSION_ID) VALUES (...)`（H2/PostgreSQL/MySQL 兼容），同 sessionId 重复 save 是等幂覆写。
- **listAllSessions SQL-based discovery（vs FileBackedSessionStore 磁盘扫描）**：`SELECT SESSION_DATA FROM ai_agent_session` → 逐行 deserialize。损坏/截断 JSON 跳过 + LOG.warn（corruption isolation，同 `FileBackedSessionStore.listAllSessions`）。
- **跨进程接管锁仍 deferred**：`DBSessionStore` 使 session 可被发现和加载（任何共享 DB 的服务实例都能 `get(sessionId)`），但"防止多实例同时恢复同一 session"的并发接管锁是 L4-8 Actor Runtime 的职责（设计 §1.1 并发接管的锁机制由 actor 调度系统负责）。

### 1.3 同 session 并发执行保护（plan 197 已落地）

`DefaultAgentEngine` 用 `ConcurrentHashMap<String, CancelHandle> runningExecutions` 提供单进程内 session 执行并发管理。三个执行入口点（`doExecute` / `resumeSession` / `restoreSession`）的注册/注销逻辑统一收敛为 **fail-fast + cancel-safe** 模式，消除审计 [维度14-1]（put/remove 不去重导致 handle 互覆）与 [维度14-2]（cancel 在异步入队窗口内丢失）两处 P1 竞态。

**架构决策（plan 197 已落地）**：

- **去重注册策略 = `putIfAbsent` + fail-fast（拒绝无条件 `put`）**：三个执行入口点在 **同步阶段**（`supplyAsync` 之外）执行 `putIfAbsent(sessionId, handle)`——返回值非 null 表示 session 已在执行，立即抛 `NopAiAgentException("session already executing: sessionId=...")`（fail-fast，Minimum Rules #24），绝不静默覆盖。采用字符串消息而非 `ErrorCode`（与模块内 100+ 既有抛出站点风格一致；`NopAiAgentException` 已支持 `ErrorCode` 构造器，但本场景的错误消息足够自描述，无需额外 `ErrorCode` 常量）。并发执行排队/复用语义（第二次 execute 排队等待第一次完成而非 fail-fast）是产品策略变更，属 successor（Non-Goals），本层采用 fail-fast。

- **值比较注销策略 = `remove(sessionId, handle)`（拒绝按 key `remove`）**：三个入口点的 `finally` 块使用 `ConcurrentHashMap.remove(key, value)`——仅当当前映射值 `==` 调用者注册的 handle 时才移除。`CancelHandle` 是无 `equals` 覆写的 `private static final class`，`remove(key, value)` 使用引用相等：同一 handle 实例可移除，不同执行创建的不同 handle 实例不被误删。这保证了第一个执行的 `finally` 不清除第二个仍在运行的 handle（消除 [维度14-1] 的 handle 互覆竞态）。

- **cancel 丢失窗口修复 = 选项 A（同步阶段预注册 handle）**：在 `supplyAsync` **之外**（同步阶段）创建并注册 `CancelHandle`，使 `cancelSession` 在 `execute()` 返回后、`supplyAsync` lambda 实际运行前的入队窗口内即可通过 `runningExecutions.get(sessionId)` 找到 handle，走 `ctx.setCancelRequested(true)` 路径（而非 else 分支的 `session.setStatus(cancelled)`，后者会被 lambda 内的 `session.setStatus(running)` 覆盖）。cancel 信号经由 `AgentExecutionContext.cancelRequested`（`volatile boolean`，已存在）传递，**不**经由 `AgentSession.status`——因此 `AgentSession.status` 的 `volatile` 修饰不是 cancel-window 修复的必要前置（[14-6] 的 status volatile 留为 non-blocking follow-up）。拒绝选项 B（lambda 入口状态检查，依赖 `AgentSession.status` 可见性→需纳入 volatile）与"排队等待而非 fail-fast"替代方案。

- **`CancelHandle.thread` 延迟绑定（`volatile Thread`，null 初始化）**：预注册发生在同步阶段，此时 ForkJoinPool 执行线程尚未确定。`CancelHandle.thread` 从 `final Thread` 改为 `volatile Thread thread`，同步阶段初始化为 `null`，lambda 入口更新为 `Thread.currentThread()`（执行线程）。`cancelSession(forced=true)` 在 `handle.thread.interrupt()` 前 null 检查——入队窗口内 forced cancel 不 interrupt 任何人（避免误 interrupt 调用线程），但 `ctx.setCancelRequested(true)` 保证 lambda 启动后执行器在首个循环边界检测到 cancel 并 abort。

- **`restoreSession` 既有 `containsKey` 检查移除（putIfAbsent 是唯一 guard）**：`restoreSession` 在 `supplyAsync` 之前的 `runningExecutions.containsKey(sessionId)` 检查（原 TOCTOU 竞态——containsKey 通过后、putIfAbsent 之前另一线程可能已注册）被移除。`putIfAbsent` 本身是原子操作，是唯一的并发 guard。移除冗余检查使三个入口点的 fail-fast 行为一致（统一抛 "session already executing"），而非 restore 给出不同错误消息。

- **`AgentSession.status` volatile 不纳入 scope**：cancel 信号经由 `AgentExecutionContext.cancelRequested`（已 volatile）传递，不经由 `AgentSession.status`。`session.setStatus(running)` 在 lambda 内设置后，执行器检测到 `ctx.isCancelRequested()` → abort → `ctx.setStatus(cancelled)` → `finally { session.setStatus(ctx.getStatus()) }` 把 cancelled 写回 session。status 的跨线程可见性不影响 cancel 正确性（[14-6] 的 status volatile 是独立 hardening，留为 non-blocking follow-up）。

- **`sendMessage` 向后兼容 = fail-fast 异常传播**：`sendMessage` 调用 `doExecute` 并立即返回 ack（fire-and-forget）。`putIfAbsent` 在同步阶段 fail-fast 抛出 `NopAiAgentException` 时，异常在 `doExecute` 返回 future 之前同步传播到 `sendMessage` 调用方（不进入 `future.exceptionally(...)` 路径）。这是正确的 fail-fast 行为——对正在执行的 session 发送消息应报错而非静默覆盖。`AgentMessageAck` 不新增 error 字段（API 变更属 successor），调用方需 catch 异常。

## 2. 故障模型

Agent 运行时面临的故障，与普通业务代码不同，主要有五类：

1. 模型调用故障
   - 限流
   - provider 5xx
   - 网络超时
2. 工具执行故障
   - 参数错误
   - 文件或权限错误
   - shell 超时
3. 上下文故障
   - token 超限
   - 历史过长导致推理退化
4. 行为故障
   - 循环调用同一工具
   - 持续产出无效动作
5. 长流程故障
   - 任务超时
   - 进程崩溃
   - 执行中断后无法恢复

可靠性设计必须围绕这五类故障展开。

## 2.5 Tool-Call Repair 四阶段修复管线

LLM 输出的工具调用 JSON 经常存在参数丢失、JSON 截断、重复调用风暴。来自 Reasonix 的四阶段 Chain of Responsibility 管线：

| 阶段 | 触发条件 | 算法 |
|------|---------|------|
| **flatten** | schema 叶子参数 >10 或深度 >2 | 自动展平为点记法 (`a.b.c`)，dispatch 时重新嵌套 |
| **scavenge** | 每轮 | 正则扫描 `reasoning_content` 寻找遗漏的 tool-call JSON（3 种模式匹配器），MAX_SCAVENGE_INPUT=100KB 防 ReDoS |
| **truncation** | JSON 不完整 | 括号栈状态机：trim → 去尾逗号 → null 填充 → 闭合括号 → fallback `"{}"` |
| **storm** | 每轮 | 滑动窗口(windowSize=6)追踪最近调用，相同(name, args)≥3次 → 抑制 |

**Nop 映射**：`Chain<ToolCall>` 拦截器链，每阶段实现 `BiFunction<ToolCall, ToolCall>` 接口。Storm 用 `LinkedHashMap` + `removeEldestEntry` 实现滑动窗口。

> Storm 阶段是工具级别的去重，熔断器 (§5.1) 是 turn 级别的终止——两层保护互补。

## 3. 分层设计

建议把可靠性能力分成四层：

### 3.1 调用层

负责单次调用层面的故障：

- 错误分类
- 重试
- 超时

**实现状态（plan 207 ✅ 已落地 — 错误分类 + 重试）**：`LlmErrorClassifier`（按 `ARG_HTTP_STATUS` 把异常映射为 `ErrorClassification`：429→RATE_LIMITED、5xx/超时→TRANSIENT、4xx→NON_TRANSIENT、配额→QUOTA_EXCEEDED）+ `IRetryPolicy`（RETRY/STOP/FALLBACK 决策）+ `NoRetryPolicy`（shipped 默认，恒 STOP，零行为回归）+ `StandardRetryPolicy`（功能性 opt-in：最多 3 次指数退避，仅 TRANSIENT/RATE_LIMITED 重试）。重试循环接线到 `ReActAgentExecutor` 的单次 LLM 调用点（`chatService.call`）。注意：当前 `nop-ai-core` 的 `ChatServiceImpl` 抛出的 `ERR_AI_SERVICE_HTTP_ERROR` 异常丢弃 HTTP headers/body（仅保留状态码），因此 Retry-After header 解析不可达——429 使用指数退避而非 Retry-After 等待（独立 successor）。单次 LLM 调用超时预算是独立能力（Non-Goal），本计划的重试由调用本身抛出的超时异常（`NopTimeoutException`/`SocketTimeoutException`）驱动。roadmap §4 L3-2 ✅。

### 3.2 运行层

负责单个 Agent loop 的可持续运行：

- 上下文窗口保护
- 历史压缩
- 循环检测
- 工具参数和安全验证

### 3.3 平台层

负责 provider 和模型层面的降级：

- 断路器
- 模型回退

**实现状态（plan 210 ✅ 断路器契约 + AlwaysClosed 默认 + ThresholdBreaker 功能实现；plan 213 ✅ 熔断感知路由解析）**：`ICircuitBreaker`（`allowCall(modelKey)` / `getState(modelKey)` / `recordSuccess(modelKey)` / `recordFailure(modelKey)` 契约）+ `CircuitState`（CLOSED/OPEN/HALF_OPEN 三态枚举）+ `AlwaysClosed`（shipped 默认，恒 CLOSED、恒放行、record 为显式 no-op，零行为回归）+ `ThresholdBreaker`（功能性 opt-in：per-model-key 状态机——CLOSED 连续失败达 `failureThreshold`（默认 3）→ OPEN；OPEN 冷却 `cooldownMs`（默认 60s）后 lazy 转 HALF_OPEN；HALF_OPEN 单 probe 原子占位，成功→CLOSED 复位，失败→OPEN 重启冷却；线程安全 via ConcurrentHashMap + per-entry synchronized）。熔断检查接线到 `ReActAgentExecutor`（见下），失败记录接线到 retry 循环内 per-attempt（catch 入口处 `recordFailure`，在 FALLBACK 切换 model-key 之前）+ 非异常失败路径（`!response.isSuccess()`），成功记录在循环后（`recordSuccess` 复位）。`DefaultAgentEngine` 通过 field+setter+resolveExecutor 装配 circuitBreaker（默认 AlwaysClosed）。

**plan 213 熔断感知路由解析（circuit-aware routing）**：`ReActAgentExecutor` 在 `route()` 返回后、model-switched 审计检测（plan 205）之前新增 circuit-aware 解析步骤（executor 私有方法）。当 `circuitBreaker.allowCall(primaryModelKey)` 返回 true（默认 AlwaysClosed 即此路径，零开销零回归）时解析为 no-op；返回 false（主模型 OPEN 或 HALF_OPEN probe 占用）时主动沿 `IModelRouter.getFallback(...)` 回退链逐个查询直到找到一个 `allowCall == true` 的健康模型，将 `routedOptions` 切换到该模型续跑（circuit-induced switch 经 `LOG.warn` 记录 + model-switched 审计消息自然体现，不修改不可变的 `RoutingResult`）。全部回退链耗尽仍无 circuit-closed 模型可用时 fail-fast 抛 `NopAiAgentException`（含已检查的所有 model-key + 各自 circuit state + 指引），不静默继续（Minimum Rules #24）。解析步骤在 model-switched 检测之前执行，使审计消息正确反映实际使用的模型。原 retry 循环外层 circuit check（plan 210）转为 safety-net（解析已保证 circuit-cleared，此 check 仅在并发竞争导致 circuit 在解析后、check 前 trip 时触发 fail-fast）。回退链扫描有上限（配置的链长度 + 防御性硬上限），无死循环风险。模型回退见 §5.2。

### 3.4 恢复层

负责跨进程和长流程恢复：

- 检查点
- 会话恢复

## 4. Layer 1-2 优先能力

> **Layer 映射**：本篇"优先能力"对应 roadmap Layer 1（核心闭环）中的可靠性子集 + Layer 2（执行扩展）的前半部分；"后续能力"对应 Layer 3（可靠性扩展）。具体分配见各节。

### 4.1 错误分类

这是最值得尽快落地的可靠性能力。

建议最小分类：

- `RETRYABLE`
- `NON_RETRYABLE`
- `RECOVERABLE`

语义：

- `RETRYABLE`
  - 适合程序化重试
  - 如 429、5xx、网络故障、临时超时
- `NON_RETRYABLE`
  - 不适合程序化重试
  - 如参数错误、权限错误、工具名不存在
- `RECOVERABLE`
  - 需要先做额外处理再继续
  - 如上下文溢出、需要压缩后重试

没有错误分类，就很难决定哪些问题该交给程序、哪些问题该交给 LLM 或 Advisor Agent。

### 4.2 分层超时

建议尽早统一时间预算：

- Agent 总超时
- 单次 LLM 调用超时
- 单个工具默认超时

这是运行可控性的基础。

### 4.3 上下文压缩

长会话必然面临上下文膨胀，因此压缩不是可选项。

**Layer 1-2 实现**：Layer 0（Tool Result 预截断）+ Layer 1（零成本微压缩）+ 基础 Layer 3（LLM 摘要）。完整 Layer 2 和 Layer 4 推迟到 Layer 3 可靠性扩展（见 roadmap.md §5.2）。

完整 5 层管道定义见 §7。

### 4.4 工具验证

这部分也应尽快做，并且尽量程序化而不是 prompt 化。

最小验证顺序：

1. 工具名存在
2. 参数可解析
3. 参数符合 schema
4. 参数满足安全约束

## 5. Layer 3 后续能力

### 5.1 断路器

当某个模型或 provider 连续失败时，继续调用它只会浪费时间和 token。

因此可以为模型层引入断路器状态：

- `CLOSED`
- `OPEN`
- `HALF_OPEN`

但这类能力更适合在 runtime 稳定后再引入，因为阈值和冷却时间需要真实运行数据校准。

**实现状态（plan 210 ✅ 已落地 — 三态状态机）**：`ThresholdBreaker`（`io.nop.ai.agent.reliability` 包，opt-in）实现 per-model-key（`provider:model` 复合键）三态状态机：CLOSED 连续失败计数达 `failureThreshold`（默认 3，构造器可配）→ OPEN；OPEN 经 `cooldownMs`（默认 60000ms，构造器可配）后 lazy 转 HALF_OPEN（无后台线程，下次 `allowCall` 时检查）；HALF_OPEN 单 probe 原子占位（首个 caller 放行作 probe，并发 caller 拒绝视为仍 OPEN），probe 成功→CLOSED 复位计数，probe 失败→OPEN 重启冷却。线程安全 via `ConcurrentHashMap` + per-entry `synchronized` + `volatile state`。状态 in-memory per-breaker-instance（跨 execute() 调用累积，持久化/跨进程共享是 Non-Goal successor）。circuit OPEN 时抛 `NopAiAgentException`（含 model-key + state + 指引），不静默返回。动态阈值校准（滑动窗口失败率）是独立 successor（设计本节原注"阈值和冷却时间需要真实运行数据校准"——首版交付静态可配置阈值）。

### 5.1a 弹性策略选择（Sisyphean vs Fast-fail）

熔断器代表"快速熔断"哲学。另一种截然不同的弹性哲学是 oh-my-claudecode 的 Sisyphean 模型——Stop-hook 拦截退出事件，检查 todo 列表，强制继续执行。

| 哲学 | 代表 | 行为 | 适用场景 |
|------|------|------|---------|
| "快速熔断" | PilotDeck (Circuit Breaker) | 3 轮失败即终止，fail-fast | 交互式场景，成本敏感 |
| "永不放弃" | oh-my-claudecode (Sisyphean) | Stop-hook 确保任务完成，at-least-once | 无人值守长时间执行 |

**设计决策**：两种策略作为 `ICircuitBreaker` 和 `ISustainer` 的互斥配置选项。Layer 1 默认 fail-fast（与 Nop 无人值守定位一致），Sisyphean 可选激活。在 biz action 的后置拦截器中实现。

**实现状态（plan 210 ✅ fail-fast 已落地 + plan 212 ✅ Sisyphean 已落地）**：

- **fail-fast 哲学（plan 210 ✅）**：`ICircuitBreaker` 为默认弹性策略（契约见 §3.3，功能性 `ThresholdBreaker` 见 §5.1）。断路检查接线到 ReAct 循环单次 LLM 调用块（retry 循环外层），circuit OPEN 时抛 `NopAiAgentException` 拒绝调用（快速失败，不浪费 token）。
- **"永不放弃"哲学（plan 212 ✅ 契约 + 默认 + 功能实现）**：`ISustainer` 契约（`onStop(SustainContext)` 返回 `SustainDecision`(CONTINUE/STOP)）+ `SustainContext` reliability-local 数据载体（sessionId / stopReason / currentIteration / sustainCountSoFar，不引用 engine 类型，对称 `IterationSnapshot`）+ `SustainStopReason` 枚举（首版仅 `MAX_ITERATIONS`）+ `NoOpSustainer` shipped 默认（恒返回 STOP、`onStop` 为显式 no-op 决策、singleton）+ `SisypheanSustainer` 功能实现（**stateless**：仅持 `final int maxSustainCount`（默认 3，构造器可配），无 per-session 可变状态——per-execution sustain 计数由 executor 持有并经 `SustainContext.sustainCountSoFar` 传入，因此并发 execute() 天然隔离，无需并发原语或 per-session map）已全部落地于 `io.nop.ai.agent.reliability` 包。`SisypheanSustainer` 的 at-least-once 语义：被迭代预算截断的执行至少获得 `maxSustainCount` 次额外续跑机会（每次扩展原始 `maxIterations` 的预算）以确保任务完成，达上限后放行 STOP（fail-safe，非无限循环）。接线到 `ReActAgentExecutor` 的退出决策点（reactLoop 自然退出 + status 仍为 running = MAX_ITERATIONS 截断 → 咨询 sustainer；CONTINUE 扩展预算重入 reactLoop 顶部检查链，STOP 走 post-loop 终态变更）。`DefaultAgentEngine` 通过 field + setter（null-safe 兜底 `NoOpSustainer`）+ `resolveExecutor` 装配。
- **互斥执行机制裁定（plan 212 裁定）**：design §11a 原文"设计为互斥配置选项，由部署场景决定"明确把决定权交给部署。裁定为**部署层文档约束**（非运行时 guard）：`ICircuitBreaker` 与 `ISustainer` 作为**独立 opt-in 扩展点**共存（各自 NoOp/Always shipped 默认），引擎**不**在 setter/构造器抛互斥异常。两者处于不同层（breaker 在 model-call 层、sustainer 在 task-exit 层），集成商按部署场景二选一（交互式/成本敏感 → fail-fast + breaker；无人值守长执行 → Sisyphean + sustainer）。运行时硬性互斥 guard 是独立 successor（Non-Goal）。
- **可持续退出点清单（plan 212 裁定，首版）**：首版只 sustain **MAX_ITERATIONS 截断**（reactLoop while 条件为假导致循环自然退出——最客观的"被截断、任务尚未完成"信号）。以下退出点**不** sustainable（尊重原行为，不咨询 sustainer）：completion-judge `isComplete`（自愿完成）、`isEscalate`（自愿升级）、`shouldForceStop`（上下文溢出，sustain 会立刻再溢出）、cancel / denial-ledger pause（governance/用户发起）。sustain 其他退出点是独立 successor。
- **语义区分（plan 212 裁定）**：(1) `IGoalTracker.assessGoal` 在**迭代开始边界**返回 STUCK → **中止**卡住的 agent（escalate）；(2) `ISustainer.onStop` 在**退出决策点**返回 CONTINUE → **强制续跑**想停但未完成的 agent；(3) completion-judge `Continue` 是 agent **自愿**续跑（带既有 `consecutiveContinues` 死循环保护）。三者操作方向/决策点/触发主体不同，互不重叠。sustain 的强制续跑**不**绕过 `consecutiveContinues` 保护——sustain 续跑产生的迭代仍受 completion-judge Continue 死循环保护约束。

### 5.2 模型回退

当主模型不可用时，系统可以沿着有序回退链切换到备用模型。这类能力与断路器天然配套。

**实现状态（plan 209 ✅ 回退链契约 + SmartModelRouter；plan 213 ✅ 熔断感知路由解析）**：

- **回退链契约（plan 209 ✅）**：`IModelRouter.getFallback(currentOptions)` default 方法提供回退链查询（默认返回 null = 无回退能力）。`PassThroughModelRouter`（shipped 默认）`getFallback` 返回 null；`SmartModelRouter`（opt-in）维护 per-tier 有序回退链，`getFallback` 返回链中后继模型的合并 options（保留 tools/settings），链尾返回 null。retry 循环内 `RetryDecision.FALLBACK` 分支消费 `getFallback`（单次调用周期内瞬态失败触发的被动回退）。回退链消费的 usage 归因见 `nop-ai-agent-llm-layer.md` §6.5。

- **熔断感知路由解析（plan 213 ✅ circuit-aware routing）**：plan 209 的 retry 循环内被动回退覆盖"单次调用周期瞬态失败"。plan 213 把"主模型 circuit OPEN"的处理从"拒绝即终止整个 agent 执行"（plan 210）升级为"拒绝→主动沿 `IModelRouter.getFallback(...)` 链查找 circuit-closed 的健康模型→切换续跑"。解析步骤位于 `ReActAgentExecutor` 的 `route()` 返回后、model-switched 审计检测（plan 205）之前（executor 私有方法，不新增公共类型、不改 `IModelRouter` 接口）——circuit 解析是 route() 之后的 post-processing，router 本身不耦合 circuit 状态（Layer 2 路由与 Layer 3 可靠性保持正交）。全部回退链耗尽仍无 circuit-closed 模型可用时 fail-fast 抛 `NopAiAgentException`（含已检查 model-key + circuit state + 指引）。shipped 默认（AlwaysClosed + PassThroughModelRouter）行为零回归：`allowCall` 恒 true → 解析步骤首次检查即通过 → 不进入回退链扫描。详细接线与裁定见 §3.3 与 plan 213。

- **架构决策**：解析步骤留在 executor 编排层（不改 `IModelRouter` 接口），router 仍按复杂度/预算路由，circuit 解析是 route() 之后的 post-processing。retry 循环内 FALLBACK 模型的 circuit 检查不在本层覆盖（FALLBACK 本身是对失败的响应）。router 侧 tier 预判（在 `route()` 内部就跳过 circuit-OPEN 的 tier）是独立 successor（当前 post-routing 解析已覆盖功能需求）。

### 5.3 目标跟踪与行为故障检测（IGoalTracker / L3-3）

**实现状态（plan 211 ✅ 已落地 — `IGoalTracker` 契约 + `NoOpGoalTracker` shipped 默认 + `SessionGoalTracker` 功能实现）**：目标跟踪器把 agent 行为故障（§2 #4：循环调用同一工具、持续产出无效动作）的处理从"仅依赖 `maxIterations` 硬上限无差别截断"收敛为"按可插拔 `IGoalTracker` 决策检测 stuck/looping 模式"。reliability 包归属：所有目标跟踪类型（`IGoalTracker` / `GoalAssessment` / `IterationSnapshot` / `NoOpGoalTracker` / `SessionGoalTracker`）放入 `io.nop.ai.agent.reliability` 包，与 L3-1/L3-2/L3-4 同包边界。

**契约设计（读写分离，对称于 ICircuitBreaker）**：`IGoalTracker` 暴露两个方法，遵循读侧/写侧分离（与 `ICircuitBreaker` 的 `recordSuccess/recordFailure`（写）+ `allowCall/getState`（读）一致）：

- **`recordIteration(sessionId, IterationSnapshot)`（写侧）** — 在每轮 LLM 响应后、tool-dispatch / completion-judge 分支判断前被调用，更新 per-session 追踪状态。`IterationSnapshot` 是 reliability-local 数据载体（不引用 engine 类型 `AgentExecutionContext`，保持 reliability 包自包含，对称于 `RetryContext`/`Checkpoint`），含本轮 tool-call 签名列表 + 当前迭代号。签名 = `toolName:stableArgsString`（args 做 key 排序后序列化，避免 key 顺序差异导致签名不同）；无 tool call 轮次签名列表为空。
- **`assessGoal(sessionId)`（读侧）** — 在下一轮迭代开始时被调用（force-stop 上下文溢出硬保护之后、PRE_REASONING hook 之前），返回 `GoalAssessment` 三值枚举：`PROGRESSING`（默认，正常继续）、`STUCK`（检测到 stuck/looping，ReAct 循环 abort/escalate）、`GOAL_ACHIEVED`（预留值，程序化检测不产出此值，为 LLM-based assessment successor 预留契约空间）。

**STUCK abort 语义**：`assessGoal` 返回 `STUCK` 时，设 `ctx.setStatus(escalated)` + `ctx.setLastError(...)` + `break reactLoop`，与 denial-ledger pause break 同级 governance-abort 模式。不静默继续、不吞异常信号。

**接线点**：

- `recordIteration`：LLM 响应生成 `assistantMsg` 之后、`if (!hasToolCalls())` 分支判断前（单一调用点覆盖有/无 tool calls 两种场景）。
- `assessGoal`：循环顶部检查链中，`shouldForceStop`（上下文溢出）之后、`PRE_REASONING` hook 之前（force-stop 是上下文安全硬保护，优先级高于 stuck 检测；stuck 检测在 hook 之前避免 hook 副作用）。

**shipped 默认（NoOpGoalTracker）**：恒返回 `PROGRESSING`、`recordIteration` 为显式 no-op（非空方法体占位），singleton 模式。注入后引擎行为与 plan 211 前完全一致——stuck agent 仍由 `maxIterations` 兜底，零行为回归。

**SessionGoalTracker 功能策略**：per-session 滑动窗口追踪 tool-call 签名——窗口内相同签名重复达 `stuckThreshold`（默认 3）→ 下次 `assessGoal` 返回 `STUCK`。窗口大小 `windowSize`（默认 5）与 `stuckThreshold` 为构造器参数。"无 tool call"轮次不添加新签名到窗口（不构成 stuck 证据也不构成 progress 证据，窗口状态不变）。线程安全：`ConcurrentHashMap<String, SessionState>` + per-session 状态独立。状态 in-memory only（per-tracker-instance），跨进程共享 / DB-backed 是独立 successor。

**架构决策**：

- **签名检测 vs LLM-based assessment**：首版使用 tool-call 签名重复检测（程序化、确定性、零额外 LLM 调用成本）。LLM-based 进度评估（调用便宜模型判断"agent 是否在取得进展"）引入额外 LLM 成本 + 延迟，是独立 successor（类比 RuleBasedCompletionJudge vs LlmCompletionJudge 的分层）。
- **session 级 vs per-tool 级检测**：本节覆盖 session 级别的跨迭代进度模式检测；per-tool 级别的重复调用去重由 §2.5 Tool-Call Repair 的 storm 阶段（L2-2 `ChainRepairer`）覆盖。两者正交。
- **STUCK → abort（非自动 course-correction）**：STUCK 评估的语义是 abort（escalate status + 事件），不自动注入"你似乎在循环"的 course-correction 消息让 agent 自我修正。自动 course-correction 是产品策略增强，独立 successor。

**原始概念来源**：本节前身是 plan 211 前的概念级"循环检测"描述（调用签名 `toolName + normalizedArgs` + 软提示/硬中断两级处理）。plan 211 把该概念规格化为完整 `IGoalTracker` 契约，裁定为首版只实现硬中断（STUCK → abort），软提示（自动 course-correction）降级为 Non-Goal successor。

### 5.4 检查点与恢复

检查点的目标是：

- 长任务可恢复
- 崩溃后可恢复到最近安全点
- plan/todo/message/token budget 等状态可继续使用

**实现状态（L3-4 ✅ 已落地 + plan 183 ✅ restore 路径消费 + plan 186 ✅ DB-backed 持久化）**：检查点的 **记录/检索能力** 已落地为 `ICheckpointManager` 契约 + `NoOpCheckpoint` 透传默认 + `ToolExecutionCheckpoint` in-memory 功能化实现 + `FileBackedCheckpointManager` 文件级持久化 + `DBCheckpointManager` DB-backed 持久化。分发循环在每次工具执行完成后调用 `saveCheckpoint`（§5.4a "工具执行后" 触发点），checkpoint 携带 toolName/callId/input-output 摘要 + messageCount/tokenEstimate 快照。检索方法 `getLatestCheckpoint` / `getCheckpoint(watermark)` 是契约表面，**已在 plan 183 restore 路径被运行时消费**——`DefaultAgentEngine.restoreSession` 调用 `getLatestCheckpoint` 获取 resume-point 元数据 + 一致性校验（checkpoint.messageCount ≤ 持久化 session.messageCount）。

**架构决策**：

- **save-side 先行，restore-side ✅ 已落地（plan 183）**：L3-4 交付 save side（记录 + in-memory 检索）。restore-side crash/restart restore protocol 已由 plan 183 落地：`IAgentEngine.restoreSession(sessionId, approver, reason)` 从持久化 session + checkpoint 恢复，详见 §1.1。跨进程 session 接管锁仍依赖 L4-8 Actor Runtime，是独立 successor。这平行于 L3-6 模式：plan 177 交付 `recordDenial` save-side，plan 180 单独交付 sticky-pause restore-side。
- **DB-backed 持久化 ✅ 已落地（plan 186，实现为 `DBCheckpointManager`）**：`DBCheckpointManager` 使用 raw JDBC（`DataSource` + `PreparedStatement`，与 `DBSessionStore`/`DBDenialLedger`/`DBMessageService` 同级），将 checkpoint 持久化到 `ai_agent_checkpoint` 表。hybrid 列布局：9 scalar 列（`WATERMARK`/`SESSION_ID`/`SEQ`/`CHECKPOINT_TIMESTAMP`/`CHECKPOINT_TYPE`/`TOOL_NAME`/`CALL_ID`/`MESSAGE_COUNT`/`TOKEN_ESTIMATE`）+ 2 CLOB 列（`INPUT_SUMMARY`/`OUTPUT_SUMMARY`——这两字段承载全量 tool I/O，非短摘要，对比 `DBSessionStore` 的单一 `SESSION_DATA` CLOB）。`saveCheckpoint` 使用 INSERT（append-only，非 `MERGE INTO` upsert——checkpoint 是追加非覆写，与 `DBSessionStore.save` 的 upsert 语义不同）；`getLatestCheckpoint` 经 `WHERE SESSION_ID = ? ORDER BY SEQ DESC` SQL（无 `LIMIT 1`，warm-cache 模式）；write-through `ConcurrentHashMap` cache（同 `FileBackedCheckpointManager`/`DBSessionStore`）。checkpoint 持久化到 DB 后跨进程 crash/restart 任何服务实例可检索 resume-point。跨进程接管锁仍 deferred（依赖 L4-8 Actor Runtime）。
- **reliability 包归属**：checkpoint 类型放入 `io.nop.ai.agent.reliability` 包，与 `io.nop.ai.agent.security`（安全/治理层）平行。可靠性增强（L3-1 circuit breaker / L3-2 retry / L3-3 goal tracker / L3-4 checkpoint / L3-8 sustainer）非安全治理，包边界反映这一区分。
- **in-memory 触发范围**：`ToolExecutionCheckpoint` 对 **所有** 工具执行记录 checkpoint（非仅 long-running tool）。§5.4a "仅 long-running tool" 限定本是为约束持久化 I/O 频率，对 in-memory 默认不适用；过滤将下沉到持久化层（roadmap A4 journal/snapshot）应用。

### 5.4a Checkpoint Journal 格式（MiMoCode 吸收）— roadmap A4（✅ 已落地）

> **状态**：journal.md + snapshot.json 双文件格式 + 按 watermark 恢复读取路径 + 文件级 `ICheckpointManager` 功能化实现（`FileBackedCheckpointManager`）已落地（plan 182 / roadmap A4）。格式层包含 `CheckpointJournalWriter`（追加写入 §5.4a markdown 段落）、`CheckpointJournalReader`（全量 + 增量读取）、`CheckpointSnapshotWriter`/`CheckpointSnapshotReader`（JSON 派生缓存）。文件级 manager 是 `ToolExecutionCheckpoint` 的 drop-in 替换（同一契约，存储后端从 in-memory 换为文件），checkpoint 跨进程重启存活。**crash/restart restore protocol 已由 plan 183 落地**——`IAgentEngine.restoreSession` 消费 `getLatestCheckpoint` 作为 resume-point metadata + 一致性校验（checkpoint 子系统首次在 restore 路径被运行时消费，详见 §1.1 + §5.4）。

参考 MiMoCode 的 `checkpoint.ts`（1478 行），检查点日志采用 journal.md（追加写入）+ snapshot.json 双文件格式：

**journal.md**（追加写入，source of truth）：
```markdown
# Checkpoint Journal - sess-001

## CP-001
type: tool_execution
seq: 1
timestamp: 2026-06-12T10:00:00Z
entries:
  - tool: file_write
    callId: call_abc
    input: { path: "src/main.java", content: "..." }
    output: { status: "ok" }
watermark: cp_001

## CP-002
type: llm_turn
seq: 2
timestamp: 2026-06-12T10:01:00Z
entries:
  - turn: 3
    promptTokens: 4500
    completionTokens: 1200
    toolCalls: [call_def, call_ghi]
watermark: cp_002
```

**snapshot.json**（派生缓存，可重建）：
```json
{
  "snapshotId": "snap-003",
  "sessionId": "sess-001",
  "lastWatermark": "cp_002",
  "messageCount": 14,
  "tokenEstimate": 8500,
  "planStatus": { "phase": "implementation", "progress": "0.6" },
  "toolResults": [
    { "callId": "call_def", "tool": "file_write", "status": "success" }
  ],
  "createdAt": "2026-06-12T10:01:00Z"
}
```

**恢复流程**：
1. 定位最近的 `snapshot.json`
2. 从 `lastWatermark` 之后的 journal entries 重建增量状态
3. 加载 `firstKeptEntryId` 之后的消息（与 session-and-storage.md §5.3 一致）。checkpoint 加载侧的 `firstKeptEntryId` 语义由 COMPACTION checkpoint 位置实现——`FileBackedCheckpointManager.loadSessionFromDisk` / `DBCheckpointManager.loadSessionFromDb` 加载全部 checkpoint 后，截断 per-session `bySession` 列表到最近 `CheckpointType.COMPACTION` checkpoint（inclusive），丢弃 pre-compaction stale checkpoint（plan 188 已落地）

**触发时机**：
- ✅ 每个 LLM turn 完成后自动写入 journal entry（plan 187，`LLM_TURN` checkpoint 发射——dispatch loop 在 token 计账完成后、completion judge 之前发射）
- ✅ 压缩时生成完整 snapshot（plan 187，`COMPACTION` checkpoint 发射——`performCompaction` 在实际压缩成功后发射；snapshot.json 文件生成仍 deferred）
- ✅ 工具执行前后（仅 long-running tool）写入 tool-level checkpoint（plan 181，`TOOL_EXECUTION` checkpoint 发射）

**与 Nop Event Log 的关系**：
- Journal 是 Event Log 的运行时加速结构，不是替代
- Event Log（`events.jsonl`）保持 source of truth 地位
- Journal 提供按 watermark 快速定位和恢复的能力
- Phase 1 用 Event Log 重建即可；Phase 2 可选启用 journal 加速恢复

**架构决策（A4 已落地）**：

- **文件级 drop-in 模式（同契约、存储后端替换）**：`FileBackedCheckpointManager` 与 `ToolExecutionCheckpoint` 实现同一 `ICheckpointManager` 契约（saveCheckpoint/getLatestCheckpoint/getCheckpoint）。executor/engine 的 dispatch-path 接线（`ReActAgentExecutor` dispatch loop，工具执行后调用 `saveCheckpoint`）不变。选择哪个实现取决于是否需要跨进程持久化——Builder 注入点不变，存储后端从 in-memory 换为 journal.md + snapshot.json。
- **snapshot 生成时机（周期性/按需，compaction 延期）**：snapshot.json 在配置阈值（默认每 10 个 checkpoint）或显式 `flushSnapshot` 时生成。compaction-triggered snapshot（`IContextCompactor` 压缩时自动生成）是独立增强，不在 A4 scope。默认阈值确保 snapshot 不会过时（恢复时 journal replay 范围有界），同时避免每次 saveCheckpoint 都重写 snapshot.json 的 I/O 成本。
- **long-running 过滤裁定（默认全记录 + 可配置过滤）**：`FileBackedCheckpointManager` 默认记录所有 tool-execution checkpoint（与 L3-4 in-memory 行为一致）。理由：(1) "long-running" 是部署特定概念，框架层无法统一定义；(2) 默认全记录降低认知负担；(3) 过滤是非阻塞配置增强，不影响格式契约。
- **Event Log 关系（journal 独立于 Event Log）**：journal.md 是 checkpoint 的 source of truth（追加写入、可重建 checkpoint 列表）；Event Log（events.jsonl）是 event 的 source of truth。两者独立——journal 不从 Event Log 派生也不同步。Journal 提供 watermark 恢复能力，Event Log 提供事件溯源能力。Event Log 集成（journal 作为 Event Log 加速结构）是独立增强。
- **recovery-critical 字段子集（planStatus/toolResults 延期）**：A4 snapshot.json 含 recovery-critical 字段子集（snapshotId/sessionId/lastWatermark/messageCount/tokenEstimate/createdAt）。§5.4a 完整字段的 planStatus（plan 阶段/进度）+ toolResults（聚合工具结果）需要 session 级状态访问（超出 checkpoint 值类型承载范围），属 crash/restart restore successor。
- **watermark 恢复读取路径**：文件级 manager 在首次访问 session 时从文件加载——(1) 读取 snapshot.json（如存在）获取 lastWatermark 元数据；(2) 读取 journal.md 全量 entries 重建完整 checkpoint 列表 + watermark 索引（确保 `getCheckpoint(watermark)` 对任意 watermark 可用）；(3) 若 snapshot.json 不存在，全量扫描 journal.md（降级路径，正确但不加速）。`CheckpointJournalReader.readAfter`（增量读取，已测试验证）是 restore-on-restart successor 消费的增量读取原语。
- **LLM_TURN / COMPACTION 触发点 ✅ 已落地（plan 187）**：dispatch loop 在每次成功 LLM 响应后（token 计账完成后、completion judge 之前）发射 `LLM_TURN` checkpoint，`performCompaction` 在实际压缩成功后（消息替换 + token 调整后）发射 `COMPACTION` checkpoint。三个触发点共享同一 per-execution `checkpointSeq` counter，seq 跨类型在单次 execute() 内单调递增。watermark 嵌入 per-execution start time 保证跨 execute() 调用唯一（防 DB-backed manager PK 冲突——restore re-execution 的 LLM_TURN(0) 不与 pre-crash LLM_TURN(0) 碰撞）。两个新触发点各自跟 intra-execution persistence（同 plan 183 TOOL_EXECUTION 模式：replaceMessages + sessionStore.save），维持 restore 一致性不变量（checkpoint.messageCount ≤ session.messageCount）。NoOpCheckpoint 默认透传；NoOpContextCompactor 默认不触发 COMPACTION checkpoint。`Checkpoint` 值类型不扩展——LLM_TURN 复用 outputSummary 捕获 assistant 响应、tokenEstimate 捕获累计 token；COMPACTION 的 outputSummary 捕获压缩前后 token/message 变化。independent prompt/completion token 分拆 + per-session seq 跨 restore 持久化 + compaction-triggered snapshot.json 文件生成仍是独立 successor（firstKeptEntryId compaction-aware 截断加载 ✅ 已由 plan 188 落地）。
- **crash/restart restore protocol ✅ 已落地（plan 183）**：A4 交付 checkpoint 文件持久化 + watermark 恢复读取路径。plan 183 落地 crash/restart restore 协议（`IAgentEngine.restoreSession`）：从 `FileBackedSessionStore` 加载持久化 session（完整消息历史）→ `getLatestCheckpoint` 一致性校验（checkpoint.messageCount ≤ session.messageCount；违反时 best-effort 警告，不阻断）→ `buildBaseExecutionContext` 重建 → status 置 running → 发布 `SESSION_RESTORED` 事件 → `resolveExecutor` + `executor.execute` 续跑。checkpoint journal **首次在 restore 路径被运行时消费**（plan 182 投资兑现）。**自动 restore-on-startup ✅ 已落地（plan 184）**：`IAgentEngine.restorePendingSessions(approver, reason)` 是 auto-restore 批量入口（发现 → 筛选 running/pending 候选 → 逐个 restoreSession → 摘要），消费 plan 183 单 session primitive + 新增 `ISessionStore.listAllSessions()` 磁盘发现契约（不改 `getAll()` 语义）。详见 §1.1。跨进程 session 接管锁（防多实例同时恢复）+ DB-backed session store 仍是独立 successor（firstKeptEntryId compaction-aware 截断加载 ✅ 已由 plan 188 落地）。
- **compaction-aware 截断加载 ✅ 已落地（plan 188）**：`FileBackedCheckpointManager.loadSessionFromDisk` 和 `DBCheckpointManager.loadSessionFromDb` 在加载全部 checkpoint 到内存后，截断 per-session `bySession` 列表使其从最近的 `CheckpointType.COMPACTION` checkpoint 开始（inclusive）。截断使 `getCheckpoints` / `getLatestCheckpoint` 返回的 active restore set 仅含 post-compaction checkpoint——pre-compaction checkpoint 的 messageCount 超过压缩后 session 的 messageCount，保留它们会违反文档化的 `checkpoint.messageCount ≤ session.messageCount` 一致性不变量。架构决策：(1) **截断点从已有 `COMPACTION` type 派生**（不新增 `firstKeptEntryId` 字段——避免 `Checkpoint` 值类型扩展 + DB schema 变更）；(2) **`bySession` 截断 + `byWatermark` 保留（职责分离）**——`bySession` = compaction-aware active set（restore 用），`byWatermark` = full history index（audit 用）；file-backed manager 的 journal.md 和 DB-backed manager 的 `ai_agent_checkpoint` 表行不删除（持久化审计历史完整保留）；DB-backed `getCheckpoint(oldWatermark)` 经 `loadCheckpointFromDb` direct-query fallback 仍解析 pre-compaction checkpoint；(3) **COMPACTION inclusive**——COMPACTION checkpoint 是 post-compaction 基线的第一个 checkpoint，合法恢复起点，保留；(4) **无 COMPACTION 不截断（向后兼容）**——NoOpContextCompactor 默认不压缩，无 COMPACTION checkpoint 时全量保留，行为与 pre-plan-188 完全一致；(5) **load-time only**——截断在初始 load（cache-miss）时执行一次，不删持久化数据、不 re-truncate（active execution 中第二次 compaction 后 inter-compaction stale checkpoint 保留在内存直到进程结束，非阻塞优化）。截断逻辑由共享工具类 `CompactionAwareTruncation.truncateToLatestCompaction(List<Checkpoint>)` 提供。

## 6. 工具失败处理

### 6.1 两类失败

工具失败建议分成两类处理：

- 物理性失败
  - 超时
  - 网络异常
  - provider 暂时不可用
- 语义性失败
  - 参数错误
  - 工具名错
  - 输出不符合预期

### 6.2 处理原则

- 物理性失败优先走程序化策略
- 语义性失败再交给 LLM 或 Advisor Agent 决策

这样可以避免把所有错误都扔给 Agent 自己推理，导致确定性问题也被 prompt 化。

## 7. 上下文窗口保护

基于 5 框架（Codex, Claude Code, OpenCode, SolonCode, Reasonix）源码级深度调研（详见 `ai-dev/analysis/agent-survey/2026-06-10-token-estimation-and-context-compression-survey.md`），结合 Nop 框架的可插拔定位，确定以下策略。

### 7.1 设计原则

1. **逐级升级，不可跳级**——先尝试零成本操作，再尝试无 LLM 调用操作，最后才调用 LLM 生成摘要
2. **5 层管道为默认实现**——清晰、可操作、每个层级做什么和什么时候触发一目了然
3. **双维度触发**——token 占比和消息数量两个维度独立检查，任一越线即触发（来自 SolonCode 的实践）
4. **可插拔策略作为 Layer 3 扩展点**——默认 5 层管道不需要任何配置即可运行，高级用户通过 `ICompressionStrategy` 接口替换或扩展
5. **前缀缓存感知**——压缩操作不影响 `prefixLength` 前缀区（见 `nop-ai-agent-llm-layer.md` §八）

### 7.2 五层保护模型（默认实现）

| 层级 | 名称 | LLM 调用 | 触发时机 | 操作 | 参考 |
|------|------|---------|---------|------|------|
| Layer 0 | Tool Result 预截断 | 无 | 每次工具执行后 | 截断 tool result 超过阈值（默认 8000 tokens）的部分，保留 head + 1KB tail | Reasonix |
| Layer 1 | 零成本微压缩 | 无 | ReAct 循环每轮检查 | 替换旧 tool_result 内容为 placeholder，仅处理可压缩工具（read_file, bash, grep 等），保留最近 N 条 | PilotDeck MicroCompaction |
| Layer 2 | 中间 Turn 裁剪 | 无 | 超过 Layer 1 阈值 | 裁剪中间 turns，保留 head + tail anchors，维护 tool_call/tool_result 跨边界完整性 | PilotDeck SnipEngine |
| Layer 3 | LLM 摘要 | 1 次（便宜模型） | 超过 Layer 2 阈值 | LLM 生成结构化摘要（增量更新前次 summary），完整历史 offload 到持久存储 | OpenCode + DeepAgents |
| Layer 4 | 强制退出 | 0 或 1 次 | context >90% | 生成最终摘要，停止工具调用，发布 AgentEvent.FORCED_STOP | Reasonix |

**逐级升级规则**：Layer 0 是独立预截断（每次工具执行后自动应用），Layer 1→2→3 逐级尝试——本级解决问题则停止，不跳级。Layer 4 是硬上限保护。

**各层 NoOp/fallback 行为**：

| 层级 | 前提条件不满足时 | 触发条件独立？ |
|------|---------------|-------------|
| Layer 0 | `enabled=false` 时不截断 | ✅ 独立（每次工具执行后） |
| Layer 1 | 无可压缩工具（非 read_file/bash/grep 等）→跳过 | ✅ 独立（ReAct 每轮检查 token 占比） |
| Layer 2 | head + tail 窗口重叠（消息过少）→跳过 | ⚠️ 依赖 Layer 1 已执行。**设计修正**：Layer 2 改为独立检查自己的触发条件（`messageCount > layer2Threshold`），而非"超过 Layer 1 阈值" |
| Layer 3 | LLM 不可用 → 降级为 Layer 2 效果（保留更多原始消息），记录 fallback 日志 | ✅ 独立（有自己的触发阈值） |
| Layer 4 | 不适用（硬保护始终生效） | ✅ 独立 |

### 7.3 触发机制

**双维度 OR 门**（参考 SolonCode）：

```
ReAct reason 循环开始前（每轮检查）:
  tokenEstimate > maxTokens * triggerTokenPercent    // 默认 0.8
  OR
  messageCount > triggerMaxMessages                   // 默认 30

  任一条件满足 → 从 Layer 1 开始逐级尝试压缩
```

**两个触发点**：

1. **Pre-iteration**（每轮 ReAct reason 前检查）：`ILlmDialect.estimateTokens()` 本地估算 vs 阈值
2. **Post-response**（每次 LLM 响应后检查）：Provider 报告的精确 `usage.prompt_tokens` vs 阈值。Post-response 使用精确值，可校准 Pre-iteration 的估算偏差

### 7.4 Token 计数

双层策略：Provider 报告为主，轻量估算为辅。

- **Post-call（精确）**：直接使用 LLM API 响应中的 `usage.prompt_tokens` / `usage.completion_tokens`。这是精确值，无需自行计算
- **Pre-call（估算）**：通过 `ILlmDialect.estimateTokens()` 估算，缺省 chars/4（见 `nop-ai-agent-llm-layer.md` §4.0）。对触发阈值（80%）来说精度够用——chars/4 的 ±10% 偏差不会导致错误决策
- **校准**：Post-call 精确值与 Pre-call 估算值的偏差记录在 Dialect 内部，用于修正后续估算

### 7.5 摘要策略

- **增量更新**：如果前次 summary 存在，用 `<previous-summary>` 传递，要求 LLM 增量更新（非全量重写）（OpenCode 模式）
- **结构化模板**（8 节）：Goal / Constraints & Preferences / Progress (Done/In Progress/Blocked) / Key Decisions / Next Steps / Critical Context / Relevant Files
- **文件追踪**：Compaction 时提取 read/modified 文件列表，附加到摘要末尾（pi-agent 模式）
- **专用模型**：摘要生成使用便宜/快速模型，不与主 Agent 的 LLM 竞争（Reasonix 用 flash，Hermes 用 auxiliary）。通过 `compressionModel` 配置
- **PreCompact hook**：压缩前保存关键状态（todo, plan, project memory），压缩后重新注入（oh-my-claudecode 模式）。使用 `PRE_COMPACT` / `POST_COMPACT` 生命周期点（`02-execution-model.md` §5.1 Layer 2 扩展）

### 7.6 压缩后保留规则

必须保留：

1. 最早的 system message
2. 最早的用户初始目标
3. 压缩生成的摘要消息
4. 最近 N 条消息（`keepTailPercent` 决定大小）
5. Pinned items（活跃 skills、约束条件——Reasonix 的 `<skill-pin>` 模式）
6. 文件追踪列表（read-files, modified-files——pi-agent 模式）

禁止递归压缩：执行压缩的流程本身不再触发压缩。

### 7.7 配置模型

通过 `agent.xdef` 的 `<compaction>` 元素配置。所有参数都有合理默认值，零配置即可运行。

```xml
<agent name="my-agent">
  <compaction enabled="true"
              triggerTokenPercent="0.8"
              triggerMaxMessages="30"
              forcedStopPercent="0.9"
              keepTailPercent="0.15"
              compressionModel=""/>
</agent>
```

| 参数 | 默认值 | 含义 |
|------|--------|------|
| `enabled` | `true` | 是否启用自动压缩 |
| `triggerTokenPercent` | `0.8` | token 占比超过此值触发（Post-call 用精确值，Pre-call 用估算值） |
| `triggerMaxMessages` | `30` | 非首链消息数超过此值触发 |
| `forcedStopPercent` | `0.9` | 超过此值触发 Layer 4 强制退出 |
| `keepTailPercent` | `0.15` | Layer 3 压缩时保留尾部消息的比例 |
| `compressionModel` | 空（=主模型） | Layer 3 摘要使用的模型（建议用便宜模型） |

### 7.8 可插拔策略（Layer 3 扩展点）

默认 5 层管道覆盖绝大多数场景。如果需要定制，实现 `ICompressionStrategy` 接口并通过 Delta 替换 Layer 3 的默认摘要逻辑：

```
ICompressionStrategy:
  String name()
  CompactionResult compact(CompactionContext ctx)
```

可用策略实现（Layer 3 可选）：

| 策略 | LLM 调用 | 机制 | 参考 |
|------|---------|------|------|
| `FullSummary`（默认 Layer 3） | 1 次 | 结构化摘要（8 节模板），增量更新 | OpenCode |
| `KeyInfoExtraction` | 1 次 | 提取业务参数、确认事实、失败路径 | SolonCode |
| `HierarchicalRolling` | 1 次 | 合并旧摘要 + 新过期消息，输出有界（max 500 chars） | SolonCode |
| `VectorArchive` | 0 次 | 过期消息存入向量存储，提供 recall 工具 | SolonCode |

Delta 定制示例：

```xml
<!-- delta: /delta/default/my-agent.agent.xml -->
<compaction>
  <strategy class="com.mycompany.MyCustomSummaryStrategy"/>
</compaction>
```

### 7.9 与前缀缓存的协同

压缩操作只修改 `messages[prefixLength..]` 的 Log Zone，不触及前缀区。引擎层在 `AgentExecutionContext` 中维护 `prefixLength` 和 `prefixHash`（见 `nop-ai-agent-llm-layer.md` §八），压缩前后校验前缀完整性。

## 8. 超时与预算

时间预算与 token 预算本质上都是有限资源控制问题。

建议统一概念：

- token budget
- timeout budget

并在运行时支持父子预算级联，避免子调用无限制消耗整个 Agent 的资源。

## 9. 输出和安全限制

工具层还应具备几个基础保护：

- 输出大小上限
- shell 工作目录限制
- 环境变量白名单
- 路径穿越防护
- 进程组终止策略

这部分越程序化越好，不应依赖 prompt 来守护。

## 10. 推荐实施顺序

可靠性增强建议按下面顺序落地：

1. 错误分类
2. 分层超时
3. 上下文压缩
4. 工具验证和安全限制
5. 循环检测
6. 模型回退和断路器
7. 检查点和恢复

## 11. 明确延期项

下面这些先不要作为 MVP 强依赖：

- ~~完整断路器策略~~（plan 210 ✅ 已落地：`ICircuitBreaker` + `AlwaysClosed` 默认 + `ThresholdBreaker` 功能实现，见 §3.3 / §5.1）
- 多模型冷却追踪
- 全量恢复存储模型
- 所有 provider 的统一回退配置

建议保留但后置的失败升级链：

- `retry-advisor` 返回 `repair`
- 在隔离上下文中尝试 AI 修复
- 修复失败后触发 `bug-report` 或等价故障上报工具

这条链路不进入 Layer 1-2，但应作为 Layer 3 设计保留。

## 11a. 拒绝了什么

### 拒绝：所有错误都交给 LLM 自行推理

**方案**：不做错误分类，所有工具调用失败都由 LLM 判断是否重试。

**拒绝理由**：确定性错误（参数错误、权限错误、工具名不存在）不需要 LLM 推理，程序化处理更快更准。混合处理会导致确定性问题被 prompt 化，浪费 token 且结果不稳定。

### 拒绝：统一重试策略（不区分 Provider 级别 vs 工具级别）

**方案**：用一个通用重试策略覆盖 LLM 调用失败和工具调用失败。

**拒绝理由**：LLM 调用失败需要 429 语义分类、Retry-After 解析、流式保护（已流出内容不 failover）、图片 fallback——这些是 Provider 特有逻辑。工具调用失败需要参数修复、schema 验证——逻辑完全不同。强行统一会导致策略既不适合 LLM 也不适合工具。

### 拒绝：压缩时全量重写历史

**方案**：每次触发压缩时，LLM 重写整个消息历史为摘要。

**拒绝理由**：全量重写成本高（需要传入完整历史），增量更新更高效（传入前次 summary + 新消息）。OpenCode 和 Reasonix 的实践证明增量摘要效果足够好。

### 拒绝：固定四级梯度触发

**方案**：采用 Reasonix 的 75%/78%/80%/90% 四级梯度。

**拒绝理由**：四级是为 DeepSeek 1M 超大窗口优化的 Provider 特定设计。Nop 作为通用框架面向 128K-200K 的常见窗口，5 层管道的逐级升级更清晰、更通用。需要更精细控制的用户可通过 `ICompressionStrategy` 扩展点定制。

### 拒绝：Layer 1 就暴露 7 种可配置策略

**方案**：默认实现就提供 7 种可组合策略，通过 `<strategy>` 元素配置管道。

**拒绝理由**：对 Layer 1-2 的实现者来说，7 种策略是认知负担。5 层管道每一层做什么和什么时候触发一目了然，实施者读完就知道要实现什么。可插拔策略作为 Layer 3 扩展点保留，需要时才引入。

### 拒绝：断路器和 Sisyphean 同时启用

**方案**：断路器和 Sisyphean 作为可叠加的策略同时工作。

**拒绝理由**：两者代表对立的弹性哲学——"快速熔断" vs "永不放弃"。同时启用语义矛盾（断路器要终止，Sisyphean 要继续）。设计为互斥配置选项，由部署场景决定。

**互斥执行机制最终状态（plan 212 裁定）**：互斥裁定为**部署层文档约束**（非运行时 guard）。`ICircuitBreaker`（L3-1，model-call 层）与 `ISustainer`（L3-8，task-exit 层）作为独立 opt-in 扩展点共存（各自 NoOp/Always shipped 默认），引擎**不**在 setter/构造器抛互斥异常。集成商按部署场景二选一（交互式/成本敏感 → fail-fast + breaker；无人值守长执行 → Sisyphean + sustainer）。运行时硬性互斥 guard（检测 `ThresholdBreaker` + `SisypheanSustainer` 同时注册并抛异常/warn）是独立 successor。

## 12. 本篇结论

可靠性设计必须保留，但它应该是建立在稳定 Agent runtime 之上的增强层：

- 先让单 Agent 正常跑通（Layer 1）
- 再让它在真实环境中稳定运行（Layer 2 执行扩展）
- 最后再支持复杂恢复和平台级降级（Layer 3-4）

这样实现风险最低，也最符合当前阶段的成熟度。

---

## 与其他文档的关系

- `nop-ai-agent-roadmap.md` — 分层架构（本篇覆盖 Layer 1-3 的可靠性部分）
- `nop-ai-agent-llm-layer.md` — LLM 层设计（IRetryPolicy、ILlmDialect 的详细设计）
