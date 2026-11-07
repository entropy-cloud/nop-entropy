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
import io.nop.stream.core.common.state.CheckpointListener;
import io.nop.stream.core.testsupport.TestAwait;

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
 *   <li><b>Test B</b> — completion critical path not blocked: with retention I/O
 *       injected as a latch-held getAllCheckpoints (fast stores), a subsequent
 *       checkpoint completes while the retention round is verifiably still in
 *       flight — structural proof instead of an elapsed-time threshold
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
     *
     * <p>Item 33: the manifest plane is REAL here — {@code storeEpochManifest}
     * records into a map and {@code pruneEpochManifests} implements the
     * keep-newest-N semantics (epochId descending) so the retention tests observe
     * the dual-plane bound, plus prune call probes (count/args/thread) for wiring
     * verification and a failure toggle.
     */
    static final class RetentionStorage implements ICheckpointStorage {
        final Map<Long, CompletedCheckpoint> checkpoints = new TreeMap<>();
        final TreeMap<Long, EpochManifest> manifests = new TreeMap<>();
        final AtomicReference<String> getAllThread = new AtomicReference<>();
        final AtomicReference<String> deleteThread = new AtomicReference<>();
        final AtomicReference<String> pruneThread = new AtomicReference<>();
        final AtomicInteger getAllCount = new AtomicInteger();
        final AtomicInteger deleteCount = new AtomicInteger();
        final AtomicInteger pruneCount = new AtomicInteger();
        final AtomicInteger inFlightGetAll = new AtomicInteger();
        final AtomicInteger peakInFlightGetAll = new AtomicInteger();
        final List<String> pruneArgs = new java.util.concurrent.CopyOnWriteArrayList<>();
        volatile boolean failGetAll;
        volatile boolean failDelete;
        volatile boolean failPrune;
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
        public void storeEpochManifest(String jobId, String pipelineId, EpochManifest manifest) {
            synchronized (manifests) {
                manifests.put(manifest.getEpochId(), manifest);
            }
        }

        @Override
        public EpochManifest loadLatestEpochManifest(String jobId, String pipelineId) {
            synchronized (manifests) {
                return manifests.isEmpty() ? null : manifests.get(manifests.lastKey());
            }
        }

        /**
         * Real keep-newest-N semantics (epochId descending — mirrors
         * LocalFileCheckpointStorage/JdbcCheckpointStorage prune face, §9.2 D2).
         */
        @Override
        public List<Long> pruneEpochManifests(String jobId, String pipelineId, int maxRetained) {
            pruneThread.set(Thread.currentThread().getName());
            pruneCount.incrementAndGet();
            pruneArgs.add(jobId + "/" + pipelineId + "/" + maxRetained);
            if (failPrune) {
                throw new IllegalStateException("Simulated pruneEpochManifests failure");
            }
            List<Long> pruned = new ArrayList<>();
            synchronized (manifests) {
                List<Long> desc = new ArrayList<>(manifests.descendingKeySet());
                for (int i = Math.max(0, maxRetained); i < desc.size(); i++) {
                    pruned.add(desc.get(i));
                    manifests.remove(desc.get(i));
                }
            }
            return pruned;
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
            synchronized (manifests) {
                manifests.clear();
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

        int size() {
            synchronized (checkpoints) {
                return checkpoints.size();
            }
        }

        int manifestSize() {
            synchronized (manifests) {
                return manifests.size();
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

    /** Completion-listener latch: fires only AFTER the coordinator scheduled the retention run. */
    static final class CompletionLatch implements CheckpointListener {
        final CountDownLatch latch = new CountDownLatch(1);
        volatile long completedId = -1;

        @Override
        public void notifyCheckpointComplete(long checkpointId) {
            completedId = checkpointId;
            latch.countDown();
        }

        @Override
        public void notifyCheckpointAborted(long checkpointId) {
        }
    }

    /**
     * Drive one epoch to fully-acknowledged and wait for durable completion.
     * The completion-listener latch is structural re-evaluation evidence:
     * {@code notifyCheckpointCompleted} fires after
     * {@code scheduleRetentionCleanup()} in {@code onCompletePersistSuccess}, so
     * returning from this method proves the completion path already made the
     * retention trigger/coalesce decision.
     */
    private CompletedCheckpoint completeOne(CheckpointCoordinator coord) throws Exception {
        PendingCheckpoint pending = coord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        org.junit.jupiter.api.Assertions.assertNotNull(pending);
        CompletionLatch latch = new CompletionLatch();
        coord.addListener(latch);
        coord.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
        coord.acknowledgeTask(LOC_2, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));
        CompletedCheckpoint completed = pending.getCompletableFuture().get(30, TimeUnit.SECONDS);
        assertTrue(latch.latch.await(10, TimeUnit.SECONDS), "completion listeners must fire");
        assertEquals(pending.getCheckpointId(), latch.completedId);
        return completed;
    }

    /**
     * TestAwait-backed bounded async predicate — 确定性条件 + 挂死保护上限（60s）。
     * Returns false on timeout so callers keep their own assertion messages with
     * live state; the timeout is hang protection only.
     */
    private static boolean await(java.util.function.BooleanSupplier predicate, String what) {
        try {
            TestAwait.until(what, predicate);
            return true;
        } catch (AssertionError e) {
            return false;
        }
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
            assertTrue(await(() -> storage.size() <= 2, "retention must converge"),
                    "retained set must converge to <= maxRetained=2 within 10s (was "
                            + storage.size() + ")");
            // The NEWEST checkpoints are retained (deletion always targets the oldest).
            assertTrue(await(() -> storage.checkpoints.containsKey(newest)
                            && storage.checkpoints.containsKey(secondNewest), "newest retained"),
                    "retained set must be the two newest epochs {" + secondNewest + "," + newest
                            + "}, was " + storage.checkpoints.keySet());
            assertTrue(storage.deleteCount.get() >= 3,
                    "three oldest checkpoints must have been deleted (deleteCount="
                            + storage.deleteCount.get() + ")");

            // Item 33: the manifest plane converges in the SAME retention rounds and
            // keeps the SAME newest epochs (dual-plane bound, §9.2 D2).
            assertTrue(await(() -> storage.manifestSize() <= 2, "manifests converge"),
                    "retained manifest set must converge to <= maxRetained=2 within 10s (was "
                            + storage.manifestSize() + ")");
            assertTrue(await(() -> storage.manifests.containsKey(newest)
                            && storage.manifests.containsKey(secondNewest), "newest manifests retained"),
                    "retained manifest set must be the two newest epochs {" + secondNewest + "," + newest
                            + "}, was " + storage.manifests.keySet());

            // Wiring verification (plan guide #23): the REAL completion path
            // (trigger→ACK→persist→retention) actually invoked the prune face with
            // the coordinator identity and configured bound — not just its existence.
            assertTrue(storage.pruneCount.get() >= 3,
                    "retention rounds must have invoked pruneEpochManifests (pruneCount="
                            + storage.pruneCount.get() + ")");
            for (String arg : storage.pruneArgs) {
                assertEquals(JOB_ID + "/" + PIPELINE_ID + "/2", arg,
                        "every prune call must carry the coordinator jobId/pipelineId and maxRetained=2");
            }
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
        // Slow retention I/O injected as a deterministic latch-held getAllCheckpoints
        // (no real-time delay) — stores stay fast, isolating retention's contribution
        // to any blocking of the completion path.
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        storage.getAllEnterLatch = entered;
        storage.getAllReleaseLatch = release;
        CheckpointCoordinator coord = newCoordinator(storage, asyncConfig(1));
        try {
            // cp1: durable; its retention run enters the (now blocked) getAllCheckpoints.
            completeOne(coord);
            assertTrue(entered.await(10, TimeUnit.SECONDS),
                    "retention must have entered getAllCheckpoints after the first completion");

            // cp2 completes while the retention round is verifiably still in flight
            // (run #1 is parked on the unreleased latch). Under the old (in-monitor)
            // behavior the coordinator monitor was held for the whole slow getAll+delete,
            // so this completion could never happen before the retention I/O finished;
            // with retention off the monitor the future completes while the latch is
            // still held — structural proof, no elapsed-time threshold.
            PendingCheckpoint p2 = coord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            org.junit.jupiter.api.Assertions.assertNotNull(p2);
            coord.acknowledgeTask(LOC_1, p2.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
            coord.acknowledgeTask(LOC_2, p2.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));
            CompletedCheckpoint cp2 = p2.getCompletableFuture().get(30, TimeUnit.SECONDS);
            org.junit.jupiter.api.Assertions.assertNotNull(cp2);
            final long newest = cp2.getCheckpointId();
            assertEquals(1, release.getCount(),
                    "retention round #1 must still be in flight (latch held) — cp2 completed "
                            + "without waiting for retention I/O");

            // The slow retention path was genuinely exercised and eventually converges
            // (retention deletes cp1: maxRetained=1 keeps only the newest).
            release.countDown();
            assertTrue(await(() -> storage.deleteCount.get() >= 1, "slow delete ran"),
                    "slow retention delete must eventually run (deleteCount="
                            + storage.deleteCount.get() + ")");
            assertTrue(await(() -> storage.size() <= 1, "converge after slow io"),
                    "retained set must converge to <= 1 after slow retention I/O (was "
                            + storage.size() + ")");
            assertTrue(storage.checkpoints.containsKey(newest),
                    "the newest checkpoint must be the retained one");

            // Item 33: the manifest plane converges too — slow retention I/O delays
            // but never blocks the dual-plane bound (eventual consistency).
            assertTrue(await(() -> storage.manifestSize() <= 1, "manifests converge after slow io"),
                    "retained manifest set must converge to <= 1 after slow retention I/O (was "
                            + storage.manifestSize() + ")");
            assertTrue(storage.manifests.containsKey(newest),
                    "the newest manifest must be the retained one");
        } finally {
            release.countDown();
            coord.shutdown();
        }
    }

    // ------------------------------------------------------------------
    // D1(f): serialization guard — retention runs never overlap
    // ------------------------------------------------------------------

    @Test
    void retentionRunsAreSerializedUnderRapidCompletions() throws Exception {
        RetentionStorage storage = new RetentionStorage();
        // Block run #1 inside getAllCheckpoints with a latch (deterministic in-flight
        // window instead of an injected real-time delay): while it is parked, the
        // remaining completions are processed by the coordinator. A multi-threaded
        // (non-serialized) retention executor would enter a second getAllCheckpoints
        // during that window — caught by both the getAllCount and the peak probe.
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        storage.getAllEnterLatch = entered;
        storage.getAllReleaseLatch = release;
        CheckpointCoordinator coord = newCoordinator(storage, asyncConfig(3));
        try {
            completeOne(coord);
            assertTrue(entered.await(10, TimeUnit.SECONDS),
                    "first retention run must enter getAllCheckpoints");
            for (int i = 0; i < 7; i++) {
                completeOne(coord);
            }
            // 结构性再评估证据：每次 completeOne 都等 completion listener 触发
            // （生产在 scheduleRetentionCleanup 之后才 notify listeners）——全部
            // 完成/触发决定均已作出，run #1 仍阻塞时 getAllCount 必须保持 1。
            assertEquals(1, storage.getAllCount.get(),
                    "while run #1 is parked, no second retention run may enter (serialized)");

            release.countDown();
            assertTrue(await(() -> storage.size() <= 3, "converge"),
                    "retained set must converge to <= maxRetained=3 (was " + storage.size() + ")");
            assertEquals(1, storage.peakInFlightGetAll.get(),
                    "retention runs must be serialized (peak concurrent getAllCheckpoints="
                            + storage.peakInFlightGetAll.get() + ")");

            // Item 33: manifest plane bounded under the same rapid completions.
            assertTrue(await(() -> storage.manifestSize() <= 3, "manifests converge"),
                    "retained manifest set must converge to <= maxRetained=3 (was "
                            + storage.manifestSize() + ")");
        } finally {
            release.countDown();
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
            // 结构性再评估证据：completeOne 已等 completion listener 触发（生产在
            // scheduleRetentionCleanup 之后才 notify listeners），即 cp2 的完成路径
            // 已作出 retention 触发/合并决定；run #1 仍阻塞在 getAllReleaseLatch 上，
            // 直接断言 getAllCount 保持为 1（coalesce 语义），无需固定观察窗。
            assertEquals(1, storage.getAllCount.get(),
                    "the in-flight retention run must still be the only one (coalesced trigger)");

            // ... and the trailing re-run must fire after run #1 finishes.
            release.countDown();
            assertTrue(await(() -> storage.getAllCount.get() >= 2, "trailing re-run"),
                    "the trailing retention re-run must execute after the in-flight run "
                            + "(getAllCount=" + storage.getAllCount.get() + ")");
            assertTrue(await(() -> storage.size() <= 1, "converge"),
                    "retained set must converge to <= 1 (was " + storage.size() + ")");
            assertTrue(storage.checkpoints.containsKey(newest),
                    "the newest checkpoint must be the retained one");

            // Item 33: the manifests completed during the blocked retention run are
            // covered by the trailing re-run too — both planes converge to the newest.
            assertTrue(await(() -> storage.manifestSize() <= 1, "manifests converge"),
                    "retained manifest set must converge to <= 1 after the trailing re-run (was "
                            + storage.manifestSize() + ")");
            assertTrue(storage.manifests.containsKey(newest),
                    "the newest manifest must be the retained one");
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
            assertTrue(await(() -> storage.getAllCount.get() >= 1, "retention attempted"),
                    "retention must have been attempted");
            assertEquals(2, storage.size(),
                    "with retention failing, the retained set stays over maxRetained");

            // Self-heal: the next completion re-triggers retention (natural retry);
            // with the failure cleared it converges.
            storage.failGetAll = false;
            long newest = completeOne(coord).getCheckpointId();
            assertTrue(await(() -> storage.size() <= 1, "self-heal converge"),
                    "retention must self-heal on the next completion (was " + storage.size() + ")");
            assertTrue(storage.checkpoints.containsKey(newest),
                    "the newest checkpoint must be the retained one after self-heal");

            // Item 33: the manifest plane self-heals on the same next completion —
            // the round that failed never pruned, the healed round prunes both planes.
            assertTrue(await(() -> storage.manifestSize() <= 1, "manifest self-heal converge"),
                    "manifest retention must self-heal on the next completion (was "
                            + storage.manifestSize() + ")");
            assertTrue(storage.manifests.containsKey(newest),
                    "the newest manifest must be the retained one after self-heal");
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

            // Item 33 (D1(d) inline semantics): manifest pruning runs on the SAME
            // ACK caller stack as deleteCheckpoint (same point, same style) and has
            // already converged before the ACK returned.
            assertEquals(1, storage.manifestSize(),
                    "sync-fallback manifest retention must have converged inline (was "
                            + storage.manifestSize() + ")");
            assertEquals(caller, storage.pruneThread.get(),
                    "sync-fallback pruneEpochManifests must run on the ACK caller thread");
            assertTrue(storage.manifests.containsKey(newest),
                    "the newest manifest must be the retained one");
        } finally {
            coord.shutdown();
        }
    }

    // ------------------------------------------------------------------
    // Item 33 focused ④: manifest prune failure → WARN + next-round self-heal
    // ------------------------------------------------------------------

    @Test
    void manifestPruneFailureWarnsAndSelfHealsOnNextCompletion() throws Exception {
        RetentionStorage storage = new RetentionStorage();
        storage.failPrune = true;
        CheckpointCoordinator coord = newCoordinator(storage, asyncConfig(1));
        try {
            // Three completions with the manifest prune persistently failing: the
            // checkpoint plane still converges (independent failure containment —
            // the prune failure must not break the checkpoint-plane deletion), the
            // completion futures are unaffected, and the manifest plane stays over
            // the bound (WARN-contained, no propagation, no silent skip).
            long newest = -1;
            for (int i = 0; i < 3; i++) {
                newest = completeOne(coord).getCheckpointId();
            }
            assertTrue(await(() -> storage.size() <= 1, "checkpoint plane converges"),
                    "checkpoint plane must converge independently of manifest prune failures (was "
                            + storage.size() + ")");
            assertTrue(await(() -> storage.pruneCount.get() >= 1, "prune attempted"),
                    "manifest prune must have been attempted");
            assertEquals(3, storage.manifestSize(),
                    "with prune failing, the manifest plane stays over maxRetained (no silent skip)");

            // Self-heal: the next completion re-triggers retention; with the failure
            // cleared the manifest plane converges and keeps the newest.
            storage.failPrune = false;
            newest = completeOne(coord).getCheckpointId();
            assertTrue(await(() -> storage.manifestSize() <= 1, "manifest self-heal"),
                    "manifest retention must self-heal on the next completion (was "
                            + storage.manifestSize() + ")");
            assertTrue(storage.manifests.containsKey(newest),
                    "the newest manifest must be the retained one after self-heal");
        } finally {
            coord.shutdown();
        }
    }

    // ------------------------------------------------------------------
    // Item 33 focused ⑤: in-flight manifest margin — briefly over N, then
    // convergence without ever mis-deleting the newest
    // ------------------------------------------------------------------

    @Test
    void inFlightManifestMarginConvergesWithoutDeletingNewest() throws Exception {
        RetentionStorage storage = new RetentionStorage();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        storage.getAllEnterLatch = entered;
        storage.getAllReleaseLatch = release;
        CheckpointCoordinator coord = newCoordinator(storage, asyncConfig(2));
        try {
            // cp1's retention run blocks inside getAllCheckpoints; cp2..cp4 complete
            // while it is in flight — their manifests are legitimately OVER the
            // bound for a while (in-flight margin, eventual consistency).
            long newest = completeOne(coord).getCheckpointId();
            assertTrue(entered.await(10, TimeUnit.SECONDS),
                    "first retention run must enter getAllCheckpoints");
            for (int i = 0; i < 3; i++) {
                newest = completeOne(coord).getCheckpointId();
            }
            assertTrue(storage.manifestSize() > 2,
                    "manifests completed during a blocked retention round may briefly exceed the bound (was "
                            + storage.manifestSize() + ")");

            // After the round (plus its trailing re-run) both planes converge to the
            // newest two — the in-flight margin never turns into newest mis-deletion.
            release.countDown();
            final long newestFinal = newest;
            assertTrue(await(() -> storage.manifestSize() <= 2, "manifest margin converge"),
                    "manifest plane must converge after the blocked retention round (was "
                            + storage.manifestSize() + ")");
            assertTrue(await(() -> storage.size() <= 2, "checkpoint converge"),
                    "checkpoint plane must converge after the blocked retention round (was "
                            + storage.size() + ")");
            assertTrue(storage.manifests.containsKey(newestFinal),
                    "the newest manifest must never be pruned by the in-flight margin convergence");
            assertTrue(storage.checkpoints.containsKey(newestFinal),
                    "the newest checkpoint must never be deleted by the in-flight margin convergence");
        } finally {
            release.countDown();
            coord.shutdown();
        }
    }
}
