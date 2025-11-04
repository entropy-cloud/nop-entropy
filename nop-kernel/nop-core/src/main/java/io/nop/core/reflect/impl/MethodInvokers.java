/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.impl;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.functions.NoArgEvalFunction;
import io.nop.core.lang.eval.functions.OneArgEvalFunction;
import io.nop.core.lang.eval.functions.ThreeArgEvalFunction;
import io.nop.core.lang.eval.functions.TwoArgEvalFunction;

import java.util.LinkedHashMap;
import java.util.Map;

public class MethodInvokers {
    static final Class<?>[] EMPTY_TYPES = new Class<?>[0];

    private Map<MethodInvokerKey, IEvalFunction> invokers = new LinkedHashMap<>();

    public MethodInvokers call0(boolean isStatic, String name, NoArgEvalFunction fn) {
        invokers.put(new MethodInvokerKey(isStatic, name, EMPTY_TYPES), fn);
        return this;
    }

    public MethodInvokers call1(boolean isStatic, String name, Class<?> argType, OneArgEvalFunction fn) {
        invokers.put(new MethodInvokerKey(isStatic, name, new Class<?>[]{argType}), fn);
        return this;
    }

    public MethodInvokers call2(boolean isStatic, String name, Class<?> argType1, Class<?> argType2,
                                TwoArgEvalFunction fn) {
        invokers.put(new MethodInvokerKey(isStatic, name, new Class<?>[]{argType1, argType2}), fn);
        return this;
    }

    public MethodInvokers call3(boolean isStatic, String name, Class<?> argType1, Class<?> argType2, Class<?> argType3,
                                ThreeArgEvalFunction fn) {
        invokers.put(new MethodInvokerKey(isStatic, name, new Class<?>[]{argType1, argType2, argType3}), fn);
        return this;
    }

    public MethodInvokers call(boolean isStatic, String name, Class<?>[] argTypes, IEvalFunction fn) {
        invokers.put(new MethodInvokerKey(isStatic, name, argTypes), fn);
        return this;
    }

    public IEvalFunction getInvoker(boolean isStatic, String name, Class<?>[] argTypes) {
        return invokers.get(new MethodInvokerKey(isStatic, name, argTypes));
    }
}