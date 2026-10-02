package io.nop.record.codec;

import io.nop.record.codec._gen.FieldBinaryCodec_f4be;
import io.nop.record.codec._gen.FieldBinaryCodec_f8le;
import io.nop.record.codec._gen.FieldBinaryCodec_s1;
import io.nop.record.codec._gen.FieldBinaryCodec_s4be;
import io.nop.record.codec._gen.FieldBinaryCodec_s4le;
import io.nop.record.codec._gen.FieldBinaryCodec_s8be;
import io.nop.record.codec._gen.FieldBinaryCodec_u1;
import io.nop.record.codec._gen.FieldBinaryCodec_u2be;
import io.nop.record.codec._gen.FieldBinaryCodec_u2le;
import io.nop.record.reader.ByteBufferBinaryDataReader;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.writer.IBinaryDataWriter;
import io.nop.record.writer.StreamBinaryDataWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 定宽字类型二进制编解码 roundtrip 语义：encode→decode 恒等、类型字节宽度、大端/小端字节序布局、null 编码写零。
 */
public class TestBinaryWordTypeRoundtrip {

    static byte[] encode(IFieldBinaryCodec codec, Object value) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        codec.encode(writer(out), value, -1, null, null);
        return out.toByteArray();
    }

    static Object decode(IFieldBinaryCodec codec, byte[] bytes) throws Exception {
        return codec.decode(reader(bytes), null, -1, null, null);
    }

    static IBinaryDataReader reader(byte[] bytes) {
        return new ByteBufferBinaryDataReader(bytes);
    }

    static IBinaryDataWriter writer(ByteArrayOutputStream out) {
        return new StreamBinaryDataWriter(out);
    }

    // u1：宽度 1 字节，无符号 0..255；null 编码写 0；解码为 Short
    @Test
    public void testU1WidthAndBoundary() throws Exception {
        byte[] bytes = encode(FieldBinaryCodec_u1.INSTANCE, (short) 255);
        assertEquals(1, bytes.length);
        assertEquals((byte) 0xFF, bytes[0]);
        assertEquals(255, ((Number) decode(FieldBinaryCodec_u1.INSTANCE, bytes)).intValue());

        assertEquals(0, ((Number) decode(FieldBinaryCodec_u1.INSTANCE,
                encode(FieldBinaryCodec_u1.INSTANCE, null))).intValue());
    }

    // u2be：宽度 2 字节，大端布局，边界 65535
    @Test
    public void testU2beEndiannessAndBoundary() throws Exception {
        byte[] bytes = encode(FieldBinaryCodec_u2be.INSTANCE, 0x0102);
        assertArrayEquals(new byte[]{0x01, 0x02}, bytes);
        assertEquals(0x0102, decode(FieldBinaryCodec_u2be.INSTANCE, bytes));

        byte[] max = encode(FieldBinaryCodec_u2be.INSTANCE, 0xFFFF);
        assertEquals(2, max.length);
        assertEquals(0xFFFF, decode(FieldBinaryCodec_u2be.INSTANCE, max));
    }

    // u2le：同值的小端布局为反序
    @Test
    public void testU2leEndianness() throws Exception {
        byte[] bytes = encode(FieldBinaryCodec_u2le.INSTANCE, 0x0102);
        assertArrayEquals(new byte[]{0x02, 0x01}, bytes);
        assertEquals(0x0102, decode(FieldBinaryCodec_u2le.INSTANCE, bytes));
    }

    // s1：宽度 1 字节的有符号字节，解码为 Byte
    @Test
    public void testS1Boundary() throws Exception {
        byte[] bytes = encode(FieldBinaryCodec_s1.INSTANCE, (byte) -128);
        assertEquals(1, bytes.length);
        assertEquals(-128, ((Number) decode(FieldBinaryCodec_s1.INSTANCE, bytes)).intValue());
        assertEquals(127, ((Number) decode(FieldBinaryCodec_s1.INSTANCE,
                encode(FieldBinaryCodec_s1.INSTANCE, (byte) 127))).intValue());
    }

    // s4be：宽度 4 字节，大端布局，覆盖 Integer.MIN_VALUE / MAX_VALUE 边界
    @Test
    public void testS4beBoundariesAndLayout() throws Exception {
        byte[] min = encode(FieldBinaryCodec_s4be.INSTANCE, Integer.MIN_VALUE);
        assertArrayEquals(new byte[]{(byte) 0x80, 0x00, 0x00, 0x00}, min);
        assertEquals(Integer.MIN_VALUE, decode(FieldBinaryCodec_s4be.INSTANCE, min));

        byte[] max = encode(FieldBinaryCodec_s4be.INSTANCE, Integer.MAX_VALUE);
        assertArrayEquals(new byte[]{0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, max);
        assertEquals(Integer.MAX_VALUE, decode(FieldBinaryCodec_s4be.INSTANCE, max));

        // 布局：高位在前
        assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04},
                encode(FieldBinaryCodec_s4be.INSTANCE, 0x01020304));
    }

    // s4le：小端布局为反序
    @Test
    public void testS4leLayout() throws Exception {
        byte[] bytes = encode(FieldBinaryCodec_s4le.INSTANCE, 0x01020304);
        assertArrayEquals(new byte[]{0x04, 0x03, 0x02, 0x01}, bytes);
        assertEquals(0x01020304, decode(FieldBinaryCodec_s4le.INSTANCE, bytes));
    }

    // s8be：宽度 8 字节，覆盖 Long.MIN_VALUE / MAX_VALUE
    @Test
    public void testS8beBoundaries() throws Exception {
        assertEquals(8, encode(FieldBinaryCodec_s8be.INSTANCE, Long.MAX_VALUE).length);
        assertEquals(Long.MAX_VALUE,
                decode(FieldBinaryCodec_s8be.INSTANCE, encode(FieldBinaryCodec_s8be.INSTANCE, Long.MAX_VALUE)));
        assertEquals(Long.MIN_VALUE,
                decode(FieldBinaryCodec_s8be.INSTANCE, encode(FieldBinaryCodec_s8be.INSTANCE, Long.MIN_VALUE)));
        // null 编码写全零宽度
        byte[] nullBytes = encode(FieldBinaryCodec_s8be.INSTANCE, null);
        assertEquals(8, nullBytes.length);
        assertArrayEquals(new byte[8], nullBytes);
    }

    // 浮点：f4be/f8le 按 IEEE754 位模式精确 roundtrip
    @Test
    public void testFloatRoundtripExactBits() throws Exception {
        byte[] f4 = encode(FieldBinaryCodec_f4be.INSTANCE, 3.5f);
        assertEquals(4, f4.length);
        assertEquals(3.5f, decode(FieldBinaryCodec_f4be.INSTANCE, f4));

        byte[] f8 = encode(FieldBinaryCodec_f8le.INSTANCE, 3.14d);
        assertEquals(8, f8.length);
        assertEquals(3.14d, decode(FieldBinaryCodec_f8le.INSTANCE, f8));

        // 位模式精确性：-0.0f 大端编码即 floatToIntBits 的大端字节序
        byte[] negZero = encode(FieldBinaryCodec_f4be.INSTANCE, -0.0f);
        int bits = Float.floatToIntBits(-0.0f);
        assertEquals((byte) (bits >> 24), negZero[0]);
        assertEquals((byte) bits, negZero[3]);
    }
}
