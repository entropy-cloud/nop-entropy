package io.nop.netty.ext.handlers;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.nop.netty.tcp.NettyTcpServer;

public class ProxyHandler extends ChannelDuplexHandler {
    private final NettyTcpServer server;
    private final int rpcTimeout;

    public ProxyHandler(NettyTcpServer server, int rpcTimeout) {
        this.server = server;
        this.rpcTimeout = rpcTimeout;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        server.sendToAnyChannel(msg, rpcTimeout);
    }
}
