package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.IChartStyleSupportModel;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.model.ChartSeriesModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChartSeriesParser 冒烟测试
 */
public class ChartSeriesParserTest {

    @Test
    public void testParseSeries() {
        // 创建一个简单的系列节点
        String xml = "<c:ser xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\">" +
                    "<c:tx>" +
                    "<c:strRef>" +
                    "<c:f>Sheet1!$A$1</c:f>" +
                    "<c:strCache>" +
                    "<c:ptCount val=\"1\"/>" +
                    "<c:pt idx=\"0\">" +
                    "<c:v>Series 1</c:v>" +
                    "</c:pt>" +
                    "</c:strCache>" +
                    "</c:strRef>" +
                    "</c:tx>" +
                    "<c:idx val=\"0\"/>" +
                    "<c:order val=\"0\"/>" +
                    "<c:cat>" +
                    "<c:strRef>" +
                    "<c:f>Sheet1!$B$1:$D$1</c:f>" +
                    "<c:strCache>" +
                    "<c:ptCount val=\"3\"/>" +
                    "<c:pt idx=\"0\"><c:v>Category 1</c:v></c:pt>" +
                    "<c:pt idx=\"1\"><c:v>Category 2</c:v></c:pt>" +
                    "<c:pt idx=\"2\"><c:v>Category 3</c:v></c:pt>" +
                    "</c:strCache>" +
                    "</c:strRef>" +
                    "</c:cat>" +
                    "<c:val>" +
                    "<c:numRef>" +
                    "<c:f>Sheet1!$B$2:$D$2</c:f>" +
                    "<c:numCache>" +
                    "<c:ptCount val=\"3\"/>" +
                    "<c:pt idx=\"0\"><c:v>10</c:v></c:pt>" +
                    "<c:pt idx=\"1\"><c:v>20</c:v></c:pt>" +
                    "<c:pt idx=\"2\"><c:v>30</c:v></c:pt>" +
                    "</c:numCache>" +
                    "</c:numRef>" +
                    "</c:val>" +
                    "</c:ser>";
        XNode serNode = XNode.parse(xml);

        // 创建一个简单的样式提供者
        IChartStyleProvider styleProvider = new IChartStyleProvider() {
            @Override
            public String getThemeColor(String themeColor) {
                return "#000000"; // 默认黑色
            }

            @Override
            public String resolveColor(String colorRef) {
                return colorRef; // 直接返回颜色引用
            }

            @Override
            public String applyColorModifications(String baseColor, XNode colorNode) {
                return baseColor; // 不做颜色修改
            }

            @Override
            public void applyTheme(String componentType, IChartStyleSupportModel model) {
                // 不做主题应用
            }

            @Override
            public String getSeriesColor(int seriesIndex) {
                return "";
            }

            @Override
            public List<String> getColorSequence() {
                return Collections.emptyList();
            }

            @Override
            public ChartShapeStyleModel getDefaultStyle(String componentType) {
                return null; // 不返回默认样式
            }

            public void applyVaryColors(ChartSeriesModel series, boolean varyColors, ChartModel chartModel){

            }
        };

        // 调用 parseSeries 方法
        ChartSeriesParser parser = ChartSeriesParser.INSTANCE;
        ChartSeriesModel series = parser.parseSeries(serNode, 0, styleProvider, new ChartModel());

        // 验证解析结果
        assertNotNull(series, "Series should not be null");
        assertEquals("ser-0", series.getId(), "Series ID should be 'ser-0'");
        assertEquals("Series 1", series.getName(), "Series name should be 'Series 1'");
        assertEquals("Sheet1!$A$1", series.getNameCellRef(), "Series name cell ref should be 'Sheet1!$A$1'");
        assertEquals("Sheet1!$B$1:$D$1", series.getCatCellRef(), "Series category cell ref should be 'Sheet1!$B$1:$D$1'");
        assertEquals("Sheet1!$B$2:$D$2", series.getDataCellRef(), "Series data cell ref should be 'Sheet1!$B$2:$D$2'");
        assertTrue(series.isVisible(), "Series should be visible");
        assertEquals(0, series.getIndex(), "Series index should be 0");
    }

    @Test
    public void testParseSeriesWithNullNode() {
        // 测试空节点情况
        ChartSeriesParser parser = ChartSeriesParser.INSTANCE;
        ChartSeriesModel series = parser.parseSeries(null, 0, null, new ChartModel());
        assertNull(series, "Series should be null when node is null");
    }
}
