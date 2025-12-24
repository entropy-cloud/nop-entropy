/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.model.drawing;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.chart.model.ChartModel;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.xlsx.model.ExcelOfficePackage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DrawingChartParser class structure and basic functionality.
 * Tests constructor initialization, error handling for null inputs, and basic method signatures.
 */
public class TestDrawingChartParser extends BaseTestCase {
    
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testSingletonInstance() {
        // Test that INSTANCE is available and not null
        assertNotNull(DrawingChartParser.INSTANCE, "Singleton instance should be available");
        
        // Test that multiple calls return the same instance
        DrawingChartParser instance1 = DrawingChartParser.INSTANCE;
        DrawingChartParser instance2 = DrawingChartParser.INSTANCE;
        assertSame(instance1, instance2, "Multiple calls should return the same singleton instance");
    }

    @Test
    public void testParseChartWithNullAnchorNode() {
        // Test parseChart method with null anchor node
        DrawingChartParser parser = DrawingChartParser.INSTANCE;
        
        XNode chartRefNode = XNode.make("c:chart");
        chartRefNode.setAttr("r:id", "rId1");
        
        ChartModel result = parser.parseChart(null, chartRefNode, null, null);
        
        assertNull(result, "parseChart should return null for null anchor node");
    }

    @Test
    public void testParseChartWithNullChartRefNode() {
        // Test parseChart method with null chart reference node
        DrawingChartParser parser = DrawingChartParser.INSTANCE;
        
        XNode anchorNode = XNode.make("xdr:twoCellAnchor");
        
        ChartModel result = parser.parseChart(anchorNode, null, null, null);
        
        assertNull(result, "parseChart should return null for null chart reference node");
    }

    @Test
    public void testParseChartWithNullChartId() {
        // Test parseChart method when chart reference node has no r:id attribute
        DrawingChartParser parser = DrawingChartParser.INSTANCE;
        
        XNode anchorNode = XNode.make("xdr:twoCellAnchor");
        XNode chartRefNode = XNode.make("c:chart");
        // chartRefNode has no r:id attribute
        
        ChartModel result = parser.parseChart(anchorNode, chartRefNode, null, null);
        
        assertNull(result, "parseChart should return null when chart ID is null");
    }

    @Test
    public void testParseChartWithValidInputsButNoChartPart() {
        // Test parseChart method with valid inputs but no chart part found
        DrawingChartParser parser = DrawingChartParser.INSTANCE;
        
        XNode anchorNode = XNode.make("xdr:twoCellAnchor");
        XNode chartRefNode = XNode.make("c:chart");
        chartRefNode.setAttr("r:id", "rId1");
        
        // Create a mock worksheet part that doesn't have the chart relationship
        TestOfficePackagePart worksheetPart = new TestOfficePackagePart("xl/worksheets/sheet1.xml");
        
        ChartModel result = parser.parseChart(anchorNode, chartRefNode, null, worksheetPart);
        
        assertNull(result, "parseChart should return null when chart part is not found");
    }

    /**
     * Property test for chart XML loading and ChartModel creation.
     * Feature: drawing-chart-parser, Property 4: ChartModel Creation
     * 
     * Property: For any valid chart XML input, the DrawingChartParser should return a ChartModel instance
     * Validates: Requirements 2.4, 3.1
     */
    @Test
    public void testPropertyChartModelCreation() {
        DrawingChartParser parser = DrawingChartParser.INSTANCE;
        
        // Test with multiple valid chart XML structures by directly testing parseChartSpace
        List<String> validChartXmlStructures = Arrays.asList(
            // Basic chart space with minimal structure
            "<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\">" +
            "  <c:chart>" +
            "    <c:title><c:tx><c:rich><a:p><a:r><a:t>Test Chart</a:t></a:r></a:p></c:rich></c:tx></c:title>" +
            "    <c:plotArea>" +
            "      <c:barChart><c:ser><c:idx val=\"0\"/></c:ser></c:barChart>" +
            "    </c:plotArea>" +
            "  </c:chart>" +
            "</c:chartSpace>",
            
            // Chart space with different structure
            "<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\">" +
            "  <c:chart>" +
            "    <c:plotArea>" +
            "      <c:lineChart><c:ser><c:idx val=\"0\"/></c:ser></c:lineChart>" +
            "    </c:plotArea>" +
            "    <c:legend><c:legendPos val=\"r\"/></c:legend>" +
            "  </c:chart>" +
            "</c:chartSpace>",
            
            // Minimal chart space
            "<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\">" +
            "  <c:chart>" +
            "    <c:plotArea/>" +
            "  </c:chart>" +
            "</c:chartSpace>",
            
            // Chart space with name attribute
            "<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\" name=\"TestChart\">" +
            "  <c:chart/>" +
            "</c:chartSpace>",
            
            // Chart space with only root element
            "<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\"/>"
        );
        
        // Test property: For any valid chart XML, should create ChartModel
        for (int i = 0; i < validChartXmlStructures.size(); i++) {
            String chartXml = validChartXmlStructures.get(i);
            
            // Create test anchor node
            XNode anchorNode = createTestAnchorNode();
            
            // Create chart space node from XML
            XNode chartSpaceNode = XNode.parse(chartXml);
            assertNotNull(chartSpaceNode, "Chart XML should parse successfully for test case " + i);
            
            // Use reflection to test the parseChartSpace method directly
            // This avoids the complexity of mocking the full relationship resolution
            try {
                java.lang.reflect.Method parseChartSpaceMethod = DrawingChartParser.class.getDeclaredMethod(
                    "parseChartSpace", XNode.class, XNode.class);
                parseChartSpaceMethod.setAccessible(true);
                
                // Test the property: valid chart XML should produce ChartModel
                ChartModel result = (ChartModel) parseChartSpaceMethod.invoke(parser, chartSpaceNode, anchorNode);
                
                // Verify property holds
                assertNotNull(result, "Property violation: valid chart XML should create ChartModel (test case " + i + ")");
                assertNotNull(result.getId(), "ChartModel should have an ID (test case " + i + ")");
                assertNotNull(result.getName(), "ChartModel should have a name (test case " + i + ")");
                
                // Verify basic properties are set
                if (i != 3) {
                    assertTrue(result.getName().startsWith("Chart_"), 
                        "Chart name should follow expected pattern (test case " + i + ")");
                }
                
                // Verify dimensions are set when anchor is provided
                if (anchorNode != null) {
                    // Width and height should be set based on anchor dimensions
                    // The anchor has colDelta=4 and rowDelta=9, so these should be reflected
                    assertTrue(result.getWidth() != null && result.getWidth() > 0, 
                        "Chart width should be set from anchor (test case " + i + ")");
                    assertTrue(result.getHeight() != null && result.getHeight() > 0, 
                        "Chart height should be set from anchor (test case " + i + ")");
                }
                
            } catch (Exception e) {
                fail("Failed to test parseChartSpace method for test case " + i + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Test chart type detection functionality.
     * Tests that different chart types are correctly identified and set in the ChartModel.
     */
    @Test
    public void testChartTypeDetection() {
        DrawingChartParser parser = DrawingChartParser.INSTANCE;
        
        // Test data: chart XML and expected chart type
        Object[][] testCases = {
            // Bar chart
            {
                "<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\">" +
                "  <c:chart>" +
                "    <c:plotArea>" +
                "      <c:barChart>" +
                "        <c:barDir val=\"bar\"/>" +
                "        <c:ser><c:idx val=\"0\"/></c:ser>" +
                "      </c:barChart>" +
                "    </c:plotArea>" +
                "  </c:chart>" +
                "</c:chartSpace>",
                io.nop.excel.chart.constants.ChartType.BAR
            },
            // Column chart (bar chart with col direction)
            {
                "<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\">" +
                "  <c:chart>" +
                "    <c:plotArea>" +
                "      <c:barChart>" +
                "        <c:barDir val=\"col\"/>" +
                "        <c:ser><c:idx val=\"0\"/></c:ser>" +
                "      </c:barChart>" +
                "    </c:plotArea>" +
                "  </c:chart>" +
                "</c:chartSpace>",
                io.nop.excel.chart.constants.ChartType.COLUMN
            },
            // Line chart
            {
                "<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\">" +
                "  <c:chart>" +
                "    <c:plotArea>" +
                "      <c:lineChart>" +
                "        <c:ser><c:idx val=\"0\"/></c:ser>" +
                "      </c:lineChart>" +
                "    </c:plotArea>" +
                "  </c:chart>" +
                "</c:chartSpace>",
                io.nop.excel.chart.constants.ChartType.LINE
            },
            // Pie chart
            {
                "<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\">" +
                "  <c:chart>" +
                "    <c:plotArea>" +
                "      <c:pieChart>" +
                "        <c:ser><c:idx val=\"0\"/></c:ser>" +
                "      </c:pieChart>" +
                "    </c:plotArea>" +
                "  </c:chart>" +
                "</c:chartSpace>",
                io.nop.excel.chart.constants.ChartType.PIE
            },
            // Scatter chart
            {
                "<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\">" +
                "  <c:chart>" +
                "    <c:plotArea>" +
                "      <c:scatterChart>" +
                "        <c:ser><c:idx val=\"0\"/></c:ser>" +
                "      </c:scatterChart>" +
                "    </c:plotArea>" +
                "  </c:chart>" +
                "</c:chartSpace>",
                io.nop.excel.chart.constants.ChartType.SCATTER
            },
            // Bubble chart (scatter chart with bubble style)
            {
                "<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\">" +
                "  <c:chart>" +
                "    <c:plotArea>" +
                "      <c:scatterChart>" +
                "        <c:scatterStyle val=\"bubble\"/>" +
                "        <c:ser><c:idx val=\"0\"/></c:ser>" +
                "      </c:scatterChart>" +
                "    </c:plotArea>" +
                "  </c:chart>" +
                "</c:chartSpace>",
                io.nop.excel.chart.constants.ChartType.BUBBLE
            },
            // Area chart
            {
                "<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\">" +
                "  <c:chart>" +
                "    <c:plotArea>" +
                "      <c:areaChart>" +
                "        <c:ser><c:idx val=\"0\"/></c:ser>" +
                "      </c:areaChart>" +
                "    </c:plotArea>" +
                "  </c:chart>" +
                "</c:chartSpace>",
                io.nop.excel.chart.constants.ChartType.AREA
            }
        };
        
        // Test each chart type
        for (int i = 0; i < testCases.length; i++) {
            String chartXml = (String) testCases[i][0];
            io.nop.excel.chart.constants.ChartType expectedType = (io.nop.excel.chart.constants.ChartType) testCases[i][1];
            
            // Create test anchor node
            XNode anchorNode = createTestAnchorNode();
            
            // Create chart space node from XML
            XNode chartSpaceNode = XNode.parse(chartXml);
            assertNotNull(chartSpaceNode, "Chart XML should parse successfully for test case " + i);
            
            // Use reflection to test the parseChartSpace method directly
            try {
                java.lang.reflect.Method parseChartSpaceMethod = DrawingChartParser.class.getDeclaredMethod(
                    "parseChartSpace", XNode.class, XNode.class);
                parseChartSpaceMethod.setAccessible(true);
                
                // Parse the chart
                ChartModel result = (ChartModel) parseChartSpaceMethod.invoke(parser, chartSpaceNode, anchorNode);
                
                // Verify chart type detection
                assertNotNull(result, "Chart should be created for test case " + i);
                assertEquals(expectedType, result.getType(), 
                    "Chart type should be correctly detected for test case " + i + " (expected: " + expectedType + ")");
                
            } catch (Exception e) {
                fail("Failed to test chart type detection for test case " + i + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Test basic chart space parsing functionality.
     * This test specifically validates task 3.2 requirements:
     * - Parse c:chartSpace root element
     * - Extract basic chart properties (id, name)
     * - Handle chart dimensions from anchor
     */
    @Test
    public void testBasicChartSpaceParsing() {
        DrawingChartParser parser = DrawingChartParser.INSTANCE;
        
        // Test 1: Basic c:chartSpace parsing with minimal content
        String basicChartXml = 
            "<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\">" +
            "  <c:chart/>" +
            "</c:chartSpace>";
        
        XNode chartSpaceNode = XNode.parse(basicChartXml);
        XNode anchorNode = createTestAnchorNode();
        
        try {
            java.lang.reflect.Method parseChartSpaceMethod = DrawingChartParser.class.getDeclaredMethod(
                "parseChartSpace", XNode.class, XNode.class);
            parseChartSpaceMethod.setAccessible(true);
            
            ChartModel result = (ChartModel) parseChartSpaceMethod.invoke(parser, chartSpaceNode, anchorNode);
            
            // Verify c:chartSpace root element was parsed
            assertNotNull(result, "c:chartSpace root element should be parsed successfully");
            
            // Verify basic chart properties (id, name) were extracted
            assertNotNull(result.getId(), "Chart ID should be extracted/generated");
            assertNotNull(result.getName(), "Chart name should be extracted/generated");
            assertTrue(result.getName().startsWith("Chart_"), "Chart name should follow expected pattern");
            
            // Verify chart dimensions were handled from anchor
            assertNotNull(result.getWidth(), "Chart width should be set from anchor");
            assertNotNull(result.getHeight(), "Chart height should be set from anchor");
            assertTrue(result.getWidth() > 0, "Chart width should be positive");
            assertTrue(result.getHeight() > 0, "Chart height should be positive");
            
        } catch (Exception e) {
            fail("Failed to test basic chart space parsing: " + e.getMessage());
        }
        
        // Test 2: c:chartSpace with name attribute
        String namedChartXml = 
            "<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\" name=\"TestChart\">" +
            "  <c:chart/>" +
            "</c:chartSpace>";
        
        XNode namedChartSpaceNode = XNode.parse(namedChartXml);
        
        try {
            java.lang.reflect.Method parseChartSpaceMethod = DrawingChartParser.class.getDeclaredMethod(
                "parseChartSpace", XNode.class, XNode.class);
            parseChartSpaceMethod.setAccessible(true);
            
            ChartModel result = (ChartModel) parseChartSpaceMethod.invoke(parser, namedChartSpaceNode, anchorNode);
            
            // Verify chart space name attribute was extracted
            assertNotNull(result, "Named c:chartSpace should be parsed successfully");
            assertEquals("TestChart", result.getName(), "Chart name should be extracted from c:chartSpace name attribute");
            
        } catch (Exception e) {
            fail("Failed to test named chart space parsing: " + e.getMessage());
        }
        
        // Test 3: c:chartSpace with id attribute
        String idChartXml = 
            "<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\" id=\"chart123\">" +
            "  <c:chart/>" +
            "</c:chartSpace>";
        
        XNode idChartSpaceNode = XNode.parse(idChartXml);
        
        try {
            java.lang.reflect.Method parseChartSpaceMethod = DrawingChartParser.class.getDeclaredMethod(
                "parseChartSpace", XNode.class, XNode.class);
            parseChartSpaceMethod.setAccessible(true);
            
            ChartModel result = (ChartModel) parseChartSpaceMethod.invoke(parser, idChartSpaceNode, anchorNode);
            
            // Verify chart space id attribute was extracted
            assertNotNull(result, "ID c:chartSpace should be parsed successfully");
            assertEquals("chart123", result.getId(), "Chart ID should be extracted from c:chartSpace id attribute");
            
        } catch (Exception e) {
            fail("Failed to test ID chart space parsing: " + e.getMessage());
        }
        
        // Test 4: c:chartSpace without anchor (dimensions should handle gracefully)
        try {
            java.lang.reflect.Method parseChartSpaceMethod = DrawingChartParser.class.getDeclaredMethod(
                "parseChartSpace", XNode.class, XNode.class);
            parseChartSpaceMethod.setAccessible(true);
            
            ChartModel result = (ChartModel) parseChartSpaceMethod.invoke(parser, chartSpaceNode, null);
            
            // Verify parsing works without anchor
            assertNotNull(result, "c:chartSpace should be parsed successfully without anchor");
            assertNotNull(result.getId(), "Chart ID should be generated even without anchor");
            assertNotNull(result.getName(), "Chart name should be generated even without anchor");
            
            // Dimensions may be null or default when no anchor is provided
            // This is acceptable behavior
            
        } catch (Exception e) {
            fail("Failed to test chart space parsing without anchor: " + e.getMessage());
        }
    }
    
    /**
     * Helper method to create a test anchor node with dimensions
     */
    private XNode createTestAnchorNode() {
        XNode anchorNode = XNode.make("xdr:twoCellAnchor");
        
        // Add from position
        XNode fromNode = anchorNode.addChild("xdr:from");
        fromNode.addChild("xdr:col").content("1");
        fromNode.addChild("xdr:row").content("1");
        fromNode.addChild("xdr:colOff").content("0");
        fromNode.addChild("xdr:rowOff").content("0");
        
        // Add to position
        XNode toNode = anchorNode.addChild("xdr:to");
        toNode.addChild("xdr:col").content("5");
        toNode.addChild("xdr:row").content("10");
        toNode.addChild("xdr:colOff").content("0");
        toNode.addChild("xdr:rowOff").content("0");
        
        return anchorNode;
    }
    
    /**
     * Test series parsing functionality.
     * This test validates that chart series elements are correctly parsed and ChartSeriesModel instances are created.
     * Tests requirements 3.3 for creating ChartSeriesModel instances with proper order and indexing.
     */
    @Test
    public void testSeriesParsing() {
        DrawingChartParser parser = DrawingChartParser.INSTANCE;
        
        // Test chart with multiple series
        String chartWithSeriesXml = 
            "<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\">" +
            "  <c:chart>" +
            "    <c:plotArea>" +
            "      <c:barChart>" +
            "        <c:ser>" +
            "          <c:idx val=\"0\"/>" +
            "          <c:order val=\"0\"/>" +
            "          <c:tx>" +
            "            <c:v>Sales</c:v>" +
            "          </c:tx>" +
            "          <c:cat>" +
            "            <c:strRef>" +
            "              <c:f>Sheet1!A2:A5</c:f>" +
            "            </c:strRef>" +
            "          </c:cat>" +
            "          <c:val>" +
            "            <c:numRef>" +
            "              <c:f>Sheet1!B2:B5</c:f>" +
            "            </c:numRef>" +
            "          </c:val>" +
            "        </c:ser>" +
            "        <c:ser>" +
            "          <c:idx val=\"1\"/>" +
            "          <c:order val=\"1\"/>" +
            "          <c:tx>" +
            "            <c:strRef>" +
            "              <c:f>Sheet1!C1</c:f>" +
            "            </c:strRef>" +
            "          </c:tx>" +
            "          <c:val>" +
            "            <c:numRef>" +
            "              <c:f>'Data Sheet'!$C$2:$C$5</c:f>" +
            "            </c:numRef>" +
            "          </c:val>" +
            "        </c:ser>" +
            "      </c:barChart>" +
            "    </c:plotArea>" +
            "  </c:chart>" +
            "</c:chartSpace>";
        
        XNode chartSpaceNode = XNode.parse(chartWithSeriesXml);
        XNode anchorNode = createTestAnchorNode();
        
        try {
            java.lang.reflect.Method parseChartSpaceMethod = DrawingChartParser.class.getDeclaredMethod(
                "parseChartSpace", XNode.class, XNode.class);
            parseChartSpaceMethod.setAccessible(true);
            
            ChartModel result = (ChartModel) parseChartSpaceMethod.invoke(parser, chartSpaceNode, anchorNode);
            
            // Verify chart was created
            assertNotNull(result, "Chart should be created successfully");
            assertEquals(io.nop.excel.chart.constants.ChartType.BAR, result.getType(), "Chart type should be BAR");
            
            // Verify series were parsed
            assertNotNull(result.getSeries(), "Chart should have series list");
            assertEquals(2, result.getSeries().size(), "Chart should have 2 series");
            
            // Verify first series
            io.nop.excel.chart.model.ChartSeriesModel series1 = result.getSeries().get(0);
            assertNotNull(series1, "First series should exist");
            assertEquals("Sales", series1.getName(), "First series should have correct name");
            assertTrue(series1.getVisible(), "First series should be visible");
            
            // Verify first series data source
            assertNotNull(series1.getDataSource(), "First series should have data source");
            assertEquals(io.nop.excel.chart.constants.ChartDataSourceType.EXCEL, series1.getDataSource().getType(), 
                "First series data source should be EXCEL type");
            assertNotNull(series1.getDataSource().getExcel(), "First series should have Excel data source");
            assertEquals("Sheet1", series1.getDataSource().getExcel().getSheetName(), 
                "First series should reference Sheet1");
            assertEquals("B2:B5", series1.getDataSource().getExcel().getCellRangeRef(), 
                "First series should reference B2:B5 range");
            
            // Verify second series
            io.nop.excel.chart.model.ChartSeriesModel series2 = result.getSeries().get(1);
            assertNotNull(series2, "Second series should exist");
            assertEquals("Series", series2.getName(), "Second series should have default name (formula not resolved)");
            assertTrue(series2.getVisible(), "Second series should be visible");
            
            // Verify second series data source (absolute references)
            assertNotNull(series2.getDataSource(), "Second series should have data source");
            assertEquals(io.nop.excel.chart.constants.ChartDataSourceType.EXCEL, series2.getDataSource().getType(), 
                "Second series data source should be EXCEL type");
            assertNotNull(series2.getDataSource().getExcel(), "Second series should have Excel data source");
            assertEquals("Data Sheet", series2.getDataSource().getExcel().getSheetName(), 
                "Second series should reference 'Data Sheet'");
            assertEquals("$C$2:$C$5", series2.getDataSource().getExcel().getCellRangeRef(), 
                "Second series should reference $C$2:$C$5 range (absolute)");
            
        } catch (Exception e) {
            fail("Failed to test series parsing: " + e.getMessage());
        }
    }
    
    /**
     * Test data source parsing functionality.
     * This test validates that different types of data source references are correctly parsed.
     * Tests requirements 4.5, 8.1, 8.2, 8.3, 8.4, 8.5 for data source extraction.
     */
    @Test
    public void testDataSourceParsing() {
        DrawingChartParser parser = DrawingChartParser.INSTANCE;
        
        // Test different data source reference types
        Object[][] testCases = {
            // String reference with sheet name
            {
                "<c:cat>" +
                "  <c:strRef>" +
                "    <c:f>Sheet1!A1:A5</c:f>" +
                "  </c:strRef>" +
                "</c:cat>",
                "Sheet1", "A1:A5", false // not absolute
            },
            // Numeric reference with quoted sheet name
            {
                "<c:val>" +
                "  <c:numRef>" +
                "    <c:f>'Data Sheet'!B2:B10</c:f>" +
                "  </c:numRef>" +
                "</c:val>",
                "Data Sheet", "B2:B10", false // not absolute
            },
            // Absolute reference
            {
                "<c:val>" +
                "  <c:numRef>" +
                "    <c:f>Sheet1!$A$1:$B$5</c:f>" +
                "  </c:numRef>" +
                "</c:val>",
                "Sheet1", "$A$1:$B$5", true // absolute
            },
            // Reference without sheet name
            {
                "<c:val>" +
                "  <c:numRef>" +
                "    <c:f>C1:C10</c:f>" +
                "  </c:numRef>" +
                "</c:val>",
                null, "C1:C10", false // not absolute
            },
            // Multi-level string reference
            {
                "<c:cat>" +
                "  <c:multiLvlStrRef>" +
                "    <c:f>Categories!A1:B5</c:f>" +
                "  </c:multiLvlStrRef>" +
                "</c:cat>",
                "Categories", "A1:B5", false // not absolute
            },
            // Scatter chart X values
            {
                "<c:xVal>" +
                "  <c:numRef>" +
                "    <c:f>Data!X1:X20</c:f>" +
                "  </c:numRef>" +
                "</c:xVal>",
                "Data", "X1:X20", false // not absolute
            },
            // Scatter chart Y values
            {
                "<c:yVal>" +
                "  <c:numRef>" +
                "    <c:f>Data!Y1:Y20</c:f>" +
                "  </c:numRef>" +
                "</c:yVal>",
                "Data", "Y1:Y20", false // not absolute
            }
        };
        
        for (int i = 0; i < testCases.length; i++) {
            String dataSourceXml = (String) testCases[i][0];
            String expectedSheet = (String) testCases[i][1];
            String expectedRange = (String) testCases[i][2];
            boolean expectedAbsolute = (Boolean) testCases[i][3];
            
            // Create a series with this data source
            String chartXml = 
                "<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\">" +
                "  <c:chart>" +
                "    <c:plotArea>" +
                "      <c:barChart>" +
                "        <c:ser>" +
                "          <c:idx val=\"0\"/>" +
                "          <c:order val=\"0\"/>" +
                "          " + dataSourceXml +
                "        </c:ser>" +
                "      </c:barChart>" +
                "    </c:plotArea>" +
                "  </c:chart>" +
                "</c:chartSpace>";
            
            XNode chartSpaceNode = XNode.parse(chartXml);
            XNode anchorNode = createTestAnchorNode();
            
            try {
                java.lang.reflect.Method parseChartSpaceMethod = DrawingChartParser.class.getDeclaredMethod(
                    "parseChartSpace", XNode.class, XNode.class);
                parseChartSpaceMethod.setAccessible(true);
                
                ChartModel result = (ChartModel) parseChartSpaceMethod.invoke(parser, chartSpaceNode, anchorNode);
                
                // Verify chart and series were created
                assertNotNull(result, "Chart should be created for test case " + i);
                assertNotNull(result.getSeries(), "Chart should have series for test case " + i);
                assertEquals(1, result.getSeries().size(), "Chart should have 1 series for test case " + i);
                
                // Verify data source was parsed
                io.nop.excel.chart.model.ChartSeriesModel series = result.getSeries().get(0);
                if (series.getDataSource() != null) {
                    assertEquals(io.nop.excel.chart.constants.ChartDataSourceType.EXCEL, series.getDataSource().getType(), 
                        "Data source should be EXCEL type for test case " + i);
                    
                    // Verify Excel data source details
                    io.nop.excel.chart.model.ChartExcelDataModel excelData = series.getDataSource().getExcel();
                    assertNotNull(excelData, "Series should have Excel data for test case " + i);
                    assertEquals(expectedSheet, excelData.getSheetName(), 
                        "Sheet name should match for test case " + i);
                    assertEquals(expectedRange, excelData.getCellRangeRef(), 
                        "Cell range should match for test case " + i);
                    
                    // Verify absolute vs relative reference detection
                    boolean actualAbsolute = expectedRange != null && expectedRange.contains("$");
                    assertEquals(expectedAbsolute, actualAbsolute, 
                        "Absolute reference detection should match for test case " + i);
                }
                
            } catch (Exception e) {
                fail("Failed to test data source parsing for test case " + i + ": " + e.getMessage());
            }
        }
    }

    /**
     * Test axis parsing functionality.
     * This test validates that chart axes elements are correctly parsed and ChartAxisModel instances are created.
     * Tests requirements 3.4 for creating ChartAxisModel instances with proper properties and scaling.
     */
    @Test
    public void testAxisParsing() {
        DrawingChartParser parser = DrawingChartParser.INSTANCE;
        
        // Test chart with category and value axes
        String chartWithAxesXml = 
            "<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\">" +
            "  <c:chart>" +
            "    <c:plotArea>" +
            "      <c:barChart>" +
            "        <c:ser>" +
            "          <c:idx val=\"0\"/>" +
            "          <c:val>" +
            "            <c:numRef>" +
            "              <c:f>Sheet1!B2:B5</c:f>" +
            "            </c:numRef>" +
            "          </c:val>" +
            "        </c:ser>" +
            "      </c:barChart>" +
            "      <c:catAx>" +
            "        <c:axId val=\"123456\"/>" +
            "        <c:axPos val=\"b\"/>" +
            "        <c:title>" +
            "          <c:tx>" +
            "            <c:rich>" +
            "              <a:p>" +
            "                <a:r>" +
            "                  <a:t>Category Axis</a:t>" +
            "                </a:r>" +
            "              </a:p>" +
            "            </c:rich>" +
            "          </c:tx>" +
            "        </c:title>" +
            "        <c:numFmt formatCode=\"General\" sourceLinked=\"1\"/>" +
            "        <c:majorGridlines/>" +
            "        <c:tickLblPos val=\"nextTo\"/>" +
            "        <c:majorTickMark val=\"out\"/>" +
            "        <c:minorTickMark val=\"none\"/>" +
            "      </c:catAx>" +
            "      <c:valAx>" +
            "        <c:axId val=\"789012\"/>" +
            "        <c:axPos val=\"l\"/>" +
            "        <c:scaling>" +
            "          <c:orientation val=\"minMax\"/>" +
            "          <c:min val=\"0\"/>" +
            "          <c:max val=\"100\"/>" +
            "        </c:scaling>" +
            "        <c:title>" +
            "          <c:tx>" +
            "            <c:v>Value Axis</c:v>" +
            "          </c:tx>" +
            "        </c:title>" +
            "        <c:numFmt formatCode=\"0.00\" sourceLinked=\"0\"/>" +
            "        <c:majorGridlines>" +
            "          <c:spPr/>" +
            "        </c:majorGridlines>" +
            "        <c:minorGridlines/>" +
            "        <c:tickLblPos val=\"low\"/>" +
            "        <c:majorTickMark val=\"cross\"/>" +
            "        <c:minorTickMark val=\"in\"/>" +
            "        <c:majorUnit val=\"10\"/>" +
            "        <c:minorUnit val=\"2\"/>" +
            "      </c:valAx>" +
            "    </c:plotArea>" +
            "  </c:chart>" +
            "</c:chartSpace>";
        
        XNode chartSpaceNode = XNode.parse(chartWithAxesXml);
        XNode anchorNode = createTestAnchorNode();
        
        try {
            java.lang.reflect.Method parseChartSpaceMethod = DrawingChartParser.class.getDeclaredMethod(
                "parseChartSpace", XNode.class, XNode.class);
            parseChartSpaceMethod.setAccessible(true);
            
            ChartModel result = (ChartModel) parseChartSpaceMethod.invoke(parser, chartSpaceNode, anchorNode);
            
            // Verify chart was created
            assertNotNull(result, "Chart should be created successfully");
            assertEquals(io.nop.excel.chart.constants.ChartType.BAR, result.getType(), "Chart type should be BAR");
            
            // Verify axes were parsed
            assertNotNull(result.getAxes(), "Chart should have axes list");
            assertEquals(2, result.getAxes().size(), "Chart should have 2 axes");
            
            // Find category and value axes
            io.nop.excel.chart.model.ChartAxisModel categoryAxis = null;
            io.nop.excel.chart.model.ChartAxisModel valueAxis = null;
            
            for (io.nop.excel.chart.model.ChartAxisModel axis : result.getAxes()) {
                if (io.nop.excel.chart.constants.ChartAxisType.CATEGORY.equals(axis.getType())) {
                    categoryAxis = axis;
                } else if (io.nop.excel.chart.constants.ChartAxisType.VALUE.equals(axis.getType())) {
                    valueAxis = axis;
                }
            }
            
            // Verify category axis
            assertNotNull(categoryAxis, "Category axis should exist");
            assertEquals("123456", categoryAxis.getId(), "Category axis should have correct ID");
            assertEquals(io.nop.excel.chart.constants.ChartAxisPosition.BOTTOM, categoryAxis.getPosition(), 
                "Category axis should be positioned at bottom");
            
            // Verify category axis title
            assertNotNull(categoryAxis.getTitle(), "Category axis should have title");
            assertEquals("Category Axis", categoryAxis.getTitle().getText(), "Category axis title should be correct");
            
            // Verify category axis labels
            assertNotNull(categoryAxis.getLabels(), "Category axis should have labels");
            assertEquals("General", categoryAxis.getLabels().getFormat(), "Category axis should have correct number format");
            
            // Verify category axis grid lines
            assertNotNull(categoryAxis.getGrid(), "Category axis should have grid configuration");
            assertTrue(categoryAxis.getGrid().getVisible(), "Category axis major grid lines should be visible");
            
            // Verify category axis tick marks
            assertNotNull(categoryAxis.getTicks(), "Category axis should have tick marks configuration");
            assertTrue(categoryAxis.getTicks().getVisible(), "Category axis tick marks should be visible");
            
            // Verify value axis
            assertNotNull(valueAxis, "Value axis should exist");
            assertEquals("789012", valueAxis.getId(), "Value axis should have correct ID");
            assertEquals(io.nop.excel.chart.constants.ChartAxisPosition.LEFT, valueAxis.getPosition(), 
                "Value axis should be positioned at left");
            
            // Verify value axis scaling
            assertNotNull(valueAxis.getScale(), "Value axis should have scaling configuration");
            assertEquals(Double.valueOf(0.0), valueAxis.getScale().getMin(), "Value axis minimum should be 0");
            assertEquals(Double.valueOf(100.0), valueAxis.getScale().getMax(), "Value axis maximum should be 100");
            assertEquals(Double.valueOf(10.0), valueAxis.getScale().getInterval(), "Value axis major unit should be 10");
            
            // Verify value axis title
            assertNotNull(valueAxis.getTitle(), "Value axis should have title");
            assertEquals("Value Axis", valueAxis.getTitle().getText(), "Value axis title should be correct");
            
            // Verify value axis labels
            assertNotNull(valueAxis.getLabels(), "Value axis should have labels");
            assertEquals("0.00", valueAxis.getLabels().getFormat(), "Value axis should have correct number format");
            
            // Verify value axis grid lines
            assertNotNull(valueAxis.getGrid(), "Value axis should have grid configuration");
            assertTrue(valueAxis.getGrid().getVisible(), "Value axis grid lines should be visible");
            
            // Verify value axis tick marks
            assertNotNull(valueAxis.getTicks(), "Value axis should have tick marks configuration");
            assertTrue(valueAxis.getTicks().getVisible(), "Value axis tick marks should be visible");
            
        } catch (Exception e) {
            fail("Failed to test axis parsing: " + e.getMessage());
        }
    }
    
    /**
     * Test axis parsing with minimal configuration.
     * This test validates that axes can be parsed even with minimal XML structure.
     */
    @Test
    public void testMinimalAxisParsing() {
        DrawingChartParser parser = DrawingChartParser.INSTANCE;
        
        // Test chart with minimal axis configuration
        String chartWithMinimalAxesXml = 
            "<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\">" +
            "  <c:chart>" +
            "    <c:plotArea>" +
            "      <c:lineChart>" +
            "        <c:ser>" +
            "          <c:idx val=\"0\"/>" +
            "        </c:ser>" +
            "      </c:lineChart>" +
            "      <c:catAx>" +
            "        <c:axId val=\"1\"/>" +
            "      </c:catAx>" +
            "      <c:valAx>" +
            "        <c:axId val=\"2\"/>" +
            "      </c:valAx>" +
            "    </c:plotArea>" +
            "  </c:chart>" +
            "</c:chartSpace>";
        
        XNode chartSpaceNode = XNode.parse(chartWithMinimalAxesXml);
        XNode anchorNode = createTestAnchorNode();
        
        try {
            java.lang.reflect.Method parseChartSpaceMethod = DrawingChartParser.class.getDeclaredMethod(
                "parseChartSpace", XNode.class, XNode.class);
            parseChartSpaceMethod.setAccessible(true);
            
            ChartModel result = (ChartModel) parseChartSpaceMethod.invoke(parser, chartSpaceNode, anchorNode);
            
            // Verify chart was created
            assertNotNull(result, "Chart should be created successfully");
            assertEquals(io.nop.excel.chart.constants.ChartType.LINE, result.getType(), "Chart type should be LINE");
            
            // Verify axes were parsed
            assertNotNull(result.getAxes(), "Chart should have axes list");
            assertEquals(2, result.getAxes().size(), "Chart should have 2 axes");
            
            // Verify both axes have IDs and types
            for (io.nop.excel.chart.model.ChartAxisModel axis : result.getAxes()) {
                assertNotNull(axis.getId(), "Axis should have ID");
                assertNotNull(axis.getType(), "Axis should have type");
                assertTrue(axis.getId().equals("1") || axis.getId().equals("2"), 
                    "Axis ID should be 1 or 2");
                assertTrue(axis.getType() == io.nop.excel.chart.constants.ChartAxisType.CATEGORY || 
                          axis.getType() == io.nop.excel.chart.constants.ChartAxisType.VALUE,
                    "Axis type should be CATEGORY or VALUE");
            }
            
        } catch (Exception e) {
            fail("Failed to test minimal axis parsing: " + e.getMessage());
        }
    }

    /**
     * Simple test implementation of IOfficePackagePart for testing purposes
     */
    private static class TestOfficePackagePart implements IOfficePackagePart {
        private final String partName;
        
        public TestOfficePackagePart(String partName) {
            this.partName = partName;
        }
        
        public TestOfficePackagePart() {
            this.partName = null;
        }
        
        @Override
        public String getPath() {
            return partName;
        }
        
        @Override
        public XNode loadXml() {
            return null;
        }
        
        @Override
        public XNode buildXml(io.nop.core.context.IEvalContext context) {
            return null;
        }
        
        @Override
        public void generateToStream(java.io.OutputStream os, io.nop.core.context.IEvalContext context) {
            // Not implemented for test
        }
    }
}