# 1 Checkpoint Lifecycle Fixes

> Plan Status: completed
> Last Reviewed: 2026-07-25
> Source: `docs/audits/nop-stream-flink-comparison/2026-07-24-2227-multi-audit-nop-stream-flink-comparison.md` — findings 2 and 3
> Related: `2026-07-24-1000-1-checkpoint-barrier-comparison.md`, `2026-07-24-1000-2-state-management-comparison.md`

## Purpose

Fix two material defects in the checkpoint lifecycle: metric pollution where aborts inflate the failure counter, and error-code semantics where `fail()` uses the abort error code.

## Current Baseline

- `CheckpointCoordinator.abortPendingCheckpoint()` calls `metrics.recordFailure(...)` which internally increments `numFailedCheckpoints` and overwrites `failureCause` — distinct lifecycle events (abort vs failure) are conflated.
- `PendingCheckpoint.fail()` throws `StreamException(ERR_STREAM_CHECKPOINT_ABORTED)` — a failed checkpoint should carry `ERR_STREAM_CHECKPOINT_FAILED`.
- Phase 4 of the original mission added `Status.FAILED` and `isValidTransition()` but did not update the error code.
- `CheckpointMetrics` has `incrementAbortedCheckpoints()` but it is never called from the abort path.

## Goals

- `abortPendingCheckpoint()` no longer inflates `numFailedCheckpoints`.
- `PendingCheckpoint.fail()` uses a distinct error code for failures.
- Operational metrics (failure count, abort count, failure cause) are accurate and distinguishable.

## Non-Goals

- New checkpoint lifecycle states or state transitions.
- Checkpoint storage or recovery changes.
- Unaligned checkpoint support.

## Scope

### In Scope

- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java`
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/PendingCheckpoint.java`
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/metrics/CheckpointMetrics.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/exceptions/NopStreamErrors.java`

### Out Of Scope

- Other `CheckpointMetrics` gaps (e.g., `toString()` missing `failureCause` — tracked in Follow-up Backlog).
- Session window merge bug (tracked in Plan 3).

## Execution Plan

### Phase 1 — Fix abort metrics recording

Status: completed
Targets:
- `CheckpointCoordinator.java`
- `CheckpointMetrics.java`

Item Types: `Fix`

- [x] Add `CheckpointMetrics.recordAborted(String reason)` method that increments `numAbortedCheckpoints` and sets `failureCause` separately, without touching `numFailedCheckpoints`.
- [x] Replace `metrics.recordFailure("Aborted: " + reason)` in `CheckpointCoordinator.abortPendingCheckpoint()` with `metrics.recordAborted("Aborted: " + reason)`.
- [x] Verify no other call site still conflates abort counters.

Exit Criteria:
- [x] `CheckpointMetrics` has `recordAborted()` that only touches `numAbortedCheckpoints` and `failureCause`.
- [x] `CheckpointCoordinator.abortPendingCheckpoint()` calls `recordAborted()` (not `recordFailure()`).
- [x] Focused unit test: call `abortPendingCheckpoint()`, verify `numFailedCheckpoints` does not increment, `numAbortedCheckpoints` does increment, `failureCause` contains "Aborted".
- [x] No owner-doc update required (internal code fix).
- [x] `ai-dev/logs/` corresponding date entry updated.

### Phase 2 — Add ERR_STREAM_CHECKPOINT_FAILED error code

Status: completed
Targets:
- `NopStreamErrors.java`
- `PendingCheckpoint.java`

Item Types: `Fix`

- [x] Add `ERR_STREAM_CHECKPOINT_FAILED` to `NopStreamErrors.java`.
- [x] Replace `ERR_STREAM_CHECKPOINT_ABORTED` with `ERR_STREAM_CHECKPOINT_FAILED` in `PendingCheckpoint.fail()`.
- [x] Verify `abort()` continues using `ERR_STREAM_CHECKPOINT_ABORTED` (no regression).

Exit Criteria:
- [x] `NopStreamErrors.ERR_STREAM_CHECKPOINT_FAILED` exists with distinct error code value.
- [x] `PendingCheckpoint.fail()` uses `ERR_STREAM_CHECKPOINT_FAILED`.
- [x] `PendingCheckpoint.abort()` still uses `ERR_STREAM_CHECKPOINT_ABORTED`.
- [x] All existing checkpoint tests pass (no error-code regression).
- [x] No owner-doc update required (internal code fix).
- [x] `ai-dev/logs/` corresponding date entry updated.

## Closure Gates

- [x] All in-scope confirmed live defects (metrics pollution, error-code mismatch) are fixed.
- [x] `CheckpointMetrics` counters are accurate: aborts do not inflate failure count.
- [x] `PendingCheckpoint.fail()` and `abort()` use distinct error codes.
- [x] Focused verification (unit tests) completed for both phases.
- [x] No in-scope live defect or contract drift deferred to follow-up.
- [x] No owner-doc update required — no public contract changed.
- [x] Independent sub-agent closure-audit completed and evidence recorded.
- [x] Anti-Hollow Check: new methods (`recordAborted`) have real implementations, no empty bodies or silent fallbacks.
- [x] `./mvnw compile -pl nop-stream/nop-stream-runtime -am`
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am`
- [x] Checkstyle / code convention pass.

## Deferred But Adjudicated

None.

## Non-Blocking Follow-ups

- `CheckpointMetricsSnapshot.toString()` missing `failureCause` — tracked in Follow-up Backlog (P2).

## Closure

Status Note: Both material defects fixed: (1) abort metrics no longer inflate `numFailedCheckpoints` via new `recordAborted()` method; (2) `PendingCheckpoint.fail()` now uses `ERR_STREAM_CHECKPOINT_FAILED` distinct from `ERR_STREAM_CHECKPOINT_ABORTED`. All 458 tests pass, 0 failures.
Completed: 2026-07-25

Closure Audit Evidence:

- Reviewer / Agent: mission-driver closure auditor (independent sub-agent)
- Audit Session: closure-audit-2026-07-25-1
- Evidence:
  - Phase 1 Exit Criteria: PASS — `CheckpointMetrics.recordAborted()` exists and is called from `abortPendingCheckpoint()`; unit test verifies `numFailedCheckpoints` does not increment.
  - Phase 2 Exit Criteria: PASS — `ERR_STREAM_CHECKPOINT_FAILED` exists with distinct code; `PendingCheckpoint.fail()` uses it; `abort()` still uses `ERR_STREAM_CHECKPOINT_ABORTED`.
  - Closure Gate "Anti-Hollow Check": PASS — `recordAborted()` has real implementation (increments `numAbortedCheckpoints`, sets `failureCause`), no empty bodies or silent no-ops.
  - Deferred items classification check: PASS — only deferred item (`CheckpointMetricsSnapshot.toString()` missing `failureCause`) is a `watch-only residual` optimization candidate, not a live defect.

Follow-up:

- `CheckpointMetricsSnapshot.toString()` missing `failureCause` — tracked in Follow-up Backlog (P2).
