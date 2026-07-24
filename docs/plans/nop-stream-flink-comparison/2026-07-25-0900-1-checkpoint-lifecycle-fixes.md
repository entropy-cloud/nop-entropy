# 1 Checkpoint Lifecycle Fixes

> Plan Status: active
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

Status: planned
Targets:
- `CheckpointCoordinator.java`
- `CheckpointMetrics.java`

Item Types: `Fix`

- [ ] Add `CheckpointMetrics.recordAborted(String reason)` method that increments `numAbortedCheckpoints` and sets `failureCause` separately, without touching `numFailedCheckpoints`.
- [ ] Replace `metrics.recordFailure("Aborted: " + reason)` in `CheckpointCoordinator.abortPendingCheckpoint()` with `metrics.recordAborted("Aborted: " + reason)`.
- [ ] Verify no other call site still conflates abort counters.

Exit Criteria:
- [ ] `CheckpointMetrics` has `recordAborted()` that only touches `numAbortedCheckpoints` and `failureCause`.
- [ ] `CheckpointCoordinator.abortPendingCheckpoint()` calls `recordAborted()` (not `recordFailure()`).
- [ ] Focused unit test: call `abortPendingCheckpoint()`, verify `numFailedCheckpoints` does not increment, `numAbortedCheckpoints` does increment, `failureCause` contains "Aborted".
- [ ] No owner-doc update required (internal code fix).
- [ ] `ai-dev/logs/` corresponding date entry updated.

### Phase 2 — Add ERR_STREAM_CHECKPOINT_FAILED error code

Status: planned
Targets:
- `NopStreamErrors.java`
- `PendingCheckpoint.java`

Item Types: `Fix`

- [ ] Add `ERR_STREAM_CHECKPOINT_FAILED` to `NopStreamErrors.java`.
- [ ] Replace `ERR_STREAM_CHECKPOINT_ABORTED` with `ERR_STREAM_CHECKPOINT_FAILED` in `PendingCheckpoint.fail()`.
- [ ] Verify `abort()` continues using `ERR_STREAM_CHECKPOINT_ABORTED` (no regression).

Exit Criteria:
- [ ] `NopStreamErrors.ERR_STREAM_CHECKPOINT_FAILED` exists with distinct error code value.
- [ ] `PendingCheckpoint.fail()` uses `ERR_STREAM_CHECKPOINT_FAILED`.
- [ ] `PendingCheckpoint.abort()` still uses `ERR_STREAM_CHECKPOINT_ABORTED`.
- [ ] All existing checkpoint tests pass (no error-code regression).
- [ ] No owner-doc update required (internal code fix).
- [ ] `ai-dev/logs/` corresponding date entry updated.

## Closure Gates

- [ ] All in-scope confirmed live defects (metrics pollution, error-code mismatch) are fixed.
- [ ] `CheckpointMetrics` counters are accurate: aborts do not inflate failure count.
- [ ] `PendingCheckpoint.fail()` and `abort()` use distinct error codes.
- [ ] Focused verification (unit tests) completed for both phases.
- [ ] No in-scope live defect or contract drift deferred to follow-up.
- [ ] No owner-doc update required — no public contract changed.
- [ ] Independent sub-agent closure-audit completed and evidence recorded.
- [ ] Anti-Hollow Check: new methods (`recordAborted`) have real implementations, no empty bodies or silent fallbacks.
- [ ] `./mvnw compile -pl nop-stream/nop-stream-runtime -am`
- [ ] `./mvnw test -pl nop-stream/nop-stream-runtime -am`
- [ ] Checkstyle / code convention pass.

## Deferred But Adjudicated

None.

## Non-Blocking Follow-ups

- `CheckpointMetricsSnapshot.toString()` missing `failureCause` — tracked in Follow-up Backlog (P2).

## Closure

Status Note: *To be filled on completion.*
Completed:
