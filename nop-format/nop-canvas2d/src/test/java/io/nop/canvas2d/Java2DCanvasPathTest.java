package io.nop.canvas2d;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI1 Phase 2：路径子系统像素语义。断言遵守 D4 纪律（布尔覆盖、采样点距几何边界 ≥2px、无整图计数）。
 */
class Java2DCanvasPathTest {

    private static Java2DCanvas canvas(int w, int h) {
        return new Java2DCanvas(w, h);
    }

    @Test
    void rectSubpathFillAndStroke() {
        // Canvas 语义：rect() 追加闭合子路径；fill 非零缠绕
        Java2DCanvas c = canvas(60, 60);
        c.beginPath();
        c.rect(15, 15, 30, 30);
        c.fill();
        PixelAssert.assertOpaque(c.getImage(), 30, 30);
        PixelAssert.assertTransparent(c.getImage(), 8, 8);
    }

    @Test
    void nonzeroWindingKeepsSameDirectionOverlapFilled() {
        // 钉住 fill 规则 = 非零缠绕：同向嵌套子路径重叠区 winding=2 → 填充（evenodd 规则会成洞）
        Java2DCanvas c = canvas(60, 60);
        c.beginPath();
        c.rect(10, 10, 30, 30);
        c.rect(20, 20, 15, 15);
        c.fill();
        PixelAssert.assertOpaque(c.getImage(), 15, 15);
        PixelAssert.assertOpaque(c.getImage(), 27, 27);
    }

    @Test
    void strokeFollowsLineWidthAndDash() {
        // Canvas 语义：stroke 沿路径按 lineWidth 展宽，dash 交替
        Java2DCanvas c = canvas(60, 20);
        c.beginPath();
        c.moveTo(5, 10);
        c.lineTo(55, 10);
        c.setLineWidth(2);
        c.setLineDash(6, 4);
        c.stroke();
        // dash 相位自路径起点起：[5-11] 实 [11-15] 空 [15-21] 实
        PixelAssert.assertOpaque(c.getImage(), 8, 10);
        PixelAssert.assertTransparent(c.getImage(), 13, 10);
        PixelAssert.assertOpaque(c.getImage(), 18, 10);
    }

    @Test
    void lineDashOffsetShiftsPhase() {
        // 钉住 lineDashOffset 相位语义：同一路径 offset=0 与 offset=3 在 (12,10) 处覆盖状态翻转
        Java2DCanvas offsetZero = canvas(60, 20);
        offsetZero.beginPath();
        offsetZero.moveTo(5, 10);
        offsetZero.lineTo(55, 10);
        offsetZero.setLineWidth(2);
        offsetZero.setLineDash(6, 4);
        offsetZero.stroke();
        PixelAssert.assertTransparent(offsetZero.getImage(), 13, 10);

        Java2DCanvas offsetThree = canvas(60, 20);
        offsetThree.beginPath();
        offsetThree.moveTo(5, 10);
        offsetThree.lineTo(55, 10);
        offsetThree.setLineWidth(2);
        offsetThree.setLineDash(6, 4);
        offsetThree.setLineDashOffset(3);
        offsetThree.stroke();
        // offset=3 → dash 段变为 [8-14],[18-24]...，(13,10) 落入实段
        PixelAssert.assertOpaque(offsetThree.getImage(), 13, 10);
    }

    @Test
    void lineCapButtVersusRound() {
        // Canvas 语义：butt 端帽不越过端点；round 端帽延伸 lineWidth/2
        Java2DCanvas butt = canvas(60, 20);
        butt.beginPath();
        butt.moveTo(20, 10);
        butt.lineTo(40, 10);
        butt.setLineWidth(4);
        butt.setLineCap("butt");
        butt.stroke();
        PixelAssert.assertTransparent(butt.getImage(), 19, 10);
        PixelAssert.assertOpaque(butt.getImage(), 30, 10);

        Java2DCanvas round = canvas(60, 20);
        round.beginPath();
        round.moveTo(20, 10);
        round.lineTo(40, 10);
        round.setLineWidth(4);
        round.setLineCap("round");
        round.stroke();
        PixelAssert.assertOpaque(round.getImage(), 19, 10);
    }

    @Test
    void miterLimitConvertsSharpCornerToBevel() {
        // 钉住 miterLimit：90° 直角拐弯，miter 比 = 1/sin(45°) ≈ 1.414，lw=24
        // miterLimit=2 → miter（尖端越过角点 0.707*lw ≈ 17px）；miterLimit=1.2 → bevel（斜切线在角外 lw/2=12px 处）
        // 观测点 (35,16)：角点 (35,30) 外侧 14-15px——miter 楔形在该行半宽 ≈2-3px 覆盖，bevel 距弦线 2-3px 未覆盖
        Java2DCanvas miter = canvas(70, 70);
        miter.beginPath();
        miter.moveTo(14, 51);
        miter.lineTo(35, 30);
        miter.lineTo(56, 51);
        miter.setLineWidth(24);
        miter.setMiterLimit(2);
        miter.stroke();
        PixelAssert.assertOpaque(miter.getImage(), 35, 16);

        Java2DCanvas bevel = canvas(70, 70);
        bevel.beginPath();
        bevel.moveTo(14, 51);
        bevel.lineTo(35, 30);
        bevel.lineTo(56, 51);
        bevel.setLineWidth(24);
        bevel.setMiterLimit(1.2);
        bevel.stroke();
        PixelAssert.assertTransparent(bevel.getImage(), 35, 16);
        PixelAssert.assertOpaque(bevel.getImage(), 35, 29);
    }

    @Test
    void quarterArcClockwiseAndAnticlockwiseCoverDifferentSectors() {
        // 钉住 arc 方向语义（y 向下坐标系）：cw 0→π/2 覆盖右下四分之一扇区；ccw 同参数覆盖其余 270°
        Java2DCanvas cw = canvas(40, 40);
        cw.beginPath();
        cw.moveTo(30, 30);
        cw.arc(30, 30, 10, 0, Math.PI / 2, false);
        cw.fill();
        PixelAssert.assertOpaque(cw.getImage(), 35, 35);
        PixelAssert.assertTransparent(cw.getImage(), 35, 25);

        Java2DCanvas ccw = canvas(40, 40);
        ccw.beginPath();
        ccw.moveTo(30, 30);
        ccw.arc(30, 30, 10, 0, Math.PI / 2, true);
        ccw.fill();
        PixelAssert.assertTransparent(ccw.getImage(), 35, 35);
        PixelAssert.assertOpaque(ccw.getImage(), 35, 25);
    }

    @Test
    void arcDegenerateCasesFollowSpec() {
        // B1 退化角矩阵：
        // (1) sweep==0（start==end）为零长弧——只贡献连接线，不画弧
        Java2DCanvas zero = canvas(60, 60);
        zero.beginPath();
        zero.moveTo(10, 30);
        zero.arc(30, 30, 10, 0, 0, false);
        zero.lineTo(50, 30);
        zero.setLineWidth(2);
        zero.stroke();
        PixelAssert.assertOpaque(zero.getImage(), 20, 30);
        PixelAssert.assertOpaque(zero.getImage(), 45, 30);
        // 弧本体未画：弧顶 (30,20) 附近应透明
        PixelAssert.assertTransparent(zero.getImage(), 30, 20);

        // (2) |sweep| ≥ 2π 同向 → 整圆
        Java2DCanvas full = canvas(40, 40);
        full.beginPath();
        full.arc(20, 20, 10, 0, 2 * Math.PI, false);
        full.fill();
        PixelAssert.assertOpaque(full.getImage(), 20, 20);
        PixelAssert.assertTransparent(full.getImage(), 20, 6);

        // (3) anticlockwise 归一化取「符号正确且幅值最小」：0→π/2 ccw 幅值为 -3π/2 而非 +π/2（已由方向测试覆盖），
        // 此处补 ccw 大幅值回卷：0→2π+π/2 ccw 应等价于 0→π/2 ccw（幅值 -3π/2）
        Java2DCanvas wrap = canvas(40, 40);
        wrap.beginPath();
        wrap.moveTo(30, 30);
        wrap.arc(30, 30, 10, 0, Math.PI / 2 + 2 * Math.PI, true);
        wrap.fill();
        PixelAssert.assertTransparent(wrap.getImage(), 35, 35);
        PixelAssert.assertOpaque(wrap.getImage(), 35, 25);
    }

    @Test
    void arcNegativeRadiusThrows() {
        Java2DCanvas c = canvas(40, 40);
        c.beginPath();
        assertThrows(NopCanvas2DException.class, () -> c.arc(20, 20, -1, 0, Math.PI, false));
        assertThrows(NopCanvas2DException.class, () -> c.ellipse(20, 20, -1, 5, 0, 0, Math.PI, false));
        assertThrows(NopCanvas2DException.class, () -> c.arcTo(10, 10, 30, 10, -2));
        assertThrows(NopCanvas2DException.class, () -> c.arc(20, 20, 5, Double.NaN, Math.PI, false));
    }

    @Test
    void arcToRoundsCorner() {
        // 钉住 arcTo 切线圆弧语义：拐角 (40,10)，入线 (10,10)→(40,10)，出线 (40,10)→(40,40)，r=5
        // 切点 (35,10)/(40,15)，弧心 (35,15)，弧中点 (38.54,11.46) 凸向拐角
        Java2DCanvas c = canvas(60, 60);
        c.beginPath();
        c.moveTo(10, 10);
        c.arcTo(40, 10, 40, 40, 5);
        c.setLineWidth(2);
        c.stroke();
        PixelAssert.assertOpaque(c.getImage(), 38, 12);
        // 弧内侧（圆心方向）距弧带 ≥2px 处透明
        PixelAssert.assertTransparent(c.getImage(), 37, 15);
        // 拐角尖端被圆弧替代：拐角点附近透明
        PixelAssert.assertTransparent(c.getImage(), 40, 10);
    }

    @Test
    void arcToDegenerateCasesReduceToLine() {
        // Canvas 语义：radius==0 或端点重合或共线同向 → lineTo 拐点
        Java2DCanvas zero = canvas(60, 20);
        zero.beginPath();
        zero.moveTo(10, 10);
        zero.arcTo(40, 10, 40, 20, 0);
        zero.setLineWidth(2);
        zero.stroke();
        PixelAssert.assertOpaque(zero.getImage(), 30, 10);

        Java2DCanvas collinear = canvas(60, 20);
        collinear.beginPath();
        collinear.moveTo(10, 10);
        collinear.arcTo(40, 10, 70, 10, 5);
        collinear.setLineWidth(2);
        collinear.stroke();
        PixelAssert.assertOpaque(collinear.getImage(), 30, 10);
        // 线终点在 (40,10)：终点外 10px 处透明，钉住 lineTo 终点语义
        PixelAssert.assertTransparent(collinear.getImage(), 50, 10);
    }

    @Test
    void ellipseRotationRotatesMajorAxis() {
        // 钉住 ellipse rotation 语义：rx=15 沿 45° 方向延展
        Java2DCanvas c = canvas(60, 60);
        c.beginPath();
        c.ellipse(30, 30, 15, 5, Math.PI / 4, 0, 2 * Math.PI, false);
        c.fill();
        PixelAssert.assertOpaque(c.getImage(), 38, 38);
        PixelAssert.assertTransparent(c.getImage(), 45, 30);
    }

    @Test
    void closePathClosesTriangleForFill() {
        // Canvas 语义：closePath 回连子路径起点，fill 填充闭合区
        Java2DCanvas c = canvas(60, 60);
        c.beginPath();
        c.moveTo(15, 15);
        c.lineTo(35, 15);
        c.lineTo(35, 35);
        c.closePath();
        c.fill();
        PixelAssert.assertOpaque(c.getImage(), 30, 25);
        PixelAssert.assertTransparent(c.getImage(), 18, 30);
    }

    @Test
    void quadraticCurveImplicitMoveUsesControlPoint() {
        // D2：空路径上 quadraticCurveTo 隐式 moveTo 第一控制点（区别于 spike 的 (0,0) 起点）
        Java2DCanvas c = canvas(60, 60);
        c.beginPath();
        c.quadraticCurveTo(25, 5, 40, 40);
        c.setLineWidth(2);
        c.stroke();
        // 曲线起点 = 第一控制点 (25,5)，端点邻域应被描边覆盖
        PixelAssert.assertOpaque(c.getImage(), 25, 5);
    }

    @Test
    void quadraticCurveFillRegion() {
        Java2DCanvas c = canvas(60, 60);
        c.beginPath();
        c.moveTo(10, 40);
        c.quadraticCurveTo(25, 5, 40, 40);
        c.closePath();
        c.fill();
        PixelAssert.assertOpaque(c.getImage(), 25, 35);
        PixelAssert.assertTransparent(c.getImage(), 25, 15);
    }

    @Test
    void fillBeforeBeginPathThrowsButEmptyPathFillIsNoOp() {
        // D2：从未 beginPath 的绘制入口抛错；beginPath 后 0 段路径 fill/stroke 为 no-op（zrender 会 fill 空形状）
        Java2DCanvas c = canvas(20, 20);
        assertThrows(NopCanvas2DException.class, c::fill);
        assertThrows(NopCanvas2DException.class, c::stroke);
        assertThrows(NopCanvas2DException.class, c::clip);

        Java2DCanvas empty = canvas(20, 20);
        empty.beginPath();
        assertDoesNotThrow(empty::fill);
        assertDoesNotThrow(empty::stroke);
    }

    @Test
    void pathConstructionOpsAutoBegin() {
        // D2：路径构建操作隐式开始路径（Canvas 语义：当前路径恒存在）
        Java2DCanvas c = canvas(40, 40);
        c.lineTo(30, 10);
        c.lineTo(30, 30);
        c.lineTo(10, 30);
        c.closePath();
        c.fill();
        PixelAssert.assertOpaque(c.getImage(), 25, 25);
    }

    @Test
    void fillDoesNotConsumePath() {
        // D3：fill 后路径保留，可继续 stroke 同一路径
        Java2DCanvas c = canvas(60, 60);
        c.beginPath();
        c.rect(20, 20, 20, 20);
        c.setFillStyleCss("#ff0000");
        c.fill();
        c.setLineWidth(4);
        c.setStrokeStyleCss("#0000ff");
        c.stroke();
        PixelAssert.assertOpaque(c.getImage(), 30, 30);
        // 描边带在矩形边框上（边界 20，线宽 4 → 带 [18,22]，采样 21 距带边 1px 且在带内）
        PixelAssert.assertRgbNear(c.getImage(), 21, 30, 0, 0, 255, 30);
    }

    @Test
    void clipRestrictsPaintingAndRestoreRecoversClip() {
        // Canvas 语义：clip 与既有 clip 取交集；save/restore 恢复 clip
        Java2DCanvas c = canvas(60, 60);
        c.beginPath();
        c.rect(20, 20, 20, 20);
        c.save();
        c.clip();
        c.setFillStyleCss("#ff0000");
        c.fillRect(10, 10, 40, 40);
        PixelAssert.assertOpaque(c.getImage(), 30, 30);
        PixelAssert.assertTransparent(c.getImage(), 15, 15);
        c.restore();
        // restore 后 clip 解除：全画布可绘制
        c.fillRect(10, 10, 40, 40);
        PixelAssert.assertOpaque(c.getImage(), 15, 15);
    }

    @Test
    void getContext2DBindsSameCanvas() {
        Java2DCanvas c = canvas(30, 30);
        ICanvas2D element = c;
        element.getContext2D().fillRect(5, 5, 10, 10);
        BufferedImage img = element.getImage();
        PixelAssert.assertOpaque(img, 10, 10);
        assertTrue(element.getWidth() == 30 && element.getHeight() == 30);
    }
}
