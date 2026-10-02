# 2284 WI3 占位 tool result 协议（派生态渲染）

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI3（Deliverable: 代码变更 + 测试；deps: WI2）
> Baseline Ruling: `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md`（WI3 本 roadmap 吸收）
> Design Contract: `ai-dev/design/nop-ai-agent/nop-ai-agent-async-tool-execution.md`（§三.2 三不变量 / §三.3 唤醒轮语义 / §三.4 provider 构造契约）

## Purpose

落地占位 tool result 协议的三条不变量与占位渲染构件：①派生态渲染（占位仅在请求构造期存在，不入 session 历史）；②真实结果按 callId 原位插入 tool_call 之后（非尾部追加）；③崩溃恢复的开放 tool_call 集合 = 快照消息 × TOOL_EXECUTION checkpoint 差集可判定。**含一项执行中发现的 live defect 修正**：AnthropicDialect 请求侧工具消息形态与 Anthropic API 契约相悖（见 Current Baseline），该缺陷阻塞 WI2 必裁①的"合法构造形态"成立，属本协议的请求构造面，一并修正。WI4（心跳）/WI5（steering 唤醒）消费本项交付的渲染与检测构件。

## Current Baseline

（执行时 HEAD `e64b744f75`，2026-10-02 复核 + 对抗性审查 live 修正）

- 请求构造：`ReActAgentExecutor.prepareRoutedRequest` 以 `new ChatRequest(new ArrayList<>(ctx.getMessages()))` 直接承载 ctx 消息（`ReActAgentExecutor.java:921`）——ctx 消息顺序即请求消息顺序，无占位渲染层。
- 工具调用消息追加：`appendToolCallMessages`（`ReActAgentExecutor.java:588-599`）把响应中的 `ChatToolCallMessage` 逐条 append 到 ctx 尾部（assistant 消息之后）。
- 结果提交：`AgentToolDispatcher.commitToolResults`（`AgentToolDispatcher.java:343-487`）按 future 顺序逐个 `f.join()`（:352），POST_TOOL_ATTEMPT veto（:357-369）→ BEFORE_TOOL_RESULT_PROCESSED reenter 路径以 `ctx.addMessage(fromToolCall(...))` **尾部追加**（:409-410，AR-03 用真实 callId 保配对，`continue` 跳过 checkpoint 与 session sync）→ 正常路径 `ctx.addMessage(toolResponse)` 尾部追加（:416）→ TOOL_EXECUTION checkpoint（:427-441，callId 幂等键）→ persisted-session 同步（:452-458）。7-checkpoint 安全链 deny 路径在 dispatch 循环内即时尾插配对错误 result（`AgentSecurityConsultation.java` 七条 deny 路径），**不写 TOOL_EXECUTION checkpoint**。当前等待期不可能有消息插入（`awaitToolFutures` 同步阻塞），尾部追加天然紧随 tool_call——不变量②在现状下未显式成立，WI4/WI5 引入等待期消息后必须显式化。
- 消息类型契约：`ChatToolCallMessage`（callId/name/arguments，role="tool_call"）与 `ChatToolResponseMessage`（callId/name/content，**role="tool_output"**，`ChatToolResponseMessage.java:64`；工厂 `fromToolCall`/`error`/`structured` :127-144）在 nop-ai-api；`AgentExecutionContext.messages` 可变列表（`:26`），`addMessage` 尾部追加（`:127-129`）。**构件实现一律用 instanceof 类型判定，禁止 role 字符串匹配**。
- 【执行中发现 live defect】AnthropicDialect 请求侧工具消息形态与 Anthropic API 契约相悖，三处：① `getRole` 对 `ChatToolResponseMessage` 返回 base role **"tool"**（`AbstractLlmDialect.getBaseRole:279-280` + `AnthropicDialect.getRole:626-631`），Anthropic API 只接受 user/assistant；② `convertMessage` 先生成 text 块再追加 tool_result 块（`AnthropicDialect.java:566-571` 先于 `:594-601`），与契约"tool_result 先于文本块"相反；③ `buildBody` 对消息 1:1 转换无同角色相邻轮合并（`:266-282` 附近），assistant 文本与 tool_use 块、多个 tool_output 各自成轮。该缺陷未被现有测试暴露（`TestAnthropicDialect` 无工具消息场景）。OpenAiDialect 侧经核实合规：1:1 保序（`OpenAiDialect.buildMessages:441-459`）、`ChatToolCallMessage`→assistant+tool_calls（:414-421）、`ChatToolResponseMessage`→role "tool"+tool_call_id（:423-427）、user 消息独立——**OpenAI 无需变更**。
- checkpoint 读取面：`ICheckpointManager` 接口只有 `saveCheckpoint/getLatestCheckpoint/getCheckpoint/remove`，**无 per-session 列表方法**；`getCheckpoints(sessionId)` 在三个功能实现上已存在（`FileBackedCheckpointManager:230`、`DBCheckpointManager:276`、`ToolExecutionCheckpoint:115`）但不在接口上；`NoOpCheckpoint` 无此方法。`CheckpointJournalReader` 只有 `readAll/readAfter(Path)` 且 journal path 由 FileBacked 实现私有持有（:352），不是本消费点的正确数据源。
- 恢复路径：`AgentSessionLifecycle.restoreSession:582` → `verifyLatestCheckpoint`（**:638-690**）做 checkpoint.messageCount ≤ session.messageCount 一致性告警，无开放 tool_call 集合判定。
- 测试基建：`TestEngineExtractedSecurityAndDispatch.java:231-233,259-262` 已有直接构造 `AgentToolDispatcher` 单测先例（sessionStore/checkpoint 传 null/NoOp）；`TestRestoreSession.java` 已有 FileBacked store+checkpoint tempDir 全套 restore harness。
- WI2 裁定锚点：占位渲染为唤醒请求构造期行为（`nop-ai-agent-async-tool-execution.md` §三.2/§三.4），请求构造形态 = 占位 tool_result 紧随 tool_use 同一 user 轮、文本后置、同角色相邻轮合并（Anthropic）；占位不经过安全链/PRE/POST_TOOL_ATTEMPT/POST_CALL（§三.6）。
- **必裁①约束（WI1 裁定表）**：LLM 失败响应从不入 ctx——本项改造消息追加语义时不得引入"失败响应入 ctx"的新路径。
- 消费方交付时点：占位渲染的运行时消费者是 WI4（心跳请求构造）与 WI5（steering 唤醒请求构造），同 milestone 后续 WI——本项按 plan guide Rule #23 以"契约测试 + WI4/WI5 接线承诺"显式声明分期；开放集合判定在本项即接入 restoreSession 验证日志（真实运行时消费者）。

## Goals

- `AgentToolDispatcher.commitToolResults` 结果插入改为**原位插入**：插入点 = 批次定位（callId 集合，取自 futuresArray 元素）后从该 tool_call 起连续 tool_call/tool_output 类型区段（instanceof 判定，deny 错误 result 同属区段）的末尾（首个其他类型消息之前）；每次 commit 前重算插入索引；无等待期消息时行为与现状完全一致（零回归）；覆盖正常路径与 reenter 路径（两个 addMessage 调用点共用同一插入 helper；AR-03 配对纪律保持）。
- 新增**占位渲染构件**：给定消息列表判定开放 tool_call（有 tool_call 无同 callId tool_output），渲染引导性占位 tool_result 列表（等待/并行/勿重复调用语义，英文文案），不修改 ctx。
- 新增**开放集合判定构件**（差集三态语义）：(a) 消息级开放（有 call 无 result）且无 TOOL_EXECUTION checkpoint = 恢复告警对象；(b) 消息级闭合（有 result，含 deny 错误 result）= 不告警；(c) 消息级开放但有 checkpoint = 已 commit 未 sync 窗口，归既有 messageCount 告警语义，不重复告警。接入 `restoreSession` 的 checkpoint 验证环节输出 best-effort 告警日志（NoOp checkpoint manager 下差集恒空 = 功能关闭，非静默失败）。
- 【Fix】AnthropicDialect 请求侧工具消息形态修正：`ChatToolResponseMessage` → user 角色；tool_result 块先于文本块；`buildBody` 合并相邻同角色消息为单一轮（tool_use/text 按序保块序，tool_output 并入紧随的 user 轮且 tool_result 块前置）。现有 dialect 测试按新形态更新；WI20（cache_control 放置）将同文件动工——本项落地后 WI20 先复核本形态（协同规则登记）。
- 测试矩阵：插入顺序（零回归/乱序提交/等待期消息/reenter 路径）、开放集合三态、占位渲染（文案语义/不变量①非 mutation）、dialect 请求形态断言（Anthropic 修正后形态 + OpenAI 现状合规形态）。

## Non-Goals

- 不实现等待期唤醒轮本身（WI4/WI5 承接——本项交付其消费的构件）。
- 不实现崩溃孤儿工具的合成收尾（autonomous roadmap WI10 承接；本项只交付开放集合判定）。
- 不修改 nop-ai-api 公共 API（ChatToolCallMessage/ChatToolResponseMessage 只读使用；ICheckpointManager 属 nop-ai-agent 内部接口）。
- 不改变 OpenAiDialect/GeminiDialect/OllamaDialect/ResponsesDialect（本项仅 Anthropic 修正；其余 dialect 形态问题如发现另行登记）。
- 不改变 checkpoint 写入语义（WI6 承接相位检查点）。
- 不引入任何"LLM 失败响应入 ctx"新路径（WI1 必裁①约束）。

## Scope

### In Scope

- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentToolDispatcher.java`：commitToolResults 原位插入（含 reenter 路径）。
- 新增 engine 包构件：开放 tool_call 判定（消息级配对）+ checkpoint 差集 + 占位渲染（命名遵循 design guide 接口命名规则）。
- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/ICheckpointManager.java`：新增 `getCheckpoints(sessionId)` default 方法（默认空列表；NoOp 免实现——差集恒空即告警关闭；三个功能实现已就位无需改动）。
- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentSessionLifecycle.java`：verifyLatestCheckpoint 环节接入开放集合三态告警日志（best-effort，不阻断恢复）。
- `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/AnthropicDialect.java`：请求侧工具消息形态修正（getRole tool_output→user、块序、同角色合并）。
- 测试：nop-ai-agent 插入顺序/开放集合/占位渲染/restore 接线测试；nop-ai-core AnthropicDialect 修正后形态测试（含更新既有被 pin 的断言）+ OpenAI 形态断言。
- owner doc `ai-dev/design/nop-ai-agent/nop-ai-agent-async-tool-execution.md`：§三.2/§三.4 补实现构件名锚点（最小增量）。
- `ai-dev/logs/2026/10-02.md` 条目（Phase 1 完成后即记，Phase 2 追加）。

### Out Of Scope

- WI4/WI5 的唤醒请求构造与等待期竞争实现。
- nop-ai-api 公共 API 变更。

## Execution Plan

### Phase 1 - 原位插入与构件实现（nop-ai-agent）

Status: completed
Targets: `AgentToolDispatcher.java`、engine 包新构件、`ICheckpointManager.java`、`AgentSessionLifecycle.java`

- Item Types: `Feature`

- [x] commitToolResults 原位插入（插入 helper 共用于正常/reenter 两调用点；callId 批次定位；instanceof 区段扫描；每 commit 前重算索引）
- [x] 消息级开放 tool_call 判定 + TOOL_EXECUTION checkpoint 差集构件（三态语义）
- [x] 占位渲染构件（每开放 callId 恰一份；文案含工具名与等待/并行引导语义，英文；不修改 ctx）
- [x] `ICheckpointManager.getCheckpoints(sessionId)` default 方法提升
- [x] verifyLatestCheckpoint 接入三态告警日志

Exit Criteria:

- [x] `TestToolResultCommitOrdering` 覆盖：①无插入消息时消息序与现状逐位一致（零回归断言）；②等待期消息存在时结果插于其前且紧随批次区段；③乱序完成（B 先 A 后）最终序满足 results 连续相邻；④reenter 消息按同规则插入；⑤deny 错误 result 在区段内时插入位置仍正确
- [x] 开放集合三态测试：(a) 开放+无 checkpoint=告警集；(b) 闭合（含 deny 错误 result）=不告警；(c) 开放+有 checkpoint=不重复告警
- [x] 占位渲染测试：每开放 callId 恰一份；渲染前后 ctx 消息列表逐位不变（不变量①）
- [x] restore 接线验证：`TestRestoreSession` harness 或代码审查确认 restore→verifyLatestCheckpoint→判定构件调用链连通（Rule #23）
- [x] 新增公共方法无静默跳过/空实现；NoOp 语义=差集恒空告警关闭（显式行为非隐藏失败）（Rule #24）
- [x] `ai-dev/logs/2026/10-02.md` Phase 1 条目已记
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过

### Phase 2 - AnthropicDialect 形态修正与 dialect 断言

Status: completed
Targets: `AnthropicDialect.java`、nop-ai-core 测试

- Item Types: `Fix`

- [x] getRole：ChatToolResponseMessage → "user"（仅 AnthropicDialect 覆写，不动 AbstractLlmDialect base role——OpenAI 合规依赖现值）
- [x] convertMessage：tool_result 块先于 text 块
- [x] buildBody：相邻同角色消息合并为单一轮（保块序；applyThinkingToContentBlocks 语义保持）
- [x] 更新既有 dialect 测试中被 pin 的形态断言；新增唤醒形态断言

Exit Criteria:

- [x] Anthropic 形态断言测试通过：唤醒形态列表（tool_calls+占位+steering 文本）转换后为——单一 assistant 轮含 text+tool_use 块、单一 user 轮含 tool_result 块（先）+steering 文本块（后）
- [x] OpenAI 形态断言测试通过（现状合规形态 pin）
- [x] `./mvnw test -pl nop-ai/nop-ai-core -am` 通过（含既有 dialect 测试更新后全绿）
- [x] 修正不改变响应侧解析行为（parseUsage/parseResponse 测试保持通过）

### Phase 3 - 文档同步与收口检查

Status: completed
Targets: owner doc、log、全量验证

- Item Types: `Proof`

- [x] owner doc §三.2/§三.4 补实现构件名锚点（最小增量）；AnthropicDialect 修正后 §三.4 契约与实现一致
- [x] `ai-dev/logs/2026/10-02.md` Phase 3 条目（含 WI20 同文件协同登记注）

Exit Criteria:

- [x] owner doc 锚点增量完成，不改变既有契约语义
- [x] `ai-dev/logs/2026/10-02.md` 条目在位
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `./mvnw test -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-core -am` 通过

## Closure Gates

> 收口顺序：独立 closure audit 通过 → 勾选 refactor roadmap WI3 checkbox → git commit → 本 plan 标 completed。

- [x] 三条不变量全部落地且有测试证明（①渲染不 mutation；②原位插入含零回归断言；③差集三态含 restore 接线）
- [x] AnthropicDialect live defect 修正落地且两 dialect 形态断言通过
- [x] 独立子 agent closure-audit 已完成并记录证据（Anti-Hollow：判定构件已接 restoreSession 运行时；渲染构件消费者为 WI4/WI5 已显式声明分期——契约测试在位）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-core -am` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] 受影响 owner docs 已同步（async-tool-execution.md 构件锚点）
- [x] git commit 完成（含全部 In Scope 文件）

## Deferred But Adjudicated

（无——渲染构件的 WI4/WI5 消费者接线是 roadmap 既定分期（deps 链 WI3→WI4/WI5），非本 plan 的 deferred 项；本 plan closure 以"构件 + 契约测试 + restore 接线"为完成面。其余 dialect（Gemini/Ollama/Responses）工具消息形态未审——超出 WI2 契约面，发现问题时另行立项。）

## Closure

Status Note: 三不变量全部落地（派生态渲染/原位插入/差集三态+restore 接线），执行中发现的 AnthropicDialect 请求侧形态 live defect 一并修正（role/块序/同角色合并/伪影清除），两 dialect 形态断言与 3842 项合并测试全绿——WI3 交付全部落地。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_033555b2-579a-4093-b5a2-8d4d9c2db20f（fresh session）
- Evidence:
  - 三不变量核验 PASS（渲染零 mutation、双调用点共用 helper + locator miss WARN 回退、三态差集 + restoreSession→verifyLatestCheckpoint→logOpenToolCallDiff 调用链连通）
  - AnthropicDialect 四项修正核验 PASS；AbstractLlmDialect/OpenAiDialect 零 diff 确认
  - 测试覆盖核验 PASS（16 agent 新测试覆盖 EC①-⑤ + 三态 + 非 mutation；2 dialect 形态断言精确命中 EC；reenter 断言与 AR-03 marker 语义 trace 一致）
  - surefire 聚合：nop-ai-agent 3414/0F/0E + nop-ai-core 428/0F/0E（3 skipped 存量）
  - scan-hollow --module nop-ai-agent --severity high exit 0；check-doc-links --strict exit 0
  - plan/owner doc/log 一致性 PASS；必裁①约束（无失败响应入 ctx 新路径）PASS
  - 两条非阻塞观察（javadoc 措辞/重复断言）已在收口前修复并复测通过
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2284-wi3-placeholder-tool-result-protocol.md --strict` exit 0（收口勾选后运行）

Follow-up:

- no remaining plan-owned work（渲染构件的 WI4/WI5 消费者接线为 roadmap 既定分期，非遗留债）
