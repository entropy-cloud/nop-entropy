package io.nop.socket;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.thread.ThreadHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.socket.SocketErrors.ERR_SOCKET_CONNECT_FAIL;
import static io.nop.socket.SocketErrors.ERR_SOCKET_READ_FAIL;
import static io.nop.socket.SocketErrors.ERR_SOCKET_READ_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 4.3-C6-027（plan 2561 裁定 N）载体：connect 回调段/心跳段异常必须回收连接（cleanup + onChannelClose）
 * 后重抛——异常后的重试入口（Guard.checkState(socket==null)）不得被锁死。
 */
@Timeout(60)
public class TestSocketClientConnectGuard {

    static SocketServer newEchoServer(int port) {
        SocketServer server = new SocketServer();
        server.getServerConfig().setHost("localhost");
        server.getServerConfig().setPort(port);
        server.setCommandHandler((addr, request) -> request);
        return server;
    }

    static SocketClient newClient(int port) {
        SocketClient client = new SocketClient();
        ClientConfig config = new ClientConfig();
        // 满载 seq 全量下 5s 读超时过紧（accept→dispatch 链路被调度延迟拉长），放宽到 20s
        // （仍小于类级 @Timeout(60)），超时语义不变。
        config.setReadTimeout(20000);
        config.setHost("localhost");
        config.setPort(port);
        client.setClientConfig(config);
        return client;
    }

    /**
     * 满载时服务端 accept→dispatch 链路可能被调度延迟拉长，甚至瞬时 dispatch 被线程池拒绝
     * （服务端随即关闭该连接 → 客户端 readFully EOF）。这类瞬时基建抖动不属于本测试的断言面
     * （守卫语义），因此对 typed IO/连接类 NopException 做"有界窗口内事件驱动重试"；重试预算
     * 耗尽仍失败则照常抛出——断言语义强度不降。
     */
    private static boolean isTransientIoError(NopException e) {
        String code = e.getErrorCode();
        return code.equals(ERR_SOCKET_CONNECT_FAIL.getErrorCode())
                || code.equals(ERR_SOCKET_READ_FAIL.getErrorCode())
                || code.equals(ERR_SOCKET_READ_TIMEOUT.getErrorCode());
    }

    /**
     * connect 守卫阶段：在有限窗口内等待"回调段异常"可复现。瞬时连接基建失败（connect-fail）
     * 重试；IllegalStateException（callback-boom）即为本测试等待的事件，直接向上传播。
     */
    private static void connectExpectingCallbackBoom(SocketClient client, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            try {
                client.connect();
                // 未抛异常：不应发生（onChannelOpen 恒抛）；释放连接后继续等事件
                client.close();
            } catch (IllegalStateException e) {
                throw e;
            } catch (NopException e) {
                if (!isTransientIoError(e) || System.currentTimeMillis() > deadline)
                    throw e;
            }
            ThreadHelper.sleep(50);
        }
    }

    /**
     * echo 阶段：在有限窗口内等待 echo 往返成功。readFully EOF / 读超时等瞬时 IO 抖动触发
     * reconnect 后重试；超窗则让最后一次异常照常抛出。
     */
    private static String callEchoWithRetry(SocketClient client, BinaryCommand request, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            try {
                return client.call(request).getDataAsString();
            } catch (NopException e) {
                if (!isTransientIoError(e) || System.currentTimeMillis() > deadline)
                    throw e;
                // 半开连接回收后重试入口仍可用（与本测试守卫语义一致）
                client.reconnect();
            }
        }
    }

    @Test
    public void testCallbackExceptionCleansUpAndKeepsRetryEntrance() {
        int port = io.nop.commons.util.NetHelper.findAvailableTcpPort();
        SocketServer server = newEchoServer(port);
        server.start();
        try {
            SocketClient client = newClient(port);
            AtomicInteger closeCount = new AtomicInteger();
            client.setOnChannelClose(closeCount::incrementAndGet);
            client.setOnChannelOpen(() -> {
                throw new IllegalStateException("callback-boom");
            });

            // 回调段异常必须原样重抛（瞬时连接基建抖动在窗口内重试，事件=IllegalStateException）
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> connectExpectingCallbackBoom(client, 20000));
            assertEquals("callback-boom", e.getMessage());
            // cleanup 必须已触发 onChannelClose
            assertTrue(closeCount.get() >= 1, "cleanup must fire onChannelClose");

            // 重试入口可用：回调修复后 connect 不再被 Guard.checkState 锁死
            client.setOnChannelOpen(null);
            BinaryCommand request = new BinaryCommand(client.getClientConfig().getMasks(), (short) 0x1, (short) 1,
                    (short) 0, "after");
            // connect + echo 在有界窗口内事件驱动确认（瞬时 IO 抖动重试），最终值断言不变
            assertDoesNotThrow(() -> {
                long deadline = System.currentTimeMillis() + 20000;
                while (true) {
                    try {
                        client.connect();
                        break;
                    } catch (NopException ce) {
                        if (!isTransientIoError(ce) || System.currentTimeMillis() > deadline)
                            throw ce;
                        ThreadHelper.sleep(50);
                    }
                }
            });
            assertEquals("after", callEchoWithRetry(client, request, 20000));
            client.close();
        } finally {
            server.stop();
        }
    }

    @Test
    public void testHeartbeatScheduleFailureCleansUpAndKeepsRetryEntrance() {
        int port = io.nop.commons.util.NetHelper.findAvailableTcpPort();
        SocketServer server = newEchoServer(port);
        server.start();
        try {
            SocketClient client = newClient(port);
            AtomicInteger closeCount = new AtomicInteger();
            client.setOnChannelClose(closeCount::incrementAndGet);

            // 心跳段异常：scheduleWithFixedDelay 抛出 UnsupportedOperationException
            IScheduledExecutor brokenTimer = (IScheduledExecutor) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{IScheduledExecutor.class},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) {
                            if (method.getName().equals("scheduleWithFixedDelay"))
                                throw new UnsupportedOperationException("timer-down");
                            return null;
                        }
                    });
            client.setTimer(brokenTimer);
            client.getClientConfig().setHeartbeatInterval(100);

            // 心跳段异常必须重抛（而非 Guard 的 IllegalStateException——重试入口未被锁死的证据）
            UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class, client::connect);
            assertEquals("timer-down", e.getMessage());
            assertTrue(closeCount.get() >= 1, "cleanup must fire onChannelClose");
        } finally {
            server.stop();
        }
    }

    @Test
    public void testGlobalTimerAvailable() {
        // globalTimer 为 IScheduledExecutor（recv 重连链 timer 依赖）
        assertTrue(GlobalExecutors.globalTimer() instanceof IScheduledExecutor);
    }
}
