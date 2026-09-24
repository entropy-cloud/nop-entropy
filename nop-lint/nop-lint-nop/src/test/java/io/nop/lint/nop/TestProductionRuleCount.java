package io.nop.lint.nop;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.cli.RuleSetLoader;
import io.nop.lint.core.rule.RuleDslModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The production rule library census (roadmap items 29/35, plan
 * 2026-09-24-1400-1): the conventional prefix must load exactly the landed
 * rule set — a rule file dropped or renamed without bookkeeping shifts this
 * count, and a coexistence regression (ruleset/dual-source handling) shows
 * up as a load failure. Before item 29 the library held 11 rules (slash-id
 * 9: exception 4 + api 4 + nop 1; XNode 2); item 29 brought it to 18; item
 * 35 lands the 48-rule target (18 + 30: nop 2 + antipattern 9 + deep
 * metrics/L3/L4 7 + quality/security 迁移吸收 12, of which 7 deep rules run
 * through the deep-suites family launchers).
 */
public class TestProductionRuleCount {

    private static final Set<String> EXPECTED_IDS = Set.of(
            // item 29 baseline (18)
            "nop/no-raw-exception",
            "nop/no-empty-catch",
            "nop/silent-swallow",
            "nop/no-log-getmessage",
            "nop/ibiz-missing-annotation",
            "nop/ibiz-missing-context",
            "nop/bizmodel-dao-access",
            "nop/bizmodel-safe-api",
            "nop/no-vfs-violation",
            "quality/no-system-out",
            "quality/no-return-null",
            "quality/no-transactional-annotation",
            "quality/no-star-import",
            "security/no-sensitive-literal",
            "security/no-hardcoded-crypto",
            "nop-orm-mandatory-default",
            "nop-xbiz-auth-not-sole-guard",
            "nop-orm-unique-key",
            // item 35: enum #1/#2 (nop)
            "nop/query-limit-required",
            "nop/no-direct-datasource-inject",
            // item 35: enum #3-#10, #23 (antipattern)
            "antipattern/double-brace-init",
            "antipattern/system-exit",
            "antipattern/print-stack-trace",
            "antipattern/empty-if-block",
            "antipattern/new-primitive-boxing",
            "antipattern/empty-sync-block",
            "antipattern/catch-npe",
            "antipattern/throw-in-finally",
            "antipattern/negated-equals",
            // item 35: enum #11-#17 (deep metrics/L3/L4)
            "quality/method-cyclomatic-complexity",
            "quality/method-cognitive-complexity",
            "quality/method-npath-complexity",
            "quality/unused-local-variable",
            "quality/self-assigned-local",
            "api/no-proxy-hostile-method",
            "api/no-nonslf4j-logger-call",
            // item 35: enum #18-#22, #24-#30 (quality/security 迁移吸收)
            "security/no-runtime-exec",
            "security/no-class-forname",
            "security/no-md5-digest",
            "security/no-des-encryption",
            "exception/no-catch-throwable",
            "quality/no-finalize",
            "quality/loose-coupling-hashset",
            "quality/replace-hashtable",
            "quality/replace-vector",
            "quality/empty-while-body",
            "quality/for-loop-can-be-foreach",
            "quality/control-statement-braces");

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void productionPrefixLoadsExactlyTheLandedRules() {
        Map<String, List<RuleDslModel>> grouped = new RuleSetLoader().loadGroupedByLanguage();

        Set<String> ids = new TreeSet<>();
        grouped.values().forEach(rules -> rules.forEach(rule -> ids.add(rule.getId())));

        assertEquals(48, ids.size(), "production rule census size (item 35 target)");
        assertEquals(EXPECTED_IDS, ids, "production rule census");
    }
}
