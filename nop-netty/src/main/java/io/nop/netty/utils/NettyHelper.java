package io.nop.netty.utils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.nop.netty.config.NettyBaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.util.concurrent.CompletableFuture;

public class NettyHelper {

    static final Logger LOG = LoggerFactory.getLogger(NettyHelper.class);

    public static EventLoopGroup newEventGroup(NettyBaseConfig config) {
        return newEventGroup(config.getWorkerGroupSize(), config.isUseEpoll(), config.getIoRatio());
    }

    public static EventLoopGroup newEventGroup(int groupSize, boolean useEpoll, int ioRatio) {

        if (useEpoll) {
            try {
                EpollEventLoopGroup epollGroup = new EpollEventLoopGroup(groupSize);
                if (ioRatio > 0)
                    epollGroup.setIoRatio(ioRatio);
                return epollGroup;
            } catch (Throwable e) {
                LOG.error("nop.netty.create-epoll-group-fail", e);
            }
        }

        NioEventLoopGroup eventGroup = new NioEventLoopGroup(groupSize);

        if (ioRatio > 0)
            eventGroup.setIoRatio(ioRatio);

        return eventGroup;
    }

    public static void writeFully(GatheringByteChannel channel, ByteBuf byteBuf) throws IOException {
        if (byteBuf != null) {
            int length = byteBuf.readableBytes();
            int written = 0;
            if (byteBuf.nioBufferCount() == 1) {
                ByteBuffer byteBuffer = byteBuf.nioBuffer();
                while (written < length) {
                    written += channel.write(byteBuffer);
                }
            } else {
                ByteBuffer[] byteBuffers = byteBuf.nioBuffers();
                while (written < length) {
                    written += (int) channel.write(byteBuffers);
                }
            }
        }
    }

    public static void safeClose(Channel channel) {
        try {
            channel.close();
        } catch (Exception e) {
            LOG.error("nop.netty.close-channel-fail", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> CompletableFuture<T> toCompletableFuture(ChannelFuture future) {
        CompletableFuture<T> ret = new CompletableFuture<T>();
        future.addListener(future1 -> {
            if (future1.isSuccess()) {
                ret.complete((T) future1.getNow());
            } else {
                ret.completeExceptionally(future1.cause());
            }
        });
        return ret;
    }
}
