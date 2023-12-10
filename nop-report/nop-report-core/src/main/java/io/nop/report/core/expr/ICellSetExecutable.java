package io.nop.report.core.expr;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.report.core.model.ExpandedCellSet;

public interface ICellSetExecutable extends IExecutableExpression {
    ICellSetExecutable toAbsolute();

    String getExpr();

    ExpandedCellSet execute(IExpressionExecutor executor, IEvalScope scope);
}
