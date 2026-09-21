package io.nop.lint.core.xscript;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.pattern.MetaVarEnv;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The xscript binding-face proofs (design 07 §2.1–§2.3): node/captures/
 * report/declType semantics per match, every failure path explicit. The
 * scripts run through the real {@link XScriptCompiler} whitelist, and every
 * navigation target comes from the real tree-sitter Java parse of the source
 * (Minimum Rules #23 wiring).
 */
public class TestXScriptEngine {

    private static final String RULE = "demo/xscript-bindings";

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);

    private static final String SRC = """
            class Demo {
                private List<String> names;
                public String getName() { return names.get(0); }
            }
            """;

    private static LintTree tree;
    private static SourceMap sourceMap;
    private static NodeWrapper root;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        tree = JAVA.parse(SRC);
        sourceMap = new SourceMap(tree.source());
        root = new NodeWrapper(tree.root(), sourceMap);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ==================== report(): defaults and overrides ====================

    @Test
    public void reportProducesDiagnosticWithRuleDefaults() {
        XScriptEngine.MatchOutcome outcome = run("report({ message: 'found one' });",
                root.descendant("method_declaration"), env());

        assertEquals(1, outcome.diagnostics().size());
        Diagnostic diagnostic = outcome.diagnostics().get(0);
        assertEquals(RULE, diagnostic.ruleId());
        assertEquals("warning", diagnostic.severity(), "severity defaults to the rule's");
        assertEquals("found one", diagnostic.message());
        assertEquals(root.descendant("method_declaration").unwrap().range(), diagnostic.range(),
                "default target is the current match node");
        assertFalse(outcome.capped());
    }

    @Test
    public void reportSeverityOverrideMustBeAValidLevel() {
        XScriptEngine.MatchOutcome outcome = run("report({ message: 'm', severity: 'error' });",
                root.descendant("method_declaration"), env());
        assertEquals("error", outcome.diagnostics().get(0).severity());

        Exception bad = assertThrows(Exception.class, () -> run(
                "report({ message: 'm', severity: 'fatal' });",
                root.descendant("method_declaration"), env()));
        assertTrue(bad.getMessage().contains("severity"), "invalid levels fail loudly: " + bad.getMessage());
    }

    @Test
    public void reportRequiresNonBlankMessage() {
        for (String script : new String[]{"report({});", "report({ message: '' });",
                "report({ message: null });"}) {
            Exception bad = assertThrows(Exception.class, () -> run(script,
                    root.descendant("method_declaration"), env()), script);
            assertTrue(bad.getMessage().contains("message"), script + " -> " + bad.getMessage());
        }
    }

    @Test
    public void reportRejectsNonObjectArgument() {
        Exception bad = assertThrows(Exception.class,
                () -> run("report('just a string');", root.descendant("method_declaration"), env()));
        assertTrue(bad.getMessage().contains("report()"), bad.getMessage());
    }

    @Test
    public void reportFixFieldRejectedPointingAtItem25() {
        Exception bad = assertThrows(Exception.class, () -> run(
                "report({ message: 'm', fix: { template: 'x' } });",
                root.descendant("method_declaration"), env()));
        assertTrue(bad.getMessage().contains("fix"), bad.getMessage());
        assertTrue(bad.getMessage().contains("item 25"), "the rejection must name the successor: "
                + bad.getMessage());
    }

    @Test
    public void reportMultipleCallsProduceMultipleDiagnostics() {
        XScriptEngine.MatchOutcome outcome = run("""
                report({ message: 'first' });
                report({ message: 'second', severity: 'info' });
                """, root.descendant("method_declaration"), env());

        assertEquals(2, outcome.diagnostics().size());
        assertEquals("first", outcome.diagnostics().get(0).message());
        assertEquals("info", outcome.diagnostics().get(1).severity());
    }

    // ==================== report(): node / capture target override ====================

    @Test
    public void reportNodeOverrideRetargetsTheDiagnostic() {
        NodeWrapper method = root.descendant("method_declaration");
        XScriptEngine.MatchOutcome outcome = run("report({ message: 'm', node: node.child('name') });",
                method, env());

        assertEquals(method.child("name").unwrap().range(), outcome.diagnostics().get(0).range(),
                "the diagnostic targets the name child, not the match node");
    }

    @Test
    public void reportCaptureOverrideUsesTheCapturedNode() {
        NodeWrapper name = root.descendant("method_declaration").child("name");
        MetaVarEnv env = env();
        env.insert("f", name.unwrap());

        XScriptEngine.MatchOutcome outcome = run("report({ message: captures.f.text(), capture: 'f' });",
                root.descendant("method_declaration"), env);

        assertEquals("getName", outcome.diagnostics().get(0).message());
        assertEquals(name.unwrap().range(), outcome.diagnostics().get(0).range());
    }

    @Test
    public void reportCaptureOverridesNodeTarget() {
        MetaVarEnv env = env();
        env.insert("f", root.descendant("field_declaration").unwrap());
        XScriptEngine.MatchOutcome outcome = run("report({ message: 'm', node: node, capture: 'f' });",
                root.descendant("method_declaration"), env);

        assertEquals(root.descendant("field_declaration").unwrap().range(), outcome.diagnostics().get(0).range(),
                "capture wins over node (design 07 §2.3)");
    }

    @Test
    public void reportSequenceCaptureTargetsItsFirstNode() {
        NodeWrapper method = root.descendant("method_declaration");
        MetaVarEnv env = env();
        env.insertMulti("ss", List.of(root.descendant("field_declaration").unwrap(),
                root.descendant("method_declaration").unwrap()));

        XScriptEngine.MatchOutcome outcome = run("report({ message: 'm', capture: 'ss' });", method, env);
        assertEquals(root.descendant("field_declaration").unwrap().range(), outcome.diagnostics().get(0).range(),
                "a sequence capture targets its first node in source order");
    }

    @Test
    public void reportUnknownOrEmptyCaptureFailsExplicitly() {
        Exception unknown = assertThrows(Exception.class, () -> run("report({ message: 'm', capture: 'nope' });",
                root.descendant("method_declaration"), env()));
        assertTrue(unknown.getMessage().contains("nope"), unknown.getMessage());

        MetaVarEnv empty = env();
        empty.insertMulti("empty", List.of());
        Exception none = assertThrows(Exception.class, () -> run("report({ message: 'm', capture: 'empty' });",
                root.descendant("method_declaration"), empty));
        assertTrue(none.getMessage().contains("zero nodes"), none.getMessage());
    }

    // ==================== captures: single and sequence shapes ====================

    @Test
    public void capturesExposeSingleAndSequenceShapes() {
        NodeWrapper method = root.descendant("method_declaration");
        MetaVarEnv env = env();
        env.insert("f", root.descendant("field_declaration").unwrap());
        env.insertMulti("ms", List.of(root.descendant("method_declaration").unwrap()));

        XScriptEngine.MatchOutcome outcome = run("""
                report({ message: captures.f.text(), capture: 'f' });
                for (let m of captures.ms) {
                  report({ message: m.kind(), severity: 'info' });
                }
                """, method, env);

        assertEquals(2, outcome.diagnostics().size());
        assertEquals("private List<String> names;", outcome.diagnostics().get(0).message());
        assertEquals("method_declaration", outcome.diagnostics().get(1).message());
    }

    // ==================== declType: the L1 consumer contract ====================

    @Test
    public void declTypeResolvesTheWrittenTypeOfDeclarations() {
        MetaVarEnv env = env();
        env.insert("d", root.descendant("field_declaration").unwrap());

        XScriptEngine.MatchOutcome outcome = run("report({ message: '' + declType(captures.d) });",
                root.descendant("method_declaration"), env);
        assertEquals("List<String>", outcome.diagnostics().get(0).message(),
                "the written type is returned verbatim");
    }

    @Test
    public void declTypeReturnsNullForNodesWithoutTypeField() {
        // the method's body block carries no 'type' field — the explicit
        // "cannot extract" contract returns null, never a guess
        XScriptEngine.MatchOutcome outcome = run("""
                if (declType(node) == null) {
                  report({ message: 'no declared type here' });
                }
                """, root.descendant("method_declaration").child("body"), env());
        assertEquals(1, outcome.diagnostics().size());
    }

    @Test
    public void declTypeRejectsNonNodeArguments() {
        Exception bad = assertThrows(Exception.class,
                () -> run("declType('not a node');", root.descendant("method_declaration"), env()));
        assertTrue(bad.getMessage().contains("declType()"), bad.getMessage());
    }

    // ==================== filter semantics and the diagnostic cap ====================

    @Test
    public void scriptWithoutReportFiltersTheMatch() {
        XScriptEngine.MatchOutcome outcome = run("let unused = node.text();",
                root.descendant("method_declaration"), env());
        assertTrue(outcome.diagnostics().isEmpty(), "no report() means no diagnostic (filter semantics)");
        assertFalse(outcome.capped());
    }

    @Test
    public void diagnosticCapAbortsTheScriptButKeepsReportedDiagnostics() {
        XScriptEngine capped = new XScriptEngine(RULE, "warning",
                XScriptCompiler.compile(RULE, """
                        let i = 0;
                        while (i < 20) {
                          report({ message: 'm' + i });
                          i = i + 1;
                        }
                        """), 5);
        XScriptEngine.MatchOutcome outcome = capped.executeMatch(
                root.descendant("method_declaration").unwrap(), env(), sourceMap);

        assertEquals(5, outcome.diagnostics().size(), "only the cap count of diagnostics stand");
        assertTrue(outcome.capped(), "the cap abort is surfaced for counting");
        assertEquals("m4", outcome.diagnostics().get(4).message());
    }

    @Test
    public void scriptFailurePropagatesToTheRuleRunner() {
        XScriptEngine engine = new XScriptEngine(RULE, "warning",
                XScriptCompiler.compile(RULE, "throw 'deliberate';"));
        assertThrows(Exception.class,
                () -> engine.executeMatch(root.descendant("method_declaration").unwrap(), env(), sourceMap),
                "the engine must not swallow script failures (the runner counts them)");
    }

    // ==================== helpers ====================

    private static MetaVarEnv env() {
        return new MetaVarEnv();
    }

    private static XScriptEngine.MatchOutcome run(String script, NodeWrapper node, MetaVarEnv env) {
        XScriptEngine engine = new XScriptEngine(RULE, "warning", XScriptCompiler.compile(RULE, script));
        return engine.executeMatch(node.unwrap(), env, sourceMap);
    }
}
