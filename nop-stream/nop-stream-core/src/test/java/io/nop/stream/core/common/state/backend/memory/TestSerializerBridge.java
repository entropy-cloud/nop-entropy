package io.nop.stream.core.common.state.backend.memory;

import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.typeutils.IStreamSerializer;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TestSerializerBridge {

    @Test
    void testMemoryStateSerDeWithCustomSerializer() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);

        ValueStateDescriptor<String> desc = new ValueStateDescriptor<>("test-state", String.class);
        TrackingSerializer customSer = new TrackingSerializer();
        desc.setSerializer(customSer);

        backend.setCurrentKey("key1");
        io.nop.stream.core.common.state.ValueState<String> state = backend.getState(desc);
        state.update("hello");
        state.update("world");

        StateSnapshot snapshot = backend.snapshotState();

        assertTrue(customSer.serializeCalled, "IStreamSerializer.serialize() must be called in snapshot path");
        assertFalse(customSer.deserializeCalled, "IStreamSerializer.deserialize() should not be called during snapshot");
    }

    @Test
    void testMemoryStateSerDeWithDefaultSerializerBackwardCompat() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);

        ValueStateDescriptor<String> desc = new ValueStateDescriptor<>("test-state", String.class);

        backend.setCurrentKey("key1");
        io.nop.stream.core.common.state.ValueState<String> state = backend.getState(desc);
        state.update("hello");

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        restored.restoreState(snapshot);

        restored.setCurrentKey("key1");
        io.nop.stream.core.common.state.ValueState<String> restoredState = restored.getState(
                new ValueStateDescriptor<>("test-state", String.class));
        assertEquals("hello", restoredState.value());
    }

    private static class TrackingSerializer implements IStreamSerializer<String> {
        private static final long serialVersionUID = 1L;
        boolean serializeCalled = false;
        boolean deserializeCalled = false;

        @Override
        public boolean isImmutableType() {
            return false;
        }

        @Override
        public TypeSerializer<String> duplicate() {
            TrackingSerializer copy = new TrackingSerializer();
            copy.serializeCalled = this.serializeCalled;
            copy.deserializeCalled = this.deserializeCalled;
            return copy;
        }

        @Override
        public String createInstance() {
            return "";
        }

        @Override
        public String copy(String from) {
            return from;
        }

        @Override
        public String copy(String from, String reuse) {
            return from;
        }

        @Override
        public int getLength() {
            return -1;
        }

        @Override
        public byte[] serialize(String value) {
            serializeCalled = true;
            return JsonTool.serialize(value, false).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        @SuppressWarnings("unchecked")
        public String deserialize(byte[] data, Class<String> type) {
            deserializeCalled = true;
            return JsonTool.parseBeanFromText(new String(data, StandardCharsets.UTF_8), type);
        }
    }
}
