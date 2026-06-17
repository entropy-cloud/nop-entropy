# Nop AI Agent LLM 层设计

**日期**：2026-06-17（初始 2026-06-07，§八 扩展 + §3.3/§七 gap-fix 于 2026-06-17）
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

**Layer 归属**：§三（消息格式）属于 Layer 1（Core Interfaces），Agent Engine 直接使用已有的 ChatMessage 类型。§四～六、§八属于 Layer 2（Execution Extensions），所有接口有 pass-through 默认实现。§七（IRetryPolicy）属于 Layer 3（Reliability Extensions）。统一放在本篇是因为它们都位于 Agent Engine 和 LLM Provider 之间的接口层。§七放在本篇还因为它与 LLM Provider 紧密耦合——重试逻辑需要感知 429 子类型、Retry-After header、流式保护、409 缓存状态丢失等 Provider 特定细节。

> **模式来源**：2.1 (Smart Router), 2.10 (Formatter), 2.11 (Talent), 3.9 (Provider Retry) — 来自 15+ 个 Agent 框架的源码级分析
> **Token 估算调研**：`ai-dev/analysis/agent-survey/2026-06-10-token-estimation-and-context-compression-survey.md`
> **前缀缓存调研**：`ai-dev/analysis/agent-survey/2026-06-17-pie-ds4-cache-analysis.md`（pie/DS4 运行时机制）、`ai-dev/analysis/agent-survey/2026-06-10-reasonix-prefix-cache-immutability.md`（Reasonix 不变性保证）

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

1. **Anthropic thinking 回传**：Anthropic Extended Thinking 要求多轮对话时把上一轮的 thinking block（含 `thinkSignature`）原样回传，否则 API 报错。
2. **reasoning 回传必须区分两个层面**（DeepSeek 是最易踩坑的点，勿用一句话概括）：
   - **API 正确性**（托管 DeepSeek API）：`reasoning_content` 不应作为 messages 入参回传——API 入参不接受它。
   - **缓存正确性**（本地字节精确 KV 前缀缓存服务器，如 DS4）：reasoning **是 KV checkpoint 的一部分**，必须按服务器期望的 item 类型与顺序回放，否则渲染前缀从前一点起静默分歧、磁盘 checkpoint 失效。两个层面方向相反。
   - 因此 reasoning 回放策略必须由 **per-model compat flag**（如 `requiresReasoningContentOnAssistantMessages`）在 Dialect/Provider 适配层选择，而非全局硬规则。详见 §8.4。
3. **`providerHints` 的角色**：`ChatMessage.providerHints` 是 Provider 特定提示的透传通道（如 Anthropic 的 `cache_control`）。它是元数据，不影响消息文本内容的序列化。前缀缓存匹配只看 `ILlmDialect` 输出的字节序列。

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

来自 PilotDeck 的源码级设计。实现状态以 plan 209 为基准标注：

```
Step 1: Scenario Detection → subagent / explicit / default        [deferred — 独立 successor]
Step 2: Short Continuation → ≤30 chars 且匹配正则 → 继承前轮 tier  [deferred — 独立 successor]
Step 3: Judge Classification → 便宜模型分类 simple/medium/complex [deferred — Judge LLM 为 successor；
                                                                    首版使用启发式分类，见 §6.4]
Step 4: Orchestration → 注入编排 prompt，精简工具集                [deferred — 独立 successor]
Step 5: Execute with Fallback Chain → 主模型失败→备选             [✅ 已落地 — plan 209：ReAct 重试
                                                                    循环消费 RetryDecision.FALLBACK，
                                                                    向 IModelRouter 查询回退模型]
Step 6: Zero-usage Retry Detection → 检测空用量自动重试           [deferred — 独立 successor]
```

首版只落地 Step 5 的回退链消费与 §6.4 的启发式分类路由；Steps 1/2/3/4/6 均为显式 Non-Goal（独立 successor plan），不在当前 supported baseline 内。

### 6.4 默认实现

**实现状态（plan 209 ✅ 已落地 — SmartModelRouter 启发式分类 + 预算感知降级 + 回退链消费）**：

- `PassThroughModelRouter`（shipped 默认）——直连 AgentModel 配置的模型，无路由逻辑；opt-in 装配时才被 `SmartModelRouter` 替换。
- `SmartModelRouter`（opt-in 功能实现）——按请求复杂度路由到不同模型 tier：
  - **复杂度分类（启发式，首版）**：基于可观测信号——消息总长度、工具数量、是否包含代码（fenced code block）/ 结构化内容（`{` / `<`）——把请求分为 `simple`/`medium`/`complex` 三档，阈值可配置（有默认值）。**不调用额外 LLM 做分类**：Judge LLM 分类（Step 3）引入额外延迟与成本，是独立 successor。
  - **Tier 路由**：集成商通过构造器为每个 tier 配置主模型（provider+model）；router 选择分类 tier 的主模型，保留请求的 tools/settings（只覆盖 model 身份）。
  - **预算感知降级**：读取 `AgentExecutionContext.getBudgetSnapshot()`，当 `exceeded == true` 时降级到配置过的更便宜 tier；无更便宜 tier 时保持原 tier 并在 routingReason 标注。
  - **routingReason**：每次决策填充可读原因（如 `complexity=medium` / `complexity=complex; budget-exceeded->downgraded to medium`），驱动 plan 205 的 model-switched 审计消息。
  - **回退链（plan 209 Step 5）**：每个 tier 可配置有序回退链；ReAct 重试循环收到 `RetryDecision.FALLBACK` 时调用 `IModelRouter.getFallback(currentOptions)`，router 返回链中下一个模型（合并保留 tools）或 `null`（链耗尽）。回退后 attempt 计数器重置为 0（新模型的新调用周期），且 usage record 归属回退模型而非原始首选模型。

### 6.5 Fallback 错误分类

**实现状态（plan 209 ✅ — §6.5 表中"切换 Fallback Chain"已落地）**：当 `IRetryPolicy` 返回 `RetryDecision.FALLBACK` 时，ReAct 重试循环向 `IModelRouter.getFallback(...)` 查询回退模型；有回退模型则以新模型重试（attempt 重置、usage 归属新模型），无回退模型（含 `PassThroughModelRouter` 默认与回退链耗尽）则 fail-loud 抛 `NopAiAgentException`（Minimum Rules #24，无静默跳过）。

| 错误类型 | 处理 | 实现状态 |
|---------|------|---------|
| `invalid_tool_arguments` | 可自纠正，重试 | deferred（由 `IToolCallRepairer` ChainRepairer 处理，独立于本表） |
| `prompt_too_long`, `context_overflow` | 不可重试，终止 | deferred |
| 429 rate limit | 按 Retry-After 等待后重试 | deferred（当前用指数退避，见 §7） |
| 5xx, timeout | 切换 Fallback Chain | ✅ 已落地（plan 209） |

---

## 七、重试策略（IRetryPolicy）— Layer 3

**实现状态（plan 207 ✅ 已落地 — IRetryPolicy 契约 + NoRetryPolicy 默认 + StandardRetryPolicy）**：

- **契约 + NoRetry 默认 + 重试循环接线（Phase 1 ✅）**：`IRetryPolicy` 接口 + `RetryDecision`（RETRY/STOP/FALLBACK）枚举 + `RetryOutcome`（决策 + 延迟毫秒）+ `RetryContext`（attempt/lastError/errorClassification/hasStreamedContent）+ `ErrorClassification`（TRANSIENT/NON_TRANSIENT/RATE_LIMITED/QUOTA_EXCEEDED）+ `NoRetryPolicy`（恒 STOP）+ `LlmErrorClassifier`（按 HTTP 状态码映射）全部落地于 `io.nop.ai.agent.reliability` 包。`ReActAgentExecutor` 的单次 LLM 调用点（`chatService.call`）被重试循环包装：捕获异常 → `LlmErrorClassifier.classify` → 构造 `RetryContext` → `policy.shouldRetry` → RETRY(sleep backoff 后重试同一 request)/STOP(rethrow)/FALLBACK(plan 209：向 `IModelRouter.getFallback(...)` 查询回退模型，有则切换模型重试、无则 fail-loud 抛 `NopAiAgentException`，Minimum Rules #24)。`DefaultAgentEngine` 通过 field+setter+`resolveExecutor` 装配（默认 `NoRetryPolicy`，零行为回归）。
- **StandardRetryPolicy 功能实现（Phase 2 ✅）**：最大尝试次数（默认 3）+ 指数退避（baseDelay * 2^attempt，封顶 maxDelay）+ 仅 TRANSIENT/RATE_LIMITED 重试、NON_TRANSIENT/QUOTA_EXCEEDED 立即 STOP。429 RATE_LIMITED 使用指数退避（当前调用路径异常不含 Retry-After header，见 Non-Goals）。

### 7.1 问题

nanobot 的 Provider 重试比简单的"重试 3 次"精细得多：需要区分 429 的具体原因（rate_limit_exceeded vs insufficient_quota），需要支持 persistent 模式（无限重试），需要在非瞬时错误时自动移除图片重试。

### 7.2 方案

`IRetryPolicy` 负责三个决策：是否重试（RetryDecision: RETRY / STOP / FALLBACK）、计算延迟时间、在重试前修改请求（如移除图片）。

重试上下文包含：尝试次数、上次错误、错误分类（TRANSIENT / NON_TRANSIENT / RATE_LIMITED / QUOTA_EXCEEDED / CACHE_STATE_LOST）、是否已流出内容（用于流式保护）。

`CACHE_STATE_LOST` 对应本地推理服务器（如 DS4 / DeepSeek V4 本地服务器）返回的 **HTTP 409 Conflict**：服务器丢失了该 session 的 live continuation state（被驱逐或重启），语义是"回放完整历史，我从磁盘 KV checkpoint 重建"。归类为 TRANSIENT，重试策略 = **原样重发同一请求**（因为它本身就是回放）。正确性前提：客户端总是发送完整历史——nop-ai-agent 的 ReAct 引擎在 `buildBaseExecutionContext` 中全量 replay 历史重建上下文，满足此前提，故 409-retry-as-replay 直接可用。

### 7.3 三种重试模式

**NoRetry 模式**（Layer 1 默认）：
- 从不重试，fail-fast
- 适合确定性强、延迟敏感的调用（如幂等只读查询）
- 符合 00-vision.md 约束 4 的"不引入任何外部假定"

**Standard 模式**（Layer 3 默认，通过 XDSL 配置启用）：
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

> **模式来源**：`ai-dev/analysis/2026-06-10-nop-prefix-cache-design-analysis.md`（原则层 ⟶ §8.1–8.3）+ pie DS4/DeepSeek V4 本地 KV 前缀缓存三项运行时机制（⟶ §8.4–8.8）+ Reasonix 不变性保证。详见 `ai-dev/analysis/agent-survey/2026-06-17-pie-ds4-cache-analysis.md` 与 `ai-dev/analysis/agent-survey/2026-06-10-reasonix-prefix-cache-immutability.md`。

前缀缓存设计分两层：**原则层约束**（8.1–8.3，保证序列化确定性）和**运行时机制**（8.4–8.6，保证缓存实际命中）。原则层是命中的**必要前提**，但仅靠纯函数序列化不够——还必须在运行时正确回放 reasoning、正确恢复缓存丢失、正确记账缓存流量，才能让本地字节精确缓存服务器（如 DS4）真正复用 KV checkpoint。

### 8.1 核心原则

前缀缓存只看一件事：**应用每次发送给模型的字节前缀是否一样**。模型返回什么、怎么解析、怎么存储，都不影响缓存。应用层全权控制发送内容。

前缀不变性是引擎层约束，不是 API 层数据属性——不在 `ChatMessage` 或 `ChatRequest` 上加 frozen/fixed 标记。

### 8.2 序列化确定性

前缀缓存命中的前提：`ILlmDialect.convertMessage()` 是纯函数（见 §3.2）。如果同一消息在不同位置序列化为不同字节，缓存必然失效。`convertMessage()` 不接受 `isLast` 之类的位置参数。

### 8.3 不引入新数据结构

不引入 `PrefixState`、`PrefixFingerprint`、`IContextGovernor` 等新类或接口。引擎层在 `AgentExecutionContext` 中用 `prefixLength` + `prefixHash` 两个字段管理即可——会话初始化时记录，每 turn 自律不修改前缀区。

### 8.4 reasoning 内容回放策略（运行时机制一）

**问题**：reasoning 模型（DeepSeek V4 等）的每个 assistant turn 以 thinking 开头。做字节精确 KV 前缀缓存的本地服务器会把 thinking 渲染进采样的 token 流，因此**thinking 是 KV checkpoint 的一部分**。标准客户端回放历史时往往**丢弃** thinking → 客户端发回的渲染前缀永远不匹配服务器采样的 → 一旦 live continuation state 被驱逐，磁盘 KV checkpoint 立即变 stale，每轮被迫重新 prefill 整个对话。

> 这是 §3.3 易错点 2 的根源：reasoning 回传必须区分 API 正确性（托管 API 不接受 reasoning_content 入参）与缓存正确性（本地 KV checkpoint 含 reasoning），两者方向相反。

**方案**：reasoning 回放策略由 **per-model compat flag**（如 `requiresReasoningContentOnAssistantMessages`）在 `ILlmDialect` / Provider 适配层选择，而非全局硬规则。flag 为 true 时，Dialect 把每个 assistant turn 的 thinking 按服务器期望的 item 类型回放。

**关键约束：ordering is load-bearing**。消费 reasoning item 的服务器（如 DS4）把 reasoning item **合并进它后面的 message**，故 reasoning item 必须在该 assistant message **之前**发射。任何 reasoning 回放实现都需文档化此 ordering 约定，并有单元测试固化（如断言 reasoning item 的 index 严格小于其归属 assistant message 的 index）。

**门控**：仅在 compat flag 为 true 且 thinking 非空时激活；其余场景（托管 API、非 reasoning 模型）丢弃 thinking 不受影响。nop-ai-agent 的 `ILlmDialect.convertMessage()` 纯函数契约（§3.2）是承载此逻辑的干净位置——flag 通过 Dialect 构造或 `{provider}.llm.xml` 配置注入，`convertMessage()` 输出不带位置参数。

### 8.5 缓存状态丢失与恢复（运行时机制二）

**问题**：本地推理服务器丢失某 session 的 live continuation state（被驱逐或重启）时返回 **HTTP 409 Conflict**，语义是"回放完整历史，我从磁盘 KV checkpoint 重建"。

**方案**：把 409 归类为 `CACHE_STATE_LOST`（TRANSIENT 子类），重试策略 = **原样重发同一请求**——因为客户端总是发送完整历史，重发本身就是服务器要求的回放。详见 §7.2。

**正确性前提**：客户端总是发送完整历史。nop-ai-agent 的 ReAct 引擎在 `buildBaseExecutionContext` 中全量 replay 历史重建上下文（restore/compaction 后均走全量重建），满足此前提。若未来引入"只发增量、依赖服务端 continuation state"的模式，409-retry-as-replay 不能直接复用，必须先补全历史。

**磁盘 KV checkpoint 的角色**（服务器侧特性，客户端只需配合）：DS4 类服务器把 KV checkpoint 持久化到磁盘（`--kv-disk-dir`），命中可跨驱逐/重启存活。客户端的正确回放（8.4）+ 正确恢复（409 重试）让磁盘 checkpoint 始终有效，而非每次重启从零 prefill。

### 8.6 缓存流量记账与可观测性（运行时机制三）

**问题**：仅读 cache read 看不到缓存账本的写入侧。`cache_write`（本次请求新写入 prompt cache 的 token 数）不只是成本数据，更是**缓存健康度的运行时诊断信号**：健康 session 上每轮应看到大 `cache read` + 小 `cache write`；`cache read` 中途坍塌为零 = 前缀已分歧（8.4/8.5 失效的告警信号）。

**方案**：`Usage` / 计费 schema 必须双侧记账（`cache_read` + `cache_write` 独立字段）。非标准 usage 字段（如 DS4 的 `input_tokens_details.cache_write_tokens`）由 Provider 适配层解析后折进统一字段，仅当服务器发送时才读。

> **与用量计费的关系**：`nop-ai-agent-usage-and-billing.md` §2.1 已记录"`tokens_cache_write` schema 列存在但 runtime 从不写入"。本节为该实现缺口补充**缓存动机论证**：双侧记账既是计费需求，也是前缀缓存命中率的运行时验证手段。两条需求同源，应一并实现。详见 `nop-ai-agent-usage-and-billing.md` §三。

### 8.7 实测效果

Reasonix 验证（原则层 + 不变性保证）：435M input tokens, 99.82% cache hit, ~80% 成本节省。pie 验证（运行时机制三项齐备后）：本地 DS4 长 session 从"每轮重 prefill 整个对话"降到"每轮只 prefill 几百新 token"，且 cache read 坍塌可经 `/cost` 的 cache_write 比例即时发现。

### 8.8 三项机制的门控与可移植性

三项运行时机制全部门控、零硬编码到特定服务器：

- reasoning 回放仅在 per-model compat flag 为 true 时激活；
- `cache_write_tokens` 仅当服务器发送时才读；
- 409-retry 对任何"客户端总是发完整历史"的 API 都正确。

 任何做字节精确前缀缓存且消费 reasoning input item 的本地服务器，设同样 compat flag 即可获得相同行为。这与 §3.2 的纯函数契约、§8.3 的"不加数据结构"共同保证：前缀缓存优化是 Provider 适配层的可插拔能力，不污染 Layer 1 核心类型。

### 8.9 开发实施注意事项（用于后续根据 roadmap 拟 plan）

以下三条遵循 roadmap 已落地项，不需要新增 Layer/接口，但实施时有边际条件需注意。

#### 8.9.1 reasoning 回放 compat flag（归属 L2-8 `ILlmDialect` / `{provider}.llm.xml`）

| 要点 | 说明 |
|------|------|
| **ordering is load-bearing** | DS4 把 reasoning item 合并进**后面**的 assistant message，故 reasoning 必须在前发射。单元测试必须断言 `reasoning_idx < assistant_idx` |
| **不在 ChatMessage 上加 flag** | compat flag 在 Dialect/Provider 配置层（`{provider}.llm.xml` 新增列或 Dialect 构造参数），不在 `ChatMessage` 上加。违反 §8.3 原则 |
| **不改 `convertMessage()` 签名** | `convertMessage()` 是纯函数（§3.2/§8.2），reasoning 回放逻辑应在 `buildBody()` 或独立的 transform 步骤实现，不作为参数传入 |
| **双分离：API 正确性 ≠ 缓存正确性** | flag 控制缓存正确性（本地 KV 回放），非 API 正确性（托管 API 拒绝 reasoning 入参）。同一模型在不同部署模式（托管 vs 本地）可能需要不同的 flag 值 |
| **flag 命名须自解释** | 如 `requiresReasoningContentOnAssistantMessages`（pie 用名）或 `replayThinkingAsInputItems`。行为反直觉（通常不应回放），命名必须让读者明确其用途 |
| **Delta 可配** | flag 值应可通过 `{provider}.llm.xml` 的 Delta 机制定制，非 Java 硬编码 |

#### 8.9.2 409 / CACHE_STATE_LOST（归属 L3-2 `IRetryPolicy`，✅ plan 207）

| 要点 | 说明 |
|------|------|
| **前提：客户端总发完整历史** | 409-retry-as-replay 只有客户端总是发送完整历史时才正确。nop-ai-agent 当前 `buildBaseExecutionContext` 全量 replay 满足此条件。若未来引入"只发增量"模式，必须先补全历史再重试 |
| **须门控** | 不是所有 Provider 都用 409 表示缓存丢失。应在 Provider 配置层（`{provider}.llm.xml` 或 `IRetryPolicy` 工厂）声明是否启用。如 `supportsCacheStateRecovery: true` |
| **流式兼容** | 若已流出内容再 409，重试会重复输出。需确认是否接受"看到的回复重复一次"（pie 的做法：原样重发，输出重复），或实现去重（如客户端检查已收到部分内容后不显示后续重复）。**建议：Standard 模式下 maxRetries=3 且无去重**——409 恢复场景概率低，输出重复是书斋问题 |
| **非托管 API 专用** | 托管 API（Anthropic/OpenAI）的 409 含义不同（真·冲突），CACHE_STATE_LOST 仅用于本地推理服务器 |
| **退避策略** | 409 应使用指数退避（同 429），因为服务器从磁盘加载 KV checkpoint 需要时间。但不需要无限重试——首次重试大概率恢复 |
| **日志语义** | 409 恢复成功后应记录为 `cache_recovery` 事件（非 error），供运维监控缓存丢失频率。高频 409 可能指示 KV 磁盘空间不足 |

#### 8.9.3 cache_write runtime 写入（归属 L2-18 `DbUsageRecorder`，✅ plan 202）

| 要点 | 说明 |
|------|------|
| **非标准字段** | `cache_write_tokens`（DeepSeek 的 `input_tokens_details.cache_write_tokens`）是 OpenAI 非标准。必须 per-Provider 选择性读取，仅当服务器发送时才写入 |
| **total_tokens 计算** | `total_tokens` 是否包含 cache_read + cache_write？pie 的做法是包含（`input + output + cache_read + cache_write`）。但 OpenAI 标准 total 不含。nop-ai-agent 需决定是否保持 OpenAI 兼容（total=in+out）或在独立字段展示 cache 侧 |
| **不阻塞主循环** | 纯可观测性 / 计费路径。必须遵循 `nop-ai-agent-usage-and-billing.md` §2.3 约束——异步或快速同步，不阻塞 ReAct dispatch。当前 `DbUsageRecorder` 已是同步 write-through，量级可控 |
| **验证工具** | cache_write >> cache_read → 前缀疑似分歧（§8.6）。该比值应暴露为日志 / metric，非 crash。可加入 `ReActAgentExecutor` token 累积点旁的缓存健康告警逻辑（仅有观测，不断言） |
| **Schema/DML 无变更** | `tokens_cache_write` 列已存在（`NopAiSession`），`cache_write_price_per_1m` 列已存在（`NopAiModel`）。实施纯属 runtime 记录路径，无 DDL 变更 |
| **与定价联动** | L2-19（plan 204）已加上 `cache_write_price_per_1m`。一旦 runtime 记录 `tokens_cache_write`，`estimatedCost` 计算自动纳入，无需额外 schema 工作 |

**与 roadmap 项的关系**：三项均不新增 roadmap 项，但建议在各自归属项的验收标准（acceptance criteria）中补充"缓存场景验证"子项——特别是 cache_write 记录，应作为 L2-18 的已知缺口补齐。另建议在 roadmap Layer 2 验收标准的后续 successor 区记录 cache_write runtime 写入尚未实现。

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
- `nop-ai-agent-usage-and-billing.md` — 用量追踪与计费（§8.6 缓存流量双侧记账的计费侧实现 + runtime 写入缺口）
- `nop-ai-agent-roadmap.md` — 分层架构（本篇覆盖 Layer 1 的 ChatMessage + Layer 2 的 ILlmDialect/ITalent/IModelRouter + Layer 3 的 IRetryPolicy）
