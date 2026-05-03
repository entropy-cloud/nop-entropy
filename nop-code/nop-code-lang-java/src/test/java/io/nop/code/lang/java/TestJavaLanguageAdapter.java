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
    void testGetLanguage() {
        assertEquals(CodeLanguage.JAVA, adapter.getLanguage());
    }

    @Test
    void testGetFileExtensions() {
        List<String> extensions = adapter.getFileExtensions();
        assertEquals(1, extensions.size());
        assertEquals(".java", extensions.get(0));
    }

    @Test
    void testGetExcludePatterns() {
        List<String> patterns = adapter.getExcludePatterns();
        assertTrue(patterns.contains("**/target/**"),
                "Should exclude target directory");
        assertTrue(patterns.contains("**/build/**"),
                "Should exclude build directory");
        assertTrue(patterns.contains("**/.git/**"),
                "Should exclude .git directory");
        assertTrue(patterns.contains("**/node_modules/**"),
                "Should exclude node_modules directory");
    }

    @Test
    void testGetFileAnalyzer() {
        assertNotNull(adapter.getFileAnalyzer());
        assertInstanceOf(JavaFileAnalyzer.class, adapter.getFileAnalyzer());
    }
}
