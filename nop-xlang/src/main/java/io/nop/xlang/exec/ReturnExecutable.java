/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

public class ReturnExecutable extends AbstractExecutable {
    private final IExecutableExpression expr;

    public ReturnExecutable(SourceLocation loc, IExecutableExpression expr) {
        super(loc);
        this.expr = expr;
    }

    public boolean containsReturnStatement() {
        return true;
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        if (expr != null) {
            Object ret = executor.execute(expr, scope);
            scope.setExitMode(ExitMode.RETURN);
            return ret;
        } else {
            scope.setExitMode(ExitMode.RETURN);
        }
        return null;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("return ");
        expr.display(sb);
    }
}
