---
status: active
mission: nop-ai-agent-design-comparison
work-item: P2-ROUND4-CALL-PATH
group: "2026-09-15-0818"
verify: [test]
---

# P2 round-4 LLM 调用路径契约修复（nop-ai-core / nop-ai-api）：非流式 tool_calls 丢弃 / ChatUsage.copy NPE / callAsync null options / ToolCallAccumulator JSON 吞错 / ChatOptions.merge append

## Current Baseline

- 来源：deep-audit round 4 登记、roadmap `## Follow-up Backlog` 尚未勾选的 5 项 P2/P3 项（全部经 live repo 复核，HEAD `9cbf684d88`，2026-09-15）：
  1. **`OpenAiDialect.parseResponse` 非流式路径静默丢弃 `tool_calls`**（nop-ai-core `io.nop.ai.core.dialect.OpenAiDialect`）：`:196-197` 注释自认"保持现状"，5 个 dialect 中唯一不解析（Anthropic/Gemini/Ollama/Responses 均解析）；`ILlmDialect.parseResponse` javadoc 无此例外说明——OpenAI 非流式工具调用消费者拿到零工具调用零报错。
  2. **`ChatUsage.copy()` 对 null token 字段 NPE + 重算 totalTokens**（nop-ai-api `io.nop.ai.api.chat.messages.ChatUsage`）：`:53-57` 构造器 `promptTokens + completionTokens` 拆箱 NPE；生产形态 `AbstractLlmDialect.parseUsage` 的 `getIntByPath` 可返回 null（`:407-409`）；`ChatResponse.copy()`（:362）公开 API 对合法数据 NPE，且 copy 丢失 provider 上报的 totalTokens（可能含缓存 token）。
  3. **`ChatServiceImpl.callAsync` 对 `ChatRequest.options == null` NPE**（nop-ai-core `io.nop.ai.core.service.ChatServiceImpl`）：`:114-116` `request.getOptions().getStream()` 无守卫，null options 是文档化契约（`ChatRequest.getOptions` 可 null、ModelClassRouter/RuleBasedSelectionStrategy 均按 null 处理）；`new ChatRequest(messages)` 天然入口即炸。
  4. **`ChatOptions.merge()` 对 stop/tools 列表字段 append 而非覆盖**（nop-ai-api `io.nop.ai.api.chat.ChatOptions`）：`:413` merge 声明、`:429-447` 实际 `addAll`；javadoc 承诺"非null值会覆盖"，router 逐跳 `copy().merge(tierOptions)` 重复累加。
  5. **`ChatServiceImpl.ToolCallAccumulator` 工具参数 JSON 解析失败静默吞掉**（nop-ai-core ChatServiceImpl 内部类）：`:600-606` 畸形 args 落空 Map 无日志无错误码；下游无法区分 provider 发 `{}` 与畸形 JSON；AnthropicDialect 同场景显式抛错。
- 归属模块：nop-ai-core（1/3/5）+ nop-ai-api（2/4），均为 LLM 调用路径（dialect 解析 / usage 统计 / 请求构建 / 流式累积）上的契约正确性问题。
- 验证面：运行时行为修复，涉及 `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-api -am`；owner doc `ai-dev/design/nop-ai-agent/` 中 nop-ai-core 契约描述（`nop-ai-agent-reliability.md`、`02-execution-model.md`）如有相关描述需同步。

## Goals

- OpenAI 非流式响应中的 tool_calls 与其余 4 个 dialect 一致地被解析并回填到 `ChatResponse`——消费者不再拿到"零工具调用零报错"的静默丢失；`ILlmDialect.parseResponse` javadoc 与该行为一致。
- `ChatUsage.copy()` 对 null token 字段 null-safe，且保留 provider 上报的 totalTokens（不重算丢失缓存 token）；`ChatResponse.copy()` 对合法 null-token 数据不再 NPE。
- `ChatServiceImpl.callAsync` 对 null options 按文档化契约 null-safe 处理（等价于无 options），不再 NPE。
- `ChatOptions.merge()` 对列表字段（stop/tools）执行覆盖而非 append（与 javadoc 承诺一致），router 逐跳合并不再重复累加。
- `ToolCallAccumulator` 对畸形工具参数 JSON 显式处理（日志 + 与 `{}` 可区分），不再静默落空 Map。
- 每项配套回归测试（正确结果断言，非仅"不报错"）；owner docs 同步；`node ai-dev/tools/check-doc-links.mjs --strict` 0 error。

## Non-Goals

- 不改非 OpenAI 的 4 个 dialect 既有解析行为；不重构 `ChatUsage`/`ChatOptions` 的存储形态或公开 API 签名。
- 不处置本轮 roadmap 其余未勾选项（toolkit/agent/gateway/shell/service/coder 相关 P2/P3 项、可靠性面修复与死面裁定，另开计划）。
- 不运行 mvn 全量构建；不改 `ILlmDialect` 接口签名。

## Phase 1 — OpenAiDialect 非流式 tool_calls 解析 + 契约文档

Status: planned

Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/OpenAiDialect.java`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/AbstractLlmDialect.java`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/ILlmDialect.java`、对应测试类

- Item Types: `Fix | Proof`

- [x] `Fix` `OpenAiDialect.parseResponse`（:169-197 区域）：非流式路径解析 `tool_calls`（对齐 Anthropic/Gemini/Ollama/Responses 的解析形态：`tool_calls[].id`/`function.name`/`function.arguments` → `ChatToolCall` 列表回填 `ChatResponse.toolCalls`），删除"保持现状"注释。
- [x] `Fix` `ILlmDialect.parseResponse` javadoc 与实现一致（无"不解析"例外说明残留）；`OpenAiDialect` 头部或解析点注释说明非流式路径与流式路径的 tool_calls 覆盖范围。
- [x] `Fix` 回归测试：构造含 `tool_calls` 的 OpenAI 非流式响应体 → 断言 `ChatResponse.getToolCalls()` 含预期 name/arguments/id；不含 tool_calls 的响应体 → 返回空列表不报错；与 Anthropic/Gemini 对应测试形态对齐。
- [x] `Proof` 复核：5 个 dialect 的 parseResponse tool_calls 解析一致性（逐 dialect 读码对比解析字段与回填形态）；`ChatResponse.toolCalls` 字段与流式累积器（ToolCallAccumulator）的消费路径不冲突。

Exit Criteria:

- [x] OpenAI 非流式响应 tool_calls 与其余 4 dialect 一致解析（对照表 + 回归测试断言具体字段值）。
- [x] 无 tool_calls 的响应体不报错、返回空（回归测试负例）。
- [x] **端到端验证**：`OpenAiDialect.parseResponse` 输入 OpenAI 响应体 → 解析 → `ChatResponse.toolCalls` 可被下游（如 ChatServiceImpl 工具派发路径）消费的完整链路（测试断言链路连通）。
- [x] **接线验证**：解析结果确实回填到 `ChatResponse.toolCalls` 并被调用方读取（非孤立方法测试）。
- [x] **无静默跳过**：解析异常路径不静默吞（与 ToolCallAccumulator 处置一致，见 Phase 3）。
- [x] owner doc 更新：如 `ai-dev/design/nop-ai-agent/` 中 dialect 契约描述与本次行为变化相关则同步，否则显式写 `No owner-doc update required`。
- [x] `./mvnw test -pl nop-ai/nop-ai-core -am` 通过。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Phase 2 — ChatUsage.copy null 安全 + totalTokens 保真

Status: planned

Targets: `nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/messages/ChatUsage.java`、`nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/ChatResponse.java`、对应测试类

- Item Types: `Decision | Fix | Proof`

- [x] `Decision` copy 语义裁定：(A) 复制时显式拷贝 totalTokens（保留 provider 上报值，可含缓存 token）+ null-safe 处理 prompt/completion null（不重建、不重算）；或 (B) copy 后按现有字段重算并保留 null 传播（记录理由与备选；推荐 (A)——totalTokens 可能是 provider 上报的含缓存统计，重算会丢失信息）。
- [x] `Fix` 按裁定落地 `ChatUsage.copy()`：null token 字段不 NPE（构造器或 copy 路径 null-safe），totalTokens 保真复制。
- [x] `Fix` `ChatResponse.copy()`（:362 区域）对含 null token 的合法 ChatUsage 不 NPE（经 ChatUsage.copy 修复后验证或就地守卫）。
- [x] `Fix` 回归测试：null promptTokens/null completionTokens 的 ChatUsage → copy() 不 NPE 且字段 null 保真；含非 null 值的 usage → copy() 全字段相等且 totalTokens 与原始一致（含缓存 token 场景：promptTokens+completionTokens ≠ totalTokens 时 totalTokens 不被重算覆盖）。
- [x] `Proof` 复核：`AbstractLlmDialect.parseUsage`（:407-409）可产出 null token 的生产路径确认；全仓 `ChatUsage.copy`/`ChatResponse.copy` 调用点清单核对（无新 NPE 暴露面）。

Exit Criteria:

- [x] copy() 对 null token 字段 null-safe（回归测试断言不 NPE + null 保真）。
- [x] copy() 保留 provider 上报 totalTokens（含缓存 token 场景断言）。
- [x] **端到端验证**：parseUsage（null 路径）→ ChatResponse → copy() 完整链路不 NPE 且 totalTokens 保真。
- [x] **接线验证**：copy() 的消费方（ChatResponse.copy 调用点）确实经修复路径运行。
- [x] **无静默跳过**：null 字段不静默补 0 或吞掉（显式 null 保真或显式默认，与裁定一致）。
- [x] owner doc 更新：如 `nop-ai-api` 相关契约文档描述 copy 语义则同步；否则显式写 `No owner-doc update required`。
- [x] `./mvnw test -pl nop-ai/nop-ai-api -am` 通过（含依赖模块编译）。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Phase 3 — callAsync null options 守卫 + ToolCallAccumulator JSON 显式处置

Status: planned

Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java`、对应测试类

- Item Types: `Fix | Proof`

- [x] `Fix` `ChatServiceImpl.callAsync`（:113-116）：`request.getOptions()` 为 null 时按无 options 语义处理（stream 默认值路径与 ModelClassRouter/RuleBasedSelectionStrategy 的 null 处理对齐），不再 NPE。
- [x] `Fix` `ToolCallAccumulator`（:600-606）：JSON 解析失败时显式处置——记录 WARN 日志（含 tool call id/name）+ 错误与 `{}` 可区分（如错误码或标记字段），对齐 AnthropicDialect 同场景的显式抛错姿态（具体形态以裁定为准：日志+空 Map 但可区分 vs 抛错）。
- [x] `Fix` 回归测试：`new ChatRequest(messages)`（null options）→ callAsync 正常走非流式/默认路径不 NPE；畸形 args JSON → 有日志/错误信号且与合法 `{}` 结果可区分（断言具体行为）；合法 JSON → 正常解析。
- [x] `Proof` 复核：callAsync 全路径（流式/非流式/错误路径）options 解引用点清单；ToolCallAccumulator 消费方（:534 区域）对新增区分信号的处理。

Exit Criteria:

- [x] null options 的 callAsync 不 NPE（回归测试经公开入口）。
- [x] 畸形工具参数 JSON 不再静默落空 Map（断言日志/错误信号存在且与 `{}` 可区分）。
- [x] **端到端验证**：`new ChatRequest(messages)` → callAsync → 响应返回完整链路；畸形 args → 累积 → 工具派发入口可见区分的完整链路。
- [x] **接线验证**：null options 守卫确实在 callAsync 公开入口生效；ToolCallAccumulator 处置确实被流式累积路径调用。
- [x] **无静默跳过**：畸形 JSON 不吞错（显式日志/错误码），null options 不静默造默认对象掩盖错误。
- [x] owner doc 更新：如 `ai-dev/design/nop-ai-agent/` 相关契约描述受影响则同步；否则显式写 `No owner-doc update required`。
- [x] `./mvnw test -pl nop-ai/nop-ai-core -am` 通过。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Phase 4 — ChatOptions.merge 列表字段覆盖语义

Status: planned

Targets: `nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/ChatOptions.java`、对应测试类

- Item Types: `Decision | Fix | Proof`

- [x] `Decision` merge 语义裁定：(A) stop/tools 列表字段改为覆盖（与 javadoc "非null值会覆盖"一致，router 逐跳合并不再重复累加）；或 (B) javadoc 改为声明 append（保留累加行为，说明 router 重复累加是预期）；记录理由与备选（推荐 (A)——javadoc 承诺即契约，append 是文档与实现漂移；需核对 ChatOptions 其他列表字段是否同模式）。
- [x] `Fix` 按裁定落地 `ChatOptions.merge()`（:413-447）：列表字段覆盖语义（或 javadoc 改写，视裁定）。
- [x] `Fix` 回归测试：merge 后 stop/tools 为后者值（覆盖）而非两者拼接；null 字段不覆盖；非列表字段（如 temperature）覆盖语义不变。
- [x] `Proof` 复核：ChatOptions 全部字段的 merge 行为清单（哪些覆盖/哪些拼接/哪些 null 保留）；router 逐跳 `copy().merge(tierOptions)` 消费路径复核与裁定一致。

Exit Criteria:

- [x] merge() 列表字段行为与裁定一致（覆盖或显式声明 append），javadoc 与实现无漂移。
- [x] 回归测试断言具体列表结果（非仅"不报错"）。
- [x] **端到端验证**：router 逐跳 merge（copy → merge tierOptions）→ 请求构建的完整链路中 stop/tools 无重复累加（或按裁定语义断言）。
- [x] **接线验证**：merge 的 router 消费路径确实按新语义运行。
- [x] **无静默跳过**：无静默合并歧义（覆盖/拼接语义显式化）。
- [x] owner doc 更新：如相关契约文档描述 merge 语义则同步；否则显式写 `No owner-doc update required`。
- [x] `./mvnw test -pl nop-ai/nop-ai-api -am` 通过（含依赖模块编译）。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Closure Gates

> 关闭条件记录（01-file-ledger §4.3 消解为 §5.2 完成公式派生）：5 项 in-scope confirmed live defects / contract drifts 全部收口——Phase 1 OpenAI 非流式 tool_calls 解析（`OpenAiDialect.parseResponse` 解析 `choices.0.message.tool_calls[]` → `ChatToolCallMessage` 回填，畸形 args WARN+null 标记，`ILlmDialect.parseResponse` javadoc 声明 5 方言一致）+ 3 回归测试；Phase 2 `ChatUsage.copy()` null-safe + totalTokens 保真（裁定 A：逐字段复制不重算，构造器拆箱 null-safe，`ChatResponse.copy()` 不再 NPE）+ 5 回归测试；Phase 3 callAsync null options 守卫（文档化契约 null-safe，默认 stream 路径）+ `ToolCallAccumulator` 畸形 JSON 显式处置（WARN 日志 + null 标记与合法 `{}` 可区分）+ 3 回归测试；Phase 4 `ChatOptions.merge()` 列表字段覆盖语义（裁定 A：stop/tools 整体覆盖，javadoc 无漂移）+ 4 回归测试；各 Phase Exit Criteria 全数勾选（含端到端验证、接线验证、无静默跳过），无被静默降级到 deferred / follow-up 的 in-scope live defect；受影响 owner docs 逐篇检索确认无相关契约描述需同步（`No owner-doc update required`）；独立子 agent closure-audit 由下游 CLOSURE_AUDIT 步骤 dispatch（本 plan 不自行 dispatch）；`./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-api -am` 通过（nop-ai-core 394 + nop-ai-api 57，0 失败 0 错误）+ 跨模块下游 nop-ai-agent router / nop-ai-gateway failover 全绿；`node ai-dev/tools/check-doc-links.mjs --strict` exit=0（0 errors，9 warnings 全为其他历史计划存量）——机械验证/审计收口由 `## Verification` pass 行与 `## Closure` 收口记录派生。

- 5 项 in-scope confirmed live defects / contract drifts（OpenAI 非流式 tool_calls 静默丢弃 / ChatUsage.copy null NPE + totalTokens 重算 / callAsync null options NPE / ChatOptions.merge 列表 append / ToolCallAccumulator JSON 吞错）已修复并有回归测试断言正确结果（15 例：Phase 1 +3、Phase 2 +5、Phase 3 +3、Phase 4 +4）
- 全部 Phase Exit Criteria 勾选完成，无静默降级到 deferred 的 in-scope 缺陷
- 受影响 owner docs（`ai-dev/design/nop-ai-agent/` 相关契约文档）已逐篇检索确认无相关契约描述需同步（`No owner-doc update required`）
- `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-api -am` 通过（nop-ai-core 394 + nop-ai-api 57，0 失败 0 错误）+ 跨模块下游 nop-ai-agent router / nop-ai-gateway failover 全绿；`node ai-dev/tools/check-doc-links.mjs --strict` 0 error
- 独立子 agent closure audit 已完成并记录证据（Anti-Hollow：运行时调用链连通、无空壳/静默跳过）——机械验证/审计收口由 `## Verification` pass 行与 `## Closure` 收口记录派生（本 section 不保留可写 checkbox，01-file-ledger §4.3 消解为 §5.2 完成公式）

## Draft Review Record

（空，由独立 reviewer 填写；drafter 不自行 dispatch）
- dispatch review #review-2026-09-14-110620-mission-driver-2026-09-15-0818-1-p2-round4-call-path-contract-1-3a81c7df to opencode-pid-90575
- 2026-09-15：iteration 1，共识 approved #review-2026-09-14-110620-mission-driver-2026-09-15-0818-1-p2-round4-call-path-contract-1-3a81c7df

## Verification

- pass test 20260915-0853 exit=0 (nop-ai-api targeted: TestChatOptions/TestChatResponse/TestChatUsageCopy, 0 failures)
- pass test 20260915-0856 exit=0 (nop-ai-core targeted: TestOpenAiDialect/TestStreamAggregator/TestChatServiceImplCallPathContract/TestChatUsage, 0 failures)
- pass test 20260915-0858 exit=0 (`./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-api -am` — nop-ai-core 394 + nop-ai-api 57, 0 failures 0 errors)
- pass test 20260915-0859 exit=0 (downstream nop-ai-agent router: TestSmartModelRouter 17/9/2, 0 failures)
- pass test 20260915-0900 exit=0 (downstream nop-ai-gateway failover + full gateway suite, 0 failures 0 errors)
- pass doclinks 20260915-0100 exit=0 (`node ai-dev/tools/check-doc-links.mjs --strict` — 0 errors, 9 warnings all pre-existing in other historical plans)
- pass test 20260915-0907 exit=0 (CLOSURE_AUDIT 复跑 `node ai-dev/tools/check-doc-links.mjs --strict` — 0 errors, 9 warnings all pre-existing in other historical plans)
- pass test 20260915-1118 exit=0 (CLOSURE_AUDIT round 2 本 visit 实跑 `node ai-dev/tools/check-doc-links.mjs --strict` — 0 errors, 11 warnings all pre-existing in other historical plans, none in this plan file)

## Closure

- dispatch audit #audit-20260915-0907-2026-09-15-0818-1-p2-round4-call-path-contract-1-9a6ab9cd to opencode-pid-4319 models={exec:opencode-go/deepseek-v4-flash,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-20260915-0907-2026-09-15-0818-1-p2-round4-call-path-contract-1-9a6ab9cd：审计通过——5 项 in-scope P2/P3 缺陷（OpenAI 非流式 tool_calls 静默丢弃 / ChatUsage.copy null NPE+totalTokens 重算 / callAsync null options NPE / ChatOptions.merge 列表 append / ToolCallAccumulator JSON 吞错）全部修复且有回归测试断言正确结果（15 例：Phase 1 +3、Phase 2 +5、Phase 3 +3、Phase 4 +4），无空壳/静默跳过，无被降级缺陷；`pass test 20260915-0907 exit=0`（doc-links --strict 0 errors）+ 既有 `pass test 20260915-0853/0856/0858/0859/0900 exit=0`（nop-ai-core 394 + nop-ai-api 57 + 下游 router/gateway 全绿）
- dispatch audit #audit-20260915-1118-2026-09-15-0818-1-p2-round4-call-path-contract-2-269d705f to opencode-pid-27226 models={exec:opencode-go/deepseek-v4-flash,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-20260915-1118-2026-09-15-0818-1-p2-round4-call-path-contract-2-269d705f：round 2 独立收口复核通过——SCRIPT_CHECK 原 FAIL 根因（`## Closure Gates` 5 个列 0 checkbox 位于计数域外，01-file-ledger §2.5/§4.3）已修复为纯 prose 记录；权威引擎 plan-check `--strict` exit=0（49/49 checked / 0 unchecked）+ `deriveCompleted` completed: true（ledgerValid/statusActive/allChecked/mechanicalVerification/auditReceipt/dispatchRegister 全 true，0 reasons）；本 visit 实跑 `node ai-dev/tools/check-doc-links.mjs --strict` exit=0（0 errors，11 warnings 全为其他历史计划存量，本计划文件零告警）+ 语义复核 4 Phase 全过——OpenAiDialect.parseResponse:204 非流式 tool_calls 回填 ChatToolCallMessage（TestOpenAiDialect 非流式正例/负例/畸形 args）、ChatUsage.copy():132-141 逐字段复制 null 保真 + totalTokens 保真（TestChatUsageCopy 4 例 + TestChatResponse.copy:167 例）、callAsync:118-119 null options 守卫（TestChatServiceImplCallPathContract 经公开入口）+ ToolCallAccumulator.toToolCall():607-613 畸形 JSON WARN `nop.ai.tool-call-args-parse-fail` + null 标记（TestStreamAggregator:278 断言 WARN + null/`{}` 可区分）、ChatOptions.merge():434-445 stop/tools 整体覆盖（TestChatOptions 断言后者覆盖非拼接）；daily log 09-15.md 已记录 + roadmap 5 项勾选；无 in-scope defect 被降级