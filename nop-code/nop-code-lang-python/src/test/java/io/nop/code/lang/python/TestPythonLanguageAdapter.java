package io.nop.code.lang.python;

import io.nop.code.core.model.CodeLanguage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestPythonLanguageAdapter {

    private PythonLanguageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PythonLanguageAdapter();
    }

    @Test
    void testGetLanguage() {
        assertEquals(CodeLanguage.PYTHON, adapter.getLanguage());
    }

    @Test
    void testGetFileExtensions() {
        List<String> extensions = adapter.getFileExtensions();
        assertEquals(1, extensions.size());
        assertEquals(".py", extensions.get(0));
    }

    @Test
    void testGetExcludePatterns() {
        List<String> patterns = adapter.getExcludePatterns();
        assertTrue(patterns.contains("**/__pycache__/**"));
        assertTrue(patterns.contains("**/venv/**"));
        assertTrue(patterns.contains("**/.venv/**"));
    }

    @Test
    void testGetFileAnalyzer() {
        assertNotNull(adapter.getFileAnalyzer());
        assertInstanceOf(PythonCodeFileAnalyzer.class, adapter.getFileAnalyzer());
    }
}
