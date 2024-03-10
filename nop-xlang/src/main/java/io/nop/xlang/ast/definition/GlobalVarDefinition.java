/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast.definition;

import io.nop.core.lang.eval.global.IGlobalVariableDefinition;
import io.nop.core.type.IGenericType;
import io.nop.xlang.ast.XLangIdentifierDefinition;

public class GlobalVarDefinition implements XLangIdentifierDefinition {
    private final IGlobalVariableDefinition varDef;

    public GlobalVarDefinition(IGlobalVariableDefinition varDef) {
        this.varDef = varDef;
    }

    public String toString() {
        return "GlobalVar:" + varDef;
    }

    public IGenericType getResolvedType() {
        return varDef.getResolvedType();
    }

    public IGlobalVariableDefinition getVarDefinition() {
        return varDef;
    }
}
