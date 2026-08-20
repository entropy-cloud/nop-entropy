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
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;


/**
 * 将itemsExpr返回的iterator包装为LoopVarStatus对象，并在上下文中设置varStatus变量
 */
public class VarStatusExecutable extends AbstractExecutable {
    private final String varStatusName;
    private final int varStatusSlot;
    private final IExecutableExpression itemsExpr;

    public VarStatusExecutable(SourceLocation loc, String varStatusName, int varStatusSlot,
                               IExecutableExpression itemsExpr) {
        super(loc);
        this.varStatusName = Guard.notEmpty(varStatusName, "varStatusName is empty");
        this.varStatusSlot = varStatusSlot;
        this.itemsExpr = Guard.notNull(itemsExpr, "itemsExpr is null");
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(varStatusName);
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object items = executor.execute(itemsExpr, rt);
        Object vs = XLangSemantics.varStatus(getLocation(), display(), itemsExpr.display(), items);
        if (vs != null)
            rt.getCurrentFrame().setStackValue(varStatusSlot, vs);
        return vs;
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if (visitor.onVisitExpr(this)) {
            itemsExpr.visit(visitor);
            visitor.onEndVisitExpr(this);
        }
    }
    public String getVarStatusName() {
        return varStatusName;
    }

    public int getVarStatusSlot() {
        return varStatusSlot;
    }

    public IExecutableExpression getItemsExpr() {
        return itemsExpr;
    }

}
