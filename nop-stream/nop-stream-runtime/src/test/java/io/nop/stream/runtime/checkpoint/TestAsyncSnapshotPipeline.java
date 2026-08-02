/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.checkpoint.storage.CheckpointStorageException;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.common.state.CheckpointListener;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused verification for the async two-phase snapshot pipeline (Plan
 * 2026-07-25-2200-1). Covers every Exit Criterion of Phase 1:
 * <ul>
 *   <li>storeCheckPoint/storeEpochManifest run on a {@code checkpoint-persist-*} thread, not the ACK caller.</li>
 *   <li>ACK caller returns before storage completes (non-blocking).</li>
 *   <li>decrementPendingCheckpointCount stays atomic with concurrent trigger (§13.2).</li>
 *   <li>abort/timeout interaction during the in-flight persist window.</li>
 *   <li>step ordering: forceComplete(DURABLE) after storeEpochManifest; commit after forceComplete.</li>
 *   <li>no silent skip: storage failure surfaces as FAILED + finishCommit(false).</li>
 *   <li>persist executor actually invoked by completePendingCheckpoint.</li>
 *   <li>end-to-end source→operator→sink commit on async persist success.</li>
 * </ul>
 */
class TestAsyncSnapshotPipeline {

    private static final String JOB_ID = "async-job";
    private static final String PIPELINE_ID = "1";
    private static final TaskLocation LOC_1 = new TaskLocation(JOB_ID, PIPELINE_ID, "v1", 1);
    private static final TaskLocation LOC_2 = new TaskLocation(JOB_ID, PIPELINE_ID, "v2", 2);

    /**
     * Minimal in-memory storage that records the thread that executed each store call,
     * optionally blocks on a latch, and can be flipped into a failing mode.
     */
    static final class RecordingStorage implements ICheckpointStorage {
        final AtomicReference<String> storeCheckpointThread = new AtomicReference<>();
        final AtomicReference<String> storeManifestThread = new AtomicReference<>();
        final AtomicInteger storeCheckpointCount = new AtomicInteger();
        final AtomicInteger storeManifestCount = new AtomicInteger();
        final AtomicInteger storeCheckpointFailCount = new AtomicInteger(0);
        volatile CountDownLatch storeCheckpointEnterLatch;  // if set, store blocks here
        volatile CountDownLatch storeCheckpointReleaseLatch;
        volatile boolean failStoreCheckpoint;
        volatile boolean failStoreManifest;

        @Override public String getName() { return "RecordingStorage"; }
        @Override
        public String storeCheckPoint(CompletedCheckpoint checkpoint) throws CheckpointStorageException {
            storeCheckpointThread.set(Thread.currentThread().getName());
            storeCheckpointCount.incrementAndGet();
            CountDownLatch enter = storeCheckpointEnterLatch;
            if (enter != null) {
                enter.countDown();
                CountDownLatch release = storeCheckpointReleaseLatch;
                if (release != null) {
                    try { release.await(10, TimeUnit.SECONDS); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            }
            if (failStoreCheckpoint) {
                storeCheckpointFailCount.incrementAndGet();
                throw new StreamException("Simulated storeCheckPoint failure");
            }
            return "stored-" + checkpoint.getCheckpointId();
        }
        @Override public CompletedCheckpoint getLatestCheckpoint(String jobId, String pipelineId) { return null; }
        @Override public List<CompletedCheckpoint> getAllCheckpoints(String jobId) { return Collections.emptyList(); }
        @Override public List<CompletedCheckpoint> getLatestCheckpoints(String jobId, int count) { return Collections.emptyList(); }
        @Override public void deleteCheckpoint(String jobId, String pipelineId, long checkpointId) {}
        @Override public void deleteAllCheckpoints(String jobId) {}
        @Override public int getCheckpointCount(String jobId) { return 0; }
        @Override public boolean exists(String jobId, String pipelineId, long checkpointId) { return false; }
        @Override public String storeSavepoint(CompletedCheckpoint checkpoint, String targetPath) { return targetPath; }
        @Override public CompletedCheckpoint loadSavepoint(String savepointPath) { return null; }
        @Override public SavepointMetadata loadSavepointMetadata(String savepointPath) { return null; }
        @Override
        public void storeEpochManifest(String jobId, String pipelineId, EpochManifest manifest) throws CheckpointStorageException {
            storeManifestThread.set(Thread.currentThread().getName());
            storeManifestCount.incrementAndGet();
            if (failStoreManifest) {
                throw new StreamException("Simulated storeEpochManifest failure");
            }
        }
        @Override public EpochManifest loadLatestEpochManifest(String jobId, String pipelineId) { return null; }
    }

    private CheckpointConfig asyncConfig() {
        return CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(60_000L)
                .checkpointTimeout(60_000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3)
                .asyncSnapshotEnabled(true)
                .asyncSnapshotThreadPoolSize(1)
                .build();
    }

    /** Drive a single epoch to fully-acknowledged, returning the PendingCheckpoint. */
    private PendingCheckpoint triggerAndAckBoth(CheckpointCoordinator coord) {
        PendingCheckpoint pending = coord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        coord.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
        coord.acknowledgeTask(LOC_2, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));
        return pending;
    }

    // ------------------------------------------------------------------
    // Exit Criterion: storage runs on checkpoint-persist-* thread, not ACK caller
    // ------------------------------------------------------------------

    @Test
    void testStoreRunsOnPersistExecutorThread() throws Exception {
        RecordingStorage storage = new RecordingStorage();
        CheckpointCoordinator coord = new CheckpointCoordinator(JOB_ID, PIPELINE_ID,
                new CheckpointIDCounter(), storage, asyncConfig());
        coord.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
        AtomicReference<String> ackThreadName = new AtomicReference<>();
        try {
            PendingCheckpoint pending = coord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            coord.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
            // Capture the calling thread of the final (triggering) ACK
            ackThreadName.set(Thread.currentThread().getName());
            coord.acknowledgeTask(LOC_2, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));

            pending.getCompletableFuture().get(10, TimeUnit.SECONDS);

            assertNotNull(storage.storeCheckpointThread.get(), "storeCheckPoint must have executed");
            assertTrue(storage.storeCheckpointThread.get().startsWith("checkpoint-persist-" + JOB_ID),
                    "storeCheckPoint ran on " + storage.storeCheckpointThread.get()
                            + ", expected a checkpoint-persist-" + JOB_ID + " thread");
            assertNotNull(storage.storeManifestThread.get(), "storeEpochManifest must have executed");
            assertTrue(storage.storeManifestThread.get().startsWith("checkpoint-persist-" + JOB_ID),
                    "storeEpochManifest ran on " + storage.storeManifestThread.get());
            assertNotEquals(ackThreadName.get(), storage.storeCheckpointThread.get(),
                    "Storage must NOT run on the ACK caller thread (was: " + ackThreadName.get() + ")");
        } finally {
            coord.shutdown();
        }
    }

    // ------------------------------------------------------------------
    // Exit Criterion: ACK caller returns before storage completes (non-blocking)
    // ------------------------------------------------------------------

    @Test
    void testAckCallerReturnsBeforeStorageCompletes() throws Exception {
        RecordingStorage storage = new RecordingStorage();
        // Block storeCheckPoint until we release it, so we can prove the ACK caller
        // returned while storage was still in-flight.
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        storage.storeCheckpointEnterLatch = entered;
        storage.storeCheckpointReleaseLatch = release;

        CheckpointCoordinator coord = new CheckpointCoordinator(JOB_ID, PIPELINE_ID,
                new CheckpointIDCounter(), storage, asyncConfig());
        coord.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
        try {
            PendingCheckpoint pending = coord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            coord.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));

            long t0 = System.nanoTime();
            // This call submits the persist task and must return immediately even
            // though storeCheckPoint is blocked on `release`.
            coord.acknowledgeTask(LOC_2, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

            assertTrue(entered.await(5, TimeUnit.SECONDS),
                    "storeCheckPoint must have been entered (on the executor thread)");
            // The ACK caller should not have blocked for storage: assert it returned well
            // before the persist completes. We haven't released `release` yet, so the
            // checkpoint future must NOT be done at this point.
            assertFalse(pending.getCompletableFuture().isDone(),
                    "ACK caller returned while storage was still in-flight; future should not be done");

            release.countDown();
            pending.getCompletableFuture().get(10, TimeUnit.SECONDS);
            assertTrue(elapsedMs < 5_000,
                    "ACK caller should return near-instantly (was " + elapsedMs + "ms)");
        } finally {
            coord.shutdown();
        }
    }

    // ------------------------------------------------------------------
    // Exit Criterion: decrementPendingCheckpointCount stays atomic with concurrent trigger (§13.2)
    // ------------------------------------------------------------------

    @Test
    void testNoNegativePendingCountUnderConcurrentTriggerAndComplete() throws Exception {
        RecordingStorage storage = new RecordingStorage();
        // Make storage slow so the in-flight window is wide enough to race with triggers.
        CountDownLatch release = new CountDownLatch(1);
        storage.storeCheckpointReleaseLatch = release;

        CheckpointConfig cfg = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(60_000L)
                .checkpointTimeout(60_000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3)
                .asyncSnapshotEnabled(true)
                .build();
        CheckpointCoordinator coord = new CheckpointCoordinator(JOB_ID, PIPELINE_ID,
                new CheckpointIDCounter(), storage, cfg);
        coord.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicInteger observedNegative = new AtomicInteger(0);
        try {
            PendingCheckpoint pending = coord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            coord.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
            coord.acknowledgeTask(LOC_2, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));

            // Monitor pending count from another thread while storage is in-flight and
            // trigger attempts race. numPendingCheckpoints must never go negative.
            Thread watcher = new Thread(() -> {
                while (!stop.get()) {
                    int n = coord.getNumberOfPendingCheckpoints();
                    if (n < 0) {
                        observedNegative.incrementAndGet();
                    }
                }
            });
            watcher.setDaemon(true);
            watcher.start();

            // Hammer trigger attempts while storage is in-flight (maxConcurrent=1 ⇒ all but
            // the post-completion trigger should be rejected; the count must never go negative).
            Thread trigger = new Thread(() -> {
                while (!stop.get()) {
                    try {
                        PendingCheckpoint p = coord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
                        if (p != null) {
                            coord.abortPendingCheckpoint(p, "race-test");
                        }
                    // Intentionally ignored: trigger/abort may race with coordinator shutdown
                    // or concurrent state transitions; these are the races under test.
                    } catch (Exception ignored) {
                        // no-op
                    }
                }
            });
            trigger.setDaemon(true);
            trigger.start();

            Thread.sleep(500);
            release.countDown();
            pending.getCompletableFuture().get(10, TimeUnit.SECONDS);
            // Give post-completion triggers a brief chance to race.
            Thread.sleep(200);
            stop.set(true);
            watcher.join(2_000);
            trigger.join(2_000);

            assertEquals(0, observedNegative.get(),
                    "numPendingCheckpoints must never be negative under concurrent trigger/complete");
            assertTrue(coord.getNumberOfPendingCheckpoints() >= 0);
        } finally {
            coord.shutdown();
        }
    }

    // ------------------------------------------------------------------
    // Exit Criterion: abort/timeout interaction during in-flight persist
    // ------------------------------------------------------------------

    @Test
    void testTimeoutAbortDuringInFlightPersistIsNoOpThenCompletes() throws Exception {
        RecordingStorage storage = new RecordingStorage();
        CountDownLatch release = new CountDownLatch(1);
        storage.storeCheckpointReleaseLatch = release;

        CheckpointCoordinator coord = new CheckpointCoordinator(JOB_ID, PIPELINE_ID,
                new CheckpointIDCounter(), storage, asyncConfig());
        coord.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
        try {
            PendingCheckpoint pending = coord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            coord.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
            coord.acknowledgeTask(LOC_2, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));

            // Storage is now in-flight on the executor. A timeout-driven abort at this
            // point must fail its RUNNING→ABORTED CAS (status is COMPLETED) and be a no-op.
            coord.abortPendingCheckpoint(pending, "timeout during in-flight persist");
            assertEquals(PendingCheckpoint.Status.COMPLETED, pending.getStatus().get(),
                    "Abort during in-flight persist must NOT transition status from COMPLETED");

            // Releasing storage should let the checkpoint complete normally to DURABLE.
            release.countDown();
            CompletedCheckpoint completed = pending.getCompletableFuture().get(10, TimeUnit.SECONDS);
            assertNotNull(completed);
            assertEquals(0, coord.getNumberOfPendingCheckpoints());
        } finally {
            coord.shutdown();
        }
    }

    @Test
    void testStorageFailureDuringInFlightPersistMarksFailedAndAborts() throws Exception {
        RecordingStorage storage = new RecordingStorage();
        storage.failStoreCheckpoint = true;

        CheckpointCoordinator coord = new CheckpointCoordinator(JOB_ID, PIPELINE_ID,
                new CheckpointIDCounter(), storage, asyncConfig());
        coord.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));

        AtomicBoolean finishCommitSuccessTrueCalled = new AtomicBoolean(false);
        AtomicBoolean finishCommitSuccessFalseCalled = new AtomicBoolean(false);
        coord.addParticipant(new CheckpointParticipant() {
            @Override public TaskStateSnapshot saveState(long epochId) { return TaskStateSnapshot.empty(LOC_1); }
            @Override public void prepareCommit(long epochId) {}
            @Override
            public void finishCommit(long epochId, boolean success) {
                if (success) finishCommitSuccessTrueCalled.set(true);
                else finishCommitSuccessFalseCalled.set(true);
            }
            @Override public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {}
        });

        try {
            PendingCheckpoint pending = coord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            coord.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
            coord.acknowledgeTask(LOC_2, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));

            // The failure path (段3b) sets status=FAILED and aborts the epoch; it does NOT
            // force-complete the future (matching pre-async behavior). So we must NOT wait on
            // the future here — instead poll the observable side effects (status + commit flag).
            long deadline = System.currentTimeMillis() + 5_000;
            while (pending.getStatus().get() != PendingCheckpoint.Status.FAILED
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertEquals(PendingCheckpoint.Status.FAILED, pending.getStatus().get(),
                    "Storage failure must surface as FAILED status (no silent skip)");
            // Confirm the failure callback also flushed finishCommit(false).
            deadline = System.currentTimeMillis() + 2_000;
            while (!finishCommitSuccessFalseCalled.get() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertTrue(finishCommitSuccessFalseCalled.get(),
                    "finishCommit(false) must be called on storage failure");
            assertFalse(finishCommitSuccessTrueCalled.get(),
                    "finishCommit(true) (commit) must NOT be called when storage failed (§12 invariant 5)");
            assertEquals(0, coord.getNumberOfPendingCheckpoints(),
                    "Pending counter must be decremented on failure");
            assertTrue(storage.storeCheckpointFailCount.get() >= 1);
        } finally {
            coord.shutdown();
        }
    }

    @Test
    void testManifestFailureAfterCheckpointStoreMarksFailed() throws Exception {
        RecordingStorage storage = new RecordingStorage();
        storage.failStoreManifest = true;

        CheckpointCoordinator coord = new CheckpointCoordinator(JOB_ID, PIPELINE_ID,
                new CheckpointIDCounter(), storage, asyncConfig());
        coord.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
        try {
            PendingCheckpoint pending = coord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            coord.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
            coord.acknowledgeTask(LOC_2, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));

            long deadline = System.currentTimeMillis() + 5_000;
            while (pending.getStatus().get() != PendingCheckpoint.Status.FAILED
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertEquals(PendingCheckpoint.Status.FAILED, pending.getStatus().get(),
                    "Manifest storage failure must surface as FAILED");
            assertTrue(storage.storeCheckpointCount.get() >= 1,
                    "storeCheckPoint must have succeeded before manifest failure");
            assertTrue(storage.storeManifestCount.get() >= 1,
                    "storeEpochManifest must have been attempted");
            assertEquals(0, coord.getNumberOfPendingCheckpoints());
        } finally {
            coord.shutdown();
        }
    }

    // ------------------------------------------------------------------
    // Exit Criterion: step ordering — forceComplete(DURABLE) after storeEpochManifest,
    //                 commit after forceComplete
    // ------------------------------------------------------------------

    @Test
    void testStepOrderingDurableAfterManifestAndCommitAfterDurable() throws Exception {
        RecordingStorage storage = new RecordingStorage();
        // Instrument storage so we can observe the relative ordering of:
        //   (1) storeEpochManifest success (epoch becomes durable-eligible),
        //   (2) pending future completion (forceComplete ⇒ DURABLE),
        //   (3) finishCommit(true) (commit).
        AtomicInteger manifestDoneOrder = new AtomicInteger(0);
        AtomicInteger forceCompleteOrder = new AtomicInteger(0);
        AtomicInteger commitOrder = new AtomicInteger(0);
        AtomicInteger sequence = new AtomicInteger(0);

        // Wrap manifest storage to stamp an order index at the moment it returns successfully.
        ICheckpointStorage orderedStorage = new ICheckpointStorage() {
            @Override public String getName() { return "ordered"; }
            @Override
            public String storeCheckPoint(CompletedCheckpoint checkpoint) {
                storage.storeCheckPoint(checkpoint);
                return "ok";
            }
            @Override
            public void storeEpochManifest(String jobId, String pipelineId, EpochManifest manifest) {
                storage.storeEpochManifest(jobId, pipelineId, manifest);
                manifestDoneOrder.set(sequence.incrementAndGet());
            }
            @Override public CompletedCheckpoint getLatestCheckpoint(String j, String p) { return null; }
            @Override public List<CompletedCheckpoint> getAllCheckpoints(String j) { return Collections.emptyList(); }
            @Override public List<CompletedCheckpoint> getLatestCheckpoints(String j, int c) { return Collections.emptyList(); }
            @Override public void deleteCheckpoint(String j, String p, long id) {}
            @Override public void deleteAllCheckpoints(String j) {}
            @Override public int getCheckpointCount(String j) { return 0; }
            @Override public boolean exists(String j, String p, long id) { return false; }
            @Override public String storeSavepoint(CompletedCheckpoint c, String t) { return t; }
            @Override public CompletedCheckpoint loadSavepoint(String p) { return null; }
            @Override public SavepointMetadata loadSavepointMetadata(String p) { return null; }
            @Override public EpochManifest loadLatestEpochManifest(String j, String p) { return null; }
        };

        CheckpointCoordinator coord = new CheckpointCoordinator(JOB_ID, PIPELINE_ID,
                new CheckpointIDCounter(), orderedStorage, asyncConfig());
        coord.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
        coord.addParticipant(new CheckpointParticipant() {
            @Override public TaskStateSnapshot saveState(long epochId) { return TaskStateSnapshot.empty(LOC_1); }
            @Override public void prepareCommit(long epochId) {}
            @Override
            public void finishCommit(long epochId, boolean success) {
                if (success) commitOrder.set(sequence.incrementAndGet());
            }
            @Override public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {}
        });
        // Listener that fires when the future is completed (forceComplete ⇒ DURABLE).
        coord.addListener(new CheckpointListener() {
            @Override public void notifyCheckpointComplete(long checkpointId) {}
            @Override public void notifyCheckpointAborted(long checkpointId) {}
        });

        try {
            PendingCheckpoint pending = coord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            // Install a whenComplete on the future to stamp forceComplete ordering.
            pending.getCompletableFuture().whenComplete((c, e) -> forceCompleteOrder.set(sequence.incrementAndGet()));
            coord.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
            coord.acknowledgeTask(LOC_2, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));

            pending.getCompletableFuture().get(10, TimeUnit.SECONDS);
            // Ensure commit ordering is captured.
            long deadline = System.currentTimeMillis() + 2_000;
            while (commitOrder.get() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(20);

            assertTrue(manifestDoneOrder.get() > 0, "storeEpochManifest must have completed");
            assertTrue(forceCompleteOrder.get() > 0, "forceComplete must have run");
            assertTrue(commitOrder.get() > 0, "finishCommit(true) must have run");
            assertTrue(manifestDoneOrder.get() < forceCompleteOrder.get(),
                    "forceComplete (DURABLE) must happen AFTER storeEpochManifest: manifest="
                            + manifestDoneOrder.get() + ", durable=" + forceCompleteOrder.get());
            assertTrue(forceCompleteOrder.get() < commitOrder.get(),
                    "commit (finishCommit true) must happen AFTER forceComplete (DURABLE): durable="
                            + forceCompleteOrder.get() + ", commit=" + commitOrder.get());
        } finally {
            coord.shutdown();
        }
    }

    // ------------------------------------------------------------------
    // Exit Criterion: persist executor actually invoked by completePendingCheckpoint
    // ------------------------------------------------------------------

    @Test
    void testPersistExecutorInvokedByCompletePendingCheckpoint() throws Exception {
        RecordingStorage storage = new RecordingStorage();
        CheckpointCoordinator coord = new CheckpointCoordinator(JOB_ID, PIPELINE_ID,
                new CheckpointIDCounter(), storage, asyncConfig());
        coord.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
        try {
            triggerAndAckBoth(coord);
            // storeCheckpointCount increments on the executor. Give the async path time.
            long deadline = System.currentTimeMillis() + 5_000;
            while (storage.storeCheckpointCount.get() < 1 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertTrue(storage.storeCheckpointCount.get() >= 1,
                    "completePendingCheckpoint must have invoked the persist executor (storeCheckPoint count="
                            + storage.storeCheckpointCount.get() + ")");
            assertTrue(storage.storeManifestCount.get() >= 1,
                    "completePendingCheckpoint must have invoked storeEpochManifest (count="
                            + storage.storeManifestCount.get() + ")");
        } finally {
            coord.shutdown();
        }
    }

    // ------------------------------------------------------------------
    // Exit Criterion: end-to-end source→operator→sink commit on async persist success
    // ------------------------------------------------------------------

    @Test
    void testEndToEndAsyncPersistCompletesAndCommits() throws Exception {
        RecordingStorage storage = new RecordingStorage();
        CheckpointCoordinator coord = new CheckpointCoordinator(JOB_ID, PIPELINE_ID,
                new CheckpointIDCounter(), storage, asyncConfig());
        coord.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));

        AtomicLong committedEpoch = new AtomicLong(-1L);
        AtomicLong completedNotifiedEpoch = new AtomicLong(-1L);
        coord.addParticipant(new CheckpointParticipant() {
            @Override public TaskStateSnapshot saveState(long epochId) { return TaskStateSnapshot.empty(LOC_1); }
            @Override public void prepareCommit(long epochId) {}
            @Override public void finishCommit(long epochId, boolean success) {
                if (success) committedEpoch.set(epochId);
            }
            @Override public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {}
        });
        coord.addListener(new CheckpointListener() {
            @Override public void notifyCheckpointComplete(long checkpointId) { completedNotifiedEpoch.set(checkpointId); }
            @Override public void notifyCheckpointAborted(long checkpointId) {}
        });

        try {
            PendingCheckpoint pending = triggerAndAckBoth(coord);
            CompletedCheckpoint completed = pending.getCompletableFuture().get(10, TimeUnit.SECONDS);

            // finishCommit and notifyCheckpointComplete both fire on the async persist
            // executor inside the coordinator monitor, but they are distinct side effects
            // observed via separate AtomicLongs. Poll for both before asserting (mirrors
            // testAllSideEffectsPreservedOnAsyncSuccess), since notifyCheckpointComplete
            // runs strictly after finishCommit in onCompletePersistSuccess.
            long deadline = System.currentTimeMillis() + 5_000;
            while ((committedEpoch.get() < 0 || completedNotifiedEpoch.get() < 0)
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }

            assertEquals(pending.getCheckpointId(), completed.getCheckpointId());
            assertEquals(pending.getCheckpointId(), committedEpoch.get(),
                    "Sink commit (finishCommit true) must fire after durable async persist");
            assertEquals(pending.getCheckpointId(), completedNotifiedEpoch.get(),
                    "notifyCheckpointComplete must fire after durable async persist");
            CompletedCheckpoint latest = coord.getLatestCheckpoint();
            assertNotNull(latest);
            assertEquals(pending.getCheckpointId(), latest.getCheckpointId());
        } finally {
            coord.shutdown();
        }
    }

    // ------------------------------------------------------------------
    // Exit Criterion: side effects completeness (latestCompletedCheckpoint /
    //                 notifyCheckpointCompleted / consecutiveTriggerFailures reset)
    // ------------------------------------------------------------------

    @Test
    void testAllSideEffectsPreservedOnAsyncSuccess() throws Exception {
        RecordingStorage storage = new RecordingStorage();
        CheckpointCoordinator coord = new CheckpointCoordinator(JOB_ID, PIPELINE_ID,
                new CheckpointIDCounter(), storage, asyncConfig());
        coord.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
        AtomicBoolean completedNotified = new AtomicBoolean(false);
        coord.addListener(new CheckpointListener() {
            @Override public void notifyCheckpointComplete(long checkpointId) { completedNotified.set(true); }
            @Override public void notifyCheckpointAborted(long checkpointId) {}
        });
        try {
            // Force trigger failures > 0 so we can verify the reset side effect.
            coord.incrementTriggerFailures();
            assertTrue(coord.getConsecutiveTriggerFailures() > 0);

            PendingCheckpoint pending = triggerAndAckBoth(coord);
            pending.getCompletableFuture().get(10, TimeUnit.SECONDS);

            long deadline = System.currentTimeMillis() + 5_000;
            while (!completedNotified.get() && System.currentTimeMillis() < deadline) Thread.sleep(20);

            // Side-effect completeness: matches pre-async ordering (line 295–313).
            assertNotNull(coord.getLatestCheckpoint(), "latestCompletedCheckpoint must be set");
            assertEquals(pending.getCheckpointId(), coord.getLatestCheckpoint().getCheckpointId());
            assertTrue(completedNotified.get(), "notifyCheckpointCompleted must fire");
            assertEquals(0, coord.getConsecutiveTriggerFailures(),
                    "consecutiveTriggerFailures must be reset to 0 on success");
            assertTrue(coord.getMetrics().getNumCompletedCheckpoints() >= 1,
                    "metrics.incrementCompletedCheckpoints must fire");
        } finally {
            coord.shutdown();
        }
    }

    // ------------------------------------------------------------------
    // Exit Criterion: sync fallback behaves like pre-refactor (existing tests cover this
    // in TestCheckpointCoordinator; here we add an explicit async-disabled comparison).
    // ------------------------------------------------------------------

    @Test
    void testSyncFallbackRunsStorageOnCallerThread() throws Exception {
        RecordingStorage storage = new RecordingStorage();
        CheckpointConfig syncCfg = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(60_000L)
                .checkpointTimeout(60_000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3)
                .asyncSnapshotEnabled(false)  // sync fallback
                .build();
        CheckpointCoordinator coord = new CheckpointCoordinator(JOB_ID, PIPELINE_ID,
                new CheckpointIDCounter(), storage, syncCfg);
        coord.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
        try {
            PendingCheckpoint pending = coord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            String caller = Thread.currentThread().getName();
            coord.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
            coord.acknowledgeTask(LOC_2, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));

            // In sync mode the future is already complete by the time acknowledgeTask returns.
            assertTrue(pending.getCompletableFuture().isDone(),
                    "sync fallback: checkpoint must complete synchronously before ACK caller returns");
            CompletedCheckpoint completed = pending.getCompletableFuture().get();
            assertNotNull(completed);

            assertEquals(caller, storage.storeCheckpointThread.get(),
                    "sync fallback: storeCheckPoint must run on the ACK caller thread");
            assertEquals(caller, storage.storeManifestThread.get(),
                    "sync fallback: storeEpochManifest must run on the ACK caller thread");
        } finally {
            coord.shutdown();
        }
    }

    // ------------------------------------------------------------------
    // N1: duplicate / stale ACK during async window returns false instead of throwing
    // ------------------------------------------------------------------

    @Test
    void testDuplicateAckDuringAsyncWindowReturnsFalseNotThrows() throws Exception {
        RecordingStorage storage = new RecordingStorage();
        CountDownLatch release = new CountDownLatch(1);
        storage.storeCheckpointReleaseLatch = release;

        CheckpointCoordinator coord = new CheckpointCoordinator(JOB_ID, PIPELINE_ID,
                new CheckpointIDCounter(), storage, asyncConfig());
        coord.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
        try {
            PendingCheckpoint pending = coord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            long cpId = pending.getCheckpointId();
            coord.acknowledgeTask(LOC_1, cpId, TaskStateSnapshot.empty(LOC_1));
            coord.acknowledgeTask(LOC_2, cpId, TaskStateSnapshot.empty(LOC_2));
            // Storage is now in-flight on the executor; status is COMPLETED and entry is
            // still registered. A duplicate ACK from LOC_1 must NOT throw — it must return
            // false (matching sync-mode duplicate semantics).
            boolean dup = coord.acknowledgeTask(LOC_1, cpId, TaskStateSnapshot.empty(LOC_1));
            assertFalse(dup, "Duplicate ACK during async window must return false (no throw)");

            release.countDown();
            pending.getCompletableFuture().get(10, TimeUnit.SECONDS);
        } finally {
            coord.shutdown();
        }
    }
}
