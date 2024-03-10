/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.convert.ITypeConverter;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

public class ConvertExecutable extends AbstractExecutable {
    private final IExecutableExpression expr;
    private final String funcName;
    private final ITypeConverter converter;

    public ConvertExecutable(SourceLocation loc, IExecutableExpression expr, String funcName,
                             ITypeConverter converter) {
        super(loc);
        this.expr = expr;
        this.funcName = funcName;
        this.converter = converter;
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public void display(StringBuilder sb) {
        expr.display(sb);
        sb.append('.').append(funcName);
        sb.append("()");
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object value = executor.execute(expr, scope);
        return converter.convertEx(scope, value, err -> newError(err));
    }
}