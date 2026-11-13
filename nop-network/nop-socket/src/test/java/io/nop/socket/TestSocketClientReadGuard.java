package io.nop.socket;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 4.3-C1-016（plan 2561 裁定 B）载体：SocketClient.call 与 recv-loop 的双入口读互斥。
 * 共享 headerBuf/InputStream 不允许并发读帧——违规入口必须以 ERR_SOCKET_CONCURRENT_READ 拒绝。
 */
@Timeout(60)
public class TestSocketClientReadGuard {

    static SocketServer newEchoServer(int port) {
        SocketServer server = new SocketServer();
        server.getServerConfig().setHost("localhost");
        server.getServerConfig().setPort(port);
        server.setCommandHandler((addr, request) -> request);
        return server;
    }

    static SocketClient connect(int port) {
        SocketClient client = new SocketClient();
        ClientConfig config = new ClientConfig();
        config.setReadTimeout(5000);
        config.setHost("localhost");
        config.setPort(port);
        client.setClientConfig(config);
        client.connect();
        return client;
    }

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
    public void testCallRejectedWhileRecvLoopActive() throws Exception {
        int port = io.nop.commons.util.NetHelper.findAvailableTcpPort();
        SocketServer server = newEchoServer(port);
        server.start();
        try {
            SocketClient client = connect(port);

            // 守卫空闲时 call 正常工作
            BinaryCommand request = new BinaryCommand(client.getClientConfig().getMasks(), (short) 0x1, (short) 1,
                    (short) 0, "idle");
            assertEquals("idle", client.call(request).getDataAsString());

            // recv-loop 进入后持有读守卫
            List<BinaryCommand> received = new CopyOnWriteArrayList<>();
            client.recv(threadExecutor(), received::add);
            await(client::isReaderActive, 5000, "recv loop must acquire the reader guard");
            try {
                BinaryCommand request2 = new BinaryCommand(client.getClientConfig().getMasks(), (short) 0x1,
                        (short) 1, (short) 0, "concurrent");
                NopException e = assertThrows(NopException.class, () -> client.call(request2));
                assertEquals("nop.err.socket.concurrent-read", e.getErrorCode());
            } finally {
                client.close();
            }
            await(() -> !client.isReaderActive(), 5000, "reader guard must be released after close");
        } finally {
            server.stop();
        }
    }

    @Test
    public void testSecondCallRejectedWhileFirstCallReading() throws Exception {
        int port = io.nop.commons.util.NetHelper.findAvailableTcpPort();
        SocketServer server = new SocketServer();
        server.getServerConfig().setHost("localhost");
        server.getServerConfig().setPort(port);
        // 慢 echo：拉长 call 的读守卫持有窗口
        server.setCommandHandler((addr, request) -> {
            ThreadHelperSleep.sleep(300);
            return request;
        });
        server.start();
        try {
            SocketClient client = connect(port);
            BinaryCommand request = new BinaryCommand(client.getClientConfig().getMasks(), (short) 0x1, (short) 1,
                    (short) 0, "slow");
            java.util.concurrent.atomic.AtomicReference<BinaryCommand> result = new java.util.concurrent.atomic.AtomicReference<>();
            Thread first = new Thread(() -> result.set(client.call(request)), "first-call");
            first.setDaemon(true);
            first.start();

            await(client::isReaderActive, 5000, "first call must hold the reader guard");

            BinaryCommand request2 = new BinaryCommand(client.getClientConfig().getMasks(), (short) 0x1, (short) 1,
                    (short) 0, "second");
            NopException e = assertThrows(NopException.class, () -> client.call(request2));
            assertEquals("nop.err.socket.concurrent-read", e.getErrorCode());

            first.join(5000);
            assertFalse(first.isAlive());
            assertEquals("slow", result.get().getDataAsString());
            client.close();
        } finally {
            server.stop();
        }
    }

    static class ThreadHelperSleep {
        static void sleep(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
