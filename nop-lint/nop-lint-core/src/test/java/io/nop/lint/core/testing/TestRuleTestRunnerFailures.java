package io.nop.lint.core.testing;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.LintProfile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Failure-path proofs for {@link RuleTestRunner} (plan Phase 2, Minimum
 * Rules #24/#25): every broken-suite layout and mismatch is detected with an
 * actionable message — the runner has no green-by-ignoring path. The broken
 * fixtures live under {@code /test/lint/broken-suites}, outside the JUnit
 * launcher's scanned root, so they are exercised only through the runner API.
 */
public class TestRuleTestRunnerFailures {

    private static final String BROKEN = "/test/lint/broken-suites/demo";

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

    private static FixtureFailure singleFailure(String suite) {
        SuiteResult result = runner().runSuite(BROKEN + "/" + suite);
        assertEquals(1, result.failures().size(),
                "suite " + suite + " must report exactly one failure: " + result.renderFailures());
        return result.failures().get(0);
    }

    @Test
    public void expectationCountMismatchIsDetected() {
        FixtureFailure failure = singleFailure("count-mismatch");

        assertTrue(failure.problem().contains("expected 2 diagnostics but got 1"),
                failure.problem());
        assertTrue(failure.problem().contains("actual"), failure.problem());
    }

    /**
     * Meta-test (plan Phase 3): the fixture's {@code .expect} shifts the
     * real line (5) to 99 on purpose — the runner must detect the failure.
     * Proves the assertion layer is not a tautology (防空壳验证面).
     */
    @Test
    public void metaTestRunnerDetectsShiftedLineExpectation() {
        FixtureFailure failure = singleFailure("line-mismatch");

        assertTrue(failure.problem().contains("{line=99"), failure.problem());
        assertTrue(failure.problem().contains("no actual diagnostic matches"),
                failure.problem());
    }

    @Test
    public void messageFragmentMismatchIsDetected() {
        FixtureFailure failure = singleFailure("message-mismatch");

        assertTrue(failure.problem().contains("absent-fragment-xyz"), failure.problem());
    }

    @Test
    public void ruleIdMismatchIsDetected() {
        FixtureFailure failure = singleFailure("ruleid-mismatch");

        assertTrue(failure.problem().contains("demo/other-rule"), failure.problem());
    }

    @Test
    public void missingExpectFileIsDetected() {
        FixtureFailure failure = singleFailure("missing-expect");

        assertTrue(failure.problem().contains("hit.expect"), failure.problem());
        assertTrue(failure.problem().contains("missing its expectation file"), failure.problem());
    }

    @Test
    public void suiteWithoutFixturesIsDetected() {
        FixtureFailure failure = singleFailure("empty-fixtures");

        assertTrue(failure.problem().contains("zero assertions would be vacuous"),
                failure.problem());
    }

    @Test
    public void missingRuleFileIsDetected() {
        FixtureFailure failure = singleFailure("missing-rule-file");

        assertTrue(failure.problem().contains("missing-rule-file.rule.yml"), failure.problem());
    }

    @Test
    public void triggeringValidFixtureIsDetected() {
        FixtureFailure failure = singleFailure("valid-not-clean");

        assertTrue(failure.problem().contains("must produce zero diagnostics but got 1"),
                failure.problem());
    }

    @Test
    public void strayNonJavaFixtureFileIsDetected() {
        FixtureFailure failure = singleFailure("stray-file");

        assertTrue(failure.problem().contains("notes.txt"), failure.problem());
        assertTrue(failure.problem().contains("only *.java fixtures"), failure.problem());
    }

    // ==================== discovery guards ====================

    @Test
    public void fileAtCategoryLevelFailsTheScan() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> runner().runSuites("/test/lint/broken-suites"));
        assertTrue(ex.getMessage().contains("STRAY-CATEGORY-FILE.md"), ex.getMessage());
    }

    @Test
    public void fileAtSuiteLevelFailsTheScan() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> runner().runSuites("/test/lint/broken-suites/stray-level"));
        assertTrue(ex.getMessage().contains("stray.txt"), ex.getMessage());
    }
}
