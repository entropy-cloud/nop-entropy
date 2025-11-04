/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.eval.functions;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;

import java.io.Serializable;

public class BindEvalFunction implements IEvalFunction, Serializable {
    private static final long serialVersionUID = -910390860429985250L;

    private final IEvalFunction fn;
    private final Object bindThisObj;

    public BindEvalFunction(IEvalFunction fn, Object bindThisObj) {
        this.fn = fn;
        this.bindThisObj = bindThisObj;
    }

    public String toString() {
        return fn + ".bind(" + bindThisObj + "]";
    }

    @Override
    public Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
        return fn.invoke(this.bindThisObj, args, scope);
    }

    @Override
    public Object call0(Object thisObj, IEvalScope scope) {
        return fn.call0(bindThisObj, scope);
    }

    @Override
    public Object call1(Object thisObj, Object arg, IEvalScope scope) {
        return fn.call1(bindThisObj, arg, scope);
    }

    @Override
    public Object call2(Object thisObj, Object arg1, Object arg2, IEvalScope scope) {
        return fn.call2(bindThisObj, arg1, arg2, scope);
    }

    @Override
    public Object call3(Object thisObj, Object arg1, Object arg2, Object arg3, IEvalScope scope) {
        return fn.call3(bindThisObj, arg1, arg2, arg3, scope);
    }

    @Override
    public IEvalFunction bind(Object thisObj) {
        return this;
    }
}