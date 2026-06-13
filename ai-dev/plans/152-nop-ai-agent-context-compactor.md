# 152 IContextCompactor Interface + NoOpContextCompactor + ReAct Integration

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L2-3
> **Last Reviewed**: 2026-06-13
> **Source**: Carry-over from plan 150 (`ai-dev/plans/150-nop-ai-agent-lifecycle-hook.md`), `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §7, `ai-dev/design/nop-ai-agent/02/execut-model.md` §5.1
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

Status: completed
Targets: `io.nop.ai.agent.compact` package (new)

- Item Types: `Proof`

- [x] Create `CompactionContext` data class with fields: `messages` (List<ChatMessage> — the messages to potentially compact), `compactConfig` (CompactConfig), `sessionId` (String), `agentName` (String), `executionContext` (AgentExecutionContext — for access to constraints, model, etc.)
- [x] Create `IContextCompactor` interface with method: `CompactionResult compact(CompactionContext ctx)` — takes current messages and config, returns compaction result. The implementor is responsible for deciding whether compaction is needed and performing it

Exit Criteria:

- [x] `CompactionContext` exists as an immutable data carrier with messages, compactConfig, sessionId, agentName, executionContext
- [x] `IContextCompactor` interface defines `compact(CompactionContext)` returning `CompactionResult`
- [x] **端到端验证** N/A: interface definition phase
- [x] **接线验证** N/A: no inter-component wiring yet
- [x] **无静默跳过** N/A: pure type definitions
- [x] No owner-doc update required (design docs already describe compaction contract)
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 2 - NoOpContextCompactor Implementation

Status: completed
Targets: `io.nop.ai.agent.compact.NoOpContextCompactor`

- Item Types: `Proof`

- [x] Create `NoOpContextCompactor` implementing `IContextCompactor` — `compact()` returns a `CompactionResult` with `tokensBefore == tokensAfter`, `retainedMessageCount == messages.size()`, `snapshotId = null`, and the messages list unchanged. This signals "no compaction occurred" to the executor

Exit Criteria:

- [x] `NoOpContextCompactor.compact()` returns a result where `tokensBefore == tokensAfter`
- [x] `NoOpContextCompactor.compact()` returns `retainedMessageCount == input message count`
- [x] `NoOpContextCompactor.compact()` returns `snapshotId = null`
- [x] **端到端验证** N/A: pass-through implementation, no pipeline
- [x] **接线验证** N/A: not yet wired into executor
- [x] **无静默跳过**: NoOp returns valid CompactionResult with equal before/after counts, not null or empty
- [x] No owner-doc update required
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 3 - ReActAgentExecutor Integration

Status: completed
Targets: `io.nop.ai.agent.engine.ReActAgentExecutor`, `io.nop.ai.agent.engine.DefaultAgentEngine`

- Item Types: `Proof`

- [x] Add `IContextCompactor` field to `ReActAgentExecutor` (final, set via Builder)
- [x] Add `contextCompactor(IContextCompactor)` method to `ReActAgentExecutor.Builder`, defaulting to `NoOpContextCompactor` if not set
- [x] Add compaction trigger check in the ReAct loop — before each iteration (after incrementing iteration counter, before PRE_REASONING hook), check if compaction should be triggered. Trigger condition: `ctx.getTokensUsed() > ctx.getMaxTokens() * TRIGGER_TOKEN_PERCENT` (default 0.8) OR `ctx.getMessages().size() > TRIGGER_MAX_MESSAGES` (default 30). Both thresholds are constants in the executor for now (L2-16 will introduce `ILlmDialect.estimateTokens()` for pre-call estimation; currently using `ctx.getTokensUsed()` which accumulates from LLM response usage)
- [x] When triggered: invoke `PRE_COMPACT` hook → call `contextCompactor.compact()` → invoke `POST_COMPACT` hook → if `CompactionResult.tokensAfter < tokensBefore`, replace messages in context and call `session.markCompacted()` (if session is available via execution context)
- [x] Wire `IContextCompactor` through `DefaultAgentEngine` — pass `NoOpContextCompactor.INSTANCE` to executor builder (no DSL loading yet; actual compactor selection from agent config is L2-4 scope)
- [x] Guard: compaction is never triggered during the same iteration that already performed compaction (prevent recursive compaction per design doc §7.6)

Exit Criteria:

- [x] `ReActAgentExecutor.Builder` has `contextCompactor()` method
- [x] Default contextCompactor is `NoOpContextCompactor` (backward compatible)
- [x] Compaction trigger check runs before each ReAct iteration using token and message count thresholds
- [x] `PRE_COMPACT` hook fires before `contextCompactor.compact()`
- [x] `POST_COMPACT` hook fires after compaction completes
- [x] NoOp default means no messages are actually modified
- [x] Recursive compaction is prevented (flag reset each iteration)
- [x] Existing tests pass unchanged (NoOp default)
- [x] **端到端验证**: existing e2e test `TestEndToEndReAct` passes without modification (NoOp default)
- [x] **接线验证**: new test verifies compactor is actually called from ReAct loop when threshold is artificially exceeded
- [x] **无静默跳过**: compaction trigger check produces no side effects when not triggered; when triggered but NoOp, result is explicit (tokensBefore == tokensAfter)
- [x] No owner-doc update required (design docs already describe trigger mechanism)
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 4 - Tests

Status: completed
Targets: `io.nop.ai.agent.compact.TestCompactionContext`, `io.nop.ai.agent.compact.TestNoOpContextCompactor`, `io.nop.ai.agent.compact.TestCompactionInReActLoop`

- Item Types: `Proof`

- [x] `TestCompactionContext`: verify construction with all fields, verify immutability
- [x] `TestNoOpContextCompactor`: verify `compact()` returns result with equal before/after token counts, verify `retainedMessageCount == input.size()`, verify `snapshotId == null`
- [x] `TestCompactionInReActLoop` (integration): verify compactor is NOT called when below thresholds (default NoOp), verify compactor IS called when `tokensUsed > maxTokens * 0.8`, verify compactor IS called when `messageCount > 30`, verify PRE_COMPACT hook fires before compact(), verify POST_COMPACT hook fires after compact(), verify recursive compaction prevention (compaction not triggered twice in same iteration), verify existing ReAct tests pass unchanged

Exit Criteria:

- [x] Unit tests cover CompactionContext construction and field access
- [x] Unit tests cover NoOpContextCompactor pass-through behavior (equal before/after, null snapshot)
- [x] Integration test verifies compaction trigger when token threshold exceeded
- [x] Integration test verifies compaction trigger when message count threshold exceeded
- [x] Integration test verifies PRE_COMPACT/POST_COMPACT hooks fire around compaction
- [x] Integration test verifies no recursive compaction
- [x] All existing tests pass (backward compatible)
- [x] **端到端验证**: existing e2e test `TestEndToEndReAct` passes without modification
- [x] **接线验证**: `TestCompactionInReActLoop` verifies compactor is called from `ReActAgentExecutor.execute()` when thresholds are exceeded
- [x] **无静默跳过**: tests verify NoOp returns explicit equal-count result, not null or empty
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] No owner-doc update required
- [x] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

- [x] `IContextCompactor` interface defines `compact(CompactionContext)` returning `CompactionResult`
- [x] `CompactionContext` data object carries messages, config, session info
- [x] `NoOpContextCompactor` returns explicit no-change result (equal before/after counts)
- [x] `ReActAgentExecutor` checks compaction thresholds before each iteration
- [x] `PRE_COMPACT` and `POST_COMPACT` hooks are invoked during compaction
- [x] Backward compatible: all existing tests pass with NoOp default
- [x] Roadmap L2-3 updated from ❌ to ✅
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] No silent no-op or empty method body in new code
- [x] No owner-doc update required
- [x] Independent closure audit completed and evidence recorded

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

## Follow-up handled by 155-nop-ai-agent-progressive-compression.md

Plan 155 picks up L2-4 (progressive compression initial version: Layer 0 pre-truncation + Layer 1 micro-compression), the direct successor work item from this plan's Non-Blocking Follow-ups.

## Closure

Status Note: All 4 phases implemented and verified. 288 tests pass (270 existing + 18 new). Backward compatible with NoOp default. Independent closure audit completed — all 7 closure gates PASS, all 4 anti-hollow checks PASS.

Closure Audit Evidence:

- Reviewer / Agent: Independent general subagent (task ses_1409b80cdffeHnqMWPQsy3hXvl)
- Evidence:
  - Gate 1 (IContextCompactor interface): PASS — `IContextCompactor.java:7` defines `compact(CompactionContext)` returning `CompactionResult`
  - Gate 2 (CompactionContext data object): PASS — `CompactionContext.java:13-17` immutable fields with `List.copyOf`
  - Gate 3 (NoOpContextCompactor returns no-change): PASS — `NoOpContextCompactor.java:10-18` equal before/after counts, null snapshotId
  - Gate 4 (ReActAgentExecutor threshold check): PASS — `ReActAgentExecutor.java:212-214` calls `shouldTriggerCompaction` before each iteration
  - Gate 5 (PRE/POST_COMPACT hooks invoked): PASS — `ReActAgentExecutor.java:450` PRE_COMPACT, line 454 POST_COMPACT
  - Gate 6 (Backward compatible): PASS — `DefaultAgentEngine.java:165` passes NoOpContextCompactor.INSTANCE, builder defaults to INSTANCE
  - Gate 7 (No silent no-op): PASS — all methods have substantive logic, no empty bodies or TODOs
  - Anti-Hollow: compaction trigger actually calls `contextCompactor.compact()` at line 452, hook ordering verified (PRE→compact→POST)
  - Tests: 18 new tests all pass (TestCompactionContext:6, TestNoOpContextCompactor:6, TestCompactionInReActLoop:6)

Follow-up:

- L2-4 progressive compression, L2-16 token estimation, DSL compaction config, L3-9 full pipeline
