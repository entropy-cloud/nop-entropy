# OpenAI Responses API 与 Chat Completions 格式对比及第三方厂商自动转换方案分析

> Status: superseded
> Date: 2026-08-01
> Scope: nop-ai LLM 多厂商接入层（dialect 模式）、LLM 网关协议转换
> Conclusion: 已被 `ai-dev/design/nop-ai-responses-migration-design.md` 取代——该调研的"格式差异/生态/转换路线"事实部分仍有效；"内部折叠模型保持不动、新增 ResponsesDialect"的决策被推翻，新决策为内部模型重构为 typed items canonical 核心，折叠消息降级为兼容投影。
> Superseded By: `ai-dev/design/nop-ai-responses-migration-design.md`

## Context

- OpenAI 已将 Responses API（`/v1/responses`）定为新项目推荐协议，Codex CLI 等新客户端**强制**使用该协议；Chat Completions 仍支持但功能停止增长（官方精确表述：自 GPT-5.4 起，Chat Completions 不再支持 "tool calling + `reasoning:none`" 组合，推理模型的新特性仅在 Responses 上完善）。
- 国内厂商（智谱 GLM、DeepSeek、Kimi、MiniMax、SiliconFlow）普遍只提供 Chat Completions 兼容格式，导致协议两侧对不上：客户端说 Responses，上游只懂 Chat Completions。
- 本仓库 `nop-ai` 已用 **dialect 模式**（`OpenAiDialect` / `OllamaDialect` / `AnthropicDialect` / `GeminiDialect`）做多厂商 wire 格式适配，内部统一 `ChatRequest` / `ChatResponse` 模型。需要评估：Responses 与 Chat Completions 的本质差异、生态现状、以及自动转换的可行路径。

## Analysis

### 1. 两种格式的本质差异：折叠模型 vs 拆分模型

Responses API 不是 Chat Completions 的字段改名，而是**数据模型层面的重构**。先明确一个核心概念差异——**折叠模型 vs 拆分模型**：

- **折叠模型**（Chat Completions、nop-ai 内部模型）：多个语义单元粘在一个对象里，靠 role 枚举区分。assistant message 一个对象同时承载正文（content）、思考（think）、工具调用（tool_calls）。
- **拆分模型**（Responses）：每个语义单元是独立、平级的 typed item，靠开放的 `type` 字段区分（message / reasoning / function_call / function_call_output），工具调用与消息平级。
- **折叠 ↔ 拆分无损可逆**：reasoning item ↔ think 字段、function_call item ↔ toolCalls 字段、function_call_output item ↔ tool 消息。这是"映射直接"结论的基础。
- 折叠模型是"对话视图"（模型输出=一串消息），拆分模型是"动作视图"（模型输出=一串动作）。折叠模型解析简单、与全部第三方兼容面同构；拆分模型类型安全、编排层按 `type` 直接分派、可扩展新 item 类型而不动旧结构。

同一场景（assistant 说"我来查一下天气"并调用 `get_weather`）的两种表达：

```json
// 折叠（Chat Completions）
{"choices":[{"message":{"role":"assistant","content":"我来查一下天气",
    "tool_calls":[{"id":"call_1","type":"function","function":{"name":"get_weather","arguments":"{\"city\":\"北京\"}"}}]}}]}

// 拆分（Responses）
{"output":[
    {"type":"message","role":"assistant","content":[{"type":"output_text","text":"我来查一下天气"}]},
    {"type":"function_call","call_id":"call_1","name":"get_weather","arguments":"{\"city\":\"北京\"}"}]}
```

| 维度 | Chat Completions（折叠） | Responses API（拆分） |
|------|--------------------------|----------------------|
| 端点 | `POST /v1/chat/completions` | `POST /v1/responses` |
| 输入 | `messages: [{role, content}]` 数组（每次全量重发） | `input`（单个 string 或 typed items 数组）+ 顶层 `instructions` |
| 输出 | `choices[0].message`（一个 message 对象） | `output[]`：扁平 typed items 数组（message / reasoning / function_call / function_call_output） |
| 系统提示 | `messages[0].role="system"` | 顶层 `instructions`；或 input 中 system/developer 角色 item（保留完整 transcript） |
| 对话延续 | 调用方手动维护并全量重发历史 | `previous_response_id` 链式引用 / Conversations API（服务端状态）；或手动回放 `output` items |
| 工具调用 | 嵌套在 `choices[0].message.tool_calls[]`，`id` | `output` 中 `type:"function_call"` item，`call_id` |
| 工具结果回传 | 新 message `role:"tool"` + `tool_call_id` | `type:"function_call_output"` item + `call_id` |
| 结构化输出 | `response_format`（json_object / json_schema） | `text.format`（json_schema 默认 strict；也支持 json_object） |
| 流式 | SSE chunk：`choices[0].delta` 增量拼接 | 具名事件流：`response.output_text.delta`、`response.completed` 等语义化事件 |
| 参数重命名 | `max_tokens`（旧）/ `max_completion_tokens`（当代规范） | `max_output_tokens` |
| 多路生成 | `n` 支持多候选 | `n` 已移除，一次请求一个生成 |
| 状态存储 | 默认存储（新账号），`store:false` 可关 | 默认存储，`store:false` 可关；ZDR 场景强制无状态 + 加密 reasoning item |
| 工具 strict 语义 | 默认非严格 | 省略 `strict` 视为尝试严格模式（schema 不兼容时回退非严格并返回 `strict:false`） |

关键设计意图：

- **Item 是基本单元**：reasoning、function_call、function_call_output 与 message 平级，不再"粘"在 message 里，便于 agent 编排层按 `type` 分派。
- **状态管理右移**：从"应用负责历史"变为"服务端可托管历史"（但 stored response 有 30 天 TTL，且链式引用的历史 input token 仍按 input 计费，"链式不等于免费上下文"）。
- **流式事件语义化**：文本增量、工具调用增量、完成事件是不同事件类型，比"猜 delta 形状"更类型安全。

### 2. 生态现状（2026-08 时点）：谁原生支持 Responses

| 厂商/平台 | Chat Completions | Responses API | 备注 |
|-----------|:---:|:---:|------|
| OpenAI | ✅ 仍支持 | ✅ 原生，推荐路径 | GPT-5.4 起推理模型工具调用仅在 Responses 完善 |
| Ollama | ✅ 完整支持 | ✅ 原生（v0.13.3+） | **仅无状态子集**：`input`/`instructions`/`tools`/`stream`/`temperature`/`top_p`/`max_output_tokens`；不支持 `previous_response_id`、`conversation`、`truncation` |
| 火山方舟 | ✅ | ✅ 原生（2025-10 起，`api/v3/responses`） | 国内最早的跟进者 |
| 阿里百炼 | ✅ | ✅ 原生（2026 上半年起） | 官方兼容模式端点 |
| 百度千帆 | ✅ | ✅ 原生（2026-05-27 起，`v2/responses`） | 支持 `store:true` + `previous_response_id` 与内置工具，可跑 deepseek-v4/glm-5/qwen3 系列 |
| 智谱 GLM | ✅ OpenAI 兼容 | ❌ 无 `/responses` | 含 coding plan 端点；且兼容的是"2023 年初简化版"（content 数组、tool schema 部分特性不支持，返回 1210 错误） |
| DeepSeek | ✅ | ❌ | 仅 `/chat/completions`（2026-06 官方 issue 仍未支持） |
| Kimi / MiniMax / SiliconFlow / 讯飞 | ✅ | ❌ | 同上 |
| Anthropic | Anthropic 协议 | — | 另一套协议，与 Responses 无关；Codex 只讲 Responses，Claude 兼容端点需走 Claude Code 或转换网关 |

结论：**除 OpenAI、Ollama（无状态子集）、火山/阿里/百度千帆外，主流第三方（尤其国内 DeepSeek/智谱/Kimi 等）仍停留在 Chat Completions**；且跟进者的支持范围并不一致（Ollama 只有无状态子集，国内厂商部分支持 `store`/`previous_response_id`）。而 Codex CLI 这类新客户端只讲 Responses。两侧之间的"翻译层"在可预见的未来仍是刚需——这正是 2026 年大量中转工具涌现的原因。

### 3. 自动转换的实现方式

三条路线，可叠加：

#### 路线 A：应用侧统一内部模型 + 新增 dialect 适配器（嵌入式）

本仓库 nop-ai 现有模式的直接扩展。`OpenAiDialect` 已实现 Chat Completions 的四个核心方法（`buildBody` :143、`parseResponse` :167、`parseStreamChunk` :214、`convertMessage` :243，OpenAiDialect.java）。新增 `ResponsesDialect` 只需实现同一 `ILlmDialect` 接口：

- `buildBody(ChatRequest)`：`messages` → `instructions`（system）+ `input`（items 数组）；`max_tokens` → `max_output_tokens`；`response_format` → `text.format`；`tool` 消息 → `function_call_output` item。
- `parseResponse()`：`output[]` 按 `type` 分派，message item → 正文，reasoning item → think 字段，function_call item → toolCalls。
- `parseStreamChunk()`：从 `response.output_text.delta` 事件提取内容（现有实现只认 `choices.0.delta.content` 路径，需新增事件分支）。

**适用边界**：上述"成本低"仅对**后端 Provider 方言**方向成立（消费上游 Responses 端点）。若让 ResponsesDialect 担任**前端客户端侧方言**（服务 Responses 请求，如 Codex 类客户端场景），还需实现 `parseRequestBody`（`ILlmDialect` 默认抛 `UnsupportedOperationException`）并重写 `buildResponse` / `buildStreamChunk`（默认产物是 OpenAI chat 格式），工作量约为后端方向的两倍。

额外注意：

- `ChatOptions.responseFormat` 目前是 String 且 `OpenAiDialect.buildBody` 根本不输出 `response_format`；Responses 的 `text.format.json_schema` 需要 schema 对象，String 载体无法承载——若走此路线需扩展 `ChatOptions`。
- 流式工具调用的现有缺口比文档预期大：`OpenAiDialect.parseStreamChunk` 连 chat 格式的 `choices.0.delta.tool_calls` 都不解析（`ChatToolCallChunk` 模型在 OpenAI 路径从未被填充，仅 `AnthropicDialect` 的 tool_use 分支填充），Responses 路线需要补的不只是事件分支。
- 设计约束：`ChatServiceImpl.java:175` 只把 SSE 的 `data:` 行传给 `parseStreamChunk`，`event:` 行不进 dialect；Responses 语义化事件可依靠 data 载荷内的 `type` 字段分派，可行但属架构约束。

优点：无额外部署、与 `LlmDialectFactory` 统一注入；缺点：流式事件序列重建工作量最大。

#### 路线 B：网关/代理中间层（部署型，业界主流）

客户端仍发 Responses，网关在中间转成 Chat Completions 发给上游，再把响应/流式事件转回。已验证的生产级实现：

| 工具 | 语言 | 转换机制 | 已知坑 |
|------|------|----------|--------|
| litellm | Python | Responses→Chat Completions 桥接；官方机制为 `use_chat_completions_api: true` 或模型前缀 `openai/chat_completions/<model>`（`openai/` 前缀 + 自定义 api_base 时会原样透传 `/responses` 导致 404） | `client_metadata` 等 Responses 专有参数透传 bug（官方 issue #29834）；OpenAI 专有工具类型（web_search/code_interpreter/file_search）需过滤，上游只认 `function` |
| new-api | Go | Codex CLI→智谱 GLM 官方通道，Responses↔Chat 双向映射，支持流式与非流式、function call 双向 | 需较新版本；国内网络流式超时需调大 |
| CCX | Go | `serviceType: openai` 控制协议转换（responses 分组选渠道）；`modelMapping` 做模型名映射（Codex 只认 gpt-* 名称 → 上游真实模型名）；`stripCodexClientTools` 剥离私有字段 | base_url 必须带 `/v1` 后缀；Codex 元数据表外模型名需映射 |
| cc-switch | 桌面工具 | 本地路由 `127.0.0.1:15721/v1` + `meta.apiFormat="openai_chat"` 标记上游形态 | 仅 Codex 场景 |
| glm-local-proxy | Rust | Chat Completions 兼容层（content 扁平化、tool schema 清洗、temperature 精度修正） | 只解决"简化版兼容"，不涉及 Responses |

要点：这类网关本质是**同一个转换逻辑的产品化**——请求改写（端点和请求体）+ 响应回译（JSON/SSE）+ 字段归一化（usage：`prompt_tokens`→`input_tokens` 等）+ 能力降级（剥离不支持的 hosted tools 与状态参数）。

#### 路线 C：纯客户端层（OpenAI SDK 换 base_url）

仅适用于上游已有原生 Responses 端点（Ollama v0.13.3+、火山/阿里/百度千帆）：`client.responses.create(...)` + `base_url` 指向对方。零转换，但受限。

#### 三路线对比

| 维度 | 路线 A（dialect 嵌入） | 路线 B（网关） | 路线 C（纯客户端） |
|------|----------------------|---------------|-------------------|
| 部署成本 | 无额外组件，随应用发布 | 独立部署/维护一中间层 | 无 |
| 工作量 | 中（一个方言 + 流式状态机） | 高（但可复用 litellm 等现成产品） | 零 |
| 覆盖范围 | 后端方向（消费上游）为主；前端方向成本翻倍 | 双向全量（Responses↔Chat/Anthropic 等） | 仅原生支持者 |
| 适用场景 | nop-ai 应用直接对接 OpenAI/Ollama 新端点 | 服务 Codex 类客户端接国产模型 | 上游已有原生端点 |
| 与 nop-ai 的关系 | 复用 `ILlmDialect`/`LlmDialectFactory` | 可独立模块，复用 `ChatRequest` 作中间表示 | 无 |

#### 核心转换映射表（Responses ↔ Chat Completions 双向）

Responses 请求 → Chat Completions 请求：

```
instructions + input(items)               → messages（system + user/assistant；input 中的
                                             system/developer 角色 item 按角色保留）
max_output_tokens                          → max_completion_tokens（旧字段 max_tokens）
text.format.json_schema / json_object      → response_format.json_schema / json_object
tools（function 类型）                     → tools（透传）
temperature / top_p / stream / stop        → 同名透传
```

Chat Completions 响应 → Responses 响应（非流式）：

```
choices[0].message.content                 → output[] 中 message item（content 数组）
choices[0].message.tool_calls[]            → output[] 中 function_call items（id→call_id）
reasoning_content / thinking               → output[] 中 reasoning item（summary）
usage.prompt_tokens / completion_tokens    → usage.input_tokens / output_tokens
prompt_tokens_details.cached_tokens        → input_tokens_details.cached_tokens
completion_tokens_details.reasoning_tokens → output_tokens_details.reasoning_tokens
finish_reason                              → status / message item
```

Chat Completions 请求 → Responses 请求（历史回放方向，工具场景必做）：

```
role:"tool" 消息 + tool_call_id           → function_call_output item（call_id + output）
assistant 消息嵌套 tool_calls[]           → 平铺为 function_call items（id→call_id）
```

流式（Chat SSE → Responses SSE 事件序列）：

```
choices[0].delta.content 增量              → response.output_text.delta 事件
delta.reasoning_content 增量               → response.reasoning_summary_text.delta（think 增量）
角色/工具增量                              → response.output_item.added / response.function_call_arguments.delta
完成                                       → response.output_text.done / response.completed
标准事件序列                               → response.created → output_item.added → content_part.added →
                                             output_text.delta → output_text.done → content_part.done →
                                             output_item.done → response.completed
```

**不可 1:1 映射、必须降级处理的语义**：

1. `previous_response_id` / Conversations：第三方无服务端状态。标准做法是网关强制 `store:false`，把先前 `output` items 手动回放为 `input`（ZDR 场景的官方推荐路径，Ollama 即如此）。
2. OpenAI hosted tools（web_search / file_search / code_interpreter）：上游普遍不支持，必须过滤或映射为本地工具。
3. Chat 独有、Responses 移除的参数：`n`、`logprobs`、`stop`、`presence_penalty`、`frequency_penalty`、`logit_bias`、`seed`。其中 `n` 需多次请求替代，`stop` 需转为 instructions 约束，其余直接丢弃。
4. reasoning item 加密（ZDR）：OpenAI 专属，第三方无对应物，直接剥离。
5. strict 默认值差异：Chat 默认非严格、Responses 默认尝试严格，转换后 schema 兼容性校验由网关负责。

### 4. 并发工具执行：协议支持与应用层并发

**模型侧（Responses 支持，且这是设计重点）**：`parallel_tool_calls` 参数（默认开启，设 `false` 强制每轮最多一个工具）。模型一次响应可输出**多个** `function_call` items，各带独立 `call_id`；流式下多个调用的 `function_call_arguments.delta` 交错出现，靠 item 索引 + `call_id` 区分。

**关键认知：API 只负责"批量产出"，并发执行是应用层的职责。** 标准循环：收集一轮内全部 `function_call` → 并发执行 → 全部完成后**一次性**把所有 `function_call_output` 回传。协议侧规则：

- 每个 `function_call` 必须回传一个匹配 `call_id` 的 `function_call_output`，缺一个模型就停在 `requires_action` 不继续。
- 关联只认 `call_id`，绝不按数组下标绑定。
- 并行不是省 token，是省 round-trip：顺序执行 N 个工具要 N+1 次 API 调用，并行只要 2 次；且每次 round-trip 都重算 input token，所以并行同时降低成本。
- 限制：GPT-5 起自定义函数可与内置工具并行，但 built-in tools（web_search/file_search）不能混进并行批次（有社区反馈 web_search 在场会抑制并行）；`max_tool_calls` 参数限制内置工具总调用数。

**其他框架的处理**（OpenAI Agents SDK、LangChain/LangGraph、Claude Agent SDK 一致）：

- 收集整批 `function_call` → `Promise.all` / `asyncio.gather` 并发执行 → 全部完成后一次性回传。
- 读操作并发、写操作串行化（下游冲突控制）；对下游系统加并发上限（semaphore 限流）防止打爆外部 API。
- 单工具失败不阻塞整批：失败转成错误 `output` 回传给模型，由模型决定重试/换路。

**nop-ai 现状：已经是并发执行**。`ReActAgentExecutor.java:1906-1984` 实现了完整 fan-out：每个调用提交为独立 `CompletableFuture`（`toolManager.callTool(...)`，:1914-1947）→ `CompletableFuture.allOf(...).get()` 等整批完成（:1964，interruptible）→ per-tool 独立超时（`toolTimeoutMs`，超时转错误输出，:1929-1944）→ AR-15 保护中途同步异常时取消孤儿 futures（:1948-1955）→ 整批结果统一组装为 `ChatToolResponseMessage` 回填。

**对转换层的含义**：折叠模型在这里反而是优势——`ChatAssistantMessage.toolCalls` 是 `List<ChatToolCall>`，Responses 的多个 `function_call` items 折叠进去后**直接复用现有并发循环，转换层无需为并行做任何特殊处理**。唯一例外：`output[]` 中"assistant message + 多个 function_call 交错"折叠到单条 `ChatAssistantMessage` 后，并发执行与 `call_id` 配对依然成立，但中间穿插的 reasoning item 在回传时的顺序位置会丢失。

### 5. 与 nop-ai 项目的关系

- 项目已有 dialect 模式与 `LlmDialectFactory`，`OpenAiDialect`/`OllamaDialect` 等 4 个方言并存（静态注册，`register()` 可插拔），**新增 `ResponsesDialect` 作为后端 Provider 方言的架构成本很低**（路线 A），可让 nop-ai 直接对接 OpenAI 新端点与 Ollama v0.13.3+。
- 若目标是让 nop-ai 成为"国产模型 + Codex 类客户端"的中间层（路线 B 的网关定位），则需要把转换逻辑做成独立模块（复用现有 `ChatRequest` 内部模型做中间表示，实现 Responses wire ↔ 内部模型 ↔ ChatCompletions wire 两段适配），这正是 litellm 桥接的思路。注意前端方向需额外实现 `parseRequestBody`（默认抛异常）与 `buildResponse`/`buildStreamChunk` 重写，成本约为后端方言两倍。
- "映射基本直接"的两个例外（非流式、单 message item 场景成立，工具循环场景需注意）：
  - **finish_reason 归一化缺口**：`AbstractLlmDialect.normalizeFinishReason` 只认 stop/end_turn/length/tool_calls 等（AbstractLlmDialect.java:103-120），Responses 的顶层 `status`（"completed"/"incomplete"）不在映射表内，会被原样透传而非归一为 stop，需扩展。
  - **多 item output 折叠丢失顺序**：`ChatResponse.message` 是单个折叠的 `ChatAssistantMessage`，`output[]` 中交错出现多个 message/function_call item 时（工具循环常见），折叠会丢失顺序信息。
- 注意点：`OpenAiDialect.parseStreamChunk`（OpenAiDialect.java:214）只认 `choices.0.delta.*` 路径，且不解析流式 `tool_calls`；Responses 的具名事件流需要新的解析分支，这是主要工作量。`buildUrl` 中 `chatUrl` 配置项已支持自定义路径（`ChatServiceImpl.java:206`，默认 `/v1/chat/completions`），指向 `/v1/responses` 即可复用。
- Ollama 的三层关系需澄清：`OllamaDialect`（OllamaDialect.java:49）适配的是 Ollama **原生** `/api/chat` 风格（`options.num_predict`、`done_reason`），与 OpenAI 兼容端点（`/v1/chat/completions`）、新 Responses 端点（`/v1/responses`，v0.13.3+）是三种 wire 格式。对接 Ollama Responses 端点需要新增 `ApiStyle` 枚举值（当前无 responses 值）。
- 本仓库还有 `nop-ai-gateway` 模块（`AiDialectBackendMessageConverter` 已引用 `ILlmDialect` + `LlmDialectFactory`，流程为 frontend.parseRequestBody → backend.buildBody → backend.parseResponse → frontend.buildResponse，含流式 buildStreamChunk），网关侧已具备接入转换层的挂点。
- 边界与可复用资产：`ChatToolResponseMessage` 另有 `result(Object)`/`resultType` 字段，可承载 `function_call_output.output` 的对象化输出；`ChatMessage.providerHints`（Map）目前仅 `AnthropicDialect` 消费 `cache_control` 键，其余方言未消费，可复用为 Responses 专有参数的透传通道；多模态（`ChatUserMessage.attachments`）当前无任何 dialect 序列化，Responses 的 content 数组（image_url/input_audio）无落点；`ChatCustomMessage` 折叠往返有损（未知角色一律归为 user）。

## Conclusion

- Responses 与 Chat Completions 是**结构性差异**（折叠模型 vs 拆分模型：typed items 与事件流 vs message 数组与 delta 增量），不是字段改名；"message→item"、"嵌套 tool_calls→扁平 function_call"、"delta→具名事件"三个转换点覆盖了差异的主体。
- 折叠 ↔ 拆分无损可逆，因此 nop-ai 内部模型（折叠形态，与 Chat Completions 同构）与 Responses 的映射基本直接：reasoning item → think、function_call item → toolCalls、function_call_output → tool 消息，均为字段级映射；例外集中在 finish_reason 归一化缺口与多 item 折叠丢失顺序两处。
- 自动转换完全可行，且已是成熟市场：litellm、new-api、CCX、cc-switch 均已产品化；技术要点是 双向字段映射 + SSE 事件序列重建 + usage 归一化 + 不支持能力降级（状态参数、hosted tools、`n`/logprobs/penalty 系）。
- 生态分化是转换需求的根源：2026-08 时点原生支持 Responses 的有 OpenAI、Ollama（无状态子集）、火山/阿里/百度千帆；智谱/DeepSeek/Kimi 等国内厂商均需翻译层，且跟进节奏不一。
- 对 nop-ai 的建议：优先走路线 A（新增 `ResponsesDialect` 作后端 Provider 方言，对接 OpenAI 与 Ollama 新端点），路线 B（网关转换）仅在需要服务 Codex 类客户端时再做独立转换模块。
- 被否决/延后的方案：路线 C（纯客户端换 base_url）——依赖上游原生 Responses，仅对 Ollama/火山/阿里/百度千帆可用，覆盖面窄；路线 B 的网关形态——部署与运维成本高，nop-ai 无对应产品定位前不值得自建。
- 后续工作：若决定实施，产出 `ai-dev/design/` 设计文档（ResponsesDialect 规格 + ChatOptions.responseFormat 扩展）与 `ai-dev/plans/` 执行计划。

## Open Questions

- [ ] nop-ai 是否需要现在支持 Responses 端点（用户侧是否有 Codex 类客户端接入需求）？
- [ ] 国内厂商（智谱/DeepSeek）跟进 Responses 协议的节奏评估，若跟进快则转换层价值衰减。
- [ ] 流式事件转换是否值得抽象成通用 `StreamEventTranslator`（Chat delta ↔ Responses 事件），还是每个 dialect 自带解析即可。
- [ ] `ChatServiceImpl` 只透传 SSE `data:` 行（`event:` 行不可达）的设计约束是否可接受，还是需要先改事件透传再上 ResponsesDialect。
- [ ] `ChatOptions.responseFormat` 是否升级为对象载体（承载 json_schema），还是另立字段。

## References

- OpenAI 官方迁移指南: https://developers.openai.com/api/docs/guides/migrate-to-responses
- Ollama OpenAI 兼容文档（`/v1/responses` v0.13.3+）: https://docs.ollama.com/api/openai-compatibility
- Ollama 转换实现（`openai/openai.go`）: https://github.com/ollama/ollama/blob/main/openai/openai.go
- 百度千帆 Responses API 文档: https://cloud.baidu.com/doc/qianfan-docs/s/4mi400l1m
- litellm Responses 桥接实战: https://jishuzhan.net/article/2060318271278231554
- new-api 智谱 GLM coding plan 通道: https://github.com/cuihuir/new-api/blob/main/docs/channel/zhipu-glm-coding-plan.md
- CCX 协议转换实战（serviceType/modelMapping）: https://www.kuazhi.com/post/716398157.html
- CC Switch 本地路由（DeepSeek/Kimi 等）: https://inkmetaai.com/tutorials/ai-coding/connect-deepseek-to-codex-with-cc-switch
- glm-local-proxy（智谱兼容层坑点）: https://github.com/cuiguoke/glm-local-proxy
- 智谱 OpenAI 兼容文档: https://docs.bigmodel.cn/cn/guide/develop/openai/introduction
- 项目文件: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/OpenAiDialect.java`、`ILlmDialect.java`、`LlmDialectFactory.java`、`AbstractLlmDialect.java`、`nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/`（ChatRequest/ChatResponse/ChatOptions/ChatStreamChunk/messages/）、`nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/AiDialectBackendMessageConverter.java`
