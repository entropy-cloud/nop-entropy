package io.nop.lint.nop;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.engine.DeepResolvers;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.java.semantic.JavaSemanticResolver;
import io.nop.lint.core.testing.RuleTestRunner;
import io.nop.lint.core.testing.SuiteResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The deep-profile L4 semantic production suites (roadmap item 35, plan
 * 2026-09-24-1400-1 enums #16–#17): the two {@code requires: L4} rules run
 * through the real RuleTester pipeline under {@link LintProfile#DEEP} with
 * the JavaSemanticResolver provider wired explicitly (the items 34
 * demo-suite shape, now at production scale). Suite discovery counting is
 * asserted family-overlapping in {@link TestMetricsRuleSuites}.
 */
public class TestL4RuleSuites {

    private static final Set<String> PRODUCTION_SUITES = Set.of(
            "semantic-no-proxy-hostile-method",
            "semantic-no-nonslf4j-logger-call");

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @TestFactory
    public Stream<DynamicTest> everySemanticSuiteRunsGreen() {
        RuleTestRunner runner = new RuleTestRunner(LanguageRegistry.discoverDefaults(),
                LintProfile.DEEP, null, new DeepResolvers(null, null,
                        new JavaSemanticResolver(), null));
        return PRODUCTION_SUITES.stream().sorted().map(name -> {
            SuiteResult result = runner.runSuite(TestMetricsRuleSuites.DEEP_SUITES_PATH + "/" + name);
            return DynamicTest.dynamicTest(name,
                    () -> assertTrue(result.isGreen(), result::renderFailures));
        });
    }
}
