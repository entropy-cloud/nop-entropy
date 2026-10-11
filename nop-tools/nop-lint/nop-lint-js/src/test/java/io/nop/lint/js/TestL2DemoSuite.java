package io.nop.lint.js;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import io.nop.lint.core.testing.RuleTestRunner;
import io.nop.lint.core.testing.SuiteResult;
import io.nop.lint.js.tsc.TscBridgeConfig;
import io.nop.lint.js.tsc.TscBridgeUnavailableException;
import io.nop.lint.js.tsc.TscTypeResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code requires: "L2"} demo rule end to end (roadmap item 20 Phase 3):
 * the {@code nop-lint/no-string-throw} suite runs through the real
 * {@link RuleTestRunner} with the real {@link TscTypeResolver} wired in —
 * the rule's {@code typeOf} constraint resolves the thrown expression's
 * type through the real tsc, so the string-throw fixture reports and the
 * Error-throw fixture stays clean. The FAST-profile skip leg reuses the
 * Phase 2 gate matrix on this rule's model.
 *
 * <p>Environment contract (adjudicated, plan Phase 3): without Node or the
 * typescript package this test reports <em>skipped</em> — visible in the
 * summary counts, never silently green; a present-but-broken environment
 * fails the suite run.</p>
 */
public class TestL2DemoSuite {

    private static final String SUITE_PATH = "/test/lint/l2-suites/no-string-throw";

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static TscTypeResolver newResolver(Path suiteDir) {
        TscBridgeConfig config;
        try {
            config = TscBridgeConfig.defaultEnvironment();
        } catch (TscBridgeUnavailableException e) {
            Assumptions.abort("L2 demo suite skipped (environment contract): " + e);
            return null;
        }
        if (!config.isEnvironmentUsable()) {
            Assumptions.abort("L2 demo suite skipped: node/helper script not usable on this machine");
        }
        return new TscTypeResolver(config, suiteDir.resolve("tsconfig.json"));
    }

    @Test
    public void l2SuiteRunsGreenThroughTheRealBridge() {
        Path suiteDir = suiteDirOnDisk();
        TscTypeResolver resolver = newResolver(suiteDir);
        try {
            LanguageRegistry registry = LanguageRegistry.discoverDefaults();
            RuleTestRunner runner = new RuleTestRunner(registry, LintProfile.STANDARD, resolver);
            SuiteResult result = runner.runSuite(SUITE_PATH);
            assertTrue(result.isGreen(), result::renderFailures);
        } finally {
            if (resolver != null) {
                resolver.close();
            }
        }
    }

    @Test
    public void fastProfileSkipsTheL2RuleWithoutAnyTypeCost() {
        // resolver deliberately absent: the fast ceiling has no L2, the rule
        // exits through skippedByProfile and the resolver would never be
        // consulted (no bridge, no Node cost in the editor path)
        RuleDslModel rule = new RuleDslParser().loadRuleModel(SUITE_PATH + "/no-string-throw.rule.yml");
        LanguageRegistry registry = LanguageRegistry.discoverDefaults();
        LintEngine engine = new LintEngine(registry, LintProfile.FAST);
        LintResult result = engine.lint(List.of(rule), rule.getLanguage(),
                "const s: string = \"x\";\nfunction boom(): never { throw s; }\n");

        assertEquals(1, result.stats().getRulesSkippedByProfile(),
                "the fast ceiling keeps the L2 rule out (skipped, never faked)");
        assertTrue(result.stats().getSkippedRuleIds().contains("nop-lint/no-string-throw"));
        assertEquals(0, result.diagnostics().size());
    }

    private static Path suiteDirOnDisk() {
        // the VFS synthesizes directory resources in memory, so resolve the
        // suite through its file-backed rule file and take the parent
        io.nop.core.resource.IResource ruleResource = io.nop.core.resource.VirtualFileSystem.instance()
                .getResource(SUITE_PATH + "/no-string-throw.rule.yml", true);
        java.io.File ruleFile = null;
        if (ruleResource instanceof io.nop.core.resource.IFile file) {
            ruleFile = file.toFile();
        } else if (ruleResource instanceof io.nop.core.resource.impl.ClassPathResource classPathResource) {
            ruleFile = classPathResource.toFile();
        }
        if (ruleFile != null && ruleFile.isFile()) {
            return ruleFile.getParentFile().toPath();
        }
        throw new IllegalStateException("L2 demo suite rule file is not file-backed on this "
                + "classpath: " + SUITE_PATH + " (resource="
                + (ruleResource == null ? "null" : ruleResource.getClass().getName()) + ")");
    }
}
