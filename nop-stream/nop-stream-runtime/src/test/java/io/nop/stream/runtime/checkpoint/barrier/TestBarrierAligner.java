/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.barrier;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TestBarrierAligner {

    @Test
    void testSingleInputAlignment() {
        BarrierAligner aligner = new BarrierAligner(1);

        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        boolean completed = aligner.processBarrier(barrier, 0);
        assertTrue(completed);
        assertFalse(aligner.hasPendingBarriers());
        assertEquals(0, aligner.getPendingBarrierCount());

        AlignedBarrier aligned = aligner.pollAlignedBarrier();
        assertNotNull(aligned);
        assertEquals(1L, aligned.getCheckpointId());
        assertEquals(1, aligned.getInputCount());

        aligner.close();
    }

    @Test
    void testMultipleInputAlignment() {
        BarrierAligner aligner = new BarrierAligner(3);

        CheckpointBarrier barrier1 = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        CheckpointBarrier barrier2 = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        CheckpointBarrier barrier3 = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);

        assertFalse(aligner.processBarrier(barrier1, 0));
        assertTrue(aligner.hasPendingBarriers());
        assertEquals(1, aligner.getPendingBarrierCount());

        assertFalse(aligner.processBarrier(barrier2, 1));
        assertTrue(aligner.hasPendingBarriers());
        assertEquals(2, aligner.getPendingBarrierCount());

        assertTrue(aligner.processBarrier(barrier3, 2));
        assertFalse(aligner.hasPendingBarriers());

        AlignedBarrier aligned = aligner.pollAlignedBarrier();
        assertNotNull(aligned);
        assertEquals(1L, aligned.getCheckpointId());
        assertEquals(3, aligned.getInputCount());

        aligner.close();
    }

    @Test
    void testBarrierOutOfOrder() {
        BarrierAligner aligner = new BarrierAligner(3);

        CheckpointBarrier barrier1 = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        CheckpointBarrier barrier2 = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        CheckpointBarrier barrier3 = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);

        aligner.processBarrier(barrier3, 2);
        aligner.processBarrier(barrier1, 0);
        assertTrue(aligner.processBarrier(barrier2, 1));

        AlignedBarrier aligned = aligner.pollAlignedBarrier();
        assertNotNull(aligned);
        assertEquals(1L, aligned.getCheckpointId());

        aligner.close();
    }

    @Test
    void testMultipleCheckpoints() {
        BarrierAligner aligner = new BarrierAligner(2);

        CheckpointBarrier barrier1a = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        CheckpointBarrier barrier1b = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        CheckpointBarrier barrier2a = new CheckpointBarrier(2L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        CheckpointBarrier barrier2b = new CheckpointBarrier(2L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);

        aligner.processBarrier(barrier1a, 0);
        aligner.processBarrier(barrier2a, 0);
        aligner.processBarrier(barrier2b, 1);

        AlignedBarrier aligned2 = aligner.pollAlignedBarrier();
        assertNotNull(aligned2);
        assertEquals(2L, aligned2.getCheckpointId());

        aligner.processBarrier(barrier1b, 1);

        AlignedBarrier aligned1 = aligner.pollAlignedBarrier();
        assertNotNull(aligned1);
        assertEquals(1L, aligned1.getCheckpointId());

        aligner.close();
    }

    @Test
    void testAbortAll() {
        BarrierAligner aligner = new BarrierAligner(2);

        CheckpointBarrier barrier1 = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        aligner.processBarrier(barrier1, 0);

        assertTrue(aligner.hasPendingBarriers());

        aligner.abortAll();
        assertFalse(aligner.hasPendingBarriers());

        aligner.close();
    }

    @Test
    void testClosePreventsNewBarriers() {
        BarrierAligner aligner = new BarrierAligner(1);

        aligner.close();

        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        assertFalse(aligner.processBarrier(barrier, 0));
    }

    @Test
    void testGetCurrentCheckpointId() {
        BarrierAligner aligner = new BarrierAligner(2);

        assertEquals(-1L, aligner.getCurrentCheckpointId());

        CheckpointBarrier barrier1 = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        aligner.processBarrier(barrier1, 0);
        assertEquals(1L, aligner.getCurrentCheckpointId());

        CheckpointBarrier barrier2 = new CheckpointBarrier(2L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        aligner.processBarrier(barrier2, 0);
        assertEquals(2L, aligner.getCurrentCheckpointId());

        aligner.close();
    }

    @Test
    void testAlignmentDuration() throws Exception {
        BarrierAligner aligner = new BarrierAligner(2);

        long startTime = System.currentTimeMillis();
        CheckpointBarrier barrier1 = new CheckpointBarrier(1L, startTime, CheckpointType.CHECKPOINT);
        Thread.sleep(10);
        CheckpointBarrier barrier2 = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);

        aligner.processBarrier(barrier1, 0);
        aligner.processBarrier(barrier2, 1);

        AlignedBarrier aligned = aligner.pollAlignedBarrier(100, TimeUnit.MILLISECONDS);
        assertNotNull(aligned);
        assertTrue(aligned.getAlignmentDuration() >= 0);

        aligner.close();
    }
}
