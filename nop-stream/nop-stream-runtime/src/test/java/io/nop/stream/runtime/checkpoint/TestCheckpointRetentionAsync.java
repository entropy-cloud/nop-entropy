/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.SavepointMetadata;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2026-09-03-1951-1 Phase 2 focused verification (F-A): retention storage I/O
 * ({@code getAllCheckpoints} + {@code deleteCheckpoint}) moved off the coordinator
 * monitor onto the dedicated {@code checkpoint-retention-<jobId>} executor.
 * <ul>
 *   <li><b>Test A</b> — retention eventual consistency: after completing
 *       N &gt; maxRetained checkpoints, the retained set converges to ≤ maxRetained
 *       (newest kept) via an async bounded predicate (no fixed sleeps).</li>
 *   <li><b>Test B</b> — completion critical path not blocked: with slow retention
 *       I/O injected (fast stores), a subsequent checkpoint still completes within a
 *       threshold well below the injected retention latency
 *       ({@code asyncSnapshotEnabled=true} path).</li>
 *   <li>Serialization guard: retention runs never overlap (peak concurrent
 *       {@code getAllCheckpoints} == 1).</li>
 *   <li>Trailing re-run: completions arriving while a retention run is in flight are
 *       covered by exactly one later run (coalescing does not lose the trailing edge).</li>
 *   <li>Failure semantics: retention I/O failure never propagates to the completion
 *       path and self-heals on the next completion (D1(c) natural retry).</li>
 *   <li>Sync-fallback (D1(d)): {@code asyncSnapshotEnabled=false} keeps retention
 *       inline on the ACK caller thread (converged before the ACK returns).</li>
 * </ul>
 */
class TestCheckpointRetentionAsync {

    private static final String JOB_ID = "retention-job";
    private static final String PIPELINE_ID = "1";
    private static final TaskLocation LOC_1 = new TaskLocation(JOB_ID, PIPELINE_ID, "v1", 1);
    private static final TaskLocation LOC_2 = new TaskLocation(JOB_ID, PIPELINE_ID, "v2", 2);

    /**
     * In-memory storage with real keep/delete semantics plus injectable retention
     * latency, entry blocking (latches), failure toggles, and concurrency probes.
     * {@code storeCheckPoint}/{@code storeEpochManifest} are always fast — the tests
     * isolate retention's contribution to any observed blocking.
     */
    static final class RetentionStorage implements ICheckpointStorage {
        final Map<Long, CompletedCheckpoint> checkpoints = new TreeMap<>();
        final AtomicReference<String> getAllThread = new AtomicReference<>();
        final AtomicReference<String> deleteThread = new AtomicReference<>();
        final AtomicInteger getAllCount = new AtomicInteger();
        final AtomicInteger deleteCount = new AtomicInteger();
        final AtomicInteger inFlightGetAll = new AtomicInteger();
        final AtomicInteger peakInFlightGetAll = new AtomicInteger();
        volatile long getAllDelayMs;
        volatile long deleteDelayMs;
        volatile boolean failGetAll;
        volatile boolean failDelete;
        volatile CountDownLatch getAllEnterLatch;
        volatile CountDownLatch getAllReleaseLatch;

        private void probeEnter() {
            int now = inFlightGetAll.incrementAndGet();
            peakInFlightGetAll.accumulateAndGet(now, Math::max);
        }

        @Override
        public String getName() {
            return "RetentionStorage";
        }

        @Override
        public String storeCheckPoint(CompletedCheckpoint checkpoint) {
            synchronized (checkpoints) {
                checkpoints.put(checkpoint.getCheckpointId(), checkpoint);
            }
            return "stored-" + checkpoint.getCheckpointId();
        }

        @Override
        public List<CompletedCheckpoint> getAllCheckpoints(String jobId) {
            getAllThread.set(Thread.currentThread().getName());
            getAllCount.incrementAndGet();
            probeEnter();
            try {
                CountDownLatch enter = getAllEnterLatch;
                if (enter != null) {
                    enter.countDown();
                }
                CountDownLatch release = getAllReleaseLatch;
                if (release != null) {
                    try {
                        release.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                long delay = getAllDelayMs;
                if (delay > 0) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                if (failGetAll) {
                    throw new IllegalStateException("Simulated getAllCheckpoints failure");
                }
                synchronized (checkpoints) {
                    List<CompletedCheckpoint> desc = new ArrayList<>(checkpoints.values());
                    // Storage contract: descending by checkpoint id (newest first) —
                    // the deletion set is the OLDEST tail (mirrors LocalFileCheckpointStorage).
                    desc.sort((a, b) -> Long.compare(b.getCheckpointId(), a.getCheckpointId()));
                    return desc;
                }
            } finally {
                inFlightGetAll.decrementAndGet();
            }
        }

        @Override
        public void deleteCheckpoint(String jobId, String pipelineId, long checkpointId) {
            deleteThread.set(Thread.currentThread().getName());
            long delay = deleteDelayMs;
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failDelete) {
                throw new IllegalStateException("Simulated deleteCheckpoint failure");
            }
            deleteCount.incrementAndGet();
            synchronized (checkpoints) {
                checkpoints.remove(checkpointId);
            }
        }

        @Override
        public CompletedCheckpoint getLatestCheckpoint(String jobId, String pipelineId) {
            return null;
        }

        @Override
        public List<CompletedCheckpoint> getLatestCheckpoints(String jobId, int count) {
            return Collections.emptyList();
        }

        @Override
        public void deleteAllCheckpoints(String jobId) {
            synchronized (checkpoints) {
                checkpoints.clear();
            }
        }

        @Override
        public int getCheckpointCount(String jobId) {
            synchronized (checkpoints) {
                return checkpoints.size();
            }
        }

        @Override
        public boolean exists(String jobId, String pipelineId, long checkpointId) {
            synchronized (checkpoints) {
                return checkpoints.containsKey(checkpointId);
            }
        }

        @Override
        public String storeSavepoint(CompletedCheckpoint checkpoint, String targetPath) {
            return targetPath;
        }

        @Override
        public CompletedCheckpoint loadSavepoint(String savepointPath) {
            return null;
        }

        @Override
        public SavepointMetadata loadSavepointMetadata(String savepointPath) {
            return null;
        }

        @Override
        public void storeEpochManifest(String jobId, String pipelineId, EpochManifest manifest) {
        }

        @Override
        public EpochManifest loadLatestEpochManifest(String jobId, String pipelineId) {
            return null;
        }

        int size() {
            synchronized (checkpoints) {
                return checkpoints.size();
            }
        }
    }

    private CheckpointConfig asyncConfig(int maxRetained) {
        return CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(60_000L)
                .checkpointTimeout(60_000L)
                .minPause(0L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(maxRetained)
                .asyncSnapshotEnabled(true)
                .asyncSnapshotThreadPoolSize(1)
                .build();
    }

    private CheckpointCoordinator newCoordinator(RetentionStorage storage, CheckpointConfig cfg) {
        CheckpointCoordinator coord = new CheckpointCoordinator(JOB_ID, PIPELINE_ID,
                new CheckpointIDCounter(), storage, cfg);
        coord.setTasksToAcknowledge(java.util.Arrays.asList(LOC_1, LOC_2));
        return coord;
    }

    /** Drive one epoch to fully-acknowledged and wait for durable completion. */
    private CompletedCheckpoint completeOne(CheckpointCoordinator coord) throws Exception {
        PendingCheckpoint pending = coord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        org.junit.jupiter.api.Assertions.assertNotNull(pending);
        coord.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
        coord.acknowledgeTask(LOC_2, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));
        return pending.getCompletableFuture().get(30, TimeUnit.SECONDS);
    }

    /** Bounded async predicate — polls until true or fails with the timeout message. */
    private static boolean await(java.util.function.BooleanSupplier predicate, long timeoutMs,
                                 String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (predicate.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return predicate.getAsBoolean();
    }

    // ------------------------------------------------------------------
    // Test A: retention eventual consistency (async bounded predicate)
    // ------------------------------------------------------------------

    @Test
    void retentionConvergesToMaxRetainedKeepingNewest() throws Exception {
        RetentionStorage storage = new RetentionStorage();
        CheckpointCoordinator coord = newCoordinator(storage, asyncConfig(2));
        long lastId = -1;
        long secondLastId = -1;
        try {
            for (int i = 0; i < 5; i++) {
                secondLastId = lastId;
                lastId = completeOne(coord).getCheckpointId();
            }
            final long newest = lastId;
            final long secondNewest = secondLastId;
            assertTrue(await(() -> storage.size() <= 2, 10_000, "retention must converge"),
                    "retained set must converge to <= maxRetained=2 within 10s (was "
                            + storage.size() + ")");
            // The NEWEST checkpoints are retained (deletion always targets the oldest).
            assertTrue(await(() -> storage.checkpoints.containsKey(newest)
                    && storage.checkpoints.containsKey(secondNewest), 10_000, "newest retained"),
                    "retained set must be the two newest epochs {" + secondNewest + "," + newest
                            + "}, was " + storage.checkpoints.keySet());
            assertTrue(storage.deleteCount.get() >= 3,
                    "three oldest checkpoints must have been deleted (deleteCount="
                            + storage.deleteCount.get() + ")");
        } finally {
            coord.shutdown();
        }
    }

    // ------------------------------------------------------------------
    // Test B: completion critical path not blocked by slow retention I/O
    // ------------------------------------------------------------------

    @Test
    void slowRetentionIoDoesNotBlockSubsequentCompletion() throws Exception {
        RetentionStorage storage = new RetentionStorage();
        // Slow retention I/O only — stores stay fast, isolating retention's
        // contribution to any blocking of the completion path.
        storage.getAllDelayMs = 4_000L;
        storage.deleteDelayMs = 4_000L;
        CheckpointCoordinator coord = newCoordinator(storage, asyncConfig(1));
        try {
            // cp1: durable; its retention run enters the slow getAllCheckpoints.
            completeOne(coord);
            assertTrue(await(() -> storage.getAllCount.get() >= 1, 10_000, "retention entered"),
                    "retention must have entered getAllCheckpoints after the first completion");

            // cp2 completes while the slow retention I/O is in flight. Under the old
            // (in-monitor) behavior the coordinator monitor was held for the whole
            // slow getAll+delete, so this future.get(2500ms) would time out. With the
            // retention off the monitor it must succeed well within the threshold.
            PendingCheckpoint p2 = coord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            org.junit.jupiter.api.Assertions.assertNotNull(p2);
            long t0 = System.currentTimeMillis();
            coord.acknowledgeTask(LOC_1, p2.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
            coord.acknowledgeTask(LOC_2, p2.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));
            CompletedCheckpoint cp2 = p2.getCompletableFuture().get(2_500, TimeUnit.MILLISECONDS);
            long elapsed = System.currentTimeMillis() - t0;
            org.junit.jupiter.api.Assertions.assertNotNull(cp2);
            final long newest = cp2.getCheckpointId();
            assertTrue(elapsed < 2_500,
                    "subsequent checkpoint completion must not block on slow retention I/O (took "
                            + elapsed + "ms)");

            // The slow retention path was genuinely exercised and eventually converges
            // (retention deletes cp1: maxRetained=1 keeps only the newest).
            assertTrue(await(() -> storage.deleteCount.get() >= 1, 30_000, "slow delete ran"),
                    "slow retention delete must eventually run (deleteCount="
                            + storage.deleteCount.get() + ")");
            assertTrue(await(() -> storage.size() <= 1, 30_000, "converge after slow io"),
                    "retained set must converge to <= 1 after slow retention I/O (was "
                            + storage.size() + ")");
            assertTrue(storage.checkpoints.containsKey(newest),
                    "the newest checkpoint must be the retained one");
        } finally {
            coord.shutdown();
        }
    }

    // ------------------------------------------------------------------
    // D1(f): serialization guard — retention runs never overlap
    // ------------------------------------------------------------------

    @Test
    void retentionRunsAreSerializedUnderRapidCompletions() throws Exception {
        RetentionStorage storage = new RetentionStorage();
        storage.getAllDelayMs = 100L;
        CheckpointCoordinator coord = newCoordinator(storage, asyncConfig(3));
        try {
            for (int i = 0; i < 8; i++) {
                completeOne(coord);
            }
            assertTrue(await(() -> storage.size() <= 3, 15_000, "converge"),
                    "retained set must converge to <= maxRetained=3 (was " + storage.size() + ")");
            assertEquals(1, storage.peakInFlightGetAll.get(),
                    "retention runs must be serialized (peak concurrent getAllCheckpoints="
                            + storage.peakInFlightGetAll.get() + ")");
        } finally {
            coord.shutdown();
        }
    }

    // ------------------------------------------------------------------
    // D1(f): trailing re-run covers completions during an in-flight run
    // ------------------------------------------------------------------

    @Test
    void trailingReRunCoversCompletionDuringInFlightRetention() throws Exception {
        RetentionStorage storage = new RetentionStorage();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        storage.getAllEnterLatch = entered;
        storage.getAllReleaseLatch = release;
        CheckpointCoordinator coord = newCoordinator(storage, asyncConfig(1));
        long newest = -1;
        try {
            // cp1: its retention run blocks inside getAllCheckpoints.
            completeOne(coord);
            assertTrue(entered.await(10, TimeUnit.SECONDS),
                    "first retention run must enter getAllCheckpoints");

            // cp2 completes while run #1 is in flight. Its retention trigger must
            // COALESCE (no second concurrent getAll — serialization guard) ...
            newest = completeOne(coord).getCheckpointId();
            Thread.sleep(300);
            assertEquals(1, storage.getAllCount.get(),
                    "the in-flight retention run must still be the only one (coalesced trigger)");

            // ... and the trailing re-run must fire after run #1 finishes.
            release.countDown();
            assertTrue(await(() -> storage.getAllCount.get() >= 2, 15_000, "trailing re-run"),
                    "the trailing retention re-run must execute after the in-flight run "
                            + "(getAllCount=" + storage.getAllCount.get() + ")");
            assertTrue(await(() -> storage.size() <= 1, 15_000, "converge"),
                    "retained set must converge to <= 1 (was " + storage.size() + ")");
            assertTrue(storage.checkpoints.containsKey(newest),
                    "the newest checkpoint must be the retained one");
        } finally {
            release.countDown();
            coord.shutdown();
        }
    }

    // ------------------------------------------------------------------
    // D1(c): retention failure never blocks completion, self-heals on next completion
    // ------------------------------------------------------------------

    @Test
    void retentionFailureDoesNotPropagateAndSelfHealsOnNextCompletion() throws Exception {
        RetentionStorage storage = new RetentionStorage();
        storage.failGetAll = true;
        CheckpointCoordinator coord = newCoordinator(storage, asyncConfig(1));
        try {
            // Two completions with retention persistently failing: neither the
            // completion futures nor the coordinator may be affected (failure is
            // contained + logged inside cleanupOldCheckpoints, never propagated).
            completeOne(coord);
            completeOne(coord);
            assertTrue(await(() -> storage.getAllCount.get() >= 1, 10_000, "retention attempted"),
                    "retention must have been attempted");
            assertEquals(2, storage.size(),
                    "with retention failing, the retained set stays over maxRetained");

            // Self-heal: the next completion re-triggers retention (natural retry);
            // with the failure cleared it converges.
            storage.failGetAll = false;
            long newest = completeOne(coord).getCheckpointId();
            assertTrue(await(() -> storage.size() <= 1, 15_000, "self-heal converge"),
                    "retention must self-heal on the next completion (was " + storage.size() + ")");
            assertTrue(storage.checkpoints.containsKey(newest),
                    "the newest checkpoint must be the retained one after self-heal");
        } finally {
            coord.shutdown();
        }
    }

    // ------------------------------------------------------------------
    // D1(d): sync-fallback keeps retention inline on the ACK caller thread
    // ------------------------------------------------------------------

    @Test
    void syncFallbackRunsRetentionInlineOnAckCaller() throws Exception {
        RetentionStorage storage = new RetentionStorage();
        CheckpointConfig syncCfg = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(60_000L)
                .checkpointTimeout(60_000L)
                .minPause(0L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(1)
                .asyncSnapshotEnabled(false)
                .build();
        CheckpointCoordinator coord = newCoordinator(storage, syncCfg);
        try {
            completeOne(coord);
            String caller = Thread.currentThread().getName();
            long newest = completeOne(coord).getCheckpointId();

            // Inline semantics: retention converged BEFORE the second ACK returned
            // (no async wait), executing on the ACK caller's thread.
            assertEquals(1, storage.size(),
                    "sync-fallback retention must have converged inline (was " + storage.size() + ")");
            assertEquals(caller, storage.getAllThread.get(),
                    "sync-fallback getAllCheckpoints must run on the ACK caller thread");
            assertEquals(caller, storage.deleteThread.get(),
                    "sync-fallback deleteCheckpoint must run on the ACK caller thread");
            assertTrue(storage.checkpoints.containsKey(newest),
                    "the newest checkpoint must be the retained one");
        } finally {
            coord.shutdown();
        }
    }
}
