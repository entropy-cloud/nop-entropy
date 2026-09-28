package io.nop.jpath;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestJsonPathApi {

    private Map<String, Object> sampleData() {
        Map<String, Object> book1 = new LinkedHashMap<>();
        book1.put("title", "Sayings of the Century");
        book1.put("author", "Nigel Rees");
        book1.put("price", 8.95);

        Map<String, Object> book2 = new LinkedHashMap<>();
        book2.put("title", "Sword of Honour");
        book2.put("author", "Evelyn Waugh");
        book2.put("price", 12.99);

        Map<String, Object> store = new LinkedHashMap<>();
        store.put("book", Arrays.asList(book1, book2));
        store.put("bicycle", "cheap");

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("store", store);
        return root;
    }

    @Test
    void testRead() {
        String json = "{\"name\":\"test\",\"value\":42}";
        Object result = NopJsonPath.read(json, "$.name");
        assertEquals("test", result);
    }

    @Test
    void testSet() {
        Map<String, Object> data = sampleData();
        boolean ok = NopJsonPath.set(data, "$.store.bicycle", "expensive");
        assertTrue(ok);
        assertEquals("expensive", NopJsonPath.eval(data, "$.store.bicycle"));
    }

    @Test
    void testSetArrayElement() {
        Map<String, Object> data = sampleData();
        boolean ok = NopJsonPath.set(data, "$.store.book[0].title", "New Title");
        assertTrue(ok);
        assertEquals("New Title", NopJsonPath.eval(data, "$.store.book[0].title"));
    }

    @Test
    void testRemove() {
        Map<String, Object> data = sampleData();
        boolean ok = NopJsonPath.remove(data, "$.store.bicycle");
        assertTrue(ok);
        assertNull(NopJsonPath.eval(data, "$.store.bicycle"));
    }

    @Test
    void testSize() {
        Map<String, Object> data = sampleData();
        assertEquals(2, NopJsonPath.size(data, "$.store.book"));
        assertEquals(1, NopJsonPath.size(data, "$.store.bicycle"));
    }

    @Test
    void testContains() {
        Map<String, Object> data = sampleData();
        assertTrue(NopJsonPath.contains(data, "$.store.bicycle"));
        assertFalse(NopJsonPath.contains(data, "$.store.missing"));
    }

    @Test
    void testContainsValue() {
        Map<String, Object> data = sampleData();
        assertTrue(NopJsonPath.containsValue(data, "$.store.bicycle", "cheap"));
        assertFalse(NopJsonPath.containsValue(data, "$.store.bicycle", "expensive"));
    }

    @Test
    void testKeySet() {
        Map<String, Object> data = sampleData();
        Set<String> keys = NopJsonPath.keySet(data, "$.store");
        assertTrue(keys.contains("book"));
        assertTrue(keys.contains("bicycle"));
    }

    @Test
    void testPaths() {
        Map<String, Object> data = sampleData();
        List<List<Object>> paths = NopJsonPath.paths(data, "$.store");
        assertFalse(paths.isEmpty());
    }

    @Test
    void testCompiledSet() {
        Map<String, Object> data = sampleData();
        NopCompiledJsonPath compiled = NopJsonPath.compile("$.store.bicycle");
        assertTrue(compiled.set(data, "new value"));
        assertEquals("new value", compiled.eval(data));
    }
}
