/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import io.nop.stream.core.checkpoint.SerializerFingerprint;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.StateSchemaResolver;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_SCHEMA_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@code getState()}-time schema compatibility check in
 * {@link MemoryKeyedStateBackend}. The check compares the current descriptor's
 * checksum against the restored state's descriptor checksum; mismatch throws
 * {@code ERR_STREAM_STATE_SCHEMA_MISMATCH}.
 */
class TestStateSchemaCompatibility {

    @Test
    void matchingDescriptorReturnsRestoredStateNormally() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("counter", Integer.class);
        backend.setCurrentKey("k1");
        ValueState<Integer> state = backend.getState(desc);
        state.update(42);

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        restored.restoreState(snapshot);

        restored.setCurrentKey("k1");
        // Same descriptor type → succeeds and returns restored value
        ValueState<Integer> restoredState = restored.getState(new ValueStateDescriptor<>("counter", Integer.class));
        assertEquals(Integer.valueOf(42), restoredState.value());
    }

    @Test
    void mismatchedValueTypeThrowsSchemaMismatch() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        ValueStateDescriptor<Integer> intDesc = new ValueStateDescriptor<>("counter", Integer.class);
        backend.setCurrentKey("k1");
        backend.getState(intDesc).update(7);

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        restored.restoreState(snapshot);

        // Now current code declares the state with a DIFFERENT value type (Long vs restored Integer)
        ValueStateDescriptor<Long> longDesc = new ValueStateDescriptor<>("counter", Long.class);
        StreamException ex = assertThrows(StreamException.class, () -> restored.getState(longDesc));
        assertEquals(ERR_STREAM_STATE_SCHEMA_MISMATCH.getErrorCode(), ex.getErrorCode());
        // State name appears in error params
        assertEquals("counter", ex.getParam("stateName"));
        // Checksums differ
        assertNotEquals(ex.getParam("expectedChecksum"), ex.getParam("actualChecksum"));
    }

    @Test
    void mismatchedMapKeyThrowsSchemaMismatch() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        MapStateDescriptor<String, Long> strKeyDesc = new MapStateDescriptor<>("map", String.class, Long.class);
        backend.setCurrentKey("k1");
        MapState<String, Long> s = backend.getMapState(strKeyDesc);
        s.put("a", 1L);

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        restored.restoreState(snapshot);

        // Different key type (Integer vs restored String) → must throw
        MapStateDescriptor<Integer, Long> intKeyDesc = new MapStateDescriptor<>("map", Integer.class, Long.class);
        StreamException ex = assertThrows(StreamException.class, () -> restored.getMapState(intKeyDesc));
        assertEquals(ERR_STREAM_STATE_SCHEMA_MISMATCH.getErrorCode(), ex.getErrorCode());
        assertEquals("map", ex.getParam("stateName"));
    }

    @Test
    void freshStartSkipsCheckAndCreatesNewState() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        // No restore happened → states map is empty → no check, creates new state
        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("fresh", Integer.class);
        ValueState<Integer> state = backend.getState(desc);
        // sanity: works as expected
        backend.setCurrentKey("k");
        state.update(99);
        assertEquals(Integer.valueOf(99), state.value());
    }

    @Test
    void matchingTypeButDifferentStateNameDoesNotConflict() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        ValueStateDescriptor<Integer> desc1 = new ValueStateDescriptor<>("state-a", Integer.class);
        backend.setCurrentKey("k1");
        backend.getState(desc1).update(10);

        // Take snapshot of state-a only
        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        restored.restoreState(snapshot);

        // Askiing for state-b (different name, same type) → no conflict (fresh state, not in restored map)
        ValueStateDescriptor<Integer> desc2 = new ValueStateDescriptor<>("state-b", Integer.class);
        ValueState<Integer> stateB = restored.getState(desc2);
        restored.setCurrentKey("k1");
        assertNull(stateB.value());

        // state-a still resolves with matching type
        ValueState<Integer> stateA = restored.getState(new ValueStateDescriptor<>("state-a", Integer.class));
        assertEquals(Integer.valueOf(10), stateA.value());
    }

    @Test
    void snapshotInfoContainsSchemaChecksumAndVersion() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("counter", Integer.class);
        backend.setCurrentKey("k");
        backend.getState(desc).update(1);

        StateSnapshot snapshot = backend.snapshotState();
        @SuppressWarnings("unchecked")
        Map<String, Object> states = (Map<String, Object>) snapshot.getStateData().get("states");
        @SuppressWarnings("unchecked")
        Map<String, Object> counterInfo = (Map<String, Object>) states.get("counter");

        assertTrue(counterInfo.containsKey("schemaChecksum"), "schemaChecksum must be present in info map");
        assertTrue(counterInfo.containsKey("schemaVersion"), "schemaVersion must be present in info map");
        assertEquals(1, ((Number) counterInfo.get("schemaVersion")).intValue());

        // The persisted checksum equals what the resolver computes for the same type signature
        SerializerFingerprint expectedFp = StateSchemaResolver.fromDescriptor(
                StateSchemaResolver.STATE_TYPE_VALUE, desc);
        assertEquals(expectedFp.getSchemaChecksum(), counterInfo.get("schemaChecksum"));
    }
}
