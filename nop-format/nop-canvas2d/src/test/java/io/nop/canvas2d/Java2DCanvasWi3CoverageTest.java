package io.nop.canvas2d;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3 closure audit 修复轮（Major-1/Major-2/Minor-1/Minor-2 补充）：
 * drawImage 阴影 + globalAlpha、stroke 侧渐变/图案、shadowOffsetY、D10 快照、repeat-y、工厂注入。
 */
class Java2DCanvasWi3CoverageTest {

    @Test
    void drawImageShadowRespectsGlobalAlphaOnImage() {
        // Major-1 回归钉：阴影激活时 globalAlpha 对本体 drawImage 仍生效
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 10, 10);
        g.dispose();
        Java2DCanvas c = new Java2DCanvas(80, 60);
        c.setShadowColor("#ff0000");
        c.setShadowOffsetX(20);
        c.setGlobalAlpha(0.3);
        c.drawImage(img, 20, 20);
        int alpha = (c.getImage().getRGB(25, 25) >>> 24) & 0xFF;
        assertTrue(alpha > 50 && alpha < 105,
                "image body alpha should be ≈0.3*255=77 (was lost before fix), got " + alpha);
    }

    @Test
    void blurredShadowTailClippedByClip() {
        // Minor-1 回归钉：D11 阴影受 clip 约束——模糊尾部不得越出裁剪区
        Java2DCanvas c = new Java2DCanvas(120, 60);
        c.beginPath();
        c.rect(0, 0, 60, 60);
        c.clip();
        c.setShadowColor("#ff0000");
        c.setShadowBlur(12);
        c.setFillStyleCss("#000000");
        c.fillRect(40, 20, 10, 20);
        // 形状右边界 50，clip 边界 60：clip 外 (65,30) 必须无墨迹
        int alpha = (c.getImage().getRGB(65, 30) >>> 24) & 0xFF;
        assertEquals(0, alpha, "blur tail must not leak beyond clip, got alpha=" + alpha);
    }

    @Test
    void strokeStyleGradientPaintsStroke() {
        // Major-2：stroke 侧渐变路径
        Java2DCanvas c = new Java2DCanvas(60, 60);
        c.beginPath();
        c.moveTo(10, 30);
        c.lineTo(50, 30);
        c.setLineWidth(4);
        ICanvasGradient gradient = c.createLinearGradient(10, 0, 50, 0);
        gradient.addColorStop(0, "#ff0000");
        gradient.addColorStop(1, "#0000ff");
        c.setStrokeStyleGradient(gradient);
        c.stroke();
        PixelAssert.assertRgbNear(c.getImage(), 12, 30, 255, 0, 0, 40);
        PixelAssert.assertRgbNear(c.getImage(), 48, 30, 0, 0, 255, 40);
    }

    @Test
    void strokeStylePatternPaintsStroke() {
        // Major-2：stroke 侧图案路径
        BufferedImage tile = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        tile.setRGB(0, 0, 0xFFFF0000);
        tile.setRGB(1, 0, 0xFF0000FF);
        tile.setRGB(0, 1, 0xFFFF0000);
        tile.setRGB(1, 1, 0xFF0000FF);
        Java2DCanvas c = new Java2DCanvas(60, 60);
        c.beginPath();
        c.moveTo(5, 30);
        c.lineTo(55, 30);
        c.setLineWidth(2);
        c.setStrokeStylePattern(c.createPattern(tile, "repeat"));
        c.stroke();
        // 描边带内有图案墨迹（红蓝交替在 x 方向变化）
        boolean hasRed = false;
        boolean hasBlue = false;
        for (int x = 5; x <= 55 && !(hasRed && hasBlue); x++) {
            int rgb = c.getImage().getRGB(x, 30);
            int r = (rgb >> 16) & 0xFF;
            int b = rgb & 0xFF;
            int a = (rgb >>> 24) & 0xFF;
            if (a > 200) {
                hasRed |= r > 200;
                hasBlue |= b > 200;
            }
        }
        assertTrue(hasRed && hasBlue, "pattern stroke must carry tile colors");
    }

    @Test
    void shadowOffsetYShiftsInkVertically() {
        // Major-2：offsetY 位移
        Java2DCanvas c = new Java2DCanvas(80, 80);
        c.setShadowColor("#ff0000");
        c.setShadowOffsetY(15);
        c.setFillStyleCss("#000000");
        c.fillRect(20, 20, 20, 20);
        // 形状 [20,40]²，阴影下移 [35,55]：采样 (30,50) 红色（形状下方、阴影区内）
        PixelAssert.assertRgbNear(c.getImage(), 30, 50, 255, 0, 0, 30);
    }

    @Test
    void patternSourceIsSnapshotAtCreation() {
        // D10：createPattern 后修改源图不影响 pattern
        BufferedImage tile = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        tile.setRGB(0, 0, 0xFFFF0000);
        tile.setRGB(1, 0, 0xFF0000FF);
        tile.setRGB(0, 1, 0xFFFF0000);
        tile.setRGB(1, 1, 0xFF0000FF);
        Java2DCanvas c = new Java2DCanvas(40, 40);
        ICanvasPattern pattern = c.createPattern(tile, "repeat");
        // 修改源图（红→绿）
        tile.setRGB(0, 0, 0xFF00FF00);
        tile.setRGB(1, 0, 0xFF00FF00);
        tile.setRGB(0, 1, 0xFF00FF00);
        tile.setRGB(1, 1, 0xFF00FF00);
        c.setFillStylePattern(pattern);
        c.fillRect(0, 0, 40, 40);
        // 仍是创建时的红蓝 tile
        PixelAssert.assertRgbNear(c.getImage(), 10, 10, 255, 0, 0, 10);
        PixelAssert.assertRgbNear(c.getImage(), 11, 10, 0, 0, 255, 10);
    }

    @Test
    void patternRepeatYClipsHorizontally() {
        // Minor-2：repeat-y 只在 x∈[0,2) 有墨
        BufferedImage tile = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        tile.setRGB(0, 0, 0xFFFF0000);
        tile.setRGB(1, 0, 0xFF0000FF);
        tile.setRGB(0, 1, 0xFFFF0000);
        tile.setRGB(1, 1, 0xFF0000FF);
        Java2DCanvas c = new Java2DCanvas(20, 20);
        c.setFillStylePattern(c.createPattern(tile, "repeat-y"));
        c.fillRect(0, 0, 20, 20);
        PixelAssert.assertOpaque(c.getImage(), 1, 10);
        PixelAssert.assertTransparent(c.getImage(), 5, 10);
    }

    @Test
    void factoryOverloadInjectsTextMeasurer() {
        TextMeasurer custom = new TextMeasurer();
        custom.measure("工厂注入探针", "10px sans-serif");
        long hitsBefore = custom.getMetricsCacheHits();
        Java2DCanvas c = Canvas2DFactory.create(40, 40, custom);
        c.measureText("工厂注入探针");
        assertEquals(hitsBefore + 1, custom.getMetricsCacheHits());
    }
}
