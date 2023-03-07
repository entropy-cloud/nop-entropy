/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalFrame;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

public class BuildClosureBodyExecutable extends AbstractExecutable {
    private final int closureSlot;
    private final int[] sourceSlots;
    private final int[] targetSlots;
    private final IExecutableExpression expr;

    public BuildClosureBodyExecutable(SourceLocation loc, int closureSlot, int[] sourceSlots, int[] targetSlots,
                                      IExecutableExpression expr) {
        super(loc);
        this.closureSlot = closureSlot;
        this.sourceSlots = sourceSlots;
        this.targetSlots = targetSlots;
        this.expr = expr;
    }

    @Override
    public void display(StringBuilder sb) {
        expr.display(sb);
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        EvalFrame frame = scope.getCurrentFrame();
        Object[] vars = new Object[sourceSlots.length];
        for (int i = 0, n = sourceSlots.length; i < n; i++) {
            int closureSlot = sourceSlots[i];
            vars[i] = frame.getStackValue(closureSlot);
        }
        BindVarExecutable executable = new BindVarExecutable(expr.getLocation(), targetSlots, vars, expr);
        frame.setStackValue(closureSlot, executable);
        return null;
    }
}
