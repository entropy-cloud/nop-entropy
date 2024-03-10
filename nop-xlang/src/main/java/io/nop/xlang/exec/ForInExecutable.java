/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

import java.util.Map;

import static io.nop.xlang.XLangErrors.ERR_EXEC_FOR_IN_ITEMS_MUST_BE_MAP;

public class ForInExecutable extends AbstractExecutable {
    protected final int varSlot;
    protected final IExecutableExpression itemsExpr;
    protected final IExecutableExpression bodyExpr;

    protected ForInExecutable(SourceLocation loc, int varSlot, IExecutableExpression itemsExpr,
                              IExecutableExpression bodyExpr) {
        super(loc);
        this.varSlot = varSlot;
        this.itemsExpr = itemsExpr;
        this.bodyExpr = Guard.notNull(bodyExpr, "bodyExpr is null");
    }

    public static ForInExecutable valueOf(SourceLocation loc, int varSlot, IExecutableExpression itemsExpr,
                                          IExecutableExpression bodyExpr) {
        if (bodyExpr.isUseExitMode()) {
            return new SimpleForInExecutable(loc, varSlot, itemsExpr, bodyExpr);
        } else {
            return new ForInExecutable(loc, varSlot, itemsExpr, bodyExpr);
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
        sb.append("for(var in items)");
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object items = executor.execute(itemsExpr, scope);
        if (items == null)
            return null;

        if (!(items instanceof Map))
            throw newError(ERR_EXEC_FOR_IN_ITEMS_MUST_BE_MAP);

        Map<String, Object> map = (Map<String, Object>) items;

        for (String name : map.keySet()) {
            scope.getCurrentFrame().setStackValue(varSlot, name);
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

    static class SimpleForInExecutable extends ForInExecutable {

        public SimpleForInExecutable(SourceLocation loc, int varSlot, IExecutableExpression itemsExpr,
                                     IExecutableExpression bodyExpr) {
            super(loc, varSlot, itemsExpr, bodyExpr);
        }

        @Override
        public Object execute(IExpressionExecutor executor, IEvalScope scope) {
            Object items = executor.execute(itemsExpr, scope);
            if (items == null)
                return null;

            if (!(items instanceof Map))
                throw newError(ERR_EXEC_FOR_IN_ITEMS_MUST_BE_MAP);

            Map<String, Object> map = (Map<String, Object>) items;

            for (String name : map.keySet()) {
                scope.getCurrentFrame().setStackValue(varSlot, name);
                executor.execute(bodyExpr, scope);
            }

            return null;
        }
    }
}
