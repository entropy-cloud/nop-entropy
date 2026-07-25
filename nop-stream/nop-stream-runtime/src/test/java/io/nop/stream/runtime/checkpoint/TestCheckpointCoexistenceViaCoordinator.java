package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.checkpoint.storage.CheckpointStorageException;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2 focused tests for Plan {@code 2026-07-25-2300-1-checkpoint-concurrency}.
 *
 * <p>All tests go through the **real coordinator path** ({@code tryTriggerPendingCheckpoint} /
 * {@code acknowledgeTask} / {@code completePendingCheckpoint} / {@code abortPendingCheckpoint}).
 * They explicitly do NOT use the hollow pattern {@code new PendingCheckpoint(...) + forceComplete()}
 * (which bypasses the coordinator's pendingCheckpoints map, numPending bookkeeping,
 * scheduleTimeout, cleanupOldCheckpoints — see {@code TestCheckpointConcurrencySafety}).
 *
 * <p>What is verified:
 * <ul>
 *   <li>≥2 pending checkpoints coexist in the coordinator's pendingCheckpoints map;</li>
 *   <li>each pending independently reaches the COMPLETED state via its own ACK path;</li>
 *   <li>taskStates do not cross-contaminate (cp1's state ∉ cp2, and vice versa);</li>
 *   <li>numPending accurately returns to 0;</li>
 *   <li>abort of one pending does not affect the other;</li>
 *   <li>timeout of one pending does not abort the other;</li>
 *   <li>scheduleTimeout / cleanupOldCheckpoints act per-checkpointId, not globally.</li>
 * </ul>
 */
class TestCheckpointCoexistenceViaCoordinator {

    private static final TaskLocation LOC_1 = new TaskLocation("j", "p", "v1", 1);
    private static final TaskLocation LOC_2 = new TaskLocation("j", "p", "v2", 2);

    @TempDir
    Path tempDir;

    private CheckpointCoordinator coordinator;
    private LocalFileCheckpointStorage storage;
    private CheckpointIDCounter idCounter;

    @BeforeEach
    void setUp() {
        storage = new LocalFileCheckpointStorage(tempDir.toString());
        idCounter = new CheckpointIDCounter();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(60_000L)
                .checkpointTimeout(5_000L)
                // Plan 2026-07-25-2300-1 Phase 1: minPause is now enforced — disable here
                // so consecutive triggers can build the coexistence scenario deterministically.
                .minPause(0L)
                .maxConcurrentCheckpoints(4)
                .maxRetainedCheckpoints(5)
                .asyncSnapshotEnabled(false)
                .build();
        coordinator = new CheckpointCoordinator("j", "p", idCounter, storage, config);
        coordinator.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
    }

    @AfterEach
    void tearDown() {
        if (coordinator != null) {
            coordinator.shutdown();
        }
    }

    private static TaskStateSnapshot stateFor(long cpId, String tag) {
        return TaskStateSnapshot.builder(LOC_1)
                .checkpointId(cpId)
                .putOperatorState("tag", tag)
                .putOperatorState("cpId", cpId)
                .build();
    }

    // ---- coexistence + independent complete ----

    /**
     * Trigger cp1 (no ACK) → trigger cp2 → both real in pendingCheckpoints map → ACK each
     * fully → each completes via completePendingCheckpoint independently → taskStates do
     * not cross-contaminate → numPending returns to 0.
     */
    @Test
    void twoPendingCoexistAndCompleteIndependentlyViaCoordinator() throws Exception {
        // cp1: trigger but do NOT ack — keeps cp1 in pendingCheckpoints map.
        PendingCheckpoint cp1 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(cp1, "cp1 trigger should succeed");
        long cp1Id = cp1.getCheckpointId();

        // cp2: trigger while cp1 is still in flight — both must coexist.
        PendingCheckpoint cp2 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(cp2, "cp2 trigger should succeed while cp1 is in flight (maxConcurrent=4)");
        long cp2Id = cp2.getCheckpointId();
        assertNotEquals(cp1Id, cp2Id, "Checkpoint IDs must differ");
        assertEquals(2, coordinator.getNumberOfPendingCheckpoints(),
                "Both pending checkpoints must coexist in the coordinator map");
        // Sanity: both pending objects are distinct entries in the map.
        assertSame(cp1, coordinator.getPendingCheckpoint(cp1Id));
        assertSame(cp2, coordinator.getPendingCheckpoint(cp2Id));

        // ACK cp1 fully with cp1-specific state.
        String cp1Tag = "cp1-data";
        coordinator.acknowledgeTask(LOC_1, cp1Id, stateFor(cp1Id, cp1Tag));
        coordinator.acknowledgeTask(LOC_2, cp1Id, stateFor(cp1Id, cp1Tag));
        // Sync fallback path: future completes inline.
        CompletedCheckpoint completed1 = (CompletedCheckpoint) cp1.getCompletableFuture().get();
        assertEquals(cp1Id, completed1.getCheckpointId());
        CompletedCheckpoint latestAfterCp1 = coordinator.getLatestCheckpoint();
        assertNotNull(latestAfterCp1);
        assertEquals(cp1Id, latestAfterCp1.getCheckpointId(),
                "After cp1 completes, latest checkpoint must be cp1");

        // cp2 is still in flight — cp1 completion must not have touched cp2.
        assertEquals(1, coordinator.getNumberOfPendingCheckpoints(),
                "Only cp2 should remain pending after cp1 completes");
        assertSame(cp2, coordinator.getPendingCheckpoint(cp2Id));

        // ACK cp2 fully with cp2-specific state (different tag).
        String cp2Tag = "cp2-data";
        coordinator.acknowledgeTask(LOC_1, cp2Id, stateFor(cp2Id, cp2Tag));
        coordinator.acknowledgeTask(LOC_2, cp2Id, stateFor(cp2Id, cp2Tag));
        CompletedCheckpoint completed2 = (CompletedCheckpoint) cp2.getCompletableFuture().get();
        assertEquals(cp2Id, completed2.getCheckpointId());
        CompletedCheckpoint latestAfterCp2 = coordinator.getLatestCheckpoint();
        assertEquals(cp2Id, latestAfterCp2.getCheckpointId(),
                "After cp2 completes, latest checkpoint must be cp2");

        // numPending accurately returns to 0.
        assertEquals(0, coordinator.getNumberOfPendingCheckpoints(),
                "numPending must be 0 after both checkpoints complete");

        // taskStates cross-contamination check: load both from storage and verify each has
        // only its own tag, never the other's. This proves the per-pending taskStates map
        // does not leak across coexisting pending checkpoints.
        java.util.List<CompletedCheckpoint> stored = storage.getAllCheckpoints("j");
        CompletedCheckpoint storedCp1 = stored.stream()
                .filter(c -> c.getCheckpointId() == cp1Id).findFirst().orElseThrow();
        CompletedCheckpoint storedCp2 = stored.stream()
                .filter(c -> c.getCheckpointId() == cp2Id).findFirst().orElseThrow();

        Object cp1TagInStorage = storedCp1.getTaskStates().get(LOC_1).getOperatorState("tag");
        Object cp2TagInStorage = storedCp2.getTaskStates().get(LOC_1).getOperatorState("tag");
        assertEquals(cp1Tag, cp1TagInStorage, "cp1's stored state must carry cp1's tag");
        assertEquals(cp2Tag, cp2TagInStorage, "cp2's stored state must carry cp2's tag");
        assertNotEquals(cp1TagInStorage, cp2TagInStorage,
                "taskStates MUST NOT cross-contaminate between coexisting pending checkpoints");
    }

    /**
     * End-to-end through the coordinator path: trigger → ACK → completePendingCheckpoint
     * writes storage → getLatestCheckpoint reflects the durable result. Repeated for two
     * coexisting pending checkpoints to prove the path is independently traversable.
     */
    @Test
    void endToEndEachPendingIndependentlyDurable() throws Exception {
        PendingCheckpoint cp1 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        PendingCheckpoint cp2 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(cp1);
        assertNotNull(cp2);

        // End-to-end for cp1.
        coordinator.acknowledgeTask(LOC_1, cp1.getCheckpointId(), stateFor(cp1.getCheckpointId(), "e2e-1"));
        coordinator.acknowledgeTask(LOC_2, cp1.getCheckpointId(), stateFor(cp1.getCheckpointId(), "e2e-1"));
        cp1.getCompletableFuture().get();
        // Storage must contain cp1 and getLatestCheckpoint must return cp1.
        assertTrue(storage.exists("j", "p", cp1.getCheckpointId()),
                "cp1 must be durable in storage after end-to-end completion");
        assertEquals(cp1.getCheckpointId(), coordinator.getLatestCheckpoint().getCheckpointId());

        // End-to-end for cp2.
        coordinator.acknowledgeTask(LOC_1, cp2.getCheckpointId(), stateFor(cp2.getCheckpointId(), "e2e-2"));
        coordinator.acknowledgeTask(LOC_2, cp2.getCheckpointId(), stateFor(cp2.getCheckpointId(), "e2e-2"));
        cp2.getCompletableFuture().get();
        assertTrue(storage.exists("j", "p", cp2.getCheckpointId()),
                "cp2 must be durable in storage after end-to-end completion");
        assertEquals(cp2.getCheckpointId(), coordinator.getLatestCheckpoint().getCheckpointId());
    }

    // ---- abort independence ----

    /**
     * Trigger cp1 + cp2 (both in flight) → abort cp1 → cp2 must be unaffected, still
     * ACK-able to completion → numPending returns to 0.
     */
    @Test
    void abortOfOnePendingDoesNotAffectTheOther() throws Exception {
        PendingCheckpoint cp1 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        PendingCheckpoint cp2 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(cp1);
        assertNotNull(cp2);
        assertEquals(2, coordinator.getNumberOfPendingCheckpoints());

        // Abort cp1 via the coordinator path.
        coordinator.abortPendingCheckpoint(cp1, "test abort cp1");

        // cp1 must be aborted and removed; numPending decremented.
        assertEquals(PendingCheckpoint.Status.ABORTED, cp1.getStatus().get());
        assertNull(coordinator.getPendingCheckpoint(cp1.getCheckpointId()));
        assertEquals(1, coordinator.getNumberOfPendingCheckpoints(),
                "After aborting cp1, only cp2 should remain pending");

        // cp2 must still be present and completable through the coordinator path.
        assertSame(cp2, coordinator.getPendingCheckpoint(cp2.getCheckpointId()));
        assertEquals(PendingCheckpoint.Status.RUNNING, cp2.getStatus().get(),
                "cp2 must remain RUNNING — abort of cp1 must not propagate");

        coordinator.acknowledgeTask(LOC_1, cp2.getCheckpointId(), stateFor(cp2.getCheckpointId(), "cp2-after-abort"));
        coordinator.acknowledgeTask(LOC_2, cp2.getCheckpointId(), stateFor(cp2.getCheckpointId(), "cp2-after-abort"));
        CompletedCheckpoint completed2 = (CompletedCheckpoint) cp2.getCompletableFuture().get();
        assertEquals(cp2.getCheckpointId(), completed2.getCheckpointId());

        assertEquals(0, coordinator.getNumberOfPendingCheckpoints(),
                "numPending must be 0 after cp2 completes (cp1 aborted, cp2 completed)");
    }

    // ---- timeout independence ----

    /**
     * cp1's scheduleTimeout fires and aborts only cp1; cp2's scheduleTimeout must not
     * cascade-fire prematurely. Verified by staggering the triggers so cp1's timeout
     * elapses while cp2's clock has time remaining, then ACKing cp2 before its timeout.
     */
    @Test
    void timeoutOfOnePendingDoesNotAbortTheOther() throws Exception {
        // Build a coordinator with a short timeout so the test runs fast.
        CheckpointConfig shortTimeoutConfig = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(60_000L)
                .checkpointTimeout(300L)
                .minPause(0L)
                .maxConcurrentCheckpoints(4)
                .maxRetainedCheckpoints(5)
                .asyncSnapshotEnabled(false)
                .build();
        try (CheckpointCoordinatorWithShutdown shortCoord = new CheckpointCoordinatorWithShutdown(
                "j-timeout", "p", new CheckpointIDCounter(), storage, shortTimeoutConfig)) {
            CheckpointCoordinator c = shortCoord.coordinator;
            c.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));

            // Trigger cp1 at t=0; its timeout (300ms) fires at t=300.
            PendingCheckpoint cp1 = c.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            assertNotNull(cp1);

            // Stagger: wait 150ms so cp1 has 150ms remaining but cp2 will have full 300ms.
            Thread.sleep(150);

            // Trigger cp2 at t=150; its timeout (300ms) fires at t=450.
            PendingCheckpoint cp2 = c.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            assertNotNull(cp2);

            // Wait until t≈325 — cp1's timeout (t=300) has fired and aborted cp1, but cp2's
            // timeout (t=450) has not yet fired.
            Thread.sleep(200);

            assertEquals(PendingCheckpoint.Status.ABORTED, cp1.getStatus().get(),
                    "cp1 must be aborted by its scheduleTimeout at t=300");
            assertNull(c.getPendingCheckpoint(cp1.getCheckpointId()),
                    "cp1 must be removed from pendingCheckpoints after timeout-abort");
            assertEquals(PendingCheckpoint.Status.RUNNING, cp2.getStatus().get(),
                    "cp2 must NOT be aborted by cp1's timeout — scheduleTimeout is per-pending");
            assertSame(cp2, c.getPendingCheckpoint(cp2.getCheckpointId()),
                    "cp2 must still be in pendingCheckpoints");

            // ACK cp2 fully before its own timeout (t=450) — proves cp2 is still independently
            // completable after cp1 was timed out.
            coordinator = c; // so tearDown can shut it down
            c.acknowledgeTask(LOC_1, cp2.getCheckpointId(), stateFor(cp2.getCheckpointId(), "cp2-survives-timeout"));
            c.acknowledgeTask(LOC_2, cp2.getCheckpointId(), stateFor(cp2.getCheckpointId(), "cp2-survives-timeout"));
            CompletedCheckpoint completed2 = (CompletedCheckpoint) cp2.getCompletableFuture().get();
            assertEquals(cp2.getCheckpointId(), completed2.getCheckpointId());

            assertEquals(0, c.getNumberOfPendingCheckpoints(),
                    "numPending must be 0 after cp1 timeout-aborted + cp2 completed");
        }
    }

    // ---- failure propagation (no silent skip) ----

    /**
     * Focused storage-failure propagation check: when two pending coexist and cp1's storage
     * write fails, cp1 must enter FAILED state (propagated via {@code onCompletePersistFailure},
     * NOT silently swallowed by a try/catch in completePendingCheckpoint), while cp2 remains
     * RUNNING and independently completable.
     */
    @Test
    void storageFailureOnOnePendingPropagatesAndLeavesOtherUnaffected() throws Exception {
        // Track storeCheckPoint calls so we can fail only for cp1.
        java.util.concurrent.atomic.AtomicLong callCount = new java.util.concurrent.atomic.AtomicLong();
        // cp1 is the FIRST storeCheckPoint call. We fail only that call; subsequent calls
        // (for cp2) succeed. This isolates "cp1 storage fails while cp2 succeeds".
        long failOnCall = 1L;
        ICheckpointStorage selectiveFailing = new ICheckpointStorage() {
            private final java.util.concurrent.ConcurrentHashMap<String, CompletedCheckpoint> store = new java.util.concurrent.ConcurrentHashMap<>();

            @Override public String getName() { return "SelectiveFailingStorage"; }
            @Override
            public String storeCheckPoint(CompletedCheckpoint checkpoint) throws CheckpointStorageException {
                long n = callCount.incrementAndGet();
                if (n == failOnCall) {
                    throw new StreamException("Simulated storage failure for cp " + checkpoint.getCheckpointId());
                }
                store.put(checkpoint.getJobId() + ":" + checkpoint.getCheckpointId(), checkpoint);
                return "stored";
            }
            @Override public CompletedCheckpoint getLatestCheckpoint(String jobId, String pipelineId) throws CheckpointStorageException {
                return store.values().stream()
                        .max(java.util.Comparator.comparingLong(CompletedCheckpoint::getCheckpointId))
                        .orElse(null);
            }
            @Override public java.util.List<CompletedCheckpoint> getAllCheckpoints(String jobId) throws CheckpointStorageException {
                return new java.util.ArrayList<>(store.values());
            }
            @Override public java.util.List<CompletedCheckpoint> getLatestCheckpoints(String jobId, int count) throws CheckpointStorageException { return getAllCheckpoints(jobId); }
            @Override public void deleteCheckpoint(String jobId, String pipelineId, long checkpointId) throws CheckpointStorageException {
                store.remove(jobId + ":" + checkpointId);
            }
            @Override public void deleteAllCheckpoints(String jobId) throws CheckpointStorageException { store.clear(); }
            @Override public int getCheckpointCount(String jobId) throws CheckpointStorageException { return store.size(); }
            @Override public boolean exists(String jobId, String pipelineId, long checkpointId) throws CheckpointStorageException { return store.containsKey(jobId + ":" + checkpointId); }
            @Override public String storeSavepoint(CompletedCheckpoint checkpoint, String targetPath) throws CheckpointStorageException { return targetPath; }
            @Override public CompletedCheckpoint loadSavepoint(String savepointPath) throws CheckpointStorageException { return null; }
            @Override public SavepointMetadata loadSavepointMetadata(String savepointPath) throws CheckpointStorageException { return null; }
            @Override public void storeEpochManifest(String jobId, String pipelineId, EpochManifest manifest) throws CheckpointStorageException {}
            @Override public EpochManifest loadLatestEpochManifest(String jobId, String pipelineId) throws CheckpointStorageException { return null; }
        };

        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(60_000L)
                .checkpointTimeout(30_000L)
                .minPause(0L)
                .maxConcurrentCheckpoints(4)
                .maxRetainedCheckpoints(5)
                .asyncSnapshotEnabled(false)
                .build();
        CheckpointCoordinator coord = new CheckpointCoordinator("j-fail", "p", new CheckpointIDCounter(), selectiveFailing, config);
        coordinator = coord; // for tearDown
        coord.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));

        // Trigger both pending.
        PendingCheckpoint cp1 = coord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        PendingCheckpoint cp2 = coord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(cp1);
        assertNotNull(cp2);
        assertEquals(2, coord.getNumberOfPendingCheckpoints());

        // ACK cp1 first — its storeCheckPoint call (=1) throws → cp1 must enter FAILED state.
        coord.acknowledgeTask(LOC_1, cp1.getCheckpointId(), stateFor(cp1.getCheckpointId(), "cp1-fails"));
        coord.acknowledgeTask(LOC_2, cp1.getCheckpointId(), stateFor(cp1.getCheckpointId(), "cp1-fails"));

        assertEquals(PendingCheckpoint.Status.FAILED, cp1.getStatus().get(),
                "cp1 must enter FAILED state — storage failure must propagate, not be silently swallowed");
        assertEquals(1, coord.getNumberOfPendingCheckpoints(),
                "After cp1 FAILED propagation, only cp2 should remain pending");

        // cp2 must remain RUNNING — cp1's failure must not cascade to cp2.
        assertSame(cp2, coord.getPendingCheckpoint(cp2.getCheckpointId()));
        assertEquals(PendingCheckpoint.Status.RUNNING, cp2.getStatus().get(),
                "cp2 must remain RUNNING — cp1's storage failure must not affect cp2");

        // ACK cp2 fully — its storeCheckPoint call (=2) succeeds → cp2 must complete.
        coord.acknowledgeTask(LOC_1, cp2.getCheckpointId(), stateFor(cp2.getCheckpointId(), "cp2-ok"));
        coord.acknowledgeTask(LOC_2, cp2.getCheckpointId(), stateFor(cp2.getCheckpointId(), "cp2-ok"));
        CompletedCheckpoint completed2 = (CompletedCheckpoint) cp2.getCompletableFuture().get();
        assertEquals(cp2.getCheckpointId(), completed2.getCheckpointId());
        assertEquals(cp2.getCheckpointId(), coord.getLatestCheckpoint().getCheckpointId());

        assertEquals(0, coord.getNumberOfPendingCheckpoints(),
                "numPending must return to 0 after cp1 FAILED + cp2 COMPLETED");
    }

    /**
     * Lightweight AutoCloseable wrapper to manage coordinator shutdown in tests that need
     * a separate coordinator instance from the @BeforeEach one.
     */
    private static final class CheckpointCoordinatorWithShutdown implements AutoCloseable {
        final CheckpointCoordinator coordinator;

        CheckpointCoordinatorWithShutdown(String jobId, String pipelineId,
                                          CheckpointIDCounter counter,
                                          ICheckpointStorage storage,
                                          CheckpointConfig config) {
            this.coordinator = new CheckpointCoordinator(jobId, pipelineId, counter, storage, config);
        }

        @Override
        public void close() {
            coordinator.shutdown();
        }
    }
}
