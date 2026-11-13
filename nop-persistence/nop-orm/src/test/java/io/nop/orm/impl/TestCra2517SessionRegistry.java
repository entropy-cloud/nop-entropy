/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.impl;

import io.nop.api.core.context.BaseContext;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmSessionFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2517 R2-14 红①（C1-001，C10-001）+ 红⑪（C3-001，C10-002）回归钉。
 * 新增断言即新钉：缺陷态红 → 修复后绿。
 * <ul>
 * <li>红①：双线程并发首触 registry 实例创建层双实例红 → computeIfAbsent 后同实例绿；
 *     put/restore 交错（unregister 后 finally）漏 close 红 → 无条件 close 绿。</li>
 * <li>红⑪：runInNewSessionAsync 上游永不完成 + context 关闭 → session 永不关红 → 兜底清扫绿。</li>
 * </ul>
 */
public class TestCra2517SessionRegistry {

    private static IOrmSessionFactory stubFactory(IOrmSession session) {
        return ProxyStub.of(IOrmSessionFactory.class, (proxy, method, args) -> {
            if (method.getName().equals("openSession"))
                return session;
            return null;
        });
    }

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
     * 红①-a：真 context 双线程并发首触 registry 实例创建层必须恰好一实例。
     * 缺陷态（get→new→setAttribute 三步）双线程可各建一实例 → 红；修复后 computeIfAbsent 恒同实例 → 绿。
     * 同时为 rule 23 接线断言：首触落点 = context attrs 的 CONTEXT_KEY。
     */
    @Test
    public void testCra2517_C1_001_concurrentFirstTouchSingleRegistryInstance() throws Exception {
        int rounds = 50;
        for (int i = 0; i < rounds; i++) {
            BaseContext context = new BaseContext();
            CountDownLatch start = new CountDownLatch(1);
            AtomicReference<OrmSessionRegistry> r1 = new AtomicReference<>();
            AtomicReference<OrmSessionRegistry> r2 = new AtomicReference<>();
            AtomicReference<Throwable> touchError = new AtomicReference<>();
            Callable<OrmSessionRegistry> touch = () -> {
                start.await();
                // registry 实例创建层的 get-or-create（与 OrmSessionRegistry.instance() 首触同层）
                return (OrmSessionRegistry) context.getAttrs().computeIfAbsent(
                        OrmSessionRegistry.CONTEXT_KEY, k -> new OrmSessionRegistry());
            };
            Thread t1 = new Thread(() -> {
                try {
                    r1.set(context.executeWithContext(touch));
                } catch (Exception e) {
                    touchError.set(e);
                }
            });
            Thread t2 = new Thread(() -> {
                try {
                    r2.set(context.executeWithContext(touch));
                } catch (Exception e) {
                    touchError.compareAndSet(null, e);
                }
            });
            t1.start();
            t2.start();
            start.countDown();
            t1.join(5000);
            t2.join(5000);

            assertNull(touchError.get(), "并发首触不得抛出异常: " + touchError.get());
            assertNotNull(r1.get());
            assertSame(r1.get(), r2.get(), "并发首触必须得到同一 registry 实例（round " + i + "）");
            context.close();
        }
    }

    /**
     * 红①-b：put/restore 交错（回调内 unregister 场景）不再跳过关闭——session 必须无条件 close（close 幂等兜底）。
     * 缺陷态：registry.put 返回 null（entry 已被 unregister）→ 跳过 close → 会话泄漏 → 红；
     * 修复后：WARN + 无条件 close → 绿。
     */
    @Test
    public void testCra2517_C1_001_restoreMismatchStillClosesSession() {
        RecordingSession session = new RecordingSession();
        OrmTemplateImpl template = new OrmTemplateImpl(stubFactory(session.proxy));
        template.runInNewSession(s -> {
            // 模拟交错：回调内当前会话被 unregister，finally 的 restore put 将返回 null
            template.unregisterSession();
            return null;
        });

        // 缺陷态跳过关闭（泄漏）→ 红；修复后无条件 close → 绿
        assertEquals(1, session.count("close"), "put/restore 交错后本次会话必须无条件关闭（close 幂等兜底）");
    }

    /**
     * 红①-c 接线断言（rule 23）：instance() 消费 context attrs，同 context 内复用同一实例。
     */
    @Test
    public void testCra2517_C1_001_instanceWiredToContextAttrs() throws Exception {
        BaseContext context = new BaseContext();
        AtomicReference<OrmSessionRegistry> first = new AtomicReference<>();
        context.executeWithContext(() -> {
            first.set(OrmSessionRegistry.instance());
            return null;
        });
        AtomicReference<OrmSessionRegistry> second = new AtomicReference<>();
        context.executeWithContext(() -> {
            second.set(OrmSessionRegistry.instance());
            return null;
        });
        assertSame(first.get(), second.get(), "同 context 内 instance() 必须复用同一 registry 实例");
        assertSame(first.get(), context.getAttribute(OrmSessionRegistry.CONTEXT_KEY),
                "registry 实例必须落 context attrs（computeIfAbsent 接线）");
        context.close();
    }

    /**
     * 红⑪：runInNewSessionAsync 上游永不完成 + context 关闭 → 兜底关闭登记清扫后 session 必须关闭。
     * 缺陷态：session 永不关（注册表滞留泄漏）→ 红；修复后 context 关闭后惰性清扫兜底 close → 绿。
     */
    @Test
    public void testCra2517_C3_001_pendingCloseSweptAfterContextClose() throws Exception {
        RecordingSession session = new RecordingSession();
        OrmTemplateImpl template = new OrmTemplateImpl(stubFactory(session.proxy));

        BaseContext context = new BaseContext();
        CompletableFuture<IOrmSession> never = new CompletableFuture<>();
        context.executeWithContext(() -> {
            // 上游 future 永不完成
            template.runInNewSessionAsync(s -> never);
            return null;
        });

        assertEquals(0, session.count("close"), "上游未完成时 session 尚未关闭（挂起登记态）");

        // context 关闭 → 兜底登记触发（instance() 惰性清扫）
        context.close();
        OrmSessionRegistry.instance();

        assertEquals(1, session.count("close"), "context 关闭后挂起会话必须被兜底关闭（红⑪缺陷态：永不关闭 → 红）");
    }

    /**
     * runInNewSession 正常路径行为保持：回调后恢复 oldSession 并关闭本次会话（既有语义零回退）。
     */
    @Test
    public void testCra2517_C1_001_normalPathStillClosesSession() {
        RecordingSession oldSession = new RecordingSession();
        RecordingSession newSession = new RecordingSession();
        AtomicInteger openCount = new AtomicInteger();
        IOrmSessionFactory factory = ProxyStub.of(IOrmSessionFactory.class, (proxy, method, args) -> {
            if (method.getName().equals("openSession"))
                return openCount.incrementAndGet() == 1 ? newSession.proxy : oldSession.proxy;
            return null;
        });

        OrmTemplateImpl template = new OrmTemplateImpl(factory);
        String ret = template.runInNewSession(s -> {
            assertSame(newSession.proxy, s);
            return "ok";
        });
        assertEquals("ok", ret);
        assertEquals(1, newSession.count("close"));
        assertEquals(0, oldSession.count("close"));
        assertNull(template.unregisterSession(), "runInNewSession 后恢复 oldSession=null，registry 应为空");
    }
}
