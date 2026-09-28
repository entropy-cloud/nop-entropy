/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A2 regression (plan 01 quality-perf Phase 2): an aborted epoch must not
 * linger in {@code checkpointSuccessMap}.
 *
 * <p>Pre-fix, {@code abortPendingCheckpoint} recorded the terminal marker via
 * {@code notifyParticipantsFinishCommit(id, false)} but only the success path
 * and the failed-commit retry cycle removed entries — so an abort with no
 * failing participants left the entry in the map forever: unbounded growth on
 * jobs with frequent timeout aborts.
 */
class TestCheckpointAbortMarkerCleanup {

    private static final String JOB_ID = "abort-cleanup-job";

    @TempDir
    Path tempDir;

    private CheckpointCoordinator coordinator;

    @BeforeEach
    void setUp() {
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true).checkpointInterval(1000L)
                .checkpointTimeout(10000L).maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3).build();
        coordinator = new CheckpointCoordinator(
                JOB_ID, "pipeline-0", idCounter, storage, config);
        // Seed one task-to-acknowledge so tryTriggerPendingCheckpoint passes the
        // NO_TASKS_TO_ACK gate (no checkpoint plan is wired in this focused test).
        coordinator.setTasksToAcknowledge(java.util.Collections.singletonList(
                new io.nop.stream.core.checkpoint.TaskLocation(JOB_ID, "pipeline-0", "v0", 0)));
    }

    @AfterEach
    void tearDown() {
        coordinator.shutdown();
    }

    @Test
    void abortDropsTerminalMarkerWhenNoCommitFailed() {
        RecordingParticipant participant = new RecordingParticipant();
        coordinator.addParticipant(participant);

        PendingCheckpoint checkpoint = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        org.junit.jupiter.api.Assertions.assertNotNull(checkpoint,
                "trigger must register a pending checkpoint for the abort path");
        long checkpointId = checkpoint.getCheckpointId();

        coordinator.abortPendingCheckpoint(checkpoint, "test-abort");

        assertTrue(participant.finishCommits.containsKey(checkpointId),
                "abort notifies participants with finishCommit");
        assertFalse(participant.finishCommits.get(checkpointId), "abort replays finishCommit(false)");
        assertFalse(coordinator.checkpointSuccessMap.containsKey(checkpointId),
                "aborted epoch must not linger in checkpointSuccessMap (pre-fix: entry "
                        + "stayed forever on aborts with no failing participants)");
    }

    @Test
    void abortKeepsMarkerWhileCommitRetriesPending() {
        // finishCommit throws → the participant registers for the retry cycle;
        // the marker must SURVIVE so the retry replays success=false, not true.
        FailingParticipant participant = new FailingParticipant();
        coordinator.addParticipant(participant);

        PendingCheckpoint checkpoint = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        org.junit.jupiter.api.Assertions.assertNotNull(checkpoint,
                "trigger must register a pending checkpoint for the abort path");
        long checkpointId = checkpoint.getCheckpointId();

        coordinator.abortPendingCheckpoint(checkpoint, "test-abort");

        assertTrue(coordinator.checkpointSuccessMap.containsKey(checkpointId),
                "marker must stay while failed-commit retries may still replay it "
                        + "(pre-fix removal would make the retry default to success=true)");
        assertFalse(coordinator.checkpointSuccessMap.get(checkpointId),
                "the replayed flag for an aborted epoch must be success=false");
        assertEquals(1, coordinator.failedCommitParticipants.size(),
                "the failing participant must be registered for the retry cycle");
        // The retry cycle's own remove-on-success behavior is pre-existing
        // (unchanged by A2) and runs inside the coordinator's completion path.
    }

    /** Records finishCommit invocations keyed by epoch. */
    static class RecordingParticipant implements CheckpointParticipant {
        final java.util.Map<Long, Boolean> finishCommits = new ConcurrentHashMap<>();

        @Override
        public TaskStateSnapshot saveState(long epochId) {
            return null;
        }

        @Override
        public void prepareCommit(long epochId) {
        }

        @Override
        public void finishCommit(long epochId, boolean success) {
            finishCommits.put(epochId, success);
        }

        @Override
        public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {
        }
    }

    /**
     * A2' regression (plan 366 Phase 2): a persist-FAILED epoch is terminal,
     * same as an aborted one — the marker recorded by
     * {@code notifyParticipantsFinishCommit(id, false)} must not linger in
     * {@code checkpointSuccessMap} when no commit retry is pending. Pre-fix
     * only the abort path cleaned up; a storage failure on the completion path
     * left the entry forever (unbounded growth on jobs with failing storage).
     */
    @Test
    void persistFailureDropsTerminalMarkerWhenNoCommitFailed(@org.junit.jupiter.api.io.TempDir java.nio.file.Path failDir) {
        // Storage whose storeCheckPoint always fails → the sync completion path
        // funnels into onCompletePersistFailure inline.
        io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage failingStorage =
                new io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage(
                        failDir.resolve("no-such-parent").resolve("blocked").toString()) {
                    @Override
                    public String storeCheckPoint(io.nop.stream.core.checkpoint.CompletedCheckpoint checkpoint)
                            throws io.nop.stream.core.checkpoint.storage.CheckpointStorageException {
                        throw new io.nop.stream.core.checkpoint.storage.CheckpointStorageException(
                                io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_ERROR,
                                new java.io.IOException("simulated persist failure (A2')"))
                                .param("detail", "storeCheckPoint failed");
                    }
                };
        CheckpointConfig syncConfig = CheckpointConfig.builder()
                .checkpointEnabled(true).checkpointInterval(1000L)
                .checkpointTimeout(10000L).maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3)
                .asyncSnapshotEnabled(false)
                .build();
        CheckpointCoordinator failingCoordinator = new CheckpointCoordinator(
                JOB_ID + "-a2", "pipeline-0", new CheckpointIDCounter(), failingStorage, syncConfig);
        failingCoordinator.setTasksToAcknowledge(java.util.Collections.singletonList(
                new io.nop.stream.core.checkpoint.TaskLocation(JOB_ID + "-a2", "pipeline-0", "v0", 0)));
        try {
            RecordingParticipant participant = new RecordingParticipant();
            failingCoordinator.addParticipant(participant);

            PendingCheckpoint checkpoint = failingCoordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            org.junit.jupiter.api.Assertions.assertNotNull(checkpoint,
                    "trigger must register a pending checkpoint for the persist-failure path");
            long checkpointId = checkpoint.getCheckpointId();

            // Fully acknowledge the single task through the COORDINATOR (the
            // last ACK triggers completePendingCheckpoint → persist fails
            // inline in sync mode → A2' cleanup). Acknowledging the pending
            // directly would bypass the coordinator's completion path.
            failingCoordinator.acknowledgeTask(
                    new io.nop.stream.core.checkpoint.TaskLocation(JOB_ID + "-a2", "pipeline-0", "v0", 0),
                    checkpointId,
                    TaskStateSnapshot.empty(
                            new io.nop.stream.core.checkpoint.TaskLocation(JOB_ID + "-a2", "pipeline-0", "v0", 0)));

            assertTrue(participant.finishCommits.containsKey(checkpointId),
                    "persist failure notifies participants with finishCommit(false)");
            assertFalse(failingCoordinator.checkpointSuccessMap.containsKey(checkpointId),
                    "persist-failed epoch must not linger in checkpointSuccessMap (A2': fail path "
                            + "missed the abort-path cleanup)");
        } finally {
            failingCoordinator.shutdown();
        }
    }

    /** finishCommit that can be toggled to fail once for the retry path. */
    static class FailingParticipant implements CheckpointParticipant {
        volatile boolean failNextCommit = true;

        @Override
        public TaskStateSnapshot saveState(long epochId) {
            return null;
        }

        @Override
        public void prepareCommit(long epochId) {
        }

        @Override
        public void finishCommit(long epochId, boolean success) throws java.io.IOException {
            if (failNextCommit) {
                throw new java.io.IOException("simulated commit failure");
            }
        }

        @Override
        public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {
        }
    }
}
