package io.nop.lint.nop;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.java.semantic.JavaMetricsResolver;
import io.nop.lint.core.testing.RuleTestRunner;
import io.nop.lint.core.testing.SuiteResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The deep-profile metrics demo suite (roadmap item 32, mirrors the item 20
 * {@code l2-suites} shape): a {@code requires: METRICS} rule runs through
 * the real RuleTester pipeline under {@link LintProfile#DEEP} with the
 * ServiceLoader provider class wired explicitly — pattern match →
 * {@code metrics.cyclomatic(node)} (position-keyed JavaParser bridge) →
 * threshold comparison → report. The valid fixture proves the no-report
 * face; the invalid fixture pins the exact computed complexity (4) in its
 * expect entry, so a silent evaluator change cannot pass.
 */
public class TestMetricsDemoSuite {

    private static final String SUITE_PATH = "/test/lint/deep-suites/metrics-complexity";

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void metricsSuiteRunsGreenThroughTheRealPipeline() {
        RuleTestRunner runner = new RuleTestRunner(LanguageRegistry.discoverDefaults(),
                LintProfile.DEEP, null, new JavaMetricsResolver());
        SuiteResult result = runner.runSuite(SUITE_PATH);

        assertTrue(result.isGreen(), result::renderFailures);
    }
}
