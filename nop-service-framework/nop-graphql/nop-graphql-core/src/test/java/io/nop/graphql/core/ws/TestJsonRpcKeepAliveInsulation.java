package io.nop.graphql.core.ws;

import io.nop.commons.concurrent.executor.IPeriodicTaskListener;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CRA 30 点表 #2（finding 2.1-C3-015 补登站点）回归 + Phase 2 接线验证。
 *
 * <p>站点事实（C3-round2 §1 表 #2）：JsonRpcWebSocketHandler 在 globalTimer 上以
 * scheduleWithFixedDelay 挂 keepalive（构造函数 :82），旧 sendKeepAlive 仅 catch(IOException)——
 * 消息序列化 RuntimeException / Error 逃逸任务体即杀 keepalive 链，僵尸 WebSocket 连接永不清扫。
 * 修复 = 站点侧 catch Throwable + 结构化观测日志（失败可见、不吞语义、不向外传播）。
 *
 * <p>plan 2562 裁定 E（RP-2/RP-4 重定基线）：keepAliveSender 假装句柄（never-completing
 * CF）已删除，unregisterKeepAlive 注册表逐出是唯一逐出路径——原「取消断言」重写为
 * LIVE_HANDLERS 注册表逐出断言 + markReady 装配窗口断言（本文件后两节）。
 *
 * <p>该站点所属载体（globalTimer 直挂 → DefaultScheduledExecutor.scheduleWithFixedDelay）已被
 * Phase 1 绝缘层改写；testGlobalTimerDirectScheduleWiringReachesInsulatedPath 从本消费方模块
 * 运行时验证该载体入口真实走到绝缘路径（任务体 Throwable → 观测 + 重臂 + future 失败终态）。
 */
@Timeout(30)
public class TestJsonRpcKeepAliveInsulation {

    @BeforeAll
    public static void init() {
        // keepalive 任务体经 JSON.stringify 序列化 ping 通知，JSON provider 在平台初始化时注册
        CoreInitialization.initialize();
    }


    /**
     * sendMessage 按注入序列抛出异常（RuntimeException / Error 谱系）；序列耗尽后正常记录消息。
     */
    static class FailingWebSocketSession implements IWebSocketSession {
        final Deque<Throwable> throwOnSend = new ArrayDeque<>();
        final List<String> sentMessages = new ArrayList<>();
        boolean closed;

        @Override
        public void sendMessage(String message) throws IOException {
            Throwable e = throwOnSend.poll();
            if (e == null) {
                sentMessages.add(message);
            } else if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw (Error) e;
            }
        }

        @Override
        public void close(short statusCode, String reason) {
            closed = true;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }
    }

    private static void awaitUntil(String message, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean())
                return;
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail(message);
            }
        }
        fail(message);
    }

    /**
     * 修复语义回归：keepalive 轮次内抛出 RuntimeException / Error 均不得向外传播，
     * 且失败轮次之后链仍继续投递（keepalive 保持尽力而为 + 失败可观测，非静默吞）。
     *
     * <p>plan 2498 Phase 3（2.3-C8-001/A5）：send 改经会话级有界 send pump 异步卸载到共享
     * worker 池——本测试的绝缘断言全部保留，仅投递断言改为 awaitUntil 适配异步时序；
     * 失败轮次不得投递消息的断言在异步形态下同样成立（失败 action 内部消化，永不写入 sentMessages）。
     */
    @Test
    public void testKeepAliveThrowableDoesNotPropagateAndChainContinues() {
        FailingWebSocketSession session = new FailingWebSocketSession();
        JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler((request, headers) -> null, session);
        // 4.4-C4-002（plan 2562 裁定 E②）：装配完成入表（原构造即注册——新结构下模拟 endpoint
        // 调 markReady；本测试聚焦绝缘语义，非装配窗口）
        handler.markReady();

        try {
            // 第 1 轮：消息序列化 RuntimeException（审计指认的逃逸面）——不得抛出
            session.throwOnSend.add(new IllegalStateException("serialization-boom"));
            assertDoesNotThrow(handler::sendKeepAlive);
            awaitUntil("failed round must not deliver a message (async pump must not swallow the failure)",
                    () -> session.throwOnSend.isEmpty());
            assertTrue(session.sentMessages.isEmpty(), "failed round must not deliver a message");

            // 第 2 轮：Error 谱系（守卫宽度=Throwable）——同样不得抛出
            session.throwOnSend.add(new LinkageError("injected-keepalive-error"));
            assertDoesNotThrow(handler::sendKeepAlive);
            awaitUntil("error round must be consumed without delivery",
                    () -> session.throwOnSend.isEmpty());
            assertTrue(session.sentMessages.isEmpty(), "error round must not deliver a message");

            // 第 3 轮：链未死——正常投递 ping keepalive（异步 pump 投递）
            assertDoesNotThrow(handler::sendKeepAlive);
            awaitUntil("keepalive chain must continue after failures",
                    () -> !session.sentMessages.isEmpty());
            assertEquals(1, session.sentMessages.size(), "keepalive chain must continue after failures");
            assertTrue(session.sentMessages.get(0).contains("\"method\":\"ping\""),
                    "recovered round must deliver the ping keepalive notification");

            // RP-2（plan 2562 裁定 E 重写）：markReady 后会话在 LIVE_HANDLERS 注册表中参与
            // keepalive 扫描；站点内失败不改写注册表参与语义（原 keepAliveSender 假装句柄
            // 已删除——注册表逐出是唯一逐出路径）
            assertTrue(JsonRpcWebSocketHandler.LIVE_HANDLERS.contains(handler),
                    "marked-ready handler must stay registered in LIVE_HANDLERS despite task body failures");
        } finally {
            handler.onClose();
        }
        // RP-2 逐出断言（重写）：onClose 必须把 handler 从注册表逐出（原钉断言的
        // never-completing CF cancel 是空操作，钉面过时——新机制钉为注册表逐出）
        assertFalse(JsonRpcWebSocketHandler.LIVE_HANDLERS.contains(handler),
                "onClose must evict the handler from LIVE_HANDLERS (sole eviction path, plan 2562 裁定 E①)");
    }

    /**
     * RP-4（plan 2562 裁定 E 重写）：异常路径逐出——onThrowable 必须把 handler 从
     * LIVE_HANDLERS 注册表逐出（throwOnSend stub 可抛；取消断言改为「注册表逐出」断言）。
     */
    @Test
    public void throwableEvictsHandlerFromLiveRegistry() {
        FailingWebSocketSession session = new FailingWebSocketSession();
        JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler((request, headers) -> null, session);
        handler.markReady();
        assertTrue(JsonRpcWebSocketHandler.LIVE_HANDLERS.contains(handler),
                "marked-ready handler must be registered before throwable");

        handler.onThrowable(new RuntimeException("transport blew up"));

        assertFalse(JsonRpcWebSocketHandler.LIVE_HANDLERS.contains(handler),
                "onThrowable must evict the handler from LIVE_HANDLERS (RP-4 rewritten pin)");
    }

    /**
     * RP-4（plan 2562 裁定 E②）装配窗口钉：构造期不入 LIVE_HANDLERS——半装配 session
     * （extractor 未绑定窗）不参与 keepalive 扫描，ping 不再发往半装配会话；
     * markReady 后入表；closed-before-ready 的会话永不入表（不留残余）。
     */
    @Test
    public void assemblyWindowHandlerJoinsRegistryOnlyAfterMarkReady() {
        FailingWebSocketSession session = new FailingWebSocketSession();
        JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler((request, headers) -> null, session);

        try {
            // 装配窗：未 markReady——不在注册表，keepalive 扫描轮不投递 ping
            assertFalse(JsonRpcWebSocketHandler.LIVE_HANDLERS.contains(handler),
                    "handler must not join LIVE_HANDLERS before markReady (assembly window)");
            JsonRpcWebSocketHandler.sendKeepAliveToAll();
            assertTrue(session.sentMessages.isEmpty(),
                    "keepalive ping must not be sent to a half-assembled session"
                            + " (extractor unbound window, plan 2562 裁定 E②)");

            // 装配完成：markReady 后入表，下一扫描轮投递 ping
            handler.markReady();
            assertTrue(JsonRpcWebSocketHandler.LIVE_HANDLERS.contains(handler),
                    "markReady must register the handler in LIVE_HANDLERS");
            JsonRpcWebSocketHandler.sendKeepAliveToAll();
            awaitUntil("ping must be delivered after markReady",
                    () -> !session.sentMessages.isEmpty());
            assertTrue(session.sentMessages.get(0).contains("\"method\":\"ping\""));
        } finally {
            handler.onClose();
        }
        assertFalse(JsonRpcWebSocketHandler.LIVE_HANDLERS.contains(handler),
                "onClose must evict after the assembly window (no residual entry)");
    }

    /**
     * RP-4（plan 2562 裁定 E②）closed-before-ready：会话在 markReady 前已关闭——
     * markReady 不入注册表（装配未完成即关闭，不残留任何条目）。
     */
    @Test
    public void closedBeforeReadyLeavesNothingInRegistry() {
        FailingWebSocketSession session = new FailingWebSocketSession();
        JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler((request, headers) -> null, session);
        session.closed = true;

        handler.markReady();

        assertFalse(JsonRpcWebSocketHandler.LIVE_HANDLERS.contains(handler),
                "closed-before-ready must not register the handler (leaves nothing to clean)");
    }

    /**
     * rule 23 接线验证（Phase 2）：30 点表 #2 站点的真实载体形态 = GlobalExecutors.globalTimer()
     * 直挂 scheduleWithFixedDelay（DefaultScheduledExecutor 载体）。从本消费方模块运行时证明
     * 该入口已路由到绝缘核心：任务体 Throwable → listener 失败回调可达 + 链重臂 + future 失败终态。
     */
    @Test
    public void testGlobalTimerDirectScheduleWiringReachesInsulatedPath() {
        AtomicInteger runs = new AtomicInteger();
        List<Throwable> failures = new ArrayList<>();
        IPeriodicTaskListener listener = new IPeriodicTaskListener() {
            @Override
            public void onRunFailure(Throwable exception, long consecutiveFailures) {
                failures.add(exception);
            }
        };

        Future<?> chain = io.nop.commons.concurrent.executor.GlobalExecutors.globalTimer()
                .scheduleWithFixedDelay(() -> {
                    if (runs.incrementAndGet() == 1)
                        throw new IllegalStateException("ws-keepalive-wiring-probe");
                }, 0, 20, TimeUnit.MILLISECONDS, "ws-keepalive-wiring", listener);

        try {
            assertTrue(chain instanceof CompletableFuture, "insulated carrier must return the insulated chain future");
            awaitUntil("listener failure callback must be reached through globalTimer direct schedule",
                    () -> !failures.isEmpty());
            assertInstanceOf(IllegalStateException.class, failures.get(0));
            awaitUntil("chain must re-arm after failure (keepalive would die in the old carrier semantics)",
                    () -> runs.get() >= 2);
            assertTrue(chain.isDone() && !chain.isCancelled());
            assertTrue(((CompletableFuture<?>) chain).isCompletedExceptionally(),
                    "first failure must settle the returned future exceptionally (observable, not silent)");
        } finally {
            chain.cancel(false);
        }
    }
}
