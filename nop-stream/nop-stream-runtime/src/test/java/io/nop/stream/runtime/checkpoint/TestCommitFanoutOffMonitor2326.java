/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.runtime.checkpoint.PendingCheckpoint;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R6 CC-01 regression pin (plan 2326): participant commit fan-out (sink
 * finishCommit I/O) and failed-commit retries must run on the dedicated
 * commit-fanout executor — NOT under the coordinator monitor. The pre-fix code
 * invoked {@code retryFailedCommits} + {@code notifyParticipantsFinishCommit}
 * inline inside {@code onCompletePersistSuccess} (a synchronized method), so a
 * slow sink serialized the whole control plane behind it
 * ({@code tryTriggerPendingCheckpoint}/{@code acknowledgeTask} all share the
 * same monitor).
 *
 * <p>Mutation probe: moving the fan-out back inline (direct call under the
 * monitor) turns {@code slowParticipantFinishCommitDoesNotBlockMonitor} red —
 * the monitor probe then blocks for at least the participant's sleep window.
 */
class TestCommitFanoutOffMonitor2326 {

    private static final String JOB_ID = "cc01-job";
    private static final String PIPELINE_ID = "pipeline-0";
    private static final TaskLocation LOC_1 = new TaskLocation(JOB_ID, PIPELINE_ID, "src", 0);
    private static final TaskLocation LOC_2 = new TaskLocation(JOB_ID, PIPELINE_ID, "sink", 0);
    private static final long SLOW_COMMIT_MS = 600L;

    @TempDir
    Path tempDir;

    private static CheckpointConfig config() {
        CheckpointConfig config = new CheckpointConfig();
        config.setJobId(JOB_ID);
        config.setPipelineId(PIPELINE_ID);
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(3600_000L);
        config.setStorageType("local");
        return config;
    }

    @Test
    void slowParticipantFinishCommitDoesNotBlockMonitor(@TempDir Path dir) throws Exception {
        CheckpointCoordinator coord = new CheckpointCoordinator(JOB_ID, PIPELINE_ID,
                new CheckpointIDCounter(), new LocalFileCheckpointStorage(dir.toString()), config());
        coord.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));

        AtomicBoolean slowCommitStarted = new AtomicBoolean(false);
        AtomicBoolean slowCommitFinished = new AtomicBoolean(false);
        coord.addParticipant(new CheckpointParticipant() {
            @Override
            public TaskStateSnapshot saveState(long epochId) {
                return TaskStateSnapshot.empty(LOC_1);
            }

            @Override
            public void prepareCommit(long epochId) {
            }

            @Override
            public void finishCommit(long epochId, boolean success) {
                // Simulate slow sink commit I/O (file fsync / JDBC commit).
                slowCommitStarted.set(true);
                try {
                    Thread.sleep(SLOW_COMMIT_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                slowCommitFinished.set(true);
            }

            @Override
            public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {
            }
        });

        try {
            PendingCheckpoint pending = coord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            assertNotNull(pending);
            coord.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
            coord.acknowledgeTask(LOC_2, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));

            // Wait until the fan-out task has ENTERED the slow commit — from this
            // point the monitor is (post-fix) free even though the commit sleeps.
            long deadline = System.currentTimeMillis() + 5_000;
            while (!slowCommitStarted.get() && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(slowCommitStarted.get(), "fan-out must reach the participant commit");

            // Monitor probe: ONE lightweight synchronized call, timed. Pre-fix
            // (inline fan-out under the monitor) this call blocks ~SLOW_COMMIT_MS
            // (the slow finishCommit runs while the caller holds the monitor);
            // post-fix it returns in single-digit milliseconds.
            long probeStart = System.currentTimeMillis();
            coord.registerTask(LOC_1);
            coord.unregisterTask(LOC_1);
            long probeElapsed = System.currentTimeMillis() - probeStart;
            assertTrue(probeElapsed < SLOW_COMMIT_MS / 2,
                    () -> "monitor blocked " + probeElapsed + "ms while the slow participant commit "
                            + "was in flight — commit fan-out is running under the monitor (CC-01 regression)");
            assertTrue(!slowCommitFinished.get(),
                    "probe precondition: the slow commit should still be in flight");
        } finally {
            coord.shutdown();
            assertTrue(slowCommitFinished.get(),
                    "shutdown grace must drain the queued slow commit");
        }
    }
}
