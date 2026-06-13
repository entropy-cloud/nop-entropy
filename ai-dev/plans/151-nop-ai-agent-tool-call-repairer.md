> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L2-1

# 151 IToolCallRepairer Interface + NoOpToolCallRepairer Pass-Through

## Purpose

Introduce `IToolCallRepairer` as the first Layer 2 pass-through extension point. This interface sits between "extract tool calls from LLM response" and "check tool access/permissions" in the ReAct loop, allowing future implementations to fix malformed tool calls (invalid JSON arguments, missing parameters, wrong types) before execution. The initial `NoOpToolCallRepairer` returns the tool call unchanged, keeping current behavior identical.

## Current Baseline

- `ReActAgentExecutor` (at `ReActAgentExecutor.java:231`) iterates `assistantMsg.getToolCalls()` and processes each call directly through access check → permission check → path check → execute — no repair step exists
- All Layer 1 interfaces are implemented with pass-through defaults; Builder pattern established in plan 146
- `ReActAgentExecutor.Builder` already supports 8 fields (chatService, toolManager, eventPublisher, permissionProvider, toolAccessChecker, pathAccessChecker, auditLogger, hookRegistry)
- Glossary defines `IToolCallRepairer` as "Layer 2 | 工具调用修复链"
- Roadmap L2-2 (ChainRepairer 4-stage) depends on this item

## Goals

- Define `IToolCallRepairer` interface with a single `repair` method
- Implement `NoOpToolCallRepairer` pass-through (returns input unchanged)
- Integrate `IToolCallRepairer` into `ReActAgentExecutor` Builder and ReAct loop
- Add unit tests for the interface, the pass-through, and the integration

## Non-Goals

- Implementing actual repair logic (that is L2-2: ChainRepairer)
- Changing any existing behavior in the ReAct loop
- Modifying `agent.xdef` schema

## Scope

### In Scope

- `IToolCallRepairer` interface definition
- `NoOpToolCallRepairer` implementation
- `ReActAgentExecutor` integration (Builder field + loop insertion point)
- Unit tests

### Out Of Scope

- ChainRepairer (L2-2)
- LLM-based repair strategies
- Repair logging/auditing (handled by existing `IAuditLogger`)

## Execution Plan

### Phase 1 - Interface and Pass-Through Implementation

Status: completed
Targets: `nop-ai-agent/src/main/java/io/nop/ai/agent/repair/`

- Item Types: `Proof`

- [x] Create package `io.nop.ai.agent.repair`
- [x] Create `IToolCallRepairer` interface with method: `ChatToolCall repair(ChatToolCall toolCall, AgentExecutionContext ctx)`
- [x] Create `NoOpToolCallRepairer` (singleton `INSTANCE` pattern, same as `NoOpHookRegistry`) that returns the input `ChatToolCall` unchanged

Exit Criteria:

- [x] `IToolCallRepairer.java` exists in `io.nop.ai.agent.repair` package with the `repair` method
- [x] `NoOpToolCallRepairer.java` exists and implements `IToolCallRepairer`, returning input unchanged
- [x] `NoOpToolCallRepairer` uses singleton `INSTANCE` pattern consistent with `NoOpHookRegistry`
- [x] No owner-doc update required (glossary already defines the interface)

### Phase 2 - ReActAgentExecutor Integration

Status: completed
Targets: `ReActAgentExecutor.java`, `ReActAgentExecutor.Builder`

- Item Types: `Proof`

- [x] Add `IToolCallRepairer` field to `ReActAgentExecutor`
- [x] Add `repairer(IToolCallRepairer)` method to `Builder`; default to `NoOpToolCallRepairer.INSTANCE` in `build()`
- [x] Insert repair step inside the tool-call for-loop (line ~231): right after `chatToolCall` is obtained and before the `TOOL_CALL_STARTED` event (line 234), apply `repairer.repair(chatToolCall, ctx)` to each call; the repaired result replaces `chatToolCall` for all downstream access/permission/path checks
- [x] The repair result replaces the original `ChatToolCall` for all downstream processing

Exit Criteria:

- [x] `ReActAgentExecutor.Builder` has `repairer()` setter; `build()` defaults to `NoOpToolCallRepairer.INSTANCE`
- [x] ReAct loop applies repairer to each tool call before access check (code-observable: repair call appears before `toolAccessChecker.checkAccess()` in the iteration)
- [x] No owner-doc update required (roadmap already tracks this as L2-1)

### Phase 3 - Tests

Status: completed
Targets: `nop-ai-agent/src/test/java/io/nop/ai/agent/repair/`

- Item Types: `Proof`

- [x] `TestNoOpToolCallRepairer`: verify pass-through returns input unchanged, verify singleton identity
- [x] `TestToolCallRepairerInReActLoop`: integration test verifying that a custom `IToolCallRepairer` is called during ReAct loop execution, and that the repaired call is used for downstream processing (not the original)
- [x] Existing tests continue to pass (NoOp default preserves behavior)

Exit Criteria:

- [x] `TestNoOpToolCallRepairer` passes: input == output for various ChatToolCall inputs
- [x] `TestToolCallRepairerInReActLoop` passes: custom repairer transforms calls, transformed calls are processed (verified via mock toolManager receiving repaired arguments)
- [x] `./mvnw test -pl nop-ai-agent -am -T 1C` passes
- [x] No owner-doc update required (test-only phase)

## Closure Gates

- [x] `IToolCallRepairer` interface and `NoOpToolCallRepairer` exist in `io.nop.ai.agent.repair` package
- [x] `ReActAgentExecutor.Builder` supports `repairer()` with `NoOpToolCallRepairer` default
- [x] ReAct loop applies repairer before access/permission checks
- [x] New tests cover: pass-through behavior, integration with ReAct loop
- [x] All existing tests pass unchanged
- [x] `./mvnw compile -pl nop-ai-agent -am -T 1C` passes
- [x] `./mvnw test -pl nop-ai-agent -am -T 1C` passes
- [x] Anti-Hollow Check: repairer is called in the ReAct loop execution path (verified by integration test), not just a field assignment
- [x] No silent no-op: `NoOpToolCallRepairer` returns input directly (legitimate pass-through, not a placeholder)

## Deferred But Adjudicated

(none)

## Non-Blocking Follow-ups

- L2-2: ChainRepairer (4-stage repair pipeline) — depends on this plan's interface
- Repair-specific audit events (e.g., `TOOL_CALL_REPAIRED` event type)

## Closure

Status Note: All 3 phases completed. IToolCallRepairer interface and NoOpToolCallRepairer pass-through created in io.nop.ai.agent.repair package. ReActAgentExecutor Builder extended with toolCallRepairer field (defaults to NoOpToolCallRepairer.INSTANCE). Repair step inserted in ReAct loop before TOOL_CALL_STARTED event. All 270 tests pass (including 3 new integration tests and 4 new unit tests).

Closure Audit Evidence:

- Reviewer / Agent: executing agent (self-audit for implementation; independent audit recommended per plan guide)
- Evidence:
  - Phase 1 Exit Criteria: PASS — `IToolCallRepairer.java` and `NoOpToolCallRepairer.java` exist in `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/repair/`
  - Phase 2 Exit Criteria: PASS — `ReActAgentExecutor.Builder.toolCallRepairer()` setter added; `build()` defaults to `NoOpToolCallRepairer.INSTANCE`; repair call at line 233 before `TOOL_CALL_STARTED` event at line 235
  - Phase 3 Exit Criteria: PASS — `TestNoOpToolCallRepairer` (4 tests) and `TestToolCallRepairerInReActLoop` (3 tests) all pass
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` — BUILD SUCCESS, 270 tests, 0 failures
  - Anti-Hollow Check: PASS — `TestToolCallRepairerInReActLoop.testRepairerIsCalledDuringReActLoop` verifies repairer is invoked during ReAct loop; `testRepairedCallIsUsedForDownstreamProcessing` verifies repaired arguments reach toolManager
  - No silent no-op: PASS — `NoOpToolCallRepairer.repair()` returns input directly (legitimate pass-through for a NoOp, not a placeholder)

Follow-up:

- L2-2 ChainRepairer is the natural successor
