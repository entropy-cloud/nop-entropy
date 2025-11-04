/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.type.impl;

import io.nop.api.core.util.Guard;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.StringHelper;
import io.nop.core.type.GenericTypeKind;
import io.nop.core.type.IGenericType;
import io.nop.core.type.IRawTypeResolver;
import io.nop.core.type.ITypeScope;
import io.nop.core.type.ITypeVariableBound;
import io.nop.core.type.PredefinedGenericTypes;

import jakarta.annotation.Nonnull;

public class GenericTypeVariableBoundImpl extends AbstractGenericType implements ITypeVariableBound {
    private final String name;
    private final IGenericType upperBound;
    private final IGenericType lowerBound;

    private String typeName;

    public GenericTypeVariableBoundImpl(String name, IGenericType upperBound, IGenericType lowerBound) {
        Guard.checkArgument(StringHelper.isSafeAsciiToken(name), "invalid type variable name:" + name);
        this.name = name;
        this.upperBound = upperBound == null ? PredefinedGenericTypes.ANY_TYPE : upperBound;
        this.lowerBound = lowerBound;
    }

    public GenericTypeKind getKind() {
        return GenericTypeKind.TYPE_VARIABLE_BOUND;
    }

    public Class<?> getRawClass() {
        return upperBound.getRawClass();
    }

    @Override
    public boolean containsTypeVariable() {
        return true;
    }

    public IGenericType refine(ITypeScope resolver) {
        IGenericType type = resolver.resolveVariable(name);
        if (type != null)
            return type;
        return this;
    }

    @Override
    public void resolve(IRawTypeResolver resolver) {
        upperBound.resolve(resolver);
        if (lowerBound != null)
            lowerBound.resolve(resolver);
    }

    public IGenericType getResolvedType() {
        return upperBound.getResolvedType();
    }

    public String getName() {
        return name;
    }

    public String getTypeName() {
        if (typeName == null) {
            StringBuilder sb = new StringBuilder();
            sb.append(name);
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

    @Override
    public String getRawTypeName() {
        return name;
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
