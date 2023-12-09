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
import io.nop.core.type.IRawTypeReference;
import io.nop.core.type.IRawTypeResolver;

import static io.nop.core.CoreErrors.ARG_TYPE_NAME;
import static io.nop.core.CoreErrors.ERR_TYPE_REFERENCE_NOT_RESOLVED;

public class GenericRawTypeReferenceImpl extends AbstractGenericType implements IRawTypeReference {
    private final String typeName;
    private IGenericType rawType;

    public GenericRawTypeReferenceImpl(String typeName) {
        this.typeName = typeName;
    }

    public GenericTypeKind getKind() {
        return GenericTypeKind.RAW_TYPE_REF;
    }

    @Override
    public String getRawTypeName() {
        if (rawType != null)
            return rawType.getRawTypeName();
        return typeName;
    }

    public String getClassName() {
        if (rawType != null)
            return rawType.getClassName();
        return typeName;
    }

    @Override
    public StdDataType getStdDataType() {
        return rawType == null ? StdDataType.ANY : rawType.getStdDataType();
    }

    @Override
    public IGenericType getGenericType(String rawTypeName) {
        return getResolvedType().getGenericType(rawTypeName);
    }

    public IGenericType getGenericType(Class clazz) {
        return getResolvedType().getGenericType(clazz);
    }

    public boolean isResolved() {
        return rawType != null && rawType.isResolved();
    }

    public Class<?> getRawClass() {
        return getResolvedType().getRawClass();
    }

    @Override
    public void resolve(IRawTypeResolver resolver) {
        // 一旦resolve就不允许再变化
        if (this.rawType == null) {
            this.rawType = resolver.resolveRawType(typeName);
        } else {
            rawType.resolve(resolver);
        }
    }

    @Override
    public String getTypeName() {
        if (rawType != null)
            return rawType.getTypeName();
        return typeName;
    }

    @Override
    public IGenericType getResolvedType() throws NopException {
        if (rawType == null)
            throw new NopException(ERR_TYPE_REFERENCE_NOT_RESOLVED).param(ARG_TYPE_NAME, getTypeName());
        return rawType;
    }
}
