package io.nop.stream.core.common.state.backend.memory;

import io.nop.stream.core.common.accumulators.LongCounter;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import static io.nop.stream.core.exceptions.NopStreamErrors.*;
import static org.junit.jupiter.api.Assertions.*;

class TestMemoryInternalAppendingState {

    /**
     * Test that add() throws StreamException when storage contains a wrong-type value.
     * We directly inject a String into storage where Long is expected, then call add().
     */
    @Test
    void testAddDetectsTypeMismatch() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        ReducingStateDescriptor<Long> descriptor = new ReducingStateDescriptor<>("test", Long.class, LongCounter.class);

        MemoryInternalAppendingState<String, Object, Long, Object> state =
                new MemoryInternalAppendingState<>(backend, descriptor);

        // Set namespace so getStorageKey() works
        state.setCurrentNamespace("default");
        backend.setCurrentKey("key1");

        // Directly inject a wrong-type value into storage (bypassing normal add flow)
        TypedNamespaceAndKey nk = new TypedNamespaceAndKey("default", "key1");
        state.storage.put(nk, "wrong-type-string");

        // add() should detect the type mismatch and throw
        StreamException ex = assertThrows(StreamException.class, () -> state.add(42L));
        assertEquals(ERR_STREAM_TYPE_MISMATCH.getErrorCode(), ex.getErrorCode());
        assertEquals("java.lang.Long", ex.getParam(ARG_EXPECTED_TYPE));
        assertEquals("java.lang.String", ex.getParam(ARG_ACTUAL_TYPE));
    }

    /**
     * Verify that add() works normally when storage has correct type.
     */
    @Test
    void testAddWorksWithCorrectType() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        ReducingStateDescriptor<Long> descriptor = new ReducingStateDescriptor<>("test", Long.class, LongCounter.class);

        MemoryInternalAppendingState<String, Object, Long, Object> state =
                new MemoryInternalAppendingState<>(backend, descriptor);

        state.setCurrentNamespace("default");
        backend.setCurrentKey("key1");

        // Normal add should work
        state.add(10L);
        state.add(20L);

        Object result = state.get();
        assertNotNull(result);
        assertEquals(30L, result);
    }
}
