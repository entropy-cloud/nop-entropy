/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger.session;

import io.nop.core.lang.eval.IExpressionExecutor;

import java.util.Objects;

/**
 * 调试会话句柄：身份字段不可变（sessionId 与会话执行器），destroyed 为可见性受控的
 * 状态位（volatile 写，保证路由线程可见）—— D3 §3.2 标记契约的载体。
 *
 * <p>句柄由 {@link SessionDebugManager} 创建与销毁；销毁顺序为先注销、后置 destroyed 位。
 * destroyed=true 的句柄会被 {@link SessionRoutingExecutor} 路由到
 * {@link DestroyedSessionExecutor}（跳过调试钩点但保留取消保护）。
 */
public final class DebugSessionHandle {
    private final String sessionId;
    private final IExpressionExecutor debugExecutor;
    private volatile boolean destroyed;

    DebugSessionHandle(String sessionId, IExpressionExecutor debugExecutor) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.debugExecutor = Objects.requireNonNull(debugExecutor, "debugExecutor");
    }

    public String getSessionId() {
        return sessionId;
    }

    /**
     * 该会话的调试执行器（每入口执行 checkBreakpoint 的融合执行器形态的基础）。
     */
    public IExpressionExecutor getDebugExecutor() {
        return debugExecutor;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    void markDestroyed() {
        this.destroyed = true;
    }
}
