/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger.session;

import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

import java.util.Objects;

/**
 * 会话路由执行器外壳（D3 §3.4 路由表的三条运行时路由）：
 * 无标记 → 默认会话兜底（IDEA 全局断点语义）；有标记存活 → 该会话执行器；
 * 有标记已销毁 → {@link DestroyedSessionExecutor}（跳过调试钩点、保留取消保护）。
 *
 * <p>零开销红线：无标记路径仅一次 ThreadLocal get + 分支（web 关时不安装本外壳，
 * 注入现状形态的 {@link DebugExpressionExecutor}——D0 §3.3 开关矩阵行 2）。
 * 函数/标签调用边界重读全局槽位时拿到的仍是本外壳单实例，按当前线程标记重新路由
 * ——同一线程内路由不变（D3 §3.2 双机制论证）。
 */
public final class SessionRoutingExecutor implements IExpressionExecutor {
    private final SessionDebugManager manager;

    public SessionRoutingExecutor(SessionDebugManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    @Override
    public Object execute(IExecutableExpression expr, EvalRuntime rt) {
        DebugSessionHandle handle = DebugSessionScope.currentHandle();
        if (handle == null)
            return manager.defaultExecutor().execute(expr, rt);
        if (handle.isDestroyed())
            return manager.destroyedHandle().getDebugExecutor().execute(expr, rt);
        return handle.getDebugExecutor().execute(expr, rt);
    }
}
