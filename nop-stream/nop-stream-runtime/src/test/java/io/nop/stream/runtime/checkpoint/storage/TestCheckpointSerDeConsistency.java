package io.nop.stream.runtime.checkpoint.storage;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.runtime.checkpoint.storage.CheckpointSerDe;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointSerDeConsistency {

    @Test
    void testSerializeCheckpointAndEpochManifestProduceSameFormat() {
        TaskLocation loc = new TaskLocation("job1", "pipe1", "v1", 0);
        TaskStateSnapshot snapshot = new TaskStateSnapshot(loc);
        snapshot.putOperatorState("op-key", "op-value");
        snapshot.putKeyedState("keyed-key", "keyed-value");

        Map<TaskLocation, TaskStateSnapshot> taskStates = new LinkedHashMap<>();
        taskStates.put(loc, snapshot);

        CompletedCheckpoint checkpoint = CompletedCheckpoint.builder()
                .jobId("job1")
                .pipelineId("pipe1")
                .checkpointId(1L)
                .triggerTimestamp(1000L)
                .completedTimestamp(2000L)
                .checkpointType(CheckpointType.CHECKPOINT)
                .taskStates(taskStates)
                .build();

        byte[] checkpointBytes = CheckpointSerDe.serializeCheckpoint(checkpoint);

        EpochManifest manifest = new EpochManifest(
                1L, "job1", "pipe1", 2000L,
                CheckpointType.CHECKPOINT, null, taskStates, null, null);
        byte[] manifestBytes = CheckpointSerDe.serializeEpochManifest(manifest);

        String checkpointJson = new String(checkpointBytes, java.nio.charset.StandardCharsets.UTF_8);
        String manifestJson = new String(manifestBytes, java.nio.charset.StandardCharsets.UTF_8);

        Map<String, Object> checkpointTaskStates = extractTaskStatesMap(checkpointJson);
        Map<String, Object> manifestTaskStates = extractTaskStatesMap(manifestJson);

        for (Map.Entry<String, Object> entry : checkpointTaskStates.entrySet()) {
            String key = entry.getKey();
            assertTrue(manifestTaskStates.containsKey(key),
                    "Manifest should contain task key " + key);
            Map<String, Object> cpState = (Map<String, Object>) entry.getValue();
            Map<String, Object> mfState = (Map<String, Object>) manifestTaskStates.get(key);
            assertEquals(cpState.get("operatorStates"), mfState.get("operatorStates"),
                    "operatorStates should match for " + key);
            assertEquals(cpState.get("keyedStates"), mfState.get("keyedStates"),
                    "keyedStates should match for " + key);
        }
    }

    @Test
    void testDeserializeCheckpointRoundTrip() {
        TaskLocation loc = new TaskLocation("job1", "pipe1", "v1", 0);
        TaskStateSnapshot snapshot = new TaskStateSnapshot(loc);
        snapshot.putOperatorState("op-key", "op-value");
        snapshot.putKeyedState("keyed-key", "keyed-value");

        Map<TaskLocation, TaskStateSnapshot> taskStates = new LinkedHashMap<>();
        taskStates.put(loc, snapshot);

        CompletedCheckpoint original = CompletedCheckpoint.builder()
                .jobId("job1")
                .pipelineId("pipe1")
                .checkpointId(1L)
                .triggerTimestamp(1000L)
                .completedTimestamp(2000L)
                .checkpointType(CheckpointType.CHECKPOINT)
                .taskStates(taskStates)
                .build();

        byte[] bytes = CheckpointSerDe.serializeCheckpoint(original);
        CompletedCheckpoint restored = CheckpointSerDe.deserializeCheckpoint(bytes);

        assertNotNull(restored);
        assertEquals("job1", restored.getJobId());
        assertEquals("pipe1", restored.getPipelineId());
        assertEquals(1L, restored.getCheckpointId());
        assertEquals(1, restored.getTaskStates().size());

        TaskStateSnapshot restoredSnapshot = restored.getTaskStates().get(loc);
        assertNotNull(restoredSnapshot);
        assertEquals("op-value", restoredSnapshot.getOperatorState("op-key"));
        assertEquals("keyed-value", restoredSnapshot.getKeyedState("keyed-key"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractTaskStatesMap(String json) {
        io.nop.core.lang.json.JsonTool tool = new io.nop.core.lang.json.JsonTool();
        Map<String, Object> map = io.nop.core.lang.json.JsonTool.parseMap(json);
        String taskKey = map.containsKey("taskStates") ? "taskStates" : "taskSnapshots";
        return (Map<String, Object>) map.get(taskKey);
    }
}
