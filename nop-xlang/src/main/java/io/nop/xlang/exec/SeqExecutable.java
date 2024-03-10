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
 * 按顺序执行一组表达式，如果没有通过return返回，则返回最后一个表达式的返回值
 */
public class SeqExecutable extends AbstractMultiExecutable implements ISeqExecutable {
    private final boolean mayReturn;
    private final boolean mayBreak;

    protected SeqExecutable(SourceLocation loc, IExecutableExpression[] exprs, boolean mayReturn, boolean mayBreak) {
        super(loc, exprs);
        this.mayReturn = mayReturn;
        this.mayBreak = mayBreak;
    }

    public boolean isBlockStatement() {
        return false;
    }

    public boolean allowBreakPoint() {
        return false;
    }

    public static ISeqExecutable valueOf(SourceLocation loc, IExecutableExpression[] exprs) {
        boolean mayReturn = ExecutableHelper.mayReturn(exprs);
        boolean mayBreak = ExecutableHelper.mayBreak(exprs);

        if (!mayReturn && !mayBreak) {
            return new SimpleSeqExecutable(loc, exprs);
        } else {
            return new SeqExecutable(loc, exprs, mayReturn, mayBreak);
        }
    }

    @Override
    public boolean containsReturnStatement() {
        return mayReturn;
    }

    @Override
    public boolean containsBreakStatement() {
        return mayBreak;
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object ret = null;
        for (IExecutableExpression expr : exprs) {
            ret = executor.execute(expr, scope);
            // break 或者continue将导致返回值变成null
            if (scope.getExitMode() != null) {
                return ret;
            }
        }
        return ret;
    }

    static class SimpleSeqExecutable extends AbstractMultiExecutable implements ISeqExecutable {

        public SimpleSeqExecutable(SourceLocation loc, IExecutableExpression[] exprs) {
            super(loc, exprs);
        }

        public boolean isBlockStatement() {
            return false;
        }

        @Override
        public Object execute(IExpressionExecutor executor, IEvalScope scope) {
            Object ret = null;
            for (IExecutableExpression expr : exprs) {
                ret = executor.execute(expr, scope);
            }
            return ret;
        }

    }

}
