package io.nop.svg.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 修复 SEG_ARCTO 分支缺 break：含弧段的 path 调用 toString() 必抛 RuntimeException，
 * 且 A 指令多输出一个无效的第8参数
 */
public class TestSVGPathToSVGString {

    @Test
    public void testArcSegmentToString() {
        SVGPath path = new SVGPath();
        path.moveTo(0, 0);
        path.arcTo(5, 5, 0, true, true, 10, 10);

        // 修复前：case SEG_ARCTO 贯穿到 default 抛 "Unrecognised segment type 4321"
        String svg = path.toSVGString();
        assertTrue(svg.startsWith("M"), svg);
        assertTrue(svg.contains("A"), "arc segment should be rendered: " + svg);

        // A 指令恰好7个参数：A rx ry angle laf sf x y
        String arcPart = svg.substring(svg.indexOf('A') + 1);
        assertTrue(arcPart.trim().split("[\\s,]+").length == 7, "A cmd + 7 params: " + svg);
    }

    @Test
    public void testLineAndQuadToString() {
        SVGPath path = new SVGPath();
        path.moveTo(1, 2);
        path.lineTo(3, 4);
        String svg = path.toSVGString();
        assertTrue(svg.contains("M") && svg.contains("L"), svg);
        assertFalse(svg.contains("Unrecognised"), svg);
    }

    /**
     * transform 必须只变换坐标点：修复前对 values 整体做点变换，
     * 弧段的 rx/ry/角度/标志位被当成坐标损坏
     */
    @Test
    public void testTransformPreservesArcParameters() {
        SVGPath path = new SVGPath();
        path.moveTo(0, 0);
        path.arcTo(5, 5, 0, true, true, 10, 10);
        path.transform(java.awt.geom.AffineTransform.getTranslateInstance(10, 10));

        String svg = path.toSVGString();
        // 弧段参数 rx ry angle laf sf 保持不变，仅终点平移
        assertTrue(svg.contains("A5.0 5.0 0.0 1.0 1.0 20.0 20.0"), svg);
    }

    @Test
    public void testTransformMovesLines() {
        SVGPath path = new SVGPath();
        path.moveTo(0, 0);
        path.lineTo(2, 2);
        path.transform(java.awt.geom.AffineTransform.getTranslateInstance(1, 1));
        String svg = path.toSVGString();
        assertTrue(svg.contains("M1.0,1.0"), svg);
        assertTrue(svg.contains("L3.0,3.0"), svg);
    }

    @Test
    public void testTransformOnEmptyPathDoesNotThrow() {
        SVGPath path = new SVGPath();
        // 修复前 values==null 直接NPE
        path.transform(java.awt.geom.AffineTransform.getTranslateInstance(1, 1));
        assertEquals(0, path.toSVGString().length());
    }
}
