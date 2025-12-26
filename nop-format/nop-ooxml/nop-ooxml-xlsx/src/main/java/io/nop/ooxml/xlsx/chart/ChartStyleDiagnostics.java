package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ChartStyleDiagnostics - 图表样式诊断和调试工具
 * 提供颜色解析跟踪、主题文件加载日志和复杂颜色转换的诊断输出
 * 用于调试和性能分析
 */
public class ChartStyleDiagnostics {
    private static final Logger LOG = LoggerFactory.getLogger(ChartStyleDiagnostics.class);
    
    // 诊断开关
    private static boolean colorResolutionTracing = false;
    private static boolean themeLoadingLogging = false;
    private static boolean colorTransformationDiagnostics = false;
    
    // 诊断数据收集
    private static final List<ColorResolutionTrace> colorTraces = new ArrayList<>();
    private static final List<ThemeLoadingEvent> themeEvents = new ArrayList<>();
    private static final List<ColorTransformationDiagnostic> transformationDiagnostics = new ArrayList<>();
    
    /**
     * 颜色解析跟踪记录
     */
    public static class ColorResolutionTrace {
        private final long timestamp;
        private final String inputColor;
        private final String resolvedColor;
        private final String method;
        private final String context;
        
        public ColorResolutionTrace(String inputColor, String resolvedColor, String method, String context) {
            this.timestamp = System.currentTimeMillis();
            this.inputColor = inputColor;
            this.resolvedColor = resolvedColor;
            this.method = method;
            this.context = context;
        }
        
        // Getters
        public long getTimestamp() { return timestamp; }
        public String getInputColor() { return inputColor; }
        public String getResolvedColor() { return resolvedColor; }
        public String getMethod() { return method; }
        public String getContext() { return context; }
        
        @Override
        public String toString() {
            return String.format("[%d] %s: %s -> %s (%s)", timestamp, method, inputColor, resolvedColor, context);
        }
    }
    
    /**
     * 主题加载事件记录
     */
    public static class ThemeLoadingEvent {
        private final long timestamp;
        private final String eventType;
        private final String fileName;
        private final boolean success;
        private final String details;
        
        public ThemeLoadingEvent(String eventType, String fileName, boolean success, String details) {
            this.timestamp = System.currentTimeMillis();
            this.eventType = eventType;
            this.fileName = fileName;
            this.success = success;
            this.details = details;
        }
        
        // Getters
        public long getTimestamp() { return timestamp; }
        public String getEventType() { return eventType; }
        public String getFileName() { return fileName; }
        public boolean isSuccess() { return success; }
        public String getDetails() { return details; }
        
        @Override
        public String toString() {
            return String.format("[%d] %s: %s - %s (%s)", timestamp, eventType, fileName, 
                    success ? "SUCCESS" : "FAILED", details);
        }
    }
    
    /**
     * 颜色转换诊断记录
     */
    public static class ColorTransformationDiagnostic {
        private final long timestamp;
        private final String baseColor;
        private final String finalColor;
        private final List<String> transformationSteps;
        private final String xmlStructure;
        
        public ColorTransformationDiagnostic(String baseColor, String finalColor, 
                                           List<String> transformationSteps, String xmlStructure) {
            this.timestamp = System.currentTimeMillis();
            this.baseColor = baseColor;
            this.finalColor = finalColor;
            this.transformationSteps = new ArrayList<>(transformationSteps);
            this.xmlStructure = xmlStructure;
        }
        
        // Getters
        public long getTimestamp() { return timestamp; }
        public String getBaseColor() { return baseColor; }
        public String getFinalColor() { return finalColor; }
        public List<String> getTransformationSteps() { return transformationSteps; }
        public String getXmlStructure() { return xmlStructure; }
        
        @Override
        public String toString() {
            return String.format("[%d] Color Transformation: %s -> %s\nSteps: %s\nXML: %s", 
                    timestamp, baseColor, finalColor, transformationSteps, xmlStructure);
        }
    }
    
    /**
     * 启用颜色解析跟踪
     */
    public static void enableColorResolutionTracing() {
        colorResolutionTracing = true;
        LOG.info("Color resolution tracing enabled");
    }
    
    /**
     * 禁用颜色解析跟踪
     */
    public static void disableColorResolutionTracing() {
        colorResolutionTracing = false;
        LOG.info("Color resolution tracing disabled");
    }
    
    /**
     * 启用主题加载日志
     */
    public static void enableThemeLoadingLogging() {
        themeLoadingLogging = true;
        LOG.info("Theme loading logging enabled");
    }
    
    /**
     * 禁用主题加载日志
     */
    public static void disableThemeLoadingLogging() {
        themeLoadingLogging = false;
        LOG.info("Theme loading logging disabled");
    }
    
    /**
     * 启用颜色转换诊断
     */
    public static void enableColorTransformationDiagnostics() {
        colorTransformationDiagnostics = true;
        LOG.info("Color transformation diagnostics enabled");
    }
    
    /**
     * 禁用颜色转换诊断
     */
    public static void disableColorTransformationDiagnostics() {
        colorTransformationDiagnostics = false;
        LOG.info("Color transformation diagnostics disabled");
    }
    
    /**
     * 启用所有诊断功能
     */
    public static void enableAllDiagnostics() {
        enableColorResolutionTracing();
        enableThemeLoadingLogging();
        enableColorTransformationDiagnostics();
        LOG.info("All chart style diagnostics enabled");
    }
    
    /**
     * 禁用所有诊断功能
     */
    public static void disableAllDiagnostics() {
        disableColorResolutionTracing();
        disableThemeLoadingLogging();
        disableColorTransformationDiagnostics();
        LOG.info("All chart style diagnostics disabled");
    }
    
    /**
     * 记录颜色解析跟踪
     */
    public static void traceColorResolution(String inputColor, String resolvedColor, String method, String context) {
        if (colorResolutionTracing) {
            ColorResolutionTrace trace = new ColorResolutionTrace(inputColor, resolvedColor, method, context);
            colorTraces.add(trace);
            LOG.debug("Color Resolution Trace: {}", trace);
        }
    }
    
    /**
     * 记录主题加载事件
     */
    public static void logThemeLoadingEvent(String eventType, String fileName, boolean success, String details) {
        if (themeLoadingLogging) {
            ThemeLoadingEvent event = new ThemeLoadingEvent(eventType, fileName, success, details);
            themeEvents.add(event);
            if (success) {
                LOG.info("Theme Loading: {}", event);
            } else {
                LOG.warn("Theme Loading: {}", event);
            }
        }
    }
    
    /**
     * 记录颜色转换诊断
     */
    public static void diagnoseColorTransformation(String baseColor, String finalColor, 
                                                 List<String> transformationSteps, XNode xmlNode) {
        if (colorTransformationDiagnostics) {
            String xmlStructure = xmlNode != null ? xmlNode.xml() : "null";
            ColorTransformationDiagnostic diagnostic = new ColorTransformationDiagnostic(
                    baseColor, finalColor, transformationSteps, xmlStructure);
            transformationDiagnostics.add(diagnostic);
            LOG.debug("Color Transformation Diagnostic: {}", diagnostic);
        }
    }
    
    /**
     * 获取颜色解析跟踪记录
     */
    public static List<ColorResolutionTrace> getColorTraces() {
        return new ArrayList<>(colorTraces);
    }
    
    /**
     * 获取主题加载事件记录
     */
    public static List<ThemeLoadingEvent> getThemeEvents() {
        return new ArrayList<>(themeEvents);
    }
    
    /**
     * 获取颜色转换诊断记录
     */
    public static List<ColorTransformationDiagnostic> getTransformationDiagnostics() {
        return new ArrayList<>(transformationDiagnostics);
    }
    
    /**
     * 清除所有诊断数据
     */
    public static void clearAllDiagnostics() {
        colorTraces.clear();
        themeEvents.clear();
        transformationDiagnostics.clear();
        LOG.info("All diagnostic data cleared");
    }
    
    /**
     * 生成诊断报告
     */
    public static String generateDiagnosticReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== Chart Style Diagnostics Report ===\n");
        report.append("Generated at: ").append(System.currentTimeMillis()).append("\n\n");
        
        // 颜色解析统计
        report.append("Color Resolution Traces: ").append(colorTraces.size()).append("\n");
        if (!colorTraces.isEmpty()) {
            Map<String, Integer> methodCounts = new HashMap<>();
            Map<String, Integer> contextCounts = new HashMap<>();
            
            for (ColorResolutionTrace trace : colorTraces) {
                methodCounts.merge(trace.getMethod(), 1, Integer::sum);
                contextCounts.merge(trace.getContext(), 1, Integer::sum);
            }
            
            report.append("  By Method:\n");
            for (Map.Entry<String, Integer> entry : methodCounts.entrySet()) {
                report.append("    ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            
            report.append("  By Context:\n");
            for (Map.Entry<String, Integer> entry : contextCounts.entrySet()) {
                report.append("    ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        report.append("\n");
        
        // 主题加载统计
        report.append("Theme Loading Events: ").append(themeEvents.size()).append("\n");
        if (!themeEvents.isEmpty()) {
            long successCount = themeEvents.stream().mapToLong(e -> e.isSuccess() ? 1 : 0).sum();
            report.append("  Successful: ").append(successCount).append("\n");
            report.append("  Failed: ").append(themeEvents.size() - successCount).append("\n");
        }
        report.append("\n");
        
        // 颜色转换统计
        report.append("Color Transformation Diagnostics: ").append(transformationDiagnostics.size()).append("\n");
        if (!transformationDiagnostics.isEmpty()) {
            Map<Integer, Integer> stepCounts = new HashMap<>();
            for (ColorTransformationDiagnostic diagnostic : transformationDiagnostics) {
                int steps = diagnostic.getTransformationSteps().size();
                stepCounts.merge(steps, 1, Integer::sum);
            }
            
            report.append("  By Transformation Steps:\n");
            for (Map.Entry<Integer, Integer> entry : stepCounts.entrySet()) {
                report.append("    ").append(entry.getKey()).append(" steps: ").append(entry.getValue()).append("\n");
            }
        }
        
        return report.toString();
    }
    
    /**
     * 输出详细的诊断信息
     */
    public static void printDetailedDiagnostics() {
        LOG.info("=== Detailed Chart Style Diagnostics ===");
        
        if (!colorTraces.isEmpty()) {
            LOG.info("Color Resolution Traces ({}):", colorTraces.size());
            for (ColorResolutionTrace trace : colorTraces) {
                LOG.info("  {}", trace);
            }
        }
        
        if (!themeEvents.isEmpty()) {
            LOG.info("Theme Loading Events ({}):", themeEvents.size());
            for (ThemeLoadingEvent event : themeEvents) {
                LOG.info("  {}", event);
            }
        }
        
        if (!transformationDiagnostics.isEmpty()) {
            LOG.info("Color Transformation Diagnostics ({}):", transformationDiagnostics.size());
            for (ColorTransformationDiagnostic diagnostic : transformationDiagnostics) {
                LOG.info("  {}", diagnostic);
            }
        }
        
        LOG.info("=== End Diagnostics ===");
    }
    
    /**
     * 获取性能统计信息
     */
    public static Map<String, Object> getPerformanceStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("colorResolutionCount", colorTraces.size());
        stats.put("themeLoadingCount", themeEvents.size());
        stats.put("colorTransformationCount", transformationDiagnostics.size());
        
        if (!colorTraces.isEmpty()) {
            long minTime = colorTraces.stream().mapToLong(ColorResolutionTrace::getTimestamp).min().orElse(0);
            long maxTime = colorTraces.stream().mapToLong(ColorResolutionTrace::getTimestamp).max().orElse(0);
            stats.put("colorResolutionTimeSpan", maxTime - minTime);
        }
        
        if (!themeEvents.isEmpty()) {
            long successCount = themeEvents.stream().mapToLong(e -> e.isSuccess() ? 1 : 0).sum();
            stats.put("themeLoadingSuccessRate", (double) successCount / themeEvents.size());
        }
        
        return stats;
    }
    
    /**
     * 检查是否有任何诊断功能启用
     */
    public static boolean isAnyDiagnosticEnabled() {
        return colorResolutionTracing || themeLoadingLogging || colorTransformationDiagnostics;
    }
    
    /**
     * 获取当前诊断状态
     */
    public static Map<String, Boolean> getDiagnosticStatus() {
        Map<String, Boolean> status = new HashMap<>();
        status.put("colorResolutionTracing", colorResolutionTracing);
        status.put("themeLoadingLogging", themeLoadingLogging);
        status.put("colorTransformationDiagnostics", colorTransformationDiagnostics);
        return status;
    }
}