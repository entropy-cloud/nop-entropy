package io.nop.netty.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

import java.util.Objects;

public class HeartbeatHandler extends ChannelInboundHandlerAdapter {
    private final byte[] pingMessage;
    private final String pongMessage;

    public HeartbeatHandler(byte[] pingMessage, String pongMessage) {
        this.pingMessage = pingMessage;
        this.pongMessage = pongMessage;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.WRITER_IDLE) {
                ctx.writeAndFlush(pingMessage);
            }
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (Objects.equals(pongMessage, msg))
            return;

        ctx.fireChannelRead(msg);
    }
}
