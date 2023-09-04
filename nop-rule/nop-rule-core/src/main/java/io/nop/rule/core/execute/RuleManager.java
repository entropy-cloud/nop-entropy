/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rule.core.execute;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.type.IGenericType;
import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.IRuleManager;
import io.nop.rule.core.IRuleRuntime;
import io.nop.rule.core.RuleConstants;
import io.nop.rule.core.model.RuleInputDefineModel;
import io.nop.rule.core.model.RuleModel;

import java.util.List;
import java.util.Map;

import static io.nop.rule.core.RuleErrors.ARG_DISPLAY_NAME;
import static io.nop.rule.core.RuleErrors.ARG_VAR_NAME;
import static io.nop.rule.core.RuleErrors.ERR_RULE_INPUT_NOT_ALLOW_COMPUTED_VAR;
import static io.nop.rule.core.RuleErrors.ERR_RULE_INPUT_VAR_NOT_ALLOW_EMPTY;
import static io.nop.rule.core.RuleErrors.ERR_RULE_UNKNOWN_INPUT_VAR;

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
        try {
            RuleModel ruleModel = getRuleModel(ruleName, ruleVersion);
            if (ruleModel.getRuleVersion() != null)
                ruleVersion = ruleModel.getRuleVersion();
            ruleRt.setRuleVersion(ruleVersion);

            IExecutableRule rule = ruleModel.getExecutableRule();
            prepareScope(ruleRt, ruleModel);

            boolean ruleMatch = rule.execute(ruleRt);
            ruleRt.setRuleMatch(ruleMatch);

            if (ruleModel.getAfterExecute() != null) {
                ruleRt.getEvalScope().setLocalValue(RuleConstants.VAR_RULE_MATCH, ruleMatch);
                ruleModel.getAfterExecute().invoke(ruleRt);
            }

            return ruleRt.getOutputs();
        } catch (NopException e) {
            e.addXplStack("executeRule:ruleName=" + ruleName + ",ruleVersion=" + ruleVersion);
            throw e;
        }
    }

    private void prepareScope(IRuleRuntime ruleRt, RuleModel ruleModel) {
        if (ruleModel.getBeforeExecute() != null) {
            ruleModel.getBeforeExecute().invoke(ruleRt);
        }

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

        List<RuleInputDefineModel> inputDefines = ruleModel.getInputs();
        for (RuleInputDefineModel inputDefine : inputDefines) {
            String name = inputDefine.getName();
            Object value = ruleRt.getInput(name);

            if (inputDefine.isComputed()) {
                // 计算表达式
                value = inputDefine.getDefaultExpr() == null ? null : inputDefine.getDefaultExpr().invoke(ruleRt);
            } else if (value == null) {
                if (inputDefine.getDefaultExpr() != null) {
                    value = inputDefine.getDefaultExpr().invoke(ruleRt);
                }
            }

            if (inputDefine.isMandatory()) {
                if (StringHelper.isEmptyObject(value))
                    throw new NopException(ERR_RULE_INPUT_VAR_NOT_ALLOW_EMPTY)
                            .source(inputDefine)
                            .param(ARG_VAR_NAME, name)
                            .param(ARG_DISPLAY_NAME, inputDefine.getDisplayName());
            }

            IGenericType type = inputDefine.getType();
            if (type != null && value != null) {
                value = BeanTool.castBeanToType(value, type);
            }
            ruleRt.getEvalScope().setLocalValue(name, value);
        }
    }
}