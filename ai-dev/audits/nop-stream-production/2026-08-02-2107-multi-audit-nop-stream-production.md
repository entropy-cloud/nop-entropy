> Audit Status: planned
> Audit Type: multi-dimensional
> Mission: nop-stream-production
> Planned To: `ai-dev/plans/nop-stream-production/2026-08-04-2300-1-coordinator-runtime-concurrency-recovery-hardening.md` (P0 JobCoordinator race, P1 InputGate, P1 TaskManager permit, P1 SupervisionLoop zombie), `2026-08-04-2300-2-checkpoint-state-backend-cep-correctness.md` (P1 incremental fail-fast, P1 ref-count leak, P1 PostgreSQL upsert), `2026-08-04-2300-3-contract-drift-config-test-integrity.md` (P1 SPI/doc drift ×4, P1 _module, P0 TestTaskManagerDaemon, P1 hollow tests ×2). P2 findings → roadmap Follow-up Backlog.

# nop-stream Multi-Dimensional Audit Report

- **Audit target**: `nop-stream/` (10 submodules, 1065 Java main-scope files, 448 test files)
- **Audit date**: 2026-08-04
- **Scope**: code, config, tests, and public contracts (exports / SPI surface); cross-referenced against `ai-dev/design/nop-stream/` architecture docs for documented contract drift
- **Methodology**: followed `ai-dev/skills/deep-audit-prompts.md`. 4 parallel dimensions dispatched (public-contracts/doc-drift, state-backend/checkpoint correctness, concurrency/error/resource, test-effectiveness/config). Each finding below is evidence-backed (file:line + snippet) and graded `[P0]`/`[P1]`/`[P2]` per the mission-driver grading rubric.
- **Calibration applied**: Nop platform CRUD patterns are N/A (nop-stream is a streaming engine, no BizModel/xbiz/xmeta); `_`-prefixed generated files (`_StreamModel.java`, `_CepPattern*Model.java`) are excluded unless the generator is wrong; documented Decisions (JsonTool-only serialization Non-Goal, "CompletedCheckpointStore 不引入", G5/G34 Decision-only) are NOT flagged.

## Severity distribution

| Severity | Count | Areas |
|----------|-------|-------|
| **P0** | 2 | concurrency race in HA recovery path; vacuous load-bearing test |
| **P1** | 13 | state-corruption fail-fast gap; ref-count/disk leaks on failed cp; PostgreSQL incompatibility; contract drift (4 SPIs); semaphore permit leak; cross-thread InputGate mutation; supervision zombie-task; missing `_module` marker; hollow tests |
| **P2** | 23 | native-handle leaks; retention cross-pipeline; content-hash gap; error-handling two-tier violations; doc/javadoc rot; low-value tests |

---

# P0 Findings (blocking)

## [P0] JobCoordinator.globalRecovery / rotateFencingEpochAndRestore / assignTasks are not synchronized — concurrent recovery drivers corrupt coordinator state
*Justification: central HA/recovery code; two racing recovery drivers can rotate the fencing epoch, clear maps, and assign tasks concurrently, leaving the cluster registry, in-memory maps, and TaskManagers in mutually inconsistent state — incorrect behavior on the correctness-critical path.*

- **File**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:889-921` (`globalRecovery`), `958-1024` (`rotateFencingEpochAndRestore`), `459-576` (`assignTasks`)
- **Evidence**:
```java
public void globalRecovery() {
    int newCount = restartCount.incrementAndGet();
    if (newCount > maxRestarts) { failJob(...); return; }
    ...
    long newEpoch;
    LeaderEpoch leadership = this.currentLeadership;             // volatile read
    long leaderEpochValue = leadership != null ? leadership.getEpoch() : 0L;
    long newGen = recoveryGen.incrementAndGet();
    newEpoch = deriveHaFencingEpoch(leaderEpochValue, newGen);
    rotateFencingEpochAndRestore(newEpoch, false);               // unsynchronized
}

private void rotateFencingEpochAndRestore(long newEpoch, boolean restoreFromStorage) {
    fencingEpoch.set(newEpoch);
    clusterRegistry.registerCoordinator(jobId, coordinatorId, newEpoch);
    taskAssignmentMap.clear();                                   // unsynchronized mutation
    allTaskLocations.clear();
    for (IStreamTaskRpcService rpc : taskRpcServices.values()) rpc.updateFencingToken(newEpoch);
    ...
    assignTasks();                                               // unsynchronized, mutates maps + makes RPCs
}
```
- **现状**: `globalRecovery()` is reachable concurrently from (a) `detectFailures()` on the `failureDetector` single-thread scheduler (`:868`) and (b) `reportTaskStatus()` on the RPC server thread pool when a FAILED report arrives and `autoRecoverOnFailedReport=true` (`:751`). None of `globalRecovery`, `rotateFencingEpochAndRestore`, `assignTasks` is `synchronized`. `taskAssignmentMap`/`allTaskLocations` are `ConcurrentHashMap`s but the multi-step clear→register→assign sequence is not atomic.
- **风险**: Two overlapping recoveries can (1) interleave clears and puts so one driver's assignments land in the other's freshly-cleared map; (2) push two different fencing epochs to the same TaskManager (the second `updateFencingToken` wins, fencing out the first driver's just-assigned tasks); (3) call `clusterRegistry.assignTask` for the same subtask with two different `attemptId`s, duplicating attempt history; (4) corrupt ACK tracking.
- **建议**: Make the three methods mutually exclusive (single `synchronized` monitor, or `ReentrantLock` with a post-acquire guard re-checking `restartCount`/`fencingEpoch`). Add an "epoch already rotated past" short-circuit so a late caller sees the new epoch and returns.
- **信心水平**: 确定
- **误报排除**: Not a mailbox single-writer case — JobCoordinator is by design the control point across multiple RPC threads plus its own failure-detector thread. `restartCount` is atomic but the post-increment body is unprotected.

---

## [P0] TestTaskManagerDaemon passes vacuously — TaskManager never started, no threads exist to assert on
*Justification: claimed to test the daemon-thread invariant for production TaskExecutor/Heartbeat threads but cannot catch any regression (assertions are inside an `if` branch that never executes); a test that should guard against JVM-shutdown hangs is hollow — "absent test for changed behavior".*

- **File**: `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/execution/TestTaskManagerDaemon.java:11-39`
- **Evidence**:
```java
@Test
void testTaskExecutorThreadsAreDaemon() throws Exception {
    TaskManager tm = new TaskManager("node-daemon-test", "localhost:0", 2, null, null, "ctrl");
    java.util.Set<Thread> threadsBefore = Thread.getAllStackTraces().keySet();
    java.util.concurrent.atomic.AtomicBoolean foundDaemonTaskThread = new java.util.concurrent.atomic.AtomicBoolean(false);
    for (Thread t : Thread.getAllStackTraces().keySet()) {
        if (t.getName().startsWith("tm-task-")) {            // never true: pool threads spawn on demand
            foundDaemonTaskThread.set(true);
            assertTrue(t.isDaemon(), ...);                   // assertion INSIDE the if-branch
        }
    }
    tm.stop();   // returns immediately because running==false
}
```
- **现状**: `TaskManager`'s constructor builds `Executors.newFixedThreadPool(...)` and `newSingleThreadScheduledExecutor(...)`, both of which spawn threads lazily. The test never calls `tm.start()` nor submits any task, so zero threads named `tm-task-*`/`tm-heartbeat-*` exist at assertion time. The for-loop body never executes; `foundDaemonTaskThread` is set but never asserted against.
- **风险**: A regression removing `t.setDaemon(true)` from `TaskManager.java:129,134` will not be caught. If non-daemon threads leak on stop, JVM shutdown hangs — exactly what this test claims to guard.
- **建议**: Call `tm.start()` (and submit at least one dummy task) before scanning threads, then add `assertTrue(foundDaemonTaskThread.get(), "expected at least one tm-task-* thread to exist after start")` after the loop. Apply the same fix to `testHeartbeatThreadIsDaemon`.
- **信心水平**: 确定
- **误报排除**: Not `@EnabledIfSystemProperty`-gated; not an AutoTest snapshot. Production `TaskManager` lines 127-136 unconditionally set daemon, but the thread factory only fires after `submit/schedule`, which the test never triggers.

---

# P1 Findings (material — must be fixed)

## [P1] Incremental checkpoint restore bypasses the mandated `keyLayoutVersion` fail-fast — silent state corruption on legacy SST
*Justification: design mandates this fail-fast on the incremental path; the incremental restore branch skips it, so a legacy v1-layout SST would be byte-range-scanned under the v2 encoder and silently copy a wrong/empty subset — data loss on rescale.*

- **File**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/RocksDBKeyedStateBackend.java:772-791`
- **Evidence**:
```java
@Override
public void restoreState(StateSnapshot snapshot) throws Exception {
    if (snapshot != null && !snapshot.isEmpty()) {
        Object marker = snapshot.getStateData().get(IncrementalSnapshotResult.MARKER_KEY);
        if (marker instanceof IncrementalSnapshotResult) {
            restoreIncremental((IncrementalSnapshotResult) marker);
            return;                       // <- no verifyKeyLayoutVersion(..., true)
        }
        if (marker instanceof Map) {
            ...
            if (result != null) {
                restoreIncremental(result);
                return;                   // <- no verifyKeyLayoutVersion(..., true)
            }
        }
    }
    RocksDBSnapshotSerDe.restoreState(this, snapshot);  // only this branch verifies (with false)
}
```
- **现状**: `RocksDBKeyEncoder.verifyKeyLayoutVersion(snapshotData, true)` exists (`:164-176`) and is documented as required on the incremental path. `snapshotIncremental()` stamps `keyLayoutVersion=2` (`:748`). But the incremental restore branch jumps straight to `restoreIncremental(result)` → `RocksDBIncrementalRestore.restoreRangeInto(...)` → `copyColumnFamilyRange(...)` without calling `verifyKeyLayoutVersion(snapshotData, true)`. The only production caller is `RocksDBSnapshotSerDe.restoreState` (`:416`) with `incremental=false`.
- **风险**: A legacy v1-layout incremental checkpoint (shardId embedded mid-key, non-sortable) restored under the v2 encoder would have its first 4 bytes interpreted as a big-endian key-group id; the range scan copies a wrong/empty subset → state corruption (data loss or duplicate state on rescale). Directly contradicts `state-management-design.md` §5.3 ("增量 restore 路径对 absent/version!=2 的 SST 必须 fail-fast").
- **建议**: Before `restoreIncremental(result)` in both branches, call `RocksDBKeyEncoder.verifyKeyLayoutVersion(snapshot.getStateData(), true)`. Add an E2E test constructing an `IncrementalSnapshotResult` with `keyLayoutVersion=1` (or absent) asserting `ERR_STREAM_STATE_ERROR`.
- **信心水平**: 确定
- **误报排除**: The helper exists, is documented as mandatory on the incremental path, and is invoked from the sibling full-snapshot path — only the incremental branch in the same file omits it.

---

## [P1] Incremental persist registers shared-state refs before storage persistence; storage failure permanently leaks ref-counts
*Justification: every failed incremental checkpoint permanently inflates ref-counts for its SST segments; the physical files can never be discarded by GC for the coordinator's lifetime — disk grows monotonically per failed cp.*

- **File**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:581-624`
- **Evidence**:
```java
private void executeIncrementalPersistAsync(...) {
    final List<StateSegmentDescriptor> segments;
    try {
        segments = buildAndMaterializeSegments(completed);   // (1) registers handles in sharedStateRegistry
    } catch (Exception e) { ... }

    final EpochManifest manifest = buildEpochManifest(completed, fingerprint, segments);
    try {
        checkpointStorage.storeCheckPoint(completed);          // (2) may throw
    } catch (Exception e) {
        synchronized (this) {
            onCompletePersistFailure(completed, pending, "Failed to store checkpoint", e);
            // <-- segments already registered in (1) are NEVER unregistered
        }
        return;
    }
    try {
        checkpointStorage.storeEpochManifest(jobId, pipelineId, manifest); // (3) may throw
    } catch (Exception e) {
        synchronized (this) { onCompletePersistFailure(...); } // same: segments leak
        return;
    }
    synchronized (this) {
        checkpointSegments.put(checkpointId, segments);        // (4) only set on success
        onCompletePersistSuccess(completed, pending);
    }
}
```
- **现状**: `buildAndMaterializeSegments` calls `sharedStateRegistry.register(handle)` for every SST handle BEFORE persisting `CompletedCheckpoint`/`EpochManifest`. On storage failure `onCompletePersistFailure` (`:783-793`) does NOT unregister. `checkpointSegments.put(checkpointId, segments)` only runs on success, so a later `gcSegmentsForCheckpoint(checkpointId)` finds nothing and returns immediately — ref-counts stranded.
- **风险**: Every failed incremental checkpoint permanently inflates reference counts; physical files never discarded until coordinator restart rebuilds the registry from retained manifests.
- **建议**: In the failure branches, iterate `segments` and `sharedStateRegistry.unregister(seg.getPath())`, discarding zero-ref entries. Alternatively, register only AFTER both storage writes succeed (split materialize vs register).
- **信心水平**: 确定
- **误报排除**: `onCompletePersistFailure` does not call any unregister path; `gcSegmentsForCheckpoint` (`:995`) reads `checkpointSegments` populated only on the success branch — leak is structurally guaranteed.

---

## [P1] JdbcCheckpointStorage INSERT-then-UPDATE inside a single transaction breaks PostgreSQL
*Justification: PostgreSQL documents that any error inside a transaction puts it in "aborted" state and all subsequent statements fail — the duplicate-key fallback UPDATE cannot complete; JDBC storage is advertised as production-grade in design §9.1.*

- **File**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java:96-119` (same pattern at `:324-348`, `:514-537`)
- **Evidence**:
```java
jdbcTemplate.txn().runInTransaction(querySpace, TransactionPropagation.REQUIRED, txn -> {
    try {
        jdbcTemplate.executeUpdate(sql);                    // INSERT
    } catch (Exception e) {
        if (!isDuplicateKeyException(e)) {
            throw e;
        }
        LOG.debug("INSERT failed (duplicate key), attempting UPDATE ...", e);
        SQL updateSql = SQL.begin()...                       // UPDATE inside same txn
                .end();
        jdbcTemplate.executeUpdate(updateSql);               // <-- aborted in PostgreSQL
    }
    return null;
});
```
- **现状**: After the INSERT throws inside the transaction, PostgreSQL enters "current transaction is aborted, commands ignored until end of transaction block"; the UPDATE fails, the txn rolls back, `storeCheckPoint` reports failure. The `isDuplicateKeyException` heuristic (`:665-680`, pattern-matches class names/messages) compounds mis-classification risk.
- **风险**: Any checkpoint hitting the duplicate-key branch (retry after partial write, HA failover with fencing overlap, savepoint re-store with same id) cannot complete on PostgreSQL. The job fails to make progress.
- **建议**: Use dialect-aware upsert: `INSERT ... ON CONFLICT (...) DO UPDATE ...` (PostgreSQL) / `ON DUPLICATE KEY UPDATE` (MySQL) / `MERGE`. At minimum wrap the INSERT in a SAVEPOINT. Add a PostgreSQL integration test.
- **信心水平**: 确定
- **误报排除**: PostgreSQL's "current transaction is aborted" is documented behavior; the pattern is well-known to be PostgreSQL-incompatible. Design §9.1 explicitly claims JDBC storage is for production.

---

## [P1] TaskManager.deployTask leaks one capacity permit on every redeploy of an occupied slot
*Justification: each in-place redeploy of an occupied slot permanently shrinks effective node capacity by 1; after N cycles the node is wedged at capacity and silently rejects all assignments — slow resource exhaustion cascading into global recovery.*

- **File**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:417-429`
- **Evidence**:
```java
RunningTask existing = runningTasks.get(taskKey);
if (existing != null) {
    LOG.warn("Slot {} already occupied (attempt={}); fencing old before redeploy", ...);
    runningTasks.remove(taskKey);
    existing.cancel();
    if (existing.semaphoreReleased.compareAndSet(false, true)) {
        capacitySemaphore.release();          // line 424: returns the old slot's permit
    }
    // Re-acquire for the new deployment (we already acquired above; the
    // release just balanced the old slot's permit).
    capacitySemaphore.acquireUninterruptibly(); // line 428: extra acquire with no matching release
}
```
- **现状**: Permit accounting on recovery-of-occupied-slot: line 404 already did `capacitySemaphore.tryAcquire()` for the new task (–1); line 424 releases the old slot's permit (+1) and pre-marks `semaphoreReleased=true` so the old task's finally (`:713-715`) won't release again; line 428 then `acquireUninterruptibly()` again (–1). Net –1 (one extra acquire with no matching release). When the old task's finally runs, the CAS returns false, so no balancing release ever happens.
- **风险**: Slow capacity exhaustion. Each recovery hitting an occupied slot permanently shrinks effective capacity by 1. After `capacity` cycles the node is wedged ("Node X at capacity"); coordinator stops assigning; cascades into `globalRecovery`.
- **建议**: Remove the `capacitySemaphore.acquireUninterruptibly()` on line 428 (line 404's acquire already provides the new task's permit; line 424 rebalances the old slot). Add a regression test running `deployTask` against an occupied slot `capacity+1` times asserting `availablePermits()` stabilizes.
- **信心水平**: 确定
- **误报排除**: Traced `RunningTask.semaphoreReleased` lifecycle (only flipped false→true at `:423`/`:533`/`:713`); the old task's finally cannot balance the extra acquire.

---

## [P1] InputGate is mutated by two threads (task thread + abort handler thread) through non-thread-safe collections
*Justification: `inFlightAlignments` (LinkedHashMap), `blockedChannels`/`abortedBarriers` (HashSet) are unsynchronized and concurrently mutated — corrupts the barrier-alignment state machine, can lose barriers, deadlock channels, or throw CME/infinite-loop in the LinkedHashMap iterator.*

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:90,99,107` (fields), `575-633` (`handleBarrierNonRecursive`, task thread), `640-660` (`markFinishedChannel`, task thread), `692-702` (`abortBarrierAlignment`, abort thread); call site `nop-stream-runtime/.../GraphModelCheckpointExecutor.java:850`
- **Evidence**:
```java
private final LinkedHashMap<Long, BarrierAlignment> inFlightAlignments = new LinkedHashMap<>();
private final Set<Long> abortedBarriers = new HashSet<>();
private final Set<Integer> blockedChannels = new HashSet<>();

public void abortBarrierAlignment(long checkpointId) {     // called from coordinator-abort thread
    BarrierAlignment removed = inFlightAlignments.remove(checkpointId);
    if (removed != null) {
        for (int c : removed.blockedChannels) blockedChannels.remove(c);
    }
    abortedBarriers.add(checkpointId);
}
// task thread concurrently:
private Optional<StreamElement> handleBarrierNonRecursive(int channelIndex, CheckpointBarrier barrier) {
    ...
    align.receivedChannels.add(channelIndex);
    if (barrierAlignment) { align.blockedChannels.add(channelIndex); blockedChannels.add(channelIndex); }
    ...
    if (fullyReceived) {
        inFlightAlignments.remove(id);
        for (int c : align.blockedChannels) blockedChannels.remove(c);
    }
}
```
- **现状**: Class Javadoc says InputGate is owned by the task thread, but `abortBarrierAlignment` is invoked from the checkpoint-coordinator's timeout/ACK thread via `GraphModelCheckpointExecutor.registerLocalAbortHandler`. No method on InputGate is `synchronized`; none of the three collections is thread-safe. The task thread iterates `inFlightAlignments` via `oldestAlignments()`/`markFinishedChannel()` while the abort thread mutates it.
- **风险**: Concurrent `LinkedHashMap.remove` during `for (BarrierAlignment a : inFlightAlignments.values())` throws `ConcurrentModificationException` (uncaught → task FAILED → spurious recovery). Lost updates on `blockedChannels` can leave a channel permanently blocked. `abortedBarriers.add` racing with `contains` can fail to detect an aborted barrier, re-injecting it as a fresh alignment.
- **建议**: Either (a) make InputGate's mutable state truly single-threaded by delivering the abort as a control mail to the task's mailbox (`mailbox.put(Mail.control(() -> abortBarrierAlignment(checkpointId), "abort-barrier"))`); or (b) replace the three collections with `ConcurrentHashMap`/`ConcurrentSkipListMap` + synchronized iteration. Option (a) matches the design's mailbox-thread invariant.
- **信心水平**: 确定
- **误报排除**: Verified the call site at `GraphModelCheckpointExecutor.java:850` is reached from the abort handler closure, dispatched by `CheckpointCoordinator.abortPendingCheckpoint` on the timeout-scheduler/ACK thread, not the task thread that owns the gate.

---

## [P1] SupervisionLoop.waitForTerminal proceeds to rebuild while the old task thread may still be alive — two concurrent task instances of the same vertex
*Justification: on a slow cooperatively-cancelled task the 10s bounded wait expires and the loop silently falls through to rebuild+resubmit while the old thread keeps emitting to the same ResultPartition — breaking exactly-once (duplicate/divided emissions, corrupted materialization epochs).*

- **File**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/SupervisionLoop.java:463-477` (`waitForTerminal`), `431-440` (rebuild + resubmit on the same taskKey)
- **Evidence**:
```java
private static void waitForTerminal(SubtaskTask task, String taskKey) {
    long deadline = System.currentTimeMillis() + 10_000L; // 10s budget
    while (!task.isFinished() && System.currentTimeMillis() < deadline) {
        try { Thread.sleep(10L); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
    }
    if (!task.isFinished()) {
        LOG.warn("Task {} did not reach terminal state within 10s after cancel; state={}",
                taskKey, task.getState());            // logs and FALLS THROUGH
    }
}

// caller:
for (String taskKey : taskKeysToRestart) {
    SubtaskTask oldTask = tasks.get(taskKey);
    if (oldTask == null) continue;
    SubtaskTask newTask = rebuildTask(execPlan, oldTask, regionId, coordinator, checkpointPlan);
    tasks.put(taskKey, newTask);
    executor.submitTask(newTask);                       // both old and new now alive
}
```
- **现状**: `cancelTaskWithMailbox` issues a cooperative cancel (mailbox flag + thread interrupt) and `waitForTerminal` polls for ≤10s. If the old task is stuck in a non-interruptible section, the wait expires, the loop logs WARN and proceeds; the new task is built (with `deepCopy()` of the operator chain) and submitted. For producer-role rebuilds the design reuses the same `RecordWriter` pointing at the same `ResultPartition` (`SupervisionLoop.java:658`) — two producer threads writing to the same `LinkedBlockingQueue<StreamElement>` is unsynchronized and races on `currentMaterializationEpoch`.
- **风险**: Duplicate/divided data emissions, corrupted materialization epoch tags, race on operator internal state in deep-copied chains that share native resources. Exactly-once is broken.
- **建议**: Make the post-deadline path fail-loud — surface `ERR_STREAM_SUPERVISION_RESTART_EXHAUSTED` so the global-recovery fallback fires, instead of silently rebuilding alongside a zombie. Alternatively escalate the cancel (`future.cancel(true)` + unbounded join) only if the task thread is guaranteed interruptible.
- **信心水平**: 很可能
- **误报排除**: The 10s budget is documented as a "budget" but the fall-through code path is a silent continue — no exception, no `return false`, no caller-checked flag. This is exactly the "silent skip" anti-pattern (plan guide #24).

---

## [P1] Design docs claim "Operator State 尚未实现" but it IS implemented with full redistribution — contract drift
*Justification: two authoritative design docs (marked active) actively contradict live production code; readers will mis-assess a major subsystem as missing and either avoid it or re-implement it incorrectly — material doc/contract drift.*

- **File**: `ai-dev/design/nop-stream/state-management-design.md:407` (`§10.4`), `:§11.9`; `ai-dev/design/nop-stream/core-design.md:419` (`§7.3`)
- **Evidence**:
```
// state-management-design.md §10.4
当前缺口：Operator State 尚未实现。实现计划见 completion-roadmap.md Phase 0.3。
// state-management-design.md §11.9
无 Operator State 实现 — OperatorStateStore 接口未实现，source offset checkpoint 缺口。
// core-design.md §7.3
当前缺口：Operator State 尚未实现（见 completion-roadmap.md Phase 0.3）。
```
Live code:
```
nop-stream-core/.../backend/memory/MemoryOperatorStateBackend.java:25  implements IOperatorStateBackend  (snapshotState + 4-way restoreState redistribution)
nop-stream-core/.../DefaultOperatorStateStore.java:15  implements IOperatorStateStore
nop-stream-core/.../checkpoint/TestE2EOperatorStateCheckpoint.java  (151 lines)
nop-stream-core/.../checkpoint/TestE2EOperatorStateRedistribution.java  (162 lines)
```
- **现状**: Operator State is implemented with full redistribution (NONE/UNION/BROADCAST/SPLIT_DISTRIBUTE), wired through `AbstractUdfStreamOperator.initializeState` and `ICheckpointedFunction`. `00-vision.md:99` itself contradicts the two design docs ("nop-stream 已落地 operator state 重分布").
- **风险**: Architects/users reading the design docs will believe source-offset/Kafka-partition/CDC-progress checkpointing is unimplemented and either refuse to use it, re-implement it incorrectly via keyed-state fake-key, or block a feature pending a non-existent Phase 0.3.
- **建议**: Update `state-management-design.md` §10.4/§11.9 and `core-design.md` §7.3 to reflect live state (`IOperatorStateBackend` 4 methods incl. 4-mode redistribution + `IOperatorStateStore.getListState` + `DefaultOperatorStateStore` + `MemoryOperatorStateBackend`), with E2E tests as evidence. Remove or mark the "Phase 0.3" backlog reference completed.
- **信心水平**: 确定
- **误报排除**: Both files are marked "Status: active"; `MemoryOperatorStateBackend` is in main scope and used by `RocksDBStateBackend.createOperatorStateBackend`.

---

## [P1] `IOperatorStateStore` SPI exposes 1 method while design specifies 3 (`getUnionListState`/`getBroadcastState` missing) — contract drift
*Justification: the documented public API contract actively disagrees with the SPI; users following the design will write `store.getUnionListState(...)` which does not compile — real contract drift.*

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/IOperatorStateStore.java:10-13`
- **Evidence**:
```java
// Design (state-management-design.md §10.1):
interface OperatorStateStore {
    <T> ListState<T> getListState(ListStateDescriptor<T> descriptor) throws Exception;
    <T> ListState<T> getUnionListState(ListStateDescriptor<T> descriptor) throws Exception;
    <T> ListState<T> getBroadcastState(ListStateDescriptor<T> descriptor) throws Exception;
}
// Live:
public interface IOperatorStateStore {
    <T> ListState<T> getListState(ListStateDescriptor<T> stateProperties);
}
```
- **现状**: Design says the user picks UNION/BROADCAST/SPLIT_DISTRIBUTE by calling `getUnionListState`/`getBroadcastState`/`getListState`. Actual SPI gives the user no choice at register time — mode is set externally by the operator/backend during `IOperatorStateBackend.restoreState(..., mode, ...)`. The two paradigms are not equivalent.
- **风险**: API mismatch between documented and actual contract; users cannot express the redistribution intent the design says they can. `00-vision.md` §七 #G36 line 99 even cites `RedistributionMode.UNION/BROADCAST/SPLIT_DISTRIBUTE` as user-facing.
- **建议**: Either align docs to actual SPI (mode chosen at restore time; user-facing store only has `getListState`), OR add `getUnionListState`/`getBroadcastState` to `IOperatorStateStore` with per-descriptor mode tagging.
- **信心水平**: 确定
- **误报排除**: The 3-method `OperatorStateStore` is repeated verbatim in two design docs (state-management §10.1, core-design §7.3) and underlies the §10.2 redistribution-mode table — contract-level disagreement, not naming drift.

---

## [P1] `KeyedStateStore` SPI exposes 5 methods while design says 2; `ListState` exposed despite design saying it must not be — contract drift
*Justification: explicit documented invariant ("只暴露 getState/getMapState", "ListState 不通过 KeyedStateStore 暴露") is violated by the live public SPI — contract drift on the core state API.*

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/KeyedStateStore.java:66-109`
- **Evidence**:
```
// state-management-design.md §5.1:
KeyedStateStore（IKeyedStateBackend 的父接口）只暴露 getState() 和 getMapState()。
// state-management-design.md §2.1:
ListState 不通过 KeyedStateStore 暴露给用户，只作为 InternalListState<K,N,T> 存在于 IInternalStateBackend 中…
```
```java
public interface KeyedStateStore {
    <T> ValueState<T>        getState(ValueStateDescriptor<T> stateProperties);
    <T> ListState<T>         getListState(ListStateDescriptor<T> stateProperties);          // design says absent
    <T> ReducingState<T>     getReducingState(ReducingStateDescriptor<T> stateProperties);  // design says absent
    <IN, ACC, OUT> AggregatingState<IN, OUT> getAggregatingState(...);                       // design says absent
    <UK, UV> MapState<UK, UV> getMapState(MapStateDescriptor<UK, UV> stateProperties);
}
```
- **现状**: User-facing SPI exposes all 5 accessors. `MemoryKeyedStateBackend` (`:167-250`) and `RocksDBKeyedStateBackend` (`:482-518`) implement all 5. Design says only `getState`/`getMapState` exist and `ListState` is internal-only.
- **风险**: Users reading design will believe list/reducing/aggregating state must go through the internal-only `IInternalStateBackend` SPI; the design's stated encapsulation boundary is unenforced.
- **建议**: Update `state-management-design.md` §2.1 and §5.1 to reflect actual surface (5 accessors), or narrow live `KeyedStateStore` to 2 methods. Given wide production usage, doc update is lower-risk.
- **信心水平**: 确定
- **误报排除**: Both design statements are normative ("只暴露", "不通过…暴露"); the live interface has the methods on the public SPI with full impls in two backends.

---

## [P1] `StateDescriptor` carries a `TypeSerializer` reference and exposes getter/setter, contradicting design invariant §6.1 — contract drift
*Justification: design invariant explicitly says "StateDescriptor 不携带 serializer 引用" and "IStreamSerializer 接口不向上暴露"; live code violates both in the public SPI, and the production SerDe path branches on `IStreamSerializer` — undocumented escape hatch with schema-checksum blind-spot risk.*

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/StateDescriptor.java:22,62-68`
- **Evidence**:
```
// state-management-design.md §6.1 (key invariant #2):
2. 不暴露序列化接口：StateDescriptor 不携带 serializer 引用。IStreamSerializer / TypeSerializer
   接口不向上暴露。算子代码只调用 getState() / putState()，不接触任何序列化 API。
```
```java
public class StateDescriptor<T> implements Serializable {
    private TypeSerializer<T> serializer;           // line 22
    ...
    public TypeSerializer<T> getSerializer() {       // line 62
        return serializer;
    }
    public void setSerializer(TypeSerializer<T> serializer) {  // line 66
        this.serializer = serializer;
    }
}
```
- **现状**: `MemoryStateSerDe.java:466,493,557,...,754` actively inspects descriptors via `instanceof IStreamSerializer` and calls `serialize/deserialize` on the per-descriptor, so the field is on the live serialization path. This is precisely the Flink Type-based serialization escape hatch the design says it rejects.
- **风险**: (a) Doc-reader developers won't know they can plug a custom `IStreamSerializer` to bypass JSON for hot state. (b) Doc-reader architects will believe schema compatibility is enforced purely via JSON checksum when in fact a custom serializer can produce binary blobs the checksum does not introspect.
- **建议**: Either update §6.1 to document the optional serializer field + `IStreamSerializer` SPI as an explicit opt-in escape hatch, or remove `serializer` from `StateDescriptor` and the `instanceof IStreamSerializer` branch in `MemoryStateSerDe` to enforce the invariant.
- **信心水平**: 确定
- **误报排除**: Public field/getter/setter on the user-facing `StateDescriptor`, in main scope, read by the production SerDe path.

---

## [P1] Missing `_module` marker file under `_vfs/nop/stream/` — beans.xml may be silently skipped by global IoC auto-discovery
*Justification: standard Nop platform convention requires a `_module` marker for the global IoC initializer to traverse into the module directory and load `beans/*.beans.xml`; other nop modules ship it, nop-stream does not — silent bean-wiring gap.*

- **File**: `nop-stream/nop-stream-runtime/src/main/resources/_vfs/nop/stream/` (no `_module` file present)
- **Evidence**:
```
$ ls nop-stream/nop-stream-runtime/src/main/resources/_vfs/nop/stream/
beans       <-- only this subdir, no _module file
$ find nop-stream -name "_module"   <-- zero hits anywhere in nop-stream
$ ls nop-auth/nop-auth-service/src/main/resources/_vfs/nop/auth/
_module     <-- standard nop pattern requires this marker
auth
beans
model
```
- **现状**: The two `beans.xml` files (`stream-control-rpc.beans.xml`, `stream-data-plane.beans.xml`) declare `ioc:default="true"` beans (`streamMessageService` → `LocalMessageService`, `streamDataPlaneWireCodec` → `IdentityWireCodec`). The platform convention requires `_vfs/<root>/<module>/_module` for global IoC discovery. `nop-auth`/`nop-file`/`nop-demo` all ship it; nop-stream does not. The current `TestStreamControlRpcBootstrap` test loads the file explicitly via `BeanContainerBuilder.addResource(...)`, bypassing discovery.
- **风险**: If a production deployment relies on auto-discovery of the `ioc:default` beans via the global IoC container, they will silently not be wired, and an unrelated `LocalMessageService` (or none) will be used.
- **建议**: Add an empty `_vfs/nop/stream/_module` file (mirroring `nop-auth`), then add a test building the *global* container (post-`INITIALIZER_PRIORITY_IOC`) asserting `container.containsBean("streamMessageService")`.
- **信心水平**: 很可能
- **误报排除**: The beans themselves are not `_`-prefixed; this finding is specifically about the missing `_module` discovery marker, verified against sibling modules' convention.

---

## [P1] TestTaskExecutorDaemonThreads tests its own inline lambda ThreadFactory, not the production ThreadFactory
*Justification: claims to test daemon-thread behavior but defines its own `ThreadFactory` inside the test body, fully disconnected from any production class — zero bug-catching power; false coverage on a real JVM-shutdown concern.*

- **File**: `nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/execution/TestTaskExecutorDaemonThreads.java:13-37`
- **Evidence**:
```java
@Test
void testDaemonThreadFactory() throws Exception {
    AtomicLong counter = new AtomicLong(0);
    ...
    ThreadFactory factory = r -> {
        Thread t = new Thread(r, "stream-task-executor-" + counter.getAndIncrement());
        t.setDaemon(true);   // <-- the test itself sets daemon, not the production code
        return t;
    };
    ExecutorService pool = Executors.newFixedThreadPool(1, factory);
    pool.submit(() -> { isDaemon[0] = Thread.currentThread().isDaemon(); ... });
    assertTrue(isDaemon[0], "Thread should be a daemon thread");
}
```
- **现状**: No production class is exercised. The test verifies `new Thread(...).setDaemon(true)` then `isDaemon()` returns true — a Java language tautology. If production `TaskExecutor`/`TaskManager` forgot `setDaemon(true)`, this test still passes.
- **风险**: False sense of coverage on task-executor thread leaks blocking JVM shutdown.
- **建议**: Delete this test (zero bug-catching value), or rewrite it to instantiate the production `TaskManager`/`TaskExecutor` and verify *its* threads are daemon.
- **信心水平**: 确定
- **误报排除**: Not an AutoTest snapshot; not a setup helper. Production `TaskManager` thread factory at `:127-136` is the real subject but is never referenced.

---

## [P1] TestSinkTransformation — 17 @Test methods all verify constructor-storage round-trip on a data-holder class
*Justification: 397 lines / 17 methods of pure P-1/P-2 anti-patterns on a transformation descriptor with no business logic; inflates coverage without any bug-catching power — maintenance cost with false confidence.*

- **File**: `nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/transformation/TestSinkTransformation.java:26-334` (same pattern: `TestOneInputTransformation.java`, 18 methods / 439 lines)
- **Evidence** (representative):
```java
@Test public void testExtendsPhysicalTransformation() {
    SinkTransformation<String> transformation = new SinkTransformation<>(...);
    assertTrue(transformation instanceof PhysicalTransformation);   // compiler-guaranteed
}
@Test public void testSerialization() {
    assertTrue(transformation instanceof java.io.Serializable);    // compiler-guaranteed
}
@Test public void testBasicConstruction() {
    assertEquals("TestSink", transformation.getName());   // pure set→get round-trip
    assertEquals(1, transformation.getParallelism());
}
```
- **现状**: Every method constructs a `SinkTransformation`, calls getters, asserts constructor args. No business logic exists to test.
- **风险**: Coverage noise; wastes maintenance time; false confidence.
- **建议**: Delete or `@Tag("low-value")`-mark and exclude (the project already self-tags 18 such files). If `SinkTransformation` ever gains a real invariant, add ONE test for that invariant.
- **信心水平**: 确定
- **误报排除**: Has `@Test` methods (not a setup helper). Not testing a custom equals/hashCode or fromValue factory. Canonical P-1 from the anti-pattern skill.

---

# P2 Findings (non-blocking polish — recorded, not plan-driving)

## [P2] Options native handle leaked in RocksDBKeyedStateBackend.openDB()
*Justification: real RocksDB native-object leak per backend construction; single-shot, but accumulates on test churn / HA restart loops.*

- **File**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/RocksDBKeyedStateBackend.java:196-201`
- **Evidence**:
```java
List<byte[]> existingCFs;
try {
    Options options = new Options(dbOptions, cfOptions);    // native RocksDB object
    existingCFs = RocksDB.listColumnFamilies(options, dbPath);
} catch (RocksDBException e) {
    existingCFs = Collections.emptyList();
}
// options never closed
```
- **现状**: `org.rocksdb.Options` extends `RocksObject` and holds a native handle that must be `.close()`'d. The sibling code in `RocksDBIncrementalRestore.java:150` correctly uses try-with-resources; `openDB()` does not.
- **风险**: Native memory leak per backend instance; survives until non-deterministic GC finalization.
- **建议**: Wrap in try-with-resources.
- **信心水平**: 确定
- **误报排除**: `org.rocksdb.Options extends RocksObject`; `close()` is required. The codebase shows the correct idiom in `RocksDBIncrementalRestore.java:150`.

---

## [P2] RocksDBKeyedStateBackend.close() is non-robust: an exception in any close() skips the rest
*Justification: leaks accumulate when close() throws; production jobs that fail during teardown can leak native handles.*

- **File**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/RocksDBKeyedStateBackend.java:836-859`
- **Evidence**:
```java
@Override
public void close() {
    if (cfHandles != null) {
        for (ColumnFamilyHandle handle : cfHandles.values()) {
            if (handle != null) {
                handle.close();              // <- if this throws, rest of cfHandles skipped
            }
        }
        cfHandles.clear();
    }
    if (defaultCF != null) {
        defaultCF.close();                   // <- if this throws, db/cfOptions/dbOptions skipped
    }
    if (db != null) {
        db.close();
        db = null;
    }
    if (cfOptions != null) { cfOptions.close(); }
    if (dbOptions != null) { dbOptions.close(); }
}
```
- **现状**: No try/finally around individual `close()` calls. RocksDB JNI `close()` can throw on double-close or native error; an early throw leaks the rest.
- **风险**: Native resource leak on close path; compounded because close() is often called during failure recovery.
- **建议**: Wrap each close() in its own try/finally (or suppressed-exception pattern); at minimum put `db.close()` in a finally after the cf handles loop.
- **信心水平**: 很可能
- **误报排除**: RocksDB JNI `RocksObject.close()` does throw on certain error paths.

---

## [P2] MemoryStateSerDe.serializeWithSerializer silently swallows serialization errors and falls back to the raw value
*Justification: silent checkpoint corruption when a custom IStreamSerializer fails; violates checkpoint-design §13.2 "禁止静默跳过".*

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java:763-772`
- **Evidence**:
```java
private <T> Object serializeWithSerializer(Object value, IStreamSerializer<T> serializer) {
    if (serializer == null || value == null) {
        return value;
    }
    try {
        return serializer.serialize((T) value);
    } catch (Exception e) {
        return value;            // <- silent: no log, no rethrow; raw value written
    }
}
```
- **现状**: When the user-supplied serializer throws, the exception is swallowed and the raw Java object is written. On restore the shape differs from write → `ClassCastException` or silent coercion.
- **风险**: Silent checkpoint corruption when a custom serializer is flaky.
- **建议**: At minimum LOG + rethrow as `StreamException(ERR_STREAM_STATE_ERROR)`; if best-effort is intended, document it and add a metric.
- **信心水平**: 很可能
- **误报排除**: Method is on the production snapshot path (all `snapshot*State` methods call it). catch-Exception with `return value;` and zero logging is the flagged anti-pattern.

---

## [P2] Checkpoint retention ignores `pipelineId` and applies `maxRetained` globally across pipelines
*Justification: in a multi-pipeline job one pipeline's retained checkpoints can be over-deleted because another pipeline occupies slots in the global window; contradicts design §9.2 namespace rule.*

- **File**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:969-986` (and `LocalFileCheckpointStorage.getAllCheckpoints` `:149-192`)
- **Evidence**:
```java
private void cleanupOldCheckpoints() {
    int maxRetained = config.getMaxRetainedCheckpoints();
    try {
        List<CompletedCheckpoint> allCheckpoints = checkpointStorage.getAllCheckpoints(jobId);  // jobId only
        if (allCheckpoints.size() > maxRetained) {
            for (int i = maxRetained; i < allCheckpoints.size(); i++) {
                CompletedCheckpoint old = allCheckpoints.get(i);
                checkpointStorage.deleteCheckpoint(jobId, old.getPipelineId(), old.getCheckpointId());
                ...
                gcSegmentsForCheckpoint(old.getCheckpointId());
            }
        }
    } catch (Exception e) {
        LOG.warn("Failed to cleanup old checkpoints", e);
    }
}
```
- **现状**: `getAllCheckpoints(jobId)` walks all pipelines under the job; cutoff applied globally. Design §9.2 explicitly states retention must be per-namespace (`jobId + pipelineId`), not cross-namespace.
- **风险**: Multi-pipeline job over-deletes another pipeline's checkpoints; GC of missing segments leaks SST files.
- **建议**: Filter the list by `old.getPipelineId().equals(pipelineId)` before applying cutoff (or add `getAllCheckpoints(jobId, pipelineId)`). Add a multi-pipeline test.
- **信心水平**: 很可能
- **误报排除**: `checkpoint-design.md` §9.2 explicitly forbids cross-pipeline counting.

---

## [P2] Incremental snapshot leaks the per-checkpoint native RocksDB checkpoint directory forever
*Justification: disk leak proportional to checkpoint churn; the native checkpoint dir is ~size of the live DB and nothing deletes it.*

- **File**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/incremental/RocksDBIncrementalSnapshotStrategy.java:60-112`; `RocksDBKeyedStateBackend.java:733-753`
- **Evidence**:
```java
public IncrementalSnapshotResult doSnapshot(RocksDB db, Path checkpointBaseDir, long checkpointId) {
    Path checkpointDir = checkpointBaseDir.resolve("cp-" + checkpointId);
    Path nativeDir = checkpointDir.resolve("native");
    Path nonSstDir = checkpointDir.resolve("non-sst");
    ...
    try (Checkpoint cp = Checkpoint.create(db)) {
        cp.createCheckpoint(nativeDir.toAbsolutePath().toString());
    }
    ...  // enumerates SST/non-SST, copies non-SST, returns handles pointing at nativeDir/<name>.sst
}
```
- **现状**: Each incremental snapshot creates `{checkpointBaseDir}/cp-{cpId}/native/` (RocksDB native checkpoint, multi-GB) and `non-sst/`. After coordinator `buildAndMaterializeSegments` copies bytes out, no code deletes `cp-{cpId}`. `gcSegmentsForCheckpoint` only discards by hash from the shared store, not the source native dir.
- **风险**: Disk grows monotonically; ~1 GB/min at 60s checkpoint interval on 1 GB state.
- **建议**: After successful materialization into the shared store, delete `cp-{cpId}/` (coordinator cleanup RPC or task-side sweep of dirs older than latest successful cpId). Add a disk-usage test.
- **信心水平**: 很可能
- **误报排除**: `grep "cp-"` cleanup finds nothing in `nop-stream-rocksdb/` or `nop-stream-runtime/`.

---

## [P2] RocksDBIncrementalRestore does not verify segment content hash on read — corrupted shared SST silently proceeds
*Justification: content-addressing is the whole correctness premise of the shared segment store; reading without SHA-256 verification defeats it (disk bit rot, buggy ISegmentStore, stale remote blob).*

- **File**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/incremental/RocksDBIncrementalRestore.java:86-115`
- **Evidence**:
```java
for (Map.Entry<String, String> e : nameMap.entrySet()) {
    String hash = e.getKey();
    String originalName = e.getValue();
    Path source = segmentStore.getSegmentPath(hash);
    if (!Files.exists(source)) {
        throw new IOException("Shared SST segment missing in store for hash " + hash ...);
    }
    Files.copy(source, targetDir.resolve(originalName), StandardCopyOption.REPLACE_EXISTING);
    // <-- SHA-256 of source never recomputed; hash not validated against content
}
```
- **现状**: Snapshot computes `SstFileChecksum.sha256Hex` (content-addressing); restore copies without recomputing/validating. Corrupt bytes flow into RocksDB; per-block checksum may catch some but not MANIFEST/OPTIONS, and failures surface as opaque RocksDBExceptions long after restore "succeeds".
- **风险**: Silent restore of corrupted state, defeating exactly-once.
- **建议**: After `Files.copy`, recompute `SstFileChecksum.sha256Hex(target)` and throw `ERR_STREAM_STATE_ERROR` if it differs from `hash`. Add a "corrupt segment" test.
- **信心水平**: 很可能
- **误报排除**: Design §9.1 treats segment identity as the integrity boundary; `SstFileChecksum` exists precisely for this verification.

---

## [P2] JdbcCheckpointStorage does not override `loadRetainedEpochManifests` — restart can delete segments referenced by retained non-latest checkpoints
*Justification: with JDBC storage, restart re-registers only the latest manifest's segments, then `cleanupOrphanSegments` deletes N-1..N-maxRetained+1 SST files; restore from any retained-but-non-latest checkpoint fails after a coordinator restart.*

- **File**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java` (no override); `CheckpointCoordinator.java:1316-1347` (`restoreSharedStateRegistry`), `:1354-1384` (`cleanupOrphanSegments`)
- **Evidence**:
```java
// ICheckpointStorage.java:62-69 — default returns at most the latest
default List<EpochManifest> loadRetainedEpochManifests(String jobId, String pipelineId, int count) {
    EpochManifest latest = loadLatestEpochManifest(jobId, pipelineId);
    if (latest == null) return Collections.emptyList();
    return Collections.singletonList(latest);
}
// CheckpointCoordinator.restoreSharedStateRegistry
List<EpochManifest> retained = checkpointStorage.loadRetainedEpochManifests(
        jobId, pipelineId, config.getMaxRetainedCheckpoints());   // <-- JDBC returns 1
Set<String> referenced = new HashSet<>();
for (EpochManifest manifest : retained) { /* add hashes */ }
cleanupOrphanSegments(referenced);   // <-- deletes any SST file whose hash is not in referenced
```
- **现状**: `JdbcCheckpointStorage` stores per-epoch rows but does not override the default, so only the latest manifest's segments are re-registered; `cleanupOrphanSegments` then deletes the rest. `LocalFileCheckpointStorage` DOES override (`:558-591`); JDBC does not.
- **风险**: Restore from any retained-but-non-latest checkpoint fails after JDBC-storage coordinator restart (`IOException("Shared SST segment missing in store for hash ...")`).
- **建议**: Override `loadRetainedEpochManifests` in `JdbcCheckpointStorage` querying `stream_epoch_manifest ORDER BY epoch_id DESC LIMIT ?`. Add a JDBC restart-with-multiple-retained-checkpoints test.
- **信心水平**: 很可能
- **误报排除**: Default method Javadoc explicitly states storages that keep per-epoch manifests should override; JDBC stores per-epoch rows but doesn't override.

---

## [P2] RocksDBMapState.contains() returns false on TTL-expired entry without deleting it (lazy-eviction inconsistency)
*Justification: minor eviction gap vs the documented "double cleanup" §12.4 invariant; entry leaks until next sweep; behaviorally inconsistent read path.*

- **File**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/RocksDBMapState.java:216-227`
- **Evidence**:
```java
@Override
public boolean contains(UK key) {
    try {
        byte[] baseKey = backend.buildStorageKeyForCurrent();
        if (ttl != null && ttl.isExpired(ByteBuffer.wrap(baseKey))) {
            return false;     // <- no deleteByPrefix, no removeTimestamp
        }
        return backend.getDb().get(cfHandle, buildFullKey(key)) != null;
    } catch (Exception e) {
        throw new StreamException("Failed to check MapState", e);
    }
}
```
- **现状**: All other read paths (`get`, `collectMap`) call `backend.deleteByPrefix(cfHandle, baseKey)` + `ttl.removeTimestamp(baseBuf)` on expiry. `contains()` only returns false. The expired entry's bytes + sidecar timestamp remain until a `get`/`put`/`collectMap`/sweep reaches the same key.
- **风险**: Storage bloat between sweeps; inconsistent with documented "double cleanup".
- **建议**: Mirror the eviction in `contains()`, or factor the lazy-evict block into a helper invoked from every read entry point.
- **信心水平**: 确定
- **误报排除**: Direct comparison with `get()` (`:154-156`) and `collectMap()` (`:232-235`) in the same file — both evict on expiry; `contains()` is the only read method that does not.

---

## [P2] CheckpointCoordinator.setTasksToAcknowledge is not synchronized — race with registerTask/unregisterTask
*Justification: mutates the volatile `tasksToAcknowledge` reference without holding the coordinator monitor while sibling mutators are synchronized; a task registered just before reassignment is lost — can complete a checkpoint that didn't snapshot all tasks.*

- **File**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:933-951`
- **Evidence**:
```java
public void setTasksToAcknowledge(Collection<TaskLocation> taskLocations) {  // NOT synchronized
    Set<TaskLocation> newSet = ConcurrentHashMap.newKeySet();
    if (taskLocations != null) for (TaskLocation loc : taskLocations) if (loc != null) newSet.add(loc);
    this.tasksToAcknowledge = newSet;                                          // replaces reference
}

public synchronized void registerTask(TaskLocation taskLocation) {
    this.tasksToAcknowledge.add(taskLocation);                                 // mutates current set
}
```
- **现状**: Thread A in `registerTask` reads set R1 then stalls; Thread B runs `setTasksToAcknowledge` installing R2; Thread A adds to R1 (no longer reachable). The registered task is lost from ACK tracking.
- **风险**: Missing ACK set tasks can cause `PendingCheckpoint.isFullyAcknowledged()` to return true prematurely, completing a checkpoint that didn't snapshot all tasks → exactly-once break.
- **建议**: Mark `setTasksToAcknowledge` as `synchronized`. Volatile is necessary but not sufficient because of the cross-method RMW.
- **信心水平**: 很可能
- **误报排除**: Cross-method RMW in `registerTask` makes this a true read-modify-write race that loses updates.

---

## [P2] JobCoordinator.assignTasks leaves registry and in-memory maps inconsistent when an RPC dispatch throws mid-iteration
*Justification: `clusterRegistry.assignTask(...)` is called before the RPC; a throw on subtask K leaves the registry with assignments but `taskAssignmentMap` missing the current vertex — next `detectFailures` is blind to those subtasks.*

- **File**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:536-565`
- **Evidence**:
```java
clusterRegistry.assignTask(jobId, vertexId, subtaskIndex, targetNodeId, attemptId, epoch, attemptNumber);

IStreamTaskRpcService rpc = taskRpcServices.get(targetNodeId);
if (rpc == null) {
    throw new StreamException(ERR_STREAM_INVALID_STATE).param(...);
}
if (remoteDeployMode) {
    rpc.deployTask(descriptor, epoch);          // may throw
} else {
    rpc.receiveAssignment(taskAssignment);      // may throw
}
vertexAssignments.add(taskAssignment);
locations.add(new TaskLocation(...));
...
taskAssignmentMap.put(vertexId, vertexAssignments);  // outside inner loop — never runs on throw
```
- **现状**: A throw on `rpc.deployTask`/`receiveAssignment` escapes. Side effects applied: cluster-registry assignments for current+prior subtasks. Not applied: `taskAssignmentMap.put` for current vertex, `allTaskLocations.addAll`, `setTasksToAcknowledge`.
- **风险**: Between throw and next recovery, monitoring is blind to those subtasks' node liveness; registry orphans accumulate.
- **建议**: Either per-subtask try/catch-and-continue (best-effort partial deploy with explicit failure surface), or document all-or-nothing and roll back cluster-registry assignments on throw.
- **信心水平**: 很可能
- **误报排除**: Divergence is transient (cleared by next `globalRecovery`) but not zero in the window.

---

## [P2] CheckpointCoordinator.validateIncrementalConfig throws bare JDK exceptions + non-English message
*Justification: violates AGENTS.md two-tier error-handling for module-internal code; uses `UnsupportedOperationException`/`IllegalStateException` with a Chinese "—" character instead of `StreamException`+`ErrorCode`+`.param(...)`.*

- **File**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:1292-1306`
- **Evidence**:
```java
public void validateIncrementalConfig() {
    if (incrementalCheckpointEnabled) {
        if (segmentStore == null) {
            throw new UnsupportedOperationException(
                    "incrementalCheckpointEnabled=true but segmentStore is null for job " + jobId
                            + " — incremental checkpoints require an ISegmentStore (no silent fallback)");
        }
        if (!config.isAsyncSnapshotEnabled()) {
            throw new IllegalStateException(
                    "incrementalCheckpointEnabled=true but asyncSnapshotEnabled=false for job " + jobId
                            + " — incremental checkpoints require async snapshot ...");
        }
    }
}
```
- **现状**: Both throws use bare JDK exception types with concatenated messages including a Chinese "—". No `ErrorCode`, no `.param(...)`. `NopStreamErrors.ERR_STREAM_CONFIG_ERROR`/`ERR_STREAM_INVALID_STATE` exist but are unused.
- **风险**: Callers cannot programmatically distinguish stream-config errors; violates "Error messages must be in English".
- **建议**: Replace with `throw new StreamException(ERR_STREAM_CONFIG_ERROR).param(ARG_CONFIG_KEY, "incrementalCheckpointEnabled").param(ARG_DETAIL, "...")`, strictly English.
- **信心水平**: 确定
- **误报排除**: Per AGENTS.md two-tier rule, even module-internal code should use the module exception class; bare JDK exceptions are explicitly wrong.

---

## [P2] LocalSourceCoordinator throws bare IllegalStateException from snapshot/restore paths (4 sites) + silent snapshot swallow at caller
*Justification: multi-call-site two-tier violation; the snapshot path's exception is caught and swallowed (warn-only) in CheckpointCoordinator — silent state loss on the checkpoint path (manifest written with missing `sourceEnumeratorSnapshots`).*

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/source/coordinator/LocalSourceCoordinator.java:127,150,267,274`; swallow at `CheckpointCoordinator.java:1208-1211`
- **Evidence**:
```java
// LocalSourceCoordinator.java
throw new IllegalStateException("Failed to snapshot source enumerator state for vertex " + vertexId, e);

// CheckpointCoordinator.java:1208-1211
try {
    SourceEnumeratorSerializedState snap = coord.snapshotState(checkpointId);
    ...
} catch (Exception e) {
    LOG.warn("Failed to snapshot source enumerator state for vertex {} (skipped)", vertexId, e);
}
```
- **现状**: Four sites throw bare `IllegalStateException`. The snapshot failure is logged at WARN and skipped, so the manifest is written with a missing `sourceEnumeratorSnapshots` entry. On restore the source re-discovers splits from scratch → at-least-once instead of exactly-once on next restore.
- **建议**: Wrap in `StreamException(ERR_STREAM_CHECKPOINT_ERROR, e).param(ARG_VERTEX_ID, vertexId)` at all four sites. At `snapshotSourceEnumerators`, change the catch policy to fail-loud (abort the checkpoint) or record an explicit partial-snapshot marker.
- **信心水平**: 确定
- **误报排除**: Design §2.6 says `sourceEnumeratorSnapshots` is load-bearing; silently dropping it is a real defect, not a deferred decision.

---

## [P2] InputGate.blockConsumption / resumeConsumption throw bare IllegalArgumentException
*Justification: minor two-tier violation in a public-core class reachable from many call paths; `NopStreamErrors.ERR_STREAM_INVALID_ARG` already exists.*

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:319-323`, `333-338`
- **Evidence**:
```java
public void blockConsumption(int channelIndex) {
    if (channelIndex < 0 || channelIndex >= channels.size()) {
        throw new IllegalArgumentException("Invalid channel index: " + channelIndex);
    }
    blockedChannels.add(channelIndex);
}
```
- **现状**: Bare JDK exception; no `.param(...)` diagnostics possible.
- **风险**: Low direct impact (internally-computed indices), but inconsistent with documented error model.
- **建议**: Replace with `throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "channelIndex").param(ARG_DETAIL, "out of range: " + channelIndex)`.
- **信心水平**: 确定
- **误报排除**: Methods are reachable from production code (alignment state machine calls them).

---

## [P2] StreamControlRpcServer.CorrelatingRpcService wraps non-Exception Throwable in bare RuntimeException
*Justification: error-path only, but a JVM `Error` (StackOverflow/OOM) gets hidden inside a faceless `RuntimeException`, losing original type — two-tier violation.*

- **File**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/rpc/StreamControlRpcServer.java:120-124`
- **Evidence**:
```java
return stage.handle((res, ex) -> {
    ApiResponse<?> out;
    if (ex != null) {
        out = DefaultRpcMessageAdapter.INSTANCE.getErrorResponse(
                ex instanceof Exception ? (Exception) ex : new RuntimeException(ex),  // bare RuntimeException
                request);
    } else {
        out = res;
    }
    ...
});
```
- **现状**: When the service throws a non-Exception `Throwable` (an `Error`), it is bundled into a fresh `RuntimeException`, masking JVM-level failures.
- **风险**: Operators can't see real Errors; opaque client stack traces.
- **建议**: Rethrow Errors directly (they should propagate), or wrap in `new StreamRuntimeException("RPC layer saw non-Exception Throwable", ex)`.
- **信心水平**: 很可能
- **误报排除**: The `instanceof Exception ?` branch shows the author considered the case; the bare RuntimeException is a deliberate (wrong) choice.

---

## [P2] JobCoordinator.failJob / stop do not cancel in-flight tasks on the TaskManagers
*Justification: on a failed job the coordinator marks FAILED locally but doesn't propagate cancel to TaskManagers; tasks keep running, consuming resources and emitting to the data plane after the job is declared FAILED.*

- **File**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:422-435` (`failJob`), `383-407` (`stop`)
- **Evidence**:
```java
public void failJob(Throwable cause) {
    if (jobStatus == JobStatus.FAILED) return;
    this.jobFailureCause = cause;
    this.jobStatus = JobStatus.FAILED;
    this.active = false;
    LOG.error("Job {} FAILED (cause={})", jobId, ...);
    try {
        failureDetector.shutdownNow();
    } catch (Exception e) {
        LOG.warn("Failed to shut down failure detector during failJob", e);
    }
    // No rpc.cancelTask(...) for the running tasks
}
```
- **现状**: `failJob` (from `globalRecovery` when `restartCount` exceeds cap) marks FAILED locally but does not propagate cancel. Tasks keep running; zombie emissions pollute downstream consumers. The standby coordinator after HA failover must rely on fencing-epoch rotation (correct but slow).
- **风险**: Resource leak on workers; zombie tasks emit to data plane after job FAILED.
- **建议**: In `failJob`, iterate `taskAssignmentMap` and call `rpc.cancelTask(...)` per task (best-effort, log per-node failure), mirroring `registerDistributedAbortHandler`.
- **信心水平**: 很可能
- **误报排除**: `stop()` has the same gap but is the embedded LOCAL path where executor `shutdownNow` handles interruption; `failJob` is the distributed terminal transition with no equivalent cleanup.

---

## [P2] IStateBackend Javadoc references non-existent RedisStateBackend; real production backend RocksDBStateBackend not mentioned
*Justification: Javadoc actively lies about which production backend exists; `{@link RedisStateBackend}` cannot resolve (Javadoc warning); users look for a class that isn't there.*

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/IStateBackend.java:23`
- **Evidence**:
```java
 * <ul>
 *     <li>{@link MemoryStateBackend} - 内存实现，用于测试</li>
 *     <li>{@link RedisStateBackend} - Redis 实现，用于生产环境</li>
 * </ul>
```
- **现状**: `RedisStateBackend` does not exist anywhere in the repo (`glob`/`grep "class RedisStateBackend"` → no matches). The actual production backend is `RocksDBStateBackend` in `nop-stream-rocksdb` (`RocksDBStateBackend.java:37 implements IStateBackend`), which the Javadoc does not mention.
- **风险**: (a) Javadoc tool warning + broken IDE navigation; (b) new developers chase a non-existent Redis backend; (c) contradicts `state-management-design.md` §5.3 (RocksDB = Stage 30+ production).
- **建议**: Replace the `<ul>` with the two real impls (`MemoryStateBackend`, `RocksDBStateBackend`); remove `{@link RedisStateBackend}`.
- **信心水平**: 确定
- **误报排除**: Verified absence of `RedisStateBackend` via two patterns across the entire repo.

---

## [P2] README says "五层执行管线" while architecture baseline says "六阶段"; both include RuntimeTopology which does not exist as code
*Justification: cross-doc inconsistency + still-listed vapor stage; reader cannot tell canonical stage count and will hunt for a non-existent class.*

- **File**: `nop-stream/README.md:5,7` vs `ai-dev/design/nop-stream/01-architecture-baseline.md:13,100`
- **Evidence**:
```
// README.md:5
经统一的五层执行管线（StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan → RuntimeTopology）编译执行。
// README.md:7
> 注: RuntimeTopology 处于规划阶段，LOCAL 模式当前通过 GraphExecutionPlan 直接执行…
// architecture-baseline.md:13
3. 六阶段执行管线：StreamModel → StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan → RuntimeTopology
```
`glob nop-stream/**/RuntimeTopology*` → No files. `grep RuntimeTopology` over nop-stream sources → only README matches.
- **现状**: README counts 5 (omitting StreamModel), architecture counts 6; both list RuntimeTopology despite README's footnote. The class does not exist; `GraphExecutionPlan` + `IStreamExecutionDispatcher` are the actual final-stage artifacts.
- **风险**: Count disagreement; advertised vapor stage; architecture-baseline.md §一 module table presents RuntimeTopology as live.
- **建议**: Pick one count (recommend 6). Either drop RuntimeTopology from the public pipeline until it exists, or label it "规划中/未实现" consistently everywhere.
- **信心水平**: 确定
- **误报排除**: README's footnote hedge doesn't fix the 5-vs-6 count disagreement; architecture-baseline.md §四 carries no hedge.

---

## [P2] AbstractUdfStreamOperator.initializeState passes a null operatorStateStore when no IStateBackend configured — silent NPE
*Justification: silent null returned through the user-facing ICheckpointedFunction SPI; standard user pattern NPEs deep inside initializeState instead of failing fast.*

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/AbstractUdfStreamOperator.java:111-134`
- **Evidence**:
```java
if (userFunction instanceof ICheckpointedFunction) {
    if (operatorStateBackend == null && stateBackend != null) {
        operatorStateBackend = stateBackend.createOperatorStateBackend();
    }
    IOperatorStateStore operatorStateStore = operatorStateBackend != null
            ? new DefaultOperatorStateStore(operatorStateBackend)
            : null;                                    // ← null when stateBackend == null
    FunctionInitializationContext fnCtx = new FunctionInitializationContext() {
        ...
        @Override
        public IOperatorStateStore getOperatorStateStore() {
            return operatorStateStore;                  // ← returns null
        }
    };
    ((ICheckpointedFunction) userFunction).initializeState(fnCtx);
}
```
- **现状**: If a UDF implements `ICheckpointedFunction` but the operator was constructed without `setStateBackend(...)`, `getOperatorStateStore()` returns null; the standard `context.getOperatorStateStore().getListState(...)` NPEs deep inside initializeState.
- **风险**: Opaque NPE far from the configuration mistake; violates vision §五 "no silent skip".
- **建议**: If `stateBackend == null` and the UDF is `ICheckpointedFunction`, fail fast with `ERR_STREAM_INVALID_STATE` ("ICheckpointedFunction requires an IStateBackend").
- **信心水平**: 很可能
- **误报排除**: `ICheckpointedFunction` Javadoc says it participates in checkpointing; a null store is a contract violation.

---

## [P2] TaskManager.updateFencingToken missing @Override (all sibling IStreamTaskRpcService methods have it)
*Justification: minor contract-style inconsistency; future interface signature drift on this method won't be caught at compile time.*

- **File**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:577`
- **Evidence**:
```java
// All four sibling overrides have @Override:
:261  @Override public void receiveAssignment(TaskAssignment assignment) {...}
:366  @Override public void deployTask(TaskDeploymentDescriptor descriptor, long fencingEpoch) {...}
:507  @Override public void triggerCheckpoint(CheckpointBarrier barrier, long fencingEpoch) {...}
:527  @Override public void cancelTask(String jobId, String vertexId, int subtaskIndex) {...}
// updateFencingToken does not:
:577  public void updateFencingToken(long fencingEpoch) { ... }   // ← no @Override
```
- **现状**: Correctly overrides the SPI method but is not annotated.
- **风险**: If `IStreamTaskRpcService.updateFencingToken` signature changes, the override silently breaks; reflective RPC dispatch throws `ERR_RPC_NO_HANDLER`. Sibling methods would catch this at compile time.
- **建议**: Add `@Override` at line 577.
- **信心水平**: 确定
- **误报排除**: The file's own sibling overrides show the established convention is `@Override` on every interface method; this is a single-method deviation.

---

## [P2] TestWatermarkStateRobustness — class name lies about what it tests (P-6)
*Justification: class name implies watermark-state robustness but contents test Quantifier.Times hashConsistency and DeweyNumber inequality; misleading maintenance signal.*

- **File**: `nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/TestWatermarkStateRobustness.java:10-42`
- **Evidence**:
```java
class TestWatermarkStateRobustness {
    @Test void testQuantifierTimesHashCodeConsistencyWithDuration() { ... }    // no watermark here
    @Test void testQuantifierTimesHashCodeNullDuration() { ... }              // no watermark here
    @Test void testDeweyNumberBasedComparisonNotUsingHashCode() { ... }       // no watermark here
}
```
- **现状**: Test class/method names are about Watermark state robustness; actual contents are unrelated.
- **风险**: Reader looking for "where watermark robustness is tested" is misled; future watermark regressions won't be found here.
- **建议**: Rename to `TestQuantifierTimesAndDeweyNumberEquivalence`, or remove if `TestDeweyNumber.java` already covers it.
- **信心水平**: 确定
- **误报排除**: The class explicitly tests a different domain than its name.

---

## [P2] TestProcessingGuarantee + TestLocalExecutionBarrierAlignment — duplicate enum-metadata assertions
*Justification: P-2 anti-pattern (testing metadata) duplicated across two files; 8 methods but effectively zero-value.*

- **File**: `nop-stream-core/.../checkpoint/TestProcessingGuarantee.java:9-32` AND `nop-stream-core/.../execution/TestLocalExecutionBarrierAlignment.java:18-52`
- **Evidence**:
```java
// TestProcessingGuarantee
assertTrue(ProcessingGuarantee.STRICT_EXACTLY_ONCE.isBarrierAlignment());
// TestLocalExecutionBarrierAlignment
CheckpointConfig config = CheckpointConfig.builder()
        .processingGuarantee(ProcessingGuarantee.STRICT_EXACTLY_ONCE).build();
assertTrue(config.getProcessingGuarantee().isBarrierAlignment(), ...);   // same assertion
```
- **现状**: Both files assert enum constants return the booleans they're defined with; just mirror the enum definition.
- **建议**: Keep ONE file with these as embedded sanity inside a real behavior test; delete the duplicate.
- **信心水平**: 确定
- **误报排除**: Not testing a `fromValue()` factory — pure constant checks.

---

## [P2] TestFlowControl — asserts hardcoded constant values from production defaults
*Justification: P-4 anti-pattern (test tightly coupled to impl constants); changing the production default requires lockstep test change with no independent verification.*

- **File**: `nop-stream-core/.../execution/flow/TestFlowControl.java:9-25`
- **Evidence**:
```java
@Test void testDefaultLocalBudget() {
    MemoryBudget budget = MemoryBudget.defaultLocalBudget(1000);
    assertEquals(500, budget.getAllocation("stateBackend"));        // <-- 50% magic
    assertEquals(300, budget.getAllocation("edgeQueues"));          // <-- 30% magic
    assertEquals(200, budget.getAllocation("networkBuffers"));      // <-- 20% magic
}
```
- **现状**: Test mirrors `EdgeConfig.QUEUE_CAPACITY_DEFAULT = 1024` and the 50/30/20 split.
- **建议**: Delete, or assert invariants (`sum == totalBudget`, `each > 0`).
- **信心水平**: 很可能
- **误报排除**: Constants are not a documented public API contract.

---

## [P2] TestCountTrigger / TestMapStateDescriptor / TestE2EStorageTypeRouting — minor low-value test nits
*Justification: P-1/P-2/P-5 anti-patterns (metadata boolean, constructor round-trip, assertNotNull-only); false-coverage but low blast radius.*

- **Files**:
  - `nop-stream-core/.../windowing/triggers/TestCountTrigger.java:10-14` — single `assertFalse(trigger.canMerge())` metadata test
  - `nop-stream-core/.../common/state/TestMapStateDescriptor.java:9-21` — constructor round-trip
  - `nop-stream-runtime/.../execution/TestE2EStorageTypeRouting.java:38-51` — `assertNotNull`-only on real storage creation
- **Evidence** (TestE2EStorageTypeRouting):
```java
@Test void testLocalStorageReturnsStorage() {
    ...
    assertNotNull(GraphModelCheckpointExecutor.createStorage(config));   // only non-null
}
```
- **现状**: Low-value assertions that cannot catch real bugs.
- **建议**: Delete or strengthen: `assertInstanceOf(LocalFileCheckpointStorage.class, storage)` + verify configured path; for CountTrigger test actual fire logic; for MapStateDescriptor test the backend-produced MapState.
- **信心水平**: 确定
- **误报排除**: These are representative of the project's already-self-tagged `@Tag("low-value")` tail (18 files); listed for completeness.

---

# Positive findings — areas verified clean (no defect)

To document audit coverage and avoid false-negatives, the following areas were examined and found compliant:

- **`ICheckpointStorage` / `ISegmentStore`** — default `exists`/`loadRetainedEpochManifests` are sensible shims; LocalFile impl uses RW-lock + atomic move + temp cleanup correctly.
- **`IInternalStateBackend` / `IKeyedStateBackend`** — two `getInternalAppendingState` overloads match design §5.1.
- **`IStreamSerializer` / `TypeSerializer`** — intentional Flink-derived types with `serialVersionUID`.
- **`IStreamExecutionDispatcher`** (3-method minimal SPI), **`IBufferPool`+`BufferPool`** (fair semaphore, exhaustion=block), **`IMaterializationPoint`+`InMemoryMaterializationPoint`** (match failover-design §五.4) — all match design.
- **`IDataPlaneWireCodec` + 4 codec impls** — match D72 design.
- **`ICepPatternGroupModel` + 2 impls** — both `_CepPatternModel` and `_CepPatternGroupModel` provide all required getters.
- **`IterativeCondition`** — Flink-derived, has `serialVersionUID`.
- **Serializable DTOs across RPC boundary** (`TaskAssignment`, `TaskDeploymentDescriptor`, `OperatorSnapshotResult`, `TaskStatusReport`, `TaskProgress`, `JobStatusResponse`, `CheckpointAckMessage`, `StreamMessageEnvelope`, `CompletedCheckpoint`, `EpochManifest`, `TaskLocation`) — all carry `serialVersionUID = 1L` and Serializable fields.
- **`PendingCheckpoint`** — clean synchronized state machine.
- **`JdbcLeaderElector`** — correct optimistic-concurrency lease semantics with stop-guard.
- **`LocalFileCheckpointStorage`** — correct RW-lock + atomic move + temp cleanup; overrides `loadRetainedEpochManifests`.
- **`MailboxExecutor` / `TaskMailbox`** — correct lock-based synchronization.
- **`ResultPartition`** — correct blocking queue + buffer-pool permit accounting.
- **`GraphExecutionPlan`** — build is single-threaded; producer/consumer threads see safe publication via final fields.
- **`SubtaskTask`** — correct AtomicReference state machine.
- **`JdbcClusterRegistry` / `JdbcCheckpointStorage`** — transactions are short, no remote calls inside txn scope (except the duplicate-key-upsert issue flagged above).
- **`MiniStreamCluster`** — Process destroy with graceful→forcible escalation is correct.
- **Fencing-token unification** — single `long` comparison, no boxed-Long `==` issues.
- **Load-bearing correctness tests** (sampled: checkpoint restore exactly-once, fencing-token rejection, RocksDB snapshot/restore, incremental checkpoint GC, schema migration, key-group rescale, CEP state restore, JDBC 2PC, Debezium kill/recover) — have *real* tests with state mutation and externally-observable outcome assertions, often with explicit anti-hollow commentary.
- **Delta contract** (`_delta/default/` with `x:extends="super"`) — correctly implemented and tested with delta-unique assertions (mutation: if delta merge silently skipped, tests fail).
- **`_gen/*.java`** — all carry canonical generation headers; git history shows only regeneration commits — no hand-edits.
- **beans.xml FQCNs** — resolve to real classes; no `nop*` prefix misuse on business beans.

---

# Summary & remediation guidance

**Headline (P0)**: Two blocking items — (1) the unsynchronized `JobCoordinator` recovery path is a genuine concurrency correctness bug that can corrupt HA state under concurrent failure reports; (2) the vacuous daemon-thread test gives false assurance on a real JVM-shutdown concern.

**Highest-value P1 cluster (production correctness)**: the incremental-restore missing fail-fast (silent state corruption), the failed-checkpoint ref-count/disk leak, the PostgreSQL upsert incompatibility, the TaskManager permit leak, the InputGate cross-thread mutation, and the SupervisionLoop zombie-task fall-through. These six together threaten exactly-once and resource stability under failure/recovery — the scenario nop-stream-production exists to harden.

**P1 contract-drift cluster**: four SPI/doc disagreements (Operator State "尚未实现", IOperatorStateStore 1-vs-3 methods, KeyedStateStore 5-vs-2 methods, StateDescriptor carrying TypeSerializer) — the design docs are the canonical reference and are actively misleading. Reconcile docs↔code.

**Remediation triage** (downstream plan-drafting should target P0+P1; P2 is backlog):
1. P0 #1 (JobCoordinator race) + P1 InputGate race + P1 setTasksToAcknowledge race + P1 assignTasks partial-state — group as one "coordinator concurrency hardening" workstream (single monitor + mailbox-delivered aborts + fail-loud partial-deploy).
2. P1 incremental-checkpoint cluster (fail-fast, ref-count leak, native-dir disk leak, content-hash verify, JDBC retained-manifest override) — group as one "incremental checkpoint robustness" workstream.
3. P1 PostgreSQL upsert — standalone (dialect-aware upsert + integration test).
4. P1 TaskManager permit leak + P1 SupervisionLoop zombie — standalone fixes with regression tests.
5. P1 doc-drift cluster — single doc-reconciliation pass over `state-management-design.md` / `core-design.md` / `00-vision.md` / README / `IStateBackend` Javadoc.
6. P1 `_module` marker — trivial config fix + discovery test.
7. P1 hollow tests (TestTaskManagerDaemon, TestTaskExecutorDaemonThreads, TestSinkTransformation) — fix or delete + tag.

**Audit blind spots self-assessment**: (a) Did not run `./mvnw test -pl nop-stream -am` (mission test command) — behavioral test pass/fail not independently confirmed; several concurrency findings are mechanistic (proven by code structure) but not reproduction-confirmed. (b) Did not exhaustively read all 448 test files — sampled ~30 representatively; additional hollow tests likely exist in the long tail. (c) Kafka/Pulsar/H2-multiproc `@EnabledIfSystemProperty`-gated E2E tests were inspected structurally but not executed. (d) Connector modules (Debezium/JDBC/batch) received lighter coverage than core/runtime/rocksdb.

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
