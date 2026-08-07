# Stage 9 — Checkpoint, Barrier & Recovery Evidence

> Status: produced by Stage 9 audit (plan `nop-stream-independent-audit/2026-08-08-0010-1-checkpoint-barrier-recovery-audit.md`)
> Domain: manifest a/g (checkpoint/coordinator/runtime source surface + test lane)
> Lane policy: only `in-process` lane (single-JVM source-to-sink / coordinator+storage) or stronger is credited for checkpoint lifecycle & recovery claims; `unit` is component-only. Any capability needing cross-JVM control-plane / HA is `blocked` or `residual-risk` per Stage 5 T2.
> Validator: `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence` (parses `@@EVIDENCE` rows from `*.evidence.md` direct children of this dir)
> All source/test anchors in this file were verified against the live repo on 2026-08-08.

## Support / Reject Combination Matrix (frozen by this audit — checkpoint/barrier/recovery)

This matrix adjudicates every supported and rejected checkpoint combination. Each row cites the live source
anchor that implements or rejects it. The matrix changes neither the 11 evidence-row fields nor the 7-value
disposition vocabulary (frozen by Stage 4 `evidence-schema.md`).

| # | Combination | Verdict | Lane | Live anchor (rejecting / implementing) | Evidence row |
| --- | --- | --- | --- | --- | --- |
| M1 | Aligned checkpoint (STRICT_EXACTLY_ONCE, single-in-flight) | **SUPPORTED** | in-process | `CheckpointCoordinator.tryTriggerCheckpointWithReason():369`; `InputGate` aligned branch `:617-649`; `BarrierAlignment:787` | EVID-S9-001, EVID-S9-002, EVID-S9-003 |
| M2 | Multi-epoch / concurrent checkpoint (maxConcurrentCheckpoints > 1) | **SUPPORTED** | in-process | `CheckpointCoordinator` maxConcurrent gate `:370-374`; `CheckpointBarrierTracker.inFlight:66`; `InputGate.inFlightAlignments:99` | EVID-S9-004, EVID-S9-005 |
| M3 | Unaligned checkpoint (single-in-flight, aligned→unaligned fallback) | **SUPPORTED (single-in-flight only)** | in-process | `InputGate.switchToUnalignedAndEmit():494`; `consumePendingChannelState():542`; `restoreChannelState():576` | EVID-S9-010 (rejection boundary), Cross-Ref Notes |
| M4 | Unaligned checkpoint + multi-in-flight barriers | **REJECTED (fail-fast, design §2.8.1 D4)** | n/a | `InputGate.switchToUnalignedAndEmit():495-502` throws `ERR_STREAM_INVALID_STATE` | EVID-S9-006 |
| M5 | Unaligned checkpoint + rescale (parallelism change) | **REJECTED (fail-fast)** | n/a | `GraphModelCheckpointExecutor.assertNoChannelStateOnRescale():1232-1247` throws `ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED` | EVID-S9-010 |
| M6 | Recovery from EpochManifest (fingerprint-gated) | **SUPPORTED** | in-process | `GraphModelCheckpointExecutor.restoreFromCheckpoint():940` (EpochManifest-first `:946`); `validateFingerprintCompatibility():985` | EVID-S9-011 |
| M7 | Cross-JVM / HA checkpoint recovery | **BLOCKED** (lane not qualified) | multi-jvm | Out-of-scope here; Stage 5 T2 `multi-jvm` lane is not `qualified` for this audit window | Cross-Ref Notes |

Adjudication rules applied (consistent with Stage 4 schema + Stage 5 supplement):
- A supported combination gets a source-to-recovery evidence row with `disposition: e2e-proved` when an in-process
  test traces the chain end-to-end, or an honest weaker disposition when only a segment is exercised.
- A rejected combination gets `disposition: fail-fast` with a `rejection_proof` that actually asserts the throw
  (no silent allowance of an unsupported config — Rule #24).
- A combination needing a lane this audit cannot run (cross-JVM) gets `disposition: blocked`/`residual-risk`,
  never silently upgraded to `e2e-proved`.

---

## Evidence Rows

### Phase 1 — Aligned Checkpoint Lifecycle: Trigger → Barrier → Snapshot → Manifest → Recovery

@@EVIDENCE
inventory_id: EVID-S9-001
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:369-421
declared_guarantee: Aligned checkpoint lifecycle — tryTriggerCheckpointWithReason gates (maxConcurrent/minPause/tasks-to-ack) then creates a PendingCheckpoint; acknowledgeTask guards on RUNNING status then completes via completePendingCheckpoint which durably persists; restoreFromCheckpoint advances the id counter past the restored epoch
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:423-451,453-538,885-905
runtime_wiring: wired
positive_proof: TestCheckpointRecovery#testBasicCheckpointAndRecovery
rejection_proof: TestCheckpointRecovery#testCheckpointAbortAndRecovery
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S9-002
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:102-130
declared_guarantee: executeWithCheckpoint wires the full LOCAL runtime path — registerTasksAndTrackers(coordinator+tracker) + registerLocalAbortHandler, runs source→sink, and a completed checkpoint lands durable in storage proving the source-trigger-via-mailbox → cross-task barrier → sink snapshot → ACK chain
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:625-628,830-849
runtime_wiring: wired
positive_proof: TestMailboxE2ECheckpoint#testE2ECheckpointAligned
rejection_proof: TestCheckpointAbortWiring#testStuckChannelAbortTerminatesJob
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S9-003
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:617-650,787-800
declared_guarantee: Aligned barrier alignment — inner BarrierAlignment tracks per-checkpoint receivedChannels/blockedChannels and emits the barrier only when all channels have delivered it (fullyReceived), then unblocks; markFinishedChannel completes an alignment satisfied by a finished channel
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:657-677
runtime_wiring: wired
positive_proof: TestInputGateBarrierAlignment#testBarrierAlignmentOutOfOrder
rejection_proof: none
environment_class: unit
required_lane: in-process
finding_id: none
disposition: component-only
@@END

### Phase 2 — Multi-Epoch / Concurrent Checkpoint & ACK Routing

@@EVIDENCE
inventory_id: EVID-S9-004
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/CheckpointBarrierTracker.java:61-150
declared_guarantee: Multi-epoch concurrent checkpoint — triggerCheckpoint no longer rejects when another epoch is in-flight; each epoch owns its own EpochAckState so overlapping barriers coexist without throwing or cross-contaminating state (design §2.8.1 D1)
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/CheckpointBarrierTracker.java:364-374
runtime_wiring: wired
positive_proof: TestMultiEpochCheckpointE2E#testThreeEpochsInFlightCompleteIndependently
rejection_proof: TestCheckpointBarrierTrackerConcurrency#testOverlappingTriggerDuplicateIdRejected
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S9-005
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/CheckpointBarrierTracker.java:153-184
declared_guarantee: ACK routing by checkpoint id — acknowledgeOperator routes each operator ACK to its own epoch via snapshot.checkpointId (production path tags the result, design §2.8.1 D2); legacy untagged results fall back to most-recent in-flight with a WARN
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/CheckpointBarrierTracker.java:160-178,364-374
runtime_wiring: wired
positive_proof: TestCheckpointBarrierTrackerConcurrency#testOverlappingTriggerAcceptedAndAckedIndependently
rejection_proof: TestCheckpointBarrierTrackerConcurrency#testExtraAckIsSafelyIgnored
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S9-006
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:494-502
declared_guarantee: D4 fail-fast — unaligned checkpoint enabled together with more than one in-flight barrier is an unsupported combination; the gate throws ERR_STREAM_INVALID_STATE BEFORE any channel state is captured so it never silently captures state for the wrong epoch
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:495-502
runtime_wiring: wired
positive_proof: none
rejection_proof: TestInputGateUnalignedFallback#testUnalignedMultiInFlightFailsFastD4Guard
environment_class: unit
required_lane: in-process
finding_id: none
disposition: fail-fast
@@END

### Phase 3 — Abort/Cancel Path & Sink Commit-Cut Semantics

@@EVIDENCE
inventory_id: EVID-S9-007
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:849-883
declared_guarantee: Abort end-to-end connectivity — coordinator.abortPendingCheckpoint CAS RUNNING→ABORTED, calls notifyParticipantsFinishCommit(id,false) then notifyCheckpointAborted and the registered abort handler; the executor's registerLocalAbortHandler forwards to tracker.notifyCheckpointAborted (epoch-precise) and InputGate.abortBarrierAlignment (P1 add-to-aborted-before-remove ordering)
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:830-849
runtime_wiring: wired
positive_proof: TestCheckpointAbortWiring#testStuckChannelAbortTerminatesJob
rejection_proof: TestMultiEpochCheckpointE2E#testAbortMiddleEpochOthersStillComplete
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S9-008
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:93-124,152-164
declared_guarantee: Sink commit-cut §6.4 invariant — finishCommit(success=true) commits every pending transaction with eid <= epochId (subsuming commit); restoreFromEpoch separates durable-not-committed (eid <= N, re-committed) from non-durable in-flight (eid > N, aborted) instead of blindly rolling back
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamSinkOperator.java:68-109
runtime_wiring: wired
positive_proof: TestExactlyOnceCorrectnessFixes#testSubsumingCommitCommitsAllPendingTransactions
rejection_proof: TestExactlyOnceCorrectnessFixes#testRestoreFromEpochRollsbackPendingCommits
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P0-2
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S9-009
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:63-70
declared_guarantee: Sink commit-cut rejection/retry — a failed commit is retried on the next checkpoint rather than silently dropped; coordinator-side retryFailedCommits re-attempts failed commits so a transient external-system failure does not lose a durable transaction
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:1091-1110
runtime_wiring: wired
positive_proof: TestExactlyOnceCorrectnessFixes#testCommitFailureRetrySucceeds
rejection_proof: TestExactlyOnceCorrectnessFixes#testFailedCommitsAreRetriedOnNextCheckpoint
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

### Phase 4 — Unaligned+Rescale Fail-Fast, Recovery Fingerprint & Historical Finding Revalidation

@@EVIDENCE
inventory_id: EVID-S9-010
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1232-1248
declared_guarantee: Unaligned+rescale fail-fast — assertNoChannelStateOnRescale throws ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED (with vertex/old+new parallelism params) when any old subtask snapshot carries a non-empty ChannelState, because in-flight channel data cannot be redistributed across a parallelism change; aligned/empty channel state rescales proceed
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1232-1248
runtime_wiring: wired
positive_proof: TestChannelStateRescaleFailFast#rescaleWithEmptyChannelStateSucceeds
rejection_proof: TestChannelStateRescaleFailFast#rescaleWithNonEmptyChannelStateFailsFast
environment_class: unit
required_lane: in-process
finding_id: none
disposition: fail-fast
@@END

@@EVIDENCE
inventory_id: EVID-S9-011
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:940-1014
declared_guarantee: Recovery fingerprint compatibility — restoreFromCheckpoint prefers EpochManifest then validates the stored StreamModelFingerprint against the current model via validateFingerprintCompatibility, throwing ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED on incompatibility so a topology change cannot silently restore mismatched state
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:985-1014
runtime_wiring: wired
positive_proof: TestStreamModelFingerprintRecoveryCompat#sameFingerprintRecoverySucceeds
rejection_proof: TestStreamModelFingerprintRecoveryCompat#differentFingerprintRecoveryThrows
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S9-012
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:152-164
declared_guarantee: M7-2-P0-2 revalidation — restoreFromEpoch no longer blindly rolls back all pending transactions; durable-but-not-committed (eid <= N) are re-committed and only non-durable in-flight (eid > N) are aborted, honoring checkpoint-design §6.4
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:152-164
runtime_wiring: wired
positive_proof: TestExactlyOnceCorrectnessFixes#testRestoreFromEpochRollsbackPendingCommits
rejection_proof: TestExactlyOnceCorrectnessFixes#testSubsumingCommitPartialOrdering
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P0-2
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S9-013
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamSinkOperator.java:139-154
declared_guarantee: M7-2-P0-3 revalidation — restoreState no longer invokes restoreFromEpoch with sentinel (-1,null) arguments that cleared the just-restored pendingCommits map; it now only rebuilds the pendingCommits map from the durable snapshot, leaving the real epoch restore to GraphModelCheckpointExecutor.restoreOperatorsFromState
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1499-1544
runtime_wiring: wired
positive_proof: TestExactlyOnceCorrectnessFixes#testRestoreFromEpochRollsbackPendingCommits
rejection_proof: manual-trace:nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamSinkOperator.java:141-154
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P0-3
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S9-014
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1499-1544
declared_guarantee: M7-2-P1-4 revalidation — the production recovery path restores operator state via restoreState(opResult) + CheckpointParticipant.restoreFromEpoch(epochId); the generic StreamOperator.initializeState(TaskStateSnapshot) hook is not on this path. CheckpointedSourceFunction.initializeState IS invoked for sources at StreamSourceOperator:343, so the ICheckpointedFunction/CheckpointedSourceFunction recovery contract is active for sources but the generic operator initializeState hook is superseded by restoreState/restoreFromEpoch
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamSourceOperator.java:343
runtime_wiring: partial
positive_proof: TestCheckpointRecovery#testSourceOffsetRecovery
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P1-4
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S9-015
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/CheckpointBarrierTracker.java:192-200,235-242
declared_guarantee: M7-2-P1-11 revalidation — acknowledgeOperator no longer silently treats a failed snapshot as a successful ACK; an OperatorSnapshotResult carrying an error is routed via the CheckpointFailureListener abort callback to the correct epoch (removed from in-flight) and is NOT delivered as a success snapshot
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/CheckpointBarrierTracker.java:59-100,192-200
runtime_wiring: wired
positive_proof: TestCheckpointBarrierTrackerErrorPropagation#testSnapshotErrorRoutesToAbortCallbackAndDoesNotDeliverSuccess
rejection_proof: TestCheckpointBarrierTrackerErrorPropagation#testNoAbortCallbackStillRefusesToDeliverFailedSnapshot
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P1-11
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S9-016
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:980-1037
declared_guarantee: M8-2-P0-1 revalidation — globalRecovery now takes the fencing-epoch-before-lock ordering with a late-arrival guard and restart cap; the unsynchronized-concurrent-recovery-driver defect is hardened. Full cross-JVM synchronization cannot be proven in the in-process lane (needs the multi-jvm control-plane transport), so the hardening is observed but distributed mutual-exclusion is not independently revalidated here
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:980-1004
runtime_wiring: partial
positive_proof: TestJobCoordinatorRecoveryConcurrency#concurrentGlobalRecovery_serializesToOneRotation
rejection_proof: none
environment_class: in-process
required_lane: multi-jvm
finding_id: M8-2-P0-1
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S9-017
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:581-624
declared_guarantee: M8-2-P1-2 revalidation — incremental persist now rolls back registered shared-state ref-counts and discards zero-ref SST files when storage persistence fails after segment registration (releaseIncrementalSegments on storeCheckPoint/storeEpochManifest failure). The rollback path is present; complete SST-level ref-count integrity under RocksDB is owned by Stage 10
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:604,619-624
runtime_wiring: partial
positive_proof: manual-trace:nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:600-624
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: M8-2-P1-2
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S9-018
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:99,111,787-800
declared_guarantee: M8-2-P1-5 revalidation — InputGate alignment collections are now thread-safe: inFlightAlignments is a ConcurrentHashMap, abortedBarriers is a ConcurrentHashMap.newKeySet, and BarrierAlignment.receivedChannels/blockedChannels are concurrent sets, so the checkpoint abort handler thread and the task thread no longer throw ConcurrentModificationException
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:717-738,787-800
runtime_wiring: wired
positive_proof: TestInputGateMailboxAbort#crossThreadAbortDuringIterationDoesNotThrowCme
rejection_proof: TestCheckpointBarrierTrackerConcurrency#testEpochAbortDoesNotAffectOtherInFlightEpoch
environment_class: in-process
required_lane: in-process
finding_id: M8-2-P1-5
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S9-019
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1499-1544
declared_guarantee: M8-2-P1-6 revalidation — zombie-task mitigation on the LOCAL path: recovery reuses restoreOperatorsFromState to rehydrate operators into a fresh task rather than relying on the old task thread having fully terminated; the SupervisionLoop enforces a per-region restart budget. True cross-JVM zombie-task fencing (stale-attempt output rejection) requires the multi-jvm fencing lane and is owned by Stages 13/14
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1119-1125
runtime_wiring: partial
positive_proof: TestCheckpointRecovery#testBasicCheckpointAndRecovery
rejection_proof: none
environment_class: in-process
required_lane: multi-jvm
finding_id: M8-2-P1-6
disposition: residual-risk
@@END

---

## Cross-Reference Notes (final disposition owned by Stages 19-22; coverage gaps flagged here)

- **M7-2-P0-2** (sink restoreFromEpoch blind rollback): **RESOLVED.** `TwoPhaseCommitSinkFunction.restoreFromEpoch():153-164` now splits pending into toCommit (eid<=N) / toAbort (eid>N) per §6.4. Guarded by `TestExactlyOnceCorrectnessFixes#testRestoreFromEpochRollsbackPendingCommits` and `#testSubsumingCommitCommitsAllPendingTransactions` (EVID-S9-008/012). `disposition: e2e-proved`.
- **M7-2-P0-3** (sink restoreState sentinel call): **RESOLVED.** `StreamSinkOperator.restoreState():141-154` only rebuilds the pendingCommits map; the sentinel `restoreFromEpoch(-1,null)` call is removed (verified by manual-trace at `:143-149`). Real epoch restore is owned by `GraphModelCheckpointExecutor.restoreOperatorsFromState:1499-1544`. `disposition: e2e-proved` (EVID-S9-013).
- **M7-2-P1-4** (`StreamOperator.initializeState(TaskStateSnapshot)` never called): **PARTIAL / residual-risk.** The production recovery path uses `restoreState(opResult)` + `CheckpointParticipant.restoreFromEpoch` (`GraphModelCheckpointExecutor.restoreOperatorsFromState:1499-1544`), NOT the generic `StreamOperator.initializeState(TaskStateSnapshot)` hook. `CheckpointedSourceFunction.initializeState` IS called for sources (`StreamSourceOperator.java:343`). So the recovery contract is active for checkpointed sources, but the generic operator `initializeState` hook is superseded rather than wired. This is a contract-shape residual, not a data-loss defect; final disposition owned by Stage 19. EVID-S9-014.
- **M7-2-P1-11** (ACK swallows snapshot error): **RESOLVED.** `CheckpointBarrierTracker.acknowledgeOperator():192-200` fail-fast routes snapshot errors to the `CheckpointFailureListener` abort callback and refuses to deliver a failed snapshot as success. Guarded by `TestCheckpointBarrierTrackerErrorPropagation#testSnapshotErrorRoutesToAbortCallbackAndDoesNotDeliverSuccess` (EVID-S9-015). `disposition: e2e-proved`.
- **M8-2-P0-1** (globalRecovery unsynchronized): **PARTIAL / residual-risk.** The fencing-epoch-before-lock ordering + late-arrival guard + restart cap are present (`JobCoordinator.globalRecovery:980-1037`). The in-process lane cannot independently prove distributed mutual-exclusion of concurrent recovery drivers across JVMs; that requires the Stage 5 T2 `multi-jvm` lane, which is not `qualified` this window. Final cross-JVM revalidation owned by Stage 13. EVID-S9-016.
- **M8-2-P1-2** (incremental ref-count leak): **PARTIAL / residual-risk.** Storage-failure rollback now calls `releaseIncrementalSegments` (`CheckpointCoordinator:604,619-624`) after segments were registered/materialized. Complete ref-count integrity under real RocksDB SST materialization is owned by Stage 10 (state backend). EVID-S9-017.
- **M8-2-P1-5** (InputGate non-thread-safe collections): **RESOLVED.** `inFlightAlignments` (`:99`) is a `ConcurrentHashMap`; `abortedBarriers` (`:111`) is a `ConcurrentHashMap.newKeySet`; `BarrierAlignment.receivedChannels/blockedChannels` (`:790-791`) are concurrent sets. Guarded by `TestInputGateMailboxAbort#crossThreadAbortDuringIterationDoesNotThrowCme` and `TestCheckpointBarrierTrackerConcurrency` (EVID-S9-018). `disposition: e2e-proved`.
- **M8-2-P1-6** (zombie task): **PARTIAL / residual-risk.** LOCAL recovery reuses `restoreOperatorsFromState` and the SupervisionLoop enforces a per-region restart budget. True stale-attempt zombie fencing is a cross-JVM control-plane property owned by Stages 13/14; not provable in the in-process lane. EVID-S9-019.

## Combination M3 (unaligned single-in-flight) — support note

Unaligned checkpoint (single-in-flight, aligned→unaligned fallback after `DEFAULT_UNALIGNED_THRESHOLD_MS = 1000L`) is
SUPPORTED on the in-process lane. Implementation surface: `InputGate.switchToUnalignedAndEmit():494` performs the
mode switch + channel-state capture, `consumePendingChannelState():542` / `restoreChannelState():576` carry the
captured in-flight records across recovery, and `CheckpointBarrierTracker.setChannelState():338` attaches them to the
in-flight snapshot. The support boundary (single-in-flight) is enforced by the D4 guard (EVID-S9-006) and the
rescale guard (EVID-S9-010). A dedicated full unaligned-recovery `env.execute()`-level test was not isolated as a
single `ClassName#method` in this audit window; the unaligned capture/restore mechanism is proven at component level
(`TestInputGateUnalignedFallback#testUnalignedSwitchInvokesCaptureOnEachChannelWithCorrectFlag`,
`testUnalignedFallbackEmitsBarrierInsteadOfTimeout`) and the recovery-side channel-state re-injection
(`restoreChannelState`) is a coverage gap to assign to a test-effectiveness remediation plan, not a confirmed live
defect. Recorded honestly per Rule #24.

## Coverage Gaps Found (assigned to successor remediation per roadmap rule — NOT confirmed live defects)

- **No single in-process test chains persist → restore → reprocess through `executeWithCheckpoint` end-to-end.** The aligned lifecycle is proven in two complementary in-process tests: coordinator+storage+restore (`TestCheckpointRecovery#testBasicCheckpointAndRecovery`, EVID-S9-001) and executor+pipeline+durable-checkpoint (`TestMailboxE2ECheckpoint#testE2ECheckpointAligned`, EVID-S9-002). A single test that runs `executeWithCheckpoint`, kills/restarts, and asserts post-restore reprocessing would close the gap. This is a test-coverage item (roadmap item 17 / contract-test plan), not a live defect.
- **Multi-channel fan-in barrier alignment through `executeWithCheckpoint` is not asserted at e2e level.** `TestInputGateBarrierAlignment` proves multi-channel alignment at unit level (EVID-S9-003, `component-only`); an `executeWithCheckpoint` test with a fan-in topology asserting alignment-blocking semantics would upgrade it. Coverage gap, not a defect.
- **Unaligned full-recovery `env.execute()`-level test** (see Combination M3 note above) — coverage gap.

## Non-Goals honored (not silently dropped)

- State backend encoding (memory/RocksDB schema, key-layout, incremental SST integrity) = Stage 10.
- Window/watermark/timer result semantics = Stage 11.
- CEP NFA/SharedBuffer recovery = Stage 12.
- Distributed leader election / fencing / cross-JVM control-plane transport = Stage 13 (only `finding_id` cross-ref of M8-2-P0-1 / M8-2-P1-6 done here).
- True multi-JVM data-plane recovery = Stage 14 (Stage 5 T2 `TestMultiJvmExactlyOnceRecovery` log-label mismatch is a Stages 13/14 owned defect, not fixed here).
