# Nop AI Agent 安全与权限设计

**日期**：2026-06-08（v2 — 四层接口组织 + agent-survey 调研融合）
**范围**：`nop-ai-agent` 安全子系统
**状态**：active

---

## 1. 目标

本篇定义 Agent 的安全边界，回答五个问题：

1. Agent 如何限制可调用工具
2. Agent 如何限制可访问文件和路径
3. 子 agent 如何继承且不能提升权限
4. 外部内容如何被隔离以防注入攻击
5. 高风险操作如何被治理（审批、拒绝、暂停）

## 2. 设计原则

安全设计遵循五条原则：

1. **权限默认收敛** — deny by default，显式 allow 才放行
2. **程序校验优先于 prompt 约束** — 安全规则必须可测试、可审计、可复现
3. **子 agent 只能继承或收缩权限，不能提升** — call-agent 是能力扩张入口
4. **渐进式增强** — Layer 1 只做最简 deny/allow，Layer 2 引入分级策略，Layer 3 引入审批治理，Layer 4 引入平台级安全。每层通过 XDSL 模型引入更多假定，内部运行时始终最简化
5. **纵深防御** — 不依赖单一安全层。Tool dispatch、Tool handler、文件系统三层各自独立检查

## 3. 四层接口组织

```
Layer 4: Platform Security (平台安全层)
   ISandboxBackend, ISensitivePathProvider
   ─── 依赖 Layer 1-3 ───

Layer 3: Approval Governance (审批治理层)
   IApprovalGate, IDenialLedger, IPostDenialGuard
   ─── 依赖 Layer 1-2 ───

Layer 2: Policy Extensions (策略扩展层)
   ISecurityLevelResolver, IContentGuardrail, IPermissionMatrix
   ─── 依赖 Layer 1 ───

Layer 1: Core Security (核心安全层)
   IPermissionProvider, IToolAccessChecker, IPathAccessChecker, IAuditLogger
   ─── 无内部依赖 ───
```

**依赖规则**：上层只依赖下层的接口。每层都有 pass-through 默认实现——系统可以只带 Layer 1 运行。

**IAuditLogger 位于 Layer 1 的理由**：审计日志是安全底线决策的记录机制。Layer 1 的 deny/allow 决策如果没有审计记录，则 Layer 2-3 的分级策略和审批治理就没有可追溯的基线。IAuditLogger 的默认实现为 `Slf4jAuditLogger`（写入标准日志，单进程够用），Layer 4 的持久化审计（写入数据库）通过替换此接口实现升级。

## 4. Layer 1: Core Security（核心安全层）

系统运行的最低安全要求。没有这些接口，Agent 无法安全执行任何工具。

### 4.1 IPermissionProvider — 权限派生

**接口**：3-source merge（opencode 模式）

```
Permission resolve(toolName, agentName, sessionId)
```

> **Note**: `channelKind` parameter deferred to L2-14 (`IPermissionMatrix`). L1-6 interface omits it.

**三个来源**（按优先级）：

| 来源 | 配置位置 | 说明 |
|------|---------|------|
| Session 级临时规则 | 运行时参数 | 最高优先级 |
| Agent 专属规则 | `agent.xdef` 或 `.permissions/agent-{name}.yml` | Agent DSL 声明 |
| 默认规则 | `.permissions/agent-default.yml` | 全局基线 |

**合并语义**：deny-first（先匹配 deny，再匹配 allow，未命中默认拒绝）。

**默认实现**：`DefaultPermissionProvider`（L1-6 ✅ 已实现）。实现类名从设计文档原始名 `HierarchicalPermissionProvider` 更名为 `DefaultPermissionProvider`。

### 4.2 IToolAccessChecker — 工具访问检查

**职责**：每次工具执行前检查 allow/deny。

**规则语义**：

1. 先匹配 deny → 拒绝
2. 再匹配 allow → 放行
3. 未命中 allow → 默认拒绝（fail-closed）

**硬编码 deny（不可被 DSL 覆盖）**：与 OpenSquilla 的 `HARDCODED_ADMIN_ONLY` 模式对齐——shell_exec、file_write、file_delete、git_push 等高风险工具在未显式授权时默认拒绝。

**为什么不能只靠 prompt**：

- 模型可能幻觉工具名
- prompt 不能替代安全边界
- 安全规则必须可测试、可审计、可复现

### 4.3 IPathAccessChecker — 路径访问检查

**职责**：每次文件操作前检查路径权限。

**访问级别**：`allow` | `deny`（两值模型，已交付 ✅）。设计原始构想的 `read | read-write | deny` 三值模型延后为未来精化，绑定 tool-kind 分析（L2-13/L2-14）。

**路径匹配**：glob 模式（`AntPathMatcher`），从上到下匹配，第一条命中生效（within-agent first-match-wins）。

**Per-agent glob 路径规则（已交付 ✅，Plan 174）**：Agent 可在 DSL 中声明 `<path-rules>` — 一个有序的 glob 模式列表，每个模式带 `allow` 或 `deny` 决策。`RuleBasedPathAccessChecker` 在 agent 自身规则集内执行 first-match-wins 评估：第一条匹配的模式决定结果 — `deny` → 拒绝，`allow` → 委托给被包装的全局 checker，无匹配 → 委托给被包装的全局 checker。架构决策：

1. **访问级别模型 = allow/deny**（两值），匹配当前 `DefaultPathAccessChecker` 的 deny/allow 语义。`read | read-write | deny` 三值模型延后（需要 tool-kind 分析，绑定 L2-13/L2-14）。
2. **within-agent 评估策略 = first-match-wins**（设计 §4.3："从上到下匹配，第一条命中生效"）。第一条匹配的模式决定结果，Agent 通过规则排序控制优先级。
3. **跨层评估策略 = deny-wins**（任何父 DENY 匹配 → 拒绝）。跨委派层（父 → 子），父约束的路径规则扫描全部规则，任何 DENY 匹配即拒绝。这与 within-agent 的 first-match-wins 不同，是安全必需的：父的 deny 不能被子或更早的父 allow 覆盖。
4. **无匹配回退 = 委托全局 deny-list**。per-agent 规则是叠加在全局 deny-list 之上的额外限制，不是替代。Agent 无法 ALLOW 全局 deny-list 拒绝的路径（如 `~/.ssh/**`）。
5. **规则链累积**：嵌套委派（A → B → C）中，B 的 effective path-rules = incoming parent rules + B's own rules（拼接，父规则在前）。累积链由 C 的 wrapper 以 deny-wins 评估。C 继承完整约束链 — A 的 deny 即使 B 未重新声明也对 C 生效。
6. **与 workDir root-confinement 共存**（Plan 170）。约束可同时携带 `allowedPathRoots`（来自 workDir）和 `allowedPathRules`（来自 `<path-rules>`）。两者都 PRESENT 时，wrapper 同时检查两个维度：路径必须在允许根下 AND 通过所有 deny-rules。任一维度可独立拒绝。fail-closed（最严格）。

**`**`-leading 模式的 form 调整**：`**`-leading 模式（如 `**/secrets/**`）被设计为 workDir 无关的。实现中，当解析路径为绝对路径时，`**`-leading 模式自动前补 `/`（变为 `/**/secrets/**`）以匹配绝对路径。非 `**`-leading 的相对模式（如 `src/**`）仅匹配相对路径（当 workDir ABSENT 时）。

**路径规范化**（必须防御）：

- `..` 目录穿越
- 符号链接绕过
- 绝对路径逃逸
- 大小写折叠（Windows）

**系统目录默认保护**：

| 路径 | 默认级别 |
|------|---------|
| `.nop/**` | read-only |
| `.nop/.permissions/**` | deny |
| `.nop/.system/**` | read-only |

**敏感路径默认保护**（参考 OpenSquilla `sensitive_paths.py`）：

| 类别 | 路径 |
|------|------|
| SSH 密钥 | `~/.ssh/**` |
| 云凭证 | `~/.aws/**`, `~/.azure/**`, `~/.config/gcloud/**`, `~/.kube/**` |
| 凭证文件 | `**/.env`, `**/.env.*`, `**/id_rsa`, `**/id_ed25519`, `**/.netrc` |
| Shell 历史 | `**/.bash_history`, `**/.zsh_history` |
| 系统目录 | `/etc/**`, `/boot/**`, `/sys/**`, `/proc/**`, `/root/**` |

**默认实现**：`DefaultPathAccessChecker`——全局静态 deny-list + 路径规范化（无 per-agent 状态）。`RuleBasedPathAccessChecker`——per-agent glob 规则评估（first-match-wins，包装全局 checker）。敏感路径 denylist 可通过 XDSL 外部配置（未来，`ISensitivePathProvider` L4）。

### 4.4 Subagent 权限继承

**基本规则**：子 Agent 只能继承父 Agent 的权限，不能提升。

**继承语义**：

- 工具权限 = 父权限 ∩ 子配置（交集或收缩）— **已交付** ✅
- 文件权限 = 父权限 ∩ 子配置（交集或收缩）— **已交付** ✅
- 未明确授权的提升行为一律拒绝

**为什么**：call-agent 是能力扩张入口。如果子 Agent 可以提升权限，任意 prompt 都可能绕过父 Agent 约束。

**工具权限继承实施（已交付，Plan 169）**：

工具权限继承通过三个架构决策实现：

1. **约束表示（constraint representation）**：父 Agent 的约束携带其 **effective（clamped）allowed tool set** — 即父 Agent 在当前执行中实际可调用的工具名集合，而非其 DSL 声明的集合。对于顶层 Agent（无 incoming parent constraint），effective set 等于其 declared set（`AgentModel.getTools()`）。对于嵌套 Agent，effective set 是 incoming parent constraint 与自身 declared set 的交集。使用 effective（clamped）set 而非 declared set 是嵌套委派安全的关键：当中间 Agent B 委派给 C 时，C 的约束基于 B 的 effective set（已被 A 的约束 clamp），因此 C 无法重新获得 A 从 B 中移除的工具。约束是不可变的值对象，携带 allowed tool names + parent agent name + parent session ID（审计追溯）。

2. **执行机制（enforcement mechanism）**：在 `DefaultAgentEngine` 的 executor-resolution 时，用 `ParentConstrainedToolAccessChecker` 包装子 Agent 的 `IToolAccessChecker`。当约束存在且请求的工具不在父 Agent 的 effective set 中时，fail-closed 拒绝（拒绝原因明确标识 "parent permission constraint"）；当工具在父 Agent 的 set 中时，委托给被包装的 checker（子 Agent 自身规则仍叠加生效）；当无约束时（单 Agent 执行），wrapper 是 no-op pass-through。此包装不修改引擎自身的 `toolAccessChecker` 字段，仅在子 Agent 执行范围内生效。

3. **约束传播（constraint propagation）**：约束通过 `AgentMessageRequest.metadata` 在 well-known key `"parentPermissionConstraint"` 下传播，复用现有 metadata 基础设施。`CallAgentExecutor` 从 `AgentToolExecuteContext.getAllowedTools()` 读取父 Agent 的 effective set 并构建约束；`DefaultAgentEngine.doExecute()` 从 metadata 读取约束并包装 checker。`ReActAgentExecutor` 在构造 `AgentToolExecuteContext` 时计算 effective set = `incomingParentConstraint ∩ agentModel.getTools()`。

**文件权限继承实施（已交付，Plan 170）**：

文件权限继承复用工具权限继承的三个架构决策（同一约束对象、同一传播机制、同一包装时机），通过五个决策实现：

1. **路径范围表示（path-scope representation）**：`ParentPermissionConstraint` 扩展为携带 additive `allowedPathRoots` 字段（类型 `Set<String>`，规范化绝对目录根）。`null` 表示 ABSENT（无声明路径范围 → 无路径约束）；非 null Set（含空集）表示 PRESENT（约束激活 — 路径不在这些根下则拒绝）。PRESENT({}) = 拒绝所有路径（最大限制，如嵌套 clamp 塌缩为空时）。现有的 tool-only 构造器委托为 `allowedPathRoots = null`（ABSENT），所有 plan-169 构造路径和测试不受影响。三值语义（ABSENT/PRESENT/PRESENT({})）与工具集表示对称。

2. **路径范围 clamp（nested delegation clamping）**：Agent 的 effective path roots = `incomingParentRoots ∩ ownDeclaredRoots`，其中 ABSENT 作为单位元（ABSENT ∩ X = X），PRESENT 作为集合操作。Agent 自身声明的根 = PRESENT({normalized workDir})（当 `workDir != null`），ABSENT（当 null）。具体 clamp 规则：当 incoming parent roots 为 PRESENT 时，effective roots = agent 自身声明根中位于 incoming parent roots 之下的子集（`isUnderAnyRoot` 判定）；无任何自身根位于父根之下时 → PRESENT({})（拒绝所有路径）。这使得嵌套委派安全：中间 Agent B 的 effective roots 已被 A clamp，当 B 委派给 C 时，C 继承 B clamp 后的范围而非 B 声明的范围。

3. **路径范围来源（scope source）**：父 Agent 的声明路径范围来自其 `workDir`。`AgentModel` 通过 agent DSL schema `nop/schema/ai/agent.xdef` 新增 `workDir` 属性（type string，default null = ABSENT）。`ReActAgentExecutor` 将 `agentModel.getWorkDir()`（解析为 `File` 或 null）传入 `AgentToolExecuteContext`，替代此前硬编码的 `new File(".")`（JVM CWD）。非 null workDir → PRESENT({normalized workDir})；null/absent workDir → ABSENT（无约束）。这是一个最小、additive 的 DSL/schema 变更（一个可选字符串属性），不引入 per-agent 路径规则模型。

4. **执行机制（enforcement mechanism）**：`ParentConstrainedPathAccessChecker` 包装 `IPathAccessChecker`。语义：(a) 约束的 `allowedPathRoots` 为 ABSENT → 完全委托（no-op pass-through，向后兼容）；(b) PRESENT 且请求路径（规范化后）不在任何允许根之下 → 拒绝（fail-closed），通过 `PathAccessResult.deny(reason, matchedRule)` 工厂产生，reason 标识 "parent path permission constraint" 且包含父 Agent 名称和违规路径，matchedRule 标记为 `"parent_path_permission_constraint"`；(c) PRESENT 且路径在允许根之下 → 委托给被包装的 checker（全局 deny-list 和子 Agent 自身规则仍叠加生效）。路径匹配：路径 P 在根 R 之下当且仅当 P 的规范化绝对形式等于 R 或以 `R + "/"` 开头。规范化复用 `DefaultPathAccessChecker.normalizePathStatic()`（tilde 展开、反斜杠→正斜杠、`Paths.get(p).normalize()`）。相对路径先对子 Agent 的 `workDir` 解析再匹配。

5. **约束传播（constraint propagation）**：路径根通过同一 `ParentPermissionConstraint` 对象、同一 metadata key（`"parentPermissionConstraint"`）传播，复用 plan-169 的 metadata 基础设施。`CallAgentExecutor.buildParentConstraint()` 从 `AgentToolExecuteContext.getAllowedPathRoots()` 读取当前 Agent 的 effective path roots 并纳入约束；`DefaultAgentEngine.doExecute()` 从 metadata 读取同一约束对象，同时包装 tool checker（已有）和 path checker（新增）。`DefaultAgentEngine.resolveEffectivePathAccessChecker()` 是 `resolveEffectiveToolAccessChecker()` 的类比，当约束的 path roots 为 PRESENT 时返回 `ParentConstrainedPathAccessChecker`，否则返回引擎自身的 `pathAccessChecker`。

**Per-agent glob 路径规则模型（已交付，Plan 174）**：Plan 174 在上述 root-based 范围来源之上交付了 per-agent glob allow/deny 路径规则模型。Agent 可在 DSL 中声明 `<path-rules>`（`PathRuleModel` 由 codegen 从 `agent.xdef` 生成）。`RuleBasedPathAccessChecker` 在 agent 自身规则集内执行 first-match-wins 评估（DENY → 拒绝，ALLOW/无匹配 → 委托全局 checker）。引擎在 executor-resolution 时解析 per-agent checker：声明了 `<path-rules>` 的 agent 获得 `RuleBasedPathAccessChecker` 包装全局 checker，未声明的 agent 获得全局 checker 不变（向后兼容）。组合顺序：全局 deny-list（最内层）→ per-agent 规则 → 父约束（最外层）。路径必须通过所有层。继承机制（plan 170 的 `ParentConstrainedPathAccessChecker`）被 additively 扩展以携带和跨委派层强制执行路径规则（deny-wins 跨层语义），复用现有的 wrapper 和传播机制。

### 4.5 日志与审计

安全相关执行记录：

- sessionId, agentName, actorId
- toolName, 访问路径
- 权限命中规则
- 拒绝原因

### 4.6 默认装配策略（Secure by Default）

**决策**：`DefaultAgentEngine` 的全部短构造器与字段兜底默认装配 `DefaultToolAccessChecker` 与 `DefaultPathAccessChecker`，而不是 `AllowAllToolAccessChecker` / `AllowAllPathAccessChecker`。集成商使用 `new DefaultAgentEngine(chatService, toolManager)` 开箱即获得：(a) 对 deny-list 危险工具（`bash`、`write-file`、`delete-file`、`move-file`、`patch-file`、`apply-delta`、`http-request`、`graphql-query`，大小写不敏感）的拒绝；(b) 对敏感路径前缀（`~/.ssh/`、`~/.aws/`、`/etc/` 等）与凭证文件（`.env`、`id_rsa` 等）的拒绝。`AllowAll*` 类保留为 public，集成商可通过显式构造器参数 opt-in 全放行（用于测试或可信环境）。

**为什么选这个方案**：开箱默认必须以"对调用方最危险的选项"为假设——若集成商忘记装配 checker，后果应当是"安全地拒绝危险调用"而非"无声地全放行"。`Default*` 实现已存在并经过单元测试覆盖，本决策只是切换默认装配点，不引入新代码。

**为什么拒绝替代方案**：

1. **"仅 WARN 不改默认值"**：不采用。WARN 在生产部署中常被日志噪声淹没，且无法替代默认值本身的安全语义——一旦集成商漏读日志，引擎仍处于全放行状态。本设计同时采用 WARN 与默认值切换：默认值切换提供实际安全边界，WARN 提供降级可见性。
2. **"引入全局开关配置项"**：不采用。新增配置项意味着新一度的不安全默认（配置项本身也有默认值），且增加集成心智负担。本设计保持"默认值即安全"，集成商通过显式构造器参数或 setter 表达非默认意图。
3. **"仅改字段默认，不改构造器委派链"**：不采用。短构造器委派链中的每一层兜底都需要同步切换，否则任何走短构造器的路径仍会落回 AllowAll，使默认安全失效。

**WARN 语义（不安全默认降级可见性）**：

- **触发时机**：引擎构造期（最短构造器调用解析出最终 checker 实例后），每个 engine 实例至多触发一次。
- **触发条件**：解析后的 `toolAccessChecker` 或 `pathAccessChecker` 为 `AllowAllToolAccessChecker` / `AllowAllPathAccessChecker` 实例（即集成商显式 opt-in 了全放行，或显式传 `null` 触发了 AllowAll 兜底——后者在本决策后已不会发生，因为字段默认值已切换为 `Default*`，但保留检查以覆盖集成商显式 opt-in 的场景）。
- **日志级别**：WARN（非 ERROR，因为显式 opt-in 是合法用法；非 INFO/DEBUG，因为安全降级必须可见）。
- **文案要点**：明确告知"开箱不安全，危险工具/敏感路径无防护"，并指引如何显式装配 `Default*` checker 或修复调用点。文案按 checker 类型分别给出（tool / path），便于定位。
- **覆盖范围**：WARN 当前覆盖 Layer 1 的 `IToolAccessChecker`、`IPathAccessChecker`，`IAuditLogger`（见 §4.7），以及 Layer 2/3 的全部 5 个组件（见 §4.8：`AutoApproveGate` 构造期+setter 检查；`NoOpSecurityLevelResolver`/`PassThroughPermissionMatrix`/`NoOpDenialLedger`/`PassThroughPostDenialGuard` 仅 setter 检查以避免构造期噪音）。

**向后兼容处理原则**：

- 受影响既有测试统一按以下原则处理，不允许"为图省事整体关闭 checker"的回退。
- 当测试需要验证 allow 路径（如 ReAct 循环正常执行不被 checker 阻断）时，显式向构造器传入 `AllowAllToolAccessChecker` / `AllowAllPathAccessChecker` 实例，并在构造点注释"测试 allow 路径"。
- 当测试本身就是为了验证 deny 行为时（如 deny-list 工具被拒绝），使用默认短构造器即可——这正是被测试的 in-scope 行为。
- 当测试用到名为 deny-list 同名的工具但目的是验证其它行为（非 checker 行为）时，优先改用非 deny-list 工具名（如 `mock-tool`、`echo`），避免与默认 deny 行为冲突。

**`ReActAgentExecutor.Builder` 默认值一致性**：`ReActAgentExecutor.Builder.build()` 的 null 兜底默认同步切换为 `Default*`，避免集成商绕过 engine 直接构造 executor 时仍 AllowAll。`auditLogger` 的 null 兜底默认同步切换为 `Slf4jAuditLogger`，见 §4.7。

### 4.7 审计 Logger 默认装配（Audit Trail Secure by Default）

**决策**：`DefaultAgentEngine` 开箱即通过 `Slf4jAuditLogger` 将审计事件（工具决策 deny/approve/override 等）记录到 SLF4J——无需集成商显式装配。引擎持有 `auditLogger` 字段（默认 `Slf4jAuditLogger`），通过 `setAuditLogger` setter 暴露替换能力，使集成商可按需替换为自定义实现（如写入数据库的 logger）。`resolveExecutor` 把 engine 的 auditLogger 透传到 `ReActAgentExecutor.Builder.auditLogger(...)`，确保从 engine 入口构造的 executor 审计事件不被丢弃。`ReActAgentExecutor.Builder.build()` 的 null 兜底同步从 `NoOpAuditLogger` 切换为 `Slf4jAuditLogger`，使绕过 engine 直接构造 executor 的路径也与引擎默认一致。

**为什么选 field + setter 而非构造器重载**：`DefaultAgentEngine` 的构造器链已包含 9 层委派（chatService/toolManager → sessionStore → permissionProvider → toolAccessChecker → pathAccessChecker → ... → contextCompactor），每层都是 Layer 1 核心组件。audit logger 与 `denialLedger`/`postDenialGuard`/`checkpointManager`/`memoryStoreProvider` 等 Layer 2/3 组件同级，这些组件已统一采用"private 字段 + shipped 默认值 + public setter"模式（无构造器参数）。audit logger 沿用同一模式保持一致性，且避免构造器链进一步膨胀。

**WARN 扩展语义（审计降级可见性）**：plan 193 的 `warnIfInsecureDefaults` WARN 机制被扩展为同时检查 `NoOpAuditLogger`——当 audit logger 为 `NoOpAuditLogger` 实例时发出一次性 WARN（文案点明"审计事件将被丢弃，工具决策（deny/approve/override）无记录"，并指引如何显式装配 `Slf4jAuditLogger` 或自定义 logger）。该 WARN 与 AllowAll tool/path checker 的 WARN 共存于同一方法，不引入新的 WARN 入口点。

- **触发时机**：(a) 构造期检查——构造期字段默认为 `Slf4jAuditLogger`，此项为 defense-in-depth，默认构造不会命中 NoOp；(b) `setAuditLogger` 赋值后检查——`NoOpAuditLogger` 经 setter 注入时的实际命中路径。setter-time WARN 是"让审计降级可见而非静默"的核心。
- **为什么 setter 触发 WARN（而 `setDenialLedger` 不触发）**：`setDenialLedger` 的 NoOp 默认是计划性的（Layer 3 denial-counting 是 successor，见 Non-Goals），而 audit logger 的 NoOp 默认是被本决策收敛的安全缺陷——审计降级必须可见，故 setter 对 audit logger 有额外 WARN 要求，不能照搬 `setDenialLedger` 的无-WARN 行为。

**为什么拒绝替代方案**：

1. **"仅 WARN 不改默认值"**：不采用。理由同 §4.6——WARN 在生产部署中常被日志噪声淹没，且无法替代默认值本身的安全语义。本设计同时采用 WARN 与默认值切换：默认值切换提供实际审计可见性，WARN 提供降级可见性。
2. **"新增构造器重载装配 audit logger"**：不采用。audit logger 与其他 Layer 2/3 setter 组件同级，统一用 field + setter 模式即可，新增构造器重载会使 9 层构造器链变成 10 层，且与 `setDenialLedger`/`setCheckpointManager` 等已有 setter 模式不一致。

**向后兼容处理原则**：改为默认 `Slf4jAuditLogger` 后，通过 engine 短构造器运行的测试会产生 SLF4j INFO 审计日志（不影响断言，SLF4j INFO 不抛异常、不改变返回值）。需要捕获审计事件做断言的测试可通过 `setAuditLogger` 注入 `CollectingAuditLogger`（不再需要绕过 engine 直接用 Builder 构造 executor）。

**`ReActAgentExecutor.Builder` 默认值一致性**：`ReActAgentExecutor.Builder.build()` 的 `auditLogger` null 兜底从 `NoOpAuditLogger` 切换为 `Slf4jAuditLogger`，使绕过 engine 直接构造 executor 的路径也与引擎默认一致。

### 4.8 Layer 2/3 默认收敛（AutoApproveGate 收紧 + WARN 可见性扩展）

**决策（1）— 引入 `DefaultApprovalGate` 作为 engine 默认，收紧 RESTRICTED 级别（defense-in-depth）**：`DefaultAgentEngine` 的 `approvalGate` 字段默认值从 `AutoApproveGate` 切换为新增的 `DefaultApprovalGate`。`DefaultApprovalGate` 的行为：STANDARD 和 ELEVATED 级别操作自动批准（approver = `"default"`），RESTRICTED 级别操作拒绝（`ApprovalDecision.deny(OTHER, reason)`）。`AutoApproveGate` 保留为 public 类，集成商可通过显式 `setApprovalGate(AutoApproveGate.autoApprove())` opt-in 全自动批准（用于测试或可信环境），与 plan 193 保留 `AllowAll*` 的模式一致。`setApprovalGate(null)` 的兜底默认同步切换为 `DefaultApprovalGate`。

**Defense-in-depth 性质**：默认 `NoOpSecurityLevelResolver` 恒返回 `SecurityLevel.STANDARD`，dispatch 链以 STANDARD 调用 approval gate——RESTRICTED 在默认配置下永远不可达。`DefaultApprovalGate` 的 RESTRICTED 收紧仅在集成商注册功能性 `ISecurityLevelResolver`（返回 RESTRICTED）后才可观测。这是纵深防御：当 RESTRICTED 级别操作到达 gate 时，不再被静默放行。

**为什么选引入新 `DefaultApprovalGate` 而非直接修改 `AutoApproveGate`**：与 plan 193 的 `AllowAll*` → `Default*` 模式一致。保留原全放行实现作为 public opt-in，使"显式选择全放行"的代码意图清晰可搜索（grep `AutoApproveGate`），且已有的 `TestAutoApproveGate` 测试无需修改（它验证的就是 opt-in 行为）。直接修改 `AutoApproveGate` 会使 RESTRICTED 行为在类名层面不可见——`AutoApproveGate` 名字暗示"全部批准"但实际拒绝了 RESTRICTED，语义与类名冲突。

**决策（2）— ELEVATED 仍由默认批准**：`DefaultApprovalGate` 对 ELEVATED 级别仍然批准。设计语义中 ELEVATED 是"需要确认"（比 STANDARD 严格，比 RESTRICTED 宽松），而"确认"不等同于"人类审批"——功能性 gate 实现负责真正的 ELEVATED 确认流。默认 gate 只对 RESTRICTED（"需要审批"）做 defense-in-depth 拒绝。

**决策（3）— WARN 覆盖扩展与噪音控制策略**：`warnIfInsecureDefaults` 扩展为覆盖全部 5 个 Layer 2/3 NoOp/PassThrough 组件，但按"行为是否已变更"区分 WARN 触发时机，避免每次引擎构造产生噪音：

- **行为已变更的组件（`AutoApproveGate`）**：engine 默认从 `AutoApproveGate` 切换为 `DefaultApprovalGate`，因此 `AutoApproveGate` 成为"显式 opt-in 不安全"选项。WARN 在构造期（defense-in-depth，默认不会命中）和 `setApprovalGate` setter 赋值后均检查——当检测到 `AutoApproveGate` 实例时触发一次性 WARN。与 plan 193 的 `AllowAll*` 和 plan 194 的 `NoOpAuditLogger` 模式完全一致。
- **默认仍为 insecure 的组件（`NoOpSecurityLevelResolver`、`PassThroughPermissionMatrix`、`NoOpDenialLedger`、`PassThroughPostDenialGuard`）**：这些组件的默认仍为 NoOp/PassThrough（Non-Goals 明确不改，secure 替代实现是独立 successor）。构造期**不**检查这 4 个组件（默认值即为 NoOp/PassThrough，检查会在每次引擎构造时触发 WARN = 噪音）。WARN 仅在对应 setter 被调用且解析后的值为 NoOp/PassThrough 实例时触发——setter 调用是集成商的显式动作，将不安全装配可见化便于安全审计。

**为什么拒绝替代 WARN 策略**：

1. **"照搬 193/194 的 opt-in WARN 模式（构造期对全部 5 个组件 instanceof 检查）"**：不采用。4 个 unchanged-insecure-default 组件的构造期默认值就是 NoOp/PassThrough，构造期检查会在每次引擎构造时触发 WARN——这是纯粹的日志噪音，违反"避免每次引擎构造触发噪音 WARN"的噪音控制目标。
2. **"构造期摘要式一次性 WARN（合并列出全部 insecure 默认）"**：不采用。即使合并为一条，也会在每次引擎构造时触发，对于 NoOp 是设计性默认（非 bug）的场景，持续的 WARN 会被日志噪声淹没。摘要式 WARN 仅在创建了 secure 默认替代后才有意义（那时默认不再是 NoOp，WARN 仅在 opt-in 回退时触发）。
3. **"仅覆盖 AutoApproveGate，其他 4 个不覆盖"**：不采用。Goal 明确要求覆盖全部 5 个组件。setter-time WARN 覆盖使集成商的不安全装配可见，且不产生构造期噪音——在 noise-control 和 coverage 之间取得平衡。

**决策（4）— WARN 调用点策略**：构造器在解析出全部组件后调用一次 `warnIfInsecureDefaults`（与 plan 193/194 一致，覆盖 Layer 1 checker + audit logger + AutoApproveGate）。每个 Layer 2/3 setter（`setApprovalGate`、`setSecurityLevelResolver`、`setPermissionMatrix`、`setDenialLedger`、`setPostDenialGuard`）在赋值后也调用 `warnIfInsecureDefaults`，使 setter-time 降级即时可见。这与 plan 194 的 `setAuditLogger` 模式一致。

**决策（5）— 向后兼容处理原则**：受影响既有测试统一按以下原则处理：

- `DefaultApprovalGate` 对 STANDARD/ELEVATED 批准的行为与 `AutoApproveGate` 一致，因此默认 `NoOpSecurityLevelResolver`（恒返回 STANDARD）下的全部既有测试不受影响——RESTRICTED 不可达，gate 行为不可观测差异。
- 测试若显式验证"RESTRICTED 级别被 AutoApproveGate 批准"的行为（即显式使用 `AutoApproveGate`），保持不变——`AutoApproveGate` 的行为未改变，它仍是 public opt-in。
- 测试若需要 RESTRICTED 不可达的正常执行路径，无需修改（默认 resolver 返回 STANDARD）。
- 测试若需要验证 RESTRICTED 被 approval gate 拒绝的 defense-in-depth 行为，显式注入功能性 `ISecurityLevelResolver` 返回 RESTRICTED + 默认 gate（`DefaultApprovalGate`）。

**`ReActAgentExecutor.Builder` 默认值一致性**：`ReActAgentExecutor.Builder.build()` 的 `approvalGate` null 兜底同步从 `AutoApproveGate` 切换为 `DefaultApprovalGate`，使绕过 engine 直接构造 executor 的路径也与引擎默认一致。

### 4.9 Layer 2/3 全套 Secure 默认实现（NoOp/PassThrough → Default*）

**决策背景**：§4.8 仅收敛了 `IApprovalGate`（AutoApproveGate → DefaultApprovalGate）。其余 4 个 Layer 2/3 组件（`ISecurityLevelResolver`、`IPermissionMatrix`、`IDenialLedger`、`IPostDenialGuard`）仍以 NoOp/PassThrough 作为 engine 默认。本节决策将这 4 个组件的 engine 默认切换为功能性 `Default*` 实现，使引擎开箱即用具备安全级别分类、通道权限矩阵、拒绝计数/暂停、盲重试阻断能力。`NoOp*` / `PassThrough*` 4 个组件保留为 public opt-in（与 `AutoApproveGate` / `AllowAll*` 模式一致）。

**决策（1）— DefaultSecurityLevelResolver 采用 trusted-by-default 变体**：

设计 §5.1 规则表对 `shell.exec`/`code.exec` 标注 `highImpact → RESTRICTED`。但 `DefaultLevelHintsProducer` 对 shell.exec 恒产出 `highImpact=true`（tool-name 分类），完整实现规则表会导致 shell.exec → RESTRICTED → DefaultApprovalGate 拒绝 RESTRICTED → 引擎无法执行 shell 工具。

**选了什么**：trusted-by-default 变体。当 `trustedSource=true`（agent 自身推理链产生）时，`highImpact` 仅升级到 ELEVATED 而非 RESTRICTED；RESTRICTED 仅在 `!trustedSource && highImpact` 时触发。完整规则表变体如下：

| 条件 | 结果 |
|------|------|
| trustedSource=true, highImpact | ELEVATED |
| trustedSource=true, writesOutsideWorkspace | ELEVATED |
| trustedSource=true, 其他 | STANDARD |
| trustedSource=false, network.fetch/web.fetch | RESTRICTED |
| trustedSource=false, highImpact | RESTRICTED |
| trustedSource=false, writesOutsideWorkspace | ELEVATED |
| trustedSource=false, 其他 | ELEVATED |

**为什么选 trusted-by-default 而非完整规则表**：agent 自身推理链产生的 tool call 是引擎运行的基线场景（`DefaultContentTrustEvaluator` 对 `AGENT_GENERATED` 返回 trusted）。完整规则表会使这个基线场景中的 shell.exec 被分类为 RESTRICTED，导致引擎不可用。trusted-by-default 变体保留了功能性分类（trusted 高影响操作 → ELEVATED，untrusted 高影响操作 → RESTRICTED），同时不阻断基线执行。

**为什么拒绝替代方案**：
- **(a) 完整实现规则表，接受 shell.exec 变为 RESTRICTED**：不采用。需要同步调整 DefaultApprovalGate 或添加豁免规则才能使引擎可用，引入额外复杂度且偏离 defense-in-depth 原则。
- **(c) 仅在 `!trustedSource` 时升级（highImpact 不单独触发 RESTRICTED）**：不采用。与 (b) 的实际效果相同（trusted 时 highImpact → ELEVATED，untrusted 时 highImpact → RESTRICTED），但 (b) 的表述更清晰地映射到规则表结构。

**决策（2）— DefaultPermissionMatrix 采用 §5.3 表 + usability-safe null channel**：

设计 §5.3 表对 unknown/null channel 标注 "STANDARD only (fail-closed)"。但引擎在很多场景下 channel 为 null（测试环境、未显式设置 channelKind 的集成场景）。若 null channel fail-closed 到 STANDARD-only，则 trusted-by-default resolver 产出的 ELEVATED 操作（如 trusted shell.exec）会被 matrix 拒绝，引擎不可用。

**选了什么**：按 §5.3 表实现已知通道（WEBUI/API/DM/GROUP）的限制，但对 null/unknown channel 采用 **STANDARD + ELEVATED（deny RESTRICTED only）** 的部分 fail-closed 语义——而非完全 fail-closed 到 STANDARD-only。`PrincipalRole.OPERATOR` 可绕过 RESTRICTED（与 §5.3 设计一致）。

**为什么选 usability-safe null channel 而非完全 fail-closed**：作为 engine 默认，需要平衡安全与可用性。deny RESTRICTED 是有意义的安全边界（untrusted 高影响操作、untrusted 网络操作被拒绝）；allow STANDARD + ELEVATED 保持引擎可用（trusted 高影响操作如 shell.exec 可执行）。完全 fail-closed 到 STANDARD-only 会使 trusted-by-default resolver 产出的 ELEVATED 不可观测，matrix 的分类能力被架空。当集成商需要更严格策略时，可注册自定义 matrix 实现 §5.3 的完全 fail-closed 语义。

**决策（3）— DefaultDenialLedger 采用纯内存 threshold-based 计数**：

**选了什么**：`ConcurrentHashMap<String, AtomicInteger>` per-session 计数，threshold = 3（设计 §6.2，与 `DBDenialLedger.DEFAULT_DENIAL_THRESHOLD` 一致）。anonymous session（null sessionId）不计数（返回 count=0, threshold-not-exceeded），与 `DBDenialLedger` 语义一致。不持久化（纯内存）。

**为什么选纯内存而非 DB-backed 默认**：`DBDenialLedger` 需要 DataSource + schema init，不适合作为 engine 零依赖默认。纯内存实现使引擎开箱即用具备拒绝计数/暂停能力；DB 持久化是显式 opt-in（已有 `DBDenialLedger`）。

**决策（4）— 新建 DefaultPostDenialGuard 作为独立类**：

**选了什么**：新建 `DefaultPostDenialGuard` 类（与 `DefaultApprovalGate`/`DefaultToolAccessChecker` 命名一致），内部委托 `FingerprintPostDenialGuard` 实例（避免代码重复，最小 wrapper ceremony）。

**为什么选新建 Default* 而非直接提升 FingerprintPostDenialGuard**：命名一致性。全部 Layer 1/2/3 secure 默认均以 `Default*` 命名（`DefaultToolAccessChecker`、`DefaultPathAccessChecker`、`DefaultApprovalGate`、`DefaultLevelHintsProducer`）。`FingerprintPostDenialGuard` 保留为 public 类（描述实现策略的命名），`DefaultPostDenialGuard` 作为 shipped 默认（描述角色的命名）。

**决策（5）— WARN 策略迁移到 always-checked**：

4 个组件从 conditionally-checked 迁移到 always-checked。构造期 `warnIfInsecureDefaults` 传非 null 给全部 4 个组件（与新 Default* 默认一致）。`instanceof NoOp/PassThrough` 检查仍保留——构造期 Default* 实例不触发 WARN（非 NoOp/PassThrough），集成商经 setter 显式注入 NoOp/PassThrough 时触发 WARN。各 setter 仍只传自己刚设的组件 + null 给其余 3 个（noise control 保留）。

**决策（6）— 向后兼容处理原则**：

- 受影响既有测试统一按以下原则处理，不允许"为图省事整体关闭防护"的回退。
- 当测试需要验证 NoOp/PassThrough 行为（安全级别恒 STANDARD、permission 恒 allow、denial 不计数/不暂停、retry 不阻断）时，显式经 setter 注入 NoOp/PassThrough 实例 + 注释"测试需要 insecure 默认"。
- 当测试本身不依赖特定安全行为（如只验证 ReAct 循环正常执行不被阻断），使用非 deny-list、非 high-impact 工具名（如 `echo`、`mock-tool`），这些工具在 Default* 默认下分类为 STANDARD → 全通道放行。
- 当测试需要验证 Default* 的新行为（分类、matrix deny、denial 计数、retry 阻断），使用默认短构造器即可。

**`ReActAgentExecutor.Builder` 默认值一致性**：`ReActAgentExecutor.Builder.build()` 的 4 个 null 兜底同步从 NoOp/PassThrough 切换为 Default*，使绕过 engine 直接构造 executor 的路径也与引擎默认一致。

## 5. Layer 2: Policy Extensions（策略扩展层）

扩展安全策略的粒度，不改变核心 deny/allow 语义。所有接口有 pass-through 默认。

### 5.1 ISecurityLevelResolver — 安全等级解析

**职责**：根据 action 种类和上下文 hints 解析安全等级。

**状态**：契约 + pass-through 默认已落地（L2-13 ✅），且 dispatch-path 咨询已接通（plan 175 ✅）。`ISecurityLevelResolver` 接口 + `NoOpSecurityLevelResolver` 默认 + `LevelHints` 值类型位于 `io.nop.ai.agent.security` 包，引擎通过 `DefaultAgentEngine.setSecurityLevelResolver` / `getSecurityLevelResolver` 接线，默认 = NoOp。`ReActAgentExecutor` dispatch loop 在 Layer 1 检查之后调用 `resolver.resolve(toolName, hints)`；hints 由可插拔 `ILevelHintsProducer`（默认 `DefaultLevelHintsProducer`）运行时生产。

**来源**：OpenSquilla sandbox.policy 模块的 `select_level(action_kind, hints)` 确定性规则表（见 `ai-dev/analysis/agent-survey/2026-06-08-opensquilla-analysis.md` §4.3.1）。

**SecurityLevel 分级**：

| 等级 | 语义 | 典型操作 |
|------|------|---------|
| `STANDARD` | 正常执行，无额外限制 | fs.read, fs.list |
| `ELEVATED` | 需要确认，资源收紧 | fs.write, shell.exec (trusted source) |
| `RESTRICTED` | 需要审批，最小权限 | shell.exec (untrusted source), network.fetch |

**LevelHints 输入**（flat dataclass，每个字段是可审计的布尔值）：

| Hint | 含义 |
|------|------|
| `trustedSource` | 内容来源是否可信 |
| `writesOutsideWorkspace` | 是否写工作目录外 |
| `crossesTrustBoundary` | 是否跨信任边界 |
| `needsNetwork` | 是否需要网络访问 |
| `highImpact` | 是否高影响操作 |

**规则表**（确定性，无 AI 决策）：

| action_kind | 默认等级 | 升级条件 |
|-------------|---------|---------|
| `fs.read`, `fs.list`, `fs.grep` | STANDARD | — |
| `fs.write`, `fs.edit`, `patch.apply` | STANDARD | `writesOutsideWorkspace` → ELEVATED |
| `shell.exec`, `code.exec` | STANDARD | `!trustedSource` → ELEVATED; `highImpact` → RESTRICTED |
| `network.fetch`, `web.fetch` | STANDARD | `!trustedSource` → RESTRICTED |
| 其他 | STANDARD | `!trustedSource` → ELEVATED; `highImpact` → RESTRICTED |

**默认实现**：`NoOpSecurityLevelResolver`（所有操作返回 STANDARD，等于不分级）。上表中的确定性规则表是功能化实现，归类为 priority-5，在 NoOp 默认之上独立交付。

**架构决策**：

1. **LevelHints 值类型归属**：`LevelHints` 在本计划（L2-13）中定义，因为 resolver 契约需要它作为输入参数。`SecurityLevel` 枚举复用 L2-14（plan 172，矩阵消费者）的定义——消费者先于生产者落地，因为矩阵契约需要 SecurityLevel 作为输入参数。生产者（resolver）落地时直接复用此枚举，不重复定义。

2. **dispatch-path 咨询已接通（plan 175）**：`ReActAgentExecutor` 工具分发循环在 Layer 1 检查（toolAccessChecker / permissionProvider / pathAccessChecker）之后调用 `resolver.resolve(toolName, hints)`。`LevelHints` 由可插拔 `ILevelHintsProducer`（默认 `DefaultLevelHintsProducer`）运行时生产——`trustedSource` 经 `IContentTrustEvaluator` 评估（agent 内部推理链 → AGENT_GENERATED → trusted），`writesOutsideWorkspace` 经 path-arg（`ToolPathArgKeys`）× workDir 比对，`needsNetwork`/`highImpact` 经 tool-name 分类，`crossesTrustBoundary` 保守为 false（精确评估是后续增强）。通道与身份经 `AgentMessageRequest` → `AgentExecutionContext` 传播（`channelKind`/`principal` 可选字段，null = 未知通道/匿名身份）。NoOp 默认对所有操作返回 STANDARD，即使接通也不改变运行时行为。功能化规则表 resolver（上表的确定性 shipped 实现）归类为 priority-5，在 NoOp 默认之上独立交付。

**LevelHints 评估**：`trustedSource` 等 hints 需要程序化评估。定义 `IContentTrustEvaluator` 接口：

```
IContentTrustEvaluator:
  boolean isTrustedSource(ContentOrigin origin, AgentExecutionContext ctx)
```

**来源**：ContentOrigin 枚举标识内容来源（`CHANNEL_INPUT`, `WEB_FETCH`, `FILE_READ`, `AGENT_GENERATED`）。默认实现 `DefaultContentTrustEvaluator`：CHANNEL_INPUT 和 AGENT_GENERATED 为 trusted，WEB_FETCH 和 FILE_READ 为 untrusted。可通过 XDSL 配置覆盖。

**XDSL 配置化**：规则表定义为 `security-policy.xdef` schema，运行时从 DSL 装载。操作员可通过 Delta 覆盖默认规则，不需要改代码。

### 5.2 IContentGuardrail — 内容护栏

**职责**：拦截和分析进入/离开 LLM 的内容。

**来源**：VoltAgent 的 Input/Output Guardrail pipeline + OpenSquilla 的 injection_guard.py。

**方向**：

| 方向 | 拦截点 | 用途 |
|------|--------|------|
| `INPUT` | 用户消息 → LLM 之前 | 检测注入攻击、过滤敏感内容 |
| `OUTPUT` | LLM 输出 → 工具/用户之前 | 检测越权指令、过滤泄露内容 |

**返回值**：`PASS` | `BLOCK(reason)` | `MODIFY(content)`

**预构建 Guardrail**（Layer 2 默认提供，可选启用）：

| Guardrail | 检测内容 | 来源 |
|-----------|---------|------|
| `PromptInjectionGuardrail` | prompt_override / role_hijack / exfiltration / invisible_char 四类威胁 | OpenSquilla `injection_guard.py` 4 类正则 |
| `UntrustedEnvelopeGuardrail` | 将外部内容包裹在 `<untrusted>` 信封中 | OpenSquilla `wrap_untrusted()` |
| `PIIDetectionGuardrail` | 个人信息检测 | VoltAgent 预构建 |
| `ContentLengthGuardrail` | 输出长度限制 | VoltAgent 预构建 |

**Prompt 注入检测**（基于 OpenSquilla 分类法，参考 Simon Willison 分类法、GARAK 基准、Anthropic 红队报告）：

| 威胁类 | 检测目标 |
|--------|---------|
| `prompt_override` | 试图让模型忽略/重置系统指令 |
| `role_hijack` | 伪装为 system/admin/root |
| `exfiltration` | 试图泄露 secrets/API keys/env vars |
| `invisible_char` | 零宽字符/BIDI 控制字符走私 |

**执行模式**：`off` / `report`（仅记录）/ `enforce`（阻止）。

**Tool Call 注入守卫**：在 Tool Dispatch 管线中，如果 Tool Call 的 origin trace 位于 `<untrusted>` 块内，拒绝执行。这是双重防护——即使 LLM 被诱导生成 tool_call，只要 origin 在 untrusted 内容中就会被阻止。

**默认实现**：`NoOpContentGuardrail`（roadmap.md Layer 2 已定义）。

### 5.3 IPermissionMatrix — 通道权限矩阵

**职责**：按通道类型控制允许的工具风险等级。

**来源**：OpenSquilla safety.permission_matrix 模块（见 `ai-dev/analysis/agent-survey/2026-06-08-opensquilla-analysis.md` §4.3.2）。

**状态**：契约 + pass-through 默认已落地（L2-14 ✅），且 dispatch-path 咨询已接通（plan 175 ✅）。`IPermissionMatrix` 接口 + `PassThroughPermissionMatrix` 默认 + `SecurityLevel`/`ChannelKind`/`Principal`/`PrincipalRole` 共享值类型位于 `io.nop.ai.agent.security` 包，引擎通过 `DefaultAgentEngine.setPermissionMatrix` / `getPermissionMatrix` 接线，默认 = PassThrough。`ReActAgentExecutor` dispatch loop 在 `ISecurityLevelResolver` 解析出 `SecurityLevel` 之后调用 `matrix.check(channelKind, principal, level)`；deny 时记录审计日志 + 发布 `TOOL_CALL_DENIED` 事件 + 返回 error response（与 Layer 1 deny 路径一致）。

| 通道类型 | 允许的工具等级 |
|---------|--------------|
| `webui` | STANDARD + ELEVATED + RESTRICTED |
| `api` | STANDARD + ELEVATED |
| `dm` | STANDARD + ELEVATED |
| `group` | STANDARD |
| 未知通道 | STANDARD（fail-closed） |

**Principal 身份模型**：

| 字段 | 含义 |
|------|------|
| `role` | `PrincipalRole` 枚举：`USER` / `OPERATOR`（OPERATOR 可绕过 RESTRICTED） |
| `channelId` | 用于 per-channel override |
| `tenantId` | 多租户标识（Nop `IContext` 天然支持） |

**默认实现**：`PassThroughPermissionMatrix`（所有通道允许所有等级，singleton + `passThrough()` 工厂）。上表中的通道限制规则是功能化实现，归类为 priority-5，在 pass-through 默认之上独立交付。

**架构决策**：

1. **SecurityLevel 共享值类型的归属**：`SecurityLevel` 枚举在本计划（L2-14，矩阵消费者）中定义，而非 L2-13（`ISecurityLevelResolver`，SecurityLevel 生产者）。消费者先于生产者落地，因为矩阵契约需要 SecurityLevel 作为输入参数。L2-13 落地时直接复用此枚举，不重复定义。

2. **dispatch-path 咨询已接通（plan 175）**：`ReActAgentExecutor` 工具分发循环在 `ISecurityLevelResolver`（plan 173）解析出 `SecurityLevel` 之后调用 `matrix.check(channelKind, principal, level)`。consultation 点位于 Layer 1 检查之后；deny 路径与 Layer 1 deny 一致——记录 `AuditEvent`（DENY + reason + matched rule `layer2_permission_matrix`）+ 发布 `TOOL_CALL_DENIED` 事件 + 返回 `ChatToolResponseMessage.error(...)` 并跳过该工具调用。`channelKind`/`principal` 经 `AgentMessageRequest` → `AgentExecutionContext` 传播（可选字段，null = 未知/匿名）。pass-through 默认放行一切，即使接通也不改变运行时行为。功能化限制性 matrix（上表通道限制的 shipped 实现）归类为 priority-5，在 pass-through 默认之上独立交付。

## 6. Layer 3: Approval Governance（审批治理层）

为生产环境提供人类审批和拒绝治理。所有接口有最简默认。

### 6.1 IApprovalGate — 审批门

**状态**：契约表面 + `AutoApproveGate` 默认 + dispatch-path 咨询点已落地（plan 176 ✅）。功能化人类审批流（异步等待 + 超时 + 多通道）为后续工作项。

**职责**：当 `ISecurityLevelResolver` 返回需要审批的等级时，向人类请求批准。

**来源**：OpenSquilla sandbox.governance 模块的 ApprovalGate（见 `ai-dev/analysis/agent-survey/2026-06-08-opensquilla-analysis.md` §4.4.1）。

**行为**：

1. 如果 policy 不需要审批 → 直接 ALLOW
2. 入队审批请求（namespace 区分 exec/plugin）
3. 等待人类响应（带超时，默认 300s）
4. 超时或拒绝 → DenialResult

> **已落地范围**：步骤 1 的契约表面 + dispatch-path 咨询点 + `AutoApproveGate` shipped 默认（所有请求自动通过，approver = "auto"）。步骤 2-4 的异步人类审批流（入队 + 等待 + 超时）是功能化 gate 实现的职责，不在当前范围；`AutoApproveGate` 不涉及外部审批通道。

**审批通道**：

- Web UI 通知
- GraphQL Subscription
- RPC 轮询

> 通道抽象为可插拔接口（`IApprovalChannel`）为后续功能化审批流增强（审计发现 L3-G3），当前 `AutoApproveGate` 不需要外部通道。

**默认实现**：`AutoApproveGate`（所有请求自动通过——适用于无人值守自动化的 Layer 1 基线）。经 `DefaultAgentEngine.setApprovalGate` 程序化注入功能化实现。

**决策记录（plan 176）**：

1. **`ApprovalDecision` 作为 `IApprovalGate` 专用返回类型**，与 L3-7 的 `DenialResult` 信封边界清晰：gate 自身的 approve/deny 决策（approver + denial kind + reason）vs 拒绝后治理信封（suggestedNextStep + actionFingerprint + retryable）。`DenialResult` 归属于 L3-7（`IPostDenialGuard`），不在本工作项范围。
2. **consultation 点位于 Layer 2 matrix 放行之后**、`allowedCalls.add` 之前。deny 路径记录 `AuditEvent`（DENY + reason + matched rule `layer3_approval_gate`）+ 发布 `TOOL_CALL_DENIED` 事件 + 返回 `ChatToolResponseMessage.error(...)`，与 Layer 1/2 deny 路径完全一致。
3. **`AutoApproveGate` shipped 默认保证向后兼容**：不设 gate 的引擎执行行为与接线前完全一致（0 spurious 拒绝），无人值守 Layer 1 自动化不受影响。
4. **denial reason 区分 human_rejected / timeout**（收窄审计发现 L3-G1 到审批门自身语义）：`ApprovalDenialKind` 枚举（HUMAN_REJECTED / TIMEOUT / OTHER）使审批超时与人类拒绝在审计中可区分，而非模糊合并为一个 reason 字符串。完整枚举（threshold_exceeded / repeated_same_intent 等 L3-6/L3-7 场景）在后续工作项中扩展。

### 6.2 IDenialLedger — 拒绝账本

**职责**：per-session 拒绝计数，达到阈值自动暂停 autonomous 执行。

**来源**：OpenSquilla sandbox.governance 模块的 DenialLedger（见 `ai-dev/analysis/agent-survey/2026-06-08-opensquilla-analysis.md` §4.4.2）。

**状态**：契约表面（`IDenialLedger` + `DenialRecord` + `DenialRecordOutcome` + `DenialLayerSource` + `NoOpDenialLedger`）已落地。dispatch-path 集成已接通：每个 deny 路径（Layer 1/2/3 共 5 个 deny 点）向 ledger 记录拒绝，达到阈值后 dispatch for-loop 中止 + ReAct 循环迭代开始时 `isPaused` 检查中止。`DBDenialLedger`（DB 持久化实现）已落地：per-session 拒绝记录持久化到 `ai_agent_denial` 表，拒绝计数与暂停状态经 ledger 实例重建（模拟 session 恢复 / 跨进程）后依然存活。`pauseBehavior = sticky` 完整恢复协议已落地：`IAgentEngine.resumeSession(sessionId, approver, reason)` 是人类干预恢复入口点——调用 `denialLedger.reset` 清除暂停、发布 `SESSION_RESUMED` 审计事件、重新执行 session。

**关键设计**：

| 参数 | 默认值 | 说明 |
|------|-------|------|
| `denialThreshold` | 3 | 累计 N 次拒绝后暂停 |
| `pauseBehavior` | sticky | 暂停后只有人类干预才能恢复（已落地：`IAgentEngine.resumeSession` 清除暂停 + 重新执行；auto-recovery 经 `isPaused` 检查禁止） |
| `persistence` | DB | DenialLedger 持久化到数据库（`DBDenialLedger` 已交付，`NoOpDenialLedger` 默认不持久化） |

**Fingerprint**：`action_fingerprint = SHA-256(actionKind + argv + cwd + criticalEnv)[:32]`，用于标识"相同的危险意图"。fingerprint 基础设施在 L3-7 落地时引入，`DenialRecord` 届时可扩展携带 fingerprint 字段。

**默认实现**：`NoOpDenialLedger`（不计数，不暂停）。功能化实现：`DBDenialLedger`（raw JDBC，per-session 拒绝记录持久化到 `ai_agent_denial` 表，计数与暂停状态跨 ledger 实例重建存活）。通过 `DefaultAgentEngine.setDenialLedger(new DBDenialLedger(dataSource))` 显式注册启用。

**架构决策**：

1. **接口契约不强制持久化**（收窄审计发现 L3-G5）：`NoOpDenialLedger` 不持久化在设计上合法。持久化是 `DBDenialLedger`（已落地）的职责，非接口契约的硬性要求。
2. **每个 deny 路径均记录到 ledger**：dispatch loop 的全部 5 个 deny 点（Layer 1 tool access / Layer 1 permission / Layer 1 path access / Layer 2 security policy / Layer 3 approval gate）均通过 `handleDenialAndCheckThreshold` 向 ledger 记录拒绝，覆盖全部拒绝来源（非仅 Layer 3）。
3. **阈值检查双重机制**：(a) 每次 `recordDenial` 后立即检查返回的 `DenialRecordOutcome.thresholdExceeded`；(b) ReAct 迭代开始时检查 `isPaused`。两个机制职责分离——Mechanism 1 (dispatch-path) 负责跳过当前迭代剩余执行（break dispatch for-loop + 跳过 allowedCalls 执行但不 break reactLoop），Mechanism 2 (迭代开始检查) 负责中止 ReAct 循环（break reactLoop）。
4. **`AgentExecStatus.paused` 语义独立**：`paused` 与 `forced_stopped`/`cancelled`/`escalated` 语义不同——paused 是治理策略自动触发（denial threshold exceeded），非用户干预（cancelled）、非系统决策（forced_stopped）、非升级路径（escalated）。post-loop bookkeeping 排除 `paused`，暂停 session 不发布 `EXECUTION_COMPLETED` / 不执行 `POST_CALL` hooks。
5. **`NoOpDenialLedger` shipped 默认保证向后兼容**：不设 ledger 的引擎执行行为与接线前完全一致（0 spurious 暂停），无人值守 Layer 1 自动化不受影响。
6. **`pauseBehavior = sticky` 已落地**：`IAgentEngine.resumeSession(sessionId, approver, reason)` 是人类干预恢复入口点（`DefaultAgentEngine` 实现）。恢复协议：(a) resume 调用本身即设计要求的"人类干预"——显式、带 approver 身份和 reason 的调用 IS the human intervention，不需要额外的 `IApprovalChannel` 审批（`IApprovalChannel` gated resume 仍标注为 deferred enhancement，非前置条件）；(b) resume re-execution 从 paused session 的已有 conversation history 重建 `AgentExecutionContext`（系统 prompt + 已有消息），**不**添加新 user message——resume 是 transparent continuation 而非新一轮对话；(c) resume 调用 `denialLedger.reset(sessionId)` 清除暂停 + 发布 `SESSION_RESUMED` 审计事件（payload: approver + reason + preResetDenialCount）+ 重新执行 session；(d) sticky enforcement 经运行时 `isPaused` 检查强制执行——对 paused session 调用 `execute()`（不 resume）会在 ReAct 循环迭代开始时被 `isPaused` 中止并 re-pause，auto-recovery 不可能；(e) `resumeSession` fail-fast 语义：仅 `AgentExecStatus.paused` 的 session 能被 resume（non-existent / non-paused session 抛 `NopAiAgentException`，不静默 no-op）。
7. **`DBDenialLedger` 列存储而非 JSON blob**：`ai_agent_denial` 表每字段一列（SID + SESSION_ID + TOOL_NAME + LAYER_SOURCE + REASON + MATCHED_RULE + DENIAL_TIMESTAMP + CREATED_AT），而非 messenger 的 JSON blob 模式。理由：(1) `DenialRecord` 全部字段为简单类型（String + enum + long），无 opaque payload；(2) 核心操作是 per-session COUNT + per-session DELETE，列存储使原生 SQL 高效；(3) `DenialLayerSource` 枚举存为 VARCHAR（枚举名）。
8. **`DBDenialLedger` 使用 raw JDBC 而非 IOrmSession**（与 `DBMessageService` 一致）：`IDenialLedger` 操作是同步的 record/count/delete，无后台轮询需求，raw JDBC 对简单 COUNT/INSERT/DELETE 足够且无额外抽象层。
9. **`DBDenialLedger` 线程安全经 DB 操作保证而非内存锁**：每个 `recordDenial` 是原子 INSERT + COUNT 查询（计数实时从 DB 读取，非内存累加）；`isPaused` / `getDenialCount` 是 COUNT 查询；`reset` 是 DELETE。多 session 并发访问同一 ledger 实例时，per-session INSERT/COUNT/DELETE 互不干扰（WHERE SESSION_ID=? 隔离），无需 `ConcurrentHashMap` 内存状态。

### 6.3 IPostDenialGuard — 拒绝后守卫

**职责**：被拒后，阻止 Agent 盲重试相同操作。

**来源**：OpenSquilla sandbox.governance 模块的 post_denial_guard（见 `ai-dev/analysis/agent-survey/2026-06-08-opensquilla-analysis.md` §4.4.3）。

**合法 follow-up 标签**（只有 3 种）：

| 标签 | 语义 |
|------|------|
| `LOWER_PRIVILEGE` | 降权重试 |
| `EXPLAIN` | 向用户解释限制 |
| `NARROWER_APPROVAL` | 请求更窄范围的审批 |

**无标签的盲重试**（相同 fingerprint）→ REPEATED_SAME_INTENT 拒绝。

**DenialResult 结构化信封**：

```
DenialResult {
  reason: enum         // human_rejected / threshold_exceeded / repeated_same_intent / ...
  suggestedNextStep    // replan / askUser / lowerPrivilege / narrowerApproval
  actionFingerprint    // SHA-256[:32]
  message: String      // 人类可读
  retryable: boolean
}
```

**默认实现**：`PassThroughPostDenialGuard`（不阻止重试）。功能化实现：`FingerprintPostDenialGuard`（纯内存，per-session 已拒绝 fingerprint 集合 + exact-fingerprint matching）。

**架构决策**（L3-7 已落地）：

1. **`DenialResult` 作为 `IPostDenialGuard` 专用返回类型**，与 `IApprovalGate` 的 `ApprovalDecision`（gate 自身决策）和 `IDenialLedger` 的 `DenialRecord`（ledger 记录结构）三者边界清晰——`DenialResult` 是 post-denial 治理信封（`DenialReason` + `DenialSuggestedStep` + `actionFingerprint` + `message` + `retryable`）。三者描述纵深防御链的不同阶段：gate 产生 `ApprovalDecision`，dispatch path 向 ledger 记录 `DenialRecord`，post-denial guard 在阻止盲重试时产生 `DenialResult`。
2. **consultation 点位于 Layer 1 检查之前**（dispatch loop 中 `toolAccessChecker.checkAccess` 之前）：盲重试在安全检查链入口被拦截，不浪费 Layer 1/2/3 检查开销，也不污染 `IDenialLedger` 的拒绝计数（每次 guard-deny 也记录到 ledger，但使用 `LAYER3_POST_DENIAL_GUARD` layerSource 可区分）。
3. **recording 在每个 deny 之后**（包括 post-denial-guard 自身的 deny——形成闭环：guard deny 的 action 也被记录到 guard 自身，防止"guard deny 后 Agent 重试 guard deny 的结果"）。recording 在 `handleDenialAndCheckThreshold` 内部完成，6 个 deny 点（5 个 Layer 1/2/3 + 1 个 guard consultation）统一覆盖。
4. **`ActionFingerprint` 使用 exact-fingerprint matching**：`SHA-256(actionKind + argv + cwd + criticalEnv)[:32]`，canonical 序列化（`TreeMap` key 排序）保证确定性。相同 actionKind + argv + cwd + criticalEnv = 盲重试；参数变化自然产生不同 fingerprint = legitimate follow-up 自动放行——这覆盖了 legitimate follow-up 的主要场景（参数变化），无需显式标签检测。
5. **`PassThroughPostDenialGuard` shipped 默认保证向后兼容**：不设 guard 的引擎执行行为与接线前完全一致（0 spurious 拒绝），无人值守 Layer 1 自动化不受影响。
6. **`FingerprintPostDenialGuard` 是 shipped 功能化实现**（纯内存、`ConcurrentHashMap` per-session、无外部依赖），经 `DefaultAgentEngine.setPostDenialGuard` setter 注册启用。
7. **follow-up 标签的显式检测延期**（exact-fingerprint matching 已覆盖 legitimate follow-up 的主要场景——参数变化）；推理文本分析 / tool-call metadata 标注是后续增强。
8. **`DenialLayerSource.LAYER3_POST_DENIAL_GUARD` 新增**，使 ledger 可区分 guard deny 与其他 layer deny，5 个 layer source 扩展为 6 个。

## 7. Layer 4: Platform Security（平台安全层）

多租户、分布式、企业级安全。所有接口有单进程默认。

### 7.1 ISandboxBackend — 沙箱后端

**职责**：在隔离环境中执行高风险命令。

**来源**：OpenSquilla `sandbox/backend/`（Bubblewrap/Seatbelt/Noop），但 Nop 部署在服务器端。

**推荐后端**：

| 后端 | 场景 |
|------|------|
| Docker 容器 | 服务器端 shell/code 执行隔离（推荐） |
| ProcessBuilder + 资源限制 | 轻量级隔离 |
| Noop | 无隔离（默认） |

**安全不可降级保证**：沙箱启动失败 → `SandboxBackendError` → 拒绝执行，**绝不回退到 unsandboxed host 执行**。

**资源限制**（可配置）：

| 参数 | 默认 |
|------|------|
| `cpuCores` | 1.0 |
| `memoryMb` | 1024 |
| `wallSeconds` | 60 |
| `network` | deny |

> 实现约定：`cpuCores` 遵循 Docker `--cpus` 语义（Docker 1.13+），是分数核心配额（`1.0` = 一个完整核心，`0.5` = 半个核心，`2.5` = 两个半核心），**不是** CPU 时间预算（秒）。`DockerSandboxBackend` 将该值原样传给 `--cpus`。

**默认实现**：`NoOpSandboxBackend`（直接在 host 执行——Layer 1 无沙箱）。

**实现约定**（plan 219 落地后补录）：

- 接口与全部数据对象（`SandboxRequest`/`SandboxResult`/`SandboxConfig`/`SandboxException`/`SandboxFailureReason`）位于 `io.nop.ai.agent.security` 包，与其他纵深防御链接口同包（裁定：保持链组件内聚，避免新建 `io.nop.ai.agent.sandbox` 包；`SandboxException extends NopAiAgentException` 已跨包）。
- `DockerSandboxBackend` 通过 Docker CLI（`ProcessBuilder` 调用 `docker run`/`docker kill`/`docker rm`）与容器交互，不引入 Docker Java client 依赖（裁定：CLI 是服务器端通用前提；与 `NoOpSandboxBackend` 的 `ProcessBuilder` 模型对称）。
- 失败分类（`SandboxFailureReason`）：`IOException` 或 daemon 连接错误 → `DOCKER_UNAVAILABLE`；镜像缺失 / OCI runtime / 权限拒绝 → `CONTAINER_START_FAILED`；exit code 137（OOM-killer SIGKILL）→ `RESOURCE_LIMIT_EXCEEDED`；exit code 124（`timeout` SIGTERM）/ wall-budget 超时 → `TIMEOUT`；其余非零 exit → `CONTAINER_START_FAILED`（保守 fail-closed，绝不静默吞掉）。
- 调用前校验（plan 270 / 274）：`execute()` 在启动 `docker` 进程之前对请求做 fail-closed 校验，违法请求抛 `SandboxException` 且绝不触达 `docker run`。`HOST_PATH_NOT_ALLOWED`（plan 270）：hostPath 含 `..` / 非真实存在路径 / 不在 `allowedBaseDirs` 白名单内。`INVALID_ENVIRONMENT_VARIABLE`（plan 274 AUDIT-13-9）：环境变量键不匹配 POSIX 名语法 `^[A-Za-z_][A-Za-z0-9_]*$`（如以 `-`/数字开头、含空格/控制字符/`=`/空串），防止攻击者或 LLM 可控的键注入额外 Docker 标志（如 `--privileged`）。
- 容器清理：`--rm` 保证自然退出后自动删除；超时路径走 `docker kill <name>` + `docker rm -f` 兜底。
- `DefaultAgentEngine.setSandboxBackend` 不调用 `warnIfInsecureDefaults`：`NoOpSandboxBackend` 是 Layer 1 设计性基线（从未被更安全的 shipped 替代取代），与 `AutoApproveGate`（已被 `DefaultApprovalGate` 取代 → 回退是降级）的语义不同。
- 高风险工具执行器（shell-exec / code-exec `IToolExecutor`）如何消费 `ISandboxBackend` 是独立 successor plan；本契约仅提供平台级隔离能力。

**威胁模型注记（plan 276 追溯收口）**：对 `SandboxRequest.environmentVariables` 键的来源做了一次显式链路追溯，结论如下。

- **(a) 当前来源**：截至本注记，`SandboxRequest` 在 main src 中**无任何生产构造点**（grep `SandboxRequest.builder()/SandboxRequest.of()/new SandboxRequest` 于 `nop-ai/nop-ai-agent/src/main/java/` 仅命中 `SandboxRequest.java` 自身的 `Builder.build()`）。`ISandboxBackend.execute(...)` 在 main src 中**从未被调用**（grep `sandboxBackend.execute` / `getSandboxBackend().execute` / `*.execute(...sandbox` 于 main src = 0 命中）。`DefaultAgentEngine.sandboxBackend` 默认 `NoOpSandboxBackend.INSTANCE`（`DefaultAgentEngine.java:346-347`），经 `setSandboxBackend` setter（`:1520`）与 `ReActAgentExecutor.Builder.sandboxBackend`（`:3114`）透传到 `ReActAgentExecutor.sandboxBackend`（`:299`）后**仅存储、从不调用**。因此 `environmentVariables` 当前仅来源于测试 fixture（`TestDockerSandboxBackend`/`TestNoOpSandboxBackend`/`TestSandboxWiring` 构造 `SandboxRequest` 时直接传入 env map）与 `Builder` 默认空 map。
- **(b) 键当前非 LLM 可控**：由于不存在从 LLM tool call 到达 `SandboxRequest.environmentVariables` 的任何路径（无生产构造点 + `execute` 从未被调用），env 键当前**不可被 LLM 攻击者控制**。
- **(c) plan 274 校验的 defense-in-depth 定位**：`DockerSandboxBackend.buildDockerCommand` 的 POSIX 键校验（`^[A-Za-z_][A-Za-z0-9_]*$`，`INVALID_ENVIRONMENT_VARIABLE` reason，plan 274 AUDIT-13-9）与 host-path 白名单（`HOST_PATH_NOT_ALLOWED`，plan 270）在当前无活跃攻击面的前提下仍属**正确的 defense-in-depth**——fail-closed 消费侧校验保证：即使未来构造点接入引入了 LLM 可控的键，校验已在消费侧成立，不需要再回填补丁。
- **(d) 前瞻说明（独立 successor 接入时）**：当 shell-exec / code-exec 工具执行器（设计上文已声明的独立 successor plan）接入 `ISandboxBackend`，将 `IToolExecutor` 的参数映射进 `SandboxRequest.environmentVariables` 时，env 键将**变为 LLM 可控**。届时 plan 274 的 POSIX 键校验即从 defense-in-depth 升级为**活跃防线**——这正是其 fail-closed 校验被提前落地于消费侧的价值。该 successor 接线不在本注记范围。

### 7.2 ISensitivePathProvider — 敏感路径配置

**职责**：外部化敏感路径 denylist，支持 Delta 覆盖。

**配置来源**：`security-sensitive-paths.xdef` schema → YAML/XML 外部配置。

**特性**：

- 路径前缀匹配 + 文件后缀匹配
- Workspace 感知排除（workspace 内的 `/root` 前缀不阻止合法操作，只阻止凭证叶子文件）
- 命令级扫描（`rm /tmp/ok /etc/bad` → 阻止整个命令）

**默认实现**：`DefaultSensitivePathProvider`（内置 denylist + workspace 排除）。

### 7.3 IAuditLogger — 安全审计日志

**职责**：持久化安全事件到数据库（而非内存 log），支持审计查询。

**记录内容**：每次 gate_decision 的结构化日志（fingerprint, level, decision, reason, sessionId, timestamp）。

**默认实现**：`Slf4jAuditLogger`（写入标准日志——单进程够用）。

## 8. 纵深防御总结

外部内容（Web fetch / Channel / File read）经过的防御层：

```
外部内容
  │
  ├─ Layer 2: IContentGuardrail ──── 注入检测 + wrap_untrusted()
  │                                    │
  │                                    └─ enforce 模式: 替换为 [BLOCKED]
  │
  ├─ Tool Call 产生
  │   │
  │   ├─ Layer 2: Tool Call 注入守卫 ── origin trace 在 untrusted 块内 → 拒绝
  │   │
  │   ├─ Layer 4: ISensitivePathProvider ── 敏感路径 → hard-block
  │   │
  │   ├─ Layer 2: IPermissionMatrix ── 通道 × 等级矩阵
  │   │
  │   ├─ Layer 1: IPermissionProvider ── deny/allow 工具权限
  │   │
  │   ├─ Layer 1: IPathAccessChecker ── deny/allow 路径权限
  │   │
  │   └─ Layer 2: ISecurityLevelResolver ── hints → SecurityLevel
  │       │
  │       └─ Layer 3: IApprovalGate ── 人类审批
  │           │
  │           ├─ Layer 3: IDenialLedger ── 拒绝计数 + 阈值暂停
  │           ├─ Layer 3: IPostDenialGuard ── 盲重试阻止
  │           │
  │           └─ Layer 4: ISandboxBackend ── 隔离执行
```

## 9. 渐进式增强路径

按 `00-vision.md` 约束 4（渐进式增强），安全能力按 XDSL 配置丰富度逐步引入，不按时间分阶段。

### 最小可运行（只 Layer 1）

```yaml
# agent.xdef 中声明
tools:
  allowed: [read-file, write-file, call-agent]
  denied: [delete-file]

file-access:
  - pattern: ".nop/**"
    access: read-only
```

**行为**：IPermissionProvider 做 deny/allow；IPathAccessChecker 做路径检查；子 Agent 权限继承。不需要任何 XDSL 安全配置。

### 引入分级（Layer 2）

```yaml
# security-policy.xml（XDSL）
<security-policy>
  <rule action-kind="shell.exec" level="ELEVATED"/>
  <rule action-kind="network.fetch" level="STANDARD"
        condition="!trustedSource" upgrade-to="RESTRICTED"/>
</security-policy>
```

**行为**：ISecurityLevelResolver 解析等级；IContentGuardrail 启用注入检测；IPermissionMatrix 按通道限制。

### 引入审批治理（Layer 3）

```yaml
# approval-config.xml（XDSL）
<approval-governance>
  <denial-threshold>3</denial-threshold>
  <approval-timeout-seconds>300</approval-timeout-seconds>
</approval-governance>
```

**行为**：高风险操作需要人类审批；拒绝计数达阈值暂停；盲重试被阻止。

### 引入平台安全（Layer 4）

```yaml
# platform-security.xml（XDSL）
<sandbox backend="docker"/>
<sensitive-paths config="security-sensitive-paths.xml"/>
<audit persistence="database"/>
```

**行为**：shell/code 在 Docker 容器内执行；敏感路径 hard-block；审计日志持久化到 DB。

**关键**：从 Layer 1 到 Layer 4，引擎代码不变——只替换接口实现和增加 XDSL 配置。

## 10. Shell 与高风险工具

对 shell、http 请求、文件写入、删除类工具，额外做程序级限制：

- 允许工作目录白名单
- 环境变量白名单（默认只传 PATH, HOME, LANG）
- 输出大小限制
- 命令超时
- 进程组清理

这些约束不应交给 prompt 决定。

## 11. 与工具验证的关系

权限检查和工具验证是不同层次：

- **权限检查**：你能不能调用这个工具或路径
- **工具验证**：你传入的参数是否合法、安全、符合 schema

这两层都必须存在。

## 12. 调研来源

本篇设计融合了 15+ 个 Agent 框架的安全机制调研：

| 框架 | 借鉴点 | 对应 Layer |
|------|--------|-----------|
| **OpenSquilla** | 5 层纵深防御；4 类注入检测；`<untrusted>` 信封；L0~L3 安全等级；DenialLedger + 阈值暂停；Post-denial 守卫；敏感路径 hard-block；工具硬编码 denylist | Layer 1-4 |
| **VoltAgent** | Input/Output Guardrail pipeline；`needsApproval` per tool；预构建 guardrail 库 | Layer 2-3 |
| **opencode** | 3-source 权限合并；子 Agent 权限派生（deny 传播） | Layer 1 |
| **PilotDeck** | 5 permission modes；PreToolUse hook → permission → audit → execute 流程 | Layer 2-3 |
| **Hermes Agent** | Terminal 6 sandbox backends (Docker/SSH/Modal/...) | Layer 4 |

详见 `ai-dev/analysis/agent-survey/` 目录下的各调研报告。

## 13. 与其他文档的关系

- `00-vision.md` — 设计原则（含渐进式增强约束）
- `01-architecture-baseline.md` — 架构基线（多租户、资源隔离）
- `nop-ai-agent-context-model.md` — 上下文模型（权限继承、fork）
- `nop-ai-agent-roadmap.md` — 分层接口（IPermissionProvider 在 Layer 1，IContentGuardrail 在 Layer 2）
- `02-execution-model.md` — Hook 生命周期（Tool dispatch 中的安全检查点）
