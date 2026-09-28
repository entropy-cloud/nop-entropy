package io.nop.code.lang.go;

import io.nop.code.core.model.CodeFileAnalysisResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Memory-release parity with the typescript module's TestTreeSitterMemoryRelease:
 * repeated parses plus one large-file parse must keep succeeding (arena reuse
 * without growth blowups).
 */
@EnabledIf("io.nop.code.lang.go.TreeSitterNativeAvailableCondition#isNativeLibAvailable")
class TestGoTreeSitterMemoryRelease {

    @Test
    void repeatedParseKeepsSucceeding() {
        GoCodeFileAnalyzer analyzer = new GoCodeFileAnalyzer();
        String source = "package demo\n\nfunc F(a int) int {\n\treturn a + 1\n}\n";
        for (int i = 0; i < 15; i++) {
            CodeFileAnalysisResult result = analyzer.analyze("demo/f.go", source);
            assertNotNull(result);
            assertTrue(result.getSymbols().size() >= 1);
        }
    }

    @Test
    void largeFileParseSucceeds() {
        GoCodeFileAnalyzer analyzer = new GoCodeFileAnalyzer();
        StringBuilder sb = new StringBuilder("package demo\n");
        for (int i = 0; i < 100; i++) {
            sb.append("func F").append(i).append("(a int) int {\n\treturn a + ").append(i).append("\n}\n\n");
        }
        CodeFileAnalysisResult result = analyzer.analyze("demo/big.go", sb.toString());
        assertNotNull(result);
        assertTrue(result.getSymbols().size() >= 100,
                "100 functions must be extracted, got: " + result.getSymbols().size());
    }
}
