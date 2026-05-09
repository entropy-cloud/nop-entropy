package io.nop.code.lang.typescript;

import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.lang.typescript.analyzer.TypeScriptCodeFileAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestTypeScriptCodeFileAnalyzerEdgeCases {

    private TypeScriptCodeFileAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new TypeScriptCodeFileAnalyzer();
    }

    @Test
    void testAnalyzeEmptyString() {
        CodeFileAnalysisResult result = assertDoesNotThrow(() -> analyzer.analyze("test.ts", ""));
    }

    @Test
    void testAnalyzeSyntaxError() {
        CodeFileAnalysisResult result = assertDoesNotThrow(() -> analyzer.analyze("test.ts", "function test( { return;"));
    }
}
