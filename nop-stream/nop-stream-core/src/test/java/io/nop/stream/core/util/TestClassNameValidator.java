package io.nop.stream.core.util;

import io.nop.stream.core.exceptions.StreamException;
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
        StreamException ex = assertThrows(StreamException.class,
                () -> ClassNameValidator.validateClassName("com.malicious.Attack"));
        assertTrue(ex.getMessage().contains("com.malicious.Attack"));
    }

    @Test
    void testNullClassName() {
        assertThrows(StreamException.class,
                () -> ClassNameValidator.validateClassName(null));
    }

    @Test
    void testEmptyClassName() {
        assertThrows(StreamException.class,
                () -> ClassNameValidator.validateClassName(""));
    }

    @Test
    void testArrayClassValidationRejectsMaliciousPrefix() {
        StreamException ex = assertThrows(StreamException.class,
                () -> ClassNameValidator.validateClassName("[Lcom.evil.Malicious;"));
        assertTrue(ex.getMessage().contains("[Lcom.evil.Malicious;"));
    }

    @Test
    void testArrayClassValidationAcceptsAllowedPrefixes() {
        assertDoesNotThrow(() -> ClassNameValidator.validateClassName("[Lio.nop.stream.core.SomeClass;"));
        assertDoesNotThrow(() -> ClassNameValidator.validateClassName("[Ljava.lang.String;"));
    }

    @Test
    void testDangerousJavaClassRejected() {
        assertThrows(StreamException.class,
                () -> ClassNameValidator.validateClassName("java.rmi.server.RemoteObject"));
    }

    @Test
    void testSafeJavaClassAccepted() {
        assertDoesNotThrow(() -> ClassNameValidator.validateClassName("java.lang.String"));
        assertDoesNotThrow(() -> ClassNameValidator.validateClassName("java.util.ArrayList"));
        assertDoesNotThrow(() -> ClassNameValidator.validateClassName("java.math.BigDecimal"));
        assertDoesNotThrow(() -> ClassNameValidator.validateClassName("java.time.Instant"));
        assertDoesNotThrow(() -> ClassNameValidator.validateClassName("java.io.Serializable"));
        assertDoesNotThrow(() -> ClassNameValidator.validateClassName("java.nio.ByteBuffer"));
    }

    @Test
    void testJavaSqlClassRejected() {
        assertThrows(StreamException.class,
                () -> ClassNameValidator.validateClassName("java.sql.Connection"));
    }

    @Test
    void testAccumulatorClassValidationAcceptsNopStream() {
        assertDoesNotThrow(() ->
                ClassNameValidator.validateAccumulatorClass("io.nop.stream.core.common.accumulators.LongCounter"));
    }

    @Test
    void testAccumulatorClassValidationRejectsNonNopStream() {
        assertThrows(StreamException.class,
                () -> ClassNameValidator.validateAccumulatorClass("java.lang.String"));
    }
}
