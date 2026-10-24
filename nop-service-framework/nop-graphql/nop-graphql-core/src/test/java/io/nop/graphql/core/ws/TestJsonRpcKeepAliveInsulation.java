package io.nop.graphql.core.ws;

import io.nop.commons.concurrent.executor.IPeriodicTaskListener;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
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

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
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

            // 周期链参与未被失败取消（A5 收敛后为共享链注册句柄；站点内兜底不改写链存活语义）
            assertFalse(handler.keepAliveSender.isCancelled(),
                    "keepalive chain participation must not be cancelled by task body failures");
        } finally {
            handler.onClose();
        }
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
