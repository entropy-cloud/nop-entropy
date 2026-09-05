package io.nop.netty.tcp;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.LineEncoder;
import io.netty.handler.codec.string.StringDecoder;
import io.nop.commons.util.NetHelper;
import io.nop.commons.util.retry.RetryPolicy;
import io.nop.netty.channel.INettyChannelInitializer;
import io.nop.netty.config.NettyTcpClientConfig;
import io.nop.netty.config.NettyTcpServerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
public class TestNettyTcpClientReconnect {

    @Test
    public void testWithRetryDelaySetsRetryDelayField() {
        RetryPolicy<Object> policy = RetryPolicy.createRetryPolicy()
                .withMaxRetryCount(10)
                .withMaxRetryDelay(3000)
                .withRetryDelay(100);

        // withRetryDelay(100) 不应覆盖 maxRetryDelay(3000)，retryDelay 应为 100 而非默认值
        assertEquals(100, policy.getRetryDelay());
        assertEquals(3000, policy.getMaxRetryDelay());

        // 超过最大重试次数时返回 -1 表示不再重试
        assertEquals(-1, policy.getRetryDelay(new RuntimeException(), 11, null));
    }

    static int failCountOf(NettyTcpClient client) throws Exception {
        java.lang.reflect.Field field = NettyTcpClient.class.getDeclaredField("connectFailCount");
        field.setAccessible(true);
        return ((java.util.concurrent.atomic.AtomicInteger) field.get(client)).get();
    }

    @Test
    public void testReconnectStopsAfterMaxRetryCount() throws Exception {
        int port = NetHelper.findAvailableTcpPort();

        NettyTcpClient client = new NettyTcpClient();
        NettyTcpClientConfig config = new NettyTcpClientConfig();
        config.setAutoReconnect(true);
        config.setConnectTimeout(500);
        config.setRemoteHost("localhost");
        config.setRemotePort(port);
        config.setMaxConnectRetryCount(2);
        config.setConnectRetryDelay(50);
        client.setConfig(config);
        client.setChannelInitializer(pipeline -> {
        });
        client.start();

        try {
            // 等待重试次数超过上限
            for (int i = 0; i < 300 && failCountOf(client) <= 2; i++) {
                Thread.sleep(20);
            }
            assertTrue(failCountOf(client) > 2, "retry must have been attempted");
            int failCount = failCountOf(client);

            // 超过 maxConnectRetryCount 后必须停止重连，失败计数不再增长
            Thread.sleep(500);
            assertEquals(failCount, failCountOf(client),
                    "reconnect attempts must stop after max retry count is exceeded");
        } finally {
            client.stop();
        }
    }

    @Test
    public void testSendOnewayFlushesMessage() throws Exception {
        int port = NetHelper.findAvailableTcpPort();
        List<String> received = new CopyOnWriteArrayList<>();

        NettyTcpServer server = new NettyTcpServer();
        NettyTcpServerConfig serverConfig = new NettyTcpServerConfig();
        serverConfig.setHost("localhost");
        serverConfig.setPort(port);
        server.setConfig(serverConfig);
        server.setChannelInitializer(new INettyChannelInitializer() {
            @Override
            public void initChannel(ChannelPipeline pipeline) {
                pipeline.addLast("framer", new LineBasedFrameDecoder(4096));
                pipeline.addLast("decoder", new StringDecoder());
                pipeline.addLast("collector", new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        received.add(msg.toString());
                    }
                });
            }
        });
        server.start();

        NettyTcpClient client = new NettyTcpClient();
        NettyTcpClientConfig config = new NettyTcpClientConfig();
        config.setAutoReconnect(false);
        config.setConnectTimeout(1000);
        config.setRemoteHost("localhost");
        config.setRemotePort(port);
        client.setConfig(config);
        client.setChannelInitializer(pipeline -> pipeline.addLast("encoder", new LineEncoder()));
        client.start();

        try {
            assertTrue(client.awaitConnected(5000));
            client.sendOneway("hello-oneway");

            for (int i = 0; i < 100 && received.isEmpty(); i++) {
                Thread.sleep(20);
            }
            // sendOneway 必须 flush，消息应当到达服务端
            assertTrue(received.contains("hello-oneway"),
                    "oneway message must reach the server, received=" + received);
        } finally {
            client.stop();
            server.stop();
        }
    }
}
