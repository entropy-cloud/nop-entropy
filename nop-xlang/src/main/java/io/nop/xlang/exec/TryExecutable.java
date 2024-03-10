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
import io.nop.core.lang.eval.IEvalScope;
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
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        if (catchExpr != null) {
            try {
                return executor.execute(bodyExpr, scope);
            } catch (Exception e) {
                if (exceptionSlot >= 0) {
                    scope.getCurrentFrame().setStackValue(exceptionSlot, e);
                }
                executor.execute(catchExpr, scope);
                throw NopException.adapt(e);
            } finally {
                if (finallyExpr != null) {
                    executor.execute(finallyExpr, scope);
                }
            }
        } else {
            try {
                return executor.execute(bodyExpr, scope);
            } finally {
                if (finallyExpr != null) {
                    executor.execute(finallyExpr, scope);
                }
            }
        }
    }
}
