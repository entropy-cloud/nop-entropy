# Timer Checkpoint/Restore + Timer Service Unification (G2, G16)

> Plan Status: active
> Last Reviewed: 2026-07-25
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 15; `ai-dev/analysis/nop-stream/08-gap-analysis.md` G2, G16
> Mission: nop-stream-production
> Work Item: 15
> Related: `ai-dev/design/nop-stream/checkpoint-design.md` §2.1 (timer state in epoch binding); `ai-dev/design/nop-stream/time-model-design.md`

## Purpose

Make timer state survive checkpoint/restore so that event-time and processing-time timers fire correctly after recovery, and eliminate the duplicate timer service implementations (G2 + G16). This unblocks Stage 16 (multi-input barrier alignment) which depends on correct timer lifecycle.

## Current Baseline

- **Two duplicate timer service implementations exist**:
  1. `HeapInternalTimerService<N>` at `nop-stream-core/.../operators/HeapInternalTimerService.java:28` — uses `TreeMap<Long, Set<TimerEntry<N>>>`, `Triggerable<Object, N>`, no `K` type parameter (uses raw `Object` for keys). Has `advanceWatermark()`, `fireProcessingTimeTimers()`, `numEventTimeTimers()`, `numProcessingTimeTimers()` methods beyond the `InternalTimerService<N>` interface.
  2. `WindowOperatorTimerService<K, N>` at `nop-stream-runtime/.../operators/WindowOperatorTimerService.java:26` — uses `PriorityQueue<InternalTimer<K, N>>`, `Triggerable<K, N>`, has proper `K` type parameter. Has `advanceWatermark()`, `advanceProcessingTime()` but lacks `fireProcessingTimeTimers()`.

- **Production consumers of `HeapInternalTimerService`** (not just WindowOperator):
  - `ProcessOperator.java:14,29` — holds `HeapInternalTimerService<VoidNamespace>`, `implements Triggerable<Object, VoidNamespace>`, creates it in `open()` at line 29, registers with `TimerServiceManager` at line 34.
  - `TimerServiceManager.java:26,28` — holds `List<HeapInternalTimerService<?>>`, directly calls `service.advanceWatermark()` and `service.fireProcessingTimeTimers()` (methods NOT on `InternalTimerService<N>` interface). This is a concrete-class dependency.

- **Production consumer of `WindowOperatorTimerService`**:
  - `WindowOperator.java:369` — creates `WindowOperatorTimerService<K,W>`, then in `processWatermark()` at line 401 uses `instanceof` check + cast to call `advanceWatermark()`.

- **9 test files** reference `WindowOperatorTimerService` via `instanceof` + cast pattern: `TestWindowOperatorIntegration`, `TestEvictorIntegration`, `TestWindowOperatorAccType`, `TestWindowOperatorBuilder`, `TestPaneInfoAndAccumulationMode`, `TestWindowOperatorBehavior`, `TestTimeEvictorIntegration`, `TestWindowOperatorEvictorTimestamps`, `TestWindowOperatorCorrectness`.

- `InternalTimerService<N>` interface at `nop-stream-core/.../operators/InternalTimerService.java:31` has **no** snapshot/restore methods — only register/delete/forEach.
- `WindowOperator.snapshotState()` at line 421 snapshots only `trigger-accumulators` (line 430), **not** timers.
- `WindowOperator.restoreState()` at line 437 restores only `trigger-accumulators` (line 450), **not** timers.
- **Critical timing constraint**: `restoreState()` is called **before** `open()` (confirmed by `TestCheckpointRecovery.java:478` comment). At restore time, `internalTimerService` is still null. The existing `trigger-accumulators` restore works because it only stores into a field (`this.triggerAccumulators`) for deferred application. Timer restore must use the same deferred-application pattern.
- `CepOperator` already has its own timer persistence via a **bypass mechanism**: it maintains `registeredEventTimeTimers` (`TreeSet<Long>`, line 233), snapshots them in `snapshotState()` (line 318, key `"event-time-timers"`), and restores in `restoreState()` (line 332-339). This is a separate path from `InternalTimerService` snapshot/restore. If `InternalTimerService` interface gains new methods, `CepOperator`'s anonymous `InternalTimerService<VoidNamespace>` implementation (line 235) must also implement them.
- `checkpoint-design.md` §2.1 line 35 explicitly lists "timer state | event-time 和 processing-time timer 的待触发集合" as part of the epoch binding — this is a design contract that is currently unfulfilled for `WindowOperator`.
- Decision point D5 in roadmap: "全量 checkpoint（先正确再优化）" — full timer checkpoint, not incremental.

## Goals

- Timer state (event-time + processing-time timers) is included in checkpoint snapshots and correctly restored in `WindowOperator`
- After checkpoint→kill→restore, timers that were registered before the checkpoint fire at the correct time
- The two duplicate timer service implementations are merged into a single implementation
- `ProcessOperator` and `TimerServiceManager` continue to work after unification

## Non-Goals

- Timer incremental/differential checkpoint (optimization, later)
- Key-group aware timer snapshot (requires Stage 34 Key-Group model)
- Processing-time timer persistence across JVM restarts with real-time clock skew handling (assumes same clock domain)
- Cross-backend timer serialization (current scope: `MemoryStateBackend` only; future backends need proper serialization)
- Changing CepOperator's existing timer persistence approach (it works correctly via bypass)
- Mailbox execution model (Stage 17)

## Scope

### In Scope

- G2: Add timer snapshot/restore to the unified timer service; wire into `WindowOperator.snapshotState()` / `restoreState()` with deferred-application for restore-before-open
- G16: Merge `HeapInternalTimerService` and `WindowOperatorTimerService` into a single implementation with `K` type parameter
- Update `ProcessOperator`, `TimerServiceManager`, and 9 test files for the unified service

### Out Of Scope

- Key-group partitioned timer snapshot (Stage 34)
- Timer TTL or cleanup policies
- CepOperator timer persistence (already works via bypass)
- Cross-backend serialization format design (future backend plan)

## Execution Plan

### Phase 1 — Unify timer service implementations (G16)

Status: planned
Targets:
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/HeapInternalTimerService.java` (surviving class — add `K` type parameter)
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/WindowOperatorTimerService.java` (remove or deprecate)
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java` (update timer service creation + watermark advance)
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/ProcessOperator.java` (adapt to `HeapInternalTimerService<Object, VoidNamespace>`)
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/TimerServiceManager.java` (adapt to unified type)
- 9 test files with `instanceof WindowOperatorTimerService` casts

- Item Types: `Fix | Decision`

- [ ] `Decision` **Surviving class: `HeapInternalTimerService<K, N>`** (renamed from `HeapInternalTimerService<N>` to add `K` type parameter). Rationale: it lives in `nop-stream-core` (lower level, accessible to both `ProcessOperator` and `WindowOperator`), already has `fireProcessingTimeTimers()` needed by `TimerServiceManager`, and has more complete test coverage.
- [ ] `Fix` Add `K` type parameter to `HeapInternalTimerService`: change signature from `HeapInternalTimerService<N>` to `HeapInternalTimerService<K, N>`, change `Triggerable<Object, N>` to `Triggerable<K, N>`, change `TimerEntry` key type from `Object` to `K`. `ProcessOperator` uses `K=Object`, `WindowOperator` uses `K=keyType`.
- [ ] `Fix` Verify `HeapInternalTimerService` already has `advanceWatermark()` and `fireProcessingTimeTimers()` (needed by `TimerServiceManager`). `WindowOperatorTimerService`'s `advanceProcessingTime()` has zero production callers — do NOT port it (avoid dead code).
- [ ] `Fix` Update `WindowOperator.java:369` to create `HeapInternalTimerService<K, W>` instead of `WindowOperatorTimerService<K, W>`. Change the `internalTimerService` field type (line 220, currently `InternalTimerService<W>`) to `HeapInternalTimerService<K, W>` so that `snapshotTimers()`/`restoreTimers()` can be called without cast in Phase 2, and `advanceWatermark()` can be called without `instanceof` at line 401.
- [ ] `Fix` Update `ProcessOperator.java:14,29` to use `HeapInternalTimerService<Object, VoidNamespace>` (was `HeapInternalTimerService<VoidNamespace>`). The `Triggerable<Object, VoidNamespace>` interface is unchanged.
- [ ] `Fix` Update `TimerServiceManager.java:26,28` to use `HeapInternalTimerService<?, ?>` instead of `HeapInternalTimerService<?>`.
- [ ] `Fix` Update all 9 test files: replace `instanceof WindowOperatorTimerService` + cast with `instanceof HeapInternalTimerService` + cast. The `advanceWatermark()` method exists on both classes.
- [ ] `Fix` Remove `WindowOperatorTimerService.java` or mark it `@Deprecated` with javadoc pointing to `HeapInternalTimerService`. Verify no remaining references.

Exit Criteria:

- [ ] `HeapInternalTimerService<K, N>` is the sole timer service implementation with proper `K` type parameter
- [ ] `WindowOperator` creates `HeapInternalTimerService` in `open()` and casts to it for `advanceWatermark()`
- [ ] `ProcessOperator` compiles and works with `HeapInternalTimerService<Object, VoidNamespace>`
- [ ] `TimerServiceManager` compiles and works with unified type
- [ ] All 9 test files updated; no remaining `WindowOperatorTimerService` references in test code
- [ ] All existing timer tests pass: `TestHeapInternalTimerService`, `TestHeapInternalTimerServiceReentrancy`, `TestTimerServiceManager`, `TestTimerServiceManagerRobustness`, `TestWindowOperatorWatermarkReception`, and all 9 `WindowOperator*` test files
- [ ] **New test required**: verify the unified `HeapInternalTimerService<K,N>` handles both event-time and processing-time timers with a typed key (not just `Object`)
- [ ] **接线验证**: verify `ProcessOperator.open()` creates `HeapInternalTimerService` and registers it with `TimerServiceManager` (existing test `TestTimerServiceManager` should confirm)
- [ ] `ai-dev/design/nop-stream/time-model-design.md` updated to reflect the unified timer service with `K` type parameter
- [ ] `ai-dev/logs/` corresponding date entry updated

### Phase 2 — Timer snapshot/restore (G2)

Status: planned
Targets:
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/HeapInternalTimerService.java` (add snapshot/restore methods)
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java` (wire snapshot/restore)

- Item Types: `Fix`

- [ ] `Fix` Add `snapshotTimers()` to `HeapInternalTimerService<K,N>`: returns a `Serializable` `TimerSnapshot` DTO containing lists of `(K key, N namespace, long timestamp)` entries for both event-time and processing-time timers. For `MemoryStateBackend`, the K and N objects are stored directly (they must be `Serializable`). Implementation: iterate internal `TreeMap` data structures, collect entries.
- [ ] `Fix` Add `restoreTimers(TimerSnapshot snapshot)` to `HeapInternalTimerService<K,N>`: directly inserts `TimerEntry<K,N>` objects (with the snapshot's stored key, NOT from `currentKeySupplier`) into the internal `TreeMap` data structures. Bypassing `currentKeySupplier` is critical — the supplier returns the current processing key which may be null or stale during restore.
- [ ] `Fix` Wire timer snapshot into `WindowOperator.snapshotState()` (line 421): after the existing trigger-accumulators snapshot (line 430), add `result.putOperatorState("internal-timers", internalTimerService.snapshotTimers())`.
- [ ] `Fix` Wire timer restore into `WindowOperator.restoreState()` (line 437) using **deferred application**: store the timer snapshot into a transient field (e.g., `this.restoredTimerSnapshot`) during `restoreState()` (do NOT call `restoreTimers()` yet — `internalTimerService` is null at this point). Apply the snapshot in `open()` after line 370 (after `internalTimerService` is created): `if (restoredTimerSnapshot != null) { internalTimerService.restoreTimers(restoredTimerSnapshot); restoredTimerSnapshot = null; }`.

Exit Criteria:

- [ ] `HeapInternalTimerService<K,N>` has `snapshotTimers()` returning a `Serializable` `TimerSnapshot` DTO and `restoreTimers(TimerSnapshot)` method
- [ ] `WindowOperator.snapshotState()` includes timer state under key `"internal-timers"`
- [ ] `WindowOperator.restoreState()` stores timer snapshot for deferred application; `WindowOperator.open()` applies it after timer service creation
- [ ] **New test required**: unit test verifying `snapshotTimers()` → `restoreTimers()` round-trip preserves all registered timers (event-time + processing-time, with typed key + namespace)
- [ ] **New test required**: unit test verifying restored timers fire at the correct timestamp when `advanceWatermark()` is called
- [ ] **New test required**: unit test verifying the deferred-application pattern: `restoreState()` stores snapshot, `open()` applies it, timers fire correctly after both calls
- [ ] **接线验证**: verify `WindowOperator.snapshotState()` actually calls `snapshotTimers()` (add assertion or trace in test)
- [ ] **No silent skip**: if timer snapshot is null/empty, `restoreTimers()` is a correct no-op (not an error); but `snapshotTimers()` on a non-empty service must return non-null
- [ ] **CepOperator compatibility**: verify `CepOperator`'s anonymous `InternalTimerService<VoidNamespace>` implementation (line 235) is NOT broken. If `snapshotTimers/restoreTimers` are added to the `InternalTimerService` interface, provide default no-op implementations. If they are only on `HeapInternalTimerService` (not the interface), CepOperator is unaffected. **Decision: add methods to `HeapInternalTimerService` concrete class, NOT to the `InternalTimerService` interface**, to avoid breaking CepOperator's anonymous implementation and other `InternalTimerService` implementations.
- [ ] `ai-dev/design/nop-stream/checkpoint-design.md` §2.1 updated: add implementation status note that timer state is now persisted for WindowOperator (CepOperator already had its own bypass mechanism)
- [ ] `ai-dev/logs/` corresponding date entry updated

### Phase 3 — E2E checkpoint→restore→timer-fire test

Status: planned
Targets:
- `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/` (new test file)

- Item Types: `Proof`

- [ ] `Proof` Write an E2E test using `WindowOperatorBuilder` to create a `WindowOperator` with tumbling windows and `AggregateFunction`. Test steps: (1) `open()` the operator, (2) process elements with timestamps that register event-time cleanup timers via the trigger, (3) call `snapshotState()` to capture timer snapshot, (4) create a **new** `WindowOperator` instance via `WindowOperatorBuilder` with the same configuration, (5) call `restoreState(snapshotResult)` on the new operator, (6) call `open()` on the new operator (this applies the deferred timer snapshot), (7) call `processWatermark()` past the timer threshold, (8) verify the restored timers fire and produce correct window output matching the pre-checkpoint state.

Exit Criteria:

- [ ] **端到端验证**: E2E test covers the full path: element processing → timer registration → checkpoint snapshot → new operator creation → `restoreState()` (deferred store) → `open()` (timer restore application) → watermark advance → timer fire → window output
- [ ] The E2E test verifies that timers NOT yet fired before checkpoint are correctly restored and fire after restore
- [ ] The E2E test verifies that no timers are double-fired (already-fired timers are not in the snapshot)
- [ ] **无静默跳过**: verify that if timer snapshot is empty (no timers registered), the restore path completes without error
- [ ] No owner-doc update required (test verifies existing design contract from checkpoint-design.md §2.1)
- [ ] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

- [ ] G2 resolved: timer state survives checkpoint/restore and timers fire correctly after recovery
- [ ] G16 resolved: only one timer service implementation (`HeapInternalTimerService<K,N>`) exists
- [ ] `ProcessOperator` and `TimerServiceManager` compile and pass tests after unification
- [ ] `WindowOperator.snapshotState()` includes timer state
- [ ] Deferred-application pattern handles restore-before-open correctly
- [ ] E2E checkpoint→restore→timer-fire test passes
- [ ] CepOperator not broken by changes
- [ ] No regression in existing window/checkpoint/timer tests
- [ ] `./mvnw compile -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am`
- [ ] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am`
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` exits 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-production/2026-07-25-0800-2-timer-checkpoint-unify.md --strict` exits 0
- [ ] Independent sub-agent closure-audit completed and evidence recorded
- [ ] **Anti-Hollow Check**: closure audit verifies (a) `snapshotTimers()` is called by `WindowOperator.snapshotState()` at runtime, (b) deferred timer restore is applied in `open()` at runtime, (c) the E2E test actually triggers the timer-restore code path (not just window-state restore), (d) `ProcessOperator` timer service is still created and registered after unification
- [ ] No in-scope live defect deferred to follow-up

## Deferred But Adjudicated

### Timer incremental checkpoint

- Classification: `optimization candidate`
- Why Not Blocking Closure: Roadmap decision point D5 explicitly states "全量 checkpoint（先正确再优化）". Full timer checkpoint is sufficient for correctness. Incremental optimization belongs to Stage 18 (async snapshot pipeline).
- Successor Required: `yes`
- Successor Path: Stage 18

### Key-group partitioned timer snapshot

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: nop-stream currently has no Key-Group model (Stage 34). Timer snapshot is flat (all timers in one snapshot), which is correct for the current execution model.
- Successor Required: `yes`
- Successor Path: Stage 34

### Cross-backend timer serialization

- Classification: `optimization candidate`
- Why Not Blocking Closure: Current scope covers `MemoryStateBackend` only, where K/N objects are stored directly as `Serializable`. Future persistent backends (RocksDB, Stage 30) will need proper byte-level serialization. The `TimerSnapshot` DTO is designed to be extensible.
- Successor Required: `yes`
- Successor Path: Stage 30

## Non-Blocking Follow-ups

- CepOperator already has its own timer persistence bypass (`registeredEventTimeTimers` TreeSet → `snapshotState`). This plan does not unify CepOperator's approach with `HeapInternalTimerService.snapshotTimers()`. A future cleanup could migrate CepOperator to use the unified mechanism, but this is not required for correctness.

## Closure

Status Note: (to be filled on completion)
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: (to be filled by independent sub-agent)
- Evidence: (to be filled)

Follow-up:

- (to be filled on closure)
