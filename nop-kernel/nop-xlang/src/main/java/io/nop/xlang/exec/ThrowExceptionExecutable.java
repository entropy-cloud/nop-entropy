/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;

import static io.nop.xlang.XLangErrors.ARG_VALUE;
import static io.nop.xlang.XLangErrors.ERR_EXEC_THROW_EXCEPTION;
import static io.nop.xlang.XLangErrors.ERR_EXEC_THROW_NULL_EXCEPTION;

public class ThrowExceptionExecutable extends AbstractExecutable {
    private final IExecutableExpression expr;

    public ThrowExceptionExecutable(SourceLocation loc, IExecutableExpression expr) {
        super(loc);
        this.expr = expr;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("throw ");
        expr.display(sb);
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object value = executor.execute(expr, rt);
        if (value == null)
            throw newError(ERR_EXEC_THROW_NULL_EXCEPTION);
        if (value instanceof NopException) {
            NopException e = (NopException) value;
            if (e.getErrorLocation() == null)
                e.loc(getLocation());
            throw e;
        } else if (value instanceof Throwable) {
            throw newError(ERR_EXEC_THROW_EXCEPTION, (Throwable) value).forWrap();
        }
        throw newError(ERR_EXEC_THROW_EXCEPTION).param(ARG_VALUE, value);
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if (visitor.onVisitExpr(this)) {
            expr.visit(visitor);
            visitor.onEndVisitExpr(this);
        }
    }
}
