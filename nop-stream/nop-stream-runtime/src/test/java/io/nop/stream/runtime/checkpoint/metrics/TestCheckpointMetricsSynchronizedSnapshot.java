package io.nop.stream.runtime.checkpoint.metrics;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointMetricsSynchronizedSnapshot {

    @Test
    void testSnapshotConsistencyUnderConcurrency() throws Exception {
        CheckpointMetrics metrics = new CheckpointMetrics();
        int iterations = 100;

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(4);

        for (int i = 0; i < iterations; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (idx % 2 == 0) {
                        metrics.incrementCompletedCheckpoints();
                        metrics.updateLatestCheckpoint(idx * 100L, idx * 10L);
                    } else {
                        metrics.incrementFailedCheckpoints();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        CheckpointMetricsSnapshot snapshot = metrics.snapshot();
        assertNotNull(snapshot);
        assertEquals(iterations / 2, snapshot.getNumCompletedCheckpoints());
        assertEquals(iterations / 2, snapshot.getNumFailedCheckpoints());
    }

    @Test
    void testResetAndSnapshotConsistency() {
        CheckpointMetrics metrics = new CheckpointMetrics();
        metrics.incrementCompletedCheckpoints();
        metrics.incrementCompletedCheckpoints();
        metrics.updateLatestCheckpoint(1024, 500);

        CheckpointMetricsSnapshot before = metrics.snapshot();
        assertEquals(2, before.getNumCompletedCheckpoints());

        metrics.reset();
        CheckpointMetricsSnapshot after = metrics.snapshot();
        assertEquals(0, after.getNumCompletedCheckpoints());
        assertEquals(0, after.getLatestCheckpointSize());
    }
}
