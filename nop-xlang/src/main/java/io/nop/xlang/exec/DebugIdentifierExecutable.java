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
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.expr.ExprExecHelper;

/**
 * 在Debugger中执行调试表达式时，需要从堆栈上下文以及scope中查找变量
 */
public class DebugIdentifierExecutable extends AbstractExecutable {
    private final String varName;

    public DebugIdentifierExecutable(SourceLocation loc, String varName) {
        super(loc);
        this.varName = Guard.notEmpty(varName, "varName");
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        return ExprExecHelper.getVar(scope, varName);
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(varName);
    }
}