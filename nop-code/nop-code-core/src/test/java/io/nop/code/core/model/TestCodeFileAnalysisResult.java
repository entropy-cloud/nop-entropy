package io.nop.code.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestCodeFileAnalysisResult {
    @Test
    void testListsInitializedNonNull() {
        CodeFileAnalysisResult result = new CodeFileAnalysisResult();
        assertNotNull(result.getImports());
        assertNotNull(result.getSymbols());
        assertNotNull(result.getCalls());
        assertNotNull(result.getInheritances());
        assertNotNull(result.getAnnotationUsages());
        assertTrue(result.getImports().isEmpty());
        assertTrue(result.getSymbols().isEmpty());
    }

    @Test
    void testLanguageCanBeSet() {
        CodeFileAnalysisResult result = new CodeFileAnalysisResult();
        result.setLanguage(CodeLanguage.JAVA);
        assertEquals(CodeLanguage.JAVA, result.getLanguage());

        result.setLanguage(CodeLanguage.PYTHON);
        assertEquals(CodeLanguage.PYTHON, result.getLanguage());

        result.setLanguage(CodeLanguage.TYPESCRIPT);
        assertEquals(CodeLanguage.TYPESCRIPT, result.getLanguage());
    }
}
