package io.nop.lint.core.engine;

import io.nop.core.model.object.DynamicObject;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import io.nop.lint.core.semantic.TypeQuerySupport;
import io.nop.lint.core.semantic.TypeResolutionException;
import io.nop.lint.core.semantic.TypeResolver;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The L2 gate and degrade matrix of the engine (roadmap item 20 Phase 2,
 * design 11 §5): a {@code requires: "L2"} rule runs only under the standard
 * profile with a live resolver and a named file — every other combination
 * lands in exactly one observable exit (profile skip, gate degrade, or
 * mid-run degrade), and the resolver is verifiably consulted at run time
 * (query counter, Minimum Rules #23). Degraded rules produce no diagnostics
 * and are never answered from a lower level (roadmap hard constraint).
 */
public class TestL2TypeGate {

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"),
            null);

    private static final String SOURCE =
            "class Demo { boolean m(String x, String y) { return x == y; } }";
    private static final String FILE = "demo/Source.java";

    private final RuleDslParser parser = new RuleDslParser();
    private final LanguageRegistry registry = LanguageRegistry.empty();
    private final FakeResolver resolver = new FakeResolver();

    public TestL2TypeGate() {
        registry.register(JAVA);
    }

    // ==================== run: available resolver really serves L2 ====================

    @Test
    public void typeOfRuleExecutesUnderStandardAndReallyQueries() {
        resolver.available = true;
        resolver.assignable = true;

        LintResult result = lintStandard(rule("demo/typeof-hit"), FILE);

        assertEquals(1, result.diagnostics().size(),
                "the assignability answer holds, so the match reports");
        assertEquals(1, result.stats().getRulesExecuted());
        assertEquals(1, resolver.queries, "the resolver must be invoked at run time (wiring)");
        assertEquals(0, result.stats().getRulesDegraded());
    }

    @Test
    public void failingAssignabilityFiltersTheMatchAndCountsIt() {
        resolver.available = true;
        resolver.assignable = false;

        LintResult result = lintStandard(rule("demo/typeof-miss"), FILE);

        assertEquals(0, result.diagnostics().size(),
                "a failed typeOf must remove the diagnostic");
        assertEquals(1, result.stats().getConstraintFilteredMatches(),
                "the filtered match stays observable through the constraint counter");
        assertEquals(1, resolver.queries);
    }

    // ==================== skip: the fast profile ceiling ====================

    @Test
    public void fastProfileSkipsL2RulesWithoutConsultingTheResolver() {
        resolver.available = true;

        LintEngine engine = new LintEngine(registry, LintProfile.FAST, resolver);
        LintResult result = engine.lint(List.of(rule("demo/typeof-fast")), JAVA, FILE, SOURCE);

        assertEquals(1, result.stats().getRulesSkippedByProfile(),
                "the fast ceiling never serves L2");
        assertTrue(result.stats().getSkippedRuleIds().contains("demo/typeof-fast"));
        assertEquals(0, resolver.queries, "a skipped rule must not reach the resolver");
        assertEquals(0, result.diagnostics().size());
    }

    // ==================== degrade: gate level ====================

    @Test
    public void unavailableResolverDegradesAtTheGate() {
        resolver.available = false;

        LintResult result = lintStandard(rule("demo/typeof-gate"), FILE);

        assertEquals(1, result.stats().getRulesDegraded(), "degrade is explicit, never a plain skip");
        assertTrue(result.stats().getDegradedRuleIds().contains("demo/typeof-gate"));
        assertEquals(0, result.diagnostics().size());
        assertEquals(0, resolver.queries);
        assertEquals(0, result.stats().getRulesExecuted());
    }

    @Test
    public void missingResolverDegradesInsteadOfCrashing() {
        LintEngine engine = new LintEngine(registry, LintProfile.STANDARD);
        LintResult result = engine.lint(List.of(rule("demo/typeof-nores")), JAVA, FILE, SOURCE);

        assertEquals(1, result.stats().getRulesDegraded());
        assertEquals(0, result.diagnostics().size());
    }

    @Test
    public void unnamedSourceDegradesL2Rules() {
        resolver.available = true;

        LintEngine engine = new LintEngine(registry, LintProfile.STANDARD, resolver);
        LintResult result = engine.lint(List.of(rule("demo/typeof-unnamed")), JAVA, SOURCE);

        assertEquals(1, result.stats().getRulesDegraded(),
                "a type query needs a file path; without one the rule degrades");
        assertEquals(0, resolver.queries);
        assertEquals(0, result.diagnostics().size());
    }

    // ==================== degrade: mid-run query failure ====================

    @Test
    public void failedTypeQueryDegradesTheRuleMidRun() {
        resolver.available = true;
        resolver.failure = new TypeResolutionException("deadline exceeded");

        LintResult result = lintStandard(rule("demo/typeof-fail"), FILE);

        assertEquals(1, result.stats().getRulesDegraded(),
                "the engine degrades the rule instead of propagating the failure");
        assertTrue(result.stats().getDegradedRuleIds().contains("demo/typeof-fail"));
        assertEquals(0, result.diagnostics().size(), "degraded rules report nothing");
        assertEquals(1, result.stats().getRulesExecuted(), "the rule did run its matcher");
        assertEquals(1, resolver.queries);
    }

    // ==================== TypeQuerySupport invariants ====================

    @Test
    public void typeQueryWithoutResolverIsAnInvariantBreak() {
        LintTree tree = JAVA.parse(SOURCE);
        TypeQuerySupport support = new TypeQuerySupport(null, FILE, tree.source());

        NopLintException ex = assertThrows(NopLintException.class,
                () -> support.isAssignableTo(tree.root(), "String"));
        assertTrue(ex.getMessage().contains("invariant broken"), ex.getMessage());
    }

    @Test
    public void typeQueryWithoutFileContextIsAnInvariantBreak() {
        LintTree tree = JAVA.parse(SOURCE);
        TypeQuerySupport support = new TypeQuerySupport(resolver, "  ", tree.source());

        NopLintException ex = assertThrows(NopLintException.class,
                () -> support.isAssignableTo(tree.root(), "String"));
        assertTrue(ex.getMessage().contains("invariant broken"), ex.getMessage());
    }

    // ==================== helpers ====================

    private LintResult lintStandard(RuleDslModel rule, String filePath) {
        LintEngine engine = new LintEngine(registry, LintProfile.STANDARD, resolver);
        return engine.lint(List.of(rule), JAVA, filePath, SOURCE);
    }

    /**
     * A rule whose single constraint is {@code typeOf($A is String)} and
     * which declares {@code requires: "L2"} (the parser gate needs it),
     * built through the real parser path.
     */
    private RuleDslModel rule(String id) {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", id);
        model.addProp("severity", "warning");
        model.addProp("message", "msg " + id);
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", "$A == $B");
        model.addProp("rule", rule);
        DynamicObject typeOf = new DynamicObject("typeOf");
        typeOf.addProp("capture", "$A");
        typeOf.addProp("is", "String");
        DynamicObject constraint = new DynamicObject("constraint");
        constraint.addProp("typeOf", typeOf);
        model.addProp("constraints", List.of(constraint));
        model.addProp("requires", "L2");
        return parser.parseRuleModel(model);
    }

    /**
     * The scripted resolver: a query counter (the wiring witness), a
     * controllable availability probe and assignability answer, and an
     * optional injected failure.
     */
    private static final class FakeResolver implements TypeResolver {
        boolean available;
        boolean assignable = true;
        int queries;
        int inits;
        TypeResolutionException failure;

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public void initProject(Path tsConfigPath) {
            inits++;
        }

        @Override
        public boolean isAssignableTo(String filePath, int line, int col, String expectedType) {
            queries++;
            if (failure != null) {
                throw failure;
            }
            return assignable;
        }

        @Override
        public String typeNameAt(String filePath, int line, int col) {
            queries++;
            if (failure != null) {
                throw failure;
            }
            return "FakeType";
        }
    }
}
