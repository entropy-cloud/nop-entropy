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
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.model.query.FilterOp;
import io.nop.core.model.query.IBetweenOperator;

public class BetweenOpExecutable extends AbstractExecutable implements IEvalPredicate {
    private final FilterOp filterOp;
    private final IExecutableExpression valueExpr;
    protected final IExecutableExpression minExpr;
    protected final IExecutableExpression maxExpr;
    private final boolean excludeMin;
    private final boolean excludeMax;
    private final IBetweenOperator predicate;

    public BetweenOpExecutable(SourceLocation loc, FilterOp filterOp,
                               IExecutableExpression valueExpr,
                               IExecutableExpression minExpr,
                               IExecutableExpression maxExpr, boolean excludeMin, boolean excludeMax) {
        super(loc);
        this.filterOp = filterOp;
        this.valueExpr = Guard.notNull(valueExpr, "value");
        this.minExpr = Guard.notNull(minExpr, "min");
        this.maxExpr = Guard.notNull(maxExpr, "max");
        this.predicate = Guard.notNull(filterOp, "filterOp").getBetweenOperator();
        this.excludeMin = excludeMin;
        this.excludeMax = excludeMax;
    }

    public FilterOp getFilterOp() {
        return filterOp;
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    public void display(StringBuilder sb) {
        valueExpr.display(sb);
        sb.append(' ');
        sb.append(getFilterOp().name());
        sb.append(' ');
        minExpr.display(sb);
        sb.append(" and ");
        maxExpr.display(sb);
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object value = executor.execute(valueExpr, rt);
        Object minValue = executor.execute(minExpr, rt);
        Object maxValue = executor.execute(maxExpr, rt);
        return predicate.test(value, minValue, maxValue, excludeMin, excludeMax);
    }

    @Override
    public boolean passConditions(IEvalContext ctx) {
        IEvalScope scope = ctx.getEvalScope();
        IExpressionExecutor executor = EvalExprProvider.getGlobalExecutor();
        EvalRuntime rt = new EvalRuntime(scope);
        Object value = executor.execute(valueExpr, rt);
        Object minValue = executor.execute(minExpr, rt);
        Object maxValue = executor.execute(maxExpr, rt);
        return predicate.test(value, minValue, maxValue, excludeMin, excludeMax);
    }
}
