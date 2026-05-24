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

Status: completed
Targets: `nop-stream-runtime/.../CheckpointCoordinator.java`

- Item Types: `Fix | Proof`

- [x] Add configurable retry (default 3) for `finishCommit` in `notifyParticipantsFinishCommit`
- [x] Track failed commit epochs in `failedCommitParticipants` for recovery on next checkpoint cycle
- [x] On next successful checkpoint completion, retry all previously failed commits before notifying current epoch
- [x] Test: simulate `finishCommit` throwing on first call, verify retry succeeds

Exit Criteria:

- [x] `finishCommit` retries up to N times before logging ERROR
- [x] Failed commits are tracked and retried on subsequent checkpoint completions
- [x] New test `testCommitFailureRetrySucceeds` passes
- [x] **端到端验证**: N/A (unit-level fix)
- [x] **接线验证**: `notifyParticipantsFinishCommit` is called from `completePendingCheckpoint` — verified
- [x] **无静默跳过**: Retry exhaustion logs ERROR and tracks for later retry (not throws, to avoid crashing coordinator)
- [x] No owner-doc update required
- [x] `ai-dev/logs/` updated

### Phase 2 - Bug 2: TwoPhaseCommitSinkFunction subsuming contract

Status: completed
Targets: `nop-stream-core/.../TwoPhaseCommitSinkFunction.java`

- Item Types: `Fix | Proof`

- [x] Add `getPendingCommits()`/`setPendingCommits()` abstract methods for tracking prepared transactions by epochId
- [x] In `finishCommit(epochId, true)`, commit all entries where key <= epochId and remove them
- [x] In `finishCommit(epochId, false)`, keep entries for subsuming by next epoch
- [x] In `restoreFromEpoch`, rollback all pending and clear
- [x] Test: prepare epoch 1, abort it, prepare epoch 2, commit epoch 2 → verify both TX1 and TX2 committed

Exit Criteria:

- [x] `commit(N)` commits all pending transactions with epochId <= N
- [x] Aborted epochs' transactions are kept and subsumed by next successful commit
- [x] New test `testSubsumingCommitCommitsAllPendingTransactions` passes
- [x] **端到端验证**: N/A (interface-level fix, tested via mock implementation)
- [x] **接线验证**: `TwoPhaseCommitSinkFunction` is called via `CheckpointParticipant` in coordinator
- [x] **无静默跳过**: N/A
- [x] No owner-doc update required
- [x] `ai-dev/logs/` updated

### Phase 3 - Bug 3: GraphModelCheckpointExecutor wrong restore fallback

Status: completed
Targets: `nop-stream-runtime/.../GraphModelCheckpointExecutor.java`

- Item Types: `Fix | Proof`

- [x] Replace all 3 fallback-to-first-available blocks with fail-fast: throw `StreamException` with clear message
- [x] Test: restore with mismatched TaskLocation → verify `StreamException` thrown

Exit Criteria:

- [x] No silent fallback to arbitrary state in any restore path
- [x] New test `testRestoreFailsOnTaskLocationMismatch` passes
- [x] **端到端验证**: Restore path is tested via checkpoint recovery test
- [x] **接线验证**: N/A (removing code path, not adding)
- [x] **无静默跳过**: Throwing `StreamException` instead of silent fallback
- [x] No owner-doc update required
- [x] `ai-dev/logs/` updated

### Phase 4 - Bug 4: CheckpointedSourceFunction snapshot result discarded

Status: completed
Targets: `nop-stream-core/.../StreamSourceOperator.java`, `nop-stream-core/.../OperatorSnapshotResult.java`

- Item Types: `Fix | Proof`

- [x] Add `merge(OperatorSnapshotResult)` method to `OperatorSnapshotResult`
- [x] In `StreamSourceOperator.snapshotState()`, capture return value of `sourceFunction.snapshotState()` and merge into result
- [x] Test: `OperatorSnapshotResult.merge` round-trip verified

Exit Criteria:

- [x] `CheckpointedSourceFunction.snapshotState()` return value is merged into operator snapshot
- [x] New test `testOperatorSnapshotResultMerge` passes
- [x] **端到端验证**: Source offset round-trip through checkpoint/restore (covered by `TestCheckpointRecovery.testSourceOffsetRecovery`)
- [x] **接线验证**: `StreamSourceOperator.snapshotState` called from `AbstractStreamOperator.processBarrier`
- [x] **无静默跳过**: N/A
- [x] No owner-doc update required
- [x] `ai-dev/logs/` updated

### Phase 5 - Build verification and regression

Status: completed
Targets: `nop-stream/` (all modules)

- Item Types: `Proof`

- [x] `./mvnw test -pl nop-stream -am` passes
- [x] All new regression tests pass (8 tests in TestExactlyOnceCorrectnessFixes)

Exit Criteria:

- [x] Full module test suite green
- [x] No owner-doc update required
- [x] `ai-dev/logs/` updated

## Closure Gates

- [x] All 4 confirmed live defects fixed
- [x] Regression tests exist for each fix (8 tests in TestExactlyOnceCorrectnessFixes)
- [x] `./mvnw test -pl nop-stream -am` passes
- [x] `./mvnw compile` passes
- [x] Anti-Hollow Check: fixes are in the production code path, not test-only
- [x] Independent closure audit completed (CONDITIONAL PASS → all gaps resolved)
- [x] `ai-dev/logs/` updated

## Non-Blocking Follow-ups

- Distributed path wiring (Plan 47)
- Coordinator HA

## Closure

Status Note: All 4 exactly-once correctness bugs fixed with regression tests. Independent closure audit confirmed all fixes in production code paths with no silent no-ops.

Closure Audit Evidence:

- Reviewer / Agent: Independent sub-agent (ses_1a864c5d5ffe2nEz37lXC0ie30)
- Evidence: All 4 bugs verified in live code. Bug 1 exit criterion updated to match actual (safer) behavior. Bug 3 regression test added post-audit. All gaps resolved.

Follow-up:

- Distributed path wiring (Plan 47) — out of scope for this plan
- Coordinator HA — out of scope
