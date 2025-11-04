/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.impl;

import io.nop.commons.util.ClassHelper;
import io.nop.commons.util.ReflectionHelper;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;

import java.lang.reflect.Method;

public class MethodInvoker implements IEvalFunction {
    private final Method executor;

    public MethodInvoker(Method executor) {
        this.executor = executor;
        ReflectionHelper.makeAccessible(executor);
    }

    public String toString() {
        return executor.toString();
    }

    @Override
    public Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
        return ClassHelper.invoke(executor, thisObj, args);
    }
}