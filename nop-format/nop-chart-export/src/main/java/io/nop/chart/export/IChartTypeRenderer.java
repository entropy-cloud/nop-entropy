package io.nop.chart.export;

import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartModel;
import org.jfree.chart.JFreeChart;

/**
 * Interface for chart type specific renderers
 */
public interface IChartTypeRenderer {
    
    /**
     * 支持的图表类型
     * @return 图表类型
     */
    ChartType getSupportedType();
    
    /**
     * 渲染图表
     * @param chartModel 图表模型
     * @param resolver 数据解析器
     * @return JFreeChart对象
     */
    JFreeChart render(ChartModel chartModel, ICellRefResolver resolver);
    
    /**
     * 是否支持该图表类型
     * @param type 图表类型
     * @return true如果支持
     */
    boolean supports(ChartType type);
}