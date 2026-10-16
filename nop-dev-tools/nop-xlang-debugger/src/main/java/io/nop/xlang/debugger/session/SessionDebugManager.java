/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger.session;

import io.nop.core.lang.eval.DefaultExpressionExecutor;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.debugger.DebugExpressionExecutor;
import io.nop.xlang.debugger.XLangDebugger;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话注册表与默认会话供给（D3 §3.8）：每会话一个 {@link io.nop.xlang.debugger.XLangDebugger}
 * 实例的句柄登记；默认会话（IDEA 通道）是预置实例，路由对它无特判。
 *
 * <p>静态单例符合 nop-dev-tools 无 IoC 的 ServiceLoader/ICoreInitializer 手工装配惯例；
 * 销毁顺序按 D3 §3.5：先注销会话表、后置 destroyed 位。
 */
public final class SessionDebugManager {
    private static final SessionDebugManager INSTANCE = new SessionDebugManager();

    public static SessionDebugManager instance() {
        return INSTANCE;
    }

    private final Map<String, DebugSessionHandle> sessions = new ConcurrentHashMap<>();
    private volatile DebugSessionHandle defaultHandle;
    private volatile DebugSessionHandle destroyedHandle;

    private SessionDebugManager() {
    }

    /**
     * 初始化默认会话与 destroyed 哨兵（web 通道激活时由 Initializer 调用）。
     */
    public synchronized void init(XLangDebugger defaultDebugger) {
        Objects.requireNonNull(defaultDebugger, "defaultDebugger");
        this.defaultHandle = new DebugSessionHandle("default", new DebugExpressionExecutor(defaultDebugger));
        this.destroyedHandle = new DebugSessionHandle("__destroyed__",
                new DestroyedSessionExecutor(DefaultExpressionExecutor.INSTANCE));
        this.sessions.clear();
    }

    /**
     * 默认会话执行器（无标记执行的兜底目标，IDEA 全局断点语义）。
     */
    public IExpressionExecutor defaultExecutor() {
        DebugSessionHandle handle = defaultHandle;
        if (handle == null)
            throw new IllegalStateException("nop.xlang.debugger.session-manager-not-init");
        return handle.getDebugExecutor();
    }

    /**
     * destroyed 哨兵（miss/null 归一目标；其执行器带取消保护、无调试钩点）。
     */
    public DebugSessionHandle destroyedHandle() {
        DebugSessionHandle handle = destroyedHandle;
        if (handle == null)
            throw new IllegalStateException("nop.xlang.debugger.session-manager-not-init");
        return handle;
    }

    /**
     * 注册 web 会话（session.open 时由网关调用）。sessionId 冲突抛错。
     */
    public synchronized DebugSessionHandle createSession(String sessionId, XLangDebugger debugger) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(debugger, "debugger");
        if (sessions.containsKey(sessionId))
            throw new IllegalArgumentException("nop.xlang.debugger.session-already-exists:" + sessionId);
        DebugSessionHandle handle = new DebugSessionHandle(sessionId, new DebugExpressionExecutor(debugger));
        sessions.put(sessionId, handle);
        return handle;
    }

    /**
     * 按 id 查找会话句柄（item 12/14 消费面）；不存在返回 null。
     */
    public DebugSessionHandle getSession(String sessionId) {
        if (sessionId == null)
            return null;
        return sessions.get(sessionId);
    }

    /**
     * 销毁会话：先注销、后置 destroyed 位（D3 §3.5 顺序）。
     */
    public void destroySession(DebugSessionHandle handle) {
        Objects.requireNonNull(handle, "handle");
        sessions.remove(handle.getSessionId(), handle);
        handle.markDestroyed();
    }

    /**
     * 复位全部状态（destroy 清扫与测试卫生）。
     */
    public synchronized void reset() {
        sessions.values().forEach(DebugSessionHandle::markDestroyed);
        sessions.clear();
        defaultHandle = null;
        destroyedHandle = null;
    }
}
