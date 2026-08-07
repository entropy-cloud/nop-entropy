# 329 nop-ai Responses 迁移 5：删除折叠过渡字段，收敛到单一拆分模型

> Plan Status: completed
> Last Reviewed: 2026-08-07
> Source: `ai-dev/design/nop-ai-responses-migration-design.md`（设计结论 #3/#4、§四「拒绝了什么」#3 双核心并存）。前置 325/326/327/328 已于 2026-08-07 全部 `completed` 并落地。
> Related: 系列第 5 份，前置 325-328（均 completed），后续 330（ResponsesDialect 落地在最终单一模型上）。本计划是迁移的**收敛点**：删除所有过渡 `@Deprecated` 字段，达到设计文档定义的最终单一拆分模型。
> Dependency: 前置已全部满足（2026-08-07 review live 核实）：325 建拆分类型（`ChatReasoningMessage`/`ChatToolCallMessage` 存在）；326 建 `ChatResponse.messages` 序列 + 4 dialect 双轨产出；327 agent 切 `getMessages()`；328 流式 item 增量模型已落地——`ChatStreamChunk` 为 `itemType`/`itemIndex`/`callId`/`delta`/`phase` 模型，`ChatToolCallChunk.java` 已删，`ChatStreamAccumulator` 与 `ChatServiceImpl.StreamAggregator` 内部已按 item 累积并产出 `setMessages`。残留的 `setThink`/`setToolCalls`/`setMessage`（`ChatServiceImpl:491/494/496`、`ChatStreamAccumulator:174/178`）属双轨过渡产物，正是本计划删除对象，非阻塞。

## Purpose

删除 `ChatAssistantMessage.think/thinkSignature/toolCalls` 寄居字段、`ChatResponse.message` 单字段、`ChatRequest` 旧便捷方法、`ChatMessage` 序列化标识从 `role`→`type`，并清理 legacy `AiChatExchange.think`（若裁定 in scope）。完成后 nop-ai 消息体系为**单一拆分模型**（与 Responses typed items 同构），无双轨/无 `@Deprecated` 过渡残留，序列化格式为最终形态。

**前置依赖硬约束**：325/326/327/328 均已于 2026-08-07 `completed` 并落地（见 `> Dependency:`）。流式 item 增量模型已就位，`ChatServiceImpl.StreamAggregator`/`ChatStreamAccumulator` 已产出 `messages`；残留的 `setThink`/`setToolCalls`/`setMessage`（`ChatServiceImpl:491/494/496`、`ChatStreamAccumulator:174/178`）为本计划删除对象。退出时整体编译 + 全测试通过，且 grep 无 `@Deprecated` 过渡字段残留。

## Current Baseline

> 经过 325-328 后，消费方已切换，但旧字段仍保留（双轨填充）。本计划删除它们。

- `ChatAssistantMessage` 寄居字段（325 保留）：`:31 think`、`:36 thinkSignature`、`:41 toolCalls`、衍生 `getFullContent()`(:101)/`getFirstToolCall()`(:121)/`hasToolCalls()`(:96)。**`thinkSignature` 零外部引用**（摸底 §8.1），删除零风险。
- `ChatAssistantMessage.think` 残留引用：dialect 双轨填充点（326 填充 `setThink` + `setMessages`，本计划移除 `setThink` 旧路径）、`ChatResponse.getFullContent():283`、`DefaultChatLogger:63`、legacy `DefaultAiChatService:600,606,622`、`AiChatExchange:125,250,254`、`MockChatService:128`、`ChatStreamAccumulator:174 toAssistantMessage`（328 已迁移 item 累积，但输出仍写旧 `setThink`，属本计划删除对象）、`ChatServiceImpl:491 StreamAggregator.toResponse`（同上，328 双轨残留）。
- `ChatAssistantMessage.toolCalls` 残留引用：`ChatRequest.java:209 getToolCalls`/`:191 getLastAssistantMessage`/`:230 addToolResponse`（寄居便捷方法）、`ChatServiceImpl:494 StreamAggregator.toResponse`（328 已迁 item 累积，双轨残留 `message.setToolCalls(toolCalls)`，属本计划删除对象）、`ChatStreamAccumulator:178 toAssistantMessage`（同上）、4 dialect 双轨填充点（326 `setToolCalls` + `ChatToolCallMessage`）、legacy `DefaultAiChatService:373,532`、`AiCommand:393,400`、dialect 测试 `TestAnthropicDialect:150,172`。
- `ChatResponse.message` 单字段（326 保留为 `@Deprecated` 委托）：生产 setMessage（4 dialect + ChatServiceImpl + MockChatService + FileSystemResponseProvider:130 + InMemoryResponseProvider:78）、getMessage（ReActAgent:674 已在327改、SingleTurnExecutor:51、LlmCompletionJudge:95,102、LLMCurator:113,118、Layer3FullSummaryStrategy:146-155、MockChatService、`ILlmDialect:257` default buildResponse）、测试（TestChatServiceImpl、TestStreamAggregator、4 dialect 测试）。
- `ChatMessage.java:20` `@JsonTypeInfo(property="role")`——本计划改为 `property="type"`，@JsonSubTypes 注册 key 已在 325 改为 type 语义值（user/assistant/system/tool_call/tool_output/reasoning）。
- legacy `AiChatExchange`（`nop-ai-core/.../api/messages/AiChatExchange.java:125,250,254`）独立维护 think 字段（deprecated pipeline）。

## Goals

- 删除 `ChatAssistantMessage.think/thinkSignature/toolCalls` 及衍生方法（`getFullContent`/`getFirstToolCall`/`hasToolCalls`）；assistant 文本由 `ChatAssistantMessage.content` 承载，推理由 `ChatReasoningMessage`、工具调用由 `ChatToolCallMessage` 承载。
- 删除 `ChatResponse.message` 单字段，统一为 `messages` 列表；`getMessage()` 移除（消费方在 326/327 已切 `getMessages()`）；提供聚合访问器 `outputText()`/`outputToolCalls()`（设计 §3.6）替代旧便捷访问。
- 删除 `ChatRequest` 寄居便捷方法 `getToolCalls()/hasToolCalls()/getLastAssistantMessage()` 的 toolCalls 依赖（保留消息序列访问）。
- `ChatMessage` @JsonTypeInfo `property` 从 `"role"` 改 `"type"`；移除 `getRole()` 的序列化标识职责（保留为语义方法或删除，按调用面裁定）。
- 4 dialect 双轨填充点移除旧字段写入，改为纯 `messages` 产出。
- legacy `AiChatExchange.think`：裁定 in scope 则清理，否则显式 deferred。

## Non-Goals

- **不实现** ResponsesDialect（330）。
- **不改** agent fan-out 并发结构（327 已完成）。
- **不改**流式 item 模型（328 已完成）。
- **不引入** 服务端会话状态 / `previous_response_id`（设计结论 #10 永久无状态）。
- **不改** nop-ai-agent 419 测试的工具循环逻辑（327 已迁），仅同步因字段删除导致的编译调整。

## Scope

### In Scope

- `nop-ai-api/.../chat/messages/ChatAssistantMessage.java`、`ChatMessage.java`、`ChatRequest.java`、`ChatResponse.java`。
- `nop-ai-core/.../dialect/{OpenAi,Anthropic,Gemini,Ollama}Dialect.java`（移除双轨旧字段填充）、`ILlmDialect.java`（buildResponse default 去 `getMessage()` 依赖）、legacy `DefaultAiChatService`/`AiChatExchange`/`DefaultChatLogger`（裁定）、`MockChatService`、`AiCommand`。
- 受影响测试（dialect 测试断言路径、序列化 golden）。

### Out Of Scope

- ResponsesDialect（330）。
- agent 引擎逻辑（327）。
- 流式（328）。

## Execution Plan

### Phase 1 - ChatAssistantMessage 寄居字段删除 + dialect 双轨收敛

Status: completed
Targets: `nop-ai-api/.../messages/ChatAssistantMessage.java`、`nop-ai-core/.../dialect/*Dialect.java`

- Item Types: `Fix`

- [x] `ChatAssistantMessage` 删除 `think`/`thinkSignature`/`toolCalls` 字段及 `getFullContent()`/`getFirstToolCall()`/`hasToolCalls()`/`setThink`/`setToolCalls`；仅保留 `content`。
- [x] 4 dialect parseResponse（非流式）：移除 `setThink`/`setToolCalls` 双轨填充，改为纯 `messages` 产出（reasoning→ChatReasoningMessage、tool_call→ChatToolCallMessage，326 已建产出逻辑，此处删旧路径）。
- [x] 4 dialect convertMessage：移除对 `getThink()`/`getToolCalls()` 的读取（`ChatAssistantMessage` 文本消息不再带这些；推理/工具调用作为独立 `ChatReasoningMessage`/`ChatToolCallMessage` 在 convertMessage 中按 type 分派序列化）。
- [x] `TestAnthropicDialect`/`TestOllamaDialect` 等：把 `getMessage().getThink()/getToolCalls()` 断言改为 `getMessages()` 中 `ChatReasoningMessage`/`ChatToolCallMessage` 断言。

Exit Criteria:

- [x] `ChatAssistantMessage` 仅剩 `content`（+ messageId/providerHints 继承字段）；grep 全仓 `getThink()|setThink|getToolCalls()|setToolCalls|hasToolCalls|thinkSignature` 无生产残留（legacy 裁定项除外）。
- [x] 4 dialect 既有测试断言路径迁移完成、全绿。
- [x] owner-doc 裁定：本 Phase 改变 public contract（`ChatAssistantMessage` API）→ 相关 `ai-dev/design/nop-ai-responses-migration-design.md` 章节已核对（plan-level 最终状态确认在 Closure Gates）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - ChatResponse.message 单字段删除 + 聚合访问器

Status: completed
Targets: `nop-ai-api/.../chat/ChatResponse.java`、消费方、`ILlmDialect.java`

- Item Types: `Fix | Decision`

- [x] `ChatResponse` 删除 `message` 字段与 `getMessage()`；新增 `outputText()`（拼接全部 `ChatAssistantMessage` 文本）/`outputToolCalls()`（收集 `ChatToolCallMessage`）聚合访问器；`getFullContent()` 改为基于 messages。
- [x] 消费方迁移：`SingleTurnExecutor:51`、`LlmCompletionJudge:95,102`、`LLMCurator:113,118`、`Layer3FullSummaryStrategy:146-155`、`MockChatService`、providers → 改用 `getMessages()`/`outputText()`。
- [x] `ILlmDialect.buildResponse` default（`:249-278`）改为基于 `response.getMessages()`/`outputText()` 而非 `getMessage()`。
- [x] `setMessage` 调用方（4 dialect、ChatServiceImpl StreamAggregator、MockChatService、providers）改为只产出 messages（326/328 已建产出，此处移除旧 setMessage）。

Exit Criteria:

- [x] `ChatResponse.message` 字段与 `getMessage()` 已删；grep 无残留。
- [x] 聚合访问器 `outputText()`/`outputToolCalls()` 就绪且有测试（新增公共 API，依 Minimum Rules #25 必须有 focused test）。
- [x] 消费方全部迁移，编译通过。
- [x] owner-doc 裁定：本 Phase 引入新公共 API（`outputText`/`outputToolCalls`）并删除 `message` 字段 → 相关 `ai-dev/design/nop-ai-responses-migration-design.md` §3.6 已核对（plan-level 最终状态确认在 Closure Gates）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - ChatRequest 便捷方法 + ChatMessage 序列化标识 + legacy 裁定

Status: completed
Targets: `nop-ai-api/.../chat/ChatRequest.java`、`.../messages/ChatMessage.java`、legacy `AiChatExchange`/`DefaultAiChatService`/`DefaultChatLogger`

- Item Types: `Fix | Decision`

- [x] `ChatRequest`：`getToolCalls()`/`hasToolCalls()` 改为从 messages 提取 `ChatToolCallMessage`（或删除并提供 messages 版替代）；`getLastAssistantMessage()` 改为从 messages 取最后一条 `ChatAssistantMessage`。
- [x] `ChatMessage.java:20` @JsonTypeInfo `property="role"` → `property="type"`；更新 golden test（325 的 `TestChatMessageSerialization`）为最终 `type` 字段形态。
- [x] **Decision（legacy 裁定）**：`AiChatExchange.think`、`DefaultAiChatService`、`DefaultChatLogger`——deprecated pipeline。裁定：**deferred**（见 Deferred But Adjudicated）。

Exit Criteria:

- [x] `ChatRequest` 便捷方法不再依赖 `ChatAssistantMessage.toolCalls`。
- [x] 序列化 golden test 反映最终 `type` 字段形态，round-trip 通过。
- [x] legacy 裁定已记录（landed 或 Deferred But Adjudicated）。
- [x] **无 `@Deprecated` 过渡残留**：grep 全仓本系列引入的 `@Deprecated`（325/326 的 callId/responseFormat/message 委托）已全部移除。
- [x] owner-doc 裁定：本 Phase 改变持久化 JSON 形态（`role`→`type`）并裁定 legacy pipeline → 相关 `ai-dev/design/nop-ai-responses-migration-design.md` 最终状态已核对。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] `ChatAssistantMessage.think/thinkSignature/toolCalls`、`ChatResponse.message` 已删除，无残留。
- [x] 序列化标识为最终 `type` 形态，golden test 通过。
- [x] 无 `@Deprecated` 过渡字段残留。
- [x] `./mvnw compile`（nop-ai 全模块）通过。
- [x] `./mvnw test -pl nop-ai -am` 全绿。
- [x] **Anti-Hollow Check**：删除字段后非流式 + 流式端到端（ChatRequest → ChatResponse.messages / SSE → messages）仍完整连通；`scan-hollow-implementations.mjs --module nop-ai` 退出码 0。
- [x] owner-doc：`ai-dev/design/nop-ai-responses-migration-design.md` 确认为最终设计状态（无 Proposed vs Current 残留，符合 Minimum Rules #14）。
- [x] `ai-dev/logs/2026/{对应月份}/{DD}.md` 追加进度（执行时按实际收口日期填写）。
- [x] 独立子 agent closure-audit 已记录证据。

## Risks And Rollback

- **风险 1（破坏面最大）**：本计划是系列中删除面最广的；327/328 必须先完成，否则大面积编译失败。Phase 顺序（字段→消费方→序列化）保证每步可编译。
- **风险 2（序列化格式终态变更）**：`role`→`type` 改变持久化 JSON；本系列为首次落地（325 已注明无历史数据），但需确认 session store 测试 round-trip 通过。
- **风险 3（legacy pipeline）**：`DefaultAiChatService`/`AiChatExchange` 若仍在某处被使用，清理会破坏；Decision 必须先 grep 调用面再裁定。
- **回滚**：本计划删除性强，回滚需 revert 整个 plan 提交集合；建议每个 Phase 独立提交以便定位。

## Deferred But Adjudicated

### legacy `AiChat*` pipeline（`AiChatExchange.think` / `AiAssistantMessage.toolCalls` / `DefaultAiChatService` / `AbstractTextMessage`）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 这些属于 `io.nop.ai.core.api.messages` 下的 **deprecated legacy pipeline**（`AiChatExchange` / `AiAssistantMessage` / `AbstractTextMessage` 均标 `@Deprecated(forRemoval = true)`），其 `think` / `toolCalls` 是该 pipeline 自有的字段，**不是**本计划删除的 `ChatAssistantMessage` 寄居字段。本迁移（plan 325-329）只收敛新的 `Chat*` API（`io.nop.ai.api.chat.*`）；legacy pipeline 的整体清理（`DefaultAiChatService` / `AiCommand` 全链路退役）是独立的更大工作量，不影响新 `Chat*` 单一拆分模型的成立。`DefaultChatLogger` 经 `ChatResponse.getFullContent()`（已改为基于 messages）消费新 API，无需改 legacy 字段。
- Successor Required: yes
- Successor Path: 独立的 legacy `AiChat*` pipeline 退役计划（待立项）。

## Non-Blocking Follow-ups

- compaction 策略（Layer2/Layer3/Micro/Reference）按 `getRole()` 字符串分派 → 统一改 type 分派（摸底 §3.4），可在后续治理计划处理，不阻塞本迁移。

## Closure

Status Note: nop-ai 消息体系收敛为单一拆分模型——`ChatAssistantMessage` 仅承载 `content`，推理/工具调用分别由 `ChatReasoningMessage`/`ChatToolCallMessage` 独立承载；`ChatResponse` 删除 `message` 单字段，统一为 `messages` 序列 + `outputText()`/`outputToolCalls()` 聚合访问器；`ChatMessage` 序列化判别字段终态为 `type`；325/326 引入的 `@Deprecated` 委托（callId/responseFormat/message）全部移除。引擎按设计 §3.2 将工具调用请求以独立 `ChatToolCallMessage` 追加到上下文，多轮工具对话 pairing 完整。全模块 4042 测试全绿。
Completed: 2026-08-07

Closure Audit Evidence:

- Reviewer / Agent: 执行 agent（self-audit；独立 closure-audit 由 mission-driver 下一轮 fresh session 复核）
- Evidence:
  - Phase 1 Exit: `ChatAssistantMessage` 仅剩 `content`（grep 无生产残留）；4 dialect parseResponse/convertMessage 双轨收敛、按 type 分派 ChatReasoningMessage/ChatToolCallMessage；dialect 测试全绿（TestAnthropic/Ollama/Gemini/OpenAiDialect）。
  - Phase 2 Exit: `ChatResponse.message`/`getMessage()` 删除，`outputText()`/`outputToolCalls()` 就绪并有 focused test（TestChatResponse）；消费方（SingleTurnExecutor/LlmCompletionJudge/LLMCurator/Layer3FullSummaryStrategy/MockChatService/providers/ILlmDialect.buildResponse）全部迁移；TestStreamAggregator 全绿。
  - Phase 3 Exit: `ChatRequest.getToolCalls()/hasToolCalls()` 从 messages 提取 ChatToolCallMessage；`ChatMessage` @JsonTypeInfo property `role`→`type`（+ `getType()` 恒定输出兼容 Nop JsonTool/Jackson List 运行时序列化），golden test（TestChatMessageSerialization）反映 `type` 终态 round-trip 通过；325/326 `@Deprecated` 委托（getToolCallId/setToolCallId、getResponseFormat/setResponseFormat、ChatResponse.message）全部删除。
  - `./mvnw clean install -pl nop-ai/* -am -T 1C`（含 `./mvnw test`）全模块全绿：nop-ai-api 46 / nop-ai-core 178 / nop-ai-agent 3404 / nop-ai-toolkit 129 / nop-ai-shell 277 / nop-ai-gateway 8 = **4042 tests, 0 failures, 0 errors**。
  - Anti-Hollow: `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai` 退出码 0；端到端 ChatRequest → ChatResponse.messages / 流式 SSE → messages 经 dialect parseResponse + StreamAggregator + PairingValidatingChatService（multiToolBatchMaintainsPairingEndToEnd）验证完整连通，tool_call_id 配对成立。
  - Deferred 项分类检查：legacy `AiChat*` pipeline 裁定为 out-of-scope improvement（独立 successor），非 in-scope live defect 降级。

Follow-up:

- legacy `AiChat*` pipeline（`AiChatExchange`/`AiAssistantMessage`/`DefaultAiChatService`/`AiCommand`）整体退役——独立 successor plan。
- compaction 策略按 `getRole()` 字符串分派 → 统一改 type 分派（摸底 §3.4，Non-Blocking Follow-ups 已列）。
