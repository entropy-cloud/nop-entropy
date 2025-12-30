/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.chart.model;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.chart.constants.ChartFillType;
import io.nop.excel.chart.constants.ChartLineStyle;
import io.nop.excel.chart.util.ChartStyleHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for ChartShapeStyleModel convenience methods
 */
public class TestChartShapeStyleModel extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testConvenienceMethods() {
        // Create a style with fill and border
        ChartShapeStyleModel style = new ChartShapeStyleModel();
        
        // Test empty style
        assertNull(style.getForegroundColor(), "Empty style should have no foreground color");
        assertNull(style.getBorderColor(), "Empty style should have no border color");
        assertNull(style.getBorderWidth(), "Empty style should have no border width");
        assertFalse(style.hasFill(), "Empty style should have no fill");
        assertFalse(style.hasBorder(), "Empty style should have no border");
        assertFalse(style.hasShadow(), "Empty style should have no shadow");

        // Add fill
        ChartFillModel fill = new ChartFillModel();
        fill.setType(ChartFillType.SOLID);
        fill.setForegroundColor("#FF0000");
        fill.setBackgroundColor("#FFFFFF");
        fill.setOpacity(0.8);
        style.setFill(fill);

        // Test fill convenience methods
        assertTrue(style.hasFill(), "Style should have fill");
        assertEquals("#FF0000", style.getForegroundColor(), "Foreground color should be red");
        assertEquals("#FFFFFF", style.getBackgroundColor(), "Background color should be white");
        assertEquals(0.8, style.getFillOpacity(), 0.001, "Fill opacity should be 0.8");

        // Add border
        ChartBorderModel border = new ChartBorderModel();
        border.setColor("#000000");
        border.setWidth(2.0);
        border.setStyle(ChartLineStyle.SOLID);
        border.setOpacity(0.9);
        style.setBorder(border);

        // Test border convenience methods
        assertTrue(style.hasBorder(), "Style should have border");
        assertEquals("#000000", style.getBorderColor(), "Border color should be black");
        assertEquals(2.0, style.getBorderWidth(), 0.001, "Border width should be 2.0");
        assertEquals(0.9, style.getBorderOpacity(), 0.001, "Border opacity should be 0.9");

        // Test ChartStyleHelper
        assertTrue(ChartStyleHelper.hasValidFillColor(style), "Style should have valid fill color");
        assertTrue(ChartStyleHelper.hasValidBorder(style), "Style should have valid border");
        
        String description = ChartStyleHelper.getStyleDescription(style);
        assertNotNull(description, "Style description should not be null");
        assertTrue(description.contains("fill=#FF0000"), "Description should contain fill color");
        assertTrue(description.contains("border=#000000"), "Description should contain border color");
        
        System.out.println("Style description: " + description);
    }

    @Test
    public void testStyleCreationHelpers() {
        // Test simple style creation
        ChartShapeStyleModel style = ChartStyleHelper.createSimpleStyle("#FF0000", "#000000", 1.5);
        
        assertNotNull(style, "Created style should not be null");
        assertTrue(style.hasFill(), "Created style should have fill");
        assertTrue(style.hasBorder(), "Created style should have border");
        assertEquals("#FF0000", style.getForegroundColor(), "Fill color should be red");
        assertEquals("#000000", style.getBorderColor(), "Border color should be black");
        assertEquals(1.5, style.getBorderWidth(), 0.001, "Border width should be 1.5");

        // Test fill-only style
        ChartShapeStyleModel fillOnlyStyle = ChartStyleHelper.createFillOnlyStyle("#00FF00");
        assertTrue(fillOnlyStyle.hasFill(), "Fill-only style should have fill");
        assertFalse(fillOnlyStyle.hasBorder(), "Fill-only style should not have border");
        assertEquals("#00FF00", fillOnlyStyle.getForegroundColor(), "Fill color should be green");

        // Test border-only style
        ChartShapeStyleModel borderOnlyStyle = ChartStyleHelper.createBorderOnlyStyle("#0000FF", 3.0);
        assertFalse(borderOnlyStyle.hasFill(), "Border-only style should not have fill");
        assertTrue(borderOnlyStyle.hasBorder(), "Border-only style should have border");
        assertEquals("#0000FF", borderOnlyStyle.getBorderColor(), "Border color should be blue");
        assertEquals(3.0, borderOnlyStyle.getBorderWidth(), 0.001, "Border width should be 3.0");
    }

    @Test
    public void testNullSafety() {
        // Test null safety of convenience methods
        ChartShapeStyleModel style = new ChartShapeStyleModel();
        
        // These should not throw exceptions
        assertNull(style.getForegroundColor());
        assertNull(style.getBackgroundColor());
        assertNull(style.getBorderColor());
        assertNull(style.getBorderWidth());
        assertNull(style.getFillOpacity());
        assertNull(style.getBorderOpacity());
        
        // Test helper methods with null
        assertFalse(ChartStyleHelper.hasValidFillColor(null));
        assertFalse(ChartStyleHelper.hasValidBorder(null));
        assertEquals("No style", ChartStyleHelper.getStyleDescription(null));
    }

    @Test
    public void testSmoothSeriesHelper() {
        // Test smooth series creation
        io.nop.excel.chart.model.ChartSeriesModel smoothSeries = 
                ChartStyleHelper.createSmoothSeries("Smooth Line", "Sheet1!$B$2:$B$6", "Sheet1!$A$2:$A$6", true);
        
        assertNotNull(smoothSeries, "Smooth series should be created");
        assertEquals("Smooth Line", smoothSeries.getName(), "Series name should be correct");
        assertEquals("Sheet1!$B$2:$B$6", smoothSeries.getDataCellRef(), "Data cell ref should be correct");
        assertEquals("Sheet1!$A$2:$A$6", smoothSeries.getCatCellRef(), "Category cell ref should be correct");
        assertTrue(smoothSeries.getSmooth(), "Series should be smooth");
        assertTrue(ChartStyleHelper.isSmooth(smoothSeries), "Helper should detect smooth series");

        // Test non-smooth series creation
        io.nop.excel.chart.model.ChartSeriesModel nonSmoothSeries = 
                ChartStyleHelper.createSmoothSeries("Sharp Line", "Sheet1!$C$2:$C$6", "Sheet1!$A$2:$A$6", false);
        
        assertNotNull(nonSmoothSeries, "Non-smooth series should be created");
        assertEquals("Sharp Line", nonSmoothSeries.getName(), "Series name should be correct");
        assertFalse(nonSmoothSeries.getSmooth(), "Series should not be smooth");
        assertFalse(ChartStyleHelper.isSmooth(nonSmoothSeries), "Helper should detect non-smooth series");

        // Test null safety
        assertFalse(ChartStyleHelper.isSmooth(null), "Null series should not be smooth");
        
        io.nop.excel.chart.model.ChartSeriesModel seriesWithoutSmooth = new io.nop.excel.chart.model.ChartSeriesModel();
        assertFalse(ChartStyleHelper.isSmooth(seriesWithoutSmooth), "Series without smooth attribute should not be smooth");
    }
}