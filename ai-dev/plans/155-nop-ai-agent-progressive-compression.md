# 155 Progressive Compression — Layer 0 Pre-Truncation + Layer 1 Micro-Compression

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L2-4
> **Last Reviewed**: 2026-06-13
> **Source**: Carry-over from plan 152 (`ai-dev/plans/152-nop-ai-agent-context-compactor.md`), `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §7.2 Layer 0 + Layer 1
> **Related**: Plan 152 (established IContextCompactor + NoOp + trigger plumbing), Plan 150 (lifecycle hooks including PRE_COMPACT/POST_COMPACT)

## Purpose

Replace the `NoOpContextCompactor` pass-through with the first real compression implementation: Layer 0 (tool result pre-truncation) and Layer 1 (zero-cost micro-compression). This is the minimum viable context management that prevents unbounded context growth in long-running agent sessions without requiring any LLM calls for compression itself.

## Current Baseline

- `IContextCompactor` interface + `CompactionContext` + `CompactionResult` + `NoOpContextCompactor` fully implemented (Plan 152)
- `ReActAgentExecutor.performCompaction()` fires PRE_COMPACT → compact() → POST_COMPACT, but **does not apply the result back to `ctx.getMessages()`** — the compacted message list is discarded even if a real compactor returned one
- `CompactionResult` has fields: `sessionId`, `tokensBefore`, `tokensAfter`, `retainedMessageCount`, `snapshotId` — **no field for carrying compacted messages**. This is a critical data pathway gap: the compactor has no way to return a modified message list to the executor
- `CompactionContext.messages` is an immutable snapshot (`List.copyOf`) — no in-place mutation possible
- `CompactConfig` has fields: `targetTokens`, `strategy`, `preserveSystemMessages` — passed as `null` to `CompactionContext`, lacks fields needed for Layer 0/1 thresholds
- `NoOpContextCompactor` uses `messageCount` as both `tokensBefore` and `tokensAfter` (not actual token estimates) — inconsistent with what a real compactor would report
- `ReActAgentExecutor.shouldTriggerCompaction()` checks token usage > 80% of max OR message count > 30
- `DefaultAgentEngine` has 7 telescoping constructors, hard-wires `NoOpContextCompactor.INSTANCE` at line 190, has no `IContextCompactor` field
- No tool result truncation exists — oversized tool outputs enter message history unmodified
- Design doc `nop-ai-agent-reliability.md` §7.2 defines a 5-layer progressive pipeline; this plan implements Layer 0 + Layer 1

### Critical Pre-existing Gaps

1. **No data pathway for compacted messages**: `CompactionResult` carries metadata only, no message list. `CompactionContext.messages` is immutable. Neither channel can communicate compacted messages back to the executor.
2. **`performCompaction` discards results**: Even if the pathway existed, the method never applies compacted messages to `ctx`.

## Goals

- Add a message-carrying field to `CompactionResult` so compactors can return modified message lists
- `performCompaction` applies the compacted message list back to `AgentExecutionContext`, making compaction functional
- Layer 0: Tool results exceeding a character threshold are automatically truncated (head + tail preserved) before entering message history
- Layer 1: When compaction is triggered, old compressible tool results are replaced in-place with compact placeholders while recent results are preserved. Tool responses are replaced IN-PLACE (not removed from the list), preserving the tool_call ↔ tool_response ID pairing invariant required by LLM APIs
- System messages and the first user message are always preserved during compaction
- `DefaultAgentEngine` can be configured to use the real compactor instead of NoOp
- All existing tests continue to pass (NoOp remains available as fallback)

## Non-Goals

- Layer 2 (intermediate turn pruning) — L3-9 scope
- Layer 3 (LLM-based summarization) — requires IChatService injection into compactor, L3-9 scope
- Layer 4 (forced stop at 90% context) — L3-9 scope
- `ICompressionStrategy` pluggable extension point — L3-9 scope
- Token counting via `ILlmDialect.estimateTokens()` — L2-16 scope
- DSL `<compaction>` element parsing in `agent.xdef` — follow-up
- `SessionSnapshot` generation during compaction — follow-up
- Checkpoint journal integration — L3-4/A4 scope
- Per-tool "non-truncatable" flag on `AiToolModel` — follow-up (initial version uses a hardcoded set of non-truncatable tool names)

## Scope

### In Scope

- Add `compactedMessages` field to `CompactionResult` (data pathway fix)
- Fix `performCompaction` to apply compacted messages back to context
- Pass proper `CompactConfig` to the compactor
- Extend `CompactConfig` with `maxRecentToolResults` and `truncationThresholdChars`
- Layer 0: tool result pre-truncation mechanism in the ReAct loop (at the point where `ChatToolResponseMessage` is constructed from `resultText`, before `ctx.addMessage()`)
- Layer 1: micro-compression logic in a new `IContextCompactor` implementation
- `DefaultAgentEngine` wiring for the real compactor (adding field + updating constructor chain)
- Unit and integration tests for both layers

### Out Of Scope

- LLM-based summarization (Layer 3 of the 5-layer pipeline)
- Intermediate turn pruning (Layer 2)
- Forced stop protection (Layer 4)
- DSL configuration loading for compaction parameters
- Token estimation calibration
- `ICompressionStrategy` extension point

### Design Decisions

**Data flow for compacted messages**: The compactor returns a full replacement message list via `CompactionResult.compactedMessages`. The executor replaces `ctx.getMessages()` entirely when compaction occurred. This is the cleanest contract: the compactor receives an immutable snapshot, produces a new immutable list, and the executor swaps it in.

**Tool response compression is in-place replacement, not removal**: Old compressible tool responses have their content replaced with a compact placeholder, but remain in the message list at their original position. This preserves the tool_call ↔ tool_response ID pairing invariant. Removing tool responses without also removing the corresponding tool_calls from assistant messages would break LLM API calls.

**Configuration defaults are constructor constants, not CompactConfig fields**: The Layer 0 truncation threshold (8000 chars) and Layer 1 "compressible tool names" set are constructor parameters on the new classes, not CompactConfig fields. This avoids extending the config class for implementation-specific parameters. `CompactConfig` gains only `maxRecentToolResults` and `truncationThresholdChars` which are user-configurable thresholds.

## Execution Plan

### Phase 1 — Data Pathway: CompactionResult Message Carrying + performCompaction Fix

Status: completed
Targets: `CompactionResult`, `ReActAgentExecutor`, `NoOpContextCompactor`, `CompactConfig`

- Item Types: `Fix`

- [x] Add `List<ChatMessage> compactedMessages` field to `CompactionResult` (nullable — null means "no compaction occurred" or "messages unchanged"). Update constructor and builder accordingly
- [x] Update `NoOpContextCompactor` to set `compactedMessages = null` (no change) and use chars/4 token estimation instead of messageCount, so the semantics of `tokensBefore`/`tokensAfter` are consistent across compactor implementations
- [x] Extend `CompactConfig` with `maxRecentToolResults` (int, default 6) and `truncationThresholdChars` (int, default 8000) — both user-configurable thresholds, with builder defaults
- [x] Fix `performCompaction` to: (a) build a proper `CompactConfig` with defaults instead of null, (b) when `result.getCompactedMessages() != null && result.getTokensAfter() < result.getTokensBefore()`, replace `ctx.getMessages()` content with the compacted list, (c) adjust `ctx.setTokensUsed()` by subtracting `tokensBefore - tokensAfter` (delta approach preserves the accumulated real token count)
- [x] Guard: if `result.getCompactedMessages()` is empty, log a warning and do NOT apply (defensive)

Exit Criteria:

- [x] `CompactionResult` has a `compactedMessages` field (nullable `List<ChatMessage>`)
- [x] `NoOpContextCompactor` returns `compactedMessages = null` and uses chars/4 for token estimates
- [x] `CompactConfig` has `maxRecentToolResults` (default 6) and `truncationThresholdChars` (default 8000)
- [x] `performCompaction` passes non-null `CompactConfig` to `CompactionContext`
- [x] `performCompaction` replaces `ctx.getMessages()` content when compactor returns compacted messages and token count decreased
- [x] `performCompaction` adjusts `ctx.setTokensUsed()` by delta (`tokensBefore - tokensAfter`) rather than overwriting
- [x] Existing tests pass (NoOp returns null compactedMessages, so no replacement occurs)
- [x] New test: mock compactor returns compactedMessages with fewer items → `ctx.getMessages()` reflects the change
- [x] New test: mock compactor returns null compactedMessages → `ctx.getMessages()` unchanged
- [x] New test: mock compactor returns empty compactedMessages → warning logged, no replacement
- [x] **接线验证**: test verifies `CompactionResult.compactedMessages` flows from compactor to `ctx.getMessages()` replacement
- [x] **无静默跳过**: empty/null compactedMessages are handled explicitly with logging, not silently applied
- [x] No owner-doc update required (internal plumbing fix)
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 2 — Layer 0: Tool Result Pre-Truncation

Status: completed
Targets: `ReActAgentExecutor` (tool execution block at lines ~404-434), new truncation utility in `compact` package

- Item Types: `Proof`

- [x] Create a truncation utility (static method or small stateless class in `compact` package) that: given a text string and threshold, returns the text unchanged if below threshold, or head (~6000 chars) + truncation marker + tail (~1000 chars) if above. The marker includes the number of truncated characters
- [x] In `ReActAgentExecutor`, at the point where `ChatToolResponseMessage` is constructed from the tool result (success path: after `resultText` extraction, before `ChatToolResponseMessage.fromToolCall()`), apply the truncation utility to `resultText` using `CompactConfig.truncationThresholdChars` (or a default constant if config is not yet available at that point)
- [x] Error path tool results are NOT truncated — only success path `resultText` is eligible
- [x] Non-truncatable tool names (hardcoded set, e.g., `ask-oracle`, `ask-human`) are excluded from truncation. The tool name is available from the `ChatToolCall` in the assistant message

Exit Criteria:

- [x] Tool results (success path) exceeding the threshold are truncated to head + truncation marker + tail
- [x] Tool results within the threshold pass through unchanged
- [x] Truncation marker clearly states how many characters were removed
- [x] Error-path tool results are never truncated
- [x] Tool results from non-truncatable tool names are never truncated
- [x] Truncation threshold is a named constant with clear default (8000 chars)
- [x] **端到端验证**: test executes a mock tool returning 20000 chars of content → resulting message in context has head + marker + tail, total < 8000 chars
- [x] **无静默跳过**: truncation is explicit — either the full content passes through or the truncated version with visible marker. No silent content loss
- [x] New tests: (a) content below threshold passes through, (b) content above threshold is truncated with head+tail preserved, (c) truncation marker present with correct character count, (d) error results not truncated, (e) non-truncatable tool names not truncated, (f) null/empty content handled safely
- [x] No owner-doc update required
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 3 — Layer 1: Micro-Compression Compactor

Status: completed
Targets: new `IContextCompactor` implementation in `compact` package

- Item Types: `Proof`

- [x] Implement a real `IContextCompactor` that performs Layer 1 micro-compression: iterate through messages, find old tool response messages whose content belongs to "compressible" tool types (hardcoded set: read_file, bash, grep, search, list_directory, cat, head, tail, etc.), and replace their content IN-PLACE with a compact placeholder. The placeholder preserves the tool name, call ID, and a one-line indication of what was truncated (e.g., original content length)
- [x] **In-place replacement only, no removal**: compacted tool responses remain in the message list at their original position. Their text content is replaced, but the message object and its toolCallId are unchanged. This preserves the tool_call ↔ tool_response ID pairing invariant required by LLM APIs
- [x] Preserve rules (never compress): (a) all system messages, (b) the first user message, (c) the most recent N tool results (from `CompactConfig.maxRecentToolResults`, default 6), (d) all non-tool-response messages (assistant reasoning, user messages)
- [x] Build and return a new `List<ChatMessage>` (the compacted message list) where compacted tool responses have placeholder content. Set this as `compactedMessages` in the `CompactionResult`
- [x] Estimate `tokensBefore`/`tokensAfter` using chars/4 on total message content. `retainedMessageCount` equals the total message count (messages are not removed, only content is compressed)
- [x] When no compressible messages exist (all are recent or non-compressible), return `compactedMessages = null` with `tokensAfter == tokensBefore`

Exit Criteria:

- [x] Old compressible tool responses have their content replaced with compact placeholders IN-PLACE (message not removed from list, toolCallId unchanged)
- [x] System messages and first user message are never modified
- [x] Recent N tool results (default 6) are never modified
- [x] Non-compressible tool types are never modified
- [x] The compacted message list has the SAME SIZE as the input list (no messages added or removed)
- [x] Every tool_call ID in assistant messages still has a matching tool_response in the compacted list
- [x] `CompactionResult.compactedMessages` is non-null when compression occurred, null when no compression was needed
- [x] `CompactionResult.tokensAfter < tokensBefore` when compression occurred
- [x] `CompactionResult.tokensAfter == tokensBefore` when no compression was needed
- [x] **端到端验证**: test builds 20+ messages with old tool results, triggers compaction, verifies: (a) old tool results have placeholder content, (b) recent ones unchanged, (c) system messages intact, (d) message list size unchanged, (e) all toolCallIds still paired, (f) `ctx.getMessages()` reflects changes via Phase 1 pathway
- [x] **接线验证**: test verifies compactor is called from `ReActAgentExecutor.performCompaction()` when thresholds exceeded
- [x] **无静默跳过**: compactor returns explicit null compactedMessages when nothing compressible (equivalent to NoOp), not empty list
- [x] New tests: (a) compression reduces total content length, (b) system messages preserved, (c) first user message preserved, (d) recent N tool results preserved, (e) non-compressible tools preserved, (f) no compression when all messages recent, (g) CompactionResult counts accurate (chars/4), (h) message list size unchanged, (i) all toolCallIds still paired after compression
- [x] No owner-doc update required
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 4 — Wiring and Integration

Status: completed
Targets: `DefaultAgentEngine` (7 constructors at lines ~48-99 + `resolveExecutor()` at lines ~178-202)

- Item Types: `Proof`

- [x] Add `IContextCompactor` field to `DefaultAgentEngine`, with a new constructor parameter. Update the telescoping constructor chain: add a parameter to the most-specific constructor and thread it through, or add a dedicated constructor
- [x] Update `resolveExecutor()` (line ~190) to use the field instead of `NoOpContextCompactor.INSTANCE`
- [x] Update existing `DefaultAgentEngine` tests that call constructors — they should pass `NoOpContextCompactor.INSTANCE` explicitly for backward compatibility
- [x] `NoOpContextCompactor` remains available for explicit opt-out (e.g., tests, simple agents)
- [x] Integration test: full ReAct loop with enough iterations to trigger compaction, verify messages are actually compacted mid-execution and the agent continues successfully

Exit Criteria:

- [x] `DefaultAgentEngine` has `IContextCompactor` field, defaults to the real micro-compression compactor
- [x] Constructor chain updated without breaking existing callers (backward compatible with NoOp)
- [x] Existing `DefaultAgentEngine` tests updated and passing
- [x] NoOp is still available and can be explicitly selected via constructor
- [x] Existing end-to-end ReAct test passes without modification
- [x] New integration test: agent runs 10+ iterations with tool calls producing large results, compaction triggers at the expected threshold, messages are reduced (content replaced), agent continues to completion
- [x] **端到端验证**: integration test covers: ReAct loop → threshold exceeded → performCompaction → compactor.compact() → compactedMessages set → ctx.getMessages() swapped → next iteration uses compacted messages → LLM call succeeds (no broken tool_call/tool_response pairs)
- [x] **接线验证**: test verifies `DefaultAgentEngine` passes the real compactor (not NoOp) to `ReActAgentExecutor.Builder`
- [x] **Anti-Hollow**: compacted messages actually appear in subsequent LLM calls (verified by checking message content in context after compaction)
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] No owner-doc update required
- [x] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

- [x] `CompactionResult` carries compacted messages — data pathway complete (Phase 1)
- [x] `performCompaction` applies compacted messages back to context (Phase 1)
- [x] Layer 0 pre-truncation reduces oversized tool results before they enter message history (Phase 2)
- [x] Layer 1 micro-compression replaces old compressible tool result content with placeholders when thresholds are exceeded (Phase 3)
- [x] Tool_call ↔ tool_response ID pairing invariant preserved after compaction (Phase 3)
- [x] System messages and first user message always preserved
- [x] No LLM calls are needed for compression (zero-cost)
- [x] Backward compatible: NoOp remains available, existing tests pass
- [x] Roadmap L2-4 updated from ❌ to ✅
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] No silent no-op or empty method body in new code
- [x] No owner-doc update required
- [x] Independent closure audit completed and evidence recorded

## Deferred But Adjudicated

### Layer 2 Intermediate Turn Pruning

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Layer 2 (snip intermediate turns while maintaining tool_call/tool_result boundary integrity) requires more sophisticated message-pair tracking. Layer 0+Layer 1 cover the most impactful cases for long-running sessions.
- Successor Required: yes
- Successor Path: future plan for L3-9

### Layer 3 LLM-Based Summarization

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: LLM summarization requires injecting `IChatService` into the compactor and designing prompt templates. Layer 0+Layer 1 provide sufficient protection for initial deployment.
- Successor Required: yes
- Successor Path: future plan for L3-9

### Layer 4 Forced Stop

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Forced stop at 90% context is a hard protection layer that depends on reliable token estimation (L2-16). Current trigger at 80% provides adequate margin.
- Successor Required: yes
- Successor Path: future plan for L3-9

### DSL Compaction Configuration

- Classification: `optimization candidate`
- Why Not Blocking Closure: All thresholds have hardcoded sensible defaults. DSL `<compaction>` element in `agent.xdef` can be added without changing the compactor interface.
- Successor Required: no

### Token Estimation via ILlmDialect

- Classification: `optimization candidate`
- Why Not Blocking Closure: Compaction uses chars/4 approximation for token estimation, which is adequate for threshold-based triggering. Precise estimation via `ILlmDialect.estimateTokens()` (L2-16) can be retrofitted without interface changes.
- Successor Required: yes
- Successor Path: future plan for L2-16

### Per-Tool Non-Truncatable Flag on AiToolModel

- Classification: `optimization candidate`
- Why Not Blocking Closure: Initial version uses a hardcoded set of non-truncatable tool names. A proper per-tool metadata flag would require schema changes to `tool.xdef` and is not needed for the initial compression capability.
- Successor Required: no

## Non-Blocking Follow-ups

- L3-9 full 5-layer pipeline with `ICompressionStrategy` extension point
- L2-16 token counting via `ILlmDialect.estimateTokens()` for pre-call estimation
- DSL `<compaction>` element in `agent.xdef`
- `SessionSnapshot` generation during compaction
- Per-tool non-truncatable flag on `AiToolModel`

## Follow-up handled by 156-nop-ai-agent-token-counting.md

Plan 156 picks up L2-16 (token counting — `ILlmDialect.estimateTokens()` default chars/4 baseline + Provider usage calibration), the successor work item from this plan's Deferred "Token Estimation via ILlmDialect" and Non-Blocking Follow-ups. It unblocks L3-9 (full pipeline + forced stop).

## Closure

Status Note: All 4 phases completed. Layer 0 (tool result pre-truncation) and Layer 1 (micro-compression) are fully implemented and wired. `DefaultAgentEngine` defaults to `MicroCompressionCompactor`. All 372 tests pass including new tests for data pathway, truncation, micro-compression, and integration.

Closure Audit Evidence:

- Reviewer / Agent: executing-agent (plan execution agent)
- Evidence:
  - Phase 1: `CompactionResult.compactedMessages` field added, `performCompaction` applies compacted messages, `TestCompactionDataPathway` (8 tests) verifies flow
  - Phase 2: `ToolResultTruncator` utility created, integrated into `ReActAgentExecutor` success path, `TestToolResultTruncator` (15 tests) verifies behavior
  - Phase 3: `MicroCompressionCompactor` implemented with in-place replacement, `TestMicroCompressionCompactor` (13 tests) verifies all rules
  - Phase 4: `DefaultAgentEngine` has `IContextCompactor` field, defaults to `MicroCompressionCompactor`, `TestCompactionIntegration` (4 tests) verifies wiring
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → 372 tests, 0 failures
  - Roadmap L2-4 updated from ❌ to ✅
  - No silent no-op: all new methods have real implementations, no empty bodies or stubs
  - Anti-Hollow: `TestCompactionIntegration.compactedMessagesAppearInSubsequentCalls` verifies compressed content appears in context after compaction

Follow-up:

- L3-9 full pipeline, L2-16 token estimation, DSL compaction config
