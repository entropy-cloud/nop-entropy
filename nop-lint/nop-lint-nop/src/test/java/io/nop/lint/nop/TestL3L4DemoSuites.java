package io.nop.lint.nop;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.engine.DeepResolvers;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.java.semantic.JavaDataflowResolver;
import io.nop.lint.java.semantic.JavaSemanticResolver;
import io.nop.lint.core.testing.RuleTestRunner;
import io.nop.lint.core.testing.SuiteResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The deep-profile L3/L4 demo suites (roadmap item 34, the deep-suites
 * shape of items 32/33): {@code requires: L4} and {@code requires: L3}
 * rules run through the real RuleTester pipeline under
 * {@link LintProfile#DEEP} with the provider classes wired explicitly —
 * match a declaration → {@code semantic.isOverridable(node)} / {@code
 * dataflow.constantValue(node)} (position-keyed JavaParser bridges) →
 * threshold/null judgment → report. The valid fixtures prove the no-report
 * face; the invalid fixtures pin the exact judgment results.
 */
public class TestL3L4DemoSuites {

    private static final String SEMANTIC_SUITE = "/test/lint/deep-suites/semantic-l4";
    private static final String DATAFLOW_SUITE = "/test/lint/deep-suites/dataflow-l3";

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void semanticSuiteRunsGreenThroughTheRealPipeline() {
        RuleTestRunner runner = new RuleTestRunner(LanguageRegistry.discoverDefaults(),
                LintProfile.DEEP, null, new DeepResolvers(null, null,
                        new JavaSemanticResolver(), null));
        SuiteResult result = runner.runSuite(SEMANTIC_SUITE);

        assertTrue(result.isGreen(), result::renderFailures);
    }

    @Test
    public void dataflowSuiteRunsGreenThroughTheRealPipeline() {
        RuleTestRunner runner = new RuleTestRunner(LanguageRegistry.discoverDefaults(),
                LintProfile.DEEP, null, new DeepResolvers(null, null, null,
                        new JavaDataflowResolver()));
        SuiteResult result = runner.runSuite(DATAFLOW_SUITE);

        assertTrue(result.isGreen(), result::renderFailures);
    }
}
