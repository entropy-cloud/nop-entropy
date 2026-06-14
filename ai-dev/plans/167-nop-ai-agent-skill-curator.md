> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-4

# 167 Nop AI Agent Skill Curator (ISkillCurator + NoOpSkillCurator + LLMCurator)

> **Last Reviewed**: 2026-06-14
> **Source**: Carry-over from plan 163 (`ai-dev/plans/163-nop-ai-agent-skill-engine.md`, Deferred: "ISkillCurator / LLMCurator (L4-4)", Successor Required: yes); roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 4 L4-4; design `ai-dev/design/nop-ai-agent/skill-system-design.md` §5 (engine responsibilities: discovery + matching + assembly delivered; curation absent), `nop-ai-agent-hook-skill-engine.md` §3 (Skill engine objects)
> **Related**: Plan 163 (skill engine — ISkillProvider/SkillResolver/SkillAssemblyResult delivered; L4-4 deferred as "the consumer of the skill engine"), Plan 160 (ITalent extension-point pattern), Plan 165 (LlmCompletionJudge — LLM-backed component pattern with IChatService + config + fail-open)

## Purpose

Establish the skill curation contract — the skill-quality evaluation layer that the skill engine currently lacks. An `ISkillCurator` evaluates registered skill definitions and produces advisory curation recommendations (quality assessment, improvement suggestions). The `NoOpSkillCurator` pass-through default preserves current behavior; the `LLMCurator` functional implementation uses `IChatService` to analyze skills and propose improvements. This completes the skill-management story: Plan 163 delivered skill discovery + matching + assembly + activation; this plan adds skill quality evaluation + advisory curation.

## Current Baseline

- Plan 163 delivered the first-phase skill engine: `SkillModel` (name, goal, intentSignature, topPattern, dependencies, tags, resourceScope), `ISkillProvider`, `NoOpSkillProvider`, `FileSystemSkillProvider`, `SkillResolver` (declaration-based matching with requiredSkills fail-fast), `SkillAssemblyResult` (instructions + tool dependencies + resourceScope) — all wired into `ReActAgentExecutor.execute()` at execution setup (`consultSkills` alongside `consultTalents`, before the first LLM call).
- Design `skill-system-design.md` §5 lists four skill engine responsibilities: (1) Discovery, (2) Matching, (3) Assembly, (4) Supervision (runtime resource-scope tracking). Responsibilities 1-3 are delivered by Plan 163; responsibility 4 (supervision) is deferred (resourceScope enforcement, Plan 163 Non-Goals). **Skill curation (quality evaluation) is not among the four listed responsibilities** — it is a new capability that this plan introduces and the design doc must define.
- The module's LLM-backed component pattern is established by `LlmCompletionJudge` (Plan 165): a config object (`LlmJudgeConfig`) carries `IChatService` + system prompt + model override + fail-open fallback; the component constructs a `ChatRequest`, calls `IChatService.call()`, parses the response, and fail-opens on error/null/unparseable.
- The module's extension-point convention: interface + NoOp singleton pass-through + functional implementation + `DefaultAgentEngine` setter registration (skill provider at `setSkillProvider` (`:172`), talents at `setTalents` (`:162`), completion judge in `ReActAgentExecutor.Builder`).
- Zero curator code exists: grep for `ISkillCurator` / `SkillCurator` / `LLMCurator` across `nop-ai/` returns **zero matches** — verified absent.
- **Design gap**: no design doc defines the `ISkillCurator` contract. The only reference is roadmap §4 L4-4 ("`ISkillCurator` `LLMCurator` (技能生命周期)", dep L2-11 ✅). This plan includes a design decision (Phase 1) to establish the curation contract and updates the design doc.
- `IChatService` (`io.nop.ai.api.chat.IChatService`) + `ChatRequest` / `ChatResponse` are available via `nop-ai-api` (transitive through `nop-ai-core`). `ChatRequest.systemAndUserPrompt()` is the established factory for constructing LLM requests.

## Goals

- `ISkillCurator` interface defining the skill curation contract: given a skill registry (`Collection<SkillModel>`) and optional curation context, produce a `SkillCurationResult` carrying per-skill advisory assessments (quality rating, improvement recommendation, rationale). The curator is decoupled from `ISkillProvider` — it consumes the registry collection directly; sourcing skills from `ISkillProvider` is the engine's responsibility (Phase 3). The curator is **advisory and non-mutating** — it evaluates and recommends, never modifies skills.
- `NoOpSkillCurator` pass-through default (singleton, static factory) that returns an empty curation result — registering it (or nothing) leaves engine behavior unchanged.
- `SkillCurationResult` model carrying, at the data-contract level: per-skill advisory assessments (each assessment must identify the skill and carry a quality rating, a recommendation, and a rationale), an overall registry assessment (coverage gaps, redundancies — may be empty), and curation metadata (curator type, model used if LLM-backed, token usage, and a success/fail marker).
- `CuratorConfig` configuration object (following `LlmJudgeConfig`): must carry an `IChatService`, a curation system prompt (with sensible default), an optional model-name override, and tuning parameters (max response tokens, temperature, max skills per curation call).
- `LLMCurator` functional implementation using `IChatService` to evaluate registered skill definitions and produce structured curation recommendations, following the `LlmCompletionJudge` pattern (config + structured prompt + fail-open on LLM error/null/unparseable with **explicit failure marking**).
- `DefaultAgentEngine` registers the curator via setter (defaulting to `NoOpSkillCurator`), consistent with the skill provider pattern. The engine exposes an on-demand curation method: synchronous, no required parameters, sources skills from the registered `ISkillProvider` and passes the resulting registry to the curator (the curator itself never touches `ISkillProvider`). The curator is invokable on-demand (analytical tool, not in the ReAct loop).
- Design doc update: `skill-system-design.md` gains a section defining the curation contract (advisory semantics, non-mutating, relationship to `ISkillProvider`, quality-rating taxonomy, rejected alternatives).
- Unit tests for curation result model + NoOp contract + LLM curator (with test `IChatService`); integration test proving: NoOp default changes nothing, LLMCurator produces assessments from a test skill registry, fail-open produces explicit failure markers.
- All existing tests pass unchanged (backward compatible).

## Non-Goals

- **Automatic skill modification** — the curator only RECOMMENDS; it never writes, updates, or deletes skill definitions. Applying recommendations is a separate manual or tooling concern.
- **Runtime dynamic skill registration** — rejected per design §7.3 (Phase 2 option only).
- **Skill versioning** — design §8.3 Phase 3.
- **Execution-trace-based curation** — this plan evaluates skill *definitions* (quality, clarity, coverage); curation based on execution outcomes (which skills were effective in practice) requires session-history/telemetry infrastructure that doesn't exist yet.
- **Periodic / background curation scheduling** — the curator is invokable on-demand; scheduling is a platform concern (Actor Runtime, L4-8).
- **topPattern / intentSignature matching** — deferred from Plan 163 (requires request-side mechanism that doesn't exist).
- **Scenes / Structural layer** — deferred from Plan 163.
- **resourceScope permission enforcement** — deferred from Plan 163.
- **Delta skill registry override** — deferred from Plan 163.
- **DSL configuration in `agent.xdef`** for the curator — runtime-pluggable, consistent with how talents / skill provider / routing / compaction are excluded as DSL fields.
- **SkillActivationPolicy** — deferred from Plan 163.
- **Curation report rendering / formatting** — human-readable output from `SkillCurationResult` is a presentation concern, not a contract concern.

## Scope

### In Scope

- Design decision: define the ISkillCurator contract (advisory, non-mutating, consumes a skill registry (`Collection<SkillModel>`); sourcing skills from `ISkillProvider` is the engine's responsibility, not the curator's)
- Design decision: choose the curation invocation model (on-demand analytical tool, not in-loop)
- `ISkillCurator` interface + `NoOpSkillCurator` pass-through default
- `SkillCurationResult` model (must carry per-skill advisory assessments + registry assessment + metadata with a success/fail marker)
- `CuratorConfig` configuration object (must carry IChatService + prompt + model override + tuning parameters)
- `LLMCurator` functional implementation using IChatService
- `DefaultAgentEngine` setter registration (default NoOp) + on-demand curation invocation
- Design doc update (`skill-system-design.md` curation section)
- Unit tests + `TestSkillCuratorIntegration` integration test
- Reuse existing `*.skill.yaml` test fixtures from Plan 163 for curation testing

### Out Of Scope

- Automatic skill modification / application of recommendations
- Execution-trace-based curation (needs session history)
- Periodic background scheduling
- topPattern / intentSignature / scenes / resourceScope enforcement
- Delta skill registry override
- DSL configuration
- Skill versioning
- Curation report rendering

## Execution Plan

### Phase 1 - Design Decision + ISkillCurator Interface + NoOp Default + Curation Result Model

Status: completed
Targets: `io.nop.ai.agent.skill.ISkillCurator` (new package member), `io.nop.ai.agent.skill.NoOpSkillCurator`, `io.nop.ai.agent.skill.SkillCurationResult`, `ai-dev/design/nop-ai-agent/skill-system-design.md`

- Item Types: `Decision | Proof`

- [x] **Decision**: Define the ISkillCurator contract semantics. The curator is an **advisory, non-mutating** skill quality evaluator. Given a skill registry (from `ISkillProvider`), it produces per-skill assessments (quality rating: `WELL_DEFINED` / `NEEDS_IMPROVEMENT` / `REDUNDANT`; recommendation text; rationale). It reads skill definitions and evaluates their clarity, completeness, and coverage — it never modifies them. Rationale: (a) consistent with the `LlmCompletionJudge` advisory pattern ("裁决是'建议'不是'命令'"), (b) consistent with the skill engine's static-loading design (§7.3 — skills don't change at runtime). **Rejected**: mutating curator that auto-applies recommendations (rejected because write-back semantics need their own safety design and conflict with static loading). **Rejected**: execution-trace-based curation (rejected because session-history infrastructure doesn't exist yet). Record the rationale in the design doc.
- [x] **Decision**: Choose the curation invocation model. The curator is an **on-demand analytical tool**, not an in-loop ReAct component. It is registered with `DefaultAgentEngine` (setter, defaulting to `NoOpSkillCurator`) and invokable on-demand, but is NOT invoked during `ReActAgentExecutor.execute()`. Rationale: curation is a meta-level concern (skill quality assessment), not a per-execution concern (skill activation). **Rejected**: post-execution lifecycle hook invocation (rejected because it would couple every execution to curation overhead and conflate two concerns). Record rationale in the design doc.
- [x] Define `ISkillCurator` interface: a curation contract that takes a skill registry (`Collection<SkillModel>`) and optional curation context, and returns a `SkillCurationResult`. The optional curation context is reserved for future use (e.g., execution-trace-based curation); v1 curators ignore it / treat it as a no-op. The curator is decoupled from `ISkillProvider` — the engine (Phase 3) is responsible for sourcing skills from `ISkillProvider` and passing the resulting collection to the curator. No matching/activation logic — that's the resolver's job.
- [x] Implement `NoOpSkillCurator` pass-through default (singleton, static factory `noOp()`): returns an empty `SkillCurationResult` with zero assessments and a "no-op" success marker. Consistent with `NoOpSkillProvider.noOp()` / `NoOpCompletionJudge.noOp()`.
- [x] Define `SkillCurationResult` model carrying: per-skill assessments (skill name, quality rating enum, recommendation, rationale), overall registry assessment (coverage gaps, redundancies — may be empty), and curation metadata (curator type label, model name if LLM-backed, token usage, and a **success/fail marker** that distinguishes "curation succeeded with zero skills" from "curation failed"). An empty result with success marker is valid; an empty result with fail marker signals an error. Both are explicit, never produced by swallowing an exception.
- [x] Update `skill-system-design.md` with a new section (e.g., §5.5 "Skill 策展 / Curation") defining: advisory semantics, non-mutating contract, relationship to `ISkillProvider` (reads from, doesn't modify), quality-rating taxonomy, invocation model (on-demand, not in-loop), and the rejected alternatives.
- [x] Unit tests: `TestNoOpSkillCurator` verifies it returns an empty/non-null curation result with "no-op" success marker and is a singleton; `TestSkillCurationResult` verifies model construction + round-trip + `empty()` factory + success/fail marker distinction. (The interface contract itself is compile-time enforced; no separate interface-reflection test is needed — the behavioral NoOp + result-model tests cover the contract surface.)

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] Both decisions are recorded with rationale (contract semantics; invocation model)
- [x] `ISkillCurator` interface defines the advisory curation contract (reads skills, returns assessments, never mutates)
- [x] `NoOpSkillCurator` is a singleton pass-through that returns an empty curation result with success marker
- [x] `SkillCurationResult` carries per-skill assessments + registry assessment + metadata with success/fail marker, with an explicit `empty()` factory
- [x] `skill-system-design.md` has a new curation section defining the contract, advisory semantics, quality-rating taxonomy, invocation model, and rejected alternatives (this serves as the owner-doc marker for the curation contract)
- [x] Unit tests verify: NoOp returns empty + success marker, result model round-trip + success/fail distinction
- [x] **端到端验证** N/A: contract + model definition phase, no pipeline to verify end-to-end
- [x] **接线验证** N/A: no inter-component wiring yet
- [x] **无静默跳过**: `SkillCurationResult.empty()` factory produces an explicit success marker (not a silent null/default), and the success/fail marker is a distinct explicit value rather than the product of swallowed logic — both markers are constructible and distinguishable in tests
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 2 - LLMCurator Functional Implementation + CuratorConfig

Status: completed
Targets: `io.nop.ai.agent.skill.CuratorConfig`, `io.nop.ai.agent.skill.LLMCurator`

- Item Types: `Decision | Proof`

- [x] Implement `CuratorConfig` configuration object following the `LlmJudgeConfig` pattern: carries `IChatService` (required, non-null), curation system prompt (with a sensible default that instructs the LLM to evaluate skill definitions for clarity, completeness, coverage, and redundancy, and to output assessments in a defined structured format), optional model-name override, max response tokens (default ~1000 to allow per-skill assessments), temperature (default 0.0 for deterministic evaluation), and max skills per curation call (default ~20 to bound prompt size). Validate constructor inputs (non-null chatService, positive maxTokens, temperature in [0.0, 2.0], non-negative maxSkillsPerCall).
- [x] **Decision (output format contract)**: the curator and its default system prompt must agree on a single structured output format. The format is a behavioral requirement (not a literal prompt/schema in this plan): the LLM response must carry, per assessed skill, a name reference plus the assessment fields (quality rating, recommendation, rationale) that `SkillCurationResult` requires, and must be machine-parseable into those assessment objects. The concrete format choice (e.g., JSON with a per-skill object array) and the parsing contract (expected fields, error handling for missing/malformed entries) are **implementation decisions recorded in the design doc** (`skill-system-design.md` curation section — extended in Phase 2, see Phase 2 Exit Criteria), so that the parser and the default prompt are guaranteed to agree. The plan does not prescribe the literal format; it requires that format + parser + prompt be internally consistent and reproducible.
- [x] Implement `LLMCurator` functional implementation:
  - Accepts a `CuratorConfig` at construction (fail-fast `IllegalArgumentException` on null config)
  - Implements the `ISkillCurator` contract: given a skill registry (`Collection<SkillModel>`), if the registry is empty/null, return `SkillCurationResult.empty()` with success marker **without calling the LLM** (no tokens wasted on empty input)
  - Constructs a curation prompt: system prompt (curator role + output format instructions) + user message (listing each skill's name, goal, intentSignature, dependencies, tags, resourceScope, asking the LLM to evaluate quality and suggest improvements per skill)
  - Bounds the prompt size: if the registry exceeds `maxSkillsPerCall`, curate in batches (sequential calls) and merge results into a single `SkillCurationResult`. **Partial-failure semantics**: if some batches succeed and others fail, successful batches contribute their per-skill assessments and failed batches contribute a fail marker for that batch; the merged result's overall marker is "fail" if any batch failed, with the successful partial assessments still retained. The curator never silently drops successful results nor silently swallows failures.
  - Calls `IChatService.call()` with the constructed `ChatRequest` (using `ChatRequest.systemAndUserPrompt()`)
  - **Fail-open semantics with explicit marking**: on LLM call `RuntimeException`, null response, unsuccessful response, null/empty message content, or unparseable content → return a `SkillCurationResult` with a **"curation failed" fail marker** in metadata (NOT a silent empty success — the fail marker must distinguish "LLM error" from "zero skills assessed"). Log a warning with the error detail. This is consistent with `LlmCompletionJudge` fail-open but adds the explicit success/fail distinction.
  - Parses the LLM response into structured per-skill assessments (skill name, quality rating, recommendation, rationale), using the output-format contract established above (format + parser + default prompt must agree; format choice recorded in the design doc)
- [x] Static factory methods: `LLMCurator.llm(CuratorConfig)` and `LLMCurator.llm(IChatService)` (zero-tuning defaults via `CuratorConfig.defaults(chatService)`, following `LlmCompletionJudge.llm()`)
- [x] Unit tests with a test `IChatService` (mock/stub that returns canned responses):
  - (a) Normal curation: test `IChatService` returns a valid structured curation response → parsed into correct per-skill assessments for each skill in the registry
  - (b) Empty registry → empty success result, **no LLM call made** (verify call count = 0)
  - (c) LLM call throws `RuntimeException` → fail-open result with "curation failed" fail marker
  - (d) LLM returns null → fail-open result with fail marker
  - (e) LLM returns unsuccessful response → fail-open result with fail marker
  - (f) LLM returns unparseable content → fail-open result with fail marker
  - (g) Registry exceeds `maxSkillsPerCall` → batched curation (multiple calls), results merged into single result
  - (h) Token usage from successful response(s) is accumulated in curation metadata
  - (i) Static factory `LLMCurator.llm(IChatService)` produces a working curator with default config
  - (j) Partial batch failure: a registry exceeding `maxSkillsPerCall` where the test `IChatService` succeeds on some batch calls and fails on others → merged result retains the successful batches' assessments, has an overall fail marker, and does not silently drop successful results nor swallow failures

Exit Criteria:

- [x] `CuratorConfig` exists with IChatService + prompt + model + maxTokens + temperature + maxSkillsPerCall, with input validation and sensible defaults
- [x] `LLMCurator` evaluates skill definitions via IChatService and produces structured curation assessments
- [x] Empty registry returns empty success result without calling the LLM (call count = 0 verified)
- [x] Fail-open on LLM error/null/unparseable produces an explicit "curation failed" fail marker (distinguishable from "zero skills" success)
- [x] Batched curation works when registry exceeds maxSkillsPerCall (multiple calls merged)
- [x] Token usage is accumulated in curation metadata on successful calls
- [x] Static factory methods produce working curators
- [x] Unit tests cover all 10 scenarios (a-j)
- [x] **Test canned responses match the parser format**: the canned `IChatService` responses used in scenarios (a)/(g)/(i) are valid instances of the output-format contract recorded in the design doc, so parser + default prompt + tests are internally consistent and reproducible
- [x] **端到端验证** N/A: LLMCurator tested in isolation with test IChatService; E2E with engine wiring comes in Phase 3
- [x] **接线验证** N/A: engine wiring comes in Phase 3
- [x] **无静默跳过**: LLM error produces an explicit fail-open result with fail marker (NOT a silent empty success return); empty registry returns empty explicitly without LLM call (documented zero-input behavior, not a swallowed error); null config/chatService throws `IllegalArgumentException` at construction
- [x] **Owner-doc update**: extend the `skill-system-design.md` curation section (added in Phase 1) with the LLM-specific contract — chosen output format + parsing contract + fail-open marker semantics — so the doc reflects Phase 2's behavioral surface (Phase 1 scoped the advisory contract + invocation model only; the LLM format/marker contract is a Phase 2 addition). Record remaining implementation decisions in `ai-dev/logs/`
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 3 - Engine Wiring + Integration Tests + Backward Compatibility

Status: completed
Targets: `io.nop.ai.agent.engine.DefaultAgentEngine`, `io.nop.ai.agent.skill.TestSkillCuratorIntegration` (new)

- Item Types: `Proof`

- [x] `DefaultAgentEngine` gains a curator field with a setter (defaulting to `NoOpSkillCurator` via null-check-default in the setter, consistent with the existing skill-provider pattern — `setSkillProvider` at `:172`). Expose an on-demand curation method with the following behavioral contract: it takes no required parameters (it uses the registered `ISkillProvider` and curator; an optional curation context may be accepted), sources the skill registry from the registered `ISkillProvider`, invokes the curator with that registry, and returns the `SkillCurationResult` **synchronously** (not `CompletableFuture` — curation is an on-demand analytical tool, not part of the async `execute()` ReAct loop). If no `ISkillProvider` is registered (defaults to `NoOpSkillProvider`), curation returns an empty success result (zero skills to assess).
- [x] `TestSkillCuratorIntegration` (integration):
  - With the default (`NoOpSkillCurator` + default `NoOpSkillProvider`): invoking curation returns an empty success result, no LLM call is made, all existing behavior unchanged (backward compatibility)
  - With an `LLMCurator` backed by a test `IChatService` and a `FileSystemSkillProvider` pointing at existing test fixtures (from Plan 163 `_vfs/skills/`): invoking curation produces per-skill assessments for each fixture skill, with correct quality ratings and recommendations parsed from the test `IChatService` response
  - With an empty skill registry (`NoOpSkillProvider`) and an `LLMCurator`: invoking curation returns an empty success result without calling the LLM
  - With an `LLMCurator` whose `IChatService` throws: invoking curation returns a fail-open result with "curation failed" fail marker (engine does not crash, no exception propagates to the caller)
- [x] All existing tests pass (backward compatible)

Exit Criteria:

- [x] `DefaultAgentEngine` exposes curator registration via setter (default `NoOpSkillCurator`) and on-demand curation invocation sourcing skills from `ISkillProvider`
- [x] `TestSkillCuratorIntegration` verifies: NoOp default returns empty success (backward compat); LLMCurator produces assessments from test fixtures; empty registry returns empty without LLM call; LLM error returns fail-open result with fail marker
- [x] All existing tests pass (backward compatible)
- [x] **端到端验证**: the integration test exercises the full path: skill registry (`FileSystemSkillProvider` loading `*.skill.yaml`) → `DefaultAgentEngine` curation invocation → `LLMCurator` → `IChatService.call()` → response parsing → `SkillCurationResult` with per-skill assessments
- [x] **接线验证**: `TestSkillCuratorIntegration` proves `DefaultAgentEngine` actually invokes the registered curator when curation is requested, and the result reflects the `IChatService` response — not just that the types exist
- [x] **无静默跳过**: NoOp default returns an explicit empty success result (not null/silent); LLM error returns fail-open result with fail marker (not swallowed); empty registry returns empty explicitly without LLM call
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] No owner-doc update required (design doc updated in Phase 1; record wiring decisions in `ai-dev/logs/`)
- [x] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `ISkillCurator` interface + `NoOpSkillCurator` pass-through exist in `io.nop.ai.agent.skill`
- [x] `SkillCurationResult` model carries per-skill assessments + registry assessment + metadata with success/fail marker
- [x] `CuratorConfig` carries IChatService + prompt + model + tuning parameters with input validation
- [x] `LLMCurator` evaluates skills via `IChatService` and produces structured curation recommendations
- [x] Fail-open semantics: LLM error/null/unparseable produces explicit "curation failed" fail marker (distinguishable from "zero skills" success)
- [x] `DefaultAgentEngine` registers the curator (default NoOp) and exposes on-demand curation invocation sourcing from `ISkillProvider`
- [x] Backward compatible: all existing tests pass with the default (`NoOpSkillCurator`)
- [x] Roadmap §4 L4-4 updated to ✅
- [x] `skill-system-design.md` has a curation section defining the advisory contract
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes (this also transitively compiles the module; a separate `./mvnw compile` gate is not required)
- [x] Code-style / checkstyle check passes for the new curator code (e.g., `./mvnw checkstyle:check -pl nop-ai/nop-ai-agent` or the project's equivalent style gate) — imports grouped, no style violations
- [x] `node ai-dev/tools/check-plan-checklist.mjs 167-nop-ai-agent-skill-curator.md --strict` exits 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` exits 0
- [x] No silent no-op, empty method body, or swallowed exception in new code
- [x] Independent closure audit completed and evidence recorded

## Deferred But Adjudicated

### Execution-Trace-Based Curation

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Curation based on execution outcomes (which skills were effective in practice) requires session-history/telemetry infrastructure (usage tracking, outcome correlation) that doesn't exist yet. Definition-quality curation (assessing skill definitions for clarity/completeness/coverage) is the foundational capability and is fully deliverable without execution traces.
- Successor Required: `yes`
- Successor Path: Execution-trace curation plan (TBD — depends on session-history/telemetry infrastructure)

### Automatic Recommendation Application

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: The curator is advisory (recommends, never modifies). Auto-applying recommendations (updating `*.skill.yaml` files or the registry) is a separate tooling concern with write-back semantics that need their own safety design and conflict with the static-loading design (§7.3).
- Successor Required: `no`

### Periodic / Background Curation Scheduling

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: The curator is invokable on-demand. Scheduling periodic curation is a platform concern (Actor Runtime, L4-8) layered above the curation contract.
- Successor Required: `no`

### topPattern / intentSignature / Scenes / resourceScope Enforcement

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: All deferred from Plan 163. The curator evaluates existing skill fields (name, goal, dependencies, tags, resourceScope). When these matching/enforcement mechanisms are delivered, the curator can evaluate them too.
- Successor Required: `yes`
- Successor Path: Phase-2/3 skill matching plan (TBD — same successor as Plan 163's matching deferrals)

### Skill Versioning

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Design §8.3 Phase 3. Versioning is orthogonal to curation quality assessment.
- Successor Required: `no`

### DSL Configuration in agent.xdef

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: The curator is runtime-pluggable, consistent with how talents / skill provider / routing / compaction are excluded as DSL fields.
- Successor Required: `no`

### Curation Report Rendering / Formatting

- Classification: `optimization candidate`
- Why Not Blocking Closure: Human-readable rendering of `SkillCurationResult` (e.g., markdown report, dashboard) is a presentation-layer concern; the structured result is the contract surface.
- Successor Required: `no`

## Non-Blocking Follow-ups

- Curation result persistence (storing curation history for trend analysis across invocations)
- Curation prompt tuning / experimentation with different evaluation criteria
- Integration with skill registry management tooling (when a management UI/API exists)

## Closure

Status Note: Plan 167 delivers the skill curation contract — the advisory skill-quality evaluation layer. ISkillCurator interface + NoOpSkillCurator pass-through + SkillCurationResult model (Phase 1), CuratorConfig + LLMCurator functional implementation with batched curation and fail-open markers (Phase 2), and DefaultAgentEngine wiring with on-demand curateSkills() (Phase 3) are all landed and tested. The design doc (skill-system-design.md §5.5) defines the advisory, non-mutating curation contract. This plan can be closed because all in-scope items are delivered, all tests pass (53 curator tests + 774 module tests, 0 failures), backward compatibility is preserved (NoOp default), and no in-scope work remains.

Completed: 2026-06-14

Closure Audit Evidence:

- Reviewer / Agent: Independent closure audit subagent (explore agent, task ses_13cf157bdffepMsNoS8TGJUYaB)
- Evidence:
  - **Phase 1 Exit Criteria**: all 9 items PASS — `ISkillCurator.java:42` defines `curate(Collection<SkillModel>)`; `NoOpSkillCurator.java:18` singleton `noOp()` returns `empty()`; `SkillCurationResult` carries assessments + gaps + redundancies + metadata with success/fail marker; `SkillQualityRating` has exactly 3 values; design doc §5.5 (`skill-system-design.md:197-222`) defines advisory semantics, taxonomy, invocation model, rejected alternatives
  - **Phase 2 Exit Criteria**: all 13 items PASS — `CuratorConfig` carries all 6 fields with validation; `LLMCurator` implements empty-without-LLM-call, batched curation, partial-failure merge, fail-open with explicit markers on all 6 failure modes (RuntimeException/null/unsuccessful/null-message/empty/unparseable), token accumulation, static factories; 30 unit tests cover all 10 scenarios (a-j) + config validation + JSON extraction edge cases
  - **Phase 3 Exit Criteria**: all 8 items PASS — `DefaultAgentEngine:69` field defaults to NoOp; `:190` setter null-safe; `:209` `curateSkills()` synchronous sourcing from provider; curator NOT in execute()/resolveExecutor() path; integration test proves full E2E chain (FileSystemSkillProvider → engine → LLMCurator → IChatService → parse → result)
  - **Anti-Hollow check**: call path `engine.curateSkills()` → `skillProvider.getSkills()` → `skillCurator.curate()` → `chatService.call()` → parse → result is connected at runtime (integration test asserts `callCount=1`); `scan-hollow-implementations.mjs --module nop-ai-agent --severity high` exit 0, 0 findings at all severity levels
  - **No Silent No-Op check**: every catch block in LLMCurator logs `LOG.warn` AND returns `SkillCurationResult.failed(...)` — no exception is swallowed; `continue` statements in `extractAssessments` skip malformed per-skill entries within a batch (documented behavior, tested by `malformedAssessmentEntriesAreSkippedNotFailing`); NoOp default returns explicit empty success (not null/silent)
  - **Wiring check**: `TestSkillCuratorIntegration` proves `DefaultAgentEngine.curateSkills()` actually invokes the registered curator and the result reflects the IChatService response (not just types existing)
  - **Backward compatibility**: full module test suite `./mvnw test -pl nop-ai/nop-ai-agent -T 1C` — 774 tests, 0 failures
  - `node ai-dev/tools/check-plan-checklist.mjs 167-nop-ai-agent-skill-curator.md --strict` exit 0
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` exit 0
  - Deferred items classification: all 7 deferred items are `out-of-scope improvement` with explicit non-blocking rationale; no in-scope live defect or contract drift downgraded to follow-up

Follow-up:

- Execution-trace-based curation (successor required — depends on session-history/telemetry infrastructure)
- Curation result persistence / prompt tuning / management tooling integration (non-blocking optimizations)
- topPattern / intentSignature matching evaluation (same successor as Plan 163's matching deferrals)
