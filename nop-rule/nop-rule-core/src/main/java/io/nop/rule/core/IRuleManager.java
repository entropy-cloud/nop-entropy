/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rule.core;

import io.nop.commons.cache.ICache;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.rule.core.model.RuleModel;

import java.util.Map;

public interface IRuleManager {

    IRuleRuntime newRuntime(ICache<Object,Object> cache, IEvalScope scope);

    IRuleRuntime newRuntime();

    IExecutableRule getRule(String ruleName, Long ruleVersion);

    RuleModel getRuleModel(String ruleName, Long ruleVersion);

    Map<String, Object> executeRule(String ruleName, Long ruleVersion, IRuleRuntime ruleRt);

    default Object chooseByRule(String ruleName, Long ruleVersion, IRuleRuntime ruleRt) {
        Map<String, Object> outputs = executeRule(ruleName, ruleVersion, ruleRt);
        if (outputs == null || !ruleRt.isRuleMatch())
            return null;
        return outputs.get(RuleConstants.VAR_RESULT);
    }
}