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
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.type.IGenericType;

public class InstanceOfExecutable extends AbstractExecutable {
    private final IExecutableExpression expr;
    private final IGenericType type;

    public InstanceOfExecutable(SourceLocation loc, IExecutableExpression expr, IGenericType type) {
        super(loc);
        this.expr = Guard.notNull(expr, "expr");
        this.type = Guard.notNull(type, "type");
    }

    @Override
    public void display(StringBuilder sb) {
        expr.display(sb);
        sb.append(" instanceof ");
        sb.append(type.getTypeName());
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object value = executor.execute(expr, scope);
        if (value == null)
            return false;
        return type.isInstance(value);
    }
}
