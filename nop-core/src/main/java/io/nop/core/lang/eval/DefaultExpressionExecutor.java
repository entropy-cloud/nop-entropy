/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.eval;

public final class DefaultExpressionExecutor implements IExpressionExecutor {
    public static final DefaultExpressionExecutor INSTANCE = new DefaultExpressionExecutor();

    @Override
    public Object execute(IExecutableExpression expr, EvalRuntime rt) {
        return expr.execute(this, rt);
    }
}
