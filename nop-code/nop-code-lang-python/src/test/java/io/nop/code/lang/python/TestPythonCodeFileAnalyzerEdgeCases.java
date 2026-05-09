package io.nop.code.lang.python;

import io.nop.code.core.model.CodeFileAnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestPythonCodeFileAnalyzerEdgeCases {

    private PythonCodeFileAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new PythonCodeFileAnalyzer();
    }

    @Test
    void testAnalyzeEmptyString() {
        CodeFileAnalysisResult result = assertDoesNotThrow(() -> analyzer.analyze("test.py", ""));
    }

    @Test
    void testAnalyzeSyntaxError() {
        CodeFileAnalysisResult result = assertDoesNotThrow(() -> analyzer.analyze("test.py", "def test(:\n  return"));
    }
}
