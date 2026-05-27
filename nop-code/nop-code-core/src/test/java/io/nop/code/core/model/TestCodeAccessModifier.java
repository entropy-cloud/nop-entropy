package io.nop.code.core.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestCodeAccessModifier {

    @Test
    void testNoDuplicateIntValues() {
        Set<Integer> seen = new HashSet<>();
        for (CodeAccessModifier mod : CodeAccessModifier.values()) {
            assertTrue(seen.add(mod.getValue()), "Duplicate value: " + mod.getValue() + " for " + mod.name());
        }
    }

    @Test
    void testNoModifierHasHighestValue() {
        int maxValue = 0;
        for (CodeAccessModifier mod : CodeAccessModifier.values()) {
            maxValue = Math.max(maxValue, mod.getValue());
        }
        assertEquals(CodeAccessModifier.NO_MODIFIER.getValue(), maxValue);
    }

    @Test
    void testPublicHasLowestValue() {
        int minValue = Integer.MAX_VALUE;
        for (CodeAccessModifier mod : CodeAccessModifier.values()) {
            minValue = Math.min(minValue, mod.getValue());
        }
        assertEquals(CodeAccessModifier.PUBLIC.getValue(), minValue);
    }
}
