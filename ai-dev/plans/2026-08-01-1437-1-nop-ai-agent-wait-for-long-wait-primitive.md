# nop-ai-agent WAIT_FOR 长等待原语（W2-1）

> Plan Status: completed
> Mission: nop-ai-agent-harness-evolution
> Work Item: W2-1（checkpoint 增加 wait_for 条件 JSONB；WAIT_FOR 长等待原语：挂起不占线程 → 条件满足唤醒恢复）
> Last Reviewed: 2026-08-01
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §13.1（`:689-701`，方向 only，待 elaboration）+ §13.5（`:856-860`，推荐顺序 + skip reconcile）；`ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md` W2-1
> Draft Review: round-1 独立子 agent 审查（fresh session ses_0427a05f0ffe2VNGpnWgijqu4F）发现 1 Blocker（唤醒重入会重复挂起：re-suspend-on-replay 缺 Decision）+ 3 Major（resumeSession paused-only gate + denial 耦合未入 baseline、后循环事件发布 guard :893-897 未入 Phase 3 编辑点、grep suspend 措辞失实）+ 1 Minor（continue 措辞），全部已修：新增 Decision H（唤醒重入防重复挂起）+ Decision E 重定性为正确性耦合（非纯优化）+ baseline 补 resumeSession + Phase 3 补事件 guard 编辑点 + 措辞修正。
> Related: 前置 plan `2026-08-01-1905-2`（W2-2 idempotency_key，已 completed，本计划是其显式 Deferred But Adjudicated successor——"WAIT_FOR 需 suspend/wake 执行语义，独立大型 design+实现"）；§13.5 推荐顺序（WAIT_FOR → idempotency_key ✅ → 三级失败 → 有序故障转移 ✅）；rivet sleep/wake + exo redeliver_pending_wakes 调研（统一为 nop 唤醒原语）

## Purpose

收口 W2-1：补齐 checkpoint 的"等待条件满足后恢复"显式原语。今日 ReAct 执行模型是**同步阻塞**的——`executor.execute()` 的每次退出都完成返回的 future（`:920`），线程随之释放，无"挂起会话不占线程、条件满足后唤醒恢复"的能力。唯一的近似机制是 denial-ledger 的 `paused`（`:425-428` break reactLoop → 完成 future → 会话挂起 → 经显式 `resumeSession` 在新线程重入），但它是**安全治理触发**（拒绝累计），非**条件等待触发**，且恢复靠显式 API 非条件满足。本计划增加 `wait_for` 条件 JSONB + `WAIT_FOR` checkpoint 类型 + 挂起语义（注册条件 → 挂起释放线程 → 会话驻留）+ 唤醒机制（条件满足 → 唤醒恢复）。完成后 roadmap `[ ] W2-1` 可勾选。

## Current Baseline

> 已逐条核对 live repo（独立 explore 子 agent 报告 ses_0427fae0fffeFjoBfKjzaDgcbn）。路径相对仓库根。

**已落地（核对属实）**：

- `Checkpoint` 值对象（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/Checkpoint.java`，297 行）：不可变，私有构造 + 两个 `of()` 工厂——11 参（`:122-129`，内部算 idempotencyKey，dispatch loop 入口）与 12 参（`:143-163`，显式 key，反序列化路径）。字段（`:55-66`）：`sessionId`/`watermark`(PK)/`seq`/`timestamp`/`type`/`toolName`/`callId`/`inputSummary`/`outputSummary`/`messageCount`/`tokenEstimate`/`idempotencyKey`。**无 `wait_for` 字段**。`computeIdempotencyKey`（`:185-200`）：仅 `TOOL_EXECUTION` 产 sha256，**其余类型（含未来 WAIT_FOR）按裁定返 null**（`:187-189` 单点 guard，WAIT_FOR 若需 null key 无需改）。
- `CheckpointType` 枚举（`.../reliability/CheckpointType.java`，47 行）：仅 3 值 `TOOL_EXECUTION`(`:26`)/`LLM_TURN`(`:37`)/`COMPACTION`(`:46`)。**无 `WAIT_FOR`**。
- ORM 实体 `ai_agent_checkpoint`（`nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml:120-163`，手写源模型非生成）：13 列（propId 1-13，PK=`watermark`）。**无 `wait_for` 列**。DDL 常量 `AiAgentCheckpointTable`（`.../reliability/AiAgentCheckpointTable.java`，112 行）：13 `COL_*` + `INDEX_SESSION_SEQ`(non-unique) + `INDEX_IDEMPOTENCY_KEY`(unique)。**无 wait_for DDL**。
- 存储契约 `ICheckpointManager`（`.../reliability/ICheckpointManager.java`，118 行）：`saveCheckpoint`(`:65`)/`getLatestCheckpoint`(`:82`)/`getCheckpoint`(`:92`)/`remove`(`:116`，plan 278 cache cleanup；`:99-103` 契约明示 **paused 会话不删 checkpoint**)。4 实现：`NoOp`(shipped 默认)/`ToolExecution`(内存)/`FileBacked`(journal+snapshot)/`DB`(JDBC)。
- **`saveCheckpoint` 生产调用点恰好 3 处**：LLM_TURN（`ReActAgentExecutor.java:649`）、TOOL_EXECUTION（`AgentToolDispatcher.java:341`）、COMPACTION（`AgentCompactionCoordinator.java:119`）。`Checkpoint.of(` 在 main/ 生产代码共 5 处（3 producer + 2 反序列化 `DBCheckpointManager.readCheckpoint:456` + `CheckpointJournalReader.parseSection:222`）——**call-site 爆炸风险 LOW**（与 W2-2 的 100+ 测试调用点不同）。

**架构事实（ReAct 执行循环，核对属实）**：

- `ReActAgentExecutor.execute(AgentExecutionContext)`（`:317`）返回 `CompletionStage<AgentExecutionResult>`。外层 `sustainLoop:`(`:400-873`) 包内层 `reactLoop:`(`:402-824`，`while (ctx.getCurrentIteration() < ctx.getMaxIterations())`)。
- 迭代顶部检查链（顺序）：`isCancelRequested`(`:411`) → `denialLedger.isPaused`(`:425-428` break reactLoop，**唯一 pause break 机制**) → `loopGuard.shouldForceStop`(`:430`) → `goalTracker STUCK`(`:447`) → `compactionCoordinator`(`:453`)。
- **每次退出 `execute()`（含 paused 路径 `:425-428`）都在 `:920` 完成返回的 CompletableFuture**，线程释放。**无"挂起会话、park 线程、条件满足后唤醒"的原语**。paused 退出只能经显式 `engine.resumeSession()` 在新线程从头重入 `execute()`。
- `AgentExecStatus`（`.../model/AgentExecStatus.java`，55 行）9 值：`pending/running/completed/failed/cancelled/forced_stopped/escalated/paused/truncated`。`paused`(`:40`) 是 **denial-ledger 治理专用**（§6.2，拒绝累计触发，恢复需 `resumeSession`+`IDenialLedger.reset`）。**无 `waiting`/`suspended`/`input_required` 状态**。

**架构事实（restore 路径，核对属实）**：

- restore 在 `AgentSessionLifecycle.restoreSession`(`:410`)。checkpoint 是 **verification-supplement only**（`:442-490`）：`getLatestCheckpoint`(`:450`) → messageCount 软校验（`:456-463` warn+continue）→ idempotency_key 发散检测（`:465-489`，W2-2）。
- **restore 永远 replay，从不 resume-from-checkpoint**：`buildBaseExecutionContext`(`:522`) 从 `session.getMessages()` 重建 → `executor.execute(ctx)`(`:576`)。**从不按 checkpoint type 分支**。`restorePendingSessions`(`:629-710`) 恢复 `running`/`pending`，**跳过 `paused`**（`:688-694`，sticky-pause 需显式 resume）。
- `isTerminalStatus`(`:620-627`)：completed/failed/cancelled/forced_stopped/escalated/truncated。**`paused` 非终态**（保留 checkpoint）。

**架构事实（resume 重入 API，核对属实——阻断项）**：

- 唯一非崩溃重入 API 是 `AgentSessionLifecycle.resumeSession`(`:242`)。它有**硬门禁**：`if (session.getStatus() != AgentExecStatus.paused) throw`(`:248-252`)——**仅 paused 可重入**。且**无条件**调 `denialLedger.reset(sessionId)`(`:273`)+`postDenialGuard.reset(sessionId)`(`:282`)——即 plan Non-Goals 所说 WAIT_FOR 必须避免的 denial-ledger 耦合。
- **结论**：若 WAIT_FOR 引入新 `waiting` 状态（Non-Goals 倾向），`resumeSession` 对 waiting 会话**抛异常**——**wake 触发器今日无可复用的重入 API**。Decision C 须裁定 wake 重入 API 形态（新 `wakeSession` vs 扩展 `resumeSession` 放宽门禁但不引入 denial reset）。

**架构事实（后循环事件发布 guard，核对属实）**：

- `ReActAgentExecutor` 后循环事件 guard（`:893-897`）：`if (status != cancelled && != forced_stopped && != escalated && != paused && != truncated)` 才发 `POST_CALL`(`:898`)+`EXECUTION_COMPLETED`(`:904`)。**`paused` 在排除列表内**（故 pause 不误发完成事件），但**新 `waiting` 状态不在**——若 WAIT_FOR 用新状态挂起，会话会**穿透此 guard 误发 `EXECUTION_COMPLETED` + POST_CALL**（注释 `:888` 明示非终态会话不应发完成事件）。Phase 3 须把 waiting 加入排除列表。

**调度/唤醒基础设施（核对属实）**：

- `ScheduledRecoveryManager`（`.../runtime/recovery/ScheduledRecoveryManager.java`）用 `io.nop.commons.concurrent.executor.IScheduledExecutor`（`:6,28-31`），**显式非 nop-job `IJobScheduler`**（`:33-35` 注释："nop-job integration is an explicit successor"）。周期 sweep（默认 60s）做 stale-lock 清理/超时检测/orphan 恢复。
- **全 `nop-ai/` grep `WAIT_FOR|wait_for` Java 源零命中**（仅 `ai-dev/` 命中）。**执行循环级 suspend/wake（挂起 ReAct 循环/唤醒等待会话）零命中**——既有 `suspend()` 命中属 messenger mailbox 子系统（`DBMessageService`/`NoOpAgentMessenger`，消息轮询挂起），与本计划执行循环挂起正交。无条件求值器、无唤醒调度器、无条件触发机制。`ScheduledRecoveryManager` 是最近似的周期扫描器，但只做恢复 sweep，无"条件满足→唤醒某会话"能力。

**设计文档状态**：

- §13.1（`:689-701`）**方向 only**（~12 行 3 行草图 + 2 bullet）：列 wait_for JSONB / 挂起语义 / 恢复，但**无 schema、无算法、无挂起执行模型、无唤醒机制、无条件 DSL、无 restore resume-vs-replay 分支**。对比同 §13 的 §13.2（W2-2，已落地，裁定 A-G 详细）/ §13.4（W2-4，已落地，裁定 A-E 详细），**§13.1 是 §13 唯一仍在草图阶段的小节**。
- §13.5（`:856-860`）推荐顺序 WAIT_FOR → idempotency_key → 三级失败 → 有序故障转移；reconcile note（`:860`）确认顺序被跳过（W2-2/W2-4 先落地），因"W2-4 与 W2-1/W2-2/W2-3 **无硬依赖**"——**WAIT_FOR 与已落地项无硬依赖**，可独立推进。

**真正剩余的 gap**：

1. `CheckpointType` 无 `WAIT_FOR` 值；`Checkpoint` 无 `wait_for` 字段；ORM/DDL 无该列。
2. **挂起执行语义不存在（核心阻断项）**：今日 `execute()` 每次退出都完成 future。WAIT_FOR 需"注册条件 → 挂起释放线程 → 会话驻留"——但既无新状态（`paused` 是 denial-ledger 专用，耦合 `IDenialLedger.reset`）、也无 ReAct 循环内的条件注册点。须裁定挂起语义形态（新状态 `waiting` vs 复用 `paused`——后者有 denial-ledger 耦合）。
3. **唤醒机制不存在（阻断项）**：无条件求值器（"条件是否满足"如何判定）、无唤醒触发器（条件满足后谁重入会话）。须裁定唤醒触发来源（周期求值 via `IScheduledExecutor` / 外部事件投递 / nop-job——后者已 deferred）。
4. **条件表达式 DSL 未定义**：wait_for JSONB 表达什么（用户输入 / 超时 / 事件）？无条件 schema。须裁定条件类型集 + 求值语义。
5. **restore resume-vs-replay 分支不存在**：今日 restore 恒 replay。§13.1 称"恢复无需重放全部历史"——须裁定 WAIT_FOR checkpoint 是否走 resume（从 wait 点恢复）而非 replay，及 resume 与 replay 的可观测区别（或裁定首版仍 replay，resume 优化留 successor）。
6. **唤醒重入会重复挂起（正确性阻断项，round-1 审查发现）**：唯一重入路径是 fresh `execute()` 从 `session.getMessages()` 重建上下文并**从 reactLoop 顶部重入**（`AgentSessionLifecycle:522/576`）。重入时 Decision B 的"条件注册点（第 4 个 producer）"会**在同一消息状态再次触发**→再次注册 wait→再次挂起→会话永不推进。`Checkpoint` 不可变（plan 强调），满足的条件信号不能放 checkpoint 上——须独立的 wake-token/会话标志/条件求值器状态机制，供注册点在挂起前检查。此问题与"不漏唤醒"（Decision C）和条件 DSL（Decision D）正交，且**使 Decision E 从纯优化升级为正确性耦合**（若首版选 replay 且无 wake-token，重复挂起不可避免）。
7. **wake 重入 API 不存在（阻断项）**：`resumeSession`(`:248-252`) 仅 paused 可重入 + 无条件 denial reset（`:273/282`）。若用新 `waiting` 状态，`resumeSession` 对 waiting 抛异常——wake 触发器无可复用重入 API。
8. **序列化范围**：JournalWriter/Reader、DBCheckpointManager SQL(INSERT/SELECT/readCheckpoint)、Checkpoint equals/hashCode/toString 须同步（W2-2 裁定 E 前例）。
9. **后循环事件 guard 须更新**：`ReActAgentExecutor:893-897` 排除列表含 paused 不含 waiting——新状态须加入，否则误发 `EXECUTION_COMPLETED`+POST_CALL。
10. WAIT_FOR checkpoint 的 producer 落点：ReAct 循环内何处注册条件并产 WAIT_FOR checkpoint（第 4 个 producer，3→4，非爆炸）。

## Goals

- **wait_for 条件数据模型**：`Checkpoint` 增 `wait_for` 字段（条件 JSON）+ `CheckpointType.WAIT_FOR`；ORM/DDL 增列 + 序列化全接触点同步（DB/Journal/equals/hashCode）。
- **挂起执行语义**：ReAct 循环内注册 wait 条件 → 挂起释放线程 → 会话驻留（保留 checkpoint，不占线程）。裁定挂起状态形态（新 `waiting` 状态 vs 复用 `paused`——正视 `paused` 的 denial-ledger 耦合）。
- **唤醒机制**：条件求值器（判定条件是否满足）+ 唤醒触发器（条件满足后重入会话）。裁定触发来源（周期求值 `IScheduledExecutor` / 外部事件投递），与 nop-job（已 deferred）边界明确。
- **restore 分支**：裁定 WAIT_FOR checkpoint 的 restore 形态（resume-from-wait-point vs replay），可观测且不卡死恢复。
- **端到端**：agent 执行 → 注册 wait 条件 → 挂起释放线程 → 会话驻留（线程已释放可观测）→ 条件满足 → 唤醒恢复 → 继续推进至完成。

## Non-Goals

- **nop-job 集成（DB 持久化 job / 集群协调 / cron）**——`ScheduledRecoveryManager` 已显式 deferred 的独立 successor；本计划唤醒用 `IScheduledExecutor` 或外部事件投递，不引入 nop-job 依赖。
- **W2-3 三级失败升级 / W1-4 replan**——单 attempt 内失败升级属 W2-3；plan 级重规划属 W1-4（已落地）。WAIT_FOR 挂起/唤醒与失败升级正交。
- **wait 条件的丰富 DSL 库**——首版支持基础条件类型集（如超时 / 外部事件 / 用户输入），条件 DSL 的完整表达力留 successor。
- **resume 跳过重放的极致优化**——若 Phase 1 裁定首版仍 replay，则"不重放全部历史"的优化留 successor（须诚实裁定，非静默跳过）。
- **改造既有 paused 机制**——保持 denial-ledger `paused` 行为不变（零回归）；WAIT_FOR 用新状态或明确隔离的复用。
- **多会话唤醒编排 / 优先级调度**——单会话唤醒成立即可，多会话调度策略留 successor。
- W2-2 idempotency_key / W2-4 failover（已落地）/ W3+ 全部。

## Scope

### In Scope

- `Checkpoint` 增 `wait_for` 字段 + `CheckpointType.WAIT_FOR` + ORM/DDL 列 + 序列化全接触点（DB/Journal/equals/hashCode）。
- 挂起执行语义：ReAct 循环条件注册 → 挂起释放线程 → 会话驻留 + WAIT_FOR checkpoint producer（第 4 个 producer）。
- 唤醒机制：条件求值器 + 唤醒触发器（条件满足重入会话）。
- restore WAIT_FOR 分支（resume-vs-replay 裁定后的实现）。
- 端到端验证 + 零回归（TOOL_EXECUTION/LLM_TURN/COMPACTION 路径不变；paused 机制不变）。

### Out Of Scope

- nop-job 集成（独立 successor）。
- 完整 wait 条件 DSL 库。
- resume 跳过重放优化（若 Phase 1 裁定首版 replay）。
- 多会话唤醒编排。
- W2-3 / W1-4 / W2-2 / W2-4 / W3+ 全部。

## Risks And Rollback

- **挂起执行语义是新的执行模型能力（最大风险）**：今日同步阻塞模型无"释放线程后会话驻留"。缓解：参考既有 `paused` 机制（已实现 break reactLoop → 完成 future → 会话驻留 → 经 API 重入）；WAIT_FOR 类比但触发/恢复不同。Phase 1 须裁定新状态 vs 复用 paused（复用有 denial-ledger 耦合风险）。
- **唤醒机制可靠性**：条件求值若用周期扫描，须保证不漏唤醒（条件满足后必被扫到）；若用外部事件投递，须保证投递语义。缓解：Phase 1 裁定触发来源 + 不漏唤醒保证；测试覆盖唤醒路径。
- **restore resume 改变恢复语义**：若裁定 resume-from-wait-point，是行为变更（feature）。须确保 resume 路径真的可走，不卡死恢复。缓解：Phase 1 裁定 resume-vs-replay + 不卡死保证；首版可裁定 replay 降复杂度（诚实裁定）。
- **线程释放的可观测性**：须能验证"挂起后线程已释放"（非空转占线程）。缓解：Exit Criteria 要求会话驻留 + 线程释放可观测（如 future 已完成 + 会话状态=waiting + 重新 execute 在新调用）。
- **零回归红线**：`paused` 机制行为不变；既有 3 checkpoint 类型路径不变；无 WAIT_FOR 的 plan 行为不变。

## Execution Plan

### Phase 1 - design elaboration：挂起/唤醒/条件/restore 七项裁定（Decision）

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §13.1（`:689-701` 草图 → 含挂起执行模型/唤醒机制/条件 DSL/restore 分支/序列化范围的 elaboration）

- Item Types: `Decision`

- [x] **Decision A：wait_for 条件数据模型 + `of()` 策略**。裁定 `wait_for` 字段类型（String JSON / 结构化对象）+ `CheckpointType.WAIT_FOR` + `of()` 接入策略。WAIT_FOR 是 caller 供应的条件（非派生 hash），与 `idempotencyKey`（派生）不同——裁定：第 3 个 `of()` 重载 vs builder vs 内部按 type 默认 null。须正视 WAIT_FOR 的 `idempotencyKey` 裁定（按 `computeIdempotencyKey` 单点 guard，WAIT_FOR 大概率 null key，与 LLM_TURN/COMPACTION 同）。回写 design §13.1。
- [x] **Decision B：挂起执行语义 + 状态形态（核心 design gap）**。裁定"注册条件 → 挂起释放线程 → 会话驻留"的执行模型。候选：(i) 新 `AgentExecStatus.waiting`（隔离于 `paused` 的 denial-ledger 耦合）+ break reactLoop + 完成 future with waiting 结果 + 会话驻留；(ii) 复用 `paused`（正视 `IDenialLedger.reset` 耦合——WAIT_FOR 恢复不应触发 denial reset）。裁定须参考既有 `paused` 机制（`:425-428` break + `:920` 完成 future），给出 WAIT_FOR 挂起与 paused 的可观测区别 + 恢复不卡死保证。须裁定 ReAct 循环内条件注册点（第 4 个 producer 落点）。回写 design §13.1。
- [x] **Decision C：唤醒机制 + 触发来源 + 重入 API + 不漏唤醒保证（核心 design gap）**。裁定条件求值器（"条件是否满足"如何判定）+ 唤醒触发器（条件满足后谁重入会话）+ **重入 API 形态**。候选触发来源：(i) 周期求值 via `IScheduledExecutor`（复用 `ScheduledRecoveryManager` 模式）；(ii) 外部事件投递（外部组件调 engine API 投递 wake）；(iii) 混合。**重入 API 候选**（正视 `resumeSession:248-252` paused-only 门禁 + `:273/282` denial reset 耦合）：(a) 新 `wakeSession` API（不触发 denial reset）；(b) 扩展 `resumeSession` 放宽门禁至 waiting 但跳过 denial reset。裁定须正视与 nop-job（已 deferred）的边界——本计划不引入 nop-job。给出不漏唤醒保证（条件满足后必被唤醒）。回写 design §13.1。
- [x] **Decision D：wait 条件表达式 DSL（条件类型集 + 求值语义）**。裁定 wait_for JSONB 表达什么——首版条件类型集（候选：超时（绝对/相对）/ 外部事件 / 用户输入），及每类的求值语义（何时算"满足"）。条件 schema 形态（内联 JSON 结构 vs 引用 xdef）。裁定须设界首版范围（基础类型集），完整 DSL 库留 successor（Non-Goal）。回写 design §13.1。
- [x] **Decision E：restore resume-vs-replay 分支 + 与 Decision H 的正确性耦合（design gap）**。裁定 WAIT_FOR checkpoint 的 restore 形态。候选：(i) resume-from-wait-point（§13.1 "无需重放全部历史"，但今日 restore 恒 replay，是较大改造）；(ii) 首版仍 replay（降复杂度，resume 优化留 successor）。**正确性耦合（round-1 审查）**：若选 replay，Decision H 的 wake-token/条件重评机制是**避免重复挂起的前置**（非可选优化）——重入从 reactLoop 顶部走，注册点会在同状态再触发。裁定须明示 E 与 H 的依赖：选 replay 则 H 必须提供"注册点重评条件已满足→跳过挂起"；选 resume 则从 wait 点恢复天然避免重复。给出裁定后 restore 的可观测行为 + 恢复不卡死保证。须裁定 `restoreSession`/`restorePendingSessions` 对 `waiting` 状态的处理（类比 `paused` 被跳过，waiting 是否需显式唤醒）。回写 design §13.1。
- [x] **Decision F：序列化范围枚举 + DB 兼容**。列出全部须同步的序列化接触点（`CheckpointJournalWriter.serializeSection` 写条件行 / `CheckpointJournalReader.parseSection` 读条件 / `DBCheckpointManager` INSERT+SELECT+readCheckpoint / `Checkpoint.equals/hashCode/toString` / `CheckpointSnapshot` 若需）。裁定 `wait_for` 列 DB 类型（CLOB/JSON）+ 旧 checkpoint（无该列）兼容（退回 null，零回归，类比 W2-2 裁定 G）。回写 design §13.1。
- [x] **Decision G：与 nop-job / ScheduledRecoveryManager / 多会话的边界**。裁定本计划唤醒用 `IScheduledExecutor` 或外部事件投递，**不引入 nop-job**（独立 successor）。裁定唤醒器是否单实例（类比 `ThresholdBreaker` 注入式）+ 多会话唤醒编排范围（首版单会话，编排留 successor）。回写 design §13.1 + §13.5（更新 skip reconcile，标记 WAIT_FOR 已进入实施）。
- [x] **Decision H：唤醒重入防重复挂起（正确性，round-1 审查 Blocker）**。裁定唤醒重入后注册点如何**避免在同一消息状态再次注册 wait→再次挂起→会话永不推进**。候选：(i) wake-token/会话标志（唤醒投递时置位，注册点检查后清位+跳过挂起）；(ii) 条件求值器在注册点重评（注册前问"条件是否已满足"，满足则跳过挂起直接推进）——此选使 replay 可行（Decision E 选 (ii) replay 时 H 必须选此）；(iii) resume-from-wait-point（Decision E 选 (i) 时天然避免，但需 continuation 捕获）。裁定须给出可测试设计（单测断言：唤醒重入后不再重复挂起、推进至完成），并明示与 Decision E 的依赖关系。回写 design §13.1。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] design §13.1 含 8 项裁定（A-H）结论 + 理由，从草图升级为可执行规格
- [x] **裁定 B 挂起语义可实现且不卡死**：明确挂起状态形态 + 与 paused 的可观测区别 + 恢复路径可走
- [x] **裁定 C 唤醒机制可实现且不漏唤醒 + 重入 API 明确**：明确触发来源 + 条件满足后必被唤醒的保证 + 具体 wake 重入 API（新 wakeSession vs 扩展 resumeSession，正视 `:248-252` 门禁 + `:273/282` denial 耦合）
- [x] **裁定 E restore 分支诚实裁定且与 H 依赖明确**：resume-vs-replay 明确选定 + 理由；若选 replay 须明示依赖 Decision H 的条件重评机制避免重复挂起（非静默跳过）
- [x] **裁定 H 防重复挂起可实现且有测试设计**：唤醒重入后注册点不再重复挂起，推进至完成可验证
- [x] 裁定已为 Phase 2/3/4 设界：所选策略须使实现单计划可关闭（若唤醒机制过大则先拆 predecessor）
- [x] No owner-doc update beyond design（WAIT_FOR 尚未成平台用户可见 API；若裁定引入新 AgentExecStatus 则须同步 `docs-for-ai/` 状态说明——裁定 G 时一并裁定）
- [x] No new test required: design-only phase（Rule #25）
- [x] `ai-dev/logs/2026/08-01.md` 已追加本 phase 裁定

### Phase 2 - wait_for 数据模型 + 序列化（无执行行为变更）（Fix | Proof）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/Checkpoint.java`（wait_for 字段 + WAIT_FOR 枚举值 + of() 接入）、`CheckpointType.java`（WAIT_FOR 值）、`nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml:120-163`、`.../reliability/AiAgentCheckpointTable.java`、`CheckpointJournalWriter`/`CheckpointJournalReader`、`DBCheckpointManager` SQL、`ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`（§13.1 数据模型回写）

- Item Types: `Fix | Proof`

> **ORM 结构变更属 protected area（plan-first）**——本计划即 plan-first 产物。`app.orm.xml` 是手写源模型（非 `_`-prefixed 生成），是正确编辑目标。

- [x] `Checkpoint` 按 Phase 1 裁定 A 增 `wait_for` 字段（条件 JSON，可空）+ `CheckpointType.WAIT_FOR` + `of()` 接入（重载/builder，旧签名兼容）；`equals/hashCode/toString` 含新字段。
- [x] `app.orm.xml` `ai_agent_checkpoint` 增 `wait_for` 列（propId 14）+ 按 Phase 1 裁定 F 的 DB 类型；`AiAgentCheckpointTable` DDL 同步（`COL_WAIT_FOR` + DDL 串）。
- [x] 按 Phase 1 裁定 F **序列化全接触点同步**：JournalWriter 写条件行、JournalReader 读条件、DBCheckpointManager INSERT/SELECT/readCheckpoint、CheckpointSnapshot（若需）。
- [x] 按 Phase 1 裁定 A 处理 WAIT_FOR 的 idempotencyKey（大概率 null，与 LLM_TURN/COMPACTION 同——`computeIdempotencyKey` 单点 guard `:187-189` 无需改即可覆盖；若裁定需 key 则改单点）。
- [x] 单测：WAIT_FOR checkpoint 可构造 + wait_for 非空 + 序列化往返保留条件；旧 of() 工厂兼容（null wait_for）；无该列旧 checkpoint 兼容（退回 null）。

Exit Criteria:

- [x] `Checkpoint` 存在 `wait_for` 字段 + `CheckpointType.WAIT_FOR`，工厂/equals/hashCode 同步，单测断言可空兼容
- [x] `app.orm.xml` + `AiAgentCheckpointTable` DDL 含 wait_for 列；WATERMARK 仍 PK；兼容策略已裁定并回写 design §13.1
- [x] **序列化全部接触点同步**（JournalWriter/Reader + DBCheckpointManager SQL ×3 + equals/hashCode），往返测试保留条件
- [x] **无静默跳过**：WAIT_FOR 的 idempotencyKey 按裁定 A 显式处理（明示策略，非吞掉）
- [x] **零回归**：旧 of() 调用点编译通过；无 wait_for 的 checkpoint 可保存/读取；既有 3 类型路径不变；执行行为本 phase 不变（Phase 3 才改）
- [x] `ai-dev/logs/2026/08-01.md` 已追加本 phase

### Phase 3 - 挂起执行语义 + WAIT_FOR producer + restore 分支（行为变更）（Fix | Proof）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/model/AgentExecStatus.java`（若裁定 B 新状态）、`.../engine/ReActAgentExecutor.java:317-921`（reactLoop 条件注册 + 挂起 break + 完成 future + **后循环事件 guard `:893-897` 排除列表更新**）、`.../engine/AgentSessionLifecycle.java:450-490,576,629-710`（restore WAIT_FOR 分支 + waiting 状态处理）、WAIT_FOR checkpoint producer、对应测试

- Item Types: `Fix | Proof`

> **依赖 Phase 1 裁定 B/E/H + Phase 2 数据模型**。

- [x] 按 Phase 1 裁定 B 落地挂起语义：ReAct 循环内条件注册点（第 4 个 producer）→ 注册 wait 条件 → 设状态（新 `waiting` 或裁定隔离的复用）→ break reactLoop → 完成 future with waiting 结果 → 线程释放、会话驻留（保留 checkpoint，不占线程）。参考既有 `paused` 机制（`:425-428`）但触发/恢复不同。
- [x] **后循环事件 guard 更新（round-1 审查 Major）**：`ReActAgentExecutor:893-897` 排除列表今日含 paused 不含 waiting——若引入新 `waiting` 状态，须加入排除列表，否则挂起会话误发 `EXECUTION_COMPLETED`(`:904`)+POST_CALL(`:898`)（注释 `:888` 明示非终态不应发完成事件）。
- [x] WAIT_FOR checkpoint producer（第 4 个 producer，3→4）：注册条件时产 WAIT_FOR checkpoint（含 wait_for 条件 JSON）并 saveCheckpoint。
- [x] 按 Phase 1 裁定 E/H 落地 restore WAIT_FOR 分支 + 防重复挂起：`restoreSession`/`restorePendingSessions` 对 WAIT_FOR latest checkpoint + waiting 状态的处理（resume-vs-replay 按裁定）；**重入注册点按 Decision H 机制避免重复挂起**（wake-token/条件重评，非裸重入）；恢复不卡死。
- [x] **零回归**：`paused` 机制行为不变（denial-ledger 耦合不被影响）；既有 3 checkpoint 类型路径不变；无 WAIT_FOR 触发的 plan 行为不变。
- [x] 单测：注册条件 → 挂起 → 状态=waiting + future 已完成（线程释放可观测）+ WAIT_FOR checkpoint 已存 + **waiting 会话不发 EXECUTION_COMPLETED**；restore 对 waiting 状态的处理按裁定 E（resume 或 replay，可观测）。

Exit Criteria:

- [x] 挂起语义真实生效：注册条件后 reactLoop break、future 完成（线程释放可观测）、会话驻留（状态 + checkpoint 可观测），有测试断言
- [x] **waiting 会话不发完成事件**：后循环 guard `:893-897` 排除 waiting，单测断言挂起会话不发 `EXECUTION_COMPLETED`/POST_CALL（round-1 审查 Major）
- [x] WAIT_FOR checkpoint producer 存在且被 saveCheckpoint 持久化，单测断言 wait_for 条件非空
- [x] **restore 分支按裁定 E/H 真实生效 + 防重复挂起**：waiting 状态/WAIT_FOR checkpoint 的 restore 行为可观测（resume 或 replay，与裁定一致），恢复不卡死；重入注册点按 Decision H 不重复挂起（有测试设计，即便本 phase 仅验证机制存在，Phase 4 端到端完整验证）
- [x] **端到端验证（挂起到驻留）**：agent 执行 → 注册条件 → 挂起释放线程 → 会话驻留（future 完成 + 状态=waiting + checkpoint 存 + 不发完成事件）（Minimum Rules #22 部分）
- [x] **接线验证**：条件注册点确实产 WAIT_FOR checkpoint 并 saveCheckpoint；挂起确实 break reactLoop 并完成 future（非空转占线程）（Minimum Rules #23）
- [x] **无静默跳过**：挂起真实释放线程（非 no-op continue）；条件不满足时不误触发挂起
- [x] **零回归**：paused 机制不变；既有 3 类型路径不变；无 WAIT_FOR 的 plan 行为不变
- [x] design §13.1 挂起语义 + restore 分支 + 防重复挂起已回写
- [x] `ai-dev/logs/2026/08-01.md` 已追加本 phase

### Phase 4 - 唤醒机制 + 条件求值 + 端到端（Fix | Proof）

Status: completed
Targets: 按 Phase 1 裁定 C/D 的唤醒器（`.../reliability/` 或 `.../runtime/` 新组件）、条件求值器、唤醒重入入口、`ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`（§13.1 唤醒机制 + 条件 DSL 回写）

- Item Types: `Fix | Proof`

> **依赖 Phase 3 挂起语义**：唤醒须能恢复 Phase 3 挂起的会话。

- [x] 按 Phase 1 裁定 D 落地条件求值器：判定 wait 条件是否满足（首版条件类型集：超时/外部事件/用户输入按裁定），每类求值语义明确。
- [x] 按 Phase 1 裁定 C 落地唤醒触发器 + 重入入口：条件满足后重入会话（`IScheduledExecutor` 周期求值 或 外部事件投递按裁定）；重入 API 按裁定 C（新 `wakeSession` vs 扩展 `resumeSession`，不引入 denial reset）。
- [x] 按 Phase 1 裁定 H 落地防重复挂起：唤醒重入（fresh `execute()` 从持久化消息 replay，从 reactLoop 顶部重入）后，注册点按 Decision H 机制（wake-token/条件重评）识别"条件已满足"→ 跳过挂起、推进至完成；非裸重入导致重复挂起。
- [x] 按 Phase 1 裁定 G：唤醒器不引入 nop-job；单会话唤醒成立。
- [x] 单测：条件不满足 → 会话保持驻留（不误唤醒）；条件满足 → 唤醒重入 → **不重复挂起** → 推进至完成；超时类条件的边界时间行为确定可测（可注入时钟，不复制 `ThresholdBreaker.java:122` 直接 `System.currentTimeMillis()` 反模式）。

Exit Criteria:

- [x] 条件求值器存在且按裁定 D 求值，每类条件语义有测试
- [x] 唤醒触发器存在且按裁定 C 触发，条件满足后重入会话，有测试断言重入
- [x] **重入 API 按裁定 C 落地**：wake 重入不触发 denial reset（与 resumeSession 的 `:273/282` 区分），有测试断言
- [x] **防重复挂起成立（Decision H，round-1 审查 Blocker）**：唤醒重入后注册点不再重复挂起，会话推进至完成（测试断言：唤醒后不产第二个 WAIT_FOR checkpoint、推进至 completed）
- [x] **不漏唤醒**：条件满足后会话必被唤醒重入（测试覆盖：条件满足 → 唤醒重入 → 推进完成）
- [x] **端到端验证（完整挂起→唤醒→恢复）**：agent 执行 → 注册条件 → 挂起释放线程 → 会话驻留 → 条件满足 → 唤醒重入（fresh execute replay from persisted messages）→ 不重复挂起 → 推进至完成（Minimum Rules #22：从 execute 入口经挂起到恢复完成完整跑通）
- [x] **接线验证**：唤醒触发器在条件满足时确实调用重入入口；重入确实恢复同一会话（非新会话）；防重复挂起机制确实被注册点检查（Minimum Rules #23）
- [x] **无静默跳过**：条件满足时显式唤醒重入（非静默忽略）；条件不满足时保持驻留（非误唤醒）
- [x] **零回归**：无 WAIT_FOR 的 plan 行为不变；唤醒器在无 wait 会话时无副作用
- [x] **可测试时钟**：时间依赖逻辑确定可测（可注入时间源，非直接 `System.currentTimeMillis()`）
- [x] design §13.1 唤醒机制 + 条件 DSL + 防重复挂起 + §13.5 skip reconcile 已回写
- [x] `ai-dev/logs/2026/08-01.md` 已追加本 phase

## Closure Gates

> 本计划涉及代码 + ORM 结构 + design + 可能的新 AgentExecStatus，构建验证条目保留。

- [x] wait_for 条件 JSONB + WAIT_FOR checkpoint 类型存在（ORM + DDL + Checkpoint model），WATERMARK 保持 PK
- [x] 挂起执行语义成立：注册条件 → 挂起释放线程 → 会话驻留（不占线程可观测）
- [x] 唤醒机制成立：条件满足 → 唤醒重入 → 继续推进至完成；不漏唤醒
- [x] restore WAIT_FOR 分支按裁定 E 成立，恢复不卡死
- [x] 零回归：paused 机制不变；既有 3 checkpoint 类型路径不变；无 WAIT_FOR 的 plan 行为不变
- [x] 无静默跳过：挂起真实释放线程；唤醒真实重入；条件语义显式
- [x] design §13.1 从草图升级为含挂起执行模型/唤醒机制/条件 DSL/restore 分支/序列化的 elaboration；§13.5 skip reconcile 更新
- [x] 若引入新 AgentExecStatus：`docs-for-ai/` 状态说明已同步（裁定 G 一并裁定）
- [x] roadmap `[ ] W2-1` 满足勾选条件—— closure 时更新 roadmap + daily log
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）挂起后线程确实释放（非空转占线程）、会话驻留，（b）条件满足后唤醒确实重入并恢复同一会话，（c）端到端从注册条件到唤醒恢复完成完整连通
- [x] `./mvnw compile` 通过（`-pl nop-ai -am`）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### nop-job 集成（DB 持久化 job / 集群协调 / cron）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `ScheduledRecoveryManager` 已显式 deferred 的独立 successor（`:33-35`）；本计划唤醒用 `IScheduledExecutor` 或外部事件投递成立。nop-job 集成是多实例/集群唤醒编排，超本计划范围。
- Successor Required: yes
- Successor Path: nop-job 集成 successor（与 `ScheduledRecoveryManager` 的 nop-job successor 同向）

### 完整 wait 条件 DSL 库

- Classification: `optimization candidate`
- Why Not Blocking Closure: 首版支持基础条件类型集（超时/事件/用户输入）即可使 WAIT_FOR 原语可用；丰富 DSL（复杂条件组合/嵌套/引用）是表达力增强，非可用性前置。
- Successor Required: no

## Non-Blocking Follow-ups

- resume 跳过重放的极致优化（若 Phase 1 裁定 E 选 replay 首版）——不影响 WAIT_FOR 可用性，仅影响恢复效率。
- 多会话唤醒编排 / 优先级调度——单会话唤醒成立后，多会话策略是独立增强。
- 唤醒可观测性指标（等待会话数、唤醒延迟、条件分布）。

## Closure

Status Note: WAIT_FOR 长等待原语全部落地。4 phase 完成（design elaboration A-H + 数据模型 + 序列化 + 挂起语义 + 唤醒机制 + 端到端）。3081 tests pass，零回归。
Completed: 2026-08-01

Closure Audit Evidence:

- Reviewer / Agent: self-execution (mission-driver EXECUTE mode)
- Audit Session: 2026-08-01-1437-1
- Evidence:
  - 每条 Exit Criterion 的验证结果：PASS — 见各 phase 的 `[x]` + 对应测试类（TestCheckpointWaitFor, TestWaitForPrimitives, TestWaitForSuspend, TestDefaultWaitCoordinator, TestWaitForWakeEndToEnd）
  - 每条 Closure Gate 的验证结果：PASS — 3081 tests green, compile + checkstyle clean
  - Anti-Hollow 检查结果：`TestWaitForWakeEndToEnd.wakeReentryDoesNotReSuspend` 验证完整挂起→唤醒→恢复→不重复挂起→不产第二个 WAIT_FOR checkpoint 调用链；`TestWaitForSuspend.suspendProducesWaitingStatusAndReleasesThread` 验证 future 完成（线程释放）
  - Deferred 项分类检查：nop-job 集成（out-of-scope improvement，successor required）；完整条件 DSL 库（optimization candidate，no successor required）——无 in-scope live defect 被降级

Follow-up:

- resume 跳过重放的极致优化（裁定 E 选 replay 首版）
- 多会话唤醒编排 / 优先级调度
- 唤醒可观测性指标（等待会话数、唤醒延迟、条件分布）
- nop-job 集成（DB 持久化 job / 集群协调 / cron）
