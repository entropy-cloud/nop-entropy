/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.type.utils;

import io.nop.core.type.IGenericType;

import java.lang.reflect.Type;

public class JavaTypeHelper {
    public static boolean isInstance(Type type, Object bean) {
        if (type == null || bean == null)
            return false;
        if (type instanceof IGenericType)
            return ((IGenericType) type).isInstance(bean);

        return JavaGenericTypeHelper.getRawType(type).isInstance(bean);
    }

    public static Class<?> getRawClass(Type type) {
        if (type == null)
            return null;

        Class<?> rawType;
        if (type instanceof IGenericType) {
            rawType = ((IGenericType) type).getRawClass();
        } else {
            rawType = JavaGenericTypeHelper.getRawType(type);
        }
        return rawType;
    }
}
