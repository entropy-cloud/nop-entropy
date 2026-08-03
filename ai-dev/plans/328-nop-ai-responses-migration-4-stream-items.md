# 328 nop-ai Responses 迁移 4：流式 ChatStreamChunk item 增量重构

> Plan Status: draft
> Last Reviewed: 2026-08-02
> Source: `ai-dev/design/nop-ai-responses-migration-design.md`（设计结论 #6、§3.3）；327 已把 agent 非流式路径切到拆分消息。
> Related: 系列第 4 份，前置 327，后续 329（删字段收敛）。本计划为 330 ResponsesDialect 的具名事件流解析铺路。

## Purpose

把流式模型从「折叠 delta（content/thinking/toolCall 三字段粘在一个 chunk）」重构为「item 增量（`itemType`/`itemIndex`/`callId?`/`delta`/`phase:ADDED|DELTA|DONE`）」，删除死代码 `ChatToolCallChunk`，并补齐 OpenAI 路径流式 tool_calls 解析缺口（摸底 §2.3 确认当前永不填充）。重构后流式 chunk 与非流式 `messages` 同构（item 边界在 canonical 层保留），为 Responses 具名事件流（`response.output_text.delta` 等）提供统一解析模型。

**SSE 架构约束**（摸底 §4.1/§9.5）：`ChatServiceImpl.callStream` 的 `onNext`（`:191`）只把 `data:` 行传给 `dialect.parseStreamChunk`，`event:` 行不可达 dialect。Responses 具名事件靠 `data:` 载荷内 `type` 字段分派——本约束下 item 增量状态机可行，330 落地时遵循。

## Current Baseline

- `ChatStreamChunk` `nop-ai/nop-ai-api/.../chat/stream/ChatStreamChunk.java`：`:29 id`/`:35 index`/`:39 role`/`:44 content`/`:49 thinking`/`:54 toolCall`(ChatToolCallChunk)/`:59 finishReason`/`:64 usage`；`:157 isLastChunk()`/`:164 hasContent()`/`:171 hasThinking()`/`:178 hasToolCall()`。
- `ChatToolCallChunk` `nop-ai/nop-ai-api/.../chat/stream/ChatToolCallChunk.java`：`:26 index`/`:31 id`/`:36 type`/`:41 name`/`:46 arguments`(String 增量)。**引用面 4 处**（摸底 §1.13/§8.4）：`ChatStreamChunk.java:54,119,123`、`ChatServiceImpl.java:443`、`AnthropicDialect.java:16,284,309`（唯一填充该模型的 dialect）、`ChatStreamAccumulator.java:96,198`。
- `OpenAiDialect.parseStreamChunk` `nop-ai/nop-ai-core/.../dialect/OpenAiDialect.java:214-240` 只解析 `choices.0.delta.content/thinking/reasoning_content`，**不解析流式 tool_calls**（摸底 §2.3）。
- `AnthropicDialect.parseStreamChunk` `:248-374` 解析 `content_block_start/_delta`，唯一填充 ChatToolCallChunk（`:284,:309`）。
- `GeminiDialect.parseStreamChunk` `:216-270`、`OllamaDialect.parseStreamChunk` `:179-195`。
- `ChatServiceImpl.StreamAggregator` `nop-ai/nop-ai-core/.../service/ChatServiceImpl.java:410-490`：`:443 addToolCallChunk`、`:471 setThink`、`:487 setMessage`；`ToolCallAccumulator` 内部类 `:495-523`。
- `ChatStreamAccumulator` `nop-ai/nop-ai-api/.../chat/stream/ChatStreamAccumulator.java`：`:96 accumulateToolCall`、`:168 setThink`、`:172 getToolCalls`、`:198`。
- `ILlmDialect.buildStreamChunk` default `nop-ai/nop-ai-core/.../dialect/ILlmDialect.java:278-292` 读 `chunk.getToolCall()`——改 ChatStreamChunk 字段后此 default 需重写。
- 测试：`TestStreamAggregator`(2)、`TestChatServiceImpl`(3)、4 dialect 流式断言。

## Goals

- `ChatStreamChunk` 改造为 item 增量模型：`itemType`(text|reasoning|tool_call)、`itemIndex`、`callId?`、`delta`、`phase`(ADDED|DELTA|DONE)、`finishReason?`、`usage?`、保留 `id`/`model`。
- 删除 `ChatToolCallChunk`（tool_call 增量落在 chunk 的 itemType=tool_call 维度，靠 callId + itemIndex 区分多调用）。
- 4 dialect `parseStreamChunk` 改造为产出 item 增量；**补齐** OpenAi 流式 `delta.tool_calls` 解析（当前缺口）。
- `ChatServiceImpl.StreamAggregator` 与 `ChatStreamAccumulator` 改造为按 itemType/phase 状态机汇聚，产出与 326 `response.messages` 同构的消息序列。
- `ILlmDialect.buildStreamChunk` default 重写为基于新 chunk 字段。

## Non-Goals

- **不改** `ChatServiceImpl.callStream` 的 SSE 透传结构（`event:` 行仍不可达 dialect）；Responses 事件靠 `data:` 内 `type` 分派，本约束不解除（330 验证可行）。
- **不改** `ChatAssistantMessage.think/toolCalls` 字段（329）；StreamAggregator 产出的 `response.messages` 用拆分类型，旧 `message` 字段仍填充（双轨，过渡）。
- **不实现** ResponsesDialect 流式（330）。
- **不改** agent 引擎（327 已切非流式；agent 不直接消费 chunk）。

## Scope

### In Scope

- `nop-ai-api/.../chat/stream/ChatStreamChunk.java`、删 `ChatToolCallChunk.java`、`ChatStreamAccumulator.java`。
- `nop-ai-core/.../dialect/{OpenAi,Anthropic,Gemini,Ollama}Dialect.java` 的 `parseStreamChunk`、`ILlmDialect.buildStreamChunk` default。
- `nop-ai-core/.../service/ChatServiceImpl.java` 的 `StreamAggregator` / `ToolCallAccumulator`。
- 流式相关测试。

### Out Of Scope

- 非流式 dialect/agent（326/327 已完成）。
- 删除 `ChatAssistantMessage.think/toolCalls`（329）。
- Responses wire 解析（330）。

## Execution Plan

### Phase 1 - ChatStreamChunk item 增量模型 + 删 ChatToolCallChunk

Status: planned
Targets: `nop-ai-api/.../chat/stream/`

- Item Types: `Fix | Decision | Proof`

- [ ] `ChatStreamChunk` 改字段为 `itemType`(枚举 text|reasoning|tool_call)/`itemIndex`/`callId`/`delta`/`phase`(枚举 ADDED|DELTA|DONE)/`finishReason`/`usage`/`id`/`model`；删除 `role`/`content`/`thinking`/`toolCall` 折叠字段；`hasContent()`/`hasThinking()`/`hasToolCall()` 改为 `itemType` 判定。
- [ ] 删除 `ChatToolCallChunk.java`；移除 `ChatStreamChunk` 对其引用。
- [ ] 新增枚举 `StreamItemType`、`StreamItemPhase`（或内联常量，按代码库惯例）。
- [ ] golden test `TestChatStreamChunkSerialization`：ADDED/DELTA/DONE 三段式 round-trip；多 tool_call（不同 callId/itemIndex）chunk 独立。

Exit Criteria:

- [ ] `ChatStreamChunk` 新字段就绪，`ChatToolCallChunk` 已删除（grep 全仓无残留引用——本 Phase 必须先改掉 4 处引用中的 `ChatStreamChunk.java` 自身引用；其余 3 处在 Phase 2/3 同步）。
- [ ] golden test 覆盖三段式与多 tool_call。
- [ ] **无静默跳过**：新 chunk 字段真实承载增量，非 placeholder。

### Phase 2 - 4 dialect parseStreamChunk 改造 + OpenAi 流式 tool_calls 补齐

Status: planned
Targets: `nop-ai-core/.../dialect/{OpenAi,Anthropic,Gemini,Ollama}Dialect.java`

- Item Types: `Fix | Proof`

- [ ] OpenAiDialect.parseStreamChunk：`delta.content` → chunk(itemType=text, DELTA)；`delta.reasoning_content/thinking` → chunk(itemType=reasoning)；**新增**解析 `delta.tool_calls[]` → chunk(itemType=tool_call, callId, itemIndex=index, DELTA(arguments))，首见时发 ADDED；finish 信号 → chunk(DONE, finishReason)。
- [ ] AnthropicDialect.parseStreamChunk：`content_block_start/delta` 按 block type 映射 item（text_block→text、thinking→reasoning、tool_use→tool_call with callId）；`message_stop` → DONE。
- [ ] GeminiDialect.parseStreamChunk：parts 按 `thought:true` 映射 reasoning/text；functionCall 映射 tool_call（itemIndex 按出现序）。
- [ ] OllamaDialect.parseStreamChunk：`message.content`/`message.thinking`/`message.tool_calls` 映射对应 itemType。
- [ ] 4 dialect 既有流式测试更新断言（从 `chunk.getContent()/getThink()/getToolCall()` 改为 `itemType`/`delta`/`callId`），并保持场景语义。

Exit Criteria:

- [ ] 4 dialect parseStreamChunk 产出 item 增量；OpenAi 流式 tool_calls 缺口已补（新增测试覆盖）。
- [ ] `ILlmDialect.buildStreamChunk` default 基于新字段重写，gateway 路径（`AiDialectBackendMessageConverter.toFrontendStreamChunk`）回归通过。

### Phase 3 - StreamAggregator / ChatStreamAccumulator 改造

Status: planned
Targets: `nop-ai-core/.../service/ChatServiceImpl.java`（StreamAggregator/ToolCallAccumulator）、`nop-ai-api/.../chat/stream/ChatStreamAccumulator.java`

- Item Types: `Fix | Proof`

- [ ] `StreamAggregator`：按 itemIndex 维护 item 状态机（ADDED 建槽位 → DELTA 累加 → DONE 收尾）；itemType=text 收敛为 `ChatAssistantMessage`、reasoning 收敛为 `ChatReasoningMessage`、tool_call 收敛为 `ChatToolCallMessage`，按 itemIndex 顺序写入 `response.messages`；保留 `setMessage`（旧 message 字段，首个 text item 的视图，双轨过渡）。
- [ ] `ChatStreamAccumulator`：`accumulateToolCall` 改为按 itemType=tool_call + callId 累加；`getToolCalls` 改为从累加结果提取（或标 `@Deprecated` 委托）。
- [ ] `TestStreamAggregator` / `TestChatServiceImpl` 更新断言：汇聚后 `response.getMessages()` 含期望拆分类型。

Exit Criteria:

- [ ] StreamAggregator 产出的 `response.messages` 与非流式 dialect（326）产出的 messages 同构（同类型序列）。
- [ ] 多 tool_call 流式场景下，各 `ChatToolCallMessage` 的 arguments 完整拼接、callId 正确。
- [ ] **端到端验证**（Anti-Hollow）：`TestChatServiceImpl` 流式路径从 SSE `data:` 行 → parseStreamChunk → StreamAggregator → `response.messages` 完整跑通，断言含 `ChatToolCallMessage`。

## Closure Gates

- [ ] `ChatStreamChunk` item 增量模型落地，`ChatToolCallChunk` 删除。
- [ ] 4 dialect 流式改造 + OpenAi tool_calls 缺口补齐。
- [ ] StreamAggregator/ChatStreamAccumulator 改造，产出 messages 序列。
- [ ] `./mvnw compile`（nop-ai 全模块）通过。
- [ ] `./mvnw test -pl nop-ai -am` 全绿。
- [ ] **Anti-Hollow Check**：流式端到端（SSE data → chunk → aggregator → messages）实际连通，`scan-hollow-implementations.mjs --module nop-ai-core` 退出码 0。
- [ ] owner-doc：`ai-dev/design/nop-ai-responses-migration-design.md` §3.3 若有细化已回写；否则 `No owner-doc update required`。
- [ ] `ai-dev/logs/2026/08-02.md` 追加进度。
- [ ] 独立子 agent closure-audit 已记录证据。

## Risks And Rollback

- **风险 1（chunk 破坏面）**：`ChatStreamChunk` 字段变更是破坏性改动，4 dialect 流式 + StreamAggregator + Accumulator + gateway buildStreamChunk + 测试须同 Phase 对齐，否则编译失败。Phase 划分已把模型变更（P1）与消费方（P2/P3）紧邻，建议合并提交。
- **风险 2（OpenAi 流式 tool_calls 新增）**：这是补缺口（原永不填充），需确认 OpenAI SSE 的 `delta.tool_calls` 真实结构（含 `index`/`id`/`function.arguments` 增量）。
- **风险 3（多 tool_call 交错）**：流式下多个 function_call 的 arguments delta 交错出现，靠 itemIndex + callId 区分；exit criteria 多 tool_call 场景约束。
- **回滚**：流式改造集中，可整体 revert 回折叠模型；非流式链路（326/327）不受影响。

## Deferred But Adjudicated

### ChatStreamAccumulator.getToolCalls 旧访问

- Classification: `watch-only residual`
- Why Not Blocking Closure: 流式累加器已改 item 维度；旧 `getToolCalls` 若仍有调用点（grep 确认）标 `@Deprecated` 委托，329 统一删。
- Successor Required: yes
- Successor Path: `329-nop-ai-responses-migration-5-folded-fields-removal.md`

## Non-Blocking Follow-ups

- SSE `event:` 行透传到 dialect（解除架构约束）——仅在 330 发现 Responses 必须按 `event:` 分派时再评估，当前 `data:` 内 `type` 方案可行。

## Closure

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Evidence: <<Exit Criterion/Gate 验证 + `check-plan-checklist.mjs` 退出码 0 + `scan-hollow-implementations.mjs --module nop-ai-core` 退出码 0>>

Follow-up:

- <<完成时填写>>
