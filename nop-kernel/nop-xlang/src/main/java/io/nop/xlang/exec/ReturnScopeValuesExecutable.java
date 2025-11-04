/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.ast.definition.LocalVarDeclaration;

import java.util.ArrayList;
import java.util.List;

public class ReturnScopeValuesExecutable extends AbstractExecutable {
    private final List<LocalVarDeclaration> varDecls;
    private final List<IExecutableExpression> exprs;

    public ReturnScopeValuesExecutable(SourceLocation loc, List<LocalVarDeclaration> varDecls,
                                       List<IExecutableExpression> exprs) {
        super(loc);
        this.varDecls = varDecls;
        this.exprs = exprs;
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("return {scope vars}");
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        List<Object> values = new ArrayList<>(exprs.size());
        for (int i = 0, n = exprs.size(); i < n; i++) {
            Object value = executor.execute(exprs.get(i), rt);
            values.add(value);
        }
        return new ScopeValues(varDecls, values);
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if (visitor.onVisitExpr(this)) {
            for (IExecutableExpression expr : exprs) {
                expr.visit(visitor);
            }
            visitor.onEndVisitExpr(this);
        }
    }
}
