package io.nop.lint.core.cli;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.rule.RuleDslModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RuleSetLoader} proofs (Minimum Rules #25): classpath-VFS prefix
 * scan + {@code rule.yml} pipeline loading, language grouping under the
 * registry's normalization, and the fail-closed paths — empty prefix,
 * broken rule file, blank language. The default production prefix is also
 * pinned: on this module's own classpath the rule library is absent, so the
 * loader must stop with the explicit deployment error, never run empty.
 */
public class TestRuleSetLoader {

    private static final String VALID_PREFIX = "/test/lint/cli-rules";
    private static final String BROKEN_PREFIX = "/test/lint/cli-rules-broken";
    private static final String UNBOUND_PREFIX = "/test/lint/cli-rules-unbound";

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void loadsRulesAndGroupsByNormalizedLanguage() {
        Map<String, List<RuleDslModel>> grouped =
                new RuleSetLoader().loadGroupedByLanguage(VALID_PREFIX);

        assertEquals(1, grouped.size(), "both fixtures declare language Java");
        List<RuleDslModel> rules = grouped.get("java");
        assertEquals(2, rules.size());
        assertEquals("demo/no-bare-throw", rules.get(0).getId(),
                "rule order follows the sorted path order");
        assertEquals("demo/no-print", rules.get(1).getId());
    }

    @Test
    public void missingOrEmptyPrefixFailsClosedWithDeploymentError() {
        // a missing VFS path (null children) and a rule-free prefix converge
        // on the same explicit deployment error — never an empty silent run
        NopLintException ex = assertThrows(NopLintException.class,
                () -> new RuleSetLoader().loadGroupedByLanguage("/test/lint/no-such-prefix"));
        assertTrue(ex.getMessage().contains("no lint rule files"), ex.getMessage());
    }

    @Test
    public void brokenRuleFileFailsLoading() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> new RuleSetLoader().loadGroupedByLanguage(BROKEN_PREFIX));
        // the xdef severity enum rejects 'fatal' before the parser sees it
        assertTrue(ex.getMessage().contains("bad-severity")
                        || ex.getMessage().toLowerCase().contains("severity"),
                "the message must point at the broken rule: " + ex.getMessage());
    }

    @Test
    public void ruleWithoutLanguageFieldFailsClosed() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> new RuleSetLoader().loadGroupedByLanguage(
                        "/test/lint/cli-rules-broken-nolang"));
        assertTrue(ex.getMessage().contains("blank language"), ex.getMessage());
    }

    @Test
    public void xdefValidRuleWithUnboundLanguageStillGroups() {
        Map<String, List<RuleDslModel>> grouped =
                new RuleSetLoader().loadGroupedByLanguage(UNBOUND_PREFIX);

        assertEquals(1, grouped.size());
        assertTrue(grouped.containsKey("typescript"),
                "grouping is registry-independent; binding is checked by the CheckRunner");
        assertEquals("demo/ts-only", grouped.get("typescript").get(0).getId());
    }

    @Test
    public void defaultProductionPrefixIsAbsentOnCoreClasspathAndFailsClosed() {
        // nop-lint-nop is not on this module's classpath; the production
        // prefix must yield the explicit deployment error (exit-2 path)
        NopLintException ex = assertThrows(NopLintException.class,
                () -> new RuleSetLoader().loadGroupedByLanguage());
        assertTrue(ex.getMessage().contains(RuleSetLoader.DEFAULT_RULES_PREFIX), ex.getMessage());
    }
}
