package io.nop.netty.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import io.nop.codec.IPacketCodec;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 4.3-C3-023（plan 2561 裁定 F）钉：PacketCodecHandler codec fail-loud。
 * <ul>
 * <li>encode 侧：codec 异常直接上抛 → MessageToByteEncoder fail 本次 write promise（不自动关 channel）。
 * 变异红形态：恢复 resetWriterIndex+WARN 吞噬分支 → 首次失败被吞、write promise 成功 → 红。</li>
 * <li>decode 侧：WARN 吞噬现状保留，真实增量 = 结构化丢帧计数器 getter（单调累计，成功不清零）。</li>
 * </ul>
 */
public class TestPacketCodecHandler {

    static final int FRAME_LEN = 4;

    /** 定长 4 字节帧的假 codec：首字节 0xFF 的帧解码抛错；encode 可配置抛错。 */
    static class FakeCodec implements IPacketCodec<Object> {
        boolean failEncode;

        @Override
        public int determinePacketLength(ByteBuf buf) {
            return buf.readableBytes() >= FRAME_LEN ? FRAME_LEN : UNDETERMINED_LENGTH;
        }

        @Override
        public Object decodeFromBuf(ByteBuf buf, Class<?> targetType) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            if ((bytes[0] & 0xFF) == 0xFF)
                throw new IllegalStateException("decode-failed-for-test");
            return new String(bytes, StandardCharsets.US_ASCII);
        }

        @Override
        public void encodeToBuf(Object message, ByteBuf buf) {
            // 先写入部分字节再失败：模拟半帧（原吞噬分支靠 resetWriterIndex 回滚后静默成功）
            buf.writeBytes("ab".getBytes(StandardCharsets.US_ASCII));
            if (failEncode)
                throw new IllegalStateException("encode-failed-for-test");
            buf.writeBytes("cd".getBytes(StandardCharsets.US_ASCII));
        }
    }

    private static Throwable rootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null)
            cause = cause.getCause();
        return cause;
    }

    @Test
    public void encodeExceptionFailsWritePromiseAndKeepsChannelOpen() {
        FakeCodec codec = new FakeCodec();
        codec.failEncode = true;
        // maxEncodeErrorCount=10：缺陷态下前 10 次 encode 失败均被吞噬为「成功」
        EmbeddedChannel channel = new EmbeddedChannel(new PacketCodecHandler(codec, 1024, 10, 10));
        try {
            ChannelFuture future = channel.writeAndFlush("msg");

            assertTrue(future.isDone());
            assertFalse(future.isSuccess(),
                    "C3-023: encode 异常必须 fail 本次 write promise（缺陷态吞噬后 promise 成功）");
            assertNotNull(future.cause());
            assertEquals("encode-failed-for-test", rootCause(future.cause()).getMessage());
            assertNull(channel.readOutbound(), "失败的 encode 不得向下游写出任何（半）帧");
            assertTrue(channel.isOpen(), "encode 失败不自动关闭 channel（由调用方/exceptionCaught 观察）");
        } finally {
            channel.close();
        }
    }

    @Test
    public void encodeSuccessAndByteArrayBackdoorStillWrite() {
        FakeCodec codec = new FakeCodec();
        EmbeddedChannel channel = new EmbeddedChannel(new PacketCodecHandler(codec, 1024, 10, 10));
        try {
            assertTrue(channel.writeAndFlush("msg").isSuccess());
            ByteBuf out = channel.readOutbound();
            assertEquals("abcd", out.toString(StandardCharsets.US_ASCII));
            out.release();

            assertTrue(channel.writeAndFlush("raw!".getBytes(StandardCharsets.US_ASCII)).isSuccess());
            ByteBuf raw = channel.readOutbound();
            assertEquals("raw!", raw.toString(StandardCharsets.US_ASCII));
            raw.release();
        } finally {
            channel.close();
        }
    }

    @Test
    public void decodeFailureIsSwallowedAndCountedMonotonically() {
        FakeCodec codec = new FakeCodec();
        PacketCodecHandler handler = new PacketCodecHandler(codec, 1024, 10, 10);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        try {
            assertEquals(0, handler.getDroppedFrameCount());

            // 坏帧：WARN 吞噬（不抛、无消息产出），丢帧计数 +1
            assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{(byte) 0xFF, 1, 2, 3})));
            assertNull(channel.readInbound());
            assertEquals(1, handler.getDroppedFrameCount(), "C3-023: decode 丢帧必须计入结构化计数器");

            // 好帧：正常产出；丢帧计数为单调累计，成功不清零（区别于滑动的 decodeErrorCount）
            assertTrue(channel.writeInbound(Unpooled.wrappedBuffer("good".getBytes(StandardCharsets.US_ASCII))));
            assertEquals("good", channel.readInbound());
            assertEquals(1, handler.getDroppedFrameCount(), "丢帧计数为单调累计，成功解码不清零");

            assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{(byte) 0xFF, 9, 9, 9})));
            assertEquals(2, handler.getDroppedFrameCount());
        } finally {
            channel.close();
        }
    }
}
