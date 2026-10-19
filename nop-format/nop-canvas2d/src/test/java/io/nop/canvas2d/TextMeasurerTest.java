package io.nop.canvas2d;

import org.junit.jupiter.api.Test;

import java.awt.Font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI2 Phase 1：度量缓存（D7——key 原始串恒等、命中一致性）与 zrender 宽字符规则锚定。
 */
class TextMeasurerTest {
    private static final String FONT = "24px sans-serif";

    @Test
    void measureReturnsPositiveWidthForCjkAndLatin() {
        TextMeasurer m = new TextMeasurer();
        assertTrue(m.measure("中文", FONT).getWidth() > 0);
        assertTrue(m.measure("abc", FONT).getWidth() > 0);
    }

    @Test
    void nullCoercesToNullStringAndEmptyIsZero() {
        // D8：null → "null"（浏览器强转语义）；空串宽 0
        TextMeasurer m = new TextMeasurer();
        assertTrue(m.measure(null, FONT).getWidth() > 0);
        assertEquals(0, m.measure("", FONT).getWidth());
    }

    @Test
    void getLineHeightEqualsWideCharWidth() {
        // 接线锚定（zrender dist:5843）：getLineHeight(font) == measureText('国', font).width
        TextMeasurer m = new TextMeasurer();
        double lineHeight = m.getLineHeight(FONT);
        double wideWidth = m.measure("国", FONT).getWidth();
        assertEquals(wideWidth, lineHeight);
        assertTrue(lineHeight > 0);
    }

    @Test
    void cacheHitsOnRepeatCalls() {
        // Anti-Hollow：缓存真实生效——第二次相同 (text,font) 命中（hit 计数增长，miss 不变）
        TextMeasurer m = new TextMeasurer();
        long missesBefore = m.getMetricsCacheMisses();
        m.measure("缓存探针", FONT);
        long missesAfterFirst = m.getMetricsCacheMisses();
        assertEquals(missesBefore + 1, missesAfterFirst);
        long hitsBefore = m.getMetricsCacheHits();
        m.measure("缓存探针", FONT);
        assertEquals(hitsBefore + 1, m.getMetricsCacheHits());
        // 命中必须与直算一致：跨新实例（无缓存）对比
        TextMeasurer fresh = new TextMeasurer();
        assertEquals(fresh.measure("缓存探针", FONT).getWidth(), m.measure("缓存探针", FONT).getWidth());
    }

    @Test
    void differentFontStringsAreDistinctCacheEntries() {
        // D7：key 为原始串恒等——不同 font 串不碰撞
        TextMeasurer m = new TextMeasurer();
        double small = m.measure("中文", "12px sans-serif").getWidth();
        double big = m.measure("中文", "48px sans-serif").getWidth();
        assertTrue(big > small * 2, "48px must be roughly 4x of 12px, got " + small + " vs " + big);
    }

    @Test
    void explicitLineHeightOverridesWideCharRule() {
        TextMeasurer m = new TextMeasurer();
        TextMeasurer.ResolvedFont resolved = m.resolveFont("bold 12px/30px sans-serif");
        assertEquals(30f, resolved.lineHeight());
        TextMeasurer.ResolvedFont derived = m.resolveFont("bold 12px sans-serif");
        assertEquals(m.measure("国", "bold 12px sans-serif").getWidth(), derived.lineHeight(), 0.001);
    }

    @Test
    void parsedFontMatchesFamilyStyle() {
        TextMeasurer m = new TextMeasurer();
        TextMeasurer.ResolvedFont resolved = m.resolveFont("italic bold 20px serif");
        assertEquals(Font.ITALIC | Font.BOLD, resolved.font().getStyle());
        assertEquals(20, resolved.font().getSize());
    }
}
