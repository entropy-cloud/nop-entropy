package io.nop.netty.tcp;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.string.LineEncoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.logging.LogLevel;
import io.nop.netty.channel.INettyChannelInitializer;
import io.nop.netty.config.NettyTcpClientConfig;
import io.nop.netty.config.NettyTcpServerConfig;
import io.nop.netty.handlers.IRpcMessageAdapter;
import io.nop.netty.handlers.RpcMessageHandler;

public class TcpTestHelper {

    public static NettyTcpServer createMockServer(int port) {
        NettyTcpServer server = new NettyTcpServer();
        NettyTcpServerConfig config = new NettyTcpServerConfig();
        server.setConfig(config);
        config.setHost("localhost");
        config.setPort(port);
        server.setChannelInitializer(new INettyChannelInitializer() {
            @Override
            public void initChannel(ChannelPipeline pipeline) {
                pipeline.addLast("handler", new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object o) {
                        ctx.fireChannelRead(o);
                    }
                });
            }
        });
        server.start();
        return server;
    }

    public static NettyTcpClient createClient(int remotePort) {
        NettyTcpClient client = new NettyTcpClient();
        NettyTcpClientConfig config = new NettyTcpClientConfig();
        config.setAutoReconnect(false);
        config.setConnectTimeout(1000);
        config.setRemoteHost("localhost");
        config.setRemotePort(remotePort);
        config.setWorkerGroupSize(10);
        config.setLogLevel(LogLevel.DEBUG);
        client.setConfig(config);
        client.setChannelInitializer(pipeline -> {
            pipeline.addLast("encoder", new LineEncoder());
            pipeline.addLast("decoder", new StringDecoder());
            pipeline.addLast(new RpcMessageHandler(100, new IRpcMessageAdapter() {
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
}
