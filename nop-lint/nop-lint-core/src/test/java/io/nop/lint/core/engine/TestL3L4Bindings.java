package io.nop.lint.core.engine;

import io.nop.core.model.object.DynamicObject;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import io.nop.lint.core.semantic.DataflowResolver;
import io.nop.lint.core.semantic.SemanticResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code semantic}/{@code dataflow} binding gate matrices (roadmap item
 * 34 Phase 2): an {@code requires: L4}/{@code L3} rule reaches its script's
 * queries only through a live resolver on a named file under the deep
 * profile — every other combination lands in exactly one observable exit
 * (profile skip, gate degrade, or the compile-time rejection of an
 * undeclared reference), and the kernel answers flow through the bindings
 * into the reported messages (falsifiable, never fabricated).
 */
public class TestL3L4Bindings {

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"),
            null);

    private static final String SOURCE = "class Demo {\n"
            + "  int m() {\n"
            + "    int value = 1;\n"
            + "    return value;\n"
            + "  }\n"
            + "}\n";

    private final RuleDslParser parser = new RuleDslParser();
    private final LanguageRegistry registry = LanguageRegistry.empty();
    private final FakeSemanticResolver semantic = new FakeSemanticResolver();
    private final FakeDataflowResolver dataflow = new FakeDataflowResolver();

    public TestL3L4Bindings() {
        registry.register(JAVA);
    }

    // ==================== L4 semantic ====================

    @Test
    public void liveSemanticResolverServesTheBinding() {
        semantic.available = true;
        semantic.overridable = true;

        LintResult result = lint(LintProfile.DEEP, semanticRule(), "demo/S.java");

        assertEquals(1, result.stats().getRulesExecuted());
        assertEquals(1, result.diagnostics().size());
        assertTrue(result.diagnostics().get(0).message().contains("overridable"),
                "the semantic answer flowed into the report");
        assertEquals(0, result.stats().getRulesDegraded());
    }

    @Test
    public void missingSemanticResolverDegrades() {
        semantic.available = false;

        LintResult result = lint(LintProfile.DEEP, semanticRule(), "demo/S.java");

        assertEquals(1, result.stats().getRulesDegraded());
        assertEquals(0, result.stats().getRulesExecuted());
        assertEquals(0, result.diagnostics().size());
    }

    @Test
    public void unnamedRunDegradesSemanticRules() {
        semantic.available = true;

        LintEngine engine = new LintEngine(registry, LintProfile.DEEP, deep());
        LintResult result = engine.lint(List.of(semanticRule()), JAVA, SOURCE);

        assertEquals(1, result.stats().getRulesDegraded(),
                "a position-keyed query needs a named file");
        assertEquals(0, result.diagnostics().size());
    }

    @Test
    public void undeclaredSemanticReferenceFailsCompilation() {
        RuleDslModel undeclared = model("demo/undeclared-semantic", "variable_declarator",
                "if (semantic.isOverridable(node)) { report({ message: 'x' }); }", null);

        semantic.available = true;
        LintEngine engine = new LintEngine(registry, LintProfile.DEEP, deep());

        NopLintException ex = assertThrows(NopLintException.class,
                () -> engine.lint(List.of(undeclared), JAVA, "demo/S.java", SOURCE));
        assertTrue(ex.getMessage().contains("demo/undeclared-semantic"), ex.getMessage());
    }

    // ==================== L3 dataflow ====================

    @Test
    public void liveDataflowResolverServesTheBinding() {
        dataflow.available = true;
        dataflow.constant = "3";

        LintResult result = lint(LintProfile.DEEP, dataflowRule(), "demo/S.java");

        assertEquals(1, result.stats().getRulesExecuted());
        assertTrue(result.diagnostics().get(0).message().contains("const=3"),
                "the constant answer flowed into the report");
        assertEquals(0, result.stats().getRulesDegraded());
    }

    @Test
    public void missingDataflowResolverDegrades() {
        dataflow.available = false;

        LintResult result = lint(LintProfile.DEEP, dataflowRule(), "demo/S.java");

        assertEquals(1, result.stats().getRulesDegraded());
        assertEquals(0, result.diagnostics().size());
    }

    @Test
    public void fastAndStandardCeilingsSkipBothFamilies() {
        semantic.available = true;
        dataflow.available = true;
        for (LintProfile profile : List.of(LintProfile.FAST, LintProfile.STANDARD)) {
            assertEquals(1, lint(profile, semanticRule(), "demo/S.java")
                    .stats().getRulesSkippedByProfile(), profile + " skips the L4 rule");
            assertEquals(1, lint(profile, dataflowRule(), "demo/S.java")
                    .stats().getRulesSkippedByProfile(), profile + " skips the L3 rule");
        }
    }

    @Test
    public void undeclaredDataflowReferenceFailsCompilation() {
        RuleDslModel undeclared = model("demo/undeclared-dataflow", "variable_declarator",
                "let c = dataflow.constantValue(node); report({ message: 'x' });", null);

        dataflow.available = true;
        LintEngine engine = new LintEngine(registry, LintProfile.DEEP, deep());

        NopLintException ex = assertThrows(NopLintException.class,
                () -> engine.lint(List.of(undeclared), JAVA, "demo/S.java", SOURCE));
        assertTrue(ex.getMessage().contains("demo/undeclared-dataflow"), ex.getMessage());
    }

    // ==================== helpers ====================

    private DeepResolvers deep() {
        return new DeepResolvers(null, null,
                semantic.available ? semantic : null,
                dataflow.available ? dataflow : null);
    }

    private LintResult lint(LintProfile profile, RuleDslModel rule, String filePath) {
        LintEngine engine = new LintEngine(registry, profile, deep());
        return engine.lint(List.of(rule), JAVA, filePath, SOURCE);
    }

    private RuleDslModel semanticRule() {
        return model("demo/semantic-l4", "method_declaration",
                "if (semantic.isOverridable(node)) { report({ message: 'overridable' }); }",
                "L4");
    }

    private RuleDslModel dataflowRule() {
        return model("demo/dataflow-l3", "variable_declarator",
                "let c = dataflow.constantValue(node);"
                        + " if (c != null) { report({ message: 'const=' + c }); }",
                "L3");
    }

    private RuleDslModel model(String id, String kind, String xscript, String requires) {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", id);
        model.addProp("severity", "warning");
        model.addProp("message", "msg");
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("kind", kind);
        model.addProp("rule", rule);
        if (requires != null) {
            model.addProp("requires", requires);
        }
        model.addProp("xscript", xscript);
        return parser.parseRuleModel(model);
    }

    private static final class FakeSemanticResolver implements SemanticResolver {

        boolean available;
        boolean overridable;

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public boolean implementsInterface(String filePath, int line, int col, String name) {
            throw new UnsupportedOperationException("not queried by these tests");
        }

        @Override
        public boolean isOverridable(String filePath, int line, int col) {
            return overridable;
        }

        @Override
        public boolean isLoggerCall(String filePath, int line, int col) {
            throw new UnsupportedOperationException("not queried by these tests");
        }
    }

    private static final class FakeDataflowResolver implements DataflowResolver {

        boolean available;
        String constant;

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String constantValue(String filePath, int line, int col) {
            return constant;
        }

        @Override
        public long useCount(String filePath, int line, int col) {
            throw new UnsupportedOperationException("not queried by these tests");
        }

        @Override
        public boolean isSelfAssigned(String filePath, int line, int col) {
            throw new UnsupportedOperationException("not queried by these tests");
        }
    }
}
