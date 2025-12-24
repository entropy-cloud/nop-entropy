/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.model.drawing;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.model.ChartTitleModel;
import io.nop.excel.chart.model.ChartLegendModel;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the modular Chart Builder system.
 * Validates the new Parser-Builder pattern implementation.
 */
public class TestModularChartBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(TestModularChartBuilder.class);
    
    @Test
    public void testDrawingChartBuilderSingleton() {
        // Test singleton pattern
        DrawingChartBuilder builder1 = DrawingChartBuilder.INSTANCE;
        DrawingChartBuilder builder2 = DrawingChartBuilder.INSTANCE;
        
        assertSame(builder1, builder2, "DrawingChartBuilder should be singleton");
        assertNotNull(builder1, "DrawingChartBuilder instance should not be null");
    }
    
    @Test
    public void testSubBuilderSingletons() {
        // Test all sub-builder singletons
        ChartTitleBuilder titleBuilder1 = ChartTitleBuilder.INSTANCE;
        ChartTitleBuilder titleBuilder2 = ChartTitleBuilder.INSTANCE;
        assertSame(titleBuilder1, titleBuilder2, "ChartTitleBuilder should be singleton");
        
        ChartLegendBuilder legendBuilder1 = ChartLegendBuilder.INSTANCE;
        ChartLegendBuilder legendBuilder2 = ChartLegendBuilder.INSTANCE;
        assertSame(legendBuilder1, legendBuilder2, "ChartLegendBuilder should be singleton");
        
        ChartAxisBuilder axisBuilder1 = ChartAxisBuilder.INSTANCE;
        ChartAxisBuilder axisBuilder2 = ChartAxisBuilder.INSTANCE;
        assertSame(axisBuilder1, axisBuilder2, "ChartAxisBuilder should be singleton");
        
        ChartSeriesBuilder seriesBuilder1 = ChartSeriesBuilder.INSTANCE;
        ChartSeriesBuilder seriesBuilder2 = ChartSeriesBuilder.INSTANCE;
        assertSame(seriesBuilder1, seriesBuilder2, "ChartSeriesBuilder should be singleton");
        
        ChartStyleBuilder styleBuilder1 = ChartStyleBuilder.INSTANCE;
        ChartStyleBuilder styleBuilder2 = ChartStyleBuilder.INSTANCE;
        assertSame(styleBuilder1, styleBuilder2, "ChartStyleBuilder should be singleton");
    }
    
    @Test
    public void testBasicChartGeneration() {
        // Create a basic chart model
        ChartModel chart = createBasicChartModel();
        
        // Build chart using main builder
        DrawingChartBuilder builder = DrawingChartBuilder.INSTANCE;
        XNode chartXml = builder.build(chart);
        
        // Validate basic structure
        assertNotNull(chartXml, "Chart XML should not be null");
        assertEquals("c:chartSpace", chartXml.getTagName(), "Root element should be chartSpace");
        
        // Check namespaces
        assertEquals("http://schemas.openxmlformats.org/drawingml/2006/chart", 
                    chartXml.attrText("xmlns:c"), "Chart namespace should be correct");
        assertEquals("http://schemas.openxmlformats.org/drawingml/2006/main", 
                    chartXml.attrText("xmlns:a"), "Drawing namespace should be correct");
        assertEquals("http://schemas.openxmlformats.org/officeDocument/2006/relationships", 
                    chartXml.attrText("xmlns:r"), "Relationship namespace should be correct");
        
        // Check required Excel compatibility elements
        assertNotNull(chartXml.childByTag("c:date1904"), "Should have date1904 element");
        assertNotNull(chartXml.childByTag("c:lang"), "Should have lang element");
        assertNotNull(chartXml.childByTag("c:roundedCorners"), "Should have roundedCorners element");
        assertNotNull(chartXml.childByTag("c:chart"), "Should have chart element");
        assertNotNull(chartXml.childByTag("c:printSettings"), "Should have printSettings element");
        
        LOG.info("Basic chart generation test passed");
    }
    
    @Test
    public void testChartWithTitle() {
        // Create chart with title
        ChartModel chart = createBasicChartModel();
        ChartTitleModel title = new ChartTitleModel();
        title.setText("Test Chart Title");
        title.setVisible(true);
        chart.setTitle(title);
        
        // Build chart
        DrawingChartBuilder builder = DrawingChartBuilder.INSTANCE;
        XNode chartXml = builder.build(chart);
        
        // Validate title presence
        XNode chartElement = chartXml.childByTag("c:chart");
        assertNotNull(chartElement, "Chart element should exist");
        
        XNode titleElement = chartElement.childByTag("c:title");
        assertNotNull(titleElement, "Title element should exist");
        
        // Check autoTitleDeleted is set correctly
        XNode autoTitleDeleted = chartElement.childByTag("c:autoTitleDeleted");
        assertNotNull(autoTitleDeleted, "autoTitleDeleted element should exist");
        assertEquals("0", autoTitleDeleted.attrText("val"), "autoTitleDeleted should be 0 when title exists");
        
        LOG.info("Chart with title test passed");
    }
    
    @Test
    public void testChartWithLegend() {
        // Create chart with legend
        ChartModel chart = createBasicChartModel();
        ChartLegendModel legend = new ChartLegendModel();
        legend.setVisible(true);
        chart.setLegend(legend);
        
        // Build chart
        DrawingChartBuilder builder = DrawingChartBuilder.INSTANCE;
        XNode chartXml = builder.build(chart);
        
        // Validate legend presence
        XNode chartElement = chartXml.childByTag("c:chart");
        assertNotNull(chartElement, "Chart element should exist");
        
        XNode legendElement = chartElement.childByTag("c:legend");
        assertNotNull(legendElement, "Legend element should exist");
        
        LOG.info("Chart with legend test passed");
    }
    
    @Test
    public void testChartAnchorGeneration() {
        ChartModel chart = createBasicChartModel();
        
        DrawingChartBuilder builder = DrawingChartBuilder.INSTANCE;
        XNode anchor = builder.buildChartAnchor(chart, "rId1", 0);
        
        // Validate anchor structure
        assertNotNull(anchor, "Chart anchor should not be null");
        assertEquals("xdr:twoCellAnchor", anchor.getTagName(), "Should be twoCellAnchor");
        assertEquals("oneCell", anchor.attrText("editAs"), "Should have editAs=oneCell");
        
        // Check from and to positions
        assertNotNull(anchor.childByTag("xdr:from"), "Should have from position");
        assertNotNull(anchor.childByTag("xdr:to"), "Should have to position");
        
        // Check graphic frame
        XNode graphicFrame = anchor.childByTag("xdr:graphicFrame");
        assertNotNull(graphicFrame, "Should have graphic frame");
        
        // Check client data
        assertNotNull(anchor.childByTag("xdr:clientData"), "Should have client data");
        
        LOG.info("Chart anchor generation test passed");
    }
    
    @Test
    public void testChartReference() {
        ChartModel chart = createBasicChartModel();
        
        DrawingChartBuilder builder = DrawingChartBuilder.INSTANCE;
        XNode chartRef = builder.buildChartReference(chart, "rId1");
        
        // Validate chart reference
        assertNotNull(chartRef, "Chart reference should not be null");
        assertEquals("c:chart", chartRef.getTagName(), "Should be c:chart");
        assertEquals("rId1", chartRef.attrText("r:id"), "Should have correct relationship ID");
        
        LOG.info("Chart reference test passed");
    }
    
    @Test
    public void testNullChartHandling() {
        DrawingChartBuilder builder = DrawingChartBuilder.INSTANCE;
        
        // Test null chart
        XNode result = builder.build(null);
        assertNull(result, "Should return null for null chart");
        
        // Test null chart reference
        XNode chartRef = builder.buildChartReference(null, "rId1");
        assertNull(chartRef, "Should return null for null chart reference");
        
        LOG.info("Null chart handling test passed");
    }
    
    @Test
    public void testDifferentChartTypes() {
        ChartType[] chartTypes = {
            ChartType.BAR, ChartType.COLUMN, ChartType.LINE, 
            ChartType.PIE, ChartType.DOUGHNUT, ChartType.SCATTER, 
            ChartType.BUBBLE, ChartType.AREA
        };
        
        DrawingChartBuilder builder = DrawingChartBuilder.INSTANCE;
        
        for (ChartType chartType : chartTypes) {
            ChartModel chart = createBasicChartModel();
            chart.setType(chartType);
            
            XNode chartXml = builder.build(chart);
            assertNotNull(chartXml, "Chart XML should not be null for type: " + chartType);
            
            // Check that plot area contains chart type-specific elements
            XNode plotArea = chartXml.childByTag("c:chart").childByTag("c:plotArea");
            assertNotNull(plotArea, "Plot area should exist for type: " + chartType);
            
            LOG.debug("Chart type {} generated successfully", chartType);
        }
        
        LOG.info("Different chart types test passed");
    }
    
    /**
     * Helper method to create a basic chart model for testing.
     */
    private ChartModel createBasicChartModel() {
        ChartModel chart = new ChartModel();
        chart.setId("testChart");
        chart.setName("Test Chart");
        chart.setType(ChartType.BAR);
        chart.setWidth(500.0);
        chart.setHeight(350.0);
        return chart;
    }
}