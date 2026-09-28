package io.nop.code.lang.rust;

import io.nop.code.core.model.CodeFileAnalysisResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf("io.nop.code.lang.rust.TreeSitterNativeAvailableCondition#isNativeLibAvailable")
class TestRustTreeSitterMemoryRelease {

    @Test
    void repeatedParseKeepsSucceeding() {
        RustCodeFileAnalyzer analyzer = new RustCodeFileAnalyzer();
        String source = "pub fn f(a: i32) -> i32 {\n    a + 1\n}\n";
        for (int i = 0; i < 15; i++) {
            CodeFileAnalysisResult result = analyzer.analyze("src/f.rs", source);
            assertNotNull(result);
            assertTrue(result.getSymbols().size() >= 1);
        }
    }

    @Test
    void largeFileParseSucceeds() {
        RustCodeFileAnalyzer analyzer = new RustCodeFileAnalyzer();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("pub fn f").append(i).append("(a: i32) -> i32 {\n    a + ").append(i).append("\n}\n\n");
        }
        CodeFileAnalysisResult result = analyzer.analyze("src/big.rs", sb.toString());
        assertNotNull(result);
        assertTrue(result.getSymbols().size() >= 100,
                "100 functions must be extracted, got: " + result.getSymbols().size());
    }
}
