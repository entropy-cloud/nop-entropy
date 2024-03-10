/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

/**
 * 按顺序执行一组表达式，如果没有通过return返回，则忽略返回值
 */
public class BlockExecutable extends AbstractMultiExecutable implements ISeqExecutable {
    private final boolean mayReturn;
    private final boolean mayBreak;

    protected BlockExecutable(SourceLocation loc, IExecutableExpression[] exprs, boolean mayReturn, boolean mayBreak) {
        super(loc, exprs);
        this.mayReturn = mayReturn;
        this.mayBreak = mayBreak;
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public boolean containsReturnStatement() {
        return mayReturn;
    }

    @Override
    public boolean containsBreakStatement() {
        return mayBreak;
    }

    public boolean isBlockStatement() {
        return true;
    }

    public static IExecutableExpression valueOf(SourceLocation loc, IExecutableExpression[] exprs) {
        if (exprs.length == 0)
            return NullExecutable.NULL;

        boolean mayReturn = ExecutableHelper.mayReturn(exprs);
        boolean mayBreak = ExecutableHelper.mayBreak(exprs);

        if (!mayReturn && !mayBreak) {
            if (exprs.length == 1) {
                return ExecutableHelper.simplifySimpleBlock(exprs[0]);
            }

            return new SimpleBlockExecutable(loc, exprs);
        } else {
            return new BlockExecutable(loc, exprs, mayReturn, mayBreak);
        }
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        for (IExecutableExpression expr : exprs) {
            Object ret = executor.execute(expr, scope);
            // break 或者continue将导致返回值变成null
            if (scope.getExitMode() != null) {
                return ret;
            }
        }
        return null;
    }

    public void display(StringBuilder sb) {
        sb.append("block{}");
    }

    static class SimpleBlockExecutable extends AbstractMultiExecutable implements ISeqExecutable {
        public SimpleBlockExecutable(SourceLocation loc, IExecutableExpression[] exprs) {
            super(loc, exprs);
        }

        public boolean containsReturnStatement() {
            return false;
        }

        public boolean containsBreakStatement() {
            return false;
        }

        public boolean isBlockStatement() {
            return true;
        }

        @Override
        public Object execute(IExpressionExecutor executor, IEvalScope scope) {
            for (IExecutableExpression expr : exprs) {
                executor.execute(expr, scope);
            }
            return null;
        }
    }
}