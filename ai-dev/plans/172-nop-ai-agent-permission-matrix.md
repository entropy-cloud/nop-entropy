# 172 nop-ai-agent Permission Matrix (L2-14)

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L2-14

> Last Reviewed: 2026-06-14
> Source: roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 2 L2-14; design `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §5.3 (IPermissionMatrix — 通道权限矩阵); plan 170 Deferred "Per-agent glob path-rules" (Successor Required: yes, explicitly classified as "Layer 2 / `IPermissionMatrix` L2-14 territory")
> Related: Plan 134 (L1-6 `IPermissionProvider` + `DefaultPermissionProvider` — the Layer-1 dependency), Plan 139 (L1-7 `IToolAccessChecker`), Plan 141 (L1-8 `IPathAccessChecker`), Plan 170 (path-permission inheritance — deferred per-agent glob path-rules to L2-14 territory)

## Purpose

交付 Layer 2 策略扩展层的 `IPermissionMatrix` 接口 + `PassThroughPermissionMatrix` pass-through 默认实现及其契约所需的值类型（`SecurityLevel`、`ChannelKind`、`Principal`），并在 `DefaultAgentEngine` 上完成 setter 接线（默认 = PassThrough）。这使得通道 × 安全等级的权限矩阵策略有一个可测试、可替换的契约表面，供后续 L2-13（`ISecurityLevelResolver` — SecurityLevel 生产者）、L3-5（`IApprovalGate`）、L3-7（`IPostDenialGuard`）以及 plan 170 遗留的 per-agent 路径规则模型消费。

本计划只交付**契约表面 + pass-through 默认 + 引擎接线**，不交付 dispatch-path 的实际咨询调用（consultation），因为咨询需要 `SecurityLevel` 输入，而其生产者 `ISecurityLevelResolver`（L2-13）尚未实现。

## Current Baseline

- **L1-6 ✅ delivered**: `IPermissionProvider`（`resolve(toolName, agentName, sessionId) → Permission`）+ `DefaultPermissionProvider` + `AllowAllPermissionProvider` 已交付，位于 `io.nop.ai.agent.security` 包
- **L1-7 ✅ / L1-8 ✅ delivered**: `IToolAccessChecker`（→ `ToolAccessResult`）+ `IPathAccessChecker`（→ `PathAccessResult`）已交付，deny/allow 决策均携带可审计的 reason。这两个是 `IPermissionMatrix` 在纵深防御链（设计 §8）中的同层邻居
- **Security 包结构** (`io.nop.ai.agent.security`): `IPermissionProvider`, `IToolAccessChecker`, `IPathAccessChecker`, `IAuditLogger`, `IContentTrustEvaluator`, `ContentOrigin`(enum), `Permission`(value), `ToolAccessResult`, `PathAccessResult`, `AuditDecision`, `AuditEvent`, `ParentPermissionConstraint` + 各自的 Default/AllowAll/NoOp 实现 + `ParentConstrained*` wrapper（plan 169/170）
- **Layer 2 接线先例**: `IContentGuardrail` + `NoOpContentGuardrail`（`guardrail` 包）、`IModelRouter` + `PassThroughModelRouter`（`router` 包）、`ITalent`（`talent` 包）、`ISkillProvider`/`ISkillCurator`（`skill` 包）、`IAgentMessenger`/`NoOpAgentMessenger`（`message` 包）均已交付
- **引擎接线模式**: `DefaultAgentEngine` 对 Layer 2+ 组件使用 setter-based mutable field + getter 模式（`setSkillProvider`, `setSkillCurator`, `setToolCallRepairer`, `setMessenger`），默认在字段声明处初始化为 pass-through/noOp，setter 做 null-check + 默认回退，不修改构造器链。Javadoc 固定句式："Composition via this setter — no constructor chain change. Default is ... so engine behaviour is unchanged unless ... is explicitly registered."
- **`SecurityLevel` 不存在**: grep 确认 `nop-ai` 模块中无 `SecurityLevel` 类型——它是 L2-13（`ISecurityLevelResolver`）的设计产物（设计 §5.1），但 L2-13 未实现，枚举尚未创建
- **`ChannelKind` 不存在**: grep 确认无此类型。设计 §5.3 定义了通道类型 webui/api/dm/group
- **`IPermissionMatrix` 不存在**: grep 确认 `nop-ai` 模块中无 `IPermissionMatrix` 或 `PassThroughPermissionMatrix`
- **`AgentExecutionContext` 无通道/身份字段**: 当前字段为 `agentModel`, `messages`, `sessionId`, `chatOptionsModel`, `metadata`(Map), `status`, `cancelRequested` 等——无 `channelKind`、`principal`、`role`、`tenantId`。通道与身份信息目前无处获取
- **L2-13 ❌ not done**: `ISecurityLevelResolver` + `NoOpSecurityLevelResolver` 未实现——它是 `SecurityLevel` 的生产者（设计 §5.1），是 `IPermissionMatrix` dispatch-path 咨询的前置依赖
- **设计契约** (§5.3): 矩阵按 (channel, securityLevel) 控制允许的工具风险等级；webui 允许全部三级，api/dm 允许 STANDARD+ELEVATED，group 仅 STANDARD，未知通道仅 STANDARD（fail-closed）；Principal 携带 role(user/operator)、channelId、tenantId；operator 可绕过 RESTRICTED；默认实现 `PassThroughPermissionMatrix`（所有通道允许所有等级）
- **纵深防御位置** (§8): `IPermissionMatrix` 位于 Tool Call 产生之后、`IPermissionProvider` 之前，消费 `SecurityLevel`（由 `ISecurityLevelResolver` 在链下游解析）——这表明矩阵咨询与 SecurityLevel 解析在完整链路中是相互配合的
- **Roadmap**: L2-14 = `IPermissionMatrix` 接口 + `PassThroughPermissionMatrix`，deps = L1-6 ✅。归类为 §3 priority-3（接口定义缺失）+ priority-4（Pass-through 默认实现），非 priority-5（功能实现）

## Goals

- 一个语义完整的 `IPermissionMatrix` 契约：给定通道类型、身份、安全等级，返回 allow/deny 决策（deny 时携带可审计原因），与设计 §5.3 的通道 × 等级矩阵表一致
- 契约所需的共享值类型：`SecurityLevel` 枚举（STANDARD/ELEVATED/RESTRICTED）、`ChannelKind` 枚举（WEBUI/API/DM/GROUP）、`Principal` 值对象（role/channelId/tenantId）——这些是纯值定义，无行为，前向兼容 L2-13
- `PassThroughPermissionMatrix` pass-through 默认（所有通道允许所有等级，singleton + `passThrough()` 工厂），遵循 `NoOpContentGuardrail` / `PassThroughModelRouter` 的先例
- `DefaultAgentEngine` setter 接线：`setPermissionMatrix` + `getPermissionMatrix`，默认 = PassThrough，不修改构造器链，引擎行为不变（pass-through 放行一切）
- 单元测试覆盖：pass-through 默认（全放行）+ 一个自定义限制性矩阵（证明契约功能可用——非空壳）+ 值类型 + 接线验证（set 后 get 回同一实例）
- 设计文档 §5.3 更新：从"设计规格"变为"已落地契约"，记录 SecurityLevel 共享值类型的归属决策和 dispatch-path 咨询的 L2-13 依赖
- roadmap L2-14 ❌ → ✅

## Non-Goals

- **Dispatch-path 实际咨询**: 不在 `ReActAgentExecutor` 或 `DefaultAgentEngine` 的工具分发路径中调用 `IPermissionMatrix.isAllowed`。咨询需要 `SecurityLevel` 输入（来自 L2-13 `ISecurityLevelResolver`，未实现）和 `channelKind` 来源（`AgentExecutionContext` 当前无此字段）。pass-through 默认放行一切，即使咨询也不改变行为——交付契约表面 + 接线即可满足 priority-3/4 分类。完整 dispatch-path 咨询是 L2-13 落地后的后续工作
- **`ISecurityLevelResolver` (L2-13)**: 不实现 SecurityLevel 的生产者（hints → SecurityLevel 规则表）。本计划只定义 SecurityLevel 枚举值类型供矩阵消费
- **`AgentExecutionContext` 通道/身份字段**: 不向 `AgentExecutionContext` 添加 `channelKind`/`principal`/`role`/`tenantId` 字段——这是执行模型的跨切面变更，属于 dispatch-path 咨询的配套工作
- **DSL/XDSL 配置化**: 不引入 `security-policy.xdef` 或矩阵的 Delta 配置（设计 §9 渐进式增强）。矩阵通过 setter 程序化注入，与 talent/guardrail/messenger 一致
- **功能化矩阵实现**: 不实现设计 §5.3 表中的具体通道限制规则（webui=全部/api=STANDARD+ELEVATED 等）作为运行时默认。`PassThroughPermissionMatrix` 是唯一的 shipped 默认；限制性矩阵仅出现在测试中证明契约可用
- **Per-agent glob 路径规则模型** (plan 170 carry-over): plan 170 将 per-agent 路径规则归类为"L2-14 territory"，但那是 `IPathAccessChecker` 的规则模型扩展，不是 `IPermissionMatrix` 的通道 × 等级矩阵。两者是不同的 Layer 2 关注点，不合并
- **审批治理 (Layer 3)**: 不实现 `IApprovalGate` (L3-5)、`IDenialLedger` (L3-6)、`IPostDenialGuard` (L3-7)——它们是矩阵的下游消费者

## Scope

### In Scope

- `SecurityLevel` 枚举（STANDARD/ELEVATED/RESTRICTED）— 共享值类型
- `ChannelKind` 枚举（WEBUI/API/DM/GROUP）— 共享值类型
- `Principal` 值对象（role/channelId/tenantId）— 共享值类型
- `IPermissionMatrix` 接口 — 通道 × 等级矩阵决策契约
- `PassThroughPermissionMatrix` — pass-through 默认实现
- `DefaultAgentEngine.setPermissionMatrix` / `getPermissionMatrix` 接线
- 单元测试：值类型 + pass-through 默认 + 自定义限制性矩阵 + 引擎接线
- 设计文档 §5.3 更新 + roadmap L2-14 状态更新

### Out Of Scope

- Dispatch-path 咨询调用（依赖 L2-13 SecurityLevel 生产者）
- `ISecurityLevelResolver` (L2-13) 实现
- `AgentExecutionContext` 通道/身份字段
- DSL/XDSL 矩阵配置
- 功能化限制性矩阵（非 pass-through 的 shipped 实现）
- Layer 3 审批治理组件
- Per-agent glob 路径规则模型（plan 170 carry-over，不同关注点）

## Execution Plan

### Phase 1 - 值类型 + IPermissionMatrix 接口 + PassThroughPermissionMatrix 默认 + 单元测试

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/` (SecurityLevel, ChannelKind, Principal, IPermissionMatrix, PassThroughPermissionMatrix, 以及决策结果值类型), `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/security/`

- Item Types: `Decision | Proof`

- [x] 创建 `SecurityLevel` 枚举（值：STANDARD/ELEVATED/RESTRICTED），位于 security 包。这是设计 §5.1 定义的三级安全等级，作为 `IPermissionMatrix`（消费者，本计划）和未来 `ISecurityLevelResolver`（生产者，L2-13）的共享值类型。归属决策：在本计划定义因为矩阵契约需要它作为输入参数；L2-13 落地时复用此枚举
- [x] 创建 `ChannelKind` 枚举（值：WEBUI/API/DM/GROUP），位于 security 包。对应设计 §5.3 的四种通道类型。矩阵需处理未知/null 通道（fail-closed → 仅允许 STANDARD）
- [x] 创建 `Principal` 值对象（不可变，字段：role、channelId、tenantId），位于 security 包。role 区分 user/operator（operator 可绕过 RESTRICTED，设计 §5.3）；channelId 支持 per-channel override；tenantId 用于多租户（Nop `IContext` 天然支持，咨询时桥接）
- [x] 创建 `IPermissionMatrix` 接口，位于 security 包。契约语义：给定 (ChannelKind, Principal, SecurityLevel) 返回 allow/deny 决策；deny 时决策携带可审计原因（标识通道/等级限制），与 `ToolAccessResult`/`PathAccessResult` 的 deny-with-reason 模式一致。未知通道 fail-closed（仅 STANDARD）的语义在默认实现中体现
- [x] 创建 `PassThroughPermissionMatrix`（final，singleton，`passThrough()` 静态工厂），位于 security 包。所有通道 + 所有等级 + 所有 Principal → allow。遵循 `NoOpContentGuardrail.noOp()` / `PassThroughModelRouter.passThrough()` 先例
- [x] 单元测试 `TestPassThroughPermissionMatrix`：验证 pass-through 对全部四种 ChannelKind × 三种 SecurityLevel × user/operator role 的组合均返回 allow
- [x] 单元测试 `TestSecurityLevel` / `TestChannelKind`：枚举值域与设计 §5.1/§5.3 一致（值集、顺序）
- [x] 单元测试包含一个**自定义限制性矩阵**（测试内部类或测试辅助，非 shipped 产品代码），实现设计 §5.3 的通道限制规则表（webui=全部 / api=STANDARD+ELEVATED / dm=STANDARD+ELEVATED / group=STANDARD / 未知=STANDARD）+ operator 绕过 RESTRICTED。此测试证明契约功能可用——接口不是空壳，一个真实的限制性实现可以做出 allow/deny 决策

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `SecurityLevel` 枚举存在，值域 = {STANDARD, ELEVATED, RESTRICTED}，与设计 §5.1 一致
- [x] `ChannelKind` 枚举存在，值域 = {WEBUI, API, DM, GROUP}，与设计 §5.3 一致
- [x] `Principal` 值对象存在，不可变，携带 role/channelId/tenantId
- [x] `IPermissionMatrix` 接口存在，位于 `io.nop.ai.agent.security` 包，契约语义与设计 §5.3 一致（通道 × 等级 → allow/deny，deny 携带可审计原因）
- [x] `PassThroughPermissionMatrix` 存在，是 singleton（`passThrough()` 工厂返回同一实例），对所有输入返回 allow
- [x] **无静默跳过**: pass-through 默认的 allow 是语义正确的（设计 §5.3 明确 "所有通道允许所有等级"），非 placeholder；接口方法无空方法体
- [x] **新增功能测试**: `TestPassThroughPermissionMatrix`（全组合 allow）+ 值类型测试 + 自定义限制性矩阵测试（证明 api+RESTRICTED=deny、group+ELEVATED=deny、operator+RESTRICTED=allow 等具体决策）——全部通过
- [x] **Anti-Hollow Check（契约功能性）**: 自定义限制性矩阵测试证明 `IPermissionMatrix` 契约可以做出真实的 allow/deny 决策——接口不是只返回 true 的空壳，一个遵循设计 §5.3 规则表的实现能正确区分放行与拒绝
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过（新增 + 现有测试）
- [x] No owner-doc update required（设计文档更新在 Phase 2）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 引擎接线 + 接线验证测试 + 设计文档 + Roadmap 更新

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java`, `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/security/` 或 `engine/`, `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §5.3, `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`

- Item Types: `Proof | Follow-up`

- [x] 在 `DefaultAgentEngine` 添加 `private IPermissionMatrix permissionMatrix = PassThroughPermissionMatrix.passThrough();` 字段（字段声明处初始化默认，遵循 `skillProvider`/`skillCurator`/`messenger` 模式）
- [x] 添加 `public void setPermissionMatrix(IPermissionMatrix)` setter（null-check + 默认回退到 PassThrough，遵循 `setMessenger` 模式）+ `public IPermissionMatrix getPermissionMatrix()` getter（供未来工具/dispatch-path/L2-13 消费者获取）
- [x] setter Javadoc 遵循固定句式："Composition via this setter — no constructor chain change. Default is `PassThroughPermissionMatrix` (all channels allow all levels), so engine behaviour is unchanged unless a matrix is explicitly registered." 明确记录 dispatch-path 咨询 deferred 到 L2-13
- [x] 接线验证测试：构造 `DefaultAgentEngine`（不设 permissionMatrix）→ `getPermissionMatrix()` 返回 `PassThroughPermissionMatrix` 实例（默认接线连通）；构造后 `setPermissionMatrix(customMatrix)` → `getPermissionMatrix()` 返回同一 customMatrix 实例（set/get 同一性）
- [x] 向后兼容测试：不设 permissionMatrix 的引擎，现有 ReAct 执行行为不变（现有测试全通过——pass-through 不产生任何拒绝）
- [x] 更新设计文档 `nop-ai-agent-security-and-permissions.md` §5.3：标记 IPermissionMatrix 契约 + PassThroughPermissionMatrix 已落地；记录两个决策：(1) SecurityLevel 共享值类型在本计划定义（消费者先于生产者），L2-13 复用；(2) dispatch-path 咨询 deferred 到 L2-13（SecurityLevel 生产者），pass-through 默认使接线对运行时行为无影响
- [x] 更新 roadmap `nop-ai-agent-roadmap.md` L2-14 ❌ → ✅

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `DefaultAgentEngine` 持有 `permissionMatrix` 字段，默认 = `PassThroughPermissionMatrix.passThrough()`；`setPermissionMatrix` + `getPermissionMatrix` 存在
- [x] **接线验证**: 不设矩阵时 `getPermissionMatrix()` 返回 PassThrough 实例（默认接线连通）；`setPermissionMatrix(custom)` 后 `getPermissionMatrix()` 返回同一 custom 实例（非仅字段存在，而是 set/get 同一性可验证）
- [x] **端到端验证（向后兼容）**: 不设矩阵的引擎执行 ReAct 循环，行为与接线前完全一致——无 spurious 拒绝，现有全部测试通过。pass-through 默认对运行时透明
- [x] **无静默跳过**: getter 返回真实的 PassThrough 实例（非 null、非 placeholder）；setter 对 null 输入回退到 PassThrough（非静默忽略）
- [x] **新增功能测试**: 接线验证测试（默认 + set/get 同一性）+ 向后兼容测试——全部通过
- [x] 设计文档 §5.3 更新：契约已落地 + SecurityLevel 归属决策 + dispatch-path 咨询 L2-13 依赖记录
- [x] roadmap L2-14 → ✅
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过（全部新增 + 现有测试）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `IPermissionMatrix` 接口 + `PassThroughPermissionMatrix` 默认 + `SecurityLevel`/`ChannelKind`/`Principal` 值类型全部存在且语义完整
- [x] pass-through 默认经测试验证（全组合 allow）；自定义限制性矩阵经测试验证（契约功能可用，非空壳）
- [x] `DefaultAgentEngine` 接线完成（setter + getter），默认 = PassThrough，向后兼容
- [x] dispatch-path 咨询 deferral 已显式记录（依赖 L2-13），非静默跳过——契约表面完整，咨询是已裁定的后续工作
- [x] 设计文档 §5.3 + roadmap L2-14 同步到 live baseline
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**: closure audit 验证 (a) 自定义限制性矩阵测试证明契约可做出真实 allow/deny 决策（非空壳接口），(b) 引擎接线 set/get 同一性已验证（非仅字段存在），(c) pass-through 默认无空方法体/静默跳过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### Dispatch-path 咨询调用（ReAct/工具分发路径中调用 isAllowed）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 咨询需要 `SecurityLevel` 输入（来自 `ISecurityLevelResolver` L2-13，未实现）和 `channelKind` 来源（`AgentExecutionContext` 无此字段）。pass-through 默认放行一切，即使咨询也不改变行为。L2-14 在 roadmap 中归类为 priority-3（接口定义）+ priority-4（pass-through 默认），非 priority-5（功能实现）。交付契约表面 + 接线即满足分类。完整咨询是 L2-13 落地后的配套工作（L2-13 提供 SecurityLevel 输入 + 咨询点接入）。
- Successor Required: yes
- Successor Path: 未来 L2-13 plan 或 dispatch-path 咨询集成 plan（deps: L2-13 SecurityLevel 生产者 + AgentExecutionContext channelKind 字段）

### AgentExecutionContext 通道/身份字段（channelKind / principal / role / tenantId）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 通道与身份信息是 dispatch-path 咨询的配套输入。没有咨询调用，就不需要从执行上下文读取通道/身份。添加这些字段是执行模型的跨切面变更，应与 L2-13 + 咨询集成一同规划。
- Successor Required: yes
- Successor Path: 同 dispatch-path 咨询 successor（L2-13 plan 或咨询集成 plan）

### 功能化限制性矩阵（设计 §5.3 通道限制规则表的 shipped 实现）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `PassThroughPermissionMatrix` 是 roadmap 指定的 shipped 默认。设计 §5.3 的通道限制规则（webui=全部 / api=STANDARD+ELEVATED / group=STANDARD）是功能化实现，归类为 priority-5（功能实现），在 pass-through 默认之上独立交付。本计划在测试中验证了规则表语义可行性（自定义限制性矩阵测试），证明契约能承载这些规则。
- Successor Required: no

### DSL/XDSL 矩阵配置（security-policy.xdef + Delta 覆盖）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计 §9 渐进式增强将 DSL 配置化为后续增强。矩阵当前通过 setter 程序化注入，与 talent/guardrail/messenger 一致。DSL 配置化是独立的配置层关注点。
- Successor Required: no

### Per-agent glob 路径规则模型（plan 170 carry-over）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: plan 170 将 per-agent 路径规则归类为 "L2-14 territory"，但精确而言它是 `IPathAccessChecker` 的 per-agent 规则模型扩展（glob allow/deny 模式），不是 `IPermissionMatrix` 的通道 × 等级矩阵。两者是不同的 Layer 2 安全扩展维度。本计划交付通道 × 等级矩阵契约；per-agent glob 路径规则是独立的路径安全增强。
- Successor Required: yes
- Successor Path: 未来 per-agent-path-rules plan（扩展 IPathAccessChecker 的规则模型，复用 plan 170 的 wrapper 机制）

## Non-Blocking Follow-ups

- L2-13（`ISecurityLevelResolver` + `NoOpSecurityLevelResolver`）：SecurityLevel 的生产者，复用本计划定义的 SecurityLevel 枚举；落地后启用 dispatch-path 咨询集成
- L3-5（`IApprovalGate`）、L3-7（`IPostDenialGuard`）：矩阵的下游消费者，在审批治理链中使用通道 × 等级决策
- 功能化限制性矩阵实现（设计 §5.3 规则表的 shipped 版本，替代 pass-through 默认）
- matrix 决策的审计集成（deny 决策流入现有 `IAuditLogger` / `AuditEvent` 路径，与 ToolAccessResult/PathAccessResult 的 deny 一致）

## Closure

Status Note: Plan 172 交付了 Layer 2 的 `IPermissionMatrix` 通道 × 安全等级权限矩阵契约表面 + `PassThroughPermissionMatrix` pass-through 默认 + 共享值类型（`SecurityLevel`/`ChannelKind`/`Principal`/`PrincipalRole`/`MatrixDecision`）+ `DefaultAgentEngine` setter 接线。契约经独立子 agent closure audit 验证为非空壳——一个测试内部的限制性矩阵（`DesignSpecRestrictiveMatrix`）证明契约能做出真实的 allow/deny 决策。dispatch-path 咨询明确裁定为 L2-13 后续工作（非静默跳过）。pass-through 默认对运行时透明（全 928 测试通过，0 spurious 拒绝）。
Completed: 2026-06-14

Closure Audit Evidence:

- Reviewer / Agent: independent subagent (fresh session, task ses_13bef10d7ffejBAL5TIeZp643f)
- Audit Session: ses_13bef10d7ffejBAL5TIeZp643f
- Evidence:
  - Phase 1 Exit Criteria — 全部 PASS：SecurityLevel.java:18-22（值域 {STANDARD,ELEVATED,RESTRICTED}）、ChannelKind.java:22-27（{WEBUI,API,DM,GROUP}）、Principal.java:20-83（不可变，role/channelId/tenantId）、IPermissionMatrix.java:33-48（check→MatrixDecision，deny 携带 reason）、PassThroughPermissionMatrix.java:15-29（singleton passThrough() 工厂）
  - Phase 2 Exit Criteria — 全部 PASS：DefaultAgentEngine.java:77（字段默认 PassThroughPermissionMatrix.passThrough()）、:272-282（setPermissionMatrix null-check 回退 + getPermissionMatrix）、setter Javadoc :259-271 记录 L2-13 deferral
  - Closure Gates — 全部 PASS：接口+默认+值类型语义完整；pass-through 全组合 allow 经测试验证；引擎接线 set/get 同一性验证；dispatch-path deferral 显式记录；设计文档 §5.3 + roadmap L2-14 同步
  - Anti-Hollow Check: (a) PASS — `DesignSpecRestrictiveMatrix`（TestPassThroughPermissionMatrix.java:91-132，测试内部类非 shipped 代码）做出真实决策：API+RESTRICTED+user=deny、GROUP+ELEVATED=deny、operator+RESTRICTED=allow、null channel fail-closed deny；(b) PASS — assertSame(custom, engine.getPermissionMatrix())（TestDefaultAgentEnginePermissionMatrixWiring.java:36）验证 set/get 引用同一性，非仅字段存在；(c) PASS — PassThroughPermissionMatrix.check() 返回 MatrixDecision.allow()（语义正确的决策对象，非空方法体/stub），setPermissionMatrix(null) 回退到 PassThrough singleton（非静默忽略）
  - `node ai-dev/tools/check-plan-checklist.mjs 172-...md --strict` 退出码为 0
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码为 0（0 findings）
  - Deferred 项分类检查：dispatch-path 咨询（L2-13 前置）、AgentExecutionContext 字段（无消费者）、功能化限制性矩阵（priority-5）、DSL 配置（§9 渐进式增强）、per-agent glob 路径规则（plan 170 carry-over，IPathAccessChecker 不同关注点）——均无 in-scope live defect 被降级
  - 测试结果：指定测试集 28 tests / 0 failures；完整模块集 928 tests / 0 failures / 0 errors

Follow-up:

- L2-13（`ISecurityLevelResolver` + `NoOpSecurityLevelResolver`）：SecurityLevel 生产者，复用本计划定义的 SecurityLevel 枚举；落地后启用 dispatch-path 咨询集成（successor plan）
- L3-5（`IApprovalGate`）、L3-7（`IPostDenialGuard`）：矩阵的下游消费者
- 功能化限制性矩阵实现（设计 §5.3 规则表的 shipped 版本，替代 pass-through 默认）
- matrix 决策的审计集成（deny 决策流入现有 `IAuditLogger` / `AuditEvent` 路径）
- AgentExecutionContext channelKind/principal 字段 + dispatch-path 咨询点接入（与 L2-13 一同规划）

## Follow-up handled by 173-nop-ai-agent-security-level-resolver.md

> Additive annotation (2026-06-14). This completed plan is historical record; this section only records successor traceability and does not alter the closure above.

The deferred "L2-13（`ISecurityLevelResolver` + `NoOpSecurityLevelResolver`）" item (see `Non-Blocking Follow-ups` and `Deferred But Adjudicated` → "Dispatch-path 咨询调用" Successor Path) is being handled by successor plan [`173-nop-ai-agent-security-level-resolver.md`](173-nop-ai-agent-security-level-resolver.md).

Carry-over note for the successor: the successor delivers the `ISecurityLevelResolver` interface + `NoOpSecurityLevelResolver` default + `LevelHints` value type + engine setter wiring, reusing the `SecurityLevel` enum defined in this plan. The dispatch-path consultation integration itself remains a separate concern (it needs both L2-13 ✅ AND the `AgentExecutionContext` channelKind/principal fields) and is deferred in the successor plan under the same rationale as this plan's dispatch-path deferral.
