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

import static io.nop.xlang.XLangErrors.ARG_VAR_NAME;
import static io.nop.xlang.XLangErrors.ERR_EXEC_SCOPE_VAR_IS_UNDEFINED;

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
        Object value = rt.getValue(varName);
        if (value == null) {
            if (!rt.containsValue(varName))
                throw newError(ERR_EXEC_SCOPE_VAR_IS_UNDEFINED).param(ARG_VAR_NAME, varName);
        }
        return value;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(varName);
    }

}