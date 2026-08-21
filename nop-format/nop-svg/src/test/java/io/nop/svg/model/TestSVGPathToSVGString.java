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
        SVGPath path = SVGPath.parse("M0,0 A5,5 0 1,1 10,10");
        String svg = path.toSVGString();

        // 修复前：case SEG_ARCTO 贯穿到 default 抛 "Unrecognised segment type"
        assertTrue(svg.startsWith("M"));
        assertTrue(svg.contains("A"), "arc segment should be rendered: " + svg);

        // 输出可再次解析（往返不崩溃）
        SVGPath path2 = SVGPath.parse(svg);
        assertFalse(path2.toSVGString().isEmpty());
    }
}
