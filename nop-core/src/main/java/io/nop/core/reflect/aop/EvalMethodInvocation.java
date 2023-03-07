/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.aop;

import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.reflect.IFunctionModel;

public class EvalMethodInvocation implements IMethodInvocation {
    private final IFunctionModel method;
    private final Object thisObj;
    private final Object[] args;

    public EvalMethodInvocation(IFunctionModel method, Object thisObj, Object[] args) {
        this.thisObj = thisObj;
        this.args = args;
        this.method = method;
    }

    @Override
    public Object getThis() {
        return this;
    }

    @Override
    public Object[] getArguments() {
        return args;
    }

    @Override
    public IFunctionModel getMethod() {
        return method;
    }

    @Override
    public Object proceed() {
        return method.invoke(thisObj, args, DisabledEvalScope.INSTANCE);
    }
}