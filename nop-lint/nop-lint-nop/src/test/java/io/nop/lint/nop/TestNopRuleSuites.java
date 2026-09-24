package io.nop.lint.nop;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
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
 * The nop-lint-nop rule library launcher (roadmap item 11, grown by items
 * 29/35): every suite under {@link RuleTestRunner#DEFAULT_SUITES_PATH} runs
 * green through the real {@link RuleTestRunner} — each suite's {@code
 * x:extends} entry loads the canonical main-resource rule through the
 * registered {@code rule.yml} pipeline, every {@code valid/*.java} fixture
 * stays diagnostic-free (the xscript filter paths included), and every
 * {@code invalid/*.java} fixture matches its {@code .expect} file entry by
 * entry.
 *
 * <p>The runner is built from ServiceLoader language discovery
 * ({@code nop-lint-java} on the test classpath), so the fixtures also prove
 * the platform wiring: rules bind to the real Java grammar without any
 * test-local registration. The expected-suite-set assertion fails closed if a
 * suite directory is ever dropped silently. The deep-profile suites (metrics
 * / L3 / L4, roadmap items 32–34 + item 35) live under {@code deep-suites/}
 * and run through their family launcher classes — they need explicit
 * provider wiring, so they are not part of this default-profile set.</p>
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
            "nop/no-vfs-violation",
            "nop/no-direct-datasource-inject",
            "nop/query-limit-required",
            "quality/no-system-out",
            "quality/no-return-null",
            "quality/no-transactional-annotation",
            "quality/no-star-import",
            "quality/no-finalize",
            "quality/loose-coupling-hashset",
            "quality/replace-hashtable",
            "quality/replace-vector",
            "quality/empty-while-body",
            "quality/for-loop-can-be-foreach",
            "quality/control-statement-braces",
            "security/no-sensitive-literal",
            "security/no-hardcoded-crypto",
            "security/no-runtime-exec",
            "security/no-class-forname",
            "security/no-md5-digest",
            "security/no-des-encryption",
            "exception/no-catch-throwable",
            "antipattern/double-brace-init",
            "antipattern/system-exit",
            "antipattern/print-stack-trace",
            "antipattern/empty-if-block",
            "antipattern/new-primitive-boxing",
            "antipattern/empty-sync-block",
            "antipattern/catch-npe",
            "antipattern/throw-in-finally",
            "antipattern/negated-equals");

    /**
     * The suppression suite (roadmap item 17) is fixture-local: its demo rule
     * exists only to exercise the engine's suppression tail, so it has no
     * canonical main resource and is asserted separately.
     */
    private static final String SUPPRESSION_SUITE_RULE_ID = "demo/no-suppress-demo";

    /**
     * The autofix demo suite (roadmap item 25) is fixture-local the same way:
     * its rule proves the loader accepts the fix surface and the RuleTester
     * runs its diagnostic face; the rewrite itself is engine-level asserted
     * in {@code TestAutofixDemoRule}.
     */
    private static final String AUTOFIX_SUITE_RULE_ID = "demo/no-print-demo";

    /**
     * The XNode suite rule ids (roadmap item 21): the two design 01 §3.5
     * example rules landed as production XML rules. They are tracked apart
     * from the slash-id Java rules only because their ids carry no category
     * prefix, so the suite path cannot be derived by splitting on '/'.
     */
    private static final Set<String> XNODE_RULE_IDS = Set.of(
            "nop-orm-mandatory-default",
            "nop-xbiz-auth-not-sole-guard",
            "nop-orm-unique-key");

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * The single rule-id → rule-directory derivation (item 35, R1 F6: the
     * two former switch copies are converged here; main-resource paths and
     * suite paths share this one table). Unmapped ids fail loudly — a new
     * rule without bookkeeping can never load through a guessed directory.
     */
    static String categoryOf(String ruleId) {
        return switch (ruleId) {
            case "nop/no-raw-exception", "nop/no-empty-catch",
                 "nop/silent-swallow", "nop/no-log-getmessage",
                 "exception/no-catch-throwable" -> "exception";
            case "nop/no-vfs-violation", "nop/no-direct-datasource-inject",
                 "nop/query-limit-required" -> "nop";
            case "nop/ibiz-missing-annotation", "nop/ibiz-missing-context",
                 "nop/bizmodel-dao-access", "nop/bizmodel-safe-api" -> "api";
            default -> {
                if (ruleId.startsWith("quality/") || ruleId.startsWith("security/")
                        || ruleId.startsWith("antipattern/")) {
                    yield ruleId.substring(0, ruleId.indexOf('/'));
                }
                throw new IllegalStateException("rule id '" + ruleId
                        + "' has no category mapping in the suite launcher (add it to "
                        + "categoryOf — fail-closed, never guess a directory)");
            }
        };
    }

    @TestFactory
    public Stream<DynamicTest> everyDiscoveredSuiteRunsGreen() {
        RuleTestRunner runner = new RuleTestRunner();
        List<SuiteResult> results = runner.runSuites(RuleTestRunner.DEFAULT_SUITES_PATH);

        assertEquals(EXPECTED_RULE_IDS.size() + XNODE_RULE_IDS.size() + 2, results.size(),
                "the expected rule suites must all be discovered (no silent drops)");
        for (SuiteResult result : results) {
            assertTrue(result.isGreen(), result::renderFailures);
        }
        Set<String> discovered = results.stream().map(SuiteResult::ruleId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> expected = new java.util.HashSet<>(EXPECTED_RULE_IDS);
        expected.addAll(XNODE_RULE_IDS);
        expected.add(SUPPRESSION_SUITE_RULE_ID);
        expected.add(AUTOFIX_SUITE_RULE_ID);
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
            String category = categoryOf(ruleId);
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
        // The XNode rules (item 21) load through the same registered pipeline.
        for (String ruleId : XNODE_RULE_IDS) {
            RuleDslModel rule = parser.loadRuleModel(
                    "/nop/lint/rules/nop/" + ruleId + ".rule.yml");
            assertEquals(ruleId, rule.getId());
            assertEquals("XML", rule.getLanguage());
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
            String category = categoryOf(ruleId);
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
