package io.nop.code.lang.typescript;

import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.core.model.CodeMethodCall;
import io.nop.code.lang.typescript.analyzer.TypeScriptCodeFileAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestTypeScriptCallExtraction {

    private static final String TS_SOURCE_WITH_CALLS =
            "class UserService {\n" +
            "    private api: ApiClient;\n" +
            "    \n" +
            "    getUser(id: string): User {\n" +
            "        const response = this.api.fetch(id);\n" +
            "        return response.data;\n" +
            "    }\n" +
            "    \n" +
            "    processUser(user: User): void {\n" +
            "        this.validateUser(user);\n" +
            "        this.saveUser(user);\n" +
            "    }\n" +
            "}\n";

    @Test
    void testMethodDefinitionsProduceCalls() {
        TypeScriptCodeFileAnalyzer analyzer = new TypeScriptCodeFileAnalyzer();
        CodeFileAnalysisResult result = analyzer.analyze("UserService.ts", TS_SOURCE_WITH_CALLS);
        assertNotNull(result);

        List<CodeMethodCall> calls = result.getCalls();
        assertFalse(calls.isEmpty(), "Method definitions should produce call edges via walkNodeForCalls");
    }

    @Test
    void testTopLevelFunctionCall() {
        String source =
                "function greet(name: string): string {\n" +
                "    return formatName(name);\n" +
                "}\n";
        TypeScriptCodeFileAnalyzer analyzer = new TypeScriptCodeFileAnalyzer();
        CodeFileAnalysisResult result = analyzer.analyze("test.ts", source);
        assertNotNull(result);

        List<CodeMethodCall> calls = result.getCalls();
        assertFalse(calls.isEmpty(), "Function declarations should produce call edges via walkNodeForCalls");
    }
}
