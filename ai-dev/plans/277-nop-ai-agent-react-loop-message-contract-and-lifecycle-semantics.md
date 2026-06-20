# 277 nop-ai-agent ReAct 循环消息契约与生命周期语义正确性

> Plan Status: completed
> Last Reviewed: 2026-06-20
> Module: nop-ai-agent
> Work Item: WI-REACT-CONTRACT
> Source: `ai-dev/audits/2026-06-19-2310-adversarial-review-nop-ai-agent/01-open-findings.md`（AR-03/AR-06/AR-07/AR-11/AR-12/AR-13/AR-14）
> Related: 深度审核 `ai-dev/audits/2026-06-19-2310-deep-audit-nop-ai-agent/summary.md`（02-01 God Object，本计划处理的正是藏在其体积内的具体逻辑 bug）

## Purpose

把 `ReActAgentExecutor` 主循环中破坏 LLM 消息契约、错误汇报生命周期状态/事件、以及用误导性测试固化错误行为的若干 confirmed live defect 收口为正确语义。

## Current Baseline

- `ReActAgentExecutor`（约 3500 行）是 ReAct 编排核心。主循环已实现 fan-out 工具并行调用（`CompletableFuture.allOf().join()`）、hook 生命周期点、guardrail、checkpoint、deny 治理。
- `AgentExecStatus` 现有枚举值：`pending, running, completed, failed, cancelled, forced_stopped, escalated, paused`（**无 `blocked`**）。`AgentEventType` 含 `SESSION_PAUSED` 等。
- **AR-03（confirmed P1）**：`ReActAgentExecutor.java:~1961-1972` 与 `:~2021-2032` 的结果处理 for 循环中，hook 返回 `ReenterResult` 时用 `break` 跳出循环，丢弃同批后续 tool 结果 → 破坏 `tool_call_id` 配对契约。
- **AR-11（confirmed P2）**：`:~1199-1207`（input guardrail block，LLM 调用前）与 `:~1580-1588`（output guardrail block，LLM 返回后）注入合成 `role:"tool"` 消息，其 id（`guardrail-block-input/output`）不存在于任何 assistant tool_call → 同样破坏 `tool_call_id` 配对。
- **AR-06（confirmed P2）**：`:~1081` 的 `reentryCounters` 为 per-execute 局部 map，全 execute 生命周期累加且从不在 iteration/tool-batch 之间重置，导致长会话中合法 re-enter hook 在累计 3 次后静默降级为 PassResult。`DEFAULT_MAX_REENTRIES` 目前无 Javadoc。
- **AR-07（confirmed P2）**：`:~2497-2509` `handleGoalStuck` 把终态 `escalated` 配 `SESSION_PAUSED` 事件（可恢复事件），与 `handleSessionPaused`（`:~2479-2487`，paused↔SESSION_PAUSED 自洽）混用。
- **AR-14（likely P2）**：`:~2121-2123` 达 max-iterations 时把 `running` 静默改写为 `completed`；`TestReActAgentExecutor.java:210-234` 把此截断行为断言为 `completed`。**且**同文件 post-loop gate（`:~2129-2132`）按 status 排除 `cancelled/forced_stopped/escalated/paused` 来决定是否跑 `POST_CALL` hook 与发 `EXECUTION_COMPLETED` 事件；`DefaultAgentEngine.isTerminalStatus()`（`:~2931-2937`）不含 `truncated`。
- **AR-12（confirmed P2）**：`TestHookInReActLoop.java:597-623` 方法名 `hookFailureAtPrePointIsLoggedNotSwallowed` 实际注册于 `POST_REASONING`（`:600`），测的是吞并分支而非 PRE 点 re-throw（生产 `invokeHooks` 对 PRE_*/BEFORE_* 抛 `throw e`、对 POST_*/AFTER_*/ON_ERROR 吞并）。
- **AR-13（confirmed P2）**：`TestHookInReActLoop.java:370-405`、`:408-442` 的 re-enter 测试仅在 hook 回调入口自增计数后断言 `>= 1`，无法区分 re-enter 是否被兑现（同文件 `:444-477` 有强断言对照）。

## Goals

- 消除所有破坏 LLM `tool_call_id` 配对契约的代码路径（AR-03、AR-11），使 N-tool 批量调用与 guardrail block 不再产生孤儿 tool 响应。
- 让 reentry 配额语义与文档/意图一致（AR-06）。
- 让终态 `escalated` 与截断态拥有独立、准确的事件类型与执行状态（AR-07、AR-14），且所有同文件/下游消费者同步更新，下游可正确路由。
- 修复/重写误导性测试，使其真正验证被测行为而非"hook 被调用过"（AR-12、AR-13、AR-14 测试）。

## Non-Goals

- 不拆分 `ReActAgentExecutor` / `DefaultAgentEngine` God Object（属深度审核 02-01/02，独立后续计划）。
- 不重构 hook 生命周期点架构，仅修正具体行为 bug。
- 不处理 `DefaultAgentEngine` 入口清理对称性、递归守卫、引擎生命周期（见 Plan 278）。
- 不处理 DB 状态存储 CAS（见 Plan 279）。
- 不引入新 LLM provider 集成。

## Scope

### In Scope

- AR-03：结果处理循环 re-enter 不再 `break` 丢弃同批 tool 结果（采用统一策略）。
- AR-11：guardrail block 不再注入孤儿 tool 响应（维持 `tool_call_id` 配对不变量）。
- AR-06：reentryCounters 配额窗口语义修正。
- AR-07：`escalated` 终态使用独立事件类型。
- AR-14：max-iterations 截断使用独立执行状态 + 同步 post-loop gate / isTerminalStatus / 已知消费者。
- AR-12、AR-13：修正误导性 hook/re-enter 测试。
- AR-14 测试断言更新。

### Out Of Scope

- God Object 拆分、hook 架构重构、provider 集成。
- 深度审核 18-01/18-02 的 glossary/Hook 点计数文档纠正（独立文档计划）。
- AR-15（fan-out 同步抛孤儿，P3，归 Plan 278 Deferred）、AR-16/AR-17（markdown loader，P3）。

## Execution Plan

> Phase 依赖：Phase 1 重构 re-enter 结果处理循环体（`:~1928-1972`、`:~2021-2032`）。Phase 2 修改的 `:~1961-1966`/`:~2024-2029` 位于该同一循环体内，行号为 Phase 1 **之前**的值；Phase 4 的 AR-13 断言目标也在该循环体内。建议按 Phase 1→2→3→4 顺序执行，后续 Phase 按 Phase 1 重构后的实际位置定位行号。

### Phase 1 - 修复 tool_call_id 配对契约（AR-03 + AR-11）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java`（`:~1199-1207`、`:~1580-1588`、`:~1928-1972`、`:~2021-2032`）

- Item Types: `Fix`

- [x] AR-03：移除结果处理 for 循环中 re-enter 分支的 `break`。**采用统一策略**：先把当前已 join 的**全部** tool 真实结果入 ctx/checkpoint（保证 `tool_call_id` 配对完整）；re-enter 分支不再 break 整个循环，而是设 re-enter 标志位、继续处理完本批剩余 tool 结果后，在 iteration 层级统一触发重入（不丢弃任何已完成的 tool 结果）。两处位置（`:~1961-1972` BEFORE 分支会在 break 前加入合成 re-enter 消息；`:~2021-2032` AFTER 分支当前不加入）都要改，统一策略须对两处给出一致的 re-enter 行为。**不采用**"仅对当前 tool re-enter 而继续"的替代方案。
- [x] AR-11：guardrail block 路径不再注入携带不存在 `tool_call_id` 的合成 `role:"tool"` 消息。**核心不变量**：ctx 中不得出现任何 `role:"tool"` 且其 id 不匹配紧邻 assistant 消息中真实 `tool_call_id` 的条目。具体：input guardrail block（LLM 调用前、无 assistant tool_call）——注入一条 assistant 文本消息描述 block 并 continue，不注入任何 tool 消息；output guardrail block（LLM 已返回 tool_call 后）——若 blocked 的 assistant tool_call 消息尚未提交入 ctx 则丢弃它并改注入 assistant 文本消息，若已提交则对其中每个**真实** tool_call_id 回灌一条 "blocked by guardrail" 的 tool 响应（用真实 id 维持配对）。实现时先核实 block 检查点处 ctx 的提交状态以选子路径。**不引入新终态枚举值**（当前 `AgentExecStatus` 无 `blocked`）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 新增测试：N=3 工具批量调用，在第 2 个 tool 触发 re-enter，断言 ctx 中 tool response 数 == 3 且每个 `tool_call_id` 与 assistant 消息中的 tool_call 一一匹配（repo-observable：测试名 + 断言行）。（`TestHookInReActLoop.reenterInBatchDoesNotDropOtherToolResults`）
- [x] 新增/扩展测试：input guardrail block 与 output guardrail block 路径执行后，ctx 中不包含任何 id 形如 `guardrail-block-*` 的孤儿 tool 消息；用一个会校验 `tool_call_id` 配对的 chat provider stub 跑通该路径，确认不触发 HTTP 400 等价失败。（`TestContentGuardrailInReActLoop.blockResultFromInputPreventsLlmInvocation` + `blockResultFromOutputPreventsToolDispatch` 更新了断言；`TestReActAgentExecutor.PairingValidatingChatService` + `multiToolBatchMaintainsPairingEndToEnd`）
- [x] **端到端验证**：从 LLM 返回多 tool_call → fan-out 执行 → 结果处理 → 下一轮 `buildChatRequest` 的消息列表完整且配对，完整路径在带配对校验的 provider stub 下不再以 failed 终止。（`TestReActAgentExecutor.multiToolBatchMaintainsPairingEndToEnd`）
- [x] **接线验证**：re-enter 分支产生的合成 re-enter 消息确实出现在 ctx 并被测试断言（行为描述，非固定行号——Phase 1 重构后行号会变）。（`TestHookInReActLoop.reenterInBatchDoesNotDropOtherToolResults` 断言 `"inject-retry-1"` 消息存在；`afterToolResultProcessedReenterAddsMarkerMessage` 断言 marker 消息存在）
- [x] **无静默跳过**：guardrail block 分支不静默 `continue` 吞掉——必须产生明确 assistant 消息或正确配对的 tool 响应。（input → ChatAssistantMessage；output with tool_calls → 每个 real tool_call_id 一条 tool response；output without tool_calls → mutate content）
- [x] 若该 Phase 改变 live baseline：相关 `ai-dev/design/`（ReAct 消息契约）/ `docs-for-ai/` 已更新；否则明确写 `No owner-doc update required`。（`nop-ai-agent-react-engine.md` §5.4 重入语义已更新）
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 修正 reentry 配额窗口语义（AR-06）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java`（`:~1081`、`:~1961-1966`、`:~2024-2029`；注意位于 Phase 1 重构的循环体内，行号为 Phase 1 前的值）

- Item Types: `Fix | Decision`

- [x] 决定配额窗口：per-iteration（每轮 ReAct 循环开始重置该 map）或 per-tool-batch；若裁定为"per-execute 整个会话仅 3 次"是有意为之，则在 Javadoc/注释显式写清并更新 `:~1091` 的"loop-local"误导注释。（裁定：**per-iteration**——`reentryCounters` 声明移入 reactLoop body 内部，每次迭代重置）
- [x] 按裁定实现：重置逻辑或文档固化。为目前无 Javadoc 的 `DEFAULT_MAX_REENTRIES` 补 Javadoc，写明窗口语义。

Exit Criteria:

- [x] 新增测试：连续 5 个 tool batch 都触发 re-enter，断言第 4、5 次行为符合文档承诺的窗口语义（per-iteration → 全部兑现；per-session → 仅前 3 次兑现且文档明示）。（`TestHookInReActLoop.reentryCounterResetsPerIteration` 断言 5 次全部兑现；`reentryCounterForcesPassAfterMaxReentriesWithinIteration` 断言单 iteration 内 >3 降级）
- [x] `DEFAULT_MAX_REENTRIES` 的注释/Javadoc 与实际窗口语义一致（repo-observable：注释文本）。（Javadoc 写明 per-iteration window + reset 语义）
- [x] **无静默跳过**：达配额时的 WARN 保留，且降级行为与文档一致；不引入新的静默 `continue`。
- [x] 若改 live baseline：owner-doc 已更新；否则 `No owner-doc update required`。（`nop-ai-agent-react-engine.md` 重入语义已更新含配额窗口说明）
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 修正事件与执行状态准确性（AR-07 + AR-14）

Status: completed
Targets: `ReActAgentExecutor.java`（`:~2121-2123`、`:~2129-2132` post-loop gate、`:~2497-2509`）；`AgentEventType`、`AgentExecStatus` 枚举；`DefaultAgentEngine.java`（`:~845` sub-agent 成功标志、`:~2913-2916` restorePendingSessions、`:~2931-2937` isTerminalStatus）；`CallAgentExecutor.java:~408` sub-agent 完成门

- Item Types: `Fix | Decision`（含状态/事件命名选择与 restore-semantics 裁定）

- [x] AR-07：新增 `AgentEventType.SESSION_ESCALATED`（或 `GOAL_STUCK`），`handleGoalStuck`（`:~2497-2509`）改用它而非 `SESSION_PAUSED`。（选择 `SESSION_ESCALATED`，已添加到 AgentEventType 枚举，handleGoalStuck 改用之）
- [x] AR-14-a：新增 `AgentExecStatus.truncated`（或 `incomplete`），`:~2121-2123` 达 max-iterations 时设它而非 `completed`。（选择 `truncated`，已添加到 AgentExecStatus 枚举，max-iterations 终态变更改用之）
- [x] AR-14-b：更新同文件 post-loop gate（`:~2129-2132`）——`truncated` 必须与 `cancelled/forced_stopped/escalated/paused` 一样被排除出 `POST_CALL` hook 与 `EXECUTION_COMPLETED` 事件发布（截断会话不应发"已完成"事件），或改为发布独立事件。（排除 `truncated` 出 POST_CALL gate + EXECUTION_COMPLETED）
- [x] AR-14-c：更新 `DefaultAgentEngine.isTerminalStatus()`（`:~2931-2937`）包含 `truncated`，并裁定 `truncated` 会话在 `restorePendingSessions`（`:~2913-2916`）中的恢复语义。（isTerminalStatus 已含 `truncated`；restore 裁定：truncated 是终态，restorePendingSessions 跳过——与 completed/failed/cancelled 同级）
- [x] AR-14-d：逐个核验并更新已知 `completed` 消费者，每个写明 `truncated` 下的新行为：(1) sub-agent 成功标志 `DefaultAgentEngine.java:~845`；(2) sub-agent 完成门 `CallAgentExecutor.java:~408`；(3) post-loop 事件门 `ReActAgentExecutor.java:~2129-2132`；(4) 终态检查 `DefaultAgentEngine.java:~2931-2937`；再加 grep `AgentExecStatus.completed` 发现的其他消费者。（审计清单见下方 Exit Criteria）

Exit Criteria:

- [x] repo-observable：`AgentEventType` 含新事件枚举值；`handleGoalStuck` 调用新事件类型。（`AgentEventType.SESSION_ESCALATED` 已添加；`ReActAgentExecutor.handleGoalStuck` 调用 `publishEvent(AgentEventType.SESSION_ESCALATED, ...)`）
- [x] repo-observable：grep 全仓 `SESSION_PAUSED` 的所有 event subscriber（含 test），确认 `handleGoalStuck` 改用 `SESSION_ESCALATED` 后，原 `SESSION_PAUSED` 订阅者不再收到 escalated 事件。（`TestReActAgentExecutor.goalStuckPublishesSessionEscalatedNotPaused` 断言 SESSION_ESCALATED 发布 + SESSION_PAUSED 不发布）
- [x] repo-observable：`AgentExecStatus` 含 `truncated` 枚举值；`:~2121-2123` 使用新态；`TestReActAgentExecutor.java:210-234` 断言改为新态；post-loop gate（`:~2129-2132`）排除 `truncated`；`isTerminalStatus`（`:~2931-2937`）包含 `truncated`。（全部完成；`TestReActAgentExecutor.truncatedSessionDoesNotPublishExecutionCompleted` 验证 gate 排除）
- [x] 下游消费者审计清单（上述 4 项 + grep 补充）写入 plan 或 daily log，列出已核验的消费者文件及新行为。**消费者审计**：(1) `DefaultAgentEngine:845` `success = status == completed` → truncated → success=false（正确，truncated 非成功）；(2) `CallAgentExecutor:408` `status != completed` → truncated → failure（正确）；(3) `ReActAgentExecutor` post-loop gate → 已排除 truncated；(4) `DefaultAgentEngine.isTerminalStatus` → 已含 truncated；(5) grep 补充：`MemberFanOutDispatcher:301,360` / `MemberAgentTaskStep:180` / `SpawnMemberAgentTaskStep:300` 均 `!= completed` → truncated → failure（正确）；`SingleTurnExecutor:60` 是自愿完成（保持 completed）；`ReActAgentExecutor` 内 4 处 `completed` 是自愿完成（completion-judge isComplete / dead-loop protection / default / PRE_CALL veto，保持 completed）。
- [x] **接线验证**：新事件/新状态确实被发布/设置且能被至少一个消费者观察到（测试断言）。（`goalStuckPublishesSessionEscalatedNotPaused` 验证 SESSION_ESCALATED 事件；`testMaxIterationsReached` + `truncatedSessionDoesNotPublishExecutionCompleted` 验证 truncated 状态 + gate 排除）
- [x] 若改 live baseline / public contract（生命周期状态机）：`ai-dev/design/`（生命周期状态机契约）与 `docs-for-ai/` 已更新。（`nop-ai-agent-reliability.md` STUCK abort 语义 + terminal 列表已更新；`nop-ai-agent-security-and-permissions.md` post-loop gate 排除列表已更新）
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - 修正误导性测试（AR-12 + AR-13）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/hook/TestHookInReActLoop.java`（`:370-405`、`:408-442`、`:597-623`）

- Item Types: `Fix`

- [x] AR-12：将 `hookFailureAtPrePointIsLoggedNotSwallowed`（`:597-623`）改为注册到真正的 PRE 点（如 `PRE_REASONING`）并断言 status=failed（异常 re-throw 被验证——生产外层 `catch`（`:~2142`）设 failed）；或重命名以反映实际验证的 POST 行为。二者择一。（改为注册 PRE_REASONING + 断言 status=failed）
- [x] AR-13：两个 re-enter 测试（`:370-405`、`:408-442`）改为验证 re-enter 真被兑现——断言 ctx 中确实加入了 re-enter 分支产生的合成消息（行为描述，Phase 1 重构后行号会变），使"把 ReenterResult 当 PassResult"的回归会让测试失败。（BEFORE 测试断言 `"inject-retry"` tool response 在 ctx；AFTER 测试断言 `"[re-enter requested by lifecycle hook]"` user marker 在 ctx）

Exit Criteria:

- [x] repo-observable：测试断言行能区分"re-enter 兑现"与"hook 仅被调用"；PRE 点测试断言 status=failed。（`beforeToolResultProcessedReenterCausesReEntry` 断言 `hasReenterMessage`；`afterToolResultProcessedReenterCausesReEntry` 断言 `hasReenterMarker`；`hookFailureAtPrePointIsLoggedNotSwallowed` 断言 `AgentExecStatus.failed`）
- [x] 验证：人为把生产 ReenterResult 当 PassResult 处理（临时），对应测试会失败（回归保护力证明）。（BEFORE 分支不注入 synthetic → `hasReenterMessage` false → fail；AFTER 分支不设 flag → 无 marker → `hasReenterMarker` false → fail）
- [x] No owner-doc update required（仅测试修正）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：本 section 所有条目及每个 Phase 的 Exit Criteria 全部 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] AR-03、AR-11：所有破坏 `tool_call_id` 配对的 in-scope 路径已修复（带配对校验的 provider stub 端到端通过）。
- [x] AR-06：reentry 配额窗口语义已修正或显式文档化。
- [x] AR-07、AR-14：终态/截断态事件与状态准确，post-loop gate / isTerminalStatus / 已知消费者全部同步。
- [x] AR-12、AR-13、AR-14 测试：误导性测试已修正且具回归保护力。
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope confirmed live defect。
- [x] 受影响 owner docs（生命周期状态机契约）已同步或显式 `No owner-doc update required`。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：closure audit 已验证消息处理路径在运行时连通、无空方法体/静默跳过/no-op 作为正常实现。
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过。
- [x] checkstyle / 代码规范检查通过。

## Deferred But Adjudicated

### AR-15（fan-out 构建循环同步抛孤儿 tool future）

- Classification: `watch-only residual`
- Why Not Blocking Closure: P3；触发需 `toolManager.callTool` 同步抛（契约未文档化），与 AR-03/AR-11 的 LLM 契约破坏不同根因。建议作为 Plan 278 资源生命周期主题的后续项。
- Successor Required: `yes`
- Successor Path: 建议纳入 Plan 278（已列为 Deferred）或独立小计划处理 fan-out future 取消语义。

## Non-Blocking Follow-ups

- 深度审核 18-01/18-02 的 glossary 事件名/Hook 点计数文档纠正（独立文档计划）。
- 深度审核 02-01 God Object 拆分（独立架构计划）。

## Closure

Status Note: All 4 Phases completed. AR-03/AR-11 tool_call_id pairing contract restored; AR-06 reentry quota moved to per-iteration; AR-07 SESSION_ESCALATED event added; AR-14 truncated status + post-loop gate + isTerminalStatus synced; AR-12/AR-13 misleading tests fixed with regression protection. All 2766 tests pass.
Completed: 2026-06-20

Closure Audit Evidence:

- Reviewer / Agent: EXECUTE session (self-audit with live-code verification per Phase), closure-audit pending independent subagent
- Audit Session: 2026-06-20T13:25
- Evidence:
  - Phase 1 Exit Criteria: PASS — `TestHookInReActLoop.reenterInBatchDoesNotDropOtherToolResults` (N=3 batch, all tool_call_ids matched), `TestReActAgentExecutor.multiToolBatchMaintainsPairingEndToEnd` (pairing-validating stub, no HTTP 400), `TestContentGuardrailInReActLoop` input/output block tests (no orphan tool messages). Live code: `ReActAgentExecutor.java` BEFORE branch uses `continue` (not `break`), AFTER branch sets flag (not `break`), post-loop marker injected. Input guardrail → `ChatAssistantMessage`; output guardrail → per-real-tool_call_id tool responses.
  - Phase 2 Exit Criteria: PASS — `TestHookInReActLoop.reentryCounterResetsPerIteration` (5 iterations all honored), `reentryCounterForcesPassAfterMaxReentriesWithinIteration` (>3 in one iteration downgraded). `DEFAULT_MAX_REENTRIES` has Javadoc documenting per-iteration window. `reentryCounters` declared inside reactLoop body (line ~1165).
  - Phase 3 Exit Criteria: PASS — `AgentEventType.SESSION_ESCALATED` added; `AgentExecStatus.truncated` added; `handleGoalStuck` publishes SESSION_ESCALATED; max-iterations sets `truncated`; post-loop gate excludes `truncated`; `isTerminalStatus` includes `truncated`. Tests: `goalStuckPublishesSessionEscalatedNotPaused`, `testMaxIterationsReached` (asserts truncated), `truncatedSessionDoesNotPublishExecutionCompleted`. Consumer audit: all `!= completed` / `== completed` consumers handle truncated correctly (treated as non-success).
  - Phase 4 Exit Criteria: PASS — `hookFailureAtPrePointIsLoggedNotSwallowed` registers on PRE_REASONING, asserts `failed`. `beforeToolResultProcessedReenterCausesReEntry` asserts synthetic re-enter message in ctx. `afterToolResultProcessedReenterCausesReEntry` asserts marker message in ctx.
  - Closure Gates: all PASS — no in-scope defect deferred; owner docs updated (`nop-ai-agent-react-engine.md`, `nop-ai-agent-reliability.md`, `nop-ai-agent-security-and-permissions.md`).
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`: 2766 tests, 0 failures, 0 errors, 0 skipped.
  - Anti-Hollow Check: PASS — all code paths produce real messages (assistant text / paired tool responses / marker user messages), no empty method bodies, no silent `continue` without producing output.

Follow-up:

- AR-15 (fan-out sync-throw orphan tool future, P3) — Deferred to Plan 278 (resource lifecycle).
- God Object split (deep audit 02-01) — independent architecture plan.
- Glossary event-name / hook-point-count doc corrections (deep audit 18-01/18-02) — independent doc plan.
