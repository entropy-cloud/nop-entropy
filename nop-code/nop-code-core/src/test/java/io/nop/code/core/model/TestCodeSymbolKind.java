package io.nop.code.core.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestCodeSymbolKind {

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

    @Test
    void testTypeKindsHaveLowerValuesThanMemberKinds() {
        assertTrue(CodeSymbolKind.CLASS.getValue() < CodeSymbolKind.METHOD.getValue());
        assertTrue(CodeSymbolKind.INTERFACE.getValue() < CodeSymbolKind.FIELD.getValue());
        assertTrue(CodeSymbolKind.ENUM.getValue() < CodeSymbolKind.CONSTRUCTOR.getValue());
    }
}
