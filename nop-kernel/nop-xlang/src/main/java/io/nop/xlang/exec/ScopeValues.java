/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.xlang.ast.definition.LocalVarDeclaration;

import java.util.List;

public class ScopeValues {
    private final List<LocalVarDeclaration> varDecls;
    private final List<Object> values;

    public ScopeValues(List<LocalVarDeclaration> varDecls, List<Object> values) {
        this.varDecls = varDecls;
        this.values = values;
    }

    public List<LocalVarDeclaration> getVarDecls() {
        return varDecls;
    }

    public List<Object> getValues() {
        return values;
    }
}
