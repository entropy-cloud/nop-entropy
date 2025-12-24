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
import io.nop.excel.chart.model.ChartModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
     * Test round-trip parsing and building for a simple bar chart.
     * This test validates that a bar chart can be parsed from XML,
     * converted to ChartModel, and then built back to equivalent XML.
     */
    @Test
    public void testBarChartRoundTrip() {
        // Load input chart XML
        XNode inputChartSpace = attachmentXml("bar-chart-input.xml");
        assertNotNull(inputChartSpace, "Input chart XML should be loaded");
        
        // Create test anchor for parsing
        XNode anchorNode = createTestAnchor();
        
        // Parse chart using DrawingChartParser
        DrawingChartParser parser = DrawingChartParser.INSTANCE;
        ChartModel chartModel = parseChartSpace(parser, inputChartSpace, anchorNode);
        
        // Verify chart was parsed correctly
        assertNotNull(chartModel, "Chart should be parsed successfully");
        assertEquals(io.nop.excel.chart.constants.ChartType.BAR, chartModel.getType(), 
            "Chart type should be BAR");
        assertNotNull(chartModel.getName(), "Chart should have a name");
        assertNotNull(chartModel.getSeries(), "Chart should have series");
        assertEquals(2, chartModel.getSeries().size(), "Chart should have 2 series");
        
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

    /**
     * Test round-trip parsing and building for a line chart with multiple series.
     * This test validates that a more complex chart structure can be preserved
     * through the parse-build cycle.
     */
    @Test
    public void testLineChartRoundTrip() {
        // Load input chart XML
        XNode inputChartSpace = attachmentXml("line-chart-input.xml");
        assertNotNull(inputChartSpace, "Input chart XML should be loaded");
        
        // Create test anchor for parsing
        XNode anchorNode = createTestAnchor();
        
        // Parse chart using DrawingChartParser
        DrawingChartParser parser = DrawingChartParser.INSTANCE;
        ChartModel chartModel = parseChartSpace(parser, inputChartSpace, anchorNode);
        
        // Verify chart was parsed correctly
        assertNotNull(chartModel, "Chart should be parsed successfully");
        assertEquals(io.nop.excel.chart.constants.ChartType.LINE, chartModel.getType(), 
            "Chart type should be LINE");
        assertNotNull(chartModel.getTitle(), "Chart should have a title");
        assertEquals("Sales Trend Analysis", chartModel.getTitle().getText(), 
            "Chart title should be correct");
        
        // Verify series details
        assertNotNull(chartModel.getSeries(), "Chart should have series");
        assertEquals(3, chartModel.getSeries().size(), "Chart should have 3 series");
        
        // Verify first series
        io.nop.excel.chart.model.ChartSeriesModel series1 = chartModel.getSeries().get(0);
        assertEquals("Q1 Sales", series1.getName(), "First series name should be correct");
        assertNotNull(series1.getDataSource(), "First series should have data source");
        
        // Verify axes
        assertNotNull(chartModel.getAxes(), "Chart should have axes");
        assertEquals(2, chartModel.getAxes().size(), "Chart should have 2 axes");
        
        // Build chart back to XML using DrawingChartBuilder
        DrawingChartBuilder builder = DrawingChartBuilder.INSTANCE;
        XNode outputChartSpace = builder.build(chartModel);
        
        // Verify output XML was generated
        assertNotNull(outputChartSpace, "Output chart XML should be generated");
        
        // Compare with expected output
        String expectedOutput = attachmentXmlText("line-chart-expected.xml");
        String actualOutput = outputChartSpace.xml();
        
        // Normalize XML for comparison
        assertEquals(normalizeXml(expectedOutput), normalizeXml(actualOutput), 
            "Round-trip output should match expected XML");
    }

    /**
     * Helper method to create a test anchor node with realistic dimensions.
     */
    private XNode createTestAnchor() {
        XNode anchorNode = XNode.make("xdr:twoCellAnchor");
        anchorNode.setAttr("editAs", "oneCell");
        
        // From position (B2)
        XNode fromNode = anchorNode.addChild("xdr:from");
        fromNode.addChild("xdr:col").content("1");
        fromNode.addChild("xdr:row").content("1");
        fromNode.addChild("xdr:colOff").content("0");
        fromNode.addChild("xdr:rowOff").content("0");
        
        // To position (H15) - reasonable chart size
        XNode toNode = anchorNode.addChild("xdr:to");
        toNode.addChild("xdr:col").content("7");
        toNode.addChild("xdr:row").content("14");
        toNode.addChild("xdr:colOff").content("0");
        toNode.addChild("xdr:rowOff").content("0");
        
        return anchorNode;
    }
    
    /**
     * Helper method to parse chart space using reflection to access private method.
     */
    private ChartModel parseChartSpace(DrawingChartParser parser, XNode chartSpaceNode, XNode anchorNode) {
        try {
            java.lang.reflect.Method parseChartSpaceMethod = DrawingChartParser.class.getDeclaredMethod(
                "parseChartSpace", XNode.class, XNode.class);
            parseChartSpaceMethod.setAccessible(true);
            
            return (ChartModel) parseChartSpaceMethod.invoke(parser, chartSpaceNode, anchorNode);
        } catch (Exception e) {
            fail("Failed to parse chart space: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Helper method to normalize XML for comparison by removing extra whitespace.
     */
    private String normalizeXml(String xml) {
        if (xml == null) return null;
        
        // Remove extra whitespace between elements while preserving content
        return xml.replaceAll(">\\s+<", "><")
                 .replaceAll("\\s+", " ")
                 .trim();
    }
}