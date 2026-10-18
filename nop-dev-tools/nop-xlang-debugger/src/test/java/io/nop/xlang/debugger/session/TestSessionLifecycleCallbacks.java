/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger.session;

import io.nop.xlang.debugger.XLangDebugger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SessionListener 回调契约（接线验证 #23）：onCreated/onDestroyed 触发、异常隔离
 * （listener 抛异常不中断创建/销毁链）、锁外触发（回调中重入 manager 不死锁）。
 */
public class TestSessionLifecycleCallbacks {
    private SessionDebugManager manager;
    private final List<String> events = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        manager = SessionDebugManager.instance();
        manager.init(new XLangDebugger());
    }

    @AfterEach
    public void tearDown() {
        manager.setSessionListener(null);
        DebugSessionScope.clear();
        manager.reset();
    }

    @Test
    public void testCreatedAndDestroyedCallbacksFire() {
        manager.setSessionListener(new RecordingListener(events));

        DebugSessionHandle handle = manager.createSession("cb");
        manager.destroySession(handle);

        assertEquals(2, events.size());
        assertEquals("created:cb", events.get(0));
        assertEquals("destroyed:cb", events.get(1));
    }

    @Test
    public void testListenerExceptionDoesNotBreakLifecycleChain() {
        manager.setSessionListener(new SessionListener() {
            @Override
            public void onCreated(DebugSessionHandle handle, XLangDebugger debugger) {
                throw new IllegalStateException("listener-boom-created");
            }

            @Override
            public void onDestroyed(DebugSessionHandle handle, XLangDebugger debugger) {
                throw new IllegalStateException("listener-boom-destroyed");
            }
        });

        // 创建与销毁链必须完整走完（异常被隔离），实例正确关闭
        DebugSessionHandle handle = manager.createSession("boom");
        assertNotNull(handle);
        manager.destroySession(handle);
        assertTrue(handle.isDestroyed());
        assertTrue(handle.getDebugger().isClosed());
        org.junit.jupiter.api.Assertions.assertNull(manager.getSession("boom"));
    }

    @Test
    public void testCallbackOutsideManagerLock() throws Exception {
        // 真实锁外验证：onDestroyed 回调中销毁"另一个"会话——回调若在 manager 锁内
        // 触发，destroySession 的 synchronized 将永久阻塞（junit 超时兜底）
        manager.setSessionListener(new SessionListener() {
            @Override
            public void onCreated(DebugSessionHandle handle, XLangDebugger debugger) {
                // 锁外：创建期间可再创建其他会话
            }

            @Override
            public void onDestroyed(DebugSessionHandle handle, XLangDebugger debugger) {
                if ("first".equals(handle.getSessionId())) {
                    // 回调内重入 manager 的 synchronized 方法（跨会话销毁）
                    manager.destroySession(manager.getSession("second"));
                }
            }
        });
        DebugSessionHandle first = manager.createSession("first");
        DebugSessionHandle second = manager.createSession("second");

        Thread worker = new Thread(() -> manager.destroySession(first));
        worker.start();
        worker.join(5000);
        assertFalse(worker.isAlive(), "回调内重入 synchronized 方法不得死锁（锁外触发验证）");
        assertTrue(first.isDestroyed());
        assertTrue(second.isDestroyed());
    }

    static class RecordingListener implements SessionListener {
        private final List<String> events;

        RecordingListener(List<String> events) {
            this.events = events;
        }

        @Override
        public void onCreated(DebugSessionHandle handle, XLangDebugger debugger) {
            events.add("created:" + handle.getSessionId());
        }

        @Override
        public void onDestroyed(DebugSessionHandle handle, XLangDebugger debugger) {
            events.add("destroyed:" + handle.getSessionId());
        }
    }

    /** plan 2471 审计 F5 回归：并发 createSession 时 onCreated 回调与返回句柄一一对应（不交叉投递）。 */
    @Test
    public void testConcurrentCreateSessionCallbacksNotCrossed() throws Exception {
        Map<String, String> callbackBySession = new ConcurrentHashMap<>();
        manager.setSessionListener(new SessionListener() {
            @Override
            public void onCreated(DebugSessionHandle handle, XLangDebugger debugger) {
                callbackBySession.put(handle.getSessionId(), Thread.currentThread().getName());
            }

            @Override
            public void onDestroyed(DebugSessionHandle handle, XLangDebugger debugger) {
            }
        });

        int n = 16;
        CyclicBarrier barrier = new CyclicBarrier(n);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Future<DebugSessionHandle>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                final String sessionId = "conc-" + i;
                futures.add(pool.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return manager.createSession(sessionId);
                }));
            }
            for (int i = 0; i < n; i++) {
                DebugSessionHandle handle = futures.get(i).get(10, TimeUnit.SECONDS);
                assertNotNull(handle);
                // 每个会话的 onCreated 必须被其创建线程消费（不交叉：旧单槽机制会把
                // A 线程的回调投递给 B 线程——回调缺失即交叉证据）
                assertNotNull(callbackBySession.get(handle.getSessionId()),
                        "会话 " + handle.getSessionId() + " 的 onCreated 回调缺失（被交叉投递）");
            }
            assertEquals(n, callbackBySession.size());
        } finally {
            pool.shutdownNow();
        }
    }
}
