/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.type.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import io.nop.core.type.GenericTypeKind;
import io.nop.core.type.IGenericType;
import io.nop.core.type.IParameterizedType;
import io.nop.core.type.IRawTypeResolver;
import io.nop.core.type.ITypeScope;
import io.nop.core.type.utils.GenericTypeHelper;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static io.nop.core.CoreErrors.ARG_TYPE_NAME;
import static io.nop.core.CoreErrors.ERR_TYPE_NOT_ARRAY_OR_LIST_TYPE;
import static io.nop.core.CoreErrors.ERR_TYPE_NOT_MAP_TYPE;

public class GenericParameterizedType extends AbstractGenericType implements IParameterizedType {
    private final IGenericType rawType;
    private final List<IGenericType> typeParameters;

    private IGenericType superType;
    private List<IGenericType> interfaces;
    private IGenericType collectionType;
    private IGenericType mapType;

    private String typeName;

    public GenericParameterizedType(IGenericType rawType, List<IGenericType> typeParameters) {
        this.rawType = rawType;
        this.typeParameters = typeParameters;
    }

    public GenericTypeKind getKind() {
        return GenericTypeKind.PARAMETERIZED_TYPE;
    }

    public String getTypeName() {
        if (typeName == null) {
            String buildName = GenericTypeHelper.buildParameterizedTypeName(rawType, typeParameters);
            if (isResolved())
                this.typeName = buildName;
            return buildName;
        }
        return typeName;
    }

    @Override
    public IGenericType refine(ITypeScope resolver) {
        IGenericType refinedRaw = rawType.refine(resolver);
        List<IGenericType> refinedParams = GenericTypeHelper.refineTypes(typeParameters, resolver);
        if (refinedParams != typeParameters || refinedRaw != rawType)
            return new GenericParameterizedType(refinedRaw, refinedParams);
        return this;
    }

    @Override
    public boolean isResolved() {
        if (!rawType.isResolved())
            return false;

        for (IGenericType param : typeParameters) {
            if (!param.isResolved())
                return false;
        }
        return true;
    }

    @Override
    public void resolve(IRawTypeResolver resolver) {
        rawType.resolve(resolver);
        typeParameters.forEach(type -> type.resolve(resolver));
    }

    public IGenericType getSuperType() {
        if (superType == null) {
            IGenericType rawSuper = rawType.getSuperType();
            this.superType = GenericTypeHelper.refineType(rawSuper, rawType.getTypeParameters(), typeParameters);
        }
        return superType;
    }

    public List<IGenericType> getInterfaces() {
        if (interfaces == null) {
            List<IGenericType> rawInterfaces = rawType.getInterfaces();
            this.interfaces = GenericTypeHelper.refineTypes(rawInterfaces, rawType.getTypeParameters(), typeParameters);
        }
        return this.interfaces;
    }

    private IGenericType getCollectionType() {
        if (collectionType == null) {
            this.collectionType = getGenericType(Collection.class);
            if (this.collectionType == null)
                throw new NopException(ERR_TYPE_NOT_ARRAY_OR_LIST_TYPE).param(ARG_TYPE_NAME, getTypeName());
        }
        return collectionType;
    }

    private IGenericType getMapType() {
        if (mapType == null) {
            this.mapType = getGenericType(Map.class);
            if (this.mapType == null)
                throw new NopException(ERR_TYPE_NOT_MAP_TYPE).param(ARG_TYPE_NAME, getTypeName());
        }
        return mapType;
    }

    public IGenericType getComponentType() {
        return getCollectionType().getTypeParameters().get(0);
    }

    public IGenericType getMapKeyType() {
        return getMapType().getTypeParameters().get(0);
    }

    public IGenericType getMapValueType() {
        return getMapType().getTypeParameters().get(1);
    }

    @Override
    public boolean isFunctionalClass() {
        return rawType.isFunctionalClass();
    }

    @Override
    public List<IGenericType> getFuncArgTypes() {
        List<IGenericType> argTypes = rawType.getFuncArgTypes();
        return GenericTypeHelper.refineTypes(argTypes, rawType.getTypeParameters(), typeParameters);
    }

    @Override
    public IGenericType getFuncReturnType() {
        IGenericType returnType = rawType.getFuncReturnType();
        return GenericTypeHelper.refineType(returnType, rawType.getTypeParameters(), typeParameters);
    }

    @Override
    public boolean isCollectionLike() {
        return rawType.isCollectionLike();
    }

    @Override
    public boolean isListLike() {
        return rawType.isListLike();
    }

    @Override
    public boolean isSetLike() {
        return rawType.isSetLike();
    }

    @Override
    public boolean isMapLike() {
        return rawType.isMapLike();
    }

    @Override
    public IGenericType getGenericType(String typeName) {
        IGenericType type = rawType.getGenericType(typeName);

        return GenericTypeHelper.refineType(type, rawType.getTypeParameters(), this.getTypeParameters());
    }

    @Override
    public IGenericType getGenericType(Class<?> clazz) {
        IGenericType type = rawType.getGenericType(clazz);

        return GenericTypeHelper.refineType(type, rawType.getTypeParameters(), this.getTypeParameters());
    }

    @Override
    public String getRawTypeName() {
        return rawType.getRawTypeName();
    }

    @Override
    public StdDataType getStdDataType() {
        return rawType.getStdDataType();
    }

    @Override
    public boolean isArray() {
        return rawType.isArray();
    }

    @Override
    public Class getRawClass() {
        return rawType.getRawClass();
    }

    @Override
    public IGenericType getRawType() {
        return rawType;
    }

    @Override
    public List<IGenericType> getTypeParameters() {
        return typeParameters;
    }
}
