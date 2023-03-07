/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.debug;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

public class DebugExpressionExecutor implements IExpressionExecutor {
    private final IXLangDebugger debugger;

    public DebugExpressionExecutor(IXLangDebugger debugger) {
        this.debugger = debugger;
    }

    @Override
    public Object execute(IExecutableExpression expr, IEvalScope scope) {
        if (!expr.allowBreakPoint())
            return expr.execute(this, scope);

        debugger.checkBreakpoint(expr.getLocation(), scope);
        return expr.execute(this, scope);
    }
}