/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointBarrier {

    @Test
    void testCheckpointBarrier() {
        CheckpointBarrier barrier = new CheckpointBarrier(1L, 1000L, CheckpointType.CHECKPOINT);
        
        assertEquals(1L, barrier.getId());
        assertEquals(1000L, barrier.getTimestamp());
        assertEquals(CheckpointType.CHECKPOINT, barrier.getCheckpointType());
    }

    @Test
    void testSnapshot() {
        CheckpointBarrier barrier = new CheckpointBarrier(1L, 1000L, CheckpointType.CHECKPOINT);
        assertTrue(barrier.snapshot());
        
        CheckpointBarrier savepoint = new CheckpointBarrier(2L, 2000L, CheckpointType.SAVEPOINT);
        assertTrue(savepoint.snapshot());
    }

    @Test
    void testPrepareClose() {
        CheckpointBarrier normal = new CheckpointBarrier(1L, 1000L, CheckpointType.CHECKPOINT);
        assertFalse(normal.prepareClose());
        
        CheckpointBarrier savepoint = new CheckpointBarrier(2L, 2000L, CheckpointType.SAVEPOINT);
        assertTrue(savepoint.prepareClose());
        
        CheckpointBarrier completed = new CheckpointBarrier(3L, 3000L, CheckpointType.COMPLETED_POINT_TYPE);
        assertTrue(completed.prepareClose());
    }

    @Test
    void testIsCheckpoint() {
        CheckpointBarrier checkpoint = new CheckpointBarrier(1L, 1000L, CheckpointType.CHECKPOINT);
        assertTrue(checkpoint.isCheckpoint());
        
        CheckpointBarrier savepoint = new CheckpointBarrier(2L, 2000L, CheckpointType.SAVEPOINT);
        assertFalse(savepoint.isCheckpoint());
    }

    @Test
    void testIsSavepoint() {
        CheckpointBarrier checkpoint = new CheckpointBarrier(1L, 1000L, CheckpointType.CHECKPOINT);
        assertFalse(checkpoint.isSavepoint());
        
        CheckpointBarrier savepoint = new CheckpointBarrier(2L, 2000L, CheckpointType.SAVEPOINT);
        assertTrue(savepoint.isSavepoint());
    }

    @Test
    void testEquals() {
        CheckpointBarrier barrier1 = new CheckpointBarrier(1L, 1000L, CheckpointType.CHECKPOINT);
        CheckpointBarrier barrier2 = new CheckpointBarrier(1L, 1000L, CheckpointType.CHECKPOINT);
        CheckpointBarrier barrier3 = new CheckpointBarrier(2L, 1000L, CheckpointType.CHECKPOINT);
        
        assertEquals(barrier1, barrier2);
        assertNotEquals(barrier1, barrier3);
    }

    @Test
    void testHashCode() {
        CheckpointBarrier barrier1 = new CheckpointBarrier(1L, 1000L, CheckpointType.CHECKPOINT);
        CheckpointBarrier barrier2 = new CheckpointBarrier(1L, 1000L, CheckpointType.CHECKPOINT);
        
        assertEquals(barrier1.hashCode(), barrier2.hashCode());
    }

    @Test
    void testToString() {
        CheckpointBarrier barrier = new CheckpointBarrier(1L, 1000L, CheckpointType.CHECKPOINT);
        String str = barrier.toString();
        
        assertTrue(str.contains("id=1"));
        assertTrue(str.contains("timestamp=1000"));
        assertTrue(str.contains("checkpointType=CHECKPOINT"));
    }
}
