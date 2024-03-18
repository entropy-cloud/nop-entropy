/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

public class TryExecutable extends AbstractExecutable {
    private final IExecutableExpression bodyExpr;
    private final int exceptionSlot;
    private final IExecutableExpression catchExpr;
    private final IExecutableExpression finallyExpr;

    public TryExecutable(SourceLocation loc, IExecutableExpression bodyExpr, int exceptionSlot,
                         IExecutableExpression catchExpr, IExecutableExpression finallyExpr) {
        super(loc);
        this.bodyExpr = Guard.notNull(bodyExpr, "bodyExpr is null");
        this.exceptionSlot = exceptionSlot;
        this.catchExpr = catchExpr;
        this.finallyExpr = finallyExpr;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("try{}");
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        if (catchExpr != null) {
            try {
                return executor.execute(bodyExpr, rt);
            } catch (Exception e) {
                if (exceptionSlot >= 0) {
                    rt.getCurrentFrame().setStackValue(exceptionSlot, e);
                }
                executor.execute(catchExpr, rt);
                throw NopException.adapt(e);
            } finally {
                if (finallyExpr != null) {
                    executor.execute(finallyExpr, rt);
                }
            }
        } else {
            try {
                return executor.execute(bodyExpr, rt);
            } finally {
                if (finallyExpr != null) {
                    executor.execute(finallyExpr, rt);
                }
            }
        }
    }
}
