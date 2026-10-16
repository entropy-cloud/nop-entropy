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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话注册表与默认会话供给（D3 §3.5/§3.8）：每会话一个 {@link XLangDebugger} 实例的句柄登记；
 * 默认会话（IDEA 通道）是预置实例并注册进会话表（键 "default"）。
 *
 * <p>并发纪律：init/createSession/destroySession/shutdownAll 同 manager 监视器锁
 * （防停机清扫与网关开新会话竞态）；SessionListener 回调在锁外触发且异常隔离
 * （catch+log，不中断创建/销毁链）。静态单例符合 nop-dev-tools 的
 * ServiceLoader/ICoreInitializer 手工装配惯例。
 */
public final class SessionDebugManager {
    private static final Logger LOG = LoggerFactory.getLogger(SessionDebugManager.class);

    private static final SessionDebugManager INSTANCE = new SessionDebugManager();

    public static SessionDebugManager instance() {
        return INSTANCE;
    }

    private final Map<String, DebugSessionHandle> sessions = new ConcurrentHashMap<>();
    private volatile DebugSessionHandle defaultHandle;
    private volatile DebugSessionHandle destroyedHandle;
    private volatile SessionListener listener;
    private volatile boolean shutdown;

    private SessionDebugManager() {
    }

    /**
     * 初始化默认会话与 destroyed 哨兵（web 通道激活时由 Initializer 调用）。
     */
    public synchronized void init(XLangDebugger defaultDebugger) {
        Objects.requireNonNull(defaultDebugger, "defaultDebugger");
        this.shutdown = false;
        this.defaultHandle = new DebugSessionHandle("default",
                new DebugExpressionExecutor(defaultDebugger), defaultDebugger);
        this.destroyedHandle = new DebugSessionHandle("__destroyed__",
                new DestroyedSessionExecutor(DefaultExpressionExecutor.INSTANCE));
        this.sessions.clear();
        this.sessions.put("default", defaultHandle);
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

    public void setSessionListener(SessionListener listener) {
        this.listener = listener;
    }

    /**
     * 注册 web 会话（session.open 时由网关调用），内部创建新调试器实例。
     * sessionId 冲突或 manager 已 shutdown 抛错。
     */
    public DebugSessionHandle createSession(String sessionId) {
        DebugSessionHandle handle;
        synchronized (this) {
            ensureNotShutdown();
            handle = doCreate(sessionId, new XLangDebugger());
        }
        runPendingCreated();
        return handle;
    }

    /**
     * 注册 web 会话（外部实例形态：测试 recording 桩/特殊装配使用）。
     */
    public DebugSessionHandle createSession(String sessionId, XLangDebugger debugger) {
        DebugSessionHandle handle;
        synchronized (this) {
            ensureNotShutdown();
            handle = doCreate(sessionId, debugger);
        }
        runPendingCreated();
        return handle;
    }

    // onCreated 回调经 pendingCreated 带出 manager 锁外、在调用线程触发；
    // ThreadLocal 隔离并发 createSession 调用方（plan 2471 审计 F5：共享单槽会把
    // A 线程的回调投递给 B 线程——回调句柄与返回句柄交叉）
    private final ThreadLocal<Runnable> pendingCreated = new ThreadLocal<>();

    private void runPendingCreated() {
        Runnable notify = pendingCreated.get();
        if (notify != null) {
            pendingCreated.remove();
            notify.run();
        }
    }

    // 必须在 manager 锁内调用
    private DebugSessionHandle doCreate(String sessionId, XLangDebugger debugger) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(debugger, "debugger");
        if (sessions.containsKey(sessionId))
            throw new IllegalArgumentException("nop.xlang.debugger.session-already-exists:" + sessionId);
        DebugSessionHandle handle = new DebugSessionHandle(sessionId,
                new DebugExpressionExecutor(debugger), debugger);
        sessions.put(sessionId, handle);
        SessionListener cb = listener;
        if (cb != null)
            pendingCreated.set(() -> {
                try {
                    cb.onCreated(handle, debugger);
                } catch (Exception e) {
                    LOG.error("nop.xlang.debugger.session-listener-fail", e);
                }
            });
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
     * 销毁会话（abort 收尾链，D3 §3.5 DestroyingByClose）：close 中止挂起线程 →
     * 注销会话表 → 置 destroyed 位 → listener.onDestroy 回调（锁外、异常隔离）。
     */
    public void destroySession(DebugSessionHandle handle) {
        Objects.requireNonNull(handle, "handle");
        Runnable notify;
        synchronized (this) {
            XLangDebugger debugger = handle.getDebugger();
            if (debugger != null)
                debugger.close();
            sessions.remove(handle.getSessionId(), handle);
            handle.markDestroyed();
            SessionListener cb = listener;
            notify = (cb != null && debugger != null)
                    ? () -> {
                        try {
                            cb.onDestroyed(handle, debugger);
                        } catch (Exception e) {
                            LOG.error("nop.xlang.debugger.session-listener-fail", e);
                        }
                    }
                    : null;
        }
        if (notify != null)
            notify.run();
    }

    /**
     * 停机清扫（D6 serverShutdown，等效 close 语义）：与 init/createSession 同监视器锁，
     * 逐实例 close + 全量清扫；shutdown 后 createSession 拒绝。listener 回调锁外触发。
     */
    // shutdownAll 见下

    /**
     * 宽限销毁（D2 §3.3 DestroyingByGrace，plan 2471 新增）：租约过期戳 → releaseAll
     * （resume 语义放行，引擎侧聚合发 leaseExpired）→ 注销 → destroyed 位 → onDestroyed
     * 回调。**不调 close 不置 closed 不抛错**（挂起线程正常返回脚本执行完——D2 红线）。
     * expireLease **必须先于** releaseAll：销毁窗口内的新命中（含被放行线程抢跑的嵌套
     * 命中）由授予判定②"租约已过期不挂起"承接（D2 §3.1①②），且放行出口判定
     * 局部放行优先于租约到期，先过期戳不会改变已挂起线程的出口路径（局部放行正常返回，
     * 事件恰好一次——聚合或 per-thread 二选一）。
     */
    public void gracefulDispose(DebugSessionHandle handle) {
        Objects.requireNonNull(handle, "handle");
        Runnable notify;
        synchronized (this) {
            XLangDebugger debugger = handle.getDebugger();
            if (debugger != null) {
                debugger.expireLease();
                debugger.releaseAll();
            }
            sessions.remove(handle.getSessionId(), handle);
            handle.markDestroyed();
            notify = (listener != null && debugger != null)
                    ? () -> safeNotifyDestroyed(handle, debugger)
                    : null;
        }
        if (notify != null)
            notify.run();
    }

    /**
     * 停机清扫（D6 serverShutdown，等效 close 语义）：与 init/createSession 同监视器锁，
     * 逐实例 close + 全量清扫；shutdown 后 createSession 拒绝。listener 回调锁外触发。
     */
    public void shutdownAll() {
        List<Runnable> notifies = new ArrayList<>();
        synchronized (this) {
            this.shutdown = true;
            for (Map.Entry<String, DebugSessionHandle> entry : sessions.entrySet()) {
                DebugSessionHandle handle = entry.getValue();
                XLangDebugger debugger = handle.getDebugger();
                if (debugger != null) {
                    try {
                        debugger.close();
                    } catch (Exception e) {
                        LOG.error("nop.xlang.debugger.session-close-fail", e);
                    }
                }
                handle.markDestroyed();
                SessionListener cb = listener;
                if (cb != null && debugger != null) {
                    notifies.add(() -> {
                        try {
                            cb.onDestroyed(handle, debugger);
                        } catch (Exception e) {
                            LOG.error("nop.xlang.debugger.session-listener-fail", e);
                        }
                    });
                }
            }
            sessions.clear();
            defaultHandle = null;
        }
        // 回调在锁外触发（SessionListener 契约）
        for (Runnable notify : notifies) {
            notify.run();
        }
    }

    private void safeNotifyDestroyed(DebugSessionHandle handle, XLangDebugger debugger) {
        try {
            SessionListener cb = listener;
            if (cb != null)
                cb.onDestroyed(handle, debugger);
        } catch (Exception e) {
            LOG.error("nop.xlang.debugger.session-listener-fail", e);
        }
    }

    private void ensureNotShutdown() {
        if (shutdown)
            throw new IllegalStateException("nop.xlang.debugger.session-manager-shutdown");
    }

    /**
     * 复位全部状态（测试卫生；生产停机走 shutdownAll）。
     */
    public synchronized void reset() {
        sessions.values().forEach(DebugSessionHandle::markDestroyed);
        sessions.clear();
        defaultHandle = null;
        destroyedHandle = null;
        shutdown = false;
    }
}
