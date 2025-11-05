package io.nop.netty.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

/**
 * 为了测试拆包解包逻辑是否正确，将一个完整的数据包拆分成两个数据包发送，同时将接收到的一个包拆分成两个包读取
 */
@ChannelHandler.Sharable
public class TestFragmentChannelHandler extends ChannelDuplexHandler {
    public static TestFragmentChannelHandler INSTANCE = new TestFragmentChannelHandler();

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
        if (buf.readableBytes() > 2) {
            ByteBuf first = buf.readRetainedSlice(buf.readableBytes() / 2);
            super.write(ctx, first, ctx.voidPromise());
        }
        super.write(ctx, msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
        if (buf.readableBytes() > 2) {
            ByteBuf first = buf.readRetainedSlice(buf.readableBytes() / 2);
            super.channelRead(ctx, first);
        }

        super.channelRead(ctx, msg);
    }
}
