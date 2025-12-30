/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.model.ChartBorderModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import io.nop.excel.chart.constants.ChartLineStyle;
import io.nop.excel.chart.util.ChartStyleHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试边框noFill属性的解析和构建
 */
public class TestChartBorderNoFill {

    @Test
    public void testParseNoFillBorder() {
        // 创建包含a:noFill的边框XML
        XNode spPrNode = XNode.make("a:spPr");
        XNode lnNode = spPrNode.addChild("a:ln");
        lnNode.setAttr("w", "12700"); // 1pt = 12700 EMU
        lnNode.addChild("a:noFill");
        
        // 添加虚线样式
        XNode prstDashNode = lnNode.addChild("a:prstDash");
        prstDashNode.setAttr("val", "dash");

        // 解析
        ChartShapeStyleModel style = ChartShapeStyleParser.INSTANCE.parseShapeStyle(
            spPrNode, new DefaultChartStyleProvider());

        // 验证解析结果
        assertNotNull(style);
        assertTrue(style.hasBorder());
        
        ChartBorderModel border = style.getBorder();
        assertNotNull(border);
        assertTrue(border.getNoFill());
        assertEquals(1.0, border.getWidth(), 0.01);
        assertEquals(ChartLineStyle.DASH, border.getStyle());
        
        // 验证便利方法
        assertTrue(style.isBorderNoFill());
        assertTrue(ChartStyleHelper.isBorderNoFill(style));
    }

    @Test
    public void testBuildNoFillBorder() {
        // 创建带有noFill的边框样式
        ChartShapeStyleModel style = new ChartShapeStyleModel();
        ChartBorderModel border = new ChartBorderModel();
        border.setNoFill(true);
        border.setWidth(2.0);
        border.setStyle(ChartLineStyle.DOT);
        style.setBorder(border);

        // 构建XML
        XNode spPrNode = ChartShapeStyleBuilder.INSTANCE.buildShapeStyle(style);

        // 验证构建结果
        assertNotNull(spPrNode);
        assertEquals("a:spPr", spPrNode.getTagName());
        
        XNode lnNode = spPrNode.childByTag("a:ln");
        assertNotNull(lnNode);
        assertEquals("25400", lnNode.attrText("w")); // 2pt = 25400 EMU
        
        // 验证a:noFill存在
        XNode noFillNode = lnNode.childByTag("a:noFill");
        assertNotNull(noFillNode);
        
        // 验证虚线样式
        XNode prstDashNode = lnNode.childByTag("a:prstDash");
        assertNotNull(prstDashNode);
        assertEquals("dot", prstDashNode.attrText("val"));
        
        // 验证没有a:solidFill（因为是noFill）
        XNode solidFillNode = lnNode.childByTag("a:solidFill");
        assertNull(solidFillNode);
    }

    @Test
    public void testRoundTripNoFillBorder() {
        // 原始样式
        ChartShapeStyleModel originalStyle = ChartStyleHelper.createNoFillBorderStyle(1.5, ChartLineStyle.DASH_DOT);
        
        // 构建XML
        XNode spPrNode = ChartShapeStyleBuilder.INSTANCE.buildShapeStyle(originalStyle);
        assertNotNull(spPrNode);
        
        // 解析回来
        ChartShapeStyleModel parsedStyle = ChartShapeStyleParser.INSTANCE.parseShapeStyle(
            spPrNode, new DefaultChartStyleProvider());
        
        // 验证往返一致性
        assertNotNull(parsedStyle);
        assertTrue(parsedStyle.hasBorder());
        
        ChartBorderModel originalBorder = originalStyle.getBorder();
        ChartBorderModel parsedBorder = parsedStyle.getBorder();
        
        assertEquals(originalBorder.getNoFill(), parsedBorder.getNoFill());
        assertEquals(originalBorder.getWidth(), parsedBorder.getWidth(), 0.01);
        assertEquals(originalBorder.getStyle(), parsedBorder.getStyle());
        
        // 验证便利方法
        assertEquals(originalStyle.isBorderNoFill(), parsedStyle.isBorderNoFill());
    }

    @Test
    public void testNoFillBorderWithoutColor() {
        // 创建只有noFill和宽度的边框
        ChartShapeStyleModel style = new ChartShapeStyleModel();
        ChartBorderModel border = new ChartBorderModel();
        border.setNoFill(true);
        border.setWidth(3.0);
        style.setBorder(border);

        // 构建XML
        XNode spPrNode = ChartShapeStyleBuilder.INSTANCE.buildShapeStyle(style);
        assertNotNull(spPrNode);
        
        XNode lnNode = spPrNode.childByTag("a:ln");
        assertNotNull(lnNode);
        
        // 验证有noFill但没有solidFill
        assertNotNull(lnNode.childByTag("a:noFill"));
        assertNull(lnNode.childByTag("a:solidFill"));
        
        // 解析验证
        ChartShapeStyleModel parsedStyle = ChartShapeStyleParser.INSTANCE.parseShapeStyle(
            spPrNode, new DefaultChartStyleProvider());
        
        assertTrue(parsedStyle.isBorderNoFill());
        assertEquals(3.0, parsedStyle.getBorderWidth(), 0.01);
        assertNull(parsedStyle.getBorderColor());
    }

    @Test
    public void testNormalBorderWithColor() {
        // 创建包含颜色的正常边框XML
        XNode spPrNode = XNode.make("a:spPr");
        XNode lnNode = spPrNode.addChild("a:ln");
        lnNode.setAttr("w", "12700");
        
        XNode solidFillNode = lnNode.addChild("a:solidFill");
        XNode srgbClrNode = solidFillNode.addChild("a:srgbClr");
        srgbClrNode.setAttr("val", "FF0000");

        // 解析
        ChartShapeStyleModel style = ChartShapeStyleParser.INSTANCE.parseShapeStyle(
            spPrNode, new DefaultChartStyleProvider());

        // 验证正常边框（非noFill）
        assertNotNull(style);
        assertTrue(style.hasBorder());
        assertFalse(style.isBorderNoFill());
        assertEquals("#FF0000", style.getBorderColor());
        assertEquals(1.0, style.getBorderWidth(), 0.01);
    }

    @Test
    public void testChartStyleHelperNoFillMethods() {
        // 测试ChartStyleHelper的noFill相关方法
        ChartShapeStyleModel noFillStyle = ChartStyleHelper.createNoFillBorderStyle(2.5);
        
        assertNotNull(noFillStyle);
        assertTrue(noFillStyle.hasBorder());
        assertTrue(noFillStyle.isBorderNoFill());
        assertEquals(2.5, noFillStyle.getBorderWidth(), 0.01);
        assertEquals(ChartLineStyle.SOLID, noFillStyle.getBorder().getStyle());
        
        // 测试带样式的版本
        ChartShapeStyleModel dashNoFillStyle = ChartStyleHelper.createNoFillBorderStyle(1.0, ChartLineStyle.DASH);
        assertTrue(dashNoFillStyle.isBorderNoFill());
        assertEquals(ChartLineStyle.DASH, dashNoFillStyle.getBorder().getStyle());
        
        // 测试静态检查方法
        assertTrue(ChartStyleHelper.isBorderNoFill(noFillStyle));
        assertFalse(ChartStyleHelper.isBorderNoFill(null));
        
        ChartShapeStyleModel normalStyle = ChartStyleHelper.createBorderOnlyStyle("#000000", 1.0);
        assertFalse(ChartStyleHelper.isBorderNoFill(normalStyle));
    }
}