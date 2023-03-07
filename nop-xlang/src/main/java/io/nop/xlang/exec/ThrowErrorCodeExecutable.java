/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

import java.util.Map;

import static io.nop.xlang.XLangErrors.ARG_ARGS;
import static io.nop.xlang.XLangErrors.ARG_ERROR;
import static io.nop.xlang.XLangErrors.ERR_EXEC_THROW_INVALID_ERROR;

public class ThrowErrorCodeExecutable extends AbstractExecutable {
    private final IExecutableExpression errorExpr;
    private final IExecutableExpression paramsExpr;

    public ThrowErrorCodeExecutable(SourceLocation loc, IExecutableExpression errorExpr,
                                    IExecutableExpression paramsExpr) {
        super(loc);
        this.errorExpr = Guard.notNull(errorExpr, "errorExpr is null");
        this.paramsExpr = paramsExpr;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("throw ");
        errorExpr.display(sb);
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object error = executor.execute(errorExpr, scope);
        if (error instanceof NopException) {
            NopException exp = (NopException) error;
            if (exp.getErrorLocation() == null)
                exp.loc(getLocation());
            throw exp;
        }
        if (error instanceof Throwable)
            throw NopException.adapt((Throwable) error);

        Object params = paramsExpr == null ? null : executor.execute(paramsExpr, scope);

        if (error instanceof ErrorCode) {
            throw addParams(new NopEvalException((ErrorCode) error), params);
        } else if (error instanceof String) {
            throw new NopEvalException((String) error, null, false, false).loc(getLocation());
        }
        throw newError(ERR_EXEC_THROW_INVALID_ERROR).param(ARG_ERROR, error);
    }

    private NopException addParams(NopException e, Object params) {
        e.source(this);
        if (params == null)
            return e;
        if (params instanceof Map) {
            return e.params((Map) params);
        } else {
            return e.param(ARG_ARGS, params);
        }
    }
}
