package io.nop.pdf.extract.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 字号统计语义：优先返回出现次数最多的字号（多数派占比>1/3时），否则返回最大字号；
 * min/max 独立统计；字号按四舍五入取整
 */
public class TestFontSizeCounter {

    @Test
    public void testMajorFontSizeWins() {
        FontSizeCounter counter = new FontSizeCounter();
        // 12 出现5次，10/14 各1次：多数派占比 5/7 > 1/3，取多数派中最大字号12
        for (int i = 0; i < 5; i++) {
            counter.onFontSize(12);
        }
        counter.onFontSize(10);
        counter.onFontSize(14);

        assertEquals(12, counter.getFontSize());
        assertEquals(14, counter.getFontSizeMax());
        assertEquals(10, counter.getFontSizeMin());
    }

    @Test
    public void testNoMajorityFallsBackToMax() {
        FontSizeCounter counter = new FontSizeCounter();
        // 每个字号只出现1次：1/3 = 1/3 不大于 0.333*3=1.0，无多数派 → 回退最大字号
        counter.onFontSize(10);
        counter.onFontSize(12);
        counter.onFontSize(14);

        assertEquals(14, counter.getFontSize());
    }

    @Test
    public void testFractionalSizeRoundsHalfUp() {
        FontSizeCounter counter = new FontSizeCounter();
        counter.onFontSize(11.4);
        counter.onFontSize(11.4);
        counter.onFontSize(11.6);
        counter.onFontSize(11.6);

        // 11.4->11, 11.6->12：两个整数桶各2次，多数派取其中最大 12
        assertEquals(12, counter.getFontSize());
        assertEquals(12, counter.getFontSizeMax());
        assertEquals(11, counter.getFontSizeMin());
    }

    @Test
    public void testEmptyCounterReturnsZero() {
        FontSizeCounter counter = new FontSizeCounter();
        assertEquals(0, counter.getFontSize());
        assertEquals(0, counter.getFontSizeMax());
        assertEquals(0, counter.getFontSizeMin());
    }

    @Test
    public void testNewObservationInvalidatesCache() {
        FontSizeCounter counter = new FontSizeCounter();
        counter.onFontSize(12);
        assertEquals(12, counter.getFontSize());

        // 后续观测必须使已缓存的统计失效并重算
        counter.onFontSize(20);
        assertEquals(20, counter.getFontSizeMax());
    }
}
