/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFieldModel;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.bean.IBeanPropertyModel;
import io.nop.core.type.IGenericType;

import java.util.function.Function;

public interface IBeanClassIntrospection {

    IFunctionModel getInitMethod(IClassModel classModel);

    IFunctionModel getDestroyMethod(IClassModel classModel);

    IFunctionModel getBeanMethod(IClassModel classModel);

    IFunctionModel getRefreshConfigMethod(IClassModel classModel);

    boolean isAllowedConfigVarType(IGenericType type);

    Object convertTo(Class<?> targetClass, Object value, Function<ErrorCode, NopException> errorFactory);

    BeanInjectInfo getArgumentInject(IFunctionArgument argModel);

    BeanInjectInfo getPropertyInject(String propName, IFunctionModel propModel);

    BeanInjectInfo getPropertyInject(IBeanPropertyModel propModel);

    BeanInjectInfo getFieldInject(IFieldModel fieldModel);
}