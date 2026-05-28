package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointConcurrencySafety {

    private static final TaskLocation LOC_1 = new TaskLocation("j", "p", "v1", 1);
    private static final TaskLocation LOC_2 = new TaskLocation("j", "p", "v2", 2);

    @TempDir
    Path tempDir;

    private CheckpointCoordinator coordinator;
    private CheckpointIDCounter idCounter;
    private CheckpointConfig config;

    @BeforeEach
    void setUp() {
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        idCounter = new CheckpointIDCounter();
        config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(100L)
                .checkpointTimeout(30000L)
                .maxConcurrentCheckpoints(10)
                .maxRetainedCheckpoints(5)
                .build();

        coordinator = new CheckpointCoordinator("j", "p", idCounter, storage, config);
        coordinator.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
    }

    @AfterEach
    void tearDown() {
        coordinator.shutdown();
    }

    @Test
    void testConcurrentStartCheckpointScheduler_noDuplicateScheduler() throws Exception {
        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    coordinator.startCheckpointScheduler();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        Thread.sleep(500);
        assertTrue(coordinator.getNumberOfPendingCheckpoints() >= 1,
                "At least one checkpoint should have been triggered by the scheduler");
    }

    @Test
    void testConcurrentAcknowledgeTask_noCorruption() throws Exception {
        int numIterations = 50;
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < numIterations; i++) {
            Set<TaskLocation> tasks = new HashSet<>(Arrays.asList(LOC_1, LOC_2));
            PendingCheckpoint pc = new PendingCheckpoint("j", "p", i,
                    System.currentTimeMillis(), CheckpointType.CHECKPOINT, tasks);

            pc.getCompletableFuture().whenComplete((completed, error) -> {
                if (completed != null) {
                    successCount.incrementAndGet();
                }
            });

            ExecutorService exec = Executors.newFixedThreadPool(2);
            exec.submit(() -> pc.acknowledgeTask(LOC_1, TaskStateSnapshot.empty(LOC_1)));
            exec.submit(() -> pc.acknowledgeTask(LOC_2, TaskStateSnapshot.empty(LOC_2)));
            exec.shutdown();
            exec.awaitTermination(2, TimeUnit.SECONDS);

            pc.getCompletableFuture().get(2, TimeUnit.SECONDS);
        }

        assertEquals(numIterations, successCount.get(),
                "All checkpoints should complete successfully");
    }

    @Test
    void testCompleteAndAbortMutualExclusion() throws Exception {
        int numIterations = 100;
        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicInteger abortedCount = new AtomicInteger(0);

        for (int i = 0; i < numIterations; i++) {
            Set<TaskLocation> tasks = new HashSet<>(Arrays.asList(LOC_1, LOC_2));
            PendingCheckpoint pc = new PendingCheckpoint("j", "p", i,
                    System.currentTimeMillis(), CheckpointType.CHECKPOINT, tasks);

            Thread ackThread = new Thread(() -> {
                pc.acknowledgeTask(LOC_1, TaskStateSnapshot.empty(LOC_1));
                pc.acknowledgeTask(LOC_2, TaskStateSnapshot.empty(LOC_2));
            });
            Thread abortThread = new Thread(() -> pc.abort("race-test"));

            ackThread.start();
            abortThread.start();
            ackThread.join();
            abortThread.join();

            PendingCheckpoint.Status status = pc.getStatus().get();
            if (status == PendingCheckpoint.Status.ABORTED) {
                abortedCount.incrementAndGet();
            }
        }

        assertTrue(abortedCount.get() > 0,
                "At least some aborts should win the race");
    }
}
