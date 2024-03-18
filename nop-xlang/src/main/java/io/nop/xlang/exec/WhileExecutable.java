/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
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
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        while (passTest(executor, rt)) {
            Object ret = executor.execute(bodyExpr, rt);
            ExitMode exitMode = rt.getExitMode();
            if (exitMode != null) {
                if (exitMode == ExitMode.RETURN)
                    return ret;
                rt.setExitMode(null);
                if (exitMode == ExitMode.BREAK) {
                    break;
                }
            }
        }

        return null;
    }

    protected boolean passTest(IExpressionExecutor executor, EvalRuntime rt) {
        return ConvertHelper.toTruthy(executor.execute(testExpr, rt));
    }

    static class SimpleWhileExecutable extends WhileExecutable {

        public SimpleWhileExecutable(SourceLocation loc, IExecutableExpression testExpr,
                                     IExecutableExpression bodyExpr) {
            super(loc, testExpr, bodyExpr);
        }

        @Override
        public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
            while (passTest(executor, rt)) {
                executor.execute(bodyExpr, rt);
            }

            return null;
        }
    }
}
