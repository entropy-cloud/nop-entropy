package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TestE2EMultipleJobsIsolation {

    @TempDir
    Path tempDir;

    @Test
    void testJobIdIsolation() throws Exception {
        String jobA = "job-a";
        String jobB = "job-b";
        String pipelineId = "1";

        TaskLocation locA = new TaskLocation(jobA, pipelineId, "v0", 0);
        TaskLocation locB = new TaskLocation(jobB, pipelineId, "v0", 0);

        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());

        CheckpointIDCounter counterA = new CheckpointIDCounter();
        CheckpointConfig configA = new CheckpointConfig();
        configA.setJobId(jobA);
        configA.setPipelineId(pipelineId);
        CheckpointCoordinator coordinatorA = new CheckpointCoordinator(jobA, pipelineId, counterA, storage, configA);
        coordinatorA.registerTask(locA);

        PendingCheckpoint pendingA = coordinatorA.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pendingA);

        TaskStateSnapshot stateA = TaskStateSnapshot.builder(locA)
                .checkpointId(pendingA.getCheckpointId())
                .putOperatorState("source-offset", "100")
                .build();
        coordinatorA.acknowledgeTask(locA, pendingA.getCheckpointId(), stateA);

        CompletedCheckpoint completedA = pendingA.getCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNotNull(completedA);
        assertEquals(jobA, completedA.getJobId());

        coordinatorA.shutdown();

        CheckpointIDCounter counterB = new CheckpointIDCounter();
        CheckpointConfig configB = new CheckpointConfig();
        configB.setJobId(jobB);
        configB.setPipelineId(pipelineId);
        CheckpointCoordinator coordinatorB = new CheckpointCoordinator(jobB, pipelineId, counterB, storage, configB);
        coordinatorB.registerTask(locB);

        PendingCheckpoint pendingB = coordinatorB.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pendingB);

        TaskStateSnapshot stateB = TaskStateSnapshot.builder(locB)
                .checkpointId(pendingB.getCheckpointId())
                .putOperatorState("source-offset", "200")
                .build();
        coordinatorB.acknowledgeTask(locB, pendingB.getCheckpointId(), stateB);

        CompletedCheckpoint completedB = pendingB.getCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNotNull(completedB);
        assertEquals(jobB, completedB.getJobId());

        coordinatorB.shutdown();

        CompletedCheckpoint restoredA = storage.getLatestCheckpoint(jobA, pipelineId);
        assertNotNull(restoredA);
        assertEquals(jobA, restoredA.getJobId());
        assertEquals("100", restoredA.getTaskState(locA).getOperatorState("source-offset"));

        CompletedCheckpoint restoredB = storage.getLatestCheckpoint(jobB, pipelineId);
        assertNotNull(restoredB);
        assertEquals(jobB, restoredB.getJobId());
        assertEquals("200", restoredB.getTaskState(locB).getOperatorState("source-offset"));

        storage.deleteAllCheckpoints(jobA);
        storage.deleteAllCheckpoints(jobB);
    }
}
