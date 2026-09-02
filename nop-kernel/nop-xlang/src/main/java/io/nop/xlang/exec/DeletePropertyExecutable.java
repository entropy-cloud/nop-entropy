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
 * 执行 `delete obj.prop`（non-computed 形式）。
 * 分派语义由 {@link XLangSemantics#deleteProperty} 提供。
 */
public class DeletePropertyExecutable extends AbstractExecutable {
    private final IExecutableExpression objExpr;
    private final String propName;

    public DeletePropertyExecutable(SourceLocation loc, IExecutableExpression objExpr, String propName) {
        super(loc);
        this.objExpr = Guard.notNull(objExpr, "objExpr is null");
        this.propName = Guard.notEmpty(propName, "propName is empty");
    }

    public IExecutableExpression getObjExpr() {
        return objExpr;
    }

    public String getPropName() {
        return propName;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("delete ");
        objExpr.display(sb);
        sb.append('.');
        sb.append(propName);
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object obj = executor.execute(objExpr, rt);
        return XLangSemantics.deleteProperty(getLocation(), display(), propName, obj, rt.getScope());
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if (visitor.onVisitExpr(this)) {
            objExpr.visit(visitor);
            visitor.onEndVisitExpr(this);
        }
    }
}