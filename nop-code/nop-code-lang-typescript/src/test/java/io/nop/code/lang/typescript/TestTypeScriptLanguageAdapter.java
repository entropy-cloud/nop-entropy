package io.nop.code.lang.typescript;

import io.nop.code.core.model.CodeLanguage;
import io.nop.code.lang.typescript.analyzer.TypeScriptCodeFileAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestTypeScriptLanguageAdapter {

    private TypeScriptLanguageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TypeScriptLanguageAdapter();
    }

    @Test
    void testGetLanguage() {
        assertEquals(CodeLanguage.TYPESCRIPT, adapter.getLanguage());
    }

    @Test
    void testGetFileExtensions() {
        List<String> extensions = adapter.getFileExtensions();
        assertEquals(2, extensions.size());
        assertTrue(extensions.contains(".ts"));
        assertTrue(extensions.contains(".tsx"));
    }

    @Test
    void testGetExcludePatterns() {
        List<String> patterns = adapter.getExcludePatterns();
        assertTrue(patterns.contains("**/node_modules/**"));
        assertTrue(patterns.contains("**/dist/**"));
        assertTrue(patterns.contains("**/.git/**"));
    }

    @Test
    void testGetFileAnalyzer() {
        assertNotNull(adapter.getFileAnalyzer());
        assertInstanceOf(TypeScriptCodeFileAnalyzer.class, adapter.getFileAnalyzer());
    }
}
