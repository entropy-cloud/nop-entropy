package io.nop.lint.js;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.testing.SuiteResult;
import io.nop.lint.core.testing.RuleTestRunner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JUnit launcher of the RuleTester for this module's TypeScript suites
 * (design 03 §4.3): discovers every suite under
 * {@link RuleTestRunner#DEFAULT_SUITES_PATH} and turns each one into a
 * dynamic test. This is the item 19 end-to-end proof (Minimum Rules #22):
 * the {@code .rule.yml} (language: TypeScript) loads through the registered
 * VFS pipeline, resolves through the case-insensitive language chain to the
 * ServiceLoader-discovered TypeScript binding, and every assertion runs the
 * real {@code LintEngine} (kind filter → matcher → suppression tail).
 */
public class TestJsRuleSuites {

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
        LanguageRegistry registry = LanguageRegistry.discoverDefaults();
        assertTrue(registry.registeredIds().containsAll(List.of("typescript", "tsx")),
                "this module's bindings must be discoverable: " + registry.registeredIds());

        RuleTestRunner runner = new RuleTestRunner(registry, LintProfile.STANDARD);
        List<SuiteResult> results = runner.runSuites(RuleTestRunner.DEFAULT_SUITES_PATH);
        assertFalse(results.isEmpty(), "the suites root must discover at least one suite");
        assertTrue(results.stream().map(SuiteResult::ruleId).anyMatch("demo/ts-no-console"::equals),
                "the pattern-matching demo suite must be discovered");
        assertTrue(results.stream().map(SuiteResult::ruleId).anyMatch("demo/ts-no-enum"::equals),
                "the kind-matching demo suite must be discovered");
        return results.stream().map(result -> DynamicTest.dynamicTest(result.suitePath(),
                () -> assertTrue(result.isGreen(), result::renderFailures)));
    }
}
