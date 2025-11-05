package io.nop.netty.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.nop.netty.tcp.TcpChannelInfo;

public class ConnectionInfoHandler extends ChannelDuplexHandler {
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        TcpChannelInfo info = new TcpChannelInfo();
        info.setChannelId(ctx.channel().id().asLongText());
        info.setConnectTime(System.currentTimeMillis());
        info.setLocalAddress(ctx.channel().localAddress().toString());
        info.setRemoteAddress(ctx.channel().remoteAddress().toString());
        ctx.channel().attr(TcpChannelInfo.ATTR_KEY).set(info);
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
        TcpChannelInfo info = ctx.channel().attr(TcpChannelInfo.ATTR_KEY).get();
        info.setRecvBytes(info.getRecvBytes() + buf.readableBytes());
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
        TcpChannelInfo info = ctx.channel().attr(TcpChannelInfo.ATTR_KEY).get();
        info.setSendBytes(info.getSendBytes() + buf.readableBytes());
        super.write(ctx, msg, promise);
    }
}