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
import io.nop.core.type.IRawTypeResolver;
import io.nop.core.type.IWildcardType;
import io.nop.core.type.PredefinedGenericTypes;

import jakarta.annotation.Nonnull;

public class GenericWildcardTypeImpl extends AbstractGenericType implements IWildcardType {
    private final IGenericType upperBound;
    private final IGenericType lowerBound;

    private String typeName;

    GenericWildcardTypeImpl(IGenericType upperBound, IGenericType lowerBound) {
        this.upperBound = upperBound == null ? PredefinedGenericTypes.ANY_TYPE : upperBound;
        this.lowerBound = lowerBound;
    }

    public static IWildcardType valueOf(IGenericType upperBound, IGenericType lowerBound) {
        if (lowerBound == null) {
            if (upperBound == null || upperBound == PredefinedGenericTypes.ANY_TYPE)
                return PredefinedWildcardType.NO_BOUND_WILDCARD_TYPE;
        }
        return new GenericWildcardTypeImpl(upperBound, lowerBound);
    }

    public GenericTypeKind getKind() {
        return GenericTypeKind.WILDCARD;
    }

    public String getTypeName() {
        if (typeName == null) {
            StringBuilder sb = new StringBuilder();
            sb.append('?');
            if (upperBound != PredefinedGenericTypes.ANY_TYPE) {
                sb.append(" extends ").append(upperBound);
            }
            if (lowerBound != null) {
                sb.append(" super ").append(lowerBound);
            }
            typeName = sb.toString();
        }
        return typeName;
    }

    public boolean containsTypeVariable() {
        if (upperBound.containsTypeVariable())
            return true;
        if (lowerBound != null)
            return lowerBound.containsTypeVariable();
        return false;
    }

    public void resolve(IRawTypeResolver resolver) {
        upperBound.resolve(resolver);
        if (lowerBound != null)
            lowerBound.resolve(resolver);
    }

    public IGenericType getResolvedType() {
        return upperBound.getResolvedType();
    }

    public Class<?> getRawClass() {
        return upperBound.getRawClass();
    }

    @Override
    public String getRawTypeName() {
        return "?";
    }

    @Override
    public StdDataType getStdDataType() {
        return upperBound.getStdDataType();
    }

    @Nonnull
    @Override
    public IGenericType getUpperBound() {
        return upperBound;
    }

    @Override
    public IGenericType getLowerBound() {
        return lowerBound;
    }
}
