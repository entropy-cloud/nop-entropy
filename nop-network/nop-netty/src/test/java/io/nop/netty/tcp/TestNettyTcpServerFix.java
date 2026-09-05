package io.nop.netty.tcp;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.LineEncoder;
import io.netty.handler.codec.string.StringDecoder;
import io.nop.commons.util.NetHelper;
import io.nop.netty.channel.INettyChannelInitializer;
import io.nop.netty.config.NettyTcpClientConfig;
import io.nop.netty.config.NettyTcpServerConfig;
import io.nop.netty.config.TrafficShapingConfig;
import io.nop.netty.handlers.RpcMessageHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
public class TestNettyTcpServerFix {

    static NettyTcpServer startEchoServer(int port) {
        NettyTcpServer server = new NettyTcpServer();
        NettyTcpServerConfig config = new NettyTcpServerConfig();
        config.setHost("localhost");
        config.setPort(port);
        server.setConfig(config);
        server.setChannelInitializer(new INettyChannelInitializer() {
            @Override
            public void initChannel(ChannelPipeline pipeline) {
                pipeline.addLast("framer", new LineBasedFrameDecoder(4096));
                pipeline.addLast("decoder", new StringDecoder());
                pipeline.addLast("encoder", new LineEncoder());
                pipeline.addLast("echo", new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        ctx.writeAndFlush(msg);
                    }
                });
            }
        });
        server.start();
        return server;
    }

    static NettyTcpClient connectClient(int port) {
        NettyTcpClient client = new NettyTcpClient();
        NettyTcpClientConfig config = new NettyTcpClientConfig();
        config.setAutoReconnect(false);
        config.setConnectTimeout(2000);
        config.setRemoteHost("localhost");
        config.setRemotePort(port);
        client.setConfig(config);
        client.setChannelInitializer(pipeline -> {
            pipeline.addLast("encoder", new LineEncoder());
            pipeline.addLast("framer", new LineBasedFrameDecoder(4096));
                pipeline.addLast("decoder", new StringDecoder());
            pipeline.addLast(new RpcMessageHandler(100, new io.nop.netty.handlers.IRpcMessageAdapter() {
                @Override
                public Object getRequestId(Object request) {
                    return request;
                }

                @Override
                public Object getResponseId(Object response) {
                    return response;
                }
            }));
        });
        client.start();
        return client;
    }

    @Test
    public void testGlobalTrafficShapingDoesNotKillConnections() throws Exception {
        int port = NetHelper.findAvailableTcpPort();
        NettyTcpServer server = new NettyTcpServer();
        NettyTcpServerConfig config = new NettyTcpServerConfig();
        config.setHost("localhost");
        config.setPort(port);
        TrafficShapingConfig shapingConfig = new TrafficShapingConfig();
        shapingConfig.setWriteLimit(1024 * 1024);
        shapingConfig.setReadLimit(1024 * 1024);
        config.setGlobalTrafficShapingConfig(shapingConfig);
        server.setConfig(config);
        server.setChannelInitializer(new INettyChannelInitializer() {
            @Override
            public void initChannel(ChannelPipeline pipeline) {
                pipeline.addLast("framer", new LineBasedFrameDecoder(4096));
                pipeline.addLast("decoder", new StringDecoder());
                pipeline.addLast("encoder", new LineEncoder());
                pipeline.addLast("echo", new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        ctx.writeAndFlush(msg);
                    }
                });
            }
        });
        server.start();

        NettyTcpClient client = connectClient(port);
        try {
            assertTrue(client.awaitConnected(5000));
            // 开启全局流量整形（内部 worker group）时连接不应在 initChannel 即死
            Object echo = io.nop.api.core.util.FutureHelper.syncGet(client.sendAsync("hello", 5000));
            assertEquals("hello", echo);
        } finally {
            client.stop();
            server.stop();
        }
    }

    @Test
    public void testRandomPortExposedAfterStart() {
        NettyTcpServer server = startEchoServer(0);
        try {
            // port<=0 表示随机端口，启动后必须能取到实际绑定端口
            assertTrue(server.getPort() > 0, "actual bound port must be exposed, got " + server.getPort());
        } finally {
            server.stop();
        }
    }

    @Test
    public void testStopClosesAcceptedConnections() throws Exception {
        int port = NetHelper.findAvailableTcpPort();
        // 外部注入 worker group：stop 时已接受的连接也必须被关闭
        EventLoopGroup externalGroup = new NioEventLoopGroup(1);

        NettyTcpServer server = new NettyTcpServer();
        NettyTcpServerConfig config = new NettyTcpServerConfig();
        config.setHost("localhost");
        config.setPort(port);
        server.setConfig(config);
        server.setWorkerGroup(externalGroup);
        server.setChannelInitializer(new INettyChannelInitializer() {
            @Override
            public void initChannel(ChannelPipeline pipeline) {
                pipeline.addLast("framer", new LineBasedFrameDecoder(4096));
                pipeline.addLast("decoder", new StringDecoder());
                pipeline.addLast("encoder", new LineEncoder());
                pipeline.addLast("echo", new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        ctx.writeAndFlush(msg);
                    }
                });
            }
        });
        server.start();

        NettyTcpClient client = connectClient(port);
        try {
            assertTrue(client.awaitConnected(5000));
            Object echo = io.nop.api.core.util.FutureHelper.syncGet(client.sendAsync("ping", 5000));
            assertEquals("ping", echo);

            server.stop();
            // 客户端 channel 应当被服务端 stop 关闭
            boolean closed = false;
            for (int i = 0; i < 250 && !closed; i++) {
                closed = !client.getConnectFuture().channel().isActive();
                if (!closed) {
                    Thread.sleep(20);
                }
            }
            assertTrue(closed, "accepted connections must be closed when server stops");
        } finally {
            client.stop();
            server.stop();
            externalGroup.shutdownGracefully();
        }
    }
}
