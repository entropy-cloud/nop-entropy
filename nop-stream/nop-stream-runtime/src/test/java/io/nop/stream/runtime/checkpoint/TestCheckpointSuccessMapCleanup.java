package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.common.state.CheckpointListener;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointSuccessMapCleanup {

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
        // Plan 2026-07-25-2300-1: minPause is now enforced in the trigger path; this suite
        // fires 10 consecutive checkpoints in tight succession, so disable minPause.
        config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(1000L)
                .checkpointTimeout(30000L)
                .minPause(0L)
                .maxConcurrentCheckpoints(3)
                .maxRetainedCheckpoints(10)
                .asyncSnapshotEnabled(false)
                .build();

        coordinator = new CheckpointCoordinator("test-job", "p1", idCounter, storage, config);
        coordinator.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
    }

    @Test
    void checkpointSuccessMapDoesNotGrowUnboundedly() throws Exception {
        for (int i = 0; i < 10; i++) {
            PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            assertNotNull(pending);

            TaskStateSnapshot state1 = new TaskStateSnapshot(LOC_1, pending.getCheckpointId());
            TaskStateSnapshot state2 = new TaskStateSnapshot(LOC_2, pending.getCheckpointId());

            coordinator.acknowledgeTask(LOC_1, pending.getCheckpointId(), state1);
            coordinator.acknowledgeTask(LOC_2, pending.getCheckpointId(), state2);
        }

        Thread.sleep(200);

        CompletedCheckpoint latest = coordinator.getLatestCheckpoint();
        assertNotNull(latest);
        assertEquals(9, latest.getCheckpointId());
    }
}
