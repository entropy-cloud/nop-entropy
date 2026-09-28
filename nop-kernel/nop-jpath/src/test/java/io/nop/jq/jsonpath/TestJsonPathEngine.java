package io.nop.jq.jsonpath;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestJsonPathEngine {

    private Map<String, Object> sampleData() {
        Map<String, Object> book1 = new LinkedHashMap<>();
        book1.put("title", "Sayings of the Century");
        book1.put("author", "Nigel Rees");
        book1.put("price", 8.95);
        book1.put("category", "reference");

        Map<String, Object> book2 = new LinkedHashMap<>();
        book2.put("title", "Sword of Honour");
        book2.put("author", "Evelyn Waugh");
        book2.put("price", 12.99);
        book2.put("category", "fiction");

        Map<String, Object> book3 = new LinkedHashMap<>();
        book3.put("title", "Moby Dick");
        book3.put("author", "Herman Melville");
        book3.put("price", 8.99);
        book3.put("category", "fiction");

        Map<String, Object> store = new LinkedHashMap<>();
        store.put("book", Arrays.asList(book1, book2, book3));
        store.put("bicycle", "cheap");

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("store", store);
        root.put("expensive", 10);
        return root;
    }

    @Test
    void testRootDollar() {
        Map<String, Object> data = sampleData();
        Object result = NopJsonPath.eval(data, "$");
        assertSame(data, result);
    }

    @Test
    void testDotProperty() {
        Map<String, Object> data = sampleData();
        Object result = NopJsonPath.eval(data, "$.expensive");
        assertEquals(10, result);
    }

    @Test
    void testNestedProperty() {
        Map<String, Object> data = sampleData();
        Object result = NopJsonPath.eval(data, "$.store.bicycle");
        assertEquals("cheap", result);
    }

    @Test
    void testBracketProperty() {
        Map<String, Object> data = sampleData();
        Object result = NopJsonPath.eval(data, "$.store['book']");
        assertTrue(result instanceof List);
        assertEquals(3, ((List<?>) result).size());
    }

    @Test
    void testWildcardArray() {
        Map<String, Object> data = sampleData();
        Object result = NopJsonPath.eval(data, "$.store.book[*].title");
        assertTrue(result instanceof List);
        List<?> titles = (List<?>) result;
        assertEquals(3, titles.size());
        assertEquals("Sayings of the Century", titles.get(0));
        assertEquals("Sword of Honour", titles.get(1));
        assertEquals("Moby Dick", titles.get(2));
    }

    @Test
    void testArrayIndex() {
        Map<String, Object> data = sampleData();
        Object result = NopJsonPath.eval(data, "$.store.book[0].title");
        assertEquals("Sayings of the Century", result);
    }

    @Test
    void testNegativeArrayIndex() {
        Map<String, Object> data = sampleData();
        Object result = NopJsonPath.eval(data, "$.store.book[-1].title");
        assertEquals("Moby Dick", result);
    }

    @Test
    void testArraySlice() {
        Map<String, Object> data = sampleData();
        Object result = NopJsonPath.eval(data, "$.store.book[0:2].title");
        assertTrue(result instanceof List);
        List<?> titles = (List<?>) result;
        assertEquals(2, titles.size());
        assertEquals("Sayings of the Century", titles.get(0));
        assertEquals("Sword of Honour", titles.get(1));
    }

    @Test
    void testFilterEquals() {
        Map<String, Object> data = sampleData();
        Object result = NopJsonPath.eval(data, "$.store.book[?(@.price == 8.95)].title");
        assertTrue(result instanceof List);
        List<?> titles = (List<?>) result;
        assertEquals(1, titles.size());
        assertEquals("Sayings of the Century", titles.get(0));
    }

    @Test
    void testFilterGreaterThan() {
        Map<String, Object> data = sampleData();
        Object result = NopJsonPath.eval(data, "$.store.book[?(@.price > 10)].title");
        assertTrue(result instanceof List);
        List<?> titles = (List<?>) result;
        assertEquals(1, titles.size());
        assertEquals("Sword of Honour", titles.get(0));
    }

    @Test
    void testFilterLessThanEquals() {
        Map<String, Object> data = sampleData();
        Object result = NopJsonPath.eval(data, "$.store.book[?(@.price <= 8.99)].title");
        assertTrue(result instanceof List);
        List<?> titles = (List<?>) result;
        assertEquals(2, titles.size());
    }

    @Test
    void testFilterNotEquals() {
        Map<String, Object> data = sampleData();
        Object result = NopJsonPath.eval(data, "$.store.book[?(@.category != 'fiction')].title");
        assertTrue(result instanceof List);
        List<?> titles = (List<?>) result;
        assertEquals(1, titles.size());
        assertEquals("Sayings of the Century", titles.get(0));
    }

    @Test
    void testFilterAnd() {
        Map<String, Object> data = sampleData();
        Object result = NopJsonPath.eval(data,
                "$.store.book[?(@.price > 9 && @.price < 13)].title");
        assertTrue(result instanceof List);
        List<?> titles = (List<?>) result;
        assertEquals(1, titles.size());
        assertEquals("Sword of Honour", titles.get(0));
    }

    @Test
    void testFilterOr() {
        Map<String, Object> data = sampleData();
        Object result = NopJsonPath.eval(data,
                "$.store.book[?(@.category == 'reference' || @.price > 12)].title");
        assertTrue(result instanceof List);
        List<?> titles = (List<?>) result;
        assertEquals(2, titles.size());
    }

    @Test
    void testFilterRegex() {
        Map<String, Object> data = sampleData();
        Object result = NopJsonPath.eval(data, "$.store.book[?(@.author =~ '.*Melville')].title");
        assertTrue(result instanceof List);
        List<?> titles = (List<?>) result;
        assertEquals(1, titles.size());
        assertEquals("Moby Dick", titles.get(0));
    }

    @Test
    void testEvalOne() {
        Map<String, Object> data = sampleData();
        Object result = NopJsonPath.evalOne(data, "$.store.book[*].title");
        assertEquals("Sayings of the Century", result);
    }

    @Test
    void testEvalOneEmptyList() {
        Map<String, Object> data = sampleData();
        Object result = NopJsonPath.evalOne(data, "$.store.book[?(@.price > 100)].title");
        assertNull(result);
    }

    @Test
    void testContains() {
        Map<String, Object> data = sampleData();
        assertTrue(NopJsonPath.contains(data, "$.store.bicycle"));
        assertFalse(NopJsonPath.contains(data, "$.store.missing"));
    }

    @Test
    void testSize() {
        Map<String, Object> data = sampleData();
        assertEquals(3, NopJsonPath.size(data, "$.store.book"));
    }

    @Test
    void testNullPath() {
        Map<String, Object> data = sampleData();
        assertNull(NopJsonPath.eval(data, "$.missing.path"));
    }

    @Test
    void testBracketNotationWithQuotes() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key with spaces", "value");
        Object result = NopJsonPath.eval(data, "$['key with spaces']");
        assertEquals("value", result);
    }

    @Test
    void testCompileAndReuse() {
        NopCompiledJsonPath compiled = NopJsonPath.compile("$.store.book[0].title");
        Map<String, Object> data = sampleData();
        assertEquals("Sayings of the Century", compiled.eval(data));
        assertEquals("Sayings of the Century", compiled.eval(data));
    }
}
