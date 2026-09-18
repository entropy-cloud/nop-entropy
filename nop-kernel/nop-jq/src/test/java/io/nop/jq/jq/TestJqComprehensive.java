package io.nop.jq.jq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive jq translation test suite covering Tier 1 (core) and Tier 2 (important) patterns.
 * Tests verify that jq expressions are correctly translated to XLang expressions.
 */
class TestJqComprehensive {

    private void assertTranslation(String jq, String expectedXLang) {
        IJsonQuery query = JqEngine.compile(jq);
        assertEquals(expectedXLang, query.getXLangExpression(),
                "jq '" + jq + "' should translate to '" + expectedXLang + "'");
    }

    // ========== Tier 1: Core Patterns ==========

    // Identity and property access
    @Test void testIdentity() { assertTranslation(".", "."); }
    @Test void testProperty() { assertTranslation(".foo", ".foo"); }
    @Test void testNestedProperty() { assertTranslation(".foo.bar", ".foo.bar"); }
    @Test void testDeepProperty() { assertTranslation(".foo.bar.baz", ".foo.bar.baz"); }

    // Array operations
    @Test void testArrayIndex() { assertTranslation(".[0]", ".[0]"); }
    @Test void testNegativeIndex() { assertTranslation(".[-1]", ".[-1]"); }
    @Test void testArrayIterator() { assertTranslation(".[]", ".[]"); }
    @Test void testArraySlice() { assertTranslation(".[2:4]", ".[2:4]"); }
    @Test void testArraySliceOpen() { assertTranslation(".[2:]", ".[2:]"); }

    // Pipe
    @Test void testPipeSimple() { assertTranslation(".foo | .bar", ".foo | .bar"); }
    @Test void testPipeChain() { assertTranslation(".a | .b | .c", ".a | .b | .c"); }
    @Test void testPipeWithArray() { assertTranslation(".[] | .foo", ".[] | .foo"); }

    // Select
    @Test void testSelectSimple() { assertTranslation("select(true)", "select(true)"); }
    @Test void testSelectWithComparison() { assertTranslation("select(. > 5)", "select(. > 5)"); }
    @Test void testSelectWithProperty() { assertTranslation("select(.foo == 1)", "select(.foo == 1)"); }
    @Test void testSelectInPipe() { assertTranslation(".[] | select(. > 5)", ".[] | select(. > 5)"); }

    // Map
    @Test void testMapSimple() { assertTranslation("map(. + 1)", "map(. + 1)"); }
    @Test void testMapProperty() { assertTranslation("map(.foo)", "map(.foo)"); }

    // Arithmetic
    @Test void testAdd() { assertTranslation(". + 1", ". + 1"); }
    @Test void testSub() { assertTranslation(". - 1", ". - 1"); }
    @Test void testMul() { assertTranslation(". * 2", ". * 2"); }
    @Test void testDiv() { assertTranslation(". / 2", ". / 2"); }
    @Test void testMod() { assertTranslation(". % 3", ". % 3"); }
    @Test void testNegate() { assertTranslation("-.", "-."); }
    @Test void testComplexArithmetic() { assertTranslation(". + 1 * 2", ". + 1 * 2"); }

    // Comparison
    @Test void testEq() { assertTranslation(". == 10", ". == 10"); }
    @Test void testNe() { assertTranslation(". != 10", ". != 10"); }
    @Test void testGt() { assertTranslation(". > 10", ". > 10"); }
    @Test void testGe() { assertTranslation(". >= 10", ". >= 10"); }
    @Test void testLt() { assertTranslation(". < 10", ". < 10"); }
    @Test void testLe() { assertTranslation(". <= 10", ". <= 10"); }

    // Logical
    @Test void testAnd() { assertTranslation(". > 5 and . < 10", ". > 5 and . < 10"); }
    @Test void testOr() { assertTranslation(". == 1 or . == 2", ". == 1 or . == 2"); }
    @Test void testNot() { assertTranslation("not (. > 5)", "not (. > 5)"); }

    // Literals
    @Test void testNull() { assertTranslation("null", "null"); }
    @Test void testTrue() { assertTranslation("true", "true"); }
    @Test void testFalse() { assertTranslation("false", "false"); }
    @Test void testInteger() { assertTranslation("42", "42"); }
    @Test void testFloat() { assertTranslation("3.14", "3.14"); }
    @Test void testString() { assertTranslation("'hello'", "'hello'"); }

    // If-then-else
    @Test void testIfThenElse() {
        assertTranslation("if . > 10 then 'big' else 'small' end",
                "if . > 10 then 'big' else 'small' end");
    }
    @Test void testIfElifElse() {
        assertTranslation("if . > 10 then 'big' elif . > 5 then 'medium' else 'small' end",
                "if . > 10 then 'big' elif . > 5 then 'medium' else 'small' end");
    }

    // Reduce
    @Test void testReduce() {
        assertTranslation("reduce .[] as $x (0; . + $x)",
                "reduce .[] as x (0; . + x)");
    }

    // Array/Object construction
    @Test void testArrayConstruct() { assertTranslation("[.foo, .bar]", "[.foo, .bar]"); }
    @Test void testObjectConstruct() { assertTranslation("{a: .x, b: .y}", "{a: .x, b: .y}"); }
    @Test void testObjectShorthand() { assertTranslation("{foo, bar}", "{foo, bar}"); }

    // Built-in functions
    @Test void testLength() { assertTranslation("length", "length"); }
    @Test void testKeys() { assertTranslation("keys", "keys"); }
    @Test void testValues() { assertTranslation("values", "values"); }
    @Test void testType() { assertTranslation("type", "type"); }

    // Deep scan
    @Test void testDeepScan() { assertTranslation("..foo", "..foo"); }
    @Test void testDeepScanRoot() { assertTranslation("..", ".."); }

    // Variable references
    @Test void testVariable() { assertTranslation("$var", "var"); }

    // Parentheses
    @Test void testParentheses() { assertTranslation("(.foo)", "(.foo)"); }

    // ========== Tier 2: Important Patterns ==========

    // Complex pipe chains
    @Test void testComplexPipeChain() {
        assertTranslation(".store.book[] | select(.price > 10) | .title",
                ".store.book[] | select(.price > 10) | .title");
    }

    // Nested select
    @Test void testNestedSelect() {
        assertTranslation(".[] | select(.kind == 'book') | select(.price < 20)",
                ".[] | select(.kind == 'book') | select(.price < 20)");
    }

    // Map with select
    @Test void testMapSelect() {
        assertTranslation("map(select(. > 5))", "map(select(. > 5))");
    }

    // Reduce with complex expression
    @Test void testReduceComplex() {
        assertTranslation("reduce .[] as $x (0; . + $x.price)",
                "reduce .[] as x (0; . + x.price)");
    }

    // If-then-else with pipe
    @Test void testIfWithPipe() {
        assertTranslation(".[] | if . > 10 then 'big' else 'small' end",
                ".[] | if . > 10 then 'big' else 'small' end");
    }

    // Array construction with pipe
    @Test void testArrayWithPipe() {
        assertTranslation("[.foo, .bar | .baz]", "[.foo, .bar | .baz]");
    }

    // Object construction with expression values
    @Test void testObjectWithExpressions() {
        assertTranslation("{name: .name, price: .price * 2}",
                "{name: .name, price: .price * 2}");
    }

    // Complex comparison chains
    @Test void testComparisonChain() {
        assertTranslation(". > 5 and . < 10 and . != 7",
                ". > 5 and . < 10 and . != 7");
    }

    // Nested arithmetic (parenthesized)
    @Test void testNestedArithmetic() {
        assertTranslation("( . + 1 ) * 2", "(. + 1) * 2");
    }

    // Deep scan with filter
    @Test void testDeepScanFilter() {
        assertTranslation(".. | select(. > 10)", ".. | select(. > 10)");
    }

    // Multiple array accesses
    @Test void testMultipleArrayAccess() {
        assertTranslation(".foo[0][1]", ".foo[0][1]");
    }

    // Property after array
    @Test void testPropertyAfterArray() {
        assertTranslation(".foo[0].bar", ".foo[0].bar");
    }

    // Wildcard after property
    @Test void testWildcardAfterProperty() {
        assertTranslation(".store.book[]", ".store.book[]");
    }

    // Complex reduce with filter
    @Test void testReduceWithFilter() {
        assertTranslation("reduce (.[] | select(. > 5)) as $x (0; . + $x)",
                "reduce (.[] | select(. > 5)) as x (0; . + x)");
    }

    // Nested if-then-else
    @Test void testNestedIf() {
        assertTranslation(
                "if . > 10 then if . > 20 then 'huge' else 'big' end else 'small' end",
                "if . > 10 then if . > 20 then 'huge' else 'big' end else 'small' end");
    }

    // Object with nested expressions
    @Test void testObjectNested() {
        assertTranslation("{a: {b: .x}}", "{a: {b: .x}}");
    }

    // Array with nested expressions
    @Test void testArrayNested() {
        assertTranslation("[[.foo], [.bar]]", "[[.foo], [.bar]]");
    }

    // Complex pipe with multiple operations
    @Test void testComplexMultiPipe() {
        assertTranslation(
                ".users[] | select(.age > 18) | {name: .name, age: .age}",
                ".users[] | select(.age > 18) | {name: .name, age: .age}");
    }

    // String concatenation (using +)
    @Test void testStringConcat() {
        assertTranslation("\"hello\" + \" world\"", "'hello' + ' world'");
    }

    // Negation in comparison
    @Test void testNegationInComparison() {
        assertTranslation("select(-. > -10)", "select(-. > -10)");
    }

    // Multiple conditions in select
    @Test void testMultipleConditions() {
        assertTranslation(
                "select(.age > 18 and .status == 'active')",
                "select(.age > 18 and .status == 'active')");
    }

    // Array slice with step (not yet supported)
    @Test void testArraySliceStep() {
        assertTranslation(".[0:10]", ".[0:10]");
    }

    // Object with string keys
    @Test void testObjectStringKeys() {
        assertTranslation("{\"key\": .value}", "{'key': .value}");
    }

    // Complex expression with all operators
    @Test void testComplexExpression() {
        assertTranslation(
                ".items[] | select(.price > 10 and .quantity > 0) | .price * .quantity | . + 1",
                ".items[] | select(.price > 10 and .quantity > 0) | .price * .quantity | . + 1");
    }
}
