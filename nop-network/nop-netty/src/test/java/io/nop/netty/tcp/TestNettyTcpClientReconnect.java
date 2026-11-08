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

    static int failCountOf(NettyTcpClient client) {
        try {
            java.lang.reflect.Field field = NettyTcpClient.class.getDeclaredField("connectFailCount");
            field.setAccessible(true);
            return ((java.util.concurrent.atomic.AtomicInteger) field.get(client)).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
            // 条件等待重试次数超过上限（超时上限仅挂死保护）
            assertTrue(io.nop.api.core.util.FutureHelper.waitUntil(
                            () -> failCountOf(client) > 2, 10_000),
                    "retry must have been attempted");
            int failCount = failCountOf(client);

            // 残余具名裁定（plan 2514-WI8）：超过 maxConnectRetryCount 后停止重连的负向观察窗。
            // 重连判定在真实 Netty EventLoop 定时器上（eventLoop.schedule），
            // EventLoop 不暴露待执行任务注册表，「无定时器条目」无结构性证据可断言；
            // 判定不读 CoreMetrics，虚拟时钟不可达。停止逻辑的纯策略行为已由
            // testWithRetryDelaySetsRetryDelayField 的 retryDelay=-1 分支单独锁定。
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

            // 条件等待消息到达（超时上限仅挂死保护）
            assertTrue(io.nop.api.core.util.FutureHelper.waitUntil(
                            () -> !received.isEmpty(), 5000),
                    "oneway message must reach the server, received=" + received);
            // sendOneway 必须 flush，消息应当到达服务端
            assertTrue(received.contains("hello-oneway"),
                    "oneway message must reach the server, received=" + received);
        } finally {
            client.stop();
            server.stop();
        }
    }
}
