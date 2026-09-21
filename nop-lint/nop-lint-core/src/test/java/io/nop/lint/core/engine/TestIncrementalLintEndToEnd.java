package io.nop.lint.core.engine;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.node.SourceRange;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end proof for the incremental chain (plan Phase 3, Minimum Rules
 * #22): parse → lint (diagnostics) → source edit → incremental reparse via
 * {@code parseIncremental} → re-lint — the diagnostic set must reflect the
 * edit exactly (violations vanish / appear with correct ranges).
 */
public class TestIncrementalLintEndToEnd {

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"),
            null);

    private static RuleDslParser parser;
    private static RuleDslModel dispatchRule;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        parser = new RuleDslParser();
        dispatchRule = parser.loadRuleModel("/test/lint/rules/valid-any.rule.yml");
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static LintEngine engine() {
        LanguageRegistry registry = LanguageRegistry.empty();
        registry.register(JAVA);
        return new LintEngine(registry, LintProfile.FAST);
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static SourceRange rangeOf(String source, String nodeText) {
        for (LintNode node : JAVA.parse(source).root()) {
            if (nodeText.equals(node.text())) {
                return node.range();
            }
        }
        throw new IllegalStateException("reference node not found: " + nodeText);
    }

    @Test
    public void editThatRemovesAViolationClearsItsDiagnostic() {
        String oldSource = "class Demo { void m() { foo.invoke(\"x\"); } }";
        String newSource = "class Demo { void m() { foo.bar(); } }";

        LintTree tree1 = JAVA.parse(oldSource);
        LintResult first = engine().lint(List.of(dispatchRule), JAVA, tree1);
        assertEquals(1, first.diagnostics().size(), "the invoke call must be flagged");
        assertEquals(rangeOf(oldSource, "foo.invoke(\"x\")"), first.diagnostics().get(0).range());

        LintTree tree2 = JAVA.parseIncremental(tree1, bytes(newSource));
        LintResult second = engine().lint(List.of(dispatchRule), JAVA, tree2);
        assertTrue(second.diagnostics().isEmpty(),
                "the old diagnostic must vanish after the incremental reparse, got "
                        + second.diagnostics());
    }

    @Test
    public void editThatIntroducesViolationsReportsThemAtTheNewRanges() {
        String oldSource = "class Demo { void m() { foo.bar(); } }";
        String newSource = "class Demo { void m() { foo.invoke(\"x\"); "
                + "bar.getClass().getMethod(\"y\"); } }";

        LintTree tree1 = JAVA.parse(oldSource);
        LintResult first = engine().lint(List.of(dispatchRule), JAVA, tree1);
        assertTrue(first.diagnostics().isEmpty(), "the clean source must not be flagged");

        LintTree tree2 = JAVA.parseIncremental(tree1, bytes(newSource));
        LintResult second = engine().lint(List.of(dispatchRule), JAVA, tree2);
        assertEquals(2, second.diagnostics().size(), "both new violations must be flagged");
        assertEquals(rangeOf(newSource, "foo.invoke(\"x\")"), second.diagnostics().get(0).range());
        assertEquals(rangeOf(newSource, "bar.getClass().getMethod(\"y\")"),
                second.diagnostics().get(1).range());
        assertEquals("demo/no-dynamic-dispatch", second.diagnostics().get(0).ruleId());
    }

    @Test
    public void incrementalRelintMatchesFullReparsesDiagnosticSet() {
        String oldSource = "class Demo { void m() { foo.invoke(\"x\"); } }";
        String newSource = "class Demo { void m() { bar.getClass().getMethod(\"y\"); "
                + "foo.invoke(\"z\"); } }";

        LintTree tree1 = JAVA.parse(oldSource);
        LintTree incremental = JAVA.parseIncremental(tree1, bytes(newSource));
        LintTree full = JAVA.parse(newSource);

        LintResult viaIncremental = engine().lint(List.of(dispatchRule), JAVA, incremental);
        LintResult viaFull = engine().lint(List.of(dispatchRule), JAVA, full);
        assertEquals(viaFull.diagnostics(), viaIncremental.diagnostics(),
                "re-lint over the incremental tree must equal re-lint over a full reparse");
    }
}
