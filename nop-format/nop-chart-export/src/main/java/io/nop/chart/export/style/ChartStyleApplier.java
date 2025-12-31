package io.nop.chart.export.style;

import io.nop.chart.export.ICellRefResolver;
import io.nop.chart.export.renderer.ChartAxisRenderer;
import io.nop.chart.export.renderer.ChartLegendRenderer;
import io.nop.chart.export.renderer.ChartTitleRenderer;
import io.nop.excel.chart.model.ChartAxisModel;
import io.nop.excel.chart.model.ChartModel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.XYPlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified chart style applier
 */
public class ChartStyleApplier {
    private static final Logger LOG = LoggerFactory.getLogger(ChartStyleApplier.class);
    
    private final ChartTitleRenderer titleRenderer;
    private final ChartLegendRenderer legendRenderer;
    private final ChartAxisRenderer axisRenderer;
    private final ChartShapeStyleProcessor shapeStyleProcessor;
    private final ChartTextStyleProcessor textStyleProcessor;
    
    public ChartStyleApplier() {
        this.titleRenderer = new ChartTitleRenderer();
        this.legendRenderer = new ChartLegendRenderer();
        this.axisRenderer = new ChartAxisRenderer();
        this.shapeStyleProcessor = new ChartShapeStyleProcessor();
        this.textStyleProcessor = new ChartTextStyleProcessor();
    }
    
    /**
     * 应用所有图表样式
     * @param chart JFreeChart对象
     * @param chartModel 图表模型
     * @param resolver 数据解析器
     */
    public void applyAllStyles(JFreeChart chart, ChartModel chartModel, ICellRefResolver resolver) {
        LOG.debug("Applying all chart styles");
        
        // 应用标题样式
        applyTitleStyle(chart, chartModel, resolver);
        
        // 应用图例样式
        applyLegendStyle(chart, chartModel);
        
        // 应用坐标轴样式
        applyAxisStyles(chart, chartModel, resolver);
        
        // 应用图表背景和边框样式
        applyChartBackgroundStyle(chart, chartModel);
        
        // 应用绘图区样式
        applyPlotAreaStyle(chart, chartModel);
    }
    
    /**
     * 应用标题样式
     */
    public void applyTitleStyle(JFreeChart chart, ChartModel chartModel, ICellRefResolver resolver) {
        titleRenderer.renderTitle(chart, chartModel.getTitle(), resolver);
    }
    
    /**
     * 应用图例样式
     */
    public void applyLegendStyle(JFreeChart chart, ChartModel chartModel) {
        legendRenderer.renderLegend(chart, chartModel.getLegend());
    }
    
    /**
     * 应用坐标轴样式
     */
    public void applyAxisStyles(JFreeChart chart, ChartModel chartModel, ICellRefResolver resolver) {
        Plot plot = chart.getPlot();
        
        if (chartModel.getPlotArea() != null) {
            // 应用X轴样式 - 查找分类轴
            ChartAxisModel categoryAxis = chartModel.getPlotArea().getAxis("categoryAxis");
            if (categoryAxis != null) {
                axisRenderer.renderAxis(plot, categoryAxis, resolver, true);
            }
            
            // 应用Y轴样式 - 查找数值轴
            ChartAxisModel valueAxis = chartModel.getPlotArea().getAxis("valueAxis");
            if (valueAxis != null) {
                axisRenderer.renderAxis(plot, valueAxis, resolver, false);
            }
        }
    }
    
    /**
     * 应用图表背景样式
     */
    public void applyChartBackgroundStyle(JFreeChart chart, ChartModel chartModel) {
        if (chartModel.getShapeStyle() != null) {
            shapeStyleProcessor.applyBackgroundStyle(chart, chartModel.getShapeStyle());
        }
    }
    
    /**
     * 应用绘图区样式
     */
    public void applyPlotAreaStyle(JFreeChart chart, ChartModel chartModel) {
        if (chartModel.getPlotArea() != null && chartModel.getPlotArea().getShapeStyle() != null) {
            shapeStyleProcessor.applyPlotAreaStyle(chart.getPlot(), chartModel.getPlotArea().getShapeStyle());
        }
    }
    
    /**
     * 应用系列样式
     * @param chart JFreeChart对象
     * @param seriesIndex 系列索引
     * @param shapeStyle 形状样式
     */
    public void applySeriesStyle(JFreeChart chart, int seriesIndex, io.nop.excel.chart.model.ChartShapeStyleModel shapeStyle) {
        if (shapeStyle == null) {
            return;
        }
        
        Plot plot = chart.getPlot();
        shapeStyleProcessor.applySeriesStyle(plot, seriesIndex, shapeStyle);
    }
}