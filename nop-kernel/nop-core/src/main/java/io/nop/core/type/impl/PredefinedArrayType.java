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
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.utils.JavaGenericTypeBuilder;

import java.util.List;

public class PredefinedArrayType extends PredefinedGenericType implements IArrayType {
    private final PredefinedGenericType componentType;
    private final Class arrayClass;

    // 所有数组实现的接口都一致，所以这里保存了静态变量
    private static List<IGenericType> interfaces;

    PredefinedArrayType(PredefinedGenericType componentType, Class arrayClass) {
        super(componentType.getTypeName() + "[]", StdDataType.LIST, "ARRAY_" + componentType.getPredefinedName());
        this.componentType = componentType;
        this.arrayClass = arrayClass;
    }

    public String getRawTypeName() {
        return componentType.getRawTypeName() + "[]";
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
    public Class getRawClass() {
        return arrayClass;
    }

    @Override
    public IGenericType getComponentType() {
        return componentType;
    }

    @Override
    public boolean isArray() {
        return true;
    }

    @Override
    public GenericTypeKind getKind() {
        return GenericTypeKind.ARRAY;
    }
}