/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rule.core.execute;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.collections.IKeyedList;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.IGenericType;
import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.IRuleRuntime;
import io.nop.rule.core.model.RuleInputDefineModel;

import static io.nop.rule.core.RuleErrors.ARG_DISPLAY_NAME;
import static io.nop.rule.core.RuleErrors.ARG_VAR_NAME;
import static io.nop.rule.core.RuleErrors.ERR_RULE_INPUT_NOT_ALLOW_COMPUTED_VAR;
import static io.nop.rule.core.RuleErrors.ERR_RULE_INPUT_VAR_NOT_ALLOW_EMPTY;
import static io.nop.rule.core.RuleErrors.ERR_RULE_UNKNOWN_INPUT_VAR;

public class NormalizeInputExecutableRule implements IExecutableRule {
    private final SourceLocation loc;
    private final IKeyedList<RuleInputDefineModel> inputDefines;
    private final IExecutableRule rule;

    public NormalizeInputExecutableRule(SourceLocation loc,
                                        IKeyedList<RuleInputDefineModel> inputDefines, IExecutableRule rule) {
        this.loc = loc;
        this.inputDefines = Guard.notEmpty(inputDefines, "inputDefines");
        this.rule = rule;
    }

    @Override
    public boolean execute(IRuleRuntime ruleRt) {
        checkInputs(ruleRt);

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

    private void checkInputs(IRuleRuntime ruleRt) {
        // 输入变量必须在已知范围之内
        for (String name : ruleRt.getInputs().keySet()) {
            // 跳过$schema等额外的描述信息
            if (name.startsWith("$"))
                continue;

            RuleInputDefineModel var = inputDefines.getByKey(name);
            if (var == null)
                throw new NopException(ERR_RULE_UNKNOWN_INPUT_VAR)
                        .loc(loc)
                        .param(ARG_VAR_NAME, name);

            if (var.isComputed())
                throw new NopException(ERR_RULE_INPUT_NOT_ALLOW_COMPUTED_VAR)
                        .loc(loc)
                        .param(ARG_VAR_NAME, name)
                        .param(ARG_DISPLAY_NAME, var.getDisplayName());
        }
    }
}
