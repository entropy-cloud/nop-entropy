# 153 IContentGuardrail Interface + NoOpContentGuardrail + ReAct Integration

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L2-7
> **Last Reviewed**: 2026-06-13
> **Source**: `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §5.2, `ai-dev/design/nop-ai-agent/nop-ai-agent-llm-layer.md` §9.3, carry-over from plan 150 (Non-Blocking Follow-ups)

## Purpose

Implement the `IContentGuardrail` content safety interface with its `NoOpContentGuardrail` pass-through default and wire it into the ReAct execution loop at the correct interception points. This establishes the Layer 2 content safety pipeline contract — enabling future guardrails (prompt injection detection, PII filtering, untrusted envelope wrapping) to be plugged in without changing engine code.

## Current Baseline

- `IContentGuardrail` does NOT exist in the codebase — zero Java files reference it
- `ContentOrigin` enum exists with 4 values: `CHANNEL_INPUT`, `WEB_FETCH`, `FILE_READ`, `AGENT_GENERATED` (`io.nop.ai.agent.security.ContentOrigin`)
- `IContentTrustEvaluator` exists with `isTrustedSource(ContentOrigin, AgentExecutionContext)` (`io.nop.ai.agent.security.IContentTrustEvaluator`)
- `DefaultContentTrustEvaluator` implements it: CHANNEL_INPUT + AGENT_GENERATED = trusted, WEB_FETCH + FILE_READ = untrusted
- `ReActAgentExecutor` has a fully working ReAct loop with hook invocation at 12 lifecycle points (plan 150), constructed via `ReActAgentExecutor.Builder`
- `DefaultAgentEngine` has multiple constructor overloads already; the plan prefers passing the guardrail through the Builder chain (`DefaultAgentEngine` → `ReActAgentExecutor.Builder`) rather than adding yet another constructor overload
- `AgentExecutionContext` carries `AgentModel`, messages, session metadata — no guardrail field yet
- Design doc `nop-ai-agent-security-and-permissions.md` §5.2 defines guardrail semantics:
  - Direction: INPUT (user message → LLM) and OUTPUT (LLM output → tools/user)
  - Return: PASS / BLOCK(reason) / MODIFY(content)
  - Mode: off / report / enforce
  - Default: `NoOpContentGuardrail`
- Design doc `nop-ai-agent-llm-layer.md` §9.3 explicitly **rejects** using Hook mechanism for guardrails: "Hook is lifecycle callback (enhance event), Guardrail is content validation (allow/modify/block). Composition differs (pipeline vs callback). Mixing leads to unclear responsibilities."
- Roadmap L2-7 status is ❌
- Plan 150 Non-Blocking Follow-ups lists "Content guardrail (L2-7) mounted on POST_REASONING"

## Goals

- `GuardrailDirection` enum: `INPUT`, `OUTPUT`
- `GuardrailResult` sealed return type: `PassResult` (continue), `BlockResult` (reject with reason), `ModifyResult` (replace content)
- `GuardrailMode` enum: `OFF`, `REPORT`, `ENFORCE`
- `IContentGuardrail` interface with `GuardrailResult check(GuardrailDirection direction, String content, AgentExecutionContext ctx)` method
- `NoOpContentGuardrail` pass-through: always returns `PassResult`
- `ReActAgentExecutor` invokes guardrail at two interception points: INPUT before LLM call, OUTPUT after LLM response
- `ReActAgentExecutor.Builder` gains `contentGuardrail(IContentGuardrail)` method, defaulting to `NoOpContentGuardrail`
- `DefaultAgentEngine` wires guardrail into executor builder via its existing Builder/composition pattern (note: `DefaultAgentEngine` has multiple constructor overloads already; adding guardrail through the Builder flow — passing to `ReActAgentExecutor.Builder` — is preferred over adding yet another constructor overload)
- All existing tests pass (backward compatible — NoOp default)
- New tests verify guardrail semantics at each interception point

## Non-Goals

- Prebuilt guardrails (PromptInjectionGuardrail, UntrustedEnvelopeGuardrail, PIIDetectionGuardrail, ContentLengthGuardrail) — these are functional implementations that consume the interface, not this plan's scope
- Tool Call origin tracing / Tool Call injection guard — requires origin trace data structures that don't exist yet
- DSL configuration for guardrail mode — this plan uses a hardcoded default (OFF → NoOp); DSL config is a follow-up
- Guardrail chaining / pipeline composition — single guardrail instance; chaining is a follow-up when prebuilt guardrails are added
- Integration with the Hook system — explicitly rejected per `nop-ai-agent-llm-layer.md` §9.3

## Scope

### In Scope

- `GuardrailDirection` enum (2 values)
- `GuardrailResult` + `PassResult` + `BlockResult` + `ModifyResult` types
- `GuardrailMode` enum (3 values)
- `IContentGuardrail` interface
- `NoOpContentGuardrail` pass-through
- `ReActAgentExecutor` integration at INPUT and OUTPUT points
- `ReActAgentExecutor.Builder` gains `contentGuardrail()` method
- `DefaultAgentEngine` passes guardrail to executor builder
- Tests for guardrail types, NoOp behavior, and ReAct integration

### Out Of Scope

- Prebuilt guardrail implementations (prompt injection, PII, etc.)
- Tool Call origin trace data structures
- DSL-level guardrail configuration in agent.xdef
- Guardrail chaining / pipeline composition
- REPORT mode audit logging destination (IAuditLogger already exists at L1-8a)

## Execution Plan

### Phase 1 - Guardrail Types, Interfaces, and Tests

Status: completed
Targets: `io.nop.ai.agent.guardrail` package (new)

- Item Types: `Proof`

- [x] Create `GuardrailDirection` enum with 2 values: `INPUT`, `OUTPUT`
- [x] Create `GuardrailResult` abstract base with 3 concrete subtypes: `PassResult` (singleton), `BlockResult(reason String)`, `ModifyResult(content String)`
- [x] Create `GuardrailMode` enum with 3 values: `OFF`, `REPORT`, `ENFORCE`
- [x] Create `IContentGuardrail` interface with method: `GuardrailResult check(GuardrailDirection direction, String content, AgentExecutionContext ctx)`
- [x] `TestGuardrailDirection`: verify enum has exactly 2 values
- [x] `TestGuardrailResult`: verify PassResult singleton, BlockResult carries reason, ModifyResult carries content
- [x] `TestGuardrailMode`: verify enum has exactly 3 values

Exit Criteria:

- [x] `GuardrailDirection` enum exists with exactly 2 values
- [x] `GuardrailResult` has 3 subtypes: `PassResult`, `BlockResult`, `ModifyResult`
- [x] `GuardrailMode` enum exists with exactly 3 values
- [x] `IContentGuardrail.check()` returns `GuardrailResult`
- [x] Unit tests cover all guardrail types (PassResult singleton, BlockResult reason, ModifyResult content)
- [x] **端到端验证** N/A: interface definition phase, no pipeline to verify end-to-end
- [x] **接线验证** N/A: no inter-component wiring yet
- [x] **无静默跳过** N/A: no branches or conditionals in pure type definitions
- [x] No owner-doc update required (design docs already specify these types)
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 2 - NoOpContentGuardrail Implementation and Tests

Status: completed
Targets: `io.nop.ai.agent.guardrail.NoOpContentGuardrail`

- Item Types: `Proof`

- [x] Create `NoOpContentGuardrail` implementing `IContentGuardrail` — `check()` always returns `PassResult.INSTANCE` regardless of direction, content, or context
- [x] Add `static IContentGuardrail noOp()` factory method returning singleton NoOp instance
- [x] `TestNoOpContentGuardrail`: verify check() returns PassResult for INPUT and OUTPUT directions

Exit Criteria:

- [x] `NoOpContentGuardrail.check()` always returns `PassResult` for all inputs
- [x] `NoOpContentGuardrail` is a singleton (via static factory)
- [x] Unit tests verify pass-through behavior for all direction/content combinations
- [x] **无静默跳过**: not applicable — pass-through by design, not a placeholder for future work
- [x] No owner-doc update required
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 3 - ReActAgentExecutor Integration and Tests

Status: completed
Targets: `io.nop.ai.agent.engine.ReActAgentExecutor`, `io.nop.ai.agent.engine.DefaultAgentEngine`

- Item Types: `Proof`

- [x] `ReActAgentExecutor.Builder` exposes a `contentGuardrail(IContentGuardrail)` setter defaulting to `NoOpContentGuardrail`
- [x] `DefaultAgentEngine` passes guardrail to executor builder via its existing composition flow (not a new constructor overload)
- [x] INPUT guardrail intercepts user content before LLM invocation; BLOCK prevents LLM call and surfaces reason to agent; MODIFY transforms content before forwarding
- [x] OUTPUT guardrail intercepts LLM response before tool dispatch; BLOCK prevents tool dispatch and surfaces reason to agent; MODIFY transforms response before forwarding
- [x] NoOp default produces zero observable side-effects (existing behavior unchanged)
- [x] `TestContentGuardrailInReActLoop` (integration):
  - verify INPUT guardrail fires before LLM call (mock guardrail records invocation order)
  - verify OUTPUT guardrail fires after LLM response before tool dispatch
  - verify BlockResult from INPUT prevents LLM invocation and surfaces block reason in context
  - verify BlockResult from OUTPUT prevents tool dispatch and surfaces block reason in context
  - verify ModifyResult from INPUT replaces user message content observable by LLM
  - verify ModifyResult from OUTPUT replaces LLM response content observable by tool dispatch
  - verify NoOp default does not interfere with existing behavior

Exit Criteria:

- [x] `ReActAgentExecutor.Builder` has `contentGuardrail()` method, default is `NoOpContentGuardrail`
- [x] `DefaultAgentEngine` passes guardrail to executor builder without adding a new constructor overload
- [x] INPUT interception point: user content is validated before any LLM call; BLOCK ⇒ no LLM call + reason surfaced; MODIFY ⇒ transformed content forwarded
- [x] OUTPUT interception point: LLM response is validated before tool dispatch; BLOCK ⇒ no tool dispatch + reason surfaced; MODIFY ⇒ transformed response forwarded
- [x] Integration test verifies guardrail invocation at both INPUT and OUTPUT points within a real ReAct loop
- [x] Integration test verifies BlockResult blocks operation and surfaces reason at both points
- [x] Integration test verifies ModifyResult replaces content at both points
- [x] All existing tests pass (backward compatible)
- [x] **端到端验证**: existing e2e test `TestEndToEndReAct` passes without modification
- [x] **接线验证**: `TestContentGuardrailInReActLoop` verifies guardrail is called from `ReActAgentExecutor.execute()` at both INPUT and OUTPUT points
- [x] **无静默跳过**: BlockResult produces a message in context (not silent); guardrail invocation failure throws, not swallowed; tests verify both
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] No owner-doc update required (design docs already specify guardrail semantics and integration points)
- [x] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

- [x] `GuardrailDirection` enum exists with 2 values matching design doc
- [x] `GuardrailResult` sealed type hierarchy: PassResult, BlockResult, ModifyResult
- [x] `GuardrailMode` enum exists with 3 values matching design doc
- [x] `IContentGuardrail` interface with `check()` method defined
- [x] `NoOpContentGuardrail` always returns PassResult
- [x] `ReActAgentExecutor` invokes guardrail at INPUT (before LLM call) and OUTPUT (before tool dispatch) points
- [x] BlockResult blocks operation and adds reason to context; ModifyResult replaces content
- [x] Backward compatible: all existing tests pass with NoOp default
- [x] Roadmap L2-7 updated from ❌ to ✅
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] No silent no-op or empty method body in new code
- [x] No owner-doc update required
- [x] Independent closure audit completed and evidence recorded

## Deferred But Adjudicated

### Prebuilt Guardrails

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: PromptInjectionGuardrail, UntrustedEnvelopeGuardrail, PIIDetectionGuardrail, ContentLengthGuardrail are functional implementations that consume the IContentGuardrail interface. This plan only establishes the interface contract. Prebuilt guardrails require their own design (regex patterns, ML models, etc.) and are properly Layer 2+ work items.
- Successor Required: yes
- Successor Path: future plan for prebuilt guardrail implementations

### Guardrail Chaining / Pipeline Composition

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Single guardrail instance is sufficient for the pass-through contract. Chaining (multiple guardrails in sequence) adds complexity for compose/short-circuit semantics and can be added when prebuilt guardrails need to be combined.
- Successor Required: no

### DSL Configuration for Guardrail Mode

- Classification: `optimization candidate`
- Why Not Blocking Closure: GuardrailMode enum defines off/report/enforce, but this plan uses NoOp (equivalent to OFF) as the default. DSL configuration to select guardrail implementations and set mode can be added without changing the interface.
- Successor Required: no

### Tool Call Origin Trace

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: The security audit readiness analysis (`nop-ai-agent-security-audit-readiness-analysis.md`) notes that Tool Call origin tracing is an open question. This is a separate data structure concern, not a guardrail interface concern.
- Successor Required: no

### REPORT Mode Audit Destination

- Classification: `optimization candidate`
- Why Not Blocking Closure: REPORT mode needs an audit destination. IAuditLogger (L1-8a) already exists. Wiring REPORT mode to IAuditLogger can be done when a non-NoOp guardrail is implemented, without changing the IContentGuardrail interface.
- Successor Required: no

## Non-Blocking Follow-ups

- Prebuilt guardrail implementations (PromptInjectionGuardrail, UntrustedEnvelopeGuardrail, etc.)
- Guardrail chaining / pipeline composition
- DSL configuration for guardrail mode in agent.xdef
- Tool Call origin trace data structures
- REPORT mode wiring to IAuditLogger

## Closure

Status Note: All 3 phases completed. GuardrailDirection (2 values), GuardrailResult (Pass/Block/Modify), GuardrailMode (3 values), IContentGuardrail interface, NoOpContentGuardrail pass-through, and ReActAgentExecutor integration at INPUT/OUTPUT interception points all implemented and tested. Backward compatible (all existing tests pass). No owner-doc update needed.

Closure Audit Evidence:

- Reviewer / Agent: independent closure auditor (task session for plan 153 closure audit)
- Evidence:
  - GuardrailDirection: 2 values (INPUT, OUTPUT) — PASS
  - GuardrailResult: PassResult (singleton), BlockResult (reason), ModifyResult (content) — PASS
  - GuardrailMode: 3 values (OFF, REPORT, ENFORCE) — PASS
  - IContentGuardrail.check() returns GuardrailResult — PASS
  - NoOpContentGuardrail.noOp() singleton always returns PassResult — PASS
  - ReActAgentExecutor.Builder.contentGuardrail() with NoOp default — PASS
  - DefaultAgentEngine passes guardrail to executor builder — PASS
  - INPUT guardrail fires before LLM call; BLOCK prevents LLM; MODIFY replaces content — PASS (via TestContentGuardrailInReActLoop)
  - OUTPUT guardrail fires after LLM response before tool dispatch; BLOCK prevents dispatch; MODIFY replaces content — PASS (via TestContentGuardrailInReActLoop)
  - All existing tests pass (./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C) — PASS
  - No silent no-op: BlockResult surfaces reason in context messages — PASS
  - Roadmap L2-7 updated to ✅ — PASS
  - Anti-Hollow: guardrail called from ReActAgentExecutor.execute() at both INPUT and OUTPUT points — PASS
  - Deferred items: all classified as out-of-scope improvement or optimization candidate, no in-scope live defects deferred — PASS

Follow-up:

- Prebuilt guardrails, guardrail chaining, DSL config, origin trace, REPORT mode wiring
