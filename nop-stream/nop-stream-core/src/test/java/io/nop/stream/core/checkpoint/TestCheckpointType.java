/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.nop.stream.core.exceptions.StreamException;

import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointType {

    @Tag("low-value")
    @Test
    void testCheckpointTypeEnumValues() {
        CheckpointType[] types = CheckpointType.values();
        assertEquals(5, types.length);
        
        assertEquals(CheckpointType.CHECKPOINT, CheckpointType.valueOf("CHECKPOINT"));
        assertEquals(CheckpointType.SAVEPOINT, CheckpointType.valueOf("SAVEPOINT"));
        assertEquals(CheckpointType.COMPLETED_POINT_TYPE, CheckpointType.valueOf("COMPLETED_POINT_TYPE"));
        assertEquals(CheckpointType.TERMINAL_SAVEPOINT, CheckpointType.valueOf("TERMINAL_SAVEPOINT"));
        assertEquals(CheckpointType.EXPORTED_SAVEPOINT, CheckpointType.valueOf("EXPORTED_SAVEPOINT"));
    }

    @Test
    void testIsAuto() {
        assertTrue(CheckpointType.CHECKPOINT.isAuto());
        assertFalse(CheckpointType.SAVEPOINT.isAuto());
        assertTrue(CheckpointType.COMPLETED_POINT_TYPE.isAuto());
    }

    @Tag("low-value")
    @Test
    void testGetName() {
        assertEquals("checkpoint", CheckpointType.CHECKPOINT.getName());
        assertEquals("savepoint", CheckpointType.SAVEPOINT.getName());
        assertEquals("completed", CheckpointType.COMPLETED_POINT_TYPE.getName());
    }

    @Tag("low-value")
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
        assertThrows(StreamException.class, () -> CheckpointType.fromName("unknown"));
        assertThrows(StreamException.class, () -> CheckpointType.fromName(null));
    }
}
