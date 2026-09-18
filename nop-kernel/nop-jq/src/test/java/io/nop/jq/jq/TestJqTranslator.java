package io.nop.jq.jq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestJqTranslator {

    private void assertTranslation(String jq, String expectedXLang) {
        IJsonQuery query = JqEngine.compile(jq);
        assertEquals(expectedXLang, query.getXLangExpression());
    }

    // ========== Property access ==========
    @Test
    void testDotProperty() {
        assertTranslation(".foo", ".foo");
    }

    @Test
    void testNestedProperty() {
        assertTranslation(".foo.bar", ".foo.bar");
    }

    @Test
    void testDotDotProperty() {
        assertTranslation("..foo", "..foo");
    }

    @Test
    void testIdentity() {
        assertTranslation(".", ".");
    }

    // ========== Array operations ==========
    @Test
    void testArrayIndex() {
        assertTranslation(".[0]", ".[0]");
    }

    @Test
    void testArrayIterator() {
        assertTranslation(".[]", ".[]");
    }

    @Test
    void testNestedArrayAccess() {
        assertTranslation(".foo[0]", ".foo[0]");
    }

    // ========== Filter/select ==========
    @Test
    void testSelect() {
        assertTranslation("select(. > 10)", "select(. > 10)");
    }

    @Test
    void testSelectWithProperty() {
        assertTranslation("select(.price > 10)", "select(.price > 10)");
    }

    // ========== Pipe ==========
    @Test
    void testPipe() {
        assertTranslation(".foo | .bar", ".foo | .bar");
    }

    @Test
    void testPipeWithSelect() {
        assertTranslation(".[] | select(. > 5)", ".[] | select(. > 5)");
    }

    // ========== Map ==========
    @Test
    void testMap() {
        assertTranslation("map(.foo)", "map(.foo)");
    }

    @Test
    void testMapWithSelect() {
        assertTranslation("map(select(. > 10))", "map(select(. > 10))");
    }

    // ========== Arithmetic ==========
    @Test
    void testAddition() {
        assertTranslation(". + 1", ". + 1");
    }

    @Test
    void testSubtraction() {
        assertTranslation(". - 1", ". - 1");
    }

    @Test
    void testMultiplication() {
        assertTranslation(". * 2", ". * 2");
    }

    @Test
    void testDivision() {
        assertTranslation(". / 2", ". / 2");
    }

    // ========== Comparison ==========
    @Test
    void testEquals() {
        assertTranslation(". == 10", ". == 10");
    }

    @Test
    void testNotEquals() {
        assertTranslation(". != null", ". != null");
    }

    @Test
    void testGreaterThan() {
        assertTranslation(". > 5", ". > 5");
    }

    @Test
    void testLessThan() {
        assertTranslation(". < 5", ". < 5");
    }

    // ========== Logical ==========
    @Test
    void testAnd() {
        assertTranslation(". > 5 and . < 10", ". > 5 and . < 10");
    }

    @Test
    void testOr() {
        assertTranslation(". == 1 or . == 2", ". == 1 or . == 2");
    }

    @Test
    void testNot() {
        assertTranslation("not (. > 5)", "not (. > 5)");
    }

    // ========== String literals ==========
    @Test
    void testStringLiteral() {
        assertTranslation("'hello'", "'hello'");
    }

    // ========== Number literals ==========
    @Test
    void testIntegerLiteral() {
        assertTranslation("42", "42");
    }

    @Test
    void testFloatLiteral() {
        assertTranslation("3.14", "3.14");
    }

    // ========== Boolean and null ==========
    @Test
    void testTrue() {
        assertTranslation("true", "true");
    }

    @Test
    void testFalse() {
        assertTranslation("false", "false");
    }

    @Test
    void testNull() {
        assertTranslation("null", "null");
    }

    // ========== If-then-else ==========
    @Test
    void testIfThenElse() {
        assertTranslation("if . > 10 then 'big' else 'small' end",
                "if . > 10 then 'big' else 'small' end");
    }

    @Test
    void testIfElifElse() {
        assertTranslation("if . > 10 then 'big' elif . > 5 then 'medium' else 'small' end",
                "if . > 10 then 'big' elif . > 5 then 'medium' else 'small' end");
    }

    // ========== Array/Object construction ==========
    @Test
    void testArrayConstruct() {
        assertTranslation("[.foo, .bar]", "[.foo, .bar]");
    }

    @Test
    void testObjectConstruct() {
        assertTranslation("{a: .x, b: .y}", "{a: .x, b: .y}");
    }

    // ========== Reduce ==========
    @Test
    void testReduce() {
        assertTranslation("reduce .[] as $x (0; . + $x)",
                "reduce .[] as x (0; . + x)");
    }

    // ========== Built-in functions ==========
    @Test
    void testLength() {
        assertTranslation("length", "length");
    }

    @Test
    void testKeys() {
        assertTranslation("keys", "keys");
    }

    @Test
    void testValues() {
        assertTranslation("values", "values");
    }

    @Test
    void testType() {
        assertTranslation("type", "type");
    }

    // ========== Complex expressions ==========
    @Test
    void testComplexPipe() {
        assertTranslation(".store.book[] | select(.price > 10) | .title",
                ".store.book[] | select(.price > 10) | .title");
    }

    @Test
    void testDeepScan() {
        assertTranslation("..price", "..price");
    }

    @Test
    void testVariable() {
        assertTranslation("$var", "var");
    }

    // ========== Compile ==========
    @Test
    void testCompileReturnsQuery() {
        IJsonQuery query = JqEngine.compile(".foo");
        assertNotNull(query);
        assertEquals(".foo", query.getExpression());
        assertEquals(".foo", query.getXLangExpression());
    }

    @Test
    void testCompileCaches() {
        IJsonQuery q1 = JqEngine.compile(".foo");
        IJsonQuery q2 = JqEngine.compile(".foo");
        assertSame(q1, q2);
    }
}
