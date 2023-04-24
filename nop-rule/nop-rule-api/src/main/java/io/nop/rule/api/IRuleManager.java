/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rule.api;

import io.nop.api.core.util.IVariableScope;

public interface IRuleManager {
    String OUTPUT_RESULT = "result";

    IExecutableRule getRule(String ruleName);

    IRuleRuntime newRuleRuntime(IVariableScope scope);

    default Object chooseByRule(String ruleName, IVariableScope scope) {
        IRuleRuntime rt = newRuleRuntime(scope);
        IExecutableRule rule = getRule(ruleName);
        if (!rule.execute(rt))
            return null;
        return rt.getOutput(OUTPUT_RESULT);
    }
}
