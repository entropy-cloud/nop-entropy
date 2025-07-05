package io.nop.ai.coder.simplifier;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JsonSimplifierTest {

    @Test
    void testSimplifyPrimitiveValue() {
        JsonSimplifier simplifier = new JsonSimplifier(Collections.emptySet(), Collections.emptySet());
        
        assertEquals("test", simplifier.simplify("test"));
        assertEquals(123, simplifier.simplify(123));
        assertEquals(true, simplifier.simplify(true));
        assertNull(simplifier.simplify(null));
    }

    @Test
    void testSimplifyEmptyMap() {
        JsonSimplifier simplifier = new JsonSimplifier(Collections.emptySet(), Collections.emptySet());
        Map<String, Object> input = new HashMap<>();
        
        assertNotNull(simplifier.simplify(input));
    }

    @Test
    void testSimplifyMapWithKeysToKeep() {
        Set<String> keysToKeep = Set.of("name", "age");
        JsonSimplifier simplifier = new JsonSimplifier(keysToKeep, Collections.emptySet());
        
        Map<String, Object> input = new HashMap<>();
        input.put("name", "Alice");
        input.put("age", 30);
        input.put("address", "123 Main St");
        
        Map<String, Object> result = (Map<String, Object>) simplifier.simplify(input);
        
        assertEquals(2, result.size());
        assertEquals("Alice", result.get("name"));
        assertEquals(30, result.get("age"));
        assertFalse(result.containsKey("address"));
    }

    @Test
    void testSimplifyNestedMap() {
        Set<String> keysToKeep = Set.of("name", "address");
        JsonSimplifier simplifier = new JsonSimplifier(keysToKeep, Collections.emptySet());
        
        Map<String, Object> address = new HashMap<>();
        address.put("street", "Main St");
        address.put("city", "Metropolis");
        
        Map<String, Object> input = new HashMap<>();
        input.put("name", "Bob");
        input.put("age", 25);
        input.put("address", address);
        
        Map<String, Object> result = (Map<String, Object>) simplifier.simplify(input);
        
        assertEquals(2, result.size());
        assertEquals("Bob", result.get("name"));
        
        Map<String, Object> simplifiedAddress = (Map<String, Object>) result.get("address");
        assertEquals(0, simplifiedAddress.size());
    }

    @Test
    void testSimplifyMapWithPositioningKeys() {
        Set<String> keysToKeep = Set.of("name");
        Set<String> positioningKeys = Set.of("id", "location");
        JsonSimplifier simplifier = new JsonSimplifier(keysToKeep, positioningKeys);
        
        Map<String, Object> input = new HashMap<>();
        input.put("name", "Charlie");
        input.put("id", "user123");
        input.put("location", "sectionA");
        input.put("temp", "value");
        
        Map<String, Object> result = (Map<String, Object>) simplifier.simplify(input);
        
        assertEquals(3, result.size());
        assertEquals("Charlie", result.get("name"));
        assertEquals("user123", result.get("id"));
        assertEquals("sectionA", result.get("location"));
        assertFalse(result.containsKey("temp"));
    }

    @Test
    void testSimplifyEmptyList() {
        JsonSimplifier simplifier = new JsonSimplifier(Collections.emptySet(), Collections.emptySet());
        List<Object> input = Collections.emptyList();
        
        List<Object> result = (List<Object>) simplifier.simplify(input);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSimplifyListWithMixedContent() {
        Set<String> keysToKeep = Set.of("name");
        JsonSimplifier simplifier = new JsonSimplifier(keysToKeep, Collections.emptySet());
        
        Map<String, Object> item1 = new HashMap<>();
        item1.put("name", "Item1");
        item1.put("value", 100);
        
        Map<String, Object> item2 = new HashMap<>();
        item2.put("value", 200);
        
        List<Object> input = Arrays.asList(item1, item2, "test", null);
        
        List<Object> result = (List<Object>) simplifier.simplify(input);
        
        assertEquals(2, result.size()); // null should be filtered out
        
        Map<String, Object> simplifiedItem1 = (Map<String, Object>) result.get(0);
        assertEquals(1, simplifiedItem1.size());
        assertEquals("Item1", simplifiedItem1.get("name"));
        
        assertNotNull(result.get(1));
        assertEquals("test", result.get(1));
    }

    @Test
    void testSimplifyComplexStructure() {
        Set<String> keysToKeep = Set.of("name", "items");
        Set<String> positioningKeys = Set.of("id");
        JsonSimplifier simplifier = new JsonSimplifier(keysToKeep, positioningKeys);
        
        Map<String, Object> item1 = new HashMap<>();
        item1.put("name", "Product1");
        item1.put("price", 10.99);
        
        Map<String, Object> item2 = new HashMap<>();
        item2.put("price", 20.99);
        
        Map<String, Object> category = new HashMap<>();
        category.put("name", "Electronics");
        category.put("description", "Tech products");
        
        Map<String, Object> input = new HashMap<>();
        input.put("id", "order123");
        input.put("name", "My Order");
        input.put("items", Arrays.asList(item1, item2));
        input.put("category", category);
        input.put("timestamp", "2023-01-01");
        
        Map<String, Object> result = (Map<String, Object>) simplifier.simplify(input);
        
        assertEquals(4, result.size());
        assertEquals("order123", result.get("id"));
        assertEquals("My Order", result.get("name"));
        
        List<Object> simplifiedItems = (List<Object>) result.get("items");
        assertEquals(1, simplifiedItems.size());
        
        Map<String, Object> simplifiedItem1 = (Map<String, Object>) simplifiedItems.get(0);
        assertEquals(1, simplifiedItem1.size());
        assertEquals("Product1", simplifiedItem1.get("name"));
        

        assertTrue(result.containsKey("category"));
        assertFalse(result.containsKey("timestamp"));
    }
}