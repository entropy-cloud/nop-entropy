/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

public class WhileExecutable extends AbstractExecutable {
    private final IExecutableExpression testExpr;
    protected final IExecutableExpression bodyExpr;

    protected WhileExecutable(SourceLocation loc, IExecutableExpression testExpr, IExecutableExpression bodyExpr) {
        super(loc);
        this.testExpr = Guard.notNull(testExpr, "testExpr is null");
        this.bodyExpr = Guard.notNull(bodyExpr, "bodyExpr is null");
    }

    public static WhileExecutable valueOf(SourceLocation loc, IExecutableExpression testExpr,
                                          IExecutableExpression bodyExpr) {
        if (bodyExpr.isUseExitMode()) {
            return new SimpleWhileExecutable(loc, testExpr, bodyExpr);
        } else {
            return new WhileExecutable(loc, testExpr, bodyExpr);
        }
    }

    public boolean containsBreakStatement() {
        return false;
    }

    public boolean containsReturnStatement() {
        return bodyExpr.containsReturnStatement();
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("while()");
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        while (passTest(executor, scope)) {
            Object ret = executor.execute(bodyExpr, scope);
            ExitMode exitMode = scope.getExitMode();
            if (exitMode != null) {
                if (exitMode == ExitMode.RETURN)
                    return ret;
                scope.setExitMode(null);
                if (exitMode == ExitMode.BREAK) {
                    break;
                }
            }
        }

        return null;
    }

    protected boolean passTest(IExpressionExecutor executor, IEvalScope scope) {
        return ConvertHelper.toTruthy(executor.execute(testExpr, scope));
    }

    static class SimpleWhileExecutable extends WhileExecutable {

        public SimpleWhileExecutable(SourceLocation loc, IExecutableExpression testExpr,
                                     IExecutableExpression bodyExpr) {
            super(loc, testExpr, bodyExpr);
        }

        @Override
        public Object execute(IExpressionExecutor executor, IEvalScope scope) {
            while (passTest(executor, scope)) {
                executor.execute(bodyExpr, scope);
            }

            return null;
        }
    }
}
