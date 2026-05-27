package io.nop.code.lang.java;

import io.nop.code.core.model.CodeLanguage;
import io.nop.code.lang.java.analyzer.JavaFileAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestJavaLanguageAdapter {

    private JavaLanguageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JavaLanguageAdapter();
    }

    @Test
    void testAdapterMetadata_consistentLanguageAndExtensions() {
        assertEquals(CodeLanguage.JAVA, adapter.getLanguage());
        List<String> extensions = adapter.getFileExtensions();
        assertEquals(1, extensions.size());
        assertTrue(extensions.contains(".java"));
        assertInstanceOf(JavaFileAnalyzer.class, adapter.getFileAnalyzer());
    }

    @Test
    void testGetExcludePatterns_coversBuildAndVcsDirs() {
        List<String> patterns = adapter.getExcludePatterns();
        assertTrue(patterns.contains("**/target/**"));
        assertTrue(patterns.contains("**/build/**"));
        assertTrue(patterns.contains("**/.git/**"));
        assertTrue(patterns.contains("**/node_modules/**"));
    }
}
