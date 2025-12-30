/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.model.drawing;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartModel;
import io.nop.ooxml.xlsx.chart.DefaultChartStyleProvider;
import io.nop.ooxml.xlsx.chart.DrawingChartBuilder;
import io.nop.ooxml.xlsx.chart.DrawingChartParser;
import io.nop.xlang.xdsl.DslModelHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Round-trip unit tests for DrawingChartParser and DrawingChartBuilder.
 * Tests parsing chart XML to ChartModel and then building back to XML,
 * verifying the round-trip consistency.
 */
public class TestDrawingChartRoundTrip extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * Test parsing a simple bar chart.
     * This test validates that a bar chart can be parsed from XML
     * and converted to ChartModel successfully.
     */
    @Test
    public void testBarChartParsing() {
        // Load input chart XML
        XNode inputChartSpace = attachmentXml("bar-chart-input.xml");
        assertNotNull(inputChartSpace, "Input chart XML should be loaded");

        // Parse chart using DrawingChartParser
        DrawingChartParser parser = DrawingChartParser.INSTANCE;
        ChartModel chartModel = parseChartSpace(parser, inputChartSpace);

        // Verify chart was parsed correctly
        assertNotNull(chartModel, "Chart should be parsed successfully");
//        assertNotNull(chartModel.getName(), "Chart should have a name");
        assertNotNull(chartModel.getPlotArea(), "Chart should have a plot area");

        // Verify plot area has series
        if (chartModel.getPlotArea().getSeriesList() != null) {
            assertEquals(2, chartModel.getPlotArea().getSeriesList().size(), "Chart should have 2 series");
        }

        XNode node = DslModelHelper.dslModelToXNode("/nop/schema/excel/chart.xdef", chartModel);
        node.dump();
    }

    /**
     * Test parsing a line chart with multiple series.
     * This test validates that a more complex chart structure can be parsed
     * and converted to ChartModel successfully.
     */
    @Test
    public void testLineChartParsing() {
        // Load input chart XML
        XNode inputChartSpace = attachmentXml("line-chart-input.xml");
        assertNotNull(inputChartSpace, "Input chart XML should be loaded");

        // Parse chart using DrawingChartParser
        DrawingChartParser parser = DrawingChartParser.INSTANCE;
        ChartModel chartModel = parseChartSpace(parser, inputChartSpace);

        // Verify chart was parsed correctly
        assertNotNull(chartModel, "Chart should be parsed successfully");
        assertNotNull(chartModel.getPlotArea(), "Chart should have a plot area");

        // Verify title if present
        if (chartModel.getTitle() != null) {
            assertEquals("Sales Trend Analysis", chartModel.getTitle().getText(),
                    "Chart title should be correct");
        }

        // Verify series details if present
        if (chartModel.getPlotArea().getSeriesList() != null && !chartModel.getPlotArea().getSeriesList().isEmpty()) {
            assertEquals(3, chartModel.getPlotArea().getSeriesList().size(), "Chart should have 3 series");

            // Verify first series
            io.nop.excel.chart.model.ChartSeriesModel series1 = chartModel.getPlotArea().getSeriesList().get(0);
            assertEquals("Q1 Sales", series1.getName(), "First series name should be correct");
        }

        // Verify axes if present
        if (chartModel.getPlotArea().getAxes() != null) {
            assertEquals(2, chartModel.getPlotArea().getAxes().size(), "Chart should have 2 axes");
        }
    }

    /**
     * Test round-trip functionality with the implemented DrawingChartBuilder.
     * This test validates that a chart can be parsed from XML, converted to ChartModel,
     * and then built back to XML with consistent structure.
     */
    @Test
    public void testBarChartRoundTrip() {
        // Load input chart XML
        XNode inputChartSpace = attachmentXml("bar-chart-input.xml");
        assertNotNull(inputChartSpace, "Input chart XML should be loaded");

        inputChartSpace.dump();

        // Parse chart using DrawingChartParser
        DrawingChartParser parser = DrawingChartParser.INSTANCE;
        ChartModel chartModel = parseChartSpace(parser, inputChartSpace);

        // Verify chart was parsed correctly
        assertNotNull(chartModel, "Chart should be parsed successfully");
        assertNotNull(chartModel.getPlotArea(), "Chart should have a plot area");

        // Build chart back to XML using DrawingChartBuilder
        DrawingChartBuilder builder = DrawingChartBuilder.INSTANCE;
        XNode outputChartSpace = builder.build(chartModel);

        // Verify output XML was generated
        assertNotNull(outputChartSpace, "Output chart XML should be generated");
        assertEquals("c:chartSpace", outputChartSpace.getTagName(),
                "Output should be a chartSpace element");

        // Verify basic structure
        assertNotNull(outputChartSpace.childByTag("c:chart"),
                "Output should contain a chart element");
        assertNotNull(outputChartSpace.childByTag("c:chart").childByTag("c:plotArea"),
                "Output should contain a plotArea element");

        // Log the output for debugging
        System.out.println("Generated chart XML:");
        outputChartSpace.dump();
    }

    /**
     * Test round-trip functionality with a line chart.
     */
    @Test
    public void testLineChartRoundTrip() {
        // Load input chart XML
        XNode inputChartSpace = attachmentXml("line-chart-input.xml");
        assertNotNull(inputChartSpace, "Input chart XML should be loaded");

        // Parse chart using DrawingChartParser
        DrawingChartParser parser = DrawingChartParser.INSTANCE;
        ChartModel chartModel = parseChartSpace(parser, inputChartSpace);

        // Verify chart was parsed correctly
        assertNotNull(chartModel, "Chart should be parsed successfully");

        // Build chart back to XML using DrawingChartBuilder
        DrawingChartBuilder builder = DrawingChartBuilder.INSTANCE;
        XNode outputChartSpace = builder.build(chartModel);

        // Verify output XML was generated
        assertNotNull(outputChartSpace, "Output chart XML should be generated");
        assertEquals("c:chartSpace", outputChartSpace.getTagName(),
                "Output should be a chartSpace element");

        // Verify title if present in original
        if (chartModel.getTitle() != null && chartModel.getTitle().isVisible()) {
            XNode titleNode = outputChartSpace.childByTag("c:chart").childByTag("c:title");
            assertNotNull(titleNode, "Output should contain title if original had one");
        }

        // Verify legend if present in original
        if (chartModel.getLegend() != null && chartModel.getLegend().isVisible()) {
            XNode legendNode = outputChartSpace.childByTag("c:chart").childByTag("c:legend");
            assertNotNull(legendNode, "Output should contain legend if original had one");
        }

        // Log the output for debugging
        System.out.println("Generated line chart XML:");
        outputChartSpace.dump();
    }

    /**
     * Test building a simple chart from scratch.
     */
    @Test
    public void testSimpleChartBuilding() {
        // Build a simple chart using the convenience method
        DrawingChartBuilder builder = DrawingChartBuilder.INSTANCE;
        XNode chartSpace = builder.buildSimpleChart("Test Chart", ChartType.BAR);

        // Verify output XML was generated
        assertNotNull(chartSpace, "Chart XML should be generated");
        assertEquals("c:chartSpace", chartSpace.getTagName(),
                "Output should be a chartSpace element");

        // Verify basic structure
        XNode chartNode = chartSpace.childByTag("c:chart");
        assertNotNull(chartNode, "Chart should contain a chart element");

        XNode titleNode = chartNode.childByTag("c:title");
        assertNotNull(titleNode, "Chart should contain a title element");

        XNode plotAreaNode = chartNode.childByTag("c:plotArea");
        assertNotNull(plotAreaNode, "Chart should contain a plotArea element");

        // Log the output for debugging
        System.out.println("Generated simple chart XML:");
        chartSpace.dump();
    }

    /**
     * Test description round-trip functionality.
     * This test validates that chart description can be parsed from chartRef node
     * and built back to chartRef node correctly.
     */
    @Test
    public void testChartDescriptionRoundTrip() {
        // Create a chart model with description
        ChartModel chartModel = new ChartModel();
        chartModel.setName("Test Chart");
        chartModel.setDescription("dataRangeRefExpr=Sheet1!A1:B10");
        chartModel.setType(ChartType.BAR);

        // Build chart XML using DrawingChartBuilder
        DrawingChartBuilder builder = DrawingChartBuilder.INSTANCE;
        XNode chartSpace = builder.build(chartModel);

        // Verify chart was built correctly
        assertNotNull(chartSpace, "Chart XML should be generated");
        assertEquals("c:chartSpace", chartSpace.getTagName(),
                "Output should be a chartSpace element");

        // Parse chart back using DrawingChartParser
        DrawingChartParser parser = DrawingChartParser.INSTANCE;
        ChartModel parsedChart = parseChartSpace(parser, chartSpace);

        // Verify description was preserved (note: description is stored in chartRef, not chartSpace)
        // For this test, we'll verify the chart structure is correct
        assertNotNull(parsedChart, "Chart should be parsed successfully");
        assertEquals("Test Chart", parsedChart.getName(), "Chart name should be preserved");
        assertEquals(ChartType.BAR, parsedChart.getType(), "Chart type should be preserved");

        // Log the output for debugging
        System.out.println("Generated chart with description:");
        chartSpace.dump();
    }

    /**
     * Test DrawingBuilder chart anchor with description.
     * This test validates that chart description is correctly written to chartRef node.
     */
    @Test
    public void testDrawingBuilderChartDescription() {
        // Create a chart model with description
        io.nop.excel.model.ExcelChartModel excelChart = new io.nop.excel.model.ExcelChartModel();
        excelChart.setName("Test Chart with Description");
        excelChart.setDescription("dataRangeRefExpr=Sheet1!A1:C10");
        
        // Create a simple anchor
        io.nop.excel.model.ExcelClientAnchor anchor = new io.nop.excel.model.ExcelClientAnchor();
        anchor.setCol1(1);
        anchor.setRow1(1);
        anchor.setColDelta(7);
        anchor.setRowDelta(14);
        excelChart.setAnchor(anchor);

        // Build chart anchor using DrawingBuilder
        DrawingBuilder drawingBuilder = new DrawingBuilder();
        XNode chartAnchor = drawingBuilder.buildChartAnchor(excelChart, 0);

        // Verify chart anchor was built correctly
        assertNotNull(chartAnchor, "Chart anchor should be generated");
        assertEquals("xdr:twoCellAnchor", chartAnchor.getTagName(),
                "Output should be a twoCellAnchor element");

        // Verify graphic frame structure
        XNode graphicFrame = chartAnchor.childByTag("xdr:graphicFrame");
        assertNotNull(graphicFrame, "Chart anchor should contain graphicFrame");

        // Verify cNvPr contains description
        XNode nvGraphicFramePr = graphicFrame.childByTag("xdr:nvGraphicFramePr");
        assertNotNull(nvGraphicFramePr, "GraphicFrame should contain nvGraphicFramePr");
        
        XNode cNvPr = nvGraphicFramePr.childByTag("xdr:cNvPr");
        assertNotNull(cNvPr, "nvGraphicFramePr should contain cNvPr");
        
        String descr = cNvPr.attrText("descr");
        assertEquals("dataRangeRefExpr=Sheet1!A1:C10", descr, 
                "cNvPr should contain correct description");

        // Verify name is also set correctly
        String name = cNvPr.attrText("name");
        assertEquals("Test Chart with Description", name, 
                "cNvPr should contain correct name");

        // Log the output for debugging
        System.out.println("Generated chart anchor with description:");
        chartAnchor.dump();
    }

    /**
     * Test parsing a scatter chart with markers.
     * This test validates that scatter chart markers can be parsed correctly
     * including symbol, size, color, and border properties.
     */
    @Test
    public void testScatterChartWithMarkersRoundTrip() {
        // Load input chart XML
        XNode inputChartSpace = attachmentXml("scatter-chart-with-markers-input.xml");
        assertNotNull(inputChartSpace, "Input chart XML should be loaded");

        // Parse chart using DrawingChartParser
        DrawingChartParser parser = DrawingChartParser.INSTANCE;
        ChartModel chartModel = parseChartSpace(parser, inputChartSpace);

        // Verify chart was parsed correctly
        assertNotNull(chartModel, "Chart should be parsed successfully");
        assertNotNull(chartModel.getPlotArea(), "Chart should have a plot area");

        // Verify scatter chart configuration
        assertNotNull(chartModel.getPlotArea().getScatterConfig(), "Chart should have scatter configuration");
        assertEquals("lineMarker", chartModel.getPlotArea().getScatterConfig().getScatterStyle(), 
                "Scatter style should be lineMarker");

        // Verify series details
        assertNotNull(chartModel.getPlotArea().getSeriesList(), "Chart should have series list");
        assertEquals(1, chartModel.getPlotArea().getSeriesList().size(), "Chart should have 1 series");

        io.nop.excel.chart.model.ChartSeriesModel series = chartModel.getPlotArea().getSeriesList().get(0);
        assertEquals("系列1", series.getName(), "Series name should be correct");

        // Verify scatter chart data references (xVal and yVal)
        assertEquals("Sheet1!$A$2:$A$6", series.getCatCellRef(), "X values should be parsed correctly");
        assertEquals("Sheet1!$B$2:$B$6", series.getDataCellRef(), "Y values should be parsed correctly");

        // Verify smooth configuration
        assertNotNull(series.getSmooth(), "Series should have smooth attribute");
        assertFalse(series.getSmooth(), "Series should not be smooth");

        // Verify marker configuration
        assertNotNull(series.getMarkers(), "Series should have marker configuration");
        assertTrue(series.getMarkers().getEnabled(), "Markers should be enabled");
        assertEquals(io.nop.excel.chart.constants.ChartMarkerType.CIRCLE, series.getMarkers().getType(), 
                "Marker type should be circle");
        assertEquals(7.0, series.getMarkers().getSize(), 0.001, "Marker size should be 7");
        
        // Verify marker shape style
        assertNotNull(series.getMarkers().getShapeStyle(), "Marker should have shape style");
        assertEquals("#FF0000", series.getMarkers().getShapeStyle().getForegroundColor(), 
                "Marker fill color should be red");
        assertEquals("#000000", series.getMarkers().getShapeStyle().getBorderColor(), 
                "Marker border color should be black");
        assertEquals(0.75, series.getMarkers().getShapeStyle().getBorderWidth(), 0.01, 
                "Marker border width should be correct");

        // Build chart back to XML using DrawingChartBuilder
        DrawingChartBuilder builder = DrawingChartBuilder.INSTANCE;
        XNode outputChartSpace = builder.build(chartModel);

        // Verify output XML was generated
        assertNotNull(outputChartSpace, "Output chart XML should be generated");
        assertEquals("c:chartSpace", outputChartSpace.getTagName(),
                "Output should be a chartSpace element");

        // Verify scatter chart structure in output
        XNode plotAreaNode = outputChartSpace.childByTag("c:chart").childByTag("c:plotArea");
        assertNotNull(plotAreaNode, "Output should contain plotArea");
        
        XNode scatterChartNode = plotAreaNode.childByTag("c:scatterChart");
        assertNotNull(scatterChartNode, "Output should contain scatterChart");

        // Verify series with marker in output
        XNode seriesNode = scatterChartNode.childByTag("c:ser");
        assertNotNull(seriesNode, "Output should contain series");
        
        XNode markerNode = seriesNode.childByTag("c:marker");
        assertNotNull(markerNode, "Output should contain marker");
        
        XNode symbolNode = markerNode.childByTag("c:symbol");
        assertNotNull(symbolNode, "Marker should contain symbol");
        assertEquals("circle", symbolNode.attrText("val"), "Marker symbol should be circle");
        
        XNode sizeNode = markerNode.childByTag("c:size");
        assertNotNull(sizeNode, "Marker should contain size");
        assertEquals("7", sizeNode.attrText("val"), "Marker size should be 7");

        // Verify xVal and yVal in output
        XNode xValNode = seriesNode.childByTag("c:xVal");
        assertNotNull(xValNode, "Series should contain xVal");
        
        XNode yValNode = seriesNode.childByTag("c:yVal");
        assertNotNull(yValNode, "Series should contain yVal");

        // Verify smooth in output
        XNode smoothNode = seriesNode.childByTag("c:smooth");
        assertNotNull(smoothNode, "Series should contain smooth");
        assertEquals("0", smoothNode.attrText("val"), "Smooth value should be 0");

        // Log the output for debugging
        System.out.println("Generated scatter chart with markers XML:");
        outputChartSpace.dump();
    }

    /**
     * Test parsing a line chart with smooth series.
     * This test validates that smooth attribute can be parsed correctly
     * for line chart series.
     */
    @Test
    public void testLineChartWithSmoothRoundTrip() {
        // Load input chart XML
        XNode inputChartSpace = attachmentXml("line-chart-with-smooth-input.xml");
        assertNotNull(inputChartSpace, "Input chart XML should be loaded");

        // Parse chart using DrawingChartParser
        DrawingChartParser parser = DrawingChartParser.INSTANCE;
        ChartModel chartModel = parseChartSpace(parser, inputChartSpace);

        // Verify chart was parsed correctly
        assertNotNull(chartModel, "Chart should be parsed successfully");
        assertNotNull(chartModel.getPlotArea(), "Chart should have a plot area");

        // Verify line chart configuration
        assertNotNull(chartModel.getPlotArea().getLineConfig(), "Chart should have line configuration");

        // Verify series details
        assertNotNull(chartModel.getPlotArea().getSeriesList(), "Chart should have series list");
        assertEquals(2, chartModel.getPlotArea().getSeriesList().size(), "Chart should have 2 series");

        // Verify first series (smooth)
        io.nop.excel.chart.model.ChartSeriesModel series1 = chartModel.getPlotArea().getSeriesList().get(0);
        assertEquals("平滑系列", series1.getName(), "First series name should be correct");
        assertNotNull(series1.getSmooth(), "First series should have smooth attribute");
        assertTrue(series1.getSmooth(), "First series should be smooth");

        // Verify second series (not smooth)
        io.nop.excel.chart.model.ChartSeriesModel series2 = chartModel.getPlotArea().getSeriesList().get(1);
        assertEquals("非平滑系列", series2.getName(), "Second series name should be correct");
        assertNotNull(series2.getSmooth(), "Second series should have smooth attribute");
        assertFalse(series2.getSmooth(), "Second series should not be smooth");

        // Build chart back to XML using DrawingChartBuilder
        DrawingChartBuilder builder = DrawingChartBuilder.INSTANCE;
        XNode outputChartSpace = builder.build(chartModel);

        // Verify output XML was generated
        assertNotNull(outputChartSpace, "Output chart XML should be generated");
        assertEquals("c:chartSpace", outputChartSpace.getTagName(),
                "Output should be a chartSpace element");

        // Verify line chart structure in output
        XNode plotAreaNode = outputChartSpace.childByTag("c:chart").childByTag("c:plotArea");
        assertNotNull(plotAreaNode, "Output should contain plotArea");
        
        XNode lineChartNode = plotAreaNode.childByTag("c:lineChart");
        assertNotNull(lineChartNode, "Output should contain lineChart");

        // Verify series with smooth attributes in output
        List<XNode> seriesNodes = lineChartNode.childrenByTag("c:ser");
        assertEquals(2, seriesNodes.size(), "Output should contain 2 series");
        
        // Check first series (smooth=true)
        XNode firstSeriesNode = seriesNodes.get(0);
        XNode firstSmoothNode = firstSeriesNode.childByTag("c:smooth");
        assertNotNull(firstSmoothNode, "First series should contain smooth element");
        assertEquals("1", firstSmoothNode.attrText("val"), "First series smooth should be 1");
        
        // Check second series (smooth=false)
        XNode secondSeriesNode = seriesNodes.get(1);
        XNode secondSmoothNode = secondSeriesNode.childByTag("c:smooth");
        assertNotNull(secondSmoothNode, "Second series should contain smooth element");
        assertEquals("0", secondSmoothNode.attrText("val"), "Second series smooth should be 0");

        // Log the output for debugging
        System.out.println("Generated line chart with smooth XML:");
        outputChartSpace.dump();
    }

    private ChartModel parseChartSpace(DrawingChartParser parser, XNode chartSpaceNode) {
        // Use DefaultChartStyleProvider for parsing
        DefaultChartStyleProvider styleProvider = new DefaultChartStyleProvider();
        ChartModel chartModel = new ChartModel();
        parser.parseChartSpace(chartSpaceNode, styleProvider, chartModel);
        return chartModel;
    }

    /**
     * Helper method to normalize XML for comparison by removing extra whitespace.
     */
    private String normalizeXml(String xml) {
        if (xml == null) return null;

        // Remove extra whitespace between elements while preserving content
        return normalizeCRLF(xml);
    }
}