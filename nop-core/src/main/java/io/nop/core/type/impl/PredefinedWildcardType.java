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
import io.nop.core.type.IWildcardType;
import io.nop.core.type.PredefinedGenericTypes;

import jakarta.annotation.Nonnull;

public class PredefinedWildcardType extends PredefinedGenericType implements IWildcardType {
    public static final PredefinedWildcardType NO_BOUND_WILDCARD_TYPE = new PredefinedWildcardType();

    public PredefinedWildcardType() {
        super("?", StdDataType.ANY, "NO_BOUND_WILDCARD_TYPE");
    }

    public String toString() {
        return "?";
    }

    public GenericTypeKind getKind() {
        return GenericTypeKind.WILDCARD;
    }

    @Nonnull
    @Override
    public IGenericType getUpperBound() {
        return PredefinedGenericTypes.ANY_TYPE;
    }

    @Override
    public IGenericType getLowerBound() {
        return null;
    }
}
