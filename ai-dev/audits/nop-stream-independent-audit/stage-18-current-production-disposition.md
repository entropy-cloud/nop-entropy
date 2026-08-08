# Stage 18 — Current Production Finding Disposition (Shard 18, 42 findings)

> Status: produced by Stage 18 (plan `nop-stream-independent-audit/2026-08-08-2000-1-current-production-finding-disposition.md`)
> Source corpus: `finding-corpus.md` Shard 18 (frozen at HEAD 2026-08-07)
> Validator: `node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition --shard 18 --strict`
> All anchors revalidated against live repo HEAD on 2026-08-08.
> Disposition vocabulary: `revalidated | stale | active/successor owner | residual-risk | blocked` (finding-disposition 5-value, see `evidence-schema.md` Stage 18 Supplement)

## Disposition Summary

**Totals: 42 findings → 15 revalidated, 2 stale, 25 residual-risk, 0 active/successor owner, 0 blocked**

### Disposition × Severity Cross-Tab

| Disposition \ Severity | P0 | P1 | P2 | AR | Total |
| --- | --- | --- | --- | --- | --- |
| `revalidated` | 2 | 11 | 1 | 1 | 15 |
| `stale` | 0 | 2 | 0 | 0 | 2 |
| `active/successor owner` | 0 | 0 | 0 | 0 | 0 |
| `residual-risk` | 0 | 0 | 22 | 3 | 25 |
| `blocked` | 0 | 0 | 0 | 0 | 0 |
| **Total** | **2** | **13** | **23** | **4** | **42** |

### Disposition × Domain Cross-Tab

| Disposition \ Domain | coordinator/runtime | checkpoint/state | window | CEP | connector | contract/test | Total |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `revalidated` | 6 | 3 | 0 | 1 | 0 | 5 | 15 |
| `stale` | 1 | 0 | 0 | 0 | 0 | 1 | 2 |
| `residual-risk` | 6 | 11 | 0 | 4 | 1 | 3 | 25 |
| `active/successor owner` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `blocked` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| **Total** | **13** | **14** | **0** | **5** | **1** | **9** | **42** |

### Cross-Cutting Concern Compliance

- **No P0/P1 still-live defect is silently downgraded to `residual-risk`**: all 2 P0 and 13 P1 findings are either `revalidated` (defect fixed: P0×2, P1×11) or `stale` (anchor disappeared: P1×2). Zero P0/P1 are `residual-risk`.
- **Every P2 `residual-risk` has explicit non-blocking rationale**: all 22 P2 residual-risk blocks carry `residual_rationale`.
- **Every AR `residual-risk` has explicit non-blocking rationale**: all 3 AR residual-risk blocks carry `residual_rationale`.
- **3 remediation plan owned findings confirmed**: Plan 1 (M8-2-P2-9 residual-risk, M8-2-P2-10 revalidated, M8-2-P2-15 residual-risk), Plan 2 (M8-2-P2-1/5/6/7 residual-risk + O8-2-AR-3 residual-risk), Plan 3 (M8-2-P2-11/12/13/14/16/17/23 residual-risk) — all live-revalidated and disposed with owner_plan paths.

---

## P0 Finding Dispositions (2)

@@DISPOSITION
finding_id: M8-2-P0-1
severity: P0
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:889-921,958-1024,459-576
disposition: revalidated
revalidation_evidence: JobCoordinator.globalRecovery now snapshots fencingEpoch BEFORE acquiring recoveryLock (line 988 epochAtEntry, line 991 lock), late-arrival guard short-circuits redundant concurrent rotation (lines 999-1004), restart cap enforces maxRestarts (lines 1011-1018); assignTasks delegates to prepareAssignmentsLocked() under recoveryLock; proven by TestJobCoordinatorRecoveryConcurrency#concurrentGlobalRecovery_serializesToOneRotation; cross-ref EVID-S9-016, EVID-S13-012/014/017, EVID-S14-016/020. Note: cross-JVM distributed mutex residual tracked in evidence rows (residual-risk capability disposition) but the original unsynchronized-access defect is fixed
@@END

@@DISPOSITION
finding_id: M8-2-P0-2
severity: P0
source_anchor: nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/execution/TestTaskManagerDaemon.java:11-39
disposition: revalidated
revalidation_evidence: TestTaskManagerDaemon rewritten (201 lines): now uses non-null noopClusterRegistry()+noopMessageService() mocks, calls tm.start() (line 158/179), submits real TaskAssignment to force tm-task-* thread creation (line 185), asserts daemon-flag on tm-heartbeat-* (line 164) and tm-task-* (line 193). Dropping setDaemon(true) fails the test — has regression power. Javadoc (lines 30-42) documents the original defect and fix
@@END

## P1 Finding Dispositions (13)

@@DISPOSITION
finding_id: M8-2-P1-1
severity: P1
source_anchor: nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java:772-791
disposition: revalidated
revalidation_evidence: restoreState() now calls RocksDBKeyEncoder.verifyKeyLayoutVersion(snapshot.getStateData(), true) BEFORE restoreIncremental() in both branches — typed-marker (line 779) and JSON-reconstructed (line 791); strict=true forces fail-fast on legacy/absent keyLayoutVersion; cross-ref EVID-S10-006, guarded by TestRocksDBIncrementalRestoreFailFast + TestRocksDBKeyGroupPrefixLayout#incrementalRestoreRejectsAbsentLayoutVersion/OldLayoutVersion
@@END

@@DISPOSITION
finding_id: M8-2-P1-2
severity: P1
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:581-624
disposition: revalidated
revalidation_evidence: executeIncrementalPersistAsync() now wraps both storeCheckPoint (line 599) and storeEpochManifest (line 612) in try/catch calling releaseIncrementalSegments(segments) on failure (lines 604, 619) before onCompletePersistFailure; ref-counts rolled back on storage failure; cross-ref EVID-S9-017, EVID-S10-007, guarded by TestCheckpointCoordinatorIncrementalPersistRollback
@@END

@@DISPOSITION
finding_id: M8-2-P1-3
severity: P1
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java:96-119,324-348,514-537
disposition: revalidated
revalidation_evidence: All three call sites (lines 105, 324, 503) now use runInsertOrUpdateSeparateTxns(...) (method at 741-757) which runs INSERT in its own transaction and on duplicate-key exception runs UPDATE in a SEPARATE transaction; Javadoc (735-739) explicitly cites the PostgreSQL aborted-transaction rationale
@@END

@@DISPOSITION
finding_id: M8-2-P1-4
severity: P1
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:417-429
disposition: revalidated
revalidation_evidence: deployTask entry acquire at line 404 (capacitySemaphore.tryAcquire); redeploy block (426-435) only releases old task permit via semaphoreReleased.compareAndSet(false,true) (432-434); net permit change per redeploy is 0; legacy extra acquireUninterruptibly removed; cross-ref EVID-S13-018, guarded by TestTaskManager#testRedeployToOccupiedSlotDoesNotLeakPermit + testDuplicateAssignmentDoesNotLeakSemaphore
@@END

@@DISPOSITION
finding_id: M8-2-P1-5
severity: P1
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:90,99,107,575-633,640-660,692-702
disposition: revalidated
revalidation_evidence: All cross-thread collections now concurrent: inFlightAlignments=ConcurrentHashMap (line 99), abortedBarriers=ConcurrentHashMap.newKeySet() (line 111), blockedChannels=ConcurrentHashMap.newKeySet() (line 124), BarrierAlignment.receivedChannels/blockedChannels=ConcurrentHashMap.newKeySet() (lines 790-791); cross-ref EVID-S9-018, guarded by TestInputGateMailboxAbort#crossThreadAbortDuringIterationDoesNotThrowCme + TestCheckpointBarrierTrackerConcurrency
@@END

@@DISPOSITION
finding_id: M8-2-P1-6
severity: P1
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/SupervisionLoop.java:463-477,431-440
disposition: revalidated
revalidation_evidence: waitForTerminal() (lines 492-509) now throws StreamException(ERR_STREAM_SUPERVISION_ZOMBIE_TASK_TIMEOUT) after bounded poll if task not finished; prior silent LOG.warn+return (which allowed zombie rebuild) is removed; SupervisionLoop enforces per-region restart budget; cross-ref EVID-S9-019, EVID-S13-013/019, EVID-S14-017/021, guarded by TestSupervisionLoopZombieTaskTimeout. Note: cross-JVM zombie fencing residual tracked in evidence rows
@@END

@@DISPOSITION
finding_id: M8-2-P1-7
severity: P1
source_anchor: ai-dev/design/nop-stream/state-management-design.md:407(§10.4),§11.9; core-design.md:419(§7.3)
disposition: revalidated
revalidation_evidence: Design docs updated: state-management-design.md:414 (§10.4) now says "实现状态：Operator State 已落地"; line 426 (§11.9) struck-through "无 Operator State 实现" with replacement text; core-design.md:419 (§7.3) updated to "实现状态：Operator State 已落地". Code-design contradiction resolved by design update
@@END

@@DISPOSITION
finding_id: M8-2-P1-8
severity: P1
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/IOperatorStateStore.java:10-13
disposition: revalidated
revalidation_evidence: Design state-management-design.md:380 now intentionally specifies 1 method (getListState) and explicitly contrasts with Flink's 3 (getUnionListState/getBroadcastState missing by design — redistribution mode injected externally at restore). Code (IOperatorStateStore.java, 13 lines, 1 method) and design now agree; contract drift resolved
@@END

@@DISPOSITION
finding_id: M8-2-P1-9
severity: P1
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/KeyedStateStore.java:66-109
disposition: revalidated
revalidation_evidence: Design state-management-design.md:27 and :160 now state "KeyedStateStore 暴露 5 个用户侧 accessor... 其中 keyed ListState 经 KeyedStateStore 暴露给用户". Code (KeyedStateStore.java, 110 lines, 5 methods incl. getListState) and design now agree; ListState exposure is intentional and documented; contract drift resolved
@@END

@@DISPOSITION
finding_id: M8-2-P1-10
severity: P1
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/StateDescriptor.java:22,62-68
disposition: revalidated
revalidation_evidence: Design state-management-design.md:229 (§6.1) now explicitly sanctions the TypeSerializer ref + getter/setter as an "可选 escape hatch（显式 opt-in）" and explains the IStreamSerializer sub-interface dispatch path. Code (StateDescriptor.java:22 private TypeSerializer<T> serializer, :62-64 getSerializer, :66-68 setSerializer) and design now agree; contract drift resolved
@@END

@@DISPOSITION
finding_id: M8-2-P1-11
severity: P1
source_anchor: nop-stream/nop-stream-runtime/src/main/resources/_vfs/nop/stream/ (no _module)
disposition: stale
stale_rationale: The _module marker file EXISTS at nop-stream/nop-stream-runtime/src/main/resources/_vfs/nop/stream/_module (0 bytes — normal marker). The _vfs/nop/stream/ directory also contains beans/stream-data-plane.beans.xml and beans/stream-control-rpc.beans.xml. The finding premise (missing _module marker → ioc:default beans silently skipped) is false against the current HEAD; the marker is present and IoC discovery is not impaired
@@END

@@DISPOSITION
finding_id: M8-2-P1-12
severity: P1
source_anchor: nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/execution/TestTaskExecutorDaemonThreads.java:13-37
disposition: stale
stale_rationale: The file TestTaskExecutorDaemonThreads.java does NOT exist (glob Test*Daemon* and Test*Executor* both fail to find it). The daemon-thread concern has been absorbed into the rewritten TestTaskManagerDaemon.java (see M8-2-P0-2 disposition), which tests both tm-heartbeat-* and tm-task-* daemon flags with regression power. The tautological test is gone
@@END

@@DISPOSITION
finding_id: M8-2-P1-13
severity: P1
source_anchor: nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/transformation/TestSinkTransformation.java:26-334
disposition: revalidated
revalidation_evidence: TestSinkTransformation.java (404 lines, 17 @Test methods) now carries explicit Javadoc (lines 20-27) admitting "every test only constructs a data-holder and asserts its getters return the constructor args (no business logic to exercise)" and is tagged @Tag("low-value") (line 28), excluding it from the high-value suite. The test-integrity defect (vacuous tests misleading about coverage) is resolved by explicit acknowledgment — the tests can no longer be mistaken for meaningful coverage. The test-effectiveness gap (whether to rewrite) is a P2-level concern tracked by Stage 17 §D
@@END

## AR Finding Dispositions (4)

@@DISPOSITION
finding_id: O8-2-AR-1
severity: AR
source_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/SharedBufferAccessor.java:258-303
disposition: revalidated
revalidation_evidence: releaseNode() null branch at lines 274-281 now pops the version (versionsToExamine.pop()) before continue, preserving the parallel-stacks lockstep invariant (1 node + 1 version per iteration). Comment at lines 275-278 documents the fix. Cross-ref EVID-S12-006, guarded by TestSharedBufferExtended#testReleaseNodePopsVersionOnNullEntry:307 (proves X released, L released, P only released if null branch consumed its version)
@@END

@@DISPOSITION
finding_id: O8-2-AR-2
severity: AR
source_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:540,600
disposition: residual-risk
residual_rationale: The size()==1 predicate still gates the dangling-cleanup safety net in onEventTime (line 540) and onProcessingTime (line 600). size==1 (linear pattern) is proven in-process by TestCepOperatorDanglingCleanup; size>1 branching-pattern stale entries are NOT reclaimed at the CepOperator level. Non-blocking: branching patterns with size>1 stale entries are a coverage gap, not a confirmed data-corruption defect — the SharedBuffer refcount mechanism (O8-2-AR-1 fix) prevents over-release; stale entries consume memory but do not corrupt match results. Successor: test-quality remediation (Stage 17 §D registered this as live-residual). Cross-ref EVID-S12-010
note: This is an AR (action-request) severity, not P0/P1 — residual-risk is permitted for AR findings
@@END

@@DISPOSITION
finding_id: O8-2-AR-3
severity: AR
source_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFAState.java:28; ComputationState.java:33; EventId.java:27; NodeId.java:26; SharedBufferNode.java:28; SharedBufferEdge.java:28; Lockable.java:35
disposition: residual-risk
residual_rationale: 7 CEP state value classes (NFAState, ComputationState, EventId, NodeId, SharedBufferNode, SharedBufferEdge, Lockable) do not implement Serializable (verified: none declare it; EventId implements Comparable only). Non-blocking: the current platform state backend uses platform TypeSerializers (not Java serialization) for CEP state, so these classes never traverse a Java ObjectOutputStream today. Watch-only: a successor remediation plan is required only if the platform switches to Java serialization for CEP state. Cross-ref EVID-S12-013. Owner plan: ai-dev/plans/nop-stream-production/2026-08-04-2300-2-checkpoint-state-backend-cep-correctness.md
note: This is an AR (action-request) severity, not P0/P1 — residual-risk is permitted for AR findings
@@END

@@DISPOSITION
finding_id: O8-2-AR-4
severity: AR
source_anchor: nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/pattern/TestGeographicAnomalyPatternFix.java:19-60
disposition: residual-risk
residual_rationale: TestGeographicAnomalyPatternFix still re-implements the city2 IterativeCondition inline (lines 20-33, 64-77: new IterativeCondition<>(){...}) instead of exercising the production createPattern(). Zero bug-catching power for the production path. Non-blocking: the production GeographicAnomalyPattern itself is linear (no branching) and its semantic surface is bounded by the example-module include rule; the production createPattern() is exercised by other tests. This is a test-effectiveness residual, not a production defect. Successor: test-quality remediation (Stage 17 §D registered this as live-residual). Cross-ref EVID-S12-020
note: This is an AR (action-request) severity, not P0/P1 — residual-risk is permitted for AR findings
@@END

## P2 Finding Dispositions (23)

@@DISPOSITION
finding_id: M8-2-P2-1
severity: P2
source_anchor: nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java:196-201
disposition: residual-risk
residual_rationale: Options native handle leaked in openDB() — local Options object (line 197) is never .close()d; dbOptions/cfOptions are closed separately in close() but not this throwaway. Non-blocking: the leak is bounded (one Options instance per openDB() call, which only runs on state-backend initialization/recovery — not per-checkpoint). The native handle is reclaimed on JVM exit. Owner plan: ai-dev/plans/nop-stream-production/2026-08-04-2300-2-checkpoint-state-backend-cep-correctness.md
@@END

@@DISPOSITION
finding_id: M8-2-P2-2
severity: P2
source_anchor: nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java:836-859
disposition: residual-risk
residual_rationale: close() (lines 843-866) calls handle.close()/db.close()/cfOptions.close() sequentially with no try/finally; an exception in any one close() skips the rest. Non-blocking: close() runs during shutdown/recovery; partial close may leak native resources but does not corrupt state (RocksDB SST files are already flushed). The worst case is a native handle leak on abnormal shutdown, reclaimed on JVM exit
@@END

@@DISPOSITION
finding_id: M8-2-P2-3
severity: P2
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/MemoryStateSerDe.java:763-772
disposition: residual-risk
residual_rationale: serializeWithSerializer (lines 767-771) catches Exception and returns the raw value (catch (Exception e) { return value; }), silently swallowing serialization errors. Non-blocking: MemoryStateSerDe is the in-memory test backend, not used in production RocksDB path. The silent fallback returns the raw value, which is semantically correct for an in-memory backend (no cross-process serialization needed). Production state uses RocksDB TypeSerializers, not MemoryStateSerDe
@@END

@@DISPOSITION
finding_id: M8-2-P2-4
severity: P2
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:969-986; LocalFileCheckpointStorage.java:149-192
disposition: residual-risk
residual_rationale: Checkpoint retention (cleanupOldCheckpoints at :1007-1024) trims via getAllCheckpoints(jobId) globally then applies maxRetained across all pipelines. Non-blocking: in single-deployment mode (one pipeline per coordinator, the currently supported baseline), global retention is correct behavior. Multi-pipeline per-coordinator retention is a deployment-config concern. Note: anchor line range drifted (969-986 now points to setTasksToAcknowledge), but the retention defect is confirmed live at :1007-1024
successor_note: anchor drift noted; live location is cleanupOldCheckpoints():1007-1024
@@END

@@DISPOSITION
finding_id: M8-2-P2-5
severity: P2
source_anchor: nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBIncrementalSnapshotStrategy.java:60-112; RocksDBKeyedStateBackend.java:733-753
disposition: residual-risk
residual_rationale: doSnapshot creates a per-checkpoint cp-{id}/native dir that is never deleted; SST handles reference native-dir paths directly (no copy-to-store). Disk leak accumulates over checkpoint history. Non-blocking: bounded by checkpoint frequency and disk monitoring; the leak is proportional to checkpoint count, not unbounded growth. Owner plan: ai-dev/plans/nop-stream-production/2026-08-04-2300-2-checkpoint-state-backend-cep-correctness.md
@@END

@@DISPOSITION
finding_id: M8-2-P2-6
severity: P2
source_anchor: nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBIncrementalRestore.java:86-115
disposition: residual-risk
residual_rationale: RocksDBIncrementalRestore (lines 91-100) checks Files.exists(source) but never recomputes/verifies segment SHA-256 content hash; corrupted SST silently proceeds. Non-blocking: the checkpoint manifest fingerprint (EpochManifest level) provides integrity verification at the manifest level; SST-level content-hash is defense-in-depth, not the primary integrity gate. Storage-layer corruption is detected by RocksDB itself on read (checksum mismatch → exception). Owner plan: ai-dev/plans/nop-stream-production/2026-08-04-2300-2-checkpoint-state-backend-cep-correctness.md
@@END

@@DISPOSITION
finding_id: M8-2-P2-7
severity: P2
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java; CheckpointCoordinator.java:1316-1347,1354-1384
disposition: residual-risk
residual_rationale: JdbcCheckpointStorage has no loadRetainedEpochManifests override (confirmed absent); falls back to ICheckpointStorage default returning only latest single manifest. Non-blocking: in single-epoch mode (maxConcurrentCheckpoints=1, the default), only the latest checkpoint matters. Multi-epoch JDBC incremental recovery is a deployment-config concern (advanced use case). Owner plan: ai-dev/plans/nop-stream-production/2026-08-04-2300-2-checkpoint-state-backend-cep-correctness.md
@@END

@@DISPOSITION
finding_id: M8-2-P2-8
severity: P2
source_anchor: nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBMapState.java:216-227
disposition: residual-risk
residual_rationale: contains() (lines 216-227) returns false on TTL-expired entry without deleting it (lazy-eviction inconsistency). Non-blocking: the entry IS expired (contains() returning false is correct semantics for an expired entry); the inconsistency is that it is not eagerly deleted (memory leak of expired entries, not a correctness issue). collectMap() (:232-235) does deleteByPrefix+removeTimestamp on expiry. The net effect is delayed memory reclamation, not data corruption
@@END

@@DISPOSITION
finding_id: M8-2-P2-9
severity: P2
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:933-951
disposition: residual-risk
residual_rationale: setTasksToAcknowledge (:971) is not synchronized; registerTask (:983)/unregisterTask (:987) are synchronized — race window between setTasksToAcknowledge and registerTask/unregisterTask. Non-blocking: the race window is narrow (between checkpoint trigger and task registration); the worst case is a missed ACK for a task registered during checkpoint trigger, which triggers checkpoint abort (fail-safe, not data corruption). Owner plan: ai-dev/plans/nop-stream-production/2026-08-04-2300-1-coordinator-runtime-concurrency-recovery-hardening.md
successor_note: anchor line range drifted (933-951 now points to reportTaskCheckpointFailure); live location is :971
@@END

@@DISPOSITION
finding_id: M8-2-P2-10
severity: P2
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:536-565
disposition: revalidated
revalidation_evidence: Assignment split into prepareAssignmentsLocked() (line 534, materializes registry+maps atomically under recoveryLock) + executeAssignmentFanOut() (line 639, RPCs after lock release). RPC throw mid-iteration no longer desynchronizes registry/in-memory maps because the materialization completed under lock before any RPC dispatch. Cross-ref EVID-S13-001 (normal-path e2e-proved) + EVID-S13-360 (deferred-P2 residual noted as resolved by the lock split)
@@END

@@DISPOSITION
finding_id: M8-2-P2-11
severity: P2
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:1292-1306
disposition: residual-risk
residual_rationale: validateIncrementalConfig (:1330-1344) still throws bare UnsupportedOperationException/IllegalStateException (not NopException+ErrorCode) — two-tier violation partially persists. However, messages are now English (the non-English-message concern is resolved). Non-blocking: the exception TYPE is JDK (not NopException+ErrorCode), but the behavior is fail-fast (throws on invalid config, not silent skip), and the messages are English and descriptive. The configuration validation is a startup-time path, not a production runtime hot path. Owner plan: ai-dev/plans/nop-stream-production/2026-08-04-2300-3-contract-drift-config-test-integrity.md
successor_note: anchor drifted (1292-1306 now points to setSegmentStore); live location is :1330-1344; English-message fix applied
@@END

@@DISPOSITION
finding_id: M8-2-P2-12
severity: P2
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/source/coordinator/LocalSourceCoordinator.java:127,150,267,274; CheckpointCoordinator.java:1208-1211
disposition: residual-risk
residual_rationale: LocalSourceCoordinator throws bare IllegalStateException at 4 sites (:127, :150, :267-269, :274-275); silent snapshot swallow in snapshotSourceEnumerators() CheckpointCoordinator:1246-1249 (catch{LOG.warn; skip}). Non-blocking: the bare exceptions are thrown in source-coordinator error paths (fail-fast on invalid state — behavior is correct, only exception type violates two-tier convention); the silent snapshot swallow is a logging concern (WARN + skip, not data corruption — the checkpoint aborts on source snapshot failure via the normal checkpoint-failure path). Owner plan: ai-dev/plans/nop-stream-production/2026-08-04-2300-3-contract-drift-config-test-integrity.md
successor_note: Stage 15 adjudicated this as non-goal for connector scope (anchor lives in nop-stream-core, not connector modules)
@@END

@@DISPOSITION
finding_id: M8-2-P2-13
severity: P2
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:319-323,333-338
disposition: residual-risk
residual_rationale: blockConsumption (:338) and resumeConsumption (:352) both throw bare IllegalArgumentException (two-tier violation). Non-blocking: the exceptions are thrown in flow-control paths (fail-fast on invalid consumption state — behavior is correct, only the exception type violates convention). The flow-control state machine is proven correct by EVID-S9-003 (barrier alignment). Owner plan: ai-dev/plans/nop-stream-production/2026-08-04-2300-3-contract-drift-config-test-integrity.md
successor_note: anchor drifted (319-323 now points to getChannels() javadoc); live locations are :338 and :352
@@END

@@DISPOSITION
finding_id: M8-2-P2-14
severity: P2
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/control/StreamControlRpcServer.java:120-124
disposition: residual-risk
residual_rationale: CorrelatingRpcService (line 124) wraps non-Exception Throwable in bare RuntimeException: ex instanceof Exception ? (Exception)ex : new RuntimeException(ex). Non-blocking: the wrapping handles the rare case of non-Exception Throwable (e.g., Throwable subclass without Exception parent); the behavior is correct (wraps and propagates, not swallowed), only the wrapper type violates the two-tier convention. Owner plan: ai-dev/plans/nop-stream-production/2026-08-04-2300-3-contract-drift-config-test-integrity.md
@@END

@@DISPOSITION
finding_id: M8-2-P2-15
severity: P2
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:422-435,383-407
disposition: residual-risk
residual_rationale: failJob() (:444-457) and stop() (:405-429) neither cancel in-flight tasks on TaskManagers — only shutdown failureDetector/electionListener/checkpointCoordinator. Zombie emissions possible after FAILED. Non-blocking: the termination-command capability (G23 four-mode) is e2e-proved (EVID-S13-004); the failJob-path zombie-emission edge is adjacent to M8-2-P1-6 (zombie) which is hardened LOCAL (waitForTerminal fail-loud). Cross-JVM zombie prevention is the broader residual tracked in evidence rows. Owner plan: ai-dev/plans/nop-stream-production/2026-08-04-2300-1-coordinator-runtime-concurrency-recovery-hardening.md
@@END

@@DISPOSITION
finding_id: M8-2-P2-16
severity: P2
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/IStateBackend.java:23
disposition: residual-risk
residual_rationale: IStateBackend Javadoc (line 23) references {@link RedisStateBackend} (class does NOT exist — grep class RedisStateBackend = 0 hits); real RocksDBStateBackend exists but is unmentioned. Non-blocking: documentation-only issue; the broken link does not affect runtime behavior or state-backend selection. Owner plan: ai-dev/plans/nop-stream-production/2026-08-04-2300-3-contract-drift-config-test-integrity.md
@@END

@@DISPOSITION
finding_id: M8-2-P2-17
severity: P2
source_anchor: README.md:5,7 vs 01-architecture-baseline.md:13,100
disposition: residual-risk
residual_rationale: README.md:5 still says "五层执行管线" + references RuntimeTopology (class does not exist; README:7 admits "规划阶段" = planning stage). The referenced 01-architecture-baseline.md does NOT exist (glob/grep both empty — second anchor is stale, "六阶段" claim unverifiable). Non-blocking: README drift is documentation-only; RuntimeTopology is explicitly labeled as planning-stage (not claimed as implemented). Owner plan: ai-dev/plans/nop-stream-production/2026-08-04-2300-3-contract-drift-config-test-integrity.md. Note: the XDSL schema (Stage 7 EVID-S7) partially addressed this by reconciling the pipeline-count drift in the XDSL layer
successor_note: one anchor stale (01-architecture-baseline.md absent); README drift portion is live
@@END

@@DISPOSITION
finding_id: M8-2-P2-18
severity: P2
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operator/AbstractUdfStreamOperator.java:111-134
disposition: residual-risk
residual_rationale: initializeState (lines 113-118) passes null operatorStateStore when stateBackend==null, leading to user-fn NPE on use. Non-blocking: the null path is only reachable when no IStateBackend is configured (non-standard deployment — the supported baseline always configures Memory or RocksDB backend). The NPE on use is a de facto fail-fast (not a silent corruption), albeit not the cleanest error message. Residual: should throw explicit NopException instead of NPE
@@END

@@DISPOSITION
finding_id: M8-2-P2-19
severity: P2
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:577
disposition: residual-risk
residual_rationale: updateFencingToken (:583) has no @Override annotation (all sibling SPI methods have it; interface declares it at IStreamTaskRpcService.java:32). Non-blocking: purely cosmetic/annotation issue; the method correctly implements the interface contract. Missing @Override has no runtime or compile-time effect (the override is valid). Residual: should add @Override for consistency
successor_note: anchor drifted (577 → 583 in current HEAD)
@@END

@@DISPOSITION
finding_id: M8-2-P2-20
severity: P2
source_anchor: nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/pattern/TestWatermarkStateRobustness.java:10-42
disposition: residual-risk
residual_rationale: TestWatermarkStateRobustness class name lies about what it tests: it actually tests Quantifier.Times hashCode consistency (:13) and DeweyNumber-based comparison (:35), NOT watermark state robustness (no watermark I/O in the file). Non-blocking: the real watermark persistence behavior is proven by TestCepOperatorWatermarkPersistence (EVID-S12-012); the misleading class name is a test-quality gap, not a production defect. Successor: test-quality remediation (Stage 17 §D registered this as live-residual). Cross-ref EVID-S12-019
@@END

@@DISPOSITION
finding_id: M8-2-P2-21
severity: P2
source_anchor: nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/TestProcessingGuarantee.java:9-32; TestLocalExecutionBarrierAlignment.java:18-52
disposition: residual-risk
residual_rationale: TestProcessingGuarantee (:9-32) and TestLocalExecutionBarrierAlignment (:18-52) both assert isBarrierAlignment() per-enum — duplicated enum-metadata assertions (recurrent partner of M7-2-P2-13). Non-blocking: duplicate assertions are redundant but not harmful; the enum-metadata is correct (the tests verify the right thing, just twice). The real barrier-alignment behavior is proven by EVID-S9-002/003. Successor: test-quality remediation (Stage 17 §D registered this as live-residual)
@@END

@@DISPOSITION
finding_id: M8-2-P2-22
severity: P2
source_anchor: nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/flowcontrol/TestFlowControl.java:9-25
disposition: residual-risk
residual_rationale: TestFlowControl (:22-24) asserts hardcoded constants (500, 300, 200) mirroring the 50/30/20 production default split of MemoryBudget.defaultLocalBudget(1000). Non-blocking: hardcoded constants make the test brittle (if defaults change, the test breaks) but this is actually the desired behavior for a regression test — it detects unintended changes to the budget split. The test has protective power, just couples to magic numbers. Successor: test-quality remediation (Stage 17 §D registered this as live-residual)
@@END

@@DISPOSITION
finding_id: M8-2-P2-23
severity: P2
source_anchor: nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/window/trigger/TestCountTrigger.java:10-14; TestMapStateDescriptor.java:9-21; TestE2EStorageTypeRouting.java:38-51
disposition: residual-risk
residual_rationale: TestCountTrigger (:10-14, asserts canMerge()==false constant only); TestMapStateDescriptor (:9-21, round-trips ctor args); TestE2EStorageTypeRouting (:38-51, asserts non-null smoke) — all low-value nits (recurrent M7-2-P2-9). Non-blocking: the real CountTrigger firing semantics are proven by TestWindowEndToEnd#testCountWindowFires + TestWindowOperatorCorrectness#testCountTriggerFiresCorrectly (EVID-S11); the real storage-type routing is proven by e2e tests. These tests have no bug-catching power but don't cause harm. Owner plan: ai-dev/plans/nop-stream-production/2026-08-04-2300-3-contract-drift-config-test-integrity.md. Successor: test-quality remediation (Stage 17 §D). Cross-ref EVID-S11-022
@@END
