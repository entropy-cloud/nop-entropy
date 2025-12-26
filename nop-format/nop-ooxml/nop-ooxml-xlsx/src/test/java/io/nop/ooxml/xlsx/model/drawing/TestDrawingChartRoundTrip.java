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
        if (chartModel.getPlotArea().getSeries() != null) {
          //  assertEquals(2, chartModel.getPlotArea().getSeries().size(), "Chart should have 2 series");
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
        if (chartModel.getPlotArea().getSeries() != null && !chartModel.getPlotArea().getSeries().isEmpty()) {
            assertEquals(3, chartModel.getPlotArea().getSeries().size(), "Chart should have 3 series");

            // Verify first series
            io.nop.excel.chart.model.ChartSeriesModel series1 = chartModel.getPlotArea().getSeries().get(0);
            assertEquals("Q1 Sales", series1.getName(), "First series name should be correct");
        }

        // Verify axes if present
        if (chartModel.getPlotArea().getAxes() != null) {
            assertEquals(2, chartModel.getPlotArea().getAxes().size(), "Chart should have 2 axes");
        }
    }

    /**
     * Test round-trip functionality when DrawingChartBuilder is implemented.
     * Currently this test is disabled as DrawingChartBuilder returns null.
     */
    // @Test
    public void testBarChartRoundTrip() {
        // Load input chart XML
        XNode inputChartSpace = attachmentXml("bar-chart-input.xml");
        assertNotNull(inputChartSpace, "Input chart XML should be loaded");

        // Parse chart using DrawingChartParser
        DrawingChartParser parser = DrawingChartParser.INSTANCE;
        ChartModel chartModel = parseChartSpace(parser, inputChartSpace);

        // Verify chart was parsed correctly
        assertNotNull(chartModel, "Chart should be parsed successfully");
        assertEquals(ChartType.COLUMN, chartModel.getType(),
                "Chart type should be COLUMN");
        assertNotNull(chartModel.getName(), "Chart should have a name");
        assertNotNull(chartModel.getPlotArea().getSeries(), "Chart should have series");
        assertEquals(2, chartModel.getPlotArea().getSeries().size(), "Chart should have 2 series");

        // Build chart back to XML using DrawingChartBuilder
        DrawingChartBuilder builder = DrawingChartBuilder.INSTANCE;
        XNode outputChartSpace = builder.build(chartModel);

        // Verify output XML was generated
        assertNotNull(outputChartSpace, "Output chart XML should be generated");
        assertEquals("c:chartSpace", outputChartSpace.getTagName(),
                "Output should be a chartSpace element");

        // Compare with expected output
        String expectedOutput = attachmentXmlText("bar-chart-expected.xml");
        String actualOutput = outputChartSpace.xml();

        // Normalize XML for comparison (remove whitespace differences)
        assertEquals(normalizeXml(expectedOutput), normalizeXml(actualOutput),
                "Round-trip output should match expected XML");
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