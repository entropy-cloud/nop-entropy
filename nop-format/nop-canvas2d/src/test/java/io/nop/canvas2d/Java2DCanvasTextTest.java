package io.nop.canvas2d;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI2：文本状态机（save/restore、初始状态、枚举校验）与渲染像素语义（D8 锚点换算）。
 * 像素断言使用区域存在性检查（文本字形稀疏，不适用单点断言），仍为布尔覆盖、无整图计数。
 */
class Java2DCanvasTextTest {
    private static final String FONT = "20px sans-serif";

    @Test
    void textAlignEnumValidation() {
        Java2DCanvas c = new Java2DCanvas(40, 40);
        assertThrows(NopCanvas2DException.class, () -> c.setTextAlign("middle"));
        assertThrows(NopCanvas2DException.class, () -> c.setTextAlign(null));
        assertThrows(NopCanvas2DException.class, () -> c.setTextBaseline("center"));
        assertDoesNotThrow(() -> c.setTextAlign("start"));
        assertDoesNotThrow(() -> c.setTextBaseline("middle"));
    }

    private static void assertDoesNotThrow(Runnable r) {
        r.run();
    }

    @Test
    void fillTextWithoutSetFontUsesDefaultState() {
        // D8：初始 10px sans-serif——未 setFont 直接 fillText 合法且有墨迹
        Java2DCanvas c = new Java2DCanvas(80, 40);
        c.setFillStyleCss("#000000");
        c.fillText("MMMM", 10, 25);
        assertTrue(hasInk(c.getImage(), 5, 10, 70, 35), "default font text must paint ink");
    }

    @Test
    void saveRestoreRecoversFontBehavior() {
        // Canvas 语义：font 属于状态栈——restore 后度量行为回到 save 时刻
        Java2DCanvas c = new Java2DCanvas(80, 40);
        c.setFont(FONT);
        double widthBefore = c.measureText("MMM").getWidth();
        c.save();
        c.setFont("48px sans-serif");
        double widthBig = c.measureText("MMM").getWidth();
        assertTrue(widthBig > widthBefore);
        c.restore();
        assertEquals(widthBefore, c.measureText("MMM").getWidth());
    }

    @Test
    void textAlignShiftsInkHorizontally() {
        // D8：center 锚点使墨迹水平中心落在锚点 x；right 锚点使右缘贴锚点
        Java2DCanvas c = new Java2DCanvas(200, 60);
        c.setFont("24px sans-serif");
        c.setFillStyleCss("#000000");
        c.setTextAlign("center");
        c.fillText("MMMM", 100, 40);
        BufferedImage img = c.getImage();
        int[] span = inkSpanX(img, 0, 199, 20, 50);
        assertTrue(span != null, "center-aligned text must paint ink");
        int center = (span[0] + span[1]) / 2;
        assertTrue(Math.abs(center - 100) <= 6, "ink center " + center + " should ≈ anchor 100");

        Java2DCanvas right = new Java2DCanvas(200, 60);
        right.setFont("24px sans-serif");
        right.setFillStyleCss("#000000");
        right.setTextAlign("right");
        right.fillText("MMMM", 150, 40);
        int[] spanR = inkSpanX(right.getImage(), 0, 199, 20, 50);
        assertTrue(spanR != null && spanR[1] <= 152, "right-aligned ink right edge " + spanR[1] + " ≤ 152");
    }

    @Test
    void textBaselineShiftsInkVertically() {
        // D8：top 基线使墨迹起于锚点下方（ascent 内）；alphabetic 基线使墨迹大部分在锚点上方
        Java2DCanvas top = new Java2DCanvas(120, 80);
        top.setFont("20px sans-serif");
        top.setFillStyleCss("#000000");
        top.setTextBaseline("top");
        top.fillText("MMMM", 20, 30);
        BufferedImage imgTop = top.getImage();
        // top 基线：锚点行 y=30 及以上无墨迹（字形在 30 以下），锚点上方 2px 空白
        assertTrue(!hasInk(imgTop, 15, 20, 100, 28), "top-baseline ink must start below anchor row");
        assertTrue(hasInk(imgTop, 15, 32, 100, 55), "top-baseline ink must exist below anchor");

        Java2DCanvas alphabetic = new Java2DCanvas(120, 80);
        alphabetic.setFont("20px sans-serif");
        alphabetic.setFillStyleCss("#000000");
        alphabetic.fillText("MMMM", 20, 55);
        BufferedImage imgAlpha = alphabetic.getImage();
        // alphabetic：墨迹主体在基线行上方（ascent 区），基线下仅 descent
        assertTrue(hasInk(imgAlpha, 15, 38, 100, 54), "alphabetic ink must exist above baseline");
        assertTrue(!hasInk(imgAlpha, 15, 58, 100, 78), "alphabetic ink below baseline limited to descent");
    }

    @Test
    void measureTextIndependentOfAlignAndPathState() {
        // D8：measure 不受 align/baseline 影响；不污染路径状态
        Java2DCanvas c = new Java2DCanvas(60, 60);
        c.beginPath();
        c.rect(10, 10, 30, 30);
        double w1 = c.measureText("MMMM").getWidth();
        c.setTextAlign("center");
        c.setTextBaseline("middle");
        double w2 = c.measureText("MMMM").getWidth();
        assertEquals(w1, w2);
        c.fill();
        PixelAssert.assertOpaque(c.getImage(), 25, 25);
    }

    @Test
    void fillTextDoesNotPollutePath() {
        // D8：fillText 后路径状态保留，fill 仍按原路径绘制
        Java2DCanvas c = new Java2DCanvas(60, 60);
        c.beginPath();
        c.rect(15, 15, 30, 30);
        c.setFillStyleCss("#ff0000");
        c.fillText("MMMM", 5, 12);
        c.fill();
        PixelAssert.assertOpaque(c.getImage(), 30, 30);
    }

    @Test
    void strokeTextPaintsOutline() {
        Java2DCanvas c = new Java2DCanvas(120, 60);
        c.setFont("28px sans-serif");
        c.setStrokeStyleCss("#000000");
        c.setLineWidth(1);
        c.strokeText("MMMM", 20, 40);
        assertTrue(hasInk(c.getImage(), 10, 10, 110, 55), "strokeText outline must paint ink");
    }

    @Test
    void emptyTextIsNoOpAndNullCoerces() {
        // D8：空串不绘制（宽 0）；null 强转 "null" 有墨迹
        Java2DCanvas c = new Java2DCanvas(120, 60);
        c.setFillStyleCss("#000000");
        c.fillText("", 10, 30);
        assertTrue(!hasInk(c.getImage(), 0, 0, 119, 59), "empty text must not paint");
        c.fillText(null, 10, 30);
        assertTrue(hasInk(c.getImage(), 5, 15, 110, 50), "null text coerces to \"null\" and paints");
    }

    private static boolean hasInk(BufferedImage img, int x0, int y0, int x1, int y1) {
        return inkSpanX(img, x0, x1, y0, y1) != null;
    }

    /** 区域内是否存在近不透明像素（返回墨迹的 x 范围，无墨迹返回 null）——布尔语义，无计数 */
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
