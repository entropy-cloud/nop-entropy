/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.type.impl;

import io.nop.api.core.json.IJsonString;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.type.IGenericType;
import io.nop.core.type.IRawTypeResolver;
import io.nop.core.type.ITypeScope;
import io.nop.core.type.PredefinedGenericTypes;

import java.io.Serializable;

public abstract class PredefinedGenericType implements IGenericType, IJsonString {
    private final String typeName;
    private final StdDataType stdDataType;

    // 对应于PredefinedGenericTypes常量类中的常量名
    private final String predefinedName;

    protected PredefinedGenericType(String typeName, StdDataType stdDataType, String predefinedName) {
        this.typeName = typeName;
        this.stdDataType = stdDataType;
        this.predefinedName = predefinedName;
    }

    public static PredefinedVariableType variableType(String typeName) {
        return new PredefinedVariableType(typeName);
    }

    public static PredefinedGenericType primitiveType(StdDataType stdDataType, String predefinedName) {
        return new PredefinedPrimitiveType(stdDataType, predefinedName);
    }

    public static PredefinedRawType javaType(Class<?> clazz, String predefinedName) {
        return new PredefinedRawType(clazz.getTypeName(), clazz, predefinedName);
    }

    public static PredefinedRawType simpleType(StdDataType stdDataType, String predefinedName) {
        return new PredefinedRawType(stdDataType.getClassName(), stdDataType.getJavaClass(), predefinedName);
    }

    public static PredefinedGenericType arrayType(PredefinedGenericType componentType, Class<?> arrayClass) {
        return new PredefinedArrayType(componentType, arrayClass);
    }

    public static PredefinedGenericType parameterizedType(String predefinedName, PredefinedRawType rawType,
                                                          PredefinedGenericType... paramType) {
        return new PredefinedParameterizedType(predefinedName, rawType, CollectionHelper.buildImmutableList(paramType));
    }

    public String getTypeName() {
        return typeName;
    }

    protected Object writeReplace() {
        SerializableType ss = new SerializableType();
        ss.typeName = getTypeName();
        return ss;
    }

    private static final class SerializableType implements Serializable {

        private static final long serialVersionUID = 1L;

        private String typeName;

        private Object readResolve() {
            return PredefinedGenericTypes.getPredefinedType(typeName);
        }
    }

    /**
     * 预定义的泛型对象一般不包含未resolve的泛型变量
     */
    @Override
    public IGenericType refine(ITypeScope resolver) {
        return this;
    }

    @Override
    public boolean containsTypeVariable() {
        return false;
    }

    @Override
    public boolean isResolved() {
        return true;
    }

    @Override
    public void resolve(IRawTypeResolver resolver) {

    }

    @Override
    public final boolean isPredefined() {
        return true;
    }

    public String getRawTypeName() {
        return typeName;
    }

    @Override
    public StdDataType getStdDataType() {
        return stdDataType;
    }

    public String toString() {
        return typeName;
    }

    public String getPredefinedName() {
        return predefinedName;
    }

    public boolean isArray() {
        return getRawClass().isArray();
    }

    @Override
    public Class<?> getRawClass() {
        return getStdDataType().getJavaClass();
    }
}