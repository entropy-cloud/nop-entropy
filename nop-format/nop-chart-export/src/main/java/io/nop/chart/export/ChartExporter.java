package io.nop.chart.export;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.chart.echarts.render.EChartsRenderer;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.util.ChartDataSet;
import io.nop.excel.resolver.ChartDataResolver;
import io.nop.excel.resolver.ICellRefResolver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Main chart exporter: renders a parsed {@link ChartModel} to PNG/SVG through the
 * nop-chart-echarts engine (plan 2364: legacy renderer internals replaced, shell
 * contract {@link IChartExporter} preserved).
 *
 * <p>Pipeline: validate → resolve series data → registry option builder →
 * option JSON → EChartsRenderer. Adapter errors surface with their own
 * {@link ChartExportErrors} codes (NopException is rethrown as-is); everything
 * else wraps into ERR_CHART_RENDER_FAILED.</p>
 */
public class ChartExporter implements IChartExporter {
    private static final Logger LOG = LoggerFactory.getLogger(ChartExporter.class);

    public static final String FORMAT_PNG = "png";
    public static final String FORMAT_SVG = "svg";

    /**
     * 画布像素乘积上限（plan 2586）：防止超大 width*height 触发整幅光栅 OOM。
     */
    public static final long MAX_CANVAS_PIXELS = 16_000_000L;

    private final ChartTypeRendererRegistry rendererRegistry;
    private final EChartsRenderer chartRenderer;

    public ChartExporter(ChartTypeRendererRegistry rendererRegistry) {
        this(rendererRegistry, new EChartsRenderer());
    }

    public ChartExporter(ChartTypeRendererRegistry rendererRegistry, EChartsRenderer chartRenderer) {
        this.rendererRegistry = Guard.notNull(rendererRegistry, "rendererRegistry");
        this.chartRenderer = Guard.notNull(chartRenderer, "chartRenderer");
    }

    public ChartExporter() {
        this(ChartTypeRendererRegistry.getDefault());
    }

    @Override
    public void exportToStream(OutputStream out, ChartModel chartModel,
                               ICellRefResolver resolver, ChartExportOptions options) {
        validateInputs(chartModel, resolver);
        if (options == null)
            options = ChartExportOptions.defaultOptions();
        validateDimensions(options.getWidth(), options.getHeight());

        IProgressCallback progress = options.getProgressCallback();
        if (progress != null) {
            progress.setTotalSteps(4);
            progress.reportProgress(0, "Starting chart export");
        }

        try {
            validateNotCancelled(progress);

            // Step 1: resolve series data from the model's cell references.
            List<ChartDataSet> dataSets = ChartDataResolver.INSTANCE
                    .resolveSeriesData(chartModel.getPlotArea(), resolver);
            ChartDataValidator validator = new ChartDataValidator();
            validator.validateChartModel(chartModel);
            int maxDataSize = options.getMaxDataSize();
            for (ChartDataSet dataSet : dataSets)
                validator.checkDataVolumeLimit(dataSet, maxDataSize);

            if (progress != null) {
                progress.completeStep("Data validation completed");
                progress.reportProgress(25, "Rendering chart");
            }

            // Step 2: build the ECharts option tree via the type registry.
            IChartOptionBuilder builder = rendererRegistry.getRenderer(chartModel.getType());
            Object option = builder.buildOption(chartModel, resolver, dataSets);
            applyBackground(option, options);

            if (progress != null) {
                progress.completeStep("Chart rendering completed");
                progress.reportProgress(75, "Converting to " + options.getFormat());
            }

            // Step 3: render through the GraalJS engine and write the bytes.
            byte[] bytes = render(option, options);
            out.write(bytes);

            if (progress != null) {
                progress.completeStep(options.getFormat().toUpperCase() + " conversion completed");
                progress.reportProgress(100, "Export completed");
            }
        } catch (NopException e) {
            LOG.error("Chart export failed", e);
            if (progress != null)
                progress.reportProgress(-1, "Export failed: " + e.getMessage());
            throw e; // adapter error codes must not be swallowed into render-failed
        } catch (Exception e) {
            LOG.error("Chart export failed", e);
            if (progress != null)
                progress.reportProgress(-1, "Export failed: " + e.getMessage());
            throw new NopException(ChartExportErrors.ERR_CHART_RENDER_FAILED, e)
                    .param(ChartExportErrors.ARG_REASON, e.getMessage());
        }
    }

    private byte[] render(Object option, ChartExportOptions options) {
        String optionJson = JsonTool.stringify(option);
        String format = options.getFormat();
        // 请求级超时（plan 2586）：经渲染链传参，不再改写引擎全局状态；≤0 用引擎默认
        long timeoutMs = options.getTimeoutSeconds() * 1000L;
        if (FORMAT_SVG.equalsIgnoreCase(format))
            return chartRenderer.renderSvg(optionJson, options.getWidth(), options.getHeight(), timeoutMs)
                    .getBytes(StandardCharsets.UTF_8);
        if (!FORMAT_PNG.equalsIgnoreCase(format))
            throw new NopException(ChartExportErrors.ERR_CHART_RENDER_FAILED)
                    .param(ChartExportErrors.ARG_REASON,
                            "unsupported export format '" + format + "' (png | svg)");
        return chartRenderer.renderPng(optionJson, options.getWidth(), options.getHeight(), timeoutMs);
    }

    private void validateNotCancelled(IProgressCallback progress) {
        if (progress != null && progress.isCancelled())
            throw new NopException(ChartExportErrors.ERR_CHART_RENDER_FAILED)
                    .param(ChartExportErrors.ARG_REASON, "Export cancelled by user");
    }

    private void applyBackground(Object option, ChartExportOptions options) {
        if (StringHelper.isEmpty(options.getBackgroundColor()))
            return;
        if (option instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) option;
            map.put("backgroundColor", options.getBackgroundColor());
        }
    }

    private void validateInputs(ChartModel chartModel, ICellRefResolver resolver) {
        if (chartModel == null)
            throw new NopException(ChartExportErrors.ERR_INVALID_CHART_MODEL)
                    .param(ChartExportErrors.ARG_CHART_MODEL, "null");
        if (resolver == null)
            throw new NopException(ChartExportErrors.ERR_INVALID_CHART_MODEL)
                    .param(ChartExportErrors.ARG_REASON, "resolver cannot be null");
        if (chartModel.getType() == null)
            throw new NopException(ChartExportErrors.ERR_UNSUPPORTED_CHART_TYPE)
                    .param(ChartExportErrors.ARG_CHART_TYPE, "null");
    }

    private void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0)
            throw new NopException(ChartExportErrors.ERR_INVALID_DIMENSIONS)
                    .param(ChartExportErrors.ARG_WIDTH, width)
                    .param(ChartExportErrors.ARG_HEIGHT, height);
        if ((long) width * height > MAX_CANVAS_PIXELS)
            throw new NopException(ChartExportErrors.ERR_INVALID_DIMENSIONS)
                    .param(ChartExportErrors.ARG_WIDTH, width)
                    .param(ChartExportErrors.ARG_HEIGHT, height)
                    .param(ChartExportErrors.ARG_REASON, "canvas pixel product " + (long) width * height
                            + " exceeds limit " + MAX_CANVAS_PIXELS);
    }
}
