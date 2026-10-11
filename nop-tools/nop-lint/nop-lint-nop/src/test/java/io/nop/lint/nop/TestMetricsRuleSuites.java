package io.nop.lint.nop;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.java.semantic.JavaMetricsResolver;
import io.nop.lint.core.testing.RuleTestRunner;
import io.nop.lint.core.testing.SuiteResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The deep-profile metrics production suites (roadmap item 35, plan
 * 2026-09-24-1400-1 enums #11–#13): the three {@code requires: METRICS}
 * complexity rules run through the real RuleTester pipeline under
 * {@link LintProfile#DEEP} with the JavaMetricsResolver provider wired
 * explicitly (the items 32 demo-suite shape, now at production scale).
 *
 * <p>Discovery is fail-closed: the deep-suites directory must hold exactly
 * the eleven known suites (the four items 32–34 demos plus this plan's
 * seven production suites), so a dropped suite directory can never pass
 * silently. The invalid fixtures pin the computed metric values in their
 * expect entries, so a silent evaluator change cannot pass.</p>
 */
public class TestMetricsRuleSuites {

    static final String DEEP_SUITES_PATH = "/test/lint/deep-suites";

    /**
     * 4 demo suites (items 32–34: metrics-complexity, scope-shadow,
     * dataflow-l3, semantic-l4) + 7 production suites (item 35).
     */
    static final int DEEP_SUITE_TOTAL = 11;

    private static final Set<String> PRODUCTION_SUITES = Set.of(
            "metrics-method-cyclomatic-complexity",
            "metrics-method-cognitive-complexity",
            "metrics-method-npath-complexity");

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void deepSuitesDirectoryHoldsExactlyTheKnownSuites() {
        Set<String> discovered = new TreeSet<>();
        for (IResource child : deepSuiteDirs()) {
            discovered.add(child.getName());
        }
        assertEquals(DEEP_SUITE_TOTAL, discovered.size(),
                "the deep-suites directory must not gain or lose suites silently");
        assertTrue(discovered.containsAll(PRODUCTION_SUITES),
                "the metrics production suites must all be discovered");
    }

    @TestFactory
    public Stream<DynamicTest> everyMetricsSuiteRunsGreen() {
        RuleTestRunner runner = new RuleTestRunner(LanguageRegistry.discoverDefaults(),
                LintProfile.DEEP, null, new JavaMetricsResolver());
        return PRODUCTION_SUITES.stream().sorted().map(name -> {
            SuiteResult result = runner.runSuite(DEEP_SUITES_PATH + "/" + name);
            return DynamicTest.dynamicTest(name,
                    () -> assertTrue(result.isGreen(), result::renderFailures));
        });
    }

    static List<IResource> deepSuiteDirs() {
        List<? extends IResource> children = VirtualFileSystem.instance()
                .getChildren(DEEP_SUITES_PATH);
        List<IResource> dirs = new java.util.ArrayList<>();
        if (children != null) {
            for (IResource child : children) {
                if (child.isDirectory()) {
                    dirs.add(child);
                }
            }
        }
        return dirs;
    }
}
