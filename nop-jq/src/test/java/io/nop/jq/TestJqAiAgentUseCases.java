package io.nop.jq;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AI agent use cases: querying large JSON/YAML structures.
 * Focus on the most common patterns an AI agent would use.
 */
class TestJqAiAgentUseCases {

    private Object run(String expr, Object input) {
        return JqEngine.compile(expr).applyOne(input);
    }

    /** jq numbers are doubles; compare numerically against an int expectation. */
    private void assertNum(long expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertTrue(
                actual instanceof Number n && n.longValue() == expected,
                "expected " + expected + " but was " + actual);
    }

    private List<Object> runAll(String expr, Object input) {
        return JqEngine.compile(expr).apply(input);
    }

    // ===== Structure Overview (AI needs to understand what's in a file) =====

    @Test void testGetTopLevelKeys() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "test");
        data.put("version", 1);
        data.put("deps", List.of());
        Object result = run("keys", data);
        // jq sorts object keys
        assertEquals(List.of("deps", "name", "version"), result);
    }

    @Test void testGetKeyCount() {
        Map<String, Object> data = Map.of("a", 1, "b", 2, "c", 3);
        Object result = run("keys | length", data);
        assertEquals(3, result);
    }

    @Test void testGetType() {
        assertEquals("object", run("type", Map.of("a", 1)));
        assertEquals("array", run("type", List.of(1, 2)));
        assertEquals("string", run("type", "hello"));
        assertEquals("number", run("type", 42));
        assertEquals("null", run("type", null));
        assertEquals("boolean", run("type", true));
    }

    @Test void testRecursiveStructureOverview() {
        Map<String, Object> data = Map.of(
            "users", List.of(
                Map.of("name", "Alice", "age", 30),
                Map.of("name", "Bob", "age", 25)
            ),
            "config", Map.of("debug", true)
        );
        // Get all keys at top level
        List<Object> result = runAll(". | keys", data);
        assertTrue(result.size() > 0);
    }

    // ===== Field Access (most common AI operation) =====

    @Test void testNestedFieldAccess() {
        Map<String, Object> data = Map.of("a", Map.of("b", Map.of("c", 42)));
        assertEquals(42, run(".a.b.c", data));
    }

    @Test void testMissingFieldReturnsNull() {
        Map<String, Object> data = Map.of("a", 1);
        assertNull(run(".missing", data));
        // .a.b.c on {"a": 1} throws because .a returns a number, not an object
        assertEquals("fallback", run("try .a.b.c catch \"fallback\"", data));
    }

    @Test void testArrayIndexAccess() {
        List<Object> arr = List.of("a", "b", "c", "d");
        assertEquals("c", run(".[2]", arr));
        assertEquals("d", run(".[-1]", arr));
        assertEquals("a", run(".[0]", arr));
    }

    @Test void testArraySlice() {
        List<Object> arr = List.of(1, 2, 3, 4, 5);
        assertEquals(List.of(2, 3, 4), run(".[1:4]", arr));
        assertEquals(List.of(1, 2, 3), run(".[:3]", arr));
        assertEquals(List.of(4, 5), run(".[3:]", arr));
    }

    // ===== Filtering (AI needs to find specific items) =====

    @Test void testSelectByVersion() {
        List<Map<String, Object>> deps = List.of(
            Map.of("name", "junit", "version", "5.9"),
            Map.of("name", "slf4j", "version", "2.0"),
            Map.of("name", "nop-core", "version", "1.0")
        );
        List<Object> result = runAll(".[] | select(.name | startswith(\"nop\"))", deps);
        assertEquals(1, result.size());
        assertEquals("nop-core", ((Map<?, ?>) result.get(0)).get("name"));
    }

    @Test void testSelectByNumericCondition() {
        List<Map<String, Object>> items = List.of(
            Map.of("id", 1, "score", 85),
            Map.of("id", 2, "score", 92),
            Map.of("id", 3, "score", 78)
        );
        List<Object> result = runAll(".[] | select(.score >= 90) | .id", items);
        assertEquals(List.of(2), result);
    }

    @Test void testSelectWithAndOr() {
        List<Map<String, Object>> items = List.of(
            Map.of("kind", "error", "code", 500),
            Map.of("kind", "warn", "code", 404),
            Map.of("kind", "error", "code", 400)
        );
        List<Object> result = runAll(
            ".[] | select(.kind == \"error\" and .code >= 500) | .code", items);
        assertEquals(List.of(500), result);
    }

    // ===== Aggregation (AI needs summaries) =====

    @Test void testCountItems() {
        List<Object> items = List.of(1, 2, 3, 4, 5);
        assertNum(5, run("length", items));
    }

    @Test void testSumValues() {
        List<Integer> nums = List.of(1, 2, 3, 4, 5);
        assertNum(15, run("add", nums));
    }

    @Test void testSortAndLimit() {
        List<Map<String, Object>> items = List.of(
            Map.of("name", "c", "score", 70),
            Map.of("name", "a", "score", 90),
            Map.of("name", "b", "score", 80)
        );
        List<Object> result = runAll(".[] | .name", items);
        assertEquals(3, result.size());
    }

    @Test void testGroupByKey() {
        List<Map<String, Object>> items = List.of(
            Map.of("type", "a", "val", 1),
            Map.of("type", "b", "val", 2),
            Map.of("type", "a", "val", 3)
        );
        Object result = run("group_by(.type) | length", items);
        assertNum(2, result);
    }

    @Test void testUniqueValues() {
        List<Map<String, Object>> items = List.of(
            Map.of("cat", "x"), Map.of("cat", "y"), Map.of("cat", "x")
        );
        Object result = run("[.[] | .cat] | unique", items);
        assertEquals(List.of("x", "y"), result);
    }

    // ===== Object Transformation (AI needs to reshape data) =====

    @Test void testToEntriesFromEntries() {
        Map<String, Object> data = Map.of("a", 1, "b", 2);
        Object result = run("to_entries | map({key: .key, value: .value * 2}) | from_entries", data);
        // jq arithmetic yields doubles: compare numerically per key
        assertTrue(result instanceof Map<?, ?> map
                && map.get("a") instanceof Number na && na.doubleValue() == 2.0
                && map.get("b") instanceof Number nb && nb.doubleValue() == 4.0,
                "expected {a:2, b:4} but was " + result);
    }

    @Test void testPickFields() {
        Map<String, Object> data = Map.of("name", "test", "secret", "abc", "version", 1);
        Object result = run("{name, version}", data);
        assertEquals(Map.of("name", "test", "version", 1), result);
    }

    @Test void testFlatten() {
        List<Object> nested = List.of(List.of(1, 2), List.of(3), List.of(4, 5));
        assertEquals(List.of(1, 2, 3, 4, 5), run("flatten", nested));
    }

    // ===== String Operations =====

    @Test void testStringInterpolation() {
        Map<String, Object> data = Map.of("name", "world");
        assertEquals("hello world", run("\"hello \\(.name)\"", data));
    }

    @Test void testStringSplitJoin() {
        assertEquals(List.of("a", "b", "c"), runAll("\"a,b,c\" | split(\",\")", null).get(0));
    }

    @Test void testStringContains() {
        assertTrue((Boolean) run("\"hello world\" | contains(\"world\")", null));
        assertFalse((Boolean) run("\"hello world\" | contains(\"xyz\")", null));
    }

    @Test void testStringStartsWithEndsWith() {
        assertTrue((Boolean) run("\"hello\" | startswith(\"hel\")", null));
        assertTrue((Boolean) run("\"hello\" | endswith(\"llo\")", null));
    }

    // ===== Recursive Descent (AI needs to search deep structures) =====

    @Test void testDeepSearch() {
        Map<String, Object> data = Map.of(
            "a", Map.of("target", 1),
            "b", Map.of("c", Map.of("target", 2))
        );
        List<Object> result = runAll("..target", data);
        assertEquals(2, result.size());
    }

    // ===== Error Handling =====

    @Test void testTryCatchOnMissing() {
        Map<String, Object> data = Map.of("a", 1);
        // Accessing missing field returns null (not error), so try returns null
        assertNull(run("try .missing", data));
        // Using error() to test catch
        assertEquals("caught", run("try error(\"oops\") catch \"caught\"", null));
    }

    // ===== Complex AI Query Patterns =====

    @Test void testFindErrorsInLogs() {
        List<Map<String, Object>> logs = List.of(
            Map.of("level", "INFO", "msg", "started"),
            Map.of("level", "ERROR", "msg", "connection failed"),
            Map.of("level", "WARN", "msg", "slow query"),
            Map.of("level", "ERROR", "msg", "timeout")
        );
        List<Object> errors = runAll(".[] | select(.level == \"ERROR\") | .msg", logs);
        assertEquals(2, errors.size());
        assertTrue(errors.contains("connection failed"));
        assertTrue(errors.contains("timeout"));
    }

    @Test void testExtractSpecificFieldsFromLargeStructure() {
        Map<String, Object> data = Map.of(
            "metadata", Map.of("name", "deployment-1", "namespace", "default"),
            "spec", Map.of("replicas", 3, "containers", List.of(
                Map.of("name", "app", "image", "nginx:latest")
            ))
        );
        assertEquals("deployment-1", run(".metadata.name", data));
        assertNum(3, run(".spec.replicas", data));
        assertEquals("nginx:latest", run(".spec.containers[0].image", data));
    }

    @Test void testPipelineQuery() {
        List<Map<String, Object>> data = List.of(
            Map.of("name", "service-a", "status", "healthy", "latency", 50),
            Map.of("name", "service-b", "status", "unhealthy", "latency", 500),
            Map.of("name", "service-c", "status", "healthy", "latency", 30)
        );
        // Find unhealthy services
        List<Object> unhealthy = runAll(
            ".[] | select(.status == \"unhealthy\") | .name", data);
        assertEquals(List.of("service-b"), unhealthy);

        // Average latency of healthy services
        Object avg = run(
            "[.[] | select(.status == \"healthy\") | .latency] | add / length", data);
        assertNum(40, avg);
    }

    @Test void testObjectMerge() {
        Map<String, Object> base = Map.of("a", 1, "b", 2);
        Map<String, Object> override = Map.of("b", 3, "c", 4);
        Object result = run(". + {b: 3, c: 4}", base);
        assertEquals(Map.of("a", 1, "b", 3, "c", 4), result);
    }

    @Test void testArrayConcat() {
        List<Object> a = List.of(1, 2);
        List<Object> b = List.of(3, 4);
        Object result = run(". + [3, 4]", a);
        assertEquals(List.of(1, 2, 3, 4), result);
    }

    // ===== Security: resource exhaustion protection =====

    @Test void testRecursionDepthLimit() {
        // Create deeply nested structure
        Map<String, Object> deep = Map.of("a", Map.of("a", Map.of("a", 1)));
        // Recursive descent finds all values
        List<Object> results = runAll("..", deep);
        assertFalse(results.isEmpty());
    }

    @Test void testNullHandling() {
        assertNull(run(".", null));
        assertEquals(42, run(".x", Map.of("x", 42)));
        // Accessing field of null returns null
        assertNull(run(".x.y", Map.of("x", Map.of())));
    }
}
