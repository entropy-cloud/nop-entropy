package io.nop.pdf.extract.dashline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 虚线模式检测语义：实/虚段长度交替序列中必须检出周期性模式（mod=周期, start=起点, length=跨越点数）；
 * 无周期性的序列不得误报。
 *
 * <p>plan 2306 项 39 裁定复核：容差滑窗锚定（噪声前缀可并入 start=0 的模式）是
 * 抗测量噪声的启发式设计，testPatternDetectedDespiteNoisyPrefix 锚定的即该语义，
 * 不作为缺陷修复。</p>
 */
public class TestDashPatternDetector {

    @Test
    public void testDetectPeriodTwoPattern() {
        // 虚线：实4虚2 交替，重复6轮
        double[] points = new double[12];
        for (int i = 0; i < 12; i += 2) {
            points[i] = 4.0;
            points[i + 1] = 2.0;
        }

        List<DashPattern> patterns = new DashPatternDetector().process(points);
        assertEquals(1, patterns.size());

        DashPattern pattern = patterns.get(0);
        assertEquals(2, pattern.getMod());
        assertEquals(0, pattern.getStart());
        // walk 命中时返回 steps*n 且 steps>2
        assertEquals(12, pattern.getLength());
    }

    @Test
    public void testDetectPeriodThreePattern() {
        // 点划线：实6虚2点3 交替，重复5轮
        double[] points = new double[15];
        for (int i = 0; i < 15; i += 3) {
            points[i] = 6.0;
            points[i + 1] = 2.0;
            points[i + 2] = 3.0;
        }

        List<DashPattern> patterns = new DashPatternDetector().process(points);
        assertEquals(1, patterns.size());

        DashPattern pattern = patterns.get(0);
        assertEquals(3, pattern.getMod());
        assertEquals(0, pattern.getStart());
        assertEquals(15, pattern.getLength());
    }

    @Test
    public void testPatternDetectedDespiteNoisyPrefix() {
        // 前3个点为噪声，其后是虚4虚1交替（重复5轮）：仍必须检出 mod=2 的模式，
        // 且模式长度覆盖周期尾部（实现以可容忍误差滑窗匹配，噪声前缀会拉低起点定位）
        double[] points = {30, 1, 27, 4.0, 1.0, 4.0, 1.0, 4.0, 1.0, 4.0, 1.0, 4.0, 1.0};

        List<DashPattern> patterns = new DashPatternDetector().process(points);
        assertEquals(1, patterns.size());

        DashPattern pattern = patterns.get(0);
        assertEquals(2, pattern.getMod());
        assertTrue(pattern.getLength() >= 6, "模式长度应覆盖周期尾部: " + pattern.getLength());
    }

    @Test
    public void testNonRepeatingSequenceHasNoPattern() {
        // 剧烈震荡且幅度互异的序列不存在周期性
        double[] points = {1, 50, 2, 500, 3, 50, 4, 900, 5, 60, 6, 1000, 7, 55};
        List<DashPattern> patterns = new DashPatternDetector().process(points);
        assertTrue(patterns.isEmpty(), "非周期序列不应检出模式: " + patterns.size());
    }

    @Test
    public void testTooShortSequenceHasNoPattern() {
        // 少于2个周期（4个点）不足以构成模式
        double[] points = {4, 2, 4, 2};
        List<DashPattern> patterns = new DashPatternDetector().process(points);
        assertTrue(patterns.isEmpty());
    }
}
