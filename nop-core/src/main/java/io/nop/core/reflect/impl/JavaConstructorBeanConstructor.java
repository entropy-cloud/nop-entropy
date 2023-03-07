/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.impl;

import io.nop.commons.util.ClassHelper;
import io.nop.commons.util.ReflectionHelper;
import io.nop.core.reflect.bean.IBeanConstructor;

import java.lang.reflect.Constructor;

public class JavaConstructorBeanConstructor implements IBeanConstructor {
    static final Object[] EMPTY_ARGS = new Object[0];

    private final Constructor<?> method;

    public JavaConstructorBeanConstructor(Constructor<?> method) {
        this.method = method;
        ReflectionHelper.makeAccessible(method);
    }

    @Override
    public Object newInstance() {
        return ClassHelper.newInstance(method, EMPTY_ARGS);
    }
}
