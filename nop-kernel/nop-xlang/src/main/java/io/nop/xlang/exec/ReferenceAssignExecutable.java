/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalFrame;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.ast.XLangOperator;

public class ReferenceAssignExecutable extends AbstractExecutable {
    private final String varName;
    private final int slot;
    private final IExecutableExpression expr;

    public ReferenceAssignExecutable(SourceLocation loc, String varName, int slot, IExecutableExpression expr) {
        super(loc);
        this.varName = varName;
        this.slot = slot;
        this.expr = expr;
    }

    public static ReferenceSelfAssignExecutable build(SourceLocation loc, String varName, int slot,
                                                      XLangOperator operator, IExecutableExpression expr) {
        return new ReferenceSelfAssignExecutable(loc, varName, slot, operator, expr);
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object v = executor.execute(expr, rt);
        EvalFrame frame = rt.getCurrentFrame();
        Object updated = XLangSemantics.setRefValue(frame.getStackValue(slot), v);
        if (updated != frame.getStackValue(slot))
            frame.setStackValue(slot, updated);
        return v;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(varName);
        sb.append(" = ");
        expr.display(sb);
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if (visitor.onVisitExpr(this)) {
            expr.visit(visitor);
            visitor.onEndVisitExpr(this);
        }
    }
    public String getVarName() {
        return varName;
    }

    public int getSlot() {
        return slot;
    }

    public IExecutableExpression getExpr() {
        return expr;
    }

}
