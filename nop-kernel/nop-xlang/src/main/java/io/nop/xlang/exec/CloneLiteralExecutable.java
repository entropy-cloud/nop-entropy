/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.json.JSON;
import io.nop.api.core.util.CloneHelper;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;

public class CloneLiteralExecutable extends AbstractExecutable {
    private final Object value;

    private CloneLiteralExecutable(SourceLocation loc, Object value) {
        super(loc);
        this.value = value;
    }

    public static IExecutableExpression build(SourceLocation loc, Object value) {
        if (value == null)
            return new NullExecutable(loc);
        return new CloneLiteralExecutable(loc, value);
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        return CloneHelper.deepClone(value);
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(JSON.stringify(value));
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        visitor.onVisitSimpleExpr(this);
    }
}
