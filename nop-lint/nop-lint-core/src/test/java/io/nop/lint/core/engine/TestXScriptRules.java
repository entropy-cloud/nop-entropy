package io.nop.lint.core.engine;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.model.object.DynamicObject;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end xscript pipeline proofs (design 03 §1.1 order, design 07 §1/§3):
 * {@code .rule.yml} fixture → rule compile (whitelist) → kind filter →
 * matcher → per-match script execution → {@code report()} → asserted
 * {@link Diagnostic}s, plus the three resource paths (hit / filter /
 * failure-skip), the consecutive-failure rule disable, and the diagnostic
 * cap — every path observable through {@link LintStats}.
 */
public class TestXScriptRules {

    private static final String DIR = "/test/lint/rules/";

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);

    private static RuleDslParser parser;
    private static RuleDslModel reportRule;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        parser = new RuleDslParser();
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

    private static final String HITTING_SRC = """
            class Demo {
                void m() {
                    System.out.println("x");
                }
            }
            """;

    private static final String CLEAN_SRC = """
            class Other {
                void m() {
                    System.out.println("x");
                }
            }
            """;

    // ==================== end to end: fixture -> script -> diagnostic ====================

    @Test
    public void fixtureScriptReportsDiagnosticThroughTheWholePipeline() {
        LintResult result = engine().lint(List.of(reportRule), "Java", HITTING_SRC);

        assertEquals(1, result.diagnostics().size(), "the script's conditional report fires once");
        Diagnostic diagnostic = result.diagnostics().get(0);
        assertEquals("demo/xscript-console-in-demo-class", diagnostic.ruleId());
        assertEquals("error", diagnostic.severity(), "the script overrides the rule severity");
        assertEquals("no console logging in Demo", diagnostic.message(),
                "the message comes from the script's string concatenation");
        assertEquals(rangeOf(HITTING_SRC, "System.out.println(\"x\")"), diagnostic.range(),
                "the default target is the matched println node");

        assertEquals(1, result.stats().getRulesExecuted());
        assertEquals(1, result.stats().getXscriptMatchesExecuted(), "the script ran for the one match");
        assertEquals(1, result.stats().getDiagnostics());
    }

    @Test
    public void scriptWithoutReportFiltersTheMatch() {
        LintResult result = engine().lint(List.of(reportRule), "Java", CLEAN_SRC);

        assertTrue(result.diagnostics().isEmpty(),
                "the ancestor check fails, the script reports nothing: xscript filters");
        assertEquals(1, result.stats().getRulesExecuted());
        assertEquals(1, result.stats().getXscriptMatchesExecuted(),
                "the matcher hit, the script ran, and chose not to report");
        assertEquals(0, result.stats().getDiagnostics());
    }

    @Test
    public void kindFilterStillRunsBeforeXscriptExecution() {
        LintResult result = engine().lint(List.of(reportRule), "Java", "class Demo { int x = 1; }");

        assertTrue(result.diagnostics().isEmpty());
        assertEquals(1, result.stats().getRulesKindFiltered());
        assertEquals(0, result.stats().getXscriptMatchesExecuted(),
                "pipeline order (design 03 §1.1): the kind filter short-circuits before matching/script");
    }

    // ==================== resource path 1: script failure skips the match ====================

    @Test
    public void scriptFailureSkipsTheMatchAndCountsTheWarning() {
        RuleDslModel rule = xscriptRule("demo/xscript-always-throws", "throw 'deliberate';");
        LintResult result = engine().lint(List.of(rule), "Java", HITTING_SRC);

        assertTrue(result.diagnostics().isEmpty(), "the failed match yields no diagnostic");
        assertEquals(1, result.stats().getXscriptMatchesExecuted());
        assertEquals(1, result.stats().getXscriptFailedMatches(), "the skip is counted, not silent");
        assertTrue(result.stats().getDisabledRuleIds().isEmpty(),
                "one failure is below the consecutive-failure threshold");
    }

    // ==================== resource path 2: consecutive failures disable the rule ====================

    @Test
    public void consecutiveFailuresDisableTheRuleForTheRun() {
        RuleDslModel rule = xscriptRule("demo/xscript-disable", "throw 'deliberate';");
        StringBuilder src = new StringBuilder("class Demo {\n");
        for (int i = 0; i < 51; i++) {
            src.append("    void m").append(i).append("() { System.out.println(").append(i)
                    .append("); }\n");
        }
        src.append("}\n");

        LintResult result = engine().lint(List.of(rule), "Java", src.toString());

        assertEquals(50, result.stats().getXscriptMatchesExecuted(),
                "execution stops at the failure threshold; the remaining match never runs");
        assertEquals(50, result.stats().getXscriptFailedMatches());
        assertEquals(List.of("demo/xscript-disable"), result.stats().getDisabledRuleIds(),
                "the disable is reported with the rule id");
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    public void failureStreakResetsAfterASuccessfulMatch() {
        // every third script throws: the streak never reaches the disable
        // threshold, so all 6 matches execute
        RuleDslModel rule = xscriptRule("demo/xscript-streak", """
                if (captures.M.text() == '3' || captures.M.text() == '6') {
                  throw 'deliberate';
                }
                report({ message: 'ok ' + captures.M.text() });
                """);
        StringBuilder src = new StringBuilder("class Demo {\n");
        for (int i = 1; i <= 6; i++) {
            src.append("    void m").append(i).append("() { System.out.println(").append(i)
                    .append("); }\n");
        }
        src.append("}\n");

        LintResult result = engine().lint(List.of(rule), "Java", src.toString());

        assertEquals(6, result.stats().getXscriptMatchesExecuted(), "no disable: the streak kept resetting");
        assertEquals(2, result.stats().getXscriptFailedMatches());
        assertTrue(result.stats().getDisabledRuleIds().isEmpty());
        assertEquals(4, result.diagnostics().size());
        assertEquals("ok 1", result.diagnostics().get(0).message());
    }

    // ==================== resource path 3: per-match diagnostic cap ====================

    @Test
    public void diagnosticCapAbortsTheScriptAndCountsTheMatch() {
        RuleDslModel rule = xscriptRule("demo/xscript-cap", """
                let i = 0;
                while (i < 150) {
                  report({ message: 'm' + i });
                  i = i + 1;
                }
                """);
        LintResult result = engine().lint(List.of(rule), "Java", HITTING_SRC);

        assertEquals(100, result.diagnostics().size(), "the cap bounds the match's diagnostics");
        assertEquals(1, result.stats().getXscriptCappedMatches(), "the cap abort is counted");
        assertEquals(0, result.stats().getXscriptFailedMatches(),
                "a cap abort is a resource stop, not a script failure");
    }

    // ==================== declType through the engine ====================

    @Test
    public void declTypeInjectionWorksThroughTheEngine() {
        RuleDslModel rule = parser.parseRuleModel(patternModelWithScript(
                "demo/xscript-decl-type", "String $N = $V;", """
                if (declType(node) == 'String') {
                  report({ message: 'raw String declaration' });
                }
                """));
        LintResult result = engine().lint(List.of(rule), "Java",
                "class Demo { void m() { String name = demo(); } }");

        assertEquals(1, result.diagnostics().size(),
                "the L1 DeclTypeResolver binding reaches the script through the engine");
        assertEquals("raw String declaration", result.diagnostics().get(0).message());
    }

    // ==================== helpers ====================

    private static RuleDslModel xscriptRule(String id, String script) {
        return parser.parseRuleModel(patternModelWithScript(id, "System.out.println($M)", script));
    }

    private static DynamicObject patternModelWithScript(String id, String pattern, String script) {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", id);
        model.addProp("language", "Java");
        model.addProp("severity", "warning");
        model.addProp("message", "default " + id);
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", pattern);
        model.addProp("rule", rule);
        model.addProp("xscript", script);
        return model;
    }

    private static SourceRange rangeOf(String source, String nodeText) {
        for (LintNode node : JAVA.parse(source).root()) {
            if (nodeText.equals(node.text())) {
                return node.range();
            }
        }
        throw new IllegalStateException("reference node not found: " + nodeText);
    }
}
