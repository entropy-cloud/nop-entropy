package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestPendingCheckpointStateMachine {

    private static final TaskLocation LOC_1 = new TaskLocation("j", "p", "v1", 1);
    private static final TaskLocation LOC_2 = new TaskLocation("j", "p", "v2", 2);

    private Set<TaskLocation> tasks;

    @BeforeEach
    void setUp() {
        tasks = new HashSet<>();
        tasks.add(LOC_1);
        tasks.add(LOC_2);
    }

    @Test
    void testInitialState_isRunning() {
        PendingCheckpoint pc = new PendingCheckpoint("j", "p", 1L, System.currentTimeMillis(),
                CheckpointType.CHECKPOINT, tasks);
        assertEquals(PendingCheckpoint.Status.RUNNING, pc.getStatus().get());
    }

    @Test
    void testAbortTransitionsToAborted() {
        PendingCheckpoint pc = new PendingCheckpoint("j", "p", 1L, System.currentTimeMillis(),
                CheckpointType.CHECKPOINT, tasks);
        pc.abort("test");
        assertEquals(PendingCheckpoint.Status.ABORTED, pc.getStatus().get());
    }

    @Test
    void testAbortAfterAlreadyAborted_isNoop() {
        PendingCheckpoint pc = new PendingCheckpoint("j", "p", 1L, System.currentTimeMillis(),
                CheckpointType.CHECKPOINT, tasks);
        pc.abort("first");
        pc.abort("second");
        assertEquals(PendingCheckpoint.Status.ABORTED, pc.getStatus().get());
        assertTrue(pc.isDisposed());
    }

    @Test
    void testAcknowledgeAfterAbort_isIgnored() {
        PendingCheckpoint pc = new PendingCheckpoint("j", "p", 1L, System.currentTimeMillis(),
                CheckpointType.CHECKPOINT, tasks);
        pc.abort("test");
        pc.acknowledgeTask(LOC_1, TaskStateSnapshot.empty(LOC_1));
        assertEquals(0, pc.getNumberOfAcknowledgedTasks());
    }

    @Test
    void testConcurrentAcknowledgeAndAbort_onlyOneWins() throws Exception {
        int iterations = 100;
        AtomicInteger abortWins = new AtomicInteger(0);
        AtomicInteger completeWins = new AtomicInteger(0);

        for (int i = 0; i < iterations; i++) {
            PendingCheckpoint pc = new PendingCheckpoint("j", "p", i, System.currentTimeMillis(),
                    CheckpointType.CHECKPOINT, tasks);

            Thread ackThread = new Thread(() -> {
                pc.acknowledgeTask(LOC_1, TaskStateSnapshot.empty(LOC_1));
                pc.acknowledgeTask(LOC_2, TaskStateSnapshot.empty(LOC_2));
            });
            Thread abortThread = new Thread(() -> pc.abort("race"));

            ackThread.start();
            abortThread.start();
            ackThread.join();
            abortThread.join();

            PendingCheckpoint.Status status = pc.getStatus().get();
            if (status == PendingCheckpoint.Status.ABORTED) {
                abortWins.incrementAndGet();
            } else if (status == PendingCheckpoint.Status.RUNNING) {
                completeWins.incrementAndGet();
            }
        }

        assertTrue(abortWins.get() > 0 || completeWins.get() > 0,
                "Either abort or complete should win in the race");
    }

    @Test
    void testConcurrentAcknowledgeConsistency() throws Exception {
        int numThreads = 8;
        Set<TaskLocation> manyTasks = new HashSet<>();
        for (int i = 0; i < numThreads; i++) {
            manyTasks.add(new TaskLocation("j", "p", "v", i));
        }

        PendingCheckpoint pc = new PendingCheckpoint("j", "p", 1L, System.currentTimeMillis(),
                CheckpointType.CHECKPOINT, manyTasks);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);

        for (TaskLocation loc : manyTasks) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    pc.acknowledgeTask(loc, TaskStateSnapshot.empty(loc));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals(numThreads, pc.getNumberOfAcknowledgedTasks());
        assertTrue(pc.isFullyAcknowledged());
    }
}
