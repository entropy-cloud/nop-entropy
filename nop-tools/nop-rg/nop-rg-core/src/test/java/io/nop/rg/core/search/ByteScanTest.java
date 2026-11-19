package io.nop.rg.core.search;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Wave 5 item 16（plan 2574 Phase A）：SWAR 字长扫描与逐字节扫描语义一致性。
 */
public class ByteScanTest {

    private MemorySegment seg(byte[] data) {
        return Arena.global().allocateFrom(ValueLayout.JAVA_BYTE, data);
    }

    private long scalar(byte[] data, int offset, int limit, byte target) {
        for (int i = offset; i < limit; i++) {
            if (data[i] == target) {
                return i;
            }
        }
        return -1;
    }

    @Test
    public void testEmptyRange() {
        byte[] data = "abc".getBytes();
        assertEquals(-1, ByteScan.findFirstByte(seg(data), 0, 0, (byte) 'a'));
        assertEquals(-1, ByteScan.findFirstByte(seg(data), 2, 2, (byte) 'c'));
        assertEquals(-1, ByteScan.findFirstByte(seg(data), 3, 2, (byte) 'a'));
    }

    @Test
    public void testShorterThanWord() {
        byte[] data = {1, 2, 3};
        assertEquals(1, ByteScan.findFirstByte(seg(data), 0, 3, (byte) 2));
        assertEquals(-1, ByteScan.findFirstByte(seg(data), 0, 3, (byte) 9));
    }

    @Test
    public void testHitInEachLane() {
        // 16 字节（两字长），target 'Z' 依次落在 lane 0..7 与第二字长的 lane 0..7
        for (int lane = 0; lane < 16; lane++) {
            byte[] data = new byte[16];
            data[lane] = 'Z';
            assertEquals(lane, ByteScan.findFirstByte(seg(data), 0, 16, (byte) 'Z'),
                    "lane " + lane);
        }
    }

    @Test
    public void testUnalignedOffsets() {
        // 从非 8 对齐 offset 起扫，命中落在头部标量段、中段字长、尾部标量段
        byte[] data = new byte[41];
        data[0] = 'Q';
        data[7] = 'Q';
        data[8] = 'Q';
        data[9] = 'Q';
        data[20] = 'Q';
        data[33] = 'Q';
        data[40] = 'Q';
        MemorySegment segment = seg(data);
        assertEquals(0, ByteScan.findFirstByte(segment, 0, 41, (byte) 'Q'));
        assertEquals(7, ByteScan.findFirstByte(segment, 1, 41, (byte) 'Q'));
        assertEquals(20, ByteScan.findFirstByte(segment, 10, 41, (byte) 'Q'));
        assertEquals(33, ByteScan.findFirstByte(segment, 21, 41, (byte) 'Q'));
        assertEquals(40, ByteScan.findFirstByte(segment, 34, 41, (byte) 'Q'));
        assertEquals(-1, ByteScan.findFirstByte(segment, 34, 40, (byte) 'Q'));
    }

    @Test
    public void testHighBytes() {
        // 0x80-0xFF 负值字节走 XOR 掩码路径
        byte[] data = {0x01, (byte) 0x80, (byte) 0xFF, 0x02, (byte) 0x80};
        MemorySegment segment = seg(data);
        assertEquals(1, ByteScan.findFirstByte(segment, 0, 5, (byte) 0x80));
        assertEquals(2, ByteScan.findFirstByte(segment, 0, 5, (byte) 0xFF));
        assertEquals(4, ByteScan.findFirstByte(segment, 3, 5, (byte) 0x80));
        assertEquals(-1, ByteScan.findFirstByte(segment, 0, 5, (byte) 0x7E));
    }

    @Test
    public void testFirstAndLastByteHit() {
        byte[] data = "X------------X".getBytes();
        // 全范围：首命中
        assertEquals(0, ByteScan.findFirstByte(seg(data), 0, data.length, (byte) 'X'));
        // 跳过首命中后：尾命中
        assertEquals(data.length - 1, ByteScan.findFirstByte(seg(data), 1, data.length, (byte) 'X'));
    }

    @Test
    public void testFuzzAgainstScalar() {
        // 确定性伪随机：与逐字节扫描对拍（plan 2574 Phase A）
        Random random = new Random(4242);
        for (int iter = 0; iter < 1000; iter++) {
            int len = random.nextInt(64);
            byte[] data = new byte[len];
            random.nextBytes(data);
            byte target = (byte) random.nextInt(256);
            int offset = len == 0 ? 0 : random.nextInt(len + 1);
            int limit = offset + (len == 0 ? 0 : random.nextInt(len - offset + 1));

            long expected = scalar(data, offset, limit, target);
            long actual = ByteScan.findFirstByte(seg(data), offset, limit, target);
            assertEquals(expected, actual,
                    () -> "len=" + len + " target=" + target + " offset=" + offset + " limit=" + limit);
        }
    }
}
