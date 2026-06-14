# 159 Completion Gate: ICompletionJudge Interface + NoOp Default + ReAct Integration

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: A2
> **Last Reviewed**: 2026-06-13
> **Source**: `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md` §5.3, `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 1 (A2)
> **Related**: Plan 146 (ReActAgentExecutor Builder pattern), Plan 150 (lifecycle hook system), Plan 154 (IModelRouter — comparable interface + pass-through + ReAct integration pattern)

## Purpose

Establish the Completion Gate contract and integrate it into the ReAct execution loop, so that a "no tool calls" LLM response no longer unconditionally terminates the agent. The `NoOpCompletionJudge` default always decides "complete", preserving the current behavior exactly until a functional Judge is supplied.

## Current Baseline

- `ReActAgentExecutor.execute()` terminates the loop immediately when the LLM returns no tool calls — `ReActAgentExecutor.java:338-341` (`if (!assistantMsg.hasToolCalls()) { ctx.setStatus(completed); break; }`). There is no verification that the task goal was actually met. This is the false/premature-completion problem the Completion Gate addresses (design doc `nop-ai-agent-react-engine.md` §5.3).
- The ReAct loop structure, Builder pattern (13 existing Builder fields), and `DefaultAgentEngine.resolveExecutor()` wiring (lines 296-312) are all stable and established by plans 146/150/154/156.
- `AgentExecStatus` enum (6 values: `pending`, `running`, `completed`, `failed`, `cancelled`, `forced_stopped`) is a hand-written file (`model/AgentExecStatus.java`, not generated). It has no value for "needs human intervention". Design doc §5.3 specifies an "escalate" outcome ("标记为需人工介入"). **Decision (this plan)**: add `escalated` as a 7th value — see Phase 1 Decision item for rationale.
- The post-loop status logic at `ReActAgentExecutor.java:496-509` has two branches: (1) if status is still `running`, override to `completed` (line 496-498); (2) if status is not `cancelled` and not `forced_stopped`, fire POST_CALL hooks + publish `EXECUTION_COMPLETED` event (line 500-509). The new `escalated` status must be excluded from branch (2) so escalation does not publish a spurious "completed" event — see Phase 3.
- The `HookResult` sealed-class pattern (`PassResult` / `VetoResult` / `ReenterResult`) in `io.nop.ai.agent.hook` is the established convention for multi-valued decision results in this module.
- `DEFAULT_MAX_REENTRIES = 3` constant in `ReActAgentExecutor` (line 71) is the precedent for dead-loop protection counters; design doc §5.3 mandates "Judge 连续裁决 N 次（默认 3 次）'continue' → 强制跳出".
- `DefaultAgentEngine.resolveExecutor()` does not wire `auditLogger` or `toolCallRepairer` — they rely on `ReActAgentExecutor.Builder` defaults. This plan follows the same precedent: the judge relies on the Builder default (`NoOpCompletionJudge`), and `DefaultAgentEngine` is not extended.
- Grep confirms zero code referencing `CompletionJudge`, `CompletionGate`, `CompletionDecision`, or `completionJudge` anywhere in `nop-ai-agent/src`.
- Roadmap A2 status is ❌. Its sole dependency L1-5 (ReActExecutor) is ✅, so A2 is unblocked.

## Goals

- `ICompletionJudge` interface defining the Judge contract: receives the final assistant message + execution context, returns a `CompletionDecision`
- `CompletionDecision` result type with three outcomes per design doc §5.3: `Continue` (carries a continuation message to inject as a user message), `Complete` (task is done — exit loop), `Escalate` (needs human intervention — exit loop)
- `NoOpCompletionJudge` pass-through default that always decides `Complete`, preserving current loop-termination behavior exactly (design Phase 1 strategy: "无 Judge，跳过，保持当前行为")
- `ReActAgentExecutor` invokes the Judge at the "no tool calls" branch and acts on each decision outcome
- Dead-loop protection: after `DEFAULT_MAX_COMPLETION_CONTINUES` (3) consecutive `Continue` decisions, the engine force-exits the loop (design §5.3 key constraint)
- `ReActAgentExecutor.Builder` gains `completionJudge()` setter, defaulting to `NoOpCompletionJudge`
- All existing tests pass unchanged (backward compatible — NoOp default)

## Non-Goals

- Lightweight/functional Judge implementation (rules-based or small-model completion verification — design Phase 2 strategy). This plan only establishes the interface contract and pass-through default, matching how plans 151/152/153/154 handled their respective extension points.
- Adaptive Judge (accuracy-threshold tuning — design Phase 3 strategy).
- Goal tracking integration (`IGoalTracker` at L3-3) — a functional Judge may consume a goal tracker, but that is future work.
- `DefaultAgentEngine`-level wiring for a custom judge. The Builder defaults to `NoOpCompletionJudge` (same as `auditLogger` / `toolCallRepairer`). Engine-level injection of a custom judge is deferred to the functional-Judge successor plan.
- DSL configuration for Judge selection in `agent.xdef` — the Judge is a runtime-pluggable execution extension, not a DSL field (consistent with the routing-is-runtime-pluggable decision in `nop-ai-agent-llm-layer.md` §9.2, applied analogously).

## Scope

### In Scope

- `ICompletionJudge` interface definition
- `CompletionDecision` result type (three outcomes)
- `NoOpCompletionJudge` pass-through implementation
- `AgentExecStatus` gains `escalated` value (one-line addition to a hand-written enum)
- `ReActAgentExecutor` integration: Builder field, loop modification at "no tool calls" branch, dead-loop counter, post-loop status-logic fix
- Unit tests for interface, decision type, pass-through, and integration

### Out Of Scope

- RuleBasedCompletionJudge or LLM-based Judge functional implementation
- `DefaultAgentEngine` constructor-chain extension for judge injection
- IGoalTracker integration
- Adaptive threshold tuning
- DSL judge configuration
- Dedicated escalation event type (escalation is observable via `escalated` status + reason in `lastError`; a dedicated event can be added with the functional Judge)

## Execution Plan

### Phase 1 - Interface, Decision Type, and Escalate-Status Decision

Status: completed
Targets: new package `io.nop.ai.agent.completion` (`ICompletionJudge`, `CompletionDecision`), `io.nop.ai.agent.model.AgentExecStatus`

- Item Types: `Decision | Proof`

- [x] **Decision (escalate terminal state)**: Add `escalated` as a 7th value to `AgentExecStatus`. Rationale: the design doc §5.3 "escalate" outcome ("标记为需人工介入") is semantically distinct from both normal completion (`completed`) and tool/LLM errors (`failed`). Reusing `failed` would make escalation indistinguishable from tool-error failure by status alone, violating the "distinguishable" requirement. `AgentExecStatus` is hand-written (not generated), so adding one value is safe and low-risk. The new `escalated` status must be excluded from the post-loop `EXECUTION_COMPLETED` event block (see Phase 3).
- [x] Add `escalated` value to `AgentExecStatus` enum (after `forced_stopped`)
- [x] Create `CompletionDecision` result type with three outcomes matching design doc §5.3:
  - `Continue` — carries a continuation message string (injected into the conversation as a user message to prompt the LLM to keep working)
  - `Complete` — signals task completion, engine exits the loop
  - `Escalate` — signals human intervention needed, carries a reason string, engine exits the loop
  Follow the established sealed/abstract-class pattern from `HookResult` (`PassResult` / `VetoResult` / `ReenterResult` — package-private constructor, static final subclasses, convenience `is*()` methods)
- [x] Create `ICompletionJudge` interface with a single decision method that receives the assistant message and `AgentExecutionContext`, returning a `CompletionDecision`
- [x] `TestCompletionDecision`: verify construction and field access for all three outcome subtypes (Continue carries message, Escalate carries reason, Complete is terminal)
- [x] `TestICompletionJudge`: structural test confirming the interface contract exists

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `AgentExecStatus` has 7 values including `escalated`
- [x] `CompletionDecision` type exists with three distinguishable outcomes; each outcome carries the fields described in design §5.3
- [x] `ICompletionJudge` interface exists with a decision method returning `CompletionDecision`
- [x] Unit tests cover `CompletionDecision` construction and field access for all three outcomes
- [x] **无静默跳过**: N/A — pure type definitions, no branches or conditionals
- [x] **端到端验证** N/A: interface definition phase, no pipeline to verify end-to-end
- [x] **接线验证** N/A: no inter-component wiring yet
- [x] No owner-doc update required (design doc `nop-ai-agent-react-engine.md` §5.3 already specifies the decision outcomes; roadmap M6 row says "4 values" but that is stale — live code already has 6, this adds a 7th)
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 2 - NoOpCompletionJudge and Tests

Status: completed
Targets: `io.nop.ai.agent.completion.NoOpCompletionJudge`

- Item Types: `Proof`

- [x] Create `NoOpCompletionJudge` implementing `ICompletionJudge` — always returns `Complete`, matching design Phase 1 strategy ("无 Judge，跳过，保持当前行为")
- [x] Add a static factory method returning a singleton instance (following the `PassThroughModelRouter.passThrough()` / `NoOpContentGuardrail.noOp()` convention)
- [x] `TestNoOpCompletionJudge`: verify `decide()` always returns `Complete` regardless of input (empty message, non-empty message, various context states)

Exit Criteria:

- [x] `NoOpCompletionJudge.decide()` always returns the `Complete` outcome
- [x] `NoOpCompletionJudge` is a singleton (via static factory)
- [x] Unit tests verify the no-op behavior for varied inputs
- [x] **无静默跳过**: pass-through by design — returns an explicit `Complete` decision, not null or empty; this is the intended Phase-1 default per design §5.3, not a placeholder hidden as completed work
- [x] No owner-doc update required (design doc already specifies the Phase-1 no-op strategy)
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 3 - ReActAgentExecutor Integration and Tests

Status: completed
Targets: `io.nop.ai.agent.engine.ReActAgentExecutor`

- Item Types: `Proof`

- [x] Add `DEFAULT_MAX_COMPLETION_CONTINUES = 3` constant to `ReActAgentExecutor` (alongside existing `DEFAULT_MAX_REENTRIES = 3`)
- [x] Add `completionJudge` field to `ReActAgentExecutor` (constructor parameter + `Builder.completionJudge()` setter). The Builder's `build()` method defaults to `NoOpCompletionJudge` when null/unset (following the established null-coalescing pattern, e.g. `hookRegistry != null ? hookRegistry : NoOpHookRegistry.INSTANCE`). No `DefaultAgentEngine` change needed — it relies on the Builder default.
- [x] Add a local `int consecutiveContinues = 0` counter in `execute()` (alongside the existing `reentryCounters` map at line 241)
- [x] Modify the "no tool calls" branch at `ReActAgentExecutor.java:338-341` to invoke the Judge instead of unconditionally breaking:
  - Invoke `completionJudge.decide(assistantMsg, ctx)`
  - `Complete` → set status to `completed`, `break` (preserves current behavior exactly when NoOp default is used)
  - `Continue`:
    - If `consecutiveContinues >= DEFAULT_MAX_COMPLETION_CONTINUES` → log warning, set status to `completed`, `break` (dead-loop protection — see below)
    - Otherwise → inject the continuation message as a `ChatUserMessage` into the conversation (the design §5.3 example "你还没有完成任务 X，请继续" reads as a user-role message), increment `consecutiveContinues`, increment `currentIteration`, and `continue` the loop. **Trap note**: `currentIteration` is normally incremented at line 493 (after tool execution); the `continue` statement skips that line, so the manual increment here is mandatory for the `maxIterations` safety net to function on continuation paths.
  - `Escalate` → set status to `escalated`, record the reason in `ctx.setLastError(reason)` and `ctx.getMetadata().put("completion.escalateReason", reason)`, reset `consecutiveContinues = 0`, `break`
- [x] **Counter reset on tool-call iterations**: reset `consecutiveContinues = 0` whenever the loop processes an iteration where the LLM returned tool calls (i.e., before or at the tool-execution branch). This ensures two `Continue` decisions separated by tool-call iterations are NOT counted as consecutive. (The Judge is only invoked on no-tool-call iterations, so the reset must happen on the tool-call path, not just "on non-Continue decisions".)
- [x] **Post-loop status-logic fix** at `ReActAgentExecutor.java:500`: add `escalated` to the exclusion condition so escalation does NOT publish `EXECUTION_COMPLETED` or fire POST_CALL hooks. The condition `status != cancelled && status != forced_stopped` becomes `status != cancelled && status != forced_stopped && status != escalated`. (Dead-loop force-exit uses `completed`, which does fire `EXECUTION_COMPLETED` — this is acceptable: the loop ended normally from the engine's perspective, and the warning log makes it observable.)
- [x] `TestCompletionGateInReActLoop` (integration test):
  - Verify `NoOpCompletionJudge` default is invoked at the "no tool calls" branch and the loop terminates as before (backward compatibility)
  - Verify a custom `ICompletionJudge` returning `Continue` injects the continuation message as a `ChatUserMessage` and the loop re-enters reasoning (LLM is called again)
  - Verify a custom Judge that always returns `Continue` triggers dead-loop force-exit after 3 continuation injections (the 4th Continue decision hits the `>= 3` guard) — the agent terminates with status `completed` and a warning is logged, instead of looping forever
  - Verify the consecutive-continue counter resets when tool calls are present between `Continue` decisions: a sequence of (no-tools → Continue [counter=1] → tools-executed [reset to 0] → no-tools → Continue [counter=1] → no-tools → Continue [counter=2]) does NOT trigger force-exit, because without the reset the counter would have reached 3 and force-exited prematurely
  - Verify a custom Judge returning `Escalate` exits the loop with status `escalated`, reason recorded in `lastError` + metadata, and `EXECUTION_COMPLETED` event is NOT published (escalation is excluded from the post-loop completion block)
  - Verify `Complete` from a custom Judge sets status to `completed` and exits

Exit Criteria:

- [x] `ReActAgentExecutor.Builder` has `completionJudge()` method; default is `NoOpCompletionJudge` (no `DefaultAgentEngine` change)
- [x] The "no tool calls" branch invokes the Judge and acts on all three decision outcomes
- [x] `Continue` injects a `ChatUserMessage` continuation message, increments `currentIteration`, and re-enters the reasoning loop
- [x] Dead-loop protection force-exits after 3 consecutive `Continue` decisions (status `completed`, warning logged)
- [x] The consecutive-continue counter resets on tool-call iterations (not just non-Continue decisions)
- [x] `Escalate` sets status to `escalated`, records reason in `lastError` + metadata, and does NOT publish `EXECUTION_COMPLETED`
- [x] Post-loop status logic excludes `escalated` from the `EXECUTION_COMPLETED` / POST_CALL block
- [x] `NoOpCompletionJudge` default produces zero observable side-effects vs. current behavior (all existing tests pass)
- [x] Integration test verifies Judge invocation, continuation, dead-loop protection, counter reset on tool-call iterations, escalate + no-completion-event, and backward compatibility
- [x] **端到端验证**: existing e2e test `TestEndToEndReAct` passes without modification (NoOp default is transparent); the `Continue` path is exercised end-to-end in `TestCompletionGateInReActLoop` (LLM no-tools → Judge says continue → continuation injected → LLM called again → Judge says complete → loop exits)
- [x] **接线验证**: `TestCompletionGateInReActLoop` verifies the Judge is called from within `ReActAgentExecutor.execute()` at the "no tool calls" branch (mock judge records invocation)
- [x] **无静默跳过**: every new branch (`Complete` / `Continue` / `Escalate` / dead-loop) performs an explicit action; no empty method bodies or silent `continue` past unhandled logic
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] No owner-doc update required (design doc `nop-ai-agent-react-engine.md` §5.3 already specifies the Completion Gate algorithm and constraints; roadmap A2 will be updated from ❌ to ✅)
- [x] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `ICompletionJudge` interface with decision method returning `CompletionDecision` exists
- [x] `CompletionDecision` carries three outcomes: `Continue` (with message), `Complete`, `Escalate` (with reason)
- [x] `AgentExecStatus` includes `escalated`
- [x] `NoOpCompletionJudge` always decides `Complete` (preserves current behavior)
- [x] `ReActAgentExecutor` invokes the Judge at the "no tool calls" branch; all three outcomes are handled
- [x] Dead-loop protection force-exits after 3 consecutive `Continue` decisions; counter resets on tool-call iterations
- [x] Post-loop status logic excludes `escalated` from `EXECUTION_COMPLETED` / POST_CALL block
- [x] Backward compatible: all existing tests pass with `NoOpCompletionJudge` default
- [x] Roadmap A2 updated from ❌ to ✅
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` passes
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] checkstyle / 代码规范检查通过
- [x] No silent no-op or empty method body in new code
- [x] **Anti-Hollow Check**: closure audit verifies (a) the Judge is called from `ReActAgentExecutor.execute()` at runtime (not just type-existing), (b) the `Continue` → re-enter-reasoning → `Complete` path runs end-to-end, (c) no empty method bodies / silent no-ops
- [x] No owner-doc update required
- [x] Independent closure audit completed and evidence recorded

## Deferred But Adjudicated

### Lightweight / Rule-Based Judge (Design Phase 2 Strategy)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: A functional Judge (rules-based or small-model completion verification) is a non-trivial consumer of the `ICompletionJudge` interface. This plan only establishes the contract + pass-through default, matching the pattern used for every other Layer 2 extension point in this module (IToolCallRepairer → plan 151, IContextCompactor → plan 152, IContentGuardrail → plan 153, IModelRouter → plan 154).
- Successor Required: yes
- Successor Path: future plan for a functional CompletionJudge implementation

### DefaultAgentEngine-Level Judge Injection

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: The Builder defaults to `NoOpCompletionJudge`, so the engine works without any `DefaultAgentEngine` change (same as `auditLogger` / `toolCallRepairer`). Engine-level injection of a custom judge is only needed when a functional Judge exists and must be configurable through the engine API.
- Successor Required: yes
- Successor Path: functional-Judge successor plan

### Adaptive Judge (Design Phase 3 Strategy)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Adaptive threshold tuning depends on a functional Judge existing first and accumulating accuracy history. Strictly future work.
- Successor Required: no

### IGoalTracker Integration

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: A functional Judge may consume `IGoalTracker` (L3-3) to check whether all required tasks are complete, but `IGoalTracker` itself does not exist yet. The interface contract established here does not depend on it.
- Successor Required: no

### DSL Judge Configuration

- Classification: `optimization candidate`
- Why Not Blocking Closure: The Judge is a runtime-pluggable execution extension. DSL configuration can be added without changing the interface when needed, consistent with the routing decision in `nop-ai-agent-llm-layer.md` §9.2.
- Successor Required: no

### Dedicated Escalation Event Type

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Escalation is observable via `escalated` status + reason in `lastError`/metadata. A dedicated `AgentEventType` for escalation can be added with the functional Judge when event-driven escalation handling is needed.
- Successor Required: no

## Non-Blocking Follow-ups

- RuleBasedCompletionJudge or LLM-based Judge functional implementation (design Phase 2)
- `DefaultAgentEngine` constructor-chain extension for custom judge injection
- Dedicated escalation event type
- Adaptive threshold tuning (design Phase 3)
- IGoalTracker (L3-3) integration with a functional Judge
- DSL configuration for Judge selection

## Closure

Status Note: The Completion Gate contract (`ICompletionJudge` + `CompletionDecision`) is established, the `NoOpCompletionJudge` pass-through default preserves the current ReAct loop-termination behavior exactly, and the Judge is wired into `ReActAgentExecutor.execute()` at the "no tool calls" branch. All three decision outcomes (Complete/Continue/Escalate) are handled explicitly with dead-loop protection (3 consecutive Continue force-exit) and counter reset on tool-call iterations. The `escalated` status is excluded from the post-loop `EXECUTION_COMPLETED` block. All 461 module tests pass (455 existing unchanged + 6 new integration tests). This matches the pattern used for every other Layer 2 extension point in this module (interface + pass-through default + ReAct integration), so a functional Judge is correctly deferred to a successor plan.

Completed: 2026-06-13

Closure Audit Evidence:

- Reviewer / Agent: independent explore subagent (task ses_13e8b63d6ffeGOuWxwRNm63ARD)
- Audit Session: fresh closure-audit session (not the implementation session)
- Evidence:
  - Exit Criterion "ICompletionJudge interface": PASS — `completion/ICompletionJudge.java` has `decide(ChatAssistantMessage, AgentExecutionContext)` returning `CompletionDecision`.
  - Exit Criterion "CompletionDecision three outcomes": PASS — `completion/CompletionDecision.java` has `Continue` (message), `Complete` (singleton), `Escalate` (reason) + `is*()` methods, following the HookResult sealed-class pattern.
  - Exit Criterion "AgentExecStatus.escalated": PASS — `model/AgentExecStatus.java` has 7 values, `escalated` after `forced_stopped`.
  - Exit Criterion "NoOpCompletionJudge always Complete": PASS — `completion/NoOpCompletionJudge.java` singleton via `noOp()`, `decide()` returns `Complete.instance()`.
  - Exit Criterion "Judge invoked at no-tool-calls branch": PASS (Anti-Hollow) — `ReActAgentExecutor.java:354-355`: `if (!assistantMsg.hasToolCalls()) { CompletionDecision decision = completionJudge.decide(assistantMsg, ctx); ... }`. The old unconditional `setStatus(completed); break;` is replaced by conditional dispatch.
  - Exit Criterion "Continue injects ChatUserMessage + re-enters": PASS — lines 362-374: injects `new ChatUserMessage(...)`, increments `consecutiveContinues` and `currentIteration`, `continue`. Verified end-to-end by `TestCompletionGateInReActLoop.continueDecisionInjectsUserMessageAndReEntersReasoning` (LLM called twice).
  - Exit Criterion "Dead-loop protection 3 consecutive Continue": PASS — `DEFAULT_MAX_COMPLETION_CONTINUES = 3` (line 75), guard at line 363 (`>=`), warning log + status completed + break. Verified by `deadLoopProtectionForceExitsAfterThreeConsecutiveContinues` (LLM called exactly 4 times).
  - Exit Criterion "Counter reset on tool-call path": PASS — `consecutiveContinues = 0;` at line 391 (after the no-tool-calls block, before tool execution). Verified by `consecutiveContinueCounterResetsOnToolCallIterations` (judge reaches 5 invocations, would force-exit at 4 without reset).
  - Exit Criterion "Escalate sets escalated + no EXECUTION_COMPLETED": PASS — lines 377-384 set `escalated` + lastError + metadata. Post-loop exclusion at lines 550-552 adds `&& status != escalated`. Verified by `escalateExitsWithEscalatedStatusAndNoCompletionEvent` (status escalated, reason in lastError/metadata, no EXECUTION_COMPLETED event).
  - Exit Criterion "Backward compatible": PASS — all 455 pre-existing tests pass unchanged with NoOp default.
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/159-nop-ai-agent-completion-gate.md --strict` exit code 0 (all checklist items checked + Closure Evidence written).
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high`: 14 findings, ALL pre-existing (DefaultAgentEngine, IAgentEngine, NoOpHookRegistry, IAiMemoryStore, ISessionStore) — zero in the new `completion/` package or the modified ReActAgentExecutor lines.
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → 461 tests, 0 failures, BUILD SUCCESS.
  - `./mvnw compile -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS.
  - Deferred items classification: all deferred items (functional Judge, DefaultAgentEngine wiring, IGoalTracker, adaptive tuning, DSL config, escalation event) are correctly classified as `out-of-scope improvement` / `optimization candidate` with explicit `Why Not Blocking Closure` reasons — no in-scope live defect or contract drift hidden in deferred.

Follow-up:

- RuleBasedCompletionJudge or LLM-based Judge functional implementation (design Phase 2) — successor plan required
- `DefaultAgentEngine` constructor-chain extension for custom judge injection
- Dedicated escalation event type
- Adaptive threshold tuning (design Phase 3)
- IGoalTracker (L3-3) integration with a functional Judge
- DSL configuration for Judge selection

## Follow-up handled by 162-nop-ai-agent-functional-completion-judge.md

Plan 162 picks up the functional Completion Judge implementation — the successor work item from this plan's `Deferred But Adjudicated` §"Lightweight / Rule-Based Judge (Design Phase 2 Strategy)" (Successor Required: yes). It implements `RuleBasedCompletionJudge`, a deterministic, configurable rule-based Judge replacing the inert `NoOpCompletionJudge` for callers that want actual completion verification, realizing design doc §5.3 Phase 2 ("轻量 Judge"). The LLM-based "small model" Judge (the alternative Phase-2 approach) remains deferred to a further successor.
