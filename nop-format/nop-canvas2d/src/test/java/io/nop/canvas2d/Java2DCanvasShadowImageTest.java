package io.nop.canvas2d;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3 Phase 2：阴影（D11）、drawImage（D12）、图像数据。
 */
class Java2DCanvasShadowImageTest {

    @Test
    void shadowOffsetShiftsInk() {
        // D11：offset 阴影——形状 [20,40]，红阴影 offset(15,0) → 阴影墨迹 [35,55]
        Java2DCanvas c = new Java2DCanvas(80, 60);
        c.setShadowColor("#ff0000");
        c.setShadowOffsetX(15);
        c.setFillStyleCss("#000000");
        c.fillRect(20, 20, 20, 20);
        PixelAssert.assertOpaque(c.getImage(), 25, 30);
        // (45,30) 在形状外但阴影内 → 红色（阴影在形状下层，此处无本体叠加）
        PixelAssert.assertRgbNear(c.getImage(), 47, 30, 255, 0, 0, 30);
    }

    @Test
    void shadowOffsetUnaffectedByScaleCtm() {
        // B2 回归钉：offset/blur 在设备空间——scale(2,2) 下 offset 20px 保持设备 20px 位移
        Java2DCanvas c = new Java2DCanvas(120, 60);
        c.scale(2, 2);
        c.setShadowColor("#ff0000");
        c.setShadowOffsetX(20);
        c.setFillStyleCss("#000000");
        c.fillRect(10, 10, 10, 10);
        // 形状设备区 [20,40]；正确实现阴影区 [40,60]（offset 20 设备像素）
        PixelAssert.assertRgbNear(c.getImage(), 50, 30, 255, 0, 0, 30);
        // 若实现错误地把 offset 乘 CTM（→40），阴影区为 [60,80]，(50,30) 将无阴影墨迹
    }

    @Test
    void shadowBlurSpreadsBeyondShape() {
        // D11：σ=blur/2=4，采样距边界 ≥2σ=8px 的扩散区
        Java2DCanvas c = new Java2DCanvas(100, 60);
        c.setShadowColor("#ff0000");
        c.setShadowBlur(8);
        c.setFillStyleCss("#000000");
        c.fillRect(40, 20, 20, 20);
        // 形状右边界 x=60；blur=8 → σ=4；x=64 为边界外 1σ 处，高斯尾部 ≈16%（确定性采样带）
        int alpha = (c.getImage().getRGB(64, 30) >>> 24) & 0xFF;
        assertTrue(alpha > 20, "blurred shadow tail should paint at 1σ beyond edge, alpha=" + alpha);
        // 本体保持实心
        PixelAssert.assertOpaque(c.getImage(), 50, 30);
    }

    @Test
    void shadowColorTintsShadow() {
        Java2DCanvas c = new Java2DCanvas(80, 60);
        c.setShadowColor("#0000ff");
        c.setShadowOffsetX(15);
        c.setFillStyleCss("#000000");
        c.fillRect(20, 20, 20, 20);
        PixelAssert.assertRgbNear(c.getImage(), 47, 30, 0, 0, 255, 30);
    }

    @Test
    void transparentShadowColorIsInert() {
        // D11 激活条件：shadowColor alpha=0 → 与无阴影渲染逐像素等价
        Java2DCanvas withShadow = new Java2DCanvas(60, 60);
        withShadow.setShadowColor("rgba(0,0,0,0)");
        withShadow.setShadowOffsetX(30);
        withShadow.setFillStyleCss("#ff0000");
        withShadow.fillRect(20, 20, 20, 20);
        Java2DCanvas plain = new Java2DCanvas(60, 60);
        plain.setFillStyleCss("#ff0000");
        plain.fillRect(20, 20, 20, 20);
        for (int y = 0; y < 60; y += 7) {
            for (int x = 0; x < 60; x += 7) {
                assertEquals(plain.getImage().getRGB(x, y), withShadow.getImage().getRGB(x, y),
                        "transparent shadow must not alter rendering at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void shadowStateSnapshotRestore() {
        Java2DCanvas c = new Java2DCanvas(80, 60);
        c.save();
        c.setShadowColor("#ff0000");
        c.setShadowOffsetX(15);
        c.setFillStyleCss("#000000");
        c.restore();
        c.fillRect(20, 20, 20, 20);
        // restore 后无阴影：形状外无墨迹
        PixelAssert.assertTransparent(c.getImage(), 47, 30);
        PixelAssert.assertOpaque(c.getImage(), 30, 30);
    }

    @Test
    void shadowValidationThrows() {
        Java2DCanvas c = new Java2DCanvas(20, 20);
        assertThrows(NopCanvas2DException.class, () -> c.setShadowBlur(-1));
        assertThrows(NopCanvas2DException.class, () -> c.setShadowBlur(Double.NaN));
        assertThrows(NopCanvas2DException.class, () -> c.setShadowColor("bogus"));
        assertThrows(NopCanvas2DException.class, () -> c.setShadowOffsetX(Double.NaN));
    }

    @Test
    void fillTextAndStrokeTextCarryShadow() {
        // M7：文本受阴影影响
        Java2DCanvas c = new Java2DCanvas(160, 60);
        c.setFont("24px sans-serif");
        c.setShadowColor("#ff0000");
        c.setShadowOffsetX(10);
        c.setFillStyleCss("#000000");
        c.fillText("MMMM", 20, 40);
        // 文本本体墨迹区偏移 10px 处应有红色阴影墨迹（扫固定带找红色像素，布尔语义）
        assertTrue(hasRedInk(c.getImage(), 20, 130, 20, 58), "text shadow ink must exist");
    }

    @Test
    void drawImageCarriesShadow() {
        // B1 修正：drawImage 受阴影影响
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 10, 10);
        g.dispose();
        Java2DCanvas c = new Java2DCanvas(80, 60);
        c.setShadowColor("#ff0000");
        c.setShadowOffsetX(20);
        c.drawImage(img, 20, 20);
        // 图像 [20,30]，阴影 [40,50]：采样 (45,25) 红色
        PixelAssert.assertRgbNear(c.getImage(), 45, 25, 255, 0, 0, 30);
        PixelAssert.assertOpaque(c.getImage(), 25, 25);
    }

    @Test
    void drawImageThreeForms() {
        // 源图：左半红右半蓝
        BufferedImage img = new BufferedImage(20, 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 10, 10);
        g.setColor(Color.BLUE);
        g.fillRect(10, 0, 10, 10);
        g.dispose();

        // 形态 1：1:1 落位
        Java2DCanvas f1 = new Java2DCanvas(60, 30);
        f1.drawImage(img, 5, 10);
        PixelAssert.assertRgbNear(f1.getImage(), 10, 15, 255, 0, 0, 10);
        PixelAssert.assertRgbNear(f1.getImage(), 20, 15, 0, 0, 255, 10);

        // 形态 2：缩放 ×2
        Java2DCanvas f2 = new Java2DCanvas(80, 30);
        f2.drawImage(img, 0, 5, 40, 20);
        PixelAssert.assertRgbNear(f2.getImage(), 15, 15, 255, 0, 0, 10);
        PixelAssert.assertRgbNear(f2.getImage(), 35, 15, 0, 0, 255, 10);

        // 形态 3：源子矩形（左半）拉伸到全宽
        Java2DCanvas f3 = new Java2DCanvas(60, 30);
        f3.drawImage(img, 0, 0, 10, 10, 0, 5, 40, 20);
        PixelAssert.assertRgbNear(f3.getImage(), 30, 15, 255, 0, 0, 10);
    }

    @Test
    void drawImageSourceRectIntersectsAndShrinksProportionally() {
        // M4：源矩形 sx=-10,sw=20 与 [0,20] 求交 → 可见 [0,10]，目标同比例收缩为左半
        BufferedImage img = new BufferedImage(20, 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 20, 10);
        g.dispose();
        Java2DCanvas c = new Java2DCanvas(60, 30);
        c.drawImage(img, -10, 0, 20, 10, 0, 0, 20, 10);
        // 可见源 [0,10] 位于源矩形 [−10,10] 的后半 → 目标同比例落位 [10,20]
        PixelAssert.assertRgbNear(c.getImage(), 15, 5, 255, 0, 0, 10);
        PixelAssert.assertTransparent(c.getImage(), 5, 5);
    }

    @Test
    void drawImageValidation() {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        Java2DCanvas c = new Java2DCanvas(30, 30);
        assertThrows(NopCanvas2DException.class, () -> c.drawImage(null, 0, 0));
        assertThrows(NopCanvas2DException.class,
                () -> c.drawImage(img, 0, 0, 5, 5, Double.NaN, 0, 5, 5));
        assertThrows(NopCanvas2DException.class,
                () -> c.drawImage(img, 0, 0, -5, 5, 0, 0, 5, 5));
        // sw/sh = 0 → no-op
        c.drawImage(img, 0, 0, 0, 5, 0, 0, 5, 5);
    }

    @Test
    void getImageDataPutImageDataRoundTrip() {
        Java2DCanvas c = new Java2DCanvas(40, 40);
        c.setFillStyleCss("#ff0000");
        c.fillRect(10, 10, 20, 20);
        IImageData data = c.getImageData(0, 0, 40, 40);
        assertEquals(0xFFFF0000, data.getData()[11 * 40 + 15]);
        assertEquals(0, data.getData()[0]);

        // 往返：改一个像素写回
        data.getData()[0] = 0xFF00FF00;
        c.putImageData(data, 0, 0);
        assertEquals(0xFF00FF00, c.getImage().getRGB(0, 0));
        assertEquals(0xFFFF0000, c.getImage().getRGB(15, 11));
    }

    @Test
    void putImageDataReplacesWholesaleIncludingAlpha() {
        // M5：半透明像素覆盖不透明底 → 读回精确等于写入值（SRC 替换，非 SrcOver 混合）
        Java2DCanvas c = new Java2DCanvas(20, 20);
        c.setFillStyleCss("#ff0000");
        c.fillRect(0, 0, 20, 20);
        int[] px = new int[1];
        px[0] = 0x80000000;
        c.putImageData(new ImageData(1, 1, px), 5, 5);
        assertEquals(0x80000000, c.getImage().getRGB(5, 5));
    }

    @Test
    void putImageDataBypassesTransformClipAlpha() {
        // D12：putImageData 绕过变换/clip/alpha
        Java2DCanvas c = new Java2DCanvas(40, 40);
        c.translate(100, 100);
        c.setGlobalAlpha(0.1);
        c.beginPath();
        c.rect(0, 0, 5, 5);
        c.clip();
        int[] px = {0xFF00FF00};
        c.putImageData(new ImageData(1, 1, px), 10, 10);
        assertEquals(0xFF00FF00, c.getImage().getRGB(10, 10));
    }

    @Test
    void putImageDataDirtyRectAndValidation() {
        Java2DCanvas c = new Java2DCanvas(30, 30);
        c.setFillStyleCss("#ff0000");
        c.fillRect(0, 0, 30, 30);
        int[] px = new int[9];
        java.util.Arrays.fill(px, 0xFF00FF00);
        c.putImageData(new ImageData(3, 3, px), 10, 10, 1, 1, 1, 1);
        // 只 dirty (1,1) → 画布 (11,11) 变绿
        assertEquals(0xFF00FF00, c.getImage().getRGB(11, 11));
        assertEquals(0xFFFF0000, c.getImage().getRGB(10, 10));
        assertThrows(NopCanvas2DException.class,
                () -> c.putImageData(new ImageData(3, 3, px), 0, 0, -1, 0, 2, 2));
        assertThrows(NopCanvas2DException.class, () -> c.putImageData(null, 0, 0));
    }

    @Test
    void getImageDataOutOfBoundsReturnsTransparent() {
        Java2DCanvas c = new Java2DCanvas(20, 20);
        c.setFillStyleCss("#ff0000");
        c.fillRect(0, 0, 20, 20);
        IImageData data = c.getImageData(-5, -5, 10, 10);
        // 越界区透明
        assertEquals(0, data.getData()[0]);
        // 区内红
        assertEquals(0xFFFF0000, data.getData()[6 * 10 + 6]);
    }

    @Test
    void getImageDataSizeValidation() {
        Java2DCanvas c = new Java2DCanvas(20, 20);
        assertThrows(NopCanvas2DException.class, () -> c.getImageData(0, 0, 0, 5));
        assertThrows(NopCanvas2DException.class, () -> c.getImageData(0, 0, 5, -1));
    }

    private static boolean hasRedInk(BufferedImage img, int x0, int x1, int y0, int y1) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int a = (rgb >>> 24) & 0xFF;
                if (a > 100 && r > 180)
                    return true;
            }
        }
        return false;
    }
}
