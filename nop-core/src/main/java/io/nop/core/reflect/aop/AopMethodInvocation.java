/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.aop;

import io.nop.core.reflect.IFunctionModel;

public class AopMethodInvocation implements IMethodInvocation {
    private final IMethodInvocation invocation;
    private final IMethodInterceptor[] interceptors;
    private int currentIndex;

    public AopMethodInvocation(IMethodInvocation invocation, IMethodInterceptor[] interceptors) {
        this.invocation = invocation;
        this.interceptors = interceptors;
    }

    @Override
    public Object[] getArguments() {
        return invocation.getArguments();
    }

    @Override
    public Object proceed() throws Exception {
        if (currentIndex >= interceptors.length)
            return invocation.proceed();

        IMethodInterceptor interceptor = interceptors[currentIndex];
        currentIndex++;
        return interceptor.invoke(this);
    }

    @Override
    public Object getThis() {
        return invocation.getThis();
    }

    @Override
    public IFunctionModel getMethod() {
        return invocation.getMethod();
    }
}