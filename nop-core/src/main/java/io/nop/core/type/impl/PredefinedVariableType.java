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
import io.nop.core.type.ITypeScope;
import io.nop.core.type.ITypeVariable;

public class PredefinedVariableType extends PredefinedGenericType implements ITypeVariable {
    public PredefinedVariableType(String typeName) {
        super(typeName, StdDataType.ANY, "VARIABLE_" + typeName + "_TYPE");
    }

    public GenericTypeKind getKind() {
        return GenericTypeKind.TYPE_VARIABLE;
    }

    @Override
    public String getName() {
        return getTypeName();
    }

    @Override
    public boolean containsTypeVariable() {
        return true;
    }

    @Override
    public IGenericType refine(ITypeScope resolver) {
        IGenericType type = resolver.resolveVariable(getName());
        if (type != null)
            return type;
        return this;
    }
}
