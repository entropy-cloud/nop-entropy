package io.nop.stream.core.common.typeutils;

import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestIStreamSerializer {

    @Test
    void testJsonToolSerializerRoundTrip() {
        JsonToolSerializer<String> serializer = new JsonToolSerializer<>();
        String original = "hello world";

        byte[] data = serializer.serialize(original);
        assertNotNull(data);

        String result = serializer.deserialize(data, String.class);
        assertEquals(original, result);
    }

    @Test
    void testJsonToolSerializerIntegerRoundTrip() {
        JsonToolSerializer<Integer> serializer = new JsonToolSerializer<>();
        Integer original = 42;

        byte[] data = serializer.serialize(original);
        assertNotNull(data);

        Integer result = serializer.deserialize(data, Integer.class);
        assertEquals(original, result);
    }

    @Test
    void testStateDescriptorDefaultSerializer() {
        ValueStateDescriptor<String> descriptor = new ValueStateDescriptor<>("test", String.class);

        TypeSerializer<?> serializer = descriptor.getSerializer();
        assertNotNull(serializer);
        assertTrue(serializer instanceof IStreamSerializer);
        assertTrue(serializer instanceof JsonToolSerializer);
    }

    @Test
    void testStateDescriptorCustomSerializer() {
        ValueStateDescriptor<Integer> descriptor = new ValueStateDescriptor<>("test", Integer.class);

        JsonToolSerializer<Integer> customSer = new JsonToolSerializer<>();
        descriptor.setSerializer(customSer);

        TypeSerializer<Integer> retrievedSer = descriptor.getSerializer();
        assertSame(customSer, retrievedSer);
    }

    @Test
    void testIStreamSerializerExtendsTypeSerializer() {
        JsonToolSerializer<String> serializer = new JsonToolSerializer<>();
        assertTrue(serializer instanceof TypeSerializer);
        assertTrue(serializer instanceof IStreamSerializer);
    }

    @Test
    void testTypeSerializerMethods() {
        JsonToolSerializer<String> serializer = new JsonToolSerializer<>();
        assertFalse(serializer.isImmutableType());
        assertNotNull(serializer.duplicate());
        assertEquals(-1, serializer.getLength());
        assertNull(serializer.createInstance());
    }

    @Test
    void testNullSerialize() {
        JsonToolSerializer<String> serializer = new JsonToolSerializer<>();
        assertNull(serializer.serialize(null));
    }

    @Test
    void testNullDeserialize() {
        JsonToolSerializer<String> serializer = new JsonToolSerializer<>();
        assertNull(serializer.deserialize(null, String.class));
    }
}
