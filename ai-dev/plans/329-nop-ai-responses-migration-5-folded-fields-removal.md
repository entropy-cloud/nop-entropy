# 329 nop-ai Responses 迁移 5：删除折叠过渡字段，收敛到单一拆分模型

> Plan Status: draft
> Review Hold: 前置 325/326/327 均为 `active` 但**尚未落地**，328 亦处 `draft`（Review Hold）。live repo（commit `271f6a2d7`）核实：`ChatReasoningMessage`/`ChatToolCallMessage` 全仓不存在；`ChatResponse` 仍为单条 `private ChatAssistantMessage message`（无 `messages` 列表）；`ChatMessage.java:20` 仍为 `@JsonTypeInfo(property="role")`（未改 `type`）；`ChatAssistantMessage.think/thinkSignature/toolCalls` **无任何 `@Deprecated` 标注**。本计划所有 Phase（删除寄居字段 / 删 `message` 单字段 / 改序列化标识）均硬依赖拆分模型先落地，否则会大面积破坏 dialect/agent/流式编译。**解除条件**：325（消息类型）+ 326（`response.messages` + dialect 双轨）+ 327（agent 切换）landed 后重审；本计划与 328 同属「待前置落地」队列。
> Last Reviewed: 2026-08-06
> Source: `ai-dev/design/nop-ai-responses-migration-design.md`（设计结论 #3/#4、§四「拒绝了什么」#3 双核心并存）。原稿"327/328 已把消费方切到拆分模型"为前瞻性表述，review 时经 live repo（`271f6a2d7`）核实尚未落地，已订正；详见 Review Hold。
> Related: 系列第 5 份，前置 325-328，后续 330（ResponsesDialect 落地在最终单一模型上）。本计划是迁移的**收敛点**：删除所有过渡 `@Deprecated` 字段，达到设计文档定义的最终单一拆分模型。

## Purpose

删除 `ChatAssistantMessage.think/thinkSignature/toolCalls` 寄居字段、`ChatResponse.message` 单字段、`ChatRequest` 旧便捷方法、`ChatMessage` 序列化标识从 `role`→`type`，并清理 legacy `AiChatExchange.think`（若裁定 in scope）。完成后 nop-ai 消息体系为**单一拆分模型**（与 Responses typed items 同构），无双轨/无 `@Deprecated` 过渡残留，序列化格式为最终形态。

**前置依赖硬约束**：327（agent 不再读 toolCalls）、328（StreamAggregator 不再产 think/toolCall 折叠）必须先完成；否则本计划的删除会破坏它们。退出时整体编译 + 全测试通过，且 grep 无 `@Deprecated` 过渡字段残留。

## Current Baseline

> 经过 325-328 后，消费方已切换，但旧字段仍保留（双轨填充）。本计划删除它们。

- `ChatAssistantMessage` 寄居字段（325 保留）：`:31 think`、`:36 thinkSignature`、`:41 toolCalls`、衍生 `getFullContent()`(:101)/`getFirstToolCall()`(:121)/`hasToolCalls()`(:96)。**`thinkSignature` 零外部引用**（摸底 §8.1），删除零风险。
- `ChatAssistantMessage.think` 残留引用（327/328 后剩余）：dialect 双轨填充点（326/328 填充，本计划移除填充）、`ChatResponse.getFullContent():234`、`DefaultChatLogger:63`、legacy `DefaultAiChatService:600,606,622`、`AiChatExchange:125,250,254`、`MockChatService:128`、`ChatStreamAccumulator:168`（328 已改）。
- `ChatAssistantMessage.toolCalls` 残留引用（327 后剩余）：`ChatRequest.java:209 getToolCalls`/`:191 getLastAssistantMessage`/`:230 addToolResponse`（寄居便捷方法）、`ChatServiceImpl:483`（328 StreamAggregator 已改，确认无残留）、4 dialect 双轨填充点、legacy `DefaultAiChatService:373,532`、`AiCommand:393,400`、dialect 测试 `TestAnthropicDialect:150,172`。
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

Status: planned
Targets: `nop-ai-api/.../messages/ChatAssistantMessage.java`、`nop-ai-core/.../dialect/*Dialect.java`

- Item Types: `Fix`

- [ ] `ChatAssistantMessage` 删除 `think`/`thinkSignature`/`toolCalls` 字段及 `getFullContent()`/`getFirstToolCall()`/`hasToolCalls()`/`setThink`/`setToolCalls`；仅保留 `content`。
- [ ] 4 dialect parseResponse（非流式）：移除 `setThink`/`setToolCalls` 双轨填充，改为纯 `messages` 产出（reasoning→ChatReasoningMessage、tool_call→ChatToolCallMessage，326 已建产出逻辑，此处删旧路径）。
- [ ] 4 dialect convertMessage：移除对 `getThink()`/`getToolCalls()` 的读取（`ChatAssistantMessage` 文本消息不再带这些；推理/工具调用作为独立 `ChatReasoningMessage`/`ChatToolCallMessage` 在 convertMessage 中按 type 分派序列化）。
- [ ] `TestAnthropicDialect`/`TestOllamaDialect` 等：把 `getMessage().getThink()/getToolCalls()` 断言改为 `getMessages()` 中 `ChatReasoningMessage`/`ChatToolCallMessage` 断言。

Exit Criteria:

- [ ] `ChatAssistantMessage` 仅剩 `content`（+ messageId/providerHints 继承字段）；grep 全仓 `getThink()|setThink|getToolCalls()|setToolCalls|hasToolCalls|thinkSignature` 无生产残留（legacy 裁定项除外）。
- [ ] 4 dialect 既有测试断言路径迁移完成、全绿。

### Phase 2 - ChatResponse.message 单字段删除 + 聚合访问器

Status: planned
Targets: `nop-ai-api/.../chat/ChatResponse.java`、消费方、`ILlmDialect.java`

- Item Types: `Fix | Decision`

- [ ] `ChatResponse` 删除 `message` 字段与 `getMessage()`；新增 `outputText()`（拼接全部 `ChatAssistantMessage` 文本）/`outputToolCalls()`（收集 `ChatToolCallMessage`）聚合访问器；`getFullContent()` 改为基于 messages。
- [ ] 消费方迁移：`SingleTurnExecutor:51`、`LlmCompletionJudge:95,102`、`LLMCurator:113,118`、`Layer3FullSummaryStrategy:146-155`、`MockChatService`、providers → 改用 `getMessages()`/`outputText()`。
- [ ] `ILlmDialect.buildResponse` default（`:249-278`）改为基于 `response.getMessages()`/`outputText()` 而非 `getMessage()`。
- [ ] `setMessage` 调用方（4 dialect、ChatServiceImpl StreamAggregator、MockChatService、providers）改为只产出 messages（326/328 已建产出，此处移除旧 setMessage）。

Exit Criteria:

- [ ] `ChatResponse.message` 字段与 `getMessage()` 已删；grep 无残留。
- [ ] 聚合访问器 `outputText()`/`outputToolCalls()` 就绪且有测试。
- [ ] 消费方全部迁移，编译通过。

### Phase 3 - ChatRequest 便捷方法 + ChatMessage 序列化标识 + legacy 裁定

Status: planned
Targets: `nop-ai-api/.../chat/ChatRequest.java`、`.../messages/ChatMessage.java`、legacy `AiChatExchange`/`DefaultAiChatService`/`DefaultChatLogger`

- Item Types: `Fix | Decision`

- [ ] `ChatRequest`：`getToolCalls()`/`hasToolCalls()` 改为从 messages 提取 `ChatToolCallMessage`（或删除并提供 messages 版替代）；`getLastAssistantMessage()` 改为从 messages 取最后一条 `ChatAssistantMessage`。
- [ ] `ChatMessage.java:20` @JsonTypeInfo `property="role"` → `property="type"`；更新 golden test（325 的 `TestChatMessageSerialization`）为最终 `type` 字段形态。
- [ ] **Decision（legacy 裁定）**：`AiChatExchange.think`（`:125,250,254`）、`DefaultAiChatService`（`:373,532,600,606,622`）、`DefaultChatLogger:63`——deprecated pipeline。裁定：清理（in scope）或显式 deferred（注明 Why Not Blocking）。若清理，同步其测试。

Exit Criteria:

- [ ] `ChatRequest` 便捷方法不再依赖 `ChatAssistantMessage.toolCalls`。
- [ ] 序列化 golden test 反映最终 `type` 字段形态，round-trip 通过。
- [ ] legacy 裁定已记录（landed 或 Deferred But Adjudicated）。
- [ ] **无 `@Deprecated` 过渡残留**：grep 全仓本系列引入的 `@Deprecated`（325/326 的 callId/responseFormat/message 委托）已全部移除。

## Closure Gates

- [ ] `ChatAssistantMessage.think/thinkSignature/toolCalls`、`ChatResponse.message` 已删除，无残留。
- [ ] 序列化标识为最终 `type` 形态，golden test 通过。
- [ ] 无 `@Deprecated` 过渡字段残留。
- [ ] `./mvnw compile`（nop-ai 全模块）通过。
- [ ] `./mvnw test -pl nop-ai -am` 全绿。
- [ ] **Anti-Hollow Check**：删除字段后非流式 + 流式端到端（ChatRequest → ChatResponse.messages / SSE → messages）仍完整连通；`scan-hollow-implementations.mjs --module nop-ai` 退出码 0。
- [ ] owner-doc：`ai-dev/design/nop-ai-responses-migration-design.md` 确认为最终设计状态（无 Proposed vs Current 残留，符合 Minimum Rules #14）。
- [ ] `ai-dev/logs/2026/08-02.md` 追加进度。
- [ ] 独立子 agent closure-audit 已记录证据。

## Risks And Rollback

- **风险 1（破坏面最大）**：本计划是系列中删除面最广的；327/328 必须先完成，否则大面积编译失败。Phase 顺序（字段→消费方→序列化）保证每步可编译。
- **风险 2（序列化格式终态变更）**：`role`→`type` 改变持久化 JSON；本系列为首次落地（325 已注明无历史数据），但需确认 session store 测试 round-trip 通过。
- **风险 3（legacy pipeline）**：`DefaultAiChatService`/`AiChatExchange` 若仍在某处被使用，清理会破坏；Decision 必须先 grep 调用面再裁定。
- **回滚**：本计划删除性强，回滚需 revert 整个 plan 提交集合；建议每个 Phase 独立提交以便定位。

## Deferred But Adjudicated

### （Phase 3 Decision 后填写）

- Classification: <<watch-only residual | out-of-scope improvement>>
- Why Not Blocking Closure: <<>>
- Successor Required: yes/no
- Successor Path: <<>>

## Non-Blocking Follow-ups

- compaction 策略（Layer2/Layer3/Micro/Reference）按 `getRole()` 字符串分派 → 统一改 type 分派（摸底 §3.4），可在后续治理计划处理，不阻塞本迁移。

## Closure

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Evidence: <<Exit Criterion/Gate 验证 + `check-plan-checklist.mjs` 退出码 0 + `scan-hollow-implementations.mjs --module nop-ai` 退出码 0>>

Follow-up:

- <<完成时填写>>
