package io.nop.code.lang.rust;

import io.nop.code.core.model.CodeLanguage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRustLanguageAdapter {

    @Test
    void adapterMetadataConsistent() {
        RustLanguageAdapter adapter = new RustLanguageAdapter();
        assertEquals(CodeLanguage.RUST, adapter.getLanguage());
        List<String> extensions = adapter.getFileExtensions();
        assertEquals(1, extensions.size());
        assertTrue(extensions.contains(".rs"));
        assertInstanceOf(RustCodeFileAnalyzer.class, adapter.getFileAnalyzer());
    }

    @Test
    void excludePatternsCoverTargetAndGit() {
        RustLanguageAdapter adapter = new RustLanguageAdapter();
        List<String> patterns = adapter.getExcludePatterns();
        assertTrue(patterns.contains("**/target/**"));
        assertTrue(patterns.contains("**/.git/**"));
    }
}
