# 渐进式设计审计就绪度分析：Nop AI Agent 安全模型

**分析日期**：2026-06-11
**分析范围**：`nop-ai-agent` 安全子系统（`nop-ai-agent-security-and-permissions.md`）
**审计标准**：`00-vision.md` 约束 #4（渐进式增强）
**实现状态**：全部接口处于设计阶段（roadmap.md 标记 ❌ 未实现）

---

## 1. Security Progressive Design Overview

安全模型定义了 4 层接口组织（security-and-permissions.md §3:31-47），依赖关系严格单向：

```
Layer 4: Platform Security (ISandboxBackend, ISensitivePathProvider, IAuditLogger)
    ↑ 依赖 Layer 1-3
Layer 3: Approval Governance (IApprovalGate, IDenialLedger, IPostDenialGuard)
    ↑ 依赖 Layer 1-2
Layer 2: Policy Extensions (ISecurityLevelResolver, IContentGuardrail, IPermissionMatrix)
    ↑ 依赖 Layer 1
Layer 1: Core Security (IPermissionProvider, IToolAccessChecker, IPathAccessChecker)
    ↑ 无内部依赖
```

每层有 pass-through/NoOp 默认实现。设计声称"系统可以只带 Layer 1 运行"（security-and-permissions.md §3:49）。

---

## 2. Per-Layer Analysis

### 2.1 Layer 1: Core Security

**Progressive — 符合原则的部分：**

- IToolAccessChecker（§4.2:75-92）：明确 deny-first + fail-closed 语义。该接口的默认行为是"什么都不允许"（fail-closed），极端安全。
- IPathAccessChecker（§4.3:93-127）：deny/allow glob 匹配，有明确的路径规范化要求。默认 deny-list 是编码内但可通过 XDSL 覆盖（§4.3:126）。
- IPermissionProvider（§4.1:55-73）：3-source merge 有明确的优先级（session > agent > default）和 deny-first 语义。
- Subagent 权限继承（§4.4:128-138）：权限交集/收缩语义明确。

**NOT Progressive — 违反或模糊的部分：**

1. **IAuditLogger 缺失**（关键）：Layer 1 做所有安全决策（deny/allow），但审计接口 IAuditLogger 定义在 Layer 4（§7.3:373-378）。§4.5:141-148 中描述的审计字段仅以散文形式存在，无正式接口定义。这意味着运行仅 Layer 1 的系统**没有结构化审计能力**——安全决策发生了但无法被追溯查询。这是违反渐进式原则的：Layer 1 声称可独立运行，但独立运行时缺乏自身决策的审计记录能力。

2. **Hardcoded Deny 列表**（§4.2:85）：shell_exec、file_write、file_delete、git_push 等工具在未显式授权时默认拒绝。但"硬编码 deny"意味着这些规则**不可通过 XDSL 覆盖**。这违反了约束 #4 的"更多假定通过外部 XDSL 模型逐步引入"——这里是把假定硬编码在运行时内部，永远拒绝 XDSL 配置的可能性。如果运维人员确实需要允许 shell_exec（在沙箱内），必须改代码。

3. **Hardcoded Deny 列表未明确定义**：§4.2:85 仅举了三四个例子，实际列表不在文档中。列表位置未知（代码常量？配置文件？）。作为 Layer 1 基线行为的一部分，审计者无法确认哪些工具被硬编码拒绝。

4. **"opencode 模式"来源未定义**（§4.1:57）：`IPermissionProvider` 标注为"3-source merge（opencode 模式）"，但"opencode 模式"不在本文档或 vision.md 中定义。这要么需要跨文档引用，要么是未定义的术语。

5. **子 Agent 权限提升预防机制未定义接口**（§4.4:128-138）：设计说"子 Agent 只能继承或收缩权限，不能提升"，但未定义谁负责实施此约束。是 IPermissionProvider 的内部逻辑？还是独立接口？文档未澄清。

**审计就绪度：**

| 标准 | 结果 | 说明 |
|------|------|------|
| 每个决策可追溯至特定规则 | ⚠️ 部分通过 | 确定性 deny-first 算法可重建，但 §4.5 审计字段缺少"规则源识别"（3 个来源中的哪一个命中）和"时间戳" |
| 结构化审计日志 | ❌ 未通过 | 无正式接口。§4.5 字段为散文描述，缺少 `IAuditLogger` 接口定义 |
| 注入式 vs XDSL 约束可区分 | ❌ 未通过 | 审计日志设计未要求区分来源（session 注入 vs agent DSL vs 默认规则）。硬编码 deny 和 XDSL deny 在日志中无法区分 |
| 可重放验证决策 | ⚠️ 部分通过 | 算法确定性能重放，但缺少"审计日志包含了足够的上下文以重放"的保证 |
| 决策无审计缺口 | ❌ 未通过 | IAuditLogger 在 Layer 4，导致 Layer 1 决策无结构化审计 |

**具体问题点：**

| # | 位置 | 问题 |
|---|------|------|
| L1-G1 | §4.1:57 | "opencode 模式"未定义术语 |
| L1-G2 | §4.2:85 | Hardcoded deny 列表未完整定义且不可 XDSL 覆盖，违反约束 #4 |
| L1-G3 | §4.3:126 | 敏感路径 denylist 部分内置、部分 XDSL——谁优先未定义 |
| L1-G4 | §4.4:128-138 | 无接口定义谁负责子 agent 权限提升预防 |
| L1-G5 | §4.5:141 | 无 IAuditLogger 接口；§7.3 定义在 Layer 4 |
| L1-G6 | §4.5:142-148 | 审计字段缺少 timestamp、rule source ID、rule text |
| L1-G7 | 整体 | IPermissionProvider 的 3-source 优先级冲突时（session deny vs agent allow）退化为"按优先级选择"但未定义如何记录规则优先级 |

---

### 2.2 Layer 2: Policy Extensions

**Progressive — 符合原则的部分：**

- 三个接口都有 pass-through 默认实现（§5.1:187 NoOpSecurityLevelResolver、§5.2:228 NoOpContentGuardrail、§5.3:252 PassThroughPermissionMatrix）。启用 Layer 2 = 替换默认实现 + 添加 XDSL 配置，引擎代码不变。
- ISecurityLevelResolver 的规则表（§5.1:179-185）是确定性的，无 AI 决策，可审计。
- IContentGuardrail 有清晰的 `off / report / enforce` 模式（§5.2:224），以及明确的 guardrail 预构建列表（§5.2:208-213）。

**NOT Progressive — 违反或模糊的部分：**

1. **trustedSource 评估未定义接口**（§5.1:171）：`LevelHints` 包含 `trustedSource` 布尔值，但文档未定义**谁负责评估**可信源。如果这是另一个接口（比如 `ITrustEvaluator`），它应该属于哪一层？如果评估逻辑在 ISecurityLevelResolver 内部，则同一接口兼做等级解析和信任评估，违反单一职责。如果评估来自引擎上下文（`AgentExecutionContext`），则执行上下文需要携带信任状态——这可能是 Layer 1 的核心字段，但文档未提及。

2. **Tool Call 注入守卫的 Layer 归属问题**（§5.2:226）：Tool Call Origin 追踪需要贯穿工具调用的执行链路——origin trace 需要被记录在工具调用元数据结构中。这个数据结构应该属于 Layer 1（IToolAccessChecker 的输入）。但文件把此能力放在 Layer 2 的 IContentGuardrail 中提及，暗示 origin 追踪是 Layer 2 才引入的数据结构。这意味着 Layer 1 的 IToolAccessChecker 在做权限检查时看不到 origin trace，降低其有效性。

3. **ContentGuardrail report 模式审计去向未定**（§5.2:224）：`report` 模式需要审计日志目标。如果 IAuditLogger 在 Layer 4，Layer 2 的 report 模式要么依赖不存在的审计接口，要么使用 Slf4j 直接输出（非结构化），要么等待 Layer 4 启用后才能工作。文档未指定。

**审计就绪度：**

| 标准 | 结果 | 说明 |
|------|------|------|
| 每个决策可追溯至特定规则 | ✅ 通过 | 等级规则表确定性的，每条规则有明确的 action_kind + 条件 |
| 结构化审计日志 | ⚠️ 部分通过 | LevelHints 可审计（flat dataclass），但审计输出目标未指定 |
| 注入式 vs XDSL 约束可区分 | ✅ 通过 | LevelHints 是运行时可注入的；规则表从 XDSL 装载 |
| 可重放验证决策 | ✅ 通过 | 确定性规则表 + flat hints → 完全可重放 |
| 决策无审计缺口 | ❌ 未通过 | report 模式需要 audit logger，但 logger 在 Layer 4 |

**具体问题点：**

| # | 位置 | 问题 |
|---|------|------|
| L2-G1 | §5.1:157,171 | `trustedSource` 由谁评估未定义。无 ITurstEvaluator 接口 |
| L2-G2 | §5.1:179-185 | `!trustedSource` 条件依赖外部输入，但输入的提供者未明确 |
| L2-G3 | §5.2:226 | Tool Call origin trace 依赖 Layer 2 但安全功能穿透依赖 Layer 1 IToolAccessChecker |
| L2-G4 | §5.2:224 | `report` 模式的日志去向未定义——IAuditLogger 在 Layer 4 |
| L2-G5 | §5.1:167-174 | LevelHints 的 `highImpact` 由谁定义？无接口指定 |

---

### 2.3 Layer 3: Approval Governance

**Progressive — 符合原则的部分：**

- 三个接口有合理的 pass-through 默认（§6.1:277 AutoApproveGate、§6.2:295 NoOpDenialLedger、§6.3:325 PassThroughPostDenialGuard）。
- DenialResult 结构化信封（§6.3:315-323）有明确的枚举 reason、actionFingerprint、message。
- 审批超时、拒绝阈值的概念清晰。

**NOT Progressive — 违反或模糊的部分：**

1. **DenialResult.reason 枚举不完整**（§6.3:317）：枚举值包括 `human_rejected / threshold_exceeded / repeated_same_intent`，但 IApprovalGate 的行为（§6.1:268-269）明确指出"超时或拒绝 → DenialResult"——超时和拒绝是两个不同的事件，但枚举没有 `timeout`。审计时无法区分"被人类拒绝"和"审批超时自动拒绝"。

2. **AutoApproveGate 的安全风险**（§6.1:277）：AutoApproveGate 是所有审批请求自动通过。如果运维人员配置了 Layer 3 XDSL（`approval-config.xml`）但忘了配置审批通道，系统会**静默批准所有高风险操作**。设计中没有提到"当审批配置存在但审批门仍为默认实现时"的验证规则。这可能在配置错误时产生安全假象。

3. **Approval 审批通道未抽象为接口**（§6.1:271-275）：审批通道（Web UI 通知、GraphQL Subscription、RPC 轮询）以散文列出，未定义为可插拔接口。新审批通道需要修改引擎代码或通过不可见的方式接入。这违反了"扩展通过添加接口实现，不通过阶段切换"。

4. **DenialLedger 持久化契约**（§6.2:291-292）：文档说"DenialLedger 持久化到数据库"，但 NoOpDenialLedger 不持久化。接口契约未定义 persistence 要求——`IDenialLedger` 接口本身是否需要保证持久化？如果否，非持久化的实现可能导致 session 恢复后拒绝计数丢失，Agent 绕过阈值。

**审计就绪度：**

| 标准 | 结果 | 说明 |
|------|------|------|
| 每个决策可追溯至特定规则 | ✅ 通过 | 审批流程的每个节点（请求→审批/拒绝→执行/阻止）都有 trace |
| 结构化审计日志 | ⚠️ 部分通过 | DenialResult 是结构化的，但审批动作本身无标准审计事件格式（谁在何时批准了哪个操作） |
| 注入式 vs XDSL 约束可区分 | ⚠️ 部分通过 | 规则来自 XDSL，但审批通道的输入来源（Web UI vs API vs 自动）在审计中未区分 |
| 可重放验证决策 | ✅ 通过 | 确定性 fingerprint + 规则 → 可重放 |
| 决策无审计缺口 | ❌ 未通过 | 超时 vs 拒绝未在枚举中区分；AutoApproveGate 的自动批准可能不被审计 |

**具体问题点：**

| # | 位置 | 问题 |
|---|------|------|
| L3-G1 | §6.3:317 | DenialResult.reason 枚举缺少 `timeout`，无法区分超时和拒绝 |
| L3-G2 | §6.3:315-323 | 审批动作（approve）本身无结构化审计事件格式 |
| L3-G3 | §6.1:271-275 | 审批通道不是可插拔接口，而是硬编码列表 |
| L3-G4 | §6.1:277 + §6.1:271 | AutoApproveGate 与审批通道配置共存时无配置校验 |
| L3-G5 | §6.2:291 | IDenialLedger 接口未定义持久化契约要求 |

---

### 2.4 Layer 4: Platform Security

**Progressive — 符合原则的部分：**

- NoOpSandboxBackend（§7.1:356）作为默认，安全不可降级保证（§7.1:345-346）明确。
- ISensitivePathProvider 外部化敏感路径配置（§7.2:361-369），支持 Delta 覆盖。
- Slf4jAuditLogger（§7.3:378）作为单进程够用的默认实现。

**NOT Progressive — 违反或模糊的部分：**

1. **ISensitivePathProvider 与 IPathAccessChecker 重叠**（§7.2:360 vs §4.3:126）：Layer 1 的 IPathAccessChecker 已有内置敏感路径保护（`.nop/.permissions/**`、SSH 密钥等），Layer 4 的 ISensitivePathProvider 也提供敏感路径配置。两者关系未定义：
   - 哪个优先级更高？
   - 两者是否应该共存？
   - Layer 4 启用后 Layer 1 的内置敏感路径是否被取代？
   - 如果 IPathAccessChecker 也检查敏感路径，而 ISensitivePathProvider 提供不同列表，路径检查行为不确定。

2. **IAuditLogger 放在 Layer 4 但所有层需要它**（§7.3:374）：这是最大的设计问题。§4.5 描述 Layer 1 需要审计，§5.2:224 Layer 2 report 模式需要审计，§6.3 Layer 3 审批需要审计——但正式接口放在 Layer 4。虽然 Slf4jAuditLogger 可以通过 Slf4j 记录，但从接口设计上讲，IAuditLogger 至少应该放在 Layer 1（或单独的 Audit 层），使得依赖审计的任何实现都可以针对接口编程。

3. **SandboxBackend 不可降级保证的实现依赖**（§7.1:345-346）："绝不回退到 unsandboxed host 执行"的保证要求沙箱后端能可靠检测自身故障。但 NoOpSandboxBackend 永远"可用"（因为它在 host 直接执行）。当运维人员切换到 Docker 后端后，如何确保 Docker 不可用时不会悄悄回退到 NoOp？文档说"拒绝执行"，但实现这个保证需要在 DI/配置层面防止 NoOp 作为 fallback 被注入——这需要额外的工程实现，文档未描述。

**审计就绪度：**

| 标准 | 结果 | 说明 |
|------|------|------|
| 每个决策可追溯至特定规则 | ✅ 通过 | 沙箱执行和审计日志有明确决策点 |
| 结构化审计日志 | ✅ 通过 | IAuditLogger 是结构化接口 |
| 注入式 vs XDSL 约束可区分 | ✅ 通过 | 审计日志记录 fingerprint, level, decision, reason |
| 可重放验证决策 | ✅ 通过 | 结构化日志可重放 |
| 决策无审计缺口 | ❌ 未通过 | IAuditLogger 接口本身不在文中定义字段 schema，仅以散文描述 |

**具体问题点：**

| # | 位置 | 问题 |
|---|------|------|
| L4-G1 | §7.2:360 vs §4.3:126 | ISensitivePathProvider 与 IPathAccessChecker 内置敏感路径重叠，优先顺序未定义 |
| L4-G2 | §7.3:373-378 | IAuditLogger 接口字段 schema 未在文中定义 |
| L4-G3 | §7.3:374 | IAuditLogger 在 Layer 4，但接口被 Layer 1-3 隐性需要 |
| L4-G4 | §7.1:345-346 | 安全不可降级的实现保证（在 DI 层面防止 NoOp fallback）未描述 |
| L4-G5 | §7.3:373 | IAuditLogger 的 "gate_decision" 记录格式未定义字段 schema |

---

## 3. Cross-Layer Issues

### 3.1 审计层归属错误（Critical）

IAuditLogger 是唯一被所有层需要的横切接口。把它放在最高层（Layer 4）意味着 Layer 1-3 无法在接口层面声明对审计的依赖。实际后果：

- Layer 1 的 IPermissionProvider、IToolAccessChecker、IPathAccessChecker 实现需要 IAuditLogger 时，要么绕过接口直接写 Slf4j，要么通过 DI 容器注入 Layer 4 接口。
- DI 注入方案要求 nop-ai-agent-core 模块有对 Layer 4 接口的编译依赖，违反了"上层只依赖下层"的架构规则。
- 如果 Layer 4 不是 classpath 的一部分（多模块构建时），编译期就出问题。

**修复方向**：将 IAuditLogger 移到 Layer 1（或独立的 Audit 子层，如 Layer 0.5）。Slf4jAuditLogger 作为默认实现在核心层。

### 3.2 安全接口与执行模型 Hook 的集成点未定义（Critical）

security-and-permissions.md §4.2:88-89 说 工具访问检查是"每次工具执行前检查"，但执行模型 Hook（02-execution-model.md §5.1:91）定义的 PRE_ACTING 钩子是"工具执行前，可 block"——这是安全检查的自然集成点。

**但两个文档都没描述这个集成**：
- 安全文档从不引用 Hook 机制
- 执行模型从不引用安全检查接口
- 没有定义安全 Hook 的优先级（安全检查必须在业务 Hook 之前执行）
- 没有定义当多个安全检查（IToolAccessChecker → IPathAccessChecker → ISecurityLevelResolver → IApprovalGate）时如何按序组装

**后果**：两个子系统独立设计，将来集成时有三种可能性：
1. 安全检查不是 Hook——而是引擎主循环中的硬编码调用（违反"扩展通过添加接口"）
2. 安全检查作为优先级最高的 Hook 注册——但谁负责注册？自动发现？手动配置？
3. 安全检查在 PRE_ACTING 之前/之后额外执行——增加了引擎主循环的边界

### 3.3 纵深防御管线未定义检查顺序（Moderate）

§8:382-411 的纵深防御图显示一条线性管线，但：
- Layer 1（IPermissionProvider → IPathAccessChecker）→ Layer 2（ISecurityLevelResolver）→ Layer 3（IApprovalGate）→ Layer 4（ISandboxBackend）
- 如果 Layer 2 的 IPermissionMatrix 阻断了一个操作，后面的 Layer 3/4 检查还执行吗？管线是短路还是全路径？
- 如果某个层的接口使用 NoOp 默认，管线如何跳过该层？
- 管线顺序在代码中是如何保证的？是否可配置？

### 3.4 跨 Layer 的信任评估链未定义（Moderate）

信任评估链涉及多个层：
- Layer 2: ISecurityLevelResolver 需要 `trustedSource` 布尔值
- Layer 2: IContentGuardrail 需要 `untrusted` 信封包裹机制
- Layer 1: IToolAccessChecker 的 deny/allow 可能也需要信任输入

但没有接口定义信任评估（`ITrustEvaluator` 或类似接口）。trustedSource 的来源、传播、缓存、更新机制未定义。多个层各自评估信任可能导致不一致。

### 3.5 Subagent Permission 的引擎层实施（Moderate）

§4.4:128-138 定义子 agent 权限继承为"父权限 ∩ 子配置"，但：
- 执行模型文档（02-execution-model.md）未提及子 agnet 的安全约束
- 权限交集的计算是在创建子 agent 时（fork 时）还是在每次工具调用时？
- 如果是 fork 时计算，需要子 agent 的 AgentSession 携带"被禁用的权限列表"——这应该是 Agent 执行上下文的一部分，但 AgentSession 的字段未定义
- 如果是每次调用时计算，需要 IToolAccessChecker 知道调用链的层级关系——但接口签名只有 `resolve(toolName, agentName, sessionId, channelKind)`，没有调用深度或权限上下文

---

## 4. Audit Trail Assessment

### 当前设计可审计性评分

| 维度 | 分数 | 说明 |
|------|------|------|
| 决策可追溯至规则 | ⚠️ 7/10 | 确定性算法支持重放，但规则源 ID 和规则优先级未记录 |
| 结构化审计日志 | ❌ 4/10 | IAuditLogger 定义在 Layer 4；Layer 1-3 以非结构化日志运行 |
| 注入 vs XDSL 区分 | ❌ 5/10 | LevelHints（运行时可注入）有 flat schema；但 Injection Guard 和 3-source merge 无法区分来源 |
| 可重放性 | ⚠️ 7/10 | 确定性规则表 + plain hints 可重放；但审计字段不完整降低可靠性 |
| 无审计缺口 | ❌ 3/10 | IAuditLogger 只能覆盖部署了 Layer 4 的系统；审批超时 vs 拒绝未区分 |

**总体：5/10 — 设计概念清楚但存在结构性缺口**

### 完整审计追踪示例（理想态 vs 设计态）

理想的端到端审计追踪：Agent "code-writer" 的子 agent "shell-exec" 被拒绝执行 shell 命令。

**理想审计记录需要的字段**：
```
sessionId: "s-abc123"
agentName: "shell-exec"         (子 agent)
parentAgentName: "code-writer"   (父 agent)
actorId: "user-42"
timestamp: 2026-06-11T10:30:00Z
decision: DENY
denyLayer: "IToolAccessChecker"
denyRuleSource: "agent-default.yml"
denyRuleID: "rule-3-shell-deny"
denyRuleContent: "shell.*: deny"
fuzzyMatchTree: [
  { rule: "shell.*: deny", source: "agent-default.yml", matched: true },
  { rule: "read-file: allow", source: "agent-default.yml", matched: false }
]
isHardcodedDeny: false
actionFingerprint: "a1b2c3d4"    (仅用于拒绝后守卫)
```

**当前设计实际能产生的记录**：
```
sessionId: "s-abc123"
agentName: "shell-exec"
actorId: "user-42"
toolName: "shell.exec"
reason: "denied"
// 缺少: timestamp, parentAgentName, denyRuleSource, denyRuleID, isHardcodedDeny, 匹配路径
```

### 显著的审计缺口

1. **无标准审计事件数据结构**：`IAuditLogger` 接口的 `gate_decision` 只是在 §7.3:376 提到一句"fingerprint, level, decision, reason, sessionId, timestamp"，没有正式的 schema 定义。审计字段的可扩展性（未来需要添加字段时是否向前兼容）未处理。

2. **无规则源的审计字段**：3-source merge 的结果中，审计需要知道**哪一个源**产生了命中规则。当前设计只记录"规则命中"，不记录"哪个源文件的哪条规则"。

3. **无流程审计事件**：§8:382-411 的纵深防御管线中，每个检查点应该产生审计事件。但设计只定义了最终的决策审计，没有中间检查点的审计。

4. **无审计查询接口**：设计定义了 IAuditLogger（写入端），但没有定义审计查询接口（读取端）。审计者如何查询特定 session 的所有安全决策？如果有，接口属于哪一层？

---

## 5. Recommendations

优先级按 Critical → Significant → Moderate 排列。

### P0 (Must Fix Before Implementation)

| # | 问题 | 位置 | 推荐 |
|---|------|------|------|
| R1 | **IAuditLogger 归属层错误** | L4-G3, L1-G5, Cross-3.1 | 将 IAuditLogger 移入 Layer 1 Core Security。定义 `AuditEvent` 结构体（formal schema），包含：sessionId, agentName, parentAgentName, actorId, timestamp, decision, decisionPoint, ruleSource, ruleId, ruleText, isHardcoded, matchingTrace。Slf4jAuditLogger 作为 Layer 1 默认实现 |
| R2 | **安全接口与 Hook 的集成点未定义** | Cross-3.2 | 在 security-and-permissions.md 中新增章节，明确定义安全检查在 PRE_ACTING hook 中的实现，定义安全 hook 的优先级范围（0-99 为安全保留），定义检查链的短路规则 |
| R3 | **Hardcoded Deny 机制违反渐进式设计** | L1-G2, §4.2:85 | 将 hardcoded deny 改为 XDSL "不可覆盖"机制。定义 `security-hardcoded-deny.xdef` schema，在 `security-policy.xdef` 中增加 `override="forbidden"` 属性。将 deny 列表外部化到 XDSL 文件，仍保留 "不可被用户级覆盖" 的语义，但不再硬编码在 Java 代码中 |

### P1 (Must Fix Before Production)

| # | 问题 | 位置 | 推荐 |
|---|------|------|------|
| R4 | **trustedSource 评估无接口** | L2-G1, L2-G2, Cross-3.4 | 新增 `ITrustEvaluator` 接口（Layer 1 或 Layer 2），定义 `TrustLevel resolve(AgentExecutionContext ctx, ContentSource source)`。IContentGuardrail 和 ISecurityLevelResolver 依赖此接口，而不是接受裸 boolean |
| R5 | **审批通道不可插拔** | L3-G3, §6.1:271-275 | 将审批通道抽象为 `IApprovalChannel` 接口，支持 Web UI、GraphQL、RPC 等实现。AutoApproveGate 的"审批"通过 `AutoApprovalChannel` 实现 |
| R6 | **DenialResult.reason 枚举不完整** | L3-G1, §6.3:317 | 增加 `timeout`、`config_error`（配置错误时的自动拒绝）、`circuit_broken`（熔断状态下的自动拒绝） |
| R7 | **审计日志标准字段 schema** | §7.3:373, L4-G2 | 定义 `SecurityAuditEvent` 的正式 schema（XDSL），包含所有审计字段的 xdef 定义。使审计日志的格式可扩展、可 Delta 定制 |

### P2 (Should Fix Before Feature Complete)

| # | 问题 | 位置 | 推荐 |
|---|------|------|------|
| R8 | **ISensitivePathProvider 与 IPathAccessChecker 重叠** | L4-G1, §7.2:360 vs §4.3:126 | 明确两者的关系：IPathAccessChecker 负责运行时路径检查，ISensitivePathProvider 负责为 IPathAccessChecker 提供敏感路径列表。删除 IPathAccessChecker 的内置敏感路径，全部通过 ISensitivePathProvider 注入 |
| R9 | **子 Agent 权限实施机制未定义** | L1-G4, Cross-3.5 | 在 security-and-permissions.md 中新增子章节，明确定义 `AgentSession` 是否携带权限边界（fork 时计算）或每次调用时动态计算。对应的 `IPermissionProvider` 方法签名可能需要扩展 |
| R10 | **审批配置验证缺失** | L3-G4, §6.1:277 + §6.1:271 | 新增引导期验证规则：当 `security-policy.xdef` 中声明需要审批的等级存在，但 IApprovalGate 实现为 AutoApproveGate 时，启动时发出警告或拒绝启动 |
| R11 | **LevelHints.highImpact 定义** | L2-G5, §5.1:167-174 | 定义 `highImpact` 的确定方式——是由工具定义的静态元数据（`tool.xdef` 中的 impact 字段），还是运行时动态判断 |

### P3 (Nice to Have)

| # | 问题 | 位置 | 推荐 |
|---|------|------|------|
| R12 | **"opencode 模式"术语** | L1-G1, §4.1:57 | 定义或替换为明确术语 |
| R13 | **纵深防御管线顺序** | Cross-3.3, §8:382-411 | 在 §8 中明确定义检查点的执行顺序、短路规则、Each checkpoint's audit responsibility |
| R14 | **审计查询接口** | §7.3:374 | 定义 `IAuditQueryService`（可选，可推后），支持按 sessionId、agentName、timeRange 检索安全决策 |

---

## 总结

安全模型是一个结构清晰的分层设计，每层接口都有 pass-through 默认，**在设计概念层面基本符合渐进式增强原则**。但存在两个结构性缺陷：

1. **审计基础设施归属错误**（IAuditLogger 在 Layer 4）：这是最严重的违反渐进式设计的问题——安全决策在 Layer 1 发生，但审计能力需要到 Layer 4 才有。修复方向是将 IAuditLogger 移到 Layer 1。

2. **安全与执行模型的集成未定义**（No Hook integration）：两个子系统独立设计，谁负责在什么时候调用安全检查未定义。这个缺口在实现阶段会造成"安全检查在引擎主循环中硬编码"的风险，进而破坏渐进式设计。

整体审计就绪度评估：**5/10 — 设计概念清晰但存在结构性缺口**。实施前至少需要修复 R1（审计层归属）和 R2（Hook 集成点），否则实现时必然出现非渐进式的硬编码安全检查。

---

## 参考资料

- `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md`
- `ai-dev/design/nop-ai-agent/00-vision.md`（约束 #4 渐进式增强）
- `ai-dev/design/nop-ai-agent/02-execution-model.md`（Hook 生命周期 §5.1）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`（实现状态 §2.2）
