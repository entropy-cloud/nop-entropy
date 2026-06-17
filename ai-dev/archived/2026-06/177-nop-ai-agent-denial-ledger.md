# 177 nop-ai-agent IDenialLedger + NoOpDenialLedger (L3-6)

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L3-6

> Last Reviewed: 2026-06-14
> Source: Carry-over from plan 176 (Non-Blocking Follow-ups: "`IDenialLedger` (L3-6) + `IPostDenialGuard` (L3-7) + `DenialResult` 信封——审批治理链的拒绝计数和盲重试阻止"). Also deferred in plans 175 and 173 as the downstream enforcement consumer of the SecurityLevel + PermissionMatrix + dispatch-path consultation chain. Now unblocked: L3-5 ✅ landed (plan 176 — `IApprovalGate` + `AutoApproveGate` + Layer 3 consultation wired into dispatch loop). Dependency L1-6 (`IPermissionProvider`) ✅ satisfied. Roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 3 row L3-6; design `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §6.2 (IDenialLedger — 拒绝账本) + §8 (defense-in-depth chain — ledger sits after `IApprovalGate`, before `IPostDenialGuard`/`ISandboxBackend`)
> Related: Plan 176 (L3-5 `IApprovalGate` — direct predecessor; this plan is its carry-over for L3-6), Plan 175 (Layer 2 dispatch-path consultation — wired `checkLayer2Consultation` into dispatch loop), Plan 173 (L2-13 `ISecurityLevelResolver`), Plan 172 (L2-14 `IPermissionMatrix`), Plan 144 (audit logger — deny-path audit precedent)

## Purpose

将 Layer 3 拒绝账本（`IDenialLedger`）的契约表面 + pass-through 默认 + dispatch-path 拒绝记录接通，使 plans 172/173/175/176 交付的纵深防御链在审批门之后不再断开。当前 dispatch path 在 Layer 1/2/3 各检查点对工具调用做出 allow/deny 决策——deny 路径记录审计日志、发布事件、返回 error response。但 **没有任何组件对 per-session 的拒绝进行计数**，也没有任何机制在累计拒绝达到阈值后暂停 autonomous 执行。纵深防御链（设计 §8）在 `IApprovalGate` 之后断开。本计划接续这条链：使 dispatch path 在每次 deny（Layer 1/2/3 任意检查点）时向 ledger 记录拒绝；当累计拒绝达到阈值时，暂停该 session 的 autonomous 执行（dispatch loop 中止 + ReAct 循环停止）。`NoOpDenialLedger` 默认使行为与接线前完全一致（不计数、不暂停，0 spurious 中止）。

## Current Baseline

- **Layer 3 approval gate ✅ delivered (plan 176)**: `IApprovalGate` + `ApprovalDecision` + `ApprovalDenialKind` + `AutoApproveGate` shipped default 位于 `io.nop.ai.agent.security` 包。dispatch loop 在 Layer 2 matrix 放行后、`allowedCalls.add` 之前调用 `checkLayer3Approval`（`ReActAgentExecutor.java:655-664`）。deny 路径记录 `AuditEvent`（DENY + reason + matched rule `layer3_approval_gate`）+ 发布 `TOOL_CALL_DENIED` 事件 + 返回 `ChatToolResponseMessage.error(...)` + `continue`
- **dispatch loop 结构** (`ReActAgentExecutor.execute()` :575-667): 对每个 `chatToolCall` 依次执行 Layer 1（toolAccessChecker :584 → permissionProvider :602 → pathAccessChecker `checkPathAccess` :620）→ Layer 2（`checkLayer2Consultation` :638）→ Layer 3（`checkLayer3Approval` :655）→ `allowedCalls.add` :666。**共 5 个 deny `continue` 路径**（tool access deny :599、permission deny :617、path access deny :627、Layer 2 deny :646、Layer 3 deny :663）。deny 路径统一模式：`ChatToolResponseMessage.error(...)` + `ctx.addMessage(...)` + `continue`。（注：`toolCallRepairer.repair(chatToolCall, ctx)` at :576 不产生 deny——它只修复并返回 tool call，无 `continue` 分支）
- **post-loop bookkeeping** (`ReActAgentExecutor.java:759-765`): dispatch 循环结束后，若 `ctx.getStatus() == running` 则设为 `completed`；随后对非 `cancelled`/`forced_stopped`/`escalated` 状态的 session 发布 `EXECUTION_COMPLETED` 事件 + 执行 `POST_CALL` hooks。**新增 `paused` 状态必须被加入此排除列表**，否则暂停的 session 会错误地发布 `EXECUTION_COMPLETED`
- **`AgentExecStatus` 枚举已存在**: `{pending, running, completed, failed, cancelled, forced_stopped, escalated}`（`AgentExecStatus.java`）。无 `paused` 状态。新增 `paused` 是向后兼容的——plan 157 已有先例：新增 `cancelled` 值时验证了 `agent-plan.record-mappings.xml` 的 dict 引用（`dict="io.nop.ai.agent.model.AgentExecStatus"`）对新枚举值向后兼容
- **`AgentEventType` 枚举已存在**: 含 `EXECUTION_STARTED`/`EXECUTION_COMPLETED`/`EXECUTION_FAILED`/`SESSION_CANCELLED`/`FORCED_STOP` 等 13 个值。**无 `SESSION_PAUSED`**——本计划需新增（语义不可被现有值替代）
- **`AuditEvent` + `IAuditLogger` + `AuditDecision` 已存在**: `AuditEvent`（sessionId + agentName + actorId + toolName + decision + reason + matchedRule + path + timestamp）；`AuditDecision` {ALLOW, DENY}。dispatch loop 中每个检查点都记录审计事件。`Slf4jAuditLogger` 是 shipped 默认
- **引擎接线模式**: Layer 2+ 组件通过 mutable field + setter + getter 接线（field 声明处初始化为 pass-through 默认），`ReActAgentExecutor` 通过 Builder 接收。`DefaultAgentEngine.resolveExecutor`（:746）将组件传递给 Builder。L3-6 组件遵循同一模式——参照 `approvalGate` 接线（`DefaultAgentEngine.java:87` field 声明、:360-362 setter、:368-370 getter、:746 resolveExecutor 传递；`ReActAgentExecutor.java:128` field、:150 构造器参数、:178-179 null-default、:364-365 Builder 方法）
- **设计契约 §6.2**: `IDenialLedger` 职责——per-session 拒绝计数，达到阈值（默认 3）自动暂停 autonomous 执行。`pauseBehavior = sticky`（暂停后只有人类干预才能恢复）。`persistence = DB`（DenialLedger 持久化到数据库，不丢失）。`Fingerprint = SHA-256(actionKind + argv + cwd + criticalEnv)[:32]`（标识"相同的危险意图"）。默认 = `NoOpDenialLedger`（不计数，不暂停）
- **设计 §8 纵深防御链**: `ISecurityLevelResolver` → `IApprovalGate`（人类审批 ✅）→ **`IDenialLedger`（拒绝计数 + 阈值暂停 — 本计划）** → `IPostDenialGuard`（盲重试阻止 — L3-7）→ `ISandboxBackend`（隔离执行 — Layer 4）。ledger 是 `IApprovalGate` 之后的下一个链节点
- **审计就绪分析发现 (audit-readiness-analysis §2.3 L3-G5)**: "`IDenialLedger` 接口未定义持久化契约要求"——文档说"持久化到数据库"，但 `NoOpDenialLedger` 不持久化。接口契约本身不保证持久化；非持久化实现可能导致 session 恢复后拒绝计数丢失，Agent 绕过阈值。本计划收窄此发现：接口契约不强制持久化（`NoOpDenialLedger` 合法），DB 持久化是 `DBDenialLedger` 的职责（deferred successor）
- **`DenialResult` 结构化信封 (§6.3)**: reason 枚举（human_rejected / threshold_exceeded / repeated_same_intent）+ suggestedNextStep + actionFingerprint + message + retryable。**归属于 L3-7（`IPostDenialGuard`）**，不在本计划范围
- **L3-6/L3-7 类型在代码中均不存在**: grep `IDenialLedger|NoOpDenialLedger|DenialLedger|DenialRecord` in `nop-ai/` → 0 hits（verified）
- **roadmap 依赖 L1-6 ✅**: `IPermissionProvider` 已交付（plan 139）

## Goals

- `IDenialLedger` 契约表面位于 `io.nop.ai.agent.security` 包，遵循 `IApprovalGate` / `IPermissionMatrix` / `ISecurityLevelResolver` 的契约风格（Javadoc 说明 default 实现和 dispatch-path 集成语义）。契约核心：记录 per-session 拒绝、暴露当前拒绝计数、在阈值达到时标记 session 为暂停状态、支持 session 重置
- `DenialRecord` 不可变值类型（security 包），记录单次拒绝的结构化信息（sessionId、toolName、reason、matchedRule、来源 layer、timestamp），遵循 `AuditEvent` / `ApprovalDecision` 的不可变值对象模式
- `NoOpDenialLedger` pass-through 默认（不计数、不暂停、`isPaused` 永远返回 false），作为 shipped 默认注入引擎——无人值守自动化 Layer 1 基线不受影响
- dispatch-path 集成接通：`ReActAgentExecutor` dispatch loop 中的每个 deny 路径（Layer 1/2/3 共 5 个 `continue` 分支）在现有审计 + 事件 + error response 之后，向 ledger 记录拒绝。记录后检查阈值：若达到阈值，中止当前 dispatch 循环（不再处理后续 tool calls）+ 标记 session 暂停（`AgentExecStatus.paused`）+ 发布 `SESSION_PAUSED` 事件 + 跳过当前迭代 `allowedCalls` 执行（**不** `break reactLoop`——ReAct 循环的中止由下一轮迭代开始时的 `isPaused` 检查独占负责，见下一条 goal；两个机制职责分离：dispatch-path 负责标记暂停 + 跳过剩余执行，迭代开始检查负责中止 ReAct 循环）
- post-loop bookkeeping 正确排除 `paused` 状态：暂停的 session 不发布 `EXECUTION_COMPLETED` 事件、不执行 `POST_CALL` hooks（与 `cancelled`/`forced_stopped`/`escalated` 一致）
- 在 ReAct 循环每次迭代开始时（处理 LLM 响应之前），检查 session 是否已被 ledger 标记为暂停——若是，立即中止执行并返回暂停响应。这确保即使暂停发生在上一轮迭代的中间，下一轮迭代也不会继续
- 端到端验证：注册功能化 ledger（测试内部类，threshold = 2）后，连续 2 次 deny 触发 session 暂停——dispatch path 实际中止（非仅接口存在）；使用 `NoOpDenialLedger` 默认时行为与接线前完全一致（0 spurious 暂停，全部现有测试通过）
- 设计文档 §6.2 更新：标记 L3-6 契约已落地；记录架构决策（持久化契约边界、阈值检查位置、暂停语义与 AgentExecStatus 关系、向后兼容策略）
- 单元测试覆盖：NoOpDenialLedger 契约、DenialRecord 值语义、dispatch-path 集成（deny 记录 + 阈值暂停 + 中止行为）、接线验证（ledger 确实在 dispatch loop deny 路径被调用）、向后兼容

## Non-Goals

- **`DBDenialLedger`（DB 持久化实现）**: 需要新增 ORM 实体模型（denial 记录表）+ DAO/store + Nop ORM 集成。这是独立的功能化实现工作项，参照 plan 171（DB-backed messenger）的独立 plan 模式。本计划交付契约 + NoOp 默认 + dispatch-path 集成，功能化 DB 实现在测试中用内存计数实现验证链路非空壳
- **`IPostDenialGuard` (L3-7) 和 `DenialResult` 结构化信封**: 盲重试阻止是独立后续工作项；`DenialResult`（reason 枚举 + suggestedNextStep + actionFingerprint + message + retryable）归属于 L3-7。本计划的 `DenialRecord` 仅覆盖拒绝账本自身的记录结构，不引入 `DenialResult`
- **Fingerprint 计算（`SHA-256(actionKind + argv + cwd + criticalEnv)[:32]`）**: 设计 §6.2 将 fingerprint 列为 IDenialLedger 的关键设计元素，用于标识"相同的危险意图"。但 L3-6 的核心机制是 per-session **总拒绝计数** + 阈值暂停（denialThreshold = 3）；fingerprint 匹配更核心地服务于 L3-7（IPostDenialGuard 的"盲重试相同操作"检测）。fingerprint 基础设施在 L3-7 落地时引入
- **Sticky pause 行为（人类干预才能恢复）**: 设计 §6.2 规定 `pauseBehavior = sticky`。本计划实现阈值暂停 + session 状态标记；"只有人类干预才能恢复"的 sticky 语义需要功能化 ledger + session 恢复协议，归类为 `DBDenialLedger` successor 的职责
- **Approval-config / denial-config XDSL（§9 Layer 3 渐进式增强）**: 不引入 `denial-config.xml` 或 Delta 配置。threshold 通过功能化实现的构造器/配置注入，`NoOpDenialLedger` 不涉及
- **session 恢复后拒绝计数持久化（审计 L3-G5）**: `NoOpDenialLedger` 不持久化（设计上合法）；跨 session 恢复的拒绝计数持久化是 `DBDenialLedger` 的职责。本计划收窄 L3-G5：接口契约不强制持久化，非持久化实现的安全风险在 design doc 中记录并由 `DBDenialLedger` successor 解决
- **配置校验（"denial threshold 配置存在但 ledger 仍为默认时警告"）**: 需要 XDSL 配置基础设施，不在本计划范围

## Scope

### In Scope

- `IDenialLedger` 接口（`io.nop.ai.agent.security` 包）
- `DenialRecord` 不可变值类型（security 包）——单次拒绝的结构化记录
- `DenialRecordOutcome` 不可变值类型（security 包）——`recordDenial` 的复合返回值（记录后当前拒绝计数 + 是否已达到阈值），遵循 `MatrixDecision` / `ApprovalDecision` 的不可变值对象模式
- `NoOpDenialLedger` 默认实现
- `AgentExecStatus` 新增 `paused` 状态（或复用现有状态的明确裁定 + 理由）
- `DefaultAgentEngine` 对 `IDenialLedger` 的 mutable field + setter + getter（默认 = `NoOpDenialLedger`）
- `ReActAgentExecutor.Builder` 接收 `IDenialLedger`
- `DefaultAgentEngine.resolveExecutor` 传递 `IDenialLedger` 给 Builder
- dispatch loop 中每个 deny 路径（Layer 1/2/3）向 ledger 记录拒绝
- 阈值检查 + 暂停中止逻辑（deny 后检查阈值 → 中止 dispatch loop + ReAct 循环）
- ReAct 循环迭代开始时的暂停状态检查
- 单元测试：NoOpDenialLedger 契约 + DenialRecord 值语义 + dispatch-path 集成（记录 + 阈值暂停 + 中止）+ 接线验证 + 向后兼容
- 端到端测试：功能化 ledger（测试内部类，threshold = N）在 dispatch path 中实际记录拒绝 + 达到阈值后实际暂停 session
- 设计文档 §6.2 更新

### Out Of Scope

- `DBDenialLedger`（DB 持久化实现 + ORM 模型 + DAO）
- `IPostDenialGuard` (L3-7)、`DenialResult` 信封
- Fingerprint 计算（SHA-256 based "same intent" 标识）
- Sticky pause 人类干预恢复协议
- denial-config XDSL
- 配置校验（threshold 配置与 ledger 实现一致性检查）

## Execution Plan

### Phase 1 - IDenialLedger 契约 + DenialRecord + NoOpDenialLedger + 单元测试

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/` (IDenialLedger, DenialRecord, DenialRecordOutcome, DenialLayerSource, NoOpDenialLedger), `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/security/`

- Item Types: `Decision | Proof`

- [x] 定义 `DenialRecord` 不可变值类型（security 包），遵循 `AuditEvent` / `ApprovalDecision` 的不可变值对象模式。记录单次拒绝的结构化信息：sessionId、toolName、deny 来源 layer（layer1_tool_access / layer1_permission / layer1_path_access / layer2_security_policy / layer3_approval_gate）、reason、matchedRule、timestamp。提供工厂方法 + equals/hashCode
- [x] 定义 `DenialRecordOutcome` 不可变值类型（security 包），作为 `recordDenial` 的复合返回值：当前 session 的累计拒绝计数（`count`，记录本次拒绝后）+ 是否已达到阈值（`thresholdExceeded`）。提供工厂方法 + equals/hashCode
- [x] 定义 `IDenialLedger` 接口（security 包），契约语义：(1) 记录一次 per-session 拒绝并返回 `DenialRecordOutcome`（当前拒绝计数 + 是否已达到阈值）；(2) 查询 session 当前是否处于暂停状态（阈值已达到）；(3) 查询 session 当前拒绝计数；(4) 重置 session 的拒绝计数（人类干预恢复）。接口 Javadoc 说明：(a) default 实现 = `NoOpDenialLedger`（不计数、不暂停）；(b) dispatch-path 集成语义——deny 路径记录后检查 `thresholdExceeded`；(c) **线程安全契约**：实现必须是线程安全的（多个 session 可能并发访问同一 ledger 实例；per-session 计数必须独立，session A 的 deny 不影响 session B 的计数）；(d) 持久化不保证——`NoOpDenialLedger` 不持久化，`DBDenialLedger`（successor）持久化到 DB
- [x] 创建 `NoOpDenialLedger` 默认实现（security 包）：`recordDenial` 返回 `DenialRecordOutcome`（count = 0、thresholdExceeded = false）；`isPaused` 永远返回 false；`getDenialCount` 返回 0；`reset` no-op。作为 shipped 默认，使无人值守自动化不受影响。无状态，天然线程安全
- [x] 单元测试验证 `NoOpDenialLedger` 契约：`recordDenial` 返回的 outcome count = 0、thresholdExceeded = false；`isPaused` 永远返回 false；`getDenialCount` 永远返回 0；`reset` 不抛异常；多次 `recordDenial` 后 `isPaused` 仍为 false
- [x] 单元测试验证 `DenialRecord` + `DenialRecordOutcome` 值语义：工厂方法创建的 record/outcome 字段正确；equals/hashCode 一致性；不同 layer 来源的 record 可区分

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `IDenialLedger` 接口 + `DenialRecord` 值类型 + `DenialRecordOutcome` 值类型 + `NoOpDenialLedger` 默认实现存在，位于 `io.nop.ai.agent.security` 包
- [x] **无静默跳过**: `NoOpDenialLedger` 的所有方法有明确语义（`isPaused` 返回 `false` 是语义正确的 pass-through，非空方法体；`recordDenial` 返回 count=0 是明确的"不计数"语义，非吞异常或 `continue`）（Minimum Rules #24）
- [x] **新增功能测试**: `NoOpDenialLedger` 契约测试（`isPaused` / `getDenialCount` / `reset` / 多次 `recordDenial` + `DenialRecordOutcome` 返回值）+ `DenialRecord` + `DenialRecordOutcome` 值语义测试——全部通过（Minimum Rules #25）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过（新增 + 现有测试）
- [x] No owner-doc update required（设计文档更新在 Phase 2）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 引擎/Executor 接线 + Dispatch-loop 拒绝记录 + 阈值暂停 + 端到端验证 + 设计文档

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java` (field + setter + getter + resolveExecutor), `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java` (Builder + dispatch loop deny recording + threshold check + pause abort + post-loop bookkeeping), `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/model/AgentExecStatus.java` (新增 `paused`), `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentEventType.java` (新增 `SESSION_PAUSED`), `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/IApprovalGate.java` (Javadoc 更新——L3-6 不再是 deferred), `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/` 或 `security/`, `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §6.2

- Item Types: `Fix | Decision | Proof | Follow-up`

- [x] 新增 `AgentExecStatus.paused` 枚举值。向后兼容性已由 plan 157 先例验证：新增 `cancelled` 值时确认 `agent-plan.record-mappings.xml` 的 dict 引用（`dict="io.nop.ai.agent.model.AgentExecStatus"`）对新枚举值向后兼容——dict 引用在枚举扩展时不报错，未知值在反序列化时 fallback。无需 Option B（复用 `forced_stopped`），`paused` 语义独立且清晰
- [x] 新增 `AgentEventType.SESSION_PAUSED` 枚举值。现有 13 个值中无一可语义替代"session 因拒绝阈值暂停"——`EXECUTION_FAILED` 暗示错误，`FORCED_STOP` 暗示外部干预，`SESSION_CANCELLED` 暗示用户取消。`SESSION_PAUSED` 语义精确：session 因治理策略（denial threshold exceeded）被自动暂停
- [x] 向 `DefaultAgentEngine` 添加 `IDenialLedger` 的 mutable field + setter + getter（field 声明处初始化为 `NoOpDenialLedger`，遵循 `approvalGate` / `permissionMatrix` / `securityLevelResolver` 接线模式）。setter 对 null 输入回退默认（非静默忽略）
- [x] 向 `ReActAgentExecutor.Builder` 添加 `IDenialLedger` 接收方法。Builder build() 中对 null 输入设置 `NoOpDenialLedger` 默认，遵循现有 Builder 模式
- [x] `DefaultAgentEngine.resolveExecutor(...)` 在构建 `ReActAgentExecutor` 时，将引擎自身的 `IDenialLedger` 传递给 Builder
- [x] 提取 `handleDenialAndCheckThreshold(sessionId, toolName, layerSource, reason, matchedRule, ctx)` 辅助方法，封装：`recordDenial` → 审计日志 → 阈值检查 → 返回是否应中止。5 个 deny 路径调用此方法保持 DRY，避免 ~30-50 行重复代码。方法返回 `boolean shouldAbort`（true = 阈值已达，应中止 dispatch loop）
- [x] 在 `ReActAgentExecutor.execute()` dispatch loop 中，每个 deny `continue` 路径（Layer 1 tool access deny :599、Layer 1 permission deny :617、Layer 1 path access deny :627、Layer 2 security policy deny :646、Layer 3 approval deny :663 — 共 5 个）在现有审计 + 事件 + error response **之后**、`continue` **之前**，调用 `handleDenialAndCheckThreshold(...)` 记录拒绝并检查阈值。若返回 `shouldAbort = true`：设置 `ctx.setStatus(AgentExecStatus.paused)` + 记录审计日志（DENY + reason "denial threshold exceeded" + matched rule `layer3_denial_ledger`）+ 发布 `SESSION_PAUSED` 事件 + `break` 跳出 dispatch for-loop（不再处理后续 tool calls，不再 `continue`）。若 `shouldAbort = false`：维持原有 `continue` 行为
- [x] dispatch for-loop 结束后（无论是正常结束还是 threshold-abort `break`）、执行 `allowedCalls` 之前，检查 `ctx.getStatus() == AgentExecStatus.paused`——若是，仅跳过 `allowedCalls` 执行（**不** `break reactLoop`——reactLoop 的中止由下一轮迭代开始时的 `isPaused` 检查独占负责，见下一 item；此设计确保两个机制职责不重叠——Mechanism 1 负责跳过当前迭代剩余执行，Mechanism 2 负责中止 ReAct 循环，从而使测试场景 B 的迭代 3 `isPaused` 检查可达）
- [x] **更新 post-loop bookkeeping** (`ReActAgentExecutor.java:759-765`)：将 `paused` 加入排除列表——`if (ctx.getStatus() == running) ctx.setStatus(completed)` 的条件不变（paused 不会被覆盖，因为 paused ≠ running）；但 `EXECUTION_COMPLETED` 事件发布的条件需排除 `paused`：`if (status != cancelled && status != forced_stopped && status != escalated && status != paused)`。否则暂停的 session 会错误地发布 `EXECUTION_COMPLETED` + 执行 `POST_CALL` hooks
- [x] 在 ReAct 循环（外层 while-loop——需在 while-loop 前添加 `reactLoop:` label，匹配代码库中显式 break 的风格）每次迭代开始时，在 `cancelRequested` 检查之后、`shouldForceStop` 检查之前，检查 `denialLedger.isPaused(sessionId)`——若是，设置 `ctx.setStatus(paused)` + `break reactLoop`（不再发起 LLM 调用）。这是唯一的 reactLoop-breaking 机制——session 被 ledger 标记为暂停后（无论是本轮 dispatch-path threshold-abort 还是外部预置），下一轮迭代开始时立即中止。位置选择理由：`cancelRequested` 优先（用户显式取消总是最高优先级）；pause 检查在 `shouldForceStop` 之前（pause 是治理决策，force_stop 是系统决策，治理决策应先于系统决策检查）
- [x] 端到端测试 A（单次迭代多 tool call deny → 阈值暂停）：注册功能化 ledger（测试内部类，threshold = 2，线程安全的 `ConcurrentHashMap<String, AtomicInteger>` 计数）。LLM mock 在一次响应中返回 2 个 tool calls，功能化 `IToolAccessChecker` 对两者 deny。验证：(1) 第 1 次 deny 后 `handleDenialAndCheckThreshold` 返回 shouldAbort=false、dispatch loop 继续；(2) 第 2 次 deny 后 shouldAbort=true、dispatch for-loop `break`、session status = paused、`SESSION_PAUSED` 事件发布、`EXECUTION_COMPLETED` **不**发布；(3) `allowedCalls` 为空（第 2 个 tool call 未被处理）
- [x] 端到端测试 B（跨迭代单 tool call deny → 阈值暂停）：功能化 ledger threshold = 2。LLM mock 在 2 次迭代中各返回 1 个 tool call，功能化 `IToolAccessChecker` deny。验证：(1) 第 1 次迭代 deny 后 count = 1、ReAct 循环继续（LLM 被第 2 次调用）；(2) 第 2 次迭代开始时 `isPaused` 检查——count = 1 < 2，未暂停，继续处理；(3) 第 2 次迭代 deny 后 count = 2、thresholdExceeded = true；(4) 第 3 次迭代开始时 `isPaused` 检查——true，立即 break，LLM **不**被第 3 次调用
- [x] 向后兼容测试：使用 `NoOpDenialLedger` 默认（不设 ledger）的引擎执行 ReAct 循环，行为与接线前完全一致——全部现有测试通过，0 spurious 暂停
- [x] **接线验证测试**: 在端到端测试中添加断言验证 `denialLedger.recordDenial(...)` 确实在 dispatch loop 的 deny 路径被调用（CountingLedger 计数器或 verify），而非仅组件被传递
- [x] 更新 `IApprovalGate.java` Javadoc（:13-14）：将 "`IDenialLedger` / `IPostDenialGuard` / `ISandboxBackend` (L3-6 / L3-7 / Layer 4 — deferred successors)" 中的 `IDenialLedger` 从 deferred 移除，标注为已落地
- [x] 更新设计文档 `nop-ai-agent-security-and-permissions.md` §6.2：标记 L3-6 契约已从"deferred successor"变为"已落地"。记录架构决策：(1) 接口契约不强制持久化（`NoOpDenialLedger` 合法），DB 持久化是 `DBDenialLedger` 的职责（收窄 L3-G5）；(2) 每个 deny 路径（Layer 1/2/3 共 5 个 deny 点）均记录到 ledger（覆盖全部拒绝来源，非仅 Layer 3）；(3) 阈值检查在每次 `recordDenial` 后立即进行 + ReAct 迭代开始时双重检查；(4) `paused` AgentExecStatus 的语义（vs `forced_stopped`/`cancelled`——paused 是治理策略自动触发，非用户/系统干预）；(5) `NoOpDenialLedger` shipped 默认保证向后兼容；(6) `pauseBehavior = sticky` 延期至 successor——当前实现是非 sticky 的（session 可通过 `reset()` 恢复后重新执行），sticky 恢复协议在 `DBDenialLedger` successor 或独立 governance plan 中解决

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ReActAgentExecutor.Builder` 接收 `IDenialLedger`；`DefaultAgentEngine.resolveExecutor` 传递该组件；引擎 field/setter/getter 存在（默认 = `NoOpDenialLedger`）
- [x] dispatch loop 中每个 deny `continue` 路径均通过 `handleDenialAndCheckThreshold(...)` 调用 `denialLedger.recordDenial(...)`（Layer 1/2/3 共 5 个 deny 点）
- [x] `AgentExecStatus.paused` 枚举值新增；post-loop bookkeeping（`:759-765`）排除 `paused`，暂停 session 不发布 `EXECUTION_COMPLETED` / 不执行 `POST_CALL` hooks
- [x] `AgentEventType.SESSION_PAUSED` 枚举值新增
- [x] **端到端验证**: 功能化 ledger（threshold = N）注册后，从 `AgentMessageRequest` 到 `ReActAgentExecutor.execute()` 到 session 被 threshold-pause 中止的完整路径已验证——两种暂停入口路径均覆盖：(A) 单次迭代多 tool call deny → dispatch for-loop `break`；(B) 跨迭代单 tool call deny → 迭代开始 `isPaused` 检查 → `break reactLoop`（Minimum Rules #22 Anti-Hollow Rule）
- [x] **接线验证**: 端到端测试中 `denialLedger.recordDenial(...)` 的调用已被断言验证（CountingLedger 计数器或 verify），而非仅组件被传递（Minimum Rules #23 Wiring Verification Rule）
- [x] **无静默跳过**: deny 记录路径在 `NoOpDenialLedger` 默认下不产生暂停（ledger 不计数），在功能化实现下产生真实计数 + 暂停——非空方法体/continue 跳过/吞异常（Minimum Rules #24）。暂停路径有审计日志 + 事件 + 状态标记，非静默忽略
- [x] **新增功能测试**: dispatch-path 集成测试（deny 记录 + 阈值达到 → 暂停 + dispatch loop 中止 + ReAct 停止 — 场景 A + B）+ 接线验证测试 + 向后兼容测试——全部通过（Minimum Rules #25）
- [x] **向后兼容**: 不设 ledger（使用 `NoOpDenialLedger` 默认）的引擎执行全部现有测试通过，0 spurious 暂停
- [x] `AgentExecStatus.paused` 新增不破坏现有序列化/反序列化（现有测试覆盖；plan 157 先例：dict 引用对新值向后兼容）
- [x] `IApprovalGate.java` Javadoc 更新（IDenialLedger 不再标注为 deferred）
- [x] 设计文档 §6.2 更新：L3-6 标记为已落地 + 六个架构决策记录
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过（全部新增 + 现有测试）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `IDenialLedger` 接口 + `DenialRecord` 值类型 + `DenialRecordOutcome` 值类型 + `NoOpDenialLedger` 默认实现存在，位于 security 包
- [x] dispatch loop 中每个 deny 路径（Layer 1/2/3 共 5 个 deny 点）均通过 `handleDenialAndCheckThreshold` 向 ledger 记录拒绝
- [x] `AgentExecStatus.paused` 新增；post-loop bookkeeping 排除 `paused`（不发布 `EXECUTION_COMPLETED`）
- [x] `AgentEventType.SESSION_PAUSED` 新增
- [x] 阈值暂停机制从 `recordDenial` → 阈值检查 → dispatch loop 中止（break for-loop）→ post-loop 检查（跳过 `allowedCalls` 执行，不 break reactLoop）→ 下一轮迭代开始 `isPaused` 检查（`break reactLoop`）→ ReAct 循环停止 → session 状态标记完整接通，经端到端测试验证（两种入口路径：场景 A + B）
- [x] ReAct 循环迭代开始时的暂停状态检查存在（`cancelRequested` 之后、`shouldForceStop` 之前），防止暂停 session 在下一轮迭代恢复执行
- [x] post-loop bookkeeping（`:759-765`）正确排除 `paused` 状态——暂停 session 不发布 `EXECUTION_COMPLETED` / 不执行 `POST_CALL` hooks
- [x] 功能化 ledger 在端到端测试中实际记录拒绝 + 达到阈值后实际暂停 session（非空壳）
- [x] `NoOpDenialLedger` 默认向后兼容，现有全部测试通过
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs（设计 §6.2）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**: closure audit 验证 (a) `handleDenialAndCheckThreshold` → `ledger.recordDenial` 在 dispatch loop 的全部 5 个 deny 路径中确实被调用（不只是组件被传递），(b) 阈值暂停路径有审计日志 + `SESSION_PAUSED` 事件 + `AgentExecStatus.paused` 状态标记 + dispatch for-loop `break` + post-loop 跳过 `allowedCalls`（不 break reactLoop）+ 下一轮迭代 `isPaused` 检查 `break reactLoop`（非静默跳过），(c) 端到端路径从 request 到 session-paused 完整走通——场景 A（单次迭代多 call deny）+ 场景 B（跨迭代单 call deny → 迭代开始 isPaused 检查）均验证，(d) post-loop bookkeeping 不对 paused session 发布 `EXECUTION_COMPLETED`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### DBDenialLedger（DB 持久化实现 + ORM 模型 + DAO）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `DBDenialLedger` 需要新增 ORM 实体模型（denial 记录表）+ DAO/store + Nop ORM 集成，参照 plan 171（DB-backed messenger）的独立 plan 模式。本计划交付契约 + `NoOpDenialLedger` 默认 + dispatch-path 集成，在测试中用内存计数实现验证链路非空壳。接口契约不强制持久化——`NoOpDenialLedger` 不持久化在设计上合法（L3-G5 收窄为：持久化是功能化实现的职责，非接口契约的硬性要求）。跨 session 恢复的拒绝计数持久化在 successor plan 中解决。
- Successor Required: yes
- Successor Path: 待定（`DBDenialLedger` 独立计划，参照 plan 171 DB-backed messenger 模式）

### Sticky pause 人类干预恢复协议

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计 §6.2 规定 `pauseBehavior = sticky`（暂停后只有人类干预才能恢复）。本计划实现阈值暂停 + session 状态标记（`AgentExecStatus.paused`）。"只有人类干预才能恢复"的完整 sticky 语义需要功能化 ledger + session 恢复协议（`IDenialLedger.reset` 的调用方和调用时机）+ 可能的审批通道集成。归类为 `DBDenialLedger` successor 或独立 governance plan 的职责。
- Successor Required: yes
- Successor Path: 与 `DBDenialLedger` successor 合并或独立 governance plan

### Fingerprint 计算（"same intent" 标识）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计 §6.2 将 `SHA-256(actionKind + argv + cwd + criticalEnv)[:32]` fingerprint 列为 IDenialLedger 的关键设计元素。但 L3-6 的核心机制是 per-session **总拒绝计数** + 阈值暂停（denialThreshold = 3）；fingerprint 匹配更核心地服务于 L3-7（`IPostDenialGuard` 的"盲重试相同操作"检测——`DenialResult.actionFingerprint`）。fingerprint 基础设施在 L3-7 落地时引入，届时 `DenialRecord` 可扩展携带 fingerprint 字段。
- Successor Required: yes
- Successor Path: L3-7 (`IPostDenialGuard`) 独立计划

## Non-Blocking Follow-ups

- `DBDenialLedger` shipped 实现（DB 持久化 + ORM 模型 + DAO，参照 plan 171 模式）
- `IPostDenialGuard` (L3-7) + `DenialResult` 结构化信封——审批治理链的盲重试阻止
- Fingerprint 基础设施（SHA-256 based "same intent" 标识）——L3-7 落地时引入
- Sticky pause 恢复协议（`IDenialLedger.reset` 的调用方/时机 + 可能的审批通道集成）
- denial-config XDSL（§9 Layer 3 渐进式增强）——threshold 通过功能化实现构造器注入
- 配置校验（"denial threshold 配置存在但 ledger 仍为默认时警告"）——需 XDSL 配置
- 拒绝记录的 DB 持久化审计查询接口

## Closure

Status Note: L3-6 `IDenialLedger` 契约表面 + `DenialRecord` / `DenialRecordOutcome` / `DenialLayerSource` 值类型 + `NoOpDenialLedger` 默认实现 + dispatch-path 集成（全部 5 个 deny 路径）+ 阈值暂停双重机制（dispatch-loop break + iteration-start isPaused break reactLoop）+ post-loop bookkeeping 排除 paused + `AgentExecStatus.paused` + `AgentEventType.SESSION_PAUSED` 已全部落地。端到端测试覆盖两种暂停入口路径（场景 A + B）。向后兼容由 `NoOpDenialLedger` shipped 默认保证（1094/1094 测试通过）。`DBDenialLedger`（DB 持久化）、sticky pause 恢复协议、fingerprint 基础设施均为已裁定的 deferred successor。
Completed: 2026-06-14

Closure Audit Evidence:

- Reviewer / Agent: Independent closure auditor (subagent task_id ses_13ac6d10cffeQhsOw8sipsBeh8, separate fresh session)
- Audit Session: Live code inspection + `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C -Dtest=TestNoOpDenialLedger,TestDispatchPathDenialLedger`
- Evidence:
  - **Contract surface**: `IDenialLedger.java`, `DenialRecord.java`, `DenialRecordOutcome.java`, `DenialLayerSource.java` (5 enum values), `NoOpDenialLedger.java` all present in `io.nop.ai.agent.security` with correct contracts — PASS
  - **Engine wiring**: `DefaultAgentEngine.java:91/391-392/399-401/778` (field + null-fallback setter + getter + builder pass); `ReActAgentExecutor.java:134/157/188-190/386-387/902` (field + ctor + default + Builder + helper) — PASS
  - **Dispatch-loop integration (Anti-Hollow)**: `handleDenialAndCheckThreshold` invoked at `ReActAgentExecutor.java:638, :661, :676, :700, :722` — all 5 deny paths (L1 tool/L1 perm/L1 path/L2 policy/L3 approval), each with correct `DenialLayerSource` and `break dispatchLoop` on threshold — PASS
  - **Threshold-pause chain**: audit (`:914-916`, matchedRule `layer3_denial_ledger`) + SESSION_PAUSED event (`:922`) + `ctx.setStatus(paused)` (`:913`) + `break dispatchLoop` + post-dispatch `continue reactLoop` (`:744`, not break) + iteration-start `isPaused` → `break reactLoop` (`:472-474`) — PASS
  - **Post-loop bookkeeping**: `:845-848` excludes `paused` from POST_CALL/EXECUTION_COMPLETED — PASS
  - **Enums**: `AgentExecStatus.paused` (`:27`), `AgentEventType.SESSION_PAUSED` (`:42`) — PASS
  - **Tests**: `TestNoOpDenialLedger` (22 tests) + `TestDispatchPathDenialLedger` (8 tests: Scenario A + B + wiring + post-loop + backward-compat + builder default + setter/getter null-fallback + per-session independence) — 30/30 pass, 0 failures, 0 errors — PASS
  - **Docs**: `IApprovalGate.java:13` marks IDenialLedger as "landed"; design `nop-ai-agent-security-and-permissions.md:364` status line + `:380-385` 6 architecture decisions — PASS
  - **Backward compat**: NoOp default verified non-pausing in dedicated test; 1094/1094 existing tests pass — PASS
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0
  - Anti-Hollow 检查结果: recordDenial 在 dispatch loop 全部 5 个 deny 路径中确实被调用（:638/:661/:676/:700/:722 → :909）；阈值暂停路径完整（审计+事件+状态+break dispatchLoop+continue reactLoop+break reactLoop）；端到端路径场景 A+B 均测试通过；post-loop bookkeeping 正确排除 paused — PASS
  - Deferred 项分类检查: DBDenialLedger / sticky pause / fingerprint 均为 `out-of-scope improvement`，附带 non-blocking 理由，无 in-scope live defect 被降级 — PASS

Follow-up:

- `DBDenialLedger` shipped 实现（DB 持久化 + ORM 模型 + DAO，参照 plan 171 模式）
- `IPostDenialGuard` (L3-7) + `DenialResult` 结构化信封——审批治理链的盲重试阻止
- Fingerprint 基础设施（SHA-256 based "same intent" 标识）——L3-7 落地时引入
- Sticky pause 恢复协议（`IDenialLedger.reset` 的调用方/时机 + 可能的审批通道集成）
- denial-config XDSL（§9 Layer 3 渐进式增强）——threshold 通过功能化实现构造器注入
- 配置校验（"denial threshold 配置存在但 ledger 仍为默认时警告"）——需 XDSL 配置
- 拒绝记录的 DB 持久化审计查询接口

## Follow-up handled by 178-nop-ai-agent-post-denial-guard.md

The `IPostDenialGuard` (L3-7) + `DenialResult` structured envelope + SHA-256 fingerprint infrastructure carry-over from this plan's `Deferred But Adjudicated` (Fingerprint 计算 — `Successor Required: yes, Successor Path: L3-7 独立计划`) and `Non-Blocking Follow-ups` sections is handled by plan 178 (`ai-dev/plans/178-nop-ai-agent-post-denial-guard.md`). Plan 178 delivers the post-denial-guard contract surface + `DenialResult` envelope + `ActionFingerprint` computation + `PassThroughPostDenialGuard` pass-through default + `FingerprintPostDenialGuard` functional implementation + dispatch-path consultation (pre-Layer-1 blind-retry check + deny-path fingerprint recording). `DBDenialLedger` (DB-backed persistence) and sticky-pause recovery remain deferred to successor plans.

## Follow-up handled by 179-nop-ai-agent-db-denial-ledger.md

The `DBDenialLedger`（DB 持久化实现 + ORM 模型 + DAO）carry-over from this plan's `Deferred But Adjudicated` (DBDenialLedger — `Successor Required: yes, Successor Path: 待定`) and `Non-Blocking Follow-ups` sections is handled by plan 179 (`ai-dev/plans/179-nop-ai-agent-db-denial-ledger.md`). Plan 179 delivers the `ai_agent_denial` ORM entity + table constants + `DBDenialLedger implements IDenialLedger` (raw JDBC, per-session COUNT/INSERT/DELETE, threshold-pause persisted across ledger instance recreation) + dispatch-path wiring verification (recordDenial writes to DB) + design §6.2 update. It follows the plan 171 (`DBMessageService`) DB-backed implementation pattern. `NoOpDenialLedger` remains the shipped default. Sticky-pause recovery protocol remains a separate deferred successor (depends on DBDenialLedger but is an independent functional surface).
