/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

public class ConcatExecutable extends AbstractMultiExecutable {
    public ConcatExecutable(SourceLocation loc, IExecutableExpression[] exprs) {
        super(loc, exprs);
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        StringBuilder sb = new StringBuilder();
        for (IExecutableExpression expr : exprs) {
            Object v = executor.execute(expr, scope);
            if (v != null)
                sb.append(v);
        }
        return sb.toString();
    }

    public void display(StringBuilder sb) {
        sb.append("concat(");
        for (int i = 0, n = exprs.length; i < n; i++) {
            exprs[i].display(sb);
            if (i != n - 1)
                sb.append(',');
        }
        sb.append(")");
    }
}