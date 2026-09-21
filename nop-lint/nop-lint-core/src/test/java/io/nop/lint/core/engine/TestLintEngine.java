package io.nop.lint.core.engine;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.model.object.DynamicObject;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.SourceRange;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end proofs for {@link LintEngine} (plan Phase 3): the complete
 * path {@code .rule.yml} fixture → {@link RuleDslParser#loadRuleModel} →
 * {@code lint} → asserted {@link Diagnostic}s, plus the profile contract
 * (fast/standard v1 equivalence, requires-gating into
 * {@code skippedByProfile} with observable rule ids) and the fail-closed
 * language resolution.
 */
public class TestLintEngine {

    private static final String DIR = "/test/lint/rules/";

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);

    private static final String HITTING_SRC =
            "class Demo { void m() { foo.invoke(\"x\"); bar.getClass().getMethod(\"y\"); } }";

    private static final String CLEAN_SRC = "class Demo { void m() { foo.bar(); } }";

    private static RuleDslParser parser;
    private static RuleDslModel validAny;
    private static RuleDslModel validSimple;
    private static RuleDslModel validFull;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        parser = new RuleDslParser();
        validAny = parser.loadRuleModel(DIR + "valid-any.rule.yml");
        validSimple = parser.loadRuleModel(DIR + "valid-simple.rule.yml");
        validFull = parser.loadRuleModel(DIR + "valid-full.rule.yml");
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static LintEngine engine(LintProfile profile) {
        LanguageRegistry registry = LanguageRegistry.empty();
        registry.register(JAVA);
        return new LintEngine(registry, profile);
    }

    // ==================== end to end: fixture -> diagnostics ====================

    @Test
    public void endToEndFixtureToDiagnosticsOnHittingSource() {
        LintResult result = engine(LintProfile.FAST).lint(List.of(validAny), "Java", HITTING_SRC);

        assertEquals(2, result.diagnostics().size(), "both any branches must fire");
        assertEquals(2, result.stats().getDiagnostics());
        assertEquals(1, result.stats().getRulesLoaded());
        assertEquals(1, result.stats().getRulesExecuted());
        assertEquals(0, result.stats().getRulesKindFiltered());
        assertEquals(0, result.stats().getRulesSkippedByProfile());

        Diagnostic first = result.diagnostics().get(0);
        assertEquals("demo/no-dynamic-dispatch", first.ruleId());
        assertEquals("error", first.severity());
        assertEquals("Avoid unguarded dynamic method dispatch", first.message());
        assertEquals(rangeOf(HITTING_SRC, "foo.invoke(\"x\")"), first.range());

        Diagnostic second = result.diagnostics().get(1);
        assertEquals("demo/no-dynamic-dispatch", second.ruleId());
        assertEquals(rangeOf(HITTING_SRC, "bar.getClass().getMethod(\"y\")"), second.range());
    }

    @Test
    public void endToEndExecutedButNotMatchingYieldsNoDiagnostics() {
        LintResult result = engine(LintProfile.FAST).lint(List.of(validAny), "Java", CLEAN_SRC);

        assertTrue(result.diagnostics().isEmpty());
        assertEquals(1, result.stats().getRulesExecuted(),
                "the rule must run (its target kinds occur) even when nothing matches");
        assertEquals(0, result.stats().getDiagnostics());
    }

    @Test
    public void endToEndKindFilterCountsOnKindlessSource() {
        LintResult result = engine(LintProfile.FAST).lint(List.of(validSimple), "Java",
                "class Demo { int x = 1; }");

        assertTrue(result.diagnostics().isEmpty());
        assertEquals(1, result.stats().getRulesKindFiltered(),
                "valid-simple targets method_invocation; the source has none");
        assertEquals(0, result.stats().getRulesExecuted());
    }

    // ==================== profiles & requires ====================

    @Test
    public void fastAndStandardBehaveIdenticallyInV1() {
        List<RuleDslModel> rules = List.of(validAny, validFull);

        LintResult fast = engine(LintProfile.FAST).lint(rules, "java", HITTING_SRC);
        LintResult standard = engine(LintProfile.STANDARD).lint(rules, "java", HITTING_SRC);

        assertEquals(fast.diagnostics(), standard.diagnostics(),
                "v1 capability sets are identical, so results must be too");
        assertEquals(fast.stats().getRulesLoaded(), standard.stats().getRulesLoaded());
        assertEquals(fast.stats().getRulesExecuted(), standard.stats().getRulesExecuted());
        assertEquals(fast.stats().getRulesSkippedByProfile(), standard.stats().getRulesSkippedByProfile());
        assertEquals(1, fast.stats().getRulesExecuted());
        assertEquals(1, fast.stats().getRulesSkippedByProfile(), "valid-full requires L2 -> skipped");
    }

    @Test
    public void requiresBeyondProfileSkipsWithObservableId() {
        for (LintProfile profile : LintProfile.values()) {
            LintResult result = engine(profile).lint(List.of(validFull), "java", HITTING_SRC);

            assertTrue(result.diagnostics().isEmpty(),
                    profile + " must not run an L2 rule");
            assertEquals(1, result.stats().getRulesSkippedByProfile());
            assertEquals(List.of("demo/no-file-stream"), result.stats().getSkippedRuleIds(),
                    profile + " must record the skipped rule id (no silent skip)");
            assertEquals(0, result.stats().getRulesExecuted());
        }
    }

    @Test
    public void requiresCheckRunsBeforeXscriptCompilation() {
        // valid-full carries xscript, which CompiledRule rejects at compile
        // time; reaching a diagnostic-free, skipped-only result proves the
        // requires gate short-circuits before compilation instead.
        LintResult result = engine(LintProfile.FAST).lint(List.of(validFull), "java", HITTING_SRC);

        assertEquals(1, result.stats().getRulesSkippedByProfile(),
                "the rule must be accounted as profile-skipped, not compile-rejected");
    }

    @Test
    public void unknownRequirementTokenIsUnsatisfiable() {
        RuleDslModel dataflowRule = parser.parseRuleModel(ruleWithRequires("demo/flow", "dataflow"));

        LintResult result = engine(LintProfile.STANDARD).lint(List.of(dataflowRule), "java", HITTING_SRC);

        assertTrue(result.diagnostics().isEmpty());
        assertEquals(1, result.stats().getRulesSkippedByProfile());
        assertEquals(List.of("demo/flow"), result.stats().getSkippedRuleIds());
    }

    @Test
    public void satisfiedRequirementRunsTheRule() {
        RuleDslModel l1Rule = parser.parseRuleModel(ruleWithRequires("demo/l1", "L1"));

        LintResult result = engine(LintProfile.FAST).lint(List.of(l1Rule), "java", HITTING_SRC);

        assertEquals(1, result.stats().getRulesExecuted(), "L1 is in every v1 profile");
        assertEquals(0, result.stats().getRulesSkippedByProfile());
    }

    // ==================== fail-closed language resolution ====================

    @Test
    public void unknownLanguageIdFailsClosed() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> engine(LintProfile.FAST).lint(List.of(validAny), "cobol", HITTING_SRC));
        assertTrue(ex.getMessage().contains("cobol"), "message must carry the original id: "
                + ex.getMessage());
    }

    @Test
    public void languageResolutionNormalizesCase() {
        LintResult upper = engine(LintProfile.FAST).lint(List.of(validAny), "Java", HITTING_SRC);
        LintResult lower = engine(LintProfile.FAST).lint(List.of(validAny), "java", HITTING_SRC);

        assertEquals(upper.diagnostics(), lower.diagnostics(),
                "fixture 'Java' and binding 'java' must resolve to the same run");
    }

    // ==================== helpers ====================

    private static SourceRange rangeOf(String source, String nodeText) {
        for (LintNode node : JAVA.parse(source).root()) {
            if (nodeText.equals(node.text())) {
                return node.range();
            }
        }
        throw new IllegalStateException("reference node not found: " + nodeText);
    }

    private DynamicObject ruleWithRequires(String id, String requires) {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", id);
        model.addProp("language", "Java");
        model.addProp("severity", "warning");
        model.addProp("message", "msg " + id);
        model.addProp("requires", requires);
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", "foo.bar()");
        model.addProp("rule", rule);
        return model;
    }
}
