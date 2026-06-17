# 169 Sub-Agent Permission Inheritance Enforcement

> Plan Status: completed
> Module: nop-ai-agent
> Work Item: sec-4.4 (sub-agent permission inheritance)
> Last Reviewed: 2026-06-14
> Source: Carry-over from plan 168 (`ai-dev/plans/168-nop-ai-agent-call-agent-tool.md`, Deferred "Sub-agent permission inheritance enforcement", Successor Required: yes); design `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §4.4
> Related: Plan 168 (call-agent tool — fork+exec delivered, permission inheritance deferred), Plan 161 (forkSession — parent-child session link), Plan 134 (L1-6 IPermissionProvider + DefaultPermissionProvider), Plan 139 (L1-8 IToolAccessChecker + DefaultToolAccessChecker), Plan 141 (L1-9 IPathAccessChecker + DefaultPathAccessChecker)

## Purpose

Enforce the design contract from `nop-ai-agent-security-and-permissions.md` §4.4: "子 Agent 只能继承或收缩权限，不能提升". When a parent agent invokes a sub-agent via `call-agent`, the sub-agent's effective tool permission must be the intersection of the parent's allowed tool set and the sub-agent's own tool set. Currently the sub-agent receives the engine's global permission pipeline with only its own agent DSL rules applied — a sub-agent can declare and use tools the parent agent cannot access, violating the security boundary. This plan closes that gap for tool-level permissions.

## Current Baseline

- `CallAgentExecutor` (plan 168 ✅ delivered) invokes `IAgentEngine.execute()` for sub-agents via the fork+exec model. It constructs an `AgentMessageRequest(targetAgentId, input, subSessionId, metadata)` and calls `engine.execute()`. **No parent permission context is propagated** — the metadata map is always null.
- `DefaultAgentEngine.resolveExecutor()` passes the engine's own `permissionProvider`, `toolAccessChecker`, `pathAccessChecker` to every `ReActAgentExecutor` it creates, including sub-agent executors. The sub-agent gets the same engine-level pipeline with its own agent DSL permission rules resolved by `DefaultPermissionProvider` — **no parent constraint is applied**.
- `DefaultPermissionProvider.resolve(toolName, agentName, sessionId)` merges 3 sources (session-level > agent DSL > default rules) using deny-first semantics. It resolves permission per-tool based on the **current agent's** rules. There is no mechanism to intersect with a parent's permission.
- `DefaultToolAccessChecker.checkAccess(toolName, ctx)` checks a hardcoded deny-list of high-risk tools (bash, write-file, delete-file, etc.). It does not consider parent context.
- `AgentModel.getTools()` returns the agent's declared tool set (from `agent.xml` `<tools>` element). This is the agent's maximum tool permission — the permission provider and tool access checker can only further restrict it, never expand it.
- `AgentToolExecuteContext` (plan 168 ✅) carries `engine`, `messenger`, `sessionId`, `agentName` — but **NOT** the current agent's tool set or any permission constraint. It is constructed by `ReActAgentExecutor` at line ~470, where the executor has access to `agentModel` (the current agent's model with its tool set).
- Design doc `nop-ai-agent-security-and-permissions.md` §4.4 specifies: 工具权限 = 父权限 ∩ 子配置（交集或收缩）; 未明确授权的提升行为一律拒绝. This contract is documented but **not enforced** in code.
- grep for `permission.*inherit|SubAgent.*Permission|clamp.*permission|derivedPermission` in `nop-ai-agent/src/main/java` returns **zero matches** — confirmed unimplemented.

## Goals

- When a parent agent invokes a sub-agent via call-agent, the sub-agent's effective tool permission is the intersection of the parent's **effective (clamped)** allowed tool set and the sub-agent's own declared tool set (design §4.4: "工具权限 = 父权限 ∩ 子配置"). For a top-level parent, the effective set equals its declared set; for a nested parent, the effective set is already clamped by its own parent — so nested delegation propagates the clamped set, not the declared set.
- The enforcement is fail-closed: if a tool is not in the parent's allowed set, it is denied for the sub-agent regardless of the sub-agent's own configuration
- The enforcement is backward-compatible: single-agent executions (no parent constraint present) are completely unaffected — no behavioral change, no performance impact
- The parent's effective tool set is captured at call-agent invocation time and propagated to the sub-agent execution via a well-defined mechanism
- The enforcement is observable and testable: a sub-agent that declares tools not in the parent's set cannot use them, and the denial reason explicitly identifies the parent constraint
- Design doc §4.4 is updated to reflect the delivered enforcement and the three architectural decisions (constraint representation + enforcement mechanism + propagation mechanism)

## Non-Goals

- Path permission inheritance (§4.4 also mentions "文件权限 = 父权限 ∩ 子配置" — this plan covers tool permission only; path permission clamping requires rule-pattern intersection logic, not set-intersection, and is deferred)
- Layer 2-4 security extensions (ISecurityLevelResolver L2-13, IPermissionMatrix L2-14, IApprovalGate L3-5 — these are separate roadmap items with their own plans)
- Per-tool parameter or argument-level permission restrictions (e.g., "sub-agent can call write-file but only within /tmp/" — this is a different enforcement layer)
- Dynamic permission provider chain composition (the constraint wraps the existing pipeline; it does not replace or restructure it)
- Permission constraint persistence across session continuation (the constraint is re-derived on each call-agent invocation from the parent's current tool set; persisting it with the session is a refinement)
- Cross-process permission propagation (single-process only; cross-process is L4-2/L4-8)
- Session-level permission restrictions are **not** inherited through the constraint. The constraint is derived from the agent's declared tool set (clamped by the parent chain), NOT the runtime-session-resolved permission set (e.g., session-level tool denials that `DefaultPermissionProvider` applies against a live session). The constraint is therefore a conservative upper bound on declared capability but does not reflect session-specific runtime overrides. Inheriting session-level restrictions is out of scope.

## Scope

### In Scope

- Decision: how to represent the parent's permission constraint (the parent agent's **effective (clamped)** allowed tool set — incoming parent constraint ∩ its own declared tool set; equals the declared set for a top-level agent)
- Decision: how to propagate the constraint from call-agent to the sub-agent execution (via request metadata)
- Decision: how to enforce the constraint (wrapping the sub-agent's tool access checker with an intersection check at executor-resolution time)
- A permission constraint model carrying the parent's allowed tool names + audit metadata
- An enforcement wrapper component that intersects the parent's allowed tool set with the sub-agent's tool access pipeline (fail-closed)
- Wiring: `AgentToolExecuteContext` carries the current agent's **effective (clamped)** tool set; `CallAgentExecutor` captures it and propagates the constraint; `DefaultAgentEngine` applies the constraint when resolving the sub-agent's executor
- Fail-closed behavior with descriptive denial reasons
- Backward compatibility: no constraint = no change
- Unit tests + integration tests + end-to-end test
- Design doc §4.4 update

### Out Of Scope

- Path permission inheritance (deferred — requires rule-pattern intersection)
- Layer 2-4 security extensions (separate roadmap items)
- Argument-level or parameter-level permission restrictions
- Cross-process permission propagation
- Permission constraint persistence across session continuations

## Execution Plan

### Phase 1 - Permission Constraint Contract + Enforcement Component

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/` (permission constraint model + enforcement wrapper)

- Item Types: `Decision | Proof`

- [x] **Decision（constraint representation）**: The parent's permission constraint carries the parent agent's **effective (clamped)** allowed tool set — the set of tool names the parent can actually invoke in the current execution, NOT merely the names it declares. For a top-level agent (no incoming parent constraint), the effective set equals its declared tool set (`AgentModel.getTools()`). For a nested agent, the effective set is the intersection of the incoming parent constraint and the agent's own declared tool set. Using the effective (clamped) set — rather than the declared set — is what makes nested delegation safe: when a middle agent B delegates to C, C's constraint is built from B's effective set (already clamped to A's constraint), so C cannot regain tools that A's constraint removed from B. The constraint is an immutable set of allowed tool names plus audit metadata (parent agent name, parent session ID). Record this decision in the design doc.

- [x] **Decision（enforcement mechanism）**: The enforcement wraps the sub-agent's existing `IToolAccessChecker` with an additional intersection check. When a parent constraint is present, any tool NOT in the parent's allowed set is denied (fail-closed) before the sub-agent's own rules are evaluated — producing a denial reason that explicitly identifies "parent permission constraint". When no constraint is present (single-agent execution), the wrapper is a no-op pass-through. The wrapper is applied at executor-resolution time in `DefaultAgentEngine`, not hardcoded into the default checkers — preserving backward compatibility for all existing engine construction paths. Record this decision in the design doc.

- [x] Implement the permission constraint model: an immutable value object carrying (a) the parent's allowed tool name set, (b) parent agent name, (c) parent session ID — all for audit traceability

- [x] Implement the enforcement wrapper component that wraps an existing `IToolAccessChecker`:
  - When a constraint is present and the requested tool is NOT in the parent's effective (clamped) allowed set → deny with reason "denied by parent permission constraint: tool '{name}' not in parent agent '{parentAgent}' allowed set"
  - When a constraint is present and the requested tool IS in the parent's effective (clamped) allowed set → delegate to the wrapped checker (normal behavior)
  - When no constraint is present → delegate entirely (no-op pass-through)

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] The permission constraint model exists, is immutable, and carries the parent's effective (clamped) allowed tool names + parent agent name + parent session ID
- [x] The enforcement wrapper correctly intersects: tool in both parent's effective set and child's declared set → delegates to child checker; tool only in child's declared set → denied by constraint; no constraint → pass-through
- [x] **无静默跳过**: when a constraint is present and a tool is denied by it, the denial reason explicitly identifies "parent permission constraint" — not a generic deny, not a silent skip
- [x] **新增功能测试**: unit test verifying intersection logic: (a) tool in both sets: delegates to child checker (child can still deny); (b) tool only in child set: denied by constraint with explicit reason; (c) no constraint: pass-through (backward compatible); (d) empty parent allowed set: all tools denied (maximum restriction)
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` passes
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` existing tests still pass (no regression — the wrapper is not yet wired into the engine)
- [x] No owner-doc update required for Phase 1 (internal implementation; Phase 3 updates the design doc)
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - call-agent Integration + Engine Wiring

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentToolExecuteContext.java` (carry current agent's tool set), `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/CallAgentExecutor.java` (constraint capture + propagation), `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java` (constraint application at executor resolution), `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java` (populate tool set in context)

- Item Types: `Decision | Fix | Proof`

- [x] **Decision（constraint propagation mechanism）**: The constraint propagates via `AgentMessageRequest.metadata` using a well-known metadata key (e.g., `"parentPermissionConstraint"`). This reuses the existing metadata infrastructure without adding new request fields or changing the `AgentMessageRequest` constructor signature. The engine reads this key in `doExecute()` and applies the constraint when resolving the executor. Record this decision in the design doc.

- [x] Add the current agent's **effective (clamped)** tool set to `AgentToolExecuteContext` as an additive field (e.g., `Set<String> allowedTools`). `ReActAgentExecutor` computes this field's value as the intersection of (a) the parent constraint it reads from the request metadata/context and (b) the current agent's **declared** tool set `agentModel.getTools()`. When no parent constraint is present (top-level agent), the effective set equals the declared set unchanged. **This clamping is what makes nested delegation correct**: a middle agent B's `allowedTools` is already clamped to A's constraint, so when `CallAgentExecutor` reads B's `allowedTools` to build C's constraint, C inherits B's clamped set rather than B's declared set. Without this, permission would escalate through nested delegation. Existing tools that don't read this field are unaffected.

- [x] **Backward-compatible API for `AgentToolExecuteContext`**: introducing the `allowedTools` field must not break existing call sites that construct this context (production `ReActAgentExecutor` plus existing test constructors in `TestSendMessageExecutor`, `TestCallAgentExecutor`, `TestAgentToolExecuteContext`). Use a backward-compatible shape — e.g., keep the existing constructor signature and add an overloaded constructor or builder that accepts the new field, with the field defaulting to a "no parent constraint / top-level" sentinel (effective set = declared set) for callers that don't supply it. The production `ReActAgentExecutor` site is intentionally updated to pass the effective set; all other existing call sites compile and behave unchanged. Do not change behavior of any call site that does not opt into the constraint.

- [x] Update `CallAgentExecutor` to: (a) read the current agent's **effective (clamped)** tool set from `AgentToolExecuteContext` (the `allowedTools` field populated by `ReActAgentExecutor`), (b) construct a permission constraint carrying the parent's effective tool set + parent agent name (`agentCtx.getAgentName()`) + parent session ID (`agentCtx.getSessionId()`), (c) put the constraint into the `AgentMessageRequest.metadata` under the well-known key before calling `engine.execute()`. The method inside `CallAgentExecutor` that builds the `AgentMessageRequest` must therefore have access to `AgentToolExecuteContext` (and thus the parent's effective tool set) so it can thread the constraint into the request — for all three session modes (new, continue, fork). (Describes the required data flow, not specific method signatures.)

- [x] Update `DefaultAgentEngine.doExecute()` to: (a) check for the permission constraint key in `request.getMetadata()`, (b) if present, wrap the engine's `toolAccessChecker` with the Phase 1 enforcement wrapper (parameterized by the constraint) before passing it to `resolveExecutor()`, (c) if absent, proceed unchanged (the existing `toolAccessChecker` is passed directly). The wrapping must be scoped to the sub-agent execution only — it must not leak into the engine's field state.

- [x] Ensure `resolveExecutor()` can accept an overridden `toolAccessChecker` (either by parameterizing `resolveExecutor()` or by having `doExecute()` construct the wrapper and pass it through). The engine's own `toolAccessChecker` field remains unchanged for other executions.

Exit Criteria:

- [x] `AgentToolExecuteContext` carries the current agent's **effective (clamped)** tool set as an additive, backward-compatible field (existing constructor/builder preserved so non-opt-in call sites compile unchanged)
- [x] `ReActAgentExecutor` populates the effective tool set as `incomingParentConstraint ∩ agentModel.getTools()` (or the declared set unchanged when no parent constraint exists) when constructing the context
- [x] `CallAgentExecutor` reads the parent's **effective (clamped)** tool set from `AgentToolExecuteContext`, builds the constraint, and threads it into the sub-agent request metadata for all three session modes
- [x] `DefaultAgentEngine` reads the constraint from metadata and wraps the sub-agent's tool access checker when present; proceeds unchanged when absent
- [x] **接线验证**: the constraint propagated by call-agent is actually consumed by the engine and applied to the sub-agent's executor (verified by an integration test asserting the sub-agent's tool access is constrained — a tool in the sub-agent's set but NOT in the parent's set is denied)
- [x] **无静默跳过**: if the constraint metadata key is present but the constraint object is null/malformed, the engine fails fast (throws or applies maximum restriction) rather than silently ignoring it
- [x] **新增功能测试**: integration test — parent agent with declared tool set {read-file, call-agent} → call-agent invokes sub-agent with declared tool set {read-file, write-file, bash} → sub-agent's write-file and bash tool calls are denied by parent constraint; read-file is allowed (passes through to sub-agent's own checker)
- [x] Backward compatibility: the `AgentToolExecuteContext` API change is introduced via an overloaded constructor/builder (existing test call sites in `ReActAgentExecutor`, `TestSendMessageExecutor`, `TestCallAgentExecutor`, `TestAgentToolExecuteContext` compile unchanged); existing call-agent tests (from plan 168) pass unchanged, and the no-constraint path behaves identically to before
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] No owner-doc update required for Phase 2 (Phase 3 updates the design doc)
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - End-to-End Verification + Design Doc Update

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/` (end-to-end tests), `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` (§4.4 status update + decisions), `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` (if a work-item entry exists)

- Item Types: `Proof | Follow-up`

- [x] End-to-end test: construct an engine with a mock `IChatService` that simulates tool calling. Parent agent's declared tool set = {read-file, call-agent}. Parent agent's ReAct loop emits a `call-agent` tool call targeting a sub-agent whose declared tool set = {read-file, write-file, bash}. The sub-agent's ReAct loop attempts to call `write-file` → denied by parent permission constraint (denial message identifies "parent permission constraint"). The sub-agent attempts `read-file` → allowed (passes intersection, sub-agent's own checker allows it). **This test proves the full path: parent ReAct → call-agent → engine.execute with constraint → sub-agent ReAct → tool denied by inherited constraint.**

- [x] End-to-end test (backward compatibility): a single-agent execution (no call-agent invocation, no constraint in metadata) has identical behavior to before this plan — all existing tools work, no spurious denials. This can reuse an existing test or add a focused assertion.

- [x] End-to-end test (nested delegation): parent A (declared tools: {read-file, call-agent}) → call-agent → sub-agent B (declared tools: {read-file, call-agent, write-file} — **B deliberately declares MORE than A's constraint allows**) → call-agent → sub-sub-agent C (declared tools: {read-file, write-file}). Because A's effective set is {read-file, call-agent}, B's effective set is clamped to {read-file, call-agent} (write-file dropped despite being declared). When B delegates to C, C's constraint = B's **effective** set {read-file, call-agent}, NOT B's declared set — so C's write-file is denied even though B declared it. **This proves the constraint propagates the clamped set through nested delegation, not the declared set; without the clamping fix, C would inherit write-file from B's declared set (permission escalation).** (Include if straightforward; defer if the test setup is excessively complex.)

- [x] Update design doc `nop-ai-agent-security-and-permissions.md` §4.4: mark the tool-permission inheritance enforcement as delivered. Record the three decisions: (1) constraint representation = parent's effective (clamped) tool set (incoming parent constraint ∩ its declared set; equals the declared set for a top-level agent), (2) enforcement mechanism = wrapping IToolAccessChecker at executor-resolution time, (3) propagation via AgentMessageRequest.metadata. Note that path-permission inheritance (also in §4.4) remains deferred with rationale.

- [x] Verify the enforcement is auditable: when a tool is denied by the parent constraint, the existing `IAuditLogger` records the denial (the denial flows through the same `TOOL_CALL_DENIED` event path in `ReActAgentExecutor` at line ~498-508). The audit entry's reason field identifies "parent permission constraint". No new audit infrastructure needed — the constraint denial produces a `ToolAccessResult.deny(...)` that flows through the existing audit path.

Exit Criteria:

- [x] **端到端验证**（constraint enforcement）: from parent agent ReAct loop → call-agent → engine.execute with constraint metadata → sub-agent ReAct → sub-agent's forbidden tool denied by parent constraint. The full path is exercised in one test.
- [x] **端到端验证**（backward compatibility）: single-agent execution unchanged — no spurious denials, all existing tests pass
- [x] **端到端验证**（nested delegation, if included）: constraint propagates the **clamped (effective)** set — not the declared set — through nested call-agent chains; sub-sub-agent inherits the clamped permission (the test uses a middle agent that declares more tools than its parent allows, proving C inherits the clamped set rather than B's declared set)
- [x] **接线验证**: the constraint flows from call-agent metadata → engine constraint application → sub-agent permission wrapper → tool denial (verified by the end-to-end test asserting the denial with the correct reason)
- [x] **无静默跳过**: constraint-denied tools produce explicit denial messages identifying "parent permission constraint" (not silent skips, not generic denies); the denial is auditable via the existing TOOL_CALL_DENIED event
- [x] **新增功能测试**: end-to-end constraint test + backward-compatibility test (+ nested delegation test if included) — all passing
- [x] **Anti-Hollow Check**: the end-to-end test proves the constraint is actually enforced at runtime (sub-agent's forbidden tool call is denied with the correct reason, NOT silently allowed, NOT passed through)
- [x] Design doc §4.4 updated: delivered status + 3 decisions recorded + path-permission deferral noted
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes (all new + existing tests)
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 Plan Status 改为 `completed`。

- [x] Sub-agent permission inheritance enforcement is functional (tool-set intersection verified by end-to-end test)
- [x] Enforcement is fail-closed (tool not in parent's set → denied, with descriptive reason identifying "parent permission constraint")
- [x] Backward compatibility preserved (single-agent executions unaffected — all existing tests pass, no spurious denials)
- [x] Constraint propagation works end-to-end: call-agent → request metadata → engine → sub-agent permission wrapper → tool denial
- [x] 必要 focused verification（unit + integration + end-to-end）已完成
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs 已同步到 live baseline（security-and-permissions.md §4.4）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 (a) 约束从 call-agent metadata → engine → sub-agent 权限 wrapper → 工具拒绝 的调用链在运行时确实连通（端到端测试证明），(b) 无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### Path permission inheritance

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Design §4.4 also specifies "文件权限 = 父权限 ∩ 子配置". Path permission uses rule-pattern matching (glob patterns from `IPathAccessChecker`), not set membership. Intersecting two rule sets requires a different mechanism (rule composition or combined pattern evaluation). Tool-set intersection (this plan) addresses the higher-priority security concern because tools are the direct capability-expansion vector — call-agent is explicitly described as "能力扩张入口" in the design doc. Path permission clamping is a refinement that does not leave a direct tool-escalation hole.
- Successor Required: yes
- Successor Path: 未来 path-permission-inheritance plan (deps: this plan + path-access design refinement)

### Per-tool parameter / argument-level permission restrictions

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: The current enforcement operates at tool-name granularity (is this tool in the parent's allowed set?). Argument-level restrictions (e.g., "sub-agent can call write-file but only within /tmp/") require parameter interception at a different layer. This is a Layer 2+ concern tied to ISecurityLevelResolver (L2-13) and IPermissionMatrix (L2-14).
- Successor Required: no

### Permission constraint persistence across session continuation

- Classification: `optimization candidate`
- Why Not Blocking Closure: When call-agent continues an existing sub-session (via `sessionId` parameter), the constraint is re-derived from the parent's current tool set on each invocation. Persisting the original constraint with the session (so a continued session retains its first-invocation constraint even if the parent's tool set later changes) is a refinement that does not affect the security guarantee — the parent's current tool set is always a valid constraint.
- Successor Required: no

### Nested-delegation deep-chain auditing

- Classification: `optimization candidate`
- Why Not Blocking Closure: For deeply nested delegation (A → B → C → D), the current enforcement clamps at each level (C inherits B's clamped set, which inherits A's set). The audit log records each denial but does not reconstruct the full chain (grandparent → parent → child) in a single entry. Deep-chain auditing is an observability refinement.
- Successor Required: no

## Non-Blocking Follow-ups

- Configuration option: allow agents to explicitly opt-in to wider sub-agent permissions for trusted scenarios (design §9 progressive enhancement — an XDSL config toggle, not a code change)
- Constraint caching: if constraint construction shows up in profiling (unlikely for tool-set set operations), cache the constraint per agent model

## Closure

Status Note: Sub-agent permission inheritance enforcement (tool-level) is delivered. The design §4.4 contract "工具权限 = 父权限 ∩ 子配置" is enforced: when a parent agent invokes a sub-agent via call-agent, the sub-agent's effective tool permission is the intersection of the parent's effective (clamped) allowed tool set and the sub-agent's own declared tool set. The enforcement is fail-closed, backward compatible (no constraint = no change), and the clamped-set propagation makes nested delegation safe (a middle agent B cannot pass tools it doesn't effectively have to sub-sub-agent C). Path-permission inheritance remains deferred (out-of-scope, different mechanism required).
Completed: 2026-06-14

Closure Audit Evidence:

- Reviewer / Agent: Independent closure auditor (fresh subagent session — distinct from the implementation session; satisfies Plan Guide Minimum Rule #12 independence requirement). Prior self-audit by the implementation agent was advisory only and is superseded by this independent audit.
- Audit Session: opencode independent closure-audit task (this session), 2026-06-14
- Evidence:
  - Phase 1 Exit Criteria: PASS — `ParentPermissionConstraint` (immutable value object: `final` class, `Set.copyOf`, `Collections.unmodifiableSet`, `io.nop.ai.agent.security.ParentPermissionConstraint`) + `ParentConstrainedToolAccessChecker` (enforcement wrapper, fail-closed intersection, explicit "parent permission constraint" denial reason) verified against live source. Unit tests `TestParentConstrainedToolAccessChecker` (18 tests, all passing) verify intersection logic, fail-closed, empty-set max-restriction, explicit denial reason, backward-compatible pass-through.
  - Phase 2 Exit Criteria: PASS — `AgentToolExecuteContext` extended with backward-compatible `allowedTools` field (11-arg constructor added, existing 10-arg constructor preserved and delegates with null). `ReActAgentExecutor.computeEffectiveAllowedTools()` (line ~936) computes clamped set = `incomingParentConstraint ∩ agentModel.getTools()`, populated into context at line ~483. `CallAgentExecutor.buildParentConstraint()` (line ~144) reads `allowedTools`, builds `ParentPermissionConstraint`, threads into request metadata via `buildConstraintMetadata()` for all 3 session modes (new/continue/fork — verified lines 127, 174). `DefaultAgentEngine.resolveEffectiveToolAccessChecker()` (line ~470) wraps checker from metadata (fail-fast with `NopAiAgentException` on malformed constraint — verified lines 478-483). Integration tests `TestSubAgentPermissionWiring` (7 tests, all passing) verify the wiring.
  - Phase 3 Exit Criteria: PASS — End-to-end tests `TestSubAgentPermissionEndToEnd` (3 tests, all passing): (1) `endToEndConstraintEnforcement` — parent → call-agent → sub-agent's write-file/bash denied by parent constraint, read-file allowed, TOOL_CALL_DENIED events published with "parent permission constraint" reason; (2) `backwardCompatibilitySingleAgentNoSpuriousDenials` — single-agent execution, no spurious denials; (3) `nestedDelegationPropagatesClampedSet` — A → B (declares write-file which A's constraint removes) → C, C's write-file denied because it inherits B's CLAMPED set (not declared set). Agent fixtures verified: `test-parent-agent.agent.xml` (tools: read-file,call-agent), `test-sub-agent-wide.agent.xml` (read-file,write-file,bash), `test-middle-agent.agent.xml` (read-file,call-agent,write-file), `test-leaf-agent.agent.xml` (read-file,write-file).
  - Full module test suite re-run by auditor: `./mvnw test -pl nop-ai/nop-ai-agent` → **824 tests, 0 failures, 0 errors** (matches implementation claim).
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` → exit code 0 (no unchecked items, Closure Evidence present).
  - Anti-Hollow Check: PASS — `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` → exit code 0 (0 critical, 0 high, 0 medium, 0 low findings). Runtime call chain traced via `endToEndConstraintEnforcement`: parent ReAct → call-agent tool call → `engine.execute` with constraint metadata → `resolveEffectiveToolAccessChecker` wraps checker → sub-agent ReAct → `toolAccessChecker.checkAccess` → `ParentConstrainedToolAccessChecker.checkAccess` → `ToolAccessResult.deny("denied by parent permission constraint: ...")` → `TOOL_CALL_DENIED` event published and asserted. No empty method bodies, no silent skips, no swallowed exceptions in the new components (`ParentPermissionConstraint`, `ParentConstrainedToolAccessChecker`).
  - Deferred honesty: PASS — all 4 deferred items classified as `out-of-scope improvement` (path permission, per-tool parameter) or `optimization candidate` (constraint persistence, deep-chain auditing) — NONE are in-scope live defects or contract drift. Each has a `Why Not Blocking Closure` rationale.
  - Owner doc sync: PASS — design doc `nop-ai-agent-security-and-permissions.md` §4.4 updated: "工具权限 = 父权限 ∩ 子配置 — 已交付 ✅", 3 decisions recorded (constraint representation = effective clamped set, enforcement = wrapping IToolAccessChecker, propagation via metadata), path-permission deferral noted. Roadmap `nop-ai-agent-roadmap.md` entry L4-1c (sec-4.4) marked delivered (✅).

Follow-up:

- Path permission inheritance (§4.4 "文件权限 = 父权限 ∩ 子配置") — deferred, requires rule-pattern intersection mechanism (see Deferred But Adjudicated)
- Per-tool parameter / argument-level permission restrictions — out-of-scope, Layer 2+ concern
- Permission constraint persistence across session continuation — optimization candidate

## Follow-up handled by 170-nop-ai-agent-path-permission-inheritance.md

> Additive annotation (2026-06-14). This completed plan is historical record; this section only records successor traceability and does not alter the closure above.

The deferred "Path permission inheritance" item (see `Deferred But Adjudicated` → "Path permission inheritance", Successor Required: yes) is being handled by successor plan [`170-nop-ai-agent-path-permission-inheritance.md`](170-nop-ai-agent-path-permission-inheritance.md).

Carry-over note for the successor: the original deferral rationale assumed `IPathAccessChecker` already had "glob-pattern rule matching". A live-repo review during successor drafting found this premise inaccurate — `DefaultPathAccessChecker` is a global static deny-list with no per-agent glob rules. The successor plan therefore derives the parent path scope from the agent's existing `workDir` (no DSL/schema change) and treats per-agent glob path-rules as a separate future concern. See the successor's `Current Baseline` and `Non-Goals` for the corrected premise.
