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

    private static final TaskLocation LOC_1 = new TaskLocation("1", "1", "v1", 1);
    private static final TaskLocation LOC_2 = new TaskLocation("1", "1", "v2", 2);
    private static final TaskLocation LOC_11 = new TaskLocation("1", "1", "v11", 11);
    private static final TaskLocation LOC_22 = new TaskLocation("1", "1", "v22", 22);
    private static final TaskLocation LOC_33 = new TaskLocation("1", "1", "v33", 33);

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

        coordinator = new CheckpointCoordinator("1", "1", idCounter, storage, config);
        coordinator.setTasksToAcknowledge(java.util.Arrays.asList(LOC_1, LOC_2));
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

        TaskStateSnapshot state = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("op1", "data".getBytes())
                .build();

        boolean acknowledged = coordinator.acknowledgeTask(LOC_1, pending.getCheckpointId(), state);
        assertTrue(acknowledged);
    }

    @Test
    void testAcknowledgeUnknownCheckpoint() {
        boolean acknowledged = coordinator.acknowledgeTask(LOC_1, 999L, TaskStateSnapshot.empty(LOC_1));
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

        coordinator.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
        coordinator.acknowledgeTask(LOC_2, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));

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
        coordinator.setTasksToAcknowledge(java.util.Arrays.asList(LOC_11, LOC_22, LOC_33));

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
        coordinator.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
        coordinator.acknowledgeTask(LOC_2, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));

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
