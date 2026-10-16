/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger.session;

import java.util.function.Supplier;

/**
 * 会话标识标记契约（D3 §3.2）：代表会话触发脚本执行的入口（item 14 REPL、item 21 调试运行）
 * 通过 {@link #withSession} 包裹执行窗口，使 {@link SessionRoutingExecutor} 能按当前线程
 * 标记路由到正确的会话调试器实例。
 *
 * <p>契约要点：
 * <ul>
 * <li>save-restore：进入时保存旧标记，结束/异常时恢复——嵌套重入不丢外层标记</li>
 * <li>sessionId 形态经 {@link SessionDebugManager#getSession} 解析，miss 归一为 destroyed 哨兵
 * （执行直通，与"会话刚销毁"路径一致，不抛错）</li>
 * <li>null 入参同样归一 destroyed——绝不落默认会话（防泄漏进 IDEA 调试面）</li>
 * <li>窗口覆盖 compile+execute 全程（与 IDEA 现状行为对齐）</li>
 * </ul>
 */
public final class DebugSessionScope {
    private static final ThreadLocal<DebugSessionHandle> CURRENT = new ThreadLocal<>();

    private DebugSessionScope() {
    }

    /**
     * 以 sessionId 形态执行：经会话管理器解析，miss 归一 destroyed 哨兵。
     */
    public static <T> T withSession(String sessionId, Supplier<T> work) {
        return withHandle(resolve(sessionId), work);
    }

    /**
     * 以句柄形态执行：调用方已持有 {@link DebugSessionHandle} 时免二次解析。
     */
    public static <T> T withSession(DebugSessionHandle handle, Supplier<T> work) {
        return withHandle(normalize(handle), work);
    }

    public static void withSession(String sessionId, Runnable work) {
        withSession(sessionId, () -> {
            work.run();
            return null;
        });
    }

    public static void withSession(DebugSessionHandle handle, Runnable work) {
        withSession(handle, () -> {
            work.run();
            return null;
        });
    }

    /**
     * 当前线程的会话句柄；无标记返回 null（路由层落默认会话兜底）。
     */
    public static DebugSessionHandle currentHandle() {
        return CURRENT.get();
    }

    /**
     * 清除当前线程标记（测试卫生：线程池复用场景防标记跨用例泄漏）。
     */
    public static void clear() {
        CURRENT.remove();
    }

    private static <T> T withHandle(DebugSessionHandle handle, Supplier<T> work) {
        DebugSessionHandle prev = CURRENT.get();
        CURRENT.set(handle);
        try {
            return work.get();
        } finally {
            if (prev == null)
                CURRENT.remove();
            else
                CURRENT.set(prev);
        }
    }

    private static DebugSessionHandle resolve(String sessionId) {
        if (sessionId == null)
            return SessionDebugManager.instance().destroyedHandle();
        DebugSessionHandle handle = SessionDebugManager.instance().getSession(sessionId);
        return handle == null ? SessionDebugManager.instance().destroyedHandle() : handle;
    }

    private static DebugSessionHandle normalize(DebugSessionHandle handle) {
        return handle == null ? SessionDebugManager.instance().destroyedHandle() : handle;
    }
}
