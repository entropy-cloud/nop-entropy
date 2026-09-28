package io.nop.code.lang.go;

import io.nop.code.core.model.CodeLanguage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestGoLanguageAdapter {

    @Test
    void adapterMetadataConsistent() {
        GoLanguageAdapter adapter = new GoLanguageAdapter();
        assertEquals(CodeLanguage.GO, adapter.getLanguage());
        List<String> extensions = adapter.getFileExtensions();
        assertEquals(1, extensions.size());
        assertTrue(extensions.contains(".go"));
        assertInstanceOf(GoCodeFileAnalyzer.class, adapter.getFileAnalyzer());
    }

    @Test
    void excludePatternsCoverVendorAndGit() {
        GoLanguageAdapter adapter = new GoLanguageAdapter();
        List<String> patterns = adapter.getExcludePatterns();
        assertTrue(patterns.contains("**/vendor/**"));
        assertTrue(patterns.contains("**/.git/**"));
    }
}
