# 325 nop-ai Responses 迁移 1：消息类型体系扩展 + 序列化基础设施

> Plan Status: draft
> Last Reviewed: 2026-08-02
> Source: `ai-dev/design/nop-ai-responses-migration-design.md`（设计结论 #3/#6/#7/#11）；`ai-dev/analysis/2026-08/2026-08-01-openai-responses-vs-chat-completions.md`（已 superseded，事实部分有效）
> Related: 本计划是 nop-ai Responses 迁移系列的**第 1 份**，后续 326→327→328→329→330 依次依赖。系列总览见 design doc §五「实施路径」。迁移根因与拒绝方案见 design doc §二/§四。

## Purpose

把 nop-ai 消息模型从「角色折叠」朝「类型开放（拆分模型）」推进**第一步**：新增 `ChatToolCallMessage` / `ChatReasoningMessage` 两个独立消息类型，把 `ChatToolResponseMessage` 的关联字段重命名为 `callId` 并调整序列化 type 标识，删除零引用的兜底类型 `ChatCustomMessage`，并交付 `ApiStyle.RESPONSES` 枚举值与 `normalizeFinishReason` 扩展这两个 Responses 接入的前置基建。

**本计划严格只做「新增 + 最小破坏」**：`ChatAssistantMessage.think/toolCalls` 寄居字段**保留不动**（不加 `@Deprecated`，避免编译 warning 泛滥，留待 329 删除），4 个 dialect / agent 引擎**不改**。退出时整个 nop-ai 必须编译通过 + 全部既有测试通过。

## Current Baseline

> 锚点均来自 live repo 摸底（2026-08-02）。

- `ChatMessage` 抽象基类 `nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/messages/ChatMessage.java:20` 用 `@JsonTypeInfo(property="role")` 序列化，`:21-27` `@JsonSubTypes` 注册 user/assistant/system/tool/custom 五型。
- `ChatAssistantMessage`（同包）`:31 think` / `:36 thinkSignature` / `:41 toolCalls` 三个寄居字段——**本计划不动**。
- `ChatToolResponseMessage`（同包）`:25 toolCallId`（关联字段，本计划改名 `callId`）、`:35 content`、`:42 result`、`:47 resultType`；`:60 getRole()` 返回 `"tool"`；`:123 fromToolCall(...)` 用 `toolCall.getId()` 填 `toolCallId`。
- `ChatCustomMessage`（同包）`:29 content` / `:34 customRole` / `:38 extensions`；`:50 getRole()`。**引用面仅 `ChatMessage.java:26` 的 @JsonSubTypes + 自身**，零生产/测试引用，删除安全。
- `ApiStyle` 枚举 `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/model/ApiStyle.java:21-52`：openai/ollama/anthropic/gemini/other，**无 responses**。
- `AbstractLlmDialect.normalizeFinishReason` `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/AbstractLlmDialect.java:285-309`：当前认 stop/end_turn/length/max_tokens/content_filter/tool_calls 等，**不认 Responses 的 `completed`/`incomplete`**。
- `ChatToolCall`（同 messages 包）`:26 id` / `:31 name` / `:36 arguments(Map)`——`ChatToolCallMessage` 复用其字段语义。
- 持久化路径：`AgentSessionLifecycle` 把消息序列化到 File/DB session store；`TestFileBackedSessionStore` / `TestDBSessionStore` 用 instanceof 断言（非字节对比），序列化字段变更相对安全但需 round-trip 验证。

## Goals

- 新增 `ChatToolCallMessage`（type 标识 `tool_call`：callId + name + arguments）与 `ChatReasoningMessage`（type 标识 `reasoning`：summary）。
- `ChatToolResponseMessage`：type 标识从 `tool` 改为 `tool_output`；关联字段 `toolCallId` 改名为 `callId`（保留 `getToolCallId()` 作 `@Deprecated` 委托 getter，避免破坏 `fromToolCall` 与既有引用）。
- 删除 `ChatCustomMessage`（仅从 `ChatMessage.java:26` @JsonSubTypes 注销 + 删自身文件）。
- `ApiStyle` 新增 `RESPONSES` 枚举值（**仅枚举值，不注册 dialect**——ResponsesDialect 在 330 落地）。
- `AbstractLlmDialect.normalizeFinishReason` 扩展：`completed → stop`、`incomplete → length`。
- 交付新消息类型的序列化 golden test（round-trip：对象 → JSON → 对象 等价）。

## Non-Goals

- **不新增** `ChatResponse.messages` 列表（326）。
- **不改** `ChatOptions.responseFormat` 类型（326）。
- **不改** `ChatUserMessage` 多模态 content parts（326）。
- **不动** `ChatAssistantMessage.think/toolCalls/thinkSignature`、4 个 dialect、agent 引擎、流式模型（327/328/329）。
- **不实现** `ResponsesDialect`（330）。
- **不改** `ChatMessage` @JsonTypeInfo 的 `property` 名（仍为 `"role"`）——仅扩展 @JsonSubTypes 注册条目，避免持久化 JSON 字段名变更。property 从 role→type 的最终切换留待 329。

## Scope

### In Scope

- `nop-ai-api/.../chat/messages/`：新增 2 个消息类、改 `ChatToolResponseMessage`、删 `ChatCustomMessage`、扩 `ChatMessage` @JsonSubTypes。
- `nop-ai-core/.../model/ApiStyle.java`：新增 `RESPONSES`。
- `nop-ai-core/.../dialect/AbstractLlmDialect.java`：扩 `normalizeFinishReason`。
- `nop-ai-api/src/test/...`：新增序列化 golden test。

### Out Of Scope

- 任何 dialect 的 `buildBody`/`parseResponse`/`convertMessage` 改造（326）。
- `ChatToolCallChunk` / `ChatStreamChunk` 流式模型（328）。
- agent 引擎与 85 个 mock-LLM 测试（327）。

## Execution Plan

### Phase 1 - 新增消息类型 + type 标识扩展 + 删 ChatCustomMessage

Status: planned
Targets: `nop-ai-api/.../chat/messages/`（main + test）

- Item Types: `Fix | Decision | Proof`

- [ ] 新增 `ChatToolCallMessage extends ChatMessage`：字段 `callId`/`name`/`arguments(Map<String,Object>)`；`getRole()` 返回 `"tool_call"`（过渡期 role 值，329 统一为 type 语义）；提供 `fromChatToolCall(ChatToolCall)` 工厂复用 `ChatToolCall.getId()/getName()/getArguments()`。
- [ ] 新增 `ChatReasoningMessage extends ChatMessage`：字段 `summary`(String，必填) + 可选 `detail`(String)；`getRole()` 返回 `"reasoning"`；`getContent()` 返回 summary。
- [ ] `ChatMessage.java:21-27` @JsonSubTypes：注册 `ChatToolCallMessage → "tool_call"`、`ChatReasoningMessage → "reasoning"`；`ChatToolResponseMessage` 注册 key 从 `"tool"` 改 `"tool_output"`；移除 `ChatCustomMessage → "custom"` 注册行。
- [ ] `ChatToolResponseMessage`：字段 `toolCallId` 改名 `callId`；`getRole()` 返回 `"tool_output"`；保留 `getToolCallId()` 标 `@Deprecated` 委托 `getCallId()`；`fromToolCall(...)` 内部改用 `setCallId`。
- [ ] 删除 `ChatCustomMessage.java`（确认无引用后）。
- [ ] 新增 golden test `TestChatMessageSerialization`（nop-ai-api test）：对 user/assistant/system/tool_call/tool_output/reasoning 六型分别做 `writeValueAsString` → `readValue` round-trip 断言等价；含 `ChatToolCallMessage`/`ChatReasoningMessage` 多实例与 callId 关联字段。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。

- [ ] `ChatToolCallMessage` / `ChatReasoningMessage` 存在且 golden test 通过 round-trip 断言。
- [ ] `ChatToolResponseMessage.getCallId()` 可用，`getToolCallId()` 标 `@Deprecated` 且委托正确（既有调用点编译通过）。
- [ ] `ChatCustomMessage` 类文件已删除，全仓 grep 无残留引用。
- [ ] `ApiStyle.RESPONSES` 枚举值存在；`LlmDialectFactory` **未** 为 RESPONSES 注册 dialect（330 才注册）。
- [ ] `normalizeFinishReason("completed")` 返回 `"stop"`、`normalizeFinishReason("incomplete")` 返回 `"length"`，由新增/扩展的 `TestLlmDialectErrorResponse` 或新 `TestNormalizeFinishReason` 覆盖。
- [ ] **无静默跳过**：新类型的 `getContent()`/`getCallId()` 等公共方法返回真实字段，非 placeholder。
- [ ] 持久化 round-trip 验证：`TestFileBackedSessionStore` / `TestDBSessionStore` 既有测试仍绿（消息序列化字段名 `role` 未变，仅新增类型 + tool_output key 变更，需确认旧 session 数据格式不受影响——本计划为首次落地，无历史数据迁移负担）。
- [ ] owner-doc 更新：`ai-dev/design/nop-ai-responses-migration-design.md` 若有 type 标识命名差异已回写；否则明确 `No owner-doc update required`（design 已为最终设计）。
- [ ] `ai-dev/logs/2026/08-02.md` 追加本 plan 进度条目。

### Phase 2 - 全模块编译 + 全测试回归

Status: planned
Targets: `nop-ai/**`

- Item Types: `Proof`

- [ ] `./mvnw clean install -pl nop-ai/nop-ai-api -am -T 1C`（编译 api 层 + 依赖）
- [ ] `./mvnw test -pl nop-ai -am`（全 nop-ai 模块测试，含 core/agent/gateway）

Exit Criteria:

- [ ] `nop-ai-api` 编译零 error、零新增 warning。
- [ ] `./mvnw test -pl nop-ai -am` 全绿（既有 dialect/agent/gateway 测试均不受影响，因寄居字段未动）。
- [ ] 新增 golden test 在上述命令中实际执行且通过（非 skipped）。

## Closure Gates

- [ ] 所有 in-scope 新增类型落地且 golden test 覆盖。
- [ ] `ChatCustomMessage` 已删除且无残留引用。
- [ ] `ApiStyle.RESPONSES` + `normalizeFinishReason` 扩展落地并有测试。
- [ ] `./mvnw compile`（nop-ai 全模块）通过。
- [ ] `./mvnw test -pl nop-ai -am` 全绿。
- [ ] checkstyle / 代码规范检查通过。
- [ ] **Anti-Hollow Check**：新消息类型是真实可序列化数据载体（非空壳），golden test 证明 round-trip 成立。
- [ ] 独立子 agent closure-audit 已完成并记录证据（见 Closure）。
- [ ] 无 in-scope live defect 被降级到 deferred。

## Risks And Rollback

- **风险 1（序列化兼容）**：@JsonSubTypes 注册 key 变更（`tool`→`tool_output`）。本计划为首次落地、无历史持久化数据，风险可控；若发现旧测试固化了 `"tool"` 字面量，需同步更新测试 JSON fixture。
- **风险 2（callId 改名）**：保留 `@Deprecated getToolCallId()` 委托，确保 `AgentToolDispatcher`/`ChatToolResponseMessage.fromToolCall` 等既有引用编译通过。
- **回滚**：本计划为纯新增 + 单类删除，回滚 = revert 单次提交集合；不涉及数据迁移。

## Deferred But Adjudicated

### @JsonTypeInfo property 从 role→type 的最终切换

- Classification: `out-of-scope improvement`（归属后续计划）
- Why Not Blocking Closure: 本计划目标是新增类型不破坏既有；改 property 名会影响全部持久化 JSON 字段名，应与 329「删除寄居字段」一并收敛，那时序列化格式统一为最终形态。
- Successor Required: yes
- Successor Path: `329-nop-ai-responses-migration-5-folded-fields-removal.md`

## Non-Blocking Follow-ups

- 为 `ChatToolCallMessage` / `ChatReasoningMessage` 补充更多边界 golden 用例（空 arguments、超长 summary）——优化项。

## Closure

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent，fresh session>>
- Evidence: <<每条 Exit Criterion / Closure Gate 的 PASS/FAIL + live code path / test name；`node ai-dev/tools/check-plan-checklist.mjs` 退出码 0；`scan-hollow-implementations.mjs --module nop-ai-api` 退出码 0>>

Follow-up:

- <<完成时填写；confirmed live defect 不得出现在这里>>
