package io.nop.netty.handlers;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.nop.netty.tcp.TcpChannelInfo;

public class MessageCounterHandler extends ChannelDuplexHandler {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        TcpChannelInfo info = ctx.channel().attr(TcpChannelInfo.ATTR_KEY).get();
        if (info != null) {
            info.setRecvCount(info.getRecvCount() + 1);
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        TcpChannelInfo info = ctx.channel().attr(TcpChannelInfo.ATTR_KEY).get();
        if (info != null) {
            info.setSendCount(info.getSendCount() + 1);
        }
        super.write(ctx, msg, promise);
    }
}
