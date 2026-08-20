/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;


public class RangeExecutable extends AbstractExecutable {
    protected final IExecutableExpression beginExpr;
    protected final IExecutableExpression endExpr;
    private final IExecutableExpression stepExpr;

    public RangeExecutable(SourceLocation loc, IExecutableExpression beginExpr, IExecutableExpression endExpr, IExecutableExpression stepExpr) {
        super(loc);
        this.beginExpr = beginExpr;
        this.endExpr = endExpr;
        this.stepExpr = stepExpr;
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("range(begin to end)");
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object begin = executor.execute(beginExpr, rt);
        Object end = executor.execute(endExpr, rt);
        Object step = executor.execute(stepExpr, rt);
        return XLangSemantics.range(getLocation(), display(), begin, end, step);
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if (visitor.onVisitExpr(this)) {
            beginExpr.visit(visitor);
            endExpr.visit(visitor);
            stepExpr.visit(visitor);
            visitor.onEndVisitExpr(this);
        }
    }
    public IExecutableExpression getBeginExpr() {
        return beginExpr;
    }

    public IExecutableExpression getEndExpr() {
        return endExpr;
    }

    public IExecutableExpression getStepExpr() {
        return stepExpr;
    }

}
