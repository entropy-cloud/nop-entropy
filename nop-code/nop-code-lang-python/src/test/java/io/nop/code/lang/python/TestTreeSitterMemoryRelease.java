package io.nop.code.lang.python;

import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.lang.python.PythonCodeFileAnalyzer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestTreeSitterMemoryRelease {

    private static final String PYTHON_SOURCE =
            "class MyClass:\n" +
            "    def __init__(self):\n" +
            "        self.value = 42\n" +
            "    \n" +
            "    def greet(self, name):\n" +
            "        return f\"Hello, {name}!\"\n";

    @Test
    void testConsecutiveAnalysesNoErrors() {
        PythonCodeFileAnalyzer analyzer = new PythonCodeFileAnalyzer();
        for (int i = 0; i < 15; i++) {
            CodeFileAnalysisResult result = analyzer.analyze("test_" + i + ".py", PYTHON_SOURCE);
            assertNotNull(result, "Analysis " + i + " should succeed");
            assertFalse(result.getSymbols().isEmpty(), "Analysis " + i + " should produce symbols");
        }
    }

    @Test
    void testLargeSourceAnalysis() {
        PythonCodeFileAnalyzer analyzer = new PythonCodeFileAnalyzer();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("class Class").append(i).append(":\n");
            sb.append("    def method").append(i).append("(self):\n");
            sb.append("        pass\n\n");
        }
        CodeFileAnalysisResult result = analyzer.analyze("large.py", sb.toString());
        assertNotNull(result);
        assertFalse(result.getSymbols().isEmpty());
    }
}
