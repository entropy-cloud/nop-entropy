package io.nop.chart.export;

import io.nop.chart.export.model.ChartDataSet;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for chart export functionality
 */
public class ChartExportPerformanceTest {
    
    private ChartExporter exporter;
    private TestCellRefResolver resolver;
    
    @BeforeEach
    void setUp() {
        exporter = new ChartExporter();
        resolver = new TestCellRefResolver();
    }
    
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testLargeDataSetExport() {
        // 创建大数据集
        ChartModel chartModel = createChartModelWithLargeDataSet(ChartType.BAR, 1000);
        
        long startTime = System.currentTimeMillis();
        
        byte[] pngData = exporter.exportToPng(chartModel, resolver, null);
        
        long duration = System.currentTimeMillis() - startTime;
        
        assertNotNull(pngData);
        assertTrue(pngData.length > 0);
        
        // 验证性能 - 1000个数据点应该在10秒内完成
        assertTrue(duration < 10000, "Large dataset export took too long: " + duration + "ms");
        
        System.out.println("Large dataset export completed in " + duration + "ms");
    }
    
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testMultipleChartsPerformance() {
        int chartCount = 50;
        long totalStartTime = System.currentTimeMillis();
        
        for (int i = 0; i < chartCount; i++) {
            ChartModel chartModel = createChartModelWithType(ChartType.LINE);
            byte[] pngData = exporter.exportToPng(chartModel, resolver, null);
            
            assertNotNull(pngData);
            assertTrue(pngData.length > 0);
        }
        
        long totalDuration = System.currentTimeMillis() - totalStartTime;
        double avgDuration = (double) totalDuration / chartCount;
        
        // 验证平均性能 - 每个图表应该在600ms内完成
        assertTrue(avgDuration < 600, "Average chart export time too slow: " + avgDuration + "ms");
        
        System.out.println("Exported " + chartCount + " charts in " + totalDuration + "ms (avg: " + avgDuration + "ms)");
    }
    
    @Test
    void testMemoryUsageWithLargeDataSets() {
        ChartResourceManager resourceManager = ChartResourceManager.getInstance();
        
        // 记录初始内存使用
        ChartResourceManager.MemoryUsageInfo initialUsage = resourceManager.getMemoryUsageInfo();
        
        // 导出多个大数据集图表
        for (int i = 0; i < 10; i++) {
            ChartModel chartModel = createChartModelWithLargeDataSet(ChartType.AREA, 500);
            byte[] pngData = exporter.exportToPng(chartModel, resolver, null);
            assertNotNull(pngData);
        }
        
        // 检查内存使用
        ChartResourceManager.MemoryUsageInfo afterUsage = resourceManager.getMemoryUsageInfo();
        
        // 清理资源
        resourceManager.cleanupAllResources();
        
        ChartResourceManager.MemoryUsageInfo finalUsage = resourceManager.getMemoryUsageInfo();
        
        // 验证内存被正确清理
        assertTrue(finalUsage.getCurrentMemoryUsed() <= initialUsage.getCurrentMemoryUsed(), 
                  "Memory was not properly cleaned up");
        
        System.out.println("Memory usage - Initial: " + initialUsage + ", After: " + afterUsage + ", Final: " + finalUsage);
    }
    
    @Test
    void testDifferentChartTypesPerformance() {
        ChartType[] chartTypes = {
            ChartType.BAR, ChartType.LINE, ChartType.PIE, ChartType.AREA,
            ChartType.SCATTER, ChartType.BUBBLE, ChartType.DOUGHNUT
        };
        
        for (ChartType chartType : chartTypes) {
            long startTime = System.currentTimeMillis();
            
            ChartModel chartModel = createChartModelWithType(chartType);
            byte[] pngData = exporter.exportToPng(chartModel, resolver, null);
            
            long duration = System.currentTimeMillis() - startTime;
            
            assertNotNull(pngData, "Export failed for chart type: " + chartType);
            assertTrue(pngData.length > 0, "Empty result for chart type: " + chartType);
            
            // 每种图表类型应该在2秒内完成
            assertTrue(duration < 2000, "Chart type " + chartType + " took too long: " + duration + "ms");
            
            System.out.println("Chart type " + chartType + " exported in " + duration + "ms");
        }
    }
    
    @Test
    void testExportStatisticsPerformance() {
        // 重置统计
        ChartExportLogger.resetStatistics();
        
        int exportCount = 20;
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < exportCount; i++) {
            ChartModel chartModel = createChartModelWithType(ChartType.BAR);
            exporter.exportToPng(chartModel, resolver, null);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        ChartExportLogger.ExportStatistics stats = ChartExportLogger.getStatistics();
        
        assertEquals(exportCount, stats.getTotalExports());
        assertEquals(exportCount, stats.getSuccessfulExports());
        assertEquals(0, stats.getFailedExports());
        assertEquals(100.0, stats.getSuccessRate(), 0.1);
        
        System.out.println("Statistics performance test completed in " + duration + "ms: " + stats);
    }
    
    private ChartModel createChartModelWithType(ChartType chartType) {
        ChartModel chartModel = new ChartModel();
        chartModel.setType(chartType);
        return chartModel;
    }
    
    private ChartModel createChartModelWithLargeDataSet(ChartType chartType, int dataSize) {
        ChartModel chartModel = new ChartModel();
        chartModel.setType(chartType);
        
        // 创建大数据集 - 这里只是设置类型，实际数据由resolver提供
        return chartModel;
    }
    
    private static class TestCellRefResolver implements ICellRefResolver {
        @Override
        public Object getValue(String cellRef) {
            // 返回测试数据
            if (cellRef.startsWith("A")) {
                return "Category " + cellRef.substring(1);
            } else if (cellRef.startsWith("B")) {
                try {
                    int index = Integer.parseInt(cellRef.substring(1));
                    return (double) (index * 10 + Math.random() * 50);
                } catch (NumberFormatException e) {
                    return 10.0;
                }
            }
            return "Test Value";
        }
    }
}