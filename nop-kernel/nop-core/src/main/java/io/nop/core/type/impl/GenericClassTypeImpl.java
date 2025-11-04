/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.type.impl;

import io.nop.commons.type.StdDataType;
import io.nop.commons.util.StringHelper;
import io.nop.core.type.GenericClassKind;
import io.nop.core.type.GenericTypeKind;
import io.nop.core.type.IClassType;
import io.nop.core.type.IGenericType;
import io.nop.core.type.ITypeScope;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.utils.GenericTypeHelper;

import java.util.Collections;
import java.util.List;

public class GenericClassTypeImpl extends AbstractGenericType implements IClassType {
    private final GenericClassKind classKind;
    private final String className;
    private final List<IGenericType> typeParameters;
    private final IGenericType extendsType;
    private final List<IGenericType> implementedTypes;

    private ContainerTypeData containerTypeData;

    private String typeName;

    public GenericClassTypeImpl(GenericClassKind classKind, String className, List<IGenericType> typeParameters,
                                IGenericType extendsType, List<IGenericType> implementedTypes) {
        this.className = className;
        this.classKind = classKind;
        this.typeParameters = typeParameters == null ? Collections.emptyList() : typeParameters;
        this.extendsType = extendsType == null ? PredefinedGenericTypes.ANY_TYPE : extendsType;
        this.implementedTypes = implementedTypes == null ? Collections.emptyList() : implementedTypes;

    }

    @Override
    public String getTypeName() {
        if (typeName == null)
            typeName = buildTypeName();
        return typeName;
    }

    private String buildTypeName() {
        StringBuilder sb = new StringBuilder();
        sb.append(classKind.getText()).append(" ");
        sb.append(className);
        if (!typeParameters.isEmpty()) {
            sb.append('<');
            sb.append(StringHelper.join(typeParameters, ","));
            sb.append('>');
        }
        if (extendsType != PredefinedGenericTypes.ANY_TYPE) {
            sb.append(" extends ").append(extendsType.getTypeName());
        }

        if (!implementedTypes.isEmpty()) {
            sb.append(" implements ");
            sb.append(StringHelper.join(implementedTypes, ","));
        }
        return sb.toString();
    }

    @Override
    public String getRawTypeName() {
        return className;
    }

    @Override
    public StdDataType getStdDataType() {
        return StdDataType.UNKNOWN;
    }

    @Override
    public GenericTypeKind getKind() {
        return GenericTypeKind.CLASS_TYPE;
    }

    @Override
    public IGenericType getRawType() {
        return PredefinedGenericTypes.UNKNOWN_TYPE;
    }

    @Override
    public List<IGenericType> getTypeParameters() {
        return typeParameters;
    }

    @Override
    public boolean isPredefined() {
        return false;
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    private ContainerTypeData makeContainerTypeData() {
        if (containerTypeData == null) {
            this.containerTypeData = ContainerTypeData.buildFrom(this);
        }
        return containerTypeData;
    }

    @Override
    public boolean isListLike() {
        return makeContainerTypeData().isListLike();
    }

    @Override
    public boolean isSetLike() {
        return makeContainerTypeData().isSetLike();
    }

    @Override
    public boolean isMapLike() {
        return makeContainerTypeData().isMapLike();
    }

    @Override
    public IGenericType getMapKeyType() {
        return makeContainerTypeData().getMapKeyType(getTypeName());
    }

    @Override
    public IGenericType getMapValueType() {
        return makeContainerTypeData().getMapValueType(getTypeName());
    }

    @Override
    public IGenericType refine(ITypeScope resolver) {
        IGenericType refinedExtends = extendsType.refine(resolver);
        List<IGenericType> refinedParams = GenericTypeHelper.refineTypes(typeParameters, resolver);
        List<IGenericType> refinedImplemented = GenericTypeHelper.refineTypes(implementedTypes, resolver);
        if (refinedParams != typeParameters || refinedExtends != extendsType || refinedImplemented != implementedTypes)
            return new GenericClassTypeImpl(classKind, className, refinedParams, extendsType, implementedTypes);
        return this;
    }

    public IGenericType getGenericType(String rawTypeName) {
        return GenericTypeHelper.findGenericType(this, rawTypeName);
    }

    @Override
    public boolean isResolved() {
        return false;
    }

    @Override
    public IGenericType getResolvedType() {
        return this;
    }

    @Override
    public boolean isAssignableFrom(Class clazz) {
        return false;
    }

    @Override
    public boolean isAssignableTo(Class clazz) {
        return extendsType.isAssignableTo(clazz)
                || implementedTypes.stream().anyMatch(type -> type.isAssignableTo(clazz));
    }

    @Override
    public boolean isAssignableTo(IGenericType type) {
        if (this == type)
            return true;

        return extendsType.isAssignableTo(type) || implementedTypes.stream().anyMatch(t -> t.isAssignableTo(type));
    }

    @Override
    public boolean isTypeOrSubTypeOf(Class<?> clazz) {
        return isAssignableTo(clazz);
    }

    @Override
    public boolean isTypeOrSuperTypeOf(Class<?> clazz) {
        return isAssignableFrom(clazz);
    }

    @Override
    public boolean isInstance(Object obj) {
        return false;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public boolean isInterface() {
        return classKind.isInterface();
    }

    @Override
    public boolean isAnnotation() {
        return classKind == GenericClassKind.ANNOTATION;
    }

    @Override
    public boolean isEnum() {
        return classKind == GenericClassKind.ENUM;
    }

    @Override
    public boolean isAbstract() {
        return classKind.isAbstract();
    }
}