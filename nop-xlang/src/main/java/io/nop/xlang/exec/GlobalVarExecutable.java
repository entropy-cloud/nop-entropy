/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.lang.eval.global.IGlobalVariableDefinition;

public class GlobalVarExecutable extends AbstractExecutable {
    private final String varName;
    private final IGlobalVariableDefinition var;

    public GlobalVarExecutable(SourceLocation loc, String varName, IGlobalVariableDefinition var) {
        super(loc);
        this.varName = Guard.notEmpty(varName, "varName is empty");
        this.var = Guard.notNull(var, "var is null");
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        return var.getValue(scope);
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(varName);
    }
}
