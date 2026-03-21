/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TestPendingCheckpoint {

    private Set<Long> tasksToAck;
    private PendingCheckpoint pending;

    @BeforeEach
    void setUp() {
        tasksToAck = new HashSet<>();
        tasksToAck.add(1L);
        tasksToAck.add(2L);
        tasksToAck.add(3L);

        pending = new PendingCheckpoint(
                1L, 1, 100L, System.currentTimeMillis(),
                CheckpointType.CHECKPOINT, tasksToAck);
    }

    @Test
    void testInitialState() {
        assertEquals(1L, pending.getJobId());
        assertEquals(1, pending.getPipelineId());
        assertEquals(100L, pending.getCheckpointId());
        assertEquals(CheckpointType.CHECKPOINT, pending.getCheckpointType());
        assertEquals(3, pending.getNumberOfTasks());
        assertEquals(0, pending.getNumberOfAcknowledgedTasks());
        assertEquals(3, pending.getNumberOfNotAcknowledgedTasks());
        assertFalse(pending.isFullyAcknowledged());
    }

    @Test
    void testAcknowledgeTask() {
        TaskStateSnapshot state = TaskStateSnapshot.builder(1L)
                .putOperatorState("op1", "data".getBytes())
                .build();

        pending.acknowledgeTask(1L, state);

        assertEquals(1, pending.getNumberOfAcknowledgedTasks());
        assertEquals(2, pending.getNumberOfNotAcknowledgedTasks());
        assertFalse(pending.isFullyAcknowledged());
        assertEquals(state, pending.getTaskStates().get(1L));
    }

    @Test
    void testAllTasksAcknowledged() {
        pending.acknowledgeTask(1L, TaskStateSnapshot.empty(1L));
        assertFalse(pending.isFullyAcknowledged());

        pending.acknowledgeTask(2L, TaskStateSnapshot.empty(2L));
        assertFalse(pending.isFullyAcknowledged());

        pending.acknowledgeTask(3L, TaskStateSnapshot.empty(3L));
        assertTrue(pending.isFullyAcknowledged());
    }

    @Test
    void testToCompletedCheckpoint() {
        pending.acknowledgeTask(1L, TaskStateSnapshot.empty(1L));
        pending.acknowledgeTask(2L, TaskStateSnapshot.empty(2L));
        pending.acknowledgeTask(3L, TaskStateSnapshot.empty(3L));

        CompletedCheckpoint completed = pending.toCompletedCheckpoint();

        assertEquals(1L, completed.getJobId());
        assertEquals(1, completed.getPipelineId());
        assertEquals(100L, completed.getCheckpointId());
        assertEquals(CheckpointType.CHECKPOINT, completed.getCheckpointType());
        assertEquals(3, completed.getTaskCount());
    }

    @Test
    void testCompletableFuture() throws Exception {
        CompletableFuture<CompletedCheckpoint> future = pending.getCompletableFuture();

        assertFalse(future.isDone());

        pending.acknowledgeTask(1L, TaskStateSnapshot.empty(1L));
        pending.acknowledgeTask(2L, TaskStateSnapshot.empty(2L));
        pending.acknowledgeTask(3L, TaskStateSnapshot.empty(3L));

        CompletedCheckpoint completed = future.get(1, TimeUnit.SECONDS);
        assertNotNull(completed);
        assertEquals(100L, completed.getCheckpointId());
    }

    @Test
    void testAbort() {
        pending.abort("Test abort reason");
        assertTrue(pending.isDisposed());

        CompletableFuture<CompletedCheckpoint> future = pending.getCompletableFuture();
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    void testDispose() {
        pending.dispose();
        assertTrue(pending.isDisposed());
        assertEquals(0, pending.getNumberOfNotAcknowledgedTasks());
        assertEquals(0, pending.getTaskStates().size());
    }

    @Test
    void testAcknowledgeAfterDispose() {
        pending.dispose();
        pending.acknowledgeTask(1L, TaskStateSnapshot.empty(1L));
        assertEquals(0, pending.getNumberOfAcknowledgedTasks());
    }
}
