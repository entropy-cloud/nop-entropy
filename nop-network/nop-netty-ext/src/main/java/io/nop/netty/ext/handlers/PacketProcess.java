package io.nop.netty.ext.handlers;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;

public class PacketProcess {
    public static String VAR_PROCESS = "process";

    private final ChannelHandlerContext context;
    private Object msg;
    private boolean drop;

    public PacketProcess(ChannelHandlerContext context, Object msg) {
        this.context = context;
        this.msg = msg;
    }

    public ChannelHandlerContext getHandlerContext() {
        return context;
    }

    public ChannelFuture write(Object msg) {
        return context.write(msg);
    }

    public ChannelFuture writeAndFlush(Object msg) {
        return context.writeAndFlush(msg);
    }

    public void flush() {
        context.flush();
    }

    public void drop() {
        drop = true;
    }

    public boolean shouldDrop() {
        return drop;
    }

    public void replace(Object msg) {
        this.msg = msg;
    }

    public Object getMsg() {
        return msg;
    }
}
