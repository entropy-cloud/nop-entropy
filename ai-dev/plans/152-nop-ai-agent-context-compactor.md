# 152 IContextCompactor Interface + NoOpContextCompactor + ReAct Integration

> **Plan Status**: planned
> **Module**: nop-ai-agent
> **Work Item**: L2-3
> **Last Reviewed**: 2026-06-13
> **Source**: Carry-over from plan 150 (`ai-dev/plans/150-nop-ai-agent-lifecycle-hook.md`), `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §7, `ai-dev/design/nop-ai-agent/02-execution-model.md` §5.1
> **Related**: Plan 150 (lifecycle hook system — established PRE_COMPACT/POST_COMPACT lifecycle points)

## Purpose

Define the `IContextCompactor` interface and integrate it into the ReAct execution loop. This establishes the contract for context compaction that Layer 2 progressive compression (L2-4) and the full 5-layer pipeline (L3-9) will implement. The default `NoOpContextCompactor` ensures backward compatibility.

## Current Baseline

- `ReActAgentExecutor` (570 lines) has a working ReAct loop with 12 lifecycle hook points including `PRE_COMPACT` and `POST_COMPACT` — these points are defined in `AgentLifecyclePoint` enum but never invoked (no compaction trigger logic exists)
- `CompactionResult` class exists at `io.nop.ai.agent.session.CompactionResult` with fields: `sessionId`, `tokensBefore`, `tokensAfter`, `retainedMessageCount`, `snapshotId`
- `CompactConfig` class exists at `io.nop.ai.agent.session.CompactConfig` with fields: `targetTokens`, `strategy`, `preserveSystemMessages`
- `ISessionStore` has a `default CompactResult compact(String sessionId, CompactConfig config)` method that throws UOE
- `AgentSession` has `compactedAt` field and `markCompacted()` method
- `AgentLifecyclePoint.PRE_COMPACT` and `AgentLifecyclePoint.POST_COMPACT` are defined but not invoked by any code
- `DefaultHookRegistry` maps `before_compact`/`after_compact` to these lifecycle points
- Design doc `nop-ai-agent-reliability.md` §7 defines a 5-layer progressive compression pipeline with dual-dimension triggering (token estimate OR message count)
- Design doc `02-execution-model.md` §5.1 Layer 2 defines PRE_COMPACT/POST_COMPACT as extension lifecycle points
- No `IContextCompactor` interface, no `NoOpContextCompactor`, no compaction trigger logic exists
- Roadmap L2-3 status is ❌

## Goals

- `IContextCompactor` interface defining the compaction contract with `compact()` method
- `CompactionContext` data object providing compaction input (messages, config, session)
- `NoOpContextCompactor` pass-through implementation (returns unchanged messages)
- `ReActAgentExecutor` gains compaction trigger logic that checks token/message thresholds before each iteration and invokes compaction when triggered
- `PRE_COMPACT` and `POST_COMPACT` hooks are actually invoked during compaction
- `ReActAgentExecutor.Builder` gains `contextCompactor(IContextCompactor)` method
- All existing tests pass unchanged (backward compatible — default is NoOp)

## Non-Goals

- Progressive compression initial version (L2-4 — this is the successor work item)
- Full 5-layer pipeline (L3-9 — depends on L2-4 and L2-16)
- Token counting via `ILlmDialect.estimateTokens()` (L2-16 — compaction trigger will use simple estimation for now)
- `ICompressionStrategy` pluggable strategies (L3-9 extension point)
- Compression model configuration (`compressionModel` in agent.xdef)
- Checkpoint journal integration (L3-4/A4)
- `ISessionStore.compact()` integration (SessionStore-level compaction is a separate concern)

## Scope

### In Scope

- `IContextCompactor` interface
- `CompactionContext` data object
- `NoOpContextCompactor` pass-through implementation
- Compaction trigger logic in `ReActAgentExecutor` (threshold check before each iteration)
- `PRE_COMPACT` / `POST_COMPACT` hook invocation during compaction
- `ReActAgentExecutor.Builder` extension
- `DefaultAgentEngine` wiring
- Tests for interface, NoOp, trigger logic, and hook invocation

### Out Of Scope

- Actual compression logic (Layer 0 pre-truncation, Layer 1 micro-compression, etc.)
- `ICompressionStrategy` extension point
- Token estimation calibration
- DSL `<compaction>` element parsing
- Checkpoint/snapshot generation during compaction

## Execution Plan

### Phase 1 - Compaction Types and Interface

Status: planned
Targets: `io.nop.ai.agent.compact` package (new)

- Item Types: `Proof`

- [ ] Create `CompactionContext` data class with fields: `messages` (List<ChatMessage> — the messages to potentially compact), `compactConfig` (CompactConfig), `sessionId` (String), `agentName` (String), `executionContext` (AgentExecutionContext — for access to constraints, model, etc.)
- [ ] Create `IContextCompactor` interface with method: `CompactionResult compact(CompactionContext ctx)` — takes current messages and config, returns compaction result. The implementor is responsible for deciding whether compaction is needed and performing it

Exit Criteria:

- [ ] `CompactionContext` exists as an immutable data carrier with messages, compactConfig, sessionId, agentName, executionContext
- [ ] `IContextCompactor` interface defines `compact(CompactionContext)` returning `CompactionResult`
- [ ] **端到端验证** N/A: interface definition phase
- [ ] **接线验证** N/A: no inter-component wiring yet
- [ ] **无静默跳过** N/A: pure type definitions
- [ ] No owner-doc update required (design docs already describe compaction contract)
- [ ] `ai-dev/logs/` corresponding date entry updated

### Phase 2 - NoOpContextCompactor Implementation

Status: planned
Targets: `io.nop.ai.agent.compact.NoOpContextCompactor`

- Item Types: `Proof`

- [ ] Create `NoOpContextCompactor` implementing `IContextCompactor` — `compact()` returns a `CompactionResult` with `tokensBefore == tokensAfter`, `retainedMessageCount == messages.size()`, `snapshotId = null`, and the messages list unchanged. This signals "no compaction occurred" to the executor

Exit Criteria:

- [ ] `NoOpContextCompactor.compact()` returns a result where `tokensBefore == tokensAfter`
- [ ] `NoOpContextCompactor.compact()` returns `retainedMessageCount == input message count`
- [ ] `NoOpContextCompactor.compact()` returns `snapshotId = null`
- [ ] **端到端验证** N/A: pass-through implementation, no pipeline
- [ ] **接线验证** N/A: not yet wired into executor
- [ ] **无静默跳过**: NoOp returns valid CompactionResult with equal before/after counts, not null or empty
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` corresponding date entry updated

### Phase 3 - ReActAgentExecutor Integration

Status: planned
Targets: `io.nop.ai.agent.engine.ReActAgentExecutor`, `io.nop.ai.agent.engine.DefaultAgentEngine`

- Item Types: `Proof`

- [ ] Add `IContextCompactor` field to `ReActAgentExecutor` (final, set via Builder)
- [ ] Add `contextCompactor(IContextCompactor)` method to `ReActAgentExecutor.Builder`, defaulting to `NoOpContextCompactor` if not set
- [ ] Add compaction trigger check in the ReAct loop — before each iteration (after incrementing iteration counter, before PRE_REASONING hook), check if compaction should be triggered. Trigger condition: `ctx.getTokensUsed() > ctx.getMaxTokens() * TRIGGER_TOKEN_PERCENT` (default 0.8) OR `ctx.getMessages().size() > TRIGGER_MAX_MESSAGES` (default 30). Both thresholds are constants in the executor for now (L2-16 will introduce `ILlmDialect.estimateTokens()` for pre-call estimation; currently using `ctx.getTokensUsed()` which accumulates from LLM response usage)
- [ ] When triggered: invoke `PRE_COMPACT` hook → call `contextCompactor.compact()` → invoke `POST_COMPACT` hook → if `CompactionResult.tokensAfter < tokensBefore`, replace messages in context and call `session.markCompacted()` (if session is available via execution context)
- [ ] Wire `IContextCompactor` through `DefaultAgentEngine` — pass `NoOpContextCompactor.INSTANCE` to executor builder (no DSL loading yet; actual compactor selection from agent config is L2-4 scope)
- [ ] Guard: compaction is never triggered during the same iteration that already performed compaction (prevent recursive compaction per design doc §7.6)

Exit Criteria:

- [ ] `ReActAgentExecutor.Builder` has `contextCompactor()` method
- [ ] Default contextCompactor is `NoOpContextCompactor` (backward compatible)
- [ ] Compaction trigger check runs before each ReAct iteration using token and message count thresholds
- [ ] `PRE_COMPACT` hook fires before `contextCompactor.compact()`
- [ ] `POST_COMPACT` hook fires after compaction completes
- [ ] NoOp default means no messages are actually modified
- [ ] Recursive compaction is prevented (flag reset each iteration)
- [ ] Existing tests pass unchanged (NoOp default)
- [ ] **端到端验证**: existing e2e test `TestEndToEndReAct` passes without modification (NoOp default)
- [ ] **接线验证**: new test verifies compactor is actually called from ReAct loop when threshold is artificially exceeded
- [ ] **无静默跳过**: compaction trigger check produces no side effects when not triggered; when triggered but NoOp, result is explicit (tokensBefore == tokensAfter)
- [ ] No owner-doc update required (design docs already describe trigger mechanism)
- [ ] `ai-dev/logs/` corresponding date entry updated

### Phase 4 - Tests

Status: planned
Targets: `io.nop.ai.agent.compact.TestCompactionContext`, `io.nop.ai.agent.compact.TestNoOpContextCompactor`, `io.nop.ai.agent.compact.TestCompactionInReActLoop`

- Item Types: `Proof`

- [ ] `TestCompactionContext`: verify construction with all fields, verify immutability
- [ ] `TestNoOpContextCompactor`: verify `compact()` returns result with equal before/after token counts, verify `retainedMessageCount == input.size()`, verify `snapshotId == null`
- [ ] `TestCompactionInReActLoop` (integration): verify compactor is NOT called when below thresholds (default NoOp), verify compactor IS called when `tokensUsed > maxTokens * 0.8`, verify compactor IS called when `messageCount > 30`, verify PRE_COMPACT hook fires before compact(), verify POST_COMPACT hook fires after compact(), verify recursive compaction prevention (compaction not triggered twice in same iteration), verify existing ReAct tests pass unchanged

Exit Criteria:

- [ ] Unit tests cover CompactionContext construction and field access
- [ ] Unit tests cover NoOpContextCompactor pass-through behavior (equal before/after, null snapshot)
- [ ] Integration test verifies compaction trigger when token threshold exceeded
- [ ] Integration test verifies compaction trigger when message count threshold exceeded
- [ ] Integration test verifies PRE_COMPACT/POST_COMPACT hooks fire around compaction
- [ ] Integration test verifies no recursive compaction
- [ ] All existing tests pass (backward compatible)
- [ ] **端到端验证**: existing e2e test `TestEndToEndReAct` passes without modification
- [ ] **接线验证**: `TestCompactionInReActLoop` verifies compactor is called from `ReActAgentExecutor.execute()` when thresholds are exceeded
- [ ] **无静默跳过**: tests verify NoOp returns explicit equal-count result, not null or empty
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

- [ ] `IContextCompactor` interface defines `compact(CompactionContext)` returning `CompactionResult`
- [ ] `CompactionContext` data object carries messages, config, session info
- [ ] `NoOpContextCompactor` returns explicit no-change result (equal before/after counts)
- [ ] `ReActAgentExecutor` checks compaction thresholds before each iteration
- [ ] `PRE_COMPACT` and `POST_COMPACT` hooks are invoked during compaction
- [ ] Backward compatible: all existing tests pass with NoOp default
- [ ] Roadmap L2-3 updated from ❌ to ✅
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [ ] No silent no-op or empty method body in new code
- [ ] No owner-doc update required
- [ ] Independent closure audit completed and evidence recorded

## Deferred But Adjudicated

### Actual Compression Logic

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: This plan defines the interface and integration point. L2-4 (progressive compression) will provide the first real implementation. The NoOp pass-through is the correct default for this layer.
- Successor Required: yes
- Successor Path: future plan for L2-4

### Token Estimation via ILlmDialect

- Classification: `optimization candidate`
- Why Not Blocking Closure: The trigger logic uses `ctx.getTokensUsed()` (accumulated from LLM response usage) which is precise post-call. Pre-call estimation via `ILlmDialect.estimateTokens()` is L2-16 and can be retrofitted without interface changes.
- Successor Required: yes
- Successor Path: future plan for L2-16

### DSL Compaction Config Parsing

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `agent.xdef` does not yet have a `<compaction>` element. Thresholds are hardcoded constants. DSL config can be added when the xdef schema is extended.
- Successor Required: no

### Session.markCompacted() Integration

- Classification: `optimization candidate`
- Why Not Blocking Closure: `AgentSession.markCompacted()` exists but the executor does not currently hold a direct reference to `AgentSession` (it works through `AgentExecutionContext`). Integration will happen when session management is tighter. The compaction result is still recorded in `CompactionResult`.
- Successor Required: no

## Non-Blocking Follow-ups

- L2-4 progressive compression initial version (Layer 0 pre-truncation + Layer 1 micro-compression)
- L2-16 token counting via `ILlmDialect.estimateTokens()` for pre-call estimation
- DSL `<compaction>` element in `agent.xdef`
- L3-9 full 5-layer pipeline with `ICompressionStrategy` extension point

## Closure

Status Note: <<filled at closure>>

Closure Audit Evidence:

- Reviewer / Agent: <<filled at closure>>
- Evidence: <<filled at closure>>

Follow-up:

- L2-4 progressive compression, L2-16 token estimation, DSL compaction config, L3-9 full pipeline
