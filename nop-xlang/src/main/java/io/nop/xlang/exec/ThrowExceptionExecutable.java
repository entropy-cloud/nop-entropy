/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
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
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object value = executor.execute(expr, scope);
        if (value == null)
            throw newError(ERR_EXEC_THROW_NULL_EXCEPTION);
        if (value instanceof NopException) {
            throw (NopException) value;
        } else if (value instanceof Throwable) {
            throw newError(ERR_EXEC_THROW_EXCEPTION, (Throwable) value).forWrap();
        }
        throw newError(ERR_EXEC_THROW_EXCEPTION).param(ARG_VALUE, value);
    }
}
