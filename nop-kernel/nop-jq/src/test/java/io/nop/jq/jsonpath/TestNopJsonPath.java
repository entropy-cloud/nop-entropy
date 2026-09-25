package io.nop.jq.jsonpath;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestNopJsonPath {

    @Test
    void testCompileReturnsStub() {
        NopCompiledJsonPath compiled = NopJsonPath.compile("$.foo");
        assertNotNull(compiled);
        assertEquals("$.foo", compiled.getPathString());
    }

    @Test
    void testEvalWorks() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("foo", "bar");
        NopCompiledJsonPath compiled = NopJsonPath.compile("$.foo");
        assertEquals("bar", compiled.eval(data));
    }

    @Test
    void testSetWorks() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("foo", "old");
        NopCompiledJsonPath compiled = NopJsonPath.compile("$.foo");
        assertTrue(compiled.set(data, "new"));
        assertEquals("new", compiled.eval(data));
    }

    @Test
    void testRemoveWorks() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("foo", "bar");
        NopCompiledJsonPath compiled = NopJsonPath.compile("$.foo");
        assertTrue(compiled.remove(data));
        assertFalse(data.containsKey("foo"));
    }

    @Test
    void testSizeWorks() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("foo", "bar");
        NopCompiledJsonPath compiled = NopJsonPath.compile("$.foo");
        assertEquals(1, compiled.size(data));
    }

    @Test
    void testContainsWorks() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("foo", "bar");
        NopCompiledJsonPath compiled = NopJsonPath.compile("$.foo");
        assertTrue(compiled.contains(data));
        assertFalse(NopJsonPath.compile("$.missing").contains(data));
    }
}
