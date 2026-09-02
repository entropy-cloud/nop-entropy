/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical_entropy/nop-entropy
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
 * 执行 `delete obj[expr]`（computed 形式）。
 * 分派语义由 {@link XLangSemantics#deleteAttr} 提供。
 */
public class DeleteAttrExecutable extends AbstractExecutable {
    private final IExecutableExpression objExpr;
    private final IExecutableExpression attrExpr;

    public DeleteAttrExecutable(SourceLocation loc, IExecutableExpression objExpr,
                                IExecutableExpression attrExpr) {
        super(loc);
        this.objExpr = Guard.notNull(objExpr, "objExpr is null");
        this.attrExpr = Guard.notNull(attrExpr, "attrExpr is null");
    }

    public IExecutableExpression getObjExpr() {
        return objExpr;
    }

    public IExecutableExpression getAttrExpr() {
        return attrExpr;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("delete ");
        objExpr.display(sb);
        sb.append('[');
        attrExpr.display(sb);
        sb.append(']');
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object obj = executor.execute(objExpr, rt);
        Object attr = executor.execute(attrExpr, rt);
        return XLangSemantics.deleteAttr(getLocation(), display(), attrExpr.display(), obj, attr, rt.getScope());
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if (visitor.onVisitExpr(this)) {
            objExpr.visit(visitor);
            attrExpr.visit(visitor);
            visitor.onEndVisitExpr(this);
        }
    }
}