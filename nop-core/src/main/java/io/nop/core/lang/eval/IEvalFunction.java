/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.eval;

import io.nop.core.lang.eval.functions.BindEvalFunction;

/**
 * 可执行函数接口，类似JavaScript中的Function对象
 */
@FunctionalInterface
public interface IEvalFunction {
    Object[] EMPTY_ARGS = new Object[0];

    Object invoke(Object thisObj, Object[] args, IEvalScope scope);

    default Object call0(Object thisObj, IEvalScope scope) {
        return invoke(thisObj, EMPTY_ARGS, scope);
    }

    default Object call1(Object thisObj, Object arg, IEvalScope scope) {
        return invoke(thisObj, new Object[]{arg}, scope);
    }

    default Object call2(Object thisObj, Object arg1, Object arg2, IEvalScope scope) {
        return invoke(thisObj, new Object[]{arg1, arg2}, scope);
    }

    default Object call3(Object thisObj, Object arg1, Object arg2, Object arg3, IEvalScope scope) {
        return invoke(thisObj, new Object[]{arg1, arg2, arg3}, scope);
    }

    default IEvalFunction bind(Object thisObj) {
        return new BindEvalFunction(this, thisObj);
    }
}