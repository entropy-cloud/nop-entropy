# 176 nop-ai-agent IApprovalGate + AutoApproveGate (L3-5)

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L3-5

> Last Reviewed: 2026-06-14
> Source: Carry-over from plan 175 (Non-Blocking Follow-ups: "L3-5（`IApprovalGate`）：SecurityLevel 的下游消费者，需审批等级触发人类审批"). Also carried-over from plans 172/173 which deferred the downstream enforcement chain. Now unblocked: Layer 2 consultation fully landed (plan 175 ✅ — `checkLayer2Consultation` resolves `SecurityLevel` + consults `IPermissionMatrix` in the dispatch loop). Dependency L1-6 (`IPermissionProvider`) ✅ satisfied. Roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 3 row L3-5; design `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §6.1 (IApprovalGate) + §8 (defense-in-depth chain — gate sits after `ISecurityLevelResolver`, before `IDenialLedger`/`IPostDenialGuard`/`ISandboxBackend`)
> Related: Plan 175 (Layer 2 dispatch-path consultation — direct predecessor; this plan is its Non-Blocking Follow-up L3-5), Plan 173 (L2-13 `ISecurityLevelResolver` + `SecurityLevel` + `LevelHints`), Plan 172 (L2-14 `IPermissionMatrix` + `MatrixDecision` + shared value types), Plan 144 (audit logger — deny-path audit precedent)

## Purpose

将 Layer 3 审批门（`IApprovalGate`）的契约表面 + pass-through 默认 + dispatch-path 咨询点落地，使 plans 173/175 交付的 `SecurityLevel` 分级在生产环境可被执行拦截。当前 dispatch path 在 Layer 2 咨询中解析出 `SecurityLevel`（STANDARD/ELEVATED/RESTRICTED）并据此 allow/deny（matrix 咨询），但 **matrix 放行后没有任何组件对 ELEVATED/RESTRICTED 等级采取行动**——没有审批被请求，没有拒绝被记录。纵深防御链（设计 §8）在 Layer 2 之后断开。本计划接续这条链：使 dispatch path 在 matrix 放行后、工具实际执行前，咨询审批门；功能化 gate 实现注册后，ELEVATED/RESTRICTED 操作可被人类审批或拒绝。`AutoApproveGate` 默认使行为与接线前完全一致（无人值守自动化 Layer 1 基线）。

## Current Baseline

- **Layer 2 consultation ✅ delivered (plan 175)**: `ReActAgentExecutor.checkLayer2Consultation`（:969-997）在 dispatch loop 中调用 `levelHintsProducer.produce` → `securityLevelResolver.resolve` → `permissionMatrix.check`。deny 路径记录 `AuditEvent`（DENY + reason + matched rule `layer2_permission_matrix`）+ 发布 `TOOL_CALL_DENIED` 事件 + 返回 `ChatToolResponseMessage.error(...)` + `continue`（跳过该 tool call）。consultation 点位于 Layer 1 检查之后、`allowedCalls.add(chatToolCall)`（:626）之前
- **`checkLayer2Consultation` 返回值**: `null`（放行）或 reason `String`（拒绝）。解析出的 `SecurityLevel` 是方法局部变量（:975），当前不向上层暴露——Layer 3 咨询需要该 level（设计 §6.1: "当 ISecurityLevelResolver 返回需要审批的等级时"）
- **dispatch loop 结构** (`ReActAgentExecutor.execute()` :554-627): 对每个 `chatToolCall` 依次执行 Layer 1（toolAccessChecker :563 → permissionProvider :581 → pathAccessChecker :599）→ Layer 2（checkLayer2Consultation :615）→ `allowedCalls.add` :626。deny 路径统一模式：`ChatToolResponseMessage.error(...)` + `ctx.addMessage(...)` + `continue`
- **`SecurityLevel` 已存在**: 枚举 {STANDARD（正常执行）/ ELEVATED（需确认）/ RESTRICTED（需审批）}，注释明确 ELEVATED = "Requires confirmation"、RESTRICTED = "Requires approval"
- **`MatrixDecision` 已存在**: 不可变值对象（allowed + reason + channel + level），`allow()` / `deny(reason)` / `deny(channel, level, reason)` 工厂，`isAllowed()` / `isDenied()` 谓词
- **`AuditEvent` + `IAuditLogger` + `AuditDecision` 已存在**: `AuditEvent`（sessionId + agentName + actorId + toolName + decision + reason + matchedRule + path + timestamp）；`AuditDecision` {ALLOW, DENY}；dispatch loop 中每个检查点都记录审计事件。`Slf4jAuditLogger` 是 shipped 默认
- **`AgentExecutionContext` 已携带 channelKind/principal (plan 175 Phase 1)**: mutable 字段 + getter/setter，`doExecute` 从 `AgentMessageRequest` 传播
- **引擎接线模式**: Layer 2+ 组件通过 mutable field + setter + getter 接线（field 声明处初始化为 pass-through 默认），`ReActAgentExecutor` 通过 Builder 接收。`DefaultAgentEngine.resolveExecutor`（:714-716）将 resolver/matrix/hintsProducer 传递给 Builder。Layer 3 组件遵循同一模式
- **设计契约 §6.1**: `IApprovalGate` 行为——(1) policy 不需要审批 → 直接 ALLOW；(2) 入队审批请求；(3) 等待人类响应（带超时，默认 300s）；(4) 超时或拒绝 → DenialResult。审批通道（Web UI / GraphQL Subscription / RPC 轮询）散文列出。默认 = `AutoApproveGate`（所有请求自动通过）
- **设计 §8 纵深防御链**: `ISecurityLevelResolver` → **`IApprovalGate`（人类审批）** → `IDenialLedger`（拒绝计数）→ `IPostDenialGuard`（盲重试阻止）→ `ISandboxBackend`（隔离执行）。gate 是 Layer 3 链的入口
- **glossary**: `ApprovalDecision` = Layer 3 数据结构 "审批决策（含决策结果、审批人、原因）"
- **`DenialResult` 结构化信封 (§6.3)**: reason 枚举（human_rejected / threshold_exceeded / repeated_same_intent）+ suggestedNextStep + actionFingerprint + message + retryable。**归属于 L3-7（IPostDenialGuard）**，不在本计划范围
- **审计就绪分析发现 (audit-readiness-analysis §2.3)**: L3-G1（DenialResult.reason 枚举缺少 `timeout`）——审批超时 vs 人类拒绝需可区分；L3-G2（approve 动作无标准审计事件格式）；L3-G3（审批通道未抽象为接口）；L3-G4（AutoApproveGate 与审批配置共存时无配置校验）。这些是 analysis 层面的发现，非阻塞当前契约落地
- **L3-5/L3-6/L3-7 类型在代码中均不存在**: grep `IApprovalGate|AutoApproveGate|ApprovalDecision|DenialResult|IDenialLedger|IPostDenialGuard` in `nop-ai/` → 0 hits（verified）
- **roadmap 依赖 L1-6 ✅**: `IPermissionProvider` 已交付（plan 139）

## Goals

- `IApprovalGate` 契约表面 + `ApprovalDecision` 值类型（审批决策结果 + 审批人 + 原因）位于 `io.nop.ai.agent.security` 包，遵循 `MatrixDecision` / `ToolAccessResult` 的 deny-with-reason 值对象模式。`ApprovalDecision` 的拒绝原因区分"人类拒绝"与"超时"（收窄审计发现 L3-G1 到审批门自身语义）
- `AutoApproveGate` pass-through 默认（所有请求自动通过），作为 shipped 默认注入引擎——无人值守自动化 Layer 1 基线
- dispatch-path 咨询接通：`ReActAgentExecutor` dispatch loop 在 Layer 2 matrix 放行（`checkLayer2Consultation` 返回 null）之后、工具实际执行（`allowedCalls.add`）之前，使用解析出的 `SecurityLevel` + tool-call 上下文咨询 `IApprovalGate`。deny 时记录审计（DENY + reason + matched rule `layer3_approval_gate`）+ 发布事件 + 返回 error response（与 Layer 1/2 deny 路径一致）；approve 时继续执行
- 端到端验证：注册功能化 gate（测试内部类，对 ELEVATED/RESTRICTED 返回 deny）后，dispatch path 实际拒绝对应等级的工具调用（非仅接口存在）；使用 `AutoApproveGate` 默认时行为与接线前完全一致（0 spurious 拒绝，全部现有测试通过）
- 设计文档 §6.1 更新：标记 L3-5 契约已落地；记录架构决策（ApprovalDecision vs DenialResult 边界、consultation 点位置、向后兼容策略）
- 单元测试覆盖：AutoApproveGate 契约、ApprovalDecision 值语义、consultation 点（deny 阻止 + approve 放行 + 审计记录）、接线验证（gate 确实在 dispatch loop 被调用）、向后兼容

## Non-Goals

- **`IDenialLedger` (L3-6) 和 `IPostDenialGuard` (L3-7)**: 审批治理链的拒绝计数和盲重试阻止。它们是独立后续工作项；`DenialResult` 结构化信封（§6.3）归属于 L3-7。本计划的 `ApprovalDecision` 仅覆盖审批门自身的决策返回，不引入 `DenialResult`
- **功能化审批通道抽象（`IApprovalChannel`，审计 L3-G3/R5）**: 审批通道（Web UI / GraphQL / RPC）抽象为可插拔接口不在本计划范围。`AutoApproveGate` 是唯一 shipped 实现，不需要外部审批通道
- **真实人类审批流**: 不实现带超时的异步审批等待（设计 §6.1 步骤 2-4 的入队/等待/超时）。这些是功能化 gate 实现的职责，`AutoApproveGate` 不涉及。本计划交付契约 + 默认 + 咨询接线，功能化 gate 在测试中验证链路非空壳
- **Approval-config XDSL（§9 Layer 3 渐进式增强）**: 不引入 `approval-config.xml` 或 Delta 配置
- **DB 持久化审批状态**: 不持久化审批决策到数据库（单进程内存足够）
- **AutoApproveGate 配置校验（审计 L3-G4/R10）**: "审批配置存在但 gate 仍为默认实现时警告"需要 XDSL 配置，不在本计划范围。归类为 non-blocking follow-up
- **`ApprovalDecision` 的完整拒绝原因枚举穷举**: 仅区分"人类拒绝 / 超时 / 其他"覆盖审批门核心语义。完整枚举（threshold_exceeded / repeated_same_intent 等 L3-6/L3-7 场景）在后续工作项中扩展

## Scope

### In Scope

- `IApprovalGate` 接口 + `ApprovalDecision` 值类型（`io.nop.ai.agent.security` 包）
- `AutoApproveGate` 默认实现
- `DefaultAgentEngine` 对 `IApprovalGate` 的 mutable field + setter + getter（默认 = `AutoApproveGate`）
- `ReActAgentExecutor.Builder` 接收 `IApprovalGate`
- `DefaultAgentEngine.resolveExecutor` 传递 `IApprovalGate` 给 Builder
- dispatch loop 中 Layer 3 咨询点（Layer 2 matrix 放行之后、`allowedCalls.add` 之前），deny 路径与 Layer 1/2 一致
- 单元测试：AutoApproveGate 契约 + ApprovalDecision 值语义 + consultation 点（deny/approve/审计）+ 接线验证 + 向后兼容
- 端到端测试：功能化 gate（测试内部类）在 dispatch path 中实际 deny ELEVATED/RESTRICTED 工具调用
- 设计文档 §6.1 更新

### Out Of Scope

- `IDenialLedger` (L3-6)、`IPostDenialGuard` (L3-7)、`DenialResult` 信封
- `IApprovalChannel` 审批通道抽象（L3-G3）
- 真实人类审批流（异步等待 + 超时 + 多通道）
- Approval-config XDSL
- DB 持久化审批状态
- AutoApproveGate 配置校验（L3-G4）

## Execution Plan

### Phase 1 - IApprovalGate 契约 + ApprovalDecision + AutoApproveGate + 单元测试

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/` (IApprovalGate, ApprovalDecision, AutoApproveGate), `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/security/`

- Item Types: `Decision | Proof`

- [x] 定义 `ApprovalDecision` 值类型（security 包），遵循 `MatrixDecision` / `ToolAccessResult` 的不可变 deny-with-reason 模式。决策结果区分 approved / denied；denial 携带可审计的 reason；reason 区分"人类拒绝（human_rejected）"与"超时（timeout）"（收窄审计发现 L3-G1 到审批门自身语义）。approved 决策携带审批人标识（`AutoApproveGate` 的审批人 = "auto"/system）
- [x] 定义 `IApprovalGate` 接口（security 包），契约语义：给定 `SecurityLevel` + tool-call 上下文（toolName、channel、principal、sessionId、agentName），返回 `ApprovalDecision`。接口遵循 security 包中 `IPermissionMatrix` / `ISecurityLevelResolver` 的契约风格（Javadoc 说明 default 实现和 dispatch-path 咨询语义）
- [x] 创建 `AutoApproveGate` 默认实现（security 包）：对所有输入返回 approved（审批人 = "auto"）。作为 shipped 默认，使无人值守自动化不受影响
- [x] 单元测试验证 `AutoApproveGate` 契约：对 STANDARD / ELEVATED / RESTRICTED 三个等级均返回 approved；审批人标识为 "auto"/system；对 null channel / null principal 不抛异常（返回 approved）
- [x] 单元测试验证 `ApprovalDecision` 值语义：approved 与 denied 的谓词正确；denial reason 的 human_rejected / timeout 区分可读；equals/hashCode 一致性

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `IApprovalGate` 接口 + `ApprovalDecision` 值类型 + `AutoApproveGate` 默认实现存在，位于 `io.nop.ai.agent.security` 包
- [x] **无静默跳过**: `AutoApproveGate` 对所有输入返回 approved（语义正确的 pass-through），非空方法体/吞异常；`ApprovalDecision` 的 denial reason 显式区分 human_rejected / timeout（非模糊合并）
- [x] **新增功能测试**: `AutoApproveGate` 契约测试（3 等级 + null 输入）+ `ApprovalDecision` 值语义测试——全部通过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过（新增 + 现有测试）
- [x] No owner-doc update required（设计文档更新在 Phase 2）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 引擎/Executor 接线 + Dispatch-loop 咨询点 + 端到端验证 + 设计文档

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java` (field + setter + getter + resolveExecutor), `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java` (Builder + dispatch loop consultation), `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/` 或 `security/`, `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §6.1

- Item Types: `Fix | Decision | Proof | Follow-up`

- [x] 向 `DefaultAgentEngine` 添加 `IApprovalGate` 的 mutable field + setter + getter（field 声明处初始化为 `AutoApproveGate`，遵循 `permissionMatrix` / `securityLevelResolver` 接线模式）。setter 对 null 输入回退默认（非静默忽略）
- [x] 向 `ReActAgentExecutor.Builder` 添加 `IApprovalGate` 接收方法。Builder build() 中对 null 输入设置 `AutoApproveGate` 默认，遵循现有 Builder 模式
- [x] `DefaultAgentEngine.resolveExecutor(...)` 在构建 `ReActAgentExecutor` 时，将引擎自身的 `IApprovalGate` 传递给 Builder
- [x] 在 `ReActAgentExecutor.execute()` dispatch loop 中（Layer 2 `checkLayer2Consultation` 放行之后、`allowedCalls.add(chatToolCall)` 之前），插入 Layer 3 审批咨询步骤：
  1. 获取已解析的 `SecurityLevel`（从 Layer 2 步骤复用，避免重复 resolve——重构 `checkLayer2Consultation` 返回 `SecurityConsultationOutcome` 携带 resolvedLevel + denialReason）
  2. 调用 `approvalGate.requestApproval(level, toolName, channel, principal, sessionId, agentName)` → `ApprovalDecision`
  3. 如果 denied → 记录审计日志（`AuditEvent` + `AuditDecision.DENY` + reason + matched rule `layer3_approval_gate`）、发布 `TOOL_CALL_DENIED` 事件、返回 `ChatToolResponseMessage.error(...)`、`continue`（不加入 `allowedCalls`，与 Layer 1/2 deny 路径一致）
  4. 如果 approved → 继续加入 `allowedCalls`
- [x] 端到端测试（dispatch-path approval 非空壳验证）：注册功能化 gate（测试内部类，对 ELEVATED/RESTRICTED 返回 deny、对 STANDARD 返回 approve）。构造触发 ELEVATED 的 tool call（使用功能化 `ISecurityLevelResolver` 测试内部类，如 plan 175 端到端测试所用）+ 放行 matrix → dispatch path 实际拒绝该工具调用（非仅接口存在，而是从 request 到 tool-call-blocked 的完整路径可验证）。同时验证 STANDARD level + approve gate → allow
- [x] 向后兼容测试：使用 `AutoApproveGate` 默认（不设 gate）的引擎执行 ReAct 循环，行为与接线前完全一致——全部现有测试通过，0 spurious 拒绝
- [x] **接线验证测试**: 在端到端测试中添加断言验证 `approvalGate.requestApproval(...)` 确实在 dispatch loop 中被调用（CountingGate AtomicInteger 计数器），而非仅组件被传递
- [x] 更新设计文档 `nop-ai-agent-security-and-permissions.md` §6.1：标记 L3-5 契约已从"deferred successor"变为"已落地"。记录架构决策：(1) `ApprovalDecision` 作为 `IApprovalGate` 专用返回类型，与 L3-7 的 `DenialResult` 边界清晰（gate 自身决策 vs 拒绝后治理信封）；(2) consultation 点位于 Layer 2 matrix 放行之后，deny 路径与 Layer 1/2 一致；(3) `AutoApproveGate` shipped 默认保证向后兼容；(4) denial reason 区分 human_rejected / timeout（收窄 L3-G1）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ReActAgentExecutor.Builder` 接收 `IApprovalGate`；`DefaultAgentEngine.resolveExecutor` 传递该组件；引擎 field/setter/getter 存在（默认 = `AutoApproveGate`）
- [x] **端到端验证**: 功能化 gate + 功能化 resolver 注册后，从 `AgentMessageRequest` 到 `ReActAgentExecutor.execute()` 到工具调用被 approval-gate denied 的完整路径已验证——ELEVATED + deny-gate → block，STANDARD + approve-gate → allow（Minimum Rules #22 Anti-Hollow Rule）
- [x] **接线验证**: 端到端测试中 `approvalGate.requestApproval(...)` 的调用已被断言验证（CountingGate AtomicInteger 计数器），而非仅组件被传递（Minimum Rules #23 Wiring Verification Rule）
- [x] **无静默跳过**: consultation 步骤在 `AutoApproveGate` 默认下不产生拒绝（gate 放行一切），在功能化实现下产生真实 approve/deny——非空方法体/continue 跳过/吞异常（Minimum Rules #24）。deny 路径记录审计日志并返回 error response（与 Layer 1/2 deny 一致），非静默忽略
- [x] **新增功能测试**: consultation 点测试（deny 阻止 + approve 放行 + 审计日志记录）+ 接线验证测试 + 向后兼容测试——全部通过
- [x] **向后兼容**: 不设 gate（使用 `AutoApproveGate` 默认）的引擎执行全部现有测试通过，0 spurious 拒绝
- [x] 设计文档 §6.1 更新：L3-5 标记为已落地 + 四个架构决策记录
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过（全部新增 + 现有测试）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `IApprovalGate` 接口 + `ApprovalDecision` 值类型 + `AutoApproveGate` 默认实现存在，位于 security 包
- [x] `ReActAgentExecutor` dispatch loop 包含 Layer 3 审批咨询步骤，deny 路径与 Layer 1/2 deny 一致（审计 + 事件 + error response）
- [x] 咨询链从 `DefaultAgentEngine` → `resolveExecutor` → Builder → dispatch loop 完整接通，经端到端测试验证
- [x] 功能化 gate 在端到端测试中实际 deny/approve 工具调用（非空壳）
- [x] `AutoApproveGate` 默认向后兼容，现有全部测试通过
- [x] SecurityLevel 在 Layer 2 与 Layer 3 之间不重复 resolve（或重复 resolve 有明确理由且不影响正确性）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs（设计 §6.1）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**: closure audit 验证 (a) 审批咨询链在运行时确实连通（gate.requestApproval 在 dispatch loop 中被调用），(b) deny 路径有审计日志 + error response（非静默跳过），(c) 端到端路径从 request 到 tool-call-blocked-by-approval 完整走通
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 真实人类审批流（异步等待 + 超时 + 多通道路由）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计 §6.1 步骤 2-4（入队审批请求、等待人类响应带超时、超时/拒绝 → 决策）是功能化 gate 实现的职责。`AutoApproveGate` 作为 shipped 默认不需要外部审批通道。本计划交付契约 + 默认 + 咨询接线，在测试中用功能化 gate 验证链路非空壳。真实审批流（Web UI / GraphQL Subscription / RPC 轮询通道）归类为后续功能化增强。
- Successor Required: yes
- Successor Path: 待定（功能化 `IApprovalGate` 实现 + `IApprovalChannel` 抽象）

### DenialResult 结构化信封（§6.3）+ IDenialLedger (L3-6) + IPostDenialGuard (L3-7)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `DenialResult`（reason 枚举 + suggestedNextStep + actionFingerprint + message + retryable）归属于 L3-7（IPostDenialGuard）。`IDenialLedger`（拒绝计数 + 阈值暂停）是 L3-6。本计划的 `ApprovalDecision` 仅覆盖审批门自身的决策返回，与 `DenialResult` 边界清晰。L3-6/L3-7 是独立的 roadmap 工作项。
- Successor Required: yes
- Successor Path: L3-6 / L3-7 独立计划

### AutoApproveGate 配置校验（审计 L3-G4/R10）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: "审批配置存在但 gate 仍为默认实现时启动警告/拒绝"需要 `approval-config.xml` XDSL 配置基础设施。当前不引入 XDSL 配置（Non-Goal），gate 通过 setter 程序化注入。配置校验在 XDSL 配置化之后才有意义。
- Successor Required: no

### 审批通道可插拔接口 IApprovalChannel（审计 L3-G3/R5）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 审批通道（Web UI / GraphQL / RPC）抽象为可插拔接口属于功能化审批流增强。`AutoApproveGate` 不需要外部通道。当真实人类审批流落地时，通道抽象自然引入。
- Successor Required: yes
- Successor Path: 与"真实人类审批流"successor 合并

## Non-Blocking Follow-ups

- 功能化 `IApprovalGate` shipped 实现（带超时的人类审批流，替代 `AutoApproveGate` 默认）
- `IApprovalChannel` 审批通道抽象（L3-G3/R5）
- `IDenialLedger` (L3-6) + `IPostDenialGuard` (L3-7) + `DenialResult` 信封——审批治理链的拒绝计数和盲重试阻止
- AutoApproveGate 配置校验（L3-G4/R10）——需 `approval-config.xml` XDSL
- Approval-config XDSL（§9 Layer 3 渐进式增强）
- 审批决策的 DB 持久化（审计查询）

## Closure

Status Note: L3-5 契约表面（IApprovalGate + ApprovalDecision + ApprovalDenialKind）+ AutoApproveGate shipped 默认 + dispatch-path Layer 3 咨询点已完整落地。纵深防御链（设计 §8）在 Layer 2 之后不再断开：matrix 放行后、工具执行前，gate 被咨询；功能化 gate 注册后 ELEVATED/RESTRICTED 操作可被 deny，deny 路径与 Layer 1/2 一致（审计 + 事件 + error response）。AutoApproveGate 默认使行为与接线前完全一致（0 spurious 拒绝）。独立 closure audit 验证全部 15 项检查 PASS。
Completed: 2026-06-14

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，task_id: ses_13b13507bffeyGYMeR29U46cB8，explore 类型）
- Audit Session: ses_13b13507bffeyGYMeR29U46cB8
- Evidence:
  - Phase 1 Exit Criteria — PASS:
    - `ApprovalDenialKind.java`（security 包，HUMAN_REJECTED/TIMEOUT/OTHER）: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/ApprovalDenialKind.java:22-26`
    - `ApprovalDecision.java`（不可变，factories + predicates + equals/hashCode）: 同目录 `ApprovalDecision.java`
    - `IApprovalGate.java`（`requestApproval(SecurityLevel, String, ChannelKind, Principal, String, String) → ApprovalDecision`）: 同目录 `IApprovalGate.java:49-51`
    - `AutoApproveGate.java`（singleton，`approve("auto")` for all inputs）: 同目录 `AutoApproveGate.java`
    - 单元测试：`TestAutoApproveGate`（7 tests）+ `TestApprovalDecision`（18 tests）全部通过
  - Phase 2 Exit Criteria — PASS:
    - `DefaultAgentEngine` field/setter/getter（默认 = AutoApproveGate）: `DefaultAgentEngine.java:87,360-362,368-370`
    - `ReActAgentExecutor.Builder.approvalGate(...)` + 构造器 null-default: `ReActAgentExecutor.java:364-367,178-180`
    - `resolveExecutor` 传递 `.approvalGate(approvalGate)`: `DefaultAgentEngine.java:746`
    - dispatch loop Layer 3 咨询步骤（Layer 2 allow 之后、`allowedCalls.add` 之前）: `ReActAgentExecutor.java:630-666`
    - deny 路径 AuditEvent(matchedRule="layer3_approval_gate") + TOOL_CALL_DENIED + error response: `ReActAgentExecutor.java:1064-1089`
    - `checkLayer2Consultation` 重构为 `SecurityConsultationOutcome`（携带 resolvedLevel，Layer 3 复用不重复 resolve）: `ReActAgentExecutor.java:1015,1515-1543`
    - 端到端 + 接线 + 向后兼容测试：`TestDispatchPathApprovalGate`（7 tests）全部通过
  - Closure Gates — 全部 PASS（逐条核对见上）
  - Anti-Hollow 检查结果：(a) CountingGate.approvalCount AtomicInteger 断言 `requestApproval` 在 dispatch loop 中被调用（`TestDispatchPathApprovalGate:237,264,289`）；(b) CollectingAuditLogger 断言 `layer3_approval_gate` matched rule 出现（`:323`）；(c) 端到端 `engine.execute(req)` → "Approval denied" message（`:242-243`）；(d) AutoApproveGate 默认 → `assertFalse(containsMessage(result, "Approval denied"))`（`:353`）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/176-nop-ai-agent-approval-gate.md --strict` 退出码 0
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0（0 critical / 0 high / 0 medium / 0 low）
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → 1064 tests, 0 failures, 0 errors（独立重跑确认）
  - Deferred 项分类检查：4 个 deferred 项均为 `out-of-scope improvement`（真实人类审批流 / DenialResult+L3-6+L3-7 / AutoApproveGate 配置校验 / IApprovalChannel），无 in-scope live defect 被降级

Follow-up:

- 功能化 `IApprovalGate` shipped 实现（带超时的人类审批流，替代 `AutoApproveGate` 默认）
- `IApprovalChannel` 审批通道抽象（L3-G3/R5）
- ~~`IDenialLedger` (L3-6) + `IPostDenialGuard` (L3-7) + `DenialResult` 信封——审批治理链的拒绝计数和盲重试阻止~~ → **L3-6 handled by plan 177** (see below); L3-7 + DenialResult remain deferred
- AutoApproveGate 配置校验（L3-G4/R10）——需 `approval-config.xml` XDSL
- Approval-config XDSL（§9 Layer 3 渐进式增强）
- 审批决策的 DB 持久化（审计查询）

## Follow-up handled by 177-nop-ai-agent-denial-ledger.md

The `IDenialLedger` (L3-6) carry-over from this plan's `Deferred But Adjudicated` and `Non-Blocking Follow-ups` sections is handled by plan 177 (`ai-dev/plans/177-nop-ai-agent-denial-ledger.md`). Plan 177 delivers the denial-ledger contract + `NoOpDenialLedger` default + dispatch-loop wiring (denial recording at each Layer 1/2/3 deny path + threshold-pause). `DBDenialLedger` (DB-backed persistence) and `IPostDenialGuard` (L3-7) + `DenialResult` envelope remain deferred to successor plans.
