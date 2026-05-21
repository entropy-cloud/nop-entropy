/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestSavepointMetadata {

    private static final TaskLocation LOC_0 = new TaskLocation("1", "1", "v0", 0);

    @Test
    void testJsonRoundTrip() {
        SavepointMetadata metadata = new SavepointMetadata(
                42L, 1700000000000L, "1", "1", 3, 2);

        String json = JsonTool.serialize(metadata, false);
        assertNotNull(json);
        assertFalse(json.isEmpty());

        SavepointMetadata deserialized = JsonTool.parseBeanFromText(json, SavepointMetadata.class);
        assertNotNull(deserialized);
        assertEquals(42L, deserialized.getCheckpointId());
        assertEquals(1700000000000L, deserialized.getCreateTime());
        assertEquals("1", deserialized.getJobId());
        assertEquals("1", deserialized.getPipelineId());
        assertEquals(3, deserialized.getOperatorStateCount());
        assertEquals(2, deserialized.getKeyedStateCount());
    }

    @Test
    void testFromCompletedCheckpoint() {
        TaskStateSnapshot state = TaskStateSnapshot.builder(LOC_0)
                .putOperatorState("operator-0", "opData".getBytes())
                .putOperatorState("custom", "customData".getBytes())
                .putKeyedState("keyed-state", "keyedData".getBytes())
                .build();

        Map<TaskLocation, TaskStateSnapshot> taskStates = new HashMap<>();
        taskStates.put(LOC_0, state);

        CompletedCheckpoint checkpoint = CompletedCheckpoint.builder()
                .jobId("1")
                .pipelineId("1")
                .checkpointId(5L)
                .triggerTimestamp(1000L)
                .completedTimestamp(2000L)
                .checkpointType(CheckpointType.SAVEPOINT)
                .taskStates(taskStates)
                .build();

        SavepointMetadata metadata = SavepointMetadata.fromCompletedCheckpoint(checkpoint);

        assertEquals(5L, metadata.getCheckpointId());
        assertEquals(2000L, metadata.getCreateTime());
        assertEquals("1", metadata.getJobId());
        assertEquals("1", metadata.getPipelineId());
        assertEquals(2, metadata.getOperatorStateCount());
        assertEquals(1, metadata.getKeyedStateCount());
    }

    @Test
    void testFromEmptyCheckpoint() {
        CompletedCheckpoint checkpoint = CompletedCheckpoint.builder()
                .jobId("2")
                .pipelineId("3")
                .checkpointId(10L)
                .triggerTimestamp(1000L)
                .completedTimestamp(2000L)
                .checkpointType(CheckpointType.SAVEPOINT)
                .build();

        SavepointMetadata metadata = SavepointMetadata.fromCompletedCheckpoint(checkpoint);

        assertEquals(10L, metadata.getCheckpointId());
        assertEquals(0, metadata.getOperatorStateCount());
        assertEquals(0, metadata.getKeyedStateCount());
    }

    @Test
    void testJsonRoundTripPreservesAllFields() {
        SavepointMetadata original = new SavepointMetadata(
                99L, 1700000000000L, "job-abc", "pipe-xyz", 5, 7);

        String json = JsonTool.serialize(original, false);
        SavepointMetadata restored = JsonTool.parseBeanFromText(json, SavepointMetadata.class);

        assertEquals(original, restored);
        assertEquals(original.hashCode(), restored.hashCode());
    }

    @Test
    void testDefaults() {
        SavepointMetadata metadata = new SavepointMetadata();
        assertEquals(0L, metadata.getCheckpointId());
        assertEquals(0L, metadata.getCreateTime());
        assertNull(metadata.getJobId());
        assertNull(metadata.getPipelineId());
        assertEquals(0, metadata.getOperatorStateCount());
        assertEquals(0, metadata.getKeyedStateCount());
    }
}
