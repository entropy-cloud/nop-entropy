package io.nop.chart.export;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * Chart export error codes
 */
public interface ChartExportErrors {
    String ARG_CHART_MODEL = "chartModel";
    String ARG_CHART_TYPE = "chartType";
    String ARG_CELL_REF = "cellRef";
    String ARG_REASON = "reason";
    String ARG_WIDTH = "width";
    String ARG_HEIGHT = "height";
    String ARG_VALUE = "value";
    String ARG_TARGET_TYPE = "targetType";
    String ARG_TIMEOUT_MS = "timeoutMs";
    String ARG_ACTUAL_SIZE = "actualSize";
    String ARG_MAX_SIZE = "maxSize";

    ErrorCode ERR_INVALID_CHART_MODEL = 
        define("nop.chart.export.invalid-chart-model", "无效的图表模型: {chartModel}", ARG_CHART_MODEL);
    
    ErrorCode ERR_UNSUPPORTED_CHART_TYPE = 
        define("nop.chart.export.unsupported-chart-type", "不支持的图表类型: {chartType}", ARG_CHART_TYPE);
    
    ErrorCode ERR_DATA_RESOLUTION_FAILED = 
        define("nop.chart.export.data-resolution-failed", "数据解析失败: {cellRef}", ARG_CELL_REF);
    
    ErrorCode ERR_CHART_RENDER_FAILED = 
        define("nop.chart.export.render-failed", "图表渲染失败: {reason}", ARG_REASON);
    
    ErrorCode ERR_INVALID_DIMENSIONS = 
        define("nop.chart.export.invalid-dimensions", "无效的图片尺寸: width={width}, height={height}", 
               ARG_WIDTH, ARG_HEIGHT);
    
    ErrorCode ERR_DATA_TYPE_CONVERSION = 
        define("nop.chart.export.data-type-conversion", "数据类型转换失败: {value} -> {targetType}", 
               ARG_VALUE, ARG_TARGET_TYPE);
    
    ErrorCode ERR_EXPORT_TIMEOUT = 
        define("nop.chart.export.timeout", "图表导出超时: {timeoutMs}ms", ARG_TIMEOUT_MS);
    
    ErrorCode ERR_DATA_VOLUME_EXCEEDED = 
        define("nop.chart.export.data-volume-exceeded", "数据量超出限制: {actualSize} > {maxSize}", 
               ARG_ACTUAL_SIZE, ARG_MAX_SIZE);
}