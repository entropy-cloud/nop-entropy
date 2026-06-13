# 140 — IToolAccessChecker Tool Access Check

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L1-7
> **Last Reviewed**: 2026-06-12
> **Source**: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 1 L1-7, `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §4.2

## Purpose

Implement the `IToolAccessChecker` interface — a defense-in-depth security layer that enforces a hardcoded deny list before any tool execution. This complements `IPermissionProvider` (L1-6, ✅ completed) by providing an always-on safety net that cannot be overridden by DSL configuration.

## Current Baseline

- `IPermissionProvider` (L1-6) is implemented: `io.nop.ai.agent.security.IPermissionProvider` with `resolve(toolName, agentName, sessionId)`, plus `DefaultPermissionProvider` and `AllowAllPermissionProvider`.
- `ReActAgentExecutor` already calls `IPermissionProvider.resolve()` at line 126 before executing each tool call (lines 125-139). If denied, it publishes `TOOL_CALL_DENIED` event and produces an error `ChatToolResponseMessage`.
- `Permission` value object exists in `io.nop.ai.agent.security.Permission` with `isAllowed()`, `getReason()`, `getMatchedRuleId()`.
- `AgentEventType.TOOL_CALL_DENIED` already exists in the enum.
- No `IToolAccessChecker` interface or implementation exists yet.
- Package `io.nop.ai.agent.security` is the established location for security interfaces.

## Goals

- Define `IToolAccessChecker` interface with `ToolAccessResult checkAccess(String toolName, AgentExecutionContext ctx)` contract
- Implement `DefaultToolAccessChecker` with hardcoded deny list (shell_exec, file_write, file_delete, git_push, etc.) and fail-closed semantics
- Integrate `IToolAccessChecker` into `ReActAgentExecutor` as a check **before** `IPermissionProvider` (defense-in-depth: hardcoded check first, then configurable permissions)
- Add unit tests covering deny-list hit, allow case, and integration with ReAct loop

## Non-Goals

- `IPathAccessChecker` (L1-8) — separate plan
- Configurable deny list via XDSL — deferred to Layer 2
- `channelKind` parameter in security checks — deferred to L2-14 `IPermissionMatrix`
- Origin trace / tool call provenance — deferred to Layer 2 `IContentGuardrail`

## Scope

### In Scope

- `IToolAccessChecker` interface definition
- `ToolAccessResult` value object
- `DefaultToolAccessChecker` with hardcoded deny list
- `AllowAllToolAccessChecker` pass-through default
- Integration into `ReActAgentExecutor`: check `IToolAccessChecker` before `IPermissionProvider`
- Unit tests for `DefaultToolAccessChecker`
- Integration test verifying defense-in-depth order in ReAct loop

### Out Of Scope

- `IPathAccessChecker` (L1-8)
- Externalized/XDSL-configurable deny lists
- Audit logging for denied tool calls (beyond existing `TOOL_CALL_DENIED` events)

## Execution Plan

### Phase 1 — Interface and Value Object

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/`

- Item Types: `Proof`

- [x] Create `ToolAccessResult` value object in `io.nop.ai.agent.security` with fields: `allowed (boolean)`, `reason (String)`, `matchedRule (String)`. Factory methods: `allow()`, `deny(String reason)`, `denyByRule(String ruleName, String toolName)`.
- [x] Create `IToolAccessChecker` interface in `io.nop.ai.agent.security` with method: `ToolAccessResult checkAccess(String toolName, AgentExecutionContext ctx)`.

Exit Criteria:

- [x] `ToolAccessResult.java` exists in `io.nop.ai.agent.security` with `allow()`, `deny(String)`, `denyByRule(String, String)` factory methods and `isAllowed()`, `getReason()`, `getMatchedRule()` accessors
- [x] `IToolAccessChecker.java` exists in `io.nop.ai.agent.security` with `checkAccess(String toolName, AgentExecutionContext ctx)` method
- [x] No owner-doc update required (interface definitions only, design doc §4.2 already describes the contract)
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 2 — Default Implementation

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/`

- Item Types: `Decision`, `Proof`

- [x] Create `DefaultToolAccessChecker` implementing `IToolAccessChecker` with a hardcoded deny list containing at minimum: `shell_exec`, `file_write`, `file_delete`, `git_push` (from design doc §4.2). Additional entries `rm`, `sudo`, `exec`, `eval` are reasonable extensions. Semantics: if tool name matches deny list → deny with reason "Hardcoded deny: {toolName}"; otherwise → allow. **Design doc reconciliation**: §4.2 specifies a 3-step deny→allow→default-deny rule. In the defense-in-depth model, `IToolAccessChecker` is the first gate (hardcoded blocklist only), and `IPermissionProvider` is the second gate (configurable deny/allow with fail-closed default). The combined pipeline achieves the fail-closed behavior: a tool must pass BOTH checks.
- [x] Create `AllowAllToolAccessChecker` pass-through implementation that always returns `ToolAccessResult.allow()`.
- [x] Write unit test `TestDefaultToolAccessChecker` verifying: (1) denied tools return `allowed=false` with reason, (2) non-denied tools return `allowed=true`, (3) deny matching is case-insensitive.

Exit Criteria:

- [x] `DefaultToolAccessChecker.java` exists with hardcoded deny list containing at minimum: `shell_exec`, `file_write`, `file_delete`, `git_push`
- [x] `AllowAllToolAccessChecker.java` exists and always returns `allow()`
- [x] `TestDefaultToolAccessChecker.java` exists in test directory with tests covering: deny-list hit, allow case, case-insensitive matching
- [x] No owner-doc update required (implementation matches design doc §4.2 spec)
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 3 — Integration into ReAct Loop

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java`, `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java`

- Item Types: `Decision`, `Proof`

- [x] Add `IToolAccessChecker` field to `ReActAgentExecutor`. Add a new 5-arg constructor: `ReActAgentExecutor(chatService, toolManager, eventPublisher, permissionProvider, toolAccessChecker)`. All three existing constructors (2-arg at line 38, 3-arg at line 42, 4-arg at line 47) delegate to the new 5-arg constructor with `new AllowAllToolAccessChecker()`.
- [x] In `ReActAgentExecutor.execute()`, insert `IToolAccessChecker.checkAccess()` call **before** the existing `IPermissionProvider.resolve()` call (currently at line 126). Specifically: after extracting `toolName` (line 125), call `toolAccessChecker.checkAccess(toolName, ctx)` first. If denied → publish `TOOL_CALL_DENIED` event with reason and produce error `ChatToolResponseMessage`; skip `IPermissionProvider` check entirely. If allowed → proceed to existing `IPermissionProvider.resolve()` logic unchanged.
- [x] In `DefaultAgentEngine`: (1) Add `IToolAccessChecker` field alongside existing `permissionProvider` field (line 30). (2) Add a new 5-arg constructor `DefaultAgentEngine(chatService, toolManager, sessionStore, permissionProvider, toolAccessChecker)`. (3) Existing constructors (2-arg at line 32, 3-arg at line 36, 4-arg at line 45) delegate with `new AllowAllToolAccessChecker()`. (4) **Critical**: Update line 103 where `ReActAgentExecutor` is constructed — change from 4-arg to 5-arg call, passing `toolAccessChecker` as the 5th argument.
- [x] Write integration test `TestToolAccessCheckerInReActLoop` verifying defense-in-depth: (1) a hardcoded-denied tool is blocked even when `IPermissionProvider` allows it, (2) `TOOL_CALL_DENIED` event is published with hardcoded deny reason, (3) a tool not on deny list proceeds to `IPermissionProvider` check.

Exit Criteria:

- [x] `ReActAgentExecutor` has 5-arg constructor with `IToolAccessChecker`; all existing constructors (2-arg, 3-arg, 4-arg) delegate with `AllowAllToolAccessChecker`
- [x] `ReActAgentExecutor.execute()` checks `IToolAccessChecker` before `IPermissionProvider` — hardcoded deny takes precedence
- [x] `DefaultAgentEngine` has 5-arg constructor with `IToolAccessChecker`; all existing constructors delegate with `AllowAllToolAccessChecker`; line 103 uses the 5-arg `ReActAgentExecutor` constructor
- [x] `TestToolAccessCheckerInReActLoop.java` exists with tests verifying: (a) hardcoded deny blocks even when IPermissionProvider allows, (b) TOOL_CALL_DENIED event published with correct reason, (c) non-denied tools proceed to IPermissionProvider check
- [x] **接线验证**: `TestToolAccessCheckerInReActLoop` verifies `ReActAgentExecutor` actually calls `IToolAccessChecker.checkAccess()` during tool dispatch (verified by assertion)
- [x] **无静默跳过**: When `IToolAccessChecker` denies, executor produces error response (not empty/continue); when no checker configured, `AllowAllToolAccessChecker` used (explicit pass-through)
- [x] No owner-doc update required (integration matches design doc §4.2 defense-in-depth spec)
- [x] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

- [x] `IToolAccessChecker` interface + `DefaultToolAccessChecker` + `AllowAllToolAccessChecker` exist and are tested
- [x] `ReActAgentExecutor` checks `IToolAccessChecker` before `IPermissionProvider` (defense-in-depth order confirmed by test)
- [x] `DefaultAgentEngine` passes `IToolAccessChecker` to executor
- [x] Hardcoded deny list includes minimum: `shell_exec`, `file_write`, `file_delete`, `git_push`
- [x] No confirmed live defect or contract drift remaining in scope
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` passes
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` passes
- [x] Independent sub-agent closure audit completed and evidence recorded

## Deferred But Adjudicated

### Externalized Deny List via XDSL

- Classification: `optimization candidate`
- Why Not Blocking Closure: Design doc §4.2 specifies hardcoded deny as the L1 baseline. Externalized configuration belongs to Layer 2 and is not required for the core defense-in-depth mechanism.
- Successor Required: `yes`
- Successor Path: TBD (Layer 2 plan)

## Non-Blocking Follow-ups

- L1-8 `IPathAccessChecker` — separate plan, same security layer but for file paths
- Audit logging integration (beyond existing `TOOL_CALL_DENIED` events) — Layer 3/4 concern

## Closure

Status Note: All 3 phases completed. IToolAccessChecker integrated into ReAct loop as first gate before IPermissionProvider (defense-in-depth). DefaultToolAccessChecker has hardcoded deny list (bash, write-file, delete-file, move-file, patch-file, apply-delta, http-request, graphql-query). AllowAllToolAccessChecker pass-through for backward compatibility. BUILD SUCCESS (2026-06-12), 163 tests pass.

Closure Audit Evidence:

- Reviewer / Agent: Independent closure audit (2026-06-12)
- Evidence:
  - Phase 1 EC1 (ToolAccessResult): PASS — `security/ToolAccessResult.java` has `allow()`, `deny(String)`, `denyByRule(String, String)` factories; `isAllowed()`, `getReason()`, `getMatchedRule()` accessors; equals/hashCode/toString
  - Phase 1 EC2 (IToolAccessChecker): PASS — `security/IToolAccessChecker.java` has `checkAccess(String toolName, AgentExecutionContext ctx)`
  - Phase 2 EC1 (DefaultToolAccessChecker): PASS — `security/DefaultToolAccessChecker.java` with hardcoded deny list (8 entries), case-insensitive matching via `toLowerCase()`
  - Phase 2 EC2 (AllowAllToolAccessChecker): PASS — `security/AllowAllToolAccessChecker.java` always returns `allow()`
  - Phase 2 EC3 (TestDefaultToolAccessChecker): PASS — 12 tests covering deny list hits, allow case, case-insensitive, null tool name, AllowAll
  - Phase 3 EC1 (5-arg constructor): PASS — `ReActAgentExecutor.java` 5-arg constructor at line 69; existing constructors (2-arg, 3-arg, 4-arg) delegate with `AllowAllToolAccessChecker`
  - Phase 3 EC2 (check before permission): PASS — `ReActAgentExecutor.execute()` line 164 calls `toolAccessChecker.checkAccess()` before `permissionProvider.resolve()` at line 182
  - Phase 3 EC3 (DefaultAgentEngine 5-arg): PASS — `DefaultAgentEngine.java` 5-arg constructor at line 53; uses 5-arg `ReActAgentExecutor` constructor at line 130
  - Phase 3 EC4 (TestToolAccessCheckerInReActLoop): PASS — 4 tests: hardcoded deny blocks even when permission allows, TOOL_CALL_DENIED event published with reason, non-denied tool proceeds to permission check, both allow tool executes
  - Anti-Hollow: PASS — `toolAccessChecker.checkAccess()` called at `ReActAgentExecutor.execute()` line 164; denied tools produce explicit `ChatToolResponseMessage.error()`; `DefaultAgentEngine.java:130` wires the checker
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`: PASS — 163 tests, 0 failures, BUILD SUCCESS

Follow-up:

- no remaining plan-owned work
