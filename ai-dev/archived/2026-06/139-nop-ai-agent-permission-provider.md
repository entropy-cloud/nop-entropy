> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L1-6

# 139 — IPermissionProvider Permission Resolution

> Last Reviewed: 2026-06-12
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 1, L1-6
> Related: `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §4.1

## Purpose

Implement `IPermissionProvider` — the core permission resolution interface that determines whether a tool call is allowed or denied based on three merged sources: session-level overrides, agent DSL permissions, and global defaults.

## Current Baseline

- `agent.xdef` defines `<permissions><permission id="..." resource="..." action="..."/></permissions>` and generates `AgentPermissionModel` (fields: `id`, `resource`, `action`). The model exists but is unused by runtime code.
- `ReActAgentExecutor` calls `toolManager.callTool()` directly (around line 118) without any permission check.
- `AgentExecutionContext` holds the `AgentModel` (which contains `permissions` list) and session metadata, but has no permission resolution capability.
- No `IPermissionProvider`, `IToolAccessChecker`, or `IPathAccessChecker` interfaces exist yet.
- `DefaultAgentEngine` constructs `ReActAgentExecutor` directly at line 90: `new ReActAgentExecutor(chatService, toolManager, eventPublisher)`.
- Existing tests use mock `IToolManager` and do not test permission behavior.

## Design Decisions (in this plan)

1. **`AgentPermissionModel.action` semantics**: The field value `"allow"` means the tool is allowed; `"deny"` means the tool is denied. This is a new semantic interpretation — the original `agent.xdef` defines `action` as `!string` without enum constraint. This plan establishes the convention that `action` = `"allow" | "deny"`.
2. **`channelKind` deferred to Layer 2**: The `IPermissionProvider.resolve()` signature omits `channelKind` for L1-6. Channel-based permission routing belongs to L2-14 (`IPermissionMatrix`). Adding it now would require inventing a source for `channelKind` that doesn't exist in the current execution flow.
3. **Session permissions format**: Session-level permission overrides are passed via `AgentExecutionContext.metadata` key `"sessionPermissions"` as `List<AgentPermissionModel>`. The caller is responsible for constructing the list before execution.
4. **Implementation class name**: Using `DefaultPermissionProvider` (not `HierarchicalPermissionProvider` from the design doc). The design doc's name will be updated to match at plan completion.

## Goals

- Define `IPermissionProvider` interface with `Permission resolve(toolName, agentName, sessionId)` contract
- Implement `DefaultPermissionProvider` with 3-source merge (session > agent DSL > default), deny-first semantics
- Integrate permission check into `ReActAgentExecutor` tool dispatch loop so denied tools produce error responses instead of executing
- Add `TOOL_CALL_DENIED` event type to `AgentEventType`
- Unit tests verifying deny-first, 3-source precedence, and integration with ReAct loop

## Non-Goals

- `IToolAccessChecker` (L1-7) — separate plan
- `IPathAccessChecker` (L1-8) — separate plan
- Hardcoded deny list for dangerous tools — belongs to L1-7
- Security levels, guardrails, approval gates — Layer 2/3
- Subagent permission inheritance — requires L1-7/L1-8 first
- Changing `agent.xdef` schema — the existing `<permission>` model is sufficient for L1-6
- `channelKind` parameter in `resolve()` — deferred to L2-14 (`IPermissionMatrix`)
- IoC bean registration — will be done as part of wiring in a later plan

## Scope

### In Scope

- `IPermissionProvider` interface definition
- `Permission` result data class (allow/deny + reason + matched rule id)
- `DefaultPermissionProvider` implementation with 3-source deny-first merge
- Integration point in `ReActAgentExecutor` (check before tool execution)
- `DefaultAgentEngine` update to pass `IPermissionProvider` to `ReActAgentExecutor`
- `TOOL_CALL_DENIED` event type
- Unit tests for permission resolution
- Integration test verifying denied tool produces error response in ReAct loop

### Out Of Scope

- `IToolAccessChecker`, `IPathAccessChecker` (L1-7, L1-8)
- Hardcoded deny list for shell_exec, file_write, etc.
- Content guardrails, security levels, approval gates
- Path normalization or glob matching
- Subagent permission inheritance
- IoC bean registration

## Execution Plan

### Phase 1 — Interface and Data Model

Status: completed
Targets: `io.nop.ai.agent.security` package (new)

- Item Types: `Decision`, `Proof`

- [x] Create `Permission` data class in `io.nop.ai.agent.security` with fields: `allowed` (boolean), `reason` (String, nullable), `matchedRuleId` (String, nullable). Static factory methods: `allow()`, `deny(reason)`, `deny(reason, ruleId)`.
- [x] Create `IPermissionProvider` interface in `io.nop.ai.agent.security` with method: `Permission resolve(String toolName, String agentName, String sessionId)`
- [x] Create `AllowAllPermissionProvider` pass-through implementation that always returns `Permission.allow()` — used as default when no security configuration exists
- [x] Write `TestPermission` unit test for Permission data class (constructor, factory methods, equals/hashCode)
- [x] Write `TestAllowAllPermissionProvider` unit test verifying always-allow behavior

Exit Criteria:

- [x] `Permission` class exists in `io.nop.ai.agent.security` with `allow()`, `deny(reason)`, `deny(reason, ruleId)` factories
- [x] `IPermissionProvider` interface exists with `resolve(toolName, agentName, sessionId)` method (no `channelKind` parameter)
- [x] `AllowAllPermissionProvider` exists and always returns `Permission.allow()`
- [x] `TestPermission` and `TestAllowAllPermissionProvider` pass
- [x] `./mvnw test -pl nop-ai-agent -am -T 1C` passes
- [x] No owner-doc update required (interfaces only, no behavior change yet)
- [x] `ai-dev/logs/` entry updated

### Phase 2 — DefaultPermissionProvider Implementation

Status: completed
Targets: `io.nop.ai.agent.security.DefaultPermissionProvider`

- Item Types: `Proof`

- [x] Implement `DefaultPermissionProvider` that accepts three configurable sources:
  1. Session-level rules: read from `AgentExecutionContext.metadata` key `"sessionPermissions"`, expected type `List<AgentPermissionModel>`. If key is absent or value is not a `List`, treat as empty.
  2. Agent DSL rules: from `AgentModel.getPermissions()` (already `List<AgentPermissionModel>`)
  3. Default rules: configurable `List<AgentPermissionModel>` passed to constructor, empty by default
- [x] Implement deny-first merge: iterate all sources in priority order (session → agent → default). For each source, check deny rules first. First deny match wins. If no deny, check allow rules. First allow match wins. Unmatched = deny.
- [x] Map `AgentPermissionModel` (id, resource, action) to permission rules: `resource` matches toolName (exact match or wildcard `*`), `action` is `"allow"` or `"deny"` (Design Decision #1)
- [x] Write `TestDefaultPermissionProvider` covering:
  - Empty permissions → deny all
  - Agent-level allow → allows matching tool
  - Agent-level deny → denies matching tool
  - Session override takes precedence over agent rules
  - Deny takes precedence over allow when both match at same level
  - Wildcard resource matching (`*` matches any toolName)
  - Non-List value in metadata `sessionPermissions` → treated as empty, no crash

Exit Criteria:

- [x] `DefaultPermissionProvider` exists with 3-source deny-first merge
- [x] `AgentPermissionModel` fields correctly mapped: `resource` → toolName pattern, `action` → `"allow"` / `"deny"`
- [x] Session-level rules override agent-level rules
- [x] Deny-first semantics verified: deny match wins over allow match
- [x] Non-List `sessionPermissions` metadata handled gracefully (treated as empty)
- [x] All `TestDefaultPermissionProvider` tests pass
- [x] `./mvnw test -pl nop-ai-agent -am -T 1C` passes
- [x] No owner-doc update required (implementation only, no public API change)
- [x] `ai-dev/logs/` entry updated

### Phase 3 — Integration with ReAct Loop and Engine

Status: completed
Targets: `ReActAgentExecutor`, `DefaultAgentEngine`, `AgentEventType`

- Item Types: `Fix`, `Proof`

- [x] Add `TOOL_CALL_DENIED` to `AgentEventType` enum
- [x] Add `IPermissionProvider` to `ReActAgentExecutor` as a constructor parameter: add a 4-arg constructor `ReActAgentExecutor(chatService, toolManager, eventPublisher, permissionProvider)` and keep the existing 2-arg and 3-arg constructors with `this(chatService, toolManager, eventPublisher, new AllowAllPermissionProvider())` delegation for backward compatibility
- [x] In `ReActAgentExecutor.execute()`, before calling `toolManager.callTool()`, add permission check: call `permissionProvider.resolve(toolName, agentName, sessionId)`. If denied, skip tool execution and create error `ChatToolResponseMessage` with denial reason.
- [x] Publish `TOOL_CALL_DENIED` event when a tool is denied
- [x] Update `DefaultAgentEngine.doExecute()` at line 90 to pass `IPermissionProvider` to `ReActAgentExecutor`. Add `IPermissionProvider` as a constructor field of `DefaultAgentEngine` with default value `new AllowAllPermissionProvider()`
- [x] Write integration test `TestPermissionInReActLoop`:
  - Deny a specific tool → ReAct loop receives error response, does not call toolManager
  - Allow all tools → existing behavior unchanged
  - Multiple tool calls in one iteration, some denied, some allowed → partial denial works correctly
- [x] Verify all existing tests pass unchanged (backward compatibility via constructor delegation)

Exit Criteria:

- [x] `TOOL_CALL_DENIED` exists in `AgentEventType`
- [x] `ReActAgentExecutor` has 4-arg constructor with `IPermissionProvider`; existing 2-arg/3-arg constructors delegate with `AllowAllPermissionProvider`
- [x] `DefaultAgentEngine` passes `IPermissionProvider` to `ReActAgentExecutor` (defaults to `AllowAllPermissionProvider`)
- [x] Denied tool calls produce `ChatToolResponseMessage.error()` with denial reason, do NOT call `toolManager.callTool()`
- [x] `TOOL_CALL_DENIED` event published on denial
- [x] **接线验证**: `TestPermissionInReActLoop` verifies `ReActAgentExecutor` actually calls `IPermissionProvider.resolve()` during tool dispatch (verified by mock or assertion)
- [x] **端到端验证**: Test confirms denied tool → error response in chat → agent continues or completes (full ReAct path)
- [x] All existing tests continue to pass (backward compatible — default `AllowAllPermissionProvider`)
- [x] `./mvnw test -pl nop-ai-agent -am -T 1C` passes
- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` L1-6 status updated to reflect completion
- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §4.1 updated: rename `HierarchicalPermissionProvider` to `DefaultPermissionProvider`, remove `channelKind` from L1 signature
- [x] `ai-dev/logs/` entry updated

## Closure Gates

- [x] `IPermissionProvider` interface + `DefaultPermissionProvider` + `AllowAllPermissionProvider` exist and are tested
- [x] ReAct loop checks permissions before tool execution
- [x] `DefaultAgentEngine` correctly passes `IPermissionProvider` to executor
- [x] Deny-first, 3-source merge verified by tests
- [x] Existing tests unaffected (backward compatible)
- [x] `./mvnw compile -pl nop-ai-agent -am -T 1C` passes
- [x] `./mvnw test -pl nop-ai-agent -am -T 1C` passes
- [x] No silent no-op in new code: denied tools produce explicit error, not silent skip
- [x] Design doc updated to reflect implementation decisions (class name, no channelKind)
- [x] Roadmap L1-6 status updated
- [x] `ai-dev/logs/` updated
- [x] Independent closure audit completed and evidence recorded

## Deferred But Adjudicated

### Hardcoded deny list for dangerous tools

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Belongs to L1-7 (`IToolAccessChecker`) which is a separate work item per roadmap
- Successor Required: yes
- Successor Path: Future plan for L1-7

### IoC bean registration for DefaultPermissionProvider

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Module uses constructor injection currently; IoC wiring will be added when integrating with NopIoC container
- Successor Required: no
- Successor Path: Will be addressed when module gets full IoC integration

### channelKind parameter in resolve()

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Channel-based routing belongs to L2-14 (`IPermissionMatrix`). No source for `channelKind` exists in current execution flow.
- Successor Required: yes
- Successor Path: Future plan for L2-14

## Non-Blocking Follow-ups

- L1-7 `IToolAccessChecker` with hardcoded deny list
- L1-8 `IPathAccessChecker` with glob matching
- Subagent permission inheritance (requires L1-7 + L1-8)
- `channelKind` parameter addition when `IPermissionMatrix` (L2-14) is implemented

## Closure

Status Note: All 3 phases completed. Permission system fully integrated into ReAct loop with deny-first 3-source merge, backward-compatible constructors, and event publishing. BUILD SUCCESS (2026-06-12T02:55:00+08:00).

Closure Audit Evidence:

- Reviewer / Agent: Closure audit agent (initial audit pass)
- Audit Session: 2026-06-12 initial code-level audit
- Evidence:
  - Phase 1 EC1 (Permission class): PASS — `security/Permission.java:17-27` has `allow()`, `deny(reason)`, `deny(reason, ruleId)` factories; equals/hashCode at lines 42-53
  - Phase 1 EC2 (IPermissionProvider interface): PASS — `security/IPermissionProvider.java:5` has `resolve(toolName, agentName, sessionId)` with no `channelKind`
  - Phase 1 EC3 (AllowAllPermissionProvider): PASS — `security/AllowAllPermissionProvider.java:7` returns `Permission.allow()` unconditionally
  - Phase 1 EC4 (TestPermission + TestAllowAllPermissionProvider): PASS — 6 + 3 = 9 tests, all substantive
  - Phase 1 EC5 (mvnw test): PASS — BUILD SUCCESS confirmed 2026-06-12
  - Phase 2 EC1 (DefaultPermissionProvider 3-source deny-first): PASS — `security/DefaultPermissionProvider.java:31-56` implements 3-source deny-first merge in `resolve()`
  - Phase 2 EC2 (AgentPermissionModel mapping): PASS — `resource` → toolName exact match or `*` wildcard (`matches()` line 97-99), `action` = `"allow"` | `"deny"` (lines 40, 50)
  - Phase 2 EC3 (Session override precedence): PASS — `buildSources()` line 67-79 adds session → agent → default in order; `resolve()` iterates sources in that order
  - Phase 2 EC4 (Deny-first semantics): PASS — First loop (lines 38-46) checks deny across all sources; second loop (lines 48-54) checks allow only if no deny found
  - Phase 2 EC5 (Non-List sessionPermissions): PASS — `extractSessionPermissions()` lines 82-95 checks `instanceof List` and validates each element; returns empty on type mismatch
  - Phase 2 EC6 (TestDefaultPermissionProvider): PASS — 11 tests covering all specified scenarios
  - Phase 3 EC1 (TOOL_CALL_DENIED in AgentEventType): PASS — `AgentEventType.java:14`
  - Phase 3 EC2 (ReActAgentExecutor 4-arg constructor): PASS — `ReActAgentExecutor.java:47-54`; 2-arg (line 38) and 3-arg (line 42) delegate with `AllowAllPermissionProvider`
  - Phase 3 EC3 (DefaultAgentEngine passes IPermissionProvider): PASS — `DefaultAgentEngine.java:103` passes `permissionProvider` to `ReActAgentExecutor`
  - Phase 3 EC4 (Denied tools produce error response): PASS — `ReActAgentExecutor.java:128-138` creates `ChatToolResponseMessage.error()` on denial, skips `toolManager.callTool()`
  - Phase 3 EC5 (TOOL_CALL_DENIED event): PASS — `ReActAgentExecutor.java:129` publishes event
  - Phase 3 EC6 (TestPermissionInReActLoop): PASS — 4 integration tests: denied tool, allowed tool, partial denial, event published
  - Phase 3 EC7 (Backward compatibility): PASS — 2-arg/3-arg constructors delegate with `AllowAllPermissionProvider`, no behavior change for existing callers
  - Phase 3 EC8 (mvnw test): PASS — BUILD SUCCESS, all modules compiled and tested
  - Phase 3 EC9 (roadmap L1-6): PASS — `nop-ai-agent-roadmap.md:128` shows `| L1-6 | ... | ✅ |`
  - Phase 3 EC10 (design doc updated): PASS — `nop-ai-agent-security-and-permissions.md:75` confirms `DefaultPermissionProvider` name and L1-6 status
  - Phase 3 EC11 (logs updated): PASS — `ai-dev/logs/2026/06-12.md:3-26` has Plan 139 entry with full details
  - Anti-Hollow: PASS — `permissionProvider.resolve()` called at `ReActAgentExecutor.java:126` during tool dispatch; denied tools produce explicit error (not silent skip); `DefaultAgentEngine.java:103` wires the provider; all new classes have real logic (no empty bodies)
  - Roadmap L1-6 status: PASS — ✅ in roadmap table
  - Closure Gate "no silent no-op": PASS — denied tools produce `ChatToolResponseMessage.error()` with reason string

Follow-up:

- no remaining plan-owned work
