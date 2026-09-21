package io.nop.lint.core.engine;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.model.object.DynamicObject;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Timeout and depth semantics through the engine (design 07 §3, plan item 15
 * Phase 2): a deadline expiry is counted as its own match outcome — separate
 * from script failures, never feeding the consecutive-failure disable path,
 * warned on when a rule's timeout rate exceeds 1% — while the depth-32 guard
 * aborts via the ordinary script-failure path. Every path is observable
 * through {@link LintStats} with no silent branch.
 */
public class TestXScriptTimeoutSemantics {

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);

    /**
     * Non-terminating and provably non-static (the condition reads a local):
     * the deadline, not the loop bound, is what ends the match.
     */
    private static final String DEADLOOP = """
            let i = 0;
            while (i >= 0) {
              i = i + 1;
            }
            """;

    private static final String HITTING_SRC = """
            class Demo {
                void m() {
                    System.out.println("x");
                }
            }
            """;

    private static RuleDslParser parser;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        parser = new RuleDslParser();
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

    // ==================== budget resolution matrix ====================

    @Test
    public void budgetResolutionMatrix() {
        assertEquals(100, LintProfile.STANDARD.xscriptBudgetMs(100),
                "standard honors the default 100ms rule value");
        assertEquals(250, LintProfile.STANDARD.xscriptBudgetMs(250),
                "standard honors a declared rule value as-is");
        assertEquals(1000, LintProfile.STANDARD.xscriptBudgetMs(1000),
                "standard honors the cap value as-is");
        assertEquals(20, LintProfile.FAST.xscriptBudgetMs(100),
                "fast tightens the default to its 20ms cap (design 11 §7)");
        assertEquals(20, LintProfile.FAST.xscriptBudgetMs(250),
                "fast tightens a larger declared value to its cap");
        assertEquals(20, LintProfile.FAST.xscriptBudgetMs(20),
                "fast keeps a value exactly at its cap");
        assertEquals(10, LintProfile.FAST.xscriptBudgetMs(10),
                "fast keeps values below its cap");
        assertEquals(1, LintProfile.FAST.xscriptBudgetMs(1),
                "the parser's 1ms floor survives fast scaling");
    }

    // ==================== timeout counted separately from failure ====================

    @Test
    public void timeoutIsCountedSeparatelyFromScriptFailure() {
        RuleDslModel rule = xscriptRule("demo/timeout-separate", 1, DEADLOOP);
        LintResult result = engine(LintProfile.STANDARD).lint(List.of(rule), "Java", HITTING_SRC);

        assertTrue(result.diagnostics().isEmpty(), "a timed-out match reports nothing (non-match)");
        assertEquals(1, result.stats().getXscriptMatchesExecuted());
        assertEquals(1, result.stats().getXscriptTimedOutMatches(), "the timeout has its own counter");
        assertEquals(0, result.stats().getXscriptFailedMatches(),
                "a timeout must not be counted as a script failure");
        assertTrue(result.stats().getDisabledRuleIds().isEmpty());
    }

    @Test
    public void timeoutsNeverTriggerTheConsecutiveFailureDisable() {
        RuleDslModel rule = xscriptRule("demo/timeout-no-disable", 1, DEADLOOP);
        StringBuilder src = new StringBuilder("class Demo {\n");
        for (int i = 0; i < 55; i++) {
            src.append("    void m").append(i).append("() { System.out.println(").append(i)
                    .append("); }\n");
        }
        src.append("}\n");

        LintResult result = engine(LintProfile.STANDARD).lint(List.of(rule), "Java", src.toString());

        // 55 timeouts processed: 51 consecutive *failures* would have disabled
        // the rule at match 50, so full processing proves the paths are separate
        assertEquals(55, result.stats().getXscriptTimedOutMatches());
        assertEquals(55, result.stats().getXscriptMatchesExecuted());
        assertEquals(0, result.stats().getXscriptFailedMatches());
        assertTrue(result.stats().getDisabledRuleIds().isEmpty(),
                "the timeout path must not feed the consecutive-failure disable");
        assertTrue(result.diagnostics().isEmpty());
    }

    // ==================== timeout-rate warning boundary ====================

    @Test
    public void timeoutRateWarningBoundary() {
        assertFalse(RuleSetRunner.shouldWarnTimeoutRate(0, 0), "no matches: nothing to warn about");
        assertTrue(RuleSetRunner.shouldWarnTimeoutRate(1, 1), "the only match timed out: warn");
        assertTrue(RuleSetRunner.shouldWarnTimeoutRate(3, 1), "1/3 = 33%: warn");
        assertTrue(RuleSetRunner.shouldWarnTimeoutRate(99, 1), "1/99 is just above 1%: warn");
        assertFalse(RuleSetRunner.shouldWarnTimeoutRate(100, 1), "exactly 1% is not above 1%: no warn");
        assertFalse(RuleSetRunner.shouldWarnTimeoutRate(1000, 10), "exactly 1%: no warn");
        assertTrue(RuleSetRunner.shouldWarnTimeoutRate(1000, 11), "1.1%: warn");
    }

    @Test
    public void timeoutRateDrivesTheWarningDecisionInTheLiveRun() {
        RuleDslModel rule = xscriptRule("demo/timeout-rate", 1, DEADLOOP);
        StringBuilder src = new StringBuilder("class Demo {\n");
        for (int i = 0; i < 3; i++) {
            src.append("    void m").append(i).append("() { System.out.println(").append(i)
                    .append("); }\n");
        }
        src.append("}\n");

        LintResult result = engine(LintProfile.STANDARD).lint(List.of(rule), "Java", src.toString());

        assertEquals(3, result.stats().getXscriptTimedOutMatches());
        assertTrue(RuleSetRunner.shouldWarnTimeoutRate(result.stats().getXscriptMatchesExecuted(),
                result.stats().getXscriptTimedOutMatches()),
                "an all-timeout rule must trip the live warning decision");
    }

    // ==================== depth guard: the script-failure path ====================

    @Test
    public void depthOverrunAbortsViaTheFailurePathAndCounts() {
        RuleDslModel rule = xscriptRule("demo/depth-overrun", 1000,
                "let d = node.kind()" + "+ 1".repeat(40));
        LintResult result = engine(LintProfile.STANDARD).lint(List.of(rule), "Java", HITTING_SRC);

        assertTrue(result.diagnostics().isEmpty());
        assertEquals(1, result.stats().getXscriptFailedMatches(),
                "a depth abort is a script failure, not a timeout");
        assertEquals(0, result.stats().getXscriptTimedOutMatches());
        assertTrue(result.stats().getDisabledRuleIds().isEmpty(),
                "one depth abort is below the consecutive-failure threshold");
    }

    @Test
    public void shallowScriptRunsNormallyUnderItsDeadline() {
        RuleDslModel rule = xscriptRule("demo/deadline-hit", 1000, """
                report({ message: 'reported within budget' });
                """);
        LintResult result = engine(LintProfile.STANDARD).lint(List.of(rule), "Java", HITTING_SRC);

        assertEquals(1, result.diagnostics().size());
        assertEquals(0, result.stats().getXscriptTimedOutMatches());
        assertEquals(0, result.stats().getXscriptFailedMatches());
        assertEquals(1, result.stats().getXscriptMatchesExecuted());
    }

    @Test
    public void fastProfileBudgetStillEnforcesTheDeadline() {
        RuleDslModel rule = xscriptRule("demo/timeout-fast", 100, DEADLOOP);
        LintResult result = engine(LintProfile.FAST).lint(List.of(rule), "Java", HITTING_SRC);

        // fast scales the 100ms rule budget to 20ms; the deadloop cannot
        // finish, so the run must still end in a counted timeout
        assertEquals(1, result.stats().getXscriptTimedOutMatches());
        assertEquals(0, result.stats().getXscriptFailedMatches());
    }

    // ==================== helpers ====================

    private static RuleDslModel xscriptRule(String id, int timeoutMs, String script) {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", id);
        model.addProp("language", "Java");
        model.addProp("severity", "warning");
        model.addProp("message", "msg " + id);
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", "System.out.println($M)");
        model.addProp("rule", rule);
        model.addProp("xscript", script);
        model.addProp("xscriptTimeoutMs", timeoutMs);
        return parser.parseRuleModel(model);
    }
}
