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
 * The production rule library census (roadmap item 29, plan
 * 2026-09-24-0330-1): the conventional prefix must load exactly the landed
 * rule set — a rule file dropped or renamed without bookkeeping shifts this
 * count, and a coexistence regression (ruleset/dual-source handling) shows
 * up as a load failure. Before item 29 the library held 11 rules (slash-id
 * 9: exception 4 + api 4 + nop 1; XNode 2); item 29 adds the quality batch
 * (4), the security batch (2) and the XNode unique-key rule (1) for 18.
 */
public class TestProductionRuleCount {

    private static final Set<String> EXPECTED_IDS = Set.of(
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
            "nop-orm-unique-key");

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

        assertEquals(EXPECTED_IDS, ids, "production rule census");
    }
}
