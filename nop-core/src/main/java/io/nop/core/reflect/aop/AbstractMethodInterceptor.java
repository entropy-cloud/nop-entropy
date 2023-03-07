/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.aop;

import java.util.function.Predicate;

public abstract class AbstractMethodInterceptor implements IMethodInterceptor {
    private Predicate<IMethodInvocation> filter;

    public void setMethodFilter(Predicate<IMethodInvocation> filter) {
        this.filter = filter;
    }

    public boolean isMatched(IMethodInvocation inv) {
        if (filter == null)
            return true;
        return filter.test(inv);
    }
}
