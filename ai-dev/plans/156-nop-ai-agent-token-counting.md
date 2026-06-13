# 156 Token Counting — ILlmDialect.estimateTokens() + Provider Usage Calibration

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L2-16
> **Last Reviewed**: 2026-06-13
> **Source**: Carry-over from plan 155 (`ai-dev/plans/155-nop-ai-agent-progressive-compression.md`, Deferred: "Token Estimation via ILlmDialect") and plan 152 (`ai-dev/plans/152-nop-ai-agent-context-compactor.md`, Deferred: "Token Estimation via ILlmDialect"); roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 2 L2-16
> **Related**: Plan 152 (IContextCompactor + NoOp), Plan 155 (Layer 0/1 progressive compression — both deferred L2-16 as successor). Unblocks L3-9 (full 5-layer pipeline + forced stop).

## Purpose

Replace the hardcoded `chars/4` token approximation with a dialect-aware, runtime-calibrated token estimator. After this plan, pre-call token estimation is reliable enough for compaction accounting and ready for L3-9's forced-stop protection (Layer 4 of the compression pipeline).

## Current Baseline

Verified against live repo on 2026-06-13:

- `ILlmDialect` exists in **nop-ai-core** (`io.nop.ai.core.dialect.ILlmDialect`) with 9 methods (buildUrl, setHeaders, buildBody, parseResponse, parseStreamChunk, convertMessage, getRole, convertToolDefinitions default, getName). It has **NO `estimateTokens()` method**.
- `AbstractLlmDialect` (nop-ai-core) provides shared helpers (role mapping, usage parsing, max-tokens resolution) but **no token estimation**.
- 4 dialect implementations registered in `LlmDialectFactory.getDialect(ApiStyle)`: `OpenAiDialect`, `AnthropicDialect`, `GeminiDialect`, `OllamaDialect` (all in nop-ai-core).
- Token estimation today is a single **static package-private** method `NoOpContextCompactor.estimateTokens(List<ChatMessage>)` = `sum(message.content.length()) / 4`. It ignores message roles, tool-call payloads, tool-response structure, and any per-Provider tokenization difference.
- Consumers of that static helper: `NoOpContextCompactor.compact()`, `MicroCompressionCompactor` (computes both `tokensBefore` and `tokensAfter` via it), and tests `TestMicroCompressionCompactor` / `TestCompactionDataPathway`.
- Post-call usage is **precise but only available after the call**: `ReActAgentExecutor` line 286-288 reads `response.getPromptTokens()` / `getCompletionTokens()` from the Provider's `ChatUsage` and accumulates into `ctx.tokensUsed`. `ChatUsage` (nop-ai-api) carries `promptTokens` (Integer) — this is the calibration ground truth.
- `shouldTriggerCompaction()` (line 491) compares accumulated `ctx.getTokensUsed()` vs `maxContextTokens * 0.8`. There is **no pre-call estimate** of a pending request's token cost; the first oversized request cannot be anticipated.
- `AgentExecutionContext` exposes `agentModel` (AgentModel), `chatOptionsModel` (ChatOptionsModel), `tokensUsed` (long), but holds **no ApiStyle / dialect / estimator reference**.
- `CompactionContext` carries `executionContext` (AgentExecutionContext) — a potential access path to engine-managed collaborators; `DefaultAgentEngine` constructs both the executor and the compactor, so it can inject a shared estimator into both.
- Roadmap L2-16 status is ❌; L3-9 (depends on L2-16) is ❌.

### Pre-existing Gap

There is no pre-call token estimation capability and no calibration loop. The `chars/4` constant is uncalibrated and Provider-agnostic, so threshold decisions drift as Providers/tokenizers differ.

## Goals

- `ILlmDialect` gains an `estimateTokens()` contract with a sensible default baseline (nop-ai-core).
- A runtime calibration mechanism improves the baseline estimate over time using the actual `ChatUsage.promptTokens` returned by each Provider response, so the estimate converges toward the real token count.
- The calibration feed point is wired into the ReAct loop at the same place `tokensUsed` is accumulated.
- The compaction system consumes the calibrated estimator instead of the local `chars/4` static helper, so `CompactionResult.tokensBefore`/`tokensAfter` reflect calibrated counts.
- Pre-call estimation is available as a capability (any component can ask "how many tokens are these messages?") even though changing the trigger/forced-stop logic is L3-9 scope.
- All existing tests continue to pass (calibrated estimate stays in the same order of magnitude as chars/4 before calibration data arrives).

## Non-Goals

- Layer 4 forced-stop protection (the 90% hard stop) — L3-9 scope.
- Changing `shouldTriggerCompaction()` trigger semantics / thresholds — L3-9 scope.
- Full 5-layer pipeline + `ICompressionStrategy` extension point — L3-9 scope.
- A real BPE tokenizer (tiktoken / Claude tokenizer) — out of scope; calibration closes most of the gap with far less complexity.
- Persisting calibration state across sessions / process restarts — optimization candidate; first version is in-memory per session.
- Estimating tool-definition overhead separately in the request — out of scope (baseline includes a small per-message constant that absorbs it approximately).

## Scope

### In Scope

- Add `estimateTokens(List<ChatMessage>)` to `ILlmDialect` (nop-ai-core) with a default implementation in `AbstractLlmDialect` that is better than blind `chars/4` (accounts for non-null content + a small per-message overhead so empty/structural messages still register).
- A calibration component (nop-ai-agent) that maintains a smoothed correction factor per ApiStyle, fed by actual `promptTokens` vs the dialect's base estimate.
- Calibration feed point in `ReActAgentExecutor` at the existing usage-accumulation block (line ~285-289).
- Compactor (both `NoOpContextCompactor` baseline reporting and `MicroCompressionCompactor`) obtains and uses the calibrated estimator instead of the `chars/4` static helper.
- Wiring through `DefaultAgentEngine` (engine constructs/holds the estimator and shares it with executor + compactor).
- Unit tests for the dialect default estimate, the calibration convergence, and the compactor using the estimator; integration test proving calibration actually changes compaction accounting.

### Out Of Scope

- Forced-stop trigger logic and pre-call trigger conditions (L3-9).
- Persistence of the calibration factor across sessions.
- A native BPE tokenizer implementation.
- DSL configuration for calibration parameters (smoothing factor, etc.) — constructor constants are sufficient.

### Design Decisions (behavior semantics)

**Calibration algorithm (specification, not code):** The estimator maintains, per ApiStyle, a correction factor initialized to `1.0`. After each LLM response whose `ChatUsage.promptTokens` is non-null and positive:
1. Let `estimated = dialect.estimateTokens(messagesSentThisCall)`.
2. If `estimated <= 0`, skip (nothing to learn).
3. Let `observedRatio = actual / estimated`.
4. Update factor via exponential smoothing: `factor = factor * (1 - α) + observedRatio * α`, with a fixed smoothing constant `α` (e.g. 0.3). This makes one outlier insufficient to dominate, while still converging over several calls.
5. Clamp `factor` to a sane band (e.g. `[0.25, 4.0]`) so a single pathological response cannot blow up estimates.
Calibrated estimate for any message list = `dialect.estimateTokens(messages) * factor` (factor for the relevant ApiStyle; `1.0` before any data).

**Why per ApiStyle, not per model:** different API styles tokenize differently (OpenAI vs Anthropic vs Gemini). The dialect already partitions by ApiStyle, so the calibration factor naturally keys on the same dimension. Per-model granularity is an optimization candidate.

**Compactor consumes estimator, trigger stays on accumulated usage:** The compactor reports calibrated `tokensBefore`/`tokensAfter`. The compaction trigger continues to use accumulated `ctx.tokensUsed` (precise post-call). This keeps L2-16 a pure estimation capability and leaves trigger/forced-stop logic to L3-9. The estimator is nonetheless exercised every compaction, so the capability is non-hollow.

**Baseline default is not exactly chars/4:** The dialect default adds a small fixed overhead per message (to approximate role/format tokens) on top of `content.length() / 4`. This keeps it the same order of magnitude as today (existing tests stay green) while being marginally more honest. Exact constant is an implementation detail.

## Execution Plan

### Phase 1 — ILlmDialect.estimateTokens() Contract + Default Baseline

Status: completed
Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/ILlmDialect.java`, `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/AbstractLlmDialect.java`, `nop-ai/nop-ai-core/src/test/...`

- Item Types: `Proof`

- [x] Add `estimateTokens(List<ChatMessage>)` to `ILlmDialect` as a `default` method delegating to a shared baseline in `AbstractLlmDialect`
- [x] Implement the shared baseline in `AbstractLlmDialect`: sum of non-null `content.length()` divided by 4, plus a small fixed per-message overhead constant (so structural/empty messages still register > 0). Empty/null-safe list handling (empty list → 0)
- [x] The 4 existing dialects inherit the default (no per-dialect override required for L2-16; the calibration loop compensates for Provider differences)
- [x] Unit tests (nop-ai-core): (a) empty list → 0, (b) null content skipped safely, (c) monotonic — more content ⇒ strictly greater estimate, (d) estimate is deterministic (same input ⇒ same output), (e) a message with null content still contributes the per-message overhead

Exit Criteria:

- [x] `ILlmDialect` declares `estimateTokens(List<ChatMessage>)` with a default implementation
- [x] `AbstractLlmDialect` holds the shared baseline helper used by the default
- [x] Empty list returns 0; null/empty content is skipped safely (no NPE)
- [x] Estimate is monotonic and deterministic (proven by tests)
- [x] **无静默跳过**: null/empty inputs are handled explicitly and return 0, not silently swallowed
- [x] `./mvnw test -pl nop-ai/nop-ai-core -am -T 1C` passes (new + existing dialect tests)
- [x] No owner-doc update required (interface addition; nop-ai-core has no docs-for-ai owner doc for the dialect surface)
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 2 — Runtime Calibration From Provider Usage

Status: completed
Targets: new calibration component in `nop-ai/nop-ai-agent` (engine/compact package area), `ReActAgentExecutor` (usage-accumulation block at line ~285-289)

- Item Types: `Proof`

- [x] Create a calibration component that, given an `ILlmDialect` + `ApiStyle`, maintains a smoothed correction factor per ApiStyle (initialized to 1.0) and exposes a calibrated `estimateTokens(List<ChatMessage>)` = `dialect.estimateTokens(messages) * factor`
- [x] Implement the calibration update per the Design Decisions algorithm: on each response with non-null positive `promptTokens`, compute `observedRatio = actual / baseEstimate`, EMA-smooth with fixed `α`, clamp to `[0.25, 4.0]`, skip when `baseEstimate <= 0`
- [x] The component is ApiStyle-aware: it resolves which ApiStyle's factor to use (from the LLM/model config available to the engine). When ApiStyle is unknown, factor stays 1.0 (safe fallback)
- [x] Add a calibration feed point in `ReActAgentExecutor`: at the block where `promptTokens`/`completionTokens` are read (line ~285-289), feed `(messagesAtCallTime, actualPromptTokens)` to the calibrator. Messages-at-call-time = the messages that were sent in the request just responded to
- [x] Unit tests: (a) factor starts at 1.0, (b) after one over-estimate response the factor moves toward actual/estimated, (c) after repeated consistent responses the factor converges within tolerance, (d) a single pathological response is dampened by EMA and clamping, (e) `baseEstimate <= 0` skips update safely, (f) calibrated estimate equals base estimate before any calibration data

Exit Criteria:

- [x] Calibration component exposes a calibrated `estimateTokens()` that equals the baseline before any data (factor 1.0)
- [x] After feeding actual usage, the factor moves toward `actual / baseEstimate` and converges over repeated consistent samples (proven by tests with defined tolerance)
- [x] A single outlier is dampened (EMA) and clamped (factor stays within `[0.25, 4.0]`)
- [x] `baseEstimate <= 0` is handled explicitly (skip update), not silently corrupted
- [x] `ReActAgentExecutor` feeds the calibrator on every response with non-null `promptTokens`
- [x] **接线验证**: a test asserts the calibrator's `record()` is invoked from the ReAct loop after an LLM response (e.g. counter/mock-verify), and that the factor actually changes after a calibrated response
- [x] **无静默跳过**: null/zero `promptTokens` is handled explicitly (skip with no state change), not swallowed
- [x] No owner-doc update required (internal estimation mechanism)
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 3 — Wire Calibrated Estimator Into Compaction + Build

Status: completed
Targets: `NoOpContextCompactor`, `MicroCompressionCompactor`, `CompactionContext` (estimator access path), `DefaultAgentEngine` (constructs + shares estimator), existing compaction tests

- Item Types: `Fix`

- [x] Give the compactor access to the calibrated estimator: `DefaultAgentEngine` constructs the estimator and injects it into both `ReActAgentExecutor` (calibration feed) and the compactor (consumption). When no estimator is available (e.g. explicit NoOp test setups), fall back to a default estimator wrapping the OpenAI dialect with factor 1.0 — never to a silent no-op
- [x] Replace the `NoOpContextCompactor.estimateTokens()` static `chars/4` usage: `NoOpContextCompactor` and `MicroCompressionCompactor` compute `tokensBefore`/`tokensAfter` via the injected calibrated estimator
- [x] Keep `NoOpContextCompactor.INSTANCE` usable for backward-compatible test/NoOp setups (it uses the default estimator with factor 1.0)
- [x] Update existing tests (`TestMicroCompressionCompactor`, `TestCompactionDataPathway`, `TestCompactionInReActLoop`) that referenced `NoOpContextCompactor.estimateTokens()` so they assert against the estimator's output rather than the removed static helper; preserve prior coverage intent
- [x] Integration test: run a ReAct loop with a stub `IChatService` that returns known `promptTokens`, verify (a) the calibrator factor shifts, (b) a subsequent compaction's `tokensBefore`/`tokensAfter` reflect the calibrated factor (differ from raw chars/4), (c) compaction still correctly preserves system messages / first user message / recent tool results / tool_call↔tool_response pairing

Exit Criteria:

- [x] Compactor computes `tokensBefore`/`tokensAfter` from the calibrated estimator, not from a local `chars/4` constant
- [x] `DefaultAgentEngine` shares one estimator instance between the executor (feed) and the compactor (consume)
- [x] `NoOpContextCompactor.INSTANCE` remains usable (default estimator, factor 1.0) — backward compatible
- [x] No reference to a removed `chars/4` static token helper remains in compaction production code
- [x] **端到端验证**: integration test runs ReAct loop → calibration feeds → compaction triggers → `CompactionResult` token counts reflect the calibrated factor (assertably different from raw chars/4) → message invariants (system preserved, pairing intact, list size unchanged) still hold
- [x] **接线验证**: test asserts the estimator instance used by the compactor is the same one fed by the ReAct loop (shared via `DefaultAgentEngine`)
- [x] **Anti-Hollow**: calibrated factor demonstrably affects compaction accounting (integration test shows `tokensBefore` shifts after calibration data arrives); no stub/placeholder estimate path in production code
- [x] **无静默跳过**: when no estimator is injected, an explicit default estimator (factor 1.0) is used — never an empty/zero estimate path
- [x] Existing compaction tests pass (updated to estimator-based assertions)
- [x] Roadmap L2-16 updated from ❌ to ✅ (`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`)
- [x] `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-agent -am -T 1C` passes
- [x] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

- [x] `ILlmDialect.estimateTokens()` exists with a default baseline (Phase 1)
- [x] Runtime calibration converges toward actual Provider usage using `ChatUsage.promptTokens` (Phase 2)
- [x] ReAct loop feeds the calibrator on every usable response (Phase 2)
- [x] Compaction system consumes the calibrated estimator instead of `chars/4` (Phase 3)
- [x] Pre-call token estimation is available as a capability (any caller can estimate a message list)
- [x] Backward compatible: existing tests pass; `NoOpContextCompactor.INSTANCE` still usable
- [x] Roadmap L2-16 updated ❌ → ✅
- [x] No silent no-op / empty method body / placeholder in new code (anti-hollow verified by integration test)
- [x] `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-agent -am -T 1C` passes
- [x] No owner-doc update required (except roadmap status)
- [x] Independent closure audit completed and evidence recorded

## Deferred But Adjudicated

### Forced-Stop Trigger Logic (Layer 4)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Changing `shouldTriggerCompaction()` thresholds and adding the 90% forced-stop hard protection is L3-9. L2-16 delivers the estimation capability; consuming it for forced-stop is a separate, dependent work item.
- Successor Required: yes
- Successor Path: future plan for L3-9

### Calibration State Persistence

- Classification: `optimization candidate`
- Why Not Blocking Closure: Per-session in-memory calibration converges within a few LLM calls. Persisting the factor across sessions/process restarts avoids the warm-up cost but is not needed for correct estimation within a session.
- Successor Required: no

### Native BPE Tokenizer

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: A real tokenizer (tiktoken/Claude) would be most accurate but adds heavy dependencies. Runtime calibration closes most of the accuracy gap with negligible cost.
- Successor Required: no

## Non-Blocking Follow-ups

- L3-9 full 5-layer pipeline + `ICompressionStrategy` extension point + forced-stop using the estimator
- Persist calibration factor per ApiStyle across sessions
- DSL configuration for the smoothing constant / clamp band
- Per-model (not just per-ApiStyle) calibration granularity

## Closure

Status Note: All three phases completed. `ILlmDialect.estimateTokens()` provides a dialect-aware baseline (Phase 1), `CalibratedTokenEstimator` refines it via EMA-smoothed Provider usage calibration (Phase 2), and the compaction system consumes the calibrated estimator instead of chars/4 (Phase 3). The estimator is shared between the ReAct loop (feed) and the compactor (consume) via `DefaultAgentEngine`. Pre-call token estimation is available as a capability for any caller. Backward compatible — `NoOpContextCompactor.INSTANCE` remains usable with a default estimator (factor 1.0).

Closure Audit Evidence:

- Reviewer / Agent: opencode execution session (2026-06-13)
- Audit Session: same-session execution audit
- Evidence:
  - Phase 1 Exit Criteria: PASS — `ILlmDialect.estimateTokens()` default method exists (`ILlmDialect.java:148`), `AbstractLlmDialect.estimateTokensBaseline()` at line 35; empty list → 0, null-safe, monotonic, deterministic proven by `TestLlmDialectTokenEstimation` (9 tests); `./mvnw test -pl nop-ai/nop-ai-core -am` BUILD SUCCESS
  - Phase 2 Exit Criteria: PASS — `CalibratedTokenEstimator` (`CalibratedTokenEstimator.java`) with EMA α=0.3, clamp [0.25, 4.0]; factor starts 1.0, converges, outlier dampened proven by `TestCalibratedTokenEstimator` (15 tests); wiring verified by `TestTokenEstimatorWiring` (3 tests — record() called from loop, factor shifts, actual promptTokens received); `./mvnw test -pl nop-ai/nop-ai-agent -am` BUILD SUCCESS
  - Phase 3 Exit Criteria: PASS — `NoOpContextCompactor.resolveEstimator()` reads from CompactionContext, `MicroCompressionCompactor` uses it; `DefaultAgentEngine` shares estimator with executor; `TestTokenCountingIntegration` (2 tests) proves end-to-end calibration→compaction accounting differs from raw chars/4, message invariants (system preserved, pairing intact, size unchanged), same estimator instance shared; `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-agent -am` BUILD SUCCESS
  - Anti-Hollow: integration test `calibratedEstimatorAffectsCompactionAccounting` asserts estimator instance is same (assertSame), factor shifts (!= 1.0), tokensBefore differs from raw chars/4; `compactionWithCalibratedEstimatorPreservesMessageInvariants` proves system/user/tool-pairing invariants hold
  - Deferred items: Forced-Stop Trigger (L3-9), Calibration Persistence, Native BPE Tokenizer — all correctly classified as out-of-scope/optimization with non-blocking rationale
  - Roadmap: L2-16 ❌ → ✅ at `nop-ai-agent-roadmap.md:180`

Follow-up:

- L3-9: full 5-layer pipeline + `ICompressionStrategy` extension point + forced-stop using the estimator
- Persist calibration factor per ApiStyle across sessions
- DSL configuration for smoothing constant / clamp band
- Per-model (not just per-ApiStyle) calibration granularity
