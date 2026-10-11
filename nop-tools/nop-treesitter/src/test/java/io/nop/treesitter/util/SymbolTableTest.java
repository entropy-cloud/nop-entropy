package io.nop.treesitter.util;

import io.nop.treesitter.TreeSitterException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SymbolTable interning: stable identity across inserts, reverse-lookup
 * round-trip, growth beyond initial capacity and id-collision freedom.
 */
class SymbolTableTest {

    @Test
    void internReturnsStableIdsForSameString() {
        SymbolTable table = new SymbolTable();
        int a = table.intern("array");
        int b = table.intern("array");
        assertEquals(a, b, "same string must map to the same id");
        assertEquals(1, table.size());
    }

    @Test
    void distinctStringsGetDistinctSequentialIds() {
        SymbolTable table = new SymbolTable();
        int array = table.intern("array");
        int object = table.intern("object");
        int pair = table.intern("pair");
        assertEquals(0, array);
        assertEquals(1, object);
        assertEquals(2, pair);
        assertEquals(3, table.size());
    }

    @Test
    void reverseLookupRoundTrips() {
        SymbolTable table = new SymbolTable();
        int number = table.intern("number");
        int string = table.intern("string");
        assertEquals("number", table.resolve(number));
        assertEquals("string", table.resolve(string));
    }

    @Test
    void growthBeyondInitialCapacityPreservesMappings() {
        SymbolTable table = new SymbolTable();
        for (int i = 0; i < 200; i++) {
            int id = table.intern("symbol-" + i);
            assertEquals(i, id, "no id collisions: ids must stay sequential");
        }
        for (int i = 0; i < 200; i++) {
            assertEquals("symbol-" + i, table.resolve(i), "mapping preserved after growth@" + i);
        }
        assertEquals(200, table.size());
    }

    @Test
    void resolveOnUnknownIdThrows() {
        SymbolTable table = new SymbolTable();
        table.intern("array");
        assertThrows(TreeSitterException.class, () -> table.resolve(1));
        assertThrows(TreeSitterException.class, () -> table.resolve(-1));
        assertThrows(TreeSitterException.class, () -> table.resolve(100));
    }

    @Test
    void internNullThrows() {
        SymbolTable table = new SymbolTable();
        assertThrows(IllegalArgumentException.class, () -> table.intern(null));
    }
}
