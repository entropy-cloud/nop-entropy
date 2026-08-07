# 330 nop-ai Responses 迁移 6：ResponsesDialect 落地 + 端到端集成测试

> Plan Status: draft
> Last Reviewed: 2026-08-06
> Source: `ai-dev/design/nop-ai-responses-migration-design.md`（设计结论 #8、§3.4）；325-329 已把消息体系收敛为与 Responses typed items 同构的单一拆分模型。
> Related: 系列第 6 份（**终份**），前置 325-329 全部完成。本计划交付迁移的最终目标——nop-ai 可经 `ResponsesDialect` 消费 OpenAI Responses 端点（`/v1/responses`）。
> Review Hold: 前置计划 325/326/327 仍为 `active`、328/329 仍为 `draft`，**无一 completed**。本计划 Current Baseline 中多处「已存在/已扩展/已落地」项实际是 325-329 的待交付物（经 live repo 核对：`ApiStyle` 无 `responses`、`normalizeFinishReason` 无 `completed`/`incomplete`、`ChatReasoningMessage`/`ChatToolCallMessage` 类不存在——均属 325 计划范畴）。基线文本已修正为「待前置落地」，但计划本身在 325-329 completed 前不可执行、不可对 live repo 验证。下一轮 review（前置完成后）再决定是否 promote to active。

## Purpose

新增 `ResponsesDialect`（`ILlmDialect` 实现）并注册到 `LlmDialectFactory`（`ApiStyle.RESPONSES`），让 nop-ai 在 `LlmModel.dialect=responses` + `chatUrl=/v1/responses` 配置下，经 Responses wire 与 OpenAI（及 Ollama v0.13.3+、火山/阿里/百度千帆等原生支持方）双向交互：请求 `messages` → `instructions` + `input[]` typed items，响应 `output[]` typed items → `response.messages`（326/329 的拆分模型），流式具名事件 → item 增量（328 的 chunk 模型）。

**方向界定**：本计划只做**后端 Provider 方言**（消费上游 Responses 端点）。前端方向（服务 Codex 类客户端，需 `parseRequestBody`/`buildResponse`/`buildStreamChunk` 产 Responses wire）属 gateway 场景（design §四 #6），仅预留验证挂点，不在本计划实现。

## Current Baseline

> ⚠️ **前置门禁**：以下凡标注「（325/326/... 待落地）」的条目均为兄弟计划的待交付物，**当前 live repo 尚未包含**（核对于 2026-08-06）。本计划须在 325-329 全部 `completed` 后方可执行；执行前须重核本节每一条与 live repo 一致。

- `ILlmDialect` 接口方法签名（摸底 §2.1）：`getName`/`buildUrl`/`setHeaders`/`buildBody`/`parseResponse`/`parseErrorResponse`/`parseStreamChunk`/`convertMessage`/`getRole`；default `convertToolDefinitions`(OpenAI 风格，:148)、`estimateTokens`、`parseRequestBody`(默认抛 UOE，:238)、`buildResponse`(默认 OpenAI chat 格式，:249)、`buildStreamChunk`(:278，328 已重写)。
- `LlmDialectFactory` `nop-ai/nop-ai-core/.../dialect/LlmDialectFactory.java:19-25` 静态注册块（openai/anthropic/gemini/ollama）；`:33 getDialect(ApiStyle)`、`:47/59 register`。
- `ApiStyle.RESPONSES` 已存在（325 落地，未注册 dialect）→ **（325 待落地）** live repo `ApiStyle.java:21-52` 当前仅 openai/ollama/anthropic/gemini/other，**无 responses 枚举值**。
- `AbstractLlmDialect.normalizeFinishReason` 已扩展 `completed→stop`/`incomplete→length`（325）；→ **（325 待落地）** live repo `AbstractLlmDialect.java:285-309` 当前只认 stop/end_turn/length/max_tokens/content_filter/tool_calls 等，**不含 `completed`/`incomplete`**。`parseErrorResponse`（:70）配置驱动，ResponsesDialect 可复用。
- `ChatServiceImpl.buildHttpRequest` `nop-ai/nop-ai-core/.../service/ChatServiceImpl.java:219`：`dialect.buildUrl(baseUrl, config.getChatUrl(), apiKey)`（:234），`chatUrl` 配置项默认 `/v1/chat/completions`，指向 `/v1/responses` 可复用（design §3.6）。
- SSE 约束（摸底 §9.5）：`callStream.onNext`（:191）只透传 `data:` 行；Responses 具名事件靠 `data:` 载荷内 `type` 字段分派（328 已验证可行）。
- `AiDialectBackendMessageConverter`（gateway，摸底 §7）frontend/backend 双 dialect 流程已预留挂点；frontend 默认 openai（唯一实现 `parseRequestBody` 的 dialect）。
- 消息体系（325-329 终态）：`ChatResponse.messages` 含 `ChatAssistantMessage`/`ChatReasoningMessage`/`ChatToolCallMessage`；`ChatOptions.responseFormat` 对象载体；`ChatToolResponseMessage.callId`。→ **（325-329 待落地）** live repo `nop-ai-api/.../messages/` 当前仅有 ChatAssistantMessage/ChatSystemMessage/ChatUserMessage/ChatToolResponseMessage/ChatCustomMessage/ChatToolCall/ChatUsage/ChatAttachment/ChatMessage，**`ChatReasoningMessage`/`ChatToolCallMessage` 不存在**；`ChatResponse.messages` 列表与 `ChatToolResponseMessage.callId` 字段亦未落地（属 326/325 范畴）。
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

Status: planned
Targets: `nop-ai-core/.../dialect/ResponsesDialect.java`、`LlmDialectFactory.java`

- Item Types: `Fix | Proof`

- [ ] `ResponsesDialect`：`buildBody`——ChatSystemMessage → `instructions`；其余消息经 `convertMessage` → `input[]` items；`maxTokens→max_output_tokens`；`responseFormat→text.format`；`store:false`；tools 透传 function 类型、剥离 hosted tools。
- [ ] `convertMessage`：按消息 type 分派 → Responses input item（user→message item with content parts、assistant→message item、reasoning→reasoning item、tool_call→function_call item(call_id/name/arguments)、tool_output→function_call_output item(call_id/output)）；工具结果 `output` 字符串化规则（对象 JSON 序列化，design §3.4）。
- [ ] `parseResponse`：`output[]` 按 `type` 分派 → messages（message→ChatAssistantMessage、reasoning→ChatReasoningMessage、function_call→ChatToolCallMessage）；usage 映射；status 经 normalizeFinishReason。
- [ ] `buildUrl`：拼接 baseUrl + chatUrl（`/v1/responses`）。
- [ ] `LlmDialectFactory` 注册 `register(ApiStyle.RESPONSES, new ResponsesDialect())`。
- [ ] `TestResponsesDialect`：用 mock Responses JSON fixture（非流式）测 buildBody/parseResponse round-trip；含纯文本、含 reasoning、含 function_call 场景。

Exit Criteria:

- [ ] ResponsesDialect 非流式方向（buildBody/parseResponse/convertMessage）落地，round-trip 测试通过。
- [ ] `LlmDialectFactory.getDialect(ApiStyle.RESPONSES)` 返回 ResponsesDialect。
- [ ] hosted tools 被剥离（测试断言 request body 不含 web_search/file_search/code_interpreter）。
- [ ] **无静默跳过**：未实现的方法抛 `UnsupportedOperationException`（如 `parseRequestBody` 前端方向），非空方法体。

### Phase 2 - ResponsesDialect 流式（parseStreamChunk）

Status: planned
Targets: `nop-ai-core/.../dialect/ResponsesDialect.java`

- Item Types: `Fix | Proof`

- [ ] `parseStreamChunk`：解析 `data:` 载荷内 `type` 字段 → item chunk（按 Goal 的流式映射表）；多 function_call 交错靠 callId/itemIndex 区分。
- [ ] `TestResponsesDialect` 流式用例：mock Responses SSE fixture（`response.created`→`output_item.added`→`output_text.delta`→`function_call_arguments.delta`→`completed` 序列），断言产出 item chunk 三段式。

Exit Criteria:

- [ ] 流式方向落地，SSE fixture 测试覆盖文本 + 工具调用交错场景。

### Phase 3 - 端到端集成测试

Status: planned
Targets: `nop-ai-core/src/test/.../service/` 或 `.../dialect/`

- Item Types: `Proof`

- [ ] `TestResponsesDialectIntegration`：用 mock HTTP server（或既有 mock 基建）模拟 `/v1/responses`，从 `ChatServiceImpl.callAsync`(非流式) 与 `callStream`(流式) 端到端跑通，断言 `response.messages` 含期望拆分类型。
- [ ] 含工具调用循环的端到端：mock 首轮响应含 `function_call` → 验证nop-ai 工具执行 → 回传 `function_call_output` → 第二轮响应。

Exit Criteria:

- [ ] **端到端验证**（Anti-Hollow）：从 `ChatRequest`（含 system + user + tools）→ `ChatServiceImpl` → ResponsesDialect → mock `/v1/responses` → `ChatResponse.messages` 完整路径跑通（非流式 + 流式）。
- [ ] **接线验证**：`ChatServiceImpl` 在运行时确实调用 `ResponsesDialect.buildUrl/buildBody/parseResponse`（mock verify 或行为断言，确认 ApiStyle.RESPONSES 路由生效）。

## Closure Gates

- [ ] `ResponsesDialect` 覆盖 buildBody/parseResponse/parseStreamChunk/convertMessage，注册到 LlmDialectFactory。
- [ ] 非流式 + 流式 + 工具循环端到端测试通过。
- [ ] `./mvnw compile`（nop-ai 全模块）通过。
- [ ] `./mvnw test -pl nop-ai -am` 全绿。
- [ ] **Anti-Hollow Check**：ResponsesDialect 被 `ChatServiceImpl` 在运行时实际调用（端到端测试证明），`scan-hollow-implementations.mjs --module nop-ai-core` 退出码 0。
- [ ] **接线验证**：`ApiStyle.RESPONSES` 配置路由到 ResponsesDialect 的链路连通。
- [ ] owner-doc：`docs-for-ai/` 若有 LLM 接入层文档（dialect 配置）已补充 `dialect=responses` 用法；否则 `No owner-doc update required`（若 nop-ai 无对应 owner doc）。
- [ ] `ai-dev/design/nop-ai-responses-migration-design.md` 状态从「草案」更新为最终（迁移完成）；analysis 文档 `ai-dev/analysis/2026-08/2026-08-01-openai-responses-vs-chat-completions.md` 已是 superseded（325 确认）。
- [ ] `ai-dev/logs/2026/08-02.md` 追加迁移完成记录。
- [ ] 独立子 agent closure-audit 已记录证据。
- [ ] **系列收口**：325-330 全部 completed 时，确认迁移根目标（nop-ai 可消费 Responses 端点 + 消息模型为单一拆分形态）达成。

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

Status Note: <<完成时填写：本计划完成 = nop-ai Responses 迁移系列（325-330）全部收口>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Evidence: <<Exit Criterion/Gate 验证 + `check-plan-checklist.mjs` 退出码 0 + `scan-hollow-implementations.mjs --module nop-ai-core` 退出码 0 + 系列收口确认（325-330 均 completed）>>

Follow-up:

- <<完成时填写>>
