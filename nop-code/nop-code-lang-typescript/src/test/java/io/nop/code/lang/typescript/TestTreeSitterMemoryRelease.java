package io.nop.code.lang.typescript;

import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.lang.typescript.analyzer.TypeScriptCodeFileAnalyzer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestTreeSitterMemoryRelease {

    private static final String TS_SOURCE =
            "class MyClass {\n" +
            "    private name: string;\n" +
            "    \n" +
            "    constructor(name: string) {\n" +
            "        this.name = name;\n" +
            "    }\n" +
            "    \n" +
            "    greet(): string {\n" +
            "        return \"Hello, \" + this.name;\n" +
            "    }\n" +
            "}\n";

    @Test
    void testConsecutiveAnalysesNoErrors() {
        TypeScriptCodeFileAnalyzer analyzer = new TypeScriptCodeFileAnalyzer();
        for (int i = 0; i < 15; i++) {
            CodeFileAnalysisResult result = analyzer.analyze("test_" + i + ".ts", TS_SOURCE);
            assertNotNull(result, "Analysis " + i + " should succeed");
            assertFalse(result.getSymbols().isEmpty(), "Analysis " + i + " should produce symbols");
        }
    }

    @Test
    void testLargeSourceAnalysis() {
        TypeScriptCodeFileAnalyzer analyzer = new TypeScriptCodeFileAnalyzer();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("class Class").append(i).append(" {\n");
            sb.append("    method").append(i).append("(): void {}\n");
            sb.append("}\n\n");
        }
        CodeFileAnalysisResult result = analyzer.analyze("large.ts", sb.toString());
        assertNotNull(result);
        assertFalse(result.getSymbols().isEmpty());
    }
}
