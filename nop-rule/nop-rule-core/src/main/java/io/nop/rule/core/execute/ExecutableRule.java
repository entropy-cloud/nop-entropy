/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rule.core.execute;

import io.nop.rule.api.IExecutableRule;
import io.nop.rule.api.IRuleRuntime;

public class ExecutableRule implements IExecutableRule {
    @Override
    public boolean execute(IRuleRuntime ruleRt) {
        return false;
    }
}
