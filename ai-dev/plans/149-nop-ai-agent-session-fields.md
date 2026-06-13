# 149 AgentSession Supplementary Fields: parentSessionId, planId, compactedAt

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L1-20
> **Last Reviewed**: 2026-06-13
> **Source**: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 1 L1-20, `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md` §5.1, `ai-dev/design/nop-ai-agent/nop-ai-agent-session-engine.md` §6.2

## Purpose

Add three nullable fields to `AgentSession` — `parentSessionId`, `planId`, `compactedAt` — that were identified as needed for Phase 2 session fork, plan reference, and context compaction. These fields are forward-looking: current code does not read them, but `ISessionStore` already has `forkSession(parentSessionId, ...)` and `setPlanRef(sessionId, planId)` default methods that will consume them in Phase 2.

## Current Baseline

- `AgentSession` (at `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/AgentSession.java`) has 9 fields: `sessionId`, `agentName`, `messages`, `totalTokensUsed`, `totalIterations`, `createdAt`, `updatedAt`, `status`, `metadata`
- Existing timestamp fields use primitive `long` (`createdAt`, `updatedAt`); `compactedAt` will use boxed `Long` so `null` means "never compacted" (cleaner sentinel than 0)
- Constructor is private, instances created via `AgentSession.create(sessionId, agentName)` factory method
- `ISessionStore` already defines `forkSession(String parentSessionId, ...)` and `setPlanRef(String sessionId, String planId)` as default methods throwing UOE — these will need `AgentSession.getParentSessionId()` and `AgentSession.getPlanId()` when Phase 2 lands
- Design doc `nop-ai-agent-session-and-storage.md` §5.1 shows `session_header` entry includes `parentSession` and `planId`
- Design doc `nop-ai-agent-session-and-storage.md` §17.1 shows `NopAiSession UPDATE (compactedAt)` in the write flow
- The generated ORM entity `_NopAiSession` in `nop-ai-dao` already has `parentSessionId` (String) and `compactedAt` (Timestamp) fields, confirming field name alignment with the persistence layer (though this plan does not touch persistence)
- `TestAgentSession.java` covers creation, messages, tokens, metadata, status, touch — 7 test methods
- Roadmap L1-20 status is ❌

## Goals

- `AgentSession` has three new nullable fields: `parentSessionId` (String), `planId` (String), `compactedAt` (Long)
- New fields default to `null` in `create()` factory method (backward compatible)
- Getter and setter for each field
- `compactedAt` is updated via a `markCompacted()` convenience method that sets it to `System.currentTimeMillis()` and calls `touch()`
- Existing `TestAgentSession` tests pass without modification (backward compatible)
- New tests cover the new fields

## Non-Goals

- Phase 2 session fork implementation (using `parentSessionId`)
- Phase 2 VFS session storage (using `planId`)
- Phase 2 context compaction (using `compactedAt`)
- Changes to `ISessionStore` or its implementations
- Changes to `AgentSession` serialization or persistence format

## Scope

### In Scope

- Add three fields to `AgentSession` with getters/setters
- `markCompacted()` convenience method
- Update `TestAgentSession` with tests for new fields
- Roadmap L1-20 status update

### Out Of Scope

- Phase 2 session fork/compaction/storage implementation
- ORM model changes for session persistence
- `ISessionStore` interface changes

## Execution Plan

### Phase 1 - Add Fields to AgentSession

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/AgentSession.java`

- Item Types: `Proof`

- [x] Add three nullable private fields: `String parentSessionId`, `String planId`, `Long compactedAt` (all initialized to `null` in constructor or field declaration)
- [x] Add getter/setter for each field: `getParentSessionId()`/`setParentSessionId()`, `getPlanId()`/`setPlanId()`, `getCompactedAt()`/`setCompactedAt()`
- [x] Add `markCompacted()` method: sets `compactedAt = System.currentTimeMillis()` and calls `this.touch()`
- [x] Verify `create()` factory method does not require changes (new fields default to `null`)

Exit Criteria:

- [x] `AgentSession` has three new nullable fields: `parentSessionId` (String), `planId` (String), `compactedAt` (Long)
- [x] All three fields default to `null` in instances created by `AgentSession.create()`
- [x] Getter and setter exist for each field
- [x] `markCompacted()` sets `compactedAt` to current time and calls `touch()`
- [x] No behavior change for existing code that doesn't use new fields
- [x] **端到端验证** N/A: pure field addition, no pipeline to verify end-to-end
- [x] **接线验证** N/A: no new inter-component wiring, fields not yet consumed by other components
- [x] **无静默跳过** N/A: no branches, no conditionals, pure field addition
- [x] No owner-doc update required (design docs already specify these fields)
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 2 - Tests

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/session/TestAgentSession.java`

- Item Types: `Proof`

- [x] Add test: new fields are `null` by default after `AgentSession.create()`
- [x] Add test: `setParentSessionId()` / `getParentSessionId()` round-trip
- [x] Add test: `setPlanId()` / `getPlanId()` round-trip
- [x] Add test: `setCompactedAt()` / `getCompactedAt()` round-trip
- [x] Add test: `markCompacted()` sets `compactedAt` to non-null and updates `updatedAt`

Exit Criteria:

- [x] New tests cover all three fields: default null, setter/getter round-trip
- [x] `markCompacted()` test verifies both `compactedAt` set and `updatedAt` updated
- [x] All existing tests still pass (backward compatible)
- [x] **端到端验证** N/A: pure field addition, no pipeline to verify end-to-end
- [x] **接线验证** N/A: no new inter-component wiring
- [x] **无静默跳过** N/A: no branches, no conditionals
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] No owner-doc update required
- [x] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

- [x] `AgentSession` has `parentSessionId`, `planId`, `compactedAt` fields (all nullable, default `null`)
- [x] `markCompacted()` convenience method works correctly
- [x] All new and existing tests pass
- [x] Backward compatible: `AgentSession.create()` works without specifying new fields
- [x] Roadmap L1-20 updated from ❌ to ✅
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` passes
- [x] No silent no-op or empty method body in new code
- [x] No owner-doc update required (design docs already describe these fields)
- [x] Independent closure audit completed and evidence recorded

## Deferred But Adjudicated

(none)

## Non-Blocking Follow-ups

- Phase 2 session fork using `parentSessionId` (depends on `VfsEventLogSessionStore`)
- Phase 2 `setPlanRef()` using `planId` (depends on VFS session storage)
- Phase 2 context compaction using `compactedAt` (depends on `IContextCompactor`)

## Closure

Status Note: All phases completed. Three nullable fields added to AgentSession with getters/setters and markCompacted() convenience method. 5 new tests added (total 12 in TestAgentSession). All 214 tests in nop-ai-agent pass. Build verified with `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`.

Closure Audit Evidence:

- Reviewer / Agent: Independent closure auditor (opencode subagent, fresh session)
- Audit Session: closure-audit-149
- Evidence:
  - Phase 1 Exit Criteria: PASS — `AgentSession.java` has three new nullable fields (`parentSessionId` String, `planId` String, `compactedAt` Long) all defaulting to null; getters/setters exist; `markCompacted()` sets compactedAt to `System.currentTimeMillis()` and calls `touch()`; no behavior change for existing code.
  - Phase 2 Exit Criteria: PASS — `TestAgentSession.java` has 5 new test methods covering default null, setter/getter round-trip for all three fields, and markCompacted() behavior (compactedAt set + updatedAt updated); all existing tests still pass.
  - Closure Gates: PASS — All gates verified against live repo; backward compatible; build passes; no silent no-op; no owner-doc update required.
  - Anti-Hollow Check: N/A — pure field addition with no new inter-component wiring, no branches, no conditionals. Fields not yet consumed by other components (by design, Phase 2 scope).
  - Deferred items classification: honest — no live defect or contract drift deferred.
  - `node ai-dev/tools/check-plan-checklist.mjs` exit code: verified (this audit run).

Follow-up:

- Phase 2 session fork, plan reference, and context compaction will consume these fields
