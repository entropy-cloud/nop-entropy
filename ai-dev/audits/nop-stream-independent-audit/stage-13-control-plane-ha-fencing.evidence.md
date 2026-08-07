# Stage 13 — Control Plane, HA & Fencing Evidence

> Status: produced by Stage 13 audit (plan `nop-stream-independent-audit/2026-08-08-1835-1-control-plane-ha-fencing-audit.md`)
> Domain: manifest a/d/g (coordinator/cluster/rpc/taskmanager/execution/transport source surface + test lane)
> Lane policy: only `in-process` lane (single-JVM coordinator↔task over `IMessageService` RPC / leader-election wiring / fencing) or stronger is credited for control-plane, HA and fencing claims; `unit` is component-only. Any capability requiring cross-JVM control-plane / HA / fencing is `blocked` or `residual-risk` per Stage 5 T2 record (the T2 lane is `qualified` at infrastructure level, but its two deeper capability tests have defects owned here as `blocked`).
> Validator: `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence` (parses `@@EVIDENCE` rows from `*.evidence.md` direct children of this dir)
> All source/test anchors in this file were verified against the live repo on 2026-08-08 (line anchors cross-checked by an explore agent; test method names confirmed by direct file read).

## Support / Reject Combination Matrix (frozen by this audit — control-plane / HA / fencing)

This matrix adjudicates every supported and rejected control-plane / HA / fencing combination. Each row cites the live source
anchor that implements or rejects it. The matrix changes neither the 11 evidence-row fields nor the 7-value disposition
vocabulary (frozen by Stage 4 `evidence-schema.md`).

### Control-Plane Capability Matrix (entry-to-effect)

| # | Capability | Verdict | Lane | Live anchor (implementing) | Evidence row |
| --- | --- | --- | --- | --- | --- |
| C1 | Task assignment dispatch (coordinator → task RPC) | **SUPPORTED** | in-process | `JobCoordinator.assignTasks():481` → `prepareAssignmentsLocked():534` → `executeAssignmentFanOut():639`; `TaskManager.receiveAssignment():262` | EVID-S13-001 |
| C2 | Checkpoint trigger via RPC (coordinator → task) | **SUPPORTED** | in-process | `JobCoordinator.triggerCheckpoint():684`; `TaskManager.triggerCheckpoint():514` (fencing throw `:520-524`) | EVID-S13-002 |
| C3 | Failover / recovery command (global recovery → epoch push → old-epoch cancel) | **SUPPORTED** | in-process | `JobCoordinator.globalRecovery():980`; `TaskManager.updateFencingToken():583` (cancel `:587-596`) | EVID-S13-003 |
| C4 | Termination command (G23 four-mode) | **SUPPORTED** | in-process | `JobCoordinator.terminate():1272` (four-mode switch `:1275-1291`) | EVID-S13-004 |
| C5 | Distributed abort (coordinator abort → cancelTask RPC fan-out) | **SUPPORTED** | in-process | `JobCoordinator.abortCheckpoint():1391` + `registerDistributedAbortHandler():1432` | EVID-S13-005 |
| C6 | Cross-JVM control-plane transport (real RPC, real fencing, real failover) | **PARTIALLY SUPPORTED — deeper capability BLOCKED** | multi-jvm | T2 infrastructure `qualified` (process spawn + registration); two deeper tests have defects | EVID-S13-015, EVID-S13-016 |

### Fencing Support / Reject Matrix

| # | Combination | Verdict | Lane | Live anchor (rejecting / implementing) | Evidence row |
| --- | --- | --- | --- | --- | --- |
| F1 | Stale-leader rejection (coordinator side — old leader / standby commands rejected) | **SUPPORTED** | in-process | `JobCoordinator.collectAck():754-764`; `reportTaskStatus():811-816`; `reportNodeTaskLiveness():872-877` | EVID-S13-006 |
| F2 | Stale-token rejection (task side — P0-6 hardening, throws `ERR_STREAM_FENCING_TOKEN_MISMATCH`) | **SUPPORTED** | in-process | `TaskManager.receiveAssignment():275-279`; `triggerCheckpoint():520-524`; `deployTask():388-394` | EVID-S13-007 |
| F3 | Fencing encoding dominance (single monotonic long; new leader dominates all prior leader's recoveries; same-leader recovery advances epoch, prior recovery rejected) | **SUPPORTED** | in-process | `JobCoordinator.EPOCH_SCALE:110`; `deriveHaFencingEpoch():1155` | EVID-S13-008 |
| F4 | Data-plane stale-epoch envelope discard (single long comparison — Stage 39 unification) | **SUPPORTED** | in-process | `RemoteInputChannel.EnvelopeConsumer.onMessage():368-372`; `RemoteResultPartition` stamping `:163-164,184-185,252-253` | EVID-S13-009 |
| F5 | Same-leader prior-recovery rejection (recoveryGen monotonic within a leader) | **SUPPORTED** | in-process | Covered by F3 invariant — `deriveHaFencingEpoch(leaderEpochValue, recoveryGen)`; proven by `TestFencingEpochUnification#sameLeaderRecoveryAdvancesEpochPriorRecoveryRejected` | EVID-S13-008 |
| F6 | Cross-JVM fencing (real distributed leader election + stale-attempt zombie fencing) | **PARTIALLY SUPPORTED — deeper capability has known defect** | multi-jvm | T2 infrastructure `qualified`; HA-fencing takeover deeper test fails (`TestMultiJvmCoordinatorFailover:129`); true cross-JVM zombie fencing owned by Stages 13/14 | EVID-S13-013, EVID-S13-016, EVID-S13-019 |

Adjudication rules applied (consistent with Stage 4 schema + Stage 5 supplement):
- A supported capability gets an entry-to-effect evidence row with `disposition: e2e-proved` when an in-process test traces the
  chain end-to-end (coordinator entry → RPC → task effect), or an honest weaker disposition when only a segment is exercised.
- A rejected/stale combination gets `disposition: fail-fast` / `e2e-proved` (rejection case) with a `rejection_proof` that
  actually asserts the throw or the stale-command rejection (no silent allowance — Rule #24).
- A capability needing the cross-JVM lane this audit cannot fully exercise gets `disposition: blocked`/`residual-risk`,
  never silently upgraded to `e2e-proved`. The T2 lane is `qualified` at infrastructure level (process spawn + registration),
  so cross-JVM rows cite T2 honestly rather than being silently dropped.

---

## Evidence Rows

### Phase 1 — Control-Plane RPC Entry-to-Effect (assignment / trigger / failover / termination / abort)

@@EVIDENCE
inventory_id: EVID-S13-001
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:481,534,639
declared_guarantee: Task assignment dispatch — assignTasks guards (active/epoch/nodes) then prepareAssignmentsLocked builds per-node assignments under recoveryLock; executeAssignmentFanOut issues receiveAssignment RPCs OUTSIDE the lock, so a coordinator assignment reaches TaskManager.receiveAssignment end-to-end over the control RPC
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:262-279
runtime_wiring: wired
positive_proof: TestRpcDistributedExecutorE2E#fullPipelineRunsOverRpcControlPlane
rejection_proof: TestFencingTokenRejection#staleTokenAssignmentThrowsFencingMismatch
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S13-002
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:684
declared_guarantee: Checkpoint trigger via RPC — coordinator.triggerCheckpoint fans a triggerCheckpoint RPC downlink to source tasks; the task side (TaskManager.triggerCheckpoint:514) throws ERR_STREAM_FENCING_TOKEN_MISMATCH when activeEpoch != trigger fencing epoch, so a stale-leader checkpoint trigger is rejected rather than silently honored
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:514-524
runtime_wiring: wired
positive_proof: TestJobCoordinator#testTriggerCheckpointSendsBarrier
rejection_proof: TestFencingTokenRejection#staleTokenCheckpointTriggerThrowsFencingMismatch
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S13-003
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:980
declared_guarantee: Failover/recovery command — globalRecovery rotates the fencing epoch and pushes the new epoch to all task RPCs; TaskManager.updateFencingToken rotates its active epoch and cancels old-epoch tasks, so a recovery command propagates coordinator→task and fences stale attempts
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:583-596
runtime_wiring: wired
positive_proof: TestJobCoordinator#testGlobalRecoveryGeneratesNewToken
rejection_proof: TestJobCoordinatorRecoveryConcurrency#concurrentGlobalRecovery_serializesToOneRotation
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S13-004
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:1272-1291
declared_guarantee: Termination command — JobCoordinator.terminate implements the G23 four-mode (CANCEL/DRAIN/SUSPEND/EXPORT_SAVEPOINT) dispatch; each mode drives the corresponding terminal checkpoint / savepoint / cancellation path rather than silently no-op'ing an unsupported mode
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:1272-1291
runtime_wiring: wired
positive_proof: TestJobCoordinator#testTerminateCancel
rejection_proof: TestJobCoordinator#testTerminateDrainTriggersTerminalCheckpoint
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S13-005
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:1391,1432
declared_guarantee: Distributed abort — abortCheckpoint transitions the pending checkpoint to ABORTED and the registered distributed abort handler (registerDistributedAbortHandler, wired by RpcDistributedExecutor.startJob:250) fans cancelTask RPCs out to participants, so an abort command reaches tasks over the control RPC
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/RpcDistributedExecutor.java:250
runtime_wiring: wired
positive_proof: TestCheckpointAbortWiring#testStuckChannelAbortTerminatesJob
rejection_proof: TestMultiEpochCheckpointE2E#testAbortMiddleEpochOthersStillComplete
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

### Phase 2 — Fencing Epoch Stale-Rejection & Leader Election Transition

@@EVIDENCE
inventory_id: EVID-S13-006
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:754-764,811-816,872-877
declared_guarantee: Fencing stale-leader rejection (coordinator side) — collectAck rejects when !active / epoch==0 / epoch != ack.fencingEpoch; reportTaskStatus and reportNodeTaskLiveness apply the same epoch+active guards, so a stale-leader (or standby) control command is rejected with a WARN rather than mutating coordinator state
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:744,760-764,811-816,872-877
runtime_wiring: wired
positive_proof: TestJobCoordinatorStandbyStateMachine#testStaleTokenControlRejectedByCollectAck
rejection_proof: TestJobCoordinatorLeaderElection#testStandbyRejectsCollectAckExplicitly
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S13-007
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:275-279,520-524,388-394
declared_guarantee: Fencing stale-token rejection (task side, P0-6 hardening) — receiveAssignment/triggerCheckpoint/deployTask throw ERR_STREAM_FENCING_TOKEN_MISMATCH when activeEpoch != command fencing epoch (was silent LOG.warn+return; now fail-fast per Rule #24)
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:275-279,520-524,388-394
runtime_wiring: wired
positive_proof: TestFencingTokenRejection#staleTokenAssignmentThrowsFencingMismatch
rejection_proof: TestFencingTokenRejection#staleTokenCheckpointTriggerThrowsFencingMismatch
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P0-6
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S13-008
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:110,1155
declared_guarantee: Fencing encoding dominance — a single monotonic long epoch = leaderEpochValue*EPOCH_SCALE + recoveryGen; a new leader's epoch dominates ALL prior leader's recovery epochs, and same-leader recovery increments recoveryGen so the prior recovery epoch is rejected (collapses the prior dual-key scheme)
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:1155
runtime_wiring: wired
positive_proof: TestFencingEpochUnification#encodingNewLeaderDominatesPriorLeaderRecoveries
rejection_proof: TestFencingEpochUnification#sameLeaderRecoveryAdvancesEpochPriorRecoveryRejected
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S13-009
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteInputChannel.java:368-372,74
declared_guarantee: Data-plane fencing (single long comparison) — EnvelopeConsumer.onMessage discards any envelope whose epochId != expectedEpochId BEFORE refreshing liveness, and RemoteResultPartition stamps epochId into every envelope (record/control/heartbeat), so a stale-epoch data-plane envelope is dropped not processed
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteResultPartition.java:163-164,184-185,252-253
runtime_wiring: wired
positive_proof: TestFencingEpochUnification#dataPlaneStaleEpochEnvelopeDiscardedCurrentAccepted
rejection_proof: TestFencingEpochUnification#dataPlaneStaleEpochEnvelopeDiscardedCurrentAccepted
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S13-010
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:1164,1192,1242
declared_guarantee: Leader election transition — CoordinatorElectionListener.becomeLeader → activateAsLeader (idempotent, reset recoveryGen, active=true, derive token, rotateFencingEpochCoreLocked G32 rebuild) and becomeFollower → deactivateToStandby (active=false, detector stays alive), giving the standby→active→standby→re-active state machine
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:1192-1242
runtime_wiring: wired
positive_proof: TestJobCoordinatorStandbyStateMachine#testLeaderSwitchEndToEndTwoCoordinators
rejection_proof: TestJobCoordinatorLeaderElection#testHaStartEntersStandbyNotActive
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

### Phase 3 — G32 Failover-Safe Rebuild, Concurrent Recovery Serialization & Zombie Task Fencing

@@EVIDENCE
inventory_id: EVID-S13-011
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:1081-1138
declared_guarantee: G32 failover-safe rebuild — rotateFencingEpochCoreLocked(restoreFromStorage=true) rebuilds from storage when in-memory==null, fails loud (ERR_STREAM_INVALID_STATE, "new leader cannot safely resume") on storage failure, and same-leader recovery skips storage rebuild; each path is distinct and observable
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:1098-1138
runtime_wiring: wired
positive_proof: TestJobCoordinatorFailoverRestore#testFreshCoordinatorRestoresLatestDurableEpochFromJdbcStorage
rejection_proof: TestJobCoordinatorFailoverRestore#testStorageFailureDuringActivateAsLeaderFailsLoud
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S13-012
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:980-1004
declared_guarantee: M8-2-P0-1 concurrent recovery serialization — globalRecovery snapshots fencingEpoch BEFORE acquiring recoveryLock and a late-arrival guard short-circuits (observable WARN, no double rotation) when the epoch advanced since entry; in-process two concurrent recovery drivers serialize to one epoch rotation. Distributed cross-JVM mutual-exclusion is NOT independently provable in the in-process lane
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:988,991,999-1004
runtime_wiring: partial
positive_proof: TestJobCoordinatorRecoveryConcurrency#concurrentGlobalRecovery_serializesToOneRotation
rejection_proof: TestJobCoordinatorRecoveryConcurrency#concurrentRecovery_leavesConsistentWorkingSet
environment_class: in-process
required_lane: multi-jvm
finding_id: M8-2-P0-1
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S13-013
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/SupervisionLoop.java:492-503,155
declared_guarantee: M8-2-P1-6 zombie task fencing (LOCAL hardening) — waitForTerminal throws ERR_STREAM_SUPERVISION_ZOMBIE_TASK_TIMEOUT if a task does not terminate within DEFAULT_TERMINAL_WAIT_BUDGET_MS (was silent WARN+return → two producers writing the same ResultPartition; now fail-loud per Rule #24). True cross-JVM stale-attempt zombie fencing requires the multi-jvm lane
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/SupervisionLoop.java:492-503
runtime_wiring: wired
positive_proof: TestSupervisionLoopZombieTaskTimeout#waitForTerminal_timesOut_failsLoudWithZombieTimeoutError
rejection_proof: TestSupervisionLoopZombieTaskTimeout#waitForTerminal_timesOut_failsLoudWithZombieTimeoutError
environment_class: in-process
required_lane: multi-jvm
finding_id: M8-2-P1-6
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S13-014
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:988,991,999-1004
declared_guarantee: Fencing-epoch-before-lock ordering invariant — globalRecovery snapshots epochAtEntry BEFORE locking recoveryLock (988 snapshot → 991 lock), then the late-arrival guard (999-1004) compares epochAtEntry to the current epoch under lock and short-circuits with an observable WARN when a concurrent driver already rotated, yielding exactly one rotation per concurrent burst
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:988,991,999-1004
runtime_wiring: wired
positive_proof: TestJobCoordinatorRecoveryConcurrency#concurrentGlobalRecovery_serializesToOneRotation
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: M8-2-P0-1
disposition: e2e-proved
@@END

### Phase 4 — T2 Lane Defects, Historical Finding Revalidation & Cross-JVM Boundary

@@EVIDENCE
inventory_id: EVID-S13-015
source_anchor: nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/multijvm/TestMultiJvmExactlyOnceRecovery.java:111
declared_guarantee: T2 lane deeper defect — TestMultiJvmExactlyOnceRecovery reads logFileFor("coordinator") but MiniStreamCluster.spawnJobCoordinator writes label "coordinator-0" (MiniStreamCluster.java:404), so Files.size throws NoSuchFileException on the bare "coordinator" path; the deeper cross-JVM exactly-once recovery assertion cannot be evidenced until the log-label mismatch is fixed. Owned by Stages 13/14
implementation_anchor: nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/multijvm/MiniStreamCluster.java:404
runtime_wiring: unwired
positive_proof: none
rejection_proof: none
environment_class: none
required_lane: multi-jvm
finding_id: none
disposition: blocked
@@END

@@EVIDENCE
inventory_id: EVID-S13-016
source_anchor: nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/multijvm/TestMultiJvmCoordinatorFailover.java:129
declared_guarantee: T2 lane deeper defect — testBrainSplitFencingBoundary fails the assertion "coordinator-1 must take over" (epoch1 > 0) at line 129; the cross-JVM HA-fencing takeover capability cannot be evidenced until the failover-takeover gap is fixed. Owned by Stages 13/14. T2 lane infrastructure itself is qualified (process spawn + registration)
implementation_anchor: nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/multijvm/TestMultiJvmCoordinatorFailover.java:106-132
runtime_wiring: unwired
positive_proof: none
rejection_proof: none
environment_class: none
required_lane: multi-jvm
finding_id: none
disposition: blocked
@@END

@@EVIDENCE
inventory_id: EVID-S13-017
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:980-1037
declared_guarantee: M8-2-P0-1 revalidation (globalRecovery) — the unsynchronized-concurrent-recovery-driver defect is hardened (fencing-epoch-before-lock + late-arrival guard + restart cap), proven to serialize in-process; distributed cross-JVM mutual-exclusion remains a residual-risk requiring the multi-jvm lane, owned by Stage 14
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
inventory_id: EVID-S13-018
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:426-435
declared_guarantee: M8-2-P1-4 revalidation (permit leak) — deployTask fences out the existing slot occupant and reclaims its permit before installing the new task (net permit change 0), so a redeploy of an occupied slot no longer leaks one capacity permit per redeploy
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:426-435
runtime_wiring: wired
positive_proof: TestTaskManager#testRedeployToOccupiedSlotDoesNotLeakPermit
rejection_proof: TestTaskManager#testDuplicateAssignmentDoesNotLeakSemaphore
environment_class: in-process
required_lane: in-process
finding_id: M8-2-P1-4
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S13-019
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/SupervisionLoop.java:492-503
declared_guarantee: M8-2-P1-6 revalidation (zombie task) — LOCAL zombie mitigation is hardened (waitForTerminal fail-loud timeout + per-region restart budget); true cross-JVM stale-attempt zombie fencing (rejecting output from a zombie producer in another JVM) requires the multi-jvm lane and is owned by Stages 13/14
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1499-1544
runtime_wiring: partial
positive_proof: TestSupervisionLoopZombieTaskTimeout#waitForTerminal_timesOut_failsLoudWithZombieTimeoutError
rejection_proof: none
environment_class: in-process
required_lane: multi-jvm
finding_id: M8-2-P1-6
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S13-020
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:275-279,520-524
declared_guarantee: M7-2-P0-6 revalidation (fencing-token rejection ZERO tests) — the historical finding "fencing-token rejection of stale attempt output has ZERO tests" is resolved: receiveAssignment/triggerCheckpoint/deployTask now throw ERR_STREAM_FENCING_TOKEN_MISMATCH and are covered by TestFencingTokenRejection + TestFencingEpochUnification
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:275-279,520-524,388-394
runtime_wiring: wired
positive_proof: TestFencingTokenRejection#staleTokenAssignmentThrowsFencingMismatch
rejection_proof: TestFencingTokenRejection#staleTokenCheckpointTriggerThrowsFencingMismatch
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P0-6
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S13-021
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:220-224
declared_guarantee: Local-vs-distributed recovery boundary — LOCAL recovery via SupervisionLoop.restartRegion (per-region in-process budget) is exercised in-process; DISTRIBUTED recovery via JobCoordinator.globalRecovery (cross-JVM reassignment) needs the multi-jvm lane; the JobCoordinator Javadoc (220-224) explicitly notes scoped per-region restart needs its own counter (deferred follow-up)
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/SupervisionLoop.java:382
runtime_wiring: partial
positive_proof: TestSupervisionLoopZombieTaskTimeout#waitForTerminal_timesOut_failsLoudWithZombieTimeoutError
rejection_proof: none
environment_class: in-process
required_lane: multi-jvm
finding_id: none
disposition: residual-risk
@@END

---

## Cross-Reference Notes (final disposition of historical P0/P1 findings touched by this audit)

- **M8-2-P0-1** (globalRecovery unsynchronized): **PARTIAL / residual-risk.** Fencing-epoch-before-lock ordering + late-arrival guard + restart cap are present and proven to serialize two concurrent recovery drivers to one rotation in-process (`JobCoordinator.globalRecovery:980-1004`; EVID-S13-012 / EVID-S13-014 / EVID-S13-017). The in-process lane cannot independently prove distributed mutual-exclusion across JVMs; that requires the Stage 5 T2 `multi-jvm` lane, whose deeper exactly-once-recovery test has a log-label defect (EVID-S13-015). Final cross-JVM revalidation owned by Stage 14.
- **M8-2-P1-4** (TaskManager.deployTask permit leak): **RESOLVED.** `deployTask:426-435` fences out the existing slot occupant and reclaims its permit (net permit change 0). Guarded by `TestTaskManager#testRedeployToOccupiedSlotDoesNotLeakPermit` and `#testDuplicateAssignmentDoesNotLeakSemaphore` (EVID-S13-018). `disposition: e2e-proved`.
- **M8-2-P1-6** (zombie task): **PARTIAL / residual-risk.** LOCAL zombie mitigation hardened (`SupervisionLoop.waitForTerminal:492-503` fail-loud timeout; EVID-S13-013 / EVID-S13-019). True stale-attempt zombie fencing across JVMs (rejecting output from a zombie producer in another JVM) is a cross-JVM control-plane property owned by Stages 13/14; not provable in the in-process lane. The T2 HA-fencing takeover test has a known defect (EVID-S13-016).
- **M7-2-P0-6** (fencing-token rejection ZERO tests): **RESOLVED.** `TaskManager.receiveAssignment/triggerCheckpoint/deployTask` now throw `ERR_STREAM_FENCING_TOKEN_MISMATCH` (was silent LOG.warn+return). Guarded by `TestFencingTokenRejection` + `TestFencingEpochUnification` (EVID-S13-007 / EVID-S13-020). `disposition: e2e-proved`.
- **M8-2-P2-10** (JobCoordinator.assignTasks leaves registry and in-memory maps inconsistent when RPC dispatch throws mid-iteration): **residual-risk (deferred P2).** The normal-path assignment-dispatch capability is `e2e-proved` (EVID-S13-001, `TestRpcDistributedExecutorE2E#fullPipelineRunsOverRpcControlPlane`), but the mid-iteration-throw failure-consistency edge remains a deferred P2 owned by the active remediation plan `2026-08-04-2300-1-coordinator-runtime-concurrency-recovery-hardening.md`. Not a Stage-13-confirmed new live defect; disposition tracked by that owner plan.
- **M8-2-P2-15** (JobCoordinator.failJob/stop do not cancel in-flight tasks on TaskManagers — zombie emissions after FAILED): **residual-risk (deferred P2).** The termination-command capability (G23 four-mode) is `e2e-proved` (EVID-S13-004), but the failJob-path zombie-emission edge remains a deferred P2 owned by the same coordinator-runtime-concurrency remediation plan. Adjacent to M8-2-P1-6 (zombie) cross-JVM residual.

## T2 Lane Defect Disposition (cross-ref Stage 5 T2 record `@@LANE` note)

- **TestMultiJvmExactlyOnceRecovery log-label mismatch** (`:111` reads `logFileFor("coordinator")`; `MiniStreamCluster:404` writes `"coordinator-0"`): `disposition: blocked`, `required_lane: multi-jvm` (EVID-S13-015). The T2 lane is `qualified` at infrastructure level (`TestMiniStreamClusterProcessSpawn` 3/3 PASS), so this is an honest capability-level `blocked`, NOT a silent skip — the deeper exactly-once-recovery evidence cannot be produced until the log-label defect is fixed. Owned by Stages 13/14; successor remediation plan or Stage 14.
- **TestMultiJvmCoordinatorFailover HA-fencing takeover failure** (`:129` "coordinator-1 must take over" assertion fails): `disposition: blocked`, `required_lane: multi-jvm` (EVID-S13-016). Cross-JVM HA-fencing takeover capability gap; T2 infrastructure is qualified so this is an honest `blocked`. Owned by Stages 13/14.

## Non-Goals honored (not silently dropped)

- Real multi-JVM data-plane recovery (record/barrier/watermark transport across JVMs) = Stage 14. This audit references the Stage 5 T2 `qualified` lane and its two deeper defects (EVID-S13-015/016) but does not run or fix them.
- Checkpoint barrier alignment / state backend / window / CEP semantics = Stages 9/10/11/12. This audit only cross-references M8-2-P0-1 / M8-2-P1-6 / M7-2-P0-6 live-revalidation results (consistent with Stage 9 EVID-S9-016 / EVID-S9-019).
- Connector source/sink guarantees = Stages 15/16.
- Fixing confirmed live defects discovered by this audit = assigned to a successor remediation plan per roadmap rule (none new-discovered here beyond the two known T2 defects, which were already recorded in the Stage 5 T2 lane note and are dispositioned `blocked`).

## Coverage Gaps (assigned to successor remediation — NOT confirmed new live defects)

- **No single in-process test exercises a real leadership transition (standby→active) with a concurrent in-flight control command that must be rejected by the new epoch end-to-end across the RPC boundary in one assertion.** The transition (EVID-S13-010) and the stale-rejection (EVID-S13-006/007) are each proven in-process, but a combined transition+stale-command-over-RPC assertion would be stronger. Test-effectiveness coverage gap (roadmap item 17), not a live defect.
- **Cross-JVM HA-fencing takeover + cross-JVM exactly-once recovery** are `blocked` on the T2 lane deeper-test defects (EVID-S13-015/016), owned by Stages 13/14 successor work — honestly `blocked`, not silently upgraded.
