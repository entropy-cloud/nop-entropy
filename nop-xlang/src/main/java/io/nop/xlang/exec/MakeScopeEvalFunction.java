/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.XLang;

public class MakeScopeEvalFunction implements IEvalFunction {
    private final IEvalFunction function;

    public MakeScopeEvalFunction(IEvalFunction function) {
        this.function = function;
    }

    public static IEvalFunction of(IEvalFunction fn) {
        if (fn instanceof MakeScopeEvalFunction)
            return fn;
        return new MakeScopeEvalFunction(fn);
    }

    private IEvalScope makeScope(IEvalScope scope) {
        if (scope == null || scope == DisabledEvalScope.INSTANCE)
            return XLang.newEvalScope();
        return scope;
    }

    @Override
    public Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
        return function.invoke(thisObj, args, makeScope(scope));
    }

    @Override
    public Object call0(Object thisObj, IEvalScope scope) {
        return function.call0(thisObj, makeScope(scope));
    }

    @Override
    public Object call1(Object thisObj, Object arg, IEvalScope scope) {
        return function.call1(thisObj, arg, makeScope(scope));
    }

    @Override
    public Object call2(Object thisObj, Object arg1, Object arg2, IEvalScope scope) {
        return function.call2(thisObj, arg1, arg2, makeScope(scope));
    }

    @Override
    public Object call3(Object thisObj, Object arg1, Object arg2, Object arg3, IEvalScope scope) {
        return function.call3(thisObj, arg1, arg2, arg3, makeScope(scope));
    }

    @Override
    public IEvalFunction bind(Object thisObj) {
        return of(function.bind(thisObj));
    }
}
