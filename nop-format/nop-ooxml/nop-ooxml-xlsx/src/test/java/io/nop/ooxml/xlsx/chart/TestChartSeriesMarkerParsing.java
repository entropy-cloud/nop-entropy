/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.chart;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.chart.constants.ChartMarkerType;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.model.ChartSeriesModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for ChartSeriesParser marker parsing functionality
 */
public class TestChartSeriesMarkerParsing extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testMarkerParsing() {
        // Create a test series XML with marker
        String seriesXml = attachmentXmlText("scatter-series.xml");

        XNode seriesNode = XNode.parse(seriesXml);
        
        // Parse the series
        ChartSeriesParser parser = ChartSeriesParser.INSTANCE;
        DefaultChartStyleProvider styleProvider = new DefaultChartStyleProvider();
        ChartSeriesModel series = parser.parseSeries(seriesNode, 0, styleProvider, new ChartModel());

        // Verify basic series properties
        assertNotNull(series, "Series should be parsed");
        assertEquals("Test Series", series.getName(), "Series name should be correct");

        // Verify marker configuration
        assertNotNull(series.getMarkers(), "Series should have marker configuration");
        assertTrue(series.getMarkers().getEnabled(), "Markers should be enabled");
        assertEquals(ChartMarkerType.CIRCLE, series.getMarkers().getType(), "Marker type should be circle");
        assertEquals(7.0, series.getMarkers().getSize(), 0.001, "Marker size should be 7");

        // Verify marker shape style
        assertNotNull(series.getMarkers().getShapeStyle(), "Marker should have shape style");
        
        // Test the convenience methods
        assertTrue(series.getMarkers().getShapeStyle().hasFill(), "Marker should have fill");
        assertTrue(series.getMarkers().getShapeStyle().hasBorder(), "Marker should have border");
        
        assertEquals("#FF0000", series.getMarkers().getShapeStyle().getForegroundColor(), 
                "Marker fill color should be red");
        assertEquals("#000000", series.getMarkers().getShapeStyle().getBorderColor(), 
                "Marker border color should be black");
        assertEquals(0.75, series.getMarkers().getShapeStyle().getBorderWidth(), 0.01, 
                "Marker border width should be correct");
        
        // Print style description for debugging
        System.out.println("Marker style: " + 
                io.nop.excel.chart.util.ChartStyleHelper.getStyleDescription(series.getMarkers().getShapeStyle()));
        
        // Test round-trip: build back to XML
        ChartSeriesBuilder builder = ChartSeriesBuilder.INSTANCE;
        XNode builtSeriesNode = builder.buildSeries(series, 0,XNode.make("c:barChart"));
        
        assertNotNull(builtSeriesNode, "Built series node should not be null");
        assertEquals("c:ser", builtSeriesNode.getTagName(), "Built node should be c:ser");
        
        // Verify marker node exists in built XML
        XNode markerNode = builtSeriesNode.childByTag("c:marker");
        assertNotNull(markerNode, "Built series should contain marker");
        
        XNode symbolNode = markerNode.childByTag("c:symbol");
        assertNotNull(symbolNode, "Marker should contain symbol");
        assertEquals("circle", symbolNode.attrText("val"), "Symbol should be circle");
        
        XNode sizeNode = markerNode.childByTag("c:size");
        assertNotNull(sizeNode, "Marker should contain size");
        assertEquals("7", sizeNode.attrText("val"), "Size should be 7");
        
        XNode spPrNode = markerNode.childByTag("c:spPr");
        assertNotNull(spPrNode, "Marker should contain shape properties");

        System.out.println("Built series XML:");
        builtSeriesNode.dump();
    }

}