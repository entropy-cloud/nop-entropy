/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
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
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object error = executor.execute(errorExpr, rt);
        // 与原实现求值顺序一致：params 仅在 error 非 NopException/Throwable 时求值
        Object params = null;
        if (!(error instanceof NopException) && !(error instanceof Throwable) && paramsExpr != null)
            params = executor.execute(paramsExpr, rt);
        XLangSemantics.throwErrorCode(getLocation(), this, display(), error, params);
        return null;
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if (visitor.onVisitExpr(this)) {
            errorExpr.visit(visitor);
            if (paramsExpr != null)
                paramsExpr.visit(visitor);
            visitor.onEndVisitExpr(this);
        }
    }

    public IExecutableExpression getErrorExpr() {
        return errorExpr;
    }

    public IExecutableExpression getParamsExpr() {
        return paramsExpr;
    }
}
