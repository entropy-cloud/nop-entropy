# 11 CEP 状态后端接入

> Plan Status: completed
> Plan Type: implementation
> Mission: nop-stream-flink-comparison
> Work Item: roadmap item 11
> Last Reviewed: 2026-07-25
> Source: `docs/backlog/nop-stream-flink-comparison-roadmap.md` Item 11; `ai-dev/analysis/nop-stream/08-gap-analysis.md` gaps G18, G19, G49, G65, G20(part); live code audit 2026-07-25
> Related: `2026-07-25-1210-2-gap-analysis.md` (prerequisite), `2026-07-25-1430-1-watermark-fixes.md` (G20 split scope; Phase 3 of this plan depends on Plan 10 only if multi-input scenario exists)

## Purpose

Wire the CEP operator's state backend so it uses the same pluggable `IStateBackend` as the rest of the system, enabling persistent checkpoint/restore for NFA state, shared buffer, and computation state. Live code audit confirms `CepOperator` already uses `IKeyedStateBackend` (not `SimpleKeyedStateStore`), and snapshot/restore methods are correctly implemented. The real gap: `GraphModelCheckpointExecutor` hardcodes `new MemoryStateBackend()` as a fallback default while the runtime never calls `setKeyedStateBackend()`, forcing `CepOperator.open()` to self-create a `MemoryKeyedStateBackend`.

## Current Baseline

- `CepOperator` (`nop-stream/nop-stream-cep/.../cep/operator/CepOperator.java`) uses `IKeyedStateBackend`: in `open()`, it tries `getKeyedStateBackend()` → `stateBackend.createKeyedStateBackend()` → `new MemoryKeyedStateBackend()` fallback. The actual store is always an `IKeyedStateBackend` implementation.
- `CepOperator.snapshotState()` (lines 307-315) correctly overrides `AbstractStreamOperator.snapshotState()`: saves `currentWatermark` and `registeredEventTimeTimers`.
- `CepOperator.restoreState()` (lines 317-335) correctly restores watermark and timers from snapshot.
- `setKeyedStateBackend()` is **never called from production runtime code** — only 25 test callers exist.
- `GraphModelCheckpointExecutor` (`nop-stream/nop-stream-runtime/.../runtime/execution/GraphModelCheckpointExecutor.java`) at line 540 has `if (abstractOp.getStateBackend() == null) { IStateBackend stateBackend = new MemoryStateBackend(); ... }` — a null-check fallback, effectively hardcoding MemoryStateBackend as the only provisioned backend.
- `AbstractStreamOperator.snapshotState()` calls `keyedStateBackend.snapshotState()` — serializes all keyed state (NFA state, shared buffer) into `OperatorSnapshotResult`, which goes through the checkpoint pipeline to `ICheckpointStorage`. But restore goes back into a fresh `MemoryKeyedStateBackend` — in-memory only.
- `AbstractStreamOperator.restoreState()` relies on `applyPendingRestoreState()` (called from `CepOperator.open()`) for deferred keyed state restoration — this lifecycle coupling must be preserved when changing backend wiring.
- `CepOperator` does NOT implement `ICheckpointedFunction` — uses operator-level `snapshotState()`/`restoreState()` overrides instead.
- `CepOperator` Javadoc (lines 80-83) claims "hardcoded MemoryKeyedStateBackend" — this reflects the pre-unified-backend architecture (G49).
- `SharedBuffer` uses `ConcurrentHashMap` for cache (extracted from Flink which used Guava Cache with LRU eviction) — P3 regression (G65).
- `advanceTime()` exists at `CepOperator.java:573-595` — correctly advances internal timer service on watermark arrival.

## Goals

- Make CEP's state backend pluggable: replace `GraphModelCheckpointExecutor` hardcoded `MemoryStateBackend()` fallback with configurable backend from `StreamExecutionEnvironment`
- Wire backend injection into operator initialization lifecycle so operators receive a persistent backend before `open()`, eliminating self-created `MemoryKeyedStateBackend` dependence
- Verify snapshot/restore path connectivity for CEP (NFA state + shared buffer survive checkpoint→restore)
- Verify watermark propagation runtime→CepOperator connectivity
- Fix outdated Javadoc (G49)
- Upgrade SharedBuffer cache from `ConcurrentHashMap` to LRU eviction (G65)
- CEP checkpoint/restore end-to-end test

## Non-Goals

- Operator State base system (Item 12a — separate plan)
- Common `IStateBackend` abstraction changes (already exists)
- RocksDB state backend (Phase 1)
- Unaligned checkpoint (Phase 4)
- Multi-input watermark combining (Plan 10 successor plan)
- `CheckpointMetricsSnapshot.toString()` missing `failureCause` (Follow-up Backlog)

## Scope

### In Scope

- **Pluggable state backend wiring**: replace `new MemoryStateBackend()` fallback in `GraphModelCheckpointExecutor` with configurable backend from environment config
- **Backend injection lifecycle**: ensure `setKeyedStateBackend()` or equivalent is called from operator initialization code (before `open()`) so the operator receives a persistent backend rather than self-creating `MemoryKeyedStateBackend`. Must preserve the `applyPendingRestoreState()` lifecycle for deferred keyed state restoration.
- **Snapshot/restore path audit and verification**: trace full path from `CheckpointBarrier` → `processBarrier()` → `snapshotState()` → `keyedStateBackend.snapshotState()` → `OperatorSnapshotResult` → `ICheckpointStorage`. Fix any disconnections found.
- **Watermark propagation verification**: trace watermark from source operator → `CepOperator.processElement()` → `advanceTime()`. Fix any disconnections found. Note: multi-input path (via Plan 10 StatusWatermarkValve) is conditional — if Plan 10's successor plan has not executed, the single-input path is sufficient.
- **Javadoc update** (G49): remove "hardcoded MemoryKeyedStateBackend" claims; document pluggable backend architecture
- **SharedBuffer cache LRU** (G65): replace `ConcurrentHashMap` with LRU-capable cache (Guava `CacheBuilder` per Flink pattern, or minimal `LinkedHashMap`-based LRU)
- **End-to-end test**: CEP pattern with state → checkpoint → restore → verify correct continuation

### Out Of Scope

- Multi-input watermark combining (Plan 10 successor)
- Operator state base system (Item 12a)
- RocksDB state backend (Phase 1)
- `CheckpointMetricsSnapshot.toString()` (Follow-up Backlog P2)
- `OperatorChain.open()` javadoc (AR-6)
- `PartitionPolicy` dead code (AR-7)

## Execution Plan

### Phase 1 — Pluggable state backend wiring

Status: completed
Targets:
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java` (line 540 — NOT under `runtime/checkpoint/`)
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/AbstractStreamOperator.java`

Item Types: `Proof | Decision | Fix`

- [x] `Proof` Audit `GraphModelCheckpointExecutor.registerTasksAndTrackers()`: locate the `MemoryStateBackend()` provision at line 540. Determine how `StreamExecutionEnvironment.stateBackend` propagates to this point. Identify the existing `setStateBackend()` call site.
- [x] `Proof` Audit `CepOperator.open()`: locate the self-creation of `MemoryKeyedStateBackend` (line 212-217). Understand the `applyPendingRestoreState()` lifecycle coupling — this is called at line 207 when the backend is self-created, and must be preserved if backend creation moves.
- [x] `Decision` Design the backend injection flow:
  - Pre-inject `IStateBackend` (factory) via `setStateBackend()` BEFORE operator `open()` — already partially done, just need to make it configurable
  - Option A: Also pre-inject `IKeyedStateBackend` (instance) via `setKeyedStateBackend()` BEFORE `open()` — this changes operator lifecycle significantly
  - Option B: Keep the existing pattern (`setStateBackend()` → operator `open()` → self-creates `keyedStateBackend` from factory) — simpler, less invasive
  - Recommended: Option B — minimal change; just make the injected `IStateBackend` configurable instead of hardcoded `MemoryStateBackend`
- [x] `Fix` Replace `new MemoryStateBackend()` at line 540 with the configured backend from `StreamExecutionEnvironment.stateBackend`. Default to `MemoryStateBackend` if none configured (backward compatible).
- [x] `Fix` Remove or update the `MemoryKeyedStateBackend` fallback in `CepOperator.open()`: if `stateBackend.createKeyedStateBackend()` returns a real backend, do NOT fall back to `new MemoryKeyedStateBackend()`. The fallback should only trigger if both `getKeyedStateBackend()` and `stateBackend` are null.
- [x] Add focused test: configure `LocalFileCheckpointStorage`-backed state backend → CEP operator snapshot → verify keyed state is persisted to the configured storage.
- [x] Add focused test: no backend configured → default `MemoryStateBackend` is used (backward compatibility).

Exit Criteria:

- [x] `GraphModelCheckpointExecutor` no longer hardcodes `new MemoryStateBackend()` — backend is configurable from environment
- [x] `CepOperator.open()` uses the injected backend via `stateBackend.createKeyedStateBackend()` — self-created fallback only triggers when both injected backend and stateBackend are absent
- [x] `applyPendingRestoreState()` lifecycle is preserved — keyed state restoration works correctly through the `open()` deferred restore path
- [x] **Wiring Verification**: code trace confirms `registerTasksAndTrackers()` → `setStateBackend(configurable)` → operator `open()` → `stateBackend.createKeyedStateBackend()` chain
- [x] **No Silent No-Op**: if `stateBackend.createKeyedStateBackend()` returns a real backend, the `MemoryKeyedStateBackend` fallback in `open()` is NOT used (assert in test)
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-cep,nop-stream/nop-stream-runtime -am` passes
- [x] No owner-doc update required (internal wiring — end-user API unchanged)
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 2 — Snapshot/restore path verification

Status: completed
Targets:
- `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/AbstractStreamOperator.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/StreamTaskInvokable.java` (NOT in nop-stream-runtime — this is in nop-stream-core)

Item Types: `Proof | Fix`

- [x] `Proof` Trace the snapshot path: `StreamTaskInvokable.processInputGate()` → receives `CheckpointBarrier` → `AbstractStreamOperator.processBarrier()` → `snapshotState()` → `CepOperator.snapshotState()` (saves watermark + timers) → `super.snapshotState()` (calls `keyedStateBackend.snapshotState()`). Verified connected.
- [x] `Proof` Trace the restore path: `GraphModelCheckpointExecutor` → `OperatorSnapshotResult` → `CepOperator.restoreState()` (restores watermark + timers via `super.restoreState()`) → `keyedStateBackend` restores keyed state. Verified connected.
- [x] `Proof` Verify NFA state serialization: NFA state stored via `ValueState<List<ComputationState>>` + `MapState` descriptors in `CepOperator.open()`. These are part of `keyedStateBackend.snapshotState()` output.
- [x] `Proof` Verify shared buffer serialization: `SharedBuffer` partitions stored via `MapState` descriptors. Confirmed inclusion in keyed state snapshot.
- [x] `Fix` Any disconnection found in the audit — no disconnects found.

Exit Criteria:

- [x] Full snapshot path traced and confirmed connected: barrier → `processBarrier()` → `super.snapshotState()` → `keyedStateBackend.snapshotState()` → `OperatorSnapshotResult`
- [x] Full restore path traced and confirmed connected: `OperatorSnapshotResult` → `CepOperator.restoreState()` → `super.restoreState()` → keyed state restored
- [x] NFA state (`ValueState<List<ComputationState>>`) and shared buffer (`MapState`) verified as included in keyed state snapshot
- [x] Any disconnected path found is fixed, or `Proof` confirms no disconnection exists
- [x] **Anti-Hollow Check**: `CepOperator.snapshotState()` and `restoreState()` produce/consume data through the full pipeline (not just type-level overrides)
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-cep,nop-stream/nop-stream-runtime -am` passes
- [x] No owner-doc update required
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 3 — Watermark propagation verification

Status: completed
Targets:
- `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/StreamTaskInvokable.java`

Item Types: `Proof | Fix`

- [x] `Proof` Trace single-input watermark path: `TimestampsAndWatermarksOperator.processElement()` → downstream `CepOperator.processElement()` → watermark arrives → `advanceTime()` called internally at line 573.
- [x] `Proof` Verify `advanceTime()` triggers `InternalTimerService.advanceWatermark()` and fires due event-time timers in `CepOperator`.
- [x] `Fix` If multi-input combining (Plan 10 successor executed), verify `CepOperator` on a multi-input path receives combined watermark correctly. Plan 10 successor has NOT executed — condition noted in log.
- [x] `Fix` Any disconnection found in the audit — no disconnects found.

Exit Criteria:

- [x] Single-input watermark propagation path traced and connected: watermark → `processElement()` → `advanceTime()` → timer service
- [x] `advanceTime()` fires due event-time timers (verified by test)
- [x] Multi-input path: conditionally verified if Plan 10 successor is complete; Plan 10 successor has NOT executed — condition noted in execution log
- [x] `./mvnw test -pl nop-stream/nop-stream-cep,nop-stream/nop-stream-core -am` passes
- [x] No owner-doc update required
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 4 — Code cleanup and cache improvement

Status: completed
Targets:
- `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java` (Javadoc)
- `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBuffer.java` (NOT `NfaSharedBuffer.java`)

Item Types: `Fix | Decision`

- [x] `Decision` SharedBuffer cache upgrade: choose between (a) Guava `CacheBuilder` with LRU eviction (Flink pattern, adds Guava dependency), or (b) minimal LRU via `LinkedHashMap` (no new dependency). Option (b) chosen — `LruCache` class with `ConcurrentHashMap` + `LinkedHashMap` access tracker.
- [x] `Fix` Update `CepOperator` Javadoc: remove "hardcoded MemoryKeyedStateBackend" claim at lines 80-83. Document pluggable state backend architecture and the `applyPendingRestoreState()` lifecycle.
- [x] `Fix` Upgrade `SharedBuffer` cache: replace `ConcurrentHashMap` with `LruCache` backed by `ConcurrentHashMap` + `LinkedHashMap`. Write-through to state store for data safety.
- [x] Add focused test: SharedBuffer cache eviction under capacity pressure → oldest entries evicted first.

Exit Criteria:

- [x] `CepOperator` Javadoc accurately describes pluggable state backend (no "hardcoded" claims)
- [x] `SharedBuffer` cache uses LRU eviction (test verifies: insert N+1 entries → oldest entry evicted)
- [x] No new Guava dependency introduced without explicit `Decision` justification
- [x] `./mvnw test -pl nop-stream/nop-stream-cep -am` passes
- [x] No owner-doc update required
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 5 — End-to-end CEP checkpoint/restore test

Status: completed
Targets:
- `nop-stream/nop-stream-cep/src/test/`
- `nop-stream/nop-stream-core/src/test/`

Item Types: `Proof`

- [x] Create E2E test: define CEP pattern (e.g., `start → next(b) within 1min`), feed events, trigger checkpoint, simulate restart (restore from checkpoint), verify pattern matching continues without missing events post-restore.
- [x] Test scenario 1 — NFA state: pattern in partial match state before checkpoint; after restore, verify partial match is preserved and completes correctly.
- [x] Test scenario 2 — Shared buffer: events in buffer before checkpoint; after restore, verify buffer contents are intact.
- [x] Test scenario 3 — Timer: event-time timer registered before checkpoint; after restore with advanced watermark, verify timer fires.
- [x] All tests pass with both `MemoryStateBackend` and `LocalFileCheckpointStorage` (if available).

Exit Criteria:

- [x] E2E scenario 1 passes: partial NFA match survives checkpoint→restore
- [x] E2E scenario 2 passes: shared buffer survives checkpoint→restore
- [x] E2E scenario 3 passes: event-time timer survives checkpoint→restore and fires correctly
- [x] **Anti-Hollow Check**: E2E tests verify behavioral CORRECTNESS post-restore (not just "no exception" — verify pattern matching output matches expected results)
- [x] **端到端验证**: full CEP pipeline from source events → pattern matching → checkpoint → process kill (in-process restore) → continued matching → correct output
- [x] `./mvnw test -pl nop-stream/nop-stream-cep,nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am` passes
- [x] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

- [x] All in-scope gaps addressed: G18 (runtime→setKeyedStateBackend), G19 (runtime→snapshot/restore), G49 (Javadoc), G65 (SharedBuffer cache), G20(part — watermark propagation)
- [x] `GraphModelCheckpointExecutor` backend is configurable (not hardcoded MemoryStateBackend)
- [x] CEP checkpoint/restore E2E test passes for NFA state, shared buffer, and timers
- [x] Watermark correctly propagates to `CepOperator.advanceTime()`
- [x] Existing test suite passes (282 tests, 0 failures)
- [x] No in-scope live defect or contract drift deferred to follow-up
- [x] Independent sub-agent closure-audit completed and evidence recorded
- [x] **Anti-Hollow Check**: (a) configurable backend actually produces a non-Memory backend in operator (not just type-level), (b) CEP snapshot/restore produces/consumes data through full pipeline, (c) E2E test verifies behavioral correctness, not just type-level existence
- [x] **Anti-Hollow Scan**: `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-cep --severity high` exits 0
- [x] `./mvnw compile -pl nop-stream/nop-stream-cep,nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am`
- [x] `./mvnw test -pl nop-stream/nop-stream-cep,nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am`
- [x] `node ai-dev/tools/check-plan-checklist.mjs <this-plan-file> --strict` exits 0

## Deferred But Adjudicated

### Multi-input watermark to CepOperator (G20 part requiring Plan 10 successor)

- Classification: `watch-only residual`
- Why Not Blocking Closure: Multi-input watermark combining requires multi-input operator infrastructure (Plan 10 successor). Single-input watermark path is sufficient for current architecture. Phase 3 explicitly marks this as conditional — skip if Plan 10 has not executed.
- Successor Required: `yes` (covered by Plan 10 successor)

### Guava dependency decision

- Classification: `optimization candidate`
- Why Not Blocking Closure: LRU eviction can be implemented with `LinkedHashMap` (no new dependency) or Guava Cache. Decision deferred to Phase 4 implementation.
- Successor Required: `no`

## Non-Blocking Follow-ups

- (none at draft time)

## Closure

Status Note: completed
Completed: 2026-07-25

Closure Audit Evidence:
- Phase 1: Added `IStateBackend stateBackend` to `CheckpointConfig`, `setStateBackend()` to `StreamExecutionEnvironment`, configurable backend in `GraphModelCheckpointExecutor.registerTasksAndTrackers()`. All 3 new tests pass.
- Phase 2: Code trace confirmed snapshot (barrier→processBarrier→snapshotState→CepOperator.snapshotState→keyedStateBackend.snapshotState) and restore (GraphModelCheckpointExecutor→OperatorSnapshotResult→CepOperator.restoreState→keyedStateBackend) paths fully connected. No fixes needed.
- Phase 3: Single-input watermark trace confirmed (watermark→processElement→advanceTime→timerService). Multi-input path conditional — Plan 10 successor not executed, skipped per plan.
- Phase 4: CepOperator Javadoc updated, SharedBuffer caches upgraded to LruCache (ConcurrentHashMap + LinkedHashMap access tracker, write-through to state).
- Phase 5: TestCepCheckpointRestoreE2E created — 3 scenarios (NFA state, shared buffer, timer) all pass.

Follow-up:
- Multi-input watermark combining (requires Plan 10 successor)
- RocksDB state backend (Phase 1 of broader roadmap)
- Guava dependency decision deferred (LinkedHashMap-based LRU sufficient for current scope)
