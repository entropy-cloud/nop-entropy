package io.nop.record.reader;

import io.nop.api.core.exceptions.NopException;
import io.nop.record.RecordErrors;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 二进制读取辅助工具语义：XOR/循环移位/zlib 解压与字节数组无符号比较。
 */
public class TestBinaryReaderHelper {

    @Test
    public void testToByteArrayLengthBounds() {
        assertEquals(0, BinaryReaderHelper.toByteArrayLength(0));
        assertEquals(Integer.MAX_VALUE, BinaryReaderHelper.toByteArrayLength((long) Integer.MAX_VALUE));

        assertThrows(IllegalArgumentException.class, () -> BinaryReaderHelper.toByteArrayLength(-1));
        assertThrows(IllegalArgumentException.class,
                () -> BinaryReaderHelper.toByteArrayLength((long) Integer.MAX_VALUE + 1));
    }

    // XOR：单字节 key 与循环重复 key 数组两种模式
    @Test
    public void testProcessXor() {
        assertArrayEquals(new byte[]{0x00, 0x03, 0x02}, BinaryReaderHelper.processXor(new byte[]{1, 2, 3}, (byte) 1));
        // 自反：两次 XOR 还原
        byte[] data = new byte[]{0x0F, 0x1E, 0x2D};
        assertArrayEquals(data, BinaryReaderHelper.processXor(BinaryReaderHelper.processXor(data, (byte) 0x5A),
                (byte) 0x5A));

        // 多字节 key 循环使用：data 长于 key
        byte[] out = BinaryReaderHelper.processXor(new byte[]{0x01, 0x02, 0x03, 0x04}, new byte[]{0x01, 0x02});
        assertArrayEquals(new byte[]{0x00, 0x00, 0x02, 0x06}, out);
    }

    // 8 位组循环左移；非 1 字节组不支持
    @Test
    public void testProcessRotateLeft() {
        assertArrayEquals(new byte[]{0x02, 0x04}, BinaryReaderHelper.processRotateLeft(new byte[]{0x01, 0x02}, 1, 1));
        // 位移 8 位还原原值
        byte[] data = new byte[]{(byte) 0xAB};
        assertArrayEquals(data, BinaryReaderHelper.processRotateLeft(data, 8, 1));

        assertThrows(UnsupportedOperationException.class,
                () -> BinaryReaderHelper.processRotateLeft(new byte[]{1, 2}, 1, 2));
    }

    // zlib 解压 roundtrip；坏数据报 ERR_RECORD_ZLIB_DECODE_FAIL
    @Test
    public void testProcessZlibRoundtripAndCorruptInput() {
        byte[] plain = "hello zlib roundtrip".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        Deflater deflater = new Deflater();
        deflater.setInput(plain);
        deflater.finish();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[1024];
        while (!deflater.finished()) {
            int n = deflater.deflate(tmp);
            buf.write(tmp, 0, n);
        }
        deflater.end();

        assertArrayEquals(plain, BinaryReaderHelper.processZlib(buf.toByteArray()));

        // 非 zlib 数据解压失败
        NopException e = assertThrows(NopException.class,
                () -> BinaryReaderHelper.processZlib(new byte[]{1, 2, 3, 4}));
        assertTrue(e.getErrorCode().equals(RecordErrors.ERR_RECORD_ZLIB_DECODE_FAIL.getErrorCode()));
    }

    // mod 恒非负；除数必须为正；字节按无符号比较与最值扫描
    @Test
    public void testModAndUnsignedByteArrayOps() {
        assertEquals(2, BinaryReaderHelper.mod(-1, 3));
        assertEquals(0, BinaryReaderHelper.mod(6, 3));
        assertEquals(2L, BinaryReaderHelper.mod(-1L, 3L));
        assertThrows(ArithmeticException.class, () -> BinaryReaderHelper.mod(1, 0));

        // 无符号比较：0x90 > 0x10
        byte[] big = new byte[]{(byte) 0x90};
        byte[] small = new byte[]{0x10};
        assertTrue(BinaryReaderHelper.byteArrayCompare(big, small) > 0);
        assertEquals(0, BinaryReaderHelper.byteArrayCompare(small, small));
        // 前缀短者更小
        assertTrue(BinaryReaderHelper.byteArrayCompare(new byte[]{1}, new byte[]{1, 0}) < 0);

        assertEquals(0x10, BinaryReaderHelper.byteArrayMin(new byte[]{(byte) 0x90, 0x10}));
        assertEquals(0x90, BinaryReaderHelper.byteArrayMax(new byte[]{(byte) 0x90, 0x10}));
    }
}
