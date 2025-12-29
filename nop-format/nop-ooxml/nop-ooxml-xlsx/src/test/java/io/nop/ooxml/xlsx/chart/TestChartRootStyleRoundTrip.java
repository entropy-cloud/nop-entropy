package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartFillType;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartFillModel;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import io.nop.excel.chart.model.ChartTextStyleModel;
import io.nop.excel.model.ExcelFont;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试DrawingChartParser和DrawingChartBuilder对根级别样式的往返转换
 */
public class TestChartRootStyleRoundTrip {

    @Test
    public void testRootStyleRoundTrip() {
        // 创建带有根级别样式的图表模型
        ChartModel originalChart = createChartWithRootStyles();

        // 使用Builder生成XML
        XNode chartSpaceNode = DrawingChartBuilder.INSTANCE.buildChartSpace(originalChart);
        assertNotNull(chartSpaceNode);
        assertEquals("c:chartSpace", chartSpaceNode.getTagName());

        // 验证XML中包含样式节点
        XNode spPrNode = chartSpaceNode.childByTag("c:spPr");
        assertNotNull(spPrNode, "chartSpace should contain c:spPr node");

        XNode txPrNode = chartSpaceNode.childByTag("c:txPr");
        assertNotNull(txPrNode, "chartSpace should contain c:txPr node");

        // 使用Parser解析XML
        ChartModel parsedChart = new ChartModel();
        DrawingChartParser.INSTANCE.parseChartSpace(chartSpaceNode, new DefaultChartStyleProvider(), parsedChart);

        // 验证根级别样式被正确解析
        assertNotNull(parsedChart.getShapeStyle(), "Parsed chart should have shape style");
        assertNotNull(parsedChart.getTextStyle(), "Parsed chart should have text style");

        // 验证形状样式属性
        ChartShapeStyleModel parsedShapeStyle = parsedChart.getShapeStyle();
        if (originalChart.getShapeStyle().getFill() != null) {
            assertNotNull(parsedShapeStyle.getFill());
            assertEquals(originalChart.getShapeStyle().getFill().getType(), 
                        parsedShapeStyle.getFill().getType());
            assertEquals(originalChart.getShapeStyle().getFill().getBackgroundColor(), 
                        parsedShapeStyle.getFill().getBackgroundColor());
        }

        // 验证文本样式属性
        ChartTextStyleModel parsedTextStyle = parsedChart.getTextStyle();
        if (originalChart.getTextStyle().getFont() != null) {
            assertNotNull(parsedTextStyle.getFont());
            assertEquals(originalChart.getTextStyle().getFont().getFontName(), 
                        parsedTextStyle.getFont().getFontName());
            assertEquals(originalChart.getTextStyle().getFont().getFontSize(), 
                        parsedTextStyle.getFont().getFontSize());
        }
    }

    @Test
    public void testChartWithoutRootStyles() {
        // 创建没有根级别样式的图表模型
        ChartModel originalChart = new ChartModel();
        originalChart.setType(ChartType.BAR);
        originalChart.setName("测试图表");
        originalChart.setPlotArea(new ChartPlotAreaModel());

        // 往返转换
        XNode chartSpaceNode = DrawingChartBuilder.INSTANCE.buildChartSpace(originalChart);
        ChartModel parsedChart = new ChartModel();
        DrawingChartParser.INSTANCE.parseChartSpace(chartSpaceNode, new DefaultChartStyleProvider(), parsedChart);

        // 验证没有样式时不会出错
        // 样式可能为null，这是正常的
        assertEquals(originalChart.getType(), parsedChart.getType());
    }

    @Test
    public void testRootShapeStyleOnly() {
        // 测试只有形状样式的情况
        ChartModel chart = new ChartModel();
        chart.setType(ChartType.LINE);
        
        ChartShapeStyleModel shapeStyle = new ChartShapeStyleModel();
        ChartFillModel fill = new ChartFillModel();
        fill.setType(ChartFillType.SOLID);
        fill.setForegroundColor("#FF0000");
        shapeStyle.setFill(fill);
        chart.setShapeStyle(shapeStyle);

        // 往返转换
        XNode chartSpaceNode = DrawingChartBuilder.INSTANCE.buildChartSpace(chart);
        ChartModel parsedChart = new ChartModel();
        DrawingChartParser.INSTANCE.parseChartSpace(chartSpaceNode, new DefaultChartStyleProvider(), parsedChart);

        // 验证只有形状样式被解析
        assertNotNull(parsedChart.getShapeStyle());
        // 文本样式可能为null
    }

    @Test
    public void testRootTextStyleOnly() {
        // 测试只有文本样式的情况
        ChartModel chart = new ChartModel();
        chart.setType(ChartType.PIE);
        
        ChartTextStyleModel textStyle = new ChartTextStyleModel();
        ExcelFont font = new ExcelFont();
        font.setFontName("Arial");
        font.setFontSize(12.0f);
        textStyle.setFont(font);
        chart.setTextStyle(textStyle);

        // 往返转换
        XNode chartSpaceNode = DrawingChartBuilder.INSTANCE.buildChartSpace(chart);
        ChartModel parsedChart = new ChartModel();
        DrawingChartParser.INSTANCE.parseChartSpace(chartSpaceNode, new DefaultChartStyleProvider(), parsedChart);

        // 验证只有文本样式被解析
        assertNotNull(parsedChart.getTextStyle());
        // 形状样式可能为null
    }

    private ChartModel createChartWithRootStyles() {
        ChartModel chart = new ChartModel();
        chart.setType(ChartType.BAR);
        chart.setName("带样式的测试图表");

        // 创建根级别形状样式
        ChartShapeStyleModel shapeStyle = new ChartShapeStyleModel();
        ChartFillModel fill = new ChartFillModel();
        fill.setType(ChartFillType.SOLID);
        fill.setForegroundColor("#E6E6FA"); // 淡紫色背景
        shapeStyle.setFill(fill);
        chart.setShapeStyle(shapeStyle);

        // 创建根级别文本样式
        ChartTextStyleModel textStyle = new ChartTextStyleModel();
        ExcelFont font = new ExcelFont();
        font.setFontName("Microsoft YaHei");
        font.setFontSize(14.0f);
        font.setBold(true);
        textStyle.setFont(font);
        chart.setTextStyle(textStyle);

        return chart;
    }
}