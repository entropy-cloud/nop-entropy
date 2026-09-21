package io.nop.lint.core.testing;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.engine.LintResult;
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
 * Green-path proofs for {@link RuleTestRunner} (plan Phase 2): suite
 * discovery over the conventional VFS layout, per-suite results, and the
 * wiring verification (Minimum Rules #23) — the runner really loads rules
 * through {@link RuleDslParser#loadRuleModel} and really runs
 * {@link LintEngine#lint} on real parsed trees, proven by cross-checking the
 * runner's green verdict against the engine's raw diagnostics on the same
 * fixture.
 */
public class TestRuleTestRunner {

    private static final String SUITES = "/test/lint/suites";

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static RuleTestRunner runner() {
        return new RuleTestRunner(JavaBindingTestSupport.registryWithJava(), LintProfile.STANDARD);
    }

    @Test
    public void allDiscoveredSuitesAreGreen() {
        List<SuiteResult> results = runner().runSuites(RuleTestRunner.DEFAULT_SUITES_PATH);

        assertEquals(2, results.size(), "both sample suites must be discovered");
        for (SuiteResult result : results) {
            assertTrue(result.isGreen(), result::renderFailures);
        }
        assertTrue(results.stream().anyMatch(r -> "demo/no-console".equals(r.ruleId())));
        assertTrue(results.stream().anyMatch(r -> "demo/no-dynamic-dispatch".equals(r.ruleId())));
    }

    @Test
    public void singleSuiteRunCarriesRuleIdAndStaysGreen() {
        SuiteResult result = runner().runSuite(SUITES + "/demo/no-console");

        assertEquals(SUITES + "/demo/no-console", result.suitePath());
        assertEquals("demo/no-console", result.ruleId());
        assertTrue(result.isGreen(), result::renderFailures);
    }

    @Test
    public void crossLineNodeYieldsDistinctStartAndEndLine() {
        SuiteResult result = runner().runSuite(SUITES + "/demo/no-dynamic-dispatch");

        assertTrue(result.isGreen(), result::renderFailures);
    }

    /**
     * Wiring verification: the engine's raw output on the invalid fixture
     * really contains one diagnostic whose byte range maps to line 6 — the
     * exact fact the suite's {@code .expect} file asserts. A runner that
     * compared against nothing could not stay green here.
     */
    @Test
    public void engineOutputOnInvalidFixtureMatchesTheSuiteExpectation() {
        RuleDslModel rule = new RuleDslParser().loadRuleModel(SUITES + "/demo/no-console/no-console.rule.yml");
        LanguageRegistry registry = JavaBindingTestSupport.registryWithJava();
        IResource fixture = VirtualFileSystem.instance()
                .getResource(SUITES + "/demo/no-console/invalid/basic.java");
        String source = fixture.readText();

        LintResult result = new LintEngine(registry, LintProfile.STANDARD)
                .lint(List.of(rule), rule.getLanguage(), source);

        assertEquals(1, result.diagnostics().size());
        Diagnostic diagnostic = result.diagnostics().get(0);
        LineIndex lines = new LineIndex(source);
        assertEquals(6, lines.startLine(diagnostic.range()));
        assertEquals(6, lines.endLine(diagnostic.range()));
        assertEquals("demo/no-console", diagnostic.ruleId());
        assertTrue(diagnostic.message().contains("System.out.println"));
    }

    @Test
    public void lineIndexMapsByteOffsetsToOneBasedLines() {
        LineIndex lines = new LineIndex("ab\ncd\n\nef");

        assertEquals(1, lines.lineOfByte(0));
        assertEquals(1, lines.lineOfByte(2));
        assertEquals(2, lines.lineOfByte(3));
        assertEquals(2, lines.lineOfByte(5));
        assertEquals(3, lines.lineOfByte(6));
        assertEquals(4, lines.lineOfByte(7));
        assertEquals(4, lines.lineOfByte(8));
    }

    @Test
    public void lineIndexSurvivesMultiByteCharacters() {
        LineIndex lines = new LineIndex("aé\nb");

        assertEquals(1, lines.lineOfByte(3), "the newline of line 1 sits behind the 2-byte é");
        assertEquals(2, lines.lineOfByte(4));
    }

    @Test
    public void emptySuitesPathFailsClosed() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> runner().runSuites("/test/lint/definitely-not-there"));
        assertTrue(ex.getMessage().contains("/test/lint/definitely-not-there"), ex.getMessage());
    }
}
