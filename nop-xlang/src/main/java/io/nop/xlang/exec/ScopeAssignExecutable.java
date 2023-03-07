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
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

public class ScopeAssignExecutable extends AbstractExecutable {
    private final String varName;
    private final IExecutableExpression expr;

    public ScopeAssignExecutable(SourceLocation loc, String varName, IExecutableExpression expr) {
        super(loc);
        this.varName = Guard.notEmpty(varName, "varName is empty");
        this.expr = Guard.notNull(expr, "expr is null");
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object v = executor.execute(expr, scope);
        scope.setLocalValue(getLocation(), varName, v);
        return v;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(varName);
        sb.append(" = ");
        expr.display(sb);
    }
}