# 180 nop-ai-agent Sticky-Pause Recovery Protocol

> **Plan Status**: planned
> **Module**: nop-ai-agent
> **Work Item**: L3-6 (Sticky-Pause Recovery)

> Last Reviewed: 2026-06-14
> Source: Carry-over from plans 177 (`ai-dev/plans/177-nop-ai-agent-denial-ledger.md`) and 179 (`ai-dev/plans/179-nop-ai-agent-db-denial-ledger.md`). Both plans deferred the sticky-pause recovery protocol as an independent successor with `Successor Required: yes`. Plan 179 (DBDenialLedger) is now completed — the sole dependency (DB-persisted denial counts that survive session/ledger-instance reconstruction) is landed. This plan delivers the recovery protocol: the human-intervention entry point that clears a denial-ledger-paused session and resumes execution. Design `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §6.2 (`pauseBehavior = sticky` — 暂停后只有人类干预才能恢复); roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 3 row L3-6 (IDenialLedger + NoOpDenialLedger + DBDenialLedger).
> Related: Plan 179 (L3-6 DBDenialLedger — direct predecessor; this plan is its sticky-recovery carry-over), Plan 177 (L3-6 contract + dispatch-path integration), Plan 176 (L3-5 IApprovalGate — `IApprovalChannel` for gated resume remains deferred to its successor), Plan 157 (A5 session cancel — cancelSession precedent for engine-level session lifecycle methods)

## Purpose

将设计 §6.2 `pauseBehavior = sticky` 的完整恢复协议收口到"已落地"状态：当一个 session 因 denial-ledger 阈值暂停（`AgentExecStatus.paused`）后，**只有**显式的人类干预恢复调用才能清除暂停状态并继续执行——自动恢复被 sticky 语义禁止。

当前状态：暂停机制已完整接通（plan 177 + 179 交付）。`IDenialLedger.reset(sessionId)` 方法存在且已实现（`DBDenialLedger` DELETE 该 session 的全部 denial 记录），但 `reset` 从未被任何代码调用——`IDenialLedger.java:79` 的 Javadoc 明确标注"full sticky recovery protocol is a deferred successor"。没有任何引擎方法可以从外部触发恢复：`IAgentEngine` 无 `resumeSession` 方法（`grep resumeSession` → 0 hits）。一个被暂停的 session 永远停留在 `paused` 状态——`denialLedger.isPaused` 在 ReAct 循环入口阻止任何后续执行。本计划交付恢复入口点 + 审计追踪 + sticky 语义验证。

## Current Baseline

- **L3-6 contract surface ✅ landed (plan 177)**: `IDenialLedger`（4 方法：`recordDenial` / `isPaused` / `getDenialCount` / `reset`）+ `DenialRecord` + `DenialRecordOutcome` + `DenialLayerSource` + `NoOpDenialLedger` 位于 `io.nop.ai.agent.security` 包
- **DBDenialLedger ✅ landed (plan 179)**: `DBDenialLedger implements IDenialLedger`（raw JDBC，`ai_agent_denial` 表，per-session COUNT/INSERT/DELETE，计数跨 ledger 实例重建存活）。`reset(sessionId)` = DELETE 该 session 全部行
- **Dispatch-path integration ✅ wired (plan 177)**: `ReActAgentExecutor` 的 `handleDenialAndCheckThreshold` 在全部 5 个 deny 路径调用 `recordDenial`；暂停经两步机制生效——**(Step 1，阈值在 deny 路径内触发)** `handleDenialAndCheckThreshold` 内部检查阈值、`ctx.setStatus(AgentExecStatus.paused)`（`ReActAgentExecutor.java:1006`）+ 发布 `SESSION_PAUSED` 事件（`:1015`）+ 返回 `true` → 调用方 `break dispatchLoop`（如 `:685`）；**(Step 2，下一次 ReAct 迭代)** reactLoop 开始时 `denialLedger.isPaused(sessionId)`（`:496`）返回 true → 调用 `handleSessionPaused`（`:1046`，仅设置 status + 发布事件，不重新 record denial）→ `break reactLoop`（`:498`）在任何 LLM 调用之前。注意 `handleSessionPaused` 是 Step 2 独立调用的方法，**不**是 Step 1 阈值触发的入口——plan 此前将两者混淆
- **`IDenialLedger.reset(sessionId)` ✅ exists but never called**: `IDenialLedger.java:75-85` 定义 + `DBDenialLedger` 实现（DELETE）；`grep "denialLedger.reset\|\.reset(sessionId)" nop-ai/nop-ai-agent/src/` → 0 调用点。这是本计划要收口的 gap——需要引擎入口点调用它
- **`AgentExecStatus.paused` ✅ exists**: `AgentExecStatus.java` 含 `paused` 枚举值 + Javadoc（治理策略自动触发，区别于 cancelled/forced_stopped/escalated）
- **`AgentEventType.SESSION_PAUSED` ✅ exists**: `AgentEventType.java:42`；无 `SESSION_RESUMED`（`grep SESSION_RESUMED` → 0 hits）——本计划新增
- **Sticky enforcement ALREADY works via existing isPaused check**: 在 `DBDenialLedger` 注册时，调用 `execute()` 对 paused session 会立即在 ReAct 循环入口被 `isPaused` 中止（`denialLedger` 中的计数仍在）→ re-pause。这意味着 sticky 语义已在运行时被现有代码强制执行——缺少的是 (a) 一个能清除暂停的显式恢复入口点，(b) 将此行为作为 sticky 契约的显式测试和文档
- **Engine session lifecycle methods pattern ✅ established**: `IAgentEngine` 有 `forkSession` / `getSessionStatus` / `cancelSession` default 方法（均 throw UOE），`DefaultAgentEngine` 覆盖实现。`resumeSession` 遵循同一模式
- **`DefaultAgentEngine.doExecute` context-building pattern ✅ established but NOT reusable as-is**: `doExecute` 从 `AgentMessageRequest` + `sessionStore` 构建 `AgentExecutionContext`（load agent model → getOrCreate session → build context with history → resolve executor → execute）。**Gap**：`doExecute` 在 `DefaultAgentEngine.java:617` 无条件 `ctx.addMessage(new ChatUserMessage(request.getUserMessage()))`，且未抽取可复用的 context-building helper。resume 路径需要从已暂停 session 的 message history 重建 context 且**不**添加新用户消息——"复用 doExecute 逻辑"与"不添加新用户消息"当前互斥，因此 resume 实现需要 (a) 从 doExecute 抽取共享 context-builder（首选）或 (b) 编写独立 context-build 路径（跳过 user-message 追加）
- **`cancelSession` precedent ✅**: `DefaultAgentEngine.java:466-494` 展示引擎级 session 生命周期方法的实现模式——validate session exists → update state → publish event → return。`resumeSession` 遵循同一模式
- **`NoOpDenialLedger` shipped default never pauses**: 不设 ledger 的引擎不会暂停任何 session → `resumeSession` 在默认配置下永不被需要

## Goals

- `IAgentEngine.resumeSession(String sessionId, String approver, String reason)` default 方法（throw UOE）——人类干预恢复入口点，遵循 `forkSession` / `cancelSession` 模式
- `DefaultAgentEngine.resumeSession(...)` 实现：validate session exists + status == paused（fail-fast）→ `denialLedger.reset(sessionId)`（清除暂停）→ set session running → publish `SESSION_RESUMED` 事件（payload: approver, reason, pre-reset denialCount）→ re-execute the session（继续 ReAct 循环，LLM 基于已有 conversation history 决定下一步）
- `AgentEventType.SESSION_RESUMED` 枚举值——审计追踪恢复操作
- **Sticky 契约验证 + 测试**：(a) 显式测试——对 paused session 调用 `execute()`（不 resume）→ 立即 re-pause（`isPaused` 仍 true）；(b) 显式测试——`resumeSession` 清除暂停后 re-execute 成功继续
- 设计文档 §6.2 更新：`pauseBehavior = sticky` 从"deferred to successor"变为"已落地"
- 向后兼容：`NoOpDenialLedger` 默认不受影响（永不清空，永不暂停，`resumeSession` 在默认配置下永不被调用）

## Non-Goals

- **`IApprovalChannel` 集成（gated resume）**: resume 操作本身即人类干预——显式调用 `resumeSession(approver, reason)` + 审计事件记录了谁在何时恢复。未来 `IApprovalChannel`（plan 176 successor）可以在允许 resume 之前要求 pre-approval，但当前协议不需要外部审批通道即可工作。归类为后续功能化增强
- **Crash/restart 后的 durable session recovery**: 当前 resume 仅覆盖 session 仍在 `sessionStore` 中的场景（in-memory 或 DB-backed session store）。进程崩溃后从持久化状态恢复 session 需要 `ICheckpointManager`（L3-4）——独立工作项
- **Resume rate limiting / abuse prevention**: 防止恶意反复 resume 清空拒绝计数。当前协议每次 resume 都清空计数（设计 §6.2 语义），rate limiting 是后续增强
- **Resume 自动通知 LLM**: resume 时是否向 conversation 追加一条 system message（如"Session resumed by operator X"）让 LLM 知道它被恢复——这是一个行为决策，本计划在 Phase 2 Execution Item 中裁定
- **多步恢复工作流**: 需要多个人工步骤的复杂恢复流程（如 review denied actions → approve/reset → resume）是后续增强
- **Resume 权限校验**: 当前 `approver` 参数仅用于审计（记录谁恢复了），不做权限校验（任何调用者都能 resume）。权限校验是后续增强

## Scope

### In Scope

- `IAgentEngine.resumeSession(sessionId, approver, reason)` default 方法（throw UOE）
- `AgentEventType.SESSION_RESUMED` 枚举值
- `DefaultAgentEngine.resumeSession(...)` 实现：validate paused → reset ledger → publish event → re-execute
- Sticky 契约显式测试：execute without resume = re-pause；resume = clear + continue
- 向后兼容测试：`NoOpDenialLedger` 默认不受影响
- 端到端测试：threshold pause → resume → continued execution
- 设计文档 §6.2 更新（`pauseBehavior = sticky` 标记为已落地 + 架构决策记录）

### Out Of Scope

- `IApprovalChannel` / gated resume（plan 176 successor）
- Crash/restart durable recovery（L3-4 ICheckpointManager）
- Resume rate limiting
- Resume 权限校验
- 多步恢复工作流

## Execution Plan

### Phase 1 - Resume contract surface + implementation + unit tests

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/IAgentEngine.java` (resumeSession default method), `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java` (resumeSession implementation), `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentEventType.java` (SESSION_RESUMED), `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/`

- Item Types: `Decision | Fix | Proof`

- [x] **Decision（resume 作为人类干预的语义）**: `resumeSession` 的调用本身即设计 §6.2 `pauseBehavior = sticky` 所要求的"人类干预"。不需要额外的 `IApprovalChannel` 审批——显式的、带有 approver 身份和 reason 的 `resumeSession` 调用 IS the human intervention。审计追踪经 `SESSION_RESUMED` 事件 payload（approver + reason + pre-reset denialCount）记录。未来的 `IApprovalChannel` 可以在 resume 前增加 pre-approval 步骤，但这是叠加增强而非当前协议的前置条件
- [x] **Decision（resume re-execution 不添加新用户消息）**: resume 从 paused session 的已有 conversation history 重建 `AgentExecutionContext`（系统 prompt + 已有消息），**不**添加新 user message。ReAct 循环以已有 context 调用 LLM——LLM 看到 denied tool call 的错误响应，自然倾向于 replan 而非盲重试（`IPostDenialGuard` 已阻止 exact-fingerprint 盲重试）。这使 resume 成为透明的 continuation 而非新的一轮对话
- [x] 新增 `AgentEventType.SESSION_RESUMED` 枚举值 + Javadoc（语义：人类干预恢复 denial-ledger-paused session，区别于 `SESSION_PAUSED` 的自动触发）
- [x] 新增 `IAgentEngine.resumeSession(String sessionId, String approver, String reason)` default 方法（throw `UnsupportedOperationException("resumeSession requires a registered denial ledger and a paused session")`），Javadoc 说明：(1) 仅对 `AgentExecStatus.paused` 的 session 有效；(2) 调用 `denialLedger.reset` 清除暂停；(3) 发布 `SESSION_RESUMED` 事件；(4) re-execute the session；(5) approver + reason 用于审计
- [x] 实现 `DefaultAgentEngine.resumeSession(sessionId, approver, reason)`：
  - Load session from `sessionStore`；fail-fast（`NopAiAgentException`）if not found
  - Fail-fast（`NopAiAgentException`）if `session.getStatus() != AgentExecStatus.paused`——只有 paused session 能被 resume（resume 一个 running/completed/cancelled session 是操作错误）
  - 查询 pre-reset denialCount（`denialLedger.getDenialCount(sessionId)`，用于审计 payload）
  - 调用 `denialLedger.reset(sessionId)`——清除暂停状态
  - 设置 `session.setStatus(AgentExecStatus.running)`
  - Publish `SESSION_RESUMED` 事件（payload: approver, reason, preResetDenialCount）
  - Re-execute the session：从 session 的 agentName + message history 重建 `AgentExecutionContext`（**不**添加新 user message——resume 是 transparent continuation 而非新对话）。**Gap**：`doExecute`（`DefaultAgentEngine.java:617`）无条件追加 user message 且未抽取 context-building helper，"复用 doExecute"与"不添加新用户消息"互斥——因此实现需要 (a) 从 doExecute 抽取共享 context-builder（首选），或 (b) 编写独立 context-build 路径（复制 doExecute 的 context-building 步骤但跳过 user-message 追加）。Resolve executor 必须与正常 execute 一致（同一 resolution 路径——基于 request-derived 的 effective tool/path access checkers），调用 `executor.execute(ctx)`
  - Post-execution session 生命周期记账（必须与 `doExecute` 一致）：在 `runningExecutions` 注册本次 resume 执行（支持 resume-期间的 cancel）；在 `finally` 中移除；将 executor 的最终 status 传播到 session（`session.setStatus(ctx.getStatus())`）；持久化新生成的 messages（`session.appendMessages(newMessages)`）；更新 token/iteration 计数（`session.addTokensUsed` / `session.addIterations`）；`session.touch()`
  - Return `CompletableFuture<AgentExecutionResult>`
- [x] 单元测试：`resumeSession` 对 non-existent session → `NopAiAgentException`（fail-fast）
- [x] 单元测试：`resumeSession` 对 non-paused session（running / completed / cancelled）→ `NopAiAgentException`（fail-fast）
- [x] 单元测试：`resumeSession` 对 paused session 调用 `denialLedger.reset`（验证 `isPaused` 在 reset 后返回 false，`getDenialCount` 归零——经 `DBDenialLedger` 或 spy/mock ledger 验证）
- [x] 单元测试：`resumeSession` 发布 `SESSION_RESUMED` 事件，payload 含 approver + reason + preResetDenialCount

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `SESSION_RESUMED` 存在于 `AgentEventType` 枚举
- [x] `resumeSession` 存在于 `IAgentEngine` 作为 default 方法（throw UOE），`DefaultAgentEngine` 覆盖实现
- [x] `resumeSession` 对 non-existent / non-paused session fail-fast（`NopAiAgentException`），不静默返回
- [x] `resumeSession` 调用 `denialLedger.reset(sessionId)`——验证 ledger 计数归零（`getDenialCount` == 0、`isPaused` == false after reset）
- [x] `resumeSession` 发布 `SESSION_RESUMED` 事件，payload 含 approver + reason + preResetDenialCount
- [x] **新增功能测试**: resumeSession 的 fail-fast（non-existent / non-paused）、reset 调用验证、事件发布——各有对应通过的测试（Minimum Rules #25）
- [x] **无静默跳过**: resumeSession 对 invalid state 抛 `NopAiAgentException`（不静默返回 null / 不静默 no-op）（Minimum Rules #24）
- [x] No owner-doc update required（设计文档更新在 Phase 2）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` Phase 1 新增测试通过
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - End-to-end sticky recovery + sticky enforcement + backward compat + design doc

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/` 或 `security/`（端到端测试）, `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §6.2, `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 L3-6 行

- Item Types: `Proof | Follow-up`

- [ ] 端到端测试（**sticky recovery——核心价值**）：构造 `DefaultAgentEngine` + `setDenialLedger(new DBDenialLedger(dataSource, 3))` + 功能化 `IToolAccessChecker`（对 tool call deny）→ 执行 ReAct 循环 → 3 次 deny → `SESSION_PAUSED` 事件 + session status == `paused`。**然后**：替换 checker 为 allow-all → `resumeSession(sessionId, "operator-1", "denial false positive")` → `denialLedger.isPaused` 归 false → session status == running → re-execute → LLM 继续 → `EXECUTION_COMPLETED`。验证完整链路：pause → resume → continued execution
- [ ] 端到端测试（**sticky enforcement——sticky 契约验证**）：构造同上 → 3 次 deny → paused。**然后**：直接调用 `engine.execute(newMessageRequest)` （不 resume）→ ReAct 循环入口 `denialLedger.isPaused` 仍 true → 立即 re-pause → session status 回到 paused。**这证明 sticky 语义**：没有显式 `resumeSession`（不清空 ledger），paused session 无法通过 `execute` 自动恢复
- [ ] 端到端测试（向后兼容）：构造 `DefaultAgentEngine` **不**设 ledger（默认 `NoOpDenialLedger`）→ ReAct 循环正常执行 → 0 spurious 暂停 → `resumeSession` 在此配置下永不被需要（如被调用，因无暂停 session，fail-fast）
- [ ] 端到端测试（resume 审计）：`resumeSession` 发布的 `SESSION_RESUMED` 事件 payload 含 approver + reason + preResetDenialCount（preResetDenialCount == threshold == 3，证明审计记录了恢复前的拒绝计数）
- [ ] 更新 `nop-ai-agent-security-and-permissions.md` §6.2：(1) 状态行 `pauseBehavior = sticky` 从"延期至 successor"改为"已落地"；(2) 记录架构决策——resume 作为人类干预语义（不需 IApprovalChannel）、resume re-execution 不添加新用户消息（transparent continuation）、sticky enforcement 经 `isPaused` 检查强制执行（execute without resume = re-pause）、resumeSession fail-fast 语义（only paused session can be resumed）；(3) `IApprovalChannel` gated resume 仍标注为 deferred enhancement（非前置条件）
- [ ] 更新 `nop-ai-agent-roadmap.md` §4 L3-6 行（如需）：确认 sticky recovery 已落地（L3-6 行已标 ✅——确保不矛盾）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] **端到端验证**（sticky recovery）：threshold pause → `resumeSession` → denial ledger 清空 → re-execute → session 从 paused 恢复到 completed——证明恢复协议完整可用（Minimum Rules #22 Anti-Hollow Rule）
- [ ] **端到端验证**（sticky enforcement）：对 paused session 调用 `execute()`（不 resume）→ 立即 re-pause——证明 sticky 语义经现有 `isPaused` 检查强制执行
- [ ] **接线验证**：`resumeSession` 调用 `denialLedger.reset` + re-execute 经 executor——运行时调用连通性已验证（非仅方法存在）（Minimum Rules #23 Wiring Verification Rule）
- [ ] **端到端验证**（向后兼容）：`NoOpDenialLedger` 默认行为不变，全部现有测试通过，0 spurious 暂停
- [ ] **Anti-Hollow Check**: sticky recovery 测试证明 pause → resume → continue 完整链路连通（resumeSession 不只是发布事件，实际清空 ledger + re-execute + LLM 继续推理）
- [ ] **无静默跳过**: resumeSession 对 invalid state fail-fast（非静默返回 null/no-op）
- [ ] **新增功能测试**: sticky recovery + sticky enforcement + 向后兼容 + resume 审计——各有对应通过的测试（Minimum Rules #25）
- [ ] `nop-ai-agent-security-and-permissions.md` §6.2 已更新（`pauseBehavior = sticky` 标记为已落地 + 架构决策记录）；`nop-ai-agent-roadmap.md` L3-6 行不矛盾
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全部通过（含新增 + 现有测试不受影响）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] `IAgentEngine.resumeSession` default 方法 + `DefaultAgentEngine` 实现已落地
- [ ] `AgentEventType.SESSION_RESUMED` 已落地
- [ ] `resumeSession` 调用 `denialLedger.reset` + 发布 `SESSION_RESUMED` + re-execute（完整恢复链路）
- [ ] Sticky recovery 端到端验证（pause → resume → continued execution）
- [ ] Sticky enforcement 验证（execute without resume = re-pause；sticky 经 `isPaused` 强制执行）
- [ ] `resumeSession` fail-fast 语义（non-existent / non-paused session → `NopAiAgentException`）
- [ ] `NoOpDenialLedger` 默认向后兼容（全部现有测试通过，0 spurious 暂停）
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [ ] 受影响 owner docs（设计 §6.2、roadmap L3-6）已同步到 live baseline
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**: closure audit 已验证 (a) resumeSession → denialLedger.reset → re-execute → LLM continues 调用链在运行时连通，(b) sticky enforcement 经 isPaused 检查强制执行（非仅文档约定），(c) 无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/180-nop-ai-agent-sticky-pause-recovery.md --strict` 退出码为 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码为 0
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### `IApprovalChannel` gated resume

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: resume 操作本身即设计 §6.2 `pauseBehavior = sticky` 所要求的"人类干预"——显式的、带有 approver 身份和 reason 的 `resumeSession` 调用 IS the human intervention。审计追踪经 `SESSION_RESUMED` 事件 payload 记录。未来的 `IApprovalChannel`（plan 176 successor）可以在允许 resume 前增加 pre-approval 步骤（如 Web UI 审批流），但这是叠加增强，非当前 sticky recovery 协议的前置条件。当前协议已满足 sticky 语义：只有显式 resume 才能清除暂停。
- Successor Required: yes
- Successor Path: plan 176 successor（`IApprovalChannel` 真实人类审批流）

### Crash/restart durable session recovery

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前 `resumeSession` 仅覆盖 session 仍在 `sessionStore` 中的场景（in-memory 或 DB-backed session store 保持 session 对象可达）。进程崩溃后从持久化状态恢复完整 session（message history + execution state + denial counts）需要 `ICheckpointManager`（L3-4）。`DBDenialLedger` 已持久化 denial counts（plan 179），但 session 的 message history 和 AgentExecutionContext 的恢复是 checkpoint 管理器的职责。
- Successor Required: yes
- Successor Path: L3-4 `ICheckpointManager` 独立计划

### Resume 权限校验

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前 `approver` 参数仅用于审计（记录谁恢复了 session），不做调用方权限校验——任何能调用 `resumeSession` 的代码都能恢复。在生产环境中，resume 的权限控制应由 API 层（REST/GraphQL endpoint 的认证授权）或未来的 `IApprovalChannel` 执行，而非引擎内部。引擎层的职责是提供恢复入口点 + 审计追踪，而非身份验证。
- Successor Required: no

## Non-Blocking Follow-ups

- `IApprovalChannel` pre-approval gating（resume 前需人类审批流）
- Crash/restart durable session recovery（`ICheckpointManager` L3-4）
- Resume rate limiting（防止恶意反复 resume 清空拒绝计数）
- Resume 自动通知 LLM（向 conversation 追加 system message 告知 LLM 被恢复——行为增强，当前 re-execution 是 transparent continuation）
- Resume 权限校验（调用方身份验证 + 角色授权）

## Closure

Status Note: *(to be filled at closure)*
Completed: *(to be filled at closure)*

Closure Audit Evidence:

- *(to be filled by independent subagent closure audit)*

Follow-up:

- *(to be filled at closure)*
