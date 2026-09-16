/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.state.CheckpointListener;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 33 Phase 3 (END-TO-END boundedness proof, checkpoint-design §9.2): the REAL
 * coordinator completion loop — {@code trigger → ACK → durable persist
 * (storeCheckPoint + storeEpochManifest) → retention} — over the REAL
 * {@link LocalFileCheckpointStorage} filesystem plane. After N &gt;&gt; maxRetained
 * completions (10 rounds, maxRetained=3) BOTH durable planes converge to the
 * newest {@code maxRetained} artifacts:
 *
 * <ul>
 *   <li>{@code {baseDir}/{jobId}/{pipelineId}/*.checkpoint} — exactly the newest 3
 *       checkpoint files (file-name assertion);</li>
 *   <li>{@code .../*.epoch} — exactly the newest 3 manifest files (file-name
 *       assertion) — the SOAK-3 defect (137 unbounded {@code .epoch} files,
 *       runId {@code 1788456304950-1}) is closed at the coordinator level;</li>
 *   <li>restore read set: {@code loadRetainedEpochManifests(jobId, pipelineId, maxRetained)}
 *       succeeds and returns exactly the newest-N keep set (restore read set ⊆
 *       prune keep set, §9.2 D2), and {@code restoreSharedStateRegistry()} on a
 *       fresh coordinator over the same storage completes — recovery behavior does
 *       not regress.</li>
 * </ul>
 *
 * <p>Flaky-safe assertion protocol: the hard assertions run on the CONVERGED
 * terminal state (bounded async wait after the last completion — retention runs on
 * the dedicated {@code checkpoint-retention-<jobId>} executor and is eventually
 * consistent with the async persist executor). The transient in-flight margin
 * (manifests completing between two retention rounds may briefly exceed the
 * bound) is NOT hard-asserted mid-run here — Phase 2's
 * {@code TestCheckpointRetentionAsync#inFlightManifestMarginConvergesWithoutDeletingNewest}
 * pins that semantics with a controlled retention round.
 */
class TestCheckpointDualPlaneRetentionE2E {

    private static final String JOB_ID = "job-dual-e2e";
    private static final String PIPELINE_ID = "pipe-0";
    private static final TaskLocation LOC_1 = new TaskLocation(JOB_ID, PIPELINE_ID, "v1", 0);
    private static final TaskLocation LOC_2 = new TaskLocation(JOB_ID, PIPELINE_ID, "v2", 1);

    private static final int MAX_RETAINED = 3;
    private static final int COMPLETIONS = 10;

    @TempDir
    Path tmp;

    private static final class CompletionLatch implements CheckpointListener {
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

    private CheckpointCoordinator newCoordinator(LocalFileCheckpointStorage storage) {
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(60_000L)
                .checkpointTimeout(60_000L)
                .minPause(0L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(MAX_RETAINED)
                .asyncSnapshotEnabled(true)
                .asyncSnapshotThreadPoolSize(1)
                .build();
        CheckpointCoordinator coord = new CheckpointCoordinator(
                JOB_ID, PIPELINE_ID, new CheckpointIDCounter(), storage, config);
        coord.setTasksToAcknowledge(java.util.Arrays.asList(LOC_1, LOC_2));
        return coord;
    }

    /** Drive one epoch through the REAL trigger→ACK→durable path and wait for completion. */
    private CompletedCheckpoint completeOne(CheckpointCoordinator coord) throws Exception {
        PendingCheckpoint pending = coord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        CompletionLatch latch = new CompletionLatch();
        coord.addListener(latch);
        coord.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
        coord.acknowledgeTask(LOC_2, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));
        CompletedCheckpoint completed = pending.getCompletableFuture().get(30, TimeUnit.SECONDS);
        assertTrue(latch.latch.await(10, TimeUnit.SECONDS), "completion listeners must fire");
        assertEquals(pending.getCheckpointId(), latch.completedId);
        return completed;
    }

    private static boolean await(java.util.function.BooleanSupplier predicate, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (predicate.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return predicate.getAsBoolean();
    }

    private static TreeSet<String> fileNames(Path pipelineDir, String suffix) throws Exception {
        TreeSet<String> names = new TreeSet<>();
        try (var stream = Files.list(pipelineDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(suffix))
                    .forEach(p -> names.add(p.getFileName().toString()));
        }
        return names;
    }

    @Test
    void dualPlaneRetentionConvergesOnRealLocalFileStorageAndRestoreStillWorks() throws Exception {
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tmp.toString());
        Path pipelineDir = tmp.resolve(JOB_ID).resolve(PIPELINE_ID);

        CheckpointCoordinator coord = newCoordinator(storage);
        long newest = -1;
        try {
            for (int i = 0; i < COMPLETIONS; i++) {
                newest = completeOne(coord).getCheckpointId();
            }
            final long newestFinal = newest;

            assertTrue(await(() -> countFiles(pipelineDir, ".checkpoint") <= MAX_RETAINED, 15_000),
                    "checkpoint files must converge to <= maxRetained=" + MAX_RETAINED + " (was "
                            + countFiles(pipelineDir, ".checkpoint") + " after 10 completions)");
            assertTrue(await(() -> countFiles(pipelineDir, ".epoch") <= MAX_RETAINED, 15_000),
                    ".epoch manifest files must converge to <= maxRetained=" + MAX_RETAINED + " (was "
                            + countFiles(pipelineDir, ".epoch") + " after 10 completions) — "
                            + "the SOAK-3 unbounded-manifest defect must stay closed at the coordinator level");

            TreeSet<String> expected = new TreeSet<>(java.util.Arrays.asList(
                    (newestFinal - 2) + ".checkpoint", (newestFinal - 1) + ".checkpoint",
                    newestFinal + ".checkpoint"));
            assertEquals(expected, fileNames(pipelineDir, ".checkpoint"),
                    "checkpoint plane must hold exactly the newest " + MAX_RETAINED + " files");
            expected = new TreeSet<>(java.util.Arrays.asList(
                    (newestFinal - 2) + ".epoch", (newestFinal - 1) + ".epoch", newestFinal + ".epoch"));
            assertEquals(expected, fileNames(pipelineDir, ".epoch"),
                    "manifest plane must hold exactly the newest " + MAX_RETAINED + " files");
        } finally {
            coord.shutdown();
        }

        // Post-shutdown deterministic verification: shutdown runs synchronous
        // cleanupOldCheckpoints, guaranteeing convergence. This is the authoritative
        // assertion — no machine-speed dependency.
        assertEquals(MAX_RETAINED, countFiles(pipelineDir, ".checkpoint"),
                "checkpoint plane must be exactly maxRetained after shutdown");
        assertEquals(MAX_RETAINED, countFiles(pipelineDir, ".epoch"),
                "manifest plane must be exactly maxRetained after shutdown");

        // Restore read set (§9.2 D2): loadRetainedEpochManifests returns exactly the
        // newest-N keep set — the pruned plane still serves recovery.
        java.util.List<EpochManifest> retained = storage.loadRetainedEpochManifests(
                JOB_ID, PIPELINE_ID, MAX_RETAINED);
        assertEquals(MAX_RETAINED, retained.size(), "restore read set must serve the full newest-N set");
        assertEquals(newest, retained.get(0).getEpochId(), "newest manifest first");
        assertEquals(newest - 1, retained.get(1).getEpochId());
        assertEquals(newest - 2, retained.get(2).getEpochId());
        assertEquals(newest, storage.getLatestCheckpoint(JOB_ID, PIPELINE_ID).getCheckpointId(),
                "latest checkpoint restore path still works");

        // Recovery path does not regress: a fresh coordinator over the SAME storage
        // rebuilds the shared-state registry from the pruned retained manifests
        // (incremental enabled + segment store, per the restore contract).
        CheckpointCoordinator restarted = newCoordinator(storage);
        try {
            restarted.setIncrementalCheckpointEnabled(true);
            restarted.setSegmentStore(new io.nop.stream.core.checkpoint.storage.LocalFileSegmentStore(
                    tmp.resolve("segments")));
            restarted.restoreSharedStateRegistry();
            assertNotNull(restarted.getSharedStateRegistry(),
                    "restoreSharedStateRegistry must rebuild from the pruned retained set");
        } finally {
            restarted.shutdown();
        }
    }

    private static int countFiles(Path pipelineDir, String suffix) {
        if (!Files.isDirectory(pipelineDir)) {
            return 0;
        }
        try (var stream = Files.list(pipelineDir)) {
            return (int) stream.filter(p -> p.getFileName().toString().endsWith(suffix)).count();
        } catch (Exception e) {
            return -1;
        }
    }
}
