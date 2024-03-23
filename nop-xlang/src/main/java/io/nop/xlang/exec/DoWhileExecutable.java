/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;

public class DoWhileExecutable extends AbstractExecutable {
    protected final IExecutableExpression testExpr;
    protected final IExecutableExpression bodyExpr;

    public DoWhileExecutable(SourceLocation loc, IExecutableExpression testExpr, IExecutableExpression bodyExpr) {
        super(loc);
        this.testExpr = testExpr;
        this.bodyExpr = bodyExpr;
    }

    public static DoWhileExecutable valueOf(SourceLocation loc, IExecutableExpression testExpr,
                                            IExecutableExpression bodyExpr) {
        if (bodyExpr.isUseExitMode()) {
            return new SimpleDoWhileExecutable(loc, testExpr, bodyExpr);
        } else {
            return new DoWhileExecutable(loc, testExpr, bodyExpr);
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
        sb.append("do{}while()");
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if(visitor.onVisitExpr(this)) {
            testExpr.visit(visitor);
            bodyExpr.visit(visitor);
            visitor.onEndVisitExpr(this);
        }
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        do {
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

        } while (passTest(executor, rt));
        return null;
    }

    protected boolean passTest(IExpressionExecutor executor, EvalRuntime rt) {
        if (testExpr == null)
            return true;

        return ConvertHelper.toTruthy(executor.execute(testExpr, rt));
    }

    static class SimpleDoWhileExecutable extends DoWhileExecutable {

        public SimpleDoWhileExecutable(SourceLocation loc, IExecutableExpression testExpr,
                                       IExecutableExpression bodyExpr) {
            super(loc, testExpr, bodyExpr);
        }

        public boolean containsReturnStatement() {
            return false;
        }

        public boolean containsBreakStatement() {
            return false;
        }

        @Override
        public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
            do {
                executor.execute(bodyExpr, rt);
            } while (passTest(executor, rt));
            return null;
        }
    }

}
