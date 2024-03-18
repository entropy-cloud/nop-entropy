/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.collections.iterator.IntRangeIterator;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

import static io.nop.xlang.XLangErrors.ERR_EXEC_LOOP_STEP_MUST_NOT_BE_ZERO;

public class RangeExecutable extends AbstractExecutable {
    protected final IExecutableExpression beginExpr;
    protected final IExecutableExpression endExpr;
    private final IExecutableExpression stepExpr;

    public RangeExecutable(SourceLocation loc, IExecutableExpression beginExpr, IExecutableExpression endExpr,
                           IExecutableExpression stepExpr) {
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
        Integer begin = ConvertHelper.toInt(executor.execute(beginExpr, rt), err -> newError(err));
        Integer end = ConvertHelper.toInt(executor.execute(endExpr, rt), err -> newError(err));

        Integer step = ConvertHelper.toInt(executor.execute(stepExpr, rt), err -> newError(err));

        if (step == null) {
            step = 1;
        } else if (step == 0) {
            throw newError(ERR_EXEC_LOOP_STEP_MUST_NOT_BE_ZERO);
        }

        if (begin == null)
            begin = 0;

        if (end == null)
            end = 0;

        return new IntRangeIterator(begin, end, step);
    }
}
