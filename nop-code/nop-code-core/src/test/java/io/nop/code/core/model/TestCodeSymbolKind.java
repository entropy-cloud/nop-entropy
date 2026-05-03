package io.nop.code.core.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestCodeSymbolKind {
    @Test
    void testAll17ValuesExist() {
        CodeSymbolKind[] values = CodeSymbolKind.values();
        assertEquals(17, values.length);
    }

    @Test
    void testCorrectIntValues() {
        assertEquals(10, CodeSymbolKind.CLASS.getValue());
        assertEquals(20, CodeSymbolKind.INTERFACE.getValue());
        assertEquals(30, CodeSymbolKind.ENUM.getValue());
        assertEquals(40, CodeSymbolKind.ANNOTATION_TYPE.getValue());
        assertEquals(45, CodeSymbolKind.TYPE_ALIAS.getValue());
        assertEquals(46, CodeSymbolKind.MIXIN.getValue());
        assertEquals(47, CodeSymbolKind.DECORATOR.getValue());
        assertEquals(50, CodeSymbolKind.METHOD.getValue());
        assertEquals(55, CodeSymbolKind.FUNCTION.getValue());
        assertEquals(60, CodeSymbolKind.CONSTRUCTOR.getValue());
        assertEquals(70, CodeSymbolKind.FIELD.getValue());
        assertEquals(80, CodeSymbolKind.CONSTANT.getValue());
        assertEquals(90, CodeSymbolKind.NAMESPACE.getValue());
        assertEquals(95, CodeSymbolKind.PARAMETER.getValue());
        assertEquals(96, CodeSymbolKind.LOCAL_VARIABLE.getValue());
        assertEquals(97, CodeSymbolKind.TYPE_PARAMETER.getValue());
        assertEquals(98, CodeSymbolKind.IMPORT.getValue());
    }

    @Test
    void testAllKindsHaveNonNullLabel() {
        for (CodeSymbolKind kind : CodeSymbolKind.values()) {
            assertNotNull(kind.getLabel(), kind.name() + " should have a non-null label");
            assertFalse(kind.getLabel().isEmpty(), kind.name() + " should have a non-empty label");
        }
    }

    @Test
    void testNoDuplicateIntValues() {
        Set<Integer> seen = new HashSet<>();
        for (CodeSymbolKind kind : CodeSymbolKind.values()) {
            assertTrue(seen.add(kind.getValue()), "Duplicate value: " + kind.getValue() + " for " + kind.name());
        }
    }
}
