/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rule.core.execute;

import io.nop.commons.cache.ICache;
import io.nop.commons.cache.MapCache;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.IRuleManager;
import io.nop.rule.core.IRuleRuntime;
import io.nop.rule.core.model.RuleModel;
import io.nop.rule.core.model.compile.RuleModelCompiler;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;

public class RuleManager implements IRuleManager {

    @Override
    public IRuleRuntime newRuntime(ICache<Object, Object> cache, IEvalScope scope) {
        return new RuleRuntime(cache, scope);
    }

    @Override
    public IRuleRuntime newRuntime() {
        return newRuntime(new MapCache<>("rule-rt-cache", false), XLang.newEvalScope());
    }

    @Override
    public IExecutableRule getRule(String ruleName, Long ruleVersion) {
        return getExecutableRule(getRuleModel(ruleName, ruleVersion));
    }

    @Override
    public IExecutableRule loadRuleFromPath(String path) {
        return getExecutableRule(loadRuleModelFromPath(path));
    }

    @Override
    public RuleModel getRuleModel(String ruleName, Long ruleVersion) {
        String path = RuleServiceHelper.buildResolveRulePath(ruleName, ruleVersion);
        RuleModel ruleModel = (RuleModel) ResourceComponentManager.instance().loadComponentModel(path);
        return ruleModel;
    }

    @Override
    public RuleModel loadRuleModelFromPath(String path) {
        return (RuleModel) ResourceComponentManager.instance().loadComponentModel(path);
    }

    public IExecutableRule getExecutableRule(RuleModel ruleModel) {
        synchronized (ruleModel) {
            IExecutableRule rule = ruleModel.getExecutableRule();
            if (rule == null) {
                XLangCompileTool compileTool = XLang.newCompileTool().allowUnregisteredScopeVar(true);
                rule = new RuleModelCompiler(compileTool).compileRule(ruleModel);
                ruleModel.setExecutableRule(rule);
            }
            return rule;
        }
    }
}