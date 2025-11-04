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
import io.nop.commons.util.MathHelper;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.ast.XLangOperator;

public class BitNotExecutable extends AbstractExecutable {
    private final IExecutableExpression expr;

    public BitNotExecutable(SourceLocation loc, IExecutableExpression expr) {
        super(loc);
        this.expr = Guard.notNull(expr, "expr is null");
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(XLangOperator.BIT_NOT);
        expr.display(sb);
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object v = executor.execute(expr, rt);
        return MathHelper.bneg(v);
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if (visitor.onVisitExpr(this)) {
            expr.visit(visitor);
            visitor.onEndVisitExpr(this);
        }
    }
}
