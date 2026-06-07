# Nop AI Agent LLM 层设计

**日期**：2026-06-07
**范围**：Agent Engine Layer 与 LLM Layer 之间的接口层
**状态**：active
**架构层**：Layer 1 (Core) + Layer 2 (Execution) + Layer 3 (Reliability)

---

## 一、目标

本篇定义 Agent Engine 与 LLM Provider 之间的接口层，解决五个问题：

1. **消息格式**：Agent Engine 使用统一的 Provider 无关格式，不感知具体 Provider 的消息结构
2. **Provider 适配**：每种 Provider 有独立的 Formatter 将统一格式转为 Provider 特定格式
3. **动态准入**：基于上下文动态激活能力和工具集
4. **请求路由**：按复杂度和成本智能路由到合适的模型
5. **重试策略**：Provider 级别的精细化重试和错误分类

**Layer 归属**：§三（IMessageFormat）属于 Layer 1（Core Interfaces），定义在核心对象表中。§四～六、§八属于 Layer 2（Execution Extensions），所有接口有 pass-through 默认实现。§七（IRetryPolicy）属于 Layer 3（Reliability Extensions）。统一放在本篇是因为它们都位于 Agent Engine 和 LLM Provider 之间的接口层。§七放在本篇还因为它与 LLM Provider 紧密耦合——重试逻辑需要感知 429 子类型、Retry-After header、流式保护等 Provider 特定细节。

> **模式来源**：Pattern 1.8 (CanonicalMessage), 2.1 (Smart Router), 2.10 (Formatter), 2.11 (Talent), 3.9 (Provider Retry) — 来自 15+ 个 Agent 框架的源码级分析

---

## 二、设计定位

```
Agent Engine Layer
   │
   │ 调用 IMessageFormat, IModelRouter, ITalent
   │
   ▼
LLM Interface Layer  ←── 本篇焦点
   │
   │ 调用 IModelDialect, IRetryPolicy
   │
   ▼
LLM Layer (nop-ai-llm / nop-ai-llms)
```

本篇定义的接口位于 Agent Engine 和 LLM Provider 之间。Agent Engine 通过本层接口与 LLM 交互，不直接依赖具体 Provider。

---

## 三、消息格式（IMessageFormat）— Layer 1

### 3.1 问题

不同 LLM Provider 的消息格式差异大（OpenAI 的 `function_call` vs Anthropic 的 `tool_use` block）。Agent Engine 如果直接使用 Provider 特定格式，会与 Provider 耦合。

### 3.2 方案

Agent Engine 内部只使用 `CanonicalMessage`（Provider 无关的统一消息格式）。

**核心数据结构**：

```
CanonicalMessage
  role: "user" | "assistant"
  content: CanonicalContentBlock[]

CanonicalContentBlock (sealed interface):
  TextBlock         { type: "text", text }
  ThinkingBlock     { type: "thinking", text, signature? }
  ImageBlock        { type: "image", source, data, mimeType }
  ToolCallBlock     { type: "tool_call", id, name, input }
  ToolResultBlock   { type: "tool_result", toolCallId, content[] }
  ToolResultRef     { type: "tool_result_reference", path, preview }
```

**关键设计**：

1. **仅 2 个角色**（user / assistant）——工具交互是 content block 而非独立角色
2. **ToolResultReference 是代理模式**——预览内联，完整内容在磁盘/存储上按需加载。解决长工具输出导致上下文膨胀的问题
3. **ThinkingBlock** 支持 extended thinking（Claude, DeepSeek 等推理模型的思维链）
4. **sealed interface**（Java 21+）——pattern matching exhaustiveness check

### 3.3 接口契约

`IMessageFormat` 负责统一消息与 Provider 特定格式之间的双向转换，以及 token 估算。

**实现**：
- 默认实现直接使用 CanonicalMessage，无转换
- Provider 特定转换由 `IModelDialect` 负责（见 §四）

### 3.4 Nop 映射

- `CanonicalMessage` → sealed interface 层级，定义在 `nop-ai-core`
- `ToolResultReference` → `LazyContentBlock` 实现，磁盘引用 + preview
- `estimateTokens` → `chars / 4` 简单估算，Provider-reported usage 用于校准
- 与 Nop `IXMeta` schema 一致——消息格式的 XMeta 定义工具调用的 schema 约束

---

## 四、Provider 适配（IModelDialect / Formatter）

### 4.1 问题

AgentScope Java 的实践证明：每种 Provider 需要独立的 `Formatter` 将统一消息格式转为 Provider 特定格式。`Model` 接口仅暴露 `stream()` 和 `getModelName()`。

### 4.2 方案

`IModelDialect` 负责将统一消息格式转为 Provider 特定格式，以及将 Provider 响应解析回统一格式。每个 Provider 提供独立实现，通过 Nop SPI 自动发现。

**与 Nop `IDialect` 的映射**：高度一致——Nop 的数据库方言 SPI 自动发现模式可直接复用到 LLM Provider 方言。

### 4.3 已知 Provider 适配

| Provider | 特殊处理 | 参考来源 |
|----------|---------|---------|
| OpenAI | function_call / tool_choice 格式 | AgentScope |
| Anthropic | tool_use content block + thinking block | AgentScope |
| Gemini | function_declarations + Part 格式 | AgentScope |
| DashScope (阿里) | OpenAI 兼容 + Qwen 特有参数 | AgentScope |
| Ollama | OpenAI 兼容 + 本地模型参数 | AgentScope |

### 4.4 扩展方式

新 Provider = 新 `IModelDialect` 实现 + Nop SPI 自动发现。不需要修改 Agent Engine 代码。

---

## 五、动态准入（ITalent）

### 5.1 问题

不是所有能力和工具都应在每次请求中激活。例如：LSP 工具只在 IDE 场景有用，text2sql 只在有数据库时有用。静态工具列表会浪费 context window 并降低 LLM 推理质量。

### 5.2 方案

来自 Solon AI 的 Talent 模式——基于上下文动态决定是否激活行为和工具。

```
ITalent:
  boolean isSupported(AgentExecutionContext ctx)     // 动态准入判断
  void onAttach(AgentExecutionContext ctx)           // 激活时回调
  String getInstruction(AgentExecutionContext ctx)   // 动态注入的 system prompt
  Collection<ToolSpec> getTools(AgentExecutionContext ctx)  // 动态提供的工具集
```

**设计要点**：

1. `isSupported` 是准入门控——可以基于关键词匹配、上下文分析、环境检测等
2. 激活后才注入 instruction 和 tools——节省 context window
3. 与 Nop Delta 定制哲学高度一致——上下文依赖的行为激活

### 5.3 Nop 映射

- `ITalent` 接口 → Nop 扩展点 (`@XExtension`)
- `isSupported` → XPL 表达式判断（声明式）
- `getInstruction` → XPL 模板生成（动态 prompt）
- `getTools` → 从 `tool.xdef` 注册表按条件选择

### 5.4 预构建 Talent 示例

| Talent | 准入条件 | 提供的工具 | 来源 |
|--------|---------|-----------|------|
| file | 文件系统可用 | read, write, search, glob | Solon AI |
| web | 网络可用 | search, fetch | Solon AI |
| data | 数据库连接存在 | query, text2sql | Solon AI |
| cli | 终端可用 | exec, shell | Solon AI |
| lsp | LSP 服务器连接 | diagnostics, hover, complete | Solon AI |

---

## 六、请求路由（IModelRouter）

### 6.1 问题

所有请求都用最强模型成本很高。PilotDeck 的实测数据：Smart Routing 开启 $2.83 vs 关闭 $12.58（节省 ~77%）。

### 6.2 方案

`IModelRouter` 负责路由决策和回退判断。路由结果包含目标模型 ID、复杂度分级（simple/medium/complex）、按分级精简的工具集和调整后的 ChatOptions。回退判断根据错误类型决定是否切换到备选模型。

### 6.3 Smart Router 六步管线

来自 PilotDeck 的源码级设计：

```
Step 1: Scenario Detection → subagent / explicit / default
Step 2: Short Continuation → ≤30 chars 且匹配正则 → 继承前轮 tier
Step 3: Judge Classification → 便宜模型分类 simple/medium/complex
Step 4: Orchestration → 注入编排 prompt，精简工具集
Step 5: Execute with Fallback Chain → 主模型失败→备选
Step 6: Zero-usage Retry Detection → 检测空用量自动重试
```

### 6.4 默认实现

- `PassThroughModelRouter`（默认）——直连 AgentModel 配置的模型，无路由逻辑
- `SmartModelRouter`——可选的功能实现，使用便宜模型做 Judge 分类

### 6.5 Fallback 错误分类

| 错误类型 | 处理 |
|---------|------|
| `invalid_tool_arguments` | 可自纠正，重试 |
| `prompt_too_long`, `context_overflow` | 不可重试，终止 |
| 429 rate limit | 按 Retry-After 等待后重试 |
| 5xx, timeout | 切换 Fallback Chain |

---

## 七、重试策略（IRetryPolicy）— Layer 3

### 7.1 问题

nanobot 的 Provider 重试比简单的"重试 3 次"精细得多：需要区分 429 的具体原因（rate_limit_exceeded vs insufficient_quota），需要支持 persistent 模式（无限重试），需要在非瞬时错误时自动移除图片重试。

### 7.2 方案

`IRetryPolicy` 负责三个决策：是否重试（RetryDecision: RETRY / STOP / FALLBACK）、计算延迟时间、在重试前修改请求（如移除图片）。

重试上下文包含：尝试次数、上次错误、错误分类（TRANSIENT / NON_TRANSIENT / RATE_LIMITED / QUOTA_EXCEEDED）、是否已流出内容（用于流式保护）。

### 7.3 两种重试模式

**Standard 模式**（默认）：
- 最多 3 次重试，指数退避
- transient 错误重试，non-transient 不重试
- 429: `rate_limit_exceeded` 按 Retry-After 等待重试，`insufficient_quota` 不重试

**Persistent 模式**（可选）：
- 无限重试，相同错误 10 次后停止
- 适合无人值守长时间执行场景

### 7.4 流式保护

nanobot 的关键设计：**已流出内容时跳过 failover**，防止重复文本。`hasStreamedContent` 为 true 时，`shouldRetry` 返回 STOP 而不是 FALLBACK。

### 7.5 图片 Fallback

non-transient 错误时，自动移除请求中的图片重试。成功后永久记住该 Provider 不支持图片（下次直接不带图片）。这个优化来自 nanobot 的实测经验。

### 7.6 Retry-After 多源解析

```
解析优先级:
  1. HTTP header: retry-after-ms
  2. HTTP header: retry-after
  3. 响应体文本提取
  4. 默认退避: min(baseDelay * 2^attempt, maxDelay)
```

---

## 八、Cache-First 三区域架构（Layer 2）

> 本节描述 `IContextGovernor` 的缓存优化策略，与 Layer 1 的 `IMessageFormat` 协同工作。

### 8.1 问题

多数 Agent 每轮重排序/注入时间戳，导致 LLM 缓存命中率 <20%。DeepSeek 按 10% 未命中率计费，低命中意味着高成本。

### 8.2 方案

来自 Reasonix 的三区域上下文架构：

| 区域 | 内容 | 变更策略 | 缓存特性 |
|------|------|---------|---------|
| ImmutablePrefix | system prompt + tool specs + few-shots | 计算一次后不变 | SHA-256 指纹，缓存命中候选 |
| AppendOnlyLog | 对话消息 | 单调追加，不修改 | 滑动窗口 200 条，FIFO 淘汰 |
| VolatileScratch | reasoning, planState, notes | 每轮重置 | 永不上传到 LLM |

### 8.3 缓存指纹算法

```
fingerprint = sha256(JSON.stringify({system, tools, shots})).slice(0, 16)
```

缓存未命中推理链（priority chain）：cold-start → system-prompt-changed → tool-list-changed → schema-or-order-changed → unknown

### 8.4 实测效果

Reasonix: 435M input tokens, 99.82% cache hit, $12 vs 无缓存 $61（~80% 成本节省）。

### 8.5 Nop 映射

- `ImmutablePrefix` → Nop `IEvalScope` 不可变作用域，SHA-256 通过 `MessageDigest` 实现
- `AppendOnlyLog` → `ArrayDeque` 环形缓冲区 + 版本号
- `VolatileScratch` → 每请求 ThreadLocal / ScopableBean
- 三区域划分在 `IContextGovernor` 的实现中体现

---

## 九、拒绝了什么

### 9.1 拒绝：每种 Provider 独立定义完整的消息模型

**方案**：每种 Provider 有自己的 Message/ToolCall/ToolResult 类型，Agent Engine 直接使用 Provider 特定类型。

**拒绝理由**：Agent Engine 会与 Provider 强耦合，新增 Provider 需要修改 Engine 代码。CanonicalMessage 作为统一中间格式，Engine 只需处理一种消息模型。

### 9.2 拒绝：路由作为 Agent DSL 的字段

**方案**：在 `agent.xdef` 中定义 `<router>` 元素，让 DSL 直接控制路由策略。

**拒绝理由**：路由是 Layer 2 执行扩展，不是 Layer 1 核心概念。路由策略应该是运行时可插拔的（PassThrough vs Smart），不需要在 DSL 中声明。保持 DSL 精简，符合"先定义 DSL 语义，再定义 runtime 如何解释"的收敛原则。

### 9.3 拒绝：Guardrail 复用 Hook 机制

**方案**：用 Hook 的 PRE_REASONING/POST_REASONING 实现输入/输出内容检查。

**拒绝理由**：Hook 是生命周期回调，语义是"增强当前事件"。Guardrail 是内容验证，语义是"允许/修改/阻止"。两者的组合方式不同（Guardrail 是管道式，Hook 是回调式）。混合使用会导致职责不清。

---

## 十、与其他文档的关系

- `01-architecture-baseline.md` — Layer 1 核心对象（AgentModel, IAgentEngine 等）
- `02-execution-model.md` — 执行模型（ReAct 循环中调用本层接口）
- `nop-ai-agent-react-engine.md` — ReAct 引擎（使用 IMessageFormat 和 IModelRouter）
- `nop-ai-agent-reliability.md` — 可靠性增强（IRetryPolicy 与断路器协同）
- `nop-ai-agent-roadmap.md` — 分层架构（本篇覆盖 Layer 1 的 IMessageFormat + Layer 2 的 IModelDialect/ITalent/IModelRouter + Layer 3 的 IRetryPolicy）
