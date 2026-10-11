package io.nop.lint.core.engine;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import io.nop.lint.core.suppress.SuppressionMeta;
import io.nop.lint.core.testing.JavaBindingTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-end proof of the engine wiring (Minimum Rules #22/#23): the
 * suppression judgment is really invoked by the {@link LintEngine} pipeline
 * (design 03 §1.1) — proven by the differential between the same source with
 * and without a suppression directive — and the suppression counters are
 * observable in {@link LintStats} (design 09 §2).
 */
public class TestSuppressionEngineEndToEnd {

    private static final String DIR = "/test/lint/rules/valid-any.rule.yml";

    private static RuleDslModel rule;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        rule = new RuleDslParser().loadRuleModel(DIR);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static LintEngine engine(LintProfile profile) {
        return new LintEngine(JavaBindingTestSupport.registryWithJava(), profile);
    }

    private static final String UNSUPPRESSED = """
            class Demo {
                void m() {
                    foo.invoke("a");
                    foo.invoke("b");
                }
            }
            """;

    private static final String SUPPRESSED = """
            class Demo {
                void m() {
                    // nop-lint-disable-next-line demo/no-dynamic-dispatch
                    foo.invoke("a");
                    foo.invoke("b");
                }
            }
            """;

    @Test
    public void suppressionChangesTheDiagnosticSetThroughTheRealPipeline() {
        LintResult without = engine(LintProfile.STANDARD).lint(List.of(rule), "Java", UNSUPPRESSED);
        assertEquals(2, without.diagnostics().size(), "both violations fire without suppression");
        assertEquals(0, without.stats().getSuppressedDiagnostics());
        assertEquals(2, without.stats().getDiagnostics());

        LintResult with = engine(LintProfile.STANDARD).lint(List.of(rule), "Java", SUPPRESSED);
        assertEquals(1, with.diagnostics().size(), "exactly the un-suppressed violation survives");
        io.nop.lint.core.node.LineIndex lines = new io.nop.lint.core.node.LineIndex(SUPPRESSED);
        assertEquals(5, lines.startLine(with.diagnostics().get(0).range()),
                "the survivor is line 5 (foo.invoke(\"b\")); line 4 was suppressed");
        assertEquals(1, with.stats().getSuppressedDiagnostics(),
                "the removed diagnostic is counted, not silently dropped");
        assertEquals(1, with.stats().getDiagnostics(),
                "the emitted count equals the result size");
    }

    @Test
    public void fastAndStandardProfilesSuppressIdentically() {
        LintResult fast = engine(LintProfile.FAST).lint(List.of(rule), "Java", SUPPRESSED);
        LintResult standard = engine(LintProfile.STANDARD).lint(List.of(rule), "Java", SUPPRESSED);
        assertEquals(standard.diagnostics(), fast.diagnostics(),
                "design 11 §2: suppression is never profile-trimmed");
        assertEquals(standard.stats().getSuppressedDiagnostics(), fast.stats().getSuppressedDiagnostics());
    }

    @Test
    public void metaDiagnosticsFlowThroughTheEngineAndIntoStats() {
        String source = """
            class Demo {
                void clean() {
                    // nop-lint-disable-line demo/no-dynamic-dispatch
                }
            }
            """;
        LintResult result = engine(LintProfile.STANDARD).lint(List.of(rule), "Java", source);
        assertEquals(1, result.diagnostics().size());
        Diagnostic meta = result.diagnostics().get(0);
        assertEquals(SuppressionMeta.UNUSED_DISABLE_DIRECTIVE, meta.ruleId());
        assertEquals(SuppressionMeta.SEVERITY_UNUSED, meta.severity());
        assertEquals(0, result.stats().getSuppressedDiagnostics());
        assertEquals(1, result.stats().getDiagnostics(), "the meta diagnostic is part of the emitted count");
    }

    @Test
    public void unpairedDisableSuppressesAndReportsThroughTheEngine() {
        String source = """
            // nop-lint-disable demo/no-dynamic-dispatch
            class Demo {
                void m() {
                    foo.invoke("a");
                }
            }
            """;
        LintResult result = engine(LintProfile.STANDARD).lint(List.of(rule), "Java", source);
        assertEquals(1, result.diagnostics().size(),
                "the violation is suppressed (unpaired does not mean inactive); the meta diagnostic stands");
        Diagnostic meta = result.diagnostics().get(0);
        assertEquals(SuppressionMeta.UNPAIRED_DISABLE, meta.ruleId());
        assertEquals(SuppressionMeta.SEVERITY_UNPAIRED, meta.severity());
        assertEquals(1, result.stats().getSuppressedDiagnostics());
        assertEquals(1, result.stats().getDiagnostics());
    }
}
