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

import java.lang.reflect.Constructor;

class ConstructorInvoker implements IEvalFunction {
    private final Constructor executor;

    public ConstructorInvoker(Constructor executor) {
        this.executor = executor;
        ReflectionHelper.makeAccessible(executor);
    }

    @Override
    public Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
        return ClassHelper.newInstance(executor, args);
    }
}