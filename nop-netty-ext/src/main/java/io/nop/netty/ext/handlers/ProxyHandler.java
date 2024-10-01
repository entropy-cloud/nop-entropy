package io.nop.netty.ext.handlers;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.nop.netty.tcp.NettyTcpServer;

public class ProxyHandler extends ChannelDuplexHandler {
    private final NettyTcpServer server;

    public ProxyHandler(NettyTcpServer server) {
        this.server = server;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        server.sendToAnyChannel(msg, 1000).whenComplete((v, e) -> {
            if (e != null) {
                ctx.close();
            } else {
                ctx.writeAndFlush(v);
            }
        });
    }
}
