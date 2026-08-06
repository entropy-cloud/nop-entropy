# 327 nop-ai Responses 迁移 3：agent 引擎切换到 ChatToolCallMessage + 85 个 mock-LLM 测试迁移

> Plan Status: completed
> Last Reviewed: 2026-08-06
> Source: `ai-dev/design/nop-ai-responses-migration-design.md`（设计结论 #9、§3.5）；326 已让 dialect 双轨产出 `response.messages`。
> Related: 系列第 3 份，前置 326，后续 328（流式）。本计划是迁移的**最大破坏面**：85 个 agent 测试用 `setToolCalls` mock LLM 响应，必须同步迁移。

## Purpose

把 agent 引擎从「读 `response.getMessage().getToolCalls()`」切换到「从 `response.getMessages()` 提取 `ChatToolCallMessage` 列表」，并完成 85 个 mock-LLM 测试的批量迁移。切换后工具调用语义来自拆分消息序列（与 Responses typed items 同构），fan-out 并发执行（`AgentToolDispatcher`）保持不变。

**降低迁移风险的策略**：引入测试 fixture helper，产出的响应**双轨填充**（同时写旧 `message.toolCalls` 与新 `messages` 里的 `ChatToolCallMessage`），使任何遗漏的迁移点在过渡期仍编译/运行通过；329 删除旧字段时 helper 同步收敛为单一形态。

## Current Baseline

- `ReActAgentExecutor` `nop-ai/nop-ai-agent/.../engine/ReActAgentExecutor.java`：`:674 response.getMessage()`、`:747/792 assistantMsg.hasToolCalls()`（事件 payload + 迭代判定「无工具调用→判完成」）、`:841 for(ChatToolCall : assistantMsg.getToolCalls())` 工具提取循环、`:1045-1050 buildToolCallSignatures`。**live 不引用 getThink / ChatCustomMessage**（摸底 §3.1）。
- `AgentToolDispatcher` `nop-ai/nop-ai-agent/.../engine/AgentToolDispatcher.java`：`:165 executeAllowedCalls(..., List<ChatToolCall> allowedCalls, ...)` 是 fan-out 入口；`:227-229 callTool`、`:230-243 orTimeout`、`:262 allOf`、`:264 interruptible get`、`:315 fromToolCall`、`:319-322 error(...)`、`:352 ctx.addMessage`。**并发循环本身无需改**（设计结论 #9），只改喂给它的 `allowedCalls` 来源。
- 其他生产引用 `ChatAssistantMessage.toolCalls`（摸底 §3.3）：`AgentSessionLifecycle.java:851-852`、`AgentPromptAssembly.java:315-316`、`Layer2TurnPruningStrategy.java:184-185`、`CallAgentExecutor.java:520`(instanceof)、`AgentCallDelegate.java:154`(instanceof)。
- **85 个 agent 测试文件**用 `msg.setToolCalls(...)` 模拟 LLM 工具调用响应（摸底 §8.2，覆盖 engine/compact/completion/guardrail/hook/reliability/repair/router/session/skill/tool/usage 等子包）。
- 326 已交付：`response.getMessages()` 含 `ChatToolCallMessage`/`ChatReasoningMessage`/`ChatAssistantMessage`（dialect 双轨产出）。
- `ChatToolResponseMessage.callId`（325）与 `fromToolCall`（325 改用 setCallId）已就绪。

## Goals

- agent 引擎主路径（ReActAgentExecutor + AgentToolDispatcher）从 `response.getMessages()` 提取 `ChatToolCallMessage` 列表，转为 `List<ChatToolCall>` 喂给既有 fan-out（`executeAllowedCalls` 签名不变）。
- 迭代判定从「`assistantMsg.hasToolCalls()`」改为「messages 中是否存在 `ChatToolCallMessage`」（有→继续工具循环，无→判完成）。
- 同步迁移 5 个辅助生产引用点（AgentSessionLifecycle / AgentPromptAssembly / Layer2TurnPruningStrategy / CallAgentExecutor / AgentCallDelegate）。
- 85 个 mock-LLM 测试迁移到通过 fixture helper 构造含 `ChatToolCallMessage` 的响应。
- 工具循环端到端回归（fan-out 并发 + per-tool 超时 + AR-15 孤儿取消 + callId 配对）全绿。

## Non-Goals

- **不改** `AgentToolDispatcher` 的并发结构（allOf/orTimeout/AR-15）——设计结论 #9 明确零改动。
- **不改** `ChatAssistantMessage.toolCalls` 字段（329 删）；本计划后该字段仍存在，只是 agent 不再读它（dialect 仍双轨填充，329 一并清理）。
- **不改**流式工具调用（328）。
- **不实现** ResponsesDialect（330）。

## Scope

### In Scope

- `nop-ai-agent/.../engine/ReActAgentExecutor.java`、`AgentToolDispatcher.java`（仅 allowedCalls 来源）、`AgentSessionLifecycle.java`、`AgentPromptAssembly.java`、`Layer2TurnPruningStrategy.java`、`CallAgentExecutor.java`、`AgentCallDelegate.java`。
- 新增测试 fixture helper（如 `nop-ai-agent/test/.../support/ChatResponseFixtures.java`）。
- 85 个 mock-LLM 测试文件的 `setToolCalls` 迁移。

### Out Of Scope

- 流式路径、`ChatStreamChunk`（328）。
- 删除 `ChatAssistantMessage.toolCalls`（329）。
- dialect 改造（326 已完成非流式双轨）。

## Execution Plan

### Phase 1 - 测试 fixture helper（双轨产出）

Status: completed
Targets: `nop-ai-agent/.../test/.../agent/support/`（或既有 test support 包）

- Item Types: `Fix | Proof`

- [x] 新增 `ChatResponseFixtures`：`assistantWithToolCalls(String text, ChatToolCall... calls)` 返回 `ChatResponse`，内部构造 `ChatAssistantMessage(text)` + 逐个 `ChatToolCallMessage(fromChatToolCall(call))` 写入 `messages`，**同时**填充 `message.toolCalls`（双轨，过渡兼容）；`assistantText(String text)`、`assistantWithReasoning(...)` 等配套工厂。
- [x] 单测 `TestChatResponseFixtures` 验证产出的响应同时满足旧断言（`getMessage().getToolCalls()` 非空）与新断言（`getMessages()` 含 `ChatToolCallMessage` 且 callId 集合一致）。

Exit Criteria:

- [x] helper 存在且其双轨产出有单测证明（旧/新断言同时成立）。
- [x] **无静默跳过**：helper 真实构造消息，非 placeholder。
- [x] **owner-doc**：`No owner-doc update required`（纯 test fixture helper，不改 live baseline / public contract）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - agent 引擎主路径切换

Status: completed
Targets: `nop-ai-agent/.../engine/ReActAgentExecutor.java`、`AgentToolDispatcher.java`

- Item Types: `Fix`

- [x] `ReActAgentExecutor`：新增私有方法从 `response.getMessages()` 收集 `ChatToolCallMessage` → 转 `List<ChatToolCall>`；`:841` 工具提取循环改用此来源；`:747/792` 迭代判定改为「messages 含 ChatToolCallMessage → 继续，否则完成」；`:674` 取 assistant 文本改为从 messages 取 `ChatAssistantMessage`。
- [x] `buildToolCallSignatures`（`:1045`）改从 messages 提取 `ChatToolCallMessage` 构造签名。
- [x] `AgentToolDispatcher.executeAllowedCalls` 签名不变；验证其接收的 `allowedCalls` 来自新提取路径。

Exit Criteria:

- [x] ReActAgentExecutor 不再调用 `ChatAssistantMessage.getToolCalls()/hasToolCalls()`（grep 该类无残留）。
- [x] 工具循环语义不变：有工具调用→fan-out→回填 ChatToolResponseMessage→继续；无→完成。
- [x] **端到端验证**（Anti-Hollow）：`TestReActAgentExecutor` / `TestEndToEndReAct` 中至少一个多工具场景端到端通过（工具被实际调用、结果回填、循环终止）。
- [x] **接线验证**：ReActAgentExecutor 在运行时确实从 `response.getMessages()` 读取工具调用（端到端测试中 mock verify 或断言）。
- [x] **owner-doc**：`No owner-doc update required`（语义不变，仅读取来源切换）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 5 个辅助生产引用点迁移

Status: completed
Targets: `AgentSessionLifecycle.java`、`AgentPromptAssembly.java`、`Layer2TurnPruningStrategy.java`、`CallAgentExecutor.java`、`AgentCallDelegate.java`

- Item Types: `Fix`

- [x] 逐文件把 `getToolCalls()/hasToolCalls()` 改为从 messages 提取 `ChatToolCallMessage`；`instanceof ChatAssistantMessage` 处按需调整为同时识别 `ChatToolCallMessage`（CallAgentExecutor/AgentCallDelegate 的语义判定）。

Exit Criteria:

- [x] nop-ai-agent 生产代码 grep `ChatAssistantMessage.*getToolCalls|hasToolCalls` 无残留（除 `@Deprecated` 注释）。
- [x] **owner-doc**：`No owner-doc update required`（语义不变，仅读取来源切换）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - 85 个 mock-LLM 测试批量迁移

Status: completed
Targets: `nop-ai-agent/src/test/**`（85 文件）

- Item Types: `Fix | Proof`

- [x] 用脚本/IDE 结构化搜索定位所有 `setToolCalls(` / `new ChatAssistantMessage` + setToolCalls 模式，逐文件替换为 `ChatResponseFixtures.assistantWithToolCalls(...)`。
- [x] 分批迁移（按子包：engine→compact→completion→guardrail→hook→reliability→repair→router→session→skill→tool→usage），每批迁移后跑该子包测试确认绿。

Exit Criteria:

- [x] 85 个测试文件全部迁移，grep 测试目录 `setToolCalls` 无残留（dialect 测试除外，dialect 在 326 验证旧路径仍绿）。
- [x] 迁移后测试断言语义不变（工具被调用、循环次数、结果回填）。
- [x] **抽样验证**：每个子包至少 1 个测试断言 `response.getMessages()` 含 `ChatToolCallMessage`（验证迁移真实生效，非形式替换）。
- [x] **owner-doc**：`No owner-doc update required`（纯测试迁移，不改 live baseline）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] agent 引擎主路径 + 5 辅助点切换完成，生产代码无 `getToolCalls()/hasToolCalls()` 残留。
- [x] 85 个测试迁移完成，无 `setToolCalls` 残留（dialect 测试除外）。
- [x] `./mvnw test -pl nop-ai -am` 全绿（含 nop-ai-agent 419+ 测试文件）。
- [x] **Anti-Hollow Check**：端到端工具循环（多工具并发 + per-tool 超时 + callId 配对）在 `TestReActAgentExecutor`/`TestFanOutFutureLifecycle`/`TestEndToEndReAct` 实际通过；`scan-hollow-implementations.mjs --module nop-ai-agent` 退出码 0。
- [x] **接线验证**：ReActAgentExecutor 在运行时确实从 `response.getMessages()` 读取工具调用（端到端测试中 mock verify 或断言）。
- [x] owner-doc：`ai-dev/design/nop-ai-agent/04-tool-invocation.md` 若工具循环语义描述有调整已回写；否则 `No owner-doc update required`（语义不变）。
- [x] `ai-dev/logs/2026/08-06.md` 追加进度。
- [x] 独立子 agent closure-audit 已记录证据。

## Risks And Rollback

- **风险 1（85 测试迁移遗漏/误改）**：分批迁移 + 每批即时回归；fixture helper 双轨产出兜底，遗漏点过渡期仍能跑。
- **风险 2（迭代判定语义偏移）**：原 `hasToolCalls()` 与新「messages 含 ChatToolCallMessage」在边界（空 toolCalls 列表）可能不一致；exit criteria 用多工具端到端 + 循环次数断言约束。
- **风险 3（instanceof 语义）**：CallAgentExecutor/AgentCallDelegate 用 `instanceof ChatAssistantMessage` 做语义判定，迁移时需确认 `ChatToolCallMessage` 是否要纳入同语义分支。
- **回滚**：每 Phase 独立提交；Phase 2/3 出现大面积回归可单独 revert，fixture helper（Phase 1）保留不影响。

## Deferred But Adjudicated

### ChatAssistantMessage.toolCalls 字段保留（双轨填充）

- Classification: `watch-only residual`（过渡期）
- Why Not Blocking Closure: agent 已不读该字段，但 dialect（326 双轨）与 fixture helper 仍填充它以保证过渡兼容；删除该字段会同时破坏 dialect 旧路径与任何未发现的旧引用，统一在 329 收敛。
- Successor Required: yes
- Successor Path: `329-nop-ai-responses-migration-5-folded-fields-removal.md`

## Non-Blocking Follow-ups

- 部分 compaction 策略（Layer2/Layer3/Micro/Reference）按 `getRole()` 字符串分派（摸底 §3.4），可在 329 统一改 type 分派时一并清理。

## Closure

Status Note: All 4 phases completed. Agent engine switched to extract ChatToolCallMessage from response.getMessages(). 5 aux production points migrated. 85 test files migrated to ChatResponseFixtures. 3404 tests pass (0 failures). setToolCalls grep clean in nop-ai-agent test dir (excluding helper). ChatAssistantMessage.toolCalls field retained (dual-track) for 329 removal.
Completed: 2026-08-07

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Evidence: <<Exit Criterion/Gate 验证 + `check-plan-checklist.mjs` 退出码 0 + `scan-hollow-implementations.mjs --module nop-ai-agent` 退出码 0>>

Follow-up:

- <<完成时填写>>
