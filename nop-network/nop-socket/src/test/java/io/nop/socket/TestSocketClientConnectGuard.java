package io.nop.socket;

import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

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
        config.setReadTimeout(5000);
        config.setHost("localhost");
        config.setPort(port);
        client.setClientConfig(config);
        return client;
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

            // 回调段异常必须原样重抛
            IllegalStateException e = assertThrows(IllegalStateException.class, client::connect);
            assertEquals("callback-boom", e.getMessage());
            // cleanup 必须已触发 onChannelClose
            assertTrue(closeCount.get() >= 1, "cleanup must fire onChannelClose");

            // 重试入口可用：回调修复后 connect 不再被 Guard.checkState 锁死
            client.setOnChannelOpen(null);
            assertDoesNotThrow(client::connect);
            BinaryCommand request = new BinaryCommand(client.getClientConfig().getMasks(), (short) 0x1, (short) 1,
                    (short) 0, "after");
            assertEquals("after", client.call(request).getDataAsString());
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
