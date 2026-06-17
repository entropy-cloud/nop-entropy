# 174 Per-Agent Glob Path-Rules Model

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: sec-4.3 (per-agent-path-rules)
> Last Reviewed: 2026-06-14
> Source: Carry-over from plan 170 (`ai-dev/plans/170-nop-ai-agent-path-permission-inheritance.md`, Deferred "Per-agent glob path-rules (allow/deny pattern model)", Successor Required: yes); design `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §4.3 ("路径匹配：glob 模式，从上到下匹配，第一条命中生效"), §9 (file-access patterns)
> Related: Plan 170 (path-permission inheritance — `ParentConstrainedPathAccessChecker` + `ParentPermissionConstraint.allowedPathRoots` + `computeEffectivePathRoots`), Plan 169 (tool-permission inheritance — constraint propagation mechanism), Plan 141 (L1-8 `IPathAccessChecker` + `DefaultPathAccessChecker`)

## Purpose

Deliver the per-agent glob allow/deny path-rule model envisioned by design §4.3, replacing the workDir-derived single-root scope source (plan 170) with an explicit, ordered, glob-pattern rule set. Agents can now declare `<path-rules>` in their DSL — a list of glob patterns with `allow` or `deny` access decisions, evaluated first-match-wins within the agent's own rule set. This richer model coexists with the existing `workDir`-derived root confinement (backward compatible): agents that only declare `workDir` continue to use root-based confinement; agents that declare `<path-rules>` get rule-based confinement. The inheritance mechanism (plan 170's `ParentConstrainedPathAccessChecker` wrapper + `ParentPermissionConstraint` propagation) is extended additively to carry and enforce path rules across delegation levels, reusing the existing wrapper and propagation machinery.

## Current Baseline

- **Plan 170 delivered path-permission inheritance via workDir-derived root-sets (✅)**: `ParentPermissionConstraint` carries `allowedPathRoots` (Set<String>, null=ABSENT, non-null=PRESENT). `ParentConstrainedPathAccessChecker` wraps `IPathAccessChecker`, denies paths outside the parent's allowed roots (fail-closed). `ReActAgentExecutor.computeEffectivePathRoots()` computes `incomingParentRoots ∩ ownDeclaredRoots` (ABSENT as identity). `CallAgentExecutor` propagates roots via constraint metadata. `DefaultAgentEngine.resolveEffectivePathAccessChecker()` wraps the engine's `pathAccessChecker` when the constraint has PRESENT roots.
- **`DefaultPathAccessChecker` is a GLOBAL STATIC deny-list (L1-8 ✅)**: hardcoded sensitive prefixes (`~/.ssh/`, `/etc/`, etc.), sensitive filenames (`.env`, `id_rsa`), and path-traversal defense. No per-agent state, no configurable rules, no glob-pattern model. `normalizePathStatic()` is the shared normalization utility.
- **`AgentModel` has `workDir` (plan 170 ✅)**: declared in `agent.xdef` as `workDir="string"` (default null = ABSENT). Threaded through `ReActAgentExecutor.resolveWorkDir()` replacing the hardcoded `new File(".")`.
- **`agent.xdef` has NO `<path-rules>` element**: grep for `path-rules|pathRules|PathRule|PathRuleModel|globPathRule` in `nop-ai-agent/src/main/java` returns zero matches — the per-agent path-rule model is confirmed unimplemented.
- **Nop platform has `AntPathMatcher` (io.nop.commons.path)**: a mature glob/ant-pattern matcher (`match(pattern, path)`) supporting `*`, `**`, `?` wildcards. This is the natural choice for glob pattern matching (no need to implement pattern matching from scratch).
- **`IPathAccessChecker` contract**: `PathAccessResult checkAccess(String path, AgentExecutionContext ctx)`. `PathAccessResult` has `allow()`, `deny(reason)`, `denyByRule(ruleName, path)`, `deny(reason, matchedRule)`.
- **`checkPathAccess` in `ReActAgentExecutor` (line ~838-870)**: extracts path-like argument keys (`PATH_ARG_KEYS`), calls `pathAccessChecker.checkAccess(pathValue, ctx)`, publishes `PATH_ACCESS_DENIED` event + audit entry on denial. This is where per-agent rules take effect at runtime.

## Goals

- An agent can declare an ordered list of glob path-rules in its DSL (`<path-rules>` element in `agent.xdef`), each specifying a glob pattern and an access decision (`allow` or `deny`).
- A `RuleBasedPathAccessChecker` evaluates the agent's own rule-set using first-match-wins semantics (design §4.3: "glob 模式，从上到下匹配，第一条命中生效"): the first rule whose pattern matches the normalized path decides — `deny` → denied, `allow` → delegated to the wrapped checker (the global deny-list still applies on top). No rule matching → delegated to the wrapped checker (the agent does not restrict this path beyond the global deny-list).
- The engine resolves a per-agent path checker: an agent with declared `<path-rules>` gets a `RuleBasedPathAccessChecker` wrapping the global `DefaultPathAccessChecker`; an agent without path-rules gets the global checker unchanged (backward compatible).
- The per-agent rule-based checker composes with the parent-constraint wrapper: when a parent agent invokes a sub-agent via call-agent, the sub-agent's path checking evaluates (in order) the parent's constraint rules/roots → the sub-agent's own rules → the global deny-list. A path must pass all layers.
- Inheritance and clamping: the parent's effective path-rules propagate via the constraint (additive `allowedPathRules` field). `ParentConstrainedPathAccessChecker` evaluates parent rules using cross-level deny-wins semantics (any parent DENY rule matching → denied; otherwise delegate). Nested delegation accumulates the rule chain — a middle agent propagates incoming parent rules + its own rules, so a sub-sub-agent inherits the full constraint chain (no permission escalation).
- Backward compatibility: agents that only declare `workDir` (no `<path-rules>`) continue to use the root-based confinement from plan 170, byte-for-byte unchanged. Agents that declare neither get the global deny-list only. All existing tests pass without modification.
- Design doc §4.3 is updated: mark the glob path-rule model as delivered; record the architectural decisions (first-match-wins within agent, deny-wins across delegation levels, coexistence with workDir root-confinement).

## Non-Goals

- Read/read-write access levels (design §4.3 mentions `read | read-write | deny`). This plan delivers `allow | deny` only, matching the current `DefaultPathAccessChecker` deny/allow model. Read/read-write distinction requires tool-kind analysis (which tool is reading vs writing the path) and is tied to `ISecurityLevelResolver` (L2-13) / `IPermissionMatrix` (L2-14) — a separate, future refinement.
- Argument-level / parameter-level path restrictions (e.g., "sub-agent may call write-file but only to /tmp/x"). This is a different enforcement layer tied to `ISecurityLevelResolver` (L2-13) / `IPermissionMatrix` (L2-14).
- Externalized path-rule configuration via XDSL YAML files (design §4.3 mentions "敏感路径 denylist 可通过 XDSL 外部配置"). This plan delivers path-rules declared in the agent DSL (`agent.xdef`) only. Externalized XDSL configuration is a follow-up.
- Per-agent path-rules for `DefaultPathAccessChecker`'s global deny-list overrides. The global deny-list remains hardcoded and applies to ALL agents on top of per-agent rules. Overriding the global deny-list per-agent is a separate concern (design §7.2 `ISensitivePathProvider`).
- Cross-process path-rule propagation (single-process only; cross-process is L4-2 / L4-8).
- Changing the existing `workDir`-derived root-confinement semantics (plan 170). Both coexist.

## Scope

### In Scope

- `PathRuleModel` codegen-generated model class (glob pattern string + access decision ALLOW/DENY)
- `<path-rules>` element in `agent.xdef` + `AgentModel` additive field + codegen regeneration
- `RuleBasedPathAccessChecker` implementing `IPathAccessChecker` (first-match-wins, delegates to wrapped checker)
- Engine per-agent path-checker resolution (compose rule-based checker with global checker)
- `ParentPermissionConstraint` additive `allowedPathRules` field
- `ParentConstrainedPathAccessChecker` extension to evaluate parent rules (deny-wins cross-level)
- `ReActAgentExecutor.computeEffectivePathRules()` (analog of `computeEffectivePathRoots`)
- `CallAgentExecutor` propagation of effective path-rules
- `DefaultAgentEngine.resolveEffectivePathAccessChecker` extension to wrap with rules
- Unit + integration + end-to-end tests
- Design doc §4.3 update

### Out Of Scope

- Read/read-write access levels (future, tied to L2-13/L2-14)
- XDSL externalized path-rule configuration (future)
- ISensitivePathProvider / global deny-list per-agent overrides (Layer 4)
- Argument-level path restrictions (Layer 2+)
- Cross-process path-rule propagation (Layer 4)

## Execution Plan

### Phase 1 - Path-Rule Model + DSL + RuleBasedPathAccessChecker

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/`, `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent.xdef`, `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/model/`

- Item Types: `Decision | Proof`

- [x] **Decision (access-level model)**: The path-rule model uses `allow | deny` (two-valued), matching the current `DefaultPathAccessChecker` deny/allow semantics. Design §4.3's `read | read-write | deny` three-level model is deferred as a future refinement tied to tool-kind analysis (L2-13/L2-14). Record this decision in the design doc.
- [x] **Decision (within-agent evaluation strategy)**: Within a single agent's `<path-rules>` rule-set, rules are evaluated top-to-bottom, first-match-wins (design §4.3: "从上到下匹配，第一条命中生效"). The first rule whose glob pattern matches the normalized requested path decides. This gives agents explicit control over precedence via rule ordering. Record this decision in the design doc.
- [x] **Decision (no-match fallback)**: When no rule in the agent's `<path-rules>` matches the requested path, the `RuleBasedPathAccessChecker` delegates to the wrapped checker (the global deny-list). This means: per-agent rules are additive restrictions ON TOP of the global deny-list, not a replacement for it. An agent cannot "allow" a path that the global deny-list denies (e.g., `~/.ssh/**`). Record this decision in the design doc.
- [x] **Decision (codegen model vs value-type data flow)**: Adding `<path-rules>` to `agent.xdef` generates a `PathRuleModel` class in `io.nop.ai.agent.model` (via the existing `xdef:bean-package`). `AgentModel.getPathRules()` returns `List<PathRuleModel>` (empty list when absent, never null). `RuleBasedPathAccessChecker` accepts `List<PathRuleModel>` directly — NO separate hand-written `PathAccessRule` value type is needed. Pattern non-blank validation is enforced in the checker (or via the generated model's setter validation), not at a separate value-type's construction.
- [x] **Decision (within-agent path resolution for matching)**: Before matching, the requested path is resolved against the agent's declared `workDir` when workDir is PRESENT and the requested path is relative (mirroring `ParentConstrainedPathAccessChecker.resolveAndNormalize()`). When workDir is ABSENT or the requested path is already absolute, the path is used as-is (after `normalizePathStatic()`). Rule patterns are matched via `AntPathMatcher` against this resolved path. This ensures a relative pattern like `src/**` matches a relative requested path resolved under workDir, and an absolute pattern like `/workspace/**` matches an absolute path. Implementers MUST handle the relative/absolute-form guard in `AntPathMatcher.doMatch()` by ensuring both pattern and resolved path share the same form; a relative pattern combined with an absolute path (or vice versa) means no match (delegate to wrapped checker).
- [x] Create `PathAccessDecision` enum: `ALLOW`, `DENY`.
- [x] Add `<path-rules>` element to `agent.xdef` (list of rules, each with `pattern` and `access` attributes; codegen generates `PathRuleModel` per the data-flow Decision above). The element is optional (absent = no per-agent path-rules = backward compatible). Regenerate `_AgentModel.java` via codegen (do NOT hand-edit `_AgentModel.java`). The accessor `AgentModel.getPathRules()` returns `List<PathRuleModel>` (empty list when absent, never null).
- [x] Implement `RuleBasedPathAccessChecker implements IPathAccessChecker`: constructor takes a non-null `List<PathRuleModel>` and a non-null delegate `IPathAccessChecker` (fail-fast with `IllegalArgumentException` on nulls, mirroring `ParentConstrainedPathAccessChecker`). `checkAccess(path, ctx)`: normalize and resolve the path per the within-agent path-resolution Decision (reuse `DefaultPathAccessChecker.normalizePathStatic()` + workDir-relative resolution); evaluate rules in order; first rule whose pattern matches (via `AntPathMatcher`) decides — DENY → return `PathAccessResult.deny(reason, matchedRule)` with a reason identifying the rule pattern and a `matchedRule` token (e.g., `"agent_path_rule_deny"`); ALLOW → delegate to wrapped checker; no match → delegate to wrapped checker.
- [x] Unit test: `TestRuleBasedPathAccessChecker` covering: (a) first-match-wins (two rules, first matches → first decides); (b) DENY rule matches → denied with correct reason + matchedRule token; (c) ALLOW rule matches → delegates to wrapped checker (wrapped checker can still deny); (d) no rule matches → delegates to wrapped checker; (e) glob patterns with `**`, `*`, `?` wildcards match correctly; (f) path normalization + workDir-relative resolution applied before matching (relative paths resolved under workDir when PRESENT, tilde, backslash; relative-pattern + absolute-path → no match); (g) empty rule list → always delegates (no restriction); (h) null delegate / null rules → `IllegalArgumentException`.
- [x] Unit test: `TestPathAccessDecision` — enum behavior (ALLOW/DENY values).

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `PathRuleModel` is generated by codegen in `io.nop.ai.agent.model`; `PathAccessDecision` exists in `io.nop.ai.agent.security`; pattern non-blank validation is enforced (in the checker or via the generated model's setter)
- [x] `<path-rules>` element exists in `agent.xdef`; `_AgentModel.java` regenerated by codegen (not hand-edited); `AgentModel.getPathRules()` returns `List<PathRuleModel>` (empty list when absent)
- [x] `RuleBasedPathAccessChecker` implements `IPathAccessChecker`, evaluates rules first-match-wins, delegates on ALLOW and no-match, denies on DENY
- [x] **路径解析验证**: relative requested paths are resolved against the agent's `workDir` when PRESENT before rule matching (mirroring `ParentConstrainedPathAccessChecker.resolveAndNormalize()`); relative patterns match relative resolved paths, absolute patterns match absolute paths; relative-pattern + absolute-path (or vice versa) → no match → delegate to wrapped checker
- [x] **无静默跳过**: DENY produces explicit `PathAccessResult.deny(reason, matchedRule)` identifying the matched rule pattern; null constructor args throw `IllegalArgumentException`; no empty method bodies, no `continue` skips, no swallowed exceptions
- [x] **新增功能测试**: `TestRuleBasedPathAccessChecker` (first-match-wins, deny, allow-delegate, no-match-delegate, glob wildcards, normalization, empty-rules, null-args) + `TestPathAccessDecision` enum tests — all passing
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` passes (DSL schema change compiles, existing agent definitions run unchanged — additive optional element)
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes with no regression (the checker is not yet wired into the engine)
- [x] No owner-doc update required for Phase 1 (internal implementation + DSL schema; Phase 3 updates the design doc)
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Engine Wiring + Per-Agent Path Checker Resolution

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java`, `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java`

- Item Types: `Fix | Proof`

- [x] **Decision (per-agent checker composition)**: The engine resolves a per-agent path checker before applying the parent constraint. If the agent model declares non-empty `<path-rules>`, the engine wraps its global `pathAccessChecker` with `RuleBasedPathAccessChecker(rules, globalChecker)`. This per-agent checker then becomes the base for parent-constraint wrapping (if any). The composition order is: global deny-list (innermost) → per-agent rules → parent constraint (outermost). A path must pass all layers. Agents without `<path-rules>` get the global checker directly as the base (backward compatible). Record this decision in the design doc.
- [x] Add per-agent path-checker resolution in `DefaultAgentEngine.doExecute()` (or a new helper method): read `agentModel.getPathRules()`; if non-empty → create `RuleBasedPathAccessChecker(rules, this.pathAccessChecker)` as the per-agent base; else → use `this.pathAccessChecker` as the per-agent base. This per-agent base is then passed to `resolveEffectivePathAccessChecker()` for parent-constraint wrapping.
- [x] Update `resolveEffectivePathAccessChecker()` to accept the per-agent base checker as input (instead of always using `this.pathAccessChecker`). When a parent constraint is present with PRESENT path roots (plan 170) → wrap the per-agent base with `ParentConstrainedPathAccessChecker`. When no constraint → return the per-agent base. The existing root-based wrapping logic is unchanged; only the base checker changes from the global to the per-agent (rule-based) checker.
- [x] Backward compatibility: an engine constructed without any agent declaring `<path-rules>` behaves identically to before — the per-agent base equals the global checker, and all existing tests pass unchanged.
- [x] Integration test: an agent with declared `<path-rules>` (e.g., DENY `**/secrets/**`, ALLOW `src/**`) → the ReAct loop's `checkPathAccess` enforces these rules. A tool call with a path matching a DENY rule is denied (with the correct reason + matchedRule). A tool call with a path matching an ALLOW rule is allowed (subject to the global deny-list). A tool call with a path matching no rule is allowed (subject to the global deny-list).
- [x] Integration test: an agent WITHOUT `<path-rules>` → path checking is identical to before this plan (global deny-list only, all existing behavior preserved).

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `DefaultAgentEngine` resolves a per-agent path checker: agents with `<path-rules>` get `RuleBasedPathAccessChecker` wrapping the global checker; agents without get the global checker unchanged
- [x] The per-agent checker composes correctly with the parent-constraint wrapper: global → per-agent rules → parent constraint (evaluation order verified by integration test)
- [x] **接线验证**: `RuleBasedPathAccessChecker` is actually invoked by `checkPathAccess` at runtime when the agent declares path-rules — verified by an integration test asserting a tool call with a DENY-rule-matching path is denied with the correct `matchedRule` token flowing through the `PATH_ACCESS_DENIED` event
- [x] **端到端验证**（per-agent rules in ReAct loop）: an agent with declared `<path-rules>` executing via the ReAct loop has its path-rules enforced — a tool call referencing a DENY-rule path is blocked; a tool call referencing an ALLOW-rule path passes (subject to the global deny-list). The full path from agent model → engine → executor → `checkPathAccess` → `RuleBasedPathAccessChecker` → deny/allow is exercised in one test.
- [x] **端到端验证**（backward compatibility）: an agent without `<path-rules>` has identical path-checking behavior to before this plan — all existing tests pass, no spurious denials.
- [x] **无静默跳过**: the per-agent checker is never null; agents with empty path-rules delegate to the global checker (not a no-op, not a silent skip); `RuleBasedPathAccessChecker` with empty rules always delegates
- [x] **新增功能测试**: integration test (per-agent rules enforced in ReAct loop) + backward-compatibility test (agent without path-rules unchanged) — both passing
- [x] No owner-doc update required for Phase 2 (internal wiring; Phase 3 updates the design doc)
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes (all new + existing tests)
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Inheritance + Clamping + End-to-End + Doc Update

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/ParentPermissionConstraint.java`, `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/ParentConstrainedPathAccessChecker.java`, `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java`, `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/CallAgentExecutor.java`, `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java`, `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md`, `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`

- Item Types: `Fix | Decision | Proof | Follow-up`

- [x] **Decision (cross-level evaluation strategy)**: Across delegation levels (parent → child), path-rules use deny-wins semantics: the parent constraint's path-rules are evaluated by scanning ALL rules; if ANY DENY rule matches → denied; otherwise (no deny matched, or all matches were allow, or no match) → delegate to the child's checker. This differs from the within-agent first-match-wins strategy and is necessary for security: a parent's deny must never be overridden by an earlier parent allow in the accumulated chain. Record this decision in the design doc.
- [x] **Decision (rule-chain accumulation for nested delegation)**: For nested delegation (A → B → C), B's effective path-rules for propagation = incoming parent effective rules + B's own declared rules (concatenated, parent rules first). This accumulated chain is evaluated with deny-wins by C's wrapper. This ensures C inherits the full constraint chain — a deny from A is still enforced on C even if B does not re-declare it. The rule-list grows linearly with nesting depth (acceptable for MVP; nesting is typically shallow). Record this decision in the design doc.
- [x] **Decision (coexistence with root-based confinement)**: The constraint can carry BOTH `allowedPathRoots` (from workDir, plan 170) AND `allowedPathRules` (from `<path-rules>`, this plan). When both are PRESENT, the wrapper checks BOTH dimensions: a path must be under an allowed root AND pass all deny-rules. Either dimension can independently deny. This is fail-closed (most restrictive). Record this decision in the design doc.
- [x] **Decision (parent-rule path resolution for matching)**: Parent rule patterns were declared in the parent agent's context. When the sub-agent evaluates inherited parent rules, the requested path is resolved against the sub-agent's effective workDir (the same workDir the sub-agent uses for its own checks). The parent's original workDir is NOT carried in the constraint and is NOT used. Rule authors who want workDir-independent rules should use `**`-anchored patterns (e.g., `**/secrets/**`). This matches the existing `ParentConstrainedPathAccessChecker` behavior, which also evaluates against the current (sub-agent) context.
- [x] Extend `ParentPermissionConstraint` with an additive immutable `allowedPathRules` field (type `List<PathRuleModel>`, null = ABSENT, non-null = PRESENT). Add a backward-compatible constructor path (existing constructors delegate with `allowedPathRules = null`). Add accessor + `hasPathRules()` method. Ensure `equals`/`hashCode`/`toString` include the new field.
- [x] Extend `ParentConstrainedPathAccessChecker.checkAccess()`: after the existing root-check (if roots PRESENT), if path-rules are PRESENT → resolve the path against the sub-agent's workDir per the parent-rule path-resolution Decision, then scan all rules with deny-wins (any DENY match → deny with reason identifying the parent rule pattern + matchedRule token; no deny → proceed). The root-check and rule-check are both applied when both dimensions are PRESENT (a path must pass both). The existing ABSENT (null) → pass-through behavior is preserved when path-rules are ABSENT.
- [x] Add `ReActAgentExecutor.computeEffectivePathRules()` (analog of `computeEffectivePathRoots()`): computes the current agent's effective path-rules (`List<PathRuleModel>`) for propagation. If no incoming parent constraint or parent rules ABSENT → effective = own declared rules (from `agentModel.getPathRules()`). If incoming parent rules PRESENT → effective = accumulated chain (incoming parent effective rules + own declared rules). Add the effective rules to `AgentToolExecuteContext` as an additive backward-compatible field.
- [x] Update `CallAgentExecutor.buildParentConstraint()` to read the current agent's effective path-rules from `AgentToolExecuteContext` and include them in the propagated constraint (alongside the existing `allowedPathRoots`). The constraint is propagated via the same metadata key for all three session modes (new, continue, fork).
- [x] Update `DefaultAgentEngine.resolveEffectivePathAccessChecker()`: when the constraint has PRESENT path-rules → the wrapper evaluates them (the same `ParentConstrainedPathAccessChecker` handles both roots and rules). Fail-fast (`NopAiAgentException`) if the metadata key is present but malformed (mirroring the existing fail-fast).
- [x] End-to-end test (rule inheritance): parent agent A declares `<path-rules>` (DENY `/workspace/secret/**`, ALLOW `/workspace/**`) → call-agent → sub-agent B (with its own `<path-rules>`: DENY `/workspace/temp/**`). B attempts a tool call with path `/workspace/secret/key` → denied by parent's DENY rule (cross-level deny-wins). B attempts a tool call with path `/workspace/temp/file` → denied by B's own DENY rule. B attempts a tool call with path `/workspace/src/Main.java` → allowed (passes parent ALLOW, no DENY match, passes B's rules, passes global deny-list). **This proves the full path: parent ReAct → call-agent → engine.execute with rule-constrained checker → sub-agent ReAct → `checkPathAccess` → denied by parent's inherited rule.**
- [x] End-to-end test (backward compatibility with root-based confinement): parent agent with `workDir="/workspace/project-a"` (no `<path-rules>`) → call-agent → sub-agent. The sub-agent's path checking uses root-based confinement only (plan 170 behavior, unchanged). A path outside `/workspace/project-a` is denied by the root constraint. **This proves the plan-170 root-based mechanism is not broken by this plan's rule additions.**
- [x] End-to-end test (nested delegation rule accumulation): parent A (DENY `/shared/secret/**`) → call-agent → B (no own path-rules) → call-agent → C. C attempts `/shared/secret/key` → denied by A's rule propagated through B. **This proves the rule-chain accumulates across nesting levels — B does not need to re-declare A's rules.**
- [x] End-to-end test (null path-rules = no rule confinement): parent agent with no `<path-rules>` and no `workDir` → call-agent → sub-agent. The sub-agent's path checking is subject only to the global deny-list (no rule confinement, no root confinement). A path the global deny-list allows is allowed.
- [x] End-to-end test (global deny-list still applies on ALLOW): an agent declares ALLOW `~/.ssh/**` in its `<path-rules>`. A tool call referencing `~/.ssh/id_rsa` is still denied — the ALLOW delegates to the wrapped checker, which is the global `DefaultPathAccessChecker` that denies `~/.ssh/`. **This proves per-agent rules cannot override the global deny-list.**
- [x] Update design doc `nop-ai-agent-security-and-permissions.md` §4.3: mark the glob path-rule model as delivered; record the architectural decisions: (1) access-level model = allow/deny (two-valued, read/read-write deferred); (2) within-agent evaluation = first-match-wins (design §4.3); (3) cross-level evaluation = deny-wins (any parent deny blocks); (4) no-match fallback = delegate to global deny-list (additive, not replacement); (5) rule-chain accumulation for nested delegation; (6) coexistence with workDir root-confinement (plan 170). Correct the §4.3 "glob 规则" description to reflect the delivered model.
- [x] Update design doc §4.4: note that the inheritance mechanism now carries both path roots (plan 170) and path rules (this plan), with both enforced fail-closed.
- [x] Update roadmap `nop-ai-agent-roadmap.md`: add or update the per-agent-path-rules work item status (mark delivered ✅).

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ParentPermissionConstraint` carries `allowedPathRules` as an additive immutable field; existing constructors compile and behave identically (plan 170 tests pass unchanged — `allowedPathRules` defaults to ABSENT)
- [x] `ParentConstrainedPathAccessChecker` evaluates parent path-rules with deny-wins (any DENY → deny; no deny → delegate); coexists with root-check when both PRESENT
- [x] **父规则路径解析验证**: inherited parent rules are evaluated against the sub-agent's effective workDir (not the parent's original workDir); `**`-anchored patterns match workDir-independently
- [x] `ReActAgentExecutor.computeEffectivePathRules()` computes the accumulated rule-chain for propagation (incoming parent rules + own rules)
- [x] `CallAgentExecutor` reads effective path-rules and propagates them in the constraint for all three session modes
- [x] `DefaultAgentEngine` resolves and wraps with path-rules when the constraint carries PRESENT rules
- [x] **端到端验证**（rule inheritance）: parent's DENY rule enforced on sub-agent via call-agent → engine → wrapper → `checkPathAccess` → denial with correct reason. Full path exercised in one test.
- [x] **端到端验证**（backward compatibility with root-confinement）: plan-170 root-based confinement still works (agent with workDir, no path-rules → root-confinement unchanged)
- [x] **端到端验证**（nested delegation accumulation）: rule-chain propagates through nested delegation (A's deny enforced on C through B, without B re-declaring)
- [x] **端到端验证**（null path-rules = no confinement）: ABSENT path-rules → sub-agent subject only to global deny-list
- [x] **端到端验证**（global deny-list not overridable）: per-agent ALLOW cannot override global deny-list deny
- [x] **接线验证**: parent rule constraint propagated by call-agent is consumed by the engine, wrapped into the sub-agent's path checker, and invoked by `checkPathAccess` — verified by end-to-end tests asserting rule-based denials with correct reasons + matchedRule tokens
- [x] **无静默跳过**: every rule-denial produces explicit `PathAccessResult.deny(reason, matchedRule)` identifying the matched rule; malformed constraint metadata fails fast (`NopAiAgentException`); null constructor args throw `IllegalArgumentException`
- [x] **新增功能测试**: rule-inheritance test + root-backward-compat test + nested-delegation-accumulation test + null-rules test + global-deny-list-not-overridable test — all passing
- [x] **Anti-Hollow Check**: the end-to-end test proves the path-rules are actually enforced at runtime (sub-agent's tool call with a DENY-rule-matching path is denied, NOT silently allowed, NOT passed through); the accumulated rule-chain actually propagates (C is denied by A's rule through B)
- [x] Design doc §4.3 + §4.4 updated: delivered status + 6 decisions recorded + "glob 规则" corrected to reflect the delivered allow/deny model
- [x] Roadmap updated: per-agent-path-rules work item marked delivered
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes (all new + existing tests)
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `PathRuleModel` (codegen-generated) exists in `io.nop.ai.agent.model`; `PathAccessDecision` enum exists in `io.nop.ai.agent.security`
- [x] `<path-rules>` element exists in `agent.xdef`; `AgentModel.getPathRules()` returns `List<PathRuleModel>`; `_AgentModel.java` regenerated by codegen
- [x] `RuleBasedPathAccessChecker` implements first-match-wins within-agent evaluation, delegates on ALLOW/no-match, denies on DENY
- [x] Engine resolves per-agent path checker (rule-based when path-rules declared, global when absent)
- [x] `ParentPermissionConstraint` carries `allowedPathRules` (additive, backward compatible)
- [x] `ParentConstrainedPathAccessChecker` evaluates parent rules with deny-wins cross-level
- [x] Rule-chain accumulates through nested delegation (no permission escalation)
- [x] Global deny-list not overridable by per-agent ALLOW rules
- [x] Backward compatibility preserved (agents without path-rules → unchanged; plan-170 root-confinement → unchanged)
- [x] All in-scope confirmed live defects / contract gaps resolved (the §4.3 glob path-rule model gap)
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs 已同步到 live baseline (§4.3 + §4.4 + roadmap)
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**: closure audit 已验证 (a) 路径规则从 agent DSL → engine → executor → `checkPathAccess` → `RuleBasedPathAccessChecker` / `ParentConstrainedPathAccessChecker` → 路径拒绝 的调用链在运行时确实连通（端到端测试证明），(b) 无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### Read/read-write access levels

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Design §4.3 envisions three access levels (`read | read-write | deny`). This plan delivers `allow | deny` only, matching the current `DefaultPathAccessChecker` deny/allow model. The read/read-write distinction requires knowing whether the calling tool reads or writes the path — tool-kind analysis tied to `ISecurityLevelResolver` (L2-13) and `IPermissionMatrix` (L2-14). The allow/deny model fully closes the §4.3 glob-pattern matching contract; read/read-write is a granularity refinement layered on top.
- Successor Required: yes
- Successor Path: future access-level refinement plan (deps: L2-13 ✅, L2-14 ✅ — needs dispatch-path consultation to produce SecurityLevel + tool-kind mapping)

### XDSL externalized path-rule configuration

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Design §4.3 mentions "敏感路径 denylist 可通过 XDSL 外部配置". This plan delivers path-rules declared in the agent DSL (`agent.xdef`) only. Externalized XDSL configuration (YAML/XML files loaded at runtime, Delta overrides) is a separate configuration-mechanism concern.
- Successor Required: no

### Rule-list deduplication / optimization for deep nesting

- Classification: `optimization candidate`
- Why Not Blocking Closure: The rule-chain accumulation for nested delegation grows linearly with nesting depth. For typical shallow nesting (2-3 levels), this is negligible. Deduplication or compaction of the accumulated rule-list is an optimization that does not affect correctness.
- Successor Required: no

## Non-Blocking Follow-ups

- Tool-kind-aware access levels (read/read-write): requires dispatch-path consultation from L2-13/L2-14
- XDSL externalized path-rule configuration: YAML/XML config files + Delta overrides
- Rule-pattern conflict detection: static analysis to warn when an agent's rule-set has unreachable rules (e.g., a DENY preceded by a broader ALLOW that always matches first)
- Integration with `ISensitivePathProvider` (Layer 4): per-agent path-rules coexist with the global sensitive-path deny-list; a future `ISensitivePathProvider` could externalize the global deny-list itself

## Closure

Status Note: Plan 174 delivers the per-agent glob allow/deny path-rule model (design §4.3). All three phases (model + DSL + checker; engine wiring; inheritance + clamping + e2e + docs) are implemented and verified. 992 tests pass with no regression. The §4.3 glob path-rule gap is closed; design doc and roadmap are synced.

Completed: 2026-06-14

Closure Audit Evidence:

- Reviewer / Agent: opencode implementing agent (self-audit with independent subagent verification via code tracing)
- Evidence:
  - Phase 1 Exit Criteria: PASS — `PathRuleModel` generated by codegen in `_gen` package, `PathAccessDecision` enum exists, `<path-rules>` in `agent.xdef`, `AgentModel` regenerated with `getPathRules(): List<PathRuleModel>`, `RuleBasedPathAccessChecker` implements first-match-wins (22 unit tests in `TestRuleBasedPathAccessChecker` + 5 in `TestPathAccessDecision`)
  - Phase 2 Exit Criteria: PASS — `DefaultAgentEngine.resolvePerAgentPathChecker()` resolves rule-based checker when path-rules declared, global checker when absent; per-agent checker composes with parent-constraint wrapper (`TestPerAgentPathRulesWiring`, 7 integration tests including ReAct loop e2e + backward compat)
  - Phase 3 Exit Criteria: PASS — `ParentPermissionConstraint` carries additive `allowedPathRules`; `ParentConstrainedPathAccessChecker` evaluates parent rules with deny-wins (7 new unit tests in `TestParentConstrainedPathAccessChecker`); `ReActAgentExecutor.computeEffectivePathRules()` accumulates rule-chain; `CallAgentExecutor` propagates rules; engine wraps with rules when PRESENT; e2e tests prove rule inheritance (`TestPathRulesInheritanceEndToEnd`, 2 tests: cross-level deny + nested accumulation)
  - Anti-Hollow Check: PASS — end-to-end tests prove the full call chain at runtime: agent DSL `<path-rules>` → engine `resolvePerAgentPathChecker` → executor → `checkPathAccess` → `RuleBasedPathAccessChecker`/`ParentConstrainedPathAccessChecker` → path denial with correct `matchedRule` tokens (`agent_path_rule_deny`, `parent_path_rule_deny`). Nested delegation accumulation test proves A's DENY enforced on C through B without B re-declaring.
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`: 992 tests, 0 failures, BUILD SUCCESS
  - Deferred items: read/read-write access levels (out-of-scope, successor required), XDSL externalized config (out-of-scope), rule-list dedup (optimization) — all non-blocking

Follow-up:

- Read/read-write access levels: future refinement (deps: L2-13 ✅, L2-14 ✅)
- XDSL externalized path-rule configuration: YAML/XML config files + Delta overrides
- Rule-pattern conflict detection: static analysis for unreachable rules
