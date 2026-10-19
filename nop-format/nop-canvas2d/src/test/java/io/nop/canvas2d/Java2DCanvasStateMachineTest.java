package io.nop.canvas2d;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI1 Phase 1：状态机核心语义（save/restore、transform、globalAlpha、clearRect、D2 抛错矩阵）。
 * 每条测试注明所钉住的 Canvas 语义。
 */
class Java2DCanvasStateMachineTest {

    private static Java2DCanvas canvas(int w, int h) {
        return new Java2DCanvas(w, h);
    }

    @Test
    void canvasStartsFullyTransparent() {
        // D1：画布初始全透明 (0,0,0,0)
        Java2DCanvas c = canvas(20, 20);
        PixelAssert.assertAlphaNear(c.getImage(), 10, 10, 0, 0);
    }

    @Test
    void fillRectPaintsOpaqueAndRestoreStackUnderflowThrows() {
        Java2DCanvas c = canvas(20, 20);
        c.fillRect(5, 5, 10, 10);
        PixelAssert.assertOpaque(c.getImage(), 10, 10);
        PixelAssert.assertTransparent(c.getImage(), 2, 2);
        assertThrows(NopCanvas2DException.class, c::restore);
    }

    @Test
    void saveRestoreRecoversTransformBehavior() {
        // Canvas 语义：restore 恢复变换到 save 时刻，之后的绘制回到原坐标系
        Java2DCanvas c = canvas(60, 60);
        c.save();
        c.translate(30, 30);
        c.rotate(Math.PI / 2);
        c.restore();
        c.fillRect(5, 5, 10, 10);
        PixelAssert.assertOpaque(c.getImage(), 10, 10);
        PixelAssert.assertTransparent(c.getImage(), 40, 40);
    }

    @Test
    void saveRestoreRecoversStyleState() {
        // Canvas 语义：fillStyle/globalAlpha 属于状态栈
        Java2DCanvas c = canvas(60, 40);
        c.setFillStyleCss("#ff0000");
        c.save();
        c.setFillStyleCss("#00ff00");
        c.setGlobalAlpha(0.2);
        c.restore();
        c.fillRect(5, 5, 20, 20);
        PixelAssert.assertRgbNear(c.getImage(), 15, 15, 255, 0, 0, 12);
        PixelAssert.assertAlphaNear(c.getImage(), 15, 15, 255, 3);
    }

    @Test
    void transformCompositionOrderFollowsPostMultiply() {
        // Canvas 语义：transform 按调用顺序后乘——translate(20,0) 后 rotate(90°)，局部原点落在 (20,0)
        Java2DCanvas c = canvas(60, 60);
        c.translate(20, 0);
        c.rotate(Math.PI / 2);
        c.fillRect(-2, -2, 4, 4);
        PixelAssert.assertOpaque(c.getImage(), 20, 0);
        PixelAssert.assertTransparent(c.getImage(), 0, 20);
    }

    @Test
    void setTransformIsAbsoluteNotRelative() {
        // Canvas 语义：setTransform 覆盖当前矩阵
        Java2DCanvas c = canvas(60, 60);
        c.translate(50, 50);
        c.setTransform(1, 0, 0, 1, 10, 10);
        c.fillRect(0, 0, 4, 4);
        PixelAssert.assertOpaque(c.getImage(), 12, 12);
        PixelAssert.assertTransparent(c.getImage(), 52, 52);
    }

    @Test
    void globalAlphaBlendsOntoTransparentCanvas() {
        // Canvas 语义：globalAlpha=0.5 的一次覆盖绘制在透明底上得到半透明像素
        Java2DCanvas c = canvas(40, 40);
        c.setGlobalAlpha(0.5);
        c.setFillStyleCss("#ff0000");
        c.fillRect(10, 10, 20, 20);
        PixelAssert.assertAlphaNear(c.getImage(), 20, 20, 127, 6);
        PixelAssert.assertRgbNear(c.getImage(), 20, 20, 255, 0, 0, 8);
    }

    @Test
    void clearRectBypassesGlobalAlphaAndResetsToTransparent() {
        // D1：clearRect 恢复全透明，不受 globalAlpha 影响（受 clip/transform 影响不在此测）
        Java2DCanvas c = canvas(40, 40);
        c.setFillStyleCss("#ffffff");
        c.fillRect(0, 0, 40, 40);
        PixelAssert.assertOpaque(c.getImage(), 20, 20);
        c.setGlobalAlpha(0.5);
        c.clearRect(10, 10, 20, 20);
        PixelAssert.assertAlphaNear(c.getImage(), 20, 20, 0, 0);
        // 清除区域外不受影响
        PixelAssert.assertOpaque(c.getImage(), 5, 5);
    }

    @Test
    void getImageReturnsIndependentCopy() {
        // 语义：getImage 返回副本，后续绘制不影响已取出的图像
        Java2DCanvas c = canvas(40, 40);
        BufferedImage first = c.getImage();
        c.setFillStyleCss("#ff0000");
        c.fillRect(0, 0, 40, 40);
        PixelAssert.assertTransparent(first, 20, 20);
        PixelAssert.assertOpaque(c.getImage(), 20, 20);
        assertNotEquals(first, c.getImage());
    }

    @Test
    void sameContextInstanceOnRepeatedCalls() {
        Java2DCanvas c = canvas(20, 20);
        assertEquals(c.getContext2D(), c.getContext2D());
    }

    // ----- D2 抛错矩阵 -----

    @Test
    void nonFiniteCoordinatesThrow() {
        Java2DCanvas c = canvas(20, 20);
        assertThrows(NopCanvas2DException.class, () -> c.translate(Double.NaN, 0));
        assertThrows(NopCanvas2DException.class, () -> c.fillRect(0, 0, Double.POSITIVE_INFINITY, 5));
        assertThrows(NopCanvas2DException.class, () -> c.clearRect(Double.NaN, 0, 5, 5));
        assertThrows(NopCanvas2DException.class, () -> c.setTransform(Double.NaN, 0, 0, 1, 0, 0));
    }

    @Test
    void globalAlphaValidationThrows() {
        Java2DCanvas c = canvas(20, 20);
        assertThrows(NopCanvas2DException.class, () -> c.setGlobalAlpha(1.5));
        assertThrows(NopCanvas2DException.class, () -> c.setGlobalAlpha(-0.1));
        assertThrows(NopCanvas2DException.class, () -> c.setGlobalAlpha(Double.NaN));
        assertThrows(NopCanvas2DException.class, () -> c.setGlobalAlpha(Double.POSITIVE_INFINITY));
    }

    @Test
    void strokeStyleValidationThrows() {
        Java2DCanvas c = canvas(20, 20);
        assertThrows(NopCanvas2DException.class, () -> c.setLineWidth(0));
        assertThrows(NopCanvas2DException.class, () -> c.setLineWidth(-1));
        assertThrows(NopCanvas2DException.class, () -> c.setLineWidth(Double.NaN));
        assertThrows(NopCanvas2DException.class, () -> c.setLineCap("diagonal"));
        assertThrows(NopCanvas2DException.class, () -> c.setLineJoin("none"));
        assertThrows(NopCanvas2DException.class, () -> c.setMiterLimit(0));
        assertThrows(NopCanvas2DException.class, () -> c.setMiterLimit(Double.NaN));
    }

    @Test
    void lineDashValidationThrows() {
        Java2DCanvas c = canvas(20, 20);
        assertThrows(NopCanvas2DException.class, () -> c.setLineDash(-1));
        assertThrows(NopCanvas2DException.class, () -> c.setLineDash(4, -2));
        assertThrows(NopCanvas2DException.class, () -> c.setLineDash(Double.NaN));
        assertThrows(NopCanvas2DException.class, () -> c.setLineDashOffset(Double.NaN));
    }

    @Test
    void invalidColorThrows() {
        Java2DCanvas c = canvas(20, 20);
        assertThrows(NopCanvas2DException.class, () -> c.setFillStyleCss("not-a-color"));
        assertThrows(NopCanvas2DException.class, () -> c.setStrokeStyleCss("#12345"));
    }

    @Test
    void invalidCanvasSizeThrows() {
        assertThrows(NopCanvas2DException.class, () -> canvas(0, 10));
        assertThrows(NopCanvas2DException.class, () -> canvas(10, -1));
    }
}
