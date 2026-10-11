package io.nop.code.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * P1-6.7-009 (plan 2401): unit coverage for the vfs→local working-directory
 * normalization that {@code analyzeChanges} applies to the persisted rootPath.
 */
class TestCodeIndexServiceLocalWorkingDirectory {

    @Test
    void stripsFileScheme() {
        assertEquals("/Users/a/proj", CodeIndexService.toLocalWorkingDirectory("file:/Users/a/proj"));
    }

    @Test
    void restoresWindowsDriveLetter() {
        assertEquals("C:/proj", CodeIndexService.toLocalWorkingDirectory("file:/C:/proj"));
    }

    @Test
    void plainPathAndRootAreIdempotent() {
        assertEquals("/Users/a/proj", CodeIndexService.toLocalWorkingDirectory("/Users/a/proj"));
        assertEquals("/", CodeIndexService.toLocalWorkingDirectory("/"));
        assertEquals("relative/x", CodeIndexService.toLocalWorkingDirectory("relative/x"));
    }

    @Test
    void nullAndEmptyYieldNull() {
        assertNull(CodeIndexService.toLocalWorkingDirectory(null));
        assertNull(CodeIndexService.toLocalWorkingDirectory(""));
    }
}
