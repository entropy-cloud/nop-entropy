package io.nop.lint.nop;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import io.nop.lint.core.testing.RuleTestRunner;
import io.nop.lint.core.testing.SuiteResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The nop-lint-nop rule library launcher (roadmap item 11): every suite under
 * {@link RuleTestRunner#DEFAULT_SUITES_PATH} runs green through the real
 * {@link RuleTestRunner} — each suite's {@code x:extends} entry loads the
 * canonical main-resource rule through the registered {@code rule.yml}
 * pipeline, every {@code valid/*.java} fixture stays diagnostic-free (the
 * xscript filter paths included), and every {@code invalid/*.java} fixture
 * matches its {@code .expect} file entry by entry.
 *
 * <p>The runner is built from ServiceLoader language discovery
 * ({@code nop-lint-java} on the test classpath), so the fixtures also prove
 * the platform wiring: rules bind to the real Java grammar without any
 * test-local registration. The expected-suite-set assertion fails closed if a
 * suite directory is ever dropped silently.</p>
 */
public class TestNopRuleSuites {

    private static final Set<String> EXPECTED_RULE_IDS = Set.of(
            "nop/no-raw-exception",
            "nop/no-empty-catch",
            "nop/silent-swallow",
            "nop/no-log-getmessage",
            "nop/ibiz-missing-annotation",
            "nop/ibiz-missing-context",
            "nop/bizmodel-dao-access",
            "nop/bizmodel-safe-api",
            "nop/no-vfs-violation");

    /**
     * The suppression suite (roadmap item 17) is fixture-local: its demo rule
     * exists only to exercise the engine's suppression tail, so it has no
     * canonical main resource and is asserted separately.
     */
    private static final String SUPPRESSION_SUITE_RULE_ID = "demo/no-suppress-demo";

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
        RuleTestRunner runner = new RuleTestRunner();
        List<SuiteResult> results = runner.runSuites(RuleTestRunner.DEFAULT_SUITES_PATH);

        assertEquals(EXPECTED_RULE_IDS.size() + 1, results.size(),
                "the expected rule suites must all be discovered (no silent drops)");
        for (SuiteResult result : results) {
            assertTrue(result.isGreen(), result::renderFailures);
        }
        Set<String> discovered = results.stream().map(SuiteResult::ruleId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> expected = new java.util.HashSet<>(EXPECTED_RULE_IDS);
        expected.add(SUPPRESSION_SUITE_RULE_ID);
        assertEquals(expected, discovered);

        return results.stream().map(result -> DynamicTest.dynamicTest(result.suitePath(),
                () -> assertTrue(result.isGreen(), result::renderFailures)));
    }

    /**
     * The canonical main-resource rules load through the registered pipeline
     * on their own VFS paths (not only via the suite entries), carry the
     * expected ids, and the xscript-bearing rules really carry script bodies.
     */
    @Test
    public void mainResourceRulesLoadThroughTheRegisteredPipeline() {
        RuleDslParser parser = new RuleDslParser();
        for (String ruleId : EXPECTED_RULE_IDS) {
            String category = switch (ruleId) {
                case "nop/no-raw-exception", "nop/no-empty-catch",
                     "nop/silent-swallow", "nop/no-log-getmessage" -> "exception";
                case "nop/no-vfs-violation" -> "nop";
                default -> "api";
            };
            String name = ruleId.substring(ruleId.indexOf('/') + 1);
            String path = "/nop/lint/rules/" + category + "/" + name + ".rule.yml";

            RuleDslModel rule = parser.loadRuleModel(path);
            assertEquals(ruleId, rule.getId());
            assertEquals("Java", rule.getLanguage());
            assertNotNull(rule.getMessage());
            assertNotNull(rule.getMatcher());
            assertNotNull(rule.getMetadata(), "rules must carry a metadata block (version stamp)");
            assertEquals("1.0", rule.getMetadata().getVersion());
        }

        assertTrue(parser.loadRuleModel("/nop/lint/rules/exception/no-empty-catch.rule.yml")
                .getXscript().contains("report("), "no-empty-catch must carry its comment filter script");
        assertTrue(parser.loadRuleModel("/nop/lint/rules/api/ibiz-missing-annotation.rule.yml")
                .getXscript().contains("report("), "ibiz rules are xscript-filtered");
        assertEquals(null, parser.loadRuleModel("/nop/lint/rules/nop/no-vfs-violation.rule.yml")
                .getXscript(), "no-vfs-violation is pattern-only");
    }

    /**
     * The discovered suite entries are thin x:extends pointers, so a suite
     * drift between the fixture rule and the canonical main resource is
     * structurally impossible; this assertion documents that contract.
     */
    @Test
    public void suiteEntriesPointAtCanonicalRules() {
        for (String ruleId : EXPECTED_RULE_IDS) {
            String category = switch (ruleId) {
                case "nop/no-raw-exception", "nop/no-empty-catch",
                     "nop/silent-swallow", "nop/no-log-getmessage" -> "exception";
                case "nop/no-vfs-violation" -> "nop";
                default -> "api";
            };
            String name = ruleId.substring(ruleId.indexOf('/') + 1);
            RuleDslModel suiteRule = new RuleDslParser().loadRuleModel(
                    RuleTestRunner.DEFAULT_SUITES_PATH + "/" + category + "/" + name + "/" + name + ".rule.yml");
            RuleDslModel canonical = new RuleDslParser().loadRuleModel(
                    "/nop/lint/rules/" + category + "/" + name + ".rule.yml");

            assertEquals(canonical.getId(), suiteRule.getId());
            assertEquals(canonical.getMessage(), suiteRule.getMessage());
            assertEquals(canonical.getXscript(), suiteRule.getXscript());
            assertFalse(suiteRule.getId().isBlank());
        }
    }
}
