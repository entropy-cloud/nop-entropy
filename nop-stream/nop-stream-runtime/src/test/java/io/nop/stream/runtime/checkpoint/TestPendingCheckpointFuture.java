package io.nop.stream.runtime.checkpoint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TestPendingCheckpointFuture {

    private PendingCheckpoint checkpoint;
    private Set<TaskLocation> tasks;

    @BeforeEach
    void setUp() {
        tasks = new HashSet<>();
        tasks.add(new TaskLocation("v1", 0));
        tasks.add(new TaskLocation("v1", 1));
        checkpoint = new PendingCheckpoint("job-1", "pipe-1", 1L,
                System.currentTimeMillis(), CheckpointType.CHECKPOINT, tasks);
    }

    @Test
    void testFutureAutoCompletesWhenAllTasksAcknowledged() throws Exception {
        CompletableFuture<CompletedCheckpoint> future = checkpoint.getCompletableFuture();
        assertFalse(future.isDone());

        TaskStateSnapshot state0 = TaskStateSnapshot.empty(new TaskLocation("v1", 0));
        checkpoint.acknowledgeTask(new TaskLocation("v1", 0), state0);
        assertFalse(future.isDone());

        TaskStateSnapshot state1 = TaskStateSnapshot.empty(new TaskLocation("v1", 1));
        checkpoint.acknowledgeTask(new TaskLocation("v1", 1), state1);
        assertTrue(future.isDone());

        CompletedCheckpoint result = future.get(1, TimeUnit.SECONDS);
        assertNotNull(result);
        assertEquals("job-1", result.getJobId());
        assertEquals(1L, result.getCheckpointId());
    }

    @Test
    void testFutureNotAutoCompletedWithPendingTasks() {
        CompletableFuture<CompletedCheckpoint> future = checkpoint.getCompletableFuture();
        checkpoint.acknowledgeTask(new TaskLocation("v1", 0), null);
        assertFalse(future.isDone());
    }

    @Test
    void testFutureAutoCompletesWithSingleTask() throws Exception {
        Set<TaskLocation> single = Collections.singleton(new TaskLocation("v1", 0));
        PendingCheckpoint pc = new PendingCheckpoint("job-2", "pipe-2", 2L,
                System.currentTimeMillis(), CheckpointType.CHECKPOINT, single);

        CompletableFuture<CompletedCheckpoint> future = pc.getCompletableFuture();
        TaskStateSnapshot state = TaskStateSnapshot.empty(new TaskLocation("v1", 0));
        pc.acknowledgeTask(new TaskLocation("v1", 0), state);

        assertTrue(future.isDone());
        CompletedCheckpoint result = future.get(1, TimeUnit.SECONDS);
        assertNotNull(result);
    }

    @Test
    void testAbortPreventsAutoComplete() {
        checkpoint.abort("test abort");
        CompletableFuture<CompletedCheckpoint> future = checkpoint.getCompletableFuture();
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    void testAcknowledgeAfterAbortIsIgnored() {
        checkpoint.abort("test abort");
        checkpoint.acknowledgeTask(new TaskLocation("v1", 0), null);
        checkpoint.acknowledgeTask(new TaskLocation("v1", 1), null);

        CompletableFuture<CompletedCheckpoint> future = checkpoint.getCompletableFuture();
        assertTrue(future.isCompletedExceptionally());
    }
}
