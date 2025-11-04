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
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
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
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        IEvalOutput oldOut = rt.getOut();
        StringBuilderEvalOutput out = new StringBuilderEvalOutput();
        rt.setOut(out);
        try {
            bodyExpr.execute(executor, rt);
        } finally {
            rt.setOut(oldOut);
        }
        return out.getOutput();
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if(visitor.onVisitExpr(this)) {
            bodyExpr.visit(visitor);
            visitor.onEndVisitExpr(this);
        }
    }
}
