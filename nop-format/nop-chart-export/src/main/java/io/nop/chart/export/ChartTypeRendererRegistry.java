package io.nop.chart.export;

import io.nop.api.core.exceptions.NopException;
import io.nop.chart.export.echarts.AreaChartOptionBuilder;
import io.nop.chart.export.echarts.BarChartOptionBuilder;
import io.nop.chart.export.echarts.BubbleChartOptionBuilder;
import io.nop.chart.export.echarts.ComboChartOptionBuilder;
import io.nop.chart.export.echarts.DoughnutChartOptionBuilder;
import io.nop.chart.export.echarts.HeatmapChartOptionBuilder;
import io.nop.chart.export.echarts.LineChartOptionBuilder;
import io.nop.chart.export.echarts.PieChartOptionBuilder;
import io.nop.chart.export.echarts.RadarChartOptionBuilder;
import io.nop.chart.export.echarts.ScatterChartOptionBuilder;
import io.nop.excel.chart.constants.ChartType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for chart type option builders (plan 2364 D31): the class name and the
 * getRenderer/isSupported/getSupportedTypes API shape are preserved; the registered
 * elements are ECharts option builders (the legacy renderer engine is retired).
 */
public class ChartTypeRendererRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(ChartTypeRendererRegistry.class);

    private static final ChartTypeRendererRegistry _default = createDefault();

    private final Map<ChartType, IChartOptionBuilder> renderers = new ConcurrentHashMap<>();

    private static ChartTypeRendererRegistry createDefault() {
        ChartTypeRendererRegistry registry = new ChartTypeRendererRegistry();
        registry.registerDefaultRenderers();
        return registry;
    }

    public static ChartTypeRendererRegistry getDefault() {
        return _default;
    }

    /**
     * 注册默认 option builder（覆盖 ChartType 全部 10 值）。
     */
    public void registerDefaultRenderers() {
        register(new BarChartOptionBuilder());
        register(new LineChartOptionBuilder());
        register(new PieChartOptionBuilder());
        register(new DoughnutChartOptionBuilder());

        register(new AreaChartOptionBuilder());
        register(new ScatterChartOptionBuilder());
        register(new BubbleChartOptionBuilder());

        register(new RadarChartOptionBuilder());
        register(new HeatmapChartOptionBuilder());
        register(new ComboChartOptionBuilder());
    }

    public void register(IChartOptionBuilder renderer) {
        if (renderer == null)
            throw new NopException(ChartExportErrors.ERR_INVALID_CHART_MODEL)
                    .param(ChartExportErrors.ARG_CHART_MODEL, "renderer cannot be null");

        ChartType type = renderer.getSupportedType();
        if (type == null)
            throw new NopException(ChartExportErrors.ERR_UNSUPPORTED_CHART_TYPE)
                    .param(ChartExportErrors.ARG_CHART_TYPE, "null");

        renderers.put(type, renderer);
        LOG.debug("Registered chart option builder for type: {}", type);
    }

    /**
     * 获取类型的 option builder。
     */
    public IChartOptionBuilder getRenderer(ChartType type) {
        if (type == null)
            throw new NopException(ChartExportErrors.ERR_UNSUPPORTED_CHART_TYPE)
                    .param(ChartExportErrors.ARG_CHART_TYPE, "null");

        IChartOptionBuilder renderer = renderers.get(type);
        if (renderer == null)
            throw new NopException(ChartExportErrors.ERR_UNSUPPORTED_CHART_TYPE)
                    .param(ChartExportErrors.ARG_CHART_TYPE, type);

        return renderer;
    }

    public boolean isSupported(ChartType type) {
        return type != null && renderers.containsKey(type);
    }

    public Set<ChartType> getSupportedTypes() {
        return renderers.keySet();
    }
}
