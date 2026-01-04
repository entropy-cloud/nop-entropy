package io.nop.chart.export;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.excel.resolver.ICellRefResolver;
import io.nop.excel.chart.model.ChartModel;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Chart exporter interface
 */
public interface IChartExporter {

    default byte[] exportToImage(ChartModel chartModel, ICellRefResolver resolver) {
        return exportToImage(chartModel, resolver, null);
    }

    default byte[] exportToImage(ChartModel chartModel, ICellRefResolver resolver, ChartExportOptions options) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportToStream(out, chartModel, resolver, options);
        return out.toByteArray();
    }

    default void exportToStream(OutputStream out, ChartModel chartModel, ICellRefResolver resolver) {
        exportToStream(out, chartModel, resolver, null);
    }

    /**
     * 导出图表为PNG字节数组
     *
     * @param chartModel 图表模型
     * @param resolver   数据解析器
     * @param options    导出选项，null则使用默认选项
     * @return PNG字节数组
     */
    void exportToStream(OutputStream out,
                        ChartModel chartModel, ICellRefResolver resolver, ChartExportOptions options);

    /**
     * 导出图表到文件
     *
     * @param outputFile 输出文件
     * @param chartModel 图表模型
     * @param resolver   数据解析器
     */
    default void exportToFile(File outputFile, ChartModel chartModel, ICellRefResolver resolver) {
        exportToFile(outputFile, chartModel, resolver, null);
    }

    /**
     * 导出图表到文件
     *
     * @param outputFile 输出文件
     * @param chartModel 图表模型
     * @param resolver   数据解析器
     * @param options    导出选项，null则使用默认选项
     */
    default void exportToFile(File outputFile, ChartModel chartModel, ICellRefResolver resolver,
                              ChartExportOptions options) {
        OutputStream out = null;

        try {
            out = new FileOutputStream(outputFile);
            exportToStream(out, chartModel, resolver, options);
        } catch (Exception e) {
            throw new NopException(ChartExportErrors.ERR_CHART_RENDER_FAILED, e)
                    .param(ChartExportErrors.ARG_REASON, "Failed to write file: " + e.getMessage());
        } finally {
            IoHelper.safeCloseObject(out);
        }
    }
}