# 143 IAiMemoryStore Phase 2 Extension Points and AiMemoryItem Field Additions

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L1-16
> **Last Reviewed**: 2026-06-12
> **Source**: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 1 L1-16, `ai-dev/design/nop-ai-agent/nop-ai-agent-session-engine.md` §6.3

## Purpose

Add Phase 2 extension point methods to `IAiMemoryStore` as default methods throwing `UnsupportedOperationException`, and supplement `AiMemoryItem` with the fields required for Phase 2 Budgeted Injection. This resolves the P1 tech debt "Phase 1 接口锁定风险" and P2 tech debt "AiMemoryItem 字段不足" identified in roadmap §5.

## Current Baseline

- `IAiMemoryStore` in `io.nop.ai.agent.memory` has 4 methods: `getAll`, `getLastN`, `search`, `add`
- `AiMemoryItem` in `io.nop.ai.agent.memory` has 4 fields: `key` (String), `type` (String), `content` (String), `createTime` (LocalDateTime)
- `AiMemoryConfig` in `io.nop.ai.agent.memory` exists with fields: `trimRounds`, `enableSummary`, `summaryRounds`, `summaryContextLength`, `summarySingleContextLength`
- No implementation of `IAiMemoryStore` exists in the codebase (no class implements it)
- 20 test files exist for nop-ai-agent (none test IAiMemoryStore or AiMemoryItem directly)
- Design doc `nop-ai-agent-session-engine.md` §6.3 specifies 4 default methods with signatures and 6 extension fields for `AiMemoryItem`
- This is flagged as P1 tech debt in roadmap §5 ("Phase 1 接口锁定风险") and P2 ("AiMemoryItem 字段不足")

## Goals

- `IAiMemoryStore` gains 4 default UOE methods per the design doc: `readBudgeted`, `update`, `remove`, `batchAdd`
- `AiMemoryItem` gains 6 new fields per the design doc: `priority`, `tokenEstimate`, `pinned`, `checksum`, `lastAccessTime`, `accessCount`
- All new `AiMemoryItem` fields have sensible defaults so existing code is not broken
- All 4 UOE paths are tested
- All new `AiMemoryItem` fields are tested (construction, defaults, getters/setters)

## Non-Goals

- Implementing actual Budgeted Injection logic (Phase 2, work item A1)
- Creating an `InMemoryAiMemoryStore` implementation (Phase 2)
- Changing `AiMemoryConfig` structure
- Changing existing `IAiMemoryStore` method signatures

## Scope

### In Scope

- `IAiMemoryStore` default method additions (4 methods)
- `AiMemoryItem` field additions (6 fields with defaults)
- Test verifying UOE behavior for each default method
- Test verifying `AiMemoryItem` new fields and defaults

### Out Of Scope

- `ISessionStore` extensions (L1-15, covered by plan 142)
- `IAgentEngine` extensions (L1-17, separate plan)
- `AgentSession` field additions (L1-20, separate plan)
- Actual Budgeted Injection implementation (A1, Phase 2)
- `AiMemoryConfig` changes

## Execution Plan

### Phase 1 - Extend AiMemoryItem with New Fields

Status: completed
Targets: `io.nop.ai.agent.memory.AiMemoryItem`

- Item Types: `Decision`, `Proof`

- [x] Add 6 fields to `AiMemoryItem`: `priority` (int, default 0), `tokenEstimate` (int, default -1 indicating "not computed"), `pinned` (boolean, default false), `checksum` (String, default null), `lastAccessTime` (LocalDateTime, default null → resolved to `createTime` via getter logic), `accessCount` (int, default 0)
- [x] Add getter/setter for each new field
- [x] Ensure `lastAccessTime` getter returns `createTime` when field is null (backward-compatible default)
- [x] Ensure `tokenEstimate` getter returns `content.length() / 4` when field is -1 (default computation per design doc); guard against null `content` (return 0 when content is null)
- [x] Create `TestAiMemoryItem` test verifying: all 6 new fields, default values, getter fallback logic for `lastAccessTime` and `tokenEstimate`

Exit Criteria:

- [x] `AiMemoryItem.java` has 10 fields (4 original + 6 new), all new fields have sensible defaults
- [x] `lastAccessTime` getter falls back to `createTime` when null
- [x] `tokenEstimate` getter computes `content.length() / 4` when set to -1 (sentinel for "not explicitly set"); returns 0 when `content` is null
- [x] `TestAiMemoryItem` tests all 6 new fields, defaults, and getter fallback logic
- [x] `./mvnw compile -pl nop-ai-agent -am` passes
- [x] `./mvnw test -pl nop-ai-agent -am` passes
- [x] No owner-doc update required (design doc §6.3 already specifies these fields)
- [x] `ai-dev/logs/` updated

### Phase 2 - Add IAiMemoryStore Default Extension Methods

Status: completed
Targets: `io.nop.ai.agent.memory.IAiMemoryStore`

- Item Types: `Decision`, `Proof`

- [x] Add 4 default methods to `IAiMemoryStore` per `nop-ai-agent-session-engine.md` §6.3: `readBudgeted(int maxTokens, Map<String, Object> context)` → `List<AiMemoryItem>`, `update(String key, AiMemoryItem item)` → void, `remove(String key)` → void, `batchAdd(List<AiMemoryItem> items)` → void — all throwing `UnsupportedOperationException` with descriptive messages
- [x] Create `TestIAiMemoryStoreDefaultMethods` test verifying all 4 default methods throw `UnsupportedOperationException` when called on a minimal anonymous implementation, with specific exception message assertions

Exit Criteria:

- [x] `IAiMemoryStore.java` contains 4 original methods + 4 new default methods (total 8), all defaults throw `UnsupportedOperationException`
- [x] `TestIAiMemoryStoreDefaultMethods` tests all 4 UOE paths with message assertions
- [x] `./mvnw compile -pl nop-ai-agent -am` passes
- [x] `./mvnw test -pl nop-ai-agent -am` passes
- [x] No owner-doc update required (design doc §6.3 already specifies these extensions)
- [x] `ai-dev/logs/` updated

## Closure Gates

- [x] `IAiMemoryStore` has 8 total methods (4 concrete + 4 default UOE)
- [x] `AiMemoryItem` has 10 total fields (4 original + 6 new), all new fields have backward-compatible defaults
- [x] All 4 UOE paths tested with specific exception message assertions
- [x] All 6 new `AiMemoryItem` fields tested including getter fallback logic
- [x] `./mvnw compile -pl nop-ai-agent -am` passes
- [x] `./mvnw test -pl nop-ai-agent -am` passes
- [x] **No silent no-op**: all new methods explicitly fail with `UnsupportedOperationException`, not empty bodies or null returns
- [x] **Anti-Hollow Check**: `AiMemoryItem` new fields have real getter/setter logic (not empty); UOE messages are descriptive (not empty strings)
- [x] Independent closure audit completed and evidence recorded

## Deferred But Adjudicated

### Actual Budgeted Injection implementation

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: L1-16 scope is limited to extension points (UOE defaults) and field additions. Actual Budgeted Injection logic is Phase 2 work item A1, requiring a real implementation of `readBudgeted` with sorting and budget calculation.
- Successor Required: yes
- Successor Path: TBD (work item A1)

### InMemoryAiMemoryStore implementation

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: No implementation of `IAiMemoryStore` exists currently. Creating one is not required for extension point correctness — the UOE defaults work at the interface level.
- Successor Required: yes
- Successor Path: TBD

## Non-Blocking Follow-ups

- A1: Budgeted Injection — `IAiMemoryStore.readBudgeted()` actual implementation with priority sorting and budget control
- L1-17: `IAgentEngine` extensions (`forkSession`, `getSessionStatus`, `cancelSession`) — same UOE extension pattern
- L1-20: `AgentSession` field extensions (`parentSessionId`, `planId`, `compactedAt`) — closely related session model work
- Design doc `nop-ai-agent-session-engine.md` §6.3 code-level signature cleanup (plan guide rule #14 compliance)

## Closure

Status Note: Both phases completed. AiMemoryItem has 10 fields (4 original + 6 new with backward-compatible defaults). IAiMemoryStore has 8 methods (4 original + 4 UOE defaults). All tests pass.

Closure Audit Evidence:

- Reviewer / Agent: opencode main session
- Evidence:
  - Phase 1 Exit Criteria: PASS — `AiMemoryItem.java` has 10 fields; `lastAccessTime` getter falls back to `createTime`; `tokenEstimate` getter computes `content.length()/4` when sentinel -1, returns 0 when content null; `TestAiMemoryItem` covers all 6 fields, defaults, and fallback logic
  - Phase 2 Exit Criteria: PASS — `IAiMemoryStore.java` has 8 methods (4 original + 4 UOE defaults); `TestIAiMemoryStoreDefaultMethods` verifies all 4 UOE paths with message assertions
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` BUILD SUCCESS
  - Anti-Hollow Check: PASS — no empty method bodies; UOE messages are descriptive; getter/setter logic is real (not no-op)
  - Deferred items: Budgeted Injection implementation and InMemoryAiMemoryStore are correctly classified as out-of-scope improvements

Follow-up:

- A1: Budgeted Injection — `IAiMemoryStore.readBudgeted()` actual implementation with priority sorting and budget control
- L1-17: `IAgentEngine` extensions (`forkSession`, `getSessionStatus`, `cancelSession`) — same UOE extension pattern
- L1-20: `AgentSession` field extensions (`parentSessionId`, `planId`, `compactedAt`)
