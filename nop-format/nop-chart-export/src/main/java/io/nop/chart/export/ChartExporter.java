package io.nop.chart.export;

import io.nop.api.core.exceptions.NopException;
import io.nop.excel.chart.model.ChartModel;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * Main chart exporter class
 */
public class ChartExporter {
    private static final Logger LOG = LoggerFactory.getLogger(ChartExporter.class);
    
    private final ChartTypeRendererRegistry rendererRegistry;
    
    public ChartExporter(ChartTypeRendererRegistry rendererRegistry) {
        this.rendererRegistry = rendererRegistry;
    }
    
    /**
     * 导出图表为PNG字节数组
     * @param chartModel 图表模型
     * @param resolver 数据解析器
     * @return PNG字节数组
     */
    public byte[] exportToPng(ChartModel chartModel, ICellRefResolver resolver) {
        return exportToPng(chartModel, resolver, null);
    }
    
    /**
     * 导出图表为PNG字节数组
     * @param chartModel 图表模型
     * @param resolver 数据解析器
     * @param options 导出选项，null则使用默认选项
     * @return PNG字节数组
     */
    public byte[] exportToPng(ChartModel chartModel, ICellRefResolver resolver, ChartExportOptions options) {
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
    public byte[] exportToPng(ChartModel chartModel, ICellRefResolver resolver, ChartExportOptions options, IProgressCallback progressCallback) {
        validateInputs(chartModel, resolver);
        
        if (options == null) {
            options = ChartExportOptions.defaultOptions();
        }
        
        LOG.debug("Starting chart export: type={}, width={}, height={}", 
                 chartModel.getType(), options.getWidth(), options.getHeight());
        
        // 设置进度回调
        if (progressCallback != null) {
            progressCallback.setTotalSteps(4);
            progressCallback.reportProgress(0, "Starting chart export");
        }
        
        try {
            // 检查超时
            long startTime = System.currentTimeMillis();
            long timeoutMs = options.getTimeoutSeconds() * 1000L;
            
            // 步骤1: 验证和准备数据
            if (progressCallback != null) {
                if (progressCallback.isCancelled()) {
                    throw new NopException(ChartExportErrors.ERR_CHART_RENDER_FAILED)
                        .param(ChartExportErrors.ARG_REASON, "Export cancelled by user");
                }
                progressCallback.completeStep("Data validation completed");
                progressCallback.reportProgress(25, "Rendering chart");
            }
            
            // 步骤2: 渲染图表
            JFreeChart chart = renderChart(chartModel, resolver);
            
            if (progressCallback != null) {
                progressCallback.completeStep("Chart rendering completed");
                progressCallback.reportProgress(75, "Converting to PNG");
            }
            
            // 检查超时
            if (timeoutMs > 0 && (System.currentTimeMillis() - startTime) > timeoutMs) {
                throw new NopException(ChartExportErrors.ERR_CHART_RENDER_FAILED)
                    .param(ChartExportErrors.ARG_REASON, "Export timeout after " + options.getTimeoutSeconds() + " seconds");
            }
            
            // 步骤3: 转换为PNG
            byte[] result = convertToPng(chart, options);
            
            if (progressCallback != null) {
                progressCallback.completeStep("PNG conversion completed");
                progressCallback.reportProgress(100, "Export completed");
            }
            
            return result;
            
        } catch (Exception e) {
            LOG.error("Chart export failed", e);
            if (progressCallback != null) {
                progressCallback.reportProgress(-1, "Export failed: " + e.getMessage());
            }
            throw new NopException(ChartExportErrors.ERR_CHART_RENDER_FAILED)
                .param(ChartExportErrors.ARG_REASON, e.getMessage())
                .cause(e);
        }
    }
    
    /**
     * 导出图表到文件
     * @param chartModel 图表模型
     * @param resolver 数据解析器
     * @param outputFile 输出文件
     */
    public void exportToPngFile(ChartModel chartModel, ICellRefResolver resolver, File outputFile) {
        exportToPngFile(chartModel, resolver, outputFile, null);
    }
    
    /**
     * 导出图表到文件
     * @param chartModel 图表模型
     * @param resolver 数据解析器
     * @param outputFile 输出文件
     * @param options 导出选项，null则使用默认选项
     */
    public void exportToPngFile(ChartModel chartModel, ICellRefResolver resolver, File outputFile, ChartExportOptions options) {
        byte[] pngData = exportToPng(chartModel, resolver, options);
        
        try {
            java.nio.file.Files.write(outputFile.toPath(), pngData);
            LOG.debug("Chart exported to file: {}", outputFile.getAbsolutePath());
        } catch (IOException e) {
            throw new NopException(ChartExportErrors.ERR_CHART_RENDER_FAILED)
                .param(ChartExportErrors.ARG_REASON, "Failed to write file: " + e.getMessage())
                .cause(e);
        }
    }
    
    private void validateInputs(ChartModel chartModel, ICellRefResolver resolver) {
        if (chartModel == null) {
            throw new NopException(ChartExportErrors.ERR_INVALID_CHART_MODEL)
                .param(ChartExportErrors.ARG_CHART_MODEL, "null");
        }
        
        if (resolver == null) {
            throw new NopException(ChartExportErrors.ERR_INVALID_CHART_MODEL)
                .param(ChartExportErrors.ARG_CHART_MODEL, "resolver cannot be null");
        }
        
        if (chartModel.getType() == null) {
            throw new NopException(ChartExportErrors.ERR_UNSUPPORTED_CHART_TYPE)
                .param(ChartExportErrors.ARG_CHART_TYPE, "null");
        }
    }
    
    private JFreeChart renderChart(ChartModel chartModel, ICellRefResolver resolver) {
        IChartTypeRenderer renderer = rendererRegistry.getRenderer(chartModel.getType());
        return renderer.render(chartModel, resolver);
    }
    
    private byte[] convertToPng(JFreeChart chart, ChartExportOptions options) throws IOException {
        validateDimensions(options.getWidth(), options.getHeight());
        
        BufferedImage image = chart.createBufferedImage(
            options.getWidth(), 
            options.getHeight(),
            options.isAntiAlias() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB,
            null
        );
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }
    
    private void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new NopException(ChartExportErrors.ERR_INVALID_DIMENSIONS)
                .param(ChartExportErrors.ARG_WIDTH, width)
                .param(ChartExportErrors.ARG_HEIGHT, height);
        }
    }
}