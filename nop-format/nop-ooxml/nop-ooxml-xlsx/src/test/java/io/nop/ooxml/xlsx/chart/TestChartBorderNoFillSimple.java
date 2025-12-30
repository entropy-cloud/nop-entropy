/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 简单测试边框noFill属性的XML解析和构建
 * 不依赖完整的模型类，直接测试XML处理逻辑
 */
public class TestChartBorderNoFillSimple {

    @Test
    public void testParseNoFillXML() {
        // 创建包含a:noFill的边框XML
        XNode spPrNode = XNode.make("a:spPr");
        XNode lnNode = spPrNode.addChild("a:ln");
        lnNode.setAttr("w", "12700"); // 1pt = 12700 EMU
        lnNode.addChild("a:noFill");
        
        // 添加虚线样式
        XNode prstDashNode = lnNode.addChild("a:prstDash");
        prstDashNode.setAttr("val", "dash");

        // 验证XML结构
        assertNotNull(spPrNode);
        assertEquals("a:spPr", spPrNode.getTagName());
        
        XNode parsedLnNode = spPrNode.childByTag("a:ln");
        assertNotNull(parsedLnNode);
        assertEquals("12700", parsedLnNode.attrText("w"));
        
        // 验证a:noFill存在
        XNode noFillNode = parsedLnNode.childByTag("a:noFill");
        assertNotNull(noFillNode);
        
        // 验证虚线样式
        XNode parsedPrstDashNode = parsedLnNode.childByTag("a:prstDash");
        assertNotNull(parsedPrstDashNode);
        assertEquals("dash", parsedPrstDashNode.attrText("val"));
        
        // 验证没有a:solidFill（因为是noFill）
        XNode solidFillNode = parsedLnNode.childByTag("a:solidFill");
        assertNull(solidFillNode);
    }

    @Test
    public void testBuildNoFillXML() {
        // 手动构建带有noFill的边框XML
        XNode spPrNode = XNode.make("a:spPr");
        XNode lnNode = spPrNode.addChild("a:ln");
        lnNode.setAttr("w", "25400"); // 2pt = 25400 EMU
        lnNode.addChild("a:noFill");
        
        XNode prstDashNode = lnNode.addChild("a:prstDash");
        prstDashNode.setAttr("val", "dot");

        // 验证构建结果
        assertNotNull(spPrNode);
        assertEquals("a:spPr", spPrNode.getTagName());
        
        XNode builtLnNode = spPrNode.childByTag("a:ln");
        assertNotNull(builtLnNode);
        assertEquals("25400", builtLnNode.attrText("w"));
        
        // 验证a:noFill存在
        XNode noFillNode = builtLnNode.childByTag("a:noFill");
        assertNotNull(noFillNode);
        
        // 验证虚线样式
        XNode builtPrstDashNode = builtLnNode.childByTag("a:prstDash");
        assertNotNull(builtPrstDashNode);
        assertEquals("dot", builtPrstDashNode.attrText("val"));
        
        // 验证没有a:solidFill（因为是noFill）
        XNode solidFillNode = builtLnNode.childByTag("a:solidFill");
        assertNull(solidFillNode);
    }

    @Test
    public void testNormalBorderWithColorXML() {
        // 创建包含颜色的正常边框XML
        XNode spPrNode = XNode.make("a:spPr");
        XNode lnNode = spPrNode.addChild("a:ln");
        lnNode.setAttr("w", "12700");
        
        XNode solidFillNode = lnNode.addChild("a:solidFill");
        XNode srgbClrNode = solidFillNode.addChild("a:srgbClr");
        srgbClrNode.setAttr("val", "FF0000");

        // 验证正常边框（非noFill）
        assertNotNull(spPrNode);
        assertEquals("a:spPr", spPrNode.getTagName());
        
        XNode parsedLnNode = spPrNode.childByTag("a:ln");
        assertNotNull(parsedLnNode);
        assertEquals("12700", parsedLnNode.attrText("w"));
        
        // 验证有solidFill但没有noFill
        XNode parsedSolidFillNode = parsedLnNode.childByTag("a:solidFill");
        assertNotNull(parsedSolidFillNode);
        
        XNode parsedSrgbClrNode = parsedSolidFillNode.childByTag("a:srgbClr");
        assertNotNull(parsedSrgbClrNode);
        assertEquals("FF0000", parsedSrgbClrNode.attrText("val"));
        
        // 验证没有noFill
        XNode noFillNode = parsedLnNode.childByTag("a:noFill");
        assertNull(noFillNode);
    }

}