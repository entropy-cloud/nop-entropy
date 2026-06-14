# 162 Functional Completion Judge: RuleBasedCompletionJudge (Design Phase 2 Strategy)

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: functional-completion-judge
> **Last Reviewed**: 2026-06-14
> **Source**: Carry-over from plan 159 (`ai-dev/plans/159-nop-ai-agent-completion-gate.md`, Deferred: "Lightweight / Rule-Based Judge (Design Phase 2 Strategy)", Successor Required: yes); roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 (A2 ✅ for contract; Phase-2 functional implementation is this plan); design `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md` §5.3 Phase 2
> **Related**: Plan 159 (ICompletionJudge interface + NoOp default + ReAct integration — the contract this plan fills with a functional implementation)

## Purpose

Replace the inert `NoOpCompletionJudge` pass-through with a functional, deterministic, configurable rule-based Judge that detects the most common premature-completion patterns. This realizes design doc §5.3 Phase-2 strategy ("轻量 Judge：用规则检查是否所有 required task 已完成") and makes the Completion Gate actually verify task completion instead of unconditionally approving the first "no tool calls" response.

## Current Baseline

- Plan 159 (completed) established: `ICompletionJudge` interface (`completion/ICompletionJudge.java`), `CompletionDecision` sealed result type with three outcomes (Continue/Complete/Escalate — `completion/CompletionDecision.java`), `NoOpCompletionJudge` pass-through singleton (`completion/NoOpCompletionJudge.java`), and the ReAct integration at `ReActAgentExecutor.java:367-401` with dead-loop protection (`DEFAULT_MAX_COMPLETION_CONTINUES = 3`, line 77).
- `NoOpCompletionJudge.decide()` always returns `Complete` — the Completion Gate is structurally present but functionally inert. The ReAct loop terminates on the first "no tool calls" response regardless of whether the task is actually done (design §5.3 false/premature-completion problem).
- `ReActAgentExecutor.Builder.completionJudge(ICompletionJudge)` setter exists (line 215), defaulting to `NoOpCompletionJudge` when unset (line 246). A functional Judge can be wired via the Builder without any `DefaultAgentEngine` change.
- `AgentExecutionContext` exposes: `currentIteration`, `maxIterations`, `messages` (conversation history), `agentModel` (goal/instructions), `plan` (AgentPlanModel with goal/phases/tasks), `metadata` map — sufficient context for rule-based completion heuristics.
- Grep confirms zero code referencing `RuleBasedCompletionJudge`, `LlmCompletionJudge`, or any non-NoOp Judge implementation in `nop-ai-agent/src`.
- Design doc §5.3 specifies three phases: Phase 1 (no Judge — done via NoOp), Phase 2 (轻量 Judge — this plan), Phase 3 (自适应 Judge — future).
- `IGoalTracker` (L3-3) is ❌ (not started). The rule-based Judge does NOT depend on it — it works from the assistant message content + execution context, not from an explicit goal checklist.

## Goals

- `RuleBasedCompletionJudge` — a functional, deterministic, configurable Judge implementing `ICompletionJudge`, replacing the inert `NoOpCompletionJudge` for callers that want actual completion verification
- Configurable completion-heuristic rules (behavioral semantics per design §5.3 Phase 2):
  - **Empty/blank-response detection**: assistant message content is null, empty, or whitespace-only → `Continue` (inject a continuation message prompting the LLM to produce its output)
  - **Trivially-short-response detection**: trimmed content length falls below a configurable minimum threshold → `Continue`
  - **Near-budget-exhaustion detection**: `currentIteration >= maxIterations * escalationRatio` (configurable, default 0.9) → `Escalate` (human review before the loop force-exits on maxIterations)
  - All rules pass → `Complete`
- All three decision outcomes (`Continue`, `Complete`, `Escalate`) are producible by the Judge depending on input
- Unit tests covering every rule, every decision outcome, boundary conditions, and custom configuration
- End-to-end integration test wiring the functional Judge via `ReActAgentExecutor.Builder.completionJudge()` and exercising Continue → re-enter-reasoning, Escalate → status `escalated`, and Complete → loop-exit paths through a real ReAct loop
- Backward compatible: existing tests using `NoOpCompletionJudge` default remain unchanged

## Non-Goals

- LLM-based "small model" Judge (design §5.3 Phase 2 alternative) — needs `IChatService` wiring, prompt engineering, token cost, and potentially `IGoalTracker` (L3-3 ❌). Deferred to a separate successor plan.
- Adaptive threshold tuning (design §5.3 Phase 3) — depends on accuracy history accumulation, strictly future work.
- `DefaultAgentEngine` constructor-chain extension for Judge injection — the Builder setter (plan 159) is sufficient to wire the functional Judge; engine-level API injection is a non-blocking follow-up.
- `IGoalTracker` (L3-3) integration — the rule-based Judge works from message content + context heuristics, not from an explicit goal checklist.
- DSL configuration for Judge selection in `agent.xdef` — the Judge is runtime-pluggable via the Builder, consistent with the routing-is-runtime-pluggable decision.
- Semantic/quality assessment of completion content (e.g., "did the response actually address the task?") — requires an LLM-based Judge, explicitly deferred.

## Scope

### In Scope

- `RuleBasedCompletionJudge` functional implementation with configurable rules
- Completion-rule configuration (thresholds)
- Unit tests for each rule and decision outcome
- End-to-end integration test through `ReActAgentExecutor` with the functional Judge wired via Builder

### Out Of Scope

- LLM-based Judge implementation
- `DefaultAgentEngine` wiring
- `IGoalTracker` integration
- Adaptive tuning
- DSL Judge configuration
- Semantic content-quality assessment

## Execution Plan

### Phase 1 - RuleBasedCompletionJudge Implementation and Unit Tests

Status: completed
Targets: `io.nop.ai.agent.completion.RuleBasedCompletionJudge`, completion-rule configuration

- Item Types: `Decision | Proof`

- [x] **Decision (rule-based over LLM-based)**: This plan implements a rule-based Judge rather than an LLM-based "small model" Judge. Rationale: (1) design §5.3 Phase 2 lists rules as the first option ("用规则或小模型"); (2) rule-based is deterministic, testable without mocking external LLM calls, has zero token cost, and has no unmet dependencies; (3) it provides genuine value over NoOp (catches empty/trivial/near-budget premature completions); (4) the LLM-based Judge needs chat-service wiring + prompt engineering + potential IGoalTracker (L3-3 ❌) and warrants a separate successor plan.
- [x] Implement `RuleBasedCompletionJudge` implementing `ICompletionJudge` with configurable rules evaluated in priority order:
  - Rule 1 (empty/blank response): assistant content is null/empty/whitespace-only → `Continue` with a continuation message
  - Rule 2 (trivially short response): trimmed content length < `minResponseLength` (configurable, default 10) → `Continue`
  - Rule 3 (near-budget exhaustion): `currentIteration >= maxIterations * escalationRatio` (configurable, default 0.9) → `Escalate` with a descriptive reason referencing the iteration/budget
  - Default: all rules pass → `Complete`
- [x] Rules must be configurable via a configuration object carrying `minResponseLength`, `escalationRatio`, and the continuation/escalation message text. Provide sensible defaults so the Judge works zero-config. Provide a static factory method returning an instance with defaults (following the `NoOpCompletionJudge.noOp()` convention).
- [x] **Decision (rule evaluation order)**: Rules evaluate in the order: empty → trivial → near-budget → complete. Rationale: empty/trivial checks are cheap and high-precision; near-budget escalation is a safety net that should only fire when the response has substantive content but the loop is about to hit maxIterations. This ordering avoids escalating on an empty response when a Continue is more appropriate.
- [x] `TestRuleBasedCompletionJudge` unit tests covering:
  - (a) null content → Continue
  - (b) empty string content → Continue
  - (c) whitespace-only content → Continue
  - (d) content shorter than `minResponseLength` → Continue
  - (e) content at exactly `minResponseLength` boundary → Complete
  - (f) substantive content, iterations well below budget → Complete
  - (g) substantive content, `currentIteration >= maxIterations * escalationRatio` → Escalate with reason
  - (h) escalation ratio boundary (just below threshold → Complete, at threshold → Escalate)
  - (i) custom configuration overrides defaults correctly
  - (j) Continue decision carries a non-null continuation message; Escalate carries a non-null reason

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `RuleBasedCompletionJudge` exists, implements `ICompletionJudge`, and produces all three `CompletionDecision` outcomes
- [x] Empty/blank/trivial responses → Continue; near-budget → Escalate; otherwise → Complete
- [x] All thresholds are configurable with sensible defaults; zero-config instance available via static factory
- [x] Unit tests cover every rule, every decision outcome, boundary conditions, and custom configuration
- [x] **无静默跳过**: every rule branch produces an explicit `CompletionDecision` subtype (Continue/Complete/Escalate); no null return, no empty body, no silent fall-through (Rule #24)
- [x] **端到端验证** N/A: unit-test phase — end-to-end verification is in Phase 2
- [x] **接线验证** N/A: no inter-component wiring in this phase
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes (existing + new tests)
- [x] No owner-doc update required for Phase 1 (design doc §5.3 Phase 2 strategy already specifies "轻量 Judge"; implementation details belong in source code per guide rule #14)
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 2 - End-to-End Integration Through ReAct Loop

Status: completed
Targets: integration test `io.nop.ai.agent.completion.TestRuleBasedJudgeInReActLoop`

- Item Types: `Proof`

- [x] Create `TestRuleBasedJudgeInReActLoop` (integration test) wiring `RuleBasedCompletionJudge` via `ReActAgentExecutor.Builder.completionJudge()`:
  - Verify empty-response Continue scenario: LLM returns empty no-tools response on first call, substantive response on second call → Judge says Continue first → continuation injected → LLM called again → Judge says Complete → loop exits with status `completed` (chat service called twice)
  - Verify near-budget Escalate scenario: set `maxIterations` low and drive `currentIteration` near the escalation threshold, LLM returns substantive no-tools response → Judge says Escalate → loop exits with status `escalated`, reason recorded in `lastError` + metadata, `EXECUTION_COMPLETED` event NOT published (plan 159 post-loop exclusion)
  - Verify normal completion: LLM returns substantive no-tools response, iterations well below budget → Judge says Complete → loop exits with status `completed`
  - Verify dead-loop protection interaction: if LLM keeps returning empty responses, the rule-based Judge's repeated Continue decisions are bounded by `DEFAULT_MAX_COMPLETION_CONTINUES = 3` (plan 159) — engine force-exits after 3 consecutive Continues with status `completed`
- [x] Confirm backward compatibility: existing `TestCompletionGateInReActLoop` tests pass unchanged (NoOp default is untouched)

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] Integration test wires `RuleBasedCompletionJudge` via `Builder.completionJudge()` and exercises Continue → re-enter-reasoning → Complete end-to-end
- [x] Integration test exercises Escalate → status `escalated` + reason in `lastError`/metadata + no `EXECUTION_COMPLETED` event
- [x] Integration test exercises normal Complete path
- [x] Integration test verifies dead-loop protection bounds rule-based Continue decisions
- [x] **端到端验证**: from `ReActAgentExecutor.execute()` entry point, through LLM response, through `RuleBasedCompletionJudge.decide()`, through decision dispatch, to loop exit — complete path verified (Anti-Hollow Rule #22)
- [x] **接线验证**: integration test confirms `RuleBasedCompletionJudge.decide()` is actually called from within `ReActAgentExecutor.execute()` at the "no tool calls" branch (invocation counter or mock verify) (Rule #23)
- [x] **无静默跳过**: all three decision paths in the ReAct integration perform explicit actions; no silent fall-through (Rule #24)
- [x] Existing `TestCompletionGateInReActLoop` tests pass unchanged (backward compatible)
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] No owner-doc update required (design doc §5.3 already specifies the Completion Gate algorithm; roadmap A2 remains ✅ — this is the Phase-2 functional fill-in, not a new contract)
- [x] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `RuleBasedCompletionJudge` implements `ICompletionJudge` and produces all three decision outcomes based on configurable rules
- [x] All existing tests pass unchanged (backward compatible — NoOp remains the Builder default)
- [x] New unit tests cover every rule, outcome, boundary, and configuration
- [x] New integration test verifies the functional Judge end-to-end through a real ReAct loop
- [x] **Anti-Hollow Check**: closure audit verifies (a) `RuleBasedCompletionJudge.decide()` is called from `ReActAgentExecutor.execute()` at runtime, (b) the Continue → re-enter → Complete path runs end-to-end, (c) no empty method bodies or silent no-ops
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] Deferred items (LLM-based Judge, DefaultAgentEngine wiring, IGoalTracker, adaptive tuning, DSL config, semantic assessment) correctly classified as out-of-scope/optimization with explicit non-blocking rationale
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` passes
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] checkstyle / 代码规范检查通过
- [x] No silent no-op or empty method body in new code
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit code 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high` — no new findings in the `completion/` package
- [x] Independent closure audit completed and evidence recorded

## Deferred But Adjudicated

### LLM-Based "Small Model" Judge (Design §5.3 Phase 2 Alternative)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: The rule-based Judge provides deterministic, zero-cost premature-completion detection. The LLM-based Judge needs `IChatService` wiring, prompt engineering, token cost, and potentially `IGoalTracker` (L3-3 ❌). It is a semantically different approach warranting its own plan.
- Successor Required: yes
- Successor Path: future plan for LLM-based Completion Judge

### DefaultAgentEngine-Level Judge Injection

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: The Builder setter (plan 159) is sufficient to wire the functional Judge. Engine-level API injection is only needed when the Judge must be configurable through the engine constructor API rather than the Builder.
- Successor Required: no

### IGoalTracker (L3-3) Integration

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: The rule-based Judge works from message content + context heuristics, not from an explicit goal checklist. IGoalTracker integration is most valuable with an LLM-based Judge that can reason about goal completion.
- Successor Required: no

### Adaptive Threshold Tuning (Design §5.3 Phase 3)

- Classification: `optimization candidate`
- Why Not Blocking Closure: Adaptive tuning depends on a functional Judge existing first and accumulating accuracy history. Strictly future work.
- Successor Required: no

### DSL Judge Configuration

- Classification: `optimization candidate`
- Why Not Blocking Closure: The Judge is runtime-pluggable via the Builder. DSL configuration can be added without changing the interface, consistent with the routing-is-runtime-pluggable decision.
- Successor Required: no

### Semantic Content-Quality Assessment

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Assessing whether the response substantively addresses the task requires an LLM-based Judge. The rule-based Judge catches structural premature-completion patterns (empty/trivial/near-budget) but does not assess semantic quality.
- Successor Required: yes
- Successor Path: LLM-based Judge successor plan

## Non-Blocking Follow-ups

- LLM-based "small model" Judge (design §5.3 Phase 2 alternative)
- `DefaultAgentEngine` constructor-chain extension for Judge injection
- `IGoalTracker` (L3-3) integration with a functional Judge
- Adaptive threshold tuning (design §5.3 Phase 3)
- DSL configuration for Judge selection
- Semantic content-quality assessment (requires LLM-based Judge)

## Closure

Status Note: Plan 162 replaces the inert `NoOpCompletionJudge` pass-through with a functional, deterministic, configurable `RuleBasedCompletionJudge` realizing design §5.3 Phase-2 "轻量 Judge" strategy. All Phase-1 (implementation + unit tests) and Phase-2 (end-to-end integration through `ReActAgentExecutor`) items are landed. Backward compatibility preserved: NoOp remains the Builder default, existing `TestCompletionGateInReActLoop` tests pass unchanged. Independent closure audit (fresh subagent session, read-only) returned PASS on every dimension (A–G).
Completed: 2026-06-14

Closure Audit Evidence:

- Reviewer / Agent: Independent closure auditor subagent (fresh session, first contact with plan + implementation); Opencode task_id `ses_13df049a8ffe7zMas6jAkMqf35`
- Audit Session: opencode `task` tool, subagent_type=`explore`, read-only
- Evidence:
  - Phase-1 Exit Criteria — PASS:
    - `RuleBasedCompletionJudge implements ICompletionJudge` verified at `RuleBasedCompletionJudge.java:26` (`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/completion/`).
    - All three decision outcomes produced: empty/blank → Continue (`L57-59`), trivially-short → Continue (`L61-64`), near-budget → Escalate (`L66-69`), default → Complete (`L71`).
    - Configurable thresholds with sensible defaults: `CompletionRuleConfig` (same package) carries `minResponseLength`(10), `escalationRatio`(0.9), `continuationMessage`, `escalationReasonTemplate`, with input validation (`CompletionRuleConfig.java:27-33`).
    - Zero-config static factories `ruleBased()` / `ruleBased(config)` follow `NoOpCompletionJudge.noOp()` convention.
    - Unit tests `TestRuleBasedCompletionJudge` cover every rule, every outcome, every boundary, and custom configuration (29 test methods; null/empty/whitespace/trivial/at-boundary/below-budget/near-budget/custom-config/rule-ordering/null-ctx-guard/config-validation).
    - 无静默跳过: every rule branch returns an explicit `CompletionDecision` subtype; no null return, no empty body, no silent fall-through (Rule #24).
    - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS, 525 tests, 0 failures, 0 errors.
  - Phase-2 Exit Criteria — PASS:
    - `TestRuleBasedJudgeInReActLoop` (6 integration tests) wires `RuleBasedCompletionJudge` via `Builder.completionJudge()`.
    - Continue → re-enter → Complete path verified end-to-end: LLM called twice, first decision Continue, second Complete, continuation message injected before re-entry (`emptyResponseThenSubstantiveResponseCompletesLoop`).
    - Escalate → status `escalated` + reason in `lastError` + `completion.escalateReason` metadata + NO `EXECUTION_COMPLETED` event (`nearBudgetSubstantiveResponseEscalatesWithReasonAndNoCompletionEvent`).
    - Normal Complete path verified (`substantiveResponseBelowBudgetCompletesNormally`); `EXECUTION_COMPLETED` event published.
    - Dead-loop protection bounded by `DEFAULT_MAX_COMPLETION_CONTINUES = 3`: 4 LLM calls then force-exit with status `completed` (`repeatedEmptyResponsesAreBoundedByDeadLoopProtection`).
    - 端到端验证 (Rule #22): full path `ReActAgentExecutor.execute()` → LLM response → `RuleBasedCompletionJudge.decide()` at `ReActAgentExecutor.java:368` → decision dispatch (L370-401) → loop exit, verified by integration tests with explicit `chatCallCount` and `decision` assertions.
    - 接线验证 (Rule #23): `CountingJudge` decorator wraps the `RuleBasedCompletionJudge` and asserts `invocations` counter incremented from inside `execute()`; signature-message test (`ruleBasedJudgeSpecificallyIsInvokedFromExecutor`) further verifies that `RuleBasedCompletionJudge` specifically (not NoOp) produced the injected continuation message.
    - 无静默跳过: all three decision branches in `ReActAgentExecutor.java` (L370-401) perform real actions — Continue injects `ChatUserMessage` + increments counter/iteration, Escalate sets status/metadata + break, Complete sets status + break.
    - Existing `TestCompletionGateInReActLoop` passes unchanged (backward compatible — NoOp default untouched at `ReActAgentExecutor.java:125,246`).
  - Closure Gates — PASS:
    - Anti-Hollow Check PASS: (a) `RuleBasedCompletionJudge.decide()` called at `ReActAgentExecutor.java:368` at runtime (verified by `CountingJudge.invocations` assertions); (b) Continue → re-enter → Complete path runs end-to-end (verified by `emptyResponseThenSubstantiveResponseCompletesLoop`); (c) no empty bodies / no-ops / silent skips in new code.
    - No in-scope live defect or contract drift silently deferred to follow-up — verified by audit Section E.
    - All six deferred items (`LLM-based Judge`, `DefaultAgentEngine` wiring, `IGoalTracker`, adaptive tuning, DSL config, semantic assessment) correctly classified as `out-of-scope improvement` or `optimization candidate` with explicit non-blocking rationale.
    - `./mvnw compile -pl nop-ai/nop-ai-agent -am` → BUILD SUCCESS.
    - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS (525 tests).
    - checkstyle / 代码规范检查通过: build SUCCESS with default Maven checkstyle plugin (no warnings).
    - No silent no-op or empty method body in new code.
    - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/162-nop-ai-agent-functional-completion-judge.md --strict` → exit code 0 (1 plan passed).
    - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high` → 14 pre-existing findings in OTHER files (DefaultAgentEngine, IAgentEngine, NoOpHookRegistry, IAiMemoryStore, ISessionStore); 0 new findings in `completion/` package.
    - Independent closure audit completed and evidence recorded in this section.
  - Deferred classification check: All six deferred items inspected; none are in-scope live defects or contract drift smuggled out. Each has explicit `Why Not Blocking Closure` rationale tied to this plan's stated Goals.
  - Doc-sync: No owner-doc update required (design §5.3 Phase-2 strategy "轻量 Judge：用规则或小模型" already specifies the contract; implementation details belong in source code per guide rule #14). Daily log `ai-dev/logs/2026/06-14.md` updated.

Follow-up:

- LLM-based "small model" Judge (design §5.3 Phase 2 alternative — successor plan required)
- Semantic content-quality assessment (requires LLM-based Judge — successor plan required)
- `DefaultAgentEngine` constructor-chain extension for Judge injection (non-blocking)
- `IGoalTracker` (L3-3 ❌) integration with a functional Judge (non-blocking)
- Adaptive threshold tuning (design §5.3 Phase 3 — non-blocking)
- DSL configuration for Judge selection (non-blocking)

## Follow-up handled by 165-nop-ai-agent-llm-completion-judge.md

Plan 165 picks up the LLM-Based "Small Model" Judge — the successor work item from this plan's `Deferred But Adjudicated` §"LLM-Based 'Small Model' Judge (Design §5.3 Phase 2 Alternative)" (Successor Required: yes) and `Follow-up` §"LLM-based 'small model' Judge". It implements `LlmCompletionJudge` as a standalone semantic completion verifier that calls `IChatService` to assess whether the assistant's response substantively addresses the task goal — the "小模型" half of design §5.3 Phase 2 "用规则或小模型" (this plan delivered "规则", plan 165 delivers "小模型"). The `Semantic Content-Quality Assessment` deferred item (also Successor Required: yes) is partially addressed by plan 165 as a natural consequence of semantic completion verification, though deeper quality assessment remains a further follow-up.
