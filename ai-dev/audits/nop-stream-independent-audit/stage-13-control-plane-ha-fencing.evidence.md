# Stage 13 — Control Plane, HA & Fencing Evidence

> Status: produced by Stage 13 audit (plan `nop-stream-independent-audit/2026-08-08-1835-1-control-plane-ha-fencing-audit.md`)
> Domain: manifest a/d/g (coordinator/cluster/rpc/taskmanager/execution/transport source surface + test lane)
> Lane policy: only `in-process` lane (single-JVM coordinator↔task over `IMessageService` RPC / leader-election wiring / fencing) or stronger is credited for control-plane, HA and fencing claims; `unit` is component-only. Cross-JVM control-plane / HA / fencing capabilities were re-audited e2e-proved on the T2 `multi-jvm` lane on 2026-08-09-1300-1 (the T2 lane is `qualified` and its two deeper capability tests now PASS fresh after plan `2026-08-09-1252-1` fixed the root causes); residual-risk rows that assert STRONGER/NARROWER invariants (concurrent distributed mutex, cross-JVM zombie) stay `residual-risk` with updated rationale.
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
| C6 | Cross-JVM control-plane transport (real RPC, real fencing, real failover) | **SUPPORTED** (re-audited 2026-08-09-1300-1) | multi-jvm | T2 infrastructure `qualified` (process spawn + registration); both deeper tests now PASS fresh (EVID-S13-015 recovery redeploy + EVID-S13-016 HA-fencing takeover) | EVID-S13-015, EVID-S13-016 |

### Fencing Support / Reject Matrix

| # | Combination | Verdict | Lane | Live anchor (rejecting / implementing) | Evidence row |
| --- | --- | --- | --- | --- | --- |
| F1 | Stale-leader rejection (coordinator side — old leader / standby commands rejected) | **SUPPORTED** | in-process | `JobCoordinator.collectAck():754-764`; `reportTaskStatus():811-816`; `reportNodeTaskLiveness():872-877` | EVID-S13-006 |
| F2 | Stale-token rejection (task side — P0-6 hardening, throws `ERR_STREAM_FENCING_TOKEN_MISMATCH`) | **SUPPORTED** | in-process | `TaskManager.receiveAssignment():275-279`; `triggerCheckpoint():520-524`; `deployTask():388-394` | EVID-S13-007 |
| F3 | Fencing encoding dominance (single monotonic long; new leader dominates all prior leader's recoveries; same-leader recovery advances epoch, prior recovery rejected) | **SUPPORTED** | in-process | `JobCoordinator.EPOCH_SCALE:110`; `deriveHaFencingEpoch():1155` | EVID-S13-008 |
| F4 | Data-plane stale-epoch envelope discard (single long comparison — Stage 39 unification) | **SUPPORTED** | in-process | `RemoteInputChannel.EnvelopeConsumer.onMessage():368-372`; `RemoteResultPartition` stamping `:163-164,184-185,252-253` | EVID-S13-009 |
| F5 | Same-leader prior-recovery rejection (recoveryGen monotonic within a leader) | **SUPPORTED** | in-process | Covered by F3 invariant — `deriveHaFencingEpoch(leaderEpochValue, recoveryGen)`; proven by `TestFencingEpochUnification#sameLeaderRecoveryAdvancesEpochPriorRecoveryRejected` | EVID-S13-008 |
| F6 | Cross-JVM fencing (real distributed leader election + stale-attempt zombie fencing) | **PARTIALLY SUPPORTED — HA-fencing takeover e2e-proved; zombie fencing residual** (re-audited 2026-08-09-1300-1) | multi-jvm | T2 infrastructure `qualified`; HA-fencing takeover now PASS (EVID-S13-016); true cross-JVM zombie fencing stays residual-risk (EVID-S13-013/019: the T2 recovery test kills the TM rather than leaving it running as a zombie) | EVID-S13-013, EVID-S13-016, EVID-S13-019 |

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
declared_guarantee: M8-2-P0-1 concurrent recovery serialization (re-audited 2026-08-09-1300-1) — globalRecovery snapshots fencingEpoch BEFORE acquiring recoveryLock and a late-arrival guard short-circuits (observable WARN, no double rotation) when the epoch advanced since entry; in-process two concurrent recovery drivers serialize to one epoch rotation. RATIONALE UPDATE: T2 capability gaps (§2b) resolved 2026-08-09-1300-1 — the passing T2 tests now prove single-coordinator cross-JVM recovery (epoch rotation + redeploy, EVID-S13-015) and cross-JVM HA-fencing takeover (EVID-S13-016). HOWEVER this row asserts the STRONGER invariant of a single global fencing-epoch rotation under CONCURRENT distributed recovery drivers (true distributed mutual-exclusion, M8-2-P0-1), which is not directly proven by the single-coordinator T2 recovery test (it exercises one coordinator's recovery path, not two coordinators racing globalRecovery simultaneously). Stays residual-risk; the concurrent-distributed-mutex invariant remains unproven at the multi-jvm lane
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
declared_guarantee: M8-2-P1-6 zombie task fencing (LOCAL hardening, re-audited 2026-08-09-1300-1) — waitForTerminal throws ERR_STREAM_SUPERVISION_ZOMBIE_TASK_TIMEOUT if a task does not terminate within DEFAULT_TERMINAL_WAIT_BUDGET_MS (was silent WARN+return → two producers writing the same ResultPartition; now fail-loud per Rule #24). RATIONALE UPDATE: T2 capability gaps (§2b) resolved 2026-08-09-1300-1 — the passing T2 tests now prove cross-JVM recovery redeploy (EVID-S13-015) and HA-fencing takeover (EVID-S13-016). HOWEVER this row asserts the NARROWER invariant of true cross-JVM stale-attempt zombie fencing — rejecting OUTPUT from a zombie producer in another JVM that keeps emitting after its epoch was superseded. The T2 recovery test kills the TM (it stops emitting because the process is dead) rather than leaving it running as a zombie, so full distributed-zombie prevention (stopping the zombie process) is not directly proven. Stays residual-risk; the receiver-side epoch discard (Stage 14 EVID-S14-004) drops stale-epoch envelopes regardless of which JVM produced them
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
source_anchor: nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/multijvm/TestMultiJvmExactlyOnceRecovery.java:90
declared_guarantee: Cross-JVM recovery fencing/redeploy enabling infrastructure (re-audited e2e-proved 2026-08-09-1300-1) — the T2 multi-JVM test now PASSES fresh: TestMultiJvmExactlyOnceRecovery#multiJvmDeployKillRecoverFencing spawns 2 real TaskManager JVMs + 1 real coordinator JVM over a shared H2 AUTO_SERVER=TRUE DB, asserts initial fencing epoch > 0, kills one TM (real OS SIGTERM), restarts a replacement, then asserts the coordinator's recovery path strictly rotated the fencing epoch (recovered > initial) and re-issued deployTask to all subtasks at the rotated epoch (assignment count >= 2), and the coordinator log captured the recovery event. HONESTY GATE — this proves the ENABLING INFRASTRUCTURE for cross-JVM exactly-once (cross-JVM deployTask RPC + recovery redeploy + fencing epoch rotation), NOT a full source->keyBy->sink cross-JVM shared-sink exactly-once assertion (that is the Stage 43+ follow-up per the test Javadoc). The historical log-label defect (logFileFor("coordinator") vs MiniStreamCluster:404 "coordinator-"+index) was fixed by plan 2026-08-09-1252-1 centralising COORDINATOR_LABEL = "coordinator-0"; the MICROSECONDS->MILLISECONDS root cause in AbstractPollingLeaderElector.scheduleCheck was also fixed by that plan, which together make the cross-JVM recovery path actually exercisable
implementation_anchor: nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/multijvm/MiniStreamCluster.java:389,404
runtime_wiring: wired
positive_proof: TestMultiJvmExactlyOnceRecovery#multiJvmDeployKillRecoverFencing (fresh T2-lane re-run 2026-08-09: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, elapsed 67.38s; asserts initialEpoch>0, recoveredEpoch>initialEpoch, recoveredAssignmentCount>=2, coordinator log contains recovery event)
rejection_proof: none
environment_class: multi-jvm
required_lane: multi-jvm
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S13-016
source_anchor: nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/multijvm/TestMultiJvmCoordinatorFailover.java:54,108
declared_guarantee: Cross-JVM HA-fencing takeover capability (re-audited e2e-proved 2026-08-09-1300-1) — the T2 multi-JVM test now PASSES fresh: TestMultiJvmCoordinatorFailover spawns 2 real coordinator JVMs sharing one JDBC lease table, asserts coordinator-0 wins leadership (initialEpoch > 0), kills coordinator-0 (real OS SIGTERM), then asserts the standby coordinator-1 takes over with the lease leader_id flipped to coordinator-1 and a strictly greater leader_epoch (fencing invariant #8). The historical takeover gap (assertTrue epoch1 > 0 at :129 failed) had its root cause fixed by plan 2026-08-09-1252-1: AbstractPollingLeaderElector.scheduleCheck() used TimeUnit.MICROSECONDS instead of MILLISECONDS, so the leader-elector polling cadence was 1000x too fast and the lease never expired on time; fixing the unit plus adding coordinator-0 leadership pre-confirmation makes the cross-JVM HA-fencing takeover path actually exercisable. T2 lane infrastructure itself is qualified (process spawn + registration, EVID-S14-012)
implementation_anchor: nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/multijvm/TestMultiJvmCoordinatorFailover.java:106-132
runtime_wiring: wired
positive_proof: TestMultiJvmCoordinatorFailover#testCoordinatorKillTriggersStandbyTakeover + #testBrainSplitFencingBoundary (fresh T2-lane re-run 2026-08-09: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, elapsed 8.201s; both assert lease leader_id flips to coordinator-1 with strictly greater leader_epoch)
rejection_proof: none
environment_class: multi-jvm
required_lane: multi-jvm
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S13-017
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:980-1037
declared_guarantee: M8-2-P0-1 revalidation (globalRecovery, re-audited 2026-08-09-1300-1) — the unsynchronized-concurrent-recovery-driver defect is hardened (fencing-epoch-before-lock + late-arrival guard + restart cap), proven to serialize in-process. RATIONALE UPDATE: T2 capability gaps (§2b) resolved 2026-08-09-1300-1 — the passing T2 tests prove single-coordinator cross-JVM recovery epoch rotation + redeploy (EVID-S13-015). HOWEVER the concurrent-distributed-mutex invariant (single global epoch rotation under concurrent distributed recovery drivers, the core M8-2-P0-1 claim) is not directly proven by the single-coordinator T2 recovery test. Stays residual-risk; owned by Stage 14
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
declared_guarantee: M8-2-P1-6 revalidation (zombie task, re-audited 2026-08-09-1300-1) — LOCAL zombie mitigation is hardened (waitForTerminal fail-loud timeout + per-region restart budget). RATIONALE UPDATE: T2 capability gaps (§2b) resolved 2026-08-09-1300-1 — the passing T2 tests prove cross-JVM recovery redeploy (EVID-S13-015) and HA-fencing takeover (EVID-S13-016). HOWEVER true cross-JVM stale-attempt zombie fencing (rejecting output from a zombie producer in another JVM that keeps emitting after its epoch was superseded) is a narrower invariant not directly proven by the passing T2 tests (the T2 recovery test kills the TM rather than leaving it running as a zombie). Stays residual-risk; owned by Stages 13/14
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
source_anchor: nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/multijvm/TestMultiJvmExactlyOnceRecovery.java:90
declared_guarantee: Local-vs-distributed recovery boundary (re-audited e2e-proved 2026-08-09-1300-1) — the DISTRIBUTED recovery path via JobCoordinator.globalRecovery (cross-JVM reassignment) is now PROVEN at the multi-jvm lane: TestMultiJvmExactlyOnceRecovery#multiJvmDeployKillRecoverFencing kills one TM (real OS SIGTERM), restarts a replacement, and asserts the coordinator's globalRecovery fired (fencing epoch strictly rotated, recoveredAssignmentCount>=2 cross-JVM redeploy) — exactly the cross-JVM reassignment that previously "needed the multi-jvm lane". The LOCAL recovery path via SupervisionLoop.restartRegion (per-region in-process budget) remains exercised in-process. NOTE: the JobCoordinator Javadoc (220-224) note about a scoped per-region restart counter is a separate LOCAL deferred follow-up (in-process concern, not a multi-jvm claim) and does not block this row's cross-JVM claim
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:980
runtime_wiring: wired
positive_proof: TestMultiJvmExactlyOnceRecovery#multiJvmDeployKillRecoverFencing (fresh T2-lane re-run 2026-08-09: Tests run: 1, Failures: 0, Errors: 0, elapsed 67.38s; asserts recoveredEpoch>initialEpoch, recoveredAssignmentCount>=2)
rejection_proof: none
environment_class: multi-jvm
required_lane: multi-jvm
finding_id: none
disposition: e2e-proved
@@END

---

## Cross-Reference Notes (final disposition of historical P0/P1 findings touched by this audit)

- **M8-2-P0-1** (globalRecovery unsynchronized): **PARTIAL / residual-risk.** Fencing-epoch-before-lock ordering + late-arrival guard + restart cap are present and proven to serialize two concurrent recovery drivers to one rotation in-process (`JobCoordinator.globalRecovery:980-1004`; EVID-S13-012 / EVID-S13-014 / EVID-S13-017). Re-audited 2026-08-09-1300-1: the T2 `multi-jvm` lane deeper tests now PASS (EVID-S13-015/016), but the concurrent-distributed-mutex invariant (single global epoch rotation under concurrent distributed recovery drivers) is a STRONGER claim not directly proven by the single-coordinator T2 recovery test, so it stays residual-risk.
- **M8-2-P1-4** (TaskManager.deployTask permit leak): **RESOLVED.** `deployTask:426-435` fences out the existing slot occupant and reclaims its permit (net permit change 0). Guarded by `TestTaskManager#testRedeployToOccupiedSlotDoesNotLeakPermit` and `#testDuplicateAssignmentDoesNotLeakSemaphore` (EVID-S13-018). `disposition: e2e-proved`.
- **M8-2-P1-6** (zombie task): **PARTIAL / residual-risk.** LOCAL zombie mitigation hardened (`SupervisionLoop.waitForTerminal:492-503` fail-loud timeout; EVID-S13-013 / EVID-S13-019). Re-audited 2026-08-09-1300-1: the T2 HA-fencing takeover test now PASSES (EVID-S13-016), but true stale-attempt zombie fencing across JVMs (rejecting output from a zombie producer that keeps emitting after its epoch was superseded) is a NARROWER claim not directly proven — the T2 recovery test kills the TM rather than leaving it running as a zombie — so it stays residual-risk.
- **M7-2-P0-6** (fencing-token rejection ZERO tests): **RESOLVED.** `TaskManager.receiveAssignment/triggerCheckpoint/deployTask` now throw `ERR_STREAM_FENCING_TOKEN_MISMATCH` (was silent LOG.warn+return). Guarded by `TestFencingTokenRejection` + `TestFencingEpochUnification` (EVID-S13-007 / EVID-S13-020). `disposition: e2e-proved`.
- **M8-2-P2-10** (JobCoordinator.assignTasks leaves registry and in-memory maps inconsistent when RPC dispatch throws mid-iteration): **residual-risk (deferred P2).** The normal-path assignment-dispatch capability is `e2e-proved` (EVID-S13-001, `TestRpcDistributedExecutorE2E#fullPipelineRunsOverRpcControlPlane`), but the mid-iteration-throw failure-consistency edge remains a deferred P2 owned by the active remediation plan `2026-08-04-2300-1-coordinator-runtime-concurrency-recovery-hardening.md`. Not a Stage-13-confirmed new live defect; disposition tracked by that owner plan.
- **M8-2-P2-15** (JobCoordinator.failJob/stop do not cancel in-flight tasks on TaskManagers — zombie emissions after FAILED): **residual-risk (deferred P2).** The termination-command capability (G23 four-mode) is `e2e-proved` (EVID-S13-004), but the failJob-path zombie-emission edge remains a deferred P2 owned by the same coordinator-runtime-concurrency remediation plan. Adjacent to M8-2-P1-6 (zombie) cross-JVM residual.

## T2 Lane Defect Disposition (cross-ref Stage 5 T2 record `@@LANE` note) — RESOLVED 2026-08-09-1300-1

- **TestMultiJvmExactlyOnceRecovery log-label mismatch** (formerly `:111` read `logFileFor("coordinator")`; `MiniStreamCluster:404` writes `"coordinator-0"`): **RESOLVED** by plan `2026-08-09-1252-1` (centralised `COORDINATOR_LABEL = "coordinator-0"` + fixed `AbstractPollingLeaderElector.scheduleCheck` `MICROSECONDS`→`MILLISECONDS`). Re-audited `disposition: e2e-proved`, `required_lane: multi-jvm` (EVID-S13-015). Fresh T2-lane re-run 2026-08-09: Tests run: 1, Failures: 0, Errors: 0, elapsed 67.38s. Proves cross-JVM recovery fencing/redeploy enabling infrastructure (NOT full source->keyBy->sink exactly-once, which is a Stage 43+ follow-up).
- **TestMultiJvmCoordinatorFailover HA-fencing takeover failure** (formerly `:129` "coordinator-1 must take over" assertion failed): **RESOLVED** by plan `2026-08-09-1252-1` (root cause: `AbstractPollingLeaderElector.scheduleCheck` used `MICROSECONDS` instead of `MILLISECONDS`, so the leader-elector polling cadence was 1000x too fast and the lease never expired on time). Re-audited `disposition: e2e-proved`, `required_lane: multi-jvm` (EVID-S13-016). Fresh T2-lane re-run 2026-08-09: Tests run: 2, Failures: 0, Errors: 0, elapsed 8.201s. Proves cross-JVM HA-fencing takeover capability.

## Non-Goals honored (not silently dropped)

- Real multi-JVM data-plane recovery (record/barrier/watermark transport across JVMs) = Stage 14. This audit references the Stage 5 T2 `qualified` lane; the two deeper defects (EVID-S13-015/016) were fixed by plan `2026-08-09-1252-1` and re-audited e2e-proved on 2026-08-09-1300-1.
- Checkpoint barrier alignment / state backend / window / CEP semantics = Stages 9/10/11/12. This audit only cross-references M8-2-P0-1 / M8-2-P1-6 / M7-2-P0-6 live-revalidation results (consistent with Stage 9 EVID-S9-016 / EVID-S9-019).
- Connector source/sink guarantees = Stages 15/16.
- Fixing confirmed live defects discovered by this audit = the two known T2 defects (EVID-S13-015/016) were already fixed by plan `2026-08-09-1252-1` and re-audited e2e-proved here on 2026-08-09-1300-1 (no new defects discovered by this re-audit).

## Coverage Gaps (assigned to successor remediation — NOT confirmed new live defects)

- **No single in-process test exercises a real leadership transition (standby→active) with a concurrent in-flight control command that must be rejected by the new epoch end-to-end across the RPC boundary in one assertion.** The transition (EVID-S13-010) and the stale-rejection (EVID-S13-006/007) are each proven in-process, but a combined transition+stale-command-over-RPC assertion would be stronger. Test-effectiveness coverage gap (roadmap item 17), not a live defect.
- **Concurrent distributed recovery mutual-exclusion (M8-2-P0-1) and cross-JVM stale-attempt zombie fencing (M8-2-P1-6)** stay `residual-risk`: the T2 lane deeper tests now PASS (EVID-S13-015/016), but they assert STRONGER/NARROWER invariants not directly covered — concurrent racing coordinators for the mutex, and a zombie producer kept alive for zombie fencing. Honestly `residual-risk` (rationale updated in each row), not silently upgraded.
