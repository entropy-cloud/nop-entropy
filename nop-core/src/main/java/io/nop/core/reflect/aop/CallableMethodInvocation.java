/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.aop;

import io.nop.core.reflect.IFunctionModel;

import java.util.concurrent.Callable;

public class CallableMethodInvocation implements IMethodInvocation {
    private final Object thisObj;
    private final Object[] arguments;
    private final IFunctionModel method;
    private final Callable<?> callable;

    public CallableMethodInvocation(Object thisObj, Object[] arguments, IFunctionModel method, Callable<?> callable) {
        this.thisObj = thisObj;
        this.arguments = arguments;
        this.method = method;
        this.callable = callable;
    }

    @Override
    public Object getThis() {
        return thisObj;
    }

    @Override
    public Object[] getArguments() {
        return arguments;
    }

    @Override
    public IFunctionModel getMethod() {
        return method;
    }

    @Override
    public Object proceed() throws Exception {
        return callable.call();
    }
}