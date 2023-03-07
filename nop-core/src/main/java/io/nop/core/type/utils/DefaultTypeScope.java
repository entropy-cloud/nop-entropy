/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.type.utils;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.core.type.IGenericType;
import io.nop.core.type.ITypeScope;
import io.nop.core.type.ITypeVariableBound;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.core.CoreErrors.ARG_VAR_NAME;
import static io.nop.core.CoreErrors.ERR_TYPE_NOT_TYPE_VARIABLE;
import static io.nop.core.CoreErrors.ERR_TYPE_REDEFINE_TYPE_VARIABLE;

public class DefaultTypeScope implements ITypeScope {
    private Map<String, IGenericType> types;

    public DefaultTypeScope() {
        types = new HashMap<>();
    }

    protected DefaultTypeScope(Map<String, IGenericType> types) {
        this.types = Guard.notNull(types, "types is null");
    }

    public static DefaultTypeScope fromMap(Map<String, IGenericType> types) {
        return new DefaultTypeScope(types);
    }

    public static DefaultTypeScope fromTypeParameters(List<IGenericType> typeParams) {
        DefaultTypeScope scope = new DefaultTypeScope();
        scope.addTypeParameters(typeParams);
        return scope;
    }

    public void addTypeParameters(List<IGenericType> types) {
        for (IGenericType type : types) {
            if (type.isTypeVariableBound()) {
                addVariable(((ITypeVariableBound) type).getName(), type);
            } else if (type.isTypeVariable()) {
                addVariable(type.getTypeName(), type);
            } else {
                throw new NopException(ERR_TYPE_NOT_TYPE_VARIABLE).param(ARG_VAR_NAME, type.getTypeName());
            }
        }
    }

    public void addVariable(String varName, IGenericType type) {
        IGenericType oldType = types.put(varName, type);
        if (oldType != null)
            throw new NopException(ERR_TYPE_REDEFINE_TYPE_VARIABLE).param(ARG_VAR_NAME, varName);
    }

    public IGenericType resolveVariable(String varName) {
        return types.get(varName);
    }
}