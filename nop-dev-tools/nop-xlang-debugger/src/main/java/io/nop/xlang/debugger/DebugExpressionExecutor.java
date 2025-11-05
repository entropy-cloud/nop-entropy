/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger;

import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

public class DebugExpressionExecutor implements IExpressionExecutor {
    private final IXLangDebugger debugger;

    public DebugExpressionExecutor(IXLangDebugger debugger) {
        this.debugger = debugger;
    }

    @Override
    public Object execute(IExecutableExpression expr, EvalRuntime rt) {
        if (!expr.allowBreakPoint())
            return expr.execute(this, rt);

        debugger.checkBreakpoint(expr.getLocation(), rt);
        return expr.execute(this, rt);
    }
}