package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartAxisCrossBetween;
import io.nop.excel.chart.constants.ChartAxisCrosses;
import io.nop.excel.chart.constants.ChartAxisPosition;
import io.nop.excel.chart.constants.ChartAxisType;
import io.nop.excel.chart.constants.ChartLabelAlignment;
import io.nop.excel.chart.model.ChartAxisModel;
import io.nop.excel.chart.model.ChartAxisScaleModel;
import io.nop.excel.chart.model.ChartAxisTitleModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试ChartAxisParser和ChartAxisBuilder的往返转换
 */
public class TestChartAxisRoundTrip {

    @Test
    public void testAxisRoundTrip() {
        // 创建一个完整的轴模型
        ChartAxisModel originalAxis = createCompleteAxisModel();

        // 使用Builder生成XML
        XNode axisNode = ChartAxisBuilder.INSTANCE.buildAxis(originalAxis);
        assertNotNull(axisNode);

        // 使用Parser解析XML
        ChartAxisModel parsedAxis = ChartAxisParser.INSTANCE.parseAxis(axisNode, new DefaultChartStyleProvider());
        assertNotNull(parsedAxis);

        // 验证基本属性
        assertEquals(originalAxis.getId(), parsedAxis.getId());
        assertEquals(originalAxis.getType(), parsedAxis.getType());
        assertEquals(originalAxis.getPosition(), parsedAxis.getPosition());
        assertEquals(originalAxis.isVisible(), parsedAxis.isVisible());

        // 验证交叉设置
        assertEquals(originalAxis.getCrossAxisId(), parsedAxis.getCrossAxisId());
        assertEquals(originalAxis.getCrossAt(), parsedAxis.getCrossAt());
        assertEquals(originalAxis.getCrosses(), parsedAxis.getCrosses());
        assertEquals(originalAxis.getCrossBetween(), parsedAxis.getCrossBetween());

        // 验证其他属性
        assertEquals(originalAxis.getMultiLevel(), parsedAxis.getMultiLevel());
        assertEquals(originalAxis.isPrimary(), parsedAxis.isPrimary());
        assertEquals(originalAxis.getDataCellRef(), parsedAxis.getDataCellRef());
        assertEquals(originalAxis.getLabelAlign(), parsedAxis.getLabelAlign());

        // 验证比例尺
        if (originalAxis.getScale() != null) {
            assertNotNull(parsedAxis.getScale());
            assertEquals(originalAxis.getScale().getMin(), parsedAxis.getScale().getMin());
            assertEquals(originalAxis.getScale().getMax(), parsedAxis.getScale().getMax());
            assertEquals(originalAxis.getScale().getLogBase(), parsedAxis.getScale().getLogBase());
            assertEquals(originalAxis.getScale().getReverse(), parsedAxis.getScale().getReverse());
        }

        // 验证轴标题
        if (originalAxis.getTitle() != null) {
            assertNotNull(parsedAxis.getTitle());
            assertEquals(originalAxis.getTitle().getText(), parsedAxis.getTitle().getText());
            assertEquals(originalAxis.getTitle().getTextCellRef(), parsedAxis.getTitle().getTextCellRef());
            assertEquals(originalAxis.getTitle().isVisible(), parsedAxis.getTitle().isVisible());
        }
    }

    @Test
    public void testAxisTitleRoundTrip() {
        // 测试带标题的轴
        ChartAxisModel axis = new ChartAxisModel();
        axis.setId("axis1");
        axis.setType(ChartAxisType.VALUE);
        axis.setPosition(ChartAxisPosition.LEFT);

        ChartAxisTitleModel title = new ChartAxisTitleModel();
        title.setText("Y轴标题");
        title.setVisible(true);
        axis.setTitle(title);

        // 往返转换
        XNode axisNode = ChartAxisBuilder.INSTANCE.buildAxis(axis);
        ChartAxisModel parsedAxis = ChartAxisParser.INSTANCE.parseAxis(axisNode, new DefaultChartStyleProvider());

        assertNotNull(parsedAxis.getTitle());
        assertEquals("Y轴标题", parsedAxis.getTitle().getText());
        assertTrue(parsedAxis.getTitle().isVisible());
    }

    @Test
    public void testAxisWithCellRefTitle() {
        // 测试使用单元格引用的标题
        ChartAxisModel axis = new ChartAxisModel();
        axis.setId("axis2");
        axis.setType(ChartAxisType.CATEGORY);
        axis.setPosition(ChartAxisPosition.BOTTOM);

        ChartAxisTitleModel title = new ChartAxisTitleModel();
        title.setTextCellRef("Sheet1!$A$1");
        title.setVisible(true);
        axis.setTitle(title);

        // 往返转换
        XNode axisNode = ChartAxisBuilder.INSTANCE.buildAxis(axis);
        ChartAxisModel parsedAxis = ChartAxisParser.INSTANCE.parseAxis(axisNode, new DefaultChartStyleProvider());

        assertNotNull(parsedAxis.getTitle());
        assertEquals("Sheet1!$A$1", parsedAxis.getTitle().getTextCellRef());
        assertTrue(parsedAxis.getTitle().isVisible());
    }

    private ChartAxisModel createCompleteAxisModel() {
        ChartAxisModel axis = new ChartAxisModel();
        
        // 基本属性
        axis.setId("axis123");
        axis.setType(ChartAxisType.CATEGORY);
        axis.setPosition(ChartAxisPosition.LEFT);
        axis.setVisible(true);
        axis.setMultiLevel(true);
        axis.setPrimary(true);
        
        // 交叉设置
        axis.setCrossAxisId("axis456");
        axis.setCrossAt(0.0);
        axis.setCrosses(ChartAxisCrosses.AUTO_ZERO);
        axis.setCrossBetween(ChartAxisCrossBetween.BETWEEN);
        
        // 其他属性
        axis.setDataCellRef("Sheet1!$A$1:$A$10");
        axis.setLabelAlign(ChartLabelAlignment.CENTER);
        
        // 比例尺
        ChartAxisScaleModel scale = new ChartAxisScaleModel();
        scale.setMin(0.0);
        scale.setMax(100.0);
        scale.setReverse(true);
        axis.setScale(scale);
        
        // 轴标题
        ChartAxisTitleModel title = new ChartAxisTitleModel();
        title.setText("测试轴标题");
        title.setVisible(true);
        axis.setTitle(title);
        
        return axis;
    }
}