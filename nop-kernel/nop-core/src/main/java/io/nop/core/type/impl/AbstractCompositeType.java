/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.type.impl;

import io.nop.commons.type.StdDataType;
import io.nop.core.type.IGenericType;
import io.nop.core.type.IRawTypeResolver;

import java.util.List;

public abstract class AbstractCompositeType extends AbstractGenericType {
    private final List<IGenericType> subTypes;

    public AbstractCompositeType(List<IGenericType> subTypes) {
        this.subTypes = subTypes;
    }

    @Override
    public List<IGenericType> getSubTypes() {
        return subTypes;
    }

    public void resolve(IRawTypeResolver resolver) {
        if (hasTypeParameter()) {
            getTypeParameters().forEach(type -> type.resolve(resolver));
        }

        getSubTypes().forEach(type -> type.resolve(resolver));
    }

    @Override
    public boolean isResolved() {
        for (IGenericType subType : subTypes) {
            if (!subType.isResolved())
                return false;
        }
        return true;
    }

    public StdDataType getStdDataType() {
        return StdDataType.ANY;
    }
}
