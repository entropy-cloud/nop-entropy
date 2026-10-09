package io.nop.canvas2d;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * closure audit 补充覆盖（plan 2359 Major-1 / Minor-3）：scale、transform(6参)、bezierCurveTo、
 * strokeRect、closePath 空路径 no-op、setLineDash 奇数长度偶数化、scale 零因子抛错。
 */
class Java2DCanvasCoverageGapTest {

    @Test
    void scaleScalesDrawingCoordinates() {
        // Canvas 语义：scale 放大用户坐标——fillRect(5,5,5,5) 经 scale(2,2) 落在 (10,10)-(20,20)
        Java2DCanvas c = new Java2DCanvas(40, 40);
        c.scale(2, 2);
        c.fillRect(5, 5, 5, 5);
        PixelAssert.assertOpaque(c.getImage(), 15, 15);
        PixelAssert.assertTransparent(c.getImage(), 30, 30);
    }

    @Test
    void transformAppliesTranslationMatrix() {
        // Canvas 语义：transform(1,0,0,1,tx,ty) 等价平移——与 setTransform 的绝对语义区分
        Java2DCanvas c = new Java2DCanvas(40, 40);
        c.transform(1, 0, 0, 1, 20, 10);
        c.fillRect(0, 0, 5, 5);
        PixelAssert.assertOpaque(c.getImage(), 22, 12);
        PixelAssert.assertTransparent(c.getImage(), 5, 5);
    }

    @Test
    void bezierCurveToDrawsSymmetricArc() {
        // Canvas 语义：三次贝塞尔 (10,50) cp(10,10) cp(50,10) (50,50)，t=0.5 处曲线点为 (30,20)；
        // 控制点 (30,10) 不在曲线上（钉住贝塞尔插值行为）
        Java2DCanvas c = new Java2DCanvas(60, 60);
        c.beginPath();
        c.moveTo(10, 50);
        c.bezierCurveTo(10, 10, 50, 10, 50, 50);
        c.setLineWidth(4);
        c.stroke();
        PixelAssert.assertOpaque(c.getImage(), 30, 20);
        PixelAssert.assertTransparent(c.getImage(), 30, 10);
    }

    @Test
    void strokeRectStrokesBorderWithoutFillingInterior() {
        // Canvas 语义：strokeRect 只描边；lineWidth=2 → 边框带 [14,16]
        Java2DCanvas c = new Java2DCanvas(40, 40);
        c.setLineWidth(2);
        c.strokeRect(15, 15, 20, 20);
        PixelAssert.assertOpaque(c.getImage(), 15, 25);
        PixelAssert.assertTransparent(c.getImage(), 25, 25);
    }

    @Test
    void closePathOnEmptyPathIsNoOp() {
        // D2：closePath 在空路径上为 no-op（不抛、不产生段）；随后正常绘制不受影响
        Java2DCanvas c = new Java2DCanvas(40, 40);
        c.beginPath();
        assertDoesNotThrow(c::closePath);
        c.fill();
        c.moveTo(10, 10);
        c.lineTo(30, 10);
        c.lineTo(30, 30);
        c.closePath();
        c.fill();
        // (27,24) 距斜边 y=x 约 2.1px、距右边 3px，避开 AA 边缘带
        PixelAssert.assertOpaque(c.getImage(), 27, 24);
    }

    @Test
    void setLineDashOddLengthIsEvenized() {
        // Canvas 语义：奇数长度 dash 列表重复末段偶数化——[6,4,2] → [6,4,2,6] 循环
        // 若未偶数化（[6,4,2] 直接循环），(20,10) 与 (26,10) 的实空状态与本断言相反
        Java2DCanvas c = new Java2DCanvas(60, 20);
        c.beginPath();
        c.moveTo(5, 10);
        c.lineTo(55, 10);
        c.setLineWidth(2);
        c.setLineDash(6, 4, 2);
        c.stroke();
        // 偶数化后段序列：实[5-11] 空[11-15] 实[15-17] 空[17-23] 实[23-29]
        PixelAssert.assertOpaque(c.getImage(), 8, 10);
        PixelAssert.assertTransparent(c.getImage(), 13, 10);
        PixelAssert.assertTransparent(c.getImage(), 20, 10);
        PixelAssert.assertOpaque(c.getImage(), 26, 10);
    }

    @Test
    void scaleZeroFactorThrows() {
        // D2（audit Minor-5 补记行）：零因子退化矩阵抛错
        Java2DCanvas c = new Java2DCanvas(20, 20);
        assertThrows(NopCanvas2DException.class, () -> c.scale(0, 1));
        assertThrows(NopCanvas2DException.class, () -> c.scale(1, 0));
    }
}
