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
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.eval.EvalFrame;
import io.nop.core.lang.eval.EvalReference;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

import java.util.Iterator;

public class ForOfExecutable extends AbstractExecutable {
    protected final int varSlot;
    protected final int indexSlot;
    protected final boolean useRef;
    protected final IExecutableExpression itemsExpr;
    protected final IExecutableExpression bodyExpr;

    protected ForOfExecutable(SourceLocation loc, int varSlot, boolean useRef, int indexSlot,
                              IExecutableExpression itemsExpr, IExecutableExpression bodyExpr) {
        super(loc);
        this.varSlot = varSlot;
        this.indexSlot = indexSlot;
        this.useRef = useRef;
        this.itemsExpr = itemsExpr;
        this.bodyExpr = Guard.notNull(bodyExpr, "bodyExpr is null");
    }

    public static ForOfExecutable valueOf(SourceLocation loc, int varSlot, boolean useRef, int indexSlot,
                                          IExecutableExpression itemsExpr, IExecutableExpression bodyExpr) {
        if (bodyExpr.isUseExitMode()) {
            return new SimpleForOfExecutable(loc, varSlot, useRef, indexSlot, itemsExpr, bodyExpr);
        } else {
            return new ForOfExecutable(loc, varSlot, useRef, indexSlot, itemsExpr, bodyExpr);
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
        sb.append("for(var of items)");
    }

    protected Iterator<Object> toIterator(Object o) {
        try {
            return CollectionHelper.toIterator(o, false);
        } catch (NopException e) {
            e.addXplStack(itemsExpr);
            throw e;
        }
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object items = executor.execute(itemsExpr, scope);
        if (items == null)
            return null;

        Iterator<Object> it = toIterator(items);

        EvalFrame frame = scope.getCurrentFrame();
        int index = 0;
        while (it.hasNext()) {
            Object var = it.next();
            if (useRef) {
                frame.setStackValue(varSlot, new EvalReference(var));
            } else {
                frame.setStackValue(varSlot, var);
            }
            if (indexSlot >= 0) {
                frame.setStackValue(indexSlot, index);
                index++;
            }

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

    static class SimpleForOfExecutable extends ForOfExecutable {

        public SimpleForOfExecutable(SourceLocation loc, int varSlot, boolean useRef, int indexSlot,
                                     IExecutableExpression itemsExpr, IExecutableExpression bodyExpr) {
            super(loc, varSlot, useRef, indexSlot, itemsExpr, bodyExpr);
        }

        @Override
        public Object execute(IExpressionExecutor executor, IEvalScope scope) {
            Object items = executor.execute(itemsExpr, scope);
            if (items == null)
                return null;

            Iterator<Object> it = toIterator(items);

            int index = 0;
            EvalFrame frame = scope.getCurrentFrame();
            while (it.hasNext()) {
                Object var = it.next();
                if (useRef) {
                    frame.setStackValue(varSlot, new EvalReference(var));
                } else {
                    frame.setStackValue(varSlot, var);
                }

                if (indexSlot >= 0) {
                    frame.setStackValue(indexSlot, index);
                    index++;
                }

                executor.execute(bodyExpr, scope);
            }

            return null;
        }
    }
}