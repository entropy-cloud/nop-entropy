package io.nop.code.lang.csharp;

import io.nop.code.core.model.CodeFileAnalysisResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf("io.nop.code.lang.csharp.TreeSitterNativeAvailableCondition#isNativeLibAvailable")
class TestCSharpTreeSitterMemoryRelease {

    @Test
    void repeatedParseKeepsSucceeding() {
        CSharpCodeFileAnalyzer analyzer = new CSharpCodeFileAnalyzer();
        String source = "public class F { public int Add(int a) { return a + 1; } }\n";
        for (int i = 0; i < 15; i++) {
            CodeFileAnalysisResult result = analyzer.analyze("F.cs", source);
            assertNotNull(result);
            assertTrue(result.getSymbols().size() >= 1);
        }
    }

    @Test
    void largeFileParseSucceeds() {
        CSharpCodeFileAnalyzer analyzer = new CSharpCodeFileAnalyzer();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("public class C").append(i).append(" { public int M() { return ").append(i).append("; } }\n");
        }
        CodeFileAnalysisResult result = analyzer.analyze("big.cs", sb.toString());
        assertNotNull(result);
        assertTrue(result.getSymbols().size() >= 100,
                "100 classes must be extracted, got: " + result.getSymbols().size());
    }
}
