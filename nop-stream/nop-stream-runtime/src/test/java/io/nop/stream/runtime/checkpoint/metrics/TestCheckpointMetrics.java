/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointMetrics {

    private CheckpointMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new CheckpointMetrics();
    }

    @Test
    void testIncrementCompletedCheckpoints() {
        assertEquals(0, metrics.getNumCompletedCheckpoints());
        metrics.incrementCompletedCheckpoints();
        assertEquals(1, metrics.getNumCompletedCheckpoints());
        metrics.incrementCompletedCheckpoints();
        assertEquals(2, metrics.getNumCompletedCheckpoints());
    }

    @Test
    void testIncrementFailedCheckpoints() {
        assertEquals(0, metrics.getNumFailedCheckpoints());
        metrics.incrementFailedCheckpoints();
        assertEquals(1, metrics.getNumFailedCheckpoints());
    }

    @Test
    void testIncrementAbortedCheckpoints() {
        assertEquals(0, metrics.getNumAbortedCheckpoints());
        metrics.incrementAbortedCheckpoints();
        assertEquals(1, metrics.getNumAbortedCheckpoints());
    }

    @Test
    void testRecordDuration() {
        assertEquals(0, metrics.getLatestCheckpointDuration());
        metrics.recordDuration(150L);
        assertEquals(150L, metrics.getLatestCheckpointDuration());
    }

    @Test
    void testRecordStateSize() {
        assertEquals(0, metrics.getLatestCheckpointSize());
        assertEquals(0, metrics.getTotalStateSize());
        metrics.recordStateSize(1024L);
        assertEquals(1024L, metrics.getLatestCheckpointSize());
        assertEquals(1024L, metrics.getTotalStateSize());
        metrics.recordStateSize(2048L);
        assertEquals(2048L, metrics.getLatestCheckpointSize());
        assertEquals(3072L, metrics.getTotalStateSize());
    }

    @Test
    void testUpdateLatestCheckpoint() {
        metrics.updateLatestCheckpoint(512L, 200L);
        assertEquals(512L, metrics.getLatestCheckpointSize());
        assertEquals(200L, metrics.getLatestCheckpointDuration());
        assertTrue(metrics.getLastCheckpointTimestamp() > 0);
    }

    @Test
    void testAddToTotalStateSize() {
        metrics.addToTotalStateSize(100L);
        metrics.addToTotalStateSize(200L);
        assertEquals(300L, metrics.getTotalStateSize());
    }

    @Test
    void testReset() {
        metrics.incrementCompletedCheckpoints();
        metrics.incrementFailedCheckpoints();
        metrics.incrementAbortedCheckpoints();
        metrics.recordDuration(100L);
        metrics.recordStateSize(500L);

        metrics.reset();

        assertEquals(0, metrics.getNumCompletedCheckpoints());
        assertEquals(0, metrics.getNumFailedCheckpoints());
        assertEquals(0, metrics.getNumAbortedCheckpoints());
        assertEquals(0, metrics.getLatestCheckpointDuration());
        assertEquals(0, metrics.getLatestCheckpointSize());
        assertEquals(0, metrics.getTotalStateSize());
        assertEquals(0, metrics.getLastCheckpointTimestamp());
    }

    @Test
    void testSnapshot() {
        metrics.incrementCompletedCheckpoints();
        metrics.incrementCompletedCheckpoints();
        metrics.incrementFailedCheckpoints();
        metrics.updateLatestCheckpoint(1024L, 300L);
        metrics.addToTotalStateSize(1024L);

        CheckpointMetricsSnapshot snap = metrics.snapshot();
        assertEquals(2, snap.getNumCompletedCheckpoints());
        assertEquals(1, snap.getNumFailedCheckpoints());
        assertEquals(0, snap.getNumAbortedCheckpoints());
        assertEquals(1024L, snap.getLatestCheckpointSize());
        assertEquals(300L, snap.getLatestCheckpointDuration());
        assertEquals(1024L, snap.getTotalStateSize());
        assertTrue(snap.getLastCheckpointTimestamp() > 0);
    }

    @Test
    void testSnapshotIsDecoupledFromMetrics() {
        metrics.incrementCompletedCheckpoints();
        CheckpointMetricsSnapshot snap = metrics.snapshot();
        metrics.incrementCompletedCheckpoints();

        assertEquals(1, snap.getNumCompletedCheckpoints());
        assertEquals(2, metrics.getNumCompletedCheckpoints());
    }
}
