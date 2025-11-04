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

import static io.nop.api.core.convert.ConvertHelper.toTruthy;

public class IfExecutable extends AbstractExecutable {
    private final IExecutableExpression test;
    private final IExecutableExpression consequent;
    private final IExecutableExpression alternate;

    public IfExecutable(SourceLocation loc, IExecutableExpression test, IExecutableExpression consequent,
                        IExecutableExpression alternate) {
        super(loc);
        this.test = Guard.notNull(test, "test");
        this.consequent = Guard.notNull(consequent, "consequent");
        this.alternate = alternate;
    }

    public boolean containsReturnStatement() {
        if (consequent.containsReturnStatement())
            return true;
        if (alternate != null)
            return alternate.containsReturnStatement();
        return false;
    }

    public boolean containsBreakStatement() {
        if (consequent.containsBreakStatement())
            return true;
        if (alternate != null)
            return alternate.containsBreakStatement();
        return false;
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        boolean b = toTruthy(executor.execute(test, rt));
        if (b) {
            return executor.execute(consequent, rt);
        } else {
            if (alternate != null) {
                return executor.execute(alternate, rt);
            } else {
                return null;
            }
        }
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if (visitor.onVisitExpr(this)) {
            test.visit(visitor);
            consequent.visit(visitor);
            if (alternate != null)
                alternate.visit(visitor);
            visitor.onEndVisitExpr(this);
        }
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("if()");
    }
}