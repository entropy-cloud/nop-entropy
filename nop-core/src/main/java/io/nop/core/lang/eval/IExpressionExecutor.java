/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.eval;

/**
 * 为表达式执行引入引入调试功能
 */
public interface IExpressionExecutor {
    default void onEnterFunction(IExecutableExpression expr, IEvalScope scope) {
    }

    default void onExitFunction(IExecutableExpression expr, IEvalScope scope) {
    }

    Object execute(IExecutableExpression expr, IEvalScope scope);
}