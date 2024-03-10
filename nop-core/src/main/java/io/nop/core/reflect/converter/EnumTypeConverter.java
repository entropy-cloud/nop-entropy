/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.converter;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.convert.ITypeConverter;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.reflect.impl.EnumValueOfMethodInvoker;
import io.nop.core.reflect.impl.MethodInvoker;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Function;

public class EnumTypeConverter implements ITypeConverter {
    private final Class<? extends Enum> enumClass;
    private final IEvalFunction factoryMethod;

    public EnumTypeConverter(Class<? extends Enum> enumClass) {
        this.enumClass = enumClass;
        this.factoryMethod = findFactoryMethod(enumClass);
    }

    @Override
    public Object convert(Object value, Function<ErrorCode, NopException> errorFactory) {
        if (value == null)
            return null;

        if (enumClass.isInstance(value))
            return value;

        if (value instanceof String) {
            return factoryMethod.call1(null, value, DisabledEvalScope.INSTANCE);
        }
        return ConvertHelper.handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, enumClass, value, errorFactory);
    }

    IEvalFunction findFactoryMethod(Class<? extends Enum> enumClass) {
        for (Method method : enumClass.getMethods()) {
            if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != String.class)
                continue;

            if (Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers())) {
                if (method.isAnnotationPresent(StaticFactoryMethod.class)) {
                    return new MethodInvoker(method);
                }
            }
        }
        return new EnumValueOfMethodInvoker(enumClass);
    }
}
