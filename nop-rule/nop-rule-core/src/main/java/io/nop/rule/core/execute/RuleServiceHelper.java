package io.nop.rule.core.execute;

import io.nop.rule.core.RuleConstants;

public class RuleServiceHelper {
    public static String buildResolveRulePath(String ruleName, Integer ruleVersion) {
        if (ruleVersion == null)
            return RuleConstants.RESOLVE_RULE_NS_PREFIX + ruleName;
        return RuleConstants.IMP_PATH_RULE + ruleName + "/v" + ruleVersion;
    }
}
