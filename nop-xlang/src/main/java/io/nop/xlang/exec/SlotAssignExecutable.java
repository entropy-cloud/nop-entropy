/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

public class SlotAssignExecutable extends AbstractExecutable {
    private final String varName;
    private final int slot;
    private final IExecutableExpression expr;

    public SlotAssignExecutable(SourceLocation loc, String varName, int slot, IExecutableExpression expr) {
        super(loc);
        this.varName = varName;
        this.slot = slot;
        this.expr = expr;
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object v = executor.execute(expr, scope);
        scope.getCurrentFrame().setStackValue(slot, v);
        return v;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(varName);
        sb.append(" = ");
        expr.display(sb);
    }
}
