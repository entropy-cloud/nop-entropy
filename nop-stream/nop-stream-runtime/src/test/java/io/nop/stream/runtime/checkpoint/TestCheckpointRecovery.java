/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointRecovery {

    private static Path tempDir;
    private ICheckpointStorage storage;
    private CheckpointCoordinator coordinator;
    private CheckpointIDCounter idCounter;

    @BeforeAll
    static void setupClass() throws Exception {
        tempDir = Files.createTempDirectory("checkpoint-recovery-test");
    }

    @AfterAll
    static void teardownClass() throws Exception {
        deleteDirectory(tempDir.toFile());
    }

    @BeforeEach
    void setup() {
        storage = new LocalFileCheckpointStorage(tempDir.toString());
        idCounter = new CheckpointIDCounter();
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointInterval(1000);
        coordinator = new CheckpointCoordinator(1L, 0, idCounter, storage, config);
        coordinator.setTasksToAcknowledge(java.util.Arrays.asList(1L, 2L));
    }

    @AfterEach
    void teardown() throws Exception {
        coordinator.shutdown();
        storage.deleteAllCheckpoints(1);
    }

    @Test
    void testBasicCheckpointAndRecovery() throws Exception {
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        long checkpointId = pending.getCheckpointId();

        TaskStateSnapshot state1 = TaskStateSnapshot.builder(1L)
                .putOperatorState("state1", "data1".getBytes())
                .build();
        TaskStateSnapshot state2 = TaskStateSnapshot.builder(2L)
                .putOperatorState("state2", "data2".getBytes())
                .build();

        coordinator.acknowledgeTask(1L, checkpointId, state1);
        coordinator.acknowledgeTask(2L, checkpointId, state2);

        // Wait for checkpoint to complete
        Thread.sleep(200);

        CompletedCheckpoint completed = coordinator.getLatestCheckpoint();
        assertNotNull(completed);
        assertEquals(checkpointId, completed.getCheckpointId());

        coordinator.shutdown();

        // Create new coordinator and restore
        CheckpointIDCounter recoveredIdCounter = new CheckpointIDCounter();
        CheckpointCoordinator recoveredCoordinator = new CheckpointCoordinator(1L, 0, recoveredIdCounter, storage, new CheckpointConfig());
        recoveredCoordinator.restoreFromCheckpoint();

        CompletedCheckpoint restored = recoveredCoordinator.getLatestCheckpoint();
        assertNotNull(restored);
        assertEquals(checkpointId, restored.getCheckpointId());
        assertTrue(restored.isRestored());

        TaskStateSnapshot restoredState1 = restored.getTaskState(1L);
        assertNotNull(restoredState1);
        assertArrayEquals("data1".getBytes(), restoredState1.getOperatorState("state1"));

        TaskStateSnapshot restoredState2 = restored.getTaskState(2L);
        assertNotNull(restoredState2);
        assertArrayEquals("data2".getBytes(), restoredState2.getOperatorState("state2"));

        recoveredCoordinator.shutdown();
    }

    @Test
    void testMultipleCheckpointRecovery() throws Exception {
        for (int i = 0; i < 3; i++) {
            CheckpointIDCounter iterIdCounter = new CheckpointIDCounter();
            CheckpointCoordinator iterCoordinator = new CheckpointCoordinator(1L, 0, iterIdCounter, storage, new CheckpointConfig());
            iterCoordinator.setTasksToAcknowledge(java.util.Arrays.asList(1L, 2L));

            PendingCheckpoint pending = iterCoordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            assertNotNull(pending);
            long checkpointId = pending.getCheckpointId();

            TaskStateSnapshot state1 = TaskStateSnapshot.builder(1L)
                    .putOperatorState("iteration", String.valueOf(i).getBytes())
                    .build();
            TaskStateSnapshot state2 = TaskStateSnapshot.builder(2L)
                    .putOperatorState("iteration", String.valueOf(i).getBytes())
                    .build();

            iterCoordinator.acknowledgeTask(1L, checkpointId, state1);
            iterCoordinator.acknowledgeTask(2L, checkpointId, state2);
            Thread.sleep(100);

            iterCoordinator.shutdown();
        }

        Thread.sleep(100);

        // Restore and verify last checkpoint
        CheckpointIDCounter recoveredIdCounter = new CheckpointIDCounter();
        CheckpointCoordinator recoveredCoordinator = new CheckpointCoordinator(1L, 0, recoveredIdCounter, storage, new CheckpointConfig());
        recoveredCoordinator.restoreFromCheckpoint();

        CompletedCheckpoint restored = recoveredCoordinator.getLatestCheckpoint();
        assertNotNull(restored);

        byte[] restoredState = restored.getTaskState(1L).getOperatorState("iteration");
        assertEquals("2", new String(restoredState));

        recoveredCoordinator.shutdown();
    }

    @Test
    void testCheckpointWithKeyedState() throws Exception {
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        long checkpointId = pending.getCheckpointId();

        TaskStateSnapshot state1 = TaskStateSnapshot.builder(1L)
                .putOperatorState("operator", "op-data".getBytes())
                .build();
        TaskStateSnapshot state2 = TaskStateSnapshot.builder(2L)
                .putOperatorState("operator", "op-data-2".getBytes())
                .build();

        coordinator.acknowledgeTask(1L, checkpointId, state1);
        coordinator.acknowledgeTask(2L, checkpointId, state2);

        Thread.sleep(200);

        coordinator.shutdown();

        CheckpointIDCounter recoveredIdCounter = new CheckpointIDCounter();
        CheckpointCoordinator recoveredCoordinator = new CheckpointCoordinator(1L, 0, recoveredIdCounter, storage, new CheckpointConfig());
        recoveredCoordinator.restoreFromCheckpoint();

        CompletedCheckpoint restored = recoveredCoordinator.getLatestCheckpoint();
        assertNotNull(restored);

        TaskStateSnapshot restoredState = restored.getTaskState(1L);
        assertNotNull(restoredState);

        assertArrayEquals("op-data".getBytes(), restoredState.getOperatorState("operator"));

        recoveredCoordinator.shutdown();
    }

    @Test
    void testCheckpointAbortAndRecovery() throws Exception {
        // First checkpoint - will be aborted
        PendingCheckpoint pending1 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending1);
        long checkpointId1 = pending1.getCheckpointId();

        TaskStateSnapshot state1 = TaskStateSnapshot.builder(1L)
                .putOperatorState("data", "task1-data".getBytes())
                .build();
        coordinator.acknowledgeTask(1L, checkpointId1, state1);

        Thread.sleep(100);

        // Abort the first checkpoint
        coordinator.abortPendingCheckpoint(pending1, "Test abort");

        // Second checkpoint - should complete
        PendingCheckpoint pending2 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending2);
        long checkpointId2 = pending2.getCheckpointId();

        TaskStateSnapshot state1v2 = TaskStateSnapshot.builder(1L)
                .putOperatorState("data", "task1-data-v2".getBytes())
                .build();
        TaskStateSnapshot state2v2 = TaskStateSnapshot.builder(2L)
                .putOperatorState("data", "task2-data".getBytes())
                .build();

        coordinator.acknowledgeTask(1L, checkpointId2, state1v2);
        coordinator.acknowledgeTask(2L, checkpointId2, state2v2);

        Thread.sleep(200);

        CompletedCheckpoint completed = coordinator.getLatestCheckpoint();
        assertNotNull(completed);
        assertEquals(checkpointId2, completed.getCheckpointId());

        coordinator.shutdown();

        CheckpointIDCounter recoveredIdCounter = new CheckpointIDCounter();
        CheckpointCoordinator recoveredCoordinator = new CheckpointCoordinator(1L, 0, recoveredIdCounter, storage, new CheckpointConfig());
        recoveredCoordinator.restoreFromCheckpoint();

        CompletedCheckpoint restored = recoveredCoordinator.getLatestCheckpoint();
        assertNotNull(restored);
        assertEquals(checkpointId2, restored.getCheckpointId());

        assertArrayEquals("task1-data-v2".getBytes(), restored.getTaskState(1L).getOperatorState("data"));
        assertArrayEquals("task2-data".getBytes(), restored.getTaskState(2L).getOperatorState("data"));

        recoveredCoordinator.shutdown();
    }

    @Test
    void testSavepointRecovery() throws Exception {
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.SAVEPOINT);
        assertNotNull(pending);
        long checkpointId = pending.getCheckpointId();

        TaskStateSnapshot state1 = TaskStateSnapshot.builder(1L)
                .putOperatorState("savepoint-data", "important".getBytes())
                .build();
        TaskStateSnapshot state2 = TaskStateSnapshot.builder(2L)
                .putOperatorState("savepoint-data", "important-2".getBytes())
                .build();

        coordinator.acknowledgeTask(1L, checkpointId, state1);
        coordinator.acknowledgeTask(2L, checkpointId, state2);

        Thread.sleep(200);

        CompletedCheckpoint savepoint = coordinator.getLatestCheckpoint();
        assertNotNull(savepoint);
        assertEquals(CheckpointType.SAVEPOINT, savepoint.getCheckpointType());

        coordinator.shutdown();

        CheckpointIDCounter recoveredIdCounter = new CheckpointIDCounter();
        CheckpointCoordinator recoveredCoordinator = new CheckpointCoordinator(1L, 0, recoveredIdCounter, storage, new CheckpointConfig());
        recoveredCoordinator.restoreFromCheckpoint();

        CompletedCheckpoint restored = recoveredCoordinator.getLatestCheckpoint();
        assertNotNull(restored);
        assertEquals(CheckpointType.SAVEPOINT, restored.getCheckpointType());

        recoveredCoordinator.shutdown();
    }

    private static void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        dir.delete();
    }
}
