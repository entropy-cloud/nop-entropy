/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rule.core;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalPredicate;

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
}