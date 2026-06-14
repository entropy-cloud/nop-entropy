# 160 ITalent Dynamic-Admission Extension Point + NoOpTalent + ReAct Integration

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L2-11
> **Last Reviewed**: 2026-06-13
> **Source**: `ai-dev/design/nop-ai-agent/nop-ai-agent-llm-layer.md` §5, `ai-dev/design/nop-ai-agent/skill-system-design.md` §2.1, `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 2
> **Related**: Plan 154 (IModelRouter — sibling L2 interface+pass-through+wiring pattern), Plan 146 (ReActAgentExecutor Builder pattern), Plan 150 (lifecycle hook system)

## Purpose

Define the `ITalent` dynamic-admission extension point and integrate it into the ReAct execution loop, establishing the contract for context-dependent activation of behaviors and tool sets (Solon AI "Talent" pattern). The `NoOpTalent` pass-through default never activates, so by default the engine injects zero dynamic tools/instructions and current behavior is preserved exactly. This unblocks L4-4 (`ISkillCurator` / `LLMCurator`), which depends on L2-11.

## Current Baseline

- `ReActAgentExecutor.Builder` (`ReActAgentExecutor.java:128-238`) holds 14 extension fields, each with a setter and a null-check-default in `build()` (e.g. `modelRouter` at :199/:233, `contentGuardrail` at :194/:232, `toolCallRepairer` at :184/:230). There is **no talent field**.
- `ReActAgentExecutor.execute()` (`:241`) calls `buildToolDefinitions(agentModel)` at `:252` / impl `:808-828`, which builds `ChatToolDefinition` **only** from `agentModel.getTools()` resolved through `IToolManager.loadTool(name)`. No external tool source exists.
- The agent system prompt is built in `DefaultAgentEngine` (`:250-256`) from `agentModel.getPrompt().getSource()` and added as a single `ChatSystemMessage` before `execute()`. There is no dynamic instruction source.
- `DefaultAgentEngine.resolveExecutor()` (`:296-313`) assembles `ReActAgentExecutor` via the Builder without any talent configuration.
- All sibling L2 extensions (`IModelRouter`/`PassThroughModelRouter`, `IContentGuardrail`/`NoOpContentGuardrail`, `IToolCallRepairer`/`NoOpToolCallRepairer`, `IContextCompactor`/`NoOpContextCompactor`) follow an identical convention: interface + pass-through singleton in a dedicated subpackage (`io.nop.ai.agent.<ext>`), wired into the Builder, and covered by both a unit test and an `...InReActLoop` integration test.
- Design doc `nop-ai-agent-llm-layer.md` §5.2 defines the `ITalent` contract: `isSupported(ctx)` (admission gate), `onAttach(ctx)` (activation callback), `getInstruction(ctx)` (dynamic system-prompt fragment), `getTools(ctx)` (dynamic tool set).
- Design doc §5.3 maps `getTools` to "从 `tool.xdef` 注册表按条件选择" — i.e. talents select **existing registry tools**, they do not invent a parallel tool type. §5.4 lists prebuilt talents (file/web/data/cli/lsp) as examples — these are functional implementations, **not** this plan.
- Design doc `skill-system-design.md` §2.1 clarifies Skill (structured capability definition, DSL+engine) vs ITalent (runtime admission, Layer 2 execution extension) — they are complementary, not overlapping. The Skill system (`ISkillProvider`/`SkillResolver`) is out of scope.
- Roadmap L2-11 status is ❌; L4-4 (`ISkillCurator`/`LLMCurator`) is ❌ and depends on L2-11.
- Grep for `ITalent` / `ISkillCurator` in `nop-ai/nop-ai-agent/src/main/java` returns **zero matches** — verified absent.

## Goals

- `ITalent` interface in `io.nop.ai.agent.talent` defining the dynamic-admission contract per design §5.2: admission gate, activation callback, dynamic instruction fragment, dynamic tool set.
- `NoOpTalent` pass-through implementation (singleton via static factory) whose admission gate never activates — so the default engine injects zero dynamic tools/instructions and behavior is unchanged.
- `ReActAgentExecutor` consults registered talents at execution setup: for each talent whose admission gate passes, fire the activation callback, then merge its dynamic instruction(s) into the system-prompt context and its dynamic tool set into the active tool definitions sent to the LLM.
- `ReActAgentExecutor.Builder` gains a way to register zero or more talents (defaulting to none), consistent with the existing extension convention.
- `DefaultAgentEngine` exposes talent registration on the executor path (composition via Builder, no new constructor chain).
- Talent-provided tools flow through the existing `IToolManager.loadTool()` + `toolAccessChecker` pipeline (schemas + per-invocation access checks apply uniformly — no parallel tool type or bypass).
- Unit tests for the interface contract + pass-through, plus an `...InReActLoop` integration test proving the ReAct loop actually consults talents (wiring), that a supporting talent injects tools/instructions, and that `NoOpTalent`/empty default changes nothing.
- All existing tests pass unchanged (backward compatible).

## Non-Goals

- Prebuilt talents (file / web / data / cli / lsp from design §5.4) — functional implementations that consume the interface, future work.
- Smart/LLM-driven talent selection and per-iteration re-evaluation of `isSupported` — talents are consulted once at execution setup; adaptive activation is a future optimization.
- The Skill system (`ISkillProvider`, `SkillResolver`, `SkillActivationPolicy`, `*.skill.yaml` registry) — separate concern per `skill-system-design.md`; Skill describes "what a capability is", ITalent decides "is it active now".
- `ISkillCurator` / `LLMCurator` (L4-4) — the consumer of this extension point; its own roadmap item.
- A new `ToolSpec` type — talent-provided tools integrate with the existing `ChatToolDefinition` / `IToolManager` pipeline, not a parallel representation.
- DSL configuration for talents in `agent.xdef` (talents are runtime-pluggable execution extensions, consistent with how `agent.xdef` already excludes routing/compaction as DSL fields).
- Permission-overlay logic specific to talents beyond what the existing `toolAccessChecker` already enforces at invocation time.

## Scope

### In Scope

- `ITalent` interface definition (admission gate + activation callback + dynamic instruction + dynamic tool set)
- `NoOpTalent` pass-through default (never activates)
- `ReActAgentExecutor` integration: Builder talent registration + execution-setup consultation + instruction/tool merge
- `DefaultAgentEngine` wiring via Builder
- Unit tests (interface, pass-through) + integration test (`TestTalentInReActLoop`)

### Out Of Scope

- Prebuilt talents (file/web/data/cli/lsp)
- Skill system (`ISkillProvider`/`SkillResolver`/`*.skill.yaml`)
- L4-4 `ISkillCurator`/`LLMCurator`
- Smart / LLM-driven / per-iteration talent selection
- New `ToolSpec` type
- DSL talent configuration in `agent.xdef`

## Execution Plan

### Phase 1 - ITalent Interface

Status: completed
Targets: `io.nop.ai.agent.talent.ITalent` (new package)

- Item Types: `Proof | Decision`

- [x] Decide the tool-return contract: per design §5.3 ("从 `tool.xdef` 注册表按条件选择"), talent-provided tools reference **existing registry tools** and are resolved through the existing `IToolManager.loadTool()` pipeline (reusing schema + access-check logic), not a new `ToolSpec` type. Record the decision rationale.
- [x] Define `ITalent` with the four behavioral contracts from design §5.2:
  - admission gate on `AgentExecutionContext` (returns whether this talent activates for the current execution)
  - activation callback on `AgentExecutionContext` (invoked once after the gate passes, before instruction/tools are collected)
  - dynamic system-prompt instruction fragment for `AgentExecutionContext` (nullable/empty when the talent contributes no instruction)
  - dynamic tool set for `AgentExecutionContext` (empty when the talent contributes no tools)
- [x] `TestITalent`: structural/contract test confirming the interface declares the four contract methods with the agreed signatures.

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ITalent` interface exists in `io.nop.ai.agent.talent` with the four behavioral contracts
- [x] The tool-return contract decision is recorded (registry-resolved tools via `IToolManager`, no new `ToolSpec` type)
- [x] `TestITalent` confirms the declared contract surface
- [x] **端到端验证** N/A: interface-definition phase, no pipeline to verify end-to-end
- [x] **接线验证** N/A: no inter-component wiring yet
- [x] **无静默跳过** N/A: no branches or conditionals in a pure interface definition
- [x] No owner-doc update required (design doc §5.2 already specifies the contract; the tool-return mapping to `IToolManager` will be recorded as a design decision)
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 2 - NoOpTalent Pass-Through and Tests

Status: completed
Targets: `io.nop.ai.agent.talent.NoOpTalent`

- Item Types: `Proof`

- [x] Implement `NoOpTalent` (singleton, `final`, private constructor, static factory method) consistent with `NoOpContentGuardrail.noOp()` / `PassThroughModelRouter.passThrough()`
- [x] `NoOpTalent` admission gate always returns **false** — it never activates, so `onAttach`/`getInstruction`/`getTools` are never consulted by the engine on the default path
- [x] `TestNoOpTalent`: verify the admission gate returns false for any context; verify it is a singleton via the static factory; verify the inactive methods are consistent with a never-activated talent

Exit Criteria:

- [x] `NoOpTalent` admission gate always returns false
- [x] `NoOpTalent` is a singleton exposed via a static factory method
- [x] `TestNoOpTalent` verifies the gate is always false and the singleton contract
- [x] **无静默跳过**: not applicable — a never-activating gate is the *defined* pass-through semantics, not a placeholder hiding future work; `NoOpTalent`'s methods are reachable through any custom/test talent path
- [x] No owner-doc update required
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 3 - ReActAgentExecutor Integration and Tests

Status: completed
Targets: `io.nop.ai.agent.engine.ReActAgentExecutor`, `io.nop.ai.agent.engine.DefaultAgentEngine`, `io.nop.ai.agent.talent.TestTalentInReActLoop` (new)

- Item Types: `Proof`

- [x] `ReActAgentExecutor.Builder` gains talent registration accepting **zero or more** talents (design §5.4 implies multiple coexist), defaulting to none — consistent with the existing extension convention
- [x] `ReActAgentExecutor.execute()` consults talents **once at execution setup** (before the first LLM call): for each registered talent whose admission gate returns true, fire its activation callback, then collect its dynamic instruction fragment and its dynamic tool set
- [x] Active talents' dynamic tools are resolved through the existing `IToolManager.loadTool()` pipeline and merged into the tool definitions built by `buildToolDefinitions()` (talent tools are additive to the agent's declared tools)
- [x] Active talents' dynamic instruction fragments are incorporated into the system-prompt context visible to the LLM (additive, without replacing the agent's declared prompt), established before the first LLM call
- [x] `DefaultAgentEngine` wires the talent set into the executor Builder via its existing composition flow (no new constructor chain)
- [x] `TestTalentInReActLoop` (integration):
  - with the default (no talents / `NoOpTalent`), the ReAct loop runs unchanged — no extra tools, no extra instruction, zero dynamic injection (backward compatibility)
  - with a test-double talent whose gate returns true: its activation callback is invoked exactly once, its instruction fragment reaches the LLM system context, and its declared tools appear in the tool definitions sent to the LLM
  - with a test-double talent whose gate returns false: none of its instructions/tools are injected and its activation callback is never invoked
  - a custom talent can contribute additional tools that are invocable through the normal tool-execution path (subject to the existing access-check pipeline)

Exit Criteria:

- [x] `ReActAgentExecutor.Builder` exposes talent registration (zero or more), defaulting to none
- [x] `ReActAgentExecutor.execute()` consults talents once at execution setup; gating + activation-callback + instruction/tool merge all happen before the first LLM call
- [x] Active talents' tools are resolved via `IToolManager.loadTool()` and merged into the active tool definitions (additive to agent-declared tools)
- [x] Active talents' instruction fragments are merged into the system-prompt context (additive, non-destructive)
- [x] `DefaultAgentEngine` passes the talent set to the executor Builder via composition
- [x] `TestTalentInReActLoop` verifies: default changes nothing (backward compat); supporting talent injects instruction + tools; non-supporting talent injects nothing and its activation callback is never called; talent-provided tools are invocable via the normal path
- [x] All existing tests pass (backward compatible)
- [x] **端到端验证**: existing e2e test `TestEndToEndReAct` passes without modification; the integration test exercises a full ReAct turn where a supporting talent's tools/instruction flow from admission → merge → LLM request → tool execution
- [x] **接线验证**: `TestTalentInReActLoop` proves `ReActAgentExecutor.execute()` actually calls each talent's admission gate, and for supporting talents calls the activation callback + collects instruction/tools that reach `chatService.call()` — not just that the types exist
- [x] **无静默跳过**: an inactive talent is excluded only because its gate explicitly returned false (a real decision), never via an empty method body, swallowed exception, or `continue`; talent consultation produces an explicit (possibly empty) merge, not a silent no-op path
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] No owner-doc update required (design docs already specify talent semantics; record the tool-return mapping + consultation-timing decisions in `ai-dev/logs/`)
- [x] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `ITalent` interface with the four behavioral contracts (admission gate, activation callback, dynamic instruction, dynamic tool set) exists in `io.nop.ai.agent.talent`
- [x] `NoOpTalent` pass-through singleton never activates
- [x] `ReActAgentExecutor` consults talents once at execution setup and merges instruction + tools into the active request
- [x] Talent-provided tools flow through the existing `IToolManager` + access-check pipeline (no parallel tool type)
- [x] Backward compatible: all existing tests pass with the default (no talents)
- [x] Roadmap L2-11 updated from ❌ to ✅ (and note that L4-4 is unblocked)
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] No silent no-op, empty method body, or swallowed exception in new code
- [x] No owner-doc update required (or, if the design doc §5 needs the tool-return/consultation-timing clarification, it is updated)
- [x] Independent closure audit completed and evidence recorded

## Deferred But Adjudicated

### Prebuilt Talents (file / web / data / cli / lsp)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: These are functional implementations that *consume* the `ITalent` interface (design §5.4). This plan only establishes the interface contract, pass-through default, and ReAct wiring. Each prebuilt talent has its own admission condition and tool set and is independently valuable.
- Successor Required: yes
- Successor Path: future plan(s) for individual prebuilt talents or a talent-pack

### Smart / Per-Iteration Talent Selection

- Classification: `optimization candidate`
- Why Not Blocking Closure: Talents are consulted once at execution setup, which is correct and minimal for the contract. Re-evaluating `isSupported` each ReAct iteration, or using an LLM to decide activation, is an optimization that does not affect the contract surface.
- Successor Required: no

### Skill System (ISkillProvider / SkillResolver / *.skill.yaml)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Per `skill-system-design.md` §2.1, Skill (structured capability definition) and ITalent (runtime admission) are complementary layers. The Skill system is a separate engine-layer concern with its own design doc; it is not a dependency of the ITalent contract.
- Successor Required: yes
- Successor Path: future plan for the Skill engine (references `nop-ai-agent-hook-skill-engine.md`)

### ISkillCurator / LLMCurator (L4-4)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: L4-4 is the Layer-4 consumer that depends on L2-11. It is unblocked by this plan but is its own roadmap item with its own dependency set.
- Successor Required: yes
- Successor Path: future L4-4 plan

### DSL Talent Configuration in agent.xdef

- Classification: `optimization candidate`
- Why Not Blocking Closure: Talents are runtime-pluggable execution extensions, consistent with how `agent.xdef` already excludes routing/compaction as DSL fields. DSL configuration can be added later without changing the interface.
- Successor Required: no

## Non-Blocking Follow-ups

- Prebuilt talents (file / web / data / cli / lsp) and a talent-pack
- Smart / LLM-driven / per-iteration talent selection
- Skill engine (`ISkillProvider` / `SkillResolver` / `SkillActivationPolicy` / `*.skill.yaml` registry)
- L4-4 `ISkillCurator` / `LLMCurator`
- DSL configuration for talent registration in `agent.xdef`

## Closure

Status Note: All three Phases completed and independently audited. `ITalent` interface with the four behavioral contracts, `NoOpTalent` pass-through singleton (never activates), and `ReActAgentExecutor` integration (one-time consultation at execution setup, additive instruction/tool merge, tools flow through the existing `IToolManager` + access-check pipeline) are all landed and wired. Backward compatible — full module test suite passes with the default (no talents). Roadmap L2-11 updated to ✅, which unblocks L4-4 (`ISkillCurator`/`LLMCurator`). Deferred items are prebuilt talents / smart selection / skill engine / L4-4 / DSL config — all non-blocking, none are in-scope live defects.
Completed: 2026-06-13

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (fresh session task id `ses_13e4fcf66ffe00wIDnX6rZYp3L`)
- Audit Session: `ses_13e4fcf66ffe00wIDnX6rZYp3L`
- Evidence:
  - [PASS] `ITalent` interface: `ITalent.java:26` declares `isSupported`/`onAttach`/`getInstruction`/`getTools` in `io.nop.ai.agent.talent`
  - [PASS] `NoOpTalent` pass-through: `NoOpTalent.java` — `final`, private ctor, `noOp()` singleton, `isSupported`→false
  - [PASS] Builder talent registration: `ReActAgentExecutor.java:220` setter; `build()` passes to ctor; ctor `List.copyOf`/`List.of()` default
  - [PASS] `execute()` consults once at setup: `:265 consultTalents` before the while loop
  - [PASS] Gate + callback + merge: `consultTalents` — `isSupported` gate, `onAttach` only for supporting, instruction merge via `injectSystemInstruction` (additive), tool merge via `toolManager.loadTool` → `toolDefs`
  - [PASS] Talent tools use SAME pipeline: `loadTool` (same as agent tools); invocation passes `toolAccessChecker` + `permissionProvider` + `pathAccessChecker` (no bypass)
  - [PASS] `DefaultAgentEngine.setTalents` (no new ctor); `resolveExecutor` wires `.talents(talents)`
  - [PASS] `TestTalentInReActLoop` wiring: default unchanged (backward compat); supporting talent injects instruction + tool, `onAttach` exactly once; non-supporting injects nothing, `onAttach` zero; talent-provided tool invocable via `toolManager.callTool`
  - [PASS] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS (exit 0)
  - [PASS] `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` exit 0 (no unchecked items, Closure Evidence written)
  - [PASS] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` exit 0, 0 high/critical findings in new code
  - [PASS] Roadmap L2-11 = ✅ (`nop-ai-agent-roadmap.md:174`); L4-4 still ❌ (`:216`, unblocked but not done — correct)
  - [PASS] Deferred items: all 5 classified `out-of-scope-improvement` / `optimization-candidate`; no downgraded live defect
  - Anti-Hollow trace (call-chain connectivity): `execute()` → `consultTalents` → `isSupported` → `onAttach` → `getInstruction` → `injectSystemInstruction` → `ChatSystemMessage` added; → `getTools` → `toolManager.loadTool` → `toolDefs.add` → `buildChatOptions` → `chatService.call`. No empty bodies, no `continue`-skip, no swallowed exception in `consultTalents`. `NoOpTalent.onAttach` empty body is the defined pass-through (gate never passes).

Follow-up:

- Only non-blocking: prebuilt talents (file/web/data/cli/lsp), smart / per-iteration talent selection, skill engine (`ISkillProvider`/`SkillResolver`/`*.skill.yaml`), L4-4 `ISkillCurator`/`LLMCurator` (now unblocked), DSL talent config in `agent.xdef`. No in-scope live defect remains.

## Follow-up handled by 163-nop-ai-agent-skill-engine.md

Plan 163 picks up the Skill engine (`ISkillProvider` / `SkillResolver` / `SkillAssemblyResult` / `*.skill.yaml` registry) — the successor work item from this plan's `Deferred But Adjudicated` §"Skill System (ISkillProvider / SkillResolver / *.skill.yaml)" (Successor Required: yes). It implements the first-phase skill engine per design `skill-system-design.md` §8.1: `SkillModel` (Scheduling layer), file-system registry, declaration-based matching, assembly result, and ReAct loop integration. This activates the inert `availableSkills` / `requiredSkills` fields on `AgentModel` and unblocks L4-4 (`ISkillCurator` / `LLMCurator`).
