# Nop AI Agent 渐进式设计审计就绪度分析

> Status: open
> Date: 2026-06-11
> Scope: `ai-dev/design/nop-ai-agent/` 全部 27 篇设计文档（纯设计分析，不涉及代码实现）
> Sub-agents: 10 个独立子 agent 分 3 轮分析+审查+修订，交叉验证达成共识
> Version: v3 (根据第 2-3 轮事实核查+完整性审查+深度分析修订)

## Context

Nop AI Agent 在 `00-vision.md:29` 定义了**设计约束4：渐进式增强**：

> 内部运行时实现最简化——只做"不引入任何外部假定的最简行为"。更多假定通过外部 XDSL 模型逐步引入（权限规则、安全等级、审批策略等）。扩展通过添加接口实现，不通过阶段切换。

本分析回答三个问题：

1. **当前设计文档在各层面是否真的能够达到审计要求？** — 评估设计层面的审计就绪度（不含代码实现状态）
2. **到底哪些特性是渐进式设计的？具体怎么实现的？** — 逐一检视每个设计维度的渐进式合规性
3. **还有哪些遗漏的渐进式设计特性？** — 找出之前未被识别的渐进式模式

**分析边界**：纯设计文档分析，不涉及 Java 代码实现状态。所有"缺失"指设计文档中的定义缺失，非代码缺失。

---

## 一、设计全貌总览

27 篇设计文档按 README.md 组织为 9 个维度：

| 维度 | 文档数 | 渐进式设计核心议题 |
|------|--------|-----------------|
| 设计原则层 | 1 | 渐进式增强约束（00-vision.md） |
| 架构基线层 | 3 | 三对象分离、上下文模型、多 Agent |
| 执行模型层 | 3 | 双循环、LLM 层、工具调用 |
| DSL 层 | 4 | agent/plan/tool/call-agent xdef |
| Java 引擎层 | 3 | ReAct、Hook/Skill、Session 引擎 |
| 语义映射层 | 1 | DSL → 运行时语义映射 |
| 策略层 | 6 | 会话存储、安全权限、可靠性、分支调度、Shell、Skill |
| 集成层 | 1 | 信道连接器 |
| 愿景层 | 1 | Actor Runtime 愿景 |

---

## 二、各维度渐进式设计分析

### 2.1 核心架构（00-vision.md，01-architecture-baseline.md，02-execution-model.md）

#### 真正渐进式的特性

| 特性 | 设计文档依据 | 渐进式实现方式 |
|------|-----------|-------------|
| **AgentModel/Agent/AgentSession 三对象分离** | `01-architecture-baseline.md:84-99` | 配置 (xdef)、执行体 (无状态)、状态 (持久化) 完全解耦。同一配置可被多 Session 复用，状态可独立迁移恢复 |
| **IAgentExecutor 策略模式** | `01-architecture-baseline.md:62` | 执行策略接口化 (`execute()` 返回 `CompletionStage`)，设计预留多实现空间（ReAct、单轮等） |
| **followUp 与 ReAct 独立双循环** | `02-execution-model.md:46-66` | 外层循环 (followUp 检查排队消息) 与内层循环 (ReAct reasoning→acting) 职责分离，各自独立演变 |
| **Hook 生命周期分层** | `02-execution-model.md:93-104` | Layer 1 定义 5 个核心 Hook 点 (PRE_REASONING/POST_REASONING/PRE_ACTING/POST_ACTING/ON_ERROR)，Layer 2 扩展 5 个额外 Hook 点 (PRE_CALL/POST_CALL/REASONING_CHUNK/PRE_COMPACT/POST_COMPACT)。分层明确，每层 Hook 职责独立 |
| **Architecture决策有外部参照** | `00-vision.md:34` | 约束 8 要求每个关键决策有对比分析支撑 — 确保设计不凭空发明 |

#### 设计层面的渐进式争议点

| 争议 | 位置 | 分析 |
|------|------|------|
| Steering 是否应为 Hook？ | `02-execution-model.md:69-85` | Steering（外部消息注入）设计为 ReAct 内层循环的内置机制，非 Hook。从纯设计看，这**不违反渐进式原则** — 如果 IAgentExecutor 接口本身的"最小行为"就包含消息注入（外层 followUp 将消息传递给内层循环），那么 Steering 是最小运行时的必要组成部分，不是"额外假定"。争议点在于：最小运行的边界在哪里？如果 Steering 是为了支持外层 followUp 的基础消息传递机制，则合理；如果 Steering 是 ReAct 之上的额外决策层，则应是 Hook |
| Layer 2 Hook 点是否需要改 Layer 1 代码？ | `02-execution-model.md:104` | 设计文档定义 Layer 2 Hook 点为"Engine 层在对应 Hook 点触发时迭代所有已注册的实现"。从纯设计看，**这符合渐进式原则** — 只要 Layer 1 引擎有一个统一的 Hook 注册/迭代机制（设计已预留），Layer 2 Hook 点只是新增 Hook 常量 + 新增注册实现，不需要修改 Layer 1 引擎代码。但在当前设计中，Hook 注册机制本身的设计未详细展开 |

#### 审计就绪度

| 审计要求 | 设计就绪 | 设计依据 |
|---------|---------|---------|
| 执行步骤可追溯 | ✅ AgentEventPublisher + Event Log | `01-architecture-baseline.md:63`，设计定义了事件类型和发布机制 |
| Hook 执行有审计事件 | ❌ Event Log 4 种 Entry 类型中无 `hook_execution` | `glossary.md:57-66` 事件类型表 |
| Steering 注入可追溯 | ❌ Steering 注入不是 Event Log 事件类型 | 同上 |
| Fork/Join 有记录 | ❌ Fork 操作不是事件类型，无父子会话链接记录 | 同上 |

---

### 2.2 安全与权限（nop-ai-agent-security-and-permissions.md）

#### 真正渐进式的特性

| 特性 | 设计文档依据 | 渐进式实现方式 |
|------|-----------|-------------|
| **4 层独立安全接口分层** | `security-and-permissions.md:31-49` | Layer 1 (Core Security) 仅 deny/allow → Layer 2 (Policy) 分级策略 → Layer 3 (Approval) 审批治理 → Layer 4 (Platform) 沙箱+审计。每层定义独立接口，上层依赖下层接口 |
| **三源权限合并** | `security-and-permissions.md:57-73` | Session 级临时规则 + Agent 专属规则 + 默认规则，三源合并语义为 deny-first，未命中默认拒绝 (fail-closed) |
| **子 Agent 权限严格收缩** | `security-and-permissions.md:129-138` | 子 Agent 权限 = 父权限 ∩ 子配置，无法提升。call-agent 是能力扩张入口 |
| **纵深防御管道** | `security-and-permissions.md:382-411` | 外部内容经过多层独立检查（Guardrail → 工具注入守卫 → 敏感路径 → 通道矩阵 → 权限提供 → 路径检查 → 等级解析 → 审批门 → 账本 → 后拒绝守卫 → 沙箱），每层独立可插拔 |
| **每层有 pass-through 默认** | `security-and-permissions.md:49` | 系统可以只带 Layer 1 运行。每层接口设计为有最简默认实现 |

#### 设计层面的渐进式问题

| 问题 | 位置 | 分析 |
|------|------|------|
| **IAuditLogger 在 Layer 4** | `security-and-permissions.md:374-378` | 审计日志是安全底线决策的记录机制，放在 Layer 4 意味着 Layer 1-3 的安全决策 (deny/allow/approve/reject) 在 Layer 1-3 没有审计记录。设计层面应将审计接口移至 Layer 1（或至少 Layer 2），确保最简安全配置就包含审计能力 |
| **Hardcoded deny 规则不可 DSL 覆盖** | `security-and-permissions.md:85` | 文档明确 shell_exec、file_write 等为"硬编码 deny（不可被 DSL 覆盖）"。这本身违反"通过 XDSL 配置逐步引入"的原则 — 硬编码规则不是 XDSL 配置，且无法被 Delta 定制移除。**渐进式设计的正确做法**：定义 `hardcoded_protections.xdef` schema，默认 deny 名单通过此 XDSL 装载，允许 Delta 在受控下修改。但需要配套"安全不可降级"保证 |
| **trustedSource 接口未定义** | `security-and-permissions.md:171` | `trustedSource` 作为 ISecurityLevelResolver 的 LevelHints 输入，本身需要程序化判断。设计定义了 Hint（布尔值）但未定义谁负责计算 `trustedSource`。需要一个 `IContentTrustEvaluator` 接口 |

#### 审计就绪度

| 审计要求 | 设计就绪 | 设计依据 |
|---------|---------|---------|
| 安全决策可追溯到具体规则 | ✅ 三源合并 + deny-first 语义 | `security-and-permissions.md:71-72` |
| 结构化审计日志 | ⚠️ IAuditLogger 定义在 Layer 4，非 Layer 1 | `security-and-permissions.md:372` |
| 程序约束 vs DSL 配置可区分 | ⚠️ 硬编码 deny vs XDSL allow 混合但无区分标记 | `security-and-permissions.md:85` |
| 安全决策可确定性重放验证 | ⚠️ trustedSource 等判断未定义确定性算法 | `security-and-permissions.md:171` |

---

### 2.3 可靠性与上下文压缩（nop-ai-agent-reliability.md，nop-ai-agent-context-model.md）

#### 真正渐进式的特性

| 特性 | 设计文档依据 | 渐进式实现方式 |
|------|-----------|-------------|
| **5 层压缩管道的 Layer 0 和 Layer 4** | `reliability.md:246-256` | Layer 0 (Tool Result 预截断) 每次工具执行后自动触发，独立于其他层；Layer 4 (强制退出) 硬上限保护，不参与升级链。此 2 层真正独立 |
| **双维度 OR 门触发** | `reliability.md:260-261` | token 占比 + 消息数量两个维度独立检查，任一越线即触发。两维度互不影响 |
| **前缀缓存感知** | `reliability.md:245，llm-layer.md:276-285` | 压缩只操作 Log Zone，不触及前缀区。引擎层通过 prefixLength + prefixHash 两个字段管理，不引入新数据结构 |
| **ICompressionStrategy 扩展点** | `reliability.md:330-355` | Layer 3 可插拔设计，通过 Delta 配置替换默认摘要逻辑。4 种预定义策略（FullSummary/KeyInfoExtraction/HierarchicalRolling/VectorArchive） |
| **错误分类（三分类）** | `reliability.md:108-124` | RETRYABLE / NON_RETRYABLE / RECOVERABLE 三类，确定性的分类标准 |
| **逐级升级、禁止跳级** | `reliability.md:240` | Layer 1→2→3→4 严格逐级尝试，本级解决问题则停止。清晰的可验证规则 |
| **工具调用四阶段修复管线** | `reliability.md:50-61` | flatten→scavenge→truncation→storm 四阶段 Chain of Responsibility，每阶段有明确的触发条件和算法 |
| **工具调用注入守卫** | `security-and-permissions.md:226` | tool call 的 origin trace 位于 `<untrusted>` 块内→拒绝执行。双重防护 |

#### 设计层面的渐进式问题

| 问题 | 位置 | 分析 |
|------|------|------|
| **Layer 1-3 无 NoOp/PassThrough 设计** | `reliability.md:246-256` | 设计隐含假设"所有层都存在"，未定义每层在前提条件不满足时的 fallback。符合渐进式原则的做法是：Layer 1 无可压缩工具→跳过；Layer 2 head/tail 重叠→跳过；Layer 3 LLM 不可用→降级为 Layer 2, 压缩失败→跳过并记录 |
| **Layer 2 依赖 Layer 1 先执行** | `reliability.md:252` | 触发条件为"超过 Layer 1 阈值"，创建了硬排序依赖。Layer 2 应独立检查自己的条件，而非依赖 Layer 1 已触发 |
| **Layer 1 和 Layer 2 本质同算法不同参数** | `reliability.md:251-252` | Layer 1 是工具结果替换，Layer 2 是中间 turn 裁剪。两者本质上是一（裁剪->替换->保留 anchor）算法的不同参数配置。分解为两层服务于"清晰性"而非"独立性" |
| **ICompressionStrategy 仅覆盖 Layer 3** | `glossary.md:37` | 可插拔策略接口只定义在 Layer 3，Layer 1/2 无替换机制 |
| **RECOVERABLE 错误分类未完备定义** | `reliability.md:121-124` | 仅给出一个示例（上下文溢出）。无通用分类规则 |

#### 审计就绪度

| 审计要求 | 设计就绪 | 设计依据 |
|---------|---------|---------|
| 压缩事件可追溯 | ⚠️ CompactionEntry 存在但缺 snapshotId | `session-and-storage.md:109-122` |
| 被移除消息可重建 | ✅ event log append-only，技术上可重建 | `session-and-storage.md:119-120` |
| 错误分类确定性的 | ⚠️ RECOVERABLE 分类未完备 | `reliability.md:121-124` |
| 压缩决策有日志记录 | ⚠️ Layer 2+ 有 CompactionEntry，Layer 0-1 无 | `reliability.md:250-252` |
| 摘要内容可验证 | ❌ 无 checksum 或 hash 链接到原始上下文 | `reliability.md:287-290` |
| 压缩本身失败有恢复策略 | ❌ 仅定义"禁止递归压缩"，无超时/失败退出设计 | `reliability.md:303` |

---

### 2.4 多 Agent 与协作（nop-ai-agent-multi-agent.md，nop-ai-agent-context-model.md §5-6，nop-ai-call-agent-dsl.md）

#### 真正渐进式的特性

| 特性 | 设计文档依据 | 渐进式实现方式 |
|------|-----------|-------------|
| **Agent-as-Subprocess 隐喻** | `00-vision.md:77` | Fork (派生子 Agent)、Exec (替换执行配置)、Pipe (工具调用+消息队列通信)、独立地址空间。操作系统进程隐喻清晰可预测 |
| **call-agent 是标准工具** | `nop-ai-agent-context-model.md:64` | "ask-oracle 作为标准 Tool 定义，不享受特殊引擎层待遇"。人类协同是工具选择，不是引擎内置特性 |
| **子 Agent compaction 隔离** | `nop-ai-agent-multi-agent.md:205-209` | 4 条规则逐步增强：Rule 1（独立 Event Log）架构自然产生，Rule 2-3（pinned 标记）新接口，Rule 4（生命周期保护）新接口。**Additive Rule Stack 模式——最佳渐进式实践** |
| **Internal Agentification** | `nop-ai-agent-context-model.md:153-161` | Phase 1: 硬编码 → Phase 2: 替换为 Agent 实现。薄接口模式——接口预留 Agent 化空间，引擎不关心实现者的内部结构 |
| **全球协调器**明确拒绝 | `nop-ai-agent-multi-agent.md:57` | "拒绝了：Phase 1 引入全局协调器"，承诺中央编排器不作为引擎内置 |

#### 设计层面的渐进式问题

| 问题 | 位置 | 分析 |
|------|------|------|
| **无 IConflictStrategy 接口** | `nop-ai-agent-multi-agent.md:32-33` | 设计描述"Phase 1: fail-fast, Phase 2+: 协调信道"但无接口抽象。应定义 `IConflictStrategy` 接口 + `FailFastStrategy` 默认 |
| **协调消息非持久化** | `nop-ai-agent-context-model.md:27` | "协调消息...否（每轮 ReAct 前注入，不持久化）"。多 Agent 冲突决策无法追溯重放。协调消息至少应在 session 生命周期内持久化为 pinned 自定义事件 |
| **协调总线消息类型定义过早** | `nop-ai-agent-multi-agent.md:97` | 5 种协调消息类型 (scope_claim, operation_intent 等) 在 Phase 1 实现前已完整定义 schema——引入了 Phase 1 不需要的外部假定 |
| **Sibling 通信通过父中转** | `nop-ai-agent-multi-agent.md:104` | "Phase 1 通过父 Agent 中转" vs "Phase 2 通过协调信道"。应定义 `ICoordinationChannel` 接口，Phase 1 的父中转作为默认实现 |

#### 审计就绪度

| 审计要求 | 设计就绪 | 设计依据 |
|---------|---------|---------|
| 多 Agent 决策可追溯 | ❌ 协调消息不持久化 | `nop-ai-agent-context-model.md:27` |
| 资源冲突可确定性重放 | ❌ 无冲突事件类型 | `glossary.md:57-66` |
| Fork/Join 可追溯 | ❌ 无 ForkEvent 类型 | 同上 |
| 子 Agent 生命周期可见 | ❌ 无事件类型 | 同上 |

---

### 2.5 LLM 层（nop-ai-agent-llm-layer.md）

#### 真正渐进式的特性

| 特性 | 设计文档依据 | 渐进式实现方式 |
|------|-----------|-------------|
| **IChatService 作为防腐边界** | `llm-layer.md:44` | Agent Engine 只面对 `IChatService`，所有 LLM 子接口 (`ILlmDialect`, `IModelRouter`, `ITalent`, `IRetryPolicy`) 在此边界后演进。Engine 不需要"版本 2" |
| **ChatMessage 5 子类型 + custom 逃逸口** | `llm-layer.md:52-53` | Layer 1 基础类型 (user/assistant/system) + Layer 2 Tool + escape hatch custom。**注意**：custom 未定义与 ILlmDialect 的契约 |
| **ILlmDialect 纯函数约束** | `llm-layer.md:56` | `convertMessage` 声明为纯函数（前缀缓存正确性依赖于此）。契约式（非类型级）渐进——不引入 @PureFunction |
| **IModelRouter: PassThrough → Smart** | `llm-layer.md:213` | `PassThroughModelRouter` 是显式默认 → `SmartModelRouter` (Judge + Fallback Chain) 是通过接口可选添加。**最规范的渐进式示例** |
| **ITalent 动态准入** | `llm-layer.md:156-161` | 无 Talents 注册→所有工具可用（隐式 NoOp）。扩展通过 SPI 注册 |
| **前缀缓存零接口设计** | `llm-layer.md:283-284` | 两个 int 字段 (`prefixLength` + `prefixHash`) 在现有 context 对象上。零抽象成本，渐进式缓存的最佳实践 |
| **Token 估算三阶段渐进** | `llm-layer.md:85, 114` | chars/4 通用估算 (零配置) → Provider 可选覆盖 → Post-call 校准。无需注册或配置框架 |

#### 设计层面的渐进式问题

| 问题 | 位置 | 分析 |
|------|------|------|
| **IRetryPolicy 无渐进式基** | `llm-layer.md:241-244` | Standard 模式 (3 次重试 + exponential backoff) 作为最小默认——但 3 次重试不是"零外部假定"。最小应为 `NoRetryPolicy` (即 fail-fast)。这是唯一缺少 Layer 1 默认的 Layer 3 组件 |
| **校准状态无主** | `llm-layer.md:114` | `reportedTokens / estimatedTokens` 校准比例存储在何处？AgentSession? 全局 Dialect? 线程安全？设计未指定 |
| **custom ChatMessage 与 Dialect 契约未定义** | `llm-layer.md:52-53` | Dialect 不认识 custom 消息时：抛 `UnsupportedOperationException`？当 raw JSON 传递？设计未指定 |

#### 审计就绪度

| 审计要求 | 设计就绪 | 设计依据 |
|---------|---------|---------|
| LLM 路由决策可追溯 | ⚠️ Smart Router 6 步骤定义清晰，但无路由决策事件类型 | `llm-layer.md:200-208` |
| Fallback 决策可追溯 | ❌ Fallback 链执行无审计事件 | `llm-layer.md:206-207` |
| ITalent 激活可追溯 | ❌ `isSupported` 返回 false 无声消失 | `llm-layer.md:158` |

---

### 2.6 DSL 体系（nop-ai-agent-dsl.md，nop-ai-agent-plan-dsl.md，nop-ai-tool-dsl.md，nop-ai-call-agent-dsl.md，nop-ai-agent-runtime-semantics.md）

#### 真正渐进式的特性

| 特性 | 设计文档依据 | 渐进式实现方式 |
|------|-----------|-------------|
| **DSL-First 设计约束** | `00-vision.md:27` | 先定义 xdef schema 字段语义，再定义 runtime 如何解释。约束 1 确保 DSL 不假设运行时 |
| **agent.xdef 元模型** | `nop-ai-agent-dsl.md` | 完整 Agent 元模型: name → chatOptions → tools → permissions → constraints → prompt → hooks。独立可装载 |
| **agent-plan.xdef Hard/Soft 分离** | `nop-ai-agent-plan-dsl.md:28-68` | 确定性契约 (phases, tasks, dependsOn, error conditions) 与自然语言描述 (narrative) 严格分离。design doc 中列出了 10+ 条运行时验证规则 |
| **plan DSL 独立注册模型** | `nop-ai-agent-plan-dsl.md` | XML/YAML/MD 三种格式均支持。通过 `register-model.xml` 独立装载，不依赖其他 DSL |
| **Delta 定制** | Nop 平台标准机制 | 模型层 Delta 生效，非 agent 特有 |

#### 设计层面的渐进式问题

| 问题 | 位置 | 分析 |
|------|------|------|
| **DSL 拼写错误** | `nop-ai-tool-dsl.md:117` | `call-tools.xdef` 属性 `paralllel`（三 l）— 已记录在文档但未修复。每份 tool calling 配置必须使用错误拼写 |
| **agent.xdef 拼写+概念三方不一致** | `agent.xdef` 实际 schema | xdef 实际字段为 `tokenCompressionThreashold`（双重错误："Compression" 被 glossary 禁用 + "Threashold" 拼写错误）。设计文档 `nop-ai-agent-dsl.md` 静默修正为 `tokenCompactionThreshold`（正确拼写+正确概念），但 xdef 本身未修正。runtime-semantics.md 也使用修正后的名称。这意味着 xdef、设计文档、glossary 三者不一致——是**设计契约偏差**而非仅拼写错误 |
| **无 provenance 字段** | 所有 DSL 的 xdef | 任何 DSL 都无 `source`, `origin`, `inheritedFrom` 字段。无法追踪"这个值从哪里来的" |
| **无版本字段** | 所有 DSL 的 xdef | 无法追踪 DSL 配置的版本变更 |

#### 审计就绪度

| 审计要求 | 设计就绪 | 设计依据 |
|---------|---------|---------|
| DSL 可独立装载/验证 | ✅ 各 DSL 通过独立 register-model.xml | `nop-ai-agent-dsl.md` |
| 最小字段 = 可工作系统 | ⚠️ 基础字段 (name+prompt+tools) 可工作，但 hooks/permissions/skills 无运行时对应 | `agent.xdef` |
| Delta 变更可追溯 | ❌ 无 provenance 字段 | 所有 xdef |

---

### 2.7 信道连接器（nop-ai-agent-channel-connector.md）

#### 真正渐进式的特性

| 特性 | 设计文档依据 | 渐进式实现方式 |
|------|-----------|-------------|
| **引擎零感知信道** | `channel-connector.md §3` | 信道适配器是应用层集成代码，引擎 Layer 1-4 零改动。Gateway 层独立存在 |
| **IChannelConnector 统一接口** | `channel-connector.md:69-79` | 新增信道只需实现 IChannelConnector，通过 Nop IoC 注册。引擎代码不变 |
| **信道能力矩阵** | `channel-connector.md §8` | Markdown/Streaming/文件支持声明式声明 |

#### 设计层面的渐进式问题

| 问题 | 位置 | 分析 |
|------|------|------|
| **字符串事件主题** | `channel-connector.md:169` | `"agent.{sessionId}.events"` 无 schema 校验，无类型安全，多 Connector 同时订阅时无法验证过滤正确性 |
| **无背压协议** | `channel-connector.md:162` | `sendMessage()` 只返回 `AgentMessageAck`，引擎过载时 Connector 无节流信号 |
| **无健康检查接口** | `channel-connector.md:69-79` | 无 `health()` 或 `isConnected()` 方法，Gateway 无法判断 Connector 存活 |
| **接口命名不一致** | 设计文档称 `IChannelConnector`，但审计 prompt 要求检查 `IChannelAdapter` | 后者在设计文档中不存在 |

---

### 2.8 Skill 系统（skill-system-design.md）

#### 真正渐进式的特性

| 特性 | 设计文档依据 | 渐进式实现方式 |
|------|-----------|-------------|
| **Skill 独立文件复用** | `skill-system-design.md:128-133` | 拒绝嵌入 agent.xdef，Skill 是独立 `.skill.yaml` 文件。Agent 只引用 |
| **3 阶段分发计划** | `skill-system-design.md §8.1-8.3` | Phase 1 (无 scenes) → Phase 2 (+ scenes + intentSignature) → Phase 3 (+ Delta + 版本 + 语义向量)。**渐进复杂度路径清晰** |
| **3 阶段确定性匹配** | `skill-system-design.md:158-170` | Phase 1 topPattern → Phase 2 scenes → Phase 3 intentSignature。每阶段输出可日志，匹配结果 = 集合运算 |
| **4 种场景映射规则** | `skill-system-design.md:180+` | required/available × 匹配成功/失败，4 象限无歧义 |

#### 设计层面的渐进式问题

| 问题 | 位置 | 分析 |
|------|------|------|
| **回退到"全体 Skill"违反最简原则** | `skill-system-design.md:168-169` | Phase 3 无匹配→回退到 Phase 2；Phase 2 无匹配→回退到 Phase 1（**全体候选 Skill** 以低优先级注入）。这意味着意图匹配失败时所有 Skill 被激活——大量工具非预期注册，直接违背"不引入任何外部假定"。**正确行为应是回退到"空"而非"全部"** |
| **intentSignature 数组语义未定义** | `skill-system-design.md:87` | 定义为 `str \| str[]`，但匹配算法假设单值 `==` 比较。数组时是 OR 还是 AND？ |
| **tags 字段无匹配作用** | `skill-system-design.md:93` | 定义了 `tags: csv-set`，但匹配算法完全未使用 |
| **Skill 依赖无拓扑排序** | `skill-system-design.md:91` | `dependencies` 定义了 Skill 间依赖，但匹配和执行流程均未处理依赖图的拓扑排序 |
| **无 ISkillProvider NoOp 默认** | `glossary.md:54` | Glossary 定义接口但设计文档未指定无 Skill 时行为 |

---

### 2.9 Shell 设计（nop-ai-shell-design.md，nop-ai-shell-syntax-spec.md）

#### 真正渐进式的特性

| 特性 | 设计文档依据 | 渐进式实现方式 |
|------|-----------|-------------|
| **IToolExecutor 统一抽象** | `shell-design.md:94-98` | BashExecutor (真实 OS) + ShellBashExecutor (虚拟) 实现同一接口，通过 IoC 选择。零入侵 |
| **3 Tier 语法渐进覆盖** | `shell-syntax-spec.md §三/四/五` | T1 (必须实现，基础命令)、T2 (增强，管道/重定向)、T3 (按需，高级扩展)。覆盖率基线明确 |
| **3 级命令优先级 P0/P1/P2** | `shell-design.md §六` | P0 (必须实现)、P1 (增强)、P2 (可选)。实现优先级明确 |
| **ShellCommandRegistry 白名单** | `shell-design.md:239` | 未注册命令 exit 127 — 显式安全边界 |

#### 设计层面的渐进式问题

| 问题 | 位置 | 分析 |
|------|------|------|
| **env 命令可泄漏安全变量** | `shell-design.md:203` | `env` 打印环境变量，无凭证过滤设计。API key、token 可能暴露 |
| **P1 委托模式环境变量白名单未定义** | `shell-design.md:218` | "环境变量=白名单（仅传安全变量）"但未给出白名单内容 |
| **变量展开失败无声** | `shell-syntax-spec.md:425` | 展开失败时"原样传递"，而非 bash 的空字符串。LLM 可能误解 |
| **Parser 异常在 Executor 中传播未定义** | `shell-syntax-spec.md:347-351` | parse 异常是 exit 126？还是抛异常？未定义 |

---

### 2.10 分支亲和调度（nop-ai-agent-branch-affinity-scheduling.md）

#### 真正渐进式的特性

| 特性 | 设计文档依据 | 渐进式实现方式 |
|------|-----------|-------------|
| **Snapshot L2 / Worktree L4 分层归属** | `branch-affinity.md:450-451` | Snapshot (Layer 2 必需) → Branch Affinity (Layer 3) → Worktree (Layer 4 可选)。分层归属明确 |
| **Layer 1 无变更** | `branch-affinity.md:537` | 分支亲和调度完全不触及 Layer 1 核心接口 |
| **并发安全设计** | `branch-affinity.md:432-438` | 数据库唯一约束 + ReentrantLock + fencing token 三重保护 |

#### 设计层面的渐进式问题

| 问题 | 位置 | 分析 |
|------|------|------|
| **FileOperationLock 未定义** | `branch-affinity.md:249` | 仅名称，无接口、无超时策略、无死锁检测 |
| **Turn 边界未精确定义** | `branch-affinity.md:222` | "每次 agent turn 开始前自动 track"——turn 是外循环 (followUp) 还是内循环 (LLM 调用前)？粒度差异 10× |
| **Fencing token 协议未指定** | `branch-affinity.md:438` | token 的生成、传递、校验协议未定义 |
| **RecoveryManager 接口未定义** | `branch-affinity.md:410` | orphaned worktree 扫描的关键组件未定义接口 |
| **Merge Agent 无设计** | `branch-affinity.md:565` | Layer 4 组件但列在当前架构中 |
| **Snapshot vs CoW 概念差异未解释** | `00-vision.md:77` vs `branch-affinity.md` | Vision 用 CoW (Copy-on-Write) 描述 fork，Branch Affinity 用 git tree hash 做快照点。两者是不同概念，设计未解释 |

---

### 2.11 ReAct 引擎与 Actor Runtime 愿景（nop-ai-agent-react-engine.md，nop-ai-agent-actor-runtime-vision.md）

#### 真正渐进式的特性

| 特性 | 设计文档依据 | 渐进式实现方式 |
|------|-----------|-------------|
| **Dual-loop 隔离** | `react-engine.md:125-127` | 外循环 (followUp) 的 `idle→running` 转换是唯一状态边界——内循环 ReAct 迭代不改变 Actor 状态。可先实现内循环，后加 followUp |
| **sendMessage() 作为唯一外部 API** | `react-engine.md:34-59` | IAgentEngine 只有一个方法。其他全部事件驱动。最大接口极简主义 |
| **execute() 作为自贬 API** | `react-engine.md` | `execute()` 显式设计为 Phase 1 便利方法——等 AgentEventPublisher 就绪后变可选 |
| **Platform Layer as Additive Layer** | `actor-runtime-vision.md:11` | "Agent Engine Layer 不变"——整个新层加在现有层之上。最显著的渐进式断言 |
| **Virtual Thread per Actor = 零抽象并发** | `actor-runtime-vision.md:85` | 无线程池抽象、无调度器接口。纯数据优先极简主义 |
| **Resource quotas 声明式 XDSL 配置** | `actor-runtime-vision.md:235` | `@cfg:ai.agent.quota.*` + Delta 定制。每个配额维度独立可配 |
| **7 恢复场景 + 确定性策略** | `actor-runtime-vision.md:240-249` | 每个场景有具体检测方法和恢复动作。场景独立增加 |
| **5 阶段路线图 + 声明并行** | `actor-runtime-vision.md:378-387` | Phase 1 与 Phase 4 可并行——显式依赖 DAG |
| **4 个显式拒绝的替代方案** | `actor-runtime-vision.md:46-51` | 多进程/文件系统/中央编排器/心跳——拒绝理由记录。约束设计空间 |

#### 设计层面的渐进式问题

| 问题 | 位置 | 分析 |
|------|------|------|
| **Actor 状态转换无 guard conditions** | `actor-runtime-vision.md §3.2` | 7 状态 FSM 有转换但无显式 guard（如 `failed→recovering` 总能发生吗？DB 宕机时？） |
| **Team ACL 模型未定义** | `actor-runtime-vision.md §5.1` | 提到 "Team ACL" 但无 schema、无权限模型、无继承规则 |
| **Recovery 双循环错误处理未定义** | `react-engine.md §5-6` | 内循环错误是否导致 Actor 进入 `failed`？还是外循环 catch 并继续 followUp？ |

#### 审计就绪度

| 审计要求 | 设计就绪 | 设计依据 |
|---------|---------|---------|
| Actor 生命周期可追溯 | ✅ 7 状态 FSM + 状态转换表 | `actor-runtime-vision.md §3.2` |
| 恢复模式可审计 | ✅ 3 种恢复模式 (resume/retry/abort) | `actor-runtime-vision.md §6.3` |
| 配额执行可验证 | ✅ 7 维度配额 + 声明式配置 | `actor-runtime-vision.md §5.2` |
| 消息无丢失保证 | ❌ MessageRouter 与 Actor mailbox 间无背压 | `actor-runtime-vision.md §11` |

---

### 2.12 运行时语义与工具调用（nop-ai-agent-runtime-semantics.md，04-tool-invocation.md）

#### 真正渐进式的特性

| 特性 | 设计文档依据 | 渐进式实现方式 |
|------|-----------|-------------|
| **"Runtime 解释 DSL，永远不替代"原则** | `runtime-semantics.md:5-7` | 元渐进式模式：运行时层的职责是解释，非发明。DSL 保持单一真相源 |
| **call-agent 是工具非旁路** | `runtime-semantics.md:88-93` | "call-agent 是一类工具，不是旁路协议"——约束运行时不给 call-agent 特殊待遇。多 Agent 是加法，非结构性变更 |
| **XML DSL 主 + JSON Schema Phase 2 桥接** | `04-tool-invocation.md:44-52` | 显式拒绝"全切 JSON Schema"——保留 XML 为规范格式，JSON 为适配层。"规范格式+适配器"渐进式模式 |
| **IApprovalGate 作为独立拦截器** | `04-tool-invocation.md:26-34` | 审批在执行管线中是独立接口，不是工具执行器的一部分。可无审批运行，后加不改工具 |
| **after_acting 逐工具结果触发** | `runtime-semantics.md:118` | 精粒度 Hook（per-tool-result 而非 per-batch）——渐进式设计选择 |

#### 设计层面的渐进式问题

| 问题 | 位置 | 分析 |
|------|------|------|
| **运行时验证规则未指定** | `runtime-semantics.md §3` | 定义了 "runtime 回答字段如何影响执行" 但未指定字段非法/缺失时行为（如 maxIterations=0?） |
| **IApprovalGate 接口未定义方法签名** | `04-tool-invocation.md:26` | 引用了 IApprovalGate 但无方法签名。approve() 返回什么？能否修改工具调用？能否条件批准？ |
| **并行工具执行错误语义未定义** | `04-tool-invocation.md:38-40` | 3 个工具并行执行、1 个失败时：其他 2 个继续？部分结果记录？ |
| **Hook 事件匹配语法未定义** | `runtime-semantics.md §3.5` | "event matching" 但无事件模式字符串的语法定义 |

#### 审计就绪度

| 审计要求 | 设计就绪 | 设计依据 |
|---------|---------|---------|
| 工具执行经过审批可验证 | ✅ PRE_ACTING → IApprovalGate → Execute → POST_ACTING | `04-tool-invocation.md:26-34` |
| 并行执行意图可审计 | ✅ 声明式属性控制 | `04-tool-invocation.md:38-40` |
| Phase 边界可审计 | ✅ Phase 1=XML, Phase 2=+JSON adapter | `04-tool-invocation.md:44-52` |

---

### 2.13 Hook/Skill 引擎与 Session 引擎（nop-ai-agent-hook-skill-engine.md，nop-ai-agent-session-engine.md）

#### 真正渐进式的特性

| 特性 | 设计文档依据 | 渐进式实现方式 |
|------|-----------|-------------|
| **Hook + Skill 正交扩展轴** | `hook-skill-engine.md:39-42` | Skill 注入能力集合，Hook 拦截生命周期——两个独立扩展机制可组合。加 Skill 不需要 Hook，反之亦然 |
| **Internal Agentification via Hook mounting** | `hook-skill-engine.md:67-73` | 压缩→POST_REASONING，错误修复→ON_ERROR，审查→POST_CALL。能力通过 Hook 点挂载，从 0 内部 Agent 渐进替换 |
| **Session vs ExecutionContext 分离** | `session-engine.md:18-22` | Session 是持久化投影，Context 是内存工作副本。两者独立演变 |
| **Session 加载快照优先→降级 Event Log 重建** | `session-engine.md:32-35` | 快路径=快照，慢路径=重放。渐进式 fallback |

#### 设计层面的渐进式问题

| 问题 | 位置 | 分析 |
|------|------|------|
| **Hook 排序语义未定义** | `hook-skill-engine.md §2` | 提到 "排序" 但无排序规则（优先级？注册序？字母序？）多个 hook 注册于 before_acting 时谁先执行？ |
| **Hook 超时策略未定义** | `hook-skill-engine.md §2` | Hook 挂起时怎么办？ReAct 引擎的 toolTimeoutSeconds 不覆盖 Hook |
| **session-engine.md 极薄** | `session-engine.md` | 仅 52 行，无接口签名、无类关系、无错误处理。推荐对象仅列名称 |
| **CompressionCoordinator 与 reliability.md 管道关系未对齐** | `session-engine.md:44-48` vs `reliability.md §7` | 两处描述压缩但未声明哪个设计具有权威性 |

---

## 三、横向渐进式模式总结

### 3.1 已识别的渐进式模式

| 模式 | 出现位置 | 评价 |
|------|---------|------|
| **Thin Interface → Hardcoded Default → Agent Replacement** | `context-model.md:153-161` | 最佳实践。Phase 1 硬编码 → Phase 2 替换为 Agent 实现，同一接口 |
| **Additive Rule Stack (No Switch)** | `multi-agent.md:205-209`, `actor-runtime-vision.md §10`, `hook-skill-engine.md §6.3`, `04-tool-invocation.md §5` | 规则/组件/阶段逐步增加，每步新增接口，不改现有。出现在 4 个文档中 |
| **Zero-Interface Cache (数据优先于接口)** | `llm-layer.md:283-284`, `actor-runtime-vision.md:85` | 两个 int 字段代替 `ICacheManager`；Virtual Thread 代替线程池抽象。零抽象成本 |
| **Formatter as Pure Function Constraint** | `llm-layer.md:56` | 契约式（非类型级）渐进，不引入注解 |
| **IChatService 防腐边界** | `llm-layer.md:44` | Engine 接口不变，后端子接口独立演变 |
| **Token 估算三阶段渐进** | `llm-layer.md:85, 114` | 通用启发式 → Provider 可选覆盖 → Post-call 校准 |
| **Platform Layer as Additive Layer** | `actor-runtime-vision.md:11` | "Agent Engine Layer 不变"——整个新层加在现有层之上，零修改下层 |
| **Canonical Format + Adapter** | `04-tool-invocation.md:44-52` | XML 为规范格式，JSON Schema 为 Phase 2 适配层。规范格式永不替换 |
| **Hook + Skill 正交扩展轴** | `hook-skill-engine.md:39-42` | 两个独立扩展机制可组合，互不依赖 |
| **Self-deprecating API** | `react-engine.md` | `execute()` 设计为 Phase 1 便利方法，Phase 2 后变可选 |
| **DSL-Stable-Contract** | `runtime-semantics.md:5-7` | DSL 是不变量，运行时是变量。运行时解释 DSL，永远不替代 |
| **Config-Driven Quota** | `actor-runtime-vision.md:235` | 每个配额维度独立可配 via `@cfg:ai.agent.quota.*` + Delta |
| **IToolExecutor 统一抽象** | `shell-design.md:94-98` | 同一接口，不同实现，IoC 选择 |
| **3 Tier 语法 + 3 级命令优先级** | `shell-syntax-spec.md §三-六` | 覆盖率和实现优先级分层明确 |
| **Plan DSL Hard/Soft 分离** | `plan-dsl.md:28-68` | 确定性契约与自然语言严格分离 |

### 3.2 已识别的渐进式反模式

| 反模式 | 出现位置 | 问题 |
|--------|---------|------|
| **Phase 语言作为分支机制** | `multi-agent.md:32-33, 97` | "Phase 1 fail-fast, Phase 2+ 协调总线"——应用接口抽象，而非阶段分支 |
| **回退到"全部"而非"空"** | `skill-system-design.md:168-169` | 意图匹配失败→全体 Skill 激活，违反"最简行为" |
| **审计接口置于扩展层** | `security-and-permissions.md:374` | IAuditLogger 在 Layer 4，但审计是安全底线 |
| **消息类型定义过早** | `multi-agent.md §4.2` | 5 种协调消息类型在 Phase 1 之前已完整 schema 定义 |
| **字符串事件主题无 schema** | `channel-connector.md:169` | 运行时契约脆弱 |
| **无用 DSL 字段** | `skill-system-design.md:93` | `tags` 定义但匹配算法未使用 |

---

## 四、审计就绪度综合评估

### 4.1 各维度评分（纯设计层面）

| 维度 | 渐进式设计评分 | 评分依据 |
|------|--------------|---------|
| 核心架构 | 8/10 | 三对象分离、双循环、Hook 分层均为真正的渐进式。争议在 Steering 和 Layer 2 Hook 注册机制的设计展开程度 |
| 安全与权限 | 6/10 | 4 层分层 + 三源合并 + 纵深防御概念完整。IAuditLogger 位置错误（Layer 4→应为 Layer 1），hardcoded deny 不可 Delta，trustedSource 接口未定义 |
| 可靠性与压缩 | 6/10 | Layer 0/4 真正渐进，5 层管道概念清晰。Layer 1-3 无 NoOp 设计，Layer 1-2 有硬排序依赖，RECOVERABLE 分类未完备 |
| 多 Agent 与协作 | 6/10 | Agent-as-Subprocess + call-agent 工具化 + 子 Agent Compaction 隔离 (Additive Rule Stack) = 良好渐进。协调消息非持久化、无 IConflictStrategy、协调消息类型过早定义 |
| LLM 层 | 8/10 | IModelRouter PassThrough→Smart 最规范；前缀缓存零接口最佳实践；ChatMessage 三阶段渐进。唯一短板：IRetryPolicy 无 NoRetry 基 |
| DSL 体系 | 6/10 | DSL-First 约束严格；Plan DSL Hard/Soft 分离最佳实践。但 agent.xdef `tokenCompressionThreashold` 与设计文档/glossary 形成三方不一致；`paralllel` 拼写错误；无 provenance/version 字段 |
| 信道连接器 | 7/10 | 引擎零感知信道 + IChannelConnector 统一接口。字符串事件主题 + 无背压 + 无健康检查 |
| Skill 系统 | 5/10 | 3 阶段发布规划清晰。但"回退到全体 Skill" 严重违反最简原则；intentSignature 语义未定义；tags 冗余 |
| Shell 设计 | 8/10 | IToolExecutor 统一抽象 + 3 Tier 语法 + P0/P1/P2 优先级 = 最健壮。env 泄漏、变量展开失败无声为安全缺口 |
| 分支亲和调度 | 6/10 | 分层归属明确 (L2 Snapshot / L4 Worktree)。FileOperationLock、Fencing token、RecoveryManager、Merge Agent 均未定义 |
| ReAct 引擎 + Actor Runtime 愿景 | 8/10 | Dual-loop 隔离 + sendMessage 唯一 API + Platform Layer additive + Virtual Thread 零抽象 + 声明式配额。Team ACL 未定义；Recovery 双循环错误处理未定义 |
| 运行时语义 + 工具调用 | 7/10 | DSL-Stable-Contract 原则 + call-agent 是工具 + XML+JSON 规范格式+适配器 + IApprovalGate 独立拦截。运行时验证规则未指定；并行执行错误语义未定义 |
| Hook/Skill 引擎 + Session 引擎 | 6/10 | Hook+Skill 正交扩展 + Internal Agentification via Hook mounting + Session/Context 分离。Hook 排序/超时未定义；session-engine 极薄（52 行占位） |

**综合渐进式设计评分：6.8/10**（13 个维度加权平均，纯设计层面）

### 4.2 审计就绪度评分（纯设计层面）

| 审计维度 | 评分 | 核心障碍 |
|---------|------|---------|
| 安全决策可追溯 | 4/10 | IAuditLogger 在 Layer 4；trustedSource 无接口 |
| 压缩决策可追溯 | 5/10 | CompactionEntry 缺 snapshotId；Layer 0-1 无记录 |
| 多 Agent 决策可追溯 | 3/10 | 协调消息不持久化；无冲突/Fork 事件类型 |
| LLM 路由可追溯 | 4/10 | Fallback 链无审计事件；Talent 无声拒绝 |
| 执行步骤可追溯 | 6/10 | Event Log 设计良好，但缺 Hook/Steering 事件类型 |
| DSL 变更可追溯 | 2/10 | 无 provenance/version 字段；xdef 三方不一致 |
| Actor/Recovery 可追溯 | 5/10 | 7 状态 FSM + 3 种恢复模式可审计。但 MessageRouter 无背压 |

**综合审计就绪度评分：4.1/10**（7 个维度加权平均，纯设计层面）

### 4.3 核心结论

**当前设计文档在各层面是否真的能够达到审计要求？不能。** 主要设计层面的障碍：

1. **IAuditLogger 定位错误** — 位于 Layer 4 (平台安全层)，但审计是安全底线。Layer 1-3 的安全决策 (deny/allow/approve/reject) 在设计中无审计记录方式
2. **事件类型不完备** — Event Log 设计良好但缺少 `hook_execution`、`steering_injection`、`fork_event`、`conflict_detected`、`conflict_resolved`、`coordination_message` 等事件类型
3. **CompactionEntry 缺 snapshotId** — 无法链接压缩前后状态，审计员无法验证摘要准确性
4. **协调消息非持久化** — 多 Agent 的冲突解决决策在设计上就不可追溯重放
5. **DSL 无 provenance 机制** — 配置变更无法追溯来源

**但是：这些问题都是"设计即可修复"的。** 没有结构性缺陷——每个问题都有清晰的设计修复路径。

---

## 五、哪些特性是真正的渐进式设计？

### 5.1 确定性渐进式（设计完全符合约束 4）

- **AgentModel/Agent/AgentSession 三对象分离** — 配置、执行、状态完全解耦
- **IAgentExecutor 策略模式** — 执行策略接口化，设计预留多实现
- **Hook 生命周期分层** — 5+5 Hook 点，各层职责独立
- **IModelRouter: PassThrough → Smart** — 最规范的渐进式接口设计
- **前缀缓存零接口** — 两个 int 字段代替框架
- **Agent-as-Subprocess** — 进程隐喻清晰，call-agent 是工具
- **Internal Agentification: Thin Interface → Agent Replacement** — 薄接口模式
- **Additive Rule Stack** — 子 Agent Compaction 隔离规则逐步增强
- **Token 估算三阶段渐进** — chars/4 → 覆盖 → 校准
- **ITalent 动态准入** — 无 Talents=所有工具可用（隐式 NoOp）
- **Skill 3 阶段发布规划** — 渐进复杂度路径清晰
- **3 Tier 语法 + 3 级命令优先级 (Shell)** — 覆盖率和优先级分层
- **Snapshot L2 / Worktree L4 分层归属** — 明确分层
- **IToolExecutor 统一抽象** — 同一接口不同实现，不入侵引擎
- **Dual-loop 隔离** — 内循环不改变 Actor 状态，可先实现内循环后加 followUp
- **sendMessage() 唯一外部 API** — 最大接口极简主义
- **Platform Layer as Additive Layer** — 整个新层加在 Agent Engine 之上，零修改下层
- **Virtual Thread 零抽象并发** — 无线程池、无调度器，纯数据优先
- **Hook+Skill 正交扩展轴** — 两个独立扩展机制可组合
- **DSL-Stable-Contract 原则** — DSL 是不变量，运行时是变量
- **XML+JSON 规范格式+适配器** — 规范格式永不替换，适配层增量增加
- **IApprovalGate 独立拦截器** — 可无审批运行，后加不改工具
- **Resource quotas 声明式 XDSL** — 每个配额维度独立可配 via Delta

### 5.2 有条件渐进式（设计有概念但需修复）

- **安全 4 层接口** — 概念完整，但 IAuditLogger 在 Layer 4、hardcoded deny 不可 Delta、trustedSource 无接口
- **5 层压缩管道** — Layer 0/4 真正渐进，Layer 1-3 需要 NoOp 设计 + 独立性修复
- **协调总线** — 需要 IConflictStrategy 接口 + 消息类型推迟定义 + 协调消息持久化
- **DSL 体系** — DSL-First 理念正确，provenance/version/命名一致性需修复

### 5.3 之前遗漏的渐进式特性（本报告新增）

| 遗漏特性 | 发现来源 | 为何之前遗漏 |
|---------|---------|------------|
| **IModelRouter PassThrough→Smart** | `llm-layer.md:213` | 第一轮分析未覆盖 LLM 层 |
| **前缀缓存零接口模式** | `llm-layer.md:283-284` | 第一轮分析未覆盖 |
| **Token 估算三阶段渐进** | `llm-layer.md:85, 114` | 第一轮分析未覆盖 |
| **ITalent 动态准入** | `llm-layer.md:156-161` | 第一轮分析未覆盖 |
| **Internal Agentification 薄接口模式** | `context-model.md:153-161` | 第一轮分析不深入 |
| **Additive Rule Stack（子 Agent Compaction）** | `multi-agent.md:205-209` | 第一轮分析未覆盖多 Agent |
| **IToolExecutor 统一抽象** | `shell-design.md:94-98` | 第一轮分析未覆盖 Shell |
| **3 Tier 语法渐进覆盖** | `shell-syntax-spec.md §三-五` | 第一轮分析未覆盖 |
| **Skill 3 阶段分发计划** | `skill-system-design.md §8.1-8.3` | 第一轮分析未覆盖 |
| **Plan DSL Hard/Soft 分离** | `plan-dsl.md:28-68` | 第一轮分析仅草草提及 |
| **Snapshot L2 / Worktree L4 分层** | `branch-affinity.md:450-451` | 第一轮分析未覆盖 |
| **IChatService 防腐边界** | `llm-layer.md:44` | 第一轮分析未覆盖 |
| **Dual-loop 隔离（内循环不改变 Actor 状态）** | `react-engine.md:125-127` | 第一轮未分析 ReAct 引擎 |
| **sendMessage() 唯一外部 API + execute() 自贬 API** | `react-engine.md:34-59` | 第一轮未分析 |
| **Platform Layer as Additive Layer** | `actor-runtime-vision.md:11` | 第一轮未分析 Actor Runtime |
| **Virtual Thread 零抽象并发** | `actor-runtime-vision.md:85` | 第一轮未分析 |
| **Resource quotas 声明式 XDSL 配置** | `actor-runtime-vision.md:235` | 第一轮未分析 |
| **7 恢复场景 + 确定性策略** | `actor-runtime-vision.md:240-249` | 第一轮未分析 |
| **DSL-Stable-Contract 原则** | `runtime-semantics.md:5-7` | 第一轮未分析运行时语义 |
| **XML+JSON 规范格式+适配器模式** | `04-tool-invocation.md:44-52` | 第一轮未分析工具调用 |
| **IApprovalGate 独立拦截器** | `04-tool-invocation.md:26-34` | 第一轮未分析 |
| **Hook+Skill 正交扩展轴** | `hook-skill-engine.md:39-42` | 第一轮未分析 Hook/Skill 引擎 |
| **Internal Agentification via Hook mounting** | `hook-skill-engine.md:67-73` | 第一轮未分析 |
| **Session/Context 分离** | `session-engine.md:18-22` | 第一轮未分析 Session 引擎 |
| **4 个显式拒绝的替代方案** | `actor-runtime-vision.md:46-51` | 第一轮未分析 |

---

## 六、结论

### 渐进式设计的真相

**声称 vs 设计现实的对照**：

| 声称 | 设计现实 | 结论 |
|------|---------|------|
| "扩展通过添加接口实现" | **成立** — IModelRouter, IAgentExecutor, IChannelConnector, IToolExecutor, IApprovalGate, Hook+Skill 正交等均为接口加法 | ✅ |
| "不通过阶段切换" | **基本成立** — 违反案例：协调总线用 Phase 1/Phase 2 语言描述而非接口抽象；IAuditLogger 在 Layer 4。受控范围内 | ⚠️ 2 处违反 |
| "内部运行时最简化" | **部分成立** — Layer 0/4、前缀缓存、PassThroughRouter、Virtual Thread、sendMessage()唯一API 均遵循。违反：IRetryPolicy 默认 3 次重试而非 NoRetry；Skill 回退到全体而非空 | ⚠️ 2 处违反 |
| "通过 XDSL 配置逐步引入" | **部分成立** — Plan DSL Hard/Soft 分离、Resource quotas `@cfg:*` 是最佳实践。hardcoded deny 不可 Delta、agent.xdef `tokenCompressionThreashold` 与 glossary 禁用词 "compression" 矛盾 | ⚠️ 2 处违反 |

### 设计质量整体评价

**渐进式设计：6.8/10** — 约 70% 的设计元素真正遵循渐进式原则，约 30% 需要设计修复。没有结构性缺陷，所有问题都有清晰修复路径。

**审计就绪度：4.1/10** — 核心障碍在事件类型覆盖不全和审计接口定位错误。这需要设计层面的补充（新事件类型 + IAuditLogger 迁移），但无需框架重构。

两个评分间的差距 (6.8 - 4.1 = 2.7) 表明：系统在渐进式扩展性方面设计良好，但在可审计性方面考虑不足——审计是"渐进式增强"的一个被低估的设计维度。

---

## Open Questions

- [ ] IAuditLogger 放 Layer 1 还是 Layer 2？如果放 Layer 1，审计是核心安全还是策略扩展？
- [ ] Steering 是否应重构为 Hook？这取决于"最小运行时"是否包含消息注入——需要明确引擎层的"基本呼吸"定义
- [ ] Skill 回退到"全体"是设计选择还是有安全考虑（不想静默拒绝）？如果是后者，应在文档中说明理由
- [ ] Progressive 约束本身是否需要补充：对于安全相关组件，"XDSL 配置逐步引入"是否应允许某些降级约束（如 hardcoded deny 不可覆盖是合理的安全/审计决策）？
- [ ] DSL 的拼写错误 (paralllel, Threashold) 是否应在路线图中作为必须修复项？每个错误都需要 schema 版本协调

## References

- `ai-dev/design/nop-ai-agent/00-vision.md` — 设计原则（约束4）
- `ai-dev/design/nop-ai-agent/01-architecture-baseline.md` — 架构基线
- `ai-dev/design/nop-ai-agent/02-execution-model.md` — 执行模型
- `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` — 安全与权限
- `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` — 可靠性与压缩
- `ai-dev/design/nop-ai-agent/nop-ai-agent-context-model.md` — 上下文模型
- `ai-dev/design/nop-ai-agent/nop-ai-agent-multi-agent.md` — 多 Agent
- `ai-dev/design/nop-ai-agent/nop-ai-agent-llm-layer.md` — LLM 层
- `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md` — 会话与存储
- `ai-dev/design/nop-ai-agent/nop-ai-agent-session-engine.md` — 会话引擎
- `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md` — ReAct 引擎
- `ai-dev/design/nop-ai-agent/nop-ai-agent-hook-skill-engine.md` — Hook/Skill 引擎
- `ai-dev/design/nop-ai-agent/nop-ai-agent-dsl.md` — Agent DSL
- `ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md` — Plan DSL
- `ai-dev/design/nop-ai-agent/nop-ai-tool-dsl.md` — Tool DSL
- `ai-dev/design/nop-ai-agent/nop-ai-call-agent-dsl.md` — Call-Agent DSL
- `ai-dev/design/nop-ai-agent/nop-ai-agent-runtime-semantics.md` — 运行时语义
- `ai-dev/design/nop-ai-agent/nop-ai-agent-channel-connector.md` — 信道连接器
- `ai-dev/design/nop-ai-agent/skill-system-design.md` — Skill 系统
- `ai-dev/design/nop-ai-agent/nop-ai-shell-design.md` — Shell 设计
- `ai-dev/design/nop-ai-agent/nop-ai-shell-syntax-spec.md` — Shell 语法
- `ai-dev/design/nop-ai-agent/nop-ai-agent-branch-affinity-scheduling.md` — 分支亲和调度
- `ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md` — Actor Runtime 愿景
- `ai-dev/design/nop-ai-agent/04-tool-invocation.md` — 工具调用
- `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` — 路线图
- `ai-dev/design/nop-ai-agent/glossary.md` — 核心术语表
- `ai-dev/design/nop-ai-agent/README.md` — 设计文档导航
