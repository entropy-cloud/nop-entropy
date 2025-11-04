/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;

public class ReturnNullExecutable extends AbstractExecutable {
    private final IExecutableExpression executable;

    public ReturnNullExecutable(IExecutableExpression executable) {
        super(executable.getLocation());
        this.executable = executable;
    }

    public boolean containsBreakStatement() {
        return executable.containsBreakStatement();
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public boolean isUseExitMode() {
        return executable.isUseExitMode();
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        executable.execute(executor, rt);
        return null;
    }

    @Override
    public void display(StringBuilder sb) {
        executable.display(sb);
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if (visitor.onVisitExpr(this)) {
            executable.visit(visitor);
            visitor.onEndVisitExpr(this);
        }
    }
}