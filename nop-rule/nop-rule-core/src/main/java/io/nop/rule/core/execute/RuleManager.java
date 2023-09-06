/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rule.core.execute;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.cache.MapCache;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.IRuleManager;
import io.nop.rule.core.IRuleRuntime;
import io.nop.rule.core.model.RuleInputDefineModel;
import io.nop.rule.core.model.RuleModel;
import io.nop.xlang.api.XLang;

import java.util.Map;

import static io.nop.rule.core.RuleErrors.ARG_DISPLAY_NAME;
import static io.nop.rule.core.RuleErrors.ARG_VAR_NAME;
import static io.nop.rule.core.RuleErrors.ERR_RULE_INPUT_NOT_ALLOW_COMPUTED_VAR;
import static io.nop.rule.core.RuleErrors.ERR_RULE_UNKNOWN_INPUT_VAR;

public class RuleManager implements IRuleManager {

    @Override
    public IRuleRuntime newRuntime(ICache<Object, Object> cache, IEvalScope scope) {
        return new RuleRuntime(cache, scope);
    }

    @Override
    public IRuleRuntime newRuntime() {
        return newRuntime(new MapCache<>("rule-rt-cache",false), XLang.newEvalScope());
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
        try {
            RuleModel ruleModel = getRuleModel(ruleName, ruleVersion);
            if (ruleModel.getRuleVersion() != null)
                ruleVersion = ruleModel.getRuleVersion();
            ruleRt.setRuleVersion(ruleVersion);

            IExecutableRule rule = ruleModel.getExecutableRule();
            prepareScope(ruleRt, ruleModel);

            boolean ruleMatch = rule.execute(ruleRt);
            ruleRt.setRuleMatch(ruleMatch);

            return ruleRt.getOutputs();
        } catch (NopException e) {
            e.addXplStack("executeRule:ruleName=" + ruleName + ",ruleVersion=" + ruleVersion);
            throw e;
        }
    }

    private void prepareScope(IRuleRuntime ruleRt, RuleModel ruleModel) {
        if (ruleRt.getInputs() != null) {
            // 输入变量必须在已知范围之内
            for (String name : ruleRt.getInputs().keySet()) {
                // 跳过$schema等额外的描述信息
                if (name.startsWith("$"))
                    continue;

                RuleInputDefineModel var = ruleModel.getInputVar(name);
                if (var == null)
                    throw new NopException(ERR_RULE_UNKNOWN_INPUT_VAR)
                            .source(ruleModel)
                            .param(ARG_VAR_NAME, name);

                if (var.isComputed())
                    throw new NopException(ERR_RULE_INPUT_NOT_ALLOW_COMPUTED_VAR)
                            .source(ruleModel)
                            .param(ARG_VAR_NAME, name)
                            .param(ARG_DISPLAY_NAME, var.getDisplayName());
            }
        }
    }
}