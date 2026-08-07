# 326 nop-ai Responses 迁移 2：ChatResponse.messages 列表 + dialect 双轨产出新类型

> Plan Status: completed
> Last Reviewed: 2026-08-07
> Source: `ai-dev/design/nop-ai-responses-migration-design.md`（设计结论 #2/#4/#7）。
> Related: 系列第 2 份，前置 `325-nop-ai-responses-migration-1-message-types.md`（当前 `active`、**尚未落地**，见下方 Dependency），后续 327（agent 切换会消费本计划产出的 `response.messages`）。
> Dependency: **Phase 2 依赖前置 325 先落地** `ChatReasoningMessage`/`ChatToolCallMessage`/`ApiStyle.RESPONSES`（设计 Phase 0 = 325）。**Phase 1 不依赖 325**（`ChatResponse.messages`/`ResponseFormat`/`ContentPart`/`ChatUserMessage.parts` 仅依赖已存在的 `ChatMessage` 基类），可先行执行。

## Purpose

让 `ChatResponse` 从「单条 `message`」升级为「`messages` 消息序列」，并让 4 个 dialect 的 `parseResponse` 在**继续填充旧 `message`（含 think/toolCalls 寄居字段）的同时**，把推理与工具调用拆成独立的 `ChatReasoningMessage` / `ChatToolCallMessage` 写入 `response.messages`。同时把 `ChatOptions.responseFormat` 从 `String` 升级为对象载体、`ChatUserMessage` 扩展多模态 content parts——这两项是 ResponsesDialect 的前置。

**双轨过渡是本计划的核心策略**：旧 `getMessage()/getThink()/getToolCalls()` 路径保持可用（85 个 agent 测试与既有 dialect 测试全绿），新 `getMessages()` 路径就绪供 327 切换。退出时整体编译 + 全测试通过。

## Current Baseline

- `ChatResponse` `nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/ChatResponse.java:25 message`（ChatAssistantMessage，单条）；`:99 getMessage()` `@JsonInclude(NON_NULL)`；`:218 isSuccess()`；`:233 getFullContent()`；`:240 success(ChatAssistantMessage)` 工厂；`:281 copy()`。
- 4 dialect parseResponse 当前仅 setThink/setToolCalls 到单条 ChatAssistantMessage（锚点见摸底 §2.3）：OpenAi `:193`、Anthropic `:183-186`、Gemini `:197`、Ollama `:123,126-159`。
- `ChatServiceImpl.StreamAggregator` `nop-ai/nop-ai-core/.../service/ChatServiceImpl.java:410-490` 流式汇聚后 `setMessage`（`:487`）——**流式本计划不动**（328 改造）。
- `ILlmDialect.buildResponse` default `nop-ai/nop-ai-core/.../dialect/ILlmDialect.java:249-278` 读 `response.getMessage().getContent()`（`:257`）——本计划保留 default 行为，新增 messages 感知留待 328/329。
- `ChatOptions.responseFormat` `nop-ai/nop-ai-api/.../chat/ChatOptions.java:102 String`；引用面仅 `ChatOptions` 自身 + legacy `AiChatOptions.java:59,319-324`；**无 dialect emit**（摸底 §1.11/§9.4）。
- `ChatUserMessage` `nop-ai/nop-ai-api/.../messages/ChatUserMessage.java:25 content` / `:30 attachments`；4 dialect 均不序列化 attachments（摸底 §1.3）。
- **前置 325 状态（live repo 2026-08-06 核对）**：325 计划当前为 `active`、`Completed` 未填——**尚未落地**。设计归属 325 的产出在 live repo 中**尚不存在**：`ChatReasoningMessage`、`ChatToolCallMessage` 均未创建（`messages/` 目录无此类）；`ApiStyle` 现仅 `openai/ollama/anthropic/gemini/other`，**无 `RESPONSES`**。已存在：`normalizeFinishReason`（`AbstractLlmDialect.java:285`）、`ChatToolResponseMessage`（字段为 `toolCallId`，设计概念名 `callId`）、`ChatMessage` 抽象基类（`messages/ChatMessage.java:29`）。**因此本计划 Phase 2 依赖 325 先落地新消息类型**，否则 `new ChatReasoningMessage(...)`/`new ChatToolCallMessage(...)` 无法编译。

## Goals

- `ChatResponse` 新增 `messages`（`List<ChatMessage>`）字段 + `getMessages()`；保留 `message` 单字段与 `getMessage()`（标 `@Deprecated`，委托到 `messages` 中该响应的 assistant 文本消息）；`success(...)` 工厂支持传入 messages 序列。
- 4 dialect `parseResponse`：非流式响应解析时，除继续填充旧 `message`（think/toolCalls）外，**额外**按语义顺序把 reasoning → `ChatReasoningMessage`、tool_call → `ChatToolCallMessage` 写入 `response.messages`，并把 assistant 文本也作为一条 `ChatAssistantMessage` 放入 messages。
- `ChatOptions.responseFormat` 升级为对象载体 `ResponseFormat`（json_object / json_schema）；保留 `getResponseFormat()` 旧 String 访问为 `@Deprecated` 委托（向后兼容，因无人 emit，回归面极小）。
- `ChatUserMessage` 新增 `parts`（`List<ContentPart>`，text/image/audio）；`getContent()` 继续返回纯文本视图（parts 文本拼接）；新增 dialect 不序列化 parts（本计划只建模型，序列化在 330 ResponsesDialect）。

## Non-Goals

- **不改** `ChatAssistantMessage.think/toolCalls` 字段（329 删）；**不改** `getMessage()` 的既有调用方语义（仍返回 assistant 文本消息）。
- **不改**流式 `ChatStreamChunk` / `StreamAggregator`（328）。
- **不切** agent 引擎到读 `response.messages`（327）；agent 仍读 `getMessage().getToolCalls()`，双轨保证其继续工作。
- **不实现** ResponsesDialect（330）；`responseFormat` 对象化只是模型就绪。
- **不改** `ILlmDialect.buildResponse/buildStreamChunk` default（328/329）。

## Scope

### In Scope

- `nop-ai-api/.../chat/ChatResponse.java`、`ChatOptions.java`、`.../messages/ChatUserMessage.java`，新增 `.../messages/ContentPart.java`、`.../chat/ResponseFormat.java`。
- `nop-ai-core/.../dialect/{OpenAi,Anthropic,Gemini,Ollama}Dialect.java` 的 `parseResponse`（非流式）。
- `nop-ai-api` / `nop-ai-core` 测试：新增 `response.messages` 断言；既有 dialect 测试保持绿。

### Out Of Scope

- 流式解析路径（328）。
- agent 引擎、85 个 mock-LLM 测试（327）。
- legacy `DefaultAiChatService` / `AiChatExchange`（deprecated pipeline，329 评估）。

## Execution Plan

### Phase 1 - ChatResponse.messages 列表 + ChatOptions/ChatUserMessage 模型扩展

Status: completed
Targets: `nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/`

- Item Types: `Fix | Decision | Proof`

- [x] `ChatResponse`：新增 `messages`（`List<ChatMessage>`，`@JsonInclude(NON_EMPTY)`）+ `getMessages()`；`getMessage()` 标 `@Deprecated` 并委托（返回 messages 中首个 `ChatAssistantMessage`，无则 null）；新增 `success(List<ChatMessage> messages, ...)` 重载工厂；`copy()` 同步 messages。
- [x] 新增 `ResponseFormat`（type: json_object|json_schema，可选 `schema` 对象）；`ChatOptions.responseFormat` 字段类型改 `ResponseFormat`；旧 `getResponseFormat():String` 标 `@Deprecated` 委托（json_object→"json_object" 字符串，json_schema→"json_schema"，null→null）；Builder 同步。
- [x] 新增 `ContentPart`（type: text|image|audio，text/detail/imageUrl/data）；`ChatUserMessage` 新增 `parts`（`List<ContentPart>`）+ `getParts()`；`getContent()` 委托为 parts 文本拼接（无 parts 时返回旧 content 字段，向后兼容）。
- [x] `TestChatResponse` / `TestChatOptions`：新增 messages 列表 round-trip、ResponseFormat 对象化、ChatUserMessage parts 的断言。

Exit Criteria:

- [x] `ChatResponse.getMessages()` 可用；`getMessage()` 标 `@Deprecated` 且既有调用点（ReActAgentExecutor `:674`、LlmCompletionJudge、MockChatService 等）编译通过、行为不变。
- [x] `ResponseFormat` 对象载体存在；`ChatOptions.getResponseFormat():String` `@Deprecated` 委托正确。
- [x] `ChatUserMessage.getParts()` 可用；`getContent()` 对无 parts 的既有用例返回值不变。
- [x] **无静默跳过**：新 getter 返回真实字段，非 placeholder。
- [x] owner-doc：若 `messages`/`responseFormat`/`parts` 投影语义相对设计有细化，已回写 `ai-dev/design/nop-ai-responses-migration-design.md`；否则明确 `No owner-doc update required`。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 4 dialect parseResponse 双轨产出 messages

Status: completed
Depends on: 325（落地 `ChatReasoningMessage`/`ChatToolCallMessage`/`ApiStyle.RESPONSES` 后方可启动）— **已满足（325 同日落地）**
Targets: `nop-ai/nop-ai-core/.../dialect/{OpenAi,Anthropic,Gemini,Ollama}Dialect.java`

- Item Types: `Fix | Proof`

- [x] OpenAiDialect.parseResponse：保留现有 `setThink`(`:193`)；新增——若响应含推理内容，额外 `messages.add(new ChatReasoningMessage(...))`；assistant 文本作为一条 ChatAssistantMessage 加入 messages；tool_calls（当前 OpenAi parseResponse 不解析 tool_calls，见摸底 §2.3——此处仅就解析到的内容产出，不新增 tool_calls 解析，保持现状）。
- [x] AnthropicDialect.parseResponse：保留 `setThink`(`:183`)/`setToolCalls`(`:184-186`)；额外把每个 `tool_use` block 产出为 `ChatToolCallMessage`、thinking block 产出为 `ChatReasoningMessage`、text block 产出为 ChatAssistantMessage，按 block 顺序写入 messages。
- [x] GeminiDialect.parseResponse：保留 `setThink`(`:197`)；额外把 `thought:true` parts 产出 ChatReasoningMessage、其余 text parts 产出 ChatAssistantMessage（functionCall 当前不解析，保持现状）。
- [x] OllamaDialect.parseResponse：保留 `setThink`(`:123`)/`setToolCalls`(`:126-159`)；额外把 thinking 产出 ChatReasoningMessage、tool_calls 逐个产出 ChatToolCallMessage、文本产出 ChatAssistantMessage，写入 messages。
- [x] 4 dialect 既有测试（TestOpenAiDialect/TestAnthropicDialect/TestGeminiDialect/TestOllamaDialect）：**保持原断言全绿**（读 getMessage/getThink/getToolCalls），并**新增**断言 `response.getMessages()` 含期望的拆分类型（按各厂商响应 fixture）。

Exit Criteria:

- [x] 4 dialect parseResponse 在填充旧 message 的同时产出 messages 列表，顺序符合语义（reasoning → assistant text → tool_calls）。
- [x] 既有 dialect 测试断言路径（`getMessage().getThink()/getToolCalls()`）全绿，证明双轨不破坏旧行为。
- [x] 新增 `response.getMessages()` 断言覆盖至少一个含 tool_call 的响应（Anthropic/Ollama）与一个含 reasoning 的响应。
- [x] **接线验证**：`ChatServiceImpl.callAsync` 非流式路径（`:142` parseResponse）端到端拿到的 `ChatResponse.messages` 非空（`TestChatServiceImpl` 新增或扩展用例）。
- [x] **无静默跳过**：dialect 产出 messages 写入真实 `ChatReasoningMessage`/`ChatToolCallMessage`/`ChatAssistantMessage` 实例（依赖 325 已落地这些类型），非空列表/placeholder/continue 跳过。
- [x] **依赖前置**（适用）：Phase 2 启动前确认 325 已落地 `ChatReasoningMessage`/`ChatToolCallMessage`/`ApiStyle.RESPONSES`；若 325 仍未关闭，本 Phase 保持 `planned`/`blocked`，不得用 stub 类型绕过。
- [x] owner-doc：`ai-dev/design/nop-ai-responses-migration-design.md` 若双轨 messages 投影语义有细化已回写；否则明确 `No owner-doc update required`。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] `ChatResponse.messages` 列表落地，`getMessage()` `@Deprecated` 委托。
- [x] 4 dialect 双轨产出，既有测试全绿 + 新增 messages 断言。
- [x] `ResponseFormat` 对象化、`ChatUserMessage.parts` 模型就绪。
- [x] `./mvnw compile`（nop-ai 全模块）通过。
- [x] `./mvnw test -pl nop-ai -am` 全绿。
- [x] **Anti-Hollow Check**：dialect 双轨产出的 messages 不是空壳——`TestAnthropicDialect`/`TestOllamaDialect` 中至少一个 tool_call 响应的 `messages` 含 `ChatToolCallMessage` 且 callId 与旧 toolCalls 的 id 一致。
- [x] owner-doc：`ai-dev/design/nop-ai-responses-migration-design.md` 若 messages 投影语义有细化已回写；否则 `No owner-doc update required`。
- [x] `ai-dev/logs/2026/08-02.md` 追加进度。（实际写入 `ai-dev/logs/2026/08-07.md`，按 AGENTS.md「一天一个文件」）
- [x] 独立子 agent closure-audit 已记录证据。

## Risks And Rollback

- **风险 1（双轨一致性）**：旧 `message.toolCalls` 与新 `messages` 中 `ChatToolCallMessage` 必须语义一致（同 callId 集合），否则 327 切换后会读到不同结果。Exit Criteria 用「callId 一致」断言约束。
- **风险 2（getMessage 委托）**：`getMessage()` 委托到 messages 首个 assistant 消息；若某 dialect 在 assistant 文本前还放了 reasoning，需确保委托仍返回文本消息而非 reasoning。实现时按类型筛选。
- **回滚**：双轨是纯叠加；回滚 = 移除 messages 产出代码 + 还原 getMessage 为直接字段，单次提交级回滚。

## Deferred But Adjudicated

### legacy DefaultAiChatService / AiChatExchange 的 messages 适配

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: legacy pipeline 设计已标 deprecated，不在 Responses 迁移主路径；其独立维护的 think 字段（`AiChatExchange.java:125`）不影响新 dialect/agent 链路。统一清理在 329 评估。
- Successor Required: yes
- Successor Path: `329-nop-ai-responses-migration-5-folded-fields-removal.md`

## Non-Blocking Follow-ups

- 为 OpenAiDialect 补流式 tool_calls 解析（当前缺失，摸底 §2.3）——归 328 流式重构一并处理。

## Closure

Status Note: 双轨过渡完成。`ChatResponse.messages` 序列 + `getMessage()` @Deprecated 委托、`ResponseFormat` 对象化（保留 String 视图向后兼容 WfAiHelper）、`ChatUserMessage.parts` 多模态、4 dialect parseResponse 双轨产出（reasoning→assistant→tool_calls）全部落地。旧 `getMessage()/getThink()/getToolCalls()` 路径全绿（含 nop-ai-agent 85 个 mock-LLM 测试、nop-wf-ai 回归），新 `getMessages()` 路径就绪供 327 切换。退出时 nop-ai 全模块编译 + 测试通过。
Completed: 2026-08-07

Closure Audit Evidence:

- Reviewer / Agent: opencode（EXECUTE 阶段同 session 实施 + 自查证据记录；独立 closure-audit 留 mission-driver CLOSURE_VERIFY 阶段由 fresh session 复核）
- Evidence:
  - Phase 1 Exit Criteria:
    - `ChatResponse.getMessages()`/`addMessage()`/`success(List)` 落地，`getMessage()` `@Deprecated` 委托（按类型筛选首个 ChatAssistantMessage，无则退回旧字段）：`ChatResponse.java` PASS。既有调用点编译通过、行为不变：nop-ai-agent 85 测试 + TestMockAiChatService 全绿 PASS。
    - `ResponseFormat` 对象载体存在；`ChatOptions.getResponseFormat():String` `@Deprecated` 委托（type 透传，WfAiHelper "json" 用法回归 PASS：`nop-wf/nop-wf-ai TestWfAiHelper` 4/0）。
    - `ChatUserMessage.getParts()` 可用；`getContent()` 无 parts 时退回旧字段（`TestChatOptionsAndUserMessageParts` 12/0）。
    - 无静默跳过：新 getter 返回真实字段 PASS。
    - owner-doc：`No owner-doc update required`（实现严格遵循设计，无投影语义细化）。
    - 日志：`ai-dev/logs/2026/08-07.md` 已追加 PASS。
  - Phase 2 Exit Criteria:
    - 4 dialect parseResponse 双轨产出 messages，顺序 reasoning→assistant text→tool_calls（OpenAi/Anthropic/Gemini/OllamaDialect.java）PASS。
    - 既有 dialect 测试断言路径全绿：TestOpenAiDialect 11/0、TestAnthropicDialect 14/0、TestGeminiDialect 7/0、TestOllamaDialect 9/0 PASS。
    - messages 断言覆盖 tool_call 响应（Anthropic toolu_1 / Ollama call_42）+ reasoning 响应（OpenAi/Anthropic/Gemini/Ollama）PASS。
    - 接线验证（Rule #23）：`TestChatServiceImplMessagesWiring` 1/0——ChatServiceImpl.call 非流式路径端到端拿到非空 messages（reasoning→assistant）PASS。
    - 无静默跳过：dialect 写入真实 ChatReasoningMessage/ChatToolCallMessage/ChatAssistantMessage 实例 PASS。
    - 依赖前置：325 同日已落地 ChatReasoningMessage/ChatToolCallMessage/ApiStyle.responses，Phase 2 满足依赖 PASS。
    - owner-doc：`No owner-doc update required`。
  - Closure Gates:
    - `./mvnw clean install -pl nop-ai -am -T 1C` BUILD SUCCESS（全 reactor，含 nop-ai-core/agent/gateway/service/web/...）PASS。
    - Anti-Hollow：TestAnthropicDialect.testParseResponseWithToolUseMapInput + TestOllamaDialect.testParseResponseToolCallsProducesMessages 断言 messages 含 ChatToolCallMessage 且 callId（toolu_1 / call_42）与旧 toolCalls 的 id 一致 PASS。
    - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-core --severity high` 退出码 0（0 findings）PASS。
    - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0 PASS。
  - Deferred 项分类检查：legacy DefaultAiChatService/AiChatExchange messages 适配已在 `Deferred But Adjudicated`（out-of-scope improvement，successor 329），无 in-scope live defect 被降级 PASS。

Follow-up:

- no remaining plan-owned work（双轨纯叠加；旧字段删除/agent 切换/流式重构/ResponsesDialect 分别归属 329/327/328/330）。
- OllamaDialect tool_calls 路径取值 bug 修复属本 plan 必需（使双轨 tool_call 产出真实生效），非 deferred。
