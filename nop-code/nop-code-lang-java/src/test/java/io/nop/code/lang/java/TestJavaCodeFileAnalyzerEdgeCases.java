package io.nop.code.lang.java;

import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.lang.java.analyzer.JavaFileAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestJavaCodeFileAnalyzerEdgeCases {

    private JavaFileAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new JavaFileAnalyzer();
    }

    @Test
    void testAnalyzeEmptyString() {
        CodeFileAnalysisResult result = assertDoesNotThrow(() -> analyzer.analyze("Test.java", ""));
    }

    @Test
    void testAnalyzeSyntaxError() {
        CodeFileAnalysisResult result = assertDoesNotThrow(() -> analyzer.analyze("Test.java", "public class Test { void method( { }"));
    }
}
