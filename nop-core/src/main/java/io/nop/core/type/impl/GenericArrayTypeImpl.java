/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.type.impl;

import io.nop.commons.type.StdDataType;
import io.nop.core.type.GenericTypeKind;
import io.nop.core.type.IArrayType;
import io.nop.core.type.IGenericType;
import io.nop.core.type.IRawTypeResolver;
import io.nop.core.type.ITypeScope;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.utils.JavaGenericTypeBuilder;

import java.lang.reflect.Array;
import java.util.List;

public class GenericArrayTypeImpl extends AbstractGenericType implements IArrayType {
    private final IGenericType componentType;
    private final String typeName;
    private Class<?> rawClass;

    // 所有数组实现的接口都一致，所以这里保存了静态变量
    private static List<IGenericType> interfaces;

    public GenericArrayTypeImpl(IGenericType componentType) {
        this.componentType = componentType;
        this.typeName = componentType.getTypeName() + "[]";
    }

    public Class<?> getRawClass() {
        if (rawClass == null) {
            rawClass = Array.newInstance(componentType.getRawClass(), 0).getClass();
        }
        return rawClass;
    }

    @Override
    public String getRawTypeName() {
        return componentType.getRawTypeName() + "[]";
    }

    public String getTypeName() {
        return typeName;
    }

    public List<IGenericType> getInterfaces() {
        if (interfaces == null)
            this.interfaces = JavaGenericTypeBuilder.buildGenericTypes(Object[].class.getInterfaces());
        return this.interfaces;
    }

    public IGenericType getSuperType() {
        // String[].class.getSuperClass() 返回Object.class
        return PredefinedGenericTypes.ANY_TYPE;
    }

    @Override
    public boolean isResolved() {
        return componentType.isResolved();
    }

    public void resolve(IRawTypeResolver resolver) {
        componentType.resolve(resolver);
    }

    @Override
    public IGenericType refine(ITypeScope resolver) {
        IGenericType type = componentType.refine(resolver);
        if (type != componentType)
            return new GenericArrayTypeImpl(type);
        return type;
    }

    @Override
    public StdDataType getStdDataType() {
        return StdDataType.LIST;
    }

    public IGenericType getComponentType() {
        return componentType;
    }

    public boolean isArray() {
        return true;
    }

    public GenericTypeKind getKind() {
        return GenericTypeKind.ARRAY;
    }
}
