> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: skill-engine

# 163 Nop AI Agent Skill Engine (Phase 1: Scheduling Layer + Declaration Matching + ReAct Integration)

> **Last Reviewed**: 2026-06-14
> **Source**: Carry-over from plan 160 (`ai-dev/plans/160-nop-ai-agent-talent.md`, Deferred: "Skill System (ISkillProvider / SkillResolver / *.skill.yaml)", Successor Required: yes); roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §2.2 (Hook/Skill 引擎 ❌ 未开始); design `ai-dev/design/nop-ai-agent/skill-system-design.md` §4-5, §8.1 (第一阶段), `ai-dev/design/nop-ai-agent/nop-ai-agent-hook-skill-engine.md` §3
> **Related**: Plan 160 (ITalent extension point — sibling interface+pass-through+wiring pattern), Plan 134 (engine extension points), Plan 146 (ReActAgentExecutor Builder pattern)

## Purpose

Build the first-phase Skill engine: structured capability discovery (`SkillModel` Scheduling layer), file-system registry (`*.skill.yaml`), declaration-based matching (`SkillResolver`), assembly result injection, and ReAct loop integration. The `NoOpSkillProvider` pass-through default discovers zero skills, preserving current behavior for agents without skill declarations (the default path). This activates the inert `availableSkills` / `requiredSkills` fields on `AgentModel` and provides the Skill engine infrastructure that L4-4 (`ISkillCurator` / `LLMCurator`) will consume.

## Current Baseline

- `AgentModel` (`_gen/_AgentModel.java:24,94`) declares `availableSkills` and `requiredSkills` as `Set<String>` — these are **inert**: no engine resolves them, no matching occurs, no skill assembly is injected. An agent declaring `requiredSkills: [skill-x]` currently runs without error even if skill-x doesn't exist.
- `ITalent` / `NoOpTalent` (plan 160, `io.nop.ai.agent.talent`) established the extension-point convention for this module: interface + pass-through singleton + `ReActAgentExecutor` Builder registration + execution-setup consultation + existing `IToolManager.loadTool()` pipeline for dynamic tools.
- `IAgentLifecycleHook` + `IHookRegistry` + `DefaultHookRegistry` + `NoOpHookRegistry` (L2-12, plan 150) deliver the lifecycle Hook engine — 12 lifecycle points wired into `ReActAgentExecutor` at `:688-693`. Roadmap §4 L2-12 is ✅, but §2.2 "Hook/Skill 引擎" is a combined row still showing ❌ because the Skill portion is not yet delivered.
- `ReActAgentExecutor.consultTalents()` (`:853-888`) is the proven integration point: it iterates registered talents, gates via `isSupported`, collects instructions + tool names, resolves tools via `toolManager.loadTool()`, and injects instructions via `injectSystemInstruction()`. The skill engine needs a parallel consultation path.
- `DefaultAgentEngine` (`:58,154,361`) holds extension fields with setters and wires them into the executor Builder via `resolveExecutor()` composition — the established wiring pattern.
- Zero skill engine code exists: grep for `ISkillProvider` / `SkillResolver` / `SkillActivationPolicy` / `SkillAssemblyResult` / `*.skill.yaml` in `nop-ai/nop-ai-agent/src/main/java` returns **zero matches** — verified absent.
- Design docs are ready: `skill-system-design.md` (full DSL + engine design, §8.1 defines phase-1 scope), `nop-ai-agent-hook-skill-engine.md` §3 (Skill engine objects: `ISkillProvider`, `SkillResolver`, `SkillActivationPolicy`, `SkillAssemblyResult`).
- `AgentExecutionContext` (`engine/AgentExecutionContext.java`) carries `agentModel` + `messages` + `metadata` — sufficient context for skill declaration matching (no request-side `topPattern` exists yet).

## Goals

- `SkillModel` representing the Scheduling-layer skill definition per design §4.1 first-phase subset: `name`, `goal`, `intentSignature`, `topPattern`, `dependencies`, `tags`, `resourceScope`. Loadable from `*.skill.yaml`.
- `ISkillProvider` interface defining the skill discovery contract (returns the set of all registered skills keyed by name).
- `NoOpSkillProvider` pass-through default that discovers zero skills — registering it (or nothing) leaves engine behavior unchanged.
- `FileSystemSkillProvider` that scans a configured directory for `*.skill.yaml` files and loads them into `SkillModel` instances.
- `SkillResolver` that matches an `AgentModel`'s `availableSkills` / `requiredSkills` against the provider using declaration filtering (design §5.2 Phase 1):
  - `requiredSkills`: all must be present in the registry — fail-fast with `NopAiAgentException` if any is missing; all found ones are force-activated.
  - `availableSkills`: found ones are activated, missing ones are silently ignored (no error).
  - Activated set = `requiredSkills ∪ (availableSkills ∩ registry)`.
- `SkillAssemblyResult` carrying assembled instruction fragments (skill goals), tool-name dependencies, and resourceScope from all activated skills.
- `ReActAgentExecutor` consults the skill resolver at execution setup (alongside `consultTalents`), merging skill instructions into the system-prompt context and skill tool-name dependencies into the active tool definitions through the existing `IToolManager.loadTool()` + access-check pipeline.
- `DefaultAgentEngine` wires the skill provider into the executor Builder via composition (no new constructor chain).
- Unit tests for model loading + provider contract + resolver matching + assembly; integration test (`TestSkillEngineInReActLoop`) proving the ReAct loop actually resolves skills and that `NoOpSkillProvider` / empty default changes nothing.
- All existing tests pass unchanged (backward compatible).

## Non-Goals

- `topPattern` coarse filtering (design §5.2 Phase 2 matching; note: §8.1 line 255 erroneously lists it in the first phase — §8.1 will be reconciled, see Closure Gates) — requires a request-side `topPattern` mechanism (agent DSL field or request inference) that does not exist yet; deferred to a successor plan.
- `intentSignature` exact matching (design §5.2 Phase 3 matching, §8.2) — deferred to phase 2.
- `scenes` / Structural layer (design §4.1 scenes, §8.2) — deferred to phase 2.
- `resourceScope` permission overlay enforcement (`ACTIVE_SCOPE = SKILL_SCOPE ∩ AGENT_PERMISSIONS`, design §6.3, §8.2) — resourceScope is collected in the assembly result for tracing but not enforced against the permission system in phase 1.
- Skill-Tool dependency existence check at assembly time (design §8.2) — deferred.
- `ISkillCurator` / `LLMCurator` (L4-4) — the consumer of the skill engine; its own roadmap item.
- Delta override for the skill registry (design §8.3) — deferred.
- Runtime dynamic skill registration (rejected per design §7.3) — not planned.
- Semantic vector matching for `intentSignature` (design §8.3) — deferred.
- DSL configuration in `agent.xdef` for the skill provider — runtime-pluggable, consistent with how talents / routing / compaction are excluded as DSL fields.
- Prebuilt talents (file/web/data/cli/lsp from plan 160) — separate concern.
- `SkillActivationPolicy` (design §3 lists it as a recommended object) — phase-1 matching is purely declaration-based; an explicit policy object is deferred until `topPattern` / `intentSignature` matching requires pluggable strategies.
- `expectedInputs` / `expectedOutputs` (`SchemaRef`, design §4.1 lines 89-90) — not needed for declaration-based matching; deferred until intentSignature matching (Phase 3) requires input/output schema validation.

## Scope

### In Scope

- `SkillModel` (Scheduling-layer fields per design §4.1 first-phase subset)
- `ISkillProvider` interface + `NoOpSkillProvider` pass-through default
- `FileSystemSkillProvider` loading `*.skill.yaml`
- `SkillResolver` with declaration-based matching (requiredSkills fail-fast + availableSkills activation)
- `SkillAssemblyResult` (instruction fragments, tool-name dependencies, resourceScope)
- `ReActAgentExecutor` skill consultation at execution setup + `DefaultAgentEngine` wiring
- Unit tests + `TestSkillEngineInReActLoop` integration test
- At least one sample `*.skill.yaml` test fixture

### Out Of Scope

- topPattern / intentSignature matching (request-side mechanism doesn't exist)
- scenes / Structural layer
- resourceScope permission enforcement
- SkillActivationPolicy (declaration matching needs no pluggable strategy yet)
- ISkillCurator / LLMCurator (L4-4)
- Delta registry override
- Dynamic runtime registration
- agent.xdef DSL for skill provider

## Execution Plan

### Phase 1 - Skill Model + Provider Interface + Pass-Through Default

Status: completed
Targets: `io.nop.ai.agent.skill.SkillModel` (new package), `io.nop.ai.agent.skill.ISkillProvider`, `io.nop.ai.agent.skill.NoOpSkillProvider`

- Item Types: `Decision | Proof`

- [x] **Decision**: Choose the `SkillModel` representation approach. Two options: (a) hand-written POJO loaded via Nop's YAML resource loader (simpler, no codegen, can migrate later); (b) xdef-generated model following the `_AgentModel` pattern (platform-native, but touches the xdef/codegen area). Record the decision rationale. Prefer (a) for phase 1 unless the model needs validation infrastructure that xdef provides for free. **Decided (a)**: hand-written POJO. The phase-1 model needs no validation infrastructure, and avoiding the xdef/codegen area keeps the diff minimal and the model trivially loadable from YAML via `JsonTool.parseYaml`. Can migrate to xdef-generated model later if validation/round-trip needs emerge.
- [x] Define `SkillModel` with the Scheduling-layer fields from design §4.1 first-phase subset: `name` (required, unique key), `goal` (skill description, injected into prompt as instruction fragment), `intentSignature` (Java type `List<String>`, normalized from YAML scalar or list; stored but not used in phase-1 matching), `topPattern` (enum: PREPARE | ACT | VERIFY | MANAGE | RETRIEVE | TRANSFORM, stored for future use), `dependencies` (list of tool/skill names — tool names are used for assembly), `tags` (set), `resourceScope` (set: MEMORY | LOCAL_FS | CODEBASE | NETWORK | CREDENTIALS, collected in assembly but not enforced in phase 1).
- [x] **Decision**: Skill tool dependencies use the same contract as talent tools (plan 160 Phase 1 decision): `dependencies` referencing tool names are resolved through the existing `IToolManager.loadTool()` pipeline (reusing schema + access-check logic), not a new tool type. Record the rationale (consistency with plan 160, design §7.4 — Skill only declares tool names, tool definitions remain in `tool.xdef`). **Decided**: reuse `IToolManager.loadTool()` — documented in `SkillModel` / `ISkillProvider` Javadoc; the phase-3 integration resolves skill dependency tool names through the exact same `loadTool()` + skip-with-warning path as `consultTalents`.
- [x] Define `ISkillProvider` interface: a discovery contract that returns all registered skills (keyed by name). No matching logic here — matching is the resolver's job.
- [x] Implement `NoOpSkillProvider` pass-through default (singleton, static factory): discovers zero skills, returns an empty collection. Consistent with `NoOpTalent.noOp()` / `NoOpContentGuardrail.noOp()`.
- [x] Unit tests: `SkillModel` field round-trip (construct, set fields, read back); `TestISkillProvider` confirms the interface declares the discovery contract; `TestNoOpSkillProvider` verifies it returns an empty/non-null skill collection and is a singleton via the static factory.

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `SkillModel` exists in `io.nop.ai.agent.skill` with the Scheduling-layer fields from design §4.1
- [x] Both decisions are recorded with rationale (model representation approach; tool-name resolution contract)
- [x] `ISkillProvider` interface defines the discovery contract (returns all registered skills keyed by name)
- [x] `NoOpSkillProvider` is a singleton pass-through that discovers zero skills
- [x] Unit tests verify model round-trip + provider contract + NoOp returns empty
- [x] **端到端验证** N/A: model/interface-definition phase, no pipeline to verify end-to-end
- [x] **接线验证** N/A: no inter-component wiring yet
- [x] **无静默跳过** N/A: no branches or conditionals in model/interface definitions
- [x] No owner-doc update required (design doc §4.1 already specifies the fields; decisions recorded in logs)
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 2 - FileSystemSkillProvider + SkillResolver + Assembly

Status: completed
Targets: `io.nop.ai.agent.skill.FileSystemSkillProvider`, `io.nop.ai.agent.skill.SkillResolver`, `io.nop.ai.agent.skill.SkillAssemblyResult`

- Item Types: `Proof`

- [x] Implement `FileSystemSkillProvider` that loads `*.skill.yaml` files from a configured base directory via Nop's `VirtualFileSystem` resource scanning (the established project pattern). Each file is parsed into a `SkillModel`. Provider caches the loaded set (skills don't change at runtime per design §7.3). Missing directory → empty set (not an error — consistent with zero-config default). Malformed YAML → fail fast with a clear error.
- [x] Create at least one sample `*.skill.yaml` test fixture under `src/test/resources/_vfs/skills/` (aligned with the project's VFS-mounted test resource convention) for testing (with realistic fields: name, goal, dependencies, tags, resourceScope).
- [x] Implement `SkillResolver` taking an `ISkillProvider` and resolving skills for a given `AgentModel`:
  - Load all skills from the provider → registry (name → SkillModel map)
    - null `availableSkills`/`requiredSkills` are treated as empty sets (the `AgentModel` fields are nullable)
  - For each `requiredSkill`: if not in the registry, throw `NopAiAgentException` with a clear message naming the missing skill and the agent — fail-fast, agent does not execute (design §5.3)
  - Activated set = `requiredSkills ∪ (availableSkills ∩ registry keys)`
  - Build `SkillAssemblyResult` from activated skills: collect each skill's `goal` (instruction fragment), tool-name `dependencies`, and `resourceScope`
- [x] Implement `SkillAssemblyResult` carrying: assembled instruction fragments (list of skill goals), tool-name dependencies (merged set), resourceScope (merged set), activated skill names (for tracing/logging).
- [x] Unit tests:
  - `TestFileSystemSkillProvider`: loads sample `*.skill.yaml` fixtures, verifies field round-trip from YAML; missing directory returns empty set; malformed YAML fails fast
  - `TestSkillResolver`: (a) availableSkills ∩ registry → correct activation; (b) missing requiredSkill → `NopAiAgentException`; (c) requiredSkills all present → all force-activated; (d) empty availableSkills + empty requiredSkills → empty assembly; (e) skill with dependencies → tool names collected in assembly result; (f) `NoOpSkillProvider` → empty assembly; (g) requiredSkill and availableSkill overlap → no duplicate activation

Exit Criteria:

- [x] `FileSystemSkillProvider` loads `*.skill.yaml` from a configured directory into `SkillModel` instances
- [x] Missing skill directory returns an empty set (zero-config default, not an error)
- [x] `SkillResolver` produces the correct activated set from `availableSkills` / `requiredSkills` using declaration filtering
- [x] `SkillResolver` throws `NopAiAgentException` when a `requiredSkill` is not found (fail-fast, clear message naming the skill and agent)
- [x] `SkillAssemblyResult` carries instruction fragments + tool-name dependencies + resourceScope + activated names
- [x] At least one sample `*.skill.yaml` test fixture exists under `src/test/resources/_vfs/skills/`
- [x] Tests cover: normal matching, missing required skill (fail-fast), empty declarations, NoOp provider, dependency collection, overlap deduplication
- [x] **端到端验证** N/A: resolver/provider phase, integration with ReAct comes in Phase 3
- [x] **接线验证** N/A: no ReAct wiring yet
- [x] **无静默跳过**: missing requiredSkill throws an exception (not silent skip); missing directory returns empty explicitly (documented zero-config behavior, not a swallowed error); malformed YAML fails fast
- [x] No owner-doc update required (design doc §5.2-5.4 already specifies matching + assembly; decisions recorded in logs)
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 3 - ReAct Integration + Engine Wiring + E2E Tests

Status: completed
Targets: `io.nop.ai.agent.engine.ReActAgentExecutor`, `io.nop.ai.agent.engine.DefaultAgentEngine`, `io.nop.ai.agent.skill.TestSkillEngineInReActLoop` (new)

- Item Types: `Proof`

- [x] `ReActAgentExecutor.Builder` gains a skill-provider field (defaulting to `NoOpSkillProvider` via null-check-default in `build()`, consistent with the existing single-instance extension convention — `contentGuardrail` at `:243`, `hookRegistry` at `:240`).
- [x] `ReActAgentExecutor.execute()` resolves skills **once at execution setup** (before the first LLM call, alongside `consultTalents` at `:265`): call `SkillResolver` (constructed from the registered `ISkillProvider`; no separate resolver field on the Builder) with the agent model, obtain `SkillAssemblyResult`, then merge:
  - Skill instruction fragments → system-prompt context via the existing `injectSystemInstruction` helper (additive to agent prompt and talent instructions)
  - Skill tool-name dependencies → resolved through `IToolManager.loadTool()` and merged into the active tool definitions (additive to agent-declared tools and talent tools, same access-check pipeline — no parallel tool type). If `loadTool()` returns null for a skill-declared tool name, it is skipped with a warning (same pattern as `consultTalents` at `:876-882`); the existence check is deferred per Non-Goals.
  - resourceScope → collected in `SkillAssemblyResult` and logged at DEBUG level for observability (not enforced against the permission system in phase 1 — deferred)
- [x] `DefaultAgentEngine` wires the skill provider into the executor Builder via its existing composition flow (`setSkillProvider` setter + `.skillProvider(...)` in `resolveExecutor`, no new constructor chain — same pattern as `setTalents` at `:154,361`).
- [x] `TestSkillEngineInReActLoop` (integration):
  - With the default (`NoOpSkillProvider` / no skills): the ReAct loop runs unchanged — no extra tools, no extra instruction, zero skill injection (backward compatibility)
  - With a `FileSystemSkillProvider` pointing at test fixtures + an agent declaring `availableSkills` referencing those fixtures: skill goals reach the LLM system context, skill tool-name dependencies appear in the tool definitions sent to the LLM, and are invocable through the normal tool-execution path (access-check pipeline applies)
  - With an agent declaring `requiredSkills` referencing a missing skill: the engine fails fast with `NopAiAgentException` before any LLM call
  - With both talents and skills registered: both contribute additively (instructions + tools merged, no conflict)

Exit Criteria:

- [x] `ReActAgentExecutor.Builder` exposes skill-provider registration, defaulting to `NoOpSkillProvider`
- [x] `execute()` resolves skills once at execution setup; requiredSkills fail-fast + assembly merge happen before the first LLM call
- [x] Skill instruction fragments are merged into the system-prompt context (additive, non-destructive)
- [x] Skill tool-name dependencies are resolved via `IToolManager.loadTool()` and merged into the active tool definitions (additive, same access-check pipeline as agent + talent tools)
- [x] `DefaultAgentEngine` passes the skill provider to the executor Builder via composition
- [x] `TestSkillEngineInReActLoop` verifies: default changes nothing (backward compat); availableSkills inject instruction + tools; missing requiredSkills fails fast before LLM call; talent + skill both contribute additively; skill tools are invocable via the normal path
- [x] All existing tests pass (backward compatible)
- [x] **端到端验证**: the integration test exercises a full ReAct turn where skill-assembled tools/instructions flow from declaration → resolver → merge → LLM request → tool execution
- [x] **接线验证**: `TestSkillEngineInReActLoop` proves `execute()` actually calls the `SkillResolver`, and for activated skills the assembly result (instructions + tools) reaches `chatService.call()` — not just that the types exist
- [x] **无静默跳过**: missing requiredSkill throws before any LLM call (not a silent skip); no skills found produces an explicit empty assembly (not a swallowed error); missing skill directory is a documented zero-config default, not a silent `continue`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] No owner-doc update required (design docs already specify skill semantics; record the integration decisions in `ai-dev/logs/`)
- [x] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `SkillModel` (Scheduling-layer fields) + `ISkillProvider` + `NoOpSkillProvider` exist in `io.nop.ai.agent.skill`
- [x] `FileSystemSkillProvider` loads `*.skill.yaml` from a configured directory
- [x] `SkillResolver` matches `availableSkills` / `requiredSkills` via declaration filtering with requiredSkills fail-fast
- [x] `SkillAssemblyResult` carries instruction fragments + tool-name dependencies + resourceScope
- [x] `ReActAgentExecutor` consults the skill resolver at execution setup and merges instructions + tools into the active request (alongside talents)
- [x] Skill-provided tools flow through the existing `IToolManager` + access-check pipeline (no parallel tool type)
- [x] Backward compatible: all existing tests pass with the default (`NoOpSkillProvider`)
- [x] Roadmap §2.2 "Hook/Skill 引擎" updated to ✅: Hook engine delivered via Plan 150 (L2-12 ✅ — IAgentLifecycleHook + HookRegistry + 12 lifecycle points), Skill engine delivered via Plan 163. With both portions delivered, the combined row is now ✅.
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] No silent no-op, empty method body, or swallowed exception in new code
- [x] Design doc §8.1 reconciliation: §8.1 (line 255) says "匹配只做声明过滤 + topPattern 粗筛" for the first phase, but §5.2 (lines 159-163) places topPattern in Phase 2. This plan follows §5.2 (topPattern deferred) because request-side topPattern does not exist yet. Update §8.1 line 255 to remove "topPattern 粗筛" from the first phase, aligning with §5.2.
- [x] Independent closure audit completed and evidence recorded

## Closure

Status Note: The first-phase Skill engine is fully delivered and wired into the ReAct loop. All 3 phases completed: (1) SkillModel + ISkillProvider + NoOpSkillProvider; (2) FileSystemSkillProvider + SkillResolver + SkillAssemblyResult; (3) ReActAgentExecutor consultSkills integration + DefaultAgentEngine wiring + TestSkillEngineInReActLoop E2E tests. The default NoOpSkillProvider preserves existing behavior (backward compatible — 572 tests pass). Missing requiredSkills fail-fast before any LLM call. All deferred items are adjudicated as non-blocking (topPattern/intentSignature matching, scenes layer, resourceScope enforcement, ISkillCurator, Delta override, SkillActivationPolicy) with clear successor paths.
Completed: 2026-06-14

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (session: ses_13daeb878ffebn6Xm69Wljrt5u, task: "Independent closure audit Plan 163")
- Audit Session: ses_13daeb878ffebn6Xm69Wljrt5u
- Evidence:
  - Phase 1 Exit Criteria: PASS — `SkillModel.java:34-40` (7 Scheduling-layer fields), `SkillTopPattern.java:11-18` (6 values), `SkillResourceScope.java:10-16` (5 values), `ISkillProvider.java:26` (`Collection<SkillModel> getSkills()`), `NoOpSkillProvider.java:16,23,28-30` (singleton + `noOp()` factory + empty collection)
  - Phase 2 Exit Criteria: PASS — `FileSystemSkillProvider.java:82-93` (VFS scan), `:83-86` (missing dir → empty), `:133-136` (malformed YAML → NopAiAgentException); `SkillResolver.java:71-80` (requiredSkills fail-fast with skill+agent name); `SkillAssemblyResult.java:31-34` (4 carriers); fixtures under `_vfs/skills/`
  - Phase 3 Exit Criteria: PASS — `ReActAgentExecutor.java:233-236` (Builder method), `:279-280` (`consultSkills` called before while loop / before `chatService.call()` at `:334`), `:939-940` (SkillResolver invoked at runtime), `:946-954` (loadTool + skip-with-warning), `:956-959` (injectSystemInstruction); `DefaultAgentEngine.java:167-169` (setter) + `:375` (wiring); `TestSkillEngineInReActLoop` (7 tests, all PASS)
  - Anti-Hollow Check: PASS — `consultSkills` genuinely called in `execute()` at `:280` (traced from execute → consultSkills → new SkillResolver().resolve()); missing requiredSkill throws at `SkillResolver.java:74` before any LLM call (integration test asserts `chatCallCount == 0`); zero empty method bodies / silent continue / swallowed exceptions in skill package (grep-verified)
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/163-nop-ai-agent-skill-engine.md --strict` exit code 0 (no unchecked items + Closure Evidence written)
  - `scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high`: 14 pre-existing findings (all from other subsystems — engine/session/memory/hook), 0 from Plan 163 skill code
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`: BUILD SUCCESS (572 tests, 0 failures, 0 errors); 47 new skill tests
  - Deferred 项分类检查: all 10 deferred items classified as `out-of-scope improvement` or `optimization candidate` with explicit `Why Not Blocking Closure` rationale; no in-scope live defect downgraded

Follow-up:

- no remaining plan-owned work (all deferred items have successor paths recorded in `Deferred But Adjudicated`)

## Deferred But Adjudicated

### topPattern Coarse Filtering (Design §5.2 Phase 2)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Requires a request-side `topPattern` mechanism (agent DSL field or request inference) that does not exist yet; phase-1 declaration matching is sufficient to activate the skill engine.
- Successor Required: `yes`
- Successor Path: Phase-2 skill matching plan (TBD)

### intentSignature Exact Matching (Design §5.2 Phase 3, §8.2)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Phase-1 declaration matching covers the core use case; intent matching is an enhancement.
- Successor Required: `yes`
- Successor Path: Phase-2/3 skill matching plan (TBD)

### scenes / Structural Layer (Design §4.1, §8.2)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Structural layer is an independent modeling concern layered above the Scheduling layer.
- Successor Required: `yes`
- Successor Path: Structural-layer plan (TBD)

### resourceScope Permission Enforcement (Design §6.3, §8.2)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: resourceScope is collected for tracing; enforcement requires the permission system to be integrated, which is a separate concern.
- Successor Required: `yes`
- Successor Path: Permission-overlay plan (TBD)

### Skill-Tool Dependency Existence Check (Design §8.2)

- Classification: `optimization candidate`
- Why Not Blocking Closure: Skills follow the same skip-with-warning pattern as talents when a tool is not found; a strict existence check is a hardening improvement.
- Successor Required: `no`

### ISkillCurator / LLMCurator (L4-4)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: L4-4 is the consumer of the skill engine, a separate roadmap item with its own plan.
- Successor Required: `yes`
- Successor Path: L4-4 plan (TBD)

### Delta Override for Skill Registry (Design §8.3)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Delta override is a customization concern that applies after the base registry mechanism is proven.
- Successor Required: `yes`
- Successor Path: Delta-skill plan (TBD)

### Semantic Vector Matching for intentSignature (Design §8.3)

- Classification: `optimization candidate`
- Why Not Blocking Closure: Semantic matching is an advanced matching strategy; declaration matching is the foundation.
- Successor Required: `no`

### agent.xdef DSL Configuration for Skill Provider

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: The skill provider is runtime-pluggable, consistent with how talents / routing / compaction are excluded as DSL fields.
- Successor Required: `no`

### SkillActivationPolicy (Design §3)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Phase-1 matching is purely declaration-based; an explicit policy object is deferred until `topPattern` / `intentSignature` matching requires pluggable strategies.
- Successor Required: `yes`
- Successor Path: Phase-2 matching plan (TBD)

## Follow-up handled by 167-nop-ai-agent-skill-curator.md

Plan 167 picks up L4-4 (`ISkillCurator` / `LLMCurator`) — the skill curation/evaluation successor work item deferred in this plan's `Deferred But Adjudicated` §"ISkillCurator / LLMCurator (L4-4)" (Successor Required: yes). It establishes the advisory skill-quality evaluation contract (`ISkillCurator` + `NoOpSkillCurator` pass-through default + `LLMCurator` functional implementation using `IChatService`), following the same extension-point convention as this plan (interface + NoOp singleton + functional impl + `DefaultAgentEngine` setter registration). The `NoOpSkillCurator` default preserves backward compatibility (zero-config behavior unchanged).
