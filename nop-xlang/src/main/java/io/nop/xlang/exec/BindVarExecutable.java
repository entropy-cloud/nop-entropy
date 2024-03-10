/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalFrame;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

public class BindVarExecutable extends AbstractExecutable {
    private final int[] slots;
    private final Object[] vars;
    private final IExecutableExpression expr;

    public BindVarExecutable(SourceLocation loc, int[] slots, Object[] vars, IExecutableExpression expr) {
        super(loc);
        this.slots = slots;
        this.vars = vars;
        this.expr = expr;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("(lambda)");
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        EvalFrame frame = scope.getCurrentFrame();
        for (int i = 0, n = slots.length; i < n; i++) {
            frame.setStackValue(slots[i], vars[i]);
        }
        return executor.execute(expr, scope);
    }
}