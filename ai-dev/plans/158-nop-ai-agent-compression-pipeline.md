# 158 Complete 5-Layer Compression Pipeline — ICompressionStrategy Extension Point + Forced Stop

> **Plan Status**: active
> **Module**: nop-ai-agent
> **Work Item**: L3-9
> **Last Reviewed**: 2026-06-13
> **Source**: Carry-over from plan 156 (`ai-dev/plans/156-nop-ai-agent-token-counting.md`, Deferred: "Forced-Stop Trigger Logic (Layer 4)" + Non-Blocking Follow-up "L3-9 full 5-layer pipeline + `ICompressionStrategy` extension point + forced-stop using the estimator") and plan 155 (`ai-dev/plans/155-nop-ai-agent-progressive-compression.md`, Deferred: Layer 2 intermediate pruning / Layer 3 LLM summarization / Layer 4 forced stop → "future plan for L3-9"); roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 3 L3-9; design `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §7.
> **Related**: Plan 152 (IContextCompactor + NoOp), Plan 155 (Layer 0/1 progressive compression), Plan 156 (calibrated token estimator — unblocks the forced-stop pre-call check). Dependencies L2-4 (✅) and L2-16 (✅) are both satisfied.

## Purpose

Complete the context-window protection pipeline: turn the existing two-layer compaction (Layer 0 pre-truncation + Layer 1 micro-compression) into the full 5-layer model defined in `nop-ai-agent-reliability.md` §7, add the `ICompressionStrategy` pluggable extension point, and land the Layer 4 forced-stop hard protection that consumes the calibrated estimator delivered by plan 156. After this plan, long-running sessions escalate through all layers and cannot overflow the context window.

## Current Baseline

Verified against live repo on 2026-06-13:

- `IContextCompactor` is a single-method interface (`CompactionResult compact(CompactionContext ctx)`) — `nop-ai/nop-ai-agent/.../compact/IContextCompactor.java`. Two implementations exist: `NoOpContextCompactor` (estimator-based reporting, no compaction) and `MicroCompressionCompactor` (Layer 1: replaces old compressible tool results with placeholders, preserves system / first user / recent N tool results).
- Layer 0 (Tool Result pre-truncation) is implemented as `ToolResultTruncator` and is already wired **per-tool-call in the executor** at `ReActAgentExecutor.java:429` — it is independent of the compactor and remains as-is.
- `DefaultAgentEngine` defaults the executor to `MicroCompressionCompactor` (Layer 1 only). There is **no pipeline orchestrator** that escalates across layers.
- `shouldTriggerCompaction()` (`ReActAgentExecutor.java:528-534`) checks `ctx.getTokensUsed() > maxContextTokens * 0.8` **OR** `messages.size() > 30`. It uses **accumulated post-call usage only** — there is **no pre-call estimate** and **no 90% forced-stop check**. `DEFAULT_TRIGGER_TOKEN_PERCENT = 0.8`, `DEFAULT_MAX_CONTEXT_TOKENS = 128000`.
- `performCompaction()` (`:543-574`) builds `CompactConfig.defaults()`, invokes PRE_COMPACT/POST_COMPACT hooks, calls the compactor, and replaces messages only if `tokensAfter < tokensBefore`.
- `CompactionResult` carries `tokensBefore/tokensAfter/retainedMessageCount/snapshotId/compactedMessages`.
- `CompactConfig` carries `targetTokens/strategy(String)/preserveSystemMessages/maxRecentToolResults/truncationThresholdChars`. It does **not** carry `forcedStopPercent`, `keepTailPercent`, `triggerTokenPercent`, `triggerMaxMessages`, or `compressionModel` (the design's `<compaction>` attributes, §7.7).
- `CompactionContext` carries `messages/compactConfig/sessionId/agentName/executionContext/tokenEstimator`. The calibrated `ITokenEstimator` (plan 156) is reachable via both the context and the executor field.
- `AgentExecStatus` = {pending, running, completed, failed, cancelled}. There is **no** terminal status for forced-stop termination. `AgentEventType` has **no** FORCED_STOP event (grep for `FORCED_STOP|forcedStop|CONTEXT_FORCED` returns nothing).
- **`ICompressionStrategy` does not exist** — confirmed by grep across `nop-ai-agent/src/main/java`. Design §7.8 specifies it as the Layer 3 pluggable extension point (`name()` + `compact(CompactionContext)`).
- The 5-layer model is fully specified in `nop-ai-agent-reliability.md` §7.1-§7.9: escalation rule (Layer 1→2→3, stop when a layer relieves; Layer 0 independent; Layer 4 hard cap), dual-dimension trigger (token OR message count), two trigger points (pre-iteration estimate + post-response precise), forced-stop at `forcedStopPercent=0.9`, keep-tail `keepTailPercent=0.15`, Layer 3 graceful fallback (LLM unavailable → degrade to Layer 2 effect + log).
- Roadmap L3-9 status is ❌; dependencies L2-4 (✅) and L2-16 (✅) are satisfied, so L3-9 is unblocked.

### Pre-existing Gap

Only Layers 0 and 1 exist. There is no turn pruning (Layer 2), no LLM summarization (Layer 3), no forced stop (Layer 4), no `ICompressionStrategy` extension point, and no pipeline orchestrator. A long-running session can keep accumulating until the Provider rejects an oversized request — there is no hard protection.

## Goals

- Introduce the `ICompressionStrategy` pluggable extension point and a pipeline orchestrator that runs the configured layers in escalation order (a layer is invoked only when the previous one did not relieve the context).
- Implement Layer 2 (intermediate turn pruning) preserving tool_call/tool_result boundary integrity and head/tail anchors.
- Implement Layer 3 (LLM summarization) with a structured incremental summary and a graceful fallback when the LLM is unavailable/fails.
- Implement Layer 4 forced-stop hard protection at 90%, driven by the calibrated estimator's **pre-call** estimate, that terminates tool calling, sets a distinct terminal status, and publishes a FORCED_STOP event.
- Extend `CompactConfig` with the pipeline thresholds (all with sensible defaults, zero-config runnable).
- The full pipeline path from trigger → escalation → forced stop is verifiable end-to-end.

## Non-Goals

- DSL `<compaction>` element in `agent.xdef` — touches the codegen/schema (protected, plan-first); runtime `CompactConfig` with defaults makes the pipeline zero-config runnable without it (already a deferred item from plan 155).
- Alternative summarization strategies beyond the default (KeyInfoExtraction, HierarchicalRolling, VectorArchive) — optimization/out-of-scope; `ICompressionStrategy` makes them addable later.
- `SessionSnapshot` persistence / full-history offload to VFS during compaction — needs persistence (Layer 4 concern); already deferred from plan 155.
- Per-tool non-truncatable flag on `AiToolModel` — already deferred from plan 155.
- A dedicated cheaper-model `IChatService` for summarization — model override via `ChatOptions` is sufficient for v1.
- Prefix-cache integrity validation (`prefixHash` check) across compaction (design §7.9) — watch-only residual.

## Scope

### In Scope

- `ICompressionStrategy` extension point + a pipeline orchestrator compactor composing the layers with escalation.
- Layer 2 intermediate turn pruning strategy (tool_call/tool_result boundary-safe).
- Layer 3 LLM summarization strategy (FullSummary, incremental, structured) + graceful fallback.
- Layer 4 forced stop: pre-call estimate check at 90% via the calibrated estimator, terminal status, FORCED_STOP event, loop termination.
- `CompactConfig` extension with `forcedStopPercent`/`keepTailPercent`/`triggerTokenPercent`/`triggerMaxMessages`/`compressionModel` defaults.
- Wiring through `DefaultAgentEngine` (backward compatible; `MicroCompressionCompactor` / `NoOpContextCompactor.INSTANCE` remain usable).
- Unit + integration tests per layer and one full-pipeline end-to-end test.

### Out Of Scope

- DSL `<compaction>` element and Delta `<strategy>` override (codegen/protected).
- VectorArchive recall tool + alternative strategies.
- Snapshot persistence / VFS offload.
- Dedicated cheaper-model summarization service.

### Design Decisions (behavior semantics)

**Layer 0 stays where it is.** It is already applied per-tool-call in the executor (`:429`). The pipeline orchestrator composes Layers 1→2→3; Layer 0 is not moved. This keeps the change minimal and avoids re-wiring a working pre-truncation path.

**Escalation semantics (Layer 1→2→3):** the orchestrator runs layers in order; after each layer it re-estimates tokens. If the context is now below the trigger threshold, escalation stops (the layer "relieved" it). Each layer is independently triggerable per design §7.2 correction (Layer 2 checks its own `messageCount > layer2Threshold`, not "above Layer 1 threshold"). Escalation never raises an exception that fails the agent — worst case it leaves messages unchanged and logs.

**Layer 4 lives in the executor's compaction path, not purely in the compactor.** Forced stop must break the ReAct loop, so the pre-call estimate check and the termination/event/status enforcement belong in the executor (alongside `shouldTriggerCompaction`/`performCompaction`); the compactor/orchestrator produces the final summary that the forced stop emits. The estimator is consumed here (the unblocked capability from plan 156).

**Layer 3 graceful fallback is a first-class behavior, not an afterthought:** when no summarization strategy is configured or the LLM call fails, Layer 3 degrades to "Layer 2 effect" (preserve more original messages) and logs a fallback record — it never fails the agent. This follows design §7.2 and the module's D3 principle (every extension has a pass-through default), and is what allows the pipeline to be considered "complete" with a default Layer 3.

**Forced-stop terminal status is distinct.** Forced stop is neither success, failure, nor cancellation — it is a resource-limit termination. An additive `AgentExecStatus` value is introduced (not a rename/removal, so record-mappings stay valid by value). The FORCED_STOP event makes the outcome observable to subscribers.

**Configuration is runtime POJO, not DSL.** Thresholds live on `CompactConfig` (constructed by `DefaultAgentEngine` with defaults). This deliberately avoids touching the `agent.xdef` codegen/schema (protected) while keeping the pipeline zero-config.

## Execution Plan

### Phase 1 — ICompressionStrategy Extension Point + Pipeline Orchestrator + Config Model

Status: completed
Targets: new `ICompressionStrategy` interface, new pipeline orchestrator compactor (compact package), `CompactConfig` extension, `DefaultAgentEngine` wiring

- Item Types: `Fix | Proof`

- [x] Define the `ICompressionStrategy` extension point (identity + `compact(CompactionContext)` returning `CompactionResult`) per design §7.8
- [x] Implement a pipeline orchestrator compactor (implements `IContextCompactor`) that runs the configured strategies in escalation order: after each layer, re-estimate via the injected estimator and stop escalating once the context falls below the trigger threshold
- [x] Extend `CompactConfig` with `triggerTokenPercent` (0.8), `forcedStopPercent` (0.9), `keepTailPercent` (0.15), `triggerMaxMessages` (30), `compressionModel` (empty = main model) — all defaulted, zero-config runnable; preserve existing fields/constructors
- [x] Wire `DefaultAgentEngine` to default to the pipeline orchestrator composing the existing `MicroCompressionCompactor` as Layer 1 (plus Layer 2/3 from later phases as they land). `NoOpContextCompactor.INSTANCE` and `MicroCompressionCompactor` remain directly usable (backward compatible)
- [x] Unit tests: (a) orchestrator escalates — invokes the next layer only when the previous did not relieve; (b) respects `CompactConfig` thresholds; (c) backward-compatible with a single-strategy pipeline and with `NoOp` setups; (d) empty/null message list handled explicitly (returns a no-op result, not an exception)

Exit Criteria:

- [x] `ICompressionStrategy` interface exists and is composed by the orchestrator at runtime
- [x] Orchestrator runs layers in escalation order — verified by a test asserting each layer is invoked only when the prior layer did not relieve (e.g. counters/mocks on each strategy)
- [x] `CompactConfig` exposes the new thresholds with the documented defaults
- [x] `DefaultAgentEngine` defaults to the pipeline orchestrator; all existing module tests pass (backward compatible)
- [x] **接线验证**: a test asserts the orchestrator invokes the composed strategy's `compact` at runtime (not merely that both types exist)
- [x] **无静默跳过**: when no strategies are configured, an explicit NoOp-equivalent reporting result is returned (never an empty method body or swallowed path)
- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §7 updated if the orchestrator/extension-point contract differs from the documented shape; otherwise write `No owner-doc update required` — No owner-doc update required (interface `ICompressionStrategy { name(); compact(ctx); }` matches §7.8 exactly)
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes (411 tests green)
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 2 — Layer 2 Intermediate Turn Pruning

Status: completed
Targets: new Layer 2 turn-pruning strategy (compact package), orchestrator registration

- Item Types: `Proof`

- [x] Implement a Layer 2 strategy that prunes intermediate turns while preserving head anchors (system message, first user goal) and a tail window (`keepTailPercent`), and maintains tool_call↔tool_response boundary integrity (no orphaned tool_call without a response, no orphaned tool_response without a preceding call)
- [x] Give Layer 2 its own independent trigger (message count > `triggerMaxMessages`/layer2 threshold) per design §7.2 correction, not "above Layer 1 threshold"
- [x] Register Layer 2 in the orchestrator after Layer 1
- [x] Unit tests: (a) pruning reduces message count; (b) tool_call/tool_response pairing stays intact after pruning — explicit assertion that no orphaned pair exists; (c) head (system + first user) and tail window preserved; (d) skips safely when too few messages (head/tail window overlap) — explicit skip, not corruption

Exit Criteria:

- [x] Layer 2 reduces message count while preserving tool_call/tool_response pairing (a test proves zero orphaned pairs after pruning)
- [x] Head anchors (system, first user) and the tail window are preserved
- [x] Layer 2 skips explicitly when messages are too few (no exception, no corruption)
- [x] **接线验证**: a test asserts the orchestrator invokes Layer 2 only after Layer 1 failed to relieve and the message threshold is exceeded
- [x] **无静默跳过**: the too-few-messages case returns an unchanged result explicitly (logged/observable), not a silent `continue`/empty body
- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §7.2 updated if the trigger model differs from the documented correction; otherwise `No owner-doc update required` — No owner-doc update required (Layer 2 independently checks `messageCount > triggerMaxMessages` per §7.2 correction; head/tail preserved per §7.6)
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes (420 tests green)
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 3 — Layer 3 LLM Summarization + Graceful Fallback

Status: completed
Targets: new FullSummary strategy (compact package), `IChatService` injection into the summarization path, summary prompt template, `DefaultAgentEngine` wiring

- Item Types: `Proof`

- [x] Implement a FullSummary `ICompressionStrategy` that calls `IChatService` with a structured summary prompt (Goal / Constraints & Preferences / Progress / Key Decisions / Next Steps / Critical Context / Relevant Files) and produces a summary message, with incremental update when a previous summary exists (previous summary passed in, not a full rewrite)
- [x] Inject the executor's `IChatService` into the summarization strategy via `DefaultAgentEngine` wiring; if `compressionModel` is configured, route the summary call to that model via `ChatOptions` override, else use the main model
- [x] Graceful fallback: if `IChatService` is absent or the summary call fails, degrade to Layer 2 effect (preserve more original messages) and log a fallback record — the agent must not fail
- [x] Emit the summary as a retained message + keep the tail per `keepTailPercent`; never drop the system message or first user goal
- [x] Unit tests: (a) a summary message is produced on trigger (stub `IChatService`); (b) incremental update passes the previous summary into the prompt; (c) LLM failure → fallback preserves messages + logs (agent not failed); (d) system message and first user goal retained after summarization

Exit Criteria:

- [x] FullSummary produces a structured summary message via `IChatService` (verified with a stub `IChatService`)
- [x] Incremental summarization works — a test proves the previous summary is consumed when present
- [x] LLM failure degrades gracefully (no agent failure; messages preserved; fallback logged) — verified by a test that forces a failing `IChatService`
- [x] System message + first user goal retained; tail window preserved per `keepTailPercent`
- [x] **接线验证**: a test asserts the summarization strategy's `IChatService` call is invoked from the pipeline at runtime (not merely that the field is set)
- [x] **无静默跳过**: LLM failure is handled explicitly (fallback + log), not swallowed; absence of `IChatService` is an explicit fallback, not a silent no-op
- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §7.5/§7.8 updated if the summarization/fallback contract differs from the documented shape; otherwise `No owner-doc update required` — No owner-doc update required (7-section structured prompt + incremental `<previous-summary>` + graceful Layer 2 fallback match §7.5/§7.8/§7.2)
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes (430 tests green)
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 4 — Layer 4 Forced Stop (Capstone, Consumes Estimator) + Full-Pipeline End-to-End

Status: completed
Targets: forced-stop logic in the executor compaction path, `AgentExecStatus` additive value, `AgentEventType` FORCED_STOP event, end-to-end pipeline test

- Item Types: `Fix | Proof`

- [x] Add forced-stop hard protection at `forcedStopPercent` (0.9): pre-iteration, estimate the pending request with the calibrated `ITokenEstimator`; if the estimate exceeds `maxContextTokens * 0.9`, trigger forced stop (this is the capability plan 156 unblocked)
- [x] On forced stop: generate a final summary (via the Layer 3 strategy when available; otherwise a best-effort tail retention), stop further tool calls (break the ReAct loop), set the distinct terminal status, and publish the FORCED_STOP event
- [x] Add an additive terminal status value to `AgentExecStatus` (distinct from completed/failed/cancelled — not a rename/removal, so record-mappings stay valid by value)
- [x] Add a FORCED_STOP (context forced stop) event type to `AgentEventType`
- [x] End-to-end test: build a session whose estimated context exceeds 90% → the full pipeline runs (Layer 1 → 2 → 3 escalation) → forced stop fires → terminal status set, FORCED_STOP event published, and no further tool calls occur after the forced stop

Exit Criteria:

- [x] Forced stop fires when the **pre-call estimate** exceeds the 90% threshold (uses the calibrated estimator, not accumulated post-call usage) — verified by a test
- [x] A FORCED_STOP event is published on forced stop (assertable via an event subscriber)
- [x] Forced stop sets the distinct terminal status and stops further tool calls — a test proves no tool call executes after the forced stop
- [x] **端到端验证**: one test drives a session from trigger → Layer 1/2/3 escalation → forced stop, asserting status + event + message invariants (system/first-user retained, tool_call/tool_response pairing intact) along the full path
- [x] **接线验证**: a test asserts the estimator's `estimateTokens` is actually consumed by the forced-stop check at runtime (not merely that the estimator field is present)
- [x] **Anti-Hollow**: forced stop demonstrably prevents the oversized request (estimator-driven termination), not a no-op or a silent `continue`
- [x] **无静默跳过**: forced stop is explicit (event + terminal status), never a silent loop continuation
- [x] Roadmap L3-9 updated ❌ → ✅ (`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 3) once closure passes — updated at closure
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes (437 tests green)
- [x] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `ICompressionStrategy` extension point exists and is composed by the pipeline orchestrator (Phase 1)
- [x] Pipeline orchestrator runs layers in escalation order with backward-compatible defaults (Phase 1)
- [x] Layer 2 turn pruning preserves tool_call/tool_response pairing (Phase 2)
- [x] Layer 3 LLM summarization works and degrades gracefully on LLM failure (Phase 3)
- [x] Layer 4 forced stop fires on the pre-call estimate at 90%, terminates tool calls, sets terminal status, publishes FORCED_STOP event (Phase 4)
- [x] Full pipeline end-to-end (trigger → escalation → forced stop) verified by a focused test
- [x] Forced-stop consumes the calibrated estimator delivered by plan 156 (dependency satisfied, not hollow)
- [x] Backward compatible: existing module tests pass; `NoOpContextCompactor.INSTANCE` / `MicroCompressionCompactor` remain usable
- [x] No silent no-op / empty method body / placeholder in new code (anti-hollow verified by integration tests)
- [x] Roadmap L3-9 updated ❌ → ✅
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] Affected `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` sections synced to the landed contract (or `No owner-doc update required` per phase)
- [ ] Independent closure audit completed and evidence recorded

## Deferred But Adjudicated

### Alternative Summarization Strategies (KeyInfoExtraction / HierarchicalRolling / VectorArchive)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: The default FullSummary strategy covers the documented default Layer 3 behavior. `ICompressionStrategy` makes the alternatives addable without changing the pipeline. They are optimizations with distinct prompt/storage designs.
- Successor Required: yes
- Successor Path: future plan (Layer 4 VectorArchive recall tool also blocked on L2-15 working-memory tools)

### DSL `<compaction>` Element In agent.xdef + Delta `<strategy>` Override

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Adding a DSL element touches the `agent.xdef` codegen/schema (protected, plan-first). Runtime `CompactConfig` with sensible defaults already makes the pipeline zero-config runnable. Already a deferred item from plan 155.
- Successor Required: yes
- Successor Path: future plan (codegen/schema change)

### SessionSnapshot Persistence / Full-History Offload During Compaction

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Offloading the full pre-summary history to VFS needs persistence (Layer 4 concern). The summary message + tail retention already protect the context window. Already a deferred item from plan 155.
- Successor Required: yes
- Successor Path: future Layer 4 plan (needs VFS session store)

### Per-Tool Non-Truncatable Flag On AiToolModel

- Classification: `optimization candidate`
- Why Not Blocking Closure: The compressible-tool set is currently a static constant. A per-tool flag is a refinement that does not affect pipeline correctness. Already a deferred item from plan 155.
- Successor Required: no

### Dedicated Cheaper-Model Summarization IChatService

- Classification: `optimization candidate`
- Why Not Blocking Closure: `compressionModel` routing via `ChatOptions` override on the existing `IChatService` is sufficient for v1. A dedicated cheap-model service is a deployment optimization.
- Successor Required: no

## Non-Blocking Follow-ups

- Prefix-cache integrity validation (`prefixHash` check) across compaction (design §7.9) — watch-only residual
- Alternative summarization strategies + VectorArchive recall tool (see Deferred)
- DSL `<compaction>` element + Delta `<strategy>` override (see Deferred)
- `compressionModel` dedicated cheap-model service (see Deferred)

## Closure

Status Note: *(filled at closure)*

Closure Audit Evidence:

- Reviewer / Agent: *(filled at closure by an independent subagent)*
- Audit Session: *(filled at closure)*
- Evidence: *(filled at closure — per-Exit-Criterion PASS/FAIL + live code/test anchors + `check-plan-checklist.mjs` exit 0 + `scan-hollow-implementations.mjs` exit 0)*

Follow-up:

- *(filled at closure; only non-blocking items listed in Non-Blocking Follow-ups / Deferred But Adjudicated)*
