package io.nop.code.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestCodeAccessModifier {
    @Test
    void testAll6ValuesExist() {
        assertEquals(6, CodeAccessModifier.values().length);
    }

    @Test
    void testCorrectIntValues() {
        assertEquals(10, CodeAccessModifier.PUBLIC.getValue());
        assertEquals(20, CodeAccessModifier.PROTECTED.getValue());
        assertEquals(30, CodeAccessModifier.PRIVATE.getValue());
        assertEquals(40, CodeAccessModifier.PACKAGE_PRIVATE.getValue());
        assertEquals(41, CodeAccessModifier.INTERNAL.getValue());
        assertEquals(50, CodeAccessModifier.NO_MODIFIER.getValue());
    }

    @Test
    void testNoModifierIsHighestValue() {
        int maxValue = 0;
        for (CodeAccessModifier mod : CodeAccessModifier.values()) {
            maxValue = Math.max(maxValue, mod.getValue());
        }
        assertEquals(CodeAccessModifier.NO_MODIFIER.getValue(), maxValue);
    }
}
