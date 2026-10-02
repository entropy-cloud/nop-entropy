package io.nop.batch.core.utils;

import io.nop.commons.util.objects.Pair;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RangeSplitUtils 分片边界语义：分片结果必须首尾等于原区间、严格递增、
 * 分片之间不重叠不遗漏（前闭后开铺满整个区间）；区间过小或重合时允许实际分片数收缩，但不允许越界。
 */
public class TestRangeSplitUtils {

    @Test
    public void testLongSplitEvenDivision() {
        long[] result = RangeSplitUtils.doLongSplit(0, 100, 5);

        assertEquals(6, result.length, "n slices produce n+1 boundaries");
        assertEquals(0L, result[0]);
        assertEquals(100L, result[5]);
        // 整除时每片等宽
        for (int i = 0; i < 5; i++) {
            assertEquals(20L, result[i + 1] - result[i], "equal-width slices when evenly divisible");
        }
    }

    @Test
    public void testLongSplitUnevenDivision() {
        long[] result = RangeSplitUtils.doLongSplit(0, 10, 3);

        // gap=10, step=3, remainder=1：余数分配给最早的分片
        assertEquals(4, result.length);
        assertEquals(0L, result[0]);
        assertEquals(10L, result[3]);
        assertStrictlyIncreasing(result);
        // 前闭后开铺满区间：分片宽度 4,3,3，总和等于区间长度
        assertEquals(4L, result[1] - result[0]);
        assertEquals(3L, result[2] - result[1]);
        assertEquals(3L, result[3] - result[2]);
    }

    @Test
    public void testLongSplitReversedRangeIsNormalizedAscending() {
        long[] result = RangeSplitUtils.doLongSplit(100, 0, 4);

        assertEquals(0L, result[0], "left>right must be swapped so result is ascending");
        assertEquals(100L, result[result.length - 1]);
        assertEquals(5, result.length);
        assertStrictlyIncreasing(result);
    }

    @Test
    public void testLongSplitSinglePointRange() {
        long[] result = RangeSplitUtils.doLongSplit(5, 5, 4);

        assertEquals(2, result.length);
        assertEquals(5L, result[0]);
        assertEquals(5L, result[1]);
    }

    @Test
    public void testLongSplitStepZeroShrinksSliceCount() {
        // 区间宽度(2) < 期望分片数(5)：step=0，实际分片数收缩为 remainder=2，退化为 [0,1,2]
        long[] result = RangeSplitUtils.doLongSplit(0, 2, 5);

        assertEquals(3, result.length, "degenerate range shrinks actual slice count");
        assertStrictlyIncreasing(result);
        assertEquals(0L, result[0]);
        assertEquals(2L, result[2]);

        // 宽度 1 的极端情况：只有 1 个分片
        long[] tiny = RangeSplitUtils.doLongSplit(0, 1, 5);
        assertEquals(2, tiny.length);
        assertEquals(0L, tiny[0]);
        assertEquals(1L, tiny[1]);
    }

    @Test
    public void testBigIntegerSplitPartitionsRangeWithoutGapOrOverlap() {
        BigInteger[] result = RangeSplitUtils.doBigIntegerSplit(BigInteger.ZERO, BigInteger.valueOf(1000), 7);

        assertEquals(8, result.length);
        assertEquals(BigInteger.ZERO, result[0]);
        assertEquals(BigInteger.valueOf(1000), result[7]);
        // 严格递增 => 分片之间无重叠、无遗漏
        for (int i = 0; i < 7; i++) {
            assertTrue(result[i].compareTo(result[i + 1]) < 0, "boundaries strictly increasing at " + i);
        }
        assertEquals(java.util.List.of(BigInteger.ZERO, BigInteger.valueOf(143), BigInteger.valueOf(286),
                BigInteger.valueOf(429), BigInteger.valueOf(572), BigInteger.valueOf(715),
                BigInteger.valueOf(858), BigInteger.valueOf(1000)), java.util.Arrays.asList(result));
    }

    @Test
    public void testLongSplitInvalidSliceNumberThrows() {
        assertInstanceOf(IllegalArgumentException.class,
                assertThrows(Exception.class, () -> RangeSplitUtils.doLongSplit(0, 100, 0)));
        assertInstanceOf(IllegalArgumentException.class,
                assertThrows(Exception.class, () -> RangeSplitUtils.doBigIntegerSplit(BigInteger.ZERO,
                        BigInteger.TEN, -1)));
    }

    @Test
    public void testLongSplitNullBoundsThrow() {
        assertInstanceOf(IllegalArgumentException.class,
                assertThrows(Exception.class, () -> RangeSplitUtils.doBigIntegerSplit(null, BigInteger.TEN, 3)));
        assertInstanceOf(IllegalArgumentException.class,
                assertThrows(Exception.class, () -> RangeSplitUtils.doBigIntegerSplit(BigInteger.ZERO, null, 3)));
    }

    @Test
    public void testAsciiStringSplitKeepsEndpointsAndMonotonicOrder() {
        String[] result = RangeSplitUtils.doAsciiStringSplit("a", "azz", 3);

        assertEquals(4, result.length);
        assertEquals("a", result[0], "first boundary must be the original left endpoint");
        assertEquals("azz", result[3], "last boundary must be the original right endpoint");

        // 中间分片点换算回数值后必须严格落在区间内且单调递增（不重不漏）
        BigInteger prev = RangeSplitUtils.stringToBigInteger(result[0], 128);
        for (int i = 1; i < result.length; i++) {
            BigInteger cur = RangeSplitUtils.stringToBigInteger(result[i], 128);
            assertTrue(prev.compareTo(cur) < 0, "split point " + i + " must be strictly increasing");
            prev = cur;
        }
        assertTrue(RangeSplitUtils.stringToBigInteger(result[1], 128)
                .compareTo(RangeSplitUtils.stringToBigInteger("azz", 128)) < 0);
    }

    @Test
    public void testAsciiStringSplitDegenerateRangeShrinks() {
        // 相邻字符区间宽度1 < 期望3片：退化为仅首尾两个边界
        String[] result = RangeSplitUtils.doAsciiStringSplit("A", "B", 3);

        assertEquals(2, result.length);
        assertEquals("A", result[0]);
        assertEquals("B", result[1]);
    }

    @Test
    public void testStringToBigIntegerAsciiValue() {
        // 'a'=97 'b'=98：两位128进制数值为 97*128+98
        assertEquals(BigInteger.valueOf(12514), RangeSplitUtils.stringToBigInteger("ab", 128));
        assertEquals(BigInteger.valueOf(97), RangeSplitUtils.stringToBigInteger("a", 128));
    }

    @Test
    public void testStringToBigIntegerRejectsNonAsciiAndBadRadix() {
        assertInstanceOf(IllegalArgumentException.class,
                assertThrows(Exception.class, () -> RangeSplitUtils.stringToBigInteger("中", 128)));
        assertInstanceOf(IllegalArgumentException.class,
                assertThrows(Exception.class, () -> RangeSplitUtils.stringToBigInteger("a", 0)));
        assertInstanceOf(IllegalArgumentException.class,
                assertThrows(Exception.class, () -> RangeSplitUtils.stringToBigInteger("a", 129)));
        assertInstanceOf(IllegalArgumentException.class,
                assertThrows(Exception.class, () -> RangeSplitUtils.stringToBigInteger(null, 128)));
    }

    @Test
    public void testGetMinAndMaxCharacter() {
        Pair<Character, Character> pair = RangeSplitUtils.getMinAndMaxCharacter("hello");
        assertEquals(Character.valueOf('e'), pair.getLeft());
        assertEquals(Character.valueOf('o'), pair.getRight());
    }

    @Test
    public void testGetMinAndMaxCharacterRejectsNonAscii() {
        assertInstanceOf(IllegalArgumentException.class,
                assertThrows(Exception.class, () -> RangeSplitUtils.getMinAndMaxCharacter("hé")));
        assertInstanceOf(IllegalArgumentException.class,
                assertThrows(Exception.class, () -> RangeSplitUtils.getMinAndMaxCharacter(null)));
    }

    private void assertStrictlyIncreasing(long[] result) {
        for (int i = 0; i < result.length - 1; i++) {
            assertTrue(result[i] < result[i + 1], "boundary " + i + " must be strictly increasing");
        }
    }
}
