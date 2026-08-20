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
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;


public class GuardNotEmptyExecutable extends AbstractExecutable {
    private final IExecutableExpression expr;
    private final String target;

    public GuardNotEmptyExecutable(SourceLocation loc, IExecutableExpression expr, String target) {
        super(loc);
        this.expr = Guard.notNull(expr, "expr");
        this.target = target;
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object v = executor.execute(expr, rt);
        return XLangSemantics.guardNotEmpty(getLocation(), display(), target, v);
    }

    @Override
    public void display(StringBuilder sb) {
        expr.display(sb);
        sb.append('!');
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if (visitor.onVisitExpr(this)) {
            expr.visit(visitor);
            visitor.onEndVisitExpr(this);
        }
    }
    public IExecutableExpression getExpr() {
        return expr;
    }

    public String getTarget() {
        return target;
    }

}
