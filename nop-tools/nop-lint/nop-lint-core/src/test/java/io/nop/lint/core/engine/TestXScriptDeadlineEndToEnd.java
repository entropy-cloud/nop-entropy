package io.nop.lint.core.engine;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end deadline closure (design 07 §4 route A, plan item 15 Phase 3):
 * a {@code .rule.yml} fixture whose xscript never terminates runs through the
 * real pipeline (fixture load → compile → kind filter → matcher → per-match
 * execution under the global deadline wrapper) and the lint call finishes
 * inside the match's budget. The test completing at all is the termination
 * proof — before the wrapper, this script hung the run. The timeout is
 * contained: no diagnostic, no failure count, no rule disable, and other
 * rules' diagnostics are unaffected.
 */
public class TestXScriptDeadlineEndToEnd {

    private static final String DIR = "/test/lint/rules/";

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);

    private static final String HITTING_SRC = """
            class Demo {
                void m() {
                    System.out.println("x");
                }
            }
            """;

    private static RuleDslParser parser;
    private static RuleDslModel deadloopRule;
    private static RuleDslModel reportRule;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        parser = new RuleDslParser();
        deadloopRule = parser.loadRuleModel(DIR + "xscript-deadloop.rule.yml");
        reportRule = parser.loadRuleModel(DIR + "xscript-report.rule.yml");
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static LintEngine engine() {
        LanguageRegistry registry = LanguageRegistry.empty();
        registry.register(JAVA);
        return new LintEngine(registry, LintProfile.STANDARD);
    }

    @Test
    public void deadloopMatchTerminatesWithinItsDeadlineAndStaysContained() {
        LintResult result = engine().lint(List.of(deadloopRule, reportRule), "Java", HITTING_SRC);

        // the run finished: the deadloop match was aborted at its deadline
        assertEquals(1, result.stats().getXscriptTimedOutMatches(),
                "the deadloop match must be aborted and counted as a timeout");
        assertEquals(0, result.stats().getXscriptFailedMatches(),
                "a timeout is not a script failure");
        assertTrue(result.stats().getDisabledRuleIds().isEmpty(),
                "the deadloop rule must not be disabled by the failure-streak path");

        // the timed-out match reports nothing; the other rule is unaffected
        assertEquals(1, result.diagnostics().size());
        assertEquals("demo/xscript-console-in-demo-class", result.diagnostics().get(0).ruleId(),
                "only the healthy rule's diagnostic survives");

        assertEquals(2, result.stats().getXscriptMatchesExecuted(),
                "both xscript rules ran their match");
        assertEquals(1, result.stats().getDiagnostics());
    }

    @Test
    public void deadloopAloneYieldsAResultWithZeroDiagnostics() {
        LintResult result = engine().lint(List.of(deadloopRule), "Java", HITTING_SRC);

        assertTrue(result.diagnostics().isEmpty(),
                "the aborted match is treated as non-matching");
        assertEquals(1, result.stats().getXscriptTimedOutMatches());
        assertEquals(1, result.stats().getXscriptMatchesExecuted());
        assertTrue(result.stats().getDisabledRuleIds().isEmpty());
    }
}
