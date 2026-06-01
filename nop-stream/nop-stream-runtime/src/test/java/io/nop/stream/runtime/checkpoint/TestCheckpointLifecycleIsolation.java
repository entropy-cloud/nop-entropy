package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointLifecycleIsolation {

    private static final TaskLocation LOC_1 = new TaskLocation("1", "1", "v1", 1);
    private static final TaskLocation LOC_2 = new TaskLocation("1", "1", "v2", 2);

    @TempDir
    Path tempDir;

    private CheckpointCoordinator coordinator;
    private LocalFileCheckpointStorage storage;
    private CheckpointIDCounter idCounter;
    private CheckpointConfig config;

    @BeforeEach
    void setUp() {
        storage = new LocalFileCheckpointStorage(tempDir.toString());
        idCounter = new CheckpointIDCounter();
        config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(1000L)
                .checkpointTimeout(5000L)
                .maxConcurrentCheckpoints(2)
                .maxRetainedCheckpoints(3)
                .build();

        coordinator = new CheckpointCoordinator("1", "1", idCounter, storage, config);
        coordinator.setTasksToAcknowledge(java.util.Arrays.asList(LOC_1, LOC_2));
    }

    @AfterEach
    void tearDown() {
        coordinator.shutdown();
    }

    @Test
    void testCompletedCheckpointLateAckReturnsFalse() {
        PendingCheckpoint cp1 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(cp1);
        long cp1Id = cp1.getCheckpointId();

        TaskStateSnapshot state1 = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("op1", "data1".getBytes())
                .build();
        TaskStateSnapshot state2 = TaskStateSnapshot.builder(LOC_2)
                .putOperatorState("op1", "data2".getBytes())
                .build();

        assertTrue(coordinator.acknowledgeTask(LOC_1, cp1Id, state1));
        assertTrue(coordinator.acknowledgeTask(LOC_2, cp1Id, state2));

        CompletedCheckpoint latest = coordinator.getLatestCheckpoint();
        assertNotNull(latest);
        assertEquals(cp1Id, latest.getCheckpointId());

        TaskStateSnapshot lateState = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("op1", "late-data".getBytes())
                .build();
        boolean result = coordinator.acknowledgeTask(LOC_1, cp1Id, lateState);
        assertFalse(result, "Late ACK for completed checkpoint should return false");
    }

    @Test
    void testAbortedCheckpointLateAckReturnsFalse() {
        PendingCheckpoint cp1 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(cp1);
        long cp1Id = cp1.getCheckpointId();

        coordinator.abortPendingCheckpoint(cp1, "Manual abort for test");

        TaskStateSnapshot state = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("op1", "data".getBytes())
                .build();
        boolean result = coordinator.acknowledgeTask(LOC_1, cp1Id, state);
        assertFalse(result, "ACK for aborted checkpoint should return false");
    }

    @Test
    void testConsecutiveCheckpointsAreIndependent() {
        PendingCheckpoint cp1 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(cp1);
        long cp1Id = cp1.getCheckpointId();

        TaskStateSnapshot state1a = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("op1", "cp1-data-1".getBytes())
                .build();
        TaskStateSnapshot state1b = TaskStateSnapshot.builder(LOC_2)
                .putOperatorState("op1", "cp1-data-2".getBytes())
                .build();

        assertTrue(coordinator.acknowledgeTask(LOC_1, cp1Id, state1a));
        assertTrue(coordinator.acknowledgeTask(LOC_2, cp1Id, state1b));

        CompletedCheckpoint cp1Completed = coordinator.getLatestCheckpoint();
        assertNotNull(cp1Completed);
        assertEquals(cp1Id, cp1Completed.getCheckpointId());

        PendingCheckpoint cp2 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(cp2);
        long cp2Id = cp2.getCheckpointId();
        assertNotEquals(cp1Id, cp2Id, "Consecutive checkpoint IDs should differ");

        assertNull(coordinator.getPendingCheckpoint(cp1Id),
                "Checkpoint 1 should no longer be pending after completion");
        assertNotNull(coordinator.getPendingCheckpoint(cp2Id),
                "Checkpoint 2 should be pending");

        TaskStateSnapshot state2a = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("op1", "cp2-data-1".getBytes())
                .build();
        TaskStateSnapshot state2b = TaskStateSnapshot.builder(LOC_2)
                .putOperatorState("op1", "cp2-data-2".getBytes())
                .build();

        assertTrue(coordinator.acknowledgeTask(LOC_1, cp2Id, state2a));
        assertTrue(coordinator.acknowledgeTask(LOC_2, cp2Id, state2b));

        CompletedCheckpoint cp2Completed = coordinator.getLatestCheckpoint();
        assertNotNull(cp2Completed);
        assertEquals(cp2Id, cp2Completed.getCheckpointId());

        assertNotNull(cp2Completed.getTaskStates().get(LOC_1));
        assertNotNull(cp2Completed.getTaskStates().get(LOC_2));
    }

    @Test
    void testCompletedCheckpointSnapshotNotInLaterCheckpoint() {
        PendingCheckpoint cp1 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(cp1);
        long cp1Id = cp1.getCheckpointId();

        byte[] cp1SpecificData = "checkpoint-1-specific".getBytes();
        TaskStateSnapshot state1a = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("op1", cp1SpecificData)
                .build();
        TaskStateSnapshot state1b = TaskStateSnapshot.builder(LOC_2)
                .putOperatorState("op1", "cp1-data".getBytes())
                .build();

        assertTrue(coordinator.acknowledgeTask(LOC_1, cp1Id, state1a));
        assertTrue(coordinator.acknowledgeTask(LOC_2, cp1Id, state1b));

        PendingCheckpoint cp2 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(cp2);
        long cp2Id = cp2.getCheckpointId();

        byte[] cp2SpecificData = "checkpoint-2-specific".getBytes();
        TaskStateSnapshot state2a = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("op1", cp2SpecificData)
                .build();
        TaskStateSnapshot state2b = TaskStateSnapshot.builder(LOC_2)
                .putOperatorState("op1", "cp2-data".getBytes())
                .build();

        assertTrue(coordinator.acknowledgeTask(LOC_1, cp2Id, state2a));
        assertTrue(coordinator.acknowledgeTask(LOC_2, cp2Id, state2b));

        CompletedCheckpoint latest = coordinator.getLatestCheckpoint();
        assertNotNull(latest);
        assertEquals(cp2Id, latest.getCheckpointId());

        TaskStateSnapshot latestTaskState = latest.getTaskStates().get(LOC_1);
        assertNotNull(latestTaskState);
        Object storedOp1 = latestTaskState.getOperatorState("op1");
        assertArrayEquals(cp2SpecificData, (byte[]) storedOp1,
                "Latest checkpoint should contain cp2 data, not cp1 data");
    }
}
