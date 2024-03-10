/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast.definition;

import io.nop.core.reflect.IFunctionModel;
import io.nop.core.type.IGenericType;
import io.nop.xlang.ast.XLangIdentifierDefinition;

public class ResolvedFuncDefinition implements XLangIdentifierDefinition {
    private final IFunctionModel method;

    public ResolvedFuncDefinition(IFunctionModel method) {
        this.method = method;
    }

    public IFunctionModel getFunctionModel() {
        return method;
    }

    public boolean isAllowAssignment() {
        return false;
    }

    @Override
    public IGenericType getResolvedType() {
        return getFunctionModel().getFunctionType();
    }
}