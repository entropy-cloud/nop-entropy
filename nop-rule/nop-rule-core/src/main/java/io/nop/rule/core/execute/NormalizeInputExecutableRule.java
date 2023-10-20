/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rule.core.execute;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.IGenericType;
import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.IRuleRuntime;
import io.nop.rule.core.model.RuleInputDefineModel;

import java.util.List;

import static io.nop.rule.core.RuleErrors.ARG_DISPLAY_NAME;
import static io.nop.rule.core.RuleErrors.ARG_VAR_NAME;
import static io.nop.rule.core.RuleErrors.ERR_RULE_INPUT_VAR_NOT_ALLOW_EMPTY;

public class NormalizeInputExecutableRule implements IExecutableRule {
    private final List<RuleInputDefineModel> inputDefines;
    private final IExecutableRule rule;

    public NormalizeInputExecutableRule(List<RuleInputDefineModel> inputDefines, IExecutableRule rule) {
        this.inputDefines = Guard.notEmpty(inputDefines, "inputDefines");
        this.rule = rule;
    }

    @Override
    public boolean execute(IRuleRuntime ruleRt) {
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
        return rule.execute(ruleRt);
    }
}
