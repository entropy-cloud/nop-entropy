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

public class ForExecutable extends AbstractExecutable {
    protected final IExecutableExpression initExpr;
    protected final IExecutableExpression testExpr;
    protected final IExecutableExpression updateExpr;
    protected final IExecutableExpression bodyExpr;

    protected ForExecutable(SourceLocation loc, IExecutableExpression initExpr, IExecutableExpression testExpr,
                            IExecutableExpression updateExpr, IExecutableExpression bodyExpr) {
        super(loc);
        this.initExpr = initExpr;
        this.testExpr = testExpr;
        this.updateExpr = updateExpr;
        this.bodyExpr = Guard.notNull(bodyExpr, "bodyExpr is null");
    }

    public boolean containsBreakStatement() {
        return false;
    }

    public boolean containsReturnStatement() {
        return bodyExpr.containsReturnStatement();
    }

    public static ForExecutable valueOf(SourceLocation loc, IExecutableExpression initExpr,
                                        IExecutableExpression testExpr, IExecutableExpression updateExpr, IExecutableExpression bodyExpr) {
        if (bodyExpr.isUseExitMode()) {
            return new SimpleForExecutable(loc, initExpr, testExpr, updateExpr, bodyExpr);
        } else {
            return new ForExecutable(loc, initExpr, testExpr, updateExpr, bodyExpr);
        }
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("for()");
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        if (initExpr != null)
            executor.execute(initExpr, scope);

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

            if (updateExpr != null) {
                executor.execute(updateExpr, scope);
            }
        }

        return null;
    }

    protected boolean passTest(IExpressionExecutor executor, IEvalScope scope) {
        if (testExpr == null)
            return true;

        return ConvertHelper.toTruthy(executor.execute(testExpr, scope));
    }

    static class SimpleForExecutable extends ForExecutable {

        public SimpleForExecutable(SourceLocation loc, IExecutableExpression initExpr, IExecutableExpression testExpr,
                                   IExecutableExpression updateExpr, IExecutableExpression bodyExpr) {
            super(loc, initExpr, testExpr, updateExpr, bodyExpr);
        }

        public boolean containsBreakStatement() {
            return false;
        }

        public boolean containsReturnStatement() {
            return false;
        }

        @Override
        public Object execute(IExpressionExecutor executor, IEvalScope scope) {
            if (initExpr != null)
                executor.execute(initExpr, scope);

            while (passTest(executor, scope)) {
                executor.execute(bodyExpr, scope);
                if (updateExpr != null) {
                    executor.execute(updateExpr, scope);
                }
            }

            return null;
        }
    }
}
