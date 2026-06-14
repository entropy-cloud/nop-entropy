# 165 LLM-Based Completion Judge (Design §5.3 Phase 2 "Small Model" Strategy)

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: llm-completion-judge
> **Last Reviewed**: 2026-06-14
> **Source**: Carry-over from plan 162 (`ai-dev/plans/162-nop-ai-agent-functional-completion-judge.md`, Deferred: "LLM-Based 'Small Model' Judge", Successor Required: yes); roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` A2 ✅ (contract + rule-based functional); design `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md` §5.3 Phase 2 ("用规则或小模型")
> **Related**: Plan 159 (ICompletionJudge interface + NoOp + ReAct integration), Plan 162 (RuleBasedCompletionJudge — the deterministic sibling strategy)

## Purpose

Realize the "小模型" half of design §5.3 Phase 2 Completion Gate strategy. The deterministic `RuleBasedCompletionJudge` (plan 162) catches structural premature-completion patterns (empty/trivial/near-budget). An LLM-based Judge adds **semantic** verification: it asks a small model whether the assistant's response actually addresses the task goal, catching cases where the response is structurally valid but substantively incomplete (e.g., the LLM gives a confident-sounding but partial answer, or claims completion without addressing all requirements).

## Current Baseline

- Plan 159 (completed): `ICompletionJudge` interface (`completion/ICompletionJudge.java`), `CompletionDecision` abstract base type with package-private constructor sealing (Continue/Complete/Escalate — `completion/CompletionDecision.java`), `NoOpCompletionJudge` pass-through singleton, ReAct integration at `ReActAgentExecutor.java:398` with dead-loop protection (`DEFAULT_MAX_COMPLETION_CONTINUES = 3`, line 82).
- Plan 162 (completed): `RuleBasedCompletionJudge` — deterministic rule-based Judge (empty/blank → Continue, trivially-short → Continue, near-budget → Escalate, default → Complete). `CompletionRuleConfig` with configurable thresholds. Unit tests (29 methods) + integration tests (6 scenarios through real ReAct loop). All 525 module tests pass.
- `IChatService.call(ChatRequest, ICancelToken)` — synchronous non-streaming LLM call available in `nop-ai-api` (`IChatService.java:19`). `ChatResponse` exposes `getMessage()` (ChatAssistantMessage with content), `getPromptTokens()`/`getCompletionTokens()`, and `isSuccess()`.
- `AgentExecutionContext` exposes: `agentModel` (getName/getDescription/getPrompt), `messages` (conversation history List), `plan` (AgentPlanModel with `getGoal()`), `currentIteration`, `maxIterations`, `metadata` (Map), `tokensUsed` (long), `isCancelRequested()` — sufficient context for semantic completion assessment.
- `ChatRequest` supports a `systemAndUserPrompt(system, user)` factory, `withSystemPrompt()` to set the system prompt, and `addUserPrompt()` to append a user message. For options, prefer the chainable `with*` variants — `withTemperature(Float)` and `withMaxTokens(Integer)` — because the `setTemperature(float)` / `setMaxTokens(int)` methods return `void` and are NOT chainable.
- Grep confirms zero code referencing `LlmCompletionJudge`, `LLMCompletionJudge`, `LlmJudge`, or any LLM-based Judge in `nop-ai-agent/src`.
- Design §5.3 Phase 2 specifies two alternative strategies: "用规则或小模型". Plan 162 delivered "规则". This plan delivers "小模型".
- `IGoalTracker` (L3-3) is ❌ (not started). The LLM Judge does NOT depend on it — it works from the task goal in the plan/model + the assistant message content + conversation context.
- Design §5.3 key constraint: "Judge 的裁决是'建议'不是'命令'——引擎保留最终跳出权". The dead-loop protection (`DEFAULT_MAX_COMPLETION_CONTINUES = 3`) already bounds repeated Continue decisions at the engine level.

## Goals

- `LlmCompletionJudge` — a functional Judge implementing `ICompletionJudge` that calls `IChatService` to semantically verify task completion, complementing the deterministic `RuleBasedCompletionJudge` as an alternative Phase-2 strategy
- Configurable via a config object carrying: the `IChatService` to use for judgment calls, the judge system prompt (with sensible default), model name override, max response tokens, temperature, the fallback decision on error/unparseable response, and the max number of conversation messages to include as context
- Graceful degradation: on LLM call error, null/empty response, or unparseable verdict, return the configured fallback decision (default: Complete — consistent with design §5.3 "Judge's verdict is a suggestion, not a command")
- Verdict parsing: the Judge's LLM response is parsed into one of Continue/Complete/Escalate; the parser extracts a continuation message (Continue) or escalation reason (Escalate) from the response when present
- Auditability: each Judge invocation records the raw LLM verdict and fallback flag in `ctx.getMetadata()` for observability
- Unit tests with mocked `IChatService` covering: all three verdicts, parse-failure fallback, call-error fallback, empty-response guards, cancel-requested guard, custom config overrides, context-limit truncation, metadata recording
- End-to-end integration test through `ReActAgentExecutor` exercising the LLM Judge's Complete/Continue paths through a real ReAct loop
- Backward compatible: existing tests using NoOp/RuleBased defaults remain unchanged

## Non-Goals

- Composing LLM Judge with RuleBased Judge (e.g., rules-first-then-LLM pipeline) — optimization candidate, separate follow-up
- Adaptive threshold tuning (design §5.3 Phase 3) — depends on accuracy history accumulation
- `IGoalTracker` (L3-3) integration — the LLM Judge works from the task goal in the plan/model, not from an explicit goal checklist
- `DefaultAgentEngine` constructor-chain extension — the Builder setter (plan 159) is sufficient
- DSL configuration in `agent.xdef` — runtime-pluggable via Builder, consistent with the routing-is-runtime-pluggable decision
- Semantic content-quality assessment beyond binary completion verification — the Judge answers "is the task done?" not "is the response high-quality?"; deeper quality scoring is a further follow-up
- Async/streaming Judge calls — `decide()` is synchronous; `IChatService.call()` is synchronous
- Multi-turn judgment dialogue — single-call decision per invocation

## Scope

### In Scope

- `LlmCompletionJudge` functional implementation with configurable prompt, model, and fallback
- `LlmJudgeConfig` configuration object with defaults
- Verdict response parsing logic
- Unit tests with mocked `IChatService`
- End-to-end integration test through `ReActAgentExecutor`
- Metadata recording for auditability

### Out Of Scope

- RuleBased+LLM composition Judge
- IGoalTracker integration
- DefaultAgentEngine wiring
- Adaptive tuning
- DSL Judge configuration
- Quality assessment beyond completion verification

## Execution Plan

### Phase 1 — LlmCompletionJudge Implementation and Unit Tests

Status: completed
Targets: `io.nop.ai.agent.completion.LlmCompletionJudge`, `io.nop.ai.agent.completion.LlmJudgeConfig`, verdict parsing

- Item Types: `Decision | Proof`

- [x] **Decision (standalone LLM Judge, not composition)**: This plan implements a standalone LLM-based Judge, not a composition that chains RuleBased → LLM. Rationale: (1) design §5.3 says "用规则**或**小模型" — these are alternative strategies, not layers; (2) a standalone Judge is independently testable and composable by callers (they can wrap it with a RuleBased delegate if desired); (3) composition logic is an optimization that can be added later without changing the interface.

- [x] **Decision (fail-open default)**: On LLM call error or unparseable response, the Judge returns `Complete` by default (configurable to Continue or Escalate). Rationale: (1) design §5.3 states "Judge 的裁决是'建议'不是'命令'——引擎保留最终跳出权"; (2) failing-open preserves the original ReAct loop termination behavior when the Judge infrastructure is unavailable; (3) failing-closed (Continue) would risk dead-looping when the Judge itself is broken, fighting the engine's dead-loop protection; (4) callers who want fail-closed behavior can configure `fallbackDecision`.

- [x] **Decision (verdict output format)**: The Judge's LLM is instructed to output exactly one keyword on the first non-empty line: `COMPLETE`, `CONTINUE`, or `ESCALATE` (case-insensitive). If CONTINUE, the remainder of the response is used as the continuation guidance message. If ESCALATE, the remainder is used as the escalation reason. Unparseable first-line → fallback decision. This format is simple to parse, language-agnostic, and minimizes token overhead.

- [x] Implement `LlmJudgeConfig` carrying: `IChatService chatService` (required), `String systemPrompt` (with a default judge prompt that explains the role, the three verdicts, and the output format), `String model` (optional model-name override — passed via ChatOptions), `Integer maxTokens` (default 200 — Judge responses are short), `Float temperature` (default 0.0 for deterministic judgment), `CompletionDecision fallbackDecision` (default `Complete.instance()`), `int maxContextMessages` (default 20 — cap conversation history included in the prompt to control cost). Input validation: chatService must not be null; maxTokens > 0; temperature in [0.0, 2.0]; maxContextMessages >= 0. Provide a static factory `defaults(IChatService)` returning a zero-tuning instance.

- [x] Implement `LlmCompletionJudge` implementing `ICompletionJudge` with the following behavioral semantics:
  - **Null/blank guard**: if `assistantMessage` is null or its content is null/empty/whitespace-only → return `Complete` without calling the LLM (structural emptiness is the RuleBased Judge's domain; calling an LLM on empty content wastes tokens). The `chatService.call()` invocation count must be 0 in this path.
  - **Cancel guard**: if `ctx != null && ctx.isCancelRequested()` → return `fallbackDecision` without calling the LLM.
  - **Prompt construction**: build a `ChatRequest` containing (a) the system prompt from config (judge instructions + output format), (b) a user message composed of: the task goal (from `ctx.getPlan().getGoal()` if plan and goal are non-null, else `ctx.getAgentModel().getDescription()` if agentModel is non-null, else literal "N/A"), the last `maxContextMessages` messages from `ctx.getMessages()` as conversation context (if ctx is available), and the assistant's response content being judged. Conversation-context selection: take the last N messages regardless of type (do NOT over-engineer filtering), exclude any system-role message to avoid duplicating the Judge's own system prompt, and concatenate the remaining ones as prior-turn context (role + content) into the user message. Set temperature and maxTokens from config. Set model override if configured (via `request.makeOptions().setModel(...)` since `ChatRequest` carries the model name on its `ChatOptions`, not via a direct setter).
  - **LLM call**: call `chatService.call(request, null)` synchronously. Wrap in try-catch — any exception → fallback decision (no exception propagates to the ReAct loop).
  - **Error response guard**: if `response` is null, `response.isSuccess()` is false, or `response.getMessage()` is null → fallback decision.
  - **Verdict parsing**: extract the first non-empty line of `response.getMessage().getContent()`, match case-insensitively against COMPLETE/CONTINUE/ESCALATE. If CONTINUE, use the remaining text (after the first line) as the continuation message (trimmed; if empty, use a default continuation message). If ESCALATE, use the remaining text as the reason. If no match → fallback decision.
  - **Metadata recording**: after each `decide()` call (both LLM-success and fallback paths where ctx is available), record in `ctx.getMetadata()`: `completion.llmJudgeVerdict` (the raw first-line of the LLM response, or "FALLBACK" if fallback was used), and `completion.llmJudgeFallback` (Boolean true/false). If the LLM response includes token usage, add it to `ctx.getTokensUsed()`.

- [x] `TestLlmCompletionJudge` unit tests with mocked `IChatService` covering:
  - (a) LLM returns "COMPLETE" → Complete decision
  - (b) LLM returns "CONTINUE\nPlease address requirement X" → Continue with continuation message containing "requirement X"
  - (c) LLM returns "ESCALATE\nAmbiguous requirements need human input" → Escalate with reason containing "Ambiguous"
  - (d) LLM returns lowercase "complete" → Complete (case-insensitive matching)
  - (e) LLM returns "  COMPLETE  " with leading/trailing whitespace → Complete (trimmed)
  - (f) LLM returns "COMPLETE\n\nextra notes" → Complete (first-line match, extra lines ignored)
  - (g) LLM returns unparseable text (e.g., "Yes, the task appears complete.") → fallback decision (Complete by default)
  - (h) LLM returns empty content string → fallback decision
  - (i) LLM returns null message → fallback decision
  - (j) `IChatService.call()` throws RuntimeException → fallback decision (no exception propagates)
  - (k) `IChatService.call()` returns error response (`isSuccess() == false`) → fallback decision
  - (l) Custom `fallbackDecision = Continue` → all parse-failure/error paths return Continue instead of Complete
  - (m) Null `assistantMessage` → Complete (guard; verify `chatService.call()` invocation count is 0)
  - (n) Blank/whitespace-only assistantMessage → Complete (guard; verify `chatService.call()` invocation count is 0)
  - (o) `ctx.isCancelRequested() == true` → fallback decision (guard; verify `chatService.call()` invocation count is 0)
  - (p) `maxContextMessages = 3` with a ctx containing 10 messages → verify the `ChatRequest` passed to `chatService` references at most 3 conversation messages in its user prompt
  - (q) Custom `systemPrompt` → verify the `ChatRequest` passed to `chatService` contains the custom system prompt
  - (r) Custom `temperature = 0.7` → verify the `ChatRequest` carries temperature 0.7
  - (s) Metadata recorded: after a successful verdict, `ctx.getMetadata()` contains `completion.llmJudgeVerdict` and `completion.llmJudgeFallback == false`
  - (t) Metadata recorded: after a fallback, `ctx.getMetadata()` contains `completion.llmJudgeVerdict == "FALLBACK"` and `completion.llmJudgeFallback == true`
  - (u) Config validation: null chatService → IllegalArgumentException; maxTokens <= 0 → IllegalArgumentException; temperature out of range → IllegalArgumentException

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `LlmCompletionJudge` exists, implements `ICompletionJudge`, and produces all three `CompletionDecision` outcomes based on LLM verdicts
- [x] `LlmJudgeConfig` provides all configurable fields with input validation and a zero-tuning static factory
- [x] Null/blank/cancel guards prevent unnecessary LLM calls (verified by invocation-count assertions)
- [x] All error/parse-failure paths return the configured fallback decision without propagating exceptions
- [x] Unit tests cover every verdict, every fallback path, every guard, config validation, and metadata recording (45 test methods in `TestLlmCompletionJudge`)
- [x] **无静默跳过**: every code path in `decide()` produces an explicit `CompletionDecision` subtype (Continue/Complete/Escalate) or the fallback decision; no null return, no empty body, no silent exception swallow without a fallback return (Rule #24)
- [x] **端到端验证** N/A: unit-test phase — end-to-end verification is in Phase 2
- [x] **接线验证** N/A: no inter-component wiring in this phase (LLM call is mocked)
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes (672 tests, 0 failures — existing + 45 new)
- [x] No owner-doc update required for Phase 1 (design doc §5.3 Phase 2 strategy already specifies "用规则或小模型"; implementation details belong in source code per guide rule #14)
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 2 — End-to-End Integration Through ReAct Loop

Status: completed
Targets: integration test `io.nop.ai.agent.completion.TestLlmJudgeInReActLoop`

- Item Types: `Proof`

- [x] Create `TestLlmJudgeInReActLoop` (integration test) wiring `LlmCompletionJudge` via `ReActAgentExecutor.Builder.completionJudge()`:
  - **Scenario 1 (LLM says Complete)**: the main chat service returns a substantive no-tools response; the Judge's chat service returns "COMPLETE" → loop exits with status `completed`, `EXECUTION_COMPLETED` event published
  - **Scenario 2 (LLM says Continue → re-enter → Complete)**: the main chat service returns a substantive no-tools response on first call; the Judge says "CONTINUE\nYou haven't addressed requirement Y" on first invocation; the main chat service returns another substantive response on second call; the Judge says "COMPLETE" on second invocation → loop exits with status `completed`, main chat service called twice, continuation message containing "requirement Y" injected before re-entry
  - **Scenario 3 (Judge LLM call fails → fallback Complete)**: the main chat service returns a substantive no-tools response; the Judge's chat service throws an exception → fallback Complete → loop exits with status `completed`, `completion.llmJudgeFallback == true` in metadata
  - **Scenario 4 (dead-loop protection)**: the main chat service always returns substantive no-tools responses; the Judge always says "CONTINUE" → after `DEFAULT_MAX_COMPLETION_CONTINUES = 3` consecutive Continues, engine force-exits with status `completed`
- [x] Use separate `IChatService` instances for the main ReAct loop and the Judge (the Judge uses its own cheap-model service) — verify they are independent
- [x] Confirm backward compatibility: existing `TestCompletionGateInReActLoop` and `TestRuleBasedJudgeInReActLoop` tests pass unchanged (NoOp and RuleBased defaults are untouched)

Exit Criteria:

> 每个 Phase 宍成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] Integration test wires `LlmCompletionJudge` via `Builder.completionJudge()` with a dedicated Judge `IChatService` and exercises the Complete path end-to-end
- [x] Integration test exercises Continue → re-enter-reasoning → Complete end-to-end (main chat service called twice, continuation message injected)
- [x] Integration test exercises fallback-Complete when the Judge's LLM call fails (no exception propagates to the ReAct loop, `completion.llmJudgeFallback == true` in metadata)
- [x] Integration test verifies dead-loop protection bounds LLM-Judge Continue decisions (consistent with RuleBased Judge behavior from plan 162)
- [x] **端到端验证**: from `ReActAgentExecutor.execute()` entry point, through main LLM response, through `LlmCompletionJudge.decide()` (which internally calls the Judge's `IChatService.call()`), through verdict parsing, through decision dispatch, to loop exit — complete path verified (Anti-Hollow Rule #22)
- [x] **接线验证**: integration test confirms `LlmCompletionJudge.decide()` is actually called from within `ReActAgentExecutor.execute()` at the "no tool calls" branch (invocation counter or mock verify — reuse the `CountingJudge` decorator pattern from `TestRuleBasedJudgeInReActLoop`) (Rule #23)
- [x] **无静默跳过**: all decision paths in the ReAct integration perform explicit actions; the Judge's fallback path produces an explicit Complete decision, not a null or silent skip (Rule #24)
- [x] Existing `TestCompletionGateInReActLoop` and `TestRuleBasedJudgeInReActLoop` tests pass unchanged (backward compatible)
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes (678 tests, 0 failures)
- [x] No owner-doc update required (design doc §5.3 already specifies the Completion Gate algorithm and the "小模型" Phase-2 strategy; roadmap A2 remains ✅ — this is the Phase-2 "小模型" fill-in, not a new contract)
- [x] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] `LlmCompletionJudge` implements `ICompletionJudge` and produces all three decision outcomes based on LLM verdicts
- [x] All error/parse-failure paths return the configured fallback decision without propagating exceptions
- [x] All existing tests pass unchanged (backward compatible — NoOp and RuleBased remain available as Builder defaults)
- [x] New unit tests cover every verdict, every fallback path, every guard, config validation, and metadata recording
- [x] New integration test verifies the LLM Judge end-to-end through a real ReAct loop (Complete, Continue→re-enter, fallback, dead-loop)
- [x] **Anti-Hollow Check**: closure audit verifies (a) `LlmCompletionJudge.decide()` is called from `ReActAgentExecutor.execute()` at runtime, (b) the Continue → re-enter → Complete path runs end-to-end, (c) the fallback path produces explicit decisions (no empty bodies / silent no-ops / null returns)
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] Deferred items correctly classified as out-of-scope/optimization with explicit non-blocking rationale
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` passes
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] checkstyle / 代码规范检查通过
- [x] No silent no-op or empty method body in new code
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit code 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high` — no new findings in the `completion/` package
- [x] Independent closure audit completed and evidence recorded

## Deferred But Adjudicated

### RuleBased+LLM Composition Judge

- Classification: `optimization candidate`
- Why Not Blocking Closure: The standalone LLM Judge is independently usable. A composition that runs cheap rules first and only invokes the LLM on rule-passing responses is a cost optimization. Callers can achieve composition today by wrapping the LLM Judge with a rule-based delegate. Adding a built-in composition class is a refinement.
- Successor Required: no

### IGoalTracker (L3-3) Integration

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: The LLM Judge works from the task goal in the plan/model + conversation context. IGoalTracker would provide an explicit goal checklist for more precise verification, but the LLM can infer completion from the goal text alone. IGoalTracker integration is most valuable when combined with adaptive tuning (Phase 3).
- Successor Required: no

### DefaultAgentEngine-Level Judge Injection

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: The Builder setter (plan 159) is sufficient to wire the LLM Judge. Engine-level API injection is only needed when the Judge must be configurable through the engine constructor API rather than the Builder.
- Successor Required: no

### Adaptive Threshold Tuning (Design §5.3 Phase 3)

- Classification: `optimization candidate`
- Why Not Blocking Closure: Adaptive tuning depends on a functional Judge existing first and accumulating accuracy history. Both the rule-based and LLM-based Judges now exist; Phase 3 can build on either.
- Successor Required: no

### DSL Judge Configuration

- Classification: `optimization candidate`
- Why Not Blocking Closure: The Judge is runtime-pluggable via the Builder. DSL configuration can be added without changing the interface, consistent with the routing-is-runtime-pluggable decision.
- Successor Required: no

### Deep Semantic Content-Quality Assessment

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: The LLM Judge answers "is the task done?" (binary completion verification). Deeper quality assessment (correctness, completeness, tone, safety) is a different concern that would require a different prompt, scoring model, and potentially a different interface. The LLM Judge provides the foundation (IChatService call + verdict parsing) that a quality assessor could build on.
- Successor Required: no

## Non-Blocking Follow-ups

- RuleBased+LLM composition Judge (cost optimization — run cheap rules first, LLM only on rule-passing responses)
- `DefaultAgentEngine` constructor-chain extension for Judge injection
- `IGoalTracker` (L3-3) integration with the LLM Judge
- Adaptive threshold tuning (design §5.3 Phase 3 — needs accuracy history)
- DSL configuration for Judge selection in `agent.xdef`
- Deep semantic content-quality assessment beyond binary completion verification

## Closure

Status Note: Plan delivers design §5.3 Phase 2 "小模型" LLM-based Completion Judge. The deterministic sibling `RuleBasedCompletionJudge` (plan 162) delivered "规则"; this plan delivers "小模型". Both are now available as alternative Phase-2 strategies via `ReActAgentExecutor.Builder.completionJudge()`. The LLM Judge adds semantic verification (asks a small model whether the response substantively addresses the task goal) complementing the rule-based Judge's structural checks. Fail-open semantics consistent with design §5.3 ("Judge 的裁决是'建议'不是'命令'") and bounded by engine dead-loop protection. Backward compatible: NoOp and RuleBased defaults untouched.
Completed: 2026-06-14

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (session `ses_13d522ac1ffeMgwCZxT4lKHNiH`, fresh session — not the implementation session)
- Audit Session: `ses_13d522ac1ffeMgwCZxT4lKHNiH`
- Evidence:
  - **A. implements ICompletionJudge** — PASS: `LlmCompletionJudge.java:38` `public final class LlmCompletionJudge implements ICompletionJudge`; `decide()` signature matches `ICompletionJudge.java:8`
  - **B. all three outcomes** — PASS: `parseVerdict()` returns `Complete.instance()` (line 125), `new Continue(message)` (line 129), `new Escalate(reason)` (line 134)
  - **C. fail-open semantics** — PASS: exception catch returns fallback (lines 88-92, no rethrow); null/error/null-message response returns fallback (lines 94-97); unparseable verdict returns fallback (lines 136-138); default fallback = Complete (`LlmJudgeConfig.java:85`)
  - **D. guards precede LLM call** — PASS: null/blank content guard (lines 73-75) and cancel guard (lines 78-81) both return before `chatService.call()` (first reached at line 87)
  - **E. no silent no-op (Rule #24)** — PASS: every branch in `decide()`/`parseVerdict()` returns explicit decision; no `return null`; no empty body; catch returns fallback
  - **F. metadata recorded on both paths** — PASS: success path writes `META_KEY_VERDICT`+`fallback=false` (lines 124/128/133); fallback path writes `fallback=true` (lines 79/90/95/103/114/137)
  - **G. verdict parsing** — PASS: `firstNonEmptyLine()` skips blanks; `.toUpperCase()` case-insensitive switch; remainder-as-message/reason
  - **H. wiring (Rule #23)** — PASS: `TestLlmJudgeInReActLoop` CountingJudge assertion `counting.invocations.get()==1` (line 247) + dedicated test `judgeSvc.callCount.get()==1` (line 414); `ReActAgentExecutor.java:398` `completionJudge.decide(assistantMsg, ctx)`
  - **I. end-to-end (Rule #22)** — PASS: Scenario 2 (`llmJudgeContinueThenCompleteReEntersAndInjectsContinuationMessage`) main called twice (line 286), continuation "requirement Y" injected (lines 300-306), loop exits completed
  - **J. backward compat** — PASS: `NoOpCompletionJudge`/`RuleBasedCompletionJudge` intact; Builder default unchanged (`ReActAgentExecutor.java:132/274`); only NEW files added to `completion/`
  - **K. config validation** — PASS: null chatService / maxTokens<=0 / temperature out of [0,2] / maxContextMessages<0 all throw IllegalArgumentException (`LlmJudgeConfig.java:66-79`)
  - **L. test coverage** — PASS: `TestLlmCompletionJudge`=45 @Test methods covering (a)-(u); `TestLlmJudgeInReActLoop`=6 @Test methods
  - **M. deferred classification** — PASS: all 6 deferred items have Classification + Why-Not-Blocking rationale; none are disguised live defects
  - **Anti-Hollow Check** — PASS: (a) `decide()` called from `execute()` at runtime (proven by invocation counters); (b) Continue→re-enter→Complete end-to-end (Scenario 2); (c) fallback produces explicit Complete (Scenario 3, no null/empty/no-op)
  - **checklist tool**: `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit code 0
  - **hollow scan**: `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high` exit code 0; NO findings in `completion/` package (pre-existing findings are all in engine/hook/memory/session subsystems)
  - **build/test**: `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → 678 tests, 0 failures, 0 errors (672 pre-existing + 45 unit + 6 integration new)
  - **Deferred 项分类检查**: all 6 deferred items are `optimization candidate` or `out-of-scope improvement` — no in-scope live defect downgraded

Follow-up:

- no remaining plan-owned work (see "Non-Blocking Follow-ups" for optimization/out-of-scope items tracked outside this plan)
