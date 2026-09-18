package io.nop.jq.jsonpath;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JsonPath test suite covering common patterns from fastjson JSONPath.
 */
class TestJsonPathComprehensive {

    // ========== Helper: Build sample data ==========
    private Map<String, Object> buildStore() {
        Map<String, Object> book1 = new LinkedHashMap<>();
        book1.put("title", "Sayings of the Century");
        book1.put("author", "Nigel Rees");
        book1.put("price", 8.95);
        book1.put("isbn", "0-553-21311-3");
        book1.put("category", "reference");

        Map<String, Object> book2 = new LinkedHashMap<>();
        book2.put("title", "Sword of Honour");
        book2.put("author", "Evelyn Waugh");
        book2.put("price", 12.99);
        book2.put("isbn", "0-553-21312-1");
        book2.put("category", "fiction");

        Map<String, Object> book3 = new LinkedHashMap<>();
        book3.put("title", "Moby Dick");
        book3.put("author", "Herman Melville");
        book3.put("price", 8.99);
        book3.put("isbn", "0-553-21313-X");
        book3.put("category", "fiction");

        Map<String, Object> book4 = new LinkedHashMap<>();
        book4.put("title", "The Lord of the Rings");
        book4.put("author", "J. R. R. Tolkien");
        book4.put("price", 22.99);
        book4.put("isbn", "0-395-19395-8");
        book4.put("category", "fiction");

        Map<String, Object> bicycle = new LinkedHashMap<>();
        bicycle.put("color", "red");
        bicycle.put("price", 19.95);

        Map<String, Object> store = new LinkedHashMap<>();
        store.put("book", Arrays.asList(book1, book2, book3, book4));
        store.put("bicycle", bicycle);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("store", store);
        root.put("expensive", 10);
        return root;
    }

    // ========== Root access ==========
    @Test
    void testRootDollar() {
        Map<String, Object> data = buildStore();
        assertSame(data, NopJsonPath.eval(data, "$"));
    }

    // ========== Dot notation ==========
    @Test
    void testDotNotation() {
        Map<String, Object> data = buildStore();
        assertEquals(10, NopJsonPath.eval(data, "$.expensive"));
    }

    @Test
    void testNestedDotNotation() {
        Map<String, Object> data = buildStore();
        Object result = NopJsonPath.eval(data, "$.store.bicycle.color");
        assertEquals("red", result);
    }

    // ========== Bracket notation ==========
    @Test
    void testBracketNotationStringKey() {
        Map<String, Object> data = buildStore();
        Object result = NopJsonPath.eval(data, "$['store']");
        assertNotNull(result);
    }

    @Test
    void testBracketNotationWithSpaces() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key with spaces", "value");
        assertEquals("value", NopJsonPath.eval(data, "$['key with spaces']"));
    }

    // ========== Wildcard ==========
    @Test
    void testWildcardArray() {
        Map<String, Object> data = buildStore();
        Object result = NopJsonPath.eval(data, "$.store.book[*].author");
        assertTrue(result instanceof List);
        List<?> authors = (List<?>) result;
        assertEquals(4, authors.size());
        assertTrue(authors.contains("Nigel Rees"));
        assertTrue(authors.contains("Herman Melville"));
    }

    @Test
    void testWildcardObject() {
        Map<String, Object> data = buildStore();
        Object result = NopJsonPath.eval(data, "$.store.bicycle.*");
        assertNotNull(result);
    }

    // ========== Array index ==========
    @Test
    void testArrayIndex() {
        Map<String, Object> data = buildStore();
        assertEquals("Sayings of the Century", NopJsonPath.eval(data, "$.store.book[0].title"));
    }

    @Test
    void testNegativeArrayIndex() {
        Map<String, Object> data = buildStore();
        assertEquals("The Lord of the Rings", NopJsonPath.eval(data, "$.store.book[-1].title"));
    }

    @Test
    void testArrayIndexOutOfBounds() {
        Map<String, Object> data = buildStore();
        assertNull(NopJsonPath.eval(data, "$.store.book[100].title"));
    }

    // ========== Array slice ==========
    @Test
    void testArraySliceStartEnd() {
        Map<String, Object> data = buildStore();
        Object result = NopJsonPath.eval(data, "$.store.book[0:2].title");
        assertTrue(result instanceof List);
        List<?> titles = (List<?>) result;
        assertEquals(2, titles.size());
    }

    @Test
    void testArraySliceOpenEnd() {
        Map<String, Object> data = buildStore();
        Object result = NopJsonPath.eval(data, "$.store.book[2:].title");
        assertTrue(result instanceof List);
        List<?> titles = (List<?>) result;
        assertEquals(2, titles.size());
    }

    // ========== Filter expressions ==========
    @Test
    void testFilterEquals() {
        Map<String, Object> data = buildStore();
        Object result = NopJsonPath.eval(data, "$.store.book[?(@.category == 'fiction')].title");
        assertTrue(result instanceof List);
        List<?> titles = (List<?>) result;
        assertEquals(3, titles.size());
    }

    @Test
    void testFilterNotEquals() {
        Map<String, Object> data = buildStore();
        Object result = NopJsonPath.eval(data, "$.store.book[?(@.category != 'fiction')].title");
        assertTrue(result instanceof List);
        List<?> titles = (List<?>) result;
        assertEquals(1, titles.size());
        assertEquals("Sayings of the Century", titles.get(0));
    }

    @Test
    void testFilterGreaterThan() {
        Map<String, Object> data = buildStore();
        Object result = NopJsonPath.eval(data, "$.store.book[?(@.price > 10)].title");
        assertTrue(result instanceof List);
        List<?> titles = (List<?>) result;
        assertEquals(2, titles.size());
    }

    @Test
    void testFilterLessThan() {
        Map<String, Object> data = buildStore();
        Object result = NopJsonPath.eval(data, "$.store.book[?(@.price < 10)].title");
        assertTrue(result instanceof List);
        List<?> titles = (List<?>) result;
        assertEquals(2, titles.size());
    }

    @Test
    void testFilterGreaterThanOrEqual() {
        Map<String, Object> data = buildStore();
        Object result = NopJsonPath.eval(data, "$.store.book[?(@.price >= 12.99)].title");
        assertTrue(result instanceof List);
        List<?> titles = (List<?>) result;
        assertEquals(2, titles.size());
    }

    @Test
    void testFilterLessThanOrEqual() {
        Map<String, Object> data = buildStore();
        Object result = NopJsonPath.eval(data, "$.store.book[?(@.price <= 8.99)].title");
        assertTrue(result instanceof List);
        List<?> titles = (List<?>) result;
        assertEquals(2, titles.size());
    }

    @Test
    void testFilterAnd() {
        Map<String, Object> data = buildStore();
        Object result = NopJsonPath.eval(data,
                "$.store.book[?(@.price > 10 && @.category == 'fiction')].title");
        assertTrue(result instanceof List);
        List<?> titles = (List<?>) result;
        assertEquals(2, titles.size());
    }

    @Test
    void testFilterOr() {
        Map<String, Object> data = buildStore();
        Object result = NopJsonPath.eval(data,
                "$.store.book[?(@.category == 'reference' || @.price > 20)].title");
        assertTrue(result instanceof List);
        List<?> titles = (List<?>) result;
        assertEquals(2, titles.size());
    }

    @Test
    void testFilterRegex() {
        Map<String, Object> data = buildStore();
        Object result = NopJsonPath.eval(data, "$.store.book[?(@.author =~ '.*Melville')].title");
        assertTrue(result instanceof List);
        List<?> titles = (List<?>) result;
        assertEquals(1, titles.size());
        assertEquals("Moby Dick", titles.get(0));
    }

    @Test
    void testFilterRegexCaseInsensitive() {
        Map<String, Object> data = buildStore();
        Object result = NopJsonPath.eval(data, "$.store.book[?(@.author =~ '(?i)melville')].title");
        assertTrue(result instanceof List);
        List<?> titles = (List<?>) result;
        assertEquals(1, titles.size());
    }

    // ========== Deep scan ==========
    @Test
    void testDeepScan() {
        Map<String, Object> data = buildStore();
        Object result = NopJsonPath.eval(data, "$..author");
        assertTrue(result instanceof List);
        List<?> authors = (List<?>) result;
        assertEquals(4, authors.size());
    }

    @Test
    void testDeepScanNested() {
        Map<String, Object> data = buildStore();
        Object result = NopJsonPath.eval(data, "$..price");
        assertTrue(result instanceof List);
        List<?> prices = (List<?>) result;
        assertTrue(prices.size() >= 5); // 4 books + 1 bicycle
    }

    // ========== EvalOne ==========
    @Test
    void testEvalOne() {
        Map<String, Object> data = buildStore();
        assertEquals("Sayings of the Century",
                NopJsonPath.evalOne(data, "$.store.book[*].title"));
    }

    @Test
    void testEvalOneEmpty() {
        Map<String, Object> data = buildStore();
        assertNull(NopJsonPath.evalOne(data, "$.store.book[?(@.price > 100)].title"));
    }

    // ========== Set ==========
    @Test
    void testSetProperty() {
        Map<String, Object> data = buildStore();
        NopJsonPath.set(data, "$.expensive", 20);
        assertEquals(20, NopJsonPath.eval(data, "$.expensive"));
    }

    @Test
    void testSetNestedProperty() {
        Map<String, Object> data = buildStore();
        NopJsonPath.set(data, "$.store.bicycle.color", "blue");
        assertEquals("blue", NopJsonPath.eval(data, "$.store.bicycle.color"));
    }

    // ========== Remove ==========
    @Test
    void testRemove() {
        Map<String, Object> data = buildStore();
        assertTrue(NopJsonPath.remove(data, "$.expensive"));
        assertNull(NopJsonPath.eval(data, "$.expensive"));
    }

    // ========== Size ==========
    @Test
    void testSizeArray() {
        Map<String, Object> data = buildStore();
        assertEquals(4, NopJsonPath.size(data, "$.store.book"));
    }

    @Test
    void testSizeObject() {
        Map<String, Object> data = buildStore();
        assertEquals(2, NopJsonPath.size(data, "$.store"));
    }

    // ========== Contains ==========
    @Test
    void testContainsTrue() {
        Map<String, Object> data = buildStore();
        assertTrue(NopJsonPath.contains(data, "$.store.bicycle"));
    }

    @Test
    void testContainsFalse() {
        Map<String, Object> data = buildStore();
        assertFalse(NopJsonPath.contains(data, "$.store.missing"));
    }

    // ========== ContainsValue ==========
    @Test
    void testContainsValue() {
        Map<String, Object> data = buildStore();
        assertTrue(NopJsonPath.containsValue(data, "$.store.bicycle.color", "red"));
        assertFalse(NopJsonPath.containsValue(data, "$.store.bicycle.color", "blue"));
    }

    // ========== KeySet ==========
    @Test
    void testKeySet() {
        Map<String, Object> data = buildStore();
        Set<String> keys = NopJsonPath.keySet(data, "$.store");
        assertTrue(keys.contains("book"));
        assertTrue(keys.contains("bicycle"));
        assertEquals(2, keys.size());
    }

    // ========== Null handling ==========
    @Test
    void testNullRoot() {
        assertNull(NopJsonPath.eval(null, "$.foo"));
    }

    @Test
    void testNullProperty() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("foo", null);
        assertNull(NopJsonPath.eval(data, "$.foo.bar"));
    }

    @Test
    void testMissingPath() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("foo", "bar");
        assertNull(NopJsonPath.eval(data, "$.missing"));
    }

    // ========== Complex nested paths ==========
    @Test
    void testComplexPath() {
        Map<String, Object> data = buildStore();
        Object result = NopJsonPath.eval(data, "$.store.book[0].title");
        assertEquals("Sayings of the Century", result);
    }

    // ========== Read from JSON string ==========
    @Test
    void testRead() {
        String json = "{\"store\":{\"book\":[{\"title\":\"Test Book\",\"price\":9.99}]}}";
        Object result = NopJsonPath.read(json, "$.store.book[0].title");
        assertEquals("Test Book", result);
    }
}
