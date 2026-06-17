# 197 nop-ai-agent 同 Session 并发执行竞态修复（AUDIT-14-01）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: AUDIT-14-01
> Last Reviewed: 2026-06-15
> Source: carry-over from `ai-dev/plans/195-nop-ai-agent-atomic-file-write.md`（Non-Goals 第 1 条：`AUDIT-14-01（同 session 并发执行竞态）——独立 work item，见 roadmap §5b`）；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §5b（`AUDIT-14-01 | P1 | ❌ 未修复 | 同 session 并发执行竞态`，line 273）；`ai-dev/audits/2026-06-15-1146-deep-audit-nop-ai-agent/14-async-transaction.md` [维度14-1]（P1，put/remove 不去重）+ [维度14-2]（P1，cancel 丢失窗口），两处独立复核均维持 P1
> Related: `193-nop-ai-agent-secure-by-default.md`、`194-nop-ai-agent-audit-logger-default.md`、`196-nop-ai-agent-exception-base-class.md`（均在 Non-Goals 将 AUDIT-14-01 切出为独立 work item）；`180-nop-ai-agent-sticky-pause-recovery.md`（`resumeSession` 的来源）；`183-nop-ai-agent-crash-restart-session-restore.md`（`restoreSession` 的来源）

## Purpose

把 `DefaultAgentEngine` 的同 session 并发执行从"竞态互覆"收敛为"fail-fast + cancel-safe"。本计划只负责这一件事：让 `runningExecutions` map 真正兑现其在 `nop-ai-agent-reliability.md:31` 中宣称的"单进程内并发保护"契约——第二次并发执行快速失败、finally 不误删他人的 CancelHandle、cancel 在异步入队窗口内不丢失。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-15）：

- **`runningExecutions` 是 `ConcurrentHashMap<String, CancelHandle>`**（`DefaultAgentEngine.java:120`）。它是单进程内 session 执行并发管理的唯一机制。
- **三个执行入口点全部使用无条件 `put` + 按 key `remove`**：
  - `doExecute`（`:772-784`）：`supplyAsync` lambda 内 `runningExecutions.put(sessionId, handle)`（`:776`，无 `putIfAbsent`），`finally { runningExecutions.remove(sessionId); }`（`:782`，无值比较）。
  - `resumeSession`（`:967-991`）：与 `doExecute` **完全相同**的模式——`put`（`:969`）+ 按 key `remove`（`:975`）。
  - `restoreSession`（`:1081-1091`）：与 `doExecute` **完全相同**的模式——`put`（`:1083`）+ 按 key `remove`（`:1089`）。
- **`doExecute` 与 `resumeSession` 无 `containsKey` 前置检查**。`restoreSession` 在 `supplyAsync` 之前有 `runningExecutions.containsKey(sessionId)` 检查（`:1002-1007`），但该检查是 TOCTOU 竞态——`containsKey` 通过后、`put` 之前另一线程可能已注册。
- **[维度14-1] put/remove 不去重（P1，已确认）**：两个并发 `execute()` 调用同一 sessionId 时，第二次 `put`（`:776`）覆盖第一次的 handle。第一个执行结束时 `finally` 中的 `remove(sessionId)`（`:782`）把第二个仍在运行的 handle 也清除。此后 `cancelSession` 永远找不到该 session（`runningExecutions.get(sessionId)` 返回 null），CancelHandle 语义被破坏。
- **[维度14-2] cancel 丢失窗口（P1，已确认）**：`execute(request)` 立即返回 `CompletableFuture`（任务已入队 ForkJoinPool，`supplyAsync` 尚未运行）。此窗口内 `runningExecutions` 为空。若外部调用 `cancelSession`，走 else 分支将 `session.setStatus(AgentExecStatus.cancelled)`（`:648`）。随后 `supplyAsync` 启动，`:773` `session.setStatus(AgentExecStatus.running)` 覆盖 cancelled 状态，cancel 信息丢失，executor 正常执行。`ctx.setCancelRequested(true)` 仅在 handle != null 分支调用（`:632`），故 `CANCEL_REQUESTED` 标志也未设置。
- **LLM 可达的并发向量已确证**：`CallAgentExecutor.executeSubAgent`（`tool/CallAgentExecutor.java:132-135`）在 sessionId 非空时直接用该 sessionId 调用 `engine.execute(execRequest)`（`:208`）。sessionId 由 LLM 通过 call-agent 工具参数控制。ReAct 循环并发调度多个 call-agent 工具时（`ReActAgentExecutor` 的 `allOf` fan-out），若两个工具的 sessionId 相同，即触发本竞态。
- **`AgentSession.status` 非 volatile**（`session/AgentSession.java:21`，`private AgentExecStatus status;`）——这是 [维度14-6]（P2）的发现，与 cancel 窗口修复的可见性相关，本计划裁定是否纳入 scope。
- **设计文档宣称了并发保护**：`nop-ai-agent-reliability.md:31` 明确写"单进程内由 `runningExecutions` map 提供并发保护"——但当前实现未兑现此契约（contract drift：文档宣称保护，代码竞态）。
- **roadmap §5b**（line 273）：`AUDIT-14-01 | P1 | ❌ 未修复 | 同 session 并发执行竞态`。本计划就是关闭这一行。
- **`NopAiAgentException` 已是 `NopException` 子类**（plan 196 ✅ 已落地）——本计划新增的 fail-fast 异常可直接使用 `NopAiAgentException`，并可选附带 `ErrorCode`。

## Goals

- 对同一 sessionId 的第二次并发执行（`execute` / `resumeSession` / `restoreSession` 任一入口），快速失败抛出 `NopAiAgentException`（fail-fast，Minimum Rules #24），而非静默覆盖。
- 某次执行的 `finally` 块只移除自己注册的 CancelHandle，绝不误删其他执行注册的 handle（值比较 remove）。
- `cancelSession` 在异步入队窗口（`execute()` 返回后、`supplyAsync` lambda 实际运行前）内发起的 cancel，被后续执行线程感知并生效，而非被 `setStatus(running)` 静默覆盖。
- `nop-ai-agent-reliability.md` 的并发保护契约描述与 live baseline 一致（收敛 contract drift）。
- roadmap §5b `AUDIT-14-01` 行从 ❌ → ✅ 并标注本 plan。

## Non-Goals

- **[维度14-3] allOf().join() 单工具异常终止整轮**（P1，`ReActAgentExecutor.java:979-991`）——不同文件、不同关注点（工具 fan-out 容错，非 session 级并发），独立 work item，见 roadmap §5b / 审计 14。
- **[维度14-4] checkpoint 写入与 sessionStore.save 非原子**（P2）——事务原子性，非并发执行竞态，独立 work item。
- **[维度14-6] cancelSession 的 `thread.interrupt()` 对 HTTP 阻塞调用无立即效果**（P2）——cancel *生效速度*（需等 LLM 调用返回），非 cancel *是否丢失*。本计划修复 cancel 不丢失；cancel 多快生效是独立 hardening。但 `AgentSession.status` 的 volatile 可见性是否纳入 scope 由 Phase 1 裁定（见 Phase 1 裁定项）。
- **[维度14-7]/[14-8]/[14-9]** `DBMessageService` 的订阅/超时/关闭竞态（P2/P3）——不同子系统（消息服务，非执行引擎），独立 work item。
- **[维度14-10] supplyAsync 用 ForkJoinPool.commonPool**（P3）——executor 注入是性能/资源治理，非正确性缺陷。
- **跨进程接管锁**（L4-8 Actor Runtime）——roadmap P3，依赖未就绪。
- **并发执行排队/复用语义**（第二次执行排队等待第一次完成而非 fail-fast）——是产品策略变更，超出"修复竞态"范围。本计划采用 fail-fast（与 audit 建议一致），排队语义是 successor。

## Scope

### In Scope

- `engine/DefaultAgentEngine.java`：`doExecute`、`resumeSession`、`restoreSession` 三个 `supplyAsync` lambda 的 `runningExecutions` 注册/注销逻辑收敛为 `putIfAbsent` + 值比较 `remove` + fail-fast。
- `engine/DefaultAgentEngine.java`：`cancelSession` 与 `supplyAsync` 之间的 cancel 丢失窗口修复（具体策略由 Phase 1 裁定）。
- `engine/DefaultAgentEngine.java`：`restoreSession` 既有 `containsKey` 检查与新 `putIfAbsent` 一致性（移除冗余或保留为 defense-in-depth，由 Phase 1 裁定）。
- Phase 1 裁定 `AgentSession.status` 的 volatile 可见性是否为 cancel-window 修复的必要前置（若是则纳入 scope，若 cancel 信号经由 `runningExecutions`/`CancelHandle` 传递则不纳入）。
- 新增 focused 测试：并发执行 fail-fast、finally 不误删、cancel 窗口生效、restoreSession 与新 guard 一致。
- `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`：并发保护契约收敛（记录 fail-fast 策略 + cancel-window 修复决策）。
- roadmap §5b `AUDIT-14-01` 状态同步。

### Out Of Scope

- [14-3] 工具 fan-out 容错、[14-4] 事务原子性、[14-7]/[14-8]/[14-9] 消息服务竞态、[14-10] executor 注入、跨进程接管锁、并发排队语义。

## Execution Plan

### Phase 1 - 并发保护策略裁定与设计落档

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`

- Item Types: `Decision`

- [x] 裁定并落档 **去重注册策略**：三个执行入口点的 `runningExecutions.put(sessionId, handle)` 改为 `putIfAbsent`——当返回值非 null（即 session 已在执行）时，快速失败抛出 `NopAiAgentException`（fail-fast，Minimum Rules #24），异常消息需点明 "session already executing" + sessionId。裁定 fail-fast 是否附带 `ErrorCode`（plan 196 已使 `NopAiAgentException` 支持 `ErrorCode` 构造器——裁定使用字符串消息还是定义模块级 `ErrorCode`）。
- [x] 裁定并落档 **值比较注销策略**：三个入口点的 `finally { runningExecutions.remove(sessionId); }` 改为值比较 `remove(sessionId, handle)`（`ConcurrentHashMap.remove(key, value)` 语义：仅当当前映射值 == handle 时才移除）。确保第一次执行的 finally 不误删第二次（或并发执行者）注册的 handle。裁定 handle 的 `equals` 语义——`CancelHandle` 是 `private static final class` 无 `equals` 覆写（`:709-717`），`ConcurrentHashMap.remove(key, value)` 使用 `==` 比较（`CancelHandle` 引用相等），确认此语义正确（同一 handle 实例的引用相等，不同执行创建不同 handle 实例）。
- [x] 裁定并落档 **cancel 丢失窗口修复策略**：当前 `cancelSession` 的 else 分支（handle == null）设置 `session.setStatus(cancelled)`，但随后 `supplyAsync` lambda `:773` 覆盖为 `running`。裁定修复方案（预期行为：cancel 在入队窗口内发起时，后续执行线程必须感知并生效）：
  - **选项 A（预注册 handle）**：在 `supplyAsync` **之外**（同步阶段）预注册 CancelHandle 到 `runningExecutions`，使 cancelSession 在入队后即可见 handle。裁定 handle 的 `thread` 字段处理（`CancelHandle.thread` 在同步阶段是调用线程，需在 lambda 内更新为执行线程——裁定 `thread` 字段改为 `volatile` 或 `AtomicReference<Thread>`，或拆分为预注册不含 thread 的 handle + lambda 内补充）。
  - **选项 B（lambda 入口状态检查）**：在 `supplyAsync` lambda 入口检查 session 是否已被 cancel（`if (session.getStatus() == cancelled) → abort`），裁定 abort 语义（立即返回 cancelled 结果 vs 抛异常）。裁定此方案对 `AgentSession.status` 可见性的依赖——若依赖 status 可见性，则 `status` 字段的 volatile 修饰（[14-6]）**纳入本计划 scope**。
  - **选项 C（组合）**：预注册 handle + lambda 入口 defense-in-depth 检查。
  - 裁定时必须明确：所选方案的 cancel 信号传递路径（经由 `runningExecutions`/`CancelHandle` 的 ConcurrentHashMap happens-before，还是经由 `AgentSession.status`），以及是否因此将 [14-6] 的 volatile 修饰纳入 scope。
- [x] 裁定并落档 **`restoreSession` 既有 `containsKey` 检查的去留**：当前 `restoreSession` 在 `supplyAsync` 之前有 `runningExecutions.containsKey(sessionId)` 检查（`:1002-1007`）。改为 `putIfAbsent` 后，该检查变为冗余（putIfAbsent 本身是原子的）。裁定是（a）移除冗余检查（putIfAbsent 是唯一 guard），还是（b）保留为 defense-in-depth（提前 fail-fast，给出更精确的错误消息 "session is still in active execution map" vs putIfAbsent 的 "session already executing"）。裁定两个入口点（restore vs execute/resume）的 fail-fast 行为是否应一致。
- [x] 裁定并落档 **`putIfAbsent` 注册时机**：裁定 `putIfAbsent` 发生在 `supplyAsync` **之内**还是**之外**。若在之内（当前 put 的位置），cancel 窗口修复需依赖选项 B/C（lambda 内检查）。若移到之外（同步阶段），则选项 A 的预注册自然解决 cancel 窗口，但需裁定 CancelHandle.thread 的延迟绑定（thread 在 supplyAsync 之外时尚未创建）。此裁定与 cancel 丢失窗口策略（上一条）耦合，需一致。
- [x] 裁定并落档 **向后兼容处理**：`sendMessage`（`:720-728`）调用 `doExecute` 并立即返回 ack（fire-and-forget）——改为 fail-fast 后，同一 session 的快速重复 sendMessage 会抛异常。裁定 `sendMessage` 是否捕获该异常并返回 error ack（而非让异常传播到调用方），还是保持 fail-fast 传播。裁定既有测试中是否有依赖"同 session 可并发执行"的行为（预期没有，因这是缺陷而非 feature）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop-ai-agent-reliability.md` 在 §1.1 恢复模型（或新增"并发执行保护"子节）明确记录：选了什么去重策略（putIfAbsent + fail-fast）、什么注销策略（值比较 remove）、什么 cancel-window 修复方案（A/B/C 及理由）、拒绝了哪些替代方案（如"排队等待而非 fail-fast"为何不采用）、CancelHandle.thread 的处理（若选项 A）、`AgentSession.status` volatile 是否纳入及理由、`restoreSession.containsKey` 去留裁定。
- [x] 设计文档不含类签名/伪代码（只记录决策与契约，遵循 Minimum Rules #14）。
- [x] No owner-doc update required for `docs-for-ai/`（本模块无独立 owner doc 章节；如裁定需要，在此条注明具体文件）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 实现并发 guard + cancel-window 修复

Status: completed
Targets: `engine/DefaultAgentEngine.java`（`doExecute`、`resumeSession`、`restoreSession`、`cancelSession`、`CancelHandle`）；若 Phase 1 裁定纳入，则 `session/AgentSession.java`

- Item Types: `Fix`

- [x] `doExecute`（`:772-784`）：按 Phase 1 裁定实现——`putIfAbsent` + fail-fast（返回值非 null 时抛 `NopAiAgentException`）+ `finally { runningExecutions.remove(sessionId, handle); }`（值比较）+ cancel-window 修复（选项 A/B/C 之一）。三个变更在同一改动内完成（去重 + 值比较 remove + cancel 窗口），不分步。
- [x] `resumeSession`（`:967-991`）：与 `doExecute` 完全对称的改动——`putIfAbsent` + fail-fast + 值比较 `remove` + cancel-window 修复。
- [x] `restoreSession`（`:1081-1091` + `:1002-1007`）：与 `doExecute` 对称的改动——`putIfAbsent` + fail-fast + 值比较 `remove` + cancel-window 修复。按 Phase 1 裁定处理既有 `containsKey` 检查（移除冗余或保留为 defense-in-depth）。
- [x] 若 Phase 1 裁定选项 A（预注册 handle）：实现 `CancelHandle.thread` 的延迟绑定（按裁定——volatile 字段 / AtomicReference / 拆分 handle），确保 `cancelSession(forced=true)` 的 `handle.thread.interrupt()`（`:640`）在 lambda 启动后指向正确的执行线程，在 lambda 启动前指向调用线程或 null-safe（interrupt 调用线程是无害的 no-op 或需 null 检查）。
- [x] 若 Phase 1 裁定纳入 `AgentSession.status` volatile：将 `session/AgentSession.java:21` 的 `private AgentExecStatus status;` 改为 `private volatile AgentExecStatus status;`。
- [x] 若 Phase 1 裁定 `sendMessage` 需捕获 fail-fast 异常：实现 error-ack 返回（而非异常传播）。

Exit Criteria:

> 注：本 Phase 的端到端验证与并发断言 Exit Criteria 由 Phase 3 的 focused 测试落地后一并满足。

- [x] grep 确认 `DefaultAgentEngine.java` 中不再有无条件的 `runningExecutions.put(sessionId, handle)`（全部改为 `putIfAbsent` 或等价原子注册）。
- [x] grep 确认 `DefaultAgentEngine.java` 中不再有按 key 的 `runningExecutions.remove(sessionId)`（全部改为值比较 `remove(sessionId, handle)`）。
- [x] 三个执行入口点在 session 已有活跃 handle 时抛 `NopAiAgentException`（fail-fast，Minimum Rules #24），不静默覆盖。
- [x] **无静默跳过（Minimum Rules #24）**：fail-fast 路径抛异常，不返回 null/默认值/静默 continue；cancel-window 修复路径不吞掉 cancel 信号。
- [x] 若该 Phase 改变 live baseline：`ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` 已在 Phase 1 更新；本 Phase 不新增 owner-doc 变更。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - Focused 测试 + roadmap 同步

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/**`、`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`

- Item Types: `Proof`、`Follow-up`

- [x] 新增 focused 测试（Minimum Rules #25），覆盖以下行为点：
  - (1) **并发执行 fail-fast**：对同一 sessionId 发起两次 `execute()`（或 `execute` + `resumeSession`），断言第二次抛 `NopAiAgentException`（消息含 "already executing" 或等价），且第一次正常完成。
  - (2) **finally 不误删他人 handle（值比较 remove 核心保证）**：构造场景使第一次执行在第二次注册后结束（或 mock 序列），断言第一次的 finally 未移除第二次的 handle——`cancelSession` 仍能找到第二次的 handle（或等价地，第二次执行期间 handle 在 map 中可见）。
  - (3) **cancel 窗口生效**：`execute()` 返回 future 后、在 `supplyAsync` lambda 实际运行前（用 latch/屏障控制时序），调用 `cancelSession`，断言后续执行线程感知 cancel（执行以 cancelled 状态结束，或 `ctx.isCancelRequested()` 为 true），而非被 `setStatus(running)` 覆盖后正常执行。
  - (4) **restoreSession 与新 guard 一致**：`restoreSession` 对已在 `runningExecutions` 的 session fail-fast（行为与 execute/resume 一致，错误消息按 Phase 1 裁定）。
  - (5) **正常路径无回归**：单线程顺序执行/恢复/restore 的正常流程不受影响（`putIfAbsent` 在无竞争时成功，`remove(sessionId, handle)` 在 handle 匹配时移除）。
- [x] 审计既有测试受影响面：grep 测试中是否有依赖"同 session 可并发 execute"的行为（预期没有），确认既有测试无回归。
- [x] roadmap §5b：将 `AUDIT-14-01` 行 ❌ → ✅，落地 plan 标注 197。

Exit Criteria:

- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全绿（既有测试无回归，新增测试已加入并覆盖上述行为点）。
- [x] 新增测试**显式列出**所验证的新行为（concurrent-execute-fail-fast、finally-no-misremove、cancel-window-honored、restore-guard-consistent、no-regression-normal-path），不是"原有测试通过"。
- [x] **端到端验证（Minimum Rules #22）**：至少一条测试从 `engine.execute(request)` 入口、经 `doExecute` → `supplyAsync` → `runningExecutions` 注册、到并发第二次 `execute` 被 fail-fast 拒绝，完整路径走通——验证 guard 在运行时实际生效（不只是 map 类型正确）。
- [x] **接线验证（Minimum Rules #23）**：测试断言 `cancelSession` 在 cancel 窗口内发起后，执行线程的 `ctx.isCancelRequested()` 确实为 true（cancel 信号从 cancelSession → runningExecutions/CancelHandle → 执行线程 ctx 连通）。
- [x] **无静默跳过（Minimum Rules #24）**：fail-fast 路径的测试断言异常被抛出（非静默返回）；cancel 窗口测试断言 cancel 被感知（非静默覆盖）。
- [x] roadmap §5b `AUDIT-14-01` 行已更新为 ✅ 并指向本 plan。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：本 section 与每个 Phase 的 Exit Criteria 全部 `[x]` 后，方可将 `Plan Status` 改为 `completed`。关闭流程见 plan guide 的 `When Closing The Plan` 与 `Closure Audit Rule`。

- [x] `DefaultAgentEngine` 三个执行入口点全部使用 `putIfAbsent`（或等价原子注册）+ fail-fast，不再有无条件 `put`（live code 验证，非仅类型存在）。
- [x] 三个入口点的 `finally` 全部使用值比较 `remove(sessionId, handle)`，不再按 key `remove`。
- [x] cancel 丢失窗口已修复：cancel 在入队窗口内发起时被后续执行线程感知（由测试证明）。
- [x] finally 不误删他人 handle：由测试证明（值比较 remove 核心保证）。
- [x] roadmap §5b `AUDIT-14-01` 同步为 ✅。
- [x] 设计文档记录了并发保护策略（fail-fast + cancel-window 修复决策，最终状态，无 Proposed/Current 对比）。
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect（[14-3]/[14-4]/[14-6 非 volatile 部分]/[14-7~14-10] 等已显式移入 Non-Goals，属裁定移出而非隐藏）。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：closure audit 验证并发 guard 在运行时确被触发（端到端测试：第二次 execute 真实 fail-fast，非仅字段类型正确），无空方法体/静默 no-op。
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过。
- [x] checkstyle / 代码规范检查通过。
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/197-nop-ai-agent-session-concurrency-guard.md --strict` 退出码为 0。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high` — 在本计划触碰文件中无 NEW high/critical findings（区分 pre-existing UOE stubs 与本计划引入的新增）。

## Deferred But Adjudicated

（暂无；本计划范围聚焦于 AUDIT-14-01 = [14-1] + [14-2]，其余审计发现已在 Non-Goals 显式切出。Phase 1 裁定后若有 residual 再补充。）

## Non-Blocking Follow-ups

- **[维度14-6] cancel 生效速度**（`thread.interrupt()` 对 HTTP 阻塞调用无立即效果）：本计划修复 cancel 不丢失，但 cancel 多快生效（需等 LLM 调用返回）是独立 hardening。（Classification: optimization candidate）
- **[维度14-6] `AgentSession` 其他可变字段的 volatile**：若 Phase 1 裁定只对 `status` 加 volatile（cancel-window 修复必要），其他可变字段（counters/timestamps）的可见性是独立 hardening。（Classification: watch-only residual）
- **并发执行排队语义**：第二次 execute 排队等待第一次完成而非 fail-fast，是产品策略变更（如 webhook 重试场景的幂等处理）。（Classification: out-of-scope improvement）

## Closure

Status Note: AUDIT-14-01（同 session 并发执行竞态）已修复。三个执行入口点（doExecute/resumeSession/restoreSession）的 runningExecutions 注册从无条件 put 收敛为 putIfAbsent + fail-fast；注销从按 key remove 收敛为值比较 remove(sessionId, handle)；cancel 丢失窗口通过同步阶段预注册 handle 修复（cancel 信号经由 ctx.cancelRequested volatile 传递）；restoreSession 冗余 containsKey 检查移除。1547 tests 全绿（1542 既有 + 5 新增），零回归。
Completed: 2026-06-15

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit subagent（fresh session，task_id ses_134660222ffeFIb0BMn1Im36dG）
- Audit Session: ses_134660222ffeFIb0BMn1Im36dG
- Evidence:
  - Phase 1 Exit Criteria — PASS：`nop-ai-agent-reliability.md` §1.3（line 88）记录了 7 条裁定（putIfAbsent + fail-fast / 值比较 remove / 选项 A 预注册 / volatile thread 延迟绑定 / containsKey 移除 / status volatile 不纳入 / sendMessage 异常传播），无类签名/伪代码
  - Phase 2 Exit Criteria — PASS：live code 验证（grep 确认 0 个无条件 `put(` + 0 个 key-only `remove(sessionId)` + 0 个 `containsKey`）；doExecute L797/819/845、resumeSession L1014/1029/1048、restoreSession L1140/1155/1172 全部 putIfAbsent + 值比较 remove；CancelHandle.thread L728 volatile；cancelSession L644-647 null-check
  - Phase 3 Exit Criteria — PASS：TestDefaultAgentEngineConcurrencyGuard 5 tests（concurrentExecuteFailFast L187 / finallyDoesNotMisremoveHandle L231 / cancelWindowHonored L273 / restoreSessionGuardConsistentWithExecute L316 / noRegressionNormalPath L368）全绿；端到端验证（concurrentExecuteFailFast 从 engine.execute 入口经 doExecute → putIfAbsent → 第二次 execute 被 fail-fast 拒绝）；接线验证（cancelWindowHonored 断言 cancel 被 ctx 感知，result=cancelled）；无静默跳过（fail-fast 抛 NopAiAgentException，cancel 被感知非静默覆盖）
  - Closure Gates — PASS（逐条）：
    1. putIfAbsent + fail-fast：L797/L1014/L1140 live code 验证 PASS
    2. 值比较 remove：L819/L1029/L1155 live code 验证 PASS
    3. cancel 丢失窗口修复：cancelWindowHonored 测试证明 PASS
    4. finally 不误删：finallyDoesNotMisremoveHandle 测试证明 PASS
    5. roadmap §5b：L273 ✅ 已修复 PASS
    6. 设计文档：§1.3 最终状态无 Proposed/Current 对比 PASS
    7. 无 in-scope defect 降级：[14-3]/[14-4]/[14-6 volatile]/[14-7~14-10] 显式移入 Non-Goals PASS
    8. 独立 closure-audit：本条记录即证据 PASS
    9. Anti-Hollow：concurrentExecuteFailFast 端到端测试第二次 execute 真实 fail-fast，非字段类型检查 PASS
    10. `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`：1547 tests, 0 failures, 0 errors PASS
    11. checkstyle：BUILD SUCCESS PASS
    12. `check-plan-checklist.mjs --strict` 退出码 0 PASS
    13. `scan-hollow-implementations.mjs --severity high`：本计划触碰文件无 NEW findings（DefaultAgentEngine.java 唯一 finding 是 pre-existing mode=plan UOE at L1451，未触碰）PASS
  - Deferred 项分类检查：[14-6] cancel 生效速度（optimization candidate）、[14-6] status volatile（watch-only residual）、并发排队语义（out-of-scope improvement）——均属 non-blocking follow-up，无 in-scope defect 被降级

Follow-up:

- [维度14-6] cancel 生效速度（thread.interrupt() 对 HTTP 阻塞调用无立即效果）——optimization candidate，独立 hardening
- [维度14-6] AgentSession 其他可变字段的 volatile——watch-only residual，独立 hardening
- 并发执行排队语义（第二次 execute 排队等待而非 fail-fast）——out-of-scope improvement，产品策略变更
