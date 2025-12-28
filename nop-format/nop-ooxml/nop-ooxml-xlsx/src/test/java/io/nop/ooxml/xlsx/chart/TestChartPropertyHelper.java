/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.chart;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ChartPropertyHelper测试类
 */
public class TestChartPropertyHelper {

    @Test
    public void testGenerateAxisId() {
        // 测试生成单个轴ID
        String axisId = ChartPropertyHelper.generateAxisId();
        
        assertNotNull(axisId);
        assertFalse(axisId.isEmpty());
        
        // 验证ID是数字
        assertTrue(axisId.matches("\\d+"));
        
        // 验证ID长度在合理范围内（8-9位）
        int idValue = Integer.parseInt(axisId);
        assertTrue(idValue >= 10_000_000);
        assertTrue(idValue < 1_000_000_000);
    }

    @Test
    public void testGenerateAxisIdPair() {
        // 测试生成轴ID对
        String[] axisIds = ChartPropertyHelper.generateAxisIdPair();
        
        assertNotNull(axisIds);
        assertEquals(2, axisIds.length);
        
        // 验证两个ID都不为空
        assertNotNull(axisIds[0]);
        assertNotNull(axisIds[1]);
        assertFalse(axisIds[0].isEmpty());
        assertFalse(axisIds[1].isEmpty());
        
        // 验证两个ID不相同
        assertNotEquals(axisIds[0], axisIds[1]);
        
        // 验证两个ID都是数字且在合理范围内
        for (String axisId : axisIds) {
            assertTrue(axisId.matches("\\d+"));
            int idValue = Integer.parseInt(axisId);
            assertTrue(idValue >= 10_000_000);
            assertTrue(idValue < 1_000_000_000);
        }
    }

    @Test
    public void testAxisIdUniqueness() {
        // 测试多次生成的ID具有唯一性
        String id1 = ChartPropertyHelper.generateAxisId();
        String id2 = ChartPropertyHelper.generateAxisId();
        String id3 = ChartPropertyHelper.generateAxisId();
        
        // 虽然理论上可能相同，但概率极低
        assertNotEquals(id1, id2);
        assertNotEquals(id2, id3);
        assertNotEquals(id1, id3);
    }

    @Test
    public void testAngleConversion() {
        // 测试基本角度转换
        assertEquals(0, ChartPropertyHelper.degreesToOoxmlAngle(0.0));
        assertEquals(60000, ChartPropertyHelper.degreesToOoxmlAngle(1.0));
        assertEquals(5400000, ChartPropertyHelper.degreesToOoxmlAngle(90.0));
        assertEquals(10800000, ChartPropertyHelper.degreesToOoxmlAngle(180.0));
        assertEquals(21600000, ChartPropertyHelper.degreesToOoxmlAngle(360.0));
        
        // 测试反向转换
        assertEquals(0.0, ChartPropertyHelper.ooxmlAngleToDegrees(0), 0.001);
        assertEquals(1.0, ChartPropertyHelper.ooxmlAngleToDegrees(60000), 0.001);
        assertEquals(90.0, ChartPropertyHelper.ooxmlAngleToDegrees(5400000), 0.001);
        assertEquals(180.0, ChartPropertyHelper.ooxmlAngleToDegrees(10800000), 0.001);
        assertEquals(360.0, ChartPropertyHelper.ooxmlAngleToDegrees(21600000), 0.001);
    }

    @Test
    public void testAngleStringConversion() {
        // 测试字符串转换
        assertEquals("0", ChartPropertyHelper.degreesToOoxmlAngleString(0.0));
        assertEquals("60000", ChartPropertyHelper.degreesToOoxmlAngleString(1.0));
        assertEquals("5400000", ChartPropertyHelper.degreesToOoxmlAngleString(90.0));
        assertNull(ChartPropertyHelper.degreesToOoxmlAngleString(null));
        
        // 测试字符串解析
        assertEquals(0.0, ChartPropertyHelper.ooxmlAngleStringToDegrees("0"), 0.001);
        assertEquals(1.0, ChartPropertyHelper.ooxmlAngleStringToDegrees("60000"), 0.001);
        assertEquals(90.0, ChartPropertyHelper.ooxmlAngleStringToDegrees("5400000"), 0.001);
        assertNull(ChartPropertyHelper.ooxmlAngleStringToDegrees(null));
        assertNull(ChartPropertyHelper.ooxmlAngleStringToDegrees(""));
        assertNull(ChartPropertyHelper.ooxmlAngleStringToDegrees("invalid"));
    }

    @Test
    public void testAngleRoundTrip() {
        // 测试往返转换的精度
        double[] testAngles = {0.0, 1.0, 45.0, 90.0, 180.0, 270.0, 360.0, -90.0, 123.456};
        
        for (double originalAngle : testAngles) {
            long ooxmlAngle = ChartPropertyHelper.degreesToOoxmlAngle(originalAngle);
            double convertedBack = ChartPropertyHelper.ooxmlAngleToDegrees(ooxmlAngle);
            
            // 由于舍入误差，允许小的差异
            assertEquals(originalAngle, convertedBack, 0.001, 
                        "Round-trip conversion failed for angle: " + originalAngle);
        }
    }
}