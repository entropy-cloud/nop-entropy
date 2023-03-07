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

public class EqNullExecutable extends AbstractExecutable {
    private final IExecutableExpression expr;

    public EqNullExecutable(SourceLocation loc, IExecutableExpression expr) {
        super(loc);
        this.expr = Guard.notNull(expr, "expr");
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public void display(StringBuilder sb) {
        expr.display(sb);
        sb.append(" == null");
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        return executor.execute(expr, scope) == null;
    }
}
