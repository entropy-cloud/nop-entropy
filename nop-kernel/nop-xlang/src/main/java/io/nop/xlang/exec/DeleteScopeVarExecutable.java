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
 * 执行 `delete $scope.x` / `delete $scope["x"]`。
 * computed 形式下 attrExpr 为运行时表达式；non-computed 形式下 attrExpr 为 null。
 */
public class DeleteScopeVarExecutable extends AbstractExecutable {
    private final String varName;
    private final IExecutableExpression attrExpr;

    public DeleteScopeVarExecutable(SourceLocation loc, String varName, IExecutableExpression attrExpr) {
        super(loc);
        this.varName = varName;
        this.attrExpr = attrExpr;
    }

    public String getVarName() {
        return varName;
    }

    public IExecutableExpression getAttrExpr() {
        return attrExpr;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("delete $scope.");
        sb.append(varName);
        if (attrExpr != null) {
            sb.append('[');
            attrExpr.display(sb);
            sb.append(']');
        }
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        String name = varName;
        if (attrExpr != null) {
            Object attr = executor.execute(attrExpr, rt);
            if (attr == null)
                return false;
            name = String.valueOf(attr);
        }
        if (name == null)
            return false;
        return XLangSemantics.deleteScopeValue(getLocation(), rt.getScope(), name);
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if (visitor.onVisitExpr(this)) {
            if (attrExpr != null)
                attrExpr.visit(visitor);
            visitor.onEndVisitExpr(this);
        }
    }
}