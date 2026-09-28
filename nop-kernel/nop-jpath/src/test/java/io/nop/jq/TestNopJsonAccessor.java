package io.nop.jq;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestNopJsonAccessor {

    private final NopJsonAccessor accessor = NopJsonAccessor.INSTANCE;

    @Test
    void testGetMapProperty() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Alice");
        map.put("age", 30);

        assertEquals("Alice", accessor.getProperty(map, "name"));
        assertEquals(30, accessor.getProperty(map, "age"));
        assertNull(accessor.getProperty(map, "missing"));
    }

    @Test
    void testSetMapProperty() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Alice");

        accessor.setProperty(map, "name", "Bob");
        assertEquals("Bob", map.get("name"));

        accessor.setProperty(map, "new", "value");
        assertEquals("value", map.get("new"));
    }

    @Test
    void testHasMapProperty() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", "value");

        assertTrue(accessor.hasProperty(map, "key"));
        assertFalse(accessor.hasProperty(map, "missing"));
    }

    @Test
    void testGetMapPropertyNames() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", 1);
        map.put("b", 2);

        assertEquals(2, accessor.getPropertyNames(map).size());
        assertTrue(accessor.getPropertyNames(map).contains("a"));
    }

    @Test
    void testIsMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        assertTrue(accessor.isMap(map));

        // JavaBeans are treated as maps by the accessor
        assertFalse(accessor.isMap(null));
        assertFalse(accessor.isMap("string"));
        assertFalse(accessor.isMap(Arrays.asList(1, 2)));
    }

    @Test
    void testIsArray() {
        List<Integer> list = Arrays.asList(1, 2, 3);
        assertTrue(accessor.isArray(list));
        assertTrue(accessor.isArray(new int[]{1, 2}));
        assertFalse(accessor.isArray(new LinkedHashMap<>()));
        assertFalse(accessor.isArray(null));
    }

    @Test
    void testSize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", 1);
        assertEquals(1, accessor.size(map));

        List<Integer> list = Arrays.asList(1, 2, 3);
        assertEquals(3, accessor.size(list));

        assertEquals(0, accessor.size(null));
    }

    @Test
    void testGetArrayItem() {
        List<Integer> list = Arrays.asList(10, 20, 30);
        assertEquals(10, accessor.getArrayItem(list, 0));
        assertEquals(30, accessor.getArrayItem(list, 2));
    }

    @Test
    void testSetArrayItem() {
        List<Object> list = new java.util.ArrayList<>(Arrays.asList(1, 2, 3));
        accessor.setArrayItem(list, 1, 99);
        assertEquals(99, list.get(1));
    }

    @Test
    void testGetTypeName() {
        assertEquals("null", accessor.getTypeName(null));
        assertEquals("object", accessor.getTypeName(new LinkedHashMap<>()));
        assertEquals("array", accessor.getTypeName(Arrays.asList(1)));
        assertEquals("string", accessor.getTypeName("hello"));
        assertEquals("boolean", accessor.getTypeName(true));
        assertEquals("number", accessor.getTypeName(42));
    }
}
