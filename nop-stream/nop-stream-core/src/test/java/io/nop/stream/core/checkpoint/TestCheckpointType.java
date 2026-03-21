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

class TestCheckpointType {

    @Test
    void testCheckpointTypeEnumValues() {
        CheckpointType[] types = CheckpointType.values();
        assertEquals(3, types.length);
        
        assertEquals(CheckpointType.CHECKPOINT, CheckpointType.valueOf("CHECKPOINT"));
        assertEquals(CheckpointType.SAVEPOINT, CheckpointType.valueOf("SAVEPOINT"));
        assertEquals(CheckpointType.COMPLETED_POINT_TYPE, CheckpointType.valueOf("COMPLETED_POINT_TYPE"));
    }

    @Test
    void testIsAuto() {
        assertTrue(CheckpointType.CHECKPOINT.isAuto());
        assertFalse(CheckpointType.SAVEPOINT.isAuto());
        assertTrue(CheckpointType.COMPLETED_POINT_TYPE.isAuto());
    }

    @Test
    void testGetName() {
        assertEquals("checkpoint", CheckpointType.CHECKPOINT.getName());
        assertEquals("savepoint", CheckpointType.SAVEPOINT.getName());
        assertEquals("completed", CheckpointType.COMPLETED_POINT_TYPE.getName());
    }

    @Test
    void testIsFinalCheckpoint() {
        assertFalse(CheckpointType.CHECKPOINT.isFinalCheckpoint());
        assertTrue(CheckpointType.SAVEPOINT.isFinalCheckpoint());
        assertTrue(CheckpointType.COMPLETED_POINT_TYPE.isFinalCheckpoint());
    }

    @Test
    void testFromName() {
        assertEquals(CheckpointType.CHECKPOINT, CheckpointType.fromName("checkpoint"));
        assertEquals(CheckpointType.SAVEPOINT, CheckpointType.fromName("savepoint"));
        assertEquals(CheckpointType.COMPLETED_POINT_TYPE, CheckpointType.fromName("completed"));
        assertNull(CheckpointType.fromName("unknown"));
        assertNull(CheckpointType.fromName(null));
    }
}
