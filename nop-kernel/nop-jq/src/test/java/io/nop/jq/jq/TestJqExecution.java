package io.nop.jq.jq;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the new AST-based jq execution engine.
 */
class TestJqExecution {

    private void assertJq(String expr, Object input, Object... expectedOutputs) {
        IJsonQuery query = JqEngine.compile(expr);
        List<Object> results = query.apply(input);
        assertEquals(expectedOutputs.length, results.size(),
                "Expression '" + expr + "' produced " + results.size() + " outputs, expected " + expectedOutputs.length);
        for (int i = 0; i < expectedOutputs.length; i++) {
            assertEquals(expectedOutputs[i], results.get(i),
                    "Output " + i + " mismatch for '" + expr + "'");
        }
    }

    private void assertJqOne(String expr, Object input, Object expected) {
        IJsonQuery query = JqEngine.compile(expr);
        Object result = query.applyOne(input);
        assertEquals(expected, result);
    }

    // ===== Literals =====

    @Test void testNull() { assertJqOne("null", null, null); }
    @Test void testTrue() { assertJqOne("true", null, true); }
    @Test void testFalse() { assertJqOne("false", null, false); }
    @Test void testInteger() { assertJqOne("42", null, 42); }
    @Test void testNegativeInt() { assertJqOne("-1", null, -1.0); }
    @Test void testString() { assertJqOne("\"hello\"", null, "hello"); }

    // ===== Identity =====
    @Test void testIdentity() { assertJqOne(".", "hello", "hello"); }
    @Test void testIdentityMap() {
        Map<String, Object> m = Map.of("a", 1);
        assertJqOne(".", m, m);
    }

    // ===== Field access =====
    @Test void testFieldAccess() {
        Map<String, Object> m = Map.of("foo", 42);
        assertJqOne(".foo", m, 42);
    }
    @Test void testNestedField() {
        Map<String, Object> inner = Map.of("bar", 99);
        Map<String, Object> m = Map.of("foo", inner);
        assertJqOne(".foo.bar", m, 99);
    }
    @Test void testFieldAccessMissing() {
        Map<String, Object> m = Map.of("foo", 42);
        assertJqOne(".missing", m, null);
    }

    // ===== Array access =====
    @Test void testArrayIndex() {
        List<Integer> arr = List.of(10, 20, 30);
        assertJqOne(".[0]", arr, 10);
    }
    @Test void testNegativeIndex() {
        List<Integer> arr = List.of(10, 20, 30);
        assertJqOne(".[-1]", arr, 30);
    }
    @Test void testArrayIterator() {
        List<Integer> arr = List.of(1, 2, 3);
        assertJq(".[]", arr, 1, 2, 3);
    }
    @Test void testArraySlice() {
        List<Integer> arr = List.of(1, 2, 3, 4, 5);
        assertJqOne(".[1:3]", arr, List.of(2, 3));
    }

    // ===== Pipe =====
    @Test void testPipe() {
        Map<String, Object> m = Map.of("a", Map.of("b", 42));
        assertJqOne(".a | .b", m, 42);
    }

    // ===== Comma (multiple outputs) =====
    @Test void testComma() {
        Map<String, Object> m = Map.of("a", 1, "b", 2);
        assertJq(".a, .b", m, 1, 2);
    }

    // ===== Arithmetic =====
    @Test void testAdd() { assertJqOne("1 + 2", null, 3.0); }
    @Test void testSub() { assertJqOne("5 - 3", null, 2.0); }
    @Test void testMul() { assertJqOne("3 * 4", null, 12.0); }
    @Test void testDiv() { assertJqOne("10 / 3", null, 10.0 / 3.0); }
    @Test void testMod() { assertJqOne("10 % 3", null, 1.0); }
    @Test void testNegate() { assertJqOne("-5", null, -5.0); }

    // ===== Comparison =====
    @Test void testEq() { assertJqOne("1 == 1", null, true); }
    @Test void testNe() { assertJqOne("1 != 2", null, true); }
    @Test void testGt() { assertJqOne("5 > 3", null, true); }
    @Test void testLt() { assertJqOne("3 < 5", null, true); }
    @Test void testGe() { assertJqOne("5 >= 5", null, true); }
    @Test void testLe() { assertJqOne("5 <= 3", null, false); }

    // ===== Boolean operators =====
    @Test void testAnd() { assertJqOne("true and true", null, true); }
    @Test void testOr() { assertJqOne("false or true", null, true); }
    @Test void testNot() { assertJqOne("not", true, false); }

    // ===== String operations =====
    @Test void testStringConcat() { assertJqOne("\"a\" + \"b\"", null, "ab"); }
    @Test void testStringLength() {
        assertJqOne("\"hello\" | length", null, 5);
    }

    // ===== if-then-else =====
    @Test void testIfThenElse() {
        assertJqOne("if . > 10 then \"big\" else \"small\" end", 15, "big");
        assertJqOne("if . > 10 then \"big\" else \"small\" end", 5, "small");
    }

    // ===== select =====
    @Test void testSelect() {
        List<Integer> arr = List.of(1, 2, 3, 4, 5);
        assertJq(".[] | select(. > 3)", arr, 4, 5);
    }

    // ===== map =====
    @Test void testMap() {
        List<Integer> arr = List.of(1, 2, 3);
        assertJq("map(. + 10)", arr, List.of(11.0, 12.0, 13.0));
    }

    // ===== Object construction =====
    @Test void testObjectConstruct() {
        Map<String, Object> m = Map.of("x", 1, "y", 2);
        assertJqOne("{a: .x, b: .y}", m, Map.of("a", 1, "b", 2));
    }

    // ===== Array construction =====
    @Test void testArrayConstruct() {
        Map<String, Object> m = Map.of("a", 1, "b", 2);
        assertJqOne("[.a, .b]", m, List.of(1, 2));
    }

    // ===== Variable binding =====
    @Test void testVariableBinding() {
        Map<String, Object> m = Map.of("x", 10);
        assertJqOne(".x as $val | $val + 5", m, 15.0);
    }

    // ===== Recursive descent =====
    @Test void testRecursiveDescent() {
        Map<String, Object> inner = Map.of("a", 1);
        Map<String, Object> outer = Map.of("nested", inner, "b", 2);
        List<Object> results = JqEngine.compile("..").apply(outer);
        assertFalse(results.isEmpty());
    }

    // ===== reduce =====
    @Test void testReduce() {
        List<Integer> arr = List.of(1, 2, 3);
        assertJqOne("reduce .[] as $x (0; . + $x)", arr, 6.0);
    }

    // ===== Complex pipeline =====
    @Test void testComplexPipeline() {
        List<Map<String, Object>> arr = List.of(
                Map.of("name", "a", "score", 80),
                Map.of("name", "b", "score", 90),
                Map.of("name", "c", "score", 70)
        );
        assertJq("[.[] | select(.score > 75) | .name]", arr,
                List.of("a", "b"));
    }

    // ===== try-catch =====
    @Test void testTryCatch() {
        Map<String, Object> m = Map.of();
        // In jq, accessing a missing property returns null, not an error
        // So try .missing catch "error" returns null (no error to catch)
        assertJqOne("try .missing catch \"error\"", m, null);
    }

    @Test void testTryWithoutCatch() {
        Map<String, Object> m = Map.of();
        // In jq, accessing a missing property returns null, not an error
        // So try .missing returns null
        assertJqOne("try .missing", m, null);
    }

    // ===== limit =====
    @Test void testLimit() {
        List<Integer> arr = List.of(1, 2, 3, 4, 5);
        assertJq("limit(3; .[])", arr, 1, 2, 3);
    }

    // ===== empty =====
    @Test void testEmpty() {
        Map<String, Object> m = Map.of("a", 1);
        List<Object> results = JqEngine.compile(".a, empty, .a").apply(m);
        assertEquals(2, results.size()); // empty produces nothing
    }

    // ===== deep scan =====
    @Test void testDeepScan() {
        Map<String, Object> nested = Map.of("a", Map.of("b", Map.of("target", 42)));
        List<Object> results = JqEngine.compile("..target").apply(nested);
        assertEquals(1, results.size());
        assertEquals(42, results.get(0));
    }

    // ===== String interpolation =====
    @Test void testStringInterpolation() {
        Map<String, Object> m = Map.of("name", "world");
        assertJqOne("\"hello \\(.name)\"", m, "hello world");
    }

    // ===== Object iteration =====
    @Test void testObjectIteration() {
        Map<String, Object> m = Map.of("a", 1, "b", 2, "c", 3);
        List<Object> results = JqEngine.compile(".[]").apply(m);
        assertEquals(3, results.size());
        assertTrue(results.contains(1));
        assertTrue(results.contains(2));
        assertTrue(results.contains(3));
    }

    // ===== Chained operations =====
    @Test void testChainedFilterSort() {
        List<Map<String, Object>> arr = List.of(
                Map.of("name", "c", "score", 70),
                Map.of("name", "a", "score", 90),
                Map.of("name", "b", "score", 80)
        );
        List<Object> results = JqEngine.compile(".[] | .name").apply(arr);
        assertEquals(3, results.size());
    }

    // ===== Compilation caching =====
    @Test void testCompilationCaching() {
        IJsonQuery q1 = JqEngine.compile(".foo");
        IJsonQuery q2 = JqEngine.compile(".foo");
        assertSame(q1, q2);
    }
}
