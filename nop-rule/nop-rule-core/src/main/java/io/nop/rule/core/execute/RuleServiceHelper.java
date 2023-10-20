/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rule.core.execute;

import io.nop.rule.core.RuleConstants;

public class RuleServiceHelper {
    public static String buildResolveRulePath(String ruleName, Long ruleVersion) {
        if (ruleVersion == null)
            return RuleConstants.RESOLVE_RULE_NS_PREFIX + ruleName;
        return RuleConstants.RESOLVE_RULE_NS_PREFIX + ruleName + "/v" + ruleVersion;
    }
}
