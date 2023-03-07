/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

public class OutputValueExecutable extends AbstractExecutable {
    private final IExecutableExpression valueExpr;

    public OutputValueExecutable(SourceLocation loc, IExecutableExpression valueExpr) {
        super(loc);
        this.valueExpr = Guard.notNull(valueExpr, "valueExpr");
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("@value:");
        valueExpr.display(sb);
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object value = executor.execute(valueExpr, scope);
        IEvalOutput out = scope.getOut();
        out.value(getLocation(), value);
        return null;
    }
}
