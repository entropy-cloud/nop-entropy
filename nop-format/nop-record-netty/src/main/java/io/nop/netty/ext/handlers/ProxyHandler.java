package io.nop.netty.ext.handlers;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.nop.netty.tcp.NettyTcpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 将消息转发给server上的其他连接。sendToAnyChannel返回的响应future被写回当前channel，
 * 使中转调用方能够收到回程消息
 */
public class ProxyHandler extends ChannelDuplexHandler {
    static final Logger LOG = LoggerFactory.getLogger(ProxyHandler.class);

    private final NettyTcpServer server;
    private final int rpcTimeout;

    public ProxyHandler(NettyTcpServer server, int rpcTimeout) {
        this.server = server;
        this.rpcTimeout = rpcTimeout;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        server.sendToAnyChannel(msg, rpcTimeout).whenComplete((resp, err) -> {
            if (err != null) {
                LOG.error("nop.netty.proxy-forward-fail", err);
            } else if (resp != null) {
                ctx.writeAndFlush(resp);
            }
        });
    }
}
