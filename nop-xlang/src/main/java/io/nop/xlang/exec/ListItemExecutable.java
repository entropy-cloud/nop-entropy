/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

public class ListItemExecutable {
    private final boolean spread;
    private final IExecutableExpression valueExpr;

    public ListItemExecutable(boolean spread, IExecutableExpression valueExpr) {
        this.spread = spread;
        this.valueExpr = valueExpr;
    }

    public void display(StringBuilder sb) {
        if (spread)
            sb.append("...");
        valueExpr.display(sb);
    }

    public boolean isSpread() {
        return spread;
    }

    public Object getValue(IExpressionExecutor executor, IEvalScope scope) {
        return executor.execute(valueExpr, scope);
    }
}
