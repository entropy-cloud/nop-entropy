/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.impl;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;

/**
 * EvalMethod的第一个参数为IEvalScope
 */
public class EvalMethodInvoker implements IEvalFunction {
    private final IEvalFunction fn;

    public EvalMethodInvoker(IEvalFunction fn) {
        this.fn = fn;
    }

    @Override
    public Object call0(Object thisObj, IEvalScope scope) {
        return fn.call1(thisObj, scope, scope);
    }

    @Override
    public Object call1(Object thisObj, Object arg, IEvalScope scope) {
        return fn.call2(thisObj, scope, arg, scope);
    }

    @Override
    public Object call2(Object thisObj, Object arg1, Object arg2, IEvalScope scope) {
        return fn.call3(thisObj, scope, arg1, arg2, scope);
    }

    @Override
    public Object call3(Object thisObj, Object arg1, Object arg2, Object arg3, IEvalScope scope) {
        return doInvoke(thisObj, new Object[]{scope, arg1, arg2, arg3}, scope);
    }

    @Override
    public Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
        Object[] mArgs = new Object[args.length + 1];
        mArgs[0] = scope;
        if (args.length > 0)
            System.arraycopy(args, 0, mArgs, 1, args.length);
        return doInvoke(thisObj, mArgs, scope);
    }

    private Object doInvoke(Object thisObj, Object[] args, IEvalScope scope) {
        return fn.invoke(thisObj, args, scope);
    }
}