/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.chart.export;

import io.nop.api.core.exceptions.NopException;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.constants.ChartType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChartExporterTest - 图表导出器测试类
 */
public class ExcelChartExporterTest {
    
    private ChartExporter exporter;
    
    @BeforeEach
    void setUp() {
        // Create registry with default renderers
        ChartTypeRendererRegistry registry = ChartTypeRendererRegistry.createDefault();
        exporter = new ChartExporter(registry);
    }
    
    @Test
    void testExportToPng() throws IOException {
        // Create a proper chart model for testing
        ChartModel chartModel = createTestChartModel(ChartType.BAR);
        
        // Create a mock resolver
        ICellRefResolver resolver = new MockCellRefResolver();
        
        // Test export to PNG
        assertDoesNotThrow(() -> {
            byte[] pngData = exporter.exportToPng(chartModel, resolver);
            assertTrue(pngData.length > 0, "PNG data should not be empty");
        });
    }
    
    @Test
    void testExportToPngWithOptions() throws IOException {
        // Create a proper chart model for testing
        ChartModel chartModel = createTestChartModel(ChartType.LINE);
        
        // Create a mock resolver
        ICellRefResolver resolver = new MockCellRefResolver();
        
        // Create export options
        ChartExportOptions options = new ChartExportOptions();
        options.setWidth(1024);
        options.setHeight(768);
        
        // Test export to PNG with options
        assertDoesNotThrow(() -> {
            byte[] pngData = exporter.exportToPng(chartModel, resolver, options);
            assertTrue(pngData.length > 0, "PNG data should not be empty");
        });
    }
    
    @Test
    void testExportToPngFile() throws IOException {
        // Create a proper chart model for testing
        ChartModel chartModel = createTestChartModel(ChartType.PIE);
        
        // Create a mock resolver
        ICellRefResolver resolver = new MockCellRefResolver();
        
        // Create temporary file
        java.io.File tempFile = java.io.File.createTempFile("test_chart", ".png");
        tempFile.deleteOnExit();
        
        // Test export to file
        assertDoesNotThrow(() -> {
            exporter.exportToPngFile(chartModel, resolver, tempFile);
            assertTrue(tempFile.exists(), "Output file should exist");
            assertTrue(tempFile.length() > 0, "Output file should not be empty");
        });
    }
    
    @Test
    void testInvalidInputs() {
        ICellRefResolver resolver = new MockCellRefResolver();
        
        // Test null chart model
        assertThrows(NopException.class, () -> {
            exporter.exportToPng(null, resolver);
        });
        
        // Test null resolver
        ChartModel chartModel = createTestChartModel(ChartType.BAR);
        assertThrows(NopException.class, () -> {
            exporter.exportToPng(chartModel, null);
        });
        
        // Test null chart type
        ChartModel invalidChart = new ChartModel();
        invalidChart.setType(null);
        assertThrows(NopException.class, () -> {
            exporter.exportToPng(invalidChart, resolver);
        });
    }
    
    /**
     * Create a proper test chart model with series data
     */
    private ChartModel createTestChartModel(io.nop.excel.chart.constants.ChartType type) {
        ChartModel chartModel = new ChartModel();
        chartModel.setType(type);
        chartModel.setName("Test Chart");
        
        io.nop.excel.chart.model.ChartPlotAreaModel plotArea = new io.nop.excel.chart.model.ChartPlotAreaModel();
        chartModel.setPlotArea(plotArea);
        
        // Create test series
        io.nop.excel.chart.model.ChartSeriesModel series = new io.nop.excel.chart.model.ChartSeriesModel();
        series.setId("test-series");
        series.setName("Test Series");
        series.setDataCellRef("B1:B5");  // Data values
        series.setCatCellRef("A1:A5");   // Category labels
        
        plotArea.setSeriesList(java.util.Arrays.asList(series));
        
        return chartModel;
    }
    
    /**
     * Mock implementation of ICellRefResolver for testing
     */
    private static class MockCellRefResolver implements ICellRefResolver {
        @Override
        public Object getValue(String cellRef) {
            // Return mock data based on cell reference
            if (cellRef.contains("A") || cellRef.toLowerCase().contains("cat")) {
                return "Category " + cellRef.charAt(cellRef.length() - 1);
            } else {
                return Math.random() * 100;
            }
        }
        
        @Override
        public java.util.List<Object> getValues(String cellRef) {
            java.util.List<Object> values = new java.util.ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                if (cellRef.contains("A") || cellRef.toLowerCase().contains("cat")) {
                    values.add("Category " + i);
                } else {
                    // Return numeric values for data series
                    values.add(10.0 + Math.random() * 90.0);
                }
            }
            return values;
        }
        
        @Override
        public boolean isValidRef(String cellRef) {
            // Simple validation - just check if not null and not empty
            return cellRef != null && !cellRef.trim().isEmpty();
        }
    }
}