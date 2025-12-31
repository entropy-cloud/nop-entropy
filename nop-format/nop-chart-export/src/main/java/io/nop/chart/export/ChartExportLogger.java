package io.nop.chart.export;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralized logging for chart export operations
 */
public class ChartExportLogger {
    private static final Logger LOG = LoggerFactory.getLogger(ChartExportLogger.class);
    
    // 操作统计
    private static final AtomicLong totalExports = new AtomicLong(0);
    private static final AtomicLong successfulExports = new AtomicLong(0);
    private static final AtomicLong failedExports = new AtomicLong(0);
    
    // 错误统计
    private static final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    
    // MDC键常量
    private static final String MDC_EXPORT_ID = "exportId";
    private static final String MDC_CHART_TYPE = "chartType";
    private static final String MDC_OPERATION = "operation";
    
    /**
     * 开始导出操作日志
     * @param exportId 导出ID
     * @param chartType 图表类型
     * @param width 宽度
     * @param height 高度
     */
    public static void logExportStart(String exportId, String chartType, int width, int height) {
        MDC.put(MDC_EXPORT_ID, exportId);
        MDC.put(MDC_CHART_TYPE, chartType);
        MDC.put(MDC_OPERATION, "export");
        
        totalExports.incrementAndGet();
        
        LOG.info("Starting chart export: type={}, size={}x{}", chartType, width, height);
    }
    
    /**
     * 记录导出成功
     * @param exportId 导出ID
     * @param durationMs 耗时毫秒
     * @param outputSize 输出大小字节
     */
    public static void logExportSuccess(String exportId, long durationMs, int outputSize) {
        successfulExports.incrementAndGet();
        
        LOG.info("Chart export completed successfully: duration={}ms, size={} bytes", durationMs, outputSize);
        
        clearMDC();
    }
    
    /**
     * 记录导出失败
     * @param exportId 导出ID
     * @param error 错误信息
     * @param exception 异常对象
     */
    public static void logExportFailure(String exportId, String error, Throwable exception) {
        failedExports.incrementAndGet();
        
        // 统计错误类型
        String errorType = exception != null ? exception.getClass().getSimpleName() : "UnknownError";
        errorCounts.computeIfAbsent(errorType, k -> new AtomicLong(0)).incrementAndGet();
        
        if (exception != null) {
            LOG.error("Chart export failed: {}", error, exception);
        } else {
            LOG.error("Chart export failed: {}", error);
        }
        
        clearMDC();
    }
    
    /**
     * 记录数据验证错误
     * @param validationErrors 验证错误列表
     */
    public static void logValidationErrors(java.util.List<String> validationErrors) {
        MDC.put(MDC_OPERATION, "validation");
        
        LOG.warn("Chart data validation failed with {} errors:", validationErrors.size());
        for (int i = 0; i < validationErrors.size(); i++) {
            LOG.warn("  {}. {}", i + 1, validationErrors.get(i));
        }
    }
    
    /**
     * 记录性能警告
     * @param operation 操作名称
     * @param durationMs 耗时毫秒
     * @param threshold 阈值毫秒
     */
    public static void logPerformanceWarning(String operation, long durationMs, long threshold) {
        if (durationMs > threshold) {
            LOG.warn("Performance warning: {} took {}ms (threshold: {}ms)", operation, durationMs, threshold);
        }
    }
    
    /**
     * 记录内存使用警告
     * @param currentMemoryMB 当前内存使用MB
     * @param maxMemoryMB 最大内存限制MB
     */
    public static void logMemoryWarning(long currentMemoryMB, long maxMemoryMB) {
        double usagePercent = (double) currentMemoryMB / maxMemoryMB * 100;
        
        if (usagePercent > 80) {
            LOG.warn("High memory usage: {}MB / {}MB ({:.1f}%)", currentMemoryMB, maxMemoryMB, usagePercent);
        }
    }
    
    /**
     * 记录资源清理
     * @param resourceCount 清理的资源数量
     * @param memoryFreedMB 释放的内存MB
     */
    public static void logResourceCleanup(int resourceCount, long memoryFreedMB) {
        LOG.debug("Resource cleanup completed: {} resources, {}MB memory freed", resourceCount, memoryFreedMB);
    }
    
    /**
     * 获取导出统计信息
     * @return 统计信息
     */
    public static ExportStatistics getStatistics() {
        return new ExportStatistics(
            totalExports.get(),
            successfulExports.get(),
            failedExports.get(),
            new ConcurrentHashMap<>(errorCounts)
        );
    }
    
    /**
     * 重置统计信息
     */
    public static void resetStatistics() {
        totalExports.set(0);
        successfulExports.set(0);
        failedExports.set(0);
        errorCounts.clear();
        
        LOG.info("Export statistics reset");
    }
    
    /**
     * 记录调试信息
     * @param message 消息
     * @param args 参数
     */
    public static void debug(String message, Object... args) {
        LOG.debug(message, args);
    }
    
    /**
     * 记录信息
     * @param message 消息
     * @param args 参数
     */
    public static void info(String message, Object... args) {
        LOG.info(message, args);
    }
    
    /**
     * 记录警告
     * @param message 消息
     * @param args 参数
     */
    public static void warn(String message, Object... args) {
        LOG.warn(message, args);
    }
    
    /**
     * 记录错误
     * @param message 消息
     * @param throwable 异常
     */
    public static void error(String message, Throwable throwable) {
        LOG.error(message, throwable);
    }
    
    private static void clearMDC() {
        MDC.remove(MDC_EXPORT_ID);
        MDC.remove(MDC_CHART_TYPE);
        MDC.remove(MDC_OPERATION);
    }
    
    /**
     * 导出统计信息
     */
    public static class ExportStatistics {
        private final long totalExports;
        private final long successfulExports;
        private final long failedExports;
        private final Map<String, AtomicLong> errorCounts;
        
        public ExportStatistics(long totalExports, long successfulExports, long failedExports, 
                              Map<String, AtomicLong> errorCounts) {
            this.totalExports = totalExports;
            this.successfulExports = successfulExports;
            this.failedExports = failedExports;
            this.errorCounts = errorCounts;
        }
        
        public long getTotalExports() {
            return totalExports;
        }
        
        public long getSuccessfulExports() {
            return successfulExports;
        }
        
        public long getFailedExports() {
            return failedExports;
        }
        
        public double getSuccessRate() {
            return totalExports > 0 ? (double) successfulExports / totalExports * 100 : 0;
        }
        
        public Map<String, AtomicLong> getErrorCounts() {
            return errorCounts;
        }
        
        @Override
        public String toString() {
            return String.format("ExportStatistics{total=%d, success=%d, failed=%d, successRate=%.1f%%}", 
                totalExports, successfulExports, failedExports, getSuccessRate());
        }
    }
}