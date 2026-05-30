package io.nop.stream.core.common.typeinfo;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestSimpleTypeSerializer {

    @Test
    void testSerializeAndDeserialize() throws Exception {
        SimpleTypeSerializer<String> serializer = new SimpleTypeSerializer<>(String.class);
        byte[] data = serializer.serialize("hello");
        String result = serializer.deserialize(data);
        assertEquals("hello", result);
    }

    @Test
    void testDeserializeNullReturnsNull() throws Exception {
        SimpleTypeSerializer<String> serializer = new SimpleTypeSerializer<>(String.class);
        assertNull(serializer.deserialize(null));
    }

    @Test
    void testDeserializeAllowedMapClass() throws Exception {
        SimpleTypeSerializer<Map> serializer = new SimpleTypeSerializer<>(Map.class);
        Map<String, String> map = new HashMap<>();
        map.put("key", "value");
        byte[] data = serializer.serialize(map);
        Map result = serializer.deserialize(data);
        assertEquals("value", result.get("key"));
    }

    @Test
    void testSerializeNullReturnsNull() throws Exception {
        SimpleTypeSerializer<String> serializer = new SimpleTypeSerializer<>(String.class);
        assertNull(serializer.serialize(null));
    }
}
