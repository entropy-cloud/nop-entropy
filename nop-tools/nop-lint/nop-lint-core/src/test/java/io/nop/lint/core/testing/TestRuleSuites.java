package io.nop.lint.core.testing;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.engine.LintProfile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JUnit launcher of the RuleTester (design 03 §4.3): discovers every
 * suite under {@link RuleTestRunner#DEFAULT_SUITES_PATH} and turns each one
 * into a dynamic test, so suites added as resources run under
 * {@code ./mvnw test} without touching Java code. A suite is green only when
 * its rule loads through the registered {@code rule.yml} pipeline, every
 * {@code valid/*.java} fixture is diagnostic-free, and every
 * {@code invalid/*.java} fixture matches its {@code .expect} file entry by
 * entry.
 */
public class TestRuleSuites {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @TestFactory
    public Stream<DynamicTest> everyDiscoveredSuiteRunsGreen() {
        RuleTestRunner runner = new RuleTestRunner(JavaBindingTestSupport.registryWithJava(),
                LintProfile.STANDARD);
        List<SuiteResult> results = runner.runSuites(RuleTestRunner.DEFAULT_SUITES_PATH);
        assertFalse(results.isEmpty(), "the suites root must discover at least one suite");
        return results.stream().map(result -> DynamicTest.dynamicTest(result.suitePath(),
                () -> assertTrue(result.isGreen(), result::renderFailures)));
    }
}
