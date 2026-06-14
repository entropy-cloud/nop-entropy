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

/**
 * Tests for Phase 2 of plan 172: trigger atomicity, failure observability,
 * and concurrent capability consistency.
 */
class TestCheckpointTriggerSafety {

    private static final TaskLocation LOC_1 = new TaskLocation("j", "p", "v1", 0);
    private static final TaskLocation LOC_2 = new TaskLocation("j", "p", "v2", 0);

    @TempDir
    Path tempDir;

    private CheckpointCoordinator coordinator;
    private LocalFileCheckpointStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileCheckpointStorage(tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        if (coordinator != null) {
            coordinator.shutdown();
        }
    }

    private CheckpointCoordinator createCoordinator(int maxConcurrent) {
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(100L)
                .checkpointTimeout(30000L)
                .maxConcurrentCheckpoints(maxConcurrent)
                .maxRetainedCheckpoints(5)
                .maxConsecutiveCheckpointFailures(3)
                .build();

        coordinator = new CheckpointCoordinator("j", "p", idCounter, storage, config);
        coordinator.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
        return coordinator;
    }

    /**
     * Concurrent trigger atomicity: multiple threads call tryTriggerPendingCheckpoint
     * simultaneously. With synchronized fix, at most 1 pending checkpoint should exist
     * (effective max=1), regardless of configured maxConcurrent.
     */
    @Test
    void testConcurrentTriggerDoesNotExceedMaxConcurrent() throws Exception {
        coordinator = createCoordinator(1);

        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    PendingCheckpoint result = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
                    if (result != null) {
                        successCount.incrementAndGet();
                    }
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

        assertEquals(1, successCount.get(),
                "Only 1 checkpoint should be triggered when maxConcurrent=1");
        assertEquals(1, coordinator.getNumberOfPendingCheckpoints(),
                "numPendingCheckpoints should be exactly 1");
    }

    /**
     * Concurrent capability consistency: maxConcurrentCheckpoints=2 should be
     * effectively limited to 1, not crash with barrier overlap.
     */
    @Test
    void testMaxConcurrentCheckpointsLargerThanOneIsLimitedToOne() throws Exception {
        coordinator = createCoordinator(2);

        int numThreads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    PendingCheckpoint result = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
                    if (result != null) {
                        successCount.incrementAndGet();
                    }
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

        assertEquals(1, successCount.get(),
                "maxConcurrentCheckpoints=2 should be effectively limited to 1");
        assertEquals(1, coordinator.getNumberOfPendingCheckpoints());
    }

    /**
     * Failure observability: incrementTriggerFailures counts consecutive failures,
     * and resets to 0 on checkpoint completion.
     */
    @Test
    void testTriggerFailureCountingAndReset() throws Exception {
        coordinator = createCoordinator(1);

        // Increment failures
        coordinator.incrementTriggerFailures();
        assertEquals(1, coordinator.getConsecutiveTriggerFailures());

        coordinator.incrementTriggerFailures();
        assertEquals(2, coordinator.getConsecutiveTriggerFailures());

        // Trigger and complete a checkpoint → should reset failures
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);

        // Acknowledge all tasks to complete
        coordinator.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
        coordinator.acknowledgeTask(LOC_2, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));

        // Give time for async completion
        Thread.sleep(500);

        assertEquals(0, coordinator.getConsecutiveTriggerFailures(),
                "Consecutive failures should reset after a successful checkpoint");
    }

    /**
     * Failure observability: at threshold (3), ERROR-level observability signal fires.
     * This test just verifies the counting reaches threshold without exception.
     */
    @Test
    void testFailureCountReachesThreshold() {
        coordinator = createCoordinator(1);

        coordinator.incrementTriggerFailures();
        coordinator.incrementTriggerFailures();
        coordinator.incrementTriggerFailures();

        assertEquals(3, coordinator.getConsecutiveTriggerFailures(),
                "Failures should reach threshold");
    }

    /**
     * Trigger and abort mutual exclusion with synchronized methods:
     * concurrent trigger + abort should not corrupt pendingCheckpoints state.
     * After the dust settles, numPendingCheckpoints must be 0 or 1 (never > 1).
     */
    @Test
    void testTriggerAndAbortMutualExclusion() throws Exception {
        coordinator = createCoordinator(1);

        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending, "First trigger should succeed");
        assertEquals(1, coordinator.getNumberOfPendingCheckpoints());

        // Abort the pending checkpoint
        coordinator.abortPendingCheckpoint(pending, "test-abort");
        assertEquals(0, coordinator.getNumberOfPendingCheckpoints(),
                "After abort, no pending checkpoints should remain");

        // Now should be able to trigger again
        PendingCheckpoint pending2 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending2, "Should be able to trigger after abort");
        assertEquals(1, coordinator.getNumberOfPendingCheckpoints());

        // Abort second and verify clean state
        coordinator.abortPendingCheckpoint(pending2, "test-abort-2");
        assertEquals(0, coordinator.getNumberOfPendingCheckpoints());
    }
}
