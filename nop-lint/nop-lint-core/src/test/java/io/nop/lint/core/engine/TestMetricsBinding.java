package io.nop.lint.core.engine;

import io.nop.core.model.object.DynamicObject;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import io.nop.lint.core.semantic.MetricsResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code metrics} binding gate matrix (roadmap item 32 Phase 2, plan
 * Decisions 2/5/6): a {@code requires: METRICS} rule reaches its script's
 * metrics queries only through a live resolver on a named file under the
 * deep profile — every other combination lands in exactly one observable
 * exit (profile skip, gate degrade, or the compile-time rejection of an
 * undeclared reference), and a query on a non-method node fails loudly
 * through the xscript failure path instead of faking a zero.
 */
public class TestMetricsBinding {

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"),
            null);

    private static final String SOURCE = "class Demo {\n"
            + "  int score(int a) {\n"
            + "    if (a > 0) { return 1; }\n"
            + "    return 0;\n"
            + "  }\n"
            + "}\n";

    private final RuleDslParser parser = new RuleDslParser();
    private final LanguageRegistry registry = LanguageRegistry.empty();
    private final FakeMetricsResolver resolver = new FakeMetricsResolver();

    public TestMetricsBinding() {
        registry.register(JAVA);
    }

    @Test
    public void liveResolverOnNamedFileServesTheBinding() {
        resolver.available = true;
        resolver.cyclomatic = 4;

        LintResult result = lint(LintProfile.DEEP, metricsRule(), "demo/S.java");

        assertEquals(1, result.stats().getRulesExecuted());
        assertEquals(1, result.diagnostics().size(), "the threshold comparison reports");
        assertTrue(result.diagnostics().get(0).message().contains("cyclomatic=4"),
                "the binding handed the resolver's answer to the script");
        assertEquals(0, result.stats().getRulesDegraded());
    }

    @Test
    public void missingResolverDegradesBeforeCompiling() {
        resolver.available = false;

        LintResult result = lint(LintProfile.DEEP, metricsRule(), "demo/S.java");

        assertEquals(1, result.stats().getRulesDegraded());
        assertEquals(0, result.stats().getRulesExecuted());
        assertEquals(0, result.diagnostics().size());
    }

    @Test
    public void unnamedRunDegrades() {
        resolver.available = true;

        LintEngine engine = new LintEngine(registry, LintProfile.DEEP, new DeepResolvers(resolver, null, null, null));
        LintResult result = engine.lint(List.of(metricsRule()), JAVA, SOURCE);

        assertEquals(1, result.stats().getRulesDegraded(),
                "a position-keyed query needs a named file (plan Decision 6)");
        assertEquals(0, result.diagnostics().size());
    }

    @Test
    public void fastAndStandardCeilingsSkipMetricsRules() {
        resolver.available = true;
        for (LintProfile profile : List.of(LintProfile.FAST, LintProfile.STANDARD)) {
            LintResult result = lint(profile, metricsRule(), "demo/S.java");
            assertEquals(1, result.stats().getRulesSkippedByProfile(), profile + " skips by ceiling");
        }
    }

    @Test
    public void undeclaredReferenceFailsCompilation() {
        // the rule references metrics but declares no requires: METRICS —
        // the whitelist variant never registers the identifier (plan
        // Decision 2, fail-closed at compile time)
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", "demo/undeclared-metrics");
        model.addProp("severity", "warning");
        model.addProp("message", "msg");
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("kind", "method_declaration");
        model.addProp("rule", rule);
        model.addProp("xscript", "if (metrics.cyclomatic(node) > 2) { report({ message: 'x' }); }");
        RuleDslModel undeclared = parser.parseRuleModel(model);

        resolver.available = true;
        LintEngine engine = new LintEngine(registry, LintProfile.DEEP, new DeepResolvers(resolver, null, null, null));

        NopLintException ex = assertThrows(NopLintException.class,
                () -> engine.lint(List.of(undeclared), JAVA, "demo/S.java", SOURCE));
        assertTrue(ex.getMessage().contains("demo/undeclared-metrics"), ex.getMessage());
    }

    @Test
    public void nonMethodQueryFailsTheMatchNotTheRun() {
        resolver.available = true;
        // match a method_invocation (not inside a method's metrics surface
        // here: the class has none) — the resolver's fail-closed answer
        // surfaces as one skipped match with the failure counter, never as
        // a faked zero or a crashed run
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", "demo/metrics-on-call");
        model.addProp("severity", "warning");
        model.addProp("message", "msg");
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", "foo.bar()");
        model.addProp("rule", rule);
        model.addProp("requires", "METRICS");
        model.addProp("xscript", "let c = metrics.cyclomatic(node); report({ message: 'c=' + c });");
        RuleDslModel callRule = parser.parseRuleModel(model);

        resolver.available = true;
        resolver.failWithNoEnclosingMethod = true;
        LintEngine engine = new LintEngine(registry, LintProfile.DEEP, new DeepResolvers(resolver, null, null, null));
        LintResult result = engine.lint(List.of(callRule), JAVA, "demo/S.java",
                "class Demo { void m() { foo.bar(); } }");

        assertEquals(1, result.stats().getRulesExecuted());
        assertEquals(1, result.stats().getXscriptFailedMatches(),
                "the fail-closed resolver answer takes the skip-and-count path");
        assertEquals(0, result.diagnostics().size(), "no diagnostic is faked");
        assertEquals(1, result.diagnostics().size() + result.stats().getXscriptFailedMatches());
    }

    private LintResult lint(LintProfile profile, RuleDslModel rule, String filePath) {
        LintEngine engine = new LintEngine(registry, profile, new DeepResolvers(resolver, null, null, null));
        return engine.lint(List.of(rule), JAVA, filePath, SOURCE);
    }

    private RuleDslModel metricsRule() {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", "demo/metrics-complexity");
        model.addProp("severity", "warning");
        model.addProp("message", "msg");
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("kind", "method_declaration");
        model.addProp("rule", rule);
        model.addProp("requires", "METRICS");
        model.addProp("xscript",
                "let c = metrics.cyclomatic(node); if (c > 2) { report({ message: 'cyclomatic=' + c }); }");
        return parser.parseRuleModel(model);
    }

    /**
     * Deterministic resolver: fixed answers, no file IO — the gate and the
     * binding behavior are what these tests pin, not the Java parsing.
     */
    private static final class FakeMetricsResolver implements MetricsResolver {

        boolean available;
        int cyclomatic;
        boolean failWithNoEnclosingMethod;

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public int cyclomatic(String filePath, int line, int col) {
            if (failWithNoEnclosingMethod) {
                throw new NopLintException("no enclosing method for metrics at " + line + ":"
                        + col + " in '" + filePath + "'");
            }
            return cyclomatic;
        }

        @Override
        public int cognitive(String filePath, int line, int col) {
            return 0;
        }

        @Override
        public long npath(String filePath, int line, int col) {
            return 1;
        }
    }
}
