package io.nop.lint.core.xscript;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.lint.core.NopLintException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The xscript compile-time whitelist matrix (design 07 §3, Minimum Rules
 * #25): every forbidden construct class fails the compile explicitly, every
 * legal subset form compiles. Compile-time, not runtime — a violation never
 * reaches an executable expression.
 */
public class TestXScriptCompiler {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static final String RULE = "demo/xscript-compile";

    // ==================== forbidden constructs ====================

    @Test
    public void javaStyleImportDeclarationRejected() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> XScriptCompiler.compile(RULE, "import java.io.File"));
        assertTrue(ex.getMessage().contains(RULE), "message must carry the rule id: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("import"), "message must name the forbidden form: "
                + ex.getMessage());
    }

    @Test
    public void jsStyleImportDeclarationRejected() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> XScriptCompiler.compile(RULE, "import { File } from 'java.io'"));
        assertTrue(ex.getMessage().contains(RULE), "message must carry the rule id: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("import"), "message must name the forbidden form: "
                + ex.getMessage());
    }

    @Test
    public void classDefinitionRejected() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> XScriptCompiler.compile(RULE, "class Evil { }"));
        assertTrue(ex.getMessage().contains(RULE), "message must carry the rule id: " + ex.getMessage());
    }

    @Test
    public void classForNameReflectionRejected() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> XScriptCompiler.compile(RULE, "Class.forName('java.lang.Runtime')"));
        assertTrue(ex.getMessage().contains(RULE), "message must carry the rule id: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("invalid xscript"), "message must state the reason: "
                + ex.getMessage());
    }

    @Test
    public void fileOrNetworkGlobalFunctionRejected() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> XScriptCompiler.compile(RULE, "readText('/etc/passwd')"));
        assertTrue(ex.getMessage().contains(RULE), "message must carry the rule id: " + ex.getMessage());
    }

    @Test
    public void unknownBindingRejected() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> XScriptCompiler.compile(RULE, "foo.bar()"));
        assertTrue(ex.getMessage().contains(RULE), "message must carry the rule id: " + ex.getMessage());
    }

    @Test
    public void laterCapabilityBindingsNotRegistered() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> XScriptCompiler.compile(RULE, "typeAnalyzer.isSubtypeOf('A', 'B')"));
        assertTrue(ex.getMessage().contains(RULE), "typeAnalyzer is L2 (item 26), not in the v1 whitelist: "
                + ex.getMessage());
    }

    @Test
    public void blankScriptRejected() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> XScriptCompiler.compile(RULE, "   "));
        assertTrue(ex.getMessage().contains(RULE), "message must carry the rule id: " + ex.getMessage());
    }

    // ==================== legal subset (design 07 §6 grammar) ====================

    @Test
    public void legalSubsetWithLetIfForOfLambdaMatchesCompiles() {
        String script = """
                let getters = node.children('method_declaration')
                  .filter(x => x.child('name').text().matches('^get[A-Z].*'));
                if (getters.isEmpty()) {
                  report({ message: `no getters found` });
                }
                for (let getter of getters) {
                  report({ message: 'getter: ' + getter.text(), severity: 'warning' });
                }
                return null;
                """;
        IEvalAction action = XScriptCompiler.compile(RULE, script);
        assertTrue(action != null, "the legal subset must compile to an executable action");
    }

    @Test
    public void legalBacktickLiteralAndStringConcatCompiles() {
        IEvalAction action = XScriptCompiler.compile(RULE,
                "if (node.text().length() > 0) { report({ message: `len ` + node.text().length() }); }");
        assertTrue(action != null);
    }

    @Test
    public void legalCapturesAndDeclTypeReferencesCompile() {
        IEvalAction action = XScriptCompiler.compile(RULE, """
                let t = declType(node);
                if (t != null && captures.f != null) {
                  report({ message: t, capture: 'f' });
                }
                """);
        assertTrue(action != null);
    }
}
