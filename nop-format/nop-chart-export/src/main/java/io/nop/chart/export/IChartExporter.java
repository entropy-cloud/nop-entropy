package io.nop.chart.export;

import io.nop.api.core.exceptions.NopException;
import io.nop.excel.chart.model.ChartModel;

import java.io.File;

/**
 * Chart exporter interface
 */
public interface IChartExporter {


    /**
     * 导出图表为PNG字节数组
     * @param chartModel 图表模型
     * @param resolver 数据解析器
     * @return PNG字节数组
     */
    default byte[] exportToPng(ChartModel chartModel, ICellRefResolver resolver) {
        return exportToPng(chartModel, resolver, null);
    }

    /**
     * 导出图表为PNG字节数组
     * @param chartModel 图表模型
     * @param resolver 数据解析器
     * @param options 导出选项，null则使用默认选项
     * @return PNG字节数组
     */
    default byte[] exportToPng(ChartModel chartModel, ICellRefResolver resolver, ChartExportOptions options) {
        return exportToPng(chartModel, resolver, options, null);
    }

    /**
     * 导出图表为PNG字节数组
     * @param chartModel 图表模型
     * @param resolver 数据解析器
     * @param options 导出选项，null则使用默认选项
     * @param progressCallback 进度回调，可为null
     * @return PNG字节数组
     */
    byte[] exportToPng(ChartModel chartModel, ICellRefResolver resolver, ChartExportOptions options, IProgressCallback progressCallback);
    
    /**
     * 导出图表到文件
     * @param chartModel 图表模型
     * @param resolver 数据解析器
     * @param outputFile 输出文件
     */
    default void exportToPngFile(ChartModel chartModel, ICellRefResolver resolver, File outputFile) {
        exportToPngFile(chartModel, resolver, outputFile, null);
    }
    
    /**
     * 导出图表到文件
     * @param chartModel 图表模型
     * @param resolver 数据解析器
     * @param outputFile 输出文件
     * @param options 导出选项，null则使用默认选项
     */
    default void exportToPngFile(ChartModel chartModel, ICellRefResolver resolver, File outputFile, ChartExportOptions options) {
        byte[] pngData = exportToPng(chartModel, resolver, options);
        
        try {
            java.nio.file.Files.write(outputFile.toPath(), pngData);
        } catch (Exception e) {
            throw new NopException(ChartExportErrors.ERR_CHART_RENDER_FAILED)
                .param(ChartExportErrors.ARG_REASON, "Failed to write file: " + e.getMessage())
                .cause(e);
        }
    }
}