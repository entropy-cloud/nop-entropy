# 46 nop-stream Exactly-Once Correctness Bug Fixes

> Plan Status: completed
> Last Reviewed: 2026-05-24
> Source: Deep adversarial audit of Plans 43-45 (3 independent sub-agents)
> Related: Plan 43 (core correctness), Plan 44 (runtime integration), Plan 45 (distributed runtime)

## Purpose

Fix 4 confirmed exactly-once correctness bugs in the single-JVM production path of nop-stream. These bugs were discovered by independent deep audits after Plans 43-45 were marked completed.

## Current Baseline

Plans 43-45 are completed. The single-JVM path (via `StreamExecutionEnvironment.execute()`) works for happy-path streaming with checkpoint/recovery. However, 4 correctness bugs exist:

| Bug | Severity | Location | Impact |
|-----|----------|----------|--------|
| 1. Commit failure swallowed | Critical | `CheckpointCoordinator.java:362-372` | Data loss when sink commit fails transiently |
| 2. Subsuming contract missing | Critical | `TwoPhaseCommitSinkFunction.java:104-112` | Prior epoch transactions never committed |
| 3. Wrong restore fallback | High | `GraphModelCheckpointExecutor.java:576-578, 610-612, 699-701` | Wrong state applied to operators |
| 4. CheckpointedSourceFunction snapshot discarded | High | `StreamSourceOperator.java:219-221` | Source offset lost for non-replayable sources |

## Goals

- Fix all 4 bugs in the single-JVM production path
- Add regression tests proving each fix
- Pass `./mvnw test -pl nop-stream -am`

## Non-Goals

- Distributed path wiring (Plan 47)
- Network transport / remote execution
- Coordinator HA / leader election

## Execution Plan

### Phase 1 - Bug 1: CheckpointCoordinator commit retry

Status: planned
Targets: `nop-stream-runtime/.../CheckpointCoordinator.java`

- Item Types: `Fix | Proof`

- [ ] Add configurable retry (default 3) for `finishCommit` in `notifyParticipantsFinishCommit`
- [ ] Track failed commit epochs in a `Set<Long>` for recovery on next checkpoint cycle
- [ ] On next successful checkpoint completion, retry all previously failed commits before notifying current epoch
- [ ] Test: simulate `finishCommit` throwing on first call, verify retry succeeds

Exit Criteria:

- [ ] `finishCommit` retries up to N times before logging ERROR
- [ ] Failed commits are tracked and retried on subsequent checkpoint completions
- [ ] New test `testCommitFailureRetrySucceeds` passes
- [ ] **端到端验证**: N/A (unit-level fix)
- [ ] **接线验证**: `notifyParticipantsFinishCommit` is called from `completePendingCheckpoint` — verified
- [ ] **无静默跳过**: Retry exhaustion logs ERROR and tracks for later retry (not throws, to avoid crashing coordinator)
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` updated

### Phase 2 - Bug 2: TwoPhaseCommitSinkFunction subsuming contract

Status: planned
Targets: `nop-stream-core/.../TwoPhaseCommitSinkFunction.java`

- Item Types: `Fix | Proof`

- [ ] Add `pendingCommits` field (`TreeMap<Long, Object>`) to track prepared transactions by epochId
- [ ] In `prepareCommit`, store the prepared transaction in `pendingCommits`
- [ ] In `finishCommit(epochId, true)`, commit all entries where key <= epochId and remove them
- [ ] In `finishCommit(epochId, false)`, keep entries for subsuming by next epoch
- [ ] In `restoreFromEpoch`, rollback all pending and clear
- [ ] Test: prepare epoch 1, abort it, prepare epoch 2, commit epoch 2 → verify both TX1 and TX2 committed

Exit Criteria:

- [ ] `commit(N)` commits all pending transactions with epochId <= N
- [ ] Aborted epochs' transactions are kept and subsumed by next successful commit
- [ ] New test `testSubsumingCommit` passes
- [ ] **端到端验证**: N/A (interface-level fix, tested via mock implementation)
- [ ] **接线验证**: `TwoPhaseCommitSinkFunction` is called via `CheckpointParticipant` in coordinator
- [ ] **无静默跳过**: N/A
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` updated

### Phase 3 - Bug 3: GraphModelCheckpointExecutor wrong restore fallback

Status: planned
Targets: `nop-stream-runtime/.../GraphModelCheckpointExecutor.java`

- Item Types: `Fix | Proof`

- [ ] Replace all 3 fallback-to-first-available blocks with fail-fast: throw `StreamException` with clear message
- [ ] Test: restore with mismatched TaskLocation → verify `StreamException` thrown

Exit Criteria:

- [ ] No silent fallback to arbitrary state in any restore path
- [ ] New test `testRestoreFailsOnTaskLocationMismatch` passes
- [ ] **端到端验证**: Restore path is tested via checkpoint recovery test
- [ ] **接线验证**: N/A (removing code path, not adding)
- [ ] **无静默跳过**: Throwing `StreamException` instead of silent fallback
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` updated

### Phase 4 - Bug 4: CheckpointedSourceFunction snapshot result discarded

Status: planned
Targets: `nop-stream-core/.../StreamSourceOperator.java`, `nop-stream-core/.../OperatorSnapshotResult.java`

- Item Types: `Fix | Proof`

- [ ] Add `merge(OperatorSnapshotResult)` method to `OperatorSnapshotResult`
- [ ] In `StreamSourceOperator.snapshotState()`, capture return value of `sourceFunction.snapshotState()` and merge into result
- [ ] Test: `CheckpointedSourceFunction` that returns non-empty state → verify it survives checkpoint/restore cycle

Exit Criteria:

- [ ] `CheckpointedSourceFunction.snapshotState()` return value is merged into operator snapshot
- [ ] New test `testCheckpointedSourceFunctionStateSurvivesRestore` passes
- [ ] **端到端验证**: Source offset round-trip through checkpoint/restore
- [ ] **接线验证**: `StreamSourceOperator.snapshotState` called from `AbstractStreamOperator.processBarrier`
- [ ] **无静默跳过**: N/A
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` updated

### Phase 5 - Build verification and regression

Status: planned
Targets: `nop-stream/` (all modules)

- Item Types: `Proof`

- [ ] `./mvnw test -pl nop-stream -am` passes
- [ ] All new regression tests pass

Exit Criteria:

- [ ] Full module test suite green
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` updated

## Closure Gates

- [ ] All 4 confirmed live defects fixed
- [ ] Regression tests exist for each fix
- [ ] `./mvnw test -pl nop-stream -am` passes
- [ ] `./mvnw compile` passes
- [ ] Anti-Hollow Check: fixes are in the production code path, not test-only
- [ ] Independent closure audit completed
- [ ] `ai-dev/logs/` updated

## Non-Blocking Follow-ups

- Distributed path wiring (Plan 47)
- Coordinator HA
