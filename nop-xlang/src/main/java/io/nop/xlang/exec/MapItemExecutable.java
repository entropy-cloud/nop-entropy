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
import io.nop.core.lang.eval.IExpressionExecutor;

public class MapItemExecutable {
    private final IExecutableExpression keyExpr;
    private final IExecutableExpression valueExpr;
    private final boolean spread;

    public MapItemExecutable(IExecutableExpression keyExpr, IExecutableExpression valueExpr, boolean spread) {
        this.keyExpr = keyExpr;
        this.valueExpr = valueExpr;
        this.spread = spread;
    }

    public void display(StringBuilder sb) {
        if (spread) {
            sb.append("...");
            valueExpr.display(sb);
        } else {
            keyExpr.display(sb);
            sb.append(':');
            valueExpr.display(sb);
        }
    }

    public boolean isSpread() {
        return spread;
    }

    public Object getValue(IExpressionExecutor executor, EvalRuntime rt) {
        return executor.execute(valueExpr, rt);
    }

    public Object getKey(IExpressionExecutor executor, EvalRuntime rt) {
        return executor.execute(keyExpr, rt);
    }
}
