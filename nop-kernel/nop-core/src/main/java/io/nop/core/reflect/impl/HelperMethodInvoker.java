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
 * 将this指针作为函数的第一个参数传递，用于将静态函数模拟为对象成员函数。
 */
public class HelperMethodInvoker implements IEvalFunction {
    private final IEvalFunction fn;

    public HelperMethodInvoker(IEvalFunction fn) {
        this.fn = fn;
    }

    @Override
    public Object call0(Object thisObj, IEvalScope scope) {
        return fn.call1(null, thisObj, scope);
    }

    @Override
    public Object call1(Object thisObj, Object arg, IEvalScope scope) {
        return fn.call2(null, thisObj, arg, scope);
    }

    @Override
    public Object call2(Object thisObj, Object arg1, Object arg2, IEvalScope scope) {
        return fn.call3(null, thisObj, arg1, arg2, scope);
    }

    @Override
    public Object call3(Object thisObj, Object arg1, Object arg2, Object arg3, IEvalScope scope) {
        return doInvoke(new Object[]{thisObj, arg1, arg2, arg3}, scope);
    }

    @Override
    public Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
        Object[] mArgs = new Object[args.length + 1];
        mArgs[0] = thisObj;
        if (args.length > 0)
            System.arraycopy(args, 0, mArgs, 1, args.length);
        return doInvoke(mArgs, scope);
    }

    private Object doInvoke(Object[] args, IEvalScope scope) {
        return fn.invoke(null, args, scope);
    }
}