# 145 IAgentEngine Phase 2 Extension Points

> Plan Status: completed
> Module: nop-ai-agent
> Work Item: L1-17
> Last Reviewed: 2026-06-12
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 1 L1-17, `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md` §3.2

## Purpose

Add Phase 2 extension point methods to `IAgentEngine` as default methods throwing `UnsupportedOperationException`. This resolves the P1 tech debt "Phase 1 接口锁定风险" identified in roadmap §5, completing the trilogy of Phase 2 extension point additions (L1-15 ISessionStore, L1-16 IAiMemoryStore, L1-17 IAgentEngine).

## Current Baseline

- `IAgentEngine` in `io.nop.ai.agent.engine` has 2 methods: `sendMessage(AgentMessageRequest)` and `execute(AgentMessageRequest)`
- `DefaultAgentEngine` implements both methods; it does **not** override any Phase 2 extension methods
- `AgentExecStatus` enum exists in `io.nop.ai.agent.model` with values: `pending`, `running`, `completed`, `failed`
- `AgentMessageRequest` is a value object with `agentName`, `userMessage`, `sessionId`, `metadata`
- Design doc `nop-ai-agent-react-engine.md` §3.2 specifies exactly 3 default methods with signatures and UOE messages
- `ISessionStore` (L1-15) and `IAiMemoryStore` (L1-16) already have their Phase 2 default UOE methods — this plan follows the identical pattern
- This is flagged as P1 tech debt in roadmap §5 ("Phase 1 接口锁定风险")

## Goals

- `IAgentEngine` gains 3 default UOE methods per the design doc: `forkSession`, `getSessionStatus`, `cancelSession`
- All 3 UOE paths are tested
- `DefaultAgentEngine` is unaffected (inherits default methods that throw UOE)

## Non-Goals

- Implementing actual fork/cancel/status logic (Phase 2)
- Changing existing `sendMessage` / `execute` method signatures
- Adding `IAgentEngine` extensions to any other interface
- Wiring cancel/fork into the Actor runtime (Phase 2)

## Scope

### In Scope

- `IAgentEngine` default method additions (3 methods)
- Test verifying UOE behavior for each default method via `DefaultAgentEngine`

### Out Of Scope

- Actual fork/cancel implementation (Phase 2 Actor lifecycle)
- `ISessionStore` extensions (L1-15, covered by plan 142)
- `IAiMemoryStore` extensions (L1-16, covered by plan 143)
- `AgentSession` field additions (L1-20, separate plan)

## Execution Plan

### Phase 1 - Add Phase 2 Default Methods to IAgentEngine

Status: completed
Targets: `io.nop.ai.agent.engine.IAgentEngine`, `io.nop.ai.agent.engine.TestIAgentEngineDefaultMethods`

- Item Types: `Fix`

- [x] Add `forkSession(AgentMessageRequest, boolean)` default method → `CompletableFuture<String>` throwing UOE
- [x] Add `getSessionStatus(String)` default method → `AgentExecStatus` throwing UOE
- [x] Add `cancelSession(String, String, boolean)` default method → `CompletableFuture<Void>` throwing UOE
- [x] Create `TestIAgentEngineDefaultMethods` verifying all 3 methods throw UOE via `DefaultAgentEngine`

Exit Criteria:

- [x] All 3 default methods exist in `IAgentEngine` with correct signatures and UOE messages matching design doc §3.2
- [x] `DefaultAgentEngine` compiles without changes (inherits defaults)
- [x] `TestIAgentEngineDefaultMethods` has 3 tests, each verifying the correct UOE message
- [x] **No new test required beyond the 3 UOE tests**: no implementation logic is added, only UOE stubs
- [x] **No silent no-op**: all 3 methods throw `UnsupportedOperationException`, not return null/empty (Minimum Rules #24)
- [x] `ai-dev/logs/` corresponding date entry updated
- [x] No owner-doc update required: design doc `nop-ai-agent-react-engine.md` §3.2 already specifies these exact methods; no design change

## Closure Gates

- [x] All 3 default UOE methods added to `IAgentEngine` with correct signatures
- [x] `DefaultAgentEngine` compiles and inherits defaults without override
- [x] 3 focused tests verify UOE behavior
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -T 1C` passes (180 tests, 0 failures)
- [x] No in-scope live defect silently deferred
- [x] Independent closure audit completed and evidence recorded

## Closure

Status Note: L1-17 is complete — `IAgentEngine` now has 3 Phase 2 default UOE methods (`forkSession`, `getSessionStatus`, `cancelSession`) matching design doc §3.2 exactly. This completes the P1 "Phase 1 接口锁定风险" trilogy (L1-15 ISessionStore, L1-16 IAiMemoryStore, L1-17 IAgentEngine). Additionally, a pre-existing build break from plan 143 (L1-16) was fixed: `AiMemoryItem` source was missing 6 fields and `IAiMemoryStore` was missing 4 default methods — both now match their completed tests and design spec.

Closure Audit Evidence:

- Reviewer / Agent: independent sub-agent audit (ses_14384f749ffe2Z6BPYZ2HADZES)
- Audit Session: 2026-06-12T23:36 (independent re-audit, all 8 checks PASS)
- Evidence:
  - Exit Criterion 1 (3 default methods exist): PASS — `IAgentEngine.java:14-24` defines `forkSession`, `getSessionStatus`, `cancelSession` with UOE messages matching design doc §3.2 verbatim
  - Exit Criterion 2 (DefaultAgentEngine compiles): PASS — `DefaultAgentEngine` unchanged, inherits all 3 defaults; compiled successfully
  - Exit Criterion 3 (3 tests verify UOE): PASS — `TestIAgentEngineDefaultMethods.java` has `forkSessionThrowsUOE`, `getSessionStatusThrowsUOE`, `cancelSessionThrowsUOE`, each asserting exact UOE message
  - Exit Criterion (No silent no-op): PASS — all 3 methods throw `UnsupportedOperationException`, none return null/empty
  - Closure Gate (tests pass): PASS — `./mvnw test -pl nop-ai/nop-ai-agent -T 1C` → 180 tests, 0 failures, 0 errors, BUILD SUCCESS
  - Pre-existing fix (L1-16): `AiMemoryItem` now has 6 fields (priority, tokenEstimate with content/4 fallback, pinned, checksum, lastAccessTime with createTime fallback, accessCount); `IAiMemoryStore` now has 4 default UOE methods (readBudgeted, update, remove, batchAdd) — all matching design doc §6.3 and existing tests
  - Deferred items: none — no in-scope work remains

Follow-up:

- No remaining plan-owned work
- Note: `./mvnw test -pl nop-ai-agent -am` fails due to pre-existing upstream compilation issues in `nop-dao` (unrelated to this plan); tests pass without `-am` since all dependencies are installed
