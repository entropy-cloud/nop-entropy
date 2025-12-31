package io.nop.chart.export;

import io.nop.chart.export.renderer.BarChartRenderer;
import io.nop.chart.export.renderer.LineChartRenderer;
import io.nop.chart.export.renderer.PieChartRenderer;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartSeriesModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for ChartExporter
 */
public class TestChartExporter {
    
    private ChartExporter exporter;
    private ICellRefResolver mockResolver;
    
    @BeforeEach
    void setUp() {
        ChartTypeRendererRegistry registry = new ChartTypeRendererRegistry();
        registry.register(new BarChartRenderer());
        registry.register(new LineChartRenderer());
        registry.register(new PieChartRenderer());
        
        exporter = new ChartExporter(registry);
        
        // 创建模拟数据解析器
        mockResolver = new ICellRefResolver() {
            @Override
            public Object getValue(String cellRef) {
                return "Test Value";
            }
            
            @Override
            public List<Object> getValues(String cellRangeRef) {
                return Arrays.asList("Category1", "Category2", "Category3");
            }
            
            @Override
            public boolean isValidRef(String cellRef) {
                return true;
            }
        };
    }
    
    @Test
    void testExportBarChart() {
        ChartModel chartModel = createTestChartModel(ChartType.BAR);
        
        byte[] pngData = exporter.exportToPng(chartModel, mockResolver);
        
        assertNotNull(pngData);
        assertTrue(pngData.length > 0);
    }
    
    @Test
    void testExportLineChart() {
        ChartModel chartModel = createTestChartModel(ChartType.LINE);
        
        byte[] pngData = exporter.exportToPng(chartModel, mockResolver);
        
        assertNotNull(pngData);
        assertTrue(pngData.length > 0);
    }
    
    @Test
    void testExportPieChart() {
        ChartModel chartModel = createTestChartModel(ChartType.PIE);
        
        byte[] pngData = exporter.exportToPng(chartModel, mockResolver);
        
        assertNotNull(pngData);
        assertTrue(pngData.length > 0);
    }
    
    @Test
    void testInvalidChartModel() {
        assertThrows(Exception.class, () -> {
            exporter.exportToPng(null, mockResolver);
        });
    }
    
    @Test
    void testInvalidResolver() {
        ChartModel chartModel = createTestChartModel(ChartType.BAR);
        
        assertThrows(Exception.class, () -> {
            exporter.exportToPng(chartModel, null);
        });
    }
    
    private ChartModel createTestChartModel(ChartType type) {
        ChartModel chartModel = new ChartModel();
        chartModel.setType(type);
        
        ChartPlotAreaModel plotArea = new ChartPlotAreaModel();
        chartModel.setPlotArea(plotArea);
        
        // 创建测试系列
        ChartSeriesModel series = new ChartSeriesModel();
        series.setId("test-series");
        series.setName("Test Series");
        series.setDataCellRef("A1:A3");
        series.setCatCellRef("B1:B3");
        
        plotArea.setSeriesList(Arrays.asList(series));
        
        return chartModel;
    }
}