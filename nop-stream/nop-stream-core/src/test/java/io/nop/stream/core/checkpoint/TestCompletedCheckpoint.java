/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestCompletedCheckpoint {

    private static final TaskLocation LOC_1 = new TaskLocation("1", "1", "v1", 1);
    private static final TaskLocation LOC_2 = new TaskLocation("1", "1", "v2", 2);

    private CompletedCheckpoint checkpoint;

    @BeforeEach
    void setUp() {
        checkpoint = CompletedCheckpoint.builder()
                .jobId("1")
                .pipelineId("1")
                .checkpointId(100L)
                .triggerTimestamp(1000L)
                .completedTimestamp(2000L)
                .checkpointType(CheckpointType.CHECKPOINT)
                .build();
    }

    @Test
    void testBuilder() {
        assertEquals("1", checkpoint.getJobId());
        assertEquals("1", checkpoint.getPipelineId());
        assertEquals(100L, checkpoint.getCheckpointId());
        assertEquals(1000L, checkpoint.getTriggerTimestamp());
        assertEquals(2000L, checkpoint.getCompletedTimestamp());
        assertEquals(CheckpointType.CHECKPOINT, checkpoint.getCheckpointType());
    }

    @Test
    void testTaskStates() {
        TaskStateSnapshot state1 = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("op1", "data1".getBytes())
                .build();
        TaskStateSnapshot state2 = TaskStateSnapshot.builder(LOC_2)
                .putOperatorState("op2", "data2".getBytes())
                .build();

        checkpoint.addTaskState(LOC_1, state1);
        checkpoint.addTaskState(LOC_2, state2);

        assertEquals(2, checkpoint.getTaskCount());
        assertEquals(state1, checkpoint.getTaskState(LOC_1));
        assertEquals(state2, checkpoint.getTaskState(LOC_2));
        assertNull(checkpoint.getTaskState(new TaskLocation("1", "1", "v999", 999)));
    }

    @Test
    void testRestored() {
        assertFalse(checkpoint.isRestored());
        checkpoint.setRestored(true);
        assertTrue(checkpoint.isRestored());
    }

    @Tag("low-value")
    @Test
    void testGetDuration() {
        assertEquals(1000L, checkpoint.getDuration());
    }

    @Test
    void testEstimateSize() {
        assertEquals(0, checkpoint.estimateSize());

        TaskStateSnapshot state = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("op1", "operator-data")
                .putKeyedState("key1", "keyed-data")
                .build();
        checkpoint.addTaskState(LOC_1, state);

        assertEquals(2, checkpoint.estimateSize());
    }

    @Test
    void testEqualsAndHashCode() {
        CompletedCheckpoint other = CompletedCheckpoint.builder()
                .jobId("1")
                .pipelineId("1")
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
        assertTrue(str.contains("jobId='1'"));
        assertTrue(str.contains("checkpointId=100"));
    }

    @Test
    void testSerialization() throws Exception {
        TaskStateSnapshot state = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("op1", "data".getBytes())
                .build();
        checkpoint.addTaskState(LOC_1, state);

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
