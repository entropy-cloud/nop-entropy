package io.nop.stream.core.common.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestMapStateDescriptor {

    @Test
    void testKeyClassAndValueClassPreserved() {
        MapStateDescriptor<String, Long> desc = new MapStateDescriptor<>("test", String.class, Long.class);
        assertEquals(String.class, desc.getKeyClass());
        assertEquals(Long.class, desc.getValueType());
    }

    @Test
    void testDifferentTypeCombinations() {
        MapStateDescriptor<Integer, String> desc = new MapStateDescriptor<>("test2", Integer.class, String.class);
        assertEquals(Integer.class, desc.getKeyClass());
        assertEquals(String.class, desc.getValueType());
    }
}