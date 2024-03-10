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
import io.nop.core.type.IGenericType;
import io.nop.core.type.ITypeScope;
import io.nop.core.type.ITypeVariable;

public class GenericTypeVariableImpl extends AbstractGenericType implements ITypeVariable {
    private final String name;

    public GenericTypeVariableImpl(String name) {
        this.name = name;
    }

    public IGenericType refine(ITypeScope resolver) {
        IGenericType type = resolver.resolveVariable(name);
        if (type != null)
            return type;
        return this;
    }

    @Override
    public boolean containsTypeVariable() {
        return true;
    }

    public GenericTypeKind getKind() {
        return GenericTypeKind.TYPE_VARIABLE;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getRawTypeName() {
        return name;
    }

    public String getTypeName() {
        return name;
    }

    @Override
    public StdDataType getStdDataType() {
        return StdDataType.ANY;
    }
}