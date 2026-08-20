/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.ISourceLocationSetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.reflect.IClassModel;


public class NewObjectExecutable extends AbstractExecutable {
    private final IClassModel classModel;
    private final IExecutableExpression[] argExprs;
    private final boolean locSetter;

    public NewObjectExecutable(SourceLocation loc, IClassModel classModel, IExecutableExpression[] argExprs) {
        super(loc);
        this.classModel = classModel;
        this.argExprs = argExprs;
        this.locSetter = classModel.isAssignableTo(ISourceLocationSetter.class);
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("new ");
        sb.append(classModel.getClassName());
        sb.append('(');
        for (int i = 0, n = argExprs.length; i < n; i++) {
            argExprs[i].display(sb);
            if (i != n - 1)
                sb.append(',');
        }
        sb.append(')');
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object[] argValues = evaluateArgs(argExprs, executor, rt);
        return XLangSemantics.newInstance(getLocation(), display(), classModel, argValues, rt.getScope());
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if (visitor.onVisitExpr(this)) {
            for (IExecutableExpression argExpr : argExprs) {
                argExpr.visit(visitor);
            }
            visitor.onEndVisitExpr(this);
        }
    }
    public io.nop.core.reflect.IClassModel getClassModel() {
        return classModel;
    }

    public IExecutableExpression[] getArgExprs() {
        return argExprs;
    }

}
