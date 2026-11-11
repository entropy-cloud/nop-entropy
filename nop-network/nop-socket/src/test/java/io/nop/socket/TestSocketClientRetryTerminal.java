package io.nop.socket;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.util.retry.RetryPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 4.3-C3-026（plan 2561 裁定 H）载体：
 * ① send 在未连接（os==null）时必须 typed 异常（原静默丢弃）；
 * ② recv 重连链彻底失败必须经终态 exceptionally 处理器收口：LOG + closed 置位 + onChannelClose。
 */
@Timeout(60)
public class TestSocketClientRetryTerminal {

    static Executor threadExecutor() {
        return command -> {
            Thread thread = new Thread(command, "socket-recv-loop");
            thread.setDaemon(true);
            thread.start();
        };
    }

    static void await(BooleanSupplier cond, long timeoutMs, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!cond.getAsBoolean()) {
            if (System.currentTimeMillis() >= deadline)
                throw new AssertionError(message);
            Thread.sleep(10);
        }
    }

    @Test
    public void testSendWithoutConnectionIsTyped() {
        SocketClient client = new SocketClient();
        BinaryCommand cmd = new BinaryCommand(client.getClientConfig().getMasks(), (short) 0x1, (short) 1, (short) 0,
                "x");
        NopException e = assertThrows(NopException.class, () -> client.send(cmd, true));
        assertEquals("nop.err.socket.not-connected", e.getErrorCode());
    }

    @Test
    public void testReconnectTerminalHandlerMarksClosedAndFiresOnChannelClose() throws Exception {
        int port = io.nop.commons.util.NetHelper.findAvailableTcpPort();
        SocketServer server = new SocketServer();
        server.getServerConfig().setHost("localhost");
        server.getServerConfig().setPort(port);
        server.setCommandHandler((addr, request) -> request);
        server.start();

        SocketClient client = new SocketClient();
        ClientConfig config = new ClientConfig();
        config.setReadTimeout(5000);
        config.setHost("localhost");
        config.setPort(port);
        config.setAutoReconnect(true);
        // 重试策略：首次失败即放弃——触发重连链终态 exceptionally 处理器
        config.setReconnectPolicy(new RetryPolicy<Object>() {
            @Override
            public long getRetryDelay(Throwable ex, int retryTimes, Object context) {
                return -1;
            }
        });
        client.setClientConfig(config);
        client.setTimer(GlobalExecutors.globalTimer());

        AtomicInteger closeCount = new AtomicInteger();
        client.setOnChannelClose(closeCount::incrementAndGet);
        client.connect();

        try {
            client.recv(threadExecutor(), cmd -> {
            });
            await(client::isReaderActive, 5000, "recv loop must be active");

            // 服务端停止 → 读失败 → reconnect 失败 → 终态处理器收口
            server.stop();

            await(client::isClosed, 10000, "terminal handler must mark the client closed");
            assertTrue(closeCount.get() >= 1, "terminal handler must fire onChannelClose");
        } finally {
            if (!client.isClosed())
                client.close();
            server.stop();
        }
    }
}
