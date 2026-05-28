package io.nop.stream.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestClassNameValidator {

    @Test
    void testAllowedNopStreamClass() {
        assertDoesNotThrow(() -> ClassNameValidator.validateClassName("io.nop.stream.core.SomeClass"));
    }

    @Test
    void testAllowedJavaClass() {
        assertDoesNotThrow(() -> ClassNameValidator.validateClassName("java.lang.String"));
    }

    @Test
    void testAllowedArrayClass() {
        assertDoesNotThrow(() -> ClassNameValidator.validateClassName("[Ljava.lang.String;"));
    }

    @Test
    void testDisallowedClass() {
        SecurityException ex = assertThrows(SecurityException.class,
                () -> ClassNameValidator.validateClassName("com.malicious.Attack"));
        assertTrue(ex.getMessage().contains("com.malicious.Attack"));
    }

    @Test
    void testNullClassName() {
        assertThrows(IllegalArgumentException.class,
                () -> ClassNameValidator.validateClassName(null));
    }

    @Test
    void testEmptyClassName() {
        assertThrows(IllegalArgumentException.class,
                () -> ClassNameValidator.validateClassName(""));
    }
}
