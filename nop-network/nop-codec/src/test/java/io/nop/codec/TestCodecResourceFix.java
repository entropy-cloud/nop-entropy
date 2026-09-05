package io.nop.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.nop.codec.compress.DeflateCompressCodec;
import io.nop.codec.compress.GZipCompressCodec;
import io.nop.codec.support.AbstractByteBufCodec;
import io.nop.codec.util.ByteBufHelper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCodecResourceFix {

    /**
     * 记录 encodeBuf 返回的 buffer，验证 encodeBytes 用完后释放引用计数
     */
    static class TrackingCodec extends AbstractByteBufCodec {
        final List<ByteBuf> allocated = new ArrayList<>();

        @Override
        public ByteBuf encodeBuf(ByteBuf data, ByteBufAllocator allocator) {
            ByteBuf buf = allocator.buffer();
            buf.writeBytes(data);
            allocated.add(buf);
            return buf;
        }

        @Override
        public ByteBuf decodeBuf(ByteBuf data, ByteBufAllocator allocator) {
            ByteBuf buf = allocator.buffer();
            buf.writeBytes(data);
            allocated.add(buf);
            return buf;
        }
    }

    @Test
    public void testEncodeBytesReleasesBuffer() {
        TrackingCodec codec = new TrackingCodec();
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] out = codec.encodeBytes(data);
        assertArrayEquals(data, out);
        // encodeBytes 返回 byte[] 拷贝后必须释放原 buffer（否则泄漏堆外内存）
        assertEquals(0, codec.allocated.get(0).refCnt(), "encode buf must be released");
    }

    @Test
    public void testDecodeBytesReleasesBuffer() {
        TrackingCodec codec = new TrackingCodec();
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] out = codec.decodeBytes(data);
        assertArrayEquals(data, out);
        assertEquals(0, codec.allocated.get(0).refCnt(), "decode buf must be released");
    }

    @Test
    public void testPacketCodecEncodeToBytesReleasesBuffer() {
        IPacketCodec<String> codec = new IPacketCodec<String>() {
            @Override
            public int determinePacketLength(ByteBuf buf) {
                return 0;
            }

            @Override
            public String decodeFromBuf(ByteBuf buf, Class<?> targetType) {
                return null;
            }

            @Override
            public void encodeToBuf(String message, ByteBuf buf) {
                buf.writeBytes(message.getBytes(StandardCharsets.UTF_8));
            }
        };

        // encodeToBytes 默认实现不应泄漏它分配的 buffer
        byte[] out = codec.encodeToBytes("abc");
        assertEquals("abc", new String(out, StandardCharsets.UTF_8));
    }

    @Test
    public void testGzipRoundTripAndRelease() {
        GZipCompressCodec codec = new GZipCompressCodec();
        byte[] data = "hello gzip 中文".getBytes(StandardCharsets.UTF_8);
        byte[] compressed = codec.encodeBytes(data);
        byte[] decompressed = codec.decodeBytes(compressed);
        assertArrayEquals(data, decompressed);
    }

    @Test
    public void testDeflateRoundTrip() {
        DeflateCompressCodec codec = new DeflateCompressCodec();
        byte[] data = "hello deflate".getBytes(StandardCharsets.UTF_8);
        byte[] compressed = codec.encodeBytes(data);
        byte[] decompressed = codec.decodeBytes(compressed);
        assertArrayEquals(data, decompressed);
    }

    @Test
    public void testWriteBufHonorsStartAndLength() throws IOException {
        ByteBuf buf = Unpooled.directBuffer(8);
        buf.writeBytes(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ByteBufHelper.writeBuf(os, buf, 2, 3);
        // 4 参数契约：从 start 开始写 length 字节（直连 buffer 不能整段写出）
        assertArrayEquals(new byte[]{3, 4, 5}, os.toByteArray());
        buf.release();
    }

    @Test
    public void testGzipDecodeCorruptDataThrows() {
        GZipCompressCodec codec = new GZipCompressCodec();
        byte[] garbage = "not-a-gzip-stream".getBytes(StandardCharsets.UTF_8);
        boolean threw = false;
        try {
            codec.decodeBytes(garbage);
        } catch (Exception e) {
            threw = true;
        }
        assertTrue(threw, "corrupt gzip data must throw");
    }
}
