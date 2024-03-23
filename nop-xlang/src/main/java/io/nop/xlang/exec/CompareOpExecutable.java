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
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.model.query.FilterOp;
import io.nop.xlang.api.XLang;

import java.util.function.BiPredicate;

public class CompareOpExecutable extends AbstractExecutable implements IEvalPredicate {
    private final FilterOp filterOp;
    protected final IExecutableExpression left;
    protected final IExecutableExpression right;
    private final BiPredicate<Object, Object> predicate;

    public CompareOpExecutable(SourceLocation loc, FilterOp filterOp, IExecutableExpression left,
                               IExecutableExpression right) {
        super(loc);
        this.filterOp = filterOp;
        this.left = Guard.notNull(left, "left");
        this.right = Guard.notNull(right, "right");
        this.predicate = Guard.notNull(filterOp, "filterOp").getBiPredicate();
    }

    public FilterOp getFilterOp() {
        return filterOp;
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    public void display(StringBuilder sb) {
        left.display(sb);
        sb.append(' ');
        sb.append(getFilterOp().name());
        sb.append(' ');
        right.display(sb);
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object v1 = executor.execute(left, rt);
        Object v2 = executor.execute(right, rt);
        return predicate.test(v1, v2);
    }

    @Override
    public boolean passConditions(IEvalContext ctx) {
        IEvalScope scope = ctx.getEvalScope();
        IExpressionExecutor executor = XLang.getExecutor();
        EvalRuntime rt = new EvalRuntime(scope);
        Object v1 = executor.execute(left, rt);
        Object v2 = executor.execute(right, rt);
        return predicate.test(v1, v2);
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if(visitor.onVisitExpr(this)) {
            left.visit(visitor);
            right.visit(visitor);
            visitor.onEndVisitExpr(this);
        }
    }
}
