/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger.session;

import io.nop.core.lang.eval.DefaultExpressionExecutor;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

import java.util.Objects;

/**
 * 会话销毁分支的执行器（D3 §3.4 路由表 destroyed 行 + D6 §3.2 `executeDestroyed` 形态）：
 * 无调试钩点（不走 checkBreakpoint），但保留取消保护——入口查取消通道后委托默认执行器。
 *
 * <p>本执行器确保"REPL 执行中途会话销毁不解除取消保护"（D6 §一.10）；
 * 取消检查通道实现归 item 14，一期为 null（纯委托直通）。
 */
public final class DestroyedSessionExecutor implements IExpressionExecutor {
    private final IExpressionExecutor delegate;
    private final ReplDeadlineChannel deadlineChannel;

    public DestroyedSessionExecutor(IExpressionExecutor delegate, ReplDeadlineChannel deadlineChannel) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.deadlineChannel = deadlineChannel;
    }

    public DestroyedSessionExecutor(IExpressionExecutor delegate) {
        this(delegate, null);
    }

    @Override
    public Object execute(IExecutableExpression expr, EvalRuntime rt) {
        if (deadlineChannel != null)
            deadlineChannel.checkDeadline(rt);
        return expr.execute(delegate, rt);
    }
}
