/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.impl;

import io.nop.api.core.context.BaseContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2517 R2-14 红⑤（C2-002，C10-002）回归钉：runInNewSessionAsync 收尾线程纪律——
 * 完成线程只投递不执行，收尾（flush/registry 恢复/close）整段经 context.execute 投递回 context 队列
 * （对齐 thenOnContext 出口泵语义；Base 型 context 的内联降级语义由裁定④ session 互斥兜底，设计 §3.3）。
 * <p>缺陷态：whenComplete 回调体在完成线程内联执行 flush/close——上游依赖完成（经出口泵投回 context 线程）
 * 后无需任何 context 队列泵动 session 即被关闭（半操作/与在用线程交错面）→ 红；
 * 修复后：依赖完成时 session 保持开放，收尾任务作为 pending 任务挂在 context 队列、由 context 线程泵动执行 → 绿。</p>
 */
public class TestCra2517AsyncFinalize {

    private QueueContextStub context;
    private RecordingSession session;
    private OrmTemplateImpl template;

    private static class RecordingSession {
        final IOrmSession proxy;
        final CopyOnWriteArrayList<String> calls = new CopyOnWriteArrayList<>();

        RecordingSession() {
            this.proxy = ProxyStub.of(IOrmSession.class, (p, method, args) -> {
                calls.add(method.getName());
                return null;
            });
        }

        int count(String name) {
            int n = 0;
            for (String call : calls) {
                if (call.equals(name))
                    n++;
            }
            return n;
        }
    }

    /**
     * context 队列替身：execute 投递的任务挂队列，由测试方模拟 context 线程泵动；
     * 其余方法返回缺省值，getAttrs 返回真 CHM（裁定① computeIfAbsent 载体）。
     */
    private static class QueueContextStub {
        final Map<String, Object> attrs = new ConcurrentHashMap<>();
        final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();
        final IContext proxy = ProxyStub.of(IContext.class, (InvocationHandler) (p, method, args) -> {
            switch (method.getName()) {
                case "getAttrs":
                    return attrs;
                case "execute":
                    queue.add((Runnable) args[0]);
                    return null;
                case "isClosed":
                case "isRunningOnContext":
                    return Boolean.FALSE;
                default:
                    return null;
            }
        });

        void pumpOne() {
            Runnable task = queue.poll();
            if (task != null)
                task.run();
        }

        void pumpAll() {
            Runnable task;
            while ((task = queue.poll()) != null) {
                task.run();
            }
        }

        void pumpUntilDispatchCount(int count) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 5000;
            while (queue.size() < count && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
        }
    }

    private static IOrmSessionFactory stubFactory(IOrmSession session) {
        return ProxyStub.of(IOrmSessionFactory.class, (proxy, method, args) -> {
            if (method.getName().equals("openSession"))
                return session;
            return null;
        });
    }

    @BeforeEach
    public void setUp() {
        context = new QueueContextStub();
        session = new RecordingSession();
        template = new OrmTemplateImpl(stubFactory(session.proxy));
        BaseContextProvider.contextHolder().set(context.proxy);
    }

    @AfterEach
    public void tearDown() {
        BaseContextProvider.contextHolder().remove();
    }

    /**
     * 红⑤核心：上游依赖在完成线程完成后（且出口泵任务已在 context 队列挂起时），
     * session 不得已被完成线程内联 close——收尾必须整体挂 context 队列等待泵动。
     */
    @Test
    public void testCra2517_C2_002_finalizeDispatchedToContextQueueNotCompletionThread() throws Exception {
        CompletableFuture<String> upstream = new CompletableFuture<>();

        AtomicReference<Throwable> asyncError = new AtomicReference<>();
        CountDownLatch subscribed = new CountDownLatch(1);
        context.proxy.execute(() -> {
            try {
                template.runInNewSessionAsync(s -> upstream);
            } catch (Throwable e) {
                asyncError.set(e);
            }
            subscribed.countDown();
        });
        context.pumpAll();
        assertTrue(subscribed.await(5, TimeUnit.SECONDS), "异步入口未执行");
        assertTrue(asyncError.get() == null, "runInNewSessionAsync 失败: " + asyncError.get());

        // 上游在「完成线程」完成（非 context 线程）
        Thread completer = new Thread(() -> upstream.complete("done"));
        completer.start();
        completer.join(5000);

        // 出口泵任务已投递（thenOnContext：完成线程只投递）
        context.pumpUntilDispatchCount(1);
        assertTrue(context.queue.size() >= 1, "上游完成后出口泵任务必须已投递到 context 队列");

        // context 线程泵动出口泵任务（完成依赖传递）。缺陷态：whenComplete 回调体（flush/registry 恢复/close）
        // 在该步内联执行——session 立即被完成链关闭（无 context 队列收尾任务）→ 红；
        // 修复后：收尾整体作为 context 队列任务挂起，session 保持开放 → 绿。
        context.pumpOne();

        assertEquals(0, session.count("close"),
                "缺陷态：收尾在完成链线程内联执行（close 已发生、无队列收尾任务）→ 红；"
                        + "修复后：完成线程只投递不执行，收尾挂 context 队列等待泵动 → 绿");
        assertEquals(0, session.count("flush"), "flush 同样必须在 context 队列收尾任务内执行");
        assertTrue(context.queue.size() >= 1, "收尾任务（flush/registry 恢复/close）必须挂在 context 队列");

        // context 线程泵动收尾任务：flush → registry 恢复 → close（+ 兜底登记摘除）全部完成
        context.pumpAll();
        assertEquals(1, session.count("flush"), "收尾任务泵动时 flush 恰好一次");
        assertEquals(1, session.count("close"), "收尾任务泵动时 close 恰好一次（close 幂等）");
        assertNull(template.unregisterSession(), "registry 恢复必须在收尾任务内执行（收尾后无残留会话）");
    }
}
