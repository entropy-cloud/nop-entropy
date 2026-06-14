# 178 nop-ai-agent IPostDenialGuard + PassThroughPostDenialGuard + FingerprintPostDenialGuard (L3-7)

> **Plan Status**: active
> **Module**: nop-ai-agent
> **Work Item**: L3-7

> Last Reviewed: 2026-06-14
> Source: Carry-over from plan 177 (`ai-dev/plans/177-nop-ai-agent-denial-ledger.md`, Deferred But Adjudicated "Fingerprint 计算 — Successor Required: yes, Successor Path: L3-7 独立计划" + Non-Blocking Follow-ups "`IPostDenialGuard` (L3-7) + `DenialResult` 结构化信封——审批治理链的盲重试阻止" + "Fingerprint 基础设施——L3-7 落地时引入"). Also deferred in plans 176 and 175 as the final Layer 3 defense-in-depth chain node. Now unblocked: L3-6 ✅ landed (plan 177 — `IDenialLedger` + `NoOpDenialLedger` + dispatch-path deny recording + threshold-pause). All dependencies satisfied: L3-5 ✅ (plan 176 — `IApprovalGate`), L3-6 ✅ (plan 177 — `IDenialLedger`), L1-6 ✅ (plan 139 — `IPermissionProvider`). Roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 3 row L3-7; design `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §6.3 (IPostDenialGuard — 拒绝后守卫) + §6.3 DenialResult 结构化信封 + §8 (defense-in-depth chain — guard sits after `IDenialLedger`, parallel in the chain before `ISandboxBackend`)
> Related: Plan 177 (L3-6 `IDenialLedger` — direct predecessor; this plan is its carry-over for L3-7 + fingerprint infrastructure), Plan 176 (L3-5 `IApprovalGate` — approval-gate chain predecessor), Plan 175 (Layer 2 dispatch-path consultation — consultation-point precedent), Plan 173 (L2-13 `ISecurityLevelResolver`), Plan 172 (L2-14 `IPermissionMatrix`), Plan 144 (audit logger — deny-path audit precedent)

## Purpose

将 Layer 3 拒绝后守卫（`IPostDenialGuard`）的契约表面 + `DenialResult` 结构化信封 + `ActionFingerprint` 计算基础设施 + pass-through 默认 + 功能化指纹实现 + dispatch-path 咨询接通，完成纵深防御链（设计 §8）的最后一个 Layer 3 链节点。当前 dispatch path 在 Layer 1/2/3 各检查点对工具调用做出 allow/deny 决策——deny 路径记录审计日志、发布事件、返回 error response、向 `IDenialLedger` 记录拒绝（plan 177）。但 **没有任何组件阻止 Agent 盲重试相同的已拒绝操作**——Agent 可以在下一轮迭代中再次提交完全相同的 tool call，每次都走完全部 Layer 1/2/3 检查后才被拒绝，浪费 token、污染对话上下文、最终触发 denial-threshold 暂停（而非在重试入口就被拦截）。纵深防御链（设计 §8）在 `IDenialLedger` 之后缺少"盲重试阻止"节点。

本计划接续这条链：使 dispatch path 在处理每个 tool call 时，**在 Layer 1 检查之前**咨询 post-denial guard——如果该 action 的 fingerprint 已在本 session 的已拒绝集合中（且无合法 follow-up 标签），直接以 `REPEATED_SAME_INTENT` 拒绝，跳过全部 Layer 1/2/3 检查。同时，在每次 Layer 1/2/3 deny 后向 guard 记录被拒绝 action 的 fingerprint，使后续重试可被检测。`PassThroughPostDenialGuard` 默认使行为与接线前完全一致（不阻止任何重试，0 spurious 拒绝）。

## Current Baseline

- **Layer 3 denial ledger ✅ delivered (plan 177)**: `IDenialLedger` + `DenialRecord` + `DenialRecordOutcome` + `DenialLayerSource`（5 个 enum 值：`LAYER1_TOOL_ACCESS`/`LAYER1_PERMISSION`/`LAYER1_PATH_ACCESS`/`LAYER2_SECURITY_POLICY`/`LAYER3_APPROVAL_GATE`）+ `NoOpDenialLedger` shipped 默认位于 `io.nop.ai.agent.security` 包。dispatch loop 通过 `handleDenialAndCheckThreshold(...)` 在全部 5 个 deny 路径向 ledger 记录拒绝，达到阈值后 dispatch for-loop `break` + session 状态标记 `paused` + `SESSION_PAUSED` 事件 + ReAct 迭代开始 `isPaused` 检查 `break reactLoop`
- **Layer 3 approval gate ✅ delivered (plan 176)**: `IApprovalGate` + `ApprovalDecision` + `ApprovalDenialKind` + `AutoApproveGate` shipped 默认位于 security 包。dispatch loop 在 Layer 2 matrix 放行后调用 `checkLayer3Approval`
- **dispatch loop 结构** (`ReActAgentExecutor.execute()` :613-731): `dispatchLoop:` for-loop 对每个 `chatToolCall` 依次执行 `toolCallRepairer.repair` :615 → `toolAccessChecker.checkAccess` :623 → `permissionProvider.resolve` :646 → `checkPathAccess` :669 → `checkLayer2Consultation` :692 → `checkLayer3Approval` :714 → `allowedCalls.add` :730。**共 5 个 deny `continue` 路径**（tool access deny :628-643、permission deny :651-666、path access deny :670-681、Layer 2 deny :694-705、Layer 3 deny :716-727）。每个 deny 路径在 error response + `ctx.addMessage` 之后调用 `handleDenialAndCheckThreshold(...)` 记录到 ledger 并检查阈值，然后 `continue`（或 threshold 达到时 `break dispatchLoop`）
- **`handleDenialAndCheckThreshold` helper** (`ReActAgentExecutor.java:902-924`): 接收 `(sessionId, toolName, layerSource, reason, matchedRule, ctx, agentName)`，构造 `DenialRecord.of(...)` → `denialLedger.recordDenial(record)` → 检查 `outcome.isThresholdExceeded()` → 若达到阈值：`ctx.setStatus(paused)` + 审计日志 + `SESSION_PAUSED` 事件 + 返回 `true`（调用方 `break dispatchLoop`）。**当前不接收 tool call arguments**——仅用 toolName 构造 DenialRecord，无法计算 action fingerprint
- **post-dispatch pause handling** (`ReActAgentExecutor.java:733-744`): dispatch for-loop 结束后（正常或 threshold-abort `break`），检查 `ctx.getStatus() == paused` → 若是，跳过 `allowedCalls` 执行 + `continue reactLoop`（**不** `break reactLoop`——reactLoop 中止由下一轮迭代开始 `isPaused` 检查负责）
- **reactLoop iteration-start checks** (`ReActAgentExecutor.java:456-474`): `reactLoop:` while-loop 每次迭代开始，依次检查 `cancelRequested` → `denialLedger.isPaused(sessionId)`（→ `handleSessionPaused` + `break reactLoop`）→ `shouldForceStop`
- **`AgentExecStatus` 枚举**: `{pending, running, completed, failed, cancelled, forced_stopped, escalated, paused}`（plan 177 新增 `paused`）。`AgentEventType` 含 `SESSION_PAUSED`（plan 177 新增）
- **`AuditEvent` + `IAuditLogger` + `AuditDecision` 已存在**: dispatch loop 中每个检查点都记录审计事件。`Slf4jAuditLogger` 是 shipped 默认
- **引擎接线模式**: Layer 3 组件通过 mutable field + setter + getter 接线（field 声明处初始化为 pass-through 默认），`ReActAgentExecutor` 通过 Builder 接收。`DefaultAgentEngine.resolveExecutor`（:752-778）将组件传递给 Builder。L3-7 组件遵循同一模式——参照 `denialLedger` 接线（`DefaultAgentEngine.java:91` field 声明、:391-392 setter、:399-401 getter、:778 resolveExecutor 传递；`ReActAgentExecutor.java:134` field、:157 构造器参数、:188-189 null-default、:386-387 Builder 方法）
- **设计契约 §6.3**: `IPostDenialGuard` 职责——被拒后阻止 Agent 盲重试相同操作。合法 follow-up 标签（只有 3 种）：`LOWER_PRIVILEGE`（降权重试）、`EXPLAIN`（向用户解释限制）、`NARROWER_APPROVAL`（请求更窄范围的审批）。无标签的盲重试（相同 fingerprint）→ `REPEATED_SAME_INTENT` 拒绝。`DenialResult` 结构化信封：reason 枚举 + suggestedNextStep + actionFingerprint + message + retryable。默认 = `PassThroughPostDenialGuard`（不阻止重试）
- **设计 §8 纵深防御链**: `ISecurityLevelResolver` → `IApprovalGate`（人类审批 ✅）→ `IDenialLedger`（拒绝计数 + 阈值暂停 ✅）→ **`IPostDenialGuard`（盲重试阻止 — 本计划）** → `ISandboxBackend`（隔离执行 — Layer 4）。guard 是 `IDenialLedger` 之后的下一个（也是最后一个 Layer 3）链节点
- **fingerprint 设计 §6.2/§6.3**: `action_fingerprint = SHA-256(actionKind + argv + cwd + criticalEnv)[:32]`，用于标识"相同的危险意图"。plan 177 将 fingerprint 基础设施 deferred 到 L3-7——本计划引入
- **L3-7 类型在代码中均不存在**: grep `IPostDenialGuard|PostDenialGuard|DenialResult|DenialReason|ActionFingerprint` in `nop-ai/` → 0 type definitions（仅 5 个 Javadoc 引用在 `IApprovalGate.java:14`、`IDenialLedger.java:7`、`ApprovalDecision.java:23-24`、`DenialRecord.java:13-15`、`ApprovalDenialKind.java:10-11` 中以 `{@code DenialResult}` / `{@code IPostDenialGuard}` 形式引用为"归属于 L3-7"——verified）
- **roadmap 依赖全部满足**: L3-5 ✅ (plan 176), L3-6 ✅ (plan 177), L1-6 ✅ (plan 139)

## Goals

- `IPostDenialGuard` 契约表面位于 `io.nop.ai.agent.security` 包，遵循 `IApprovalGate` / `IDenialLedger` / `IPermissionMatrix` 的契约风格（Javadoc 说明 default 实现和 dispatch-path 集成语义）。契约核心两个操作：(1) **consultation**——在 dispatch-path 处理 tool call 时（Layer 1 检查之前），检查该 action 是否为本 session 已拒绝操作的盲重试（相同 fingerprint），若是则返回 `DenialResult`（阻止），否则返回 null（放行到 Layer 1/2/3 检查）；(2) **recording**——在每次 Layer 1/2/3 deny 后，向 guard 记录被拒绝 action 的 fingerprint，使后续重试可被检测
- `DenialResult` 不可变值类型（security 包），遵循 `ApprovalDecision` / `MatrixDecision` 的 deny-with-reason 值对象模式。字段：`DenialReason reason`（拒绝原因枚举）+ `DenialSuggestedStep suggestedNextStep`（建议下一步枚举）+ `String actionFingerprint`（被阻止 action 的 SHA-256 指纹）+ `String message`（人类可读消息）+ `boolean retryable`（是否可重试）。提供工厂方法 + equals/hashCode
- `DenialReason` 枚举（security 包）覆盖设计 §6.3 的 reason 值域：`HUMAN_REJECTED`（人类拒绝）+ `THRESHOLD_EXCEEDED`（阈值超过）+ `REPEATED_SAME_INTENT`（盲重试相同操作——L3-7 核心场景）+ `TIMEOUT`（审批超时，收窄审计发现 L3-G1）
- `DenialSuggestedStep` 枚举（security 包）覆盖设计 §6.3 的 suggestedNextStep 值域：`REPLAN`（重新规划）+ `ASK_USER`（询问用户）+ `LOWER_PRIVILEGE`（降权重试——对应 `LOWER_PRIVILEGE` follow-up 标签）+ `NARROWER_APPROVAL`（请求更窄范围审批——对应 `NARROWER_APPROVAL` follow-up 标签）
- `ActionFingerprint` 不可变值类型 + 计算工具（security 包）：给定 actionKind（toolName）、arguments（tool call 参数）、workDir、criticalEnv，产生确定性的 SHA-256 指纹（截断到固定长度的 hex 字符串）。指纹计算必须确定性——相同输入总是产生相同指纹（参数 map 经 canonical 序列化：key 排序 + 一致 JSON 表示）。`ActionFingerprint` 值类型包装指纹字符串，提供 `compute(...)` 工厂 + equals/hashCode
- `PassThroughPostDenialGuard` pass-through 默认（consultation 永远返回 null = 不阻止任何重试；recording no-op；reset no-op），作为 shipped 默认注入引擎——无人值守自动化 Layer 1 基线不受影响
- `FingerprintPostDenialGuard` 功能化实现（security 包）：维护 per-session 的已拒绝 fingerprint 集合（线程安全——`ConcurrentHashMap<String, Set<String>>`，per-session 集合独立）。consultation 时计算 incoming action 的 fingerprint，检查是否在 session 的已拒绝集合中——若在则返回 `DenialResult`（reason = `REPEATED_SAME_INTENT`，suggestedNextStep = `REPLAN`，retryable = false）；若不在则返回 null（放行）。recording 时计算 fingerprint 并加入 session 集合。reset 清除 session 集合。这是 shipped 功能化实现（纯内存、无外部依赖），可经 setter 注册启用盲重试阻止
- dispatch-path 集成接通：(a) 在 dispatch loop 中每个 tool call 处理开始时（toolName 提取之后、Layer 1 `toolAccessChecker.checkAccess` 之前），新增 post-denial-guard consultation 步骤——若 guard 返回 `DenialResult`（盲重试检测命中），记录审计日志（DENY + reason + matched rule `layer3_post_denial_guard`）+ 发布 `TOOL_CALL_DENIED` 事件 + 返回 `ChatToolResponseMessage.error(...)` + 向 `IDenialLedger` 记录拒绝（通过 `handleDenialAndCheckThreshold`，layerSource = 新增 `LAYER3_POST_DENIAL_GUARD`）+ `continue`/`break dispatchLoop`（与现有 deny 路径模式一致）；(b) 在每次 Layer 1/2/3 deny 后（现有 `handleDenialAndCheckThreshold` 调用之内或之后），向 guard 记录被拒绝 action 的 fingerprint
- `DenialLayerSource` 枚举新增 `LAYER3_POST_DENIAL_GUARD` 值，标识 post-denial-guard consultation deny 路径（与现有 5 个 layer source 并列）
- 端到端验证：注册 `FingerprintPostDenialGuard` 后，Agent 盲重试已拒绝操作时在 Layer 1 检查之前被 guard 拦截（`REPEATED_SAME_INTENT`），而非走完全部 Layer 1/2/3 检查；使用 `PassThroughPostDenialGuard` 默认时行为与接线前完全一致（0 spurious 拒绝，全部现有测试通过）
- 设计文档 §6.3 更新：标记 L3-7 契约已落地；记录架构决策（fingerprint 计算策略、consultation 点位置、follow-up 标签检测策略、`DenialResult` vs `ApprovalDecision` vs `DenialRecord` 三者边界、向后兼容策略）
- 单元测试覆盖：PassThroughPostDenialGuard 契约、FingerprintPostDenialGuard 功能（fingerprint 计算 + 盲重试检测 + per-session 隔离 + reset）、DenialResult 值语义、ActionFingerprint 确定性 + 碰撞抵抗、dispatch-path 集成（pre-Layer-1 consultation + deny-path recording）、接线验证（guard 确实在 dispatch loop 被调用）、向后兼容

## Non-Goals

- **`DenialFollowUpTag` 合法标签的运行时检测**: 设计 §6.3 定义了 3 种合法 follow-up 标签（`LOWER_PRIVILEGE` / `EXPLAIN` / `NARROWER_APPROVAL`），表示 Agent 在重试时改变了策略（降权、解释、请求更窄审批）。完整标签检测需要分析 Agent 的推理文本或 tool-call 参数差异（如路径变窄 = `NARROWER_APPROVAL`，权限参数变化 = `LOWER_PRIVILEGE`）。本计划的 `FingerprintPostDenialGuard` 使用 **exact-fingerprint matching**——相同 fingerprint（相同 actionKind + argv + cwd + criticalEnv）= 盲重试。参数变化自然产生不同 fingerprint，因此 legitimate follow-up（不同参数）自动放行。显式标签检测（标记 tool call 的 metadata 或推理文本分析）是后续增强
- **`DenialResult` 在 ReAct 循环中的消费策略**: `DenialResult` 作为 structured envelope 被 dispatch path 产生并记录到审计日志/事件，但 Agent 如何消费 `suggestedNextStep`（是否自动 replan、是否自动 ask-user）是独立的 ReAct 策略决策，不在本计划范围。本计划交付 envelope 的产生 + 记录，不交付 envelope 的消费
- **跨 session fingerprint 持久化**: `FingerprintPostDenialGuard` 是纯内存实现（`ConcurrentHashMap`），不持久化已拒绝 fingerprint 到 DB。跨 session 恢复的 fingerprint 持久化（Agent 在 session A 被拒绝的操作在 session B 仍被阻止）是 `DBDenialLedger` successor 或独立 governance plan 的职责——与 plan 177 的持久化 deferral 对称
- **post-denial-guard XDSL（§9 Layer 3 渐进式增强）**: 不引入 `post-denial-config.xml` 或 Delta 配置。`FingerprintPostDenialGuard` 通过 setter 程序化注入，与 `IApprovalGate` / `IDenialLedger` 一致
- **配置校验（"post-denial guard 配置存在但 guard 仍为默认时警告"）**: 需要 XDSL 配置基础设施，不在本计划范围
- **`DenialResult.reason` 的完整枚举穷举**: 仅覆盖设计 §6.3 列出的 4 个 reason 值（`HUMAN_REJECTED` / `THRESHOLD_EXCEEDED` / `REPEATED_SAME_INTENT` / `TIMEOUT`）。完整 reason 值域在后续工作项中按需扩展
- **fingerprint 碰撞处理**: SHA-256[:32]（32 hex 字符 = 128 bit）的碰撞概率在 Agent session 级别可忽略。碰撞检测/处理（如使用更长指纹或多级 hash）不在本计划范围
- **`ISandboxBackend`（Layer 4 隔离执行）**: 纵深防御链的下一节点（Layer 4），是独立工作项

## Scope

### In Scope

- `IPostDenialGuard` 接口（`io.nop.ai.agent.security` 包）
- `DenialResult` 不可变值类型（security 包）——结构化拒绝信封
- `DenialReason` 枚举（security 包）——`HUMAN_REJECTED` / `THRESHOLD_EXCEEDED` / `REPEATED_SAME_INTENT` / `TIMEOUT`
- `DenialSuggestedStep` 枚举（security 包）——`REPLAN` / `ASK_USER` / `LOWER_PRIVILEGE` / `NARROWER_APPROVAL`
- `ActionFingerprint` 不可变值类型 + 计算工厂（security 包）——SHA-256 based 确定性指纹
- `PassThroughPostDenialGuard` 默认实现（shipped 默认）
- `FingerprintPostDenialGuard` 功能化实现（shipped 功能化默认，纯内存）
- `DenialLayerSource` 枚举新增 `LAYER3_POST_DENIAL_GUARD`
- `DefaultAgentEngine` 对 `IPostDenialGuard` 的 mutable field + setter + getter（默认 = `PassThroughPostDenialGuard`）
- `ReActAgentExecutor.Builder` 接收 `IPostDenialGuard`
- `DefaultAgentEngine.resolveExecutor` 传递 `IPostDenialGuard` 给 Builder
- dispatch loop pre-Layer-1 consultation 步骤（blind-retry 检测）
- dispatch loop deny 路径 fingerprint recording（现有 5 个 deny 点 + 新增 post-denial-guard consultation deny 点向 guard 记录）
- 单元测试：PassThroughPostDenialGuard 契约 + FingerprintPostDenialGuard 功能 + DenialResult 值语义 + ActionFingerprint 确定性 + dispatch-path 集成 + 接线验证 + 向后兼容
- 端到端测试：FingerprintPostDenialGuard 注册后盲重试在 Layer 1 之前被拦截
- 设计文档 §6.3 更新

### Out Of Scope

- `DenialFollowUpTag` 运行时检测（推理文本分析 / tool-call metadata 标注）
- `DenialResult` 的 ReAct 循环消费策略（自动 replan / ask-user）
- 跨 session fingerprint 持久化（DB-backed guard）
- post-denial-guard XDSL 配置
- 配置校验
- `ISandboxBackend`（Layer 4）
- 完整 `DenialReason` 枚举穷举

## Execution Plan

### Phase 1 - IPostDenialGuard 契约 + DenialResult + ActionFingerprint + PassThroughPostDenialGuard + FingerprintPostDenialGuard + 单元测试

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/` (IPostDenialGuard, DenialResult, DenialReason, DenialSuggestedStep, ActionFingerprint, PassThroughPostDenialGuard, FingerprintPostDenialGuard, DenialLayerSource enum extension), `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/security/`

- Item Types: `Decision | Proof`

- [x] 定义 `DenialReason` 枚举（security 包），值域 = {`HUMAN_REJECTED`, `THRESHOLD_EXCEEDED`, `REPEATED_SAME_INTENT`, `TIMEOUT`}。每个值附带 Javadoc 说明语义和适用场景
- [x] 定义 `DenialSuggestedStep` 枚举（security 包），值域 = {`REPLAN`, `ASK_USER`, `LOWER_PRIVILEGE`, `NARROWER_APPROVAL`}。每个值附带 Javadoc 说明对应的 follow-up 策略
- [x] 定义 `DenialResult` 不可变值类型（security 包），遵循 `ApprovalDecision` / `MatrixDecision` 的不可变值对象模式。字段：`DenialReason reason`（never null）+ `DenialSuggestedStep suggestedNextStep`（never null）+ `String actionFingerprint`（may be null when fingerprint not applicable）+ `String message`（人类可读，may be null）+ `boolean retryable`。提供工厂方法 `of(reason, suggestedNextStep, actionFingerprint, message, retryable)` + 便捷工厂 `repeatedSameIntent(actionFingerprint, message)`（reason = `REPEATED_SAME_INTENT`, suggestedNextStep = `REPLAN`, retryable = false）。equals/hashCode 覆盖全部字段
- [x] 定义 `ActionFingerprint` 不可变值类型 + 计算工厂（security 包）。内部包装一个 `String` 指纹（固定长度 hex）。工厂方法 `compute(String actionKind, Map<String, Object> arguments, String workDir, Map<String, String> criticalEnv)`：(a) canonical 序列化 arguments（key 排序 + 确定性 String 表示——`TreeMap` 排序后 `toString()`）；(b) canonical 序列化 criticalEnv（同上）；(c) 拼接 `actionKind + "|" + canonicalArgs + "|" + workDir + "|" + canonicalEnv`；(d) SHA-256 哈希 → hex 编码 → 截断到 32 hex 字符；(e) 返回 `ActionFingerprint`。null arguments/workDir/criticalEnv 统一处理（null → 空字符串）。提供 `getValue()` getter + equals/hashCode。`ActionFingerprint` 是纯值类型——无状态、无副作用、确定性
- [x] 定义 `IPostDenialGuard` 接口（security 包），契约语义：(1) consultation——`DenialResult checkBeforeDispatch(String sessionId, String toolName, Map<String,Object> arguments, String workDir)`，返回 null（放行到 Layer 1/2/3 检查）或 `DenialResult`（盲重试检测命中，阻止）；(2) recording——`void recordDeniedAction(String sessionId, String toolName, Map<String,Object> arguments, String workDir)`，在 Layer 1/2/3 deny 后调用，记录被拒绝 action 的 fingerprint；(3) reset——`void reset(String sessionId)`，清除 session 的已拒绝 fingerprint 集合。接口 Javadoc 说明：(a) default 实现 = `PassThroughPostDenialGuard`（不阻止任何重试）；(b) dispatch-path 集成语义——consultation 在 Layer 1 之前、recording 在每个 deny 之后；(c) **线程安全契约**：实现必须是线程安全的（多个 session 可能并发访问同一 guard 实例；per-session fingerprint 集合必须独立）；(d) fingerprint 计算由实现内部完成（调用方不感知 fingerprint 细节）
- [x] 创建 `PassThroughPostDenialGuard` 默认实现（security 包）：`checkBeforeDispatch` 永远返回 null（不阻止）；`recordDeniedAction` no-op；`reset` no-op。无状态，singleton + `passThrough()` 工厂，遵循 `AutoApproveGate.autoApprove()` / `NoOpDenialLedger.noOp()` 先例。作为 shipped 默认使无人值守自动化不受影响
- [x] 创建 `FingerprintPostDenialGuard` 功能化实现（security 包）：内部 `ConcurrentHashMap<String, Set<String>>` 维护 per-session 已拒绝 fingerprint 集合（value 使用 `ConcurrentHashMap.newKeySet()` 线程安全 set）。`checkBeforeDispatch`：(a) sessionId 为 null → 返回 null（匿名 session 不跟踪）；(b) 计算 `ActionFingerprint.compute(toolName, arguments, workDir, null)`；(c) 检查 fingerprint 是否在 session 的集合中——若在 → 返回 `DenialResult.repeatedSameIntent(fingerprint.getValue(), "Repeated same denied action: " + toolName)`；(d) 若不在 → 返回 null。`recordDeniedAction`：(a) sessionId 为 null → no-op；(b) 计算 fingerprint；(c) 加入 session 集合（`computeIfAbsent`）。`reset`：从 map 中移除 session 条目。线程安全——per-session set 独立，session A 的 deny 不影响 session B 的 fingerprint 集合
- [x] 向 `DenialLayerSource` 枚举新增 `LAYER3_POST_DENIAL_GUARD` 值，标识 post-denial-guard consultation deny 路径。Javadoc 说明：Layer 3 post-denial-guard blind-retry detection denial（`IPostDenialGuard`）
- [x] 单元测试验证 `PassThroughPostDenialGuard` 契约：`checkBeforeDispatch` 永远返回 null（包括 sessionId=null、重复调用后仍返回 null）；`recordDeniedAction` 不抛异常；`reset` 不抛异常
- [x] 单元测试验证 `FingerprintPostDenialGuard` 功能：(a) 首次 `checkBeforeDispatch` 返回 null（无记录）；(b) `recordDeniedAction` 后，相同参数的 `checkBeforeDispatch` 返回 `DenialResult`（reason = `REPEATED_SAME_INTENT`，retryable = false）；(c) 不同参数的 `checkBeforeDispatch` 返回 null（参数变化 → 不同 fingerprint → 放行）；(d) `reset` 后 `checkBeforeDispatch` 返回 null（集合已清除）；(e) per-session 隔离——session A 的 `recordDeniedAction` 不影响 session B 的 `checkBeforeDispatch`；(f) sessionId = null 时 `checkBeforeDispatch` 返回 null、`recordDeniedAction` no-op
- [x] 单元测试验证 `DenialResult` 值语义：工厂方法字段正确；`repeatedSameIntent` 便捷工厂的 reason = `REPEATED_SAME_INTENT`、suggestedNextStep = `REPLAN`、retryable = false；equals/hashCode 一致性
- [x] 单元测试验证 `ActionFingerprint` 确定性：(a) 相同输入（相同 actionKind + 相同 arguments + 相同 workDir + 相同 criticalEnv）→ 相同指纹；(b) arguments key 顺序不同但内容相同 → 相同指纹（canonical 序列化）；(c) 任一输入不同 → 不同指纹；(d) null arguments/workDir/criticalEnv 不抛异常（统一为空字符串）；(e) 指纹长度固定（32 hex 字符）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `IPostDenialGuard` 接口 + `DenialResult` 值类型 + `DenialReason` 枚举 + `DenialSuggestedStep` 枚举 + `ActionFingerprint` 值类型 + `PassThroughPostDenialGuard` 默认 + `FingerprintPostDenialGuard` 功能化实现存在，位于 `io.nop.ai.agent.security` 包
- [x] `DenialLayerSource` 枚举包含 `LAYER3_POST_DENIAL_GUARD` 值
- [x] **无静默跳过**: `PassThroughPostDenialGuard` 的所有方法有明确语义（`checkBeforeDispatch` 返回 null 是语义正确的 pass-through = "不阻止"，非空方法体；`recordDeniedAction` no-op 是明确的"不跟踪"语义，非吞异常或 `continue`）。`FingerprintPostDenialGuard` 的 `checkBeforeDispatch` 在 sessionId=null 时返回 null 是明确的"匿名 session 不跟踪"语义（Minimum Rules #24）
- [x] **新增功能测试**: `PassThroughPostDenialGuard` 契约测试 + `FingerprintPostDenialGuard` 功能测试（首次放行 + record 后阻止 + 参数变化放行 + reset 清除 + per-session 隔离 + null session）+ `DenialResult` 值语义测试 + `ActionFingerprint` 确定性测试（相同输入相同指纹 + canonical 序列化 + 输入差异产生差异指纹 + null 安全 + 固定长度）——全部通过（Minimum Rules #25）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过（新增 + 现有测试）
- [x] No owner-doc update required（设计文档更新在 Phase 2）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 引擎/Executor 接线 + Dispatch-loop consultation + recording + 端到端验证 + 设计文档

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java` (field + setter + getter + resolveExecutor), `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java` (Builder + pre-Layer-1 consultation + deny-path recording + helper extension), `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/IDenialLedger.java` (Javadoc 更新——L3-7 不再是 deferred), `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/IApprovalGate.java` (Javadoc 更新——L3-7 不再是 deferred), `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/` 或 `security/`, `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §6.3

- Item Types: `Fix | Decision | Proof | Follow-up`

- [x] 向 `DefaultAgentEngine` 添加 `IPostDenialGuard` 的 mutable field + setter + getter（field 声明处初始化为 `PassThroughPostDenialGuard`，遵循 `denialLedger` / `approvalGate` / `permissionMatrix` / `securityLevelResolver` 接线模式）。setter 对 null 输入回退默认（非静默忽略）
- [x] 向 `ReActAgentExecutor.Builder` 添加 `IPostDenialGuard` 接收方法。Builder build() 中对 null 输入设置 `PassThroughPostDenialGuard` 默认，遵循现有 Builder 模式（参照 `denialLedger`）
- [x] `DefaultAgentEngine.resolveExecutor(...)` 在构建 `ReActAgentExecutor` 时，将引擎自身的 `IPostDenialGuard` 传递给 Builder（参照 `denialLedger` 传递 at :778）
- [x] 在 `ReActAgentExecutor.execute()` dispatch loop 中，**在 `toolAccessChecker.checkAccess`（:623）之前**、toolName 提取（:617）之后，新增 post-denial-guard consultation 步骤：(a) 调用 `postDenialGuard.checkBeforeDispatch(sessionId, toolName, extractArguments(chatToolCall), resolveWorkDir(agentModel, ctx))`；(b) 若返回 `DenialResult`（盲重试命中）——记录审计日志（`AuditEvent` DENY + `DenialResult.message` + matched rule `layer3_post_denial_guard`）+ 发布 `TOOL_CALL_DENIED` 事件 + 返回 `ChatToolResponseMessage.error(...)`（message 包含 `DenialResult.message` + suggestedNextStep 提示）+ 调用 `handleDenialAndCheckThreshold(sessionId, toolName, DenialLayerSource.LAYER3_POST_DENIAL_GUARD, denialResult.getMessage(), "layer3_post_denial_guard", ctx, agentName)` 记录到 ledger 并检查阈值 + `continue`/`break dispatchLoop`（与现有 deny 路径模式一致）；(c) 若返回 null——继续到 Layer 1 `toolAccessChecker.checkAccess`
- [x] 扩展 deny-path fingerprint recording：在每个 Layer 1/2/3 deny 路径的 `handleDenialAndCheckThreshold(...)` 调用**之后**、`continue`/`break dispatchLoop` **之前**，调用 `postDenialGuard.recordDeniedAction(sessionId, toolName, extractArguments(chatToolCall), resolveWorkDir(agentModel, ctx))` 向 guard 记录被拒绝 action 的 fingerprint。共 5 个现有 deny 点 + 1 个新增 post-denial-guard consultation deny 点 = 6 个 recording 点。`PassThroughPostDenialGuard` 默认下 recording 是 no-op（0 开销），`FingerprintPostDenialGuard` 下实际计算 fingerprint 并加入集合
- [x] 提取 `extractArguments(ChatToolCall)` 辅助方法：从 `ChatToolCall` 提取参数 Map（`chatToolCall.getArguments()` 或等价方法），返回 `Map<String, Object>`。若 tool call 无参数，返回空 map。提取 `resolveWorkDir(AgentModel, AgentExecutionContext)` 辅助方法（若不存在则复用现有 workDir 解析逻辑——plan 170 已引入 `agentModel.getWorkDir()` 解析）
- [x] 端到端测试 A（盲重试阻止——单次迭代）：注册 `FingerprintPostDenialGuard`。LLM mock 在一次响应中返回 2 个相同 tool call（相同 toolName + 相同 arguments + 相同 workDir）。功能化 `IToolAccessChecker` 对第一个 deny（如 "tool not allowed"）。验证：(1) 第 1 个 tool call：post-denial-guard consultation 放行（首次，fingerprint 不在集合中）→ Layer 1 deny → `recordDeniedAction` 记录 fingerprint → `handleDenialAndCheckThreshold` 记录到 ledger（count=1）→ `continue`；(2) 第 2 个 tool call（相同 fingerprint）：post-denial-guard consultation 命中 → `DenialResult`（reason=`REPEATED_SAME_INTENT`）→ 审计日志 matchedRule=`layer3_post_denial_guard` → `handleDenialAndCheckThreshold` 记录到 ledger（count=2，layerSource=`LAYER3_POST_DENIAL_GUARD`）→ `continue`；(3) Layer 1 `toolAccessChecker.checkAccess` 对第 2 个 tool call **未被调用**（guard 在 Layer 1 之前拦截）
- [x] 端到端测试 B（盲重试阻止——跨迭代）：注册 `FingerprintPostDenialGuard`。LLM mock 在 2 次迭代中各返回 1 个相同 tool call。功能化 `IToolAccessChecker` deny。验证：(1) 第 1 次迭代：guard consultation 放行 → Layer 1 deny → recording → `continue` + reactLoop 继续；(2) 第 2 次迭代：guard consultation 命中（fingerprint 已在集合中）→ `REPEATED_SAME_INTENT` deny → Layer 1 未被调用
- [x] 端到端测试 C（参数变化放行——legitimate follow-up）：注册 `FingerprintPostDenialGuard`。LLM mock 返回 2 个 tool call，相同 toolName 但**不同 arguments**（如 `write-file` path `/a/b.txt` vs `/a/c.txt`）。功能化 `IToolAccessChecker` 对第一个 deny。验证：(1) 第 1 个 tool call deny → recording；(2) 第 2 个 tool call：guard consultation 放行（不同 arguments → 不同 fingerprint → 不在集合中）→ 继续到 Layer 1 检查
- [x] 向后兼容测试：使用 `PassThroughPostDenialGuard` 默认（不设 guard）的引擎执行 ReAct 循环，行为与接线前完全一致——全部现有测试通过，0 spurious 拒绝，Layer 1 检查对每个 tool call 正常执行
- [x] **接线验证测试**: 在端到端测试中添加断言验证 `postDenialGuard.checkBeforeDispatch(...)` 和 `postDenialGuard.recordDeniedAction(...)` 确实在 dispatch loop 中被调用（计数 guard 或 verify），而非仅组件被传递
- [x] 更新 `IDenialLedger.java` Javadoc（:7）：将 "`@code IPostDenialGuard` (L3-7)" 从 deferred 描述中移除/标注为已落地
- [x] 更新 `IApprovalGate.java` Javadoc（:13-14）：将 "`IPostDenialGuard` / `ISandboxBackend` (L3-7 / Layer 4 — deferred successors)" 中的 `IPostDenialGuard` 从 deferred 移除，标注为已落地
- [x] 更新设计文档 `nop-ai-agent-security-and-permissions.md` §6.3：标记 L3-7 契约已从"deferred successor"变为"已落地"。记录架构决策：(1) `DenialResult` 作为 `IPostDenialGuard` 专用返回类型，与 `IApprovalGate` 的 `ApprovalDecision`（gate 自身决策）和 `IDenialLedger` 的 `DenialRecord`（ledger 记录结构）三者边界清晰——`DenialResult` 是 post-denial 治理信封（suggestedNextStep + actionFingerprint + retryable）；(2) consultation 点位于 Layer 1 检查之前（盲重试在安全检查链入口被拦截，不浪费 Layer 1/2/3 检查开销）；(3) recording 在每个 deny 之后（包括 post-denial-guard 自身的 deny——形成闭环：guard deny 的 action 也被记录到 guard 自身，防止"guard deny 后 Agent 重试 guard deny 的结果"）；(4) `ActionFingerprint` 使用 exact-fingerprint matching（相同 actionKind + argv + cwd + criticalEnv = 盲重试；参数变化自然产生不同 fingerprint = legitimate follow-up 自动放行）；(5) `PassThroughPostDenialGuard` shipped 默认保证向后兼容；(6) `FingerprintPostDenialGuard` 是 shipped 功能化实现（纯内存、无外部依赖），经 setter 注册启用；(7) follow-up 标签的显式检测延期（exact-fingerprint matching 已覆盖 legitimate follow-up 的主要场景——参数变化）；(8) `DenialLayerSource.LAYER3_POST_DENIAL_GUARD` 新增，使 ledger 可区分 guard deny 与其他 layer deny

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ReActAgentExecutor.Builder` 接收 `IPostDenialGuard`；`DefaultAgentEngine.resolveExecutor` 传递该组件；引擎 field/setter/getter 存在（默认 = `PassThroughPostDenialGuard`）
- [x] dispatch loop 在 Layer 1 `toolAccessChecker.checkAccess` 之前调用 `postDenialGuard.checkBeforeDispatch(...)`；deny 时记录审计 + 事件 + error response + 向 ledger 记录（layerSource = `LAYER3_POST_DENIAL_GUARD`）+ continue/break（与现有 deny 路径一致）
- [x] 每个 Layer 1/2/3 deny 路径（共 5 个 + 新增 post-denial-guard consultation deny = 6 个）在 `handleDenialAndCheckThreshold` 之后调用 `postDenialGuard.recordDeniedAction(...)`
- [x] `DenialLayerSource.LAYER3_POST_DENIAL_GUARD` 枚举值新增
- [x] **端到端验证**: `FingerprintPostDenialGuard` 注册后，从 `AgentMessageRequest` 到 `ReActAgentExecutor.execute()` 到盲重试被 guard 在 Layer 1 之前拦截的完整路径已验证——三种场景均覆盖：(A) 单次迭代相同 tool call 盲重试 → guard consultation 命中 + Layer 1 未被调用；(B) 跨迭代相同 tool call 盲重试 → guard consultation 命中；(C) 参数变化（legitimate follow-up）→ guard consultation 放行 + Layer 1 正常执行（Minimum Rules #22 Anti-Hollow Rule）
- [x] **接线验证**: 端到端测试中 `postDenialGuard.checkBeforeDispatch(...)` 和 `postDenialGuard.recordDeniedAction(...)` 的调用已被断言验证（计数 guard 或 verify），而非仅组件被传递（Minimum Rules #23 Wiring Verification Rule）
- [x] **无静默跳过**: consultation 在 `PassThroughPostDenialGuard` 默认下不产生拒绝（guard 不跟踪），在 `FingerprintPostDenialGuard` 下产生真实盲重试检测——非空方法体/continue 跳过/吞异常（Minimum Rules #24）。deny 路径有审计日志 + 事件 + error response，非静默忽略
- [x] **新增功能测试**: dispatch-path 集成测试（pre-Layer-1 consultation + deny-path recording — 场景 A + B + C）+ 接线验证测试 + 向后兼容测试——全部通过（Minimum Rules #25）
- [x] **向后兼容**: 不设 guard（使用 `PassThroughPostDenialGuard` 默认）的引擎执行全部现有测试通过，0 spurious 拒绝
- [x] `IDenialLedger.java` + `IApprovalGate.java` Javadoc 更新（IPostDenialGuard 不再标注为 deferred）
- [x] 设计文档 §6.3 更新：L3-7 标记为已落地 + 八个架构决策记录
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过（全部新增 + 现有测试）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `IPostDenialGuard` 接口 + `DenialResult` 值类型 + `DenialReason` 枚举 + `DenialSuggestedStep` 枚举 + `ActionFingerprint` 值类型 + `PassThroughPostDenialGuard` 默认 + `FingerprintPostDenialGuard` 功能化实现存在，位于 security 包
- [x] dispatch loop pre-Layer-1 consultation 接通：`postDenialGuard.checkBeforeDispatch(...)` 在 `toolAccessChecker.checkAccess` 之前调用
- [x] dispatch loop deny-path recording 接通：每个 deny 路径（共 6 个）在 `handleDenialAndCheckThreshold` 之后调用 `postDenialGuard.recordDeniedAction(...)`
- [x] `DenialLayerSource.LAYER3_POST_DENIAL_GUARD` 枚举值新增
- [x] consultation deny 路径与现有 deny 路径模式一致（审计 + 事件 + error response + ledger 记录 + continue/break）
- [x] `FingerprintPostDenialGuard` 在端到端测试中实际检测盲重试 + 参数变化放行（非空壳）
- [x] `PassThroughPostDenialGuard` 默认向后兼容，现有全部测试通过
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs（设计 §6.3 + IApprovalGate/IDenialLedger Javadoc）已同步到 live baseline
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**: closure audit 验证 (a) `checkBeforeDispatch` 和 `recordDeniedAction` 在 dispatch loop 中确实被调用（不只是组件被传递），(b) consultation deny 路径有审计 + 事件 + error response + ledger 记录（非静默跳过），(c) 端到端路径从 request 到 blind-retry-blocked 完整走通——场景 A + B + C 均验证，(d) `PassThroughPostDenialGuard` 默认下 0 spurious 拒绝
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### DenialFollowUpTag 运行时检测（合法标签识别）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计 §6.3 定义了 3 种合法 follow-up 标签（`LOWER_PRIVILEGE` / `EXPLAIN` / `NARROWER_APPROVAL`），表示 Agent 在重试时改变了策略。完整标签检测需要分析 Agent 推理文本或 tool-call 参数差异（路径变窄 = `NARROWER_APPROVAL`，权限参数变化 = `LOWER_PRIVILEGE`）。本计划的 `FingerprintPostDenialGuard` 使用 exact-fingerprint matching——相同 actionKind + argv + cwd + criticalEnv = 盲重试。参数变化自然产生不同 fingerprint，因此 legitimate follow-up（不同参数）自动放行，无需显式标签检测。显式标签检测（标记 tool call metadata 或推理文本分析）在后续增强中引入。
- Successor Required: no
- Successor Path: 无（exact-fingerprint matching 已覆盖 legitimate follow-up 的主要场景）

### 跨 session fingerprint 持久化

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `FingerprintPostDenialGuard` 是纯内存实现（`ConcurrentHashMap`），不持久化已拒绝 fingerprint。跨 session 恢复的 fingerprint 持久化（Agent 在 session A 被拒绝的操作在 session B 仍被阻止）与 `DBDenialLedger` 的持久化需求对称。归类为 `DBDenialLedger` successor 或独立 governance plan 的职责。
- Successor Required: yes
- Successor Path: 与 `DBDenialLedger` successor 合并或独立 plan

### DenialResult 的 ReAct 循环消费策略

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `DenialResult` 作为 structured envelope 被 dispatch path 产生并记录到审计日志/事件，但 Agent 如何消费 `suggestedNextStep`（是否自动 replan、是否自动 ask-user）是独立的 ReAct 策略决策。本计划交付 envelope 的产生 + 记录，不交付 envelope 的消费（Agent 行为策略）。
- Successor Required: no
- Successor Path: 无（ReAct 策略增强是独立关注点）

## Non-Blocking Follow-ups

- `DenialFollowUpTag` 显式检测（tool-call metadata 标注 + 推理文本分析）
- `DenialResult` 的 ReAct 循环消费策略（自动 replan / ask-user / lowerPrivilege）
- 跨 session fingerprint 持久化（DB-backed guard，与 `DBDenialLedger` successor 合并）
- post-denial-guard XDSL（§9 Layer 3 渐进式增强）
- 配置校验（"post-denial guard 配置存在但 guard 仍为默认时警告"）
- `DenialReason` 完整枚举穷举（后续按需扩展）
- fingerprint 碰撞处理（更长指纹或多级 hash——session 级别碰撞概率可忽略）
- `ISandboxBackend`（Layer 4 隔离执行——纵深防御链的下一节点）
