/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;

public class NotExecutable extends AbstractExecutable {
    private final IExecutableExpression expr;

    public NotExecutable(SourceLocation loc, IExecutableExpression expr) {
        super(loc);
        this.expr = Guard.notNull(expr, "expr");
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object v = executor.execute(expr, rt);
        return !ConvertHelper.toTruthy(v);
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append('!');
        expr.display(sb);
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if (visitor.onVisitExpr(this)) {
            expr.visit(visitor);
            visitor.onEndVisitExpr(this);
        }
    }
}
