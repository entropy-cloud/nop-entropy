/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExpressionExecutor;


public class ScopeIdentifierExecutable extends AbstractExecutable {
    private final String varName;

    public ScopeIdentifierExecutable(SourceLocation loc, String varName) {
        super(loc);
        this.varName = Guard.notEmpty(varName, "varName");
    }

    public String getVarName() {
        return varName;
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        return XLangSemantics.getScopeValue(getLocation(), display(), rt.getScope(), varName);
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(varName);
    }

}