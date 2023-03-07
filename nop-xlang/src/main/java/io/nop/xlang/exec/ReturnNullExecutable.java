/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
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
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        executable.execute(executor, scope);
        return null;
    }

    @Override
    public void display(StringBuilder sb) {
        executable.display(sb);
    }
}