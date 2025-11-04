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
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
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
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        return var.getValue(rt);
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(varName);
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        visitor.onVisitSimpleExpr(this);
    }
}
