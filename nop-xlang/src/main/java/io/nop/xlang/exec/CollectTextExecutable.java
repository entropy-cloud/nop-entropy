/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.lang.eval.StringBuilderEvalOutput;

public class CollectTextExecutable extends AbstractExecutable {
    private final IExecutableExpression bodyExpr;

    public CollectTextExecutable(SourceLocation loc, IExecutableExpression bodyExpr) {
        super(loc);
        this.bodyExpr = bodyExpr;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("@collect:");
        bodyExpr.display(sb);
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        IEvalOutput oldOut = scope.getOut();
        StringBuilderEvalOutput out = new StringBuilderEvalOutput();
        scope.setOut(out);
        try {
            bodyExpr.execute(executor, scope);
        } finally {
            scope.setOut(oldOut);
        }
        return out.getOutput();
    }
}
