/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rule.core.execute;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.IRuleManager;
import io.nop.rule.core.IRuleRuntime;
import io.nop.rule.core.model.RuleModel;

import java.util.Map;

public class RuleManager implements IRuleManager {

    @Override
    public IRuleRuntime newRuntime(IEvalScope scope) {
        return new RuleRuntime(scope);
    }

    @Override
    public IExecutableRule getRule(String ruleName, Integer ruleVersion) {
        return getRuleModel(ruleName, ruleVersion).getExecutableRule();
    }

    @Override
    public RuleModel getRuleModel(String ruleName, Integer ruleVersion) {
        String path = RuleServiceHelper.buildResolveRulePath(ruleName, ruleVersion);
        RuleModel ruleModel = (RuleModel) ResourceComponentManager.instance().loadComponentModel(path);
        return ruleModel;
    }

    @Override
    public Map<String, Object> executeRule(String ruleName, Integer ruleVersion, IRuleRuntime ruleRt) {
        ruleRt.clearOutputs();

        IExecutableRule rule = getRule(ruleName, ruleVersion);
        if (!rule.execute(ruleRt))
            return null;

        return ruleRt.getOutputs();
    }
}