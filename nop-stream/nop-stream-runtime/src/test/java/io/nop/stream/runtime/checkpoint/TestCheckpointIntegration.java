/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.common.state.CheckpointListener;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointIntegration {

    private static final TaskLocation LOC_1 = new TaskLocation("1", "1", "v1", 1);
    private static final TaskLocation LOC_2 = new TaskLocation("1", "1", "v2", 2);

    @TempDir
    Path tempDir;

    private CheckpointCoordinator coordinator;
    private LocalFileCheckpointStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(1000L)
                .checkpointTimeout(30000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(5)
                .build();

        coordinator = new CheckpointCoordinator("1", "1", idCounter, storage, config);
        coordinator.setTasksToAcknowledge(java.util.Arrays.asList(LOC_1, LOC_2));
    }

    @AfterEach
    void tearDown() {
        coordinator.shutdown();
    }

    @Test
    void testFullCheckpointLifecycle() throws Exception {
        AtomicLong completedCheckpointId = new AtomicLong(-1);
        AtomicBoolean checkpointAborted = new AtomicBoolean(false);

        coordinator.addListener(new CheckpointListener() {
            @Override
            public void notifyCheckpointComplete(long checkpointId) throws Exception {
                completedCheckpointId.set(checkpointId);
            }

            @Override
            public void notifyCheckpointAborted(long checkpointId) throws Exception {
                checkpointAborted.set(true);
            }
        });

        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending, "Checkpoint should be triggered");

        long checkpointId = pending.getCheckpointId();
        assertEquals(0L, checkpointId, "First checkpoint ID should be 0");

        assertFalse(pending.isFullyAcknowledged(), "Checkpoint should not be fully acknowledged initially");

        TaskStateSnapshot state1 = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("op1", "data1".getBytes())
                .putKeyedState("key1", "keyedData1".getBytes())
                .build();

        TaskStateSnapshot state2 = TaskStateSnapshot.builder(LOC_2)
                .putOperatorState("op2", "data2".getBytes())
                .putKeyedState("key2", "keyedData2".getBytes())
                .build();

        coordinator.acknowledgeTask(LOC_1, checkpointId, state1);
        assertEquals(1, pending.getNumberOfAcknowledgedTasks());

        coordinator.acknowledgeTask(LOC_2, checkpointId, state2);
        assertEquals(2, pending.getNumberOfAcknowledgedTasks());

        assertTrue(pending.isFullyAcknowledged(), "Checkpoint should be fully acknowledged");

        CompletableFuture<CompletedCheckpoint> future = pending.getCompletableFuture();
        CompletedCheckpoint completed = future.get(10, TimeUnit.SECONDS);

        assertNotNull(completed, "Completed checkpoint should not be null");
        assertEquals(checkpointId, completed.getCheckpointId());
        assertEquals(2, completed.getTaskCount());

        CompletedCheckpoint stored = storage.getLatestCheckpoint("1", "1");
        assertNotNull(stored, "Checkpoint should be stored");
        assertEquals(checkpointId, stored.getCheckpointId());

        assertEquals(checkpointId, completedCheckpointId.get(), "Listener should be notified");
        assertFalse(checkpointAborted.get(), "Checkpoint should not be aborted");
    }

    @Test
    void testMultipleCheckpoints() throws Exception {
        for (int i = 0; i < 3; i++) {
            PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            assertNotNull(pending);

            long checkpointId = pending.getCheckpointId();
            coordinator.acknowledgeTask(LOC_1, checkpointId, TaskStateSnapshot.empty(LOC_1));
            coordinator.acknowledgeTask(LOC_2, checkpointId, TaskStateSnapshot.empty(LOC_2));

            CompletedCheckpoint completed = pending.getCompletableFuture().get(10, TimeUnit.SECONDS);
            assertEquals(i, completed.getCheckpointId());
        }

        int count = storage.getCheckpointCount("1");
        assertEquals(3, count, "Should have 3 stored checkpoints");
    }

    @Test
    void testCheckpointRecovery() throws Exception {
        PendingCheckpoint pending1 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        coordinator.acknowledgeTask(LOC_1, pending1.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
        coordinator.acknowledgeTask(LOC_2, pending1.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));
        CompletedCheckpoint completed1 = pending1.getCompletableFuture().get(10, TimeUnit.SECONDS);

        coordinator.shutdown();

        CheckpointIDCounter newIdCounter = new CheckpointIDCounter();
        newIdCounter.set(completed1.getCheckpointId() + 1);
        CheckpointConfig config = CheckpointConfig.builder().checkpointEnabled(true).build();
        CheckpointCoordinator newCoordinator = new CheckpointCoordinator("1", "1", newIdCounter, storage, config);

        CompletedCheckpoint restored = newCoordinator.restoreFromCheckpoint();
        assertNotNull(restored);
        assertEquals(completed1.getCheckpointId(), restored.getCheckpointId());
        assertTrue(restored.isRestored());

        CompletedCheckpoint latest = newCoordinator.getLatestCheckpoint();
        assertNotNull(latest);
        assertEquals(completed1.getCheckpointId(), latest.getCheckpointId());

        newCoordinator.shutdown();
    }

    @Test
    void testCheckpointTimeout() throws Exception {
        CheckpointConfig shortTimeoutConfig = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointTimeout(100L)
                .build();

        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointCoordinator shortTimeoutCoordinator = new CheckpointCoordinator(
                "1", "1", idCounter, storage, shortTimeoutConfig);
        shortTimeoutCoordinator.setTasksToAcknowledge(java.util.Arrays.asList(LOC_1, LOC_2));

        CompletableFuture<Void> abortFuture = new CompletableFuture<>();
        shortTimeoutCoordinator.addListener(new CheckpointListener() {
            @Override
            public void notifyCheckpointComplete(long checkpointId) throws Exception {
            }

            @Override
            public void notifyCheckpointAborted(long checkpointId) throws Exception {
                abortFuture.complete(null);
            }
        });

        PendingCheckpoint pending = shortTimeoutCoordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);

        shortTimeoutCoordinator.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));

        abortFuture.get(5, TimeUnit.SECONDS);
        assertEquals(0, shortTimeoutCoordinator.getNumberOfPendingCheckpoints());

        shortTimeoutCoordinator.shutdown();
    }

    @Test
    void testCheckpointStorageOperations() throws Exception {
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        coordinator.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
        coordinator.acknowledgeTask(LOC_2, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));
        pending.getCompletableFuture().get(10, TimeUnit.SECONDS);

        CompletedCheckpoint latest = storage.getLatestCheckpoint("1", "1");
        assertNotNull(latest);

        java.util.List<CompletedCheckpoint> all = storage.getAllCheckpoints("1");
        assertFalse(all.isEmpty());

        int count = storage.getCheckpointCount("1");
        assertEquals(1, count);

        storage.deleteCheckpoint("1", "1", pending.getCheckpointId());
        count = storage.getCheckpointCount("1");
        assertEquals(0, count);
    }
}
