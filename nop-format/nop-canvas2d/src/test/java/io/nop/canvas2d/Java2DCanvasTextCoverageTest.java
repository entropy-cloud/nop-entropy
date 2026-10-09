package io.nop.canvas2d;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI2 closure audit 修复轮（Major-1/Minor-3 补充）：五类 baseline 锚点像素反差、
 * save/restore 恢复 align/baseline、LRU 逐出、ascent/descent、注入路径。
 */
class Java2DCanvasTextCoverageTest {

    @Test
    void baselineMiddleCentersInkOnAnchor() {
        // D8：middle → drawY = y + (ascent-descent)/2，墨迹纵向中心 ≈ 锚点
        Java2DCanvas c = new Java2DCanvas(120, 120);
        c.setFont("20px sans-serif");
        c.setFillStyleCss("#000000");
        c.setTextBaseline("middle");
        c.fillText("MMMM", 20, 60);
        int[] span = inkSpanY(c.getImage(), 15, 100, 0, 119);
        assertTrue(span != null);
        int center = (span[0] + span[1]) / 2;
        assertTrue(Math.abs(center - 60) <= 4, "middle ink center " + center + " should ≈ anchor 60");
    }

    @Test
    void baselineBottomKeepsInkAboveAnchor() {
        // D8：bottom → 墨迹底部 ≈ 锚点（-descent 语义），锚点下方 3px 内无墨迹
        Java2DCanvas c = new Java2DCanvas(120, 120);
        c.setFont("20px sans-serif");
        c.setFillStyleCss("#000000");
        c.setTextBaseline("bottom");
        c.fillText("MMMM", 20, 60);
        int[] span = inkSpanY(c.getImage(), 15, 100, 0, 119);
        assertTrue(span != null);
        assertTrue(span[1] <= 62, "bottom ink bottom " + span[1] + " should be ≈ anchor 60");
        assertTrue(span[1] - span[0] > 8, "ink must exist above anchor");
    }

    @Test
    void hangingMatchesTopAndIdeographicMatchesBottom() {
        // D8：hanging≡top、ideographic≡bottom 同锚换算——同参数渲染像素分布一致
        int[][] spans = new int[4][2];
        String[] baselines = {"top", "hanging", "bottom", "ideographic"};
        for (int i = 0; i < 4; i++) {
            Java2DCanvas c = new Java2DCanvas(120, 120);
            c.setFont("20px sans-serif");
            c.setFillStyleCss("#000000");
            c.setTextBaseline(baselines[i]);
            c.fillText("MMMM", 20, 60);
            int[] span = inkSpanY(c.getImage(), 15, 100, 0, 119);
            assertTrue(span != null, baselines[i] + " must paint ink");
            spans[i] = span;
        }
        assertTrue(Math.abs(spans[0][0] - spans[1][0]) <= 2, "hanging must match top");
        assertTrue(Math.abs(spans[2][1] - spans[3][1]) <= 2, "ideographic must match bottom");
    }

    @Test
    void saveRestoreRecoversAlignAndBaseline() {
        // Canvas 语义：align/baseline 属于状态栈——restore 后锚点行为回到 save 时刻
        Java2DCanvas c = new Java2DCanvas(160, 120);
        c.setFont("20px sans-serif");
        c.setFillStyleCss("#000000");
        c.save();
        c.setTextAlign("center");
        c.setTextBaseline("middle");
        c.restore();
        // restore 后为 left/start + alphabetic：墨迹起于锚点 x 右侧、主体在锚点上方
        c.fillText("MMMM", 40, 80);
        BufferedImage img = c.getImage();
        int[] spanX = inkSpanX(img, 0, 159, 40, 110);
        assertTrue(spanX != null && spanX[0] >= 38 && spanX[0] <= 48,
                "restored left-align ink should start at anchor x≈40, got " + spanX[0]);
        int[] spanY = inkSpanY(img, 30, 120, 0, 119);
        assertTrue(spanY != null && spanY[1] <= 86, "restored alphabetic ink should end near baseline 80");
    }

    @Test
    void lruEvictionWithCustomCacheSize() {
        // D7：LRU 上限可配——cacheSize=1 时第三个文本把第一个挤出，再测第一个是 miss（两次 miss）
        TextMeasurer m = new TextMeasurer(new CanvasFontMapper(), 1);
        m.measure("A", "20px sans-serif");
        long misses1 = m.getMetricsCacheMisses();
        m.measure("B", "20px sans-serif");
        m.measure("A", "20px sans-serif");
        long misses3 = m.getMetricsCacheMisses();
        assertEquals(misses1 + 2, misses3, "A must have been evicted and re-missed");
    }

    @Test
    void textMetricsAscentDescentConsistent() {
        TextMeasurer m = new TextMeasurer();
        ITextMetrics metrics = m.measure("Mg", "20px sans-serif");
        assertTrue(metrics.getAscent() > 0 && metrics.getDescent() > 0);
        // 20px 字体的 ascent+descent 应在合理区间
        assertTrue(metrics.getAscent() + metrics.getDescent() > 15
                && metrics.getAscent() + metrics.getDescent() < 40);
        assertTrue(metrics.getAscent() > metrics.getDescent(), "ascent typically exceeds descent");
    }

    @Test
    void constructorInjectionWiresCustomMeasurer() {
        // 接线验证：注入的 TextMeasurer 真实被 canvas 消费（注入前预热缓存，canvas 度量命中）
        TextMeasurer custom = new TextMeasurer();
        custom.measure("注入探针", "10px sans-serif");
        long hitsBefore = custom.getMetricsCacheHits();
        Java2DCanvas c = new Java2DCanvas(60, 60, custom);
        c.measureText("注入探针");
        assertEquals(hitsBefore + 1, custom.getMetricsCacheHits(),
                "canvas measureText must hit the injected measurer's cache");
    }

    private static int[] inkSpanY(BufferedImage img, int x0, int x1, int y0, int y1) {
        int first = -1;
        int last = -1;
        for (int y = Math.max(0, y0); y <= Math.min(img.getHeight() - 1, y1); y++) {
            for (int x = Math.max(0, x0); x <= Math.min(img.getWidth() - 1, x1); x++) {
                if (((img.getRGB(x, y) >>> 24) & 0xFF) >= 140) {
                    if (first < 0)
                        first = y;
                    last = y;
                }
            }
        }
        return first < 0 ? null : new int[]{first, last};
    }

    private static int[] inkSpanX(BufferedImage img, int x0, int x1, int y0, int y1) {
        int first = -1;
        int last = -1;
        for (int y = Math.max(0, y0); y <= Math.min(img.getHeight() - 1, y1); y++) {
            for (int x = Math.max(0, x0); x <= Math.min(img.getWidth() - 1, x1); x++) {
                if (((img.getRGB(x, y) >>> 24) & 0xFF) >= 140) {
                    if (first < 0)
                        first = x;
                    last = x;
                }
            }
        }
        return first < 0 ? null : new int[]{first, last};
    }
}
