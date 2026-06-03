package io.nop.stream.core.common.state.backend.memory;

import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestMemoryInternalAppendingStateIsolation {

    @SuppressWarnings("unchecked")
    private static ReducingStateDescriptor<ArrayList<String>> createDescriptor() {
        return new ReducingStateDescriptor<>("list-state",
                (Class<ArrayList<String>>) (Class<?>) ArrayList.class,
                (Class<? extends SimpleAccumulator<ArrayList<String>>>) (Class<?>) ListMergeAccumulator.class);
    }

    static class ListMergeAccumulator implements SimpleAccumulator<ArrayList<String>> {
        private ArrayList<String> value = new ArrayList<>();

        @Override
        public void add(ArrayList<String> value) {
            this.value.addAll(value);
        }

        @Override
        public ArrayList<String> getLocalValue() {
            return value;
        }

        @Override
        public void resetLocal() {
            value = new ArrayList<>();
        }

        @Override
        public void merge(io.nop.stream.core.common.accumulators.Accumulator<ArrayList<String>, ArrayList<String>> other) {
            value.addAll(other.getLocalValue());
        }

        @Override
        public ListMergeAccumulator clone() {
            ListMergeAccumulator copy = new ListMergeAccumulator();
            copy.value = new ArrayList<>(value);
            return copy;
        }
    }

    @Test
    void testListAccumulatorKeyIsolation() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);

        ReducingStateDescriptor<ArrayList<String>> descriptor = createDescriptor();

        MemoryInternalAppendingState<String, Object, ArrayList<String>, Object> state =
                new MemoryInternalAppendingState<>(backend, descriptor);
        state.setCurrentNamespace("default");

        backend.setCurrentKey("keyA");
        state.add(new ArrayList<>(List.of("a1")));

        backend.setCurrentKey("keyB");
        state.add(new ArrayList<>(List.of("b1")));

        backend.setCurrentKey("keyA");
        state.add(new ArrayList<>(List.of("a2")));

        backend.setCurrentKey("keyA");
        @SuppressWarnings("unchecked")
        ArrayList<String> keyAValue = (ArrayList<String>) state.get();
        assertNotNull(keyAValue);
        assertTrue(keyAValue.contains("a1"), "keyA should contain a1");
        assertTrue(keyAValue.contains("a2"), "keyA should contain a2");
        assertFalse(keyAValue.contains("b1"), "keyA should NOT contain b1");

        backend.setCurrentKey("keyB");
        @SuppressWarnings("unchecked")
        ArrayList<String> keyBValue = (ArrayList<String>) state.get();
        assertNotNull(keyBValue);
        assertTrue(keyBValue.contains("b1"), "keyB should contain b1");
        assertFalse(keyBValue.contains("a1"), "keyB should NOT contain a1");
        assertFalse(keyBValue.contains("a2"), "keyB should NOT contain a2");
    }

    @Test
    void testListAccumulatorStorageReferencesAreDistinct() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);

        ReducingStateDescriptor<ArrayList<String>> descriptor = createDescriptor();

        MemoryInternalAppendingState<String, Object, ArrayList<String>, Object> state =
                new MemoryInternalAppendingState<>(backend, descriptor);
        state.setCurrentNamespace("default");

        backend.setCurrentKey("keyA");
        state.add(new ArrayList<>(List.of("a1")));

        backend.setCurrentKey("keyB");
        state.add(new ArrayList<>(List.of("b1")));

        TypedNamespaceAndKey nkA = new TypedNamespaceAndKey("default", "keyA");
        TypedNamespaceAndKey nkB = new TypedNamespaceAndKey("default", "keyB");

        Object valA = state.storage.get(nkA);
        Object valB = state.storage.get(nkB);

        assertNotNull(valA);
        assertNotNull(valB);
        assertNotSame(valA, valB, "Storage entries for different keys must be distinct references");
    }
}
