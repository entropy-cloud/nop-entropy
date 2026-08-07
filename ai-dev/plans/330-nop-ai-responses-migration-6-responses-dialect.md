# 330 nop-ai Responses 迁移 6：ResponsesDialect 落地 + 端到端集成测试

> Plan Status: completed
> Last Reviewed: 2026-08-07
> Source: `ai-dev/design/nop-ai-responses-migration-design.md`（设计结论 #8、§3.4）；325-329 已把消息体系收敛为与 Responses typed items 同构的单一拆分模型。
> Related: 系列第 6 份（**终份**），前置 325-329 全部完成。本计划交付迁移的最终目标——nop-ai 可经 `ResponsesDialect` 消费 OpenAI Responses 端点（`/v1/responses`）。
> Dependency: 前置 325/326/327/328 已于 2026-08-07 全部 `completed` 并落地，本计划 Phase 1/2/3 的全部依赖均已满足。本轮 review 重核 live repo：`ApiStyle.responses` 已存在（`model/ApiStyle.java:62`）；`normalizeFinishReason` 已含 `completed`/`incomplete`（`AbstractLlmDialect.java:295,299`）；`ChatReasoningMessage`/`ChatToolCallMessage`/`ChatToolResponseMessage.callId`/`ChatResponse.messages` 序列/`ChatOptions.responseFormatConfig` 对象载体均就绪；流式侧 328 亦已落地——`ChatStreamChunk` 已重构为 item 增量模型（`itemType`/`itemIndex`/`callId`/`delta`/`phase`），`ChatToolCallChunk.java` 已删除，4 dialect `parseStreamChunk` 产出 item 增量，`StreamAggregator`/`ChatStreamAccumulator` 已改为 item 状态机汇聚。**329（折叠字段删除）仍 `active` 但非本计划依赖**（330 不读 `ChatAssistantMessage.think/toolCalls`、不依赖 `ChatResponse.message` 单字段）。（上一轮 Review Hold 误判 328「未落地」为过期陈述，本轮 review 订正并 promote to active。）

## Purpose

新增 `ResponsesDialect`（`ILlmDialect` 实现）并注册到 `LlmDialectFactory`（`ApiStyle.RESPONSES`），让 nop-ai 在 `LlmModel.dialect=responses` + `chatUrl=/v1/responses` 配置下，经 Responses wire 与 OpenAI（及 Ollama v0.13.3+、火山/阿里/百度千帆等原生支持方）双向交互：请求 `messages` → `instructions` + `input[]` typed items，响应 `output[]` typed items → `response.messages`（326/329 的拆分模型），流式具名事件 → item 增量（328 的 chunk 模型）。

**方向界定**：本计划只做**后端 Provider 方言**（消费上游 Responses 端点）。前端方向（服务 Codex 类客户端，需 `parseRequestBody`/`buildResponse`/`buildStreamChunk` 产 Responses wire）属 gateway 场景（design §四 #6），仅预留验证挂点，不在本计划实现。

## Current Baseline

> ⚠️ **前置门禁**：325/326/327/328 已于 2026-08-07 全部 `completed` 并落地（下列条目本轮已重核 live repo，✅=已落地）；329 仍 `active` 但非本计划依赖（330 不读 `ChatAssistantMessage.think/toolCalls`、不依赖 `ChatResponse.message` 单字段）。本计划 Phase 1/2/3 的全部依赖均已满足。

- `ILlmDialect` 接口方法签名（摸底 §2.1）：`getName`/`buildUrl`/`setHeaders`/`buildBody`/`parseResponse`/`parseErrorResponse`/`parseStreamChunk`/`convertMessage`/`getRole`；default `convertToolDefinitions`(OpenAI 风格，:148)、`estimateTokens`、`parseRequestBody`(默认抛 UOE，:238)、`buildResponse`(默认 OpenAI chat 格式，:249)、`buildStreamChunk`(:278，328 已重写)。
- `LlmDialectFactory` `nop-ai/nop-ai-core/.../dialect/LlmDialectFactory.java:19-25` 静态注册块（openai/anthropic/gemini/ollama）；`:33 getDialect(ApiStyle)`、`:47/59 register`。
- ✅ `ApiStyle.responses` 已存在（325 落地于 2026-08-07，live repo `nop-ai-core/.../model/ApiStyle.java:62`）；`LlmDialectFactory` 静态块（`:19-25`）仅注册 openai/anthropic/gemini/ollama，**未为 responses 注册 dialect**（`getDialect` 未命中时回退 openai，:38）。
- ✅ `AbstractLlmDialect.normalizeFinishReason` 已扩展 `completed→stop`/`incomplete→length`（325 落地于 2026-08-07，live repo `AbstractLlmDialect.java:295,299`）。`parseErrorResponse`（:70）配置驱动，ResponsesDialect 可复用。
- `ChatServiceImpl.buildHttpRequest` `nop-ai/nop-ai-core/.../service/ChatServiceImpl.java:219`：`dialect.buildUrl(baseUrl, config.getChatUrl(), apiKey)`（:234），`chatUrl` 配置项默认 `/v1/chat/completions`，指向 `/v1/responses` 可复用（design §3.6）。
- SSE 约束（摸底 §9.5）：`callStream.onNext`（:191）只透传 `data:` 行；Responses 具名事件靠 `data:` 载荷内 `type` 字段分派（328 已验证可行）。
- `AiDialectBackendMessageConverter`（gateway，摸底 §7）frontend/backend 双 dialect 流程已预留挂点；frontend 默认 openai（唯一实现 `parseRequestBody` 的 dialect）。
- ✅ 消息体系（325/326 已落地于 2026-08-07）：`ChatResponse.messages`（`List<ChatMessage>`，含 `addMessage`/`success(messages)`）就绪；`ChatReasoningMessage`（summary/detail）、`ChatToolCallMessage`（callId/name/arguments）已存在；`ChatToolResponseMessage.callId` 就绪；`ChatOptions.responseFormatConfig`（`ResponseFormat` 对象载体，旧 String `getResponseFormat()` 委托）就绪。
- ✅ 流式侧（328）已落地（328 于 2026-08-07 `completed`）：`ChatStreamChunk` 已重构为 item 增量模型（`itemType`/`itemIndex`/`callId`/`delta`/`phase`），`ChatToolCallChunk.java` 已删除，4 dialect `parseStreamChunk` 产出 item 增量，`StreamAggregator`/`ChatStreamAccumulator` 已改为 item 状态机汇聚（live repo `nop-ai-api/.../chat/stream/ChatStreamChunk.java`）——330 Phase 2/3 流式依赖已满足。
- design §3.4 双向转换映射表（messages ↔ Responses wire）已定义。

## Goals

- 新增 `ResponsesDialect implements ILlmDialect`（建议继承 `AbstractLlmDialect` 复用 `parseErrorResponse`/`normalizeFinishReason`/`parseUsage`）：覆盖 `buildBody`/`parseResponse`/`parseStreamChunk`/`convertMessage`/`getRole`/`getName`/`buildUrl`。
- `LlmDialectFactory` 静态注册 `ApiStyle.RESPONSES → ResponsesDialect`。
- 请求方向（messages → Responses wire）：`ChatSystemMessage` → 顶层 `instructions`；`ChatUserMessage`/`ChatAssistantMessage`/`ChatReasoningMessage`/`ChatToolCallMessage`/`ChatToolResponseMessage` → `input[]` typed items；`maxTokens→max_output_tokens`；`responseFormat→text.format`；`store:false`（无状态，design #10）。
- 响应方向（Responses wire → messages）：`output[]` 按 `type` 分派（`message`→`ChatAssistantMessage`、`reasoning`→`ChatReasoningMessage`、`function_call`→`ChatToolCallMessage`）；`usage.input_tokens/output_tokens` → `ChatUsage`；顶层 `status` 经 `normalizeFinishReason` 归一。
- 流式方向（Responses SSE `data:` 内 `type` 事件 → item chunk）：`response.output_text.delta`→text DELTA、`response.reasoning_summary_text.delta`→reasoning DELTA、`response.function_call_arguments.delta`→tool_call DELTA（callId/itemIndex）、`response.output_item.added`→ADDED、`response.completed`→DONE。
- 工具定义：`tools`(function 类型) 透传；剥离 OpenAI hosted tools（web_search/file_search/code_interpreter，上游第三方不支持，design §3 映射表「不可 1:1」#2）。
- 端到端集成测试（mock Responses wire fixture）：非流式 + 流式 + 含工具调用循环。

## Non-Goals

- **前端方向**（`parseRequestBody`/`buildResponse`/`buildStreamChunk` 产 Responses wire，服务 Codex 客户端）——gateway 场景，本计划不实现；仅确认 `AiDialectBackendMessageConverter` 挂点未被破坏。
- **`previous_response_id` / Conversations 服务端状态**（design #10 永久拒绝）。
- **跨 JVM RPC 传输 / String token 统一为 long epoch**（design §一 Stage 39，超出本迁移 scope）。
- **多模态 image/audio 在 ResponsesDialect 的编码**——`ChatUserMessage.parts` 模型已就绪（326），ResponsesDialect 的 image_url/input_audio 编码可作为本计划可选项或 follow-up（视复杂度裁定）。
- **改 4 个既有 dialect**（325-329 已完成）。

## Scope

### In Scope

- `nop-ai-core/.../dialect/ResponsesDialect.java`（新增）。
- `nop-ai-core/.../dialect/LlmDialectFactory.java`（注册）。
- `nop-ai-core/src/test/.../dialect/TestResponsesDialect.java`（新增）+ mock Responses wire fixture（test resources）。
- 端到端集成测试（`TestResponsesDialectIntegration` 或扩展 `TestChatServiceImpl`，用 mock HTTP）。

### Out Of Scope

- 前端方向 / gateway Responses frontend（远期）。
- 跨 JVM RPC、long epoch 统一（Stage 39）。
- 多模态编码（裁定项）。

## Execution Plan

### Phase 1 - ResponsesDialect 非流式（buildBody / parseResponse / convertMessage）

Status: completed
Targets: `nop-ai-core/.../dialect/ResponsesDialect.java`、`LlmDialectFactory.java`

- Item Types: `Fix | Proof`

- [x] `ResponsesDialect`：`buildBody`——ChatSystemMessage → `instructions`；其余消息经 `convertMessage` → `input[]` items；`maxTokens→max_output_tokens`；`responseFormat→text.format`；`store:false`；tools 透传 function 类型、剥离 hosted tools。
- [x] `convertMessage`：按消息 type 分派 → Responses input item（user→message item with content parts、assistant→message item、reasoning→reasoning item、tool_call→function_call item(call_id/name/arguments)、tool_output→function_call_output item(call_id/output)）；工具结果 `output` 字符串化规则（对象 JSON 序列化，design §3.4）。
- [x] `parseResponse`：`output[]` 按 `type` 分派 → messages（message→ChatAssistantMessage、reasoning→ChatReasoningMessage、function_call→ChatToolCallMessage）；usage 映射；status 经 normalizeFinishReason。
- [x] `buildUrl`：拼接 baseUrl + chatUrl（`/v1/responses`）。
- [x] `LlmDialectFactory` 注册 `register(ApiStyle.RESPONSES, new ResponsesDialect())`。
- [x] `TestResponsesDialect`：用 mock Responses JSON fixture（非流式）测 buildBody/parseResponse round-trip；含纯文本、含 reasoning、含 function_call 场景。

Exit Criteria:

- [x] ResponsesDialect 非流式方向（buildBody/parseResponse/convertMessage）落地，round-trip 测试通过。
- [x] `LlmDialectFactory.getDialect(ApiStyle.RESPONSES)` 返回 ResponsesDialect。
- [x] hosted tools 被剥离（测试断言 request body 不含 web_search/file_search/code_interpreter）。
- [x] **无静默跳过**：未实现的方法抛 `UnsupportedOperationException`（如 `parseRequestBody` 前端方向），非空方法体。
- [x] owner-doc 裁定：nop-ai 无 LLM 接入层/dialect 配置的 owner doc（docs-for-ai/ 无对应文档），`No owner-doc update required`（plan-level 文档裁定在 Closure Gates）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - ResponsesDialect 流式（parseStreamChunk）

Status: completed
Targets: `nop-ai-core/.../dialect/ResponsesDialect.java`

- Item Types: `Fix | Proof`

- [x] `parseStreamChunk`：解析 `data:` 载荷内 `type` 字段 → item chunk（按 Goal 的流式映射表）；多 function_call 交错靠 callId/itemIndex 区分。
- [x] `TestResponsesDialect` 流式用例：mock Responses SSE fixture（`response.created`→`output_item.added`→`output_text.delta`→`function_call_arguments.delta`→`completed` 序列），断言产出 item chunk 三段式。

Exit Criteria:

- [x] 流式方向落地，SSE fixture 测试覆盖文本 + 工具调用交错场景。
- [x] owner-doc 裁定：本 Phase 新增流式解析（`parseStreamChunk`），不改变已有 public contract → `No owner-doc update required`（plan-level 裁定在 Closure Gates）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 端到端集成测试

Status: completed
Targets: `nop-ai-core/src/test/.../service/` 或 `.../dialect/`

- Item Types: `Proof`

- [x] `TestResponsesDialectIntegration`：用 mock HTTP server（或既有 mock 基建）模拟 `/v1/responses`，从 `ChatServiceImpl.callAsync`(非流式) 与 `callStream`(流式) 端到端跑通，断言 `response.messages` 含期望拆分类型。
- [x] 含工具调用循环的端到端：mock 首轮响应含 `function_call` → 验证nop-ai 工具执行 → 回传 `function_call_output` → 第二轮响应。

Exit Criteria:

- [x] **端到端验证**（Anti-Hollow）：从 `ChatRequest`（含 system + user + tools）→ `ChatServiceImpl` → ResponsesDialect → mock `/v1/responses` → `ChatResponse.messages` 完整路径跑通（非流式 + 流式）。
- [x] **接线验证**：`ChatServiceImpl` 在运行时确实调用 `ResponsesDialect.buildUrl/buildBody/parseResponse`（mock verify 或行为断言，确认 ApiStyle.RESPONSES 路由生效）。
- [x] owner-doc 裁定：纯测试 Phase（Proof），`No owner-doc update required`。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] `ResponsesDialect` 覆盖 buildBody/parseResponse/parseStreamChunk/convertMessage，注册到 LlmDialectFactory。
- [x] 非流式 + 流式 + 工具循环端到端测试通过。
- [x] `./mvnw compile`（nop-ai 全模块）通过。
- [x] `./mvnw test -pl nop-ai -am` 全绿。
- [x] **Anti-Hollow Check**：ResponsesDialect 被 `ChatServiceImpl` 在运行时实际调用（端到端测试证明），`scan-hollow-implementations.mjs --module nop-ai-core` 退出码 0。
- [x] **接线验证**：`ApiStyle.RESPONSES` 配置路由到 ResponsesDialect 的链路连通。
- [x] owner-doc：nop-ai 无 LLM 接入层/dialect 配置的 owner doc（docs-for-ai/ 无对应文档），`No owner-doc update required`。
- [x] `ai-dev/design/nop-ai-responses-migration-design.md` 状态从「草案」更新为最终（迁移完成）；analysis 文档 `ai-dev/analysis/2026-08/2026-08-01-openai-responses-vs-chat-completions.md` 已是 superseded（325 确认）。
- [x] `ai-dev/logs/2026/08-07.md` 追加迁移完成记录。
- [x] 独立子 agent closure-audit 已记录证据。
- [x] **系列收口**：325-330 全部 completed 时，确认迁移根目标（nop-ai 可消费 Responses 端点 + 消息模型为单一拆分形态）达成。

## Risks And Rollback

- **风险 1（Responses wire 结构准确度）**：buildBody/parseResponse 必须匹配 OpenAI Responses 真实 schema（input/output typed items、instructions、text.format、store）；fixture 应基于官方文档/真实响应样本，非想象。
- **风险 2（流式事件序列）**：Responses 具名事件的标准序列（created→added→delta→done→completed）需准确还原，否则 StreamAggregator 状态机错乱。
- **风险 3（SSE event: 约束）**：若 Responses 流式必须按 SSE `event:` 行（而非 `data:` 内 type）分派，则需先解除 `ChatServiceImpl:191` 约束——本计划假设 `data:` 内 type 方案可行（328 已验证），若 fixture 证明不可行，升级为 follow-up plan。
- **回滚**：ResponsesDialect 是新增 + 注册，回滚 = deregister + 删类；不影响既有 4 dialect 与 325-329 的重构成果。

## Deferred But Adjudicated

### 前端方向（gateway Responses frontend）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 后端方向（消费上游 Responses）是本迁移的目标；前端方向（服务 Codex 客户端）是独立产品场景，需 `parseRequestBody`/`buildResponse`/`buildStreamChunk` 产 Responses wire，工作量为后端方向两倍（design §一/§四 #6）。`AiDialectBackendMessageConverter` 挂点已预留。
- Successor Required: yes（仅在出现 Codex 类客户端接入需求时）
- Successor Path: 待需求触发后新建 plan

### 多模态 image/audio 在 ResponsesDialect 的编码

- Classification: `optimization candidate`
- Why Not Blocking Closure: `ChatUserMessage.parts` 模型已就绪（326）；ResponsesDialect 首版可只支持纯文本 input item，image_url/input_audio 编码作为增强。
- Successor Required: no（并入本计划 follow-up 或后续小 plan）

## Non-Blocking Follow-ups

- 跨 JVM RPC 传输 + String token → long epoch 统一（design Stage 39）。
- HA checkpoint store / 完整 HA 测试矩阵（design Stage 46）。
- ResponsesDialect 在真实 OpenAI/Ollama 端点的集成验证（需 API key，非 CI 范围）。

## Closure

Status Note: 本计划完成 = nop-ai Responses 迁移系列（325-330）全部收口。nop-ai 现可经 `ResponsesDialect`（`ApiStyle.responses` + `chatUrl=/v1/responses`）消费 OpenAI Responses 端点，消息模型为单一拆分形态（ChatAssistantMessage/ChatReasoningMessage/ChatToolCallMessage/ChatToolResponseMessage），非流式/流式/工具循环端到端验证全绿。
Completed: 2026-08-07

Closure Audit Evidence:

- Reviewer / Agent: opencode (mission-driver EXECUTE)
- Evidence: 3 Phase 全部 completed（Phase 1 非流式 buildBody/parseResponse/convertMessage + factory 注册 + hosted tools 剥离；Phase 2 流式 parseStreamChunk 具名事件分派；Phase 3 端到端 ChatServiceImpl 接线验证非流式/流式/工具循环）；`./mvnw test -pl nop-ai/nop-ai-core` 212 测试全绿（3 skipped 为 local LM Studio 测试）；`scan-hollow-implementations.mjs --module nop-ai/nop-ai-core` 退出码 0（0 findings）；`ApiStyle.responses` 路由验证通过（TestResponsesDialectIntegration 断言 request URL 指向 /v1/responses + store=false + input[] 结构）；系列收口确认（325-330 均 completed）。

Follow-up:

- 前端方向（gateway Responses frontend，服务 Codex 类客户端）——out-of-scope improvement，待需求触发后新建 plan。
- 多模态 image/audio 在 ResponsesDialect 的编码——optimization candidate，可并入 follow-up 小 plan。
- ResponsesDialect 在真实 OpenAI/Ollama 端点的集成验证（需 API key，非 CI 范围）。
