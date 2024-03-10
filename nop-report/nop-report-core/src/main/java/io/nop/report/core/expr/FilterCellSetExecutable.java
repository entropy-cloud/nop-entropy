/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.expr;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.report.core.model.ExpandedCellSet;
import io.nop.xlang.exec.AbstractExecutable;

public class FilterCellSetExecutable extends AbstractExecutable implements ICellSetExecutable {
    private final String expr;
    private final ICellSetExecutable executable;
    private final IEvalFunction predicate;

    public FilterCellSetExecutable(SourceLocation loc, String expr, ICellSetExecutable executable, IEvalFunction predicate) {
        super(loc);
        this.expr = expr;
        this.executable = executable;
        this.predicate = predicate;
    }

    @Override
    public ICellSetExecutable toAbsolute() {
        ICellSetExecutable absExpr = executable.toAbsolute();
        if (absExpr == executable)
            return this;

        return new FilterCellSetExecutable(getLocation(), expr, absExpr, predicate);
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(expr);
    }

    @Override
    public String getExpr() {
        return expr;
    }

    @Override
    public ExpandedCellSet execute(IExpressionExecutor executor, IEvalScope scope) {
        ExpandedCellSet cellSet = executable.execute(executor, scope);
        if (cellSet == null)
            return null;
        return cellSet.filter(e -> ConvertHelper.toTruthy(predicate.call1(null, e, scope)));
    }
}
