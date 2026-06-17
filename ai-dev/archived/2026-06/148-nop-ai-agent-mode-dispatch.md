# 148 Agent Mode Dispatch: DefaultAgentEngine dispatches IAgentExecutor by agent.xdef mode

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L1-19
> **Last Reviewed**: 2026-06-13
> **Source**: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 1, `nop-ai-agent-dsl.md` §3.3

## Purpose

Make `DefaultAgentEngine` read the `mode` attribute from `AgentModel` and dispatch to the appropriate `IAgentExecutor` implementation, instead of hardcoding `ReActAgentExecutor`. Implement `SingleTurnExecutor` for the `single-turn` mode. Leave `plan` mode as explicit `UnsupportedOperationException`.

## Current Baseline

- `agent.xdef` (`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent.xdef`) already defines `mode="enum:react,plan,single-turn|react"` — the schema side is done
- `_AgentModel.getMode()` is generated (`_AgentModel.java:260`), returns `String` (default `"react"`)
- `DefaultAgentEngine.doExecute()` (line 130) hardcodes `ReActAgentExecutor.builder()...build()` regardless of mode — uses 6 instance fields (`chatService`, `toolManager`, `eventPublisher`, `permissionProvider`, `toolAccessChecker`, `pathAccessChecker`)
- `IAgentExecutor` interface exists with single method `CompletionStage<AgentExecutionResult> execute(AgentExecutionContext ctx)`
- `AgentExecutionResult.fromContext(ctx)` reads `ctx.getStatus()`, `ctx.getTokensUsed()`, `ctx.getCurrentIteration()` — executor must set these before returning
- `ReActAgentExecutor` sets `ctx.setStatus(running/completed/failed)`, increments `ctx.setTokensUsed()`, publishes events (`EXECUTION_STARTED`/`EXECUTION_COMPLETED`/`EXECUTION_FAILED`)
- No `SingleTurnExecutor` exists; no `PlanAgentExecutor` exists
- No test `.agent.xml` files with `mode` attribute other than default (`react`)
- Design doc `nop-ai-agent-dsl.md` §3.3 specifies the three-way dispatch mapping
- Roadmap L1-19 status is ❌

## Goals

- `DefaultAgentEngine` selects `IAgentExecutor` based on `AgentModel.getMode()`
- `react` mode → `ReActAgentExecutor` (existing, no behavior change)
- `single-turn` mode → new `SingleTurnExecutor` (single LLM call, no tool loop, no iteration)
- `plan` mode → `UnsupportedOperationException("Plan execution mode is not yet implemented")`
- Unknown mode → fail fast with `NopAiAgentException`
- All modes tested

## Non-Goals

- Full `PlanAgentExecutor` implementation (Phase 2+)
- `single-turn` mode with tool calls (that would be `react` with `maxIterations=1`)
- Changes to `agent.xdef` schema (already has `mode`)

## Scope

### In Scope

- Mode dispatch logic in `DefaultAgentEngine`
- `SingleTurnExecutor` implementation
- Unit tests for dispatch and `SingleTurnExecutor`
- Roadmap L1-19 status update

### Out Of Scope

- `PlanAgentExecutor` implementation
- Changes to `ReActAgentExecutor`
- `AgentSession` field additions (L1-20)

## Execution Plan

### Phase 1 - Mode Dispatch in DefaultAgentEngine

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java`

- Item Types: `Fix`, `Proof`

- [x] Extract executor selection from `doExecute()` into a package-private method `resolveExecutor(AgentModel model)` that returns `IAgentExecutor` — accesses engine fields via `this.*`
- [x] `resolveExecutor` reads `model.getMode()`:
  - `"react"` or `null`/empty → `ReActAgentExecutor.builder()...build()` (existing builder logic using `this.chatService`, `this.toolManager`, etc.)
  - `"single-turn"` → new `SingleTurnExecutor(this.chatService, this.eventPublisher)` 
  - `"plan"` → throw `UnsupportedOperationException("Plan execution mode is not yet implemented: mode=plan")`
  - any other value → throw `NopAiAgentException("Unknown agent execution mode: " + mode)`
- [x] Update `doExecute()` to call `resolveExecutor(agentModel)` instead of hardcoded `ReActAgentExecutor`

Exit Criteria:

- [x] `DefaultAgentEngine.doExecute()` no longer references `ReActAgentExecutor` directly — it delegates to `resolveExecutor()`
- [x] `resolveExecutor()` (package-private) returns `ReActAgentExecutor` for `mode="react"` and `mode=null` (backward compatible)
- [x] `resolveExecutor()` returns `SingleTurnExecutor` for `mode="single-turn"`
- [x] `resolveExecutor()` throws `UnsupportedOperationException` for `mode="plan"`
- [x] `resolveExecutor()` throws `NopAiAgentException` for unknown mode values
- [x] Existing tests still pass (no behavior change for `react` mode)
- [x] No owner-doc update required (design doc `nop-ai-agent-dsl.md` §3.3 already specifies this dispatch)
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 2 - SingleTurnExecutor Implementation

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/SingleTurnExecutor.java`

- Item Types: `Proof`

- [x] Create `SingleTurnExecutor implements IAgentExecutor` in `io.nop.ai.agent.engine`
- [x] Constructor takes `IChatService` and `IAgentEventPublisher`
- [x] `execute()` flow (mirroring `ReActAgentExecutor` pattern for ctx lifecycle):
   1. Set `ctx.setStatus(AgentExecStatus.running)` 
   2. Publish `EXECUTION_STARTED` event
   3. Build `ChatRequest` from `ctx.getMessages()` + `ctx.getChatOptionsModel()`
   4. Call `chatService.call(request, null)` — single LLM call, no tool loop
   5. On success: append assistant message to ctx, set `ctx.setTokensUsed(response.getUsage())`, set `ctx.setStatus(AgentExecStatus.completed)`, publish `EXECUTION_COMPLETED`, return `AgentExecutionResult.fromContext(ctx)`
   6. On failure (non-success response or exception): set `ctx.setStatus(AgentExecStatus.failed)`, set `ctx.setLastError(...)`, publish `EXECUTION_FAILED`, return `AgentExecutionResult.fromContext(ctx)`

Exit Criteria:

- [x] `SingleTurnExecutor.java` exists in `io.nop.ai.agent.engine` package
- [x] `execute()` makes exactly one LLM call via `chatService` and returns result
- [x] `ctx.setStatus()` is set to `running` → `completed`/`failed` (not left as `pending`)
- [x] `ctx.setTokensUsed()` is updated from response usage data
- [x] Events published: `EXECUTION_STARTED` → `EXECUTION_COMPLETED`/`EXECUTION_FAILED` (not `TOOL_CALL_COMPLETED`)
- [x] Error path: non-success `ChatResponse` sets `ctx.setStatus(failed)` and publishes `EXECUTION_FAILED` (no NPE)
- [x] No tool loop, no iteration counter increment
- [x] No empty method body or silent no-op — every method has real logic or throws `UnsupportedOperationException`
- [x] No owner-doc update required
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 3 - Tests

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/`

- Item Types: `Proof`

- [x] Create test `.agent.xml` files in test resources: `test-single-turn-agent.agent.xml` with `mode="single-turn"`, `test-plan-agent.agent.xml` with `mode="plan"`, `test-unknown-mode-agent.agent.xml` with `mode="unknown"`
- [x] `TestModeDispatch.java`: test `resolveExecutor()` directly (package-private access) — verify correct executor type for each mode, UOE for `plan`, `NopAiAgentException` for unknown
- [x] `TestSingleTurnExecutor.java`: verify single LLM call behavior, ctx status lifecycle (`pending`→`running`→`completed`), token tracking, no tool invocation, correct event publication, error handling on failed response
- [x] Update `TestDefaultAgentEngine.java` if needed to cover mode-aware execution via `execute()` with test XML files

Exit Criteria:

- [x] Test `.agent.xml` files exist with `mode="single-turn"`, `mode="plan"`, `mode="unknown"` attributes
- [x] `TestModeDispatch` covers: `react`, `null`/empty (default), `single-turn`, `plan` (UOE), unknown (exception)
- [x] `TestSingleTurnExecutor` covers: single call, ctx status lifecycle, token tracking, event publication, error response handling
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes with all new and existing tests
- [x] No owner-doc update required
- [x] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

- [x] `DefaultAgentEngine` dispatches by `mode` attribute — `react`, `single-turn`, `plan` (UOE), unknown (exception)
- [x] `SingleTurnExecutor` performs single LLM call without tool loop
- [x] All new tests pass; existing tests unchanged in behavior
- [x] Roadmap L1-19 updated from ❌ to ✅
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] No silent no-op or empty method body in new code
- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-dsl.md` already describes this — no doc update needed
- [x] Independent closure audit completed and evidence recorded

## Deferred But Adjudicated

(none)

## Non-Blocking Follow-ups

- `PlanAgentExecutor` implementation (Layer 2+, blocked on plan DSL runtime)
- `single-turn` mode with streaming response support

## Closure

Status Note: All three phases completed. `DefaultAgentEngine.resolveExecutor()` dispatches by `mode` attribute to `ReActAgentExecutor` (react/null/empty), `SingleTurnExecutor` (single-turn), `UnsupportedOperationException` (plan), or `NopAiAgentException` (unknown). 209 tests pass including new TestModeDispatch (6 tests) and TestSingleTurnExecutor (8 tests).

Closure Audit Evidence:

- Reviewer / Agent: GLM-5.1 (independent closure audit session)
- Evidence:
  - Phase 1 Exit Criteria: PASS — `DefaultAgentEngine.java:130` calls `resolveExecutor(agentModel)`, no direct `ReActAgentExecutor` reference in `doExecute()`. `resolveExecutor()` at line 150 returns correct executor types for all modes.
  - Phase 2 Exit Criteria: PASS — `SingleTurnExecutor.java` in `io.nop.ai.agent.engine`, single `chatService.call()`, proper ctx lifecycle (`running`→`completed`/`failed`), token tracking from `response.getUsage()`, events `EXECUTION_STARTED`→`EXECUTION_COMPLETED`/`EXECUTION_FAILED`, no tool loop, no empty methods.
  - Phase 3 Exit Criteria: PASS — 3 test XML files created, TestModeDispatch covers all 5 modes (react/null/empty, single-turn, plan UOE, unknown exception), TestSingleTurnExecutor covers 8 scenarios (single call, lifecycle, tokens, events, error response, exception, no-tool, no-iteration).
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` — BUILD SUCCESS, 209 tests pass.
  - Roadmap L1-19 updated from ❌ to ✅.
  - Anti-Hollow: `resolveExecutor()` is called from `doExecute()` at runtime; `SingleTurnExecutor.execute()` contains real LLM call logic, no stubs or no-ops.

Follow-up:

- `PlanAgentExecutor` when plan DSL runtime is ready
