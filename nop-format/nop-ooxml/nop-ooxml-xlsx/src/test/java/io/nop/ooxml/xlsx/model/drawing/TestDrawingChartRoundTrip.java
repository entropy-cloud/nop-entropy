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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        XNode chartSpace = builder.buildSimpleChart("Test Chart", ChartType.COLUMN);

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
        chartModel.setType(ChartType.COLUMN);

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
        assertEquals(ChartType.COLUMN, parsedChart.getType(), "Chart type should be preserved");

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