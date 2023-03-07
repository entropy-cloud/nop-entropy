/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

import static io.nop.xlang.xpl.XplInputFormat.value;

public class TypeOfExecutable extends AbstractExecutable {
    private final IExecutableExpression expr;

    public TypeOfExecutable(SourceLocation loc, IExecutableExpression expr) {
        super(loc);
        this.expr = Guard.notNull(expr, "expr");
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object v = executor.execute(expr, scope);
        return value == null ? "undefined" : v.getClass().getTypeName();
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("typeof ");
        expr.display(sb);
    }
}
