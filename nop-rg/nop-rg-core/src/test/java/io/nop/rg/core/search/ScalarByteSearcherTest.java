package io.nop.rg.core.search;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScalarByteSearcherTest {
    private static final ScalarByteSearcher SEARCHER = ScalarByteSearcher.INSTANCE;

    private MemorySegment seg(byte[] data) {
        // 每个 arena 只在本方法内使用，数据生命周期覆盖调用期即可（测试数据无资源释放风险）
        return Arena.global().allocateFrom(ValueLayout.JAVA_BYTE, data);
    }

    private long find(byte[] data, byte[] pattern) {
        return SEARCHER.findPattern(seg(data), 0, data.length, pattern);
    }

    @Test
    public void testEmptyPatternRejected() {
        assertThrows(IllegalArgumentException.class, () -> find("abc".getBytes(), new byte[0]));
        // 空模式拒绝契约在生产消费面（PreparedLiteral）同样成立
        assertThrows(IllegalArgumentException.class, () -> PreparedLiteral.compile(new byte[0], false));
    }

    @Test
    public void testSingleBytePattern() {
        byte[] data = "hello".getBytes();
        assertEquals(1, find(data, "e".getBytes()));
        assertEquals(4, find(data, "o".getBytes()));
        assertEquals(-1, find(data, "z".getBytes()));
    }

    @Test
    public void testMultiBytePattern() {
        byte[] data = "hello world, hello nop".getBytes();
        assertEquals(6, find(data, "world".getBytes()));
        assertEquals(0, find(data, "hello".getBytes()));
        // 子区间跳过第一处
        assertEquals(13, SEARCHER.findPattern(seg(data), 7, data.length, "hello".getBytes()));
        assertEquals(-1, find(data, "xyz".getBytes()));
    }

    @Test
    public void testPatternAtBoundaries() {
        byte[] data = "abcabc".getBytes();
        assertEquals(0, find(data, "abc".getBytes()));
        assertEquals(2, find(data, "cab".getBytes()));
        // 末尾边界：模式恰好结束于数据末尾
        assertEquals(3, SEARCHER.findPattern(seg(data), 1, data.length, "abc".getBytes()));
        assertEquals(-1, find(data, "bcaX".getBytes()));
    }

    @Test
    public void testPatternLongerThanData() {
        assertEquals(-1, find("ab".getBytes(), "abcdef".getBytes()));
        // 恰好等长
        assertEquals(0, find("abcdef".getBytes(), "abcdef".getBytes()));
    }

    @Test
    public void testOverlappingMatchesFoundProgressively() {
        byte[] data = "aaaaa".getBytes();
        byte[] pattern = "aaa".getBytes();
        long first = SEARCHER.findPattern(seg(data), 0, data.length, pattern);
        assertEquals(0, first);
        // 从首个命中 +1 继续搜索可发现重叠命中（消费方循环推进的契约）
        MemorySegment segment = seg(data);
        assertEquals(1, SEARCHER.findPattern(segment, first + 1, data.length, pattern));
        assertEquals(2, SEARCHER.findPattern(segment, 2, data.length, pattern));
    }

    @Test
    public void testOffsetAndLimitSubrange() {
        byte[] data = "xneedle-xneedle-x".getBytes();
        MemorySegment segment = seg(data);
        byte[] pattern = "needle".getBytes();
        // 全范围
        assertEquals(1, SEARCHER.findPattern(segment, 0, data.length, pattern));
        // 子区间跳过第一处
        assertEquals(9, SEARCHER.findPattern(segment, 2, data.length, pattern));
        // limit 裁剪掉第二处：窗口需完整落在 [offset, limit) 内
        assertEquals(-1, SEARCHER.findPattern(segment, 2, 9 + 3, pattern));
        assertEquals(9, SEARCHER.findPattern(segment, 2, 9 + 6, pattern));
        // offset 越过一切命中
        assertEquals(-1, SEARCHER.findPattern(segment, 10, data.length, pattern));
    }

    @Test
    public void testBinaryDataWithHighBytes() {
        byte[] data = {(byte) 0xFF, 0x00, (byte) 0x80, 0x00, (byte) 0xFF, 0x7F};
        assertEquals(2, find(data, new byte[]{(byte) 0x80, 0x00}));
        assertEquals(4, find(data, new byte[]{(byte) 0xFF, 0x7F}));
        assertEquals(-1, find(data, new byte[]{0x7F, 0x00}));
    }

    @Test
    public void testFindFirstByte() {
        byte[] data = "aXbXc".getBytes();
        MemorySegment segment = seg(data);
        assertEquals(1, SEARCHER.findFirstByte(segment, 0, data.length, (byte) 'X'));
        assertEquals(3, SEARCHER.findFirstByte(segment, 2, data.length, (byte) 'X'));
        assertEquals(-1, SEARCHER.findFirstByte(segment, 4, data.length, (byte) 'X'));
        assertEquals(-1, SEARCHER.findFirstByte(segment, 0, 0, (byte) 'a'));
    }

    @Test
    public void testAnchorSelectionPrefersRarestByte() {
        // 空格频率 100，'q' 默认频率 1：锚点应落在 'q' 的最后一次出现（下标 2）
        assertEquals(2, ScalarByteSearcher.selectAnchorIndex("a q".getBytes()));
        // 全同字节：锚点为最后一次出现
        assertEquals(2, ScalarByteSearcher.selectAnchorIndex("aaa".getBytes()));
    }

    @Test
    public void testSkipTable() {
        // 模式 "abcab"，锚点 'c'（频率 1，唯一）位于下标 2
        long[] skip = ScalarByteSearcher.buildSkipTable("abcab".getBytes(), 2);
        // 'a' 在锚点前最后出现于 0 → 跳 2；'b' 出现于 1 → 跳 1；'c'/'x' 不在锚点前 → 跳 3
        assertEquals(2, skip['a' & 0xFF]);
        assertEquals(1, skip['b' & 0xFF]);
        assertEquals(3, skip['c' & 0xFF]);
        assertEquals(3, skip['x' & 0xFF]);
    }

    @Test
    public void testFuzzAgainstStringIndexOf() {
        // 确定性伪随机，与 String.indexOf 交叉验证 findPattern 在任意 offset/limit 下的正确性
        java.util.Random random = new java.util.Random(42);
        char[] alphabet = {'a', 'b', 'c'};
        for (int iter = 0; iter < 500; iter++) {
            int dataLen = 1 + random.nextInt(40);
            byte[] data = new byte[dataLen];
            for (int i = 0; i < dataLen; i++) {
                data[i] = (byte) alphabet[random.nextInt(alphabet.length)];
            }
            int patternLen = 1 + random.nextInt(5);
            byte[] pattern = new byte[patternLen];
            for (int i = 0; i < patternLen; i++) {
                pattern[i] = (byte) alphabet[random.nextInt(alphabet.length)];
            }
            int offset = random.nextInt(dataLen + 1);
            int limit = offset + random.nextInt(dataLen - offset + 1);

            long expected = indexOfInRange(data, offset, limit, pattern);
            long actual = SEARCHER.findPattern(seg(data), offset, limit, pattern);
            assertEquals(expected, actual, () -> "data=" + new String(data) + " pattern=" + new String(pattern)
                    + " offset=" + offset + " limit=" + limit);
        }
    }

    private long indexOfInRange(byte[] data, int offset, int limit, byte[] pattern) {
        if (pattern.length == 0 || limit - offset < pattern.length) {
            return pattern.length == 0 ? offset : -1;
        }
        for (int s = offset; s <= limit - pattern.length; s++) {
            boolean ok = true;
            for (int i = 0; i < pattern.length; i++) {
                if (data[s + i] != pattern[i]) {
                    ok = false;
                    break;
                }
            }
            if (ok) return s;
        }
        return -1;
    }
}
