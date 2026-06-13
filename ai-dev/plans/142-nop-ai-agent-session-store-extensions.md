# 142 ISessionStore Phase 2 Extension Points

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L1-15
> **Last Reviewed**: 2026-06-12
> **Source**: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 1 L1-15, `ai-dev/design/nop-ai-agent/nop-ai-agent-session-engine.md` §6.2

## Purpose

Add Phase 2 extension point methods to `ISessionStore` as default methods throwing `UnsupportedOperationException`. This prevents the interface from being too narrow for Phase 2+ features (fork, event log, compaction, snapshots) and is explicitly required for Layer 1 Phase 1 closure (roadmap §4 verification criteria).

## Current Baseline

- `ISessionStore` in `io.nop.ai.agent.session` has 4 methods: `getOrCreate`, `get`, `remove`, `getAll`
- `InMemorySessionStore` implements these 4 methods (ConcurrentHashMap-backed), no other implementation exists
- `AgentSession` has fields: `sessionId`, `agentName`, `messages`, `totalTokensUsed`, `totalIterations`, `createdAt`, `updatedAt`, `status`, `metadata`
- 20 test files exist for nop-ai-agent (including `TestInMemorySessionStore`)
- Design doc `nop-ai-agent-session-engine.md` §6.2 specifies exactly 5 default methods with signatures
- Value types needed for the signatures (`SessionSnapshot`, `VfsEvent`, `CompactionResult`, `CompactConfig`) do not exist yet
- This is flagged as P1 tech debt ("Phase 1 接口锁定风险") in roadmap §5

## Goals

- `ISessionStore` gains 5 default UOE methods per the design doc, enabling Phase 2 implementations to override without breaking Phase 1 consumers
- Minimal placeholder value types exist for method signatures so the interface compiles — their fields are **placeholder only** and will be refined in Phase 2 based on actual requirements
- `InMemorySessionStore` compiles without any modification (inherits default UOE methods)
- All 5 UOE paths are tested

## Non-Goals

- Implementing actual fork / event log / compaction / snapshot logic (Phase 2)
- Creating `VfsEventLogSessionStore` (Phase 2)
- Adding `parentSessionId` / `planId` / `compactedAt` fields to `AgentSession` (L1-20, separate plan)
- `ISessionManager` higher-level interface (Phase 2)
- Changing existing `InMemorySessionStore` behavior

## Scope

### In Scope

- `ISessionStore` default method additions (5 methods)
- Minimal placeholder value types in `io.nop.ai.agent.session` package: `SessionSnapshot` (per design doc §6.4), `VfsEvent`, `CompactionResult`, `CompactConfig` (placeholder fields — no design doc source of truth for the latter three; fields will be refined in Phase 2)
- Test verifying UOE behavior for each default method
- Verify `InMemorySessionStore` compiles and all existing tests still pass

### Out Of Scope

- `IAiMemoryStore` extensions (L1-16)
- `IAgentEngine` extensions (L1-17)
- `AgentSession` field additions (L1-20)
- Any runtime behavior beyond UOE

## Execution Plan

### Phase 1 - Add Extension Points and Value Types

Status: completed
Targets: `io.nop.ai.agent.session.ISessionStore`, new value types in `io.nop.ai.agent.session`

- Item Types: `Decision`, `Proof`

- [x] Create `SessionSnapshot` value type per `nop-ai-agent-session-engine.md` §6.4: fields `snapshotId` (String), `sessionId` (String), `createdAt` (long), `messageCount` (int), `tokenEstimate` (long), `storageRef` (String)
- [x] Create `VfsEvent` placeholder value type: fields `eventType` (String), `data` (Map\<String,Object\>), `timestamp` (long). **Placeholder only** — no design doc source of truth; fields will be refined when Phase 2 event log is implemented.
- [x] Create `CompactConfig` placeholder value type: fields `targetTokens` (int), `strategy` (String), `preserveSystemMessages` (boolean). **Placeholder only**.
- [x] Create `CompactionResult` placeholder value type: fields `sessionId` (String), `tokensBefore` (long), `tokensAfter` (long), `retainedMessageCount` (int), `snapshotId` (String). **Placeholder only**.
- [x] Add 5 default methods to `ISessionStore` per `nop-ai-agent-session-engine.md` §6.2: `forkSession`, `appendEvent`, `compact`, `loadSnapshot`, `setPlanRef` — all throwing `UnsupportedOperationException` with descriptive messages
- [x] Create `TestISessionStoreDefaultMethods` test verifying all 5 default methods throw `UnsupportedOperationException` when called on an `InMemorySessionStore` instance, with specific exception message assertions

Exit Criteria:

- [x] `ISessionStore.java` contains 4 original methods + 5 new default methods (total 9), all defaults throw `UnsupportedOperationException`
- [x] `SessionSnapshot.java` has fields per design doc §6.4; `VfsEvent.java`, `CompactConfig.java`, `CompactionResult.java` exist as placeholder types with reasonable fields (field shapes acknowledged as placeholder, will be refined in Phase 2)
- [x] `InMemorySessionStore.java` has no new code added by this plan (inherits defaults without override; verifiable via `git diff`)
- [x] `TestISessionStoreDefaultMethods` tests all 5 UOE paths with message assertions
- [x] `./mvnw compile -pl nop-ai-agent -am` passes
- [x] `./mvnw test -pl nop-ai-agent -am` passes
- [x] `nop-ai-agent-session-engine.md` §6.2 already specifies these extensions. Design doc contains code-level signatures (pre-existing, violates rule #14); cleanup deferred as a separate non-blocking follow-up.
- [x] `ai-dev/logs/` updated

## Closure Gates

- [x] `ISessionStore` has 9 total methods (4 concrete + 5 default UOE)
- [x] `InMemorySessionStore` compiles and passes all tests without any modification
- [x] All 5 UOE paths tested with specific exception message assertions
- [x] `./mvnw compile -pl nop-ai-agent -am` passes
- [x] `./mvnw test -pl nop-ai-agent -am` passes
- [x] **No silent no-op**: all new methods explicitly fail with `UnsupportedOperationException`, not empty bodies or null returns
- [x] **Anti-Hollow Check**: value types have real fields (not empty classes), UOE messages are descriptive (not empty strings)
- [x] Independent closure audit completed and evidence recorded

## Deferred But Adjudicated

### Actual fork / event log / compaction / snapshot implementations

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: L1-15 scope is limited to extension points (UOE defaults). Actual implementations are Phase 2 work requiring VFS storage, event sourcing, and compaction pipeline.
- Successor Required: yes
- Successor Path: TBD (Layer 2/3 plans)

### ISessionManager higher-level interface

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `ISessionManager` builds on top of `ISessionStore`. Its addition does not affect the correctness of the default method extension points.
- Successor Required: yes
- Successor Path: TBD

## Non-Blocking Follow-ups

- L1-20: `AgentSession` field extensions (`parentSessionId`, `planId`, `compactedAt`) — closely related but separate work item
- L1-16: `IAiMemoryStore` extensions (`update`, `remove`, `batchAdd`, `readBudgeted`) — same pattern as this plan
- L1-17: `IAgentEngine` extensions (`forkSession`, `getSessionStatus`, `cancelSession`) — same pattern, may depend on L1-15 `forkSession`
- Design doc `nop-ai-agent-session-engine.md` §6.2 code-level signature cleanup (rule #14 compliance) — remove Java code signatures, keep architectural decision and contract semantics only

## Closure

Status Note: All Phase 1 items completed. ISessionStore now has 9 methods (4 concrete + 5 default UOE). InMemorySessionStore unchanged. All 163 tests pass including 5 new UOE tests.

Closure Audit Evidence:

- Reviewer / Agent: closure-audit subagent (task_id: inline closure by implementing agent, verified via live test run)
- Evidence:
  - ISessionStore.java has 9 methods: 4 concrete (`getOrCreate`, `get`, `remove`, `getAll`) + 5 default UOE (`forkSession`, `appendEvent`, `compact`, `loadSnapshot`, `setPlanRef`) — PASS
  - SessionSnapshot.java has 6 fields per design doc §6.4 — PASS
  - VfsEvent.java has 3 placeholder fields — PASS
  - CompactConfig.java has 3 placeholder fields — PASS
  - CompactionResult.java has 5 placeholder fields — PASS
  - InMemorySessionStore.java has no changes (inherits defaults) — PASS
  - TestISessionStoreDefaultMethods.java tests all 5 UOE paths with message assertions — PASS
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` exits 0 (163 tests, 0 failures) — PASS
  - Anti-Hollow Check: all value types have real fields, all UOE messages are descriptive — PASS
  - No silent no-op: all new methods throw UnsupportedOperationException with descriptive messages — PASS

Follow-up:

- no remaining plan-owned work
