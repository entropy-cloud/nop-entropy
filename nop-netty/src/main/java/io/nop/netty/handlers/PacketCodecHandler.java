package io.nop.netty.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.TooLongFrameException;
import io.nop.api.core.util.Guard;
import io.nop.codec.IPacketCodec;
import io.nop.commons.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class PacketCodecHandler<T> extends ByteToMessageCodec<T> {
    static final Logger LOG = LoggerFactory.getLogger(PacketCodecHandler.class);

    private final IPacketCodec<T> codec;
    private final int maxFrameLength;
    private final int maxDecodeErrorCount;
    private final int maxEncodeErrorCount;

    private int decodeErrorCount;
    private int encodeErrorCount;

    public PacketCodecHandler(IPacketCodec<T> codec, int maxFrameLength, int maxDecodeErrorCount, int maxEncodeErrorCount) {
        Guard.notNull(codec, "codec");
        this.codec = codec;
        this.maxFrameLength = maxFrameLength;
        this.maxEncodeErrorCount = maxEncodeErrorCount;
        this.maxDecodeErrorCount = maxDecodeErrorCount;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        // 保留一个后门。如果是直接发送byte数组，则直接写出，跳过codec编码
        if (msg instanceof byte[]) {
            out.writeBytes((byte[]) msg);
        } else {
            out.markWriterIndex();
            try {
                codec.encodeToBuf((T) msg, out);
                // 如果成功则重置错误数为0
                encodeErrorCount = 0;
            } catch (RuntimeException e) {
                LOG.error("nop.netty.encode-msg-fail", e);

                encodeErrorCount++;

                if (encodeErrorCount > maxEncodeErrorCount)
                    throw e;

                LOG.warn("nop.netty.warn.ignore-encode-msg-fail:errorCount={}", encodeErrorCount);
                out.resetWriterIndex();
            }
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int len = codec.determinePacketLength(in);
        if (len == IPacketCodec.UNDETERMINED_LENGTH) {
            LOG.trace("nop.netty.packet-length-not-determined");
            return;
        }

        if (len < 0) {
            LOG.info("nop.netty.receive-corrupted-frame");
            throw new CorruptedFrameException("nop.err.netty.corrupted-frame");
        }

        if (len > maxFrameLength) {
            LOG.info("nop.netty.frame-len-exceed-limit:len={},max={}", len, maxFrameLength);
            throw new TooLongFrameException("nop.err.netty.too-long-frame");
        }

        if (in.readableBytes() < len) {
            LOG.trace("nop.netty.skip-when-msg-incomplete");
            return;
        }

        // 与LengthFieldBasedFrameDecoder不同，这里不使用retainedSlice，也就不需要release返回的frame
        ByteBuf frame = in.readSlice(len);
        try {
            Object result = codec.decodeFromBuf(frame);

            if (result != null) {
                decodeErrorCount = 0;
                out.add(result);
            }
        } catch (RuntimeException e) {
            LOG.error("nop.netty.decode-msg-fail:frame={}", StringHelper.bytesToHex(ByteBufUtil.getBytes(frame)), e);

            decodeErrorCount++;

            if (decodeErrorCount > maxDecodeErrorCount)
                throw e;

            LOG.warn("nop.netty.warn.ignore-decode-msg-fail:errorCount={}", decodeErrorCount);
        }
    }
}