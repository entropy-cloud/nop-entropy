/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.ExitMode;
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
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        if (expr != null) {
            Object ret = executor.execute(expr, rt);
            rt.setExitMode(ExitMode.RETURN);
            return ret;
        } else {
            rt.setExitMode(ExitMode.RETURN);
        }
        return null;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("return ");
        expr.display(sb);
    }
}
