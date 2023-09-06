package io.nop.rule.core;

import io.nop.commons.cache.ICache;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.rule.core.model.RuleModel;

import java.util.Map;

public interface IRuleManager {

    IRuleRuntime newRuntime(ICache<Object,Object> cache, IEvalScope scope);

    IRuleRuntime newRuntime();

    IExecutableRule getRule(String ruleName, Integer ruleVersion);

    RuleModel getRuleModel(String ruleName, Integer ruleVersion);

    Map<String, Object> executeRule(String ruleName, Integer ruleVersion, IRuleRuntime ruleRt);

    default Object chooseByRule(String ruleName, Integer ruleVersion, IRuleRuntime ruleRt) {
        Map<String, Object> outputs = executeRule(ruleName, ruleVersion, ruleRt);
        if (outputs == null || !ruleRt.isRuleMatch())
            return null;
        return outputs.get(RuleConstants.VAR_RESULT);
    }
}