# 170 Sub-Agent Path Permission Inheritance

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: sec-4.4 (path-permission-inheritance)
> Last Reviewed: 2026-06-14
> Source: Carry-over from plan 169 (`ai-dev/plans/169-nop-ai-agent-sub-agent-permission-inheritance.md`, Deferred "Path permission inheritance", Successor Required: yes); design `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §4.4 ("文件权限 = 父权限 ∩ 子配置")
> Related: Plan 169 (tool-level inheritance — `ParentConstrainedToolAccessChecker`, delivered ✅), Plan 141 (L1-9 `IPathAccessChecker` + `DefaultPathAccessChecker`), Plan 168 (call-agent tool — fork+exec)

## Purpose

Enforce the second clause of design §4.4: "文件权限 = 父权限 ∩ 子配置". Plan 169 delivered tool-level inheritance (set intersection) but explicitly deferred path-level inheritance as a carry-over (Successor Required: yes). This plan closes that gap: when a parent agent invokes a sub-agent via call-agent, the sub-agent's file access is confined to the parent agent's effective (clamped) path scope. A sub-agent that attempts to access a path outside the parent's allowed roots is denied (fail-closed), closing the incomplete security contract left by plan 169.

## Current Baseline

- **Plan 169 delivered tool-level inheritance (✅)**: `ParentPermissionConstraint` (immutable, carries `allowedTools` Set + parent agent name + parent session ID, metadata key `"parentPermissionConstraint"`), `ParentConstrainedToolAccessChecker` (wraps `IToolAccessChecker`, fail-closed set intersection). `DefaultAgentEngine.resolveEffectiveToolAccessChecker()` (`DefaultAgentEngine.java:~470`) reads the constraint from request metadata and wraps the engine's `toolAccessChecker`; `doExecute()` (`DefaultAgentEngine.java:~421-422`) passes the effective checker to `resolveExecutor()`. `AgentToolExecuteContext.allowedTools` (plan 169, additive field) carries the current agent's effective tool set; `ReActAgentExecutor.computeEffectiveAllowedTools()` (`ReActAgentExecutor.java:~936`) computes `incomingParentConstraint ∩ agentModel.getTools()`.
- **Path-level inheritance is NOT enforced (the gap)**: `DefaultAgentEngine.resolveExecutor()` (`DefaultAgentEngine.java:~492-502`) ALWAYS passes the engine's own `pathAccessChecker` field to every sub-agent executor (`.pathAccessChecker(pathAccessChecker)` at line ~502). There is NO analog of `resolveEffectiveToolAccessChecker` for paths. A sub-agent receives the identical global path checker with NO parent path constraint applied — a sub-agent can access any path the global checker allows, regardless of where the parent agent operates.
- **`IPathAccessChecker` / `DefaultPathAccessChecker` is a GLOBAL STATIC deny-list** — NOT a per-agent rule system. `DefaultPathAccessChecker.checkAccess()` (`DefaultPathAccessChecker.java:~43`) denies: path traversal (`..`), sensitive prefixes (`~/.ssh/`, `~/.aws/`, `/etc/`, `/proc/`, etc.), and sensitive filenames (`.env`, `id_rsa`, etc.). It has NO per-agent state, NO configurable rules, NO glob-pattern model, and takes no agent-specific input beyond `AgentExecutionContext`. **Correction of the carry-over premise**: the carry-over description claimed "IPathAccessChecker (L1-8) ✅ with glob-pattern rule matching already implemented". This is factually inaccurate — grep for `glob|pathRules|PathRule` in `nop-ai-agent/src/main/java` confirms zero per-agent path-rule infrastructure. Design doc §4.4 (line 154) refers to "IPathAccessChecker 的 glob 规则", but these do not exist in the live implementation; this is a design-doc-aspirational vs. reality gap.
- **Path checking entry point**: `ReActAgentExecutor.checkPathAccess()` (`ReActAgentExecutor.java:~836-868`) extracts path-like argument keys (`PATH_ARG_KEYS` = path, file, filePath, filename, directory, dir, destination, output, input, source, target, cwd — line ~831-834) from a tool call's arguments, then calls `pathAccessChecker.checkAccess(pathValue, ctx)`. On denial it publishes a `PATH_ACCESS_DENIED` event and writes a `AuditDecision.DENY` audit entry (lines ~858-864). This is the exact point where a parent path constraint must take effect.
- **`workDir` is NOT a live per-agent value (must be fixed by this plan)**: `AgentToolExecuteContext.workDir` (`AgentToolExecuteContext.java:~31`, type `File`) exists as a field, but in main code `AgentToolExecuteContext` is constructed at exactly ONE site — `ReActAgentExecutor.java:~472-483` — where `workDir` is HARDCODED to `new File(".")` (line ~473), the JVM's current working directory, identical for every agent at every nesting level. `AgentModel` / its generated `_AgentModel` / the DSL schema `nop/schema/ai/agent.xdef` (`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent.xdef`) have NO `workDir` field, so there is currently no way for an agent to declare a per-agent working directory. `getWorkDir()` (defined on `AgentToolExecuteContext` and `SimpleToolExecuteContext`) has NO main-code consumers — grep confirms only definitions + test references (`TestAgentToolExecuteContext`). Consequently `workDir` cannot serve as a parent path-scope source as-is: this plan must ADD a `workDir` field to `AgentModel` (via `agent.xdef`) and thread `agentModel.getWorkDir()` through `ReActAgentExecutor` instead of the hardcoded `new File(".")`. Until that is done, every agent — top-level, sub-agent, nested — would compute identical effective path roots (`{<JVM CWD>}`) and the confinement would be meaningless.
- **`ParentPermissionConstraint` carries ONLY `allowedTools`** — no path-scope field exists.
- grep for `ParentConstrainedPathAccessChecker|PathPermission.*[Ii]ntersect|allowedPathRoots|clampPath` in `nop-ai-agent/src/main/java` returns **zero matches** — path-permission inheritance is confirmed unimplemented.

## Goals

- When a parent agent invokes a sub-agent via call-agent AND the parent agent has a non-null `workDir`, the sub-agent's file access (any path extracted from tool-call arguments by `checkPathAccess`) is confined to the parent agent's **effective (clamped) path scope**: a path that does NOT fall under one of the parent's allowed path roots is denied (fail-closed) with a reason that explicitly identifies "parent path permission constraint", BEFORE the wrapped (global/sub-agent) path checker is consulted.
- Nested delegation propagates the clamped path scope (same principle as plan 169's tool clamping): a middle agent B confined to A's path scope cannot widen sub-sub-agent C's path scope beyond A's scope. C inherits B's effective (clamped) roots, not B's own declared roots.
- Backward compatibility: when the parent agent's `workDir` is null (no declared path scope) OR no parent constraint is present (top-level single-agent execution), path checking is byte-for-byte unchanged — the existing global `DefaultPathAccessChecker` deny-list applies, no spurious denials, no behavioral change.
- The enforcement is observable and testable: a sub-agent attempting to access a path outside the parent's workDir subtree is denied with an identifiable reason flowing through the existing `PATH_ACCESS_DENIED` event and audit path.
- Design doc §4.4 is updated: mark the path-permission inheritance clause delivered; record the architectural decisions; correct the "glob 规则" inaccuracy by documenting that the current scope source is `workDir`-derived (from a new optional `workDir` field on `AgentModel` threaded through `ReActAgentExecutor`), and that a richer per-agent glob path-rule model remains a separate future concern.
- The path-scope source is a real, per-agent declared value: `workDir` is added to the agent DSL (`AgentModel` via `agent.xdef`, default null = ABSENT) and threaded through `ReActAgentExecutor` (replacing the hardcoded `new File(".")`), so distinct agents can declare distinct working directories and the confinement is meaningful rather than a no-op over the JVM CWD.

## Non-Goals

- Per-agent glob path-rules (allow/deny pattern model in agent DSL). The design doc's full "IPathAccessChecker glob 规则" vision assumes a per-agent path-rule system that does not exist in the live implementation. Building that rule model is a separate, larger concern (Layer 2 / `IPermissionMatrix` L2-14 territory, or a dedicated path-rules feature). This plan delivers the inheritance **mechanism** (constraint wrapper + propagation + wiring) with the agent's **declared** `workDir` (added to `AgentModel` by this plan) as the concrete path-scope source — the mechanism is designed so that a future glob-rule model can replace the workDir-derived scope without changing the enforcement wrapper.
- Argument-level / parameter-level path restrictions (e.g., "sub-agent may call write-file but only to /tmp/x" — this is a different enforcement layer tied to `ISecurityLevelResolver` L2-13 / `IPermissionMatrix` L2-14).
- Changing the global `DefaultPathAccessChecker` deny-list semantics (sensitive prefixes, traversal defense) — untouched; the parent path-constraint wrapper layers ON TOP of it.
- Cross-process path-permission propagation (single-process only; cross-process is L4-2 / L4-8).
- Persisting the path-scope constraint across session continuation (re-derived per call-agent invocation from the parent's current effective roots, same as the tool constraint in plan 169).
- Dynamic path-checker chain composition (the constraint wraps the existing pipeline; it does not restructure it).

## Scope

### In Scope

- Decision: path-scope representation — the parent agent's effective allowed path roots, a normalized set of absolute directory roots derived from the agent's `workDir`. ABSENT (null) means "no declared path scope → no confinement"; PRESENT (a non-null Set, possibly empty) means "confinement active, deny paths outside roots". PRESENT({}) = deny all paths (maximum restriction).
- Decision: path-scope clamping for nested delegation — an agent's effective path roots = `incomingParentRoots ∩ ownDeclaredRoots`, where ABSENT acts as the identity (ABSENT ∩ X = X) and PRESENT acts as set-intersection (PRESENT(R1) ∩ PRESENT(R2) = PRESENT(R1 ∩ R2)). This makes nested delegation safe: a middle agent's effective roots are already clamped, so it cannot propagate a wider scope onward.
- Decision: scope source — the parent's declared path scope is derived from its `workDir`. Because `AgentModel` currently has NO `workDir` field and `ReActAgentExecutor` hardcodes `new File(".")` for every agent, this plan ADDS a `workDir` field to the agent DSL (`AgentModel` via `nop/schema/ai/agent.xdef`, type string, default null = ABSENT) and threads `agentModel.getWorkDir()` through `ReActAgentExecutor` (replacing the hardcoded `new File(".")`). Non-null workDir → PRESENT({normalized workDir}); null/absent workDir → ABSENT (no confinement). This IS a minimal, additive DSL/schema change (one optional string attribute); it does NOT introduce a per-agent path-rule model (still a Non-Goal).
- Add a `workDir` field to `AgentModel` via the agent DSL schema `nop/schema/ai/agent.xdef` (type string, default null = ABSENT); regenerate `_AgentModel.java` via the codegen. Minimal, additive, optional — existing agent definitions compile/run unchanged.
- Fix `ReActAgentExecutor`'s `AgentToolExecuteContext` construction (`ReActAgentExecutor.java:~472-483`) to pass `agentModel.getWorkDir()` (resolved to `File` or null) instead of the hardcoded `new File(".")`, so each agent's workDir carries its declared value (or null = ABSENT) rather than the JVM CWD.
- Add an additive `PathAccessResult.deny(String reason, String matchedRule)` factory (minimal, backward-compatible API extension) so a denial can carry BOTH a descriptive reason (including the parent agent name) AND a `matchedRule` token (e.g. `"parent_path_permission_constraint"`). The existing `denyByRule(ruleName, path)` auto-formats the reason with no slot for the parent agent name, and the existing `deny(reason)` leaves `matchedRule` null — neither satisfies the denial-reason requirement.
- Decision: enforcement mechanism — a `ParentConstrainedPathAccessChecker` wrapping `IPathAccessChecker`. When the constraint's path roots are PRESENT and the requested path is NOT under any allowed root → deny (fail-closed, reason identifies "parent path permission constraint"). When PRESENT and the path IS under a root → delegate to the wrapped checker (global deny-list + sub-agent rules still apply on top). When ABSENT (null path roots) → pass-through (no-op, backward compatible). The wrapper is applied at executor-resolution time in `DefaultAgentEngine`, mirroring the tool wrapper — it does NOT mutate the engine's own `pathAccessChecker` field.
- Decision: propagation — extend the existing `ParentPermissionConstraint` with an additive optional `allowedPathRoots` field (null = ABSENT, non-null Set = PRESENT), reusing the same metadata key `"parentPermissionConstraint"` and audit metadata. This keeps a single constraint object carrying both the tool set and the path roots. Existing tool-only construction paths pass null path roots (ABSENT → no path confinement), preserving plan 169 backward compatibility.
- The `ParentConstrainedPathAccessChecker` enforcement wrapper component.
- Extending `ParentPermissionConstraint` to carry the path roots (additive, backward-compatible constructor).
- Wiring: `CallAgentExecutor` captures the parent's effective path roots and includes them in the propagated constraint; `ReActAgentExecutor` computes the effective path roots (`incomingParentRoots ∩ ownWorkDirRoots`) and populates them in `AgentToolExecuteContext`; `DefaultAgentEngine` resolves an effective path checker (analog of `resolveEffectiveToolAccessChecker`) and passes it to `resolveExecutor`.
- Fail-closed behavior with descriptive denial reasons flowing through the existing `PATH_ACCESS_DENIED` event + audit path.
- Backward compatibility: null workDir / ABSENT path roots / no constraint → unchanged behavior.
- Unit tests + integration tests + end-to-end tests.
- Design doc §4.4 update (mark delivered + record decisions + correct "glob 规则" inaccuracy).

### Out Of Scope

- Per-agent glob path-rules / path-rule DSL model (separate future concern).
- Argument-level / parameter-level path restrictions.
- Cross-process path-permission propagation.
- Path-scope constraint persistence across session continuations.
- Changes to the global `DefaultPathAccessChecker` deny-list.

## Execution Plan

### Phase 1 - Path-Scope Constraint Model + Enforcement Wrapper

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/` (extend `ParentPermissionConstraint` with path roots; new `ParentConstrainedPathAccessChecker` wrapper; additive `PathAccessResult.deny(String reason, String matchedRule)` factory), `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent.xdef` (add `workDir` attribute), `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/model/_gen/_AgentModel.java` (regenerated by codegen — do not hand-edit)

- Item Types: `Decision | Fix | Proof`

- [x] **Decision（path-scope representation）**: Extend the existing `ParentPermissionConstraint` (plan 169) with an additive, immutable `allowedPathRoots` field (type `Set<String>`, normalized absolute directory roots). `null` means ABSENT (no declared path scope → no path confinement); a non-null Set (including an empty set) means PRESENT (confinement active — a path outside these roots is denied). PRESENT with an empty set = deny all paths (maximum restriction, e.g. when clamping collapses to nothing). The existing tool-only constructor delegates with `allowedPathRoots = null` (ABSENT) so all plan-169 construction paths and tests are unaffected. Record this decision in the design doc.
- [x] **Decision（enforcement mechanism）**: A `ParentConstrainedPathAccessChecker` wraps an existing `IPathAccessChecker`. Semantics: (a) when the constraint's `allowedPathRoots` is ABSENT (null) → delegate entirely (no-op pass-through, backward compatible); (b) when PRESENT and the requested path, after normalization, does NOT start with (is not under) any allowed root → deny (fail-closed) with reason "denied by parent path permission constraint: path '{path}' outside parent agent '{parentAgent}' allowed roots" (produced via the new `PathAccessResult.deny(reason, matchedRule)` factory added in this Phase, so BOTH the reason naming the parent agent AND the `matchedRule` token `"parent_path_permission_constraint"` are set); (c) when PRESENT and the path IS under an allowed root → delegate to the wrapped checker (the global deny-list and the sub-agent's own rules still apply on top). The wrapper is applied at executor-resolution time in `DefaultAgentEngine`, mirroring `ParentConstrainedToolAccessChecker` — it does NOT mutate the engine's own `pathAccessChecker` field. Record this decision in the design doc.
- [x] **Decision（path-root matching semantics）**: A path P is "under" a root R iff the normalized absolute form of P equals R or starts with `R + "/"`. Normalization reuses the existing `DefaultPathAccessChecker` path-normalization logic (tilde expansion, backslash→slash, `Paths.get(p).normalize()`). Paths that fail normalization (e.g. invalid traversal that escapes normalization) are denied by the existing checker before reaching the root check; the wrapper must not re-implement traversal defense. Relative paths are resolved against the sub-agent's workDir before root matching (so a relative path that escapes the parent root via normalization is caught).
- [x] **Decision（workDir scope source — requires a minimal DSL/schema change）**: Add a `workDir` field to `AgentModel` via the agent DSL schema `nop/schema/ai/agent.xdef` (type string, default null = ABSENT = no declared path scope). This is the only way to give each agent a meaningful, distinct working directory: the live `ReActAgentExecutor` hardcodes `new File(".")` for every agent (`ReActAgentExecutor.java:~473`), and `AgentModel` / `_AgentModel` / `agent.xdef` have no such field (see Current Baseline). Without this, every agent (top-level, sub-agent, nested) would compute identical effective path roots (`{<JVM CWD>}`) and the confinement would be meaningless. The field is additive and optional (null = ABSENT), so existing agent definitions compile/run unchanged. Record this decision (and the schema touch) in the design doc.
- [x] **Fix（add workDir to AgentModel via DSL schema）**: Add the `workDir` attribute to `nop/schema/ai/agent.xdef` (e.g. `workDir="string"` on the root `<agent>` element) and regenerate `_AgentModel.java` via the codegen (do NOT hand-edit `_AgentModel.java`). Add the accessor on `AgentModel` via the regenerated base. Default null = ABSENT.
- [x] **Fix（PathAccessResult API extension）**: Add an additive `PathAccessResult.deny(String reason, String matchedRule)` factory so a denial can carry BOTH a descriptive reason (parameterized with the parent agent name) AND a `matchedRule` token (e.g. `"parent_path_permission_constraint"`). The existing `deny(String reason)` leaves `matchedRule` null, and the existing `denyByRule(String ruleName, String path)` auto-formats the reason as `"Path denied by rule '<ruleName>': <path>"` with no slot for the parent agent name — neither satisfies this plan's requirement that the denial reason identify the parent agent AND set the matchedRule token. The constructor is private, so the new factory is the only additive entry point. This is a minimal, backward-compatible API extension (existing `allow()`, `deny(reason)`, `denyByRule(...)` unchanged).
- [x] Extend `ParentPermissionConstraint` with the immutable `allowedPathRoots` field + a backward-compatible constructor (existing constructor delegates with null). Add an accessor. Ensure `equals`/`hashCode`/`toString` include the new field.
- [x] Implement `ParentConstrainedPathAccessChecker` (final class, `implements IPathAccessChecker`): constructor takes a non-null `ParentPermissionConstraint` and a non-null delegate `IPathAccessChecker` (fail-fast with `IllegalArgumentException` on nulls, mirroring `ParentConstrainedToolAccessChecker`); `checkAccess(path, ctx)` implements the ABSENT/PRESENT semantics above; deny produces a `PathAccessResult.deny(reason, "parent_path_permission_constraint")` via the new factory added in this Phase, where `reason` includes the parent agent name (from the constraint) and the offending path.

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ParentPermissionConstraint` carries `allowedPathRoots` as an additive immutable field; the existing tool-only constructor still compiles and behaves identically (plan 169 tests pass unchanged — `allowedPathRoots` defaults to ABSENT)
- [x] `AgentModel` carries a `workDir` field added via `agent.xdef` (type string, default null = ABSENT); `_AgentModel` regenerated by codegen (not hand-edited); existing agent definitions compile and run unchanged (additive, optional field)
- [x] `PathAccessResult.deny(String reason, String matchedRule)` factory added (additive); existing `allow()`, `deny(reason)`, `denyByRule(...)` unchanged; `ParentConstrainedPathAccessChecker` uses the new factory to set BOTH the reason (with parent agent name) and the matchedRule token `"parent_path_permission_constraint"`
- [x] `ParentConstrainedPathAccessChecker` correctly implements all three branches: ABSENT roots → delegate (pass-through); PRESENT roots + path under a root → delegate; PRESENT roots + path outside all roots → deny with explicit "parent path permission constraint" reason and a `matchedRule` token
- [x] PRESENT with empty roots → ALL paths denied (maximum restriction), with the explicit reason
- [x] Path-root matching handles: absolute path under root (allowed), absolute path outside root (denied), relative path resolved against workDir, path equal to a root (allowed), trailing-slash / case differences handled by normalization
- [x] **无静默跳过**: every denial produces an explicit `PathAccessResult.deny(...)` identifying "parent path permission constraint"; no silent allow, no swallowed branch, no empty method body; null constraint/delegate constructor args throw `IllegalArgumentException` rather than silently degrading
- [x] **新增功能测试**: unit tests covering: (a) ABSENT roots pass-through (backward compatible); (b) PRESENT roots, path under root → delegates (wrapped checker can still deny); (c) PRESENT roots, path outside → denied by constraint with explicit reason + matchedRule; (d) PRESENT empty roots → all paths denied; (e) root matching edge cases (path == root, relative path resolution, normalization)
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` passes
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes with no regression (the wrapper is not yet wired into the engine)
- [x] No owner-doc update required for Phase 1 (internal implementation; Phase 3 updates the design doc)
- [x] `ai-dev/logs/` 对应日期条目已更新 (will be updated in Phase 3 closure)

### Phase 2 - Engine Wiring + Context/Propagation + Integration Tests

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentToolExecuteContext.java` (carry effective path roots), `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java` (compute effective path roots), `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/CallAgentExecutor.java` (capture + propagate path roots), `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java` (resolve effective path checker)

- Item Types: `Decision | Fix | Proof`

- [x] **Fix（thread agent workDir through ReActAgentExecutor）**: Replace the hardcoded `new File(".")` at `ReActAgentExecutor.java:~473` with `agentModel.getWorkDir()` (resolved to `File` or null). This is what makes the `workDir` field added in Phase 1 a LIVE per-agent value instead of a constant equal to the JVM CWD. Without this fix, every agent's effective path roots would be identical (`{<JVM CWD>}`) and the confinement would be meaningless. When `agentModel.getWorkDir()` is null (ABSENT), the context's workDir is null → effective roots ABSENT (backward compatible, no confinement).
- [x] **Decision（propagation mechanism）**: The path roots propagate through the SAME `ParentPermissionConstraint` object and the SAME metadata key (`"parentPermissionConstraint"`) as the tool set — the constraint now carries both `allowedTools` and `allowedPathRoots`. This reuses the plan-169 metadata infrastructure with no new metadata key and no `AgentMessageRequest` signature change. `DefaultAgentEngine.doExecute()` reads the single constraint object and wraps BOTH the tool checker (existing) and the path checker (new) from the same constraint. Record this decision in the design doc.
- [x] Add the current agent's **effective (clamped)** path roots to `AgentToolExecuteContext` as an additive field (e.g. `allowedPathRoots`, type `Set<String>`, null = ABSENT). Add a backward-compatible constructor/builder overload (existing call sites compile unchanged; the field defaults to ABSENT). `ReActAgentExecutor` computes this field's value as `incomingParentRoots ∩ ownDeclaredRoots`, where the agent's own declared roots = PRESENT({normalized workDir}) when `workDir != null`, ABSENT when null. When no parent constraint is present (top-level), the effective roots = the agent's own declared roots (PRESENT({workDir}) or ABSENT). This clamping is what makes nested delegation correct, exactly mirroring the tool-set clamping in plan 169.
- [x] Update `CallAgentExecutor` to: (a) read the current agent's **effective (clamped)** path roots from `AgentToolExecuteContext`, (b) include them (alongside the existing `allowedTools`) in the `ParentPermissionConstraint` it builds, (c) propagate the constraint via the existing metadata key for all three session modes (new, continue, fork — the same code paths updated by plan 169). The existing `buildParentConstraint()` / `buildConstraintMetadata()` methods are extended to carry path roots; no new metadata key.
- [x] Add `DefaultAgentEngine.resolveEffectivePathAccessChecker(AgentMessageRequest)` (analog of `resolveEffectiveToolAccessChecker`): read the constraint from metadata; if absent or the constraint's `allowedPathRoots` is ABSENT → return the engine's own `pathAccessChecker` (no-op); if PRESENT → return `new ParentConstrainedPathAccessChecker(constraint, this.pathAccessChecker)`. Fail-fast (`NopAiAgentException`) if the metadata key is present but the constraint object is null/malformed, mirroring the tool-checker fail-fast in plan 169.
- [x] Update `DefaultAgentEngine.doExecute()` to resolve the effective path checker and pass it to `resolveExecutor()`; update `resolveExecutor(...)` to accept an effective `IPathAccessChecker` (the engine's own `pathAccessChecker` field remains unchanged for other executions). The executor built for a sub-agent receives the wrapped path checker; the executor built for a top-level agent receives the unwrapped checker.
- [x] Ensure the wrapped path checker flows into `ReActAgentExecutor.pathAccessChecker` and is the instance invoked by `checkPathAccess()` (line ~856), so the parent path constraint actually gates real tool-call path arguments.

Exit Criteria:

- [x] `AgentToolExecuteContext` carries the current agent's effective path roots as an additive, backward-compatible field (existing constructor/builder preserved so non-opt-in call sites compile unchanged)
- [x] `ReActAgentExecutor` passes `agentModel.getWorkDir()` (not the hardcoded `new File(".")`) into `AgentToolExecuteContext`; distinct agents with distinct declared `workDir` values now produce distinct effective path roots rather than the shared JVM CWD
- [x] `ReActAgentExecutor` populates effective path roots = `incomingParentRoots ∩ ownDeclaredRoots` (ABSENT acts as identity; PRESENT acts as set-intersection) when constructing the context
- [x] `CallAgentExecutor` reads the parent's effective path roots from `AgentToolExecuteContext` and includes them in the propagated constraint for all three session modes
- [x] `DefaultAgentEngine` resolves an effective path checker from the constraint and passes it to the sub-agent executor; proceeds with the unwrapped checker when path roots are ABSENT or no constraint is present
- [x] **接线验证**: the path constraint propagated by call-agent is actually consumed by the engine, wrapped into the sub-agent's path checker, and invoked by `checkPathAccess` — verified by an integration test asserting a sub-agent's tool call with a path outside the parent's workDir is denied by the parent path constraint (and a path inside is allowed, subject to the global deny-list)
- [x] **无静默跳过**: if the constraint metadata key is present but the constraint object is null/malformed, the engine fails fast (`NopAiAgentException`) rather than silently ignoring it; the wrapper never silently allows an out-of-scope path
- [x] **新增功能测试**: integration test — parent agent with workDir `/workspace/project-a` → call-agent invokes sub-agent → sub-agent's tool call referencing `/workspace/project-b/secret` is denied by parent path constraint; sub-agent's tool call referencing `/workspace/project-a/src/Main.java` is allowed (passes the constraint, then the global deny-list); the `PATH_ACCESS_DENIED` event reason identifies "parent path permission constraint" (integration-level wiring verified in `TestSubAgentPathPermissionWiring`; full end-to-end verification in Phase 3)
- [x] Backward compatibility: the `AgentToolExecuteContext` change is additive (existing test call sites compile unchanged); the plan-169 tool-constraint integration tests (`TestSubAgentPermissionWiring`) still pass; a parent agent with null workDir propagates ABSENT path roots → sub-agent path checking is unchanged
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] No owner-doc update required for Phase 2 (Phase 3 updates the design doc)
- [x] `ai-dev/logs/` 对应日期条目已更新 (will be updated in Phase 3 closure)

### Phase 3 - End-to-End Verification + Design Doc Update

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/` (end-to-end tests), `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` (§4.4 status update + decisions + glob-rules correction), `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` (if a work-item entry exists)

- Item Types: `Proof | Follow-up`

- [x] End-to-end test (constraint enforcement): construct an engine with a mock `IChatService` simulating tool calling. Parent agent's `workDir` (declared in its agent model fixture) = `/workspace/project-a`, declared tools include `call-agent`. The parent ReAct loop emits a `call-agent` tool call targeting a sub-agent whose `workDir` (declared in its agent model fixture) is within the parent's scope. The sub-agent's ReAct loop attempts a tool call with a path argument `/workspace/project-b/secret` → denied by parent path permission constraint (denial flows through `PATH_ACCESS_DENIED` with reason identifying "parent path permission constraint"). The sub-agent attempts a tool call with `/workspace/project-a/src/Main.java` → allowed (passes constraint, then the global deny-list). **This proves the full path: parent ReAct → call-agent → engine.execute with path-constrained checker → sub-agent ReAct → `checkPathAccess` → path denied by inherited constraint.**
- [x] End-to-end test (backward compatibility): a single-agent execution (no call-agent invocation, no constraint in metadata) has identical path-checking behavior to before this plan — a path allowed by the global deny-list is still allowed, a sensitive path is still denied. Reuse an existing test or add a focused assertion.
- [x] End-to-end test (nested delegation clamping): parent A (declared `workDir` `/workspace/a`) → call-agent → sub-agent B (declared `workDir` `/workspace/a/sub` — within A's scope; B's effective roots = {/workspace/a}) → call-agent → sub-sub-agent C (declared `workDir` `/workspace/a/sub`). Because A's effective roots are {/workspace/a}, B's effective roots are clamped to {/workspace/a} (not {/workspace/a/sub}). When B delegates to C, C's constraint = B's effective roots {/workspace/a}. C attempts a path `/workspace/b/x` → denied (outside A's clamped scope). **This proves the path constraint propagates the clamped root set through nested delegation, not each agent's own workDir — mirroring the tool-clamping proof in plan 169.** (Include if straightforward; defer only if the test setup is excessively complex, with rationale recorded.)
- [x] End-to-end test (null workDir = no confinement): a parent agent whose model declares NO `workDir` (null = ABSENT) → call-agent → sub-agent. The sub-agent's path checking is subject ONLY to the global deny-list (no parent path confinement). A path that the global deny-list allows is allowed even though no parent scope was declared. This verifies the ABSENT-roots pass-through at the system level.
- [x] Update design doc `nop-ai-agent-security-and-permissions.md` §4.4: change the path-permission clause from "deferred" to "delivered ✅". Record the decisions: (1) path-scope representation = effective clamped path roots, ABSENT/PRESENT three-valued semantics; (2) clamping for nested delegation = `incoming ∩ own`, ABSENT as identity; (3) scope source = `workDir`-derived from a new optional `workDir` field on `AgentModel` (`agent.xdef`), threaded through `ReActAgentExecutor` replacing the hardcoded `new File(".")`; (4) enforcement = `ParentConstrainedPathAccessChecker` wrapping `IPathAccessChecker` at executor-resolution time, denying via the new `PathAccessResult.deny(reason, matchedRule)` factory; (5) propagation via the existing `ParentPermissionConstraint` + metadata key (extended to carry path roots). **Correct the "glob 规则" inaccuracy**: note that the live `DefaultPathAccessChecker` is a global static deny-list (not a per-agent glob-rule system), that this plan's scope source is `workDir`-derived (now declared in the agent DSL), and that a richer per-agent glob path-rule model remains a separate future concern (Layer 2 / `IPermissionMatrix` L2-14).
- [x] Verify the enforcement is auditable: when a path is denied by the parent path constraint, the existing audit path in `checkPathAccess` (line ~858-864) records the denial — the `PathAccessResult.deny(...)` reason and `matchedRule` flow through the existing `AuditDecision.DENY` + `PATH_ACCESS_DENIED` event. No new audit infrastructure needed.

Exit Criteria:

- [x] **端到端验证**（constraint enforcement）: from parent agent ReAct loop → call-agent → engine.execute with path-constrained checker → sub-agent ReAct → `checkPathAccess` → sub-agent's out-of-scope path denied by parent path constraint. The full path is exercised in one test.
- [x] **端到端验证**（backward compatibility）: single-agent execution unchanged — no spurious denials, existing global deny-list behavior intact, all existing tests pass.
- [x] **端到端验证**（nested delegation clamping, if included）: path constraint propagates the clamped root set — not each agent's own workDir — through nested call-agent chains; sub-sub-agent inherits the clamped scope.
- [x] **端到端验证**（null workDir = no confinement）: ABSENT path roots → sub-agent path checking subject only to the global deny-list (pass-through at the system level).
- [x] **接线验证**: the path constraint flows from call-agent metadata → engine constraint application → sub-agent path-checker wrapper → `checkPathAccess` → path denial (verified by the end-to-end test asserting the denial with the correct reason).
- [x] **无静默跳过**: constraint-denied paths produce explicit denial messages identifying "parent path permission constraint" (not silent skips, not generic denies); the denial is auditable via the existing `PATH_ACCESS_DENIED` event.
- [x] **新增功能测试**: end-to-end constraint test + backward-compatibility test + nested-delegation test + null-workDir test — all passing.
- [x] **Anti-Hollow Check**: the end-to-end test proves the path constraint is actually enforced at runtime (sub-agent's out-of-scope tool-call path argument is denied with the correct reason, NOT silently allowed, NOT passed through to the global checker).
- [x] Design doc §4.4 updated: delivered status + 5 decisions recorded + "glob 规则" inaccuracy corrected + per-agent glob path-rules noted as separate future concern.
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes (all new + existing tests)
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 Plan Status 改为 `completed`。

- [x] Sub-agent path-permission inheritance is functional (path-scope confinement verified by end-to-end test)
- [x] Path-scope confinement is meaningful: `workDir` is a real per-agent declared value (added to `AgentModel` via `agent.xdef`, threaded through `ReActAgentExecutor` replacing the hardcoded `new File(".")`), not the shared JVM CWD — distinct agents with distinct declared `workDir` values produce distinct effective path roots (verified by the end-to-end test)
- [x] Enforcement is fail-closed (path outside parent's effective roots → denied, with descriptive reason identifying "parent path permission constraint")
- [x] Backward compatibility preserved (null workDir / ABSENT path roots / no constraint → path checking unchanged, all existing tests pass, no spurious denials)
- [x] Path-constraint propagation works end-to-end: call-agent → constraint metadata → engine → sub-agent path-checker wrapper → `checkPathAccess` → path denial
- [x] Nested delegation propagates the clamped path-scope (a middle agent cannot widen the sub-sub-agent's scope beyond the parent chain's clamped roots)
- [x] 必要 focused verification（unit + integration + end-to-end）已完成
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs 已同步到 live baseline（security-and-permissions.md §4.4 — delivered status + decisions + glob-rules correction）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 (a) 路径约束从 call-agent metadata → engine → sub-agent path-checker wrapper → `checkPathAccess` → 路径拒绝 的调用链在运行时确实连通（端到端测试证明），(b) 无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### Per-agent glob path-rules (allow/deny pattern model)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Design §4.4's full vision assumes a per-agent path-rule system (glob allow/deny patterns) on `IPathAccessChecker`. The live `DefaultPathAccessChecker` is a global static deny-list with no per-agent rules. This plan delivers the inheritance MECHANISM (constraint wrapper + propagation + wiring) with the agent's **declared** `workDir` (added to `AgentModel` by this plan) as the concrete scope source — a meaningful, fail-closed confinement that closes the §4.4 clause. The enforcement wrapper is designed so a future glob-rule model can replace the workDir-derived scope without changing the wrapper. Building the glob-rule model itself is a separate, larger concern (Layer 2 / `IPermissionMatrix` L2-14 territory).
- Successor Required: yes
- Successor Path: future per-agent-path-rules plan (replaces workDir-derived scope with an explicit rule set; the `ParentConstrainedPathAccessChecker` wrapper and propagation machinery from this plan are reused).

### Argument-level / parameter-level path restrictions

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Path-scope confinement operates at path granularity (is this path under an allowed root?). Argument-level restrictions (e.g. "sub-agent may write but only to /tmp/x") require parameter interception at a different layer, tied to `ISecurityLevelResolver` (L2-13) and `IPermissionMatrix` (L2-14).
- Successor Required: no

### Path-scope constraint persistence across session continuation

- Classification: `optimization candidate`
- Why Not Blocking Closure: When call-agent continues an existing sub-session, the path constraint is re-derived from the parent's current effective roots on each invocation. Persisting the original constraint with the session is a refinement that does not affect the security guarantee — the parent's current effective roots are always a valid constraint. Same rationale as the tool-constraint persistence deferral in plan 169.
- Successor Required: no

## Non-Blocking Follow-ups

- Configuration option: allow agents to declare an explicit path scope (XDSL `<path-scope>` element) richer than workDir — design §9 progressive enhancement; would replace the workDir-derived scope source without changing the enforcement wrapper.
- Constraint caching: if path-root normalization shows up in profiling (unlikely for set-prefix checks), cache the normalized roots per agent context.

## Closure

Status Note: Plan 170 closes the second clause of design §4.4 ("文件权限 = 父权限 ∩ 子配置"). When a parent agent with a declared `workDir` invokes a sub-agent via call-agent, the sub-agent's file access is confined to the parent's effective (clamped) path scope. The enforcement wrapper (`ParentConstrainedPathAccessChecker`) denies out-of-scope paths with a reason identifying "parent path permission constraint" before the global deny-list is consulted. Nested delegation propagates the clamped scope. Backward compatibility is preserved: null `workDir` / ABSENT path roots / no constraint → unchanged path checking.
Completed: 2026-06-14

Closure Audit Evidence:

- Reviewer / Agent: Independent Closure Auditor (subagent, task_id: ses_13c45db8effeiWMJTSfk0vBrTa)
- Audit Session: ses_13c45db8effeiWMJTSfk0vBrTa (fresh session, not the implementation session)
- Evidence:
  - Phase 1 Exit Criteria: PASS — `ParentPermissionConstraint` carries `allowedPathRoots` (additive, immutable); `ParentConstrainedPathAccessChecker` implements all three branches (ABSENT/PRESENT-under-root/PRESENT-outside-root); `PathAccessResult.deny(reason, matchedRule)` factory added; `agent.xdef` has `workDir="string"`; `_AgentModel.java` regenerated by codegen (not hand-edited). Unit tests: 31 tests in `TestParentConstrainedPathAccessChecker` all pass.
  - Phase 2 Exit Criteria: PASS — `AgentToolExecuteContext` carries `allowedPathRoots` (additive, 3 backward-compatible constructors); `ReActAgentExecutor` passes `agentModel.getWorkDir()` (not `new File(".")`); `computeEffectivePathRoots` implements clamping; `CallAgentExecutor.buildParentConstraint` reads path roots; `DefaultAgentEngine.resolveEffectivePathAccessChecker` wraps checker when PRESENT, returns engine's own when ABSENT; fail-fast on malformed constraint. Integration tests: 12 tests in `TestSubAgentPathPermissionWiring` all pass.
  - Phase 3 Exit Criteria: PASS — 4 end-to-end tests in `TestSubAgentPathPermissionEndToEnd`: (1) constraint enforcement (out-of-scope path denied, in-scope path allowed); (2) backward compatibility (single-agent unchanged); (3) nested delegation clamping (leaf agent's out-of-scope path denied); (4) null workDir = no confinement (ABSENT roots → pass-through). Design doc §4.4 updated with 5 decisions + "glob 规则" correction. Roadmap row L4-1d added with ✅.
  - Closure Gates: PASS — all gates verified. `workDir` is a real per-agent declared value (distinct agents produce distinct roots); enforcement is fail-closed; backward compatibility preserved; nested delegation propagates clamped scope; owner docs synced.
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit code: 0 (all 70 checklist items checked, Closure Evidence written).
  - Anti-Hollow 检查结果: PASS — full runtime call chain connected: `doExecute`→`resolveEffectivePathAccessChecker`→`resolveExecutor`→`ReActAgentExecutor.pathAccessChecker`→`checkPathAccess`→`ParentConstrainedPathAccessChecker.deny`. E2E tests assert PATH_ACCESS_DENIED events with "parent path permission constraint" reason. `scan-hollow-implementations.mjs --module nop-ai-agent --severity high` exit code: 0 (0 critical, 0 high, 0 medium, 0 low findings).
  - Deferred 项分类检查: PASS — per-agent glob path-rules classified as `out-of-scope improvement` with Successor Required: yes; argument-level restrictions as `out-of-scope improvement` with Successor Required: no; path-scope persistence as `optimization candidate`. No in-scope live defect or contract drift deferred.
  - Test execution: `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → 871 tests, 0 failures, 0 errors. BUILD SUCCESS. Targeted: 47 tests across 3 test classes, all pass.

Follow-up:

- Per-agent glob path-rules — deferred, requires a path-rule model (see Deferred But Adjudicated; successor plan will replace workDir-derived scope with explicit rule set, reusing this plan's wrapper + propagation machinery)
- Argument-level path restrictions — out-of-scope, Layer 2+ concern
- Path-scope constraint persistence across session continuation — optimization candidate

## Follow-up handled by 174-nop-ai-agent-per-agent-path-rules.md

> Additive annotation (2026-06-14). This completed plan is historical record; this section only records successor traceability and does not alter the closure above.

The deferred "Per-agent glob path-rules" item (see `Deferred But Adjudicated` → "Per-agent glob path-rules (allow/deny pattern model)", Successor Required: yes) is being handled by successor plan [`174-nop-ai-agent-per-agent-path-rules.md`](174-nop-ai-agent-per-agent-path-rules.md).

Carry-over note for the successor: this plan delivered the inheritance MECHANISM (`ParentConstrainedPathAccessChecker` wrapper + propagation via `ParentPermissionConstraint.allowedPathRoots` + clamping via `computeEffectivePathRoots`) with `workDir`-derived root-sets as the scope source. The successor replaces the workDir-derived scope with an explicit glob allow/deny rule model (design §4.3). The successor reuses this plan's wrapper and propagation machinery — the constraint is extended additively with a `allowedPathRules` field, and the wrapper is extended to evaluate parent rules (deny-wins cross-level) before delegating. The root-based confinement from this plan remains intact for agents that only declare `workDir` (backward compatible).
