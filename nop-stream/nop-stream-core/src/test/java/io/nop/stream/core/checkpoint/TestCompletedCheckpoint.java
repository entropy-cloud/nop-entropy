/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestCompletedCheckpoint {

    private CompletedCheckpoint checkpoint;

    @BeforeEach
    void setUp() {
        checkpoint = CompletedCheckpoint.builder()
                .jobId(1L)
                .pipelineId(1)
                .checkpointId(100L)
                .triggerTimestamp(1000L)
                .completedTimestamp(2000L)
                .checkpointType(CheckpointType.CHECKPOINT)
                .build();
    }

    @Test
    void testBuilder() {
        assertEquals(1L, checkpoint.getJobId());
        assertEquals(1, checkpoint.getPipelineId());
        assertEquals(100L, checkpoint.getCheckpointId());
        assertEquals(1000L, checkpoint.getTriggerTimestamp());
        assertEquals(2000L, checkpoint.getCompletedTimestamp());
        assertEquals(CheckpointType.CHECKPOINT, checkpoint.getCheckpointType());
    }

    @Test
    void testTaskStates() {
        TaskStateSnapshot state1 = TaskStateSnapshot.builder(1L)
                .putOperatorState("op1", "data1".getBytes())
                .build();
        TaskStateSnapshot state2 = TaskStateSnapshot.builder(2L)
                .putOperatorState("op2", "data2".getBytes())
                .build();

        checkpoint.addTaskState(1L, state1);
        checkpoint.addTaskState(2L, state2);

        assertEquals(2, checkpoint.getTaskCount());
        assertEquals(state1, checkpoint.getTaskState(1L));
        assertEquals(state2, checkpoint.getTaskState(2L));
        assertNull(checkpoint.getTaskState(999L));
    }

    @Test
    void testRestored() {
        assertFalse(checkpoint.isRestored());
        checkpoint.setRestored(true);
        assertTrue(checkpoint.isRestored());
    }

    @Test
    void testGetDuration() {
        assertEquals(1000L, checkpoint.getDuration());
    }

    @Test
    void testEstimateSize() {
        assertEquals(0, checkpoint.estimateSize());

        TaskStateSnapshot state = TaskStateSnapshot.builder(1L)
                .putOperatorState("op1", new byte[100])
                .putKeyedState("key1", new byte[50])
                .build();
        checkpoint.addTaskState(1L, state);

        assertEquals(150, checkpoint.estimateSize());
    }

    @Test
    void testEqualsAndHashCode() {
        CompletedCheckpoint other = CompletedCheckpoint.builder()
                .jobId(1L)
                .pipelineId(1)
                .checkpointId(100L)
                .triggerTimestamp(1000L)
                .completedTimestamp(2000L)
                .checkpointType(CheckpointType.CHECKPOINT)
                .build();

        assertEquals(checkpoint, other);
        assertEquals(checkpoint.hashCode(), other.hashCode());
    }

    @Test
    void testToString() {
        String str = checkpoint.toString();
        assertTrue(str.contains("jobId=1"));
        assertTrue(str.contains("checkpointId=100"));
    }

    @Test
    void testSerialization() throws Exception {
        TaskStateSnapshot state = TaskStateSnapshot.builder(1L)
                .putOperatorState("op1", "data".getBytes())
                .build();
        checkpoint.addTaskState(1L, state);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(checkpoint);
        oos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        CompletedCheckpoint deserialized = (CompletedCheckpoint) ois.readObject();

        assertEquals(checkpoint.getCheckpointId(), deserialized.getCheckpointId());
        assertEquals(checkpoint.getJobId(), deserialized.getJobId());
        assertEquals(checkpoint.getTaskCount(), deserialized.getTaskCount());
    }
}
