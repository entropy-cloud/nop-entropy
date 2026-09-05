package io.nop.socket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
public class TestSocketServerRestart {

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

    @Test
    public void testServerRestartableAfterStop() {
        int port = io.nop.commons.util.NetHelper.findAvailableTcpPort();
        SocketServer server = newEchoServer(port);
        server.start();

        SocketClient client = connect(port);
        BinaryCommand request = new BinaryCommand(client.getClientConfig().getMasks(), (short) 0x1, (short) 1,
                (short) 0, "before");
        assertEquals("before", client.call(request).getDataAsString());
        client.close();

        server.stop();

        // stop 之后必须可以重启并继续服务
        assertDoesNotThrow(server::start);
        try {
            SocketClient client2 = connect(port);
            try {
                BinaryCommand request2 = new BinaryCommand(client2.getClientConfig().getMasks(), (short) 0x1,
                        (short) 1, (short) 0, "after");
                assertEquals("after", client2.call(request2).getDataAsString());
            } finally {
                client2.close();
            }
        } finally {
            server.stop();
        }
    }

    @Test
    public void testAcceptLoopSurvivesExecutorRejection() throws Exception {
        int port = io.nop.commons.util.NetHelper.findAvailableTcpPort();
        SocketServer server = newEchoServer(port);

        AtomicInteger calls = new AtomicInteger();
        Executor rejectingOnce = command -> {
            // 第一次 execute 是 accept 循环本身；第二个调用（首个连接的处理任务）被拒绝
            if (calls.incrementAndGet() == 2) {
                throw new java.util.concurrent.RejectedExecutionException("queue full (simulated)");
            }
            Thread thread = new Thread(command, "socket-worker");
            thread.setDaemon(true);
            thread.start();
        };
        server.setExecutor(rejectingOnce);
        server.start();

        try {
            // 第一个连接触发 executor 拒绝
            SocketClient rejected = connect(port);
            rejected.ping();
            Thread.sleep(200);
            rejected.close();

            // 第二个连接必须仍能被接受并正常工作（accept 循环不能被第一次拒绝杀死）
            SocketClient second = connect(port);
            try {
                BinaryCommand request = new BinaryCommand(second.getClientConfig().getMasks(), (short) 0x1,
                        (short) 1, (short) 0, "still-alive");
                assertEquals("still-alive", second.call(request).getDataAsString());
            } finally {
                second.close();
            }
        } finally {
            server.stop();
        }
    }

    @Test
    public void testBroadcastDoesNotCorruptConcurrentResponses() throws Exception {
        int port = io.nop.commons.util.NetHelper.findAvailableTcpPort();
        SocketServer server = newEchoServer(port);
        server.start();

        SocketClient client = connect(port);
        try {
            byte[] payload = new byte[512];
            java.util.Arrays.fill(payload, (byte) 'x');

            // 后台接收线程：任何帧交错损坏都会让 recv 抛出解析异常
            java.util.List<String> received = new java.util.concurrent.CopyOnWriteArrayList<>();
            java.util.concurrent.atomic.AtomicBoolean recvError = new java.util.concurrent.atomic.AtomicBoolean();
            Thread receiver = new Thread(() -> {
                try {
                    while (!recvError.get()) {
                        BinaryCommand cmd = client.recv();
                        if (cmd == null)
                            break;
                        received.add(cmd.getDataAsString());
                    }
                } catch (Exception e) {
                    recvError.set(true);
                }
            });
            receiver.setDaemon(true);
            receiver.start();

            Thread broadcaster = new Thread(() -> {
                for (int i = 0; i < 100; i++) {
                    server.broadcast(new BinaryCommand(client.getClientConfig().getMasks(), (short) 0x2,
                            (short) 1, (short) 0, new String(payload)));
                    ThreadHelperSleep.sleep(2);
                }
            });
            broadcaster.start();

            // 客户端持续发送请求；响应与广播可能乱序到达，但所有帧必须完整可解析
            for (int i = 0; i < 100; i++) {
                BinaryCommand request = new BinaryCommand(client.getClientConfig().getMasks(), (short) 0x1,
                        (short) 1, (short) 0, "req-" + i);
                client.send(request, true);
            }
            broadcaster.join(5000);

            // 等待全部响应与广播到达
            for (int i = 0; i < 500; i++) {
                long responses = received.stream().filter(d -> d.startsWith("req-")).count();
                if (responses >= 100)
                    break;
                ThreadHelperSleep.sleep(10);
            }

            assertTrue(!recvError.get(), "no frame parse error is allowed");
            long responses = received.stream().filter(d -> d.startsWith("req-")).count();
            long broadcasts = received.stream().filter(d -> d.length() == 512).count();
            assertTrue(responses >= 100, "all responses must arrive intact, got " + responses);
            assertTrue(broadcasts >= 99, "broadcasts must arrive intact, got " + broadcasts);
            assertTrue(received.stream().filter(d -> d.startsWith("req-")).allMatch(d -> d.matches("req-\\d+")),
                    "response frames must not be truncated");
        } finally {
            client.close();
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
