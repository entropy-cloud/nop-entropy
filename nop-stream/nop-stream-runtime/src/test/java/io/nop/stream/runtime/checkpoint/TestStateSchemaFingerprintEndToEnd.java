/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.runtime.checkpoint.storage.CheckpointSerDe;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_SCHEMA_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test for the Stage 29 schema-fingerprint system. Verifies the full
 * snapshot → CheckpointSerDe serialize → LocalFileCheckpointStorage persist → reload
 * → deserialize → MemoryKeyedStateBackend.restoreState → getState() chain:
 *
 * <ul>
 *   <li>persisted JSON contains {@code schemaChecksum} per keyed state,</li>
 *   <li>restore + {@code getState()} with matching descriptor type succeeds,</li>
 *   <li>{@code getState()} with a mismatched descriptor type throws
 *       {@code ERR_STREAM_STATE_SCHEMA_MISMATCH} (wiring verification: the
 *       {@code getState()} check is actually invoked at runtime).</li>
 * </ul>
 */
class TestStateSchemaFingerprintEndToEnd {

    private static final TaskLocation LOC_0 = new TaskLocation("job-e2e", "pipe-e2e", "v0", 0);

    @TempDir
    Path tempDir;

    @Test
    void snapshotSerializePersistReloadRestoreGetStateRoundTrip() throws Exception {
        // 1. Build keyed state with a ValueState holding two keys
        MemoryStateBackend stateBackend = new MemoryStateBackend();
        IKeyedStateBackend<String> backend = stateBackend.createKeyedStateBackend(String.class);
        ValueStateDescriptor<Long> desc = new ValueStateDescriptor<>("counter", Long.class, 0L);

        backend.setCurrentKey("key1");
        ValueState<Long> state1 = backend.getState(desc);
        state1.update(100L);
        backend.setCurrentKey("key2");
        ValueState<Long> state2 = backend.getState(desc);
        state2.update(200L);

        // 2. Snapshot the keyed backend into a TaskStateSnapshot / CompletedCheckpoint
        StateSnapshot keyedSnapshot = backend.snapshotState();
        TaskStateSnapshot taskSnapshot = new TaskStateSnapshot(LOC_0);
        taskSnapshot.putKeyedState("memory-backend", keyedSnapshot);

        Map<TaskLocation, TaskStateSnapshot> taskStates = new LinkedHashMap<>();
        taskStates.put(LOC_0, taskSnapshot);

        CompletedCheckpoint checkpoint = CompletedCheckpoint.builder()
                .jobId("job-e2e")
                .pipelineId("pipe-e2e")
                .checkpointId(1L)
                .triggerTimestamp(1000L)
                .completedTimestamp(2000L)
                .checkpointType(CheckpointType.CHECKPOINT)
                .taskStates(taskStates)
                .build();

        // 3. Persist via LocalFileCheckpointStorage (which uses CheckpointSerDe under the hood)
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        String path = storage.storeCheckPoint(checkpoint);

        // 4. Reload the persisted JSON and verify schemaChecksum is present per keyed state
        byte[] persisted = Files.readAllBytes(Path.of(path));
        String json = new String(persisted, StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> cpMap = JsonTool.parseMap(json);
        assertEquals(2, ((Number) cpMap.get("formatVersion")).intValue(),
                "formatVersion envelope must be present (G59)");

        @SuppressWarnings("unchecked")
        Map<String, Object> taskStatesReloaded = (Map<String, Object>) cpMap.get("taskStates");
        @SuppressWarnings("unchecked")
        Map<String, Object> taskSnapshotReloaded = (Map<String, Object>)
                taskStatesReloaded.get(CheckpointSerDe.taskLocationToString(LOC_0));
        @SuppressWarnings("unchecked")
        Map<String, Object> keyedStatesReloaded = (Map<String, Object>)
                taskSnapshotReloaded.get("keyedStates");
        @SuppressWarnings("unchecked")
        Map<String, Object> backendInfo = (Map<String, Object>)
                keyedStatesReloaded.get("memory-backend");
        assertNotNull(backendInfo, "keyed state 'memory-backend' must be present");

        @SuppressWarnings("unchecked")
        Map<String, Object> backendStateData = (Map<String, Object>) backendInfo.get("stateData");
        @SuppressWarnings("unchecked")
        Map<String, Object> statesMap = (Map<String, Object>) backendStateData.get("states");
        @SuppressWarnings("unchecked")
        Map<String, Object> counterInfo = (Map<String, Object>) statesMap.get("counter");
        assertTrue(counterInfo.containsKey("schemaChecksum"),
                "Persisted checkpoint JSON must contain schemaChecksum per keyed state");
        assertEquals(1, ((Number) counterInfo.get("schemaVersion")).intValue());

        // 5. Restore into a fresh backend via CheckpointSerDe.deserializeCheckpoint
        CompletedCheckpoint restoredCheckpoint = CheckpointSerDe.deserializeCheckpoint(persisted);
        assertNotNull(restoredCheckpoint);

        TaskStateSnapshot restoredTaskSnapshot = restoredCheckpoint.getTaskStates().get(LOC_0);
        assertNotNull(restoredTaskSnapshot);
        Object rawKeyedState = restoredTaskSnapshot.getKeyedState("memory-backend");
        assertNotNull(rawKeyedState, "Restored keyed state must be present");

        MemoryKeyedStateBackend<String> restoredBackend = new MemoryKeyedStateBackend<>(String.class);
        // rawKeyedState is a Map (post-JSON-roundtrip), rebuild a StateSnapshot for restoreState
        @SuppressWarnings("unchecked")
        StateSnapshot restoredSnapshot = new StateSnapshot((Map<String, Object>) rawKeyedState);
        restoredBackend.restoreState(restoredSnapshot);

        // 6. getState() with matching descriptor type → succeeds, returns restored values
        restoredBackend.setCurrentKey("key1");
        assertEquals(Long.valueOf(100L),
                restoredBackend.getState(new ValueStateDescriptor<>("counter", Long.class, 0L)).value());
        restoredBackend.setCurrentKey("key2");
        assertEquals(Long.valueOf(200L),
                restoredBackend.getState(new ValueStateDescriptor<>("counter", Long.class, 0L)).value());

        // 7. Wiring verification: getState() with mismatched descriptor type throws schema mismatch
        // (this asserts the runtime check is actually invoked — not just type-level presence)
        StreamException ex = assertThrows(StreamException.class,
                () -> restoredBackend.getState(new ValueStateDescriptor<>("counter", Integer.class)));
        assertEquals(ERR_STREAM_STATE_SCHEMA_MISMATCH.getErrorCode(), ex.getErrorCode());
        assertEquals("counter", ex.getParam("stateName"));

        backend.close();
        restoredBackend.close();
    }
}
