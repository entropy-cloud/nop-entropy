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
    void testAdapterMetadata_consistentLanguageAndExtensions() {
        assertEquals(CodeLanguage.PYTHON, adapter.getLanguage());
        List<String> extensions = adapter.getFileExtensions();
        assertEquals(1, extensions.size());
        assertTrue(extensions.contains(".py"));
        assertInstanceOf(PythonCodeFileAnalyzer.class, adapter.getFileAnalyzer());
    }

    @Test
    void testGetExcludePatterns_coversVenvAndPycache() {
        List<String> patterns = adapter.getExcludePatterns();
        assertTrue(patterns.contains("**/__pycache__/**"));
        assertTrue(patterns.contains("**/venv/**"));
        assertTrue(patterns.contains("**/.venv/**"));
    }
}
