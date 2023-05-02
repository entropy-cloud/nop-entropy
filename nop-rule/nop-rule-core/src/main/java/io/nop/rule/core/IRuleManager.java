package io.nop.rule.core;

import io.nop.core.lang.eval.IEvalScope;

import java.util.Map;

public interface IRuleManager {

    IRuleRuntime newRuntime(IEvalScope scope);

    default IRuleRuntime newRuntime() {
        return newRuntime(null);
    }

    IExecutableRule getRule(String ruleName);

    Map<String, Object> executeRule(String ruleName, IRuleRuntime ruleRt);

    default Object chooseByRule(String ruleName, IRuleRuntime ruleRt) {
        Map<String, Object> outputs = executeRule(ruleName, ruleRt);
        if (outputs == null)
            return null;
        return outputs.get(RuleConstants.VAR_RESULT);
    }
}