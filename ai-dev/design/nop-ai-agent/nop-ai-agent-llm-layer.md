# Nop AI Agent LLM 层设计

**日期**：2026-06-07
**范围**：Agent Engine Layer 与 LLM Layer 之间的接口层
**状态**：active
**架构层**：Layer 1 (Core) + Layer 2 (Execution) + Layer 3 (Reliability)

---

## 一、目标

本篇定义 Agent Engine 与 LLM Provider 之间的接口层，解决四个问题：

1. **消息格式**：Agent Engine 直接使用 `nop-ai-api` 的 `ChatMessage` 体系，Provider 差异由 `nop-ai-core` 的 `ILlmDialect` 在 ChatServiceImpl 内部屏蔽
2. **动态准入**：基于上下文动态激活能力和工具集
3. **请求路由**：按复杂度和成本智能路由到合适的模型
4. **重试策略**：Provider 级别的精细化重试和错误分类

**Layer 归属**：§三（消息格式）属于 Layer 1（Core Interfaces），Agent Engine 直接使用已有的 ChatMessage 类型。§四～六、§八属于 Layer 2（Execution Extensions），所有接口有 pass-through 默认实现。§七（IRetryPolicy）属于 Layer 3（Reliability Extensions）。统一放在本篇是因为它们都位于 Agent Engine 和 LLM Provider 之间的接口层。§七放在本篇还因为它与 LLM Provider 紧密耦合——重试逻辑需要感知 429 子类型、Retry-After header、流式保护等 Provider 特定细节。

> **模式来源**：2.1 (Smart Router), 2.10 (Formatter), 2.11 (Talent), 3.9 (Provider Retry) — 来自 15+ 个 Agent 框架的源码级分析
> **Token 估算调研**：`ai-dev/analysis/agent-survey/2026-06-10-token-estimation-and-context-compression-survey.md`

---

## 二、设计定位

```
Agent Engine Layer
   │
   │ 使用 ChatMessage (来自 nop-ai-api)
   │ 调用 IChatService, IModelRouter, ITalent
   │
   ▼
LLM Interface Layer  ←── 本篇焦点
   │
   │ IChatService 实现内部使用 ILlmDialect
   │ (ChatServiceImpl 已屏蔽 Provider 差异)
   │
   ▼
nop-ai-core (ChatServiceImpl + llm.xdef + {provider}.llm.xml)
```

Agent Engine 通过 `IChatService` 接口与 LLM 交互，消息格式统一使用 `nop-ai-api` 的 `ChatMessage` 体系。Provider 差异由 `nop-ai-core` 的 `ChatServiceImpl` + `ILlmDialect` 在内部屏蔽，对 Agent Engine 完全透明。

---

## 三、消息格式 — Layer 1

### 3.1 决策

Agent Engine 直接使用 `nop-ai-api` 的 `ChatMessage` 体系，不引入额外的统一消息层（如 CanonicalMessage）。Provider 差异由 `nop-ai-core` 的 `ILlmDialect` 在 `ChatServiceImpl` 内部屏蔽，Agent Engine 零感知。

### 3.2 ILlmDialect 序列化契约

`convertMessage(message, modelConfig, options)` 是纯函数：相同输入永远产生相同序列化字节。这保证前缀缓存的正确性——前缀区的消息无论在哪一轮 turn，序列化结果不变。

thinking prompt 注入由各 Dialect 的 `buildBody()` 在最后一条消息上后置处理，不进入 `convertMessage()` 签名。

### 3.3 易错点

1. **Anthropic thinking 回传**：Anthropic Extended Thinking 要求多轮对话时把上一轮的 thinking block（含 `thinkSignature`）原样回传，否则 API 报错。DeepSeek 的 `reasoning_content` 则不应回传。两个 Provider 的行为相反，容易搞混。
2. **`providerHints` 的角色**：`ChatMessage.providerHints` 是 Provider 特定提示的透传通道（如 Anthropic 的 `cache_control`）。它是元数据，不影响消息文本内容的序列化。前缀缓存匹配只看 `ILlmDialect` 输出的字节序列。

### 3.4 拒绝了什么

**CanonicalMessage**（2 角色 6 ContentBlock 的 sealed interface）：ChatMessage 体系已覆盖所有场景，额外转换层增加认知复杂度但收益为零。

---

## 四、Provider 适配（ILlmDialect / Formatter）

### 4.0 Token 估算服务

#### 问题

上下文窗口保护需要在两个时刻估算 token 数量：
- **Pre-call**：发送请求前判断是否触发 compaction——需要估算，没有精确值
- **Post-call**：LLM 响应后获取精确值——Provider API 直接返回 `usage.prompt_tokens`

不同 Provider 使用不同 tokenizer（OpenAI o200k_base, DeepSeek V4 BPE, Claude 独立 tokenizer），没有通用精确方案。但 Pre-call 估算不需要精确——compaction 阈值（如 80%）有足够容差，chars/4 的 ±10% 偏差不会导致错误决策。

#### 方案

在 `ILlmDialect` 上提供 token 估算的 default 方法。**缺省实现使用 chars/4 启发式（零依赖）**。具体 Dialect 可按需覆盖为更精确的实现（如 jtokkit 或 Provider 专属 tokenizer）。

```
ILlmDialect:
  // Token 估算 — 缺省 chars/4，具体 Dialect 可覆盖
  int estimateTokens(String text)                          // default: (text.length() + 3) / 4
  int estimateTokens(ChatMessage message)                  // default: 累加各部分
  int estimateRequestTokens(List<ChatMessage> messages,    // default: 累加 + 开销
                             List<ChatToolDefinition> tools,
                             ChatOptions options)
```

#### 为什么估算放在 ILlmDialect 上而不是独立接口

token 估算本质上是 Provider 特定的——OpenAI 用 o200k_base，DeepSeek 用自己的 V4 BPE，Claude 也有独立 tokenizer。把估算能力放在 Dialect 上语义最自然：

1. Dialect 已经知道消息结构（tool_calls 的参数需要额外 +10 开销，thinking 需要单独计数等）
2. Provider 的 tokenizer 与 Provider 的序列化格式天然绑定
3. 引擎层通过 `IChatService` 获取当前 Dialect，调用 `estimateTokens()` 即可，不感知具体实现

#### 为什么缺省用 chars/4 而不是 jtokkit

- **Provider API 返回的 token 数是 source of truth**——自行 BPE 计数对各模型 tokenizer 不同，都不精确，且增加依赖
- **chars/4 对触发阈值够用**——compaction 触发在 80%，chars/4 的 ±10% 偏差（72% vs 88%）不影响决策正确性，Post-call 精确值会修正
- **Post-call 校准**：Provider 返回 `usage.prompt_tokens` 后，记录 `reportedTokens / estimatedTokens` 比值，用于修正后续 Pre-call 估算
- **Dialect 可选升级**：如果未来某 Provider 的 Dialect 需要更精确的 Pre-call 估算（如 DeepSeek 1M 窗口场景），可在该 Dialect 中引入 jtokkit 或 Provider 专属 tokenizer，不影响其他 Dialect

#### 校准机制

Post-call 使用 Provider API 返回的 `usage.prompt_tokens` 作为精确值，用于：
1. 校准本地估算偏差（记录 ratio，修正后续估算）
2. 作为 compaction 触发的精确判据（参见 `nop-ai-agent-reliability.md` §7）
3. 更新 `AgentSession` 的 token 累计计数

### 4.1 问题

不同 LLM Provider 消息格式差异大，需要独立的 Formatter 将 `ChatMessage` 体系转为 Provider 特定格式。

### 4.2 方案

Provider 适配已在 `nop-ai-core` 的 `ChatServiceImpl` + `ILlmDialect` 中实现。Agent Engine 不直接处理 Provider 差异，全部通过 `IChatService` 接口屏蔽。

**与 Nop `IDialect` 的映射**：高度一致——Nop 的数据库方言 SPI 自动发现模式可直接复用到 LLM Provider 方言。

### 4.3 已知 Provider 适配

| Provider | 特殊处理 | 实现位置 |
|----------|---------|---------|
| OpenAI | function_call / tool_choice 格式 | nop-ai-core ILlmDialect |
| Anthropic | tool_use content block + thinking block | nop-ai-core ILlmDialect |
| Gemini | function_declarations + Part 格式 | nop-ai-core ILlmDialect |
| DashScope (阿里) | OpenAI 兼容 + Qwen 特有参数 | nop-ai-core ILlmDialect |
| Ollama | OpenAI 兼容 + 本地模型参数 | nop-ai-core ILlmDialect |

### 4.4 扩展方式

新 Provider = 新 `ILlmDialect` 实现 + Nop SPI 自动发现。不需要修改 Agent Engine 代码。

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

## 八、前缀缓存设计（Layer 2）

### 8.1 核心原则

前缀缓存只看一件事：**应用每次发送给模型的字节前缀是否一样**。模型返回什么、怎么解析、怎么存储，都不影响缓存。应用层全权控制发送内容。

前缀不变性是引擎层约束，不是 API 层数据属性——不在 `ChatMessage` 或 `ChatRequest` 上加 frozen/fixed 标记。

### 8.2 序列化确定性

前缀缓存命中的前提：`ILlmDialect.convertMessage()` 是纯函数（见 §3.2）。如果同一消息在不同位置序列化为不同字节，缓存必然失效。`convertMessage()` 不接受 `isLast` 之类的位置参数。

### 8.3 不引入新数据结构

不引入 `PrefixState`、`PrefixFingerprint`、`IContextGovernor` 等新类或接口。引擎层在 `AgentExecutionContext` 中用 `prefixLength` + `prefixHash` 两个字段管理即可——会话初始化时记录，每 turn 自律不修改前缀区。

### 8.4 实测效果

Reasonix 验证：435M input tokens, 99.82% cache hit, ~80% 成本节省。

---

## 九、拒绝了什么

### 9.1 拒绝：每种 Provider 独立定义完整的消息模型

**方案**：每种 Provider 有自己的 Message/ToolCall/ToolResult 类型，Agent Engine 直接使用 Provider 特定类型。

**拒绝理由**：Agent Engine 会与 Provider 强耦合，新增 Provider 需要修改 Engine 代码。当前 `ChatMessage` + `IChatService` 的组合已足够——Engine 使用统一格式，`ChatServiceImpl` + `ILlmDialect` 处理转换。

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
- `nop-ai-agent-react-engine.md` — ReAct 引擎（使用 ChatMessage 和 IModelRouter）
- `nop-ai-agent-reliability.md` — 可靠性增强（IRetryPolicy 与断路器协同）
- `nop-ai-agent-roadmap.md` — 分层架构（本篇覆盖 Layer 1 的 ChatMessage + Layer 2 的 ILlmDialect/ITalent/IModelRouter + Layer 3 的 IRetryPolicy）
