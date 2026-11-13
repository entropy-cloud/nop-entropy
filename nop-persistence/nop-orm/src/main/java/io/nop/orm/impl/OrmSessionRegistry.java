/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.impl;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.commons.concurrent.ContextualizedRegistry;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * plan 2517 R2-14 裁定①（C1-001/C1-023/C2-003）：
 * <ul>
 * <li>instance() 首触改 context.getAttrs().computeIfAbsent 原子化，消除 get→new→setAttribute 三步竞态</li>
 * <li>put/restore 单槽语义由底层 CHM 单步原子保证；close 判定与无条件 close 兜底在 OrmTemplateImpl（close 幂等）</li>
 * <li>裁定⑪（C3-001）：runInNewSessionAsync 上游永不完成时 session 的兜底关闭登记（context 生命周期反向登记形态，
 * BaseContext.close 零触碰——登记表挂本类静态弱引用表，惰性清扫已关闭 context 的挂起会话）</li>
 * </ul>
 * 设计权威：ai-dev/design/concurrency/orm-session-txn-atomicity-and-model-cache-generation.md §3.1/§3.3
 */
public class OrmSessionRegistry extends ContextualizedRegistry<IOrmSessionFactory, IOrmSession> {
    static final String CONTEXT_KEY = OrmSessionRegistry.class.getSimpleName();

    static final Logger LOG = LoggerFactory.getLogger(OrmSessionRegistry.class);

    /**
     * 裁定⑪泄漏守卫指标 nop.orm.session.pending-close 的 WARN 阈值（缺省 100）
     */
    static final int PENDING_CLOSE_WARN_THRESHOLD = 100;

    /**
     * 裁定⑪兜底登记表：context -> 该 context 上挂起待兜底关闭的 session 集合。
     * WeakHashMap 保证 context 被回收后登记表不泄漏；并发访问经 synchronizedMap 保护。
     */
    private static final Map<IContext, Set<IOrmSession>> PENDING_CLOSE = Collections
            .synchronizedMap(new WeakHashMap<>());

    private static final AtomicInteger PENDING_CLOSE_COUNT = new AtomicInteger();

    public static OrmSessionRegistry instance() {
        IContext context = ContextProvider.getOrCreateContext();
        sweepClosedContexts();
        // 裁定①：computeIfAbsent 原子首触。compute 函数体内仅 new，无 I/O 无锁获取（锁序总表 §4.6）
        return (OrmSessionRegistry) context.getAttrs().computeIfAbsent(CONTEXT_KEY, k -> new OrmSessionRegistry());
    }

    /**
     * 裁定⑪：runInNewSessionAsync 注册新会话时挂兜底 close 登记。future 完成路径收尾时调用
     * {@link #unregisterPendingClose} 摘除；上游永不完成时由 {@link #sweepClosedContexts()} 在 context 关闭后兜底 close。
     */
    public static void registerPendingClose(IContext context, IOrmSession session) {
        if (context == null || session == null)
            return;
        synchronized (PENDING_CLOSE) {
            PENDING_CLOSE.computeIfAbsent(context, k -> ConcurrentHashMap.newKeySet()).add(session);
        }
        int count = PENDING_CLOSE_COUNT.incrementAndGet();
        if (count > PENDING_CLOSE_WARN_THRESHOLD)
            LOG.warn("nop.orm.session.pending-close-exceeds-threshold:count={},threshold={}", count,
                    PENDING_CLOSE_WARN_THRESHOLD);
    }

    /**
     * 裁定⑪：future 完成路径收尾时摘除兜底 close 登记。
     */
    public static void unregisterPendingClose(IOrmSession session) {
        if (session == null)
            return;
        synchronized (PENDING_CLOSE) {
            boolean[] removed = new boolean[1];
            PENDING_CLOSE.values().forEach(set -> removed[0] |= set.remove(session));
            // 计数与登记对称：仅真正摘除时递减，防止 unregister/sweep 竞态下重复递减造成负漂移
            if (removed[0])
                PENDING_CLOSE_COUNT.decrementAndGet();
        }
    }

    public static int getPendingCloseCount() {
        return PENDING_CLOSE_COUNT.get();
    }

    /**
     * 裁定⑪兜底清扫：已关闭 context 上登记的挂起会话无条件 close（close 幂等兜底）。
     * 在 instance() 与收尾路径惰性触发。
     */
    public static void sweepClosedContexts() {
        Set<IContext> closedContexts = null;
        synchronized (PENDING_CLOSE) {
            if (PENDING_CLOSE.isEmpty())
                return;
            for (Map.Entry<IContext, Set<IOrmSession>> entry : PENDING_CLOSE.entrySet()) {
                IContext context = entry.getKey();
                if (context.isClosed()) {
                    if (closedContexts == null)
                        closedContexts = Collections.newSetFromMap(new WeakHashMap<>());
                    closedContexts.add(context);
                }
            }
        }
        if (closedContexts == null)
            return;
        for (IContext context : closedContexts) {
            Set<IOrmSession> sessions;
            synchronized (PENDING_CLOSE) {
                sessions = PENDING_CLOSE.remove(context);
            }
            if (sessions == null)
                continue;
            for (IOrmSession session : sessions) {
                PENDING_CLOSE_COUNT.decrementAndGet();
                if (!session.isClosed()) {
                    LOG.info("nop.orm.session.pending-close-sweep:session={}", session);
                    session.close();
                }
            }
        }
    }
}
