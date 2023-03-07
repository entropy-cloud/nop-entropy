/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.core.lang.eval.IExecutableExpression;

import java.util.Arrays;
import java.util.List;

public class ExecutableHelper {
    static final IExecutableExpression[] EMPTY_EXPRS = new IExecutableExpression[0];

    public static boolean mayBreak(IExecutableExpression[] exprs) {
        for (IExecutableExpression expr : exprs) {
            if (expr.containsBreakStatement())
                return true;
        }
        return false;
    }

    public static boolean mayReturn(IExecutableExpression[] exprs) {
        for (IExecutableExpression expr : exprs) {
            if (expr.containsReturnStatement())
                return true;
        }
        return false;
    }

    public static IExecutableExpression simplifySimpleBlock(IExecutableExpression expr) {
        if (expr instanceof NullExecutable)
            return NullExecutable.NULL;
        if (expr instanceof LiteralExecutable)
            return NullExecutable.NULL;
        if (expr instanceof ReturnNullExecutable)
            return expr;

        return new ReturnNullExecutable(expr);
    }

    public static IExecutableExpression[] toArray(List<IExecutableExpression> exprs) {
        if (exprs == null || exprs.isEmpty())
            return EMPTY_EXPRS;
        return exprs.toArray(new IExecutableExpression[exprs.size()]);
    }

    public static IExecutableExpression append(List<IExecutableExpression> exprs, IExecutableExpression expr) {
        if (exprs == null || exprs.isEmpty())
            return expr;

        if (expr instanceof ISeqExecutable) {
            ISeqExecutable seq = (ISeqExecutable) expr;
            for (IExecutableExpression subExpr : seq.getExprs()) {
                exprs.add(subExpr);
            }
            if (seq.isBlockStatement()) {
                return BlockExecutable.valueOf(null, toArray(exprs));
            } else {
                return SeqExecutable.valueOf(null, toArray(exprs));
            }
        } else {
            exprs.add(expr);
            return SeqExecutable.valueOf(null, toArray(exprs));
        }
    }

    public static IExecutableExpression prepend(List<IExecutableExpression> exprs, IExecutableExpression expr) {
        if (exprs == null || exprs.isEmpty())
            return expr;

        if (expr instanceof ISeqExecutable) {
            ISeqExecutable seq = (ISeqExecutable) expr;
            exprs.addAll(0, Arrays.asList(seq.getExprs()));
            if (seq.isBlockStatement()) {
                return BlockExecutable.valueOf(null, toArray(exprs));
            } else {
                return SeqExecutable.valueOf(null, toArray(exprs));
            }
        } else {
            exprs.add(0, expr);
            return SeqExecutable.valueOf(null, toArray(exprs));
        }
    }
}
