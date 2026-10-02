package io.nop.core.lang.json.utils;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestJsonTransformHelper {

    @Test
    public void testTransformScalarsWithTransformer() {
        assertEquals("N1", JsonTransformHelper.transform("n1", v -> ((String) v).toUpperCase()));
        assertEquals("x", JsonTransformHelper.transform(null, v -> "x"), "scalar null is passed to transformer");
    }

    @Test
    public void testTransformListRecurses() {
        List<Object> list = Arrays.asList("a", Arrays.asList("b", "c"));
        Object result = JsonTransformHelper.transform(list, v -> v instanceof String ? ((String) v).toUpperCase() : v);
        List<?> out = (List<?>) result;
        assertEquals("A", out.get(0));
        List<?> nested = (List<?>) out.get(1);
        assertEquals("B", nested.get(0));
        assertEquals("C", nested.get(1));
    }

    @Test
    public void testTransformMapRecursesIntoNestedValues() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("s", "x");
        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("inner", inner);
        outer.put("num", 3);

        Object result = JsonTransformHelper.transform(outer,
                v -> v instanceof String ? ((String) v).toUpperCase() : v);

        Map<?, ?> outMap = (Map<?, ?>) result;
        assertEquals("X", ((Map<?, ?>) outMap.get("inner")).get("s"));
        assertEquals(3, outMap.get("num"), "non-string values should pass through unchanged");
    }

    @Test
    public void testTransformMapWithPredicateStopsAtMatch() {
        // predicate 命中的 map 整体作为 transformer 的入参，不再下钻
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("kind", "special");

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("child", target);

        Object result = JsonTransformHelper.transform(root,
                v -> {
                    ((Map<String, Object>) v).put("transformed", true);
                    return v;
                },
                v -> v instanceof Map && "special".equals(((Map<?, ?>) v).get("kind")));

        Map<?, ?> child = (Map<?, ?>) ((Map<?, ?>) result).get("child");
        assertEquals(Boolean.TRUE, child.get("transformed"), "matched map should be transformed as a whole");
    }

    @Test
    public void testTransformInPlaceVisitsEveryNode() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", "1");
        map.put("list", Arrays.asList("2", "3"));

        JsonTransformHelper.transformInPlace(map, v -> {
            if (v instanceof String)
                return "P" + v;
            return v;
        });

        assertEquals("P1", map.get("a"));
        assertEquals(Arrays.asList("P2", "P3"), map.get("list"));
    }

    @Test
    public void testTrimKeysAndValues() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("k ", " v ");
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(" a ", inner);
        map.put("list", Arrays.asList(" x "));

        Object result = JsonTransformHelper.trim(map, true, true);
        Map<?, ?> out = (Map<?, ?>) result;
        Map<?, ?> innerOut = (Map<?, ?>) out.get("a");
        assertEquals("v", innerOut.get("k"), "keys and string values should be trimmed");
        assertEquals("x", ((List<?>) out.get("list")).get(0));
    }

    @Test
    public void testTrimValueOnlyDoesNotTouchKeys() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(" k ", " v ");
        Object result = JsonTransformHelper.trim(map, false, true);
        assertEquals("v", ((Map<?, ?>) result).get(" k "), "keys should stay untouched when trimKey=false");
    }

    @Test
    public void testTransformMapEntryInPlaceReplacesMatchedEntry() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("secret", "abc");
        map.put("normal", "def");

        JsonTransformHelper.transformMapEntryInPlace(map,
                (name, value) -> "secret".equals(name),
                (loc, name, value) -> "***");

        assertEquals("***", map.get("secret"));
        assertEquals("def", map.get("normal"));
    }

    @Test
    public void testTransformMapEntryInPlaceRecursesLists() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("secret", 1);
        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("list", Arrays.asList(inner));

        JsonTransformHelper.transformMapEntryInPlace(outer,
                (name, value) -> "secret".equals(name),
                (loc, name, value) -> 0);

        Map<?, ?> transformedInner = (Map<?, ?>) ((List<?>) outer.get("list")).get(0);
        assertEquals(0, transformedInner.get("secret"));
    }

    @Test
    public void testTransformNullListReturnsNull() {
        assertNull(JsonTransformHelper.transformList(null, v -> v, null));
        assertNull(JsonTransformHelper.transformMap(null, v -> v, null));
    }

    @Test
    public void testTransformScalarWithPredicateRejectKeepsOriginal() {
        Object result = JsonTransformHelper.transform("abc",
                v -> "changed",
                v -> v.equals("match"));
        assertSame("abc", result, "predicate not matched should keep original value");
    }
}
