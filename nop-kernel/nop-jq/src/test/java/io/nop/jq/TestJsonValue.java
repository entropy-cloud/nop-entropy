package io.nop.jq;

import io.nop.jq.jsonvalue.JsonArray;
import io.nop.jq.jsonvalue.JsonBoolean;

import io.nop.jq.jsonvalue.JsonNumber;
import io.nop.jq.jsonvalue.JsonObject;
import io.nop.jq.jsonvalue.JsonString;
import io.nop.jq.jsonvalue.JsonValue;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestJsonValue {

    @Test
    void testNullType() {
        JsonValue v = JsonValue.NULL;
        assertTrue(v.isNull());
        assertFalse(v.isBoolean());
        assertFalse(v.isNumber());
        assertFalse(v.isString());
        assertFalse(v.isArray());
        assertFalse(v.isObject());
        assertNull(v.toObject());
        assertEquals("null", v.toString());
        assertEquals(JsonValue.NULL, v);
    }

    @Test
    void testBooleanType() {
        JsonValue t = JsonValue.TRUE;
        JsonValue f = JsonValue.FALSE;
        assertTrue(t.isBoolean());
        assertTrue(t.asBoolean().value());
        assertFalse(f.asBoolean().value());
        assertEquals(true, t.toObject());
        assertEquals("true", t.toString());
        assertEquals("false", f.toString());
        assertEquals(t, JsonValue.ofBoolean(true));
        assertNotEquals(t, f);
    }

    @Test
    void testNumberType() {
        JsonValue n = JsonValue.ofInt(42);
        assertTrue(n.isNumber());
        assertEquals(42, n.asNumber().intValue());
        assertEquals(42, n.toObject());

        JsonValue d = JsonValue.ofDouble(3.14);
        assertEquals(3.14, d.asNumber().doubleValue());

        JsonValue l = JsonValue.ofLong(100L);
        assertEquals(100L, l.asNumber().longValue());

        assertEquals(n, JsonValue.ofInt(42));
        assertNotEquals(n, d);
    }

    @Test
    void testStringType() {
        JsonValue s = JsonValue.ofString("hello");
        assertTrue(s.isString());
        assertEquals("hello", s.asString().value());
        assertEquals(5, s.asString().length());
        assertEquals("hello", s.toObject());
        assertEquals(s, JsonValue.ofString("hello"));
        assertNotEquals(s, JsonValue.ofString("world"));
    }

    @Test
    void testArrayType() {
        JsonValue a = JsonValue.ofList(Arrays.asList(1, "two", true));
        assertTrue(a.isArray());
        JsonArray arr = a.asArray();
        assertEquals(3, arr.size());
        assertTrue(arr.get(0).isNumber());
        assertTrue(arr.get(1).isString());
        assertTrue(arr.get(2).isBoolean());
        assertFalse(arr.isEmpty());

        JsonValue empty = JsonValue.emptyArray();
        assertTrue(empty.asArray().isEmpty());
    }

    @Test
    void testObjectType() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "test");
        map.put("count", 42);
        map.put("active", true);

        JsonValue o = JsonValue.ofMap(map);
        assertTrue(o.isObject());
        JsonObject obj = o.asObject();
        assertEquals(3, obj.size());
        assertEquals("test", obj.getString("name"));
        assertEquals(42, obj.getInt("count", 0));
        assertTrue(obj.has("name"));
        assertFalse(obj.has("missing"));
        assertEquals(map.keySet(), obj.keySet());
    }

    @Test
    void testFromObjectRoundTrip() {
        assertEquals(JsonValue.NULL, JsonValue.fromObject(null));
        assertEquals(JsonValue.TRUE, JsonValue.fromObject(true));
        assertEquals(JsonValue.ofInt(1), JsonValue.fromObject(1));
        assertEquals(JsonValue.ofString("x"), JsonValue.fromObject("x"));
    }

    @Test
    void testImmutableArrayOperations() {
        JsonArray a = JsonValue.emptyArray();
        JsonArray b = a.add(JsonValue.ofInt(1));
        assertEquals(0, a.size());
        assertEquals(1, b.size());
    }

    @Test
    void testImmutableObjectOperations() {
        JsonObject o = JsonValue.emptyObject();
        JsonObject o2 = o.put("key", JsonValue.ofString("val"));
        assertEquals(0, o.size());
        assertEquals(1, o2.size());
        assertEquals("val", o2.getString("key"));
    }

    @Test
    void testTypeCastException() {
        assertThrows(IllegalArgumentException.class, () -> JsonValue.NULL.asBoolean());
        assertThrows(IllegalArgumentException.class, () -> JsonValue.TRUE.asNumber());
        assertThrows(IllegalArgumentException.class, () -> JsonValue.ofInt(1).asString());
    }
}
