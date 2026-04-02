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
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointCoordinator {

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
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3)
                .build();

        coordinator = new CheckpointCoordinator(1L, 1, idCounter, storage, config);
        coordinator.setTasksToAcknowledge(java.util.Arrays.asList(1L, 2L));
    }

    @AfterEach
    void tearDown() {
        coordinator.shutdown();
    }

    @Test
    void testTriggerCheckpoint() {
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);

        assertNotNull(pending);
        assertEquals(0L, pending.getCheckpointId());
        assertEquals(1, coordinator.getNumberOfPendingCheckpoints());
    }

    @Test
    void testAcknowledgeTask() {
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);

        TaskStateSnapshot state = TaskStateSnapshot.builder(1L)
                .putOperatorState("op1", "data".getBytes())
                .build();

        boolean acknowledged = coordinator.acknowledgeTask(1L, pending.getCheckpointId(), state);
        assertTrue(acknowledged);
    }

    @Test
    void testAcknowledgeUnknownCheckpoint() {
        boolean acknowledged = coordinator.acknowledgeTask(1L, 999L, TaskStateSnapshot.empty(1L));
        assertFalse(acknowledged);
    }

    @Test
    void testListenerNotification() throws Exception {
        AtomicLong notifiedCheckpointId = new AtomicLong(-1);

        coordinator.addListener(new CheckpointListener() {
            @Override
            public void notifyCheckpointComplete(long checkpointId) throws Exception {
                notifiedCheckpointId.set(checkpointId);
            }
        });

        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);

        coordinator.acknowledgeTask(1L, pending.getCheckpointId(), TaskStateSnapshot.empty(1L));
        coordinator.acknowledgeTask(2L, pending.getCheckpointId(), TaskStateSnapshot.empty(2L));

        Thread.sleep(100);

        CompletedCheckpoint completed = pending.getCompletableFuture().get();
        assertNotNull(completed);

        assertEquals(pending.getCheckpointId(), notifiedCheckpointId.get());
    }

    @Test
    void testMaxConcurrentCheckpoints() {
        config.setMaxConcurrentCheckpoints(1);

        PendingCheckpoint first = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(first);

        PendingCheckpoint second = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNull(second);
    }

    @Test
    void testSetTasksToAcknowledge() {
        coordinator.setTasksToAcknowledge(java.util.Arrays.asList(11L, 22L, 33L));

        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        assertEquals(3, pending.getNumberOfTasks());
    }

    @Test
    void testAbortIsIdempotentForPendingCount() {
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        assertEquals(1, coordinator.getNumberOfPendingCheckpoints());

        coordinator.abortPendingCheckpoint(pending, "first abort");
        assertEquals(0, coordinator.getNumberOfPendingCheckpoints());

        coordinator.abortPendingCheckpoint(pending, "second abort");
        assertEquals(0, coordinator.getNumberOfPendingCheckpoints());
    }

    @Test
    void testGetLatestCheckpoint() throws Exception {
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        coordinator.acknowledgeTask(1L, pending.getCheckpointId(), TaskStateSnapshot.empty(1L));
        coordinator.acknowledgeTask(2L, pending.getCheckpointId(), TaskStateSnapshot.empty(2L));

        CompletedCheckpoint completed = pending.getCompletableFuture().get();
        assertNotNull(completed);

        CompletedCheckpoint latest = coordinator.getLatestCheckpoint();
        assertNotNull(latest);
        assertEquals(completed.getCheckpointId(), latest.getCheckpointId());
    }

    @Test
    void testShutdown() {
        coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertEquals(1, coordinator.getNumberOfPendingCheckpoints());

        coordinator.shutdown();
        assertEquals(0, coordinator.getNumberOfPendingCheckpoints());
    }

    @Test
    void testSchedulerStartStop() {
        coordinator.startCheckpointScheduler();
        coordinator.stopCheckpointScheduler();
    }
}
