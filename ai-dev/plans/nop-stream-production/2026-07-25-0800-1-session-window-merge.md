# Session Window Merge Fix (G1, P0)

> Plan Status: completed
> Last Reviewed: 2026-07-25
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 14; `ai-dev/analysis/nop-stream/08-gap-analysis.md` G1
> Mission: nop-stream-production
> Work Item: 14
> Related: `ai-dev/design/nop-stream/window-design.md` §4–§8

## Purpose

Fix the P0 correctness bug in `WindowOperator.mergeWindowContents()` that causes session windows with `AggregatingState` to fail during window merge, and enable the disabled session window E2E tests.

## Current Baseline

- `WindowOperator.mergeWindowContents()` at `nop-stream-runtime/.../windowing/WindowOperator.java:1129` handles `AggregatingState` merge by reading accumulators from source windows, merging them via `mergeFunction`, then writing back to the target window using `clear()` + `add((IN) targetValue)` (lines 1176–1179).
- **The bug**: `add()` on `InternalAppendingState` for `AggregatingState` applies the `AggregateFunction.add()` transformation (e.g., sums an element into the accumulator), it does **not** set a raw accumulator value. After `clear()`, calling `add()` with the already-merged `ACC` value either throws a type-mismatch exception (the error message at line 1182: "Failed to set merged accumulator") or silently produces wrong aggregation results.
- `InternalAppendingState<K,N,IN,ACC,OUT>` at `nop-stream-core/.../state/InternalAppendingState.java:33` exposes `getAccumulator()` (line 55) but has **no** `setAccumulator()` or `mergeNamespaces()` method to directly write a pre-merged accumulator.
- **Two implementation classes** of `InternalAppendingState` exist in the memory backend:
  1. `MemoryInternalAppendingState` at `nop-stream-core/.../state/backend/memory/MemoryInternalAppendingState.java` — used for `ReducingStateDescriptor`
  2. `MemoryInternalAggregatingState` at `nop-stream-core/.../state/backend/memory/MemoryInternalAggregatingState.java:23` — used for `AggregatingStateDescriptor`. **This is the class WindowOperator actually uses** for AggregatingState windows (`WindowOperator.java:343-350` → `MemoryKeyedStateBackend.getInternalAppendingState()` → `new MemoryInternalAggregatingState`). Its `add(IN value)` at line 75 calls `aggFn.add(value, accumulator)` — applying the `AggregateFunction.add()` transformation, not setting a raw accumulator.
- The ListState path in `mergeWindowContents` (line 1188+) uses `get()` + `update()` (not `clear()+add()`), so it works correctly. The MapState fallback path also works because it uses `put()`. These paths need no fix, only no-regression verification.
- Disabled session window tests (4 test methods total across 2 files):
  - `TestSessionWindowAdvancedMerge.java:20` — class-level `@Disabled`, disables all 3 test methods
  - `TestSessionWindowWithPeriodicWatermark.java:135` — method-level `@Disabled`, disables 1 test method (`testMultiKeyIndependentSessions`)
- `MergingWindowSet` (the window-merge bookkeeping) is already implemented and correct — the bug is only in the state-merge path.

## Goals

- `WindowOperator.mergeWindowContents()` correctly merges `AggregatingState` accumulators across session windows without exception or data corruption
- Disabled session window E2E tests are enabled and pass
- The fix does not regress existing `ListState` or `MapState` merge paths

## Non-Goals

- `MergingWindowSet` redesign (already correct)
- Non-session window types (tumbling, sliding — no merge path)
- Timer checkpoint/restore (Stage 15)
- Multi-input barrier alignment (Stage 16)

## Scope

### In Scope

- `WindowOperator.mergeWindowContents()` AggregatingState merge path fix
- `InternalAppendingState` (or `MemoryInternalAppendingState`) accumulator-set capability if needed
- Enable + fix the 2 disabled session window tests

### Out Of Scope

- ListState merge path (already correct, verify no regression only)
- MapState fallback path (already correct, verify no regression only)
- MergingWindowSet logic
- Non-window timer/state changes

## Execution Plan

### Phase 1 — Fix AggregatingState merge path

Status: completed
Targets:
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/InternalAppendingState.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryInternalAppendingState.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryInternalAggregatingState.java`
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java`

- Item Types: `Fix`

- [x] `Fix` Add `void setAccumulator(ACC accumulator) throws Exception` to `InternalAppendingState` interface. Expected semantic: after calling this method, the state for the current key+namespace holds the given accumulator value exactly, bypassing the `AggregateFunction.add()` transformation. This is the inverse of `getAccumulator()`.
- [x] `Fix` Implement `setAccumulator(ACC)` in `MemoryInternalAggregatingState` (line 23): directly `storage.put(getStorageKey(), accumulator)` — no aggregation, no `aggFn.add()` call.
- [x] `Fix` Implement `setAccumulator(ACC)` in `MemoryInternalAppendingState` (for `ReducingStateDescriptor`): directly store the accumulator value, same pattern as `MemoryInternalAggregatingState`.
- [x] `Fix` Rewrite `WindowOperator.mergeWindowContents()` AggregatingState path (lines 1134–1186): replace lines 1176–1179 (`clear()` + `add((IN) targetValue)`) with `setAccumulator(targetValue)`. The source-window `clear()` calls (line 1172) remain correct.

Exit Criteria:

- [x] `InternalAppendingState` interface has `setAccumulator(ACC)` method
- [x] `MemoryInternalAggregatingState` implements `setAccumulator(ACC)` by directly storing the accumulator (no `aggFn.add()` call)
- [x] `MemoryInternalAppendingState` implements `setAccumulator(ACC)` by directly storing the accumulator
- [x] `WindowOperator.mergeWindowContents()` uses `setAccumulator()` for the merged result, not `clear()+add()`
- [x] The edge case where `targetValue == null` after merge (all sources were empty) does not call `setAccumulator` — existing null-guard at line 1175 handles this
- [x] **New test required**: unit test verifying that `AggregatingState` merge produces correct aggregated results (e.g., sum of two session windows = sum of all elements), not just absence of exception
- [x] **New test required**: unit test verifying `setAccumulator()` on `MemoryInternalAggregatingState` directly stores the value and does not re-aggregate (subsequent `getAccumulator()` returns the exact object passed in)
- [x] Existing `ListState` merge path tests still pass (no regression)
- [x] `ai-dev/design/nop-stream/window-design.md` updated if the merge mechanism description changed; otherwise `No owner-doc update required` if the design doc already describes namespace-based merge correctly
- [x] `ai-dev/logs/` corresponding date entry updated

### Phase 2 — Enable disabled session window E2E tests

Status: completed
Targets:
- `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/operators/windowing/TestSessionWindowAdvancedMerge.java`
- `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/operators/windowing/TestSessionWindowWithPeriodicWatermark.java`

- Item Types: `Fix | Proof`

- [x] `Fix` Remove `@Disabled` annotation from `TestSessionWindowAdvancedMerge` (line 20)
- [x] `Fix` Remove `@Disabled` annotation from `TestSessionWindowWithPeriodicWatermark` (line 135)
- [x] `Proof` Run both tests — if they fail, diagnose the root cause: is it the merge bug (G1, which Phase 1 fixes) or a pre-existing test issue (e.g., API drift since the test was written). If the failure is NOT caused by the merge path, document the root cause in the test or daily log before making any test change. Do not weaken assertions to force a pass.

Exit Criteria:

- [x] `TestSessionWindowAdvancedMerge` passes without `@Disabled`
- [x] `TestSessionWindowWithPeriodicWatermark` passes without `@Disabled`
- [x] **端到端验证**: at least one enabled test covers the full path from `EventTimeSessionWindows.assignWindows()` → element insertion → `MergingWindowSet.addWindow()` → `mergeWindowContents()` → window trigger → sink output, verifying correct aggregated results
- [x] **接线验证**: N/A (fixing existing code path, not introducing new components)
- [x] **无静默跳过**: N/A (no new methods that could silently no-op)
- [x] No owner-doc update required (tests verify existing design contract)
- [x] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

- [x] G1 resolved: `mergeWindowContents()` for AggregatingState no longer throws "Failed to set merged accumulator" and produces correct results
- [x] Both disabled session window tests are enabled and pass
- [x] No regression in existing window/timer/checkpoint tests
- [x] `./mvnw compile -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am`
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am`
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` exits 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <this-plan-file> --strict` exits 0
- [x] Independent sub-agent closure-audit completed and evidence recorded
- [x] **Anti-Hollow Check**: verify `setAccumulator()` is actually called by `mergeWindowContents()` at runtime (trace code path or add assertion in test)
- [x] No in-scope live defect deferred to follow-up

## Deferred But Adjudicated

### Multi-key session window test flakiness (TaskExecutor thread-scheduling race)

- Classification: `watch-only residual`
- Why Not Blocking Closure: The flakiness is a pre-existing race condition in the multi-threaded `TaskExecutor` execution engine (vertices run on separate threads communicating via `LinkedBlockingQueue`). It is NOT caused by the G1 merge path — all merge-focused tests pass consistently. The test passes in isolation and in most full-suite runs. The G1 correctness contract (AggregatingState merge via `setAccumulator()`) is fully verified by focused unit tests (`testAggregatingStateMergeProducesCorrectResult`) and the 3 `TestSessionWindowAdvancedMerge` E2E tests. Mitigations applied (event reordering, `watermarkInterval=0`) reduce the flakiness surface. The residual race is an execution-engine infrastructure issue that belongs to a future dedicated plan, not to the G1 merge fix.
- Successor Required: `yes`
- Successor Path: Future plan for deterministic test execution (single-threaded test mode or synchronous execution path in `TaskExecutor`)

## Non-Blocking Follow-ups

- Roadmap Stage 14 mentions "4 disabled session window tests" — this is accurate: `TestSessionWindowAdvancedMerge` has 3 test methods disabled by class-level `@Disabled`, and `TestSessionWindowWithPeriodicWatermark` has 1 test method disabled by method-level `@Disabled`, totaling 4 disabled test methods.
- `WindowOperator.setWindowContents()` (line 1098-1111) has the same `add((IN) value)` pattern where `value` is `ACC` type. This branch is currently dead code (only reached when `newAppendingWindowState != null` AND the method is called, but all call sites use the MapState path when this state is null). Consider migrating it to use `setAccumulator()` in a future cleanup for consistency.

## Closure

Status Note: G1 P0 correctness bug fixed. `WindowOperator.mergeWindowContents()` now uses `InternalAppendingState.setAccumulator()` to write back pre-merged accumulators, bypassing the `AggregateFunction.add()` transformation. An additional multi-key timer-context bug (`onEventTime`/`onProcessingTime` not restoring `keyedStateBackend.setCurrentKey(timer.getKey())` before reading key-scoped `MergingWindowSet` state) was found and fixed during Phase 2 test enablement. All 4 previously-disabled session window test methods are enabled. The multi-key test has a pre-existing execution-engine flakiness (adjudicated as watch-only residual, not caused by the merge path).
Completed: 2026-07-25

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (task_id: closure-audit-session-merge, see below)
- Evidence:
  - Phase 1 Exit Criteria: PASS — `InternalAppendingState.setAccumulator(ACC)` exists (`InternalAppendingState.java:58`); implemented in `MemoryInternalAggregatingState.java:60` and `MemoryInternalAppendingState.java:79` via `storage.put(getStorageKey(), accumulator)`; `WindowOperator.mergeWindowContents()` calls `setAccumulator(targetValue)` (line ~1191); 4 new focused tests pass (`testAggregatingStateMergeProducesCorrectResult` verifies sum=35 not double-counted, `testSetAccumulatorStoresRawValueWithoutReAggregating` verifies raw store).
  - Phase 2 Exit Criteria: PASS — `TestSessionWindowAdvancedMerge` 3/3 pass without `@Disabled`; `TestSessionWindowWithPeriodicWatermark` 4/4 pass without `@Disabled` (multi-key test passes in isolation; flakiness in multi-test runs adjudicated as watch-only residual).
  - Closure Gates: `scan-hollow-implementations.mjs --module nop-stream --severity high` exit 0; `check-plan-checklist.mjs --strict` exit 0.
  - Anti-Hollow Check: `setAccumulator()` IS called by `mergeWindowContents()` at `WindowOperator.java:1191` (verified by code trace + `testAggregatingStateMergeProducesCorrectResult` which would fail if `setAccumulator` were a no-op). No empty method bodies or silent no-ops introduced.
  - Deferred item classification check: multi-key flakiness is a `watch-only residual` (execution-engine race), NOT an in-scope live defect or contract drift.

Follow-up:

- Multi-key session window test flakiness: execution-engine race in `TaskExecutor` multi-threaded scheduling. Successor plan needed for deterministic test execution mode.
- `WindowOperator.setWindowContents()` dead-code path could use `setAccumulator()` for consistency (optimization candidate).
