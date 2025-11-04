/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.type.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.ReflectionHelper;
import io.nop.core.type.IGenericType;
import io.nop.core.type.utils.JavaGenericTypeBuilder;

import java.lang.reflect.Method;
import java.util.List;

import static io.nop.core.CoreErrors.ARG_TYPE_NAME;
import static io.nop.core.CoreErrors.ERR_TYPE_NOT_FUNCTION_TYPE;

public class FunctionalTypeData {
    public static FunctionalTypeData NOT_FUNCTION = new FunctionalTypeData(null, null);

    private final List<IGenericType> funcArgTypes;
    private final IGenericType funcReturnType;

    public FunctionalTypeData(List<IGenericType> funcArgTypes, IGenericType funcReturnType) {
        this.funcArgTypes = funcArgTypes;
        this.funcReturnType = funcReturnType;
    }

    public boolean isFunctional() {
        return funcReturnType != null;
    }

    public static FunctionalTypeData buildFrom(Class clazz) {
        if (!clazz.isAnnotationPresent(FunctionalInterface.class))
            return NOT_FUNCTION;

        Method method = ReflectionHelper.getFunctionalMethod(clazz);
        List<IGenericType> argTypes = JavaGenericTypeBuilder.buildGenericTypes(method.getGenericParameterTypes());
        IGenericType returnType = JavaGenericTypeBuilder.buildGenericType(method.getGenericReturnType());
        return new FunctionalTypeData(argTypes, returnType);
    }

    public List<IGenericType> getFuncArgTypes(String typeName) {
        if (funcArgTypes == null)
            throw new NopException(ERR_TYPE_NOT_FUNCTION_TYPE).param(ARG_TYPE_NAME, typeName);
        return funcArgTypes;
    }

    public IGenericType getFuncReturnType(String typeName) {
        if (funcReturnType == null)
            throw new NopException(ERR_TYPE_NOT_FUNCTION_TYPE).param(ARG_TYPE_NAME, typeName);
        return funcReturnType;
    }
}