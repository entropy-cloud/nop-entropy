package io.nop.code.lang.typescript;

import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.core.model.CodeMethodCall;
import io.nop.code.lang.typescript.analyzer.TypeScriptCodeFileAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * N5.1: TypeScript call-graph qualified-name candidates. The analyzer produces
 * calleeQualifiedName for same-file calls (functions/methods) and import-resolvable calls;
 * the existing full/incremental resolution machinery turns those into calleeId. Unresolvable
 * calls keep calleeQualifiedName null (INFERRED, never persisted) so no false edge is created.
 */
@EnabledIf("io.nop.code.lang.typescript.TreeSitterNativeAvailableCondition#isNativeLibAvailable")
class TestTypeScriptCallQualifiedName {

    private TypeScriptCodeFileAnalyzer newAnalyzer() {
        return new TypeScriptCodeFileAnalyzer();
    }

    private CodeMethodCall callByMethod(CodeFileAnalysisResult result, String methodName) {
        return result.getCalls().stream()
                .filter(c -> methodName.equals(c.getMethodName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("call not found: " + methodName));
    }

    @Test
    void testSameFileThisMethodCall() {
        String source = """
                class UserService {
                    validateUser(u: string): boolean { return true; }
                    process(u: string): void { this.validateUser(u); }
                }
                """;
        CodeFileAnalysisResult result = newAnalyzer().analyze("app/UserService.ts", source);
        CodeMethodCall call = callByMethod(result, "validateUser");
        String expected = result.getSymbols().stream()
                .filter(sym -> "validateUser".equals(sym.getName()))
                .findFirst().orElseThrow()
                .getQualifiedName();
        assertEquals(expected, call.getCalleeQualifiedName(),
                "this.method call must resolve to the same-file method symbol qn");
    }

    @Test
    void testSameFileTopLevelFunctionCallDeclaredLater() {
        String source = """
                function run(): void {
                    helper();
                }
                function helper(): void {}
                """;
        CodeFileAnalysisResult result = newAnalyzer().analyze("app/run.ts", source);
        CodeMethodCall call = callByMethod(result, "helper");
        assertEquals("app.run.helper", call.getCalleeQualifiedName(),
                "post-walk resolution must see symbols declared after the caller");
    }

    @Test
    void testImportedFunctionCall() {
        String source = """
                import { formatName } from './format';
                function run(name: string): string {
                    return formatName(name);
                }
                """;
        CodeFileAnalysisResult result = newAnalyzer().analyze("app/run.ts", source);
        CodeMethodCall call = callByMethod(result, "formatName");
        assertEquals("app.format.formatName", call.getCalleeQualifiedName(),
                "imported function call must build <module-prefix>.<name> qn");
    }

    @Test
    void testImportedMemberCallKeepsMethodName() {
        String source = """
                import { Bar } from './bar';
                function run(): void {
                    Bar.build();
                }
                """;
        CodeFileAnalysisResult result = newAnalyzer().analyze("app/run.ts", source);
        CodeMethodCall call = callByMethod(result, "build");
        assertEquals("app.bar.Bar.build", call.getCalleeQualifiedName(),
                "member call on an imported name must append the method name, not resolve to the class symbol");
    }

    @Test
    void testAliasedImportUsesLocalBindingName() {
        String source = """
                import { formatter as fmt } from './format';
                function run(name: string): string {
                    return fmt(name);
                }
                """;
        CodeFileAnalysisResult result = newAnalyzer().analyze("app/run.ts", source);
        CodeMethodCall call = callByMethod(result, "fmt");
        assertEquals("app.format.formatter", call.getCalleeQualifiedName(),
                "alias import must be keyed by the local binding name but resolve to the original name");
    }

    @Test
    void testImportStatementsCollectedAsWholeText() {
        String source = """
                import { helper } from './helpers';
                export function run(): void { helper(); }
                """;
        CodeFileAnalysisResult result = newAnalyzer().analyze("app/run.ts", source);
        List<String> imports = result.getImports();
        assertEquals(1, imports.size(), "import statement must be collected");
        assertTrue(imports.get(0).startsWith("import") && imports.get(0).contains("'./helpers'"),
                "whole statement text must be kept (resolver parses the quoted specifier): " + imports.get(0));
    }

    @Test
    void testUnresolvableCallKeepsNullQualifiedName() {
        String source = """
                function run(api: ApiClient): void {
                    api.fetchAll();
                }
                """;
        CodeFileAnalysisResult result = newAnalyzer().analyze("app/run.ts", source);
        CodeMethodCall call = callByMethod(result, "fetchAll");
        assertNull(call.getCalleeQualifiedName(),
                "untyped receiver calls must stay unresolved (no false edge)");
        assertFalse(result.getCalls().isEmpty());
        assertNotNull(call.getCallerId());
    }
}
