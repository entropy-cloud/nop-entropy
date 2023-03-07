/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.type.impl;

import io.nop.commons.type.StdDataType;
import io.nop.core.type.GenericTypeKind;
import io.nop.core.type.IGenericType;
import io.nop.core.type.IRawType;
import io.nop.core.type.utils.GenericTypeHelper;
import io.nop.core.type.utils.JavaGenericTypeBuilder;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;

public class PredefinedRawType extends PredefinedGenericType implements IRawType {
    private final Class<?> rawClass;

    private List<IGenericType> typeParameters;
    private ContainerTypeData containerTypeData;
    private FunctionalTypeData functionalTypeData;
    private IGenericType superType;
    private List<IGenericType> interfaces;

    public PredefinedRawType(String typeName, Class<?> rawClass, String predefinedName) {
        super(typeName, StdDataType.fromJavaClass(rawClass), predefinedName);
        this.rawClass = rawClass;
    }

    public PredefinedRawType(String typeName, StdDataType stdDataType, Class<?> rawClass, String predefinedName) {
        super(typeName, stdDataType, predefinedName);
        this.rawClass = rawClass;
    }

    public GenericTypeKind getKind() {
        return GenericTypeKind.RAW_TYPE;
    }

    // tell cpd to start ignoring code - CPD-OFF
    public String toString() {
        return rawClass.getName();
    }

    public String getRawTypeName() {
        return rawClass.getName();
    }

    private ContainerTypeData getContainerTypeData() {
        ContainerTypeData data = this.containerTypeData;
        if (data != null)
            return data;

        this.containerTypeData = data = ContainerTypeData.buildFrom(this);
        return data;
    }

    private FunctionalTypeData getFunctionalTypeData() {
        FunctionalTypeData data = this.functionalTypeData;
        if (data != null)
            return data;

        this.functionalTypeData = data = FunctionalTypeData.buildFrom(rawClass);
        return data;
    }

    public boolean isCollectionLike() {
        return getContainerTypeData().isCollectionLike();
    }

    public boolean isSetLike() {
        return getContainerTypeData().isSetLike();
    }

    public boolean isListLike() {
        return getContainerTypeData().isListLike();
    }

    public boolean isMapLike() {
        return getContainerTypeData().isMapLike();
    }

    public IGenericType getComponentType() {
        return getContainerTypeData().getComponentType(getTypeName());
    }

    public IGenericType getMapKeyType() {
        return getContainerTypeData().getMapKeyType(getTypeName());
    }

    public IGenericType getMapValueType() {
        return getContainerTypeData().getMapValueType(getTypeName());
    }

    public boolean isFunctionalClass() {
        return getFunctionalTypeData().isFunctional();
    }

    public List<IGenericType> getFuncArgTypes() {
        return getFunctionalTypeData().getFuncArgTypes(getTypeName());
    }

    public IGenericType getFuncReturnType() {
        return getFunctionalTypeData().getFuncReturnType(getTypeName());
    }

    @Override
    public Class<?> getRawClass() {
        return rawClass;
    }

    @Override
    public List<IGenericType> getTypeParameters() {
        if (typeParameters == null)
            typeParameters = JavaGenericTypeBuilder.buildTypeBounds(rawClass.getTypeParameters(), new HashSet<>());
        return typeParameters;
    }

    @Override
    public IGenericType getSuperType() {
        if (superType == null) {
            Type superClass = rawClass.getGenericSuperclass();
            if (superClass != null) {
                superType = JavaGenericTypeBuilder.buildGenericType(superClass);
            }
        }
        return superType;
    }

    @Override
    public List<IGenericType> getInterfaces() {
        if (interfaces == null) {
            this.interfaces = JavaGenericTypeBuilder.buildGenericTypes(rawClass.getGenericInterfaces());
        }
        return this.interfaces;
    }

    public IGenericType getGenericType(Class<?> clazz) {
        return GenericTypeHelper.findGenericType(this, clazz);
    }

    public IGenericType getGenericType(String rawTypeName) {
        return GenericTypeHelper.findGenericType(this, rawTypeName);
    }

    // resume CPD analysis - CPD-ON
}