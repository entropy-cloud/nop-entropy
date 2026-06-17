# 173 nop-ai-agent Security Level Resolver (L2-13)

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L2-13

> Last Reviewed: 2026-06-14
> Source: Carry-over from plan 172 (`ai-dev/plans/172-nop-ai-agent-permission-matrix.md`, Non-Blocking Follow-ups "L2-13" + Deferred "Dispatch-path 咨询调用" Successor Path: "未来 L2-13 plan"); roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 2 L2-13; design `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §5.1 (ISecurityLevelResolver — 安全等级解析)
> Related: Plan 172 (L2-14 `IPermissionMatrix` + `PassThroughPermissionMatrix` + `SecurityLevel` shared value type — the immediate predecessor), Plan 134 (L1-6 `IPermissionProvider` + `DefaultPermissionProvider` — the Layer-1 dependency), Plan 139 (L1-7 `IToolAccessChecker`), Plan 141 (L1-8 `IPathAccessChecker`)

## Purpose

交付 Layer 2 策略扩展层的 `ISecurityLevelResolver` 接口 + `NoOpSecurityLevelResolver` pass-through 默认实现及其契约所需的 `LevelHints` 值类型，并在 `DefaultAgentEngine` 上完成 setter 接线（默认 = NoOp）。这使得工具/操作的安全等级解析有一个可测试、可替换的契约表面，供后续功能化规则表解析器（priority-5）、dispatch-path 咨询集成（需要 `AgentExecutionContext` channelKind 字段 + L2-13 + L2-14 联合接入）、以及 Layer 3 审批治理（`IApprovalGate` L3-5 消费 SecurityLevel）使用。

本计划只交付**契约表面 + pass-through 默认 + 引擎接线**，不交付 dispatch-path 的实际咨询调用（consultation），因为咨询需要 `AgentExecutionContext` 上的 channelKind/principal 字段来源（当前无此字段）和 LevelHints 生产机制（谁在运行时填充 hints）。pass-through 默认对所有操作返回 STANDARD，不改变运行时行为——与 plan 172 的 dispatch-path deferral 完全对称。

## Current Baseline

- **L2-14 ✅ delivered (plan 172)**: `IPermissionMatrix` 接口 + `PassThroughPermissionMatrix` 默认 + 共享值类型（`SecurityLevel`/`ChannelKind`/`Principal`/`PrincipalRole`/`MatrixDecision`）位于 `io.nop.ai.agent.security` 包，引擎通过 `DefaultAgentEngine.setPermissionMatrix` / `getPermissionMatrix` 接线（field :77, setter/getter :272-282），默认 = PassThrough。dispatch-path 咨询明确 deferred 到 L2-13
- **`SecurityLevel` 已存在** (plan 172): 枚举位于 `SecurityLevel.java`，值域 = {STANDARD, ELEVATED, RESTRICTED}。Javadoc 明确注释 "produced by the future `ISecurityLevelResolver` (L2-13). Defined here because the matrix contract requires it as an input parameter; L2-13 will reuse this enum when landed."。本计划直接复用，不重复定义
- **`IContentTrustEvaluator` 已存在**: 接口 `isTrustedSource(ContentOrigin origin, AgentExecutionContext ctx)` 位于 security 包。默认实现 `DefaultContentTrustEvaluator`（CHANNEL_INPUT + AGENT_GENERATED = trusted, WEB_FETCH + FILE_READ = untrusted）。功能化 resolver 将消费此接口填充 `LevelHints.trustedSource`，但本计划（NoOp 默认）不需要
- **`ContentOrigin` 已存在**: 枚举 {CHANNEL_INPUT, WEB_FETCH, FILE_READ, AGENT_GENERATED} 位于 security 包
- **Security 包结构** (`io.nop.ai.agent.security`): 30 个 Java 文件，包含 `IPermissionProvider`, `IToolAccessChecker`, `IPathAccessChecker`, `IAuditLogger`, `IContentTrustEvaluator`, `ContentOrigin`, `IPermissionMatrix`, `PassThroughPermissionMatrix`, `SecurityLevel`, `ChannelKind`, `Principal`, `PrincipalRole`, `MatrixDecision`, `ParentPermissionConstraint` + 各自的 Default/AllowAll/NoOp/PassThrough 实现 + `ParentConstrained*` wrapper（plan 169/170）
- **Layer 2 接线先例**: `IContentGuardrail` + `NoOpContentGuardrail`（`guardrail` 包）、`IModelRouter` + `PassThroughModelRouter`（`router` 包）、`IPermissionMatrix` + `PassThroughPermissionMatrix`（plan 172, security 包）、`ITalent`（`talent` 包）、`ISkillProvider`/`ISkillCurator`（`skill` 包）、`IAgentMessenger`/`NoOpAgentMessenger`（`message` 包）均已交付。`NoOpSecurityLevelResolver` 是这一先例的直接延续
- **引擎接线模式** (plan 172 verified): `DefaultAgentEngine` 对 Layer 2+ 组件使用 mutable field + setter + getter 模式（非构造器参数）。field 在声明处初始化为 pass-through/noOp 默认（`permissionMatrix` at line 77），setter 做 null-check + 默认回退（line 272-273），getter 返回字段。不修改构造器链。setter Javadoc 遵循固定句式记录默认行为和 deferral
- **`ISecurityLevelResolver` 不存在**: grep 确认 `nop-ai-agent` 模块中无 `ISecurityLevelResolver` 或 `NoOpSecurityLevelResolver` 类——仅在 `SecurityLevel.java:7`、`DefaultAgentEngine.java:268`、`IPermissionMatrix.java:29` 的 Javadoc 注释中以 `{@code ISecurityLevelResolver}` 形式被引用为"未来"接口
- **`LevelHints` 不存在**: grep 确认无此类。设计 §5.1 定义其为 flat dataclass（trustedSource / writesOutsideWorkspace / crossesTrustBoundary / needsNetwork / highImpact，每个字段是可审计的布尔值）
- **`AgentExecutionContext` 无 channelKind/principal 字段**: 当前字段为 `agentModel`, `messages`, `sessionId`, `chatOptionsModel`, `metadata`(Map), `status`, `cancelRequested` 等——无 `channelKind`、`principal`。这是 dispatch-path 咨询的配套字段需求，本计划不涉及
- **设计契约** (§5.1): resolver 根据 action 种类（action kind，对应工具名/操作类别如 fs.read / shell.exec / network.fetch）和上下文 hints 解析安全等级。规则表是确定性的（无 AI 决策）：fs.read/list/grep → STANDARD；fs.write/edit/patch → STANDARD (writesOutsideWorkspace → ELEVATED)；shell.exec/code.exec → STANDARD (!trustedSource → ELEVATED, highImpact → RESTRICTED)；network.fetch/web.fetch → STANDARD (!trustedSource → RESTRICTED)；其他 → STANDARD (!trustedSource → ELEVATED, highImpact → RESTRICTED)
- **纵深防御位置** (§8): `ISecurityLevelResolver` 位于 `IPermissionMatrix` 之后、`IApprovalGate` 之前——矩阵消费 SecurityLevel（由 resolver 生产），审批门消费 SecurityLevel（需审批等级触发审批）
- **Roadmap**: L2-13 = `ISecurityLevelResolver` 接口 + `NoOpSecurityLevelResolver`，deps = L1-6 ✅。归类为 §3 priority-3（接口定义缺失）+ priority-4（Pass-through 默认实现），非 priority-5（功能实现——规则表解析器）

## Goals

- 一个语义完整的 `ISecurityLevelResolver` 契约：给定 action kind（String）和 hints（LevelHints），返回 `SecurityLevel`（STANDARD/ELEVATED/RESTRICTED），与设计 §5.1 的解析语义一致
- 契约所需的 `LevelHints` 值类型：不可变 flat value object，携带 5 个可审计布尔字段（trustedSource / writesOutsideWorkspace / crossesTrustBoundary / needsNetwork / highImpact）——纯值定义，无行为，前向兼容功能化 resolver 和 dispatch-path 咨询
- `NoOpSecurityLevelResolver` pass-through 默认（所有 action kind + 所有 hints → STANDARD，singleton + `noOp()` 工厂），遵循 `NoOpContentGuardrail.noOp()` / `PassThroughPermissionMatrix.passThrough()` 先例
- `DefaultAgentEngine` setter 接线：`setSecurityLevelResolver` + `getSecurityLevelResolver`，默认 = NoOp，不修改构造器链，引擎行为不变（NoOp 对所有操作返回 STANDARD = 等于不分级）
- 单元测试覆盖：NoOp 默认（全 STANDARD）+ 一个自定义规则表 resolver（证明契约功能可用——非空壳，实现设计 §5.1 的确定性规则表）+ LevelHints 值类型 + 接线验证（set 后 get 回同一实例）
- 设计文档 §5.1 更新：从"设计规格"变为"已落地契约"，记录 LevelHints 归属决策和 dispatch-path 咨询的 deferral
- roadmap L2-13 ❌ → ✅

## Non-Goals

- **Dispatch-path 实际咨询**: 不在 `ReActAgentExecutor` 或 `DefaultAgentEngine` 的工具分发路径中调用 `resolver.resolve(...)` 或 `matrix.check(...)`。咨询需要 `AgentExecutionContext` 上的 channelKind/principal 字段来源（当前无此字段）和 LevelHints 生产机制（谁在运行时评估 trustedSource 等 hints——需要 `IContentTrustEvaluator` 集成到执行路径）。NoOp 默认对所有操作返回 STANDARD，即使接通矩阵咨询也不改变行为——交付契约表面 + 接线即可满足 priority-3/4 分类。完整 dispatch-path 咨询是 L2-13 + L2-14 联合落地后的配套工作（需要 AgentExecutionContext 字段 + 咨询点接入 + hints 生产）
- **功能化规则表解析器**: 不实现设计 §5.1 规则表的具体升级逻辑（fs.write+writesOutsideWorkspace → ELEVATED 等）作为 shipped 默认。`NoOpSecurityLevelResolver` 是唯一的 shipped 默认；规则表解析器仅出现在测试中证明契约可用。功能化解析器归类为 priority-5
- **`AgentExecutionContext` 通道/身份字段**: 不向 `AgentExecutionContext` 添加 `channelKind`/`principal`/`role`/`tenantId` 字段——这是执行模型的跨切面变更，属于 dispatch-path 咨询的配套工作
- **LevelHints 运行时生产**: 不实现运行时如何填充 LevelHints（谁评估 trustedSource、writesOutsideWorkspace 等）。NoOp 默认忽略 hints，功能化 resolver 消费 hints，但 hints 的生产链（工具参数分析、工作目录检查、信任边界评估）是独立的 dispatch-path 咨询配套工作
- **DSL/XDSL 配置化**: 不引入 `security-policy.xdef` 或规则表的 Delta 配置（设计 §9 渐进式增强）。resolver 通过 setter 程序化注入，与 talent/guardrail/messenger/matrix 一致
- **矩阵咨询集成 (L2-14 dispatch-path)**: 不在 dispatch-path 中调用 `IPermissionMatrix.check(channel, principal, level)`。矩阵咨询需要 SecurityLevel 输入（本计划提供了 resolver 契约，但 NoOp 默认返回 STANDARD）和 channel 来源——两者就绪后矩阵咨询集成是一个独立工作项
- **审批治理 (Layer 3)**: 不实现 `IApprovalGate` (L3-5)、`IDenialLedger` (L3-6)、`IPostDenialGuard` (L3-7)——它们是 SecurityLevel 的下游消费者
- **Per-agent glob 路径规则模型** (plan 170 carry-over): 与 plan 172 相同，这是 `IPathAccessChecker` 的规则模型扩展，不是 `ISecurityLevelResolver` 的关注点

## Scope

### In Scope

- `LevelHints` 不可变值对象（5 个布尔字段：trustedSource / writesOutsideWorkspace / crossesTrustBoundary / needsNetwork / highImpact）— resolver 契约的输入值类型
- `ISecurityLevelResolver` 接口 — action kind × hints → SecurityLevel 解析契约
- `NoOpSecurityLevelResolver` — pass-through 默认实现（全 STANDARD）
- `DefaultAgentEngine.setSecurityLevelResolver` / `getSecurityLevelResolver` 接线
- 单元测试：LevelHints 值类型 + NoOp 默认 + 自定义规则表 resolver + 引擎接线
- 设计文档 §5.1 更新 + roadmap L2-13 状态更新

### Out Of Scope

- Dispatch-path 咨询调用（依赖 AgentExecutionContext channelKind/principal 字段 + LevelHints 生产机制）
- 功能化规则表解析器（设计 §5.1 规则表的 shipped 实现）
- LevelHints 运行时生产链（IContentTrustEvaluator 集成、工具参数分析）
- AgentExecutionContext 通道/身份字段
- DSL/XDSL 规则表配置
- 矩阵 dispatch-path 咨询集成（L2-14 dispatch-path）
- Layer 3 审批治理组件
- Per-agent glob 路径规则模型（plan 170 carry-over）

## Execution Plan

### Phase 1 - LevelHints 值类型 + ISecurityLevelResolver 接口 + NoOpSecurityLevelResolver 默认 + 单元测试

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/` (LevelHints, ISecurityLevelResolver, NoOpSecurityLevelResolver), `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/security/`

- Item Types: `Decision | Proof`

- [x] 创建 `LevelHints` 不可变值对象，位于 security 包。字段（每个是可审计的布尔值，设计 §5.1）：`trustedSource`（内容来源是否可信）、`writesOutsideWorkspace`（是否写工作目录外）、`crossesTrustBoundary`（是否跨信任边界）、`needsNetwork`（是否需要网络访问）、`highImpact`（是否高影响操作）。提供全参数构造器 + 便捷工厂 `LevelHints.defaults()`（所有字段 = false，对应"无任何风险信号" = STANDARD 基线）。遵循 `Principal` 值对象的先例（final class, immutable fields, equals/hashCode/toString）
- [x] 创建 `ISecurityLevelResolver` 接口，位于 security 包。契约语义：给定 action kind（String，对应工具名/操作类别如 `fs.read` / `shell.exec` / `network.fetch`）和 hints（`LevelHints`），返回 `SecurityLevel`（STANDARD/ELEVATED/RESTRICTED）。复用 plan 172 定义的 `SecurityLevel` 枚举（不重复定义）。接口 Javadoc 记录设计 §5.1 的规则表语义和 NoOp 默认行为
- [x] 创建 `NoOpSecurityLevelResolver`（final，singleton，`noOp()` 静态工厂），位于 security 包。所有 action kind + 所有 hints → `SecurityLevel.STANDARD`（等于不分级，设计 §5.1 明确）。遵循 `NoOpContentGuardrail.noOp()` / `PassThroughPermissionMatrix.passThrough()` 先例
- [x] 单元测试 `TestNoOpSecurityLevelResolver`：验证 NoOp 对多种 action kind（fs.read / fs.write / shell.exec / network.fetch / null / 未知）× LevelHints 全布尔组合均返回 STANDARD
- [x] 单元测试 `TestLevelHints`：值类型不可变性 + equals/hashCode + `defaults()` 工厂返回全 false
- [x] 单元测试包含一个**自定义规则表 resolver**（测试内部类或测试辅助，非 shipped 产品代码），实现设计 §5.1 的确定性规则表（fs.read/list/grep → STANDARD；fs.write/edit/patch + writesOutsideWorkspace → ELEVATED；shell.exec + !trustedSource → ELEVATED, + highImpact → RESTRICTED；network.fetch + !trustedSource → RESTRICTED；其他 + !trustedSource → ELEVATED, + highImpact → RESTRICTED）。此测试证明契约功能可用——接口不是空壳，一个真实的规则表实现可以根据 hints 做出 STANDARD/ELEVATED/RESTRICTED 决策

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `LevelHints` 不可变值对象存在，5 个布尔字段（trustedSource / writesOutsideWorkspace / crossesTrustBoundary / needsNetwork / highImpact）与设计 §5.1 一致；`defaults()` 工厂返回全 false
- [x] `ISecurityLevelResolver` 接口存在，位于 `io.nop.ai.agent.security` 包，契约语义 = resolve(actionKind: String, hints: LevelHints) → SecurityLevel，与设计 §5.1 一致；复用 plan 172 的 `SecurityLevel` 枚举
- [x] `NoOpSecurityLevelResolver` 存在，是 singleton（`noOp()` 工厂返回同一实例），对所有 action kind + hints 返回 STANDARD
- [x] **无静默跳过**: NoOp 默认返回 STANDARD 是语义正确的（设计 §5.1 明确 "所有操作返回 STANDARD，等于不分级"），非 placeholder；接口方法无空方法体
- [x] **新增功能测试**: `TestNoOpSecurityLevelResolver`（全 action kind × hints 组合 STANDARD）+ `TestLevelHints`（值类型不变性 + defaults）+ 自定义规则表 resolver 测试（证明 fs.write+writesOutsideWorkspace=ELEVATED、shell.exec+!trustedSource=ELEVATED、network.fetch+!trustedSource=RESTRICTED、shell.exec+highImpact=RESTRICTED 等具体决策）——全部通过
- [x] **Anti-Hollow Check（契约功能性）**: 自定义规则表 resolver 测试证明 `ISecurityLevelResolver` 契约可以做出真实的三级 SecurityLevel 决策——接口不是只返回 STANDARD 的空壳，一个遵循设计 §5.1 规则表的实现能正确区分 STANDARD/ELEVATED/RESTRICTED
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过（新增 + 现有测试）
- [x] No owner-doc update required（设计文档更新在 Phase 2）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 引擎接线 + 接线验证测试 + 设计文档 + Roadmap 更新

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java`, `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/security/` 或 `engine/`, `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §5.1, `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`

- Item Types: `Proof | Follow-up`

- [x] 在 `DefaultAgentEngine` 添加 `private ISecurityLevelResolver securityLevelResolver = NoOpSecurityLevelResolver.noOp();` 字段（字段声明处初始化默认，遵循 `permissionMatrix`/`skillProvider`/`skillCurator`/`messenger` 模式，位于 line 77 `permissionMatrix` 字段之后）
- [x] 添加 `public void setSecurityLevelResolver(ISecurityLevelResolver)` setter（null-check + 默认回退到 NoOp，遵循 `setPermissionMatrix` 模式）+ `public ISecurityLevelResolver getSecurityLevelResolver()` getter（供未来 dispatch-path/Layer-3 消费者获取）
- [x] setter Javadoc 遵循固定句式："Composition via this setter — no constructor chain change. Default is `NoOpSecurityLevelResolver` (all operations resolve to STANDARD, equivalent to no classification), so engine behaviour is unchanged unless a resolver is explicitly registered." 明确记录 dispatch-path 咨询 deferred（需要 AgentExecutionContext channelKind/principal 字段 + LevelHints 生产机制）
- [x] 接线验证测试：构造 `DefaultAgentEngine`（不设 securityLevelResolver）→ `getSecurityLevelResolver()` 返回 `NoOpSecurityLevelResolver` 实例（默认接线连通）；构造后 `setSecurityLevelResolver(customResolver)` → `getSecurityLevelResolver()` 返回同一 customResolver 实例（set/get 同一性）
- [x] 向后兼容测试：不设 securityLevelResolver 的引擎，现有 ReAct 执行行为不变（现有测试全通过——NoOp 不改变任何行为）
- [x] 更新设计文档 `nop-ai-agent-security-and-permissions.md` §5.1：标记 ISecurityLevelResolver 契约 + NoOpSecurityLevelResolver 已落地；记录两个决策：(1) LevelHints 共享值类型在本计划定义（resolver 契约需要它作为输入参数），SecurityLevel 复用 plan 172 定义的枚举（消费者先于生产者，生产者复用）；(2) dispatch-path 咨询 deferred（需要 AgentExecutionContext channelKind/principal 字段 + LevelHints 生产机制 + L2-13 + L2-14 联合接入），NoOp 默认使接线对运行时行为无影响
- [x] 更新 roadmap `nop-ai-agent-roadmap.md` L2-13 ❌ → ✅

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `DefaultAgentEngine` 持有 `securityLevelResolver` 字段，默认 = `NoOpSecurityLevelResolver.noOp()`；`setSecurityLevelResolver` + `getSecurityLevelResolver` 存在
- [x] **接线验证**: 不设 resolver 时 `getSecurityLevelResolver()` 返回 NoOp 实例（默认接线连通）；`setSecurityLevelResolver(custom)` 后 `getSecurityLevelResolver()` 返回同一 custom 实例（非仅字段存在，而是 set/get 同一性可验证）
- [x] **端到端验证（向后兼容）**: 不设 resolver 的引擎执行 ReAct 循环，行为与接线前完全一致——无行为变化，现有全部测试通过。NoOp 默认对运行时透明
- [x] **无静默跳过**: getter 返回真实的 NoOp 实例（非 null、非 placeholder）；setter 对 null 输入回退到 NoOp（非静默忽略）
- [x] **新增功能测试**: 接线验证测试（默认 + set/get 同一性）+ 向后兼容测试——全部通过
- [x] 设计文档 §5.1 更新：契约已落地 + LevelHints 归属决策 + SecurityLevel 复用记录 + dispatch-path 咨询 deferral 记录
- [x] roadmap L2-13 → ✅
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过（全部新增 + 现有测试）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `ISecurityLevelResolver` 接口 + `NoOpSecurityLevelResolver` 默认 + `LevelHints` 值类型全部存在且语义完整
- [x] NoOp 默认经测试验证（全 action kind × hints STANDARD）；自定义规则表 resolver 经测试验证（契约功能可用，非空壳——能区分 STANDARD/ELEVATED/RESTRICTED）
- [x] `DefaultAgentEngine` 接线完成（setter + getter），默认 = NoOp，向后兼容
- [x] dispatch-path 咨询 deferral 已显式记录（依赖 AgentExecutionContext 字段 + LevelHints 生产 + L2-13/L2-14 联合接入），非静默跳过——契约表面完整，咨询是已裁定的后续工作
- [x] `SecurityLevel` 枚举从 plan 172 复用（非重复定义），design §5.1 的归属决策已记录
- [x] 设计文档 §5.1 + roadmap L2-13 同步到 live baseline
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**: closure audit 验证 (a) 自定义规则表 resolver 测试证明契约可做出真实三级 SecurityLevel 决策（非空壳接口），(b) 引擎接线 set/get 同一性已验证（非仅字段存在），(c) NoOp 默认无空方法体/静默跳过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### Dispatch-path 咨询调用（ReAct/工具分发路径中调用 resolve + matrix.check）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 咨询需要 `AgentExecutionContext` 上的 channelKind/principal 字段来源（当前无此字段）和 LevelHints 生产机制（谁在运行时评估 trustedSource / writesOutsideWorkspace / highImpact 等 hints——需要 `IContentTrustEvaluator` 集成、工具参数分析、工作目录检查）。NoOp 默认对所有操作返回 STANDARD，pass-through 矩阵放行一切，即使咨询也不改变行为。L2-13 在 roadmap 中归类为 priority-3（接口定义）+ priority-4（pass-through 默认），非 priority-5（功能实现）。交付契约表面 + 接线即满足分类。完整咨询是 L2-13 + L2-14 联合落地后的配套工作（AgentExecutionContext 字段 + 咨询点接入 + hints 生产链）。
- Successor Required: yes
- Successor Path: 未来 dispatch-path 咨询集成 plan（deps: L2-13 ✅ + L2-14 ✅ + AgentExecutionContext channelKind/principal 字段 + LevelHints 运行时生产链）

### AgentExecutionContext 通道/身份字段（channelKind / principal / role / tenantId）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 通道与身份信息是 dispatch-path 咨询的配套输入。没有咨询调用，就不需要从执行上下文读取通道/身份。添加这些字段是执行模型的跨切面变更，应与 dispatch-path 咨询集成一同规划。与 plan 172 的同名 deferral 完全一致。
- Successor Required: yes
- Successor Path: 同 dispatch-path 咨询 successor

### LevelHints 运行时生产链（谁评估 trustedSource / writesOutsideWorkspace 等）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: LevelHints 的 5 个布尔字段需要运行时评估：`trustedSource` 需 `IContentTrustEvaluator`（已存在，但未集成到执行路径）；`writesOutsideWorkspace` 需工具参数路径分析 vs agent workDir；`crossesTrustBoundary` / `needsNetwork` / `highImpact` 需工具元数据或参数分析。NoOp 默认忽略 hints，功能化 resolver 消费 hints，但 hints 的生产链是 dispatch-path 咨询的配套基础设施。
- Successor Required: yes
- Successor Path: 同 dispatch-path 咨询 successor（或独立的 hints-production plan）

### 功能化规则表解析器（设计 §5.1 规则表的 shipped 实现）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `NoOpSecurityLevelResolver` 是 roadmap 指定的 shipped 默认。设计 §5.1 的确定性规则表（fs.write+writesOutsideWorkspace → ELEVATED 等）是功能化实现，归类为 priority-5（功能实现），在 NoOp 默认之上独立交付。本计划在测试中验证了规则表语义可行性（自定义规则表 resolver 测试），证明契约能承载这些规则。
- Successor Required: no

### DSL/XDSL 规则表配置（security-policy.xdef + Delta 覆盖）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计 §9 渐进式增强将 DSL 配置化为后续增强（设计 §5.1 "XDSL 配置化"段落）。resolver 当前通过 setter 程序化注入，与 talent/guardrail/messenger/matrix 一致。DSL 配置化是独立的配置层关注点。
- Successor Required: no

### Per-agent glob 路径规则模型（plan 170 carry-over）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 与 plan 172 完全一致——plan 170 将 per-agent 路径规则归类为 `IPathAccessChecker` 的规则模型扩展（glob allow/deny 模式），不是 `ISecurityLevelResolver` 的 action-kind × hints 安全等级解析。两者是不同的 Layer 2 安全扩展维度。
- Successor Required: yes
- Successor Path: 未来 per-agent-path-rules plan（扩展 IPathAccessChecker 的规则模型，复用 plan 170 的 wrapper 机制）

## Non-Blocking Follow-ups

- 功能化规则表解析器实现（设计 §5.1 规则表的 shipped 版本，替代 NoOp 默认——priority-5）
- Dispatch-path 咨询集成（AgentExecutionContext channelKind/principal 字段 + 咨询点接入 + LevelHints 运行时生产链——需要 L2-13 ✅ + L2-14 ✅ 联合接入）
- L3-5（`IApprovalGate`）：SecurityLevel 的下游消费者，需审批等级触发人类审批
- L3-6（`IDenialLedger`）、L3-7（`IPostDenialGuard`）：审批治理链的拒绝计数和盲重试阻止
- resolver 决策的审计集成（SecurityLevel 决策流入现有 `IAuditLogger` / `AuditEvent` 路径）
- 矩阵 dispatch-path 咨询集成（plan 172 的同名 deferral——需要本计划 + L2-14 + AgentExecutionContext 字段联合落地）

## Closure

Status Note: Plan 173 delivered the Layer 2 `ISecurityLevelResolver` contract surface + `NoOpSecurityLevelResolver` pass-through default + `LevelHints` value type, wired into `DefaultAgentEngine` via setter (default = NoOp, constructor chain unchanged). The NoOp default resolves all operations to STANDARD (equivalent to no classification), making the wiring transparent to runtime behaviour. The dispatch-path consultation is explicitly deferred (requires AgentExecutionContext channelKind/principal fields + LevelHints runtime-production chain), classified as out-of-scope improvement with a successor path. All in-scope items landed; all deferred items are adjudicated non-blocking improvements with explicit rationale.
Completed: 2026-06-14

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (session ID `closure-audit-173-20260614`, opencode task id `ses_13bcbc4ebffeCu7U3ZVomnrnY1`)
- Audit Mode: read-only (no files modified)
- Evidence:
  - Phase 1 Exit Criteria — all PASS:
    - LevelHints value type: `LevelHints.java` final class, 5 immutable boolean fields, `defaults()` factory → all false, equals/hashCode/toString
    - ISecurityLevelResolver interface: `ISecurityLevelResolver.java:36` `resolve(String, LevelHints) → SecurityLevel`, reuses plan 172 SecurityLevel (not redefined)
    - NoOpSecurityLevelResolver: `NoOpSecurityLevelResolver.java:16` final singleton, `noOp()` factory, all inputs → STANDARD (semantically correct §5.1 default, not a placeholder)
    - TestNoOpSecurityLevelResolver: 11/11 pass (NoOp all-standard + DesignSpecRuleTableResolver proving all 3 levels)
    - TestLevelHints: 5/5 pass (immutability + equals/hashCode + defaults)
  - Phase 2 Exit Criteria — all PASS:
    - Engine wiring: `DefaultAgentEngine.java:80` field init = NoOp, setter (null-check → NoOp fallback), getter, constructor chain unmodified
    - TestDefaultAgentEngineSecurityLevelResolverWiring: 5/5 pass (set/get identity, default=NoOp singleton, null fallback)
    - Backward-compat: full module test suite `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` BUILD SUCCESS
  - Closure Gates — all PASS:
    - Anti-Hollow: `ruleTableCanDistinguishAllThreeLevels` proves STANDARD+ELEVATED+RESTRICTED from one resolver instance; wiring set/get `assertSame` verified
    - SecurityLevel reused from plan 172 (not redefined)
    - Design §5.1 updated (contract landed + 2 architectural decisions); roadmap L2-13 → ✅
    - Deferred items: all 5 classified `out-of-scope improvement` with non-blocking rationale — no in-scope live defect downgraded
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` exit code 0
  - Deferred classification check: confirmed no in-scope live defect silently downgraded

Follow-up:

- Dispatch-path consultation integration (needs AgentExecutionContext channelKind/principal fields + LevelHints runtime-production chain — L2-13 ✅ + L2-14 ✅ joint prerequisite)
- Functional rule-table resolver (priority-5, replaces NoOp shipped default)
- L3-5 IApprovalGate (SecurityLevel downstream consumer)
- Matrix dispatch-path consultation (plan 172 deferral, needs this plan + L2-14 + AgentExecutionContext fields)

## Follow-up handled by 175-nop-ai-agent-dispatch-path-security-consultation.md

> Additive annotation (2026-06-14). This completed plan is historical record; this section only records successor traceability and does not alter the closure above.

The deferred "Dispatch-path 咨询调用" item (see `Deferred But Adjudicated` → "Dispatch-path 咨询调用（ReAct/工具分发路径中调用 resolve + matrix.check）" Successor Required: yes, and `Non-Blocking Follow-ups` first bullet) is being handled by successor plan [`175-nop-ai-agent-dispatch-path-security-consultation.md`](175-nop-ai-agent-dispatch-path-security-consultation.md).

Carry-over note for the successor: the successor wires `ISecurityLevelResolver.resolve(...)` + `IPermissionMatrix.check(...)` into the ReAct/tool-dispatch path. It requires (a) `AgentExecutionContext` + `AgentMessageRequest` channelKind/principal fields, (b) a LevelHints runtime-production component (trustedSource via `IContentTrustEvaluator`, writesOutsideWorkspace via tool-param×workDir analysis, needsNetwork/highImpact via tool-name classification), and (c) consultation-point insertion in `ReActAgentExecutor.execute()` after the existing Layer 1 checks. Both L2-13 ✅ and L2-14 ✅ are landed with contracts + pass-through defaults wired into `DefaultAgentEngine` via setters.
