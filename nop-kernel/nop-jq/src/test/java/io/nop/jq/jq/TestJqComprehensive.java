package io.nop.jq.jq;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive jq test suite covering Tier 1 (core) and Tier 2 (important) patterns.
 * Tests verify that jq expressions execute correctly.
 */
class TestJqComprehensive {

    private void assertJq(String jq, Object input, Object expected) {
        IJsonQuery query = JqEngine.compile(jq);
        Object result = query.applyOne(input);
        assertEquals(expected, result,
                "jq '" + jq + "' should return '" + expected + "' for input '" + input + "'");
    }

    // ========== Tier 1: Core Patterns ==========

    // Identity and property access
    @Test void testIdentity() { assertJq(".", Map.of("a", 1), Map.of("a", 1)); }
    @Test void testProperty() { assertJq(".foo", Map.of("foo", 42), 42); }
    @Test void testNestedProperty() { assertJq(".foo.bar", Map.of("foo", Map.of("bar", 99)), 99); }
    @Test void testDeepProperty() { assertJq(".foo.bar.baz", Map.of("foo", Map.of("bar", Map.of("baz", 7))), 7); }

    // Array operations
    @Test void testArrayIndex() { assertJq(".[0]", Arrays.asList(10, 20, 30), 10); }
    @Test void testNegativeIndex() { assertJq(".[-1]", Arrays.asList(10, 20, 30), 30); }

    // Arithmetic
    @Test void testAdd() { assertJq(". + 1", 5, 6.0); }
    @Test void testSub() { assertJq(". - 1", 5, 4.0); }
    @Test void testMul() { assertJq(". * 2", 5, 10.0); }
    @Test void testDiv() { assertJq(". / 2", 10, 5.0); }
    @Test void testMod() { assertJq(". % 3", 10, 1.0); }

    // Comparison
    @Test void testEq() { assertJq(". == 5", 5, true); }
    @Test void testNe() { assertJq(". != 5", 5, false); }
    @Test void testGt() { assertJq(". > 5", 10, true); }
    @Test void testLt() { assertJq(". < 5", 10, false); }
    @Test void testGe() { assertJq(". >= 5", 5, true); }
    @Test void testLe() { assertJq(". <= 5", 5, true); }

    // Logical
    @Test void testAnd() { assertJq(". > 5 and . < 10", 7, true); }
    @Test void testOr() { assertJq(". == 5 or . == 10", 10, true); }
    @Test void testNot() { assertJq("not", true, false); }

    // Literals
    @Test void testNull() { assertJq("null", null, null); }
    @Test void testTrue() { assertJq("true", null, true); }
    @Test void testFalse() { assertJq("false", null, false); }
    @Test void testInteger() { assertJq("42", null, 42); }
    @Test void testFloat() { assertJq("3.14", null, 3.14); }
    @Test void testString() { assertJq("'hello'", null, "hello"); }

    // String operations
    @Test void testStringConcat() { assertJq("\"hello\" + \" world\"", null, "hello world"); }
    @Test void testStringLength() { assertJq("\"hello\" | length", null, 5); }

    // Object construction
    @Test void testObjectConstruct() {
        assertJq("{a: .x, b: .y}", Map.of("x", 1, "y", 2), Map.of("a", 1, "b", 2));
    }

    // Array construction
    @Test void testArrayConstruct() {
        assertJq("[.foo, .bar]", Map.of("foo", 1, "bar", 2), Arrays.asList(1, 2));
    }

    // Variable binding
    @Test void testVariableBinding() {
        assertJq(".x as $val | $val + 5", Map.of("x", 10), 15.0);
    }

    // If-then-else
    @Test void testIfThenElse() {
        assertJq("if . > 10 then \"big\" else \"small\" end", 15, "big");
    }

    // Reduce
    @Test void testReduce() {
        assertJq("reduce .[] as $x (0; . + $x)", Arrays.asList(1, 2, 3), 6.0);
    }

    // Built-in functions
    @Test void testLength() { assertJq("length", "hello", 5); }
    @Test void testKeys() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("a", 1);
        m.put("b", 2);
        assertJq("keys", m, Arrays.asList("a", "b"));
    }
    @Test void testValues() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("a", 1);
        m.put("b", 2);
        assertJq("values", m, Arrays.asList(1, 2));
    }
    @Test void testType() { assertJq("type", "hello", "string"); }
    @Test void testEmpty() { assertJq("empty", null, null); }
    @Test void testTostring() { assertJq("tostring", 42, "42"); }
    @Test void testTonumber() { assertJq("tonumber", "42", 42.0); }
}
