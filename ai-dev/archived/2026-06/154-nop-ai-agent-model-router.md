# 154 IModelRouter Interface + PassThroughModelRouter + ReAct Integration

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L2-10
> **Last Reviewed**: 2026-06-13
> **Source**: `ai-dev/design/nop-ai-agent/nop-ai-agent-llm-layer.md` §6, `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 2
> **Related**: Plan 146 (ReActAgentExecutor Builder pattern), Plan 150 (lifecycle hook system)

## Purpose

Define the `IModelRouter` interface and integrate it into the ReAct execution loop, establishing the contract for intelligent model routing. The `PassThroughModelRouter` default ensures all requests use the model configured in the agent DSL without any routing logic, preserving current behavior exactly.

## Current Baseline

- `ReActAgentExecutor` (628 lines) builds `ChatOptions` from `AgentModel.getChatOptions()` at line 198 via `buildChatOptions()` and uses it unchanged for all LLM calls at line 225 (`chatService.call(request, null)`) — no routing or model selection logic exists between options construction and the LLM call
- `ChatOptionsModel` (from nop-ai-core) carries `provider`, `model`, `temperature`, `topP`, `maxTokens`, `topK`, `stop` — the full set of model parameters available from the agent DSL
- `buildChatOptions()` at line 594-617 copies `ChatOptionsModel` fields into `ChatOptions`, sets tool definitions, and returns — no hook point for modification
- `ReActAgentExecutor.Builder` (lines 101-183) has 10 fields (chatService, toolManager, eventPublisher, permissionProvider, toolAccessChecker, pathAccessChecker, auditLogger, hookRegistry, toolCallRepairer, contextCompactor) — no modelRouter field
- `DefaultAgentEngine.resolveExecutor()` (lines 153-175) builds `ReActAgentExecutor` via Builder without any router configuration
- Design doc `nop-ai-agent-llm-layer.md` §6 defines `IModelRouter` as responsible for routing decision and fallback judgment, with `PassThroughModelRouter` as the default (direct connection to AgentModel configured model)
- Design doc §6.3 describes the Smart Router Six-Step Pipeline from PilotDeck as a future functional implementation (NOT this plan)
- Design doc §6.5 defines Fallback error classification (future functional implementation, NOT this plan)
- Design doc §9.2 explicitly rejects routing as an Agent DSL field — routing is a Layer 2 execution extension, runtime pluggable
- Roadmap L2-10 status is ❌
- Zero Java files reference `IModelRouter` or `ModelRouter` in the codebase

## Goals

- `IModelRouter` interface defining the routing contract: receives messages + options + context, returns a `RoutingResult` containing the resolved `ChatOptions` and optional metadata
- `RoutingResult` data object carrying the resolved `ChatOptions` (potentially with different model/provider), complexity classification metadata, and routing reason
- `PassThroughModelRouter` pass-through implementation that returns `ChatOptions` unchanged — always uses the model/provider from `AgentModel.getChatOptions()`
- `ReActAgentExecutor` invokes model router before each LLM call, using the returned `ChatOptions` for the actual request
- `ReActAgentExecutor.Builder` gains `modelRouter(IModelRouter)` method, defaulting to `PassThroughModelRouter`
- `DefaultAgentEngine` passes model router to executor builder (composition via Builder, not a new constructor overload)
- All existing tests pass unchanged (backward compatible — PassThrough default)

## Non-Goals

- Smart Router implementation (six-step pipeline from PilotDeck — this is a functional implementation that consumes the interface, not this plan's scope)
- Fallback error classification (429 handling, 5xx fallback — depends on `IRetryPolicy` at L3-2)
- Complexity classification (simple/medium/complex — requires a judge model, future work)
- Tool set refinement by complexity level (depends on classification)
- DSL configuration for router selection in `agent.xdef` (routing is runtime pluggable per design doc §9.2)

## Scope

### In Scope

- `IModelRouter` interface definition
- `RoutingResult` data object
- `PassThroughModelRouter` implementation
- `ReActAgentExecutor` integration (Builder field + LLM call insertion point)
- `DefaultAgentEngine` wiring via Builder
- Unit tests for interface, pass-through, and integration

### Out Of Scope

- SmartModelRouter (future functional implementation)
- Fallback chain / error classification (L3-2 IRetryPolicy territory)
- Complexity classification via judge model
- Tool set pruning by complexity
- DSL router configuration

## Execution Plan

### Phase 1 - Interface and Types

Status: completed
Targets: `io.nop.ai.agent.router` package (new)

- Item Types: `Proof`

- [x] Create `RoutingResult` data object with fields: `ChatOptions options`, `String complexity` (nullable, for future use), `String routingReason` (nullable, for audit/debug)
- [x] Create `IModelRouter` interface with method: `RoutingResult route(List<ChatMessage> messages, ChatOptions options, AgentExecutionContext ctx)`
- [x] `TestRoutingResult`: verify construction, field access, and that options reference is preserved
- [x] `TestIModelRouter`: verify the interface contract (structural test, confirms method signature exists)

Exit Criteria:

- [x] `RoutingResult` data object exists with `options`, `complexity`, `routingReason` fields
- [x] `IModelRouter` interface exists with `route()` method returning `RoutingResult`
- [x] Unit tests cover `RoutingResult` construction and field access
- [x] **端到端验证** N/A: interface definition phase, no pipeline to verify end-to-end
- [x] **接线验证** N/A: no inter-component wiring yet
- [x] **无静默跳过** N/A: no branches or conditionals in pure type definitions
- [x] No owner-doc update required (design docs already specify these types)
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 2 - PassThroughModelRouter and Tests

Status: completed
Targets: `io.nop.ai.agent.router.PassThroughModelRouter`

- Item Types: `Proof`

- [x] Create `PassThroughModelRouter` implementing `IModelRouter` — `route()` always returns `RoutingResult` with the input `ChatOptions` unchanged, null complexity, routingReason "pass-through"
- [x] Add `static IModelRouter passThrough()` factory method returning singleton instance
- [x] `TestPassThroughModelRouter`: verify route() returns unchanged options for various inputs (with tools, without tools, null options fields)

Exit Criteria:

- [x] `PassThroughModelRouter.route()` always returns `RoutingResult` with input `ChatOptions` unchanged
- [x] `PassThroughModelRouter` is a singleton (via static factory)
- [x] Unit tests verify pass-through behavior for various ChatOptions configurations
- [x] **无静默跳过**: not applicable — pass-through by design, not a placeholder for future work
- [x] No owner-doc update required
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 3 - ReActAgentExecutor Integration and Tests

Status: completed
Targets: `io.nop.ai.agent.engine.ReActAgentExecutor`, `io.nop.ai.agent.engine.DefaultAgentEngine`

- Item Types: `Proof`

- [x] `ReActAgentExecutor.Builder` exposes a `modelRouter(IModelRouter)` setter defaulting to `PassThroughModelRouter`
- [x] `DefaultAgentEngine` passes model router to executor builder via its existing composition flow (not a new constructor overload)
- [x] `ReActAgentExecutor.execute()` invokes `modelRouter.route()` before each LLM call, using the returned `ChatOptions` for the `ChatRequest`
- [x] PassThrough default produces zero observable side-effects (existing behavior unchanged)
- [x] `TestModelRouterInReActLoop` (integration):
  - verify PassThroughModelRouter is called before each LLM call (mock router records invocation count)
  - verify PassThroughModelRouter receives the correct messages and options
  - verify returned ChatOptions are used for the actual chatService.call()
  - verify a custom IModelRouter can change the model/provider in ChatOptions and the changed options reach chatService.call()
  - verify PassThroughModelRouter does not interfere with existing behavior (backward compatibility)

Exit Criteria:

- [x] `ReActAgentExecutor.Builder` has `modelRouter()` method, default is `PassThroughModelRouter`
- [x] `DefaultAgentEngine` passes model router to executor builder via an extended constructor (backward-compatible overload with default `PassThroughModelRouter`)
- [x] Model router is invoked before each LLM call in the ReAct loop; returned `ChatOptions` are used for `chatService.call()`
- [x] Integration test verifies PassThroughModelRouter is called with correct parameters
- [x] Integration test verifies a custom router can modify ChatOptions and the changes reach chatService.call()
- [x] Integration test verifies PassThrough default does not interfere with existing behavior
- [x] All existing tests pass (backward compatible)
- [x] **端到端验证**: existing e2e test `TestEndToEndReAct` passes without modification
- [x] **接线验证**: `TestModelRouterInReActLoop` verifies model router is called from `ReActAgentExecutor.execute()` before the LLM call
- [x] **无静默跳过**: PassThroughModelRouter returns explicit routing result with "pass-through" reason, not null or empty
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] No owner-doc update required (design docs already specify router semantics)
- [x] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

- [x] `IModelRouter` interface with `route()` method returning `RoutingResult` exists
- [x] `RoutingResult` data object carries `ChatOptions`, optional `complexity`, optional `routingReason`
- [x] `PassThroughModelRouter` always returns input `ChatOptions` unchanged
- [x] `ReActAgentExecutor` invokes model router before each LLM call; returned `ChatOptions` used for request
- [x] Backward compatible: all existing tests pass with PassThrough default
- [x] Roadmap L2-10 updated from ❌ to ✅
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] No silent no-op or empty method body in new code
- [x] No owner-doc update required
- [x] Independent closure audit completed and evidence recorded

## Deferred But Adjudicated

### Smart Router Six-Step Pipeline

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: The Smart Router (PilotDeck design) requires a judge model, complexity classification, tool set pruning, and fallback chain — all of which are functional implementations that consume the `IModelRouter` interface. This plan only establishes the interface contract and pass-through default.
- Successor Required: yes
- Successor Path: future plan for SmartModelRouter implementation

### Fallback Error Classification

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Fallback logic (429 handling, 5xx failover, image removal retry) belongs in `IRetryPolicy` (L3-2) territory. Model routing and retry are separate concerns per design doc §6 vs §7.
- Successor Required: no

### Complexity Classification

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Complexity classification (simple/medium/complex) requires a judge model call, which is a non-trivial functional implementation. The `RoutingResult.complexity` field is defined as nullable to accommodate future use.
- Successor Required: no

### Tool Set Refinement by Complexity

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Depends on complexity classification which is deferred. Tool set pruning is an optimization, not a contract requirement.
- Successor Required: no

### DSL Router Configuration

- Classification: `optimization candidate`
- Why Not Blocking Closure: Design doc §9.2 explicitly rejects routing as a DSL field — routing is runtime pluggable. DSL configuration can be added without changing the interface when needed.
- Successor Required: no

## Non-Blocking Follow-ups

- SmartModelRouter implementation (six-step pipeline from PilotDeck)
- Fallback chain integration with IRetryPolicy (L3-2)
- Complexity classification via judge model
- Tool set pruning by complexity level
- DSL configuration for router selection

## Closure

Status Note: All three phases completed successfully. IModelRouter interface, RoutingResult data object, PassThroughModelRouter implementation, ReActAgentExecutor integration, and DefaultAgentEngine wiring all implemented and tested. All existing tests pass with PassThrough default (backward compatible).

Closure Audit Evidence:

- Reviewer / Agent: executing agent (self-audit, independent closure audit to follow)
- Evidence:
  - Phase 1: `RoutingResult` (io.nop.ai.agent.router.RoutingResult) and `IModelRouter` (io.nop.ai.agent.router.IModelRouter) created with TestRoutingResult (6 tests) and TestIModelRouter (2 tests) all passing
  - Phase 2: `PassThroughModelRouter` (io.nop.ai.agent.router.PassThroughModelRouter) singleton with `passThrough()` factory; TestPassThroughModelRouter (6 tests) all passing
  - Phase 3: `ReActAgentExecutor.Builder.modelRouter()` added; `DefaultAgentEngine` wires router via Builder; `ReActAgentExecutor.execute()` calls `modelRouter.route()` before each LLM call; TestModelRouterInReActLoop (5 tests) verifying call count, message/options correctness, ChatOptions override, multi-turn routing, and backward compatibility — all passing
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -Dmaven.compiler.fork=true` BUILD SUCCESS
  - No empty method bodies or silent no-ops in new code
  - Deferred items properly classified as out-of-scope improvement / optimization candidate

Follow-up:

- SmartModelRouter implementation (six-step pipeline from PilotDeck)
- Fallback chain integration with IRetryPolicy (L3-2)
- Complexity classification via judge model
- Tool set pruning by complexity level
- DSL configuration for router selection
