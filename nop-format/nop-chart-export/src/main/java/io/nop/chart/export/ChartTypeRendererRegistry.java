package io.nop.chart.export;

import io.nop.api.core.exceptions.NopException;
import io.nop.chart.export.renderer.AreaChartRenderer;
import io.nop.chart.export.renderer.BarChartRenderer;
import io.nop.chart.export.renderer.BubbleChartRenderer;
import io.nop.chart.export.renderer.ComboChartRenderer;
import io.nop.chart.export.renderer.DoughnutChartRenderer;
import io.nop.chart.export.renderer.HeatmapChartRenderer;
import io.nop.chart.export.renderer.LineChartRenderer;
import io.nop.chart.export.renderer.PieChartRenderer;
import io.nop.chart.export.renderer.RadarChartRenderer;
import io.nop.chart.export.renderer.ScatterChartRenderer;
import io.nop.excel.chart.constants.ChartType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for chart type renderers
 */
public class ChartTypeRendererRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(ChartTypeRendererRegistry.class);

    private static final ChartTypeRendererRegistry _default = createDefault();
    
    private final Map<ChartType, IChartTypeRenderer> renderers = new ConcurrentHashMap<>();
    
    /**
     * 创建带有默认渲染器的注册表
     * @return 注册表实例
     */
    private static ChartTypeRendererRegistry createDefault() {
        ChartTypeRendererRegistry registry = new ChartTypeRendererRegistry();
        registry.registerDefaultRenderers();
        return registry;
    }

    public static ChartTypeRendererRegistry getDefault(){
        return _default;
    }
    
    /**
     * 注册默认渲染器
     */
    public void registerDefaultRenderers() {
        // 基础图表类型
        register(new BarChartRenderer());
        register(new LineChartRenderer());
        register(new PieChartRenderer());
        register(new DoughnutChartRenderer());
        
        // 高级图表类型
        register(new AreaChartRenderer());
        register(new ScatterChartRenderer());
        register(new BubbleChartRenderer());
        
        // 特殊图表类型
        register(new RadarChartRenderer());
        register(new HeatmapChartRenderer());
        register(new ComboChartRenderer());
    }
    
    /**
     * 注册渲染器
     * @param renderer 渲染器实例
     */
    public void register(IChartTypeRenderer renderer) {
        if (renderer == null) {
            throw new NopException(ChartExportErrors.ERR_INVALID_CHART_MODEL)
                .param(ChartExportErrors.ARG_CHART_MODEL, "renderer cannot be null");
        }
        
        ChartType type = renderer.getSupportedType();
        if (type == null) {
            throw new NopException(ChartExportErrors.ERR_UNSUPPORTED_CHART_TYPE)
                .param(ChartExportErrors.ARG_CHART_TYPE, "null");
        }
        
        renderers.put(type, renderer);
        LOG.debug("Registered chart renderer for type: {}", type);
    }
    
    /**
     * 获取渲染器
     * @param type 图表类型
     * @return 渲染器实例
     */
    public IChartTypeRenderer getRenderer(ChartType type) {
        if (type == null) {
            throw new NopException(ChartExportErrors.ERR_UNSUPPORTED_CHART_TYPE)
                .param(ChartExportErrors.ARG_CHART_TYPE, "null");
        }
        
        IChartTypeRenderer renderer = renderers.get(type);
        if (renderer == null) {
            throw new NopException(ChartExportErrors.ERR_UNSUPPORTED_CHART_TYPE)
                .param(ChartExportErrors.ARG_CHART_TYPE, type);
        }
        
        return renderer;
    }
    
    /**
     * 检查是否支持指定类型
     * @param type 图表类型
     * @return true如果支持
     */
    public boolean isSupported(ChartType type) {
        return type != null && renderers.containsKey(type);
    }
    
    /**
     * 获取所有支持的类型
     * @return 支持的类型集合
     */
    public java.util.Set<ChartType> getSupportedTypes() {
        return renderers.keySet();
    }
}