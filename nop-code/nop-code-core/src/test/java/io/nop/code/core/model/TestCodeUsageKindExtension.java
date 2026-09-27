package io.nop.code.core.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TestCodeUsageKindExtension {

    @Test
    void testTestedByAndReferencesExist() {
        assertNotNull(CodeUsageKind.valueOf("TESTED_BY"));
        assertNotNull(CodeUsageKind.valueOf("REFERENCES"));
    }

    @Test
    void testEnumValuesAreUnique() {
        Set<String> names = Arrays.stream(CodeUsageKind.values())
                .map(Enum::name).collect(Collectors.toSet());
        assertEquals(CodeUsageKind.values().length, names.size());
        assertTrue(names.contains("TESTED_BY"));
        assertTrue(names.contains("REFERENCES"));
    }

    @Test
    void testTestedByIsDistinctFromTypeReference() {
        assertNotEquals(CodeUsageKind.TESTED_BY, CodeUsageKind.TYPE_REFERENCE);
        // TESTED_BY is a semantic complement: test file → tested class
        // while TYPE_REFERENCE is any type reference
    }
}
