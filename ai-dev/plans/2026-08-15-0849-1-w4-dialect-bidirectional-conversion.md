# W4 ILlmDialect 双向转换补全（请求 + 响应方向）

> Plan Status: completed
> Last Reviewed: 2026-08-15
> Source: `ai-dev/backlog/nop-ai-gateway-failover-roadmap.md`（W4）、`ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`（§3.7/§四.3/§七）
> Related: `ai-dev/design/nop-ai-gateway/01-architecture.md`、`ai-dev/plans/2026-08-15-0849-2-w5-model-class-routing-and-selection.md`
> Mission: nop-ai-gateway-failover
> Work Item: W4

## Purpose

补全 nop-ai-core `ILlmDialect` 的双向转换能力：`parseRequestBody`（Provider 请求体 → ChatRequest）从"仅 OpenAI"补全为全部 5 个 dialect；`buildResponse`/`buildStreamChunk`（ChatResponse/ChatStreamChunk → 前端格式）从"恒产出 OpenAI 格式"补全为逐 dialect 覆写，使"任意前端格式 ↔ 任意 Provider 后端格式"在请求和响应两个方向闭环。这是 W6/W7 非 OpenAI 后端切换的前提（roadmap Stage 4 critical path）。

## Current Baseline

（live repo 核实，2026-08-15）

- `ILlmDialect` 位于 `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/ILlmDialect.java`（323 行）。反向转换三方法现状：
  - `parseRequestBody(Map)`（`ILlmDialect.java:240-245`）：default 抛 `UnsupportedOperationException`，消息文本 "parseRequestBody is only implemented for OpenAI dialect. Anthropic, Gemini, and Ollama dialects do not support bidirectional gateway conversion. Current gateway supports one-way OpenAI→Provider only."——**该文本已过时**（Responses dialect 未被提及；"one-way" 结论将被本计划推翻）。
  - `buildResponse(ChatResponse)`（`ILlmDialect.java:251-274`）：default 恒产出 OpenAI 格式（`chat.completion` + choices[0].message.content），**无任何 dialect 覆写**。
  - `buildStreamChunk(ChatStreamChunk)`（`ILlmDialect.java:281-322`）：default 恒产出 OpenAI delta 格式（`chat.completion.chunk`，含 tool_call/reasoning 分支），**无任何 dialect 覆写**。
- 5 个 dialect 实现类（同一目录）：`OpenAiDialect`（413 行）、`AnthropicDialect`（510 行）、`GeminiDialect`（428 行）、`OllamaDialect`（342 行）、`ResponsesDialect`（494 行）。
  - **仅 `OpenAiDialect.java:62` 覆写了 `parseRequestBody`**（messages/options/tools 三部分解析，reverse of OpenAI buildBody）。
  - 全部 5 类已有**正向方向**能力（`buildBody`/`parseResponse`/`parseStreamChunk`/`convertMessage`/`buildUrl`/`setHeaders`/`parseErrorResponse`），即 reverse 的基准存在。
  - `ResponsesDialect` 是 OpenAI Responses API 风格（`buildBody` 产 `input` items 结构，`convertMessage` 产 input item，见 `ResponsesDialect.java:78-247`），其 parseRequestBody reverse 结构与 `OpenAiDialect` 不同，需独立实现。
- **既有消费方已通过接口调用反向转换方法**（Wiring 证据）：`nop-ai/nop-ai-gateway/.../AiDialectBackendMessageConverter.java` 在非流式路径经 `LlmDialectFactory.getDialect(...)` 调用 `parseRequestBody`（`:48`）、`buildResponse`（`:68`）、`buildStreamChunk`（`:78`）——新实现经同一接口分发即可达，无需新增接线。converter 当前 `frontendLlm/backendLlm` 为固定 `ApiStyle` bean 属性（`:31-32`，默认 openai），动态 dialect 解析属 W7（本计划 Out Of Scope）。
- **既有测试**（`nop-ai/nop-ai-core/src/test/java/io/nop/ai/core/dialect/`）：`TestOpenAiDialect` / `TestAnthropicDialect` / `TestGeminiDialect` / `TestOllamaDialect` / `TestResponsesDialect` / `TestLlmDialectFactory` / `TestLlmDialectErrorResponse` / `TestLlmDialectTokenEstimation` / `TestNormalizeFinishReason` / `TestChatUsage`。**注意 `TestResponsesDialect.java:346-350` 现断言 `parseRequestBody` 抛 UOE**（`assertThrowsUOE`，"No silent skip (parseRequestBody front-end not supported)"）——本计划落地后该断言失效，**必须改为真实行为断言**（预期改动，非删除覆盖）。
- 需求规格 §3.7 已明确补全范围（请求 + 响应两方向）与附带改动（javadoc + UOE 消息同步）；§七 要求 `01-architecture.md` "前端恒 OpenAI" 假设同步。
- `01-architecture.md` 现状表述（需同步）："默认的 `buildResponse`/`buildStreamChunk` 产生 OpenAI 格式（通用网关前端格式）"（`:50`）；converter 配置表 frontendLlm 默认 openai（`:75-80`）。
- 无任何 dialect 的 `buildResponse`/`buildStreamChunk` 覆写存在（grep 实证）。

## Goals

- 5 个 dialect（openai/anthropic/gemini/ollama/responses）的 `parseRequestBody` 全部可用（OpenAI 已存在，补 Anthropic/Gemini/Ollama/Responses 4 个），实现为各 dialect `buildBody` 的逆映射。
- `buildResponse`/`buildStreamChunk`：Anthropic/Gemini/Ollama/Responses 4 个覆写 + OpenAI 继承 default（default 即 OpenAI 形态），实现为各 dialect `parseResponse`/`parseStreamChunk` 的逆映射（产出各 Provider 原生格式，而非恒 OpenAI）。
- `ILlmDialect` javadoc 与 default UOE 消息文本同步更新（移除 "one-way OpenAI→Provider only" 过时表述）；default 方法保留但消息改为通用 "not implemented for this dialect" 语义（未来新 dialect 未实现时快速失败，符合 No Silent No-Op）。
- 双向转换参数化测试（每 dialect × 请求方向 × 响应方向 + roundtrip 性质）+ 既有正向方向测试零回归。
- `01-architecture.md` "前端恒 OpenAI" 假设与 converter 相关表述同步；`02-account-failover-requirement.md` §3.7 落地状态回填。

## Non-Goals

- 网关流式请求体生成（W7 的 `stream=true` 扩展）——converter 流式扩展属 W7。
- converter per-request 动态 dialect 解析（W7）。
- 任何 `ChatServiceImpl` / nop-ai-gateway 行为改动（本计划仅 nop-ai-core dialect 层）。
- 新 dialect 支持（不在 5 个既有 ApiStyle 内）。

## Scope

### In Scope

- DLCT-01: `parseRequestBody` 补全 Anthropic/Gemini/Ollama/Responses 四 dialect。
- DLCT-02: `buildResponse`/`buildStreamChunk` 逐 dialect 覆写（Anthropic/Gemini/Ollama/Responses 4 个；OpenAI 继承 default 不覆写）。
- DLCT-03: `ILlmDialect` javadoc + UOE 消息文本同步；`01-architecture.md` "前端恒 OpenAI" 假设同步修订。
- DLCT-04: 双向转换参数化测试（每 dialect × 请求/响应方向）+ roundtrip + 既有正向测试零回归。

### Out Of Scope

- W7 的 converter 扩展（流式 stream=true / 动态 dialect / 目标 provider 真实 LlmModel config）。
- W5/W6 工作。
- 各 dialect 正向方向（buildBody/parseResponse/parseStreamChunk）的语义修改。

## Execution Plan

## 附录（执行期落档）：Phase 1 字段映射表（parseRequestBody：Provider 请求体 → ChatRequest）

> reverse 契约：parseRequestBody 读取的字段必须与对应 dialect `buildBody` 写入的字段逐一对齐。
> 逐字段核对基准 = 各 dialect `buildBody` 的 `addOptionIfNotNull`/`body.put` 实况（执行时核对，行号以核对日 live repo 为准）。

### AnthropicDialect.parseRequestBody

| buildBody 字段 | parseRequestBody 读取 | 映射目标 | 备注 |
|---|---|---|---|
| `model` | — | — | 显式丢弃（OpenAI 先例：model 不进入 ChatRequest；执行时从 body map 自取） |
| `stream` | — | — | 丢弃 |
| `max_tokens`（强制 4096 兜底注入，`:90-95`） | `max_tokens` | options.maxTokens | roundtrip 前显式 setMaxTokens（有损点 (b) 裁定） |
| `system`（最后一条 system 折叠，`:113-114`） | `system` | 首位 ChatSystemMessage | system 折叠有损点 (c)：样本限一条 system |
| `messages[].role` | `role` | user→ChatUserMessage / model|assistant→ChatAssistantMessage | |
| `messages[].content[]` blocks | `type` 分派 | text→内容文本；thinking→ChatReasoningMessage；tool_use→ChatToolCallMessage(id/name/input)；tool_result→ChatToolResponseMessage(tool_use_id, name=null, content) | tool_result 不携带函数名（格式限制，有损落档）；string input 经 parseToolInput 解析（畸形 fail-fast） |
| `temperature` | `temperature` | options.temperature | |
| `top_p` / `top_k` | `top_p` / `top_k` | options.topP / options.topK | |
| `stop_sequences` | `stop_sequences` | options.stop | |
| `tools[]`（name/description/input_schema，convertToolDefinitions 覆写） | `tools[]` | ChatToolDefinition(name/description/parameters←input_schema) | |

### GeminiDialect.parseRequestBody

| buildBody 字段 | parseRequestBody 读取 | 映射目标 | 备注 |
|---|---|---|---|
| `systemInstruction.parts[].text`（`:108-112`） | `systemInstruction.parts[].text` | 首位 ChatSystemMessage（多 part 以 \n 拼接） | system 折叠有损点 (c)：样本限一条 system |
| `contents[].role` | `role` | model→assistant / user→user | |
| `contents[].parts[]` | `parts` 分派 | text→内容文本；`<thinking>..</thinking>` 包裹文本→ChatReasoningMessage（strip 逆向）；functionCall{name,args}→ChatToolCallMessage(name,args)；functionResponse{name,response.result}→ChatToolResponseMessage(name, result) | Gemini functionCall/functionResponse 不携带调用 id（格式限制，有损落档：callId=null）；args 为结构化 Map |
| `generationConfig.temperature` | `temperature` | options.temperature | |
| `generationConfig.maxOutputTokens` | `maxOutputTokens` | options.maxTokens | |
| `generationConfig.topP` / `topK` | `topP` / `topK` | options.topP / options.topK | |
| `generationConfig.stopSequences` | `stopSequences` | options.stop | |
| `tools[0].functionDeclarations[]`（`:129-131` 包装） | `tools[0].functionDeclarations[]` | ChatToolDefinition(name/description/parameters) | |

### OllamaDialect.parseRequestBody

| buildBody 字段 | parseRequestBody 读取 | 映射目标 | 备注 |
|---|---|---|---|
| `model` / `stream` | — | — | 丢弃 |
| `messages[]`（system 被 buildMessages `continue` 跳过，`:326-328`） | `messages[]` | user/assistant/tool 三角色；thinking→ChatReasoningMessage；tool_calls（OpenAI 嵌套）→ChatToolCallMessage；tool 角色→ChatToolResponseMessage(tool_call_id/name/content) | **system 消息为正向有损**（buildBody 静默丢弃）：parseRequestBody 无法还原，roundtrip 样本不含 system（落档） |
| `options.temperature` / `num_predict` / `top_p` / `top_k` / `stop` | 同名 | options.temperature / maxTokens / topP / topK / stop | num_predict→maxTokens |
| `tools[]`（OpenAI 嵌套 format，default convertToolDefinitions） | `tools[].function` | ChatToolDefinition(name/description/parameters) | |

### ResponsesDialect.parseRequestBody

| buildBody 字段 | parseRequestBody 读取 | 映射目标 | 备注 |
|---|---|---|---|
| `instructions`（多条 system 以 \n 拼接，`:93-104`） | `instructions` | 首位 ChatSystemMessage | 多条 system 折叠为一条（有损落档：样本限一条 system） |
| `input[]` typed items（convertMessage 产出） | `input[]` `type` 分派 | message(user→ChatUserMessage/assistant→ChatAssistantMessage, content parts 提取)；reasoning→ChatReasoningMessage(summary)；function_call→ChatToolCallMessage(call_id/name/arguments JSON 字符串→Map)；function_call_output→ChatToolResponseMessage(call_id, name=null, output) | function_call_output 不携带函数名（格式限制，有损落档：name=null）；arguments 字符串经 parseArguments 容错解析（与 parseResponse 同款容错，落档选择） |
| `temperature` / `top_p` / `max_output_tokens` | 同名 | options.temperature / topP / maxTokens | |
| `tool_choice` | `tool_choice` | options.toolChoice | |
| `tools[]`（扁平格式，convertToolDefinitions 覆写） | `tools[]` | ChatToolDefinition(type/name/description/parameters) | |

### 公共裁定（Phase 1）

- **model/stream 丢弃**：ChatRequest 规范模型不承载 model/stream（OpenAI `parseRequestBody` 先例），closed-loop 测试执行时从 body map 自取 model 传入 buildBody。
- **不支持字段静默丢弃为合法**（OpenAI 先例 + 计划 §Phase 1 第二项）；**结构缺失/类型错误 fail-fast**（Anthropic tool_use input 走 parseToolInput 抛 NopException；不空 catch）。
- **default UOE 保留**：`ILlmDialect.parseRequestBody` default 消息改为 `"parseRequestBody is not implemented for this dialect: " + getName()`（未来新 dialect 快速失败，Minimum Rules #24）。

### Phase 1 - 请求方向补全：parseRequestBody 四 dialect（DLCT-01）

Status: completed
Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/`（AnthropicDialect/GeminiDialect/OllamaDialect/ResponsesDialect/ILlmDialect）

- Item Types: `Fix | Decision`

- [x] 语义基准核对（Decision）：对 4 个 dialect 逐一核对 `buildBody` 的字段结构（Anthropic: system+user/assistant messages 结构、`max_tokens`/`temperature`、`tools`；Gemini: `contents`/`systemInstruction`、`generationConfig`；Ollama: `model`/`messages`/`stream`/`options`；Responses: `input` items（`input_text`/`input_image` 等）+ `instructions`/`max_output_tokens`）——parseRequestBody 必须与对应 `buildBody` 字段名逐一对齐（reverse 契约），**字段映射表落档于本 plan 文件（执行时追加附录"Phase 1 字段映射表"），closure 抽查以此表为准**。
- [x] 4 个 dialect 实现 `parseRequestBody(Map)`：解析 messages（角色/内容/tool_calls/tool response 映射到 `ChatMessage` 子类，与 `OpenAiDialect.parseRequestBody:62-125` 同构的解析风格）、options（temperature/max_tokens/top_p 等）、tools（若 buildBody 产出 tools）；Responses 按 input item 结构解析（`input_text` → user/assistant 内容、tool_call/tool_result item → 对应 ChatMessage 子类）。**不支持的字段静默丢弃为合法**（与 OpenAI 先例一致），**结构缺失/类型错误不得静默吞掉**——无法映射到 ChatRequest 的字段按 fail-fast 语义处理或显式忽略并记录（落档选择，禁止空 catch）。
- [x] `ILlmDialect.parseRequestBody` javadoc 更新（移除 "Only OpenAiDialect implements this method" / "one-way OpenAI→Provider only"）；default UOE 消息改为通用文本（如 "parseRequestBody is not implemented for this dialect: " + getName()），保留 default 抛 UOE（未来新 dialect 未实现时快速失败，Minimum Rules #24）。
- [x] 随 Phase 实现同步更新 `TestResponsesDialect.java:346-350` 的 UOE 断言（现断言 "parseRequestBody not supported" 的用例改为真实解析断言——预期行为反转，不得删除测试类整体覆盖）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 4 个 dialect 的 `parseRequestBody` 覆写存在且与各自 `buildBody` 字段映射对齐（对照映射表抽查）。
- [x] `ILlmDialect` javadoc/UOE 文本已同步（grep 无 "one-way OpenAI→Provider only" / "only implemented for OpenAI" 残留）。
- [x] `./mvnw compile -pl :nop-ai-core -am` 通过；新增方法在既有消费方（`AiDialectBackendMessageConverter` 接口分发）可达（编译级 + 测试级，测试在 Phase 3 参数化覆盖）。
- [x] `TestResponsesDialect` 的 UOE 断言已改为真实行为断言（行为反转已处理，无残留 assertThrowsUOE(parseRequestBody)）。
- [x] **Rule 25 跨 Phase 覆盖声明**：Phase 1 新增 4 个 parseRequestBody 的行为覆盖由 Phase 3 参数化测试显式列出（每 dialect × 请求方向 + roundtrip）；本 Phase 结束时允许仅 compile 门禁，但 Phase 3 清单必须逐方法对账（closure 时核对无未覆盖方法）。
- [x] **无静默跳过**（Minimum Rules #24）：新增方法无空方法体；default UOE 保留为未来 dialect 的快速失败路径。
- [x] `No owner-doc update required`（文档同步在 Phase 3 统一处理）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 响应方向补全：buildResponse/buildStreamChunk 逐 dialect（DLCT-02）

## 附录（执行期落档）：Phase 2 字段映射表（buildResponse/buildStreamChunk：ChatResponse → Provider 响应 Map）

> reverse 契约：buildResponse 产出字段必须与对应 dialect `parseResponse` 的输入结构对齐；
> buildStreamChunk 产出字段必须与 `parseStreamChunk` 的输入结构对齐。逐字段核对基准 = live repo 实况。

### AnthropicDialect.buildResponse / buildStreamChunk

| ChatResponse / ChatStreamChunk | buildResponse 产出 | parseResponse 输入路径 | 备注 |
|---|---|---|---|
| response.id | `id` | `id` | |
| response.model | `model` | `model` | |
| ChatReasoningMessage | `content[] {type:thinking, thinking}` | content blocks 循环 type==thinking | |
| ChatAssistantMessage | `content[] {type:text, text}` | type==text 拼接 | |
| ChatToolCallMessage | `content[] {type:tool_use, id, name, input:arguments}` | type==tool_use（input Map/JSON 字符串双形态） | |
| finishReason 归一化值 | `stop_reason`（stop→end_turn / length→max_tokens / tool_calls→tool_use / 未知透传） | `stop_reason` → normalizeFinishReason | 有损点 (a)（a1 判据同 Phase 1 附录） |
| usage | `usage {input_tokens, output_tokens, cache_creation_input_tokens, cache_read_input_tokens}` | `usage.*`（含 cache 字段，有损点 (d)） | 有损点 (d)：cache 字段随 parseUsage 读取 |
| chunk(text, ADDED/DELTA) | content_block_start {type:text} / content_block_delta {type:text_delta} | 对应 case | |
| chunk(reasoning, ADDED/DELTA) | content_block_start {type:thinking} / content_block_delta {type:thinking_delta} | 对应 case | |
| chunk(tool_call, ADDED/DELTA) | content_block_start {type:tool_use, id, name} / content_block_delta {type:input_json_delta, partial_json} | 对应 case | |
| chunk(DONE) | message_delta {delta.stop_reason, usage} | message_delta case | |
| chunk(无 item 载荷) | message_start {message:{id, model}} | message_start case（仅读 model） | |

### GeminiDialect.buildResponse / buildStreamChunk

| ChatResponse / ChatStreamChunk | buildResponse 产出 | parseResponse 输入路径 | 备注 |
|---|---|---|---|
| response.model | `model` | `model` | |
| ChatReasoningMessage | candidates[0].content.parts[] {text, thought:true} | parts 循环 thought==true → thinkingBuilder | |
| ChatAssistantMessage | parts[] {text} | 非 thought text → contentBuilder | |
| ChatToolCallMessage | parts[] {functionCall:{name, args}} | functionCall Map → ChatToolCallMessage | Gemini 不携带调用 id（有损落档：callId=null） |
| finishReason 归一化值 | `finishReason`（stop/tool_calls→STOP / length→MAX_TOKENS / content_filter→SAFETY / 未知透传） | `candidates.0.finishReason` → normalize | 有损点 (a)：tool_calls→STOP 归一化回 stop（不可逆，样本不用 tool_calls） |
| usage | `usageMetadata {promptTokenCount, candidatesTokenCount, totalTokenCount}` | `usageMetadata.*` | |
| chunk(text/reasoning, DELTA) | candidates[0].content.parts[] {text} / {text, thought:true} | parts 循环 | Gemini 流式无 ADDED 概念，ADDED/DELTA 同构产出 |
| chunk(tool_call, ADDED) | parts[] {functionCall:{name}} | functionCall → tool_call ADDED | |
| chunk(DONE) | candidates[0].finishReason | candidates.0.finishReason | |
| chunk.model | 顶层 `model` | `model`/`modelVersion` | |

### OllamaDialect.buildResponse / buildStreamChunk

| ChatResponse / ChatStreamChunk | buildResponse 产出 | parseResponse 输入路径 | 备注 |
|---|---|---|---|
| response.model | `model` | `model` | |
| ChatReasoningMessage | message.thinking | `message.thinking` | |
| ChatAssistantMessage | message.content | `message.content` | |
| ChatToolCallMessage | message.tool_calls[] {id, type:function, function:{name, arguments:Map}} | `message.tool_calls[].function` | |
| finishReason 归一化值 | `done_reason`（归一化词汇与 Ollama 原生重合，直接透传） | `done_reason` → normalize | 有损点 (a)：透传天然可逆 |
| usage | `prompt_eval_count` / `eval_count` | `prompt_eval_count` / `eval_count` | |
| chunk(text/reasoning, DELTA) | message {role:assistant, content/thinking} | `message.content`/`message.thinking` | |
| chunk(tool_call, ADDED) | message.tool_calls[] | `message.tool_calls` | |
| chunk(DONE) | `done_reason` | `done_reason` | |
| chunk.model | 顶层 `model` | `model` | |

### ResponsesDialect.buildResponse / buildStreamChunk

| ChatResponse / ChatStreamChunk | buildResponse 产出 | parseResponse 输入路径 | 备注 |
|---|---|---|---|
| response.id / model | `id` / `model` | `id` / `model` | |
| ChatReasoningMessage | output[] {type:reasoning, summary:[{type:summary_text, text}]} | output 循环 type==reasoning | |
| ChatAssistantMessage | output[] {type:message, role:assistant, content:[{type:output_text, text}]} | type==message | |
| ChatToolCallMessage | output[] {type:function_call, call_id, name, arguments:JSON 字符串} | type==function_call（arguments 字符串→Map） | |
| finishReason 归一化值 | `status`（stop/tool_calls→completed / length→incomplete / content_filter→failed / 未知透传） | `status` → normalize | 有损点 (a)：tool_calls→completed→stop 不可逆（样本不用 tool_calls） |
| usage | `usage {input_tokens, output_tokens, total_tokens}` | `usage.*` | |
| chunk(text, ADDED/DELTA) | response.output_item.added {item:{type:message,...}} / response.output_text.delta | 对应事件 | |
| chunk(reasoning, ADDED/DELTA) | response.output_item.added {item:{type:reasoning,...}} / response.reasoning_summary_text.delta | 对应事件 | |
| chunk(tool_call, ADDED/DELTA) | response.output_item.added {item:{type:function_call, call_id, name, arguments:""}} / response.function_call_arguments.delta | 对应事件 | |
| chunk(DONE) | response.completed {response:{id, model, status, usage}} | completed 事件 | |
| chunk(无 item 载荷) | response.created {response:{id, model}} | created 事件 | |

### 公共裁定（Phase 2）

- **OpenAI 继承 default 不覆写**（裁定落档）：default buildResponse/buildStreamChunk 即 OpenAI 原生形态
  （`chat.completion` + choices[0].message.content / `chat.completion.chunk` + delta），OpenAiDialect 零覆写。
- **无空方法体/无静默跳过**：4 dialect × 2 方法全部有真实产出逻辑；无法逆映射的字段（如 Gemini
  functionCall 无 id、tool_result 无函数名）为 Provider 格式限制，显式忽略并落档（非吞异常）。
- **reverseFinishReason/reverseStatus 未知值透传**：normalizeFinishReason 的不可逆输入（end_turn/
  max_tokens 等）不做反向还原（不可逆），roundtrip 样本只用归一化目标值（有损点 (a) 裁定）。

Status: completed
Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/`（5 个 dialect 实现 + ILlmDialect）

- Item Types: `Fix | Decision`

- [x] 语义基准核对（Decision）：对 5 个 dialect 逐一核对 `parseResponse`/`parseStreamChunk` 的产出字段结构（Anthropic: `content` blocks（text/tool_use）+ `stop_reason` + `usage`（含 `cache_read_input_tokens`/`cache_creation_input_tokens`）；Gemini: `candidates[0].content.parts` + `finishReason` + `usageMetadata`；Ollama: `message` + `done` + `eval_count`；Responses: `output` items（`message`/`function_call`）+ `status` + `usage`；OpenAI 已有 default 形态作基准）——buildResponse 产出结构必须与 parseResponse 的输入结构对齐（reverse 契约），**字段映射表落档于本 plan 文件（执行时追加附录"Phase 2 字段映射表"）**。
- [x] **roundtrip 有损点预判与处理裁定（Decision，roundtrip 测试必读）**：正反转换存在不可逆映射，断言必须按以下有损点设计，禁止为过断言而弱化为"不抛异常"：(a) **finish_reason 归一化有损**——`AbstractLlmDialect.normalizeFinishReason`（`:290-316`）将 `stop_sequence`/`end_turn`/`completed` 归一为 `"stop"`、`max_tokens`/`incomplete` 归一为 `"length"`：roundtrip 样本的 finish_reason 只取归一化目标值（`stop`/`length`），或断言归一化等价而非逐字相等（`max_tokens` 本身不可逆，不选作样本输入）；(b) **Anthropic buildBody 默认值注入**——options 无 maxTokens 时强制写 4096（`AnthropicDialect.java:90-95`）：roundtrip 断言前对 options 显式 setMaxTokens；(c) **system 折叠**——Anthropic/Gemini buildBody 仅保留最后一条 system（`:113-114`/`:97-98`）：roundtrip 样本限一条 system；请求方向同样适用（Anthropic/Gemini 顶层 `system`/`systemInstruction` 字段需折回 `ChatSystemMessage` 置于 messages 首位——Phase 1 落档）；(d) **Anthropic usage cache 字段**（`:213-226`）：usage 断言需含 cache 字段映射。各 dialect 实现 buildResponse 时同样按此有损点设计（无法逆映射的字段显式忽略并落档，非静默吞）。
- [x] 4 个 dialect（Anthropic/Gemini/Ollama/Responses）覆写 `buildResponse(ChatResponse)`：从标准 ChatResponse 产出各 Provider 原生响应 Map（含 usage 映射、finish_reason → 各 Provider 终止原因枚举的映射）。**OpenAI 裁定：继承 default 不覆写**（default 即 OpenAI 形态，`ILlmDialect.java:251-274`；零覆写 + 落档语义说明，避免"5 个覆写存在"字面冲突）。
- [x] 4 个 dialect 覆写 `buildStreamChunk(ChatStreamChunk)`：从标准 ChatStreamChunk 产出各 Provider 原生流块 Map（含 StreamItemType/StreamItemPhase → 各 Provider delta 结构映射，如 Anthropic `content_block_delta`/`message_delta`、Gemini `candidates[0].content.parts`、Ollama `message` delta、Responses `response.output_item.delta`）。**OpenAI 同上裁定：继承 default（default 即 OpenAI delta 形态，`ILlmDialect.java:281-322`）。**
- [x] `ILlmDialect` buildResponse/buildStreamChunk javadoc 更新（"Default implementation produces OpenAI format (the universal gateway format)" 表述同步为"default = OpenAI 格式；Anthropic/Gemini/Ollama/Responses 覆写产出 Provider 原生格式；OpenAI 继承 default"）；default 实现保留（OpenAI 形态 = 透传/兜底语义，非删除）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 4 个 dialect（Anthropic/Gemini/Ollama/Responses）的 `buildResponse`/`buildStreamChunk` 覆写存在 + OpenAI 继承 default 的裁定落档（default 即 OpenAI 形态），与各自 `parseResponse`/`parseStreamChunk` 输入结构对齐（映射表抽查）。
- [x] `./mvnw compile -pl :nop-ai-core -am` 通过。
- [x] **无静默跳过**：无空方法体；finish_reason/usage 等无法映射的字段有显式决策（映射或显式忽略并落档），非静默吞异常。
- [x] **Rule 25 跨 Phase 覆盖声明**：Phase 2 新增方法（4×buildResponse + 4×buildStreamChunk）的行为覆盖由 Phase 3 参数化测试显式列出（每 dialect × 响应/流式方向 + roundtrip）；本 Phase 结束时允许仅 compile 门禁，但 Phase 3 清单必须逐方法对账（closure 时核对无未覆盖方法）。
- [x] `No owner-doc update required`（文档同步在 Phase 3 统一处理）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 双向转换测试 + 零回归 + 文档同步（DLCT-04 + DLCT-03 文档部分）

Status: completed
Targets: `nop-ai/nop-ai-core/src/test/java/io/nop/ai/core/dialect/`、`ai-dev/design/nop-ai-gateway/01-architecture.md`、`ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`（§3.7 状态回填）

- Item Types: `Proof | Follow-up`

- [x] 新增双向转换测试（参数化，每 dialect × 两方向）：请求方向——`parseRequestBody(buildBody(req))` 断言 ChatRequest 关键字段语义相等（messages 角色/内容、options、tools）；响应方向——`buildResponse(parseResponse(providerSample))` 断言产出 Map 关键字段（内容、finish_reason、usage）与 providerSample 对齐、`buildStreamChunk(parseStreamChunk(providerSample))` 同理。**roundtrip 性质**：正反方向互为逆映射（字段级语义相等断言，非仅"不抛异常"）——**按 Phase 2 落档的有损点裁定设计断言**（finish_reason 用归一化可逆样本或断言归一化等价；roundtrip 请求显式 setMaxTokens；system 样本限一条；Anthropic usage 断言含 cache 字段）。
- [x] 每 dialect 至少一条 roundtrip 用例（真实 Provider 格式样本，从既有正向测试样本复用/扩展）。
- [x] **接线测试载体点名**：`AiDialectBackendMessageConverterTest` 现有用例全部 frontendLlm=openai（request 方向走既有 OpenAI parseRequestBody，不触达新实现）——**扩展该测试**：新增 frontendLlm ∈ {anthropic, gemini, ollama, responses} 的 converter 用例（request 方向经新 parseRequestBody、response 方向经新 buildResponse、stream 方向经新 buildStreamChunk），证明新实现经 `ILlmDialect` 接口分发被既有消费方在运行时可达。
- [x] 零回归：既有 dialect 正向测试（`TestOpenAiDialect`/`TestAnthropicDialect`/`TestGeminiDialect`/`TestOllamaDialect`/`TestResponsesDialect`/`TestLlmDialectFactory`/`TestLlmDialectErrorResponse` 等）全绿；`TestResponsesDialect` 行为反转用例已更新；若 `assertThrowsUOE` helper 成为无调用者死代码则顺手清理。
- [x] `01-architecture.md` 同步（DLCT-03 文档部分）：「"默认的 buildResponse/buildStreamChunk 产生 OpenAI 格式（通用网关前端格式）"」表述更新为「default = OpenAI 兜底，Anthropic/Gemini/Ollama/Responses 覆写产出 Provider 原生格式」；"前端恒 OpenAI" 相关假设（§三 converter 数据流 + 配置表 frontendLlm 默认值）修订为"前端可为任意 dialect（本需求 §3.7 偏离声明已载于 `02-account-failover-requirement.md` §3.7）」。**converter javadoc 同步**：`AiDialectBackendMessageConverter.java:23-27` "前端始终使用 OpenAI 消息结构…作为统一入口" 表述在 Phase 3 新增 frontendLlm=anthropic 等用例后失实——同步为"前端可为任意 dialect"（或至少不再断言恒 OpenAI）。
- [x] `02-account-failover-requirement.md` §3.7 落地状态回填（"本需求要求补全…" → 已补全 + 指向本 plan）；§五 Q 表相关行回填（若适用）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 双向转换参数化测试全绿（5 dialect × 请求/响应方向 + roundtrip 性质断言，用例清单见 checklist）。
- [x] **端到端验证**（Minimum Rules #22）：至少一条用例走完整转换链——Provider 原生请求体 → `parseRequestBody` → `buildBody` → 原生请求体（请求方向闭环；注意 `buildBody` 签名需 `(config, modelConfig, model, stream)` 四参，而 parseRequestBody（OpenAI 先例）丢弃 model——执行时从 body map 自取 model、传 `new LlmModel()`/null/false，落档该细节）；Provider 原生响应 → `parseResponse` → `buildResponse` → 原生响应（响应方向闭环）；Provider 原生流块 → `parseStreamChunk` → `buildStreamChunk` → 原生流块（流式方向闭环）。
- [x] **接线验证**（Minimum Rules #23）：新实现经 `ILlmDialect` 接口分发被既有消费方（`AiDialectBackendMessageConverter` 非流式路径 parseRequestBody/buildResponse/buildStreamChunk 调用点）可达——测试断言 + 代码路径实证。
- [x] `./mvnw test -pl :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -T 1C` BUILD SUCCESS（mission 三模块验证基线），既有测试零回归。
- [x] `01-architecture.md` + `02-account-failover-requirement.md` §3.7 已同步（grep 抽查无 "前端恒 OpenAI"/"one-way" 残留表述）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Rule 25 逐方法覆盖对账（closure 用）

| 新增方法 | 覆盖测试（方法名） |
|---|---|
| AnthropicDialect.parseRequestBody | `TestDialectBidirectionalConversion.testRequestRoundtrip_anthropic` + `testE2E_anthropicRequestClosedLoop` + `AiDialectBackendMessageConverterTest.toBackendRequest_anthropicToOpenAI`（接线） |
| GeminiDialect.parseRequestBody | `testRequestRoundtrip_gemini` + `testE2E_geminiRequestClosedLoop` + `toBackendRequest_geminiToOpenAI`（接线） |
| OllamaDialect.parseRequestBody | `testRequestRoundtrip_ollama` + `testE2E_ollamaRequestClosedLoop` + `toBackendRequest_ollamaToOpenAI`（接线） |
| ResponsesDialect.parseRequestBody | `testRequestRoundtrip_responses` + `testE2E_responsesRequestClosedLoop` + `TestResponsesDialect.testParseRequestBodyPlainText/testParseRequestBodyTypedItems` + `toBackendRequest_responsesToOpenAI`（接线） |
| AnthropicDialect.buildResponse | `testE2E_anthropicResponseClosedLoop` + `toFrontendResponse_openaiToAnthropic`（接线） |
| GeminiDialect.buildResponse | `testE2E_geminiResponseClosedLoop` + `toFrontendResponse_openaiToGemini`（接线） |
| OllamaDialect.buildResponse | `testE2E_ollamaResponseClosedLoop` + `testE2E_ollamaResponseToolCallClosedLoop` + `toFrontendResponse_openaiToOllama`（接线） |
| ResponsesDialect.buildResponse | `testE2E_responsesResponseClosedLoop` + `toFrontendResponse_openaiToResponses`（接线） |
| AnthropicDialect.buildStreamChunk | `testE2E_anthropicStreamClosedLoop`（text DELTA/tool ADDED/DONE 三分支）+ `toFrontendStreamChunk_openaiToAnthropic`（接线） |
| GeminiDialect.buildStreamChunk | `testE2E_geminiStreamClosedLoop`（text/tool ADDED/DONE）+ `toFrontendStreamChunk_openaiToGemini`（接线） |
| OllamaDialect.buildStreamChunk | `testE2E_ollamaStreamClosedLoop`（text/tool ADDED/DONE）+ `toFrontendStreamChunk_openaiToOllama`（接线） |
| ResponsesDialect.buildStreamChunk | `testE2E_responsesStreamClosedLoop`（text DELTA/tool ADDED/DONE）+ `toFrontendStreamChunk_openaiToResponses`（接线） |

> 覆盖声明：12 个新增方法全部有直接行为测试 + roundtrip 性质断言；接线验证经 converter 测试
> （request/response/stream 三调用点 × 4 新 dialect frontendLlm）运行时实证。无未覆盖方法。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。
>
> 关闭流程详见 `00-plan-authoring-and-execution-guide.md` 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] 5 个 dialect 双向转换全部落地（parseRequestBody：OpenAI 既有 + 4 新实现；buildResponse/buildStreamChunk：4 个覆写 + OpenAI 继承 default 裁定落档），无 "one-way" 残留。
- [x] 双向转换参数化测试 + roundtrip + 接线验证全绿；既有 dialect 测试零回归。
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项。
- [x] 受影响的 owner docs（`01-architecture.md` / `02-account-failover-requirement.md` §3.7）已同步到 live baseline。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（audit 验证：字段映射与各 dialect buildBody/parseResponse 对齐、行为反转测试已更新、零回归、文档同步）。
- [x] **Anti-Hollow Check**：closure audit 已验证（a）新实现被 converter 接口分发在运行时可达（非仅类型存在），（b）无空方法体/静默跳过/no-op 作为正常实现。
- [x] `./mvnw compile -pl :nop-ai-core -am`
- [x] `./mvnw test -pl :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -T 1C`
- [x] checkstyle / 代码规范检查通过（或按 mission 既有 lint 兜底通道判定——参考 W2/W3 记录：无有效 checkstyle 门禁，以 compile/test + grep 零残留为准）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（关闭时执行）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（文档变更后执行）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-core --severity high` 退出码 0（关闭时执行——新覆写方法无运行时调用方属空壳高危面，接线验证为其解除证据）

## Deferred But Adjudicated

无（本计划全部工作项 in-scope 落地；W7 的 converter 动态 dialect/流式扩展为 roadmap 显式归 W7 的 out-of-scope 工作，非本计划残余）。

## Non-Blocking Follow-ups

- 无（双向转换完成即本计划收口；gateway 形态消费在 W7）。

## Closure

Status Note: 5 dialect 双向转换全部落地并闭环。parseRequestBody：Anthropic/Gemini/Ollama/Responses 4 新实现（OpenAI 既有）；buildResponse/buildStreamChunk：4 覆写产出 Provider 原生格式 + OpenAI 继承 default（裁定落档）；javadoc/UOE 同步；20 参数化双向转换测试（5 dialect × 请求/响应/流式方向 + roundtrip + E2E 闭环）+ 12 converter 接线测试（frontendLlm 4 dialect × 三调用点）全绿；既有 dialect 测试零回归；01-architecture.md + 02-account-failover-requirement.md §3.7 + converter javadoc 已同步。独立 closure audit closure-approve。
Completed: 2026-08-15

Closure Audit Evidence:

- Reviewer / Agent: fresh-session closure auditor（general subagent task `ses_ffce19bdfffeWWPyzy6ihdUtDT`，独立启动于 W4 执行完成之后）
- Evidence:
  - Phase 1/2/3 全部 item + Exit Criteria 勾选、Status 全 completed（PASS）；Closure Gates 关闭前未勾选为预期状态（本 audit 解锁）
  - parseRequestBody 4 实现实证：AnthropicDialect.java:69 / GeminiDialect.java:73 / OllamaDialect.java:64 / ResponsesDialect.java:65，与各 buildBody 字段映射表逐字段对齐；无空方法体；Anthropic tool_use input 畸形经 parseToolInput fail-fast（:132）
  - buildResponse/buildStreamChunk 覆写实证：Anthropic:670/:754、Gemini:613/:692、Ollama:425/:495、Responses:577/:657；OpenAiDialect 零覆写继承 default（grep 0 匹配）
  - `rg "one-way OpenAI|only implemented for OpenAI"` 于 nop-ai src/main 0 命中；ILlmDialect.java:233-244 javadoc + UOE 通用消息；docs 中仅历史引用（明确标注已落地/已放开）
  - 行为反转：TestResponsesDialect 旧 UOE 用例删除，替换为 testParseRequestBodyPlainText(:349)/testParseRequestBodyTypedItems(:370)；assertThrowsUOE 死代码已删（rg 0 命中）
  - 测试覆盖：TestDialectBidirectionalConversion 20 用例（5 request roundtrip + 4 request E2E + 6 response E2E + 5 stream E2E），字段级断言抽查 PASS（anthropic request/response、responses response 三用例实读）
  - 接线验证：AiDialectBackendMessageConverterTest 12 新用例（frontendLlm 4 dialect × toBackendRequest/toFrontendResponse/toFrontendStreamChunk），运行时经 LlmDialectFactory.getDialect 分发（converter :48-49/:68-69/:78-79），Anti-Hollow 解除证据
  - 零回归：既有 dialect 测试文件 git diff 零改动（仅 TestResponsesDialect 计划内反转）；converter 测试 diff 261 增 0 删
  - 文档同步：01-architecture.md 状态块 + 配置表；02 §3.7 落地回填 :166-174；converter javadoc :26-28；`rg "前端恒|恒为|恒使用|恒 OpenAI|恒产生"` 0 命中
  - 构建：`./mvnw test -pl :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -T 1C` BUILD SUCCESS（core 321 + gateway 96 + agent 3387，0 failures）；`check-plan-checklist.mjs --strict` exit 0；`check-doc-links.mjs --strict` exit 0（4 warnings 均为 W5 plan 预存，非 W4）；`scan-hollow-implementations.mjs --module nop-ai-core --severity high` 0 findings
  - Deferred 分类诚实：Deferred/Follow-up 均无（W7 converter 扩展为显式 out-of-scope）
  - Verdict: **closure-approve**，无失败项

Follow-up:

- 无（双向转换完成即本计划收口；gateway 形态消费在 W7，为 roadmap 显式 out-of-scope）
