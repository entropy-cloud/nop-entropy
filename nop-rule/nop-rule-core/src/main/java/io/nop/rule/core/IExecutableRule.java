/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rule.core;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalPredicate;

import java.util.Map;

/**
 * 根据RuleBean等规则模型编译得到的可执行规则对象
 */
public interface IExecutableRule extends IEvalPredicate {
    /**
     * 执行业务规则
     *
     * @param ruleRt 规则运行时
     * @return 返回true表示本规则的匹配条件满足，触发了输出动作
     */
    boolean execute(IRuleRuntime ruleRt);

    default boolean passConditions(IEvalContext context) {
        IRuleRuntime ruleRt = IRuleRuntime.fromEvalContext(context);
        return execute(ruleRt);
    }

    /**
     * 执行业务规则，并且获取到规则的输出变量集合
     *
     * @param ruleRt 规则运行时
     * @return 返回规则执行后的输出变量集合。可以通过ruleRt.isRuleMatch()函数来判断规则是否命中。
     */
    default Map<String, Object> executeForOutputs(IRuleRuntime ruleRt) {
        boolean ruleMatch = execute(ruleRt);
        ruleRt.setRuleMatch(ruleMatch);

        return ruleRt.getOutputs();
    }

    default Object executeForResult(IRuleRuntime ruleRt) {
        Map<String, Object> outputs = executeForOutputs(ruleRt);
        if (outputs == null || !ruleRt.isRuleMatch())
            return null;
        return outputs.get(RuleConstants.VAR_RESULT);
    }
}