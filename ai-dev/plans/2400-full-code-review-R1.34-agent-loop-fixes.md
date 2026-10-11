# 2400 full-code-review R1.34 — nop-ai agent 循环修复批（P1-6.7-005 / P1-6.7-006）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.34；findings P1-6.7-005 / P1-6.7-006（`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/6.7-check-p1-batch-c.md`）
> Related: plan 2380（R1.14 secret 序列化）、plan 2399（R1.33 同模块方言批）；AR-11（plan 277 guardrail tool_call 配对先例）
> Owner Doc: `docs-for-ai/03-modules/nop-ai.md`
> Plan Review: agent_1f87c5fc 对抗审查（内容判定 Ready；发现 1 Blocker 流程态「实现先于审阅、baseline/锚点失真」+1 Major 方法名 `acceptLlmTurn` 幻影 +4 Minor）→ 本轮全修：Status 翻 active、baseline/锚点刷新、`recordLlmTurn` 勘正、压缩因子 0.8/强停 0.9 勘正、owner-doc 更新项内移各 Phase（Rule #17）、Rule #22/#23/#24 显式化、测试判据改为「call 在位且配对」；scope 含 PipelineCompactor+Layer3 经独立判定为同根必要（非 scope creep）。

## Purpose

收口 nop-ai agent 循环两条 P1：①POST_REASONING BAIL 丢弃本轮响应时不移除/不配对已持久化的 assistant + tool_call 消息，下一轮请求携带无 response 的 tool_call → 强制配对校验的 provider（Anthropic）400，且坏配对已写入会话存储（复挂）；②AgentCompactionCoordinator 把 `ChatOptions.maxTokens`（最大生成 token 数）误作上下文窗口，常规配置（如 4096）第一轮即触发 forced-stop。后者在 `PipelineCompactor` / `Layer3FullSummaryStrategy` 存在同根缺陷，一并按同一解析契约收口。

## Current Baseline

- **P1-6.7-005**（live）：`ReActAgentExecutor.recordLlmTurn`（HEAD :990 起）内 `ctx.addMessage(assistantMsg)`（HEAD :1015）+ `appendToolCallMessages(ctx, response)`（HEAD :1019）之后，`saveLlmTurnCheckpoint` 已把含该 tool_call 的消息列表写入 persisted session（HEAD :1111-1117）。`adjudicateResponse` 的 BAIL 分支（HEAD :1134-1146）只做计数/日志/`ctx.setCurrentIteration+1` 后 `return CONTINUE_LOOP`，本轮 append 的消息仍留在 ctx 与 persisted session，缺配对的 `ChatToolResponseMessage`。（修复落地后 `ReActAgentExecutor` 行号整体后移 +20，见执行后锚点。）
- **同型正确先例在位**：`AgentPromptAssembly.checkOutputGuardrail`（:322-329）在 assistant 已提交且带 tool_calls 时，逐 callId 注入 `ChatToolResponseMessage.error(...)`；wait 阶段 abandon（`ReActAgentExecutor` :1569-1581）同样注入合成结果；`persistInjectedMessages(ctx, sessionId)`（:1401-1411）为既有的 ctx→persisted session 单写者同步辅助。
- **既有测试**：`TestBailInReActLoop.postReasoningBailDiscardsToolCallResponseAndRePrompts`（:154-194）只断言「BAIL 轮的 tool_calls 未执行」，未断言配对不变量——缺陷可在该测试下存活。
- **P1-6.7-006**（live）：`AgentCompactionCoordinator.resolveMaxContextTokens`（HEAD :66-72）在 `chatOpts.getMaxTokens() != null` 时直返该值（输出上限当上下文窗口）；:58-64 以 `DEFAULT_TRIGGER_TOKEN_PERCENT = 0.8` 用于 `shouldTriggerCompaction`，`AgentLoopGuard.shouldForceStop` 另以 `DEFAULT_FORCED_STOP_PERCENT = 0.9` 消费同一解析结果。`TestEngineExtractedCoordinators.resolveMaxContextTokensFallsBackToDefault`（:349-360）把「maxTokens=4096 → 4096」钉为契约。
- **同根缺陷**：`PipelineCompactor.resolveMaxContextTokens`（:178-187）与 `Layer3FullSummaryStrategy.resolveCompressionPromptBudget`（:326-338）同样以 `chatOptions.getMaxTokens()` 充当上下文窗口；`ReActAgentExecutor.DEFAULT_MAX_CONTEXT_TOKENS = 128000`（:141）为既有缺省。
- **缺陷已被测试体系钉死（修复须同步迁移布景）**：约 13 个测试文件把 `maxTokens` 当上下文窗口来驱动 compaction/forced-stop（如 `TestForcedStop:73` `ChatOptionsModel.setMaxTokens(MAX_CONTEXT_TOKENS=1000)`、`TestCheckpointTriggersLLMTurnAndCompaction:639` 注释直书 "Set a very low maxContextTokens"、`TestCompactionDataPathway` ×5、`TestCompactionInReActLoop` ×3 等）——本修复必须把这些布景迁到独立声明面，否则解析解耦后测试失真。
- **可用的模型声明面在位**：`io.nop.ai.core.model.ChatOptionsModel` 已有 `contextLength` 字段（生成物 `_ChatOptionsModel:21-24`），但 `ChatOptionsHelper.toChatOptions`（:8-23）未透传；`ChatOptions`（nop-ai-api）无独立上下文窗口字段。
- **构建基线**：执行开始时工作树干净（`git status` 空），最近一次全量 `install` 成功；Plan Status 为 `active`，实现已在 in-tree 进行（本文档随执行前进同步勾选）。

## Goals

- BAIL（POST_REASONING）丢弃含 tool_calls 的响应后，ctx 与 persisted session 中该轮 tool_call 均有配对 tool response（或在丢弃路径下不残留孤悬 tool_call）——下一轮请求可安全发给严格配对 provider。
- 上下文窗口解析与「最大生成 token 数」彻底解耦：三名消费者（AgentCompactionCoordinator / PipelineCompactor / Layer3FullSummaryStrategy）统一走一个解析入口，取独立配置 `maxContextTokens`，缺省回落 128000。
- 声明面：`ChatOptions` 增独立 `maxContextTokens` 字段（含 copy/merge/Builder 全链），并由 `ChatOptionsModel.contextLength` 映射填充。
- 两条修复各有判别测试（stash 后 FAIL→恢复后 PASS）；nop-ai 受影响模块测试全绿。
- owner doc `docs-for-ai/03-modules/nop-ai.md` 记录两条契约（bail 丢弃轮配对不变量、上下文窗口配置语义）。

## Non-Goals

- P1-6.7-007..011（R1.35/R1.36 承接）；P1-6.7-005 之外的 ReAct 循环调度问题。
- 「模型/provider 能力表自动推断上下文窗口」——本计划只交付显式声明面（模型声明或调用方设置），不做 provider→context window 内置映射表。
- `ChatOptionsModel` 之外的其它模型层上下文窗口解析；`AiChatOptions`/legacy pipeline 面。

## Scope

### In Scope

- `nop-ai/nop-ai-agent/.../engine/ReActAgentExecutor.java`：BAIL 分支配对修复 + session 重同步；共享上下文窗口解析入口
- `nop-ai/nop-ai-api/.../chat/ChatOptions.java`：新增 `maxContextTokens` 字段（getter/setter/copy/merge/Builder）
- `nop-ai/nop-ai-agent/.../engine/AgentCompactionCoordinator.java`：改用独立解析
- `nop-ai/nop-ai-agent/.../compact/PipelineCompactor.java`、`.../compact/Layer3FullSummaryStrategy.java`：同根改用独立解析
- `nop-ai/nop-ai-agent/.../engine/ChatOptionsHelper.java`、`AgentPromptAssembly.java`：`contextLength`→`maxContextTokens` 透传
- 测试：`TestBailInReActLoop`（配对不变量用例）、`TestEngineExtractedCoordinators`（断言改写 + 新用例）、`nop-ai-api` `TestChatOptions`（新字段 copy/merge/Builder 覆盖）、上述 13 个测试文件的布景迁移
- owner doc：`docs-for-ai/03-modules/nop-ai.md`

### Out Of Scope

- 其它 nop-ai-agent 文件的行为变更；R1.32/R1.33 已修面；legacy `IAiChatService` 管线

## Execution Plan

### Phase 1 - P1-6.7-005 BAIL 配对修复 + 回归

Status: completed
Targets: `ReActAgentExecutor.java`、`TestBailInReActLoop.java`（必要时复用既有测试基建）

- Item Types: `Fix`

- [x] BAIL 分支：当 `turn.responseToolCalls()` 非空时，逐 callId 注入 `ChatToolResponseMessage.error(callId, name, "discarded by POST_REASONING middleware bail: <reason>")`（对齐 `checkOutputGuardrail` / heartbeat abandon 形态），随后调用既有 `persistInjectedMessages(ctx, sessionId)` 重同步 persisted session；空 tool_calls 时行为不变（仅计数/日志/continue）
- [x] 不变量保持：LLM_TURN checkpoint 已写 `messageCount=N`，注入只增消息（session count ≥ N），不破坏 `checkpoint.messageCount <= session.messageCount`
- [x] 回归测试：扩展/新增 `TestBailInReActLoop` 用例——捕获下一轮 `ChatRequest.messages`，断言 BAIL 轮的每个 tool_call id **既存在于历史中、又均有配对的 `ChatToolResponseMessage`**（注入式修复承诺 call 保留且配对；不接受「call 被移除」作为通过条件），且既有「BAIL 轮 tool_calls 不执行」断言保持
- [x] `docs-for-ai/03-modules/nop-ai.md`：新增「POST_REASONING BAIL 的 tool_call 配对不变量」契约小节（本 Phase 内的 owner-doc 更新项）
- [x] **判别验证（stash 腿）**：修复前新用例 FAIL（无配对 response）、恢复后 PASS，输出留档
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

Exit Criteria:

- [x] BAIL 轮含 tool_calls 时下一轮请求与 persisted session 均配对完整（call 在位 + response 配对，测试断言在案）
- [x] `TestBailInReActLoop` 全绿（10/10）+ 既有 bail 测试零回归
- [x] 判别 stash 腿 FAIL→PASS 记录在 plan
- [x] Phase 内 owner-doc 更新项已落地（nop-ai.md BAIL 配对契约小节）

判别证据（P1-6.7-005，stash 腿 python-patch）：临时在 BAIL 注入条件追加 `&& false` 后 `./mvnw test -pl nop-ai/nop-ai-agent -am -Dtest=TestBailInReActLoop` → **恰 1 FAIL**：`postReasoningBailKeepsToolCallResponsePairing ... calls=[bail-c1] responses=[]`；恢复后 10/10 绿（`_tmp/r134-stash-005.log`）。

### Phase 2 - P1-6.7-006 上下文窗口独立配置 + 同根收敛

Status: completed
Targets: `ChatOptions.java`、`AgentCompactionCoordinator.java`、`PipelineCompactor.java`、`Layer3FullSummaryStrategy.java`、`ChatOptionsHelper.java`、`AgentPromptAssembly.java` + 既有把 `maxTokens` 当作上下文窗口的测试布景

- Item Types: `Fix`

- [x] `ChatOptions`：新增 `Integer maxContextTokens`（字段+getter/setter+`@JsonInclude(NON_NULL)`+`copy()`+`merge()`+`Builder.maxContextTokens`），javadoc 明确与 `maxTokens`（输出上限）语义正交
- [x] `ReActAgentExecutor`：新增公开静态解析入口 `resolveMaxContextTokens(ChatOptions)`——`maxContextTokens != null && > 0` 时取之，否则 `DEFAULT_MAX_CONTEXT_TOKENS`
- [x] `AgentCompactionCoordinator.resolveMaxContextTokens` / `PipelineCompactor.resolveMaxContextTokens` / `Layer3FullSummaryStrategy.resolveCompressionPromptBudget`：统一走该入口，彻底移除对 `getMaxTokens()` 的消费
- [x] `ChatOptionsHelper.toChatOptions`：`ChatOptionsModel.contextLength != null` 时映射到 `ChatOptions.maxContextTokens`；`AgentPromptAssembly.buildChatOptions` 同步透传 `maxContextTokens`
- [x] **测试布景迁移（缺陷钉死面）**：既有「以 `maxTokens` 充当上下文窗口」的测试布景全部改为独立声明（12 文件：TestForcedStop/TestCompactionDataPathway ×5/TestCompactionInReActLoop ×3/TestCompactionSnapshotArchive/TestPipelineCompactor/TestReferenceCompactionEndToEnd/TestLayer3FullSummaryStrategy ×2/TestWaitForWakeEndToEnd/TestCheckpointTriggersLLMTurnAndCompaction/TestTokenCountingIntegration/TestWaitForSuspend/TestDispatchPathPostDenialGuard）；纯输出上限用途（如 `TestPassThroughModelRouter:73` 路由透传保持一致；Layer3 压缩请求 options 现无 maxTokens 设置）保持 `maxTokens` 不动
- [x] 测试：改写 `TestEngineExtractedCoordinators` 解析用例（小 maxTokens 不改解析=128000 + `shouldTriggerCompaction` 行为判别 + 声明 maxContextTokens 生效 + 非正值回落）+ `TestChatOptions` 增 4 用例覆盖 copy/merge/Builder/独立性
- [x] **判别验证（stash 腿）**：修复前新/改断言 FAIL，恢复后 PASS，输出留档
- [x] `docs-for-ai/03-modules/nop-ai.md`：新增「上下文窗口独立于生成上限」契约小节（`maxContextTokens` 为引擎内部解析/控制字段、非 provider 请求字段；缺省 128000；`ChatOptionsModel.contextLength` 映射来源）——本 Phase 内的 owner-doc 更新项
- [x] 相关模块测试：`./mvnw test -pl nop-ai/nop-ai-api,nop-ai/nop-ai-agent,nop-ai/nop-ai-core -am`（BUILD SUCCESS）
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 三名消费者解析均不再读 `getMaxTokens()`（grep 证明：三文件 `getMaxTokens()` 零命中；三处调用 `ReActAgentExecutor.resolveMaxContextTokens`（:68/:181/:333））
- [x] 独立 `maxContextTokens` 声明生效、缺省 128000
- [x] 新增/改写判别测试绿；nop-ai-agent 3538/0/0、nop-ai-api、nop-ai-core 全绿
- [x] 判别 stash 腿 FAIL→PASS 记录在 plan
- [x] **端到端验证（Rule #22）**：`TestBailInReActLoop` loop 级断言（下一轮请求 + persisted session 配对不变量）作为 BAIL 路径端到端证据；`TestForcedStop`（7/7）/`TestCompactionInReActLoop` loop 级压缩/强停断言作为上下文窗口端到端证据
- [x] **接线验证（Rule #23）**：三名消费者经实例测试真实调用 `ReActAgentExecutor.resolveMaxContextTokens`（TestEngineExtractedCoordinators/TestForcedStop/TestPipelineCompactor/TestCompactionDataPathway 全绿）
- [x] **无静默跳过（Rule #24）**：`maxContextTokens <= 0` 显式回落缺省（`resolveMaxContextTokens` 守卫 + TestEngineExtractedCoordinators 非正值断言）
- [x] Phase 内 owner-doc 更新项已落地（nop-ai.md 上下文窗口契约小节）

判别证据（P1-6.7-006，stash 腿 python-patch）：临时把 `ReActAgentExecutor.resolveMaxContextTokens` 还原为读 `getMaxTokens()` 后 `./mvnw test -pl nop-ai/nop-ai-agent -am -Dtest=TestEngineExtractedCoordinators` → **恰 1 FAIL**：`expected: <128000> but was: <4096>`；恢复后 16/16 绿（`_tmp/r134-stash-006.log`）。

### Phase 3 - owner doc + 收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-ai.md`、roadmap、index、daily log

- Item Types: `Fix | Proof`

- [x] nop-ai.md 终检：Phase 1/2 已落地的两节（BAIL 配对不变量、上下文窗口配置语义）与 live 行为逐条一致（audit 逐句核对 nop-ai.md:86-87 与 `ReActAgentExecutor.java:169-175,1170-1181`、三消费者调用点）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap R1.34 翻 done；index P1-6.7-005/006 → fixed；daily log 条目（Lead 已于 2026-10-10 同步：roadmap R1.34=done、index P1-6.7-005/006=fixed、R1.34 行=done、daily log 10-10.md 条目在位）
- [x] 独立子 agent closure audit（`plan-closure-audit-prompt.md`），evidence 写入本 plan Closure 段
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（Lead roadmap/index 记账后重跑通过）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0

Exit Criteria:

- [x] owner doc 条目在位且与 live 行为一致
- [x] doc-links 检查退出码 0
- [x] roadmap/index/log 三方一致（Lead 已同步 roadmap R1.34=done、index R1.34 行=done + P1-6.7-005/006=fixed、daily log 10-10.md 条目）
- [x] 独立 closure audit 完成并落盘

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-6.7-005 / P1-6.7-006）
- [x] 行为/契约结果已达成（配对不变量 + 上下文窗口独立解析）
- [x] 必要 focused verification 已完成（判别 stash 两腿 + nop-ai-agent/nop-ai-api 测试全绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响 owner docs 已同步到 live baseline
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证修复路径运行时连通（下一轮请求真实携带配对 response；三名消费者真实走独立解析入口），无空方法体/静默跳过作为正常实现
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（Lead roadmap/index 同步后重跑通过）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- provider→上下文窗口内置能力表（自动推断）：本计划只交付显式声明面，自动推断为 optimization candidate，不影响显式配置语义成立
- 其余 nop-ai-agent 增量发现归 R1.35/R1.36 或后续轮次

## Closure

Status Note: P1-6.7-005（POST_REASONING BAIL 对每个被丢弃 tool_call 注入配对 `ChatToolResponseMessage.error` + `persistInjectedMessages` 重同步）与 P1-6.7-006（独立 `ChatOptions.maxContextTokens` + 单一解析入口 `ReActAgentExecutor.resolveMaxContextTokens(ChatOptions)`，三同根消费者收敛）经独立 closure audit 对 live 代码逐条复核：入口到出口调用链连通、判别测试确为行为载体、owner doc 与 live 一致、focused verification 全绿、无 in-scope live defect 被降级。唯一未勾选项属 Lead 处置域的 roadmap/index 记账同步（`ai-dev/backlog/full-code-review-roadmap.md:165` 仍 `in progress（plan 2400）`；`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/index.md:156-157` 仍 `open`）——本 audit 按约束不修改该两文件，故对应勾选与 `check-plan-checklist --strict` 退出码 0 留待 Lead 完成后补勾。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: independent closure audit (subagent)，未参与实现
- Audit Session: 独立 closure-audit 子会话（parent session-9601d130-1bfd-4608-86de-471049fd6c0e）
- Evidence:
  - **Exit Criterion P1-6.7-005（BAIL 配对）PASS**：
    - live 路径：`ReActAgentExecutor.execute`（:392）→ `runSingleIteration`（:465）→ `recordLlmTurn`（:756，其 :1035/:1039 先 append assistant+tool_call，:1045 `saveLlmTurnCheckpoint` 落盘）→ `adjudicateResponse`（:759）；BAIL 分支 `:1154` 起，`:1170-1181` 对每个 `turn.responseToolCalls()` 注入 `ChatToolResponseMessage.error`，非空时调用 `persistInjectedMessages`（:1180 → :1442-1452 `session.replaceMessages` + `sessionStore.save`）——调用链从真实入口连通，非仅类型存在。
    - 判别测试：`TestBailInReActLoop.postReasoningBailKeepsToolCallResponsePairing`（:237-298，断言 `:291` 下一轮请求 + `:297` persisted session 双锚；helper `assertToolCallResponsePairing` :300-315）。surefire：该测试类 10/10 绿。
    - 判别性（独立复核，非仅读断言）：stash 腿证据在案（`calls=[bail-c1] responses=[]`）；逻辑核对：去掉注入后 `assertTrue(responseIds.containsAll(callIds))`（:312）必失败。
    - 既有「BAIL 轮 tool_calls 不执行」断言（`postReasoningBailDiscardsToolCallResponseAndRePrompts` :160）保持全绿。
  - **Exit Criterion P1-6.7-006（上下文窗口独立解析）PASS**：
    - live：`resolveMaxContextTokens`（`ReActAgentExecutor.java:169-175`）仅读 `getMaxContextTokens()`，非正/缺省回落 `DEFAULT_MAX_CONTEXT_TOKENS=128000`（:141）；`ChatOptions` 字段/getter/setter/copy/merge/Builder 在位（`ChatOptions.java:80,213-219,399,454,531-532`，`@JsonInclude(NON_NULL)` :212）；`ChatOptionsHelper.java:25-26` 将 `ChatOptionsModel.contextLength` 映射到 `maxContextTokens`；`AgentPromptAssembly.java:253-254` 透传。
    - 三消费者 `getMaxTokens()` 零命中：`AgentCompactionCoordinator.java:68`、`PipelineCompactor.java:181`、`Layer3FullSummaryStrategy.java:333` 均委托 `ReActAgentExecutor.resolveMaxContextTokens`（全仓 grep 仅剩注释命中）。
    - 判别测试：`TestEngineExtractedCoordinators.resolveMaxContextTokensUsesIndependentDeclarationAndNeverOutputCap`（:349-381，含 `maxTokens=4096` 不改解析 :364、声明 1000 生效 :372、非正值回落 :380）；`TestChatOptions` 4 用例（:48/:59/:68/:84）。判别性：若还原为读 `getMaxTokens()`，:364 断言必 `expected <128000> but was <4096>`（与在案 stash 腿一致）。
    - 测试布景迁移：12 文件已由 `maxTokens` 迁移到 `contextLength`/`maxContextTokens`（如 `TestForcedStop:73`、`TestCompactionDataPathway:123,164,203,246,320`、`TestCompactionInReActLoop:151,279,312`、`TestLayer3FullSummaryStrategy:64,571` 等）；纯输出上限用途保留（`TestPassThroughModelRouter:73`）。
  - **focused verification PASS**：`./mvnw test -pl nop-ai/nop-ai-api,nop-ai/nop-ai-agent,nop-ai/nop-ai-core -am -T 1C` → BUILD SUCCESS，exit 0，nop-ai-agent `Tests run: 3538, Failures: 0, Errors: 0`；抽查 surefire `TestBailInReActLoop 10/10`、`TestEngineExtractedCoordinators 16/16`、`TestChatOptions 16/16`。
  - **Anti-Hollow PASS**：`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0（Critical/High/Medium/Low 全 0）；入口→BAIL 分支→persist 调用链经代码追踪确认连通；无空方法体/静默跳过作为正常实现（BAIL 空 tool_calls 保持原计数/日志/continue，未实现态不适用）。
  - **owner doc PASS**：`docs-for-ai/03-modules/nop-ai.md:84-87`「Agent 循环契约（P1-6.7-005 / P1-6.7-006）」两节与 live 行为逐句一致；`check-doc-links.mjs --strict` 退出码 0。
  - **Deferred 分类检查 PASS**：`## Deferred But Adjudicated`（无）；`Non-Blocking Follow-ups` 仅 provider→窗口能力表（optimization candidate）与后续轮次发现，无 in-scope live defect 被降级。
  - **Closure Gates**：见上节——除 `check-plan-checklist --strict 退出码 0` 阻塞于 Lead 记账项外，全部 PASS。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2400-full-code-review-R1.34-agent-loop-fixes.md --strict` 退出码 1（原因唯一：Phase 3 的 Lead roadmap/index 记账项未勾选，及依赖它的该项本身与 Closure Gates 最后一项）；非代码/契约失败。

Follow-up:

- Lead 处置域：`ai-dev/backlog/full-code-review-roadmap.md:165` R1.34 翻 done、`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/index.md:156-157` P1-6.7-005/006 → fixed；完成后勾选 Phase 3 roadmap 项与 `check-plan-checklist` 项，重跑 `check-plan-checklist --strict` 应收敛为 0。属记账治理，不影响本 plan 已交付的技术契约（本 audit 不修改该两文件）。
- provider→上下文窗口内置能力表（自动推断）：optimization candidate，不影响显式声明语义成立。
- 其余 nop-ai-agent 增量发现归 R1.35/R1.36 或后续轮次。
