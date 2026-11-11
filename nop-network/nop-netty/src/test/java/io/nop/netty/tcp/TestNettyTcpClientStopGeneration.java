package io.nop.netty.tcp;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.nop.commons.util.NetHelper;
import io.nop.netty.config.NettyTcpClientConfig;
import io.nop.netty.config.NettyTcpServerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA-10（4.3-MA-14 / plan 2560 裁定 C）stop 世代守卫载体：
 * stop 递增 generation 后，两类「复活路径」必须放弃——
 * ①已调度重连任务触发时（世代失配放弃）；②在途连接建立完成回调（世代失配放弃，
 * 不得递增失败计数、不得继续调度重连）。
 */
@Timeout(60)
public class TestNettyTcpClientStopGeneration {

    static class CountingClient extends NettyTcpClient {
        final AtomicInteger doConnectCalls = new AtomicInteger();
        final AtomicInteger scheduleReconnectCalls = new AtomicInteger();

        @Override
        protected void doConnect() {
            doConnectCalls.incrementAndGet();
            super.doConnect();
        }

        @Override
        protected void scheduleReconnect(EventLoopGroup eventLoop, Throwable e) {
            scheduleReconnectCalls.incrementAndGet();
            super.scheduleReconnect(eventLoop, e);
        }
    }

    static int failCountOf(NettyTcpClient client) throws Exception {
        java.lang.reflect.Field field = NettyTcpClient.class.getDeclaredField("connectFailCount");
        field.setAccessible(true);
        return ((java.util.concurrent.atomic.AtomicInteger) field.get(client)).get();
    }

    static NettyTcpClientConfig config(String host, int port, boolean autoReconnect, int retryDelay) {
        NettyTcpClientConfig config = new NettyTcpClientConfig();
        config.setAutoReconnect(autoReconnect);
        config.setConnectTimeout(500);
        config.setRemoteHost(host);
        config.setRemotePort(port);
        config.setMaxConnectRetryCount(1000);
        config.setMaxConnectRetryDelay(5000);
        config.setConnectRetryDelay(retryDelay);
        return config;
    }

    /**
     * 路径①：stop 后已调度的重连任务不得复活连接。
     * 外部注入 workerGroup（stop 不 shutdown 该组）——真实复活窗口：stop 后事件循环
     * 仍在运行，已调度的重连任务照常触发，必须靠世代守卫放弃。
     */
    @Test
    public void testScheduledReconnectAbandonsAfterStop() throws Exception {
        // 关闭端口：connect 必败，重连循环持续运行
        int deadPort = NetHelper.findAvailableTcpPort();

        NioEventLoopGroup externalGroup = new NioEventLoopGroup(1);
        CountingClient client = new CountingClient();
        client.setConfig(config("localhost", deadPort, true, 50));
        client.setWorkerGroup(externalGroup);
        client.setChannelInitializer(pipeline -> {
        });
        client.start();
        try {
            // 等重连循环转起来（至少一次重连调度）
            for (int i = 0; i < 100 && client.scheduleReconnectCalls.get() < 2; i++) {
                Thread.sleep(20);
            }
            assertTrue(client.scheduleReconnectCalls.get() >= 2, "reconnect loop must be running");

            client.stop();

            // stop 后：失败计数必须冻结（已调度重连触发时因世代失配放弃，不得发起新连接）
            int failAtStop = failCountOf(client);
            int connectAtStop = client.doConnectCalls.get();
            int scheduleAtStop = client.scheduleReconnectCalls.get();

            Thread.sleep(600);

            assertEquals(failAtStop, failCountOf(client),
                    "connectFailCount must freeze after stop (scheduled reconnect must abandon)");
            assertEquals(connectAtStop, client.doConnectCalls.get(),
                    "no doConnect may run after stop (generation mismatch abandons scheduled reconnect)");
            assertEquals(scheduleAtStop, client.scheduleReconnectCalls.get(),
                    "no new reconnect may be scheduled after stop");
        } finally {
            client.stop();
            externalGroup.shutdownGracefully(0, 100, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 路径②：stop 时在途的连接建立，其完成回调因世代失配必须放弃——
     * 不得递增失败计数、不得调度重连复活。外部注入 workerGroup（stop 不关组），
     * 超时失败回调在 stop 后照常触发，必须靠世代守卫放弃。
     */
    @Test
    public void testInFlightConnectCallbackAbandonsAfterStop() throws Exception {
        // TEST-NET 黑洞地址：连接建立挂起，由 CONNECT_TIMEOUT_MILLIS 保证最终失败回调
        NioEventLoopGroup externalGroup = new NioEventLoopGroup(1);
        CountingClient client = new CountingClient();
        NettyTcpClientConfig cfg = config("10.255.255.1", 81, true, 50);
        cfg.setConnectTimeout(1000);
        client.setConfig(cfg);
        client.setWorkerGroup(externalGroup);
        client.setChannelInitializer(pipeline -> {
        });

        client.start();
        // 连接在途
        assertTrue(client.doConnectCalls.get() >= 1, "connect must be in flight");

        client.stop();

        int failAtStop = failCountOf(client);
        int scheduleAtStop = client.scheduleReconnectCalls.get();

        // 等在途连接超时失败回调触发（timeout 1s + 余量）
        Thread.sleep(2500);

        assertEquals(failAtStop, failCountOf(client),
                "in-flight connect completion callback must be abandoned after stop (failCount frozen)");
        assertEquals(scheduleAtStop, client.scheduleReconnectCalls.get(),
                "in-flight connect failure must not schedule reconnect after stop");

        externalGroup.shutdownGracefully(0, 100, TimeUnit.MILLISECONDS);
    }

    /**
     * 回归面：stop 前 stop-世代守卫不得影响正常连接（行为不变性）。
     */
    @Test
    public void testNormalConnectUnaffectedByGuard() throws Exception {
        int port = NetHelper.findAvailableTcpPort();
        NettyTcpServer server = new NettyTcpServer();
        NettyTcpServerConfig serverConfig = new NettyTcpServerConfig();
        serverConfig.setHost("localhost");
        serverConfig.setPort(port);
        server.setConfig(serverConfig);
        server.setChannelInitializer(pipeline -> {
        });
        server.start();
        try {
            CountingClient client = new CountingClient();
            NettyTcpClientConfig cfg = config("localhost", port, true, 50);
            cfg.setConnectTimeout(1000);
            client.setConfig(cfg);
            client.setChannelInitializer(pipeline -> {
            });
            client.start();
            try {
                assertTrue(client.awaitConnected(5000), "client must connect normally (guard must not block live path)");
                assertTrue(client.getConnectFuture().isSuccess());
            } finally {
                client.stop();
            }
        } finally {
            server.stop();
        }
    }
}
