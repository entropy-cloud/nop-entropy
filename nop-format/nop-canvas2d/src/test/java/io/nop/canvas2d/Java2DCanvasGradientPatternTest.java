package io.nop.canvas2d;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * WI3 Phase 1：渐变（D9）、图案（D10）、clip 设备空间修复、copyFrom、工厂。
 */
class Java2DCanvasGradientPatternTest {

    @Test
    void linearGradientEndpointColorsAndDirection() {
        // D9：线性渐变沿 (0,0)→(100,0)，左端红右端蓝
        Java2DCanvas c = new Java2DCanvas(100, 40);
        ICanvasGradient g = c.createLinearGradient(0, 0, 100, 0);
        g.addColorStop(0, "#ff0000");
        g.addColorStop(1, "#0000ff");
        c.setFillStyleGradient(g);
        c.fillRect(0, 0, 100, 40);
        PixelAssert.assertRgbNear(c.getImage(), 5, 20, 255, 0, 0, 40);
        PixelAssert.assertRgbNear(c.getImage(), 95, 20, 0, 0, 255, 40);
        PixelAssert.assertRgbNear(c.getImage(), 50, 20, 128, 0, 128, 90);
    }

    @Test
    void gradientFollowsCurrentTransform() {
        // D9（探针 P1 场景）：渐变坐标在绘制时用户空间——translate(50,0) 后
        // 用户 x=10（设备 x=60）取 t=0.1 的近端色
        Java2DCanvas c = new Java2DCanvas(150, 40);
        c.translate(50, 0);
        ICanvasGradient g = c.createLinearGradient(0, 0, 100, 0);
        g.addColorStop(0, "#ff0000");
        g.addColorStop(1, "#0000ff");
        c.setFillStyleGradient(g);
        c.fillRect(0, 0, 100, 40);
        PixelAssert.assertRgbNear(c.getImage(), 60, 20, 255, 0, 0, 60);
        PixelAssert.assertRgbNear(c.getImage(), 140, 20, 0, 0, 255, 60);
        PixelAssert.assertTransparent(c.getImage(), 20, 20);
    }

    @Test
    void radialGradientCenterAndEdge() {
        // D9：径向渐变中心 stop0、边缘 stop1
        Java2DCanvas c = new Java2DCanvas(60, 60);
        ICanvasGradient g = c.createRadialGradient(30, 30, 0, 30, 30, 25);
        g.addColorStop(0, "#ffffff");
        g.addColorStop(1, "#000000");
        c.setFillStyleGradient(g);
        c.fillRect(0, 0, 60, 60);
        PixelAssert.assertRgbNear(c.getImage(), 30, 30, 255, 255, 255, 20);
        PixelAssert.assertRgbNear(c.getImage(), 52, 30, 0, 0, 0, 40);
    }

    @Test
    void degenerateAndNoStopGradientsPaintNothing() {
        // D9 规范对齐例外：退化渐变（两点重合）与无 stop 渐变绘制为空、不抛错
        Java2DCanvas degenerate = new Java2DCanvas(40, 40);
        ICanvasGradient dg = degenerate.createLinearGradient(20, 20, 20, 20);
        dg.addColorStop(0, "#ff0000");
        degenerate.setFillStyleGradient(dg);
        degenerate.fillRect(0, 0, 40, 40);
        PixelAssert.assertTransparent(degenerate.getImage(), 20, 20);

        Java2DCanvas noStop = new Java2DCanvas(40, 40);
        ICanvasGradient ng = noStop.createLinearGradient(0, 0, 40, 40);
        noStop.setFillStyleGradient(ng);
        noStop.fillRect(0, 0, 40, 40);
        PixelAssert.assertTransparent(noStop.getImage(), 20, 20);
    }

    @Test
    void addColorStopValidation() {
        Java2DCanvas c = new Java2DCanvas(20, 20);
        ICanvasGradient g = c.createLinearGradient(0, 0, 20, 0);
        assertThrows(NopCanvas2DException.class, () -> g.addColorStop(-0.1, "#ff0000"));
        assertThrows(NopCanvas2DException.class, () -> g.addColorStop(1.1, "#ff0000"));
        assertThrows(NopCanvas2DException.class, () -> g.addColorStop(Double.NaN, "#ff0000"));
        assertThrows(NopCanvas2DException.class, () -> c.createRadialGradient(0, 0, -1, 0, 0, 5));
    }

    @Test
    void patternRepeatsBothDirections() {
        // D10：2x2 tile（左列红右列蓝）repeat 平铺
        BufferedImage tile = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        tile.setRGB(0, 0, 0xFFFF0000);
        tile.setRGB(1, 0, 0xFF0000FF);
        tile.setRGB(0, 1, 0xFFFF0000);
        tile.setRGB(1, 1, 0xFF0000FF);
        Java2DCanvas c = new Java2DCanvas(60, 60);
        c.setFillStylePattern(c.createPattern(tile, "repeat"));
        c.fillRect(0, 0, 60, 60);
        PixelAssert.assertRgbNear(c.getImage(), 10, 10, 255, 0, 0, 10);
        PixelAssert.assertRgbNear(c.getImage(), 11, 10, 0, 0, 255, 10);
        PixelAssert.assertRgbNear(c.getImage(), 40, 41, 255, 0, 0, 10);
    }

    @Test
    void patternRepetitionModesClipTiling() {
        BufferedImage tile = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        tile.setRGB(0, 0, 0xFFFF0000);
        tile.setRGB(1, 0, 0xFF0000FF);
        tile.setRGB(0, 1, 0xFF00FF00);
        tile.setRGB(1, 1, 0xFF0000FF);
        // repeat-x：只在 y∈[0,2) 有墨
        Java2DCanvas rx = new Java2DCanvas(20, 20);
        rx.setFillStylePattern(rx.createPattern(tile, "repeat-x"));
        rx.fillRect(0, 0, 20, 20);
        PixelAssert.assertOpaque(rx.getImage(), 10, 1);
        PixelAssert.assertTransparent(rx.getImage(), 10, 5);

        // no-repeat：只在原点 tile 区域有墨
        Java2DCanvas nr = new Java2DCanvas(20, 20);
        nr.setFillStylePattern(nr.createPattern(tile, "no-repeat"));
        nr.fillRect(0, 0, 20, 20);
        PixelAssert.assertOpaque(nr.getImage(), 1, 1);
        PixelAssert.assertTransparent(nr.getImage(), 5, 1);
        PixelAssert.assertTransparent(nr.getImage(), 1, 5);
    }

    @Test
    void patternFollowsTranslate() {
        // D10：图案锚定用户空间原点、随 CTM——translate(3,0) 后墨迹整体右移 3px
        BufferedImage tile = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        tile.setRGB(0, 0, 0xFFFF0000);
        tile.setRGB(1, 0, 0xFF0000FF);
        tile.setRGB(0, 1, 0xFFFF0000);
        tile.setRGB(1, 1, 0xFF0000FF);
        Java2DCanvas c = new Java2DCanvas(60, 60);
        c.translate(3, 0);
        c.setFillStylePattern(c.createPattern(tile, "no-repeat"));
        c.fillRect(-10, 0, 60, 60);
        // translate(3,0) 后 tile 用户区 [0,2)² 对应设备 [3,5)×[0,2)
        PixelAssert.assertRgbNear(c.getImage(), 3, 1, 255, 0, 0, 10);
        PixelAssert.assertRgbNear(c.getImage(), 4, 1, 0, 0, 255, 10);
        PixelAssert.assertTransparent(c.getImage(), 1, 1);
    }

    @Test
    void patternValidation() {
        Java2DCanvas c = new Java2DCanvas(20, 20);
        assertThrows(NopCanvas2DException.class, () -> c.createPattern(null, "repeat"));
        // 0×0 图像由 JDK 构造器抢先拒绝（IllegalArgumentException），模块级空图防御保留在 createPattern 内
        assertThrows(NopCanvas2DException.class,
                () -> c.createPattern(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "diagonal"));
    }

    @Test
    void clipIsFixedInDeviceSpace() {
        // WI3 Fix 回归钉：clip 后 translate，裁剪区不随新变换移动（探针 ProbeClip 场景）
        Java2DCanvas c = new Java2DCanvas(120, 60);
        c.beginPath();
        c.rect(10, 10, 30, 30);
        c.clip();
        c.translate(100, 0);
        c.setFillStyleCss("#ff0000");
        // 填充区（设备 x∈[20,200]）同时覆盖 clip 区内 (30,25) 与区外 (110,25)
        c.fillRect(-80, 20, 180, 20);
        PixelAssert.assertOpaque(c.getImage(), 30, 25);
        // 设备 (110,25) 在 clip 区外 → 透明（旧实现下 clip 随 transform 移动会误填充）
        PixelAssert.assertTransparent(c.getImage(), 110, 25);
    }

    @Test
    void copyFromReplacesPixelsAndValidatesSize() {
        Java2DCanvas a = new Java2DCanvas(40, 40);
        Java2DCanvas b = new Java2DCanvas(40, 40);
        b.setFillStyleCss("#00ff00");
        b.fillRect(0, 0, 40, 40);
        a.setFillStyleCss("#ff0000");
        a.fillRect(0, 0, 40, 40);
        a.copyFrom(b);
        PixelAssert.assertRgbNear(a.getImage(), 20, 20, 0, 255, 0, 10);
        assertThrows(NopCanvas2DException.class, () -> a.copyFrom(new Java2DCanvas(30, 30)));
        assertThrows(NopCanvas2DException.class, () -> a.copyFrom(null));
    }

    @Test
    void factoryCreatesDrawableCanvas() {
        Java2DCanvas c = Canvas2DFactory.create(40, 40);
        ICanvas2D element = c;
        element.getContext2D().fillRect(5, 5, 10, 10);
        assertEquals(40, element.getWidth());
        PixelAssert.assertOpaque(element.getImage(), 10, 10);
    }
}
