# 175 nop-ai-agent Dispatch-Path Security Consultation Integration

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: dispatch-path-consultation

> Last Reviewed: 2026-06-14
> Source: Carry-over from plans 172 (L2-14 `IPermissionMatrix`) and 173 (L2-13 `ISecurityLevelResolver`) — both deferred "Dispatch-path 咨询集成" as `out-of-scope improvement, Successor Required: yes`. Now unblocked: L2-13 ✅ + L2-14 ✅ contracts + pass-through defaults + engine setter wiring all landed. Roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 2 (L2-13/L2-14 integration); design `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §5.1/§5.3 (consultation integration) + §8 (defense-in-depth chain)
> Related: Plan 172 (L2-14 — `IPermissionMatrix` + `PassThroughPermissionMatrix` + shared value types), Plan 173 (L2-13 — `ISecurityLevelResolver` + `NoOpSecurityLevelResolver` + `LevelHints`), Plan 147 (L1-9 — `IContentTrustEvaluator` + `DefaultContentTrustEvaluator`), Plan 169/170 (permission inheritance — wrapper pattern precedent), Plan 174 (per-agent path rules — tool dispatch loop context)

## Purpose

将 Layer 2 安全组件（`ISecurityLevelResolver` + `IPermissionMatrix`）的实际咨询调用接入 ReAct/tool-dispatch 路径，完成 plans 172/173 交付的契约表面的"最后一公里"。当前引擎通过 setter 持有 resolver 和 matrix（默认 = NoOp/PassThrough），但 `ReActAgentExecutor` 从未在工具分发时调用它们——契约表面存在，但从入口点到出口点的调用链未接通。本计划接通这条链：使工具分发路径在每次工具调用前（Layer 1 检查之后），解析安全等级并检查通道×等级矩阵，使功能化 resolver/matrix 实现注册后真正生效。

## Current Baseline

- **L2-14 ✅ delivered (plan 172)**: `IPermissionMatrix` 接口 + `PassThroughPermissionMatrix` 默认 + 共享值类型（`SecurityLevel`/`ChannelKind`/`Principal`/`PrincipalRole`/`MatrixDecision`）位于 `io.nop.ai.agent.security` 包。`DefaultAgentEngine` 通过 `setPermissionMatrix` / `getPermissionMatrix` 接线（field :80, setter/getter），默认 = PassThrough。dispatch-path 咨询明确 deferred
- **L2-13 ✅ delivered (plan 173)**: `ISecurityLevelResolver` 接口 + `NoOpSecurityLevelResolver` 默认 + `LevelHints` 值类型位于 security 包。`DefaultAgentEngine` 通过 `setSecurityLevelResolver` / `getSecurityLevelResolver` 接线（field :81），默认 = NoOp。dispatch-path 咨询明确 deferred
- **`LevelHints` 值类型已存在**: 不可变 flat value object，5 个布尔字段（trustedSource / writesOutsideWorkspace / crossesTrustBoundary / needsNetwork / highImpact），`defaults()` 工厂返回全 false
- **`IContentTrustEvaluator` 已存在 (plan 147)**: 接口 `isTrustedSource(ContentOrigin origin, AgentExecutionContext ctx)` 位于 security 包。默认实现 `DefaultContentTrustEvaluator`（CHANNEL_INPUT + AGENT_GENERATED = trusted, WEB_FETCH + FILE_READ = untrusted）。**未集成到执行路径**——接口存在但从未在 dispatch path 中被调用
- **`ContentOrigin` 已存在**: 枚举 {CHANNEL_INPUT, WEB_FETCH, FILE_READ, AGENT_GENERATED}
- **`AgentExecutionContext` 无 channelKind/principal 字段**: 当前字段为 `agentModel`, `messages`, `sessionId`, `chatOptionsModel`, `metadata`(Map), `status`, `cancelRequested` 等——无 `channelKind`、`principal`。通道与身份信息在执行上下文中无处获取（verified by reading `AgentExecutionContext.java`）
- **`AgentMessageRequest` 无 channelKind/principal 字段**: 当前仅有 `agentName`, `userMessage`, `sessionId`, `metadata`——无通道/身份来源（verified by reading `AgentMessageRequest.java`）
- **`ReActAgentExecutor` dispatch loop 不调用 resolver/matrix**: 工具分发循环（`execute()` lines 490-546）依次调用 `toolAccessChecker.checkAccess` → `permissionProvider.resolve` → `checkPathAccess`（Layer 1），但不调用 `securityLevelResolver.resolve` 或 `permissionMatrix.check`（Layer 2）。Layer 2 组件在引擎上通过 setter 持有，但从未传递给 executor，也从未在 dispatch loop 中被咨询（verified by reading `ReActAgentExecutor.java`）
- **`DefaultAgentEngine.resolveExecutor` 不传递 resolver/matrix**: `resolveExecutor` 方法（lines 657-688）构建 `ReActAgentExecutor` via Builder，传递 chatService/toolManager/eventPublisher/permissionProvider/toolAccessChecker/pathAccessChecker/hookRegistry/contextCompactor/contentGuardrail/modelRouter/tokenEstimator/talents/skillProvider/toolCallRepairer/engine/messenger，但**不传递** `securityLevelResolver` 或 `permissionMatrix`（verified by reading `DefaultAgentEngine.java:657-688`）
- **设计契约** (§5.1/§5.3/§8): 纵深防御链中，`ISecurityLevelResolver` 在 Tool Call 产生后、`IPermissionProvider`/`IPathAccessChecker` 之后被咨询，生产 `SecurityLevel`；`IPermissionMatrix` 消费 `SecurityLevel` + `ChannelKind` + `Principal` 返回 allow/deny 决策。两者构成 Layer 2 安全等级解析 + 通道矩阵咨询
- **dispatch loop 中的 path-arg 分析先例**: `ReActAgentExecutor` 已有 `PATH_ARG_KEYS` 集合（path/file/filePath/filename/directory/dir/destination/output/input/source/target/cwd）和 `checkPathAccess` 方法，从 tool-call arguments 中提取路径字符串并检查。LevelHints 的 `writesOutsideWorkspace` 评估可复用相同的 path-arg 提取逻辑
- **引擎接线模式**: Layer 2+ 组件通过 mutable field + setter + getter 接线（非构造器参数），field 在声明处初始化为 pass-through/noOp 默认。`ReActAgentExecutor` 通过 Builder 接收组件引用。两者共同构成"引擎持有 → executor 消费"的组件传递链

## Goals

- `AgentExecutionContext` + `AgentMessageRequest` 携带 `channelKind`（ChannelKind）和 `principal`（Principal）字段，使 dispatch path 能在工具分发时获取通道与身份信息。字段在请求中可选（null = 未知通道/匿名身份），默认时矩阵的 fail-closed 语义（仅 STANDARD）生效，PassThrough 默认不受影响
- 一个 LevelHints 运行时生产组件：给定 tool name、tool-call arguments、workDir、execution context，产生有语义区分的 `LevelHints`（非全 false 空壳）——`trustedSource` 经 `IContentTrustEvaluator` 评估，`writesOutsideWorkspace` 经 path-arg×workDir 分析，`needsNetwork`/`highImpact` 经 tool-name 分类。该组件集成到引擎和 executor，使 resolver 在 dispatch path 中获得有意义的 hints 输入
- `ReActAgentExecutor` 工具分发循环在 Layer 1 检查（toolAccessChecker / permissionProvider / pathAccessChecker）之后，新增 Layer 2 咨询步骤：解析 SecurityLevel → 检查矩阵 → deny 时阻止工具调用并返回携带可审计原因的错误响应。咨询链从 `DefaultAgentEngine` → `resolveExecutor` → `ReActAgentExecutor` Builder → dispatch loop 完整接通
- 端到端验证：注册功能化 resolver + 限制性 matrix 后，dispatch path 确实根据 action kind + hints + channel 产生 SecurityLevel 并据此 allow/deny 工具调用；使用 NoOp/PassThrough 默认时行为与接线前完全一致（向后兼容）
- 设计文档 §5.1/§5.3 更新：标记 dispatch-path 咨询已落地，从"deferred successor"变为"已接通"
- 单元测试覆盖：context 字段传播、hints 生产组件（真实 hint 区分）、consultation 点（deny 阻止 + allow 放行）、向后兼容

## Non-Goals

- **功能化规则表 resolver（设计 §5.1 的 shipped 默认）**: 不实现设计 §5.1 确定性规则表（fs.write+writesOutsideWorkspace → ELEVATED 等）作为 shipped 默认。`NoOpSecurityLevelResolver` 仍是 shipped 默认。功能化 resolver 在测试中验证 consultation 链路非空壳
- **功能化限制性 matrix（设计 §5.3 通道限制的 shipped 默认）**: 不实现设计 §5.3 通道限制规则表（webui=全部/api=STANDARD+ELEVATED 等）作为 shipped 默认。`PassThroughPermissionMatrix` 仍是 shipped 默认
- **Read/read-write access levels**: 不扩展 `PathAccessDecision` 超出 ALLOW/DENY。这是 plan 174 的独立 carry-over，依赖本计划的 tool-kind mapping 但属于不同的关注点
- **审批治理 (Layer 3)**: 不实现 `IApprovalGate` (L3-5)、`IDenialLedger` (L3-6)、`IPostDenialGuard` (L3-7)——它们是 SecurityLevel 的下游消费者
- **DSL/XDSL 配置化**: 不引入 `security-policy.xdef` 或规则表的 Delta 配置（设计 §9 渐进式增强）
- **ContentOrigin 追踪到 tool-call 级别**: 不实现完整的 content-origin 追踪链（从消息内容追溯到 tool-call 的信任来源）。`trustedSource` hint 在本计划中使用保守评估（agent 内部推理产生的 tool call 默认 trusted）
- **crossesTrustBoundary 的精确评估**: `crossesTrustBoundary` hint 在本计划中保守评估为 false（无可靠的运行时启发式）。精确评估需要 tool 元数据或跨系统调用追踪，是后续增强
- **完整 action-kind 映射表**: 不建立从所有系统工具名到设计 §5.1 action-kind 分类的穷尽映射表。使用 tool name 直接作为 action kind，功能化 resolver 按需处理已知/未知 kind

## Scope

### In Scope

- `AgentExecutionContext` 添加 `channelKind`（ChannelKind）+ `principal`（Principal）字段及 getter/setter
- `AgentMessageRequest` 添加 `channelKind` + `principal` 的可选来源（新构造器重载，旧构造器向后兼容委托为 null）
- `DefaultAgentEngine.doExecute()` 将 request 的 channel/principal 传播到 context
- LevelHints 运行时生产组件（接口 + 功能化默认实现），产生有语义区分的 hints
- `DefaultAgentEngine` 对 hints 生产组件的 setter 接线（默认 = 功能化默认实现）
- `ReActAgentExecutor` Builder 接收 `securityLevelResolver`、`permissionMatrix`、hints 生产组件
- `DefaultAgentEngine.resolveExecutor()` 将上述组件传递给 executor Builder
- `ReActAgentExecutor.execute()` dispatch loop 中新增 Layer 2 咨询步骤（resolve → matrix.check → deny 时阻止）
- 单元测试：字段传播 + hints 生产 + consultation 点（deny/allow）+ 向后兼容
- 端到端测试：功能化 resolver + 限制性 matrix 在 dispatch path 中实际 allow/deny 工具调用
- 设计文档 §5.1/§5.3 更新

### Out Of Scope

- 功能化规则表 resolver 的 shipped 实现（priority-5）
- 功能化限制性 matrix 的 shipped 实现（priority-5）
- Read/read-write access levels（plan 174 carry-over）
- Layer 3 审批治理组件（L3-5/L3-6/L3-7）
- DSL/XDSL 安全策略配置
- ContentOrigin 到 tool-call 级别的完整追踪链
- crossesTrustBoundary 的精确评估

## Execution Plan

### Phase 1 - Channel/Principal 字段 + Context 传播 + 单元测试

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentExecutionContext.java`, `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentMessageRequest.java`, `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java` (doExecute), `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/`

- Item Types: `Fix | Proof`

- [x] 向 `AgentMessageRequest` 添加 `channelKind`（ChannelKind）和 `principal`（Principal）的可选字段。添加包含这两个参数的新构造器重载（位于现有构造器之后），现有构造器委托为 null（向后兼容——所有现有调用方不受影响）
- [x] 向 `AgentExecutionContext` 添加 `channelKind`（ChannelKind）和 `principal`（Principal）字段（mutable，getter + setter，遵循现有 sessionId/chatOptionsModel 字段的 mutable-field 模式）。默认 null（未知通道/匿名身份）
- [x] 在 `DefaultAgentEngine.doExecute()` 中，将 request 的 channelKind/principal 传播到 `AgentExecutionContext`（在 `ctx.getMetadata().putAll(...)` 之后，`AgentExecutionContext.create` 之后）。当 request 未携带时，context 字段保持 null
- [x] 单元测试：构造带 channelKind/principal 的 `AgentMessageRequest` → 经 `doExecute` 执行 → 验证 `AgentExecutionContext` 的 channelKind/principal 与 request 一致（传播连通）；构造不带 channel/principal 的 request → context 字段为 null（向后兼容）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `AgentMessageRequest` 存在包含 channelKind/principal 参数的构造器重载；现有构造器编译和行为不变（旧调用方零影响）
- [x] `AgentExecutionContext` 存在 channelKind/principal 字段及 getter/setter；默认 null
- [x] **接线验证**: `doExecute` 中 request → context 的 channel/principal 传播已验证——带 channel/principal 的 request 产生带对应值的 context，不带的产生 null（非仅字段存在，而是传播路径可验证）
- [x] **无静默跳过**: 字段未设置时 getter 返回 null（语义正确的"未知"），非 placeholder 值；传播逻辑对 null 输入不做静默忽略而是正确设置 null
- [x] **新增功能测试**: 字段传播测试（带值 + 不带值）——全部通过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过（新增 + 现有测试）
- [x] No owner-doc update required（设计文档更新在 Phase 3）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - LevelHints 生产组件 + 引擎接线 + 单元测试

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/` (hints 生产组件), `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java` (setter + resolveExecutor), `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/security/`

- Item Types: `Decision | Proof`

- [x] 创建 LevelHints 生产组件的接口契约（位于 security 包），定义"给定 tool name、tool-call arguments、workDir、execution context，产生 LevelHints"的语义。接口遵循 security 包中 `IContentTrustEvaluator` / `ISecurityLevelResolver` 的契约风格
- [x] 创建功能化默认实现（位于 security 包），产生有语义区分的 LevelHints（非全 false 空壳）：
  - `trustedSource`: 经 `IContentTrustEvaluator` 评估（默认 `DefaultContentTrustEvaluator`，agent 内部推理产生的 tool call → CHANNEL_INPUT/AGENT_GENERATED → trusted）。保守评估为 true（agent 自身决策链是 trusted source），除非有明确的 untrusted 内容来源信号
  - `writesOutsideWorkspace`: 复用 `ReActAgentExecutor` 已有的 `PATH_ARG_KEYS` 提取逻辑，从 tool-call arguments 中提取路径值，与 agent 的 workDir（或 JVM CWD 当 workDir absent）比对——路径在 workDir 之外 → true
  - `needsNetwork`: 经 tool-name 分类——tool name 匹配已知网络工具集合（如 web_fetch / http_request / network_fetch 等）→ true
  - `highImpact`: 经 tool-name 分类——tool name 匹配已知高影响工具集合（如 shell_exec / bash / file_delete / git_push 等）→ true
  - `crossesTrustBoundary`: 保守评估为 false（无可靠运行时启发式——精确评估是后续增强）
- [x] 在 `DefaultAgentEngine` 添加 hints 生产组件的 mutable field + setter + getter（field 声明处初始化为功能化默认实现，遵循 `permissionMatrix`/`securityLevelResolver` 模式）
- [x] 单元测试验证 hints 生产的真实区分能力（非空壳）：
  - 读文件工具（在 workDir 内）→ trustedSource=true, writesOutsideWorkspace=false, highImpact=false
  - 写文件工具（路径在 workDir 外）→ writesOutsideWorkspace=true
  - shell 执行类工具 → highImpact=true
  - 网络请求类工具 → needsNetwork=true
  - 无路径参数的工具 → writesOutsideWorkspace=false
  - 空参数 / null 参数 → 不抛异常，返回合理 hints

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] LevelHints 生产组件接口 + 功能化默认实现存在，位于 `io.nop.ai.agent.security` 包
- [x] **Anti-Hollow Check（hints 功能性）**: 功能化默认实现产生有语义区分的 hints——不同 tool name + arguments 组合产生不同的 LevelHints（writesOutsideWorkspace / highImpact / needsNetwork 至少 3 个 hint 在不同输入下有 true/false 区分），非全 false 空壳
- [x] **无静默跳过**: hints 生产组件对未知 tool name / null arguments / 空参数返回合理的保守 hints（非异常、非空方法体），对可评估的 hint 做真实评估而非全部默认 false
- [x] `DefaultAgentEngine` 持有 hints 生产组件字段（默认 = 功能化默认实现），setter + getter 存在；setter 对 null 输入有合理处理（回退默认或拒绝，非静默忽略）
- [x] **新增功能测试**: hints 生产组件测试覆盖至少 5 种 tool name × arguments 组合（read-in-workspace / write-outside-workspace / shell / network / no-args），验证 hints 真实区分——全部通过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过（新增 + 现有测试）
- [x] No owner-doc update required（设计文档更新在 Phase 3）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Consultation 点插入 + Executor 接线 + 端到端验证 + 设计文档

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java` (Builder + dispatch loop), `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java` (resolveExecutor), `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/` 或 `security/`, `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §5.1/§5.3

- Item Types: `Fix | Proof | Follow-up`

- [x] 向 `ReActAgentExecutor.Builder` 添加 `securityLevelResolver`、`permissionMatrix`、hints 生产组件的接收方法。Builder build() 中对 null 输入设置默认值（NoOp / PassThrough / 功能化默认），遵循现有 Builder 模式
- [x] `DefaultAgentEngine.resolveExecutor(model, toolAccessChecker, pathAccessChecker)` 在构建 `ReActAgentExecutor` 时，将引擎自身的 `securityLevelResolver`、`permissionMatrix`、hints 生产组件传递给 Builder
- [x] 在 `ReActAgentExecutor.execute()` 的 dispatch loop 中（Layer 1 检查通过后、`allowedCalls.add(chatToolCall)` 之前），插入 Layer 2 咨询步骤：
  1. 使用 hints 生产组件为当前 tool call 产生 `LevelHints`
  2. 调用 `securityLevelResolver.resolve(toolName, hints)` → `SecurityLevel`
  3. 从 `ctx` 读取 `channelKind` + `principal`
  4. 调用 `permissionMatrix.check(channelKind, principal, level)` → `MatrixDecision`
  5. 如果 `MatrixDecision.isDenied()` → 记录审计日志（`AuditEvent` + `AuditDecision.DENY` + reason）、发布 `TOOL_CALL_DENIED` 事件、返回携带可审计原因的 `ChatToolResponseMessage.error(...)`，不加入 `allowedCalls`（与现有 Layer 1 deny 路径一致）
  6. 如果 allowed → 继续加入 `allowedCalls`
- [x] 端到端测试（dispatch-path consultation 非空壳验证）：注册功能化 resolver（实现设计 §5.1 规则表的测试内部类，如 `shell.exec` + `!trustedSource` → ELEVATED）+ 限制性 matrix（设计 §5.3 测试内部类，如 GROUP channel + ELEVATED → deny）。构造带 GROUP channelKind 的 request + shell-exec 工具调用 → dispatch path 实际 deny 该工具调用（非仅接口存在，而是从 request 到 tool-call-blocked 的完整路径可验证）。同时验证 WEBUI channel + STANDARD tool → allow
- [x] 向后兼容测试：不设 resolver/matrix（使用 NoOp/PassThrough 默认）的引擎执行 ReAct 循环，行为与接线前完全一致——全部现有测试通过，无 spurious 拒绝
- [x] **接线验证测试**: 在端到端测试中添加断言验证 `securityLevelResolver.resolve(...)` 和 `permissionMatrix.check(...)` 确实在 dispatch loop 中被调用（如使用计数器 mock 或 verify），而非仅接口被传递
- [x] 更新设计文档 `nop-ai-agent-security-and-permissions.md` §5.1 和 §5.3：标记 dispatch-path 咨询已从"deferred successor"变为"已接通"。记录三个架构决策：(1) channel/principal 经 AgentMessageRequest → AgentExecutionContext 传播（可选字段，null = 未知/匿名）；(2) LevelHints 生产组件作为独立可插拔组件（接口 + 功能化默认），trustedSource/writesOutsideWorkspace 有真实评估，crossesTrustBoundary 保守为 false；(3) consultation 点位于 Layer 1 检查之后，deny 路径与 Layer 1 deny 一致（审计 + 事件 + error response）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ReActAgentExecutor.Builder` 接收 securityLevelResolver / permissionMatrix / hints 生产组件；`DefaultAgentEngine.resolveExecutor` 传递这三个组件
- [x] **端到端验证**: 功能化 resolver + 限制性 matrix 注册后，从 `AgentMessageRequest` 到 `ReActAgentExecutor.execute()` 到工具调用被 denied 的完整路径已验证——GROUP channel + ELEVATED action → deny，WEBUI channel + STANDARD action → allow（Minimum Rules #22 Anti-Hollow Rule）
- [x] **接线验证**: 端到端测试中 `resolver.resolve(...)` 和 `matrix.check(...)` 的调用已被断言验证（mock verify 或计数器），而非仅组件被传递（Minimum Rules #23 Wiring Verification Rule）
- [x] **无静默跳过**: consultation 步骤在 NoOp/PassThrough 默认下不产生拒绝（矩阵放行一切），在功能化实现下产生真实 allow/deny——非空方法体/continue 跳过/吞异常（Minimum Rules #24）。deny 路径记录审计日志并返回 error response（与 Layer 1 deny 一致），非静默忽略
- [x] **新增功能测试**: consultation 点测试（deny 阻止 + allow 放行 + 审计日志记录 + 向后兼容）+ 接线验证测试——全部通过
- [x] **向后兼容**: 不设 resolver/matrix 的引擎执行全部现有测试通过，0 spurious 拒绝
- [x] 设计文档 §5.1/§5.3 更新：dispatch-path 咨询标记为已接通 + 三个架构决策记录
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过（全部新增 + 现有测试）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `AgentExecutionContext` + `AgentMessageRequest` channel/principal 字段存在，doExecute 传播路径已验证
- [x] LevelHints 生产组件存在并产生有语义区分的 hints（非全 false 空壳），经测试验证
- [x] `ReActAgentExecutor` dispatch loop 包含 Layer 2 consultation 步骤（resolve + matrix.check），deny 路径与 Layer 1 deny 一致（审计 + 事件 + error response）
- [x] consultation 链从 `DefaultAgentEngine` → `resolveExecutor` → Builder → dispatch loop 完整接通，经端到端测试验证
- [x] 功能化 resolver + 限制性 matrix 在端到端测试中实际 allow/deny 工具调用（非空壳）
- [x] NoOp/PassThrough 默认向后兼容，现有全部测试通过
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs（设计 §5.1/§5.3）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**: closure audit 验证 (a) consultation 链在运行时确实连通（resolver.resolve + matrix.check 在 dispatch loop 中被调用），(b) hints 生产组件产生真实区分的 hints（非全 false），(c) deny 路径有审计日志 + error response（非静默跳过），(d) 端到端路径从 request 到 tool-call-blocked 完整走通
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 功能化规则表 resolver（设计 §5.1 确定性规则的 shipped 默认）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `NoOpSecurityLevelResolver` 是 roadmap 指定的 shipped 默认。本计划接通 consultation 链并在测试中用功能化 resolver 验证链路非空壳。设计 §5.1 的规则表 shipped 实现归类为 priority-5，在 NoOp 默认之上独立交付。
- Successor Required: no

### 功能化限制性 matrix（设计 §5.3 通道限制规则的 shipped 默认）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `PassThroughPermissionMatrix` 是 roadmap 指定的 shipped 默认。本计划接通 consultation 链并在测试中用限制性 matrix 验证链路非空壳。设计 §5.3 的通道限制 shipped 实现归类为 priority-5。
- Successor Required: no

### crossesTrustBoundary 精确评估

- Classification: `optimization candidate`
- Why Not Blocking Closure: `crossesTrustBoundary` hint 在功能化默认实现中保守为 false。精确评估需要 tool 元数据（tool 是否涉及外部系统调用）或跨系统调用追踪，当前无可靠运行时启发式。其他 4 个 hint 有真实评估，consultation 链路功能完整。精确评估是 hints 生产组件的独立增强。
- Successor Required: no

### ContentOrigin 到 tool-call 级别的完整追踪链

- Classification: `optimization candidate`
- Why Not Blocking Closure: `trustedSource` hint 在本计划中保守评估为 trusted（agent 内部推理）。完整追踪链（从 web-fetched/file-read 内容追溯到其影响的 tool call 的信任级别）需要 content-origin 标注在消息流中的传播机制，是独立的 content-trust 增强基础设施。
- Successor Required: no

### DSL/XDSL 安全策略配置（security-policy.xdef + Delta 覆盖）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计 §9 渐进式增强将 DSL 配置化为后续增强。resolver/matrix/hints 生产组件当前通过 setter 程序化注入，与 talent/guardrail/messenger 一致。
- Successor Required: no

### 完整 action-kind 映射表

- Classification: `optimization candidate`
- Why Not Blocking Closure: 本计划使用 tool name 直接作为 action kind，功能化 resolver 按需处理已知/未知 kind（设计 §5.1 "other" 行）。完整映射表（所有系统工具名 → 设计 §5.1 action-kind 分类）是功能化 resolver 实现的配套增强。
- Successor Required: no

## Non-Blocking Follow-ups

- 功能化规则表 resolver 实现（设计 §5.1 规则表的 shipped 版本，替代 NoOp 默认——priority-5）
- 功能化限制性 matrix 实现（设计 §5.3 规则表的 shipped 版本，替代 PassThrough 默认——priority-5）
- L3-5（`IApprovalGate`）：SecurityLevel 的下游消费者，需审批等级触发人类审批
- L3-6（`IDenialLedger`）、L3-7（`IPostDenialGuard`）：审批治理链的拒绝计数和盲重试阻止
- Read/read-write access levels（plan 174 carry-over）：扩展 PathAccessDecision 超出 ALLOW/DENY
- ContentOrigin 追踪链增强：从 web-fetched/file-read 内容到 tool-call 信任级别的完整追踪
- SecurityLevel/MatrixDecision 的审计集成：决策流入现有 `IAuditLogger` / `AuditEvent` 路径（本计划已在 consultation deny 路径中集成基本审计，但完整的审计查询/分类可增强）

## Closure

Status Note: Layer 2 dispatch-path consultation 已完整接通——`AgentMessageRequest`/`AgentExecutionContext` 携带 channel/principal（可选，向后兼容），`ILevelHintsProducer`（功能化默认）运行时生产有语义区分的 LevelHints，`ReActAgentExecutor` dispatch loop 在 Layer 1 检查之后调用 `resolver.resolve` + `matrix.check`，deny 路径与 Layer 1 一致（审计 + 事件 + error response）。NoOp/PassThrough 默认保持向后兼容。3 个 Phase 全部完成，in-scope 工作无剩余 debt；6 个 deferred 项均为 priority-5/optimization/out-of-scope，明确 non-blocking。
Completed: 2026-06-14

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session, task_id `ses_13b49bc52ffe9q4kbCiP8CopDM`, explore subagent）—— 与实现 session 不同的独立 audit pass
- Audit Methodology: 逐文件读取 live source + tests + 执行完整模块测试套件
- Evidence:
  - Phase 1 Exit Criteria — **PASS**: `AgentMessageRequest.java:15-16,18-26`（channelKind/principal 字段 + 6-arg 构造器）；`AgentMessageRequest.java:28-31`（4-arg 委托 null）；`AgentExecutionContext.java:33-34,177-191`（mutable 字段 + getter/setter）；`DefaultAgentEngine.java:508-509`（doExecute 传播 ctx.setChannelKind/setPrincipal）。测试 `TestChannelPrincipalPropagation`（9）含 CapturingEngine 接线验证。
  - Phase 2 Exit Criteria — **PASS (Anti-Hollow)**: `DefaultLevelHintsProducer.java` 产生有语义区分的 hints——`trustedSource` 经 IContentTrustEvaluator（:80-83），`writesOutsideWorkspace` 经 ToolPathArgKeys 提取 + workDir 比对（:97,118,128,133），`needsNetwork`/`highImpact` 经 tool-name 分类（:45-52,145），`crossesTrustBoundary` 保守 false（:74）。引擎接线 `DefaultAgentEngine.java:84,329-341`。测试 `TestDefaultLevelHintsProducer`（21）+ `TestDefaultAgentEngineLevelHintsProducerWiring`（5）。
  - Phase 3 Exit Criteria — **PASS (端到端 + 接线)**: `ReActAgentExecutor.java:323,333,344`（Builder 方法）+ `:126-145,164-172`（构造器）+ dispatch loop `:563,581,599 → 615-616(checkLayer2Consultation) → 626(allowedCalls.add)`；`checkLayer2Consultation:969-997` 调用 produce→resolve→check，deny 时 AuditEvent(:986) + TOOL_CALL_DENIED 事件(:989) + return reason(:994)。引擎传递 `DefaultAgentEngine.java:714-716`。测试 `TestDispatchPathSecurityConsultation`（5）含 GROUP+RESTRICTED→deny、WEBUI+RESTRICTED→allow、CountingResolver/CountingMatrix AtomicInteger 计数器接线断言、NoOp/PassThrough 向后兼容。
  - Closure Gates — **PASS**: NoOpSecurityLevelResolver 返回 STANDARD（:28-30）、PassThroughPermissionMatrix 返回 allow（:26-28）→ 0 spurious denial；设计 §5.1/§5.3 标记"已接通（plan 175 ✅）"（无"推迟"/"deferred"残留）；6 deferred 项全部 honest（pre-stated Non-Goals，无 in-scope defect 降级）。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（无未勾选项 + Closure Evidence 已写入）。
  - Anti-Hollow 检查结果：三条独立证据——(1) 源码追踪 `checkLayer2Consultation:969-997` 按序调用 produce→resolve→check，deny 发 AuditEvent + 事件；(2) CountingResolver.resolveCount/CountingMatrix.checkCount 在 deny(:193-198) 与 allow(:238-241) 测试中均断言 >=1（hollow 则为 0）；(3) 完整模块测试套件 `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 实跑 1032 tests, 0 failures, 0 errors, BUILD SUCCESS（closure-audit 复跑确认）。`scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high` 退出码 1——14 findings 全部为 pre-existing `UnsupportedOperationException` deferral（`DefaultAgentEngine.java:723` mode=plan、`IAgentEngine.java:38/42/46` Phase 2 ISessionStore、`NoOpHookRegistry.java:20`、`IAiMemoryStore.java:16/20/24/28` Phase 2、`ISessionStore.java:51/55/59/63/67` VfsSessionStore），均位于 plan 175 未触碰的文件，且使用 plan guide rule #24 明确背书的"暂缓实现抛 UnsupportedOperationException"模式。plan 175 自身代码（`DefaultLevelHintsProducer`、`ILevelHintsProducer`、`checkLayer2Consultation`、Builder 三方法、doExecute channel/principal 传播）引入 0 个 hollow finding。
  - Deferred 项分类检查：6 项均为 priority-5/optimization-candidate/out-of-scope-improvement，无 in-scope live defect/contract drift/owner-doc drift/硬门禁失败项被降级。

Follow-up:

- 功能化规则表 resolver shipped 实现（priority-5，替代 NoOp 默认）
- 功能化限制性 matrix shipped 实现（priority-5，替代 PassThrough 默认）
- crossesTrustBoundary 精确评估（optimization，需 tool 元数据）
- ContentOrigin → tool-call 信任追踪链（optimization，需 content-origin 标注传播基础设施）
- L3-5/L3-6/L3-7 审批治理组件（SecurityLevel 下游消费者）
- 无剩余 plan-owned in-scope work

## Follow-up handled by 176-nop-ai-agent-approval-gate.md

L3-5（`IApprovalGate` + `AutoApproveGate`）由 plan 176 接管。本计划 Non-Blocking Follow-ups 中的 "L3-5（`IApprovalGate`）：SecurityLevel 的下游消费者，需审批等级触发人类审批" 现由 176 落地：审批门契约 + pass-through 默认 + dispatch-path 咨询点（位于 Layer 2 matrix allow 之后），使 ELEVATED/RESTRICTED 等级可被执行拦截。L3-6（`IDenialLedger`）和 L3-7（`IPostDenialGuard`）仍为独立后续工作项。
