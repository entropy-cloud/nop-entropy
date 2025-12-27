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
}