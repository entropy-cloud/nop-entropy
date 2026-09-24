package io.nop.lint.nop;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.java.semantic.JavaScopeResolver;
import io.nop.lint.core.testing.RuleTestRunner;
import io.nop.lint.core.testing.SuiteResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The deep-profile scope demo suite (roadmap item 33, mirrors the item 20
 * {@code l2-suites} and item 32 {@code deep-suites/metrics-complexity}
 * shape): a {@code requires: SCOPE} rule runs through the real RuleTester
 * pipeline under {@link LintProfile#DEEP} with the provider class wired
 * explicitly — match a declaration → {@code scope.shadows(node)}
 * (position-keyed JavaParser bridge) → report. The valid fixture proves the
 * no-report face; the invalid fixture pins the shadowing line, so a silent
 * analyzer change cannot pass.
 */
public class TestScopeDemoSuite {

    private static final String SUITE_PATH = "/test/lint/deep-suites/scope-shadow";

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void scopeSuiteRunsGreenThroughTheRealPipeline() {
        RuleTestRunner runner = new RuleTestRunner(LanguageRegistry.discoverDefaults(),
                LintProfile.DEEP, null, null, new JavaScopeResolver());
        SuiteResult result = runner.runSuite(SUITE_PATH);

        assertTrue(result.isGreen(), result::renderFailures);
    }
}
