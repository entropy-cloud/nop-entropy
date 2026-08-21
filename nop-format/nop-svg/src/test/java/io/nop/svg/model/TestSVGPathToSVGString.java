package io.nop.svg.model;

import org.junit.jupiter.api.Test;

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
}
