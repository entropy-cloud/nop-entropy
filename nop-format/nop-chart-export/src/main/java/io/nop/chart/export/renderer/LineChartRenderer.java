package io.nop.chart.export.renderer;

import io.nop.excel.resolver.ICellRefResolver;
import io.nop.excel.chart.util.ChartDataSet;
import io.nop.excel.chart.constants.ChartBarGrouping;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartLineConfigModel;
import io.nop.excel.chart.model.ChartModel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.util.List;

/**
 * Line chart renderer
 */
public class LineChartRenderer extends AbstractChartRenderer {
    
    @Override
    public ChartType getSupportedType() {
        return ChartType.LINE;
    }
    
    @Override
    protected JFreeChart createChart(ChartModel chartModel, List<ChartDataSet> dataSets, ICellRefResolver resolver) {
        // 创建数据集
        CategoryDataset dataset = createCategoryDataset(dataSets);
        
        // 获取折线图配置
        ChartLineConfigModel lineConfig = chartModel.getPlotArea() != null ? 
            chartModel.getPlotArea().getLineConfig() : null;
        
        // 创建图表
        JFreeChart chart;
        if (isStackedChart(lineConfig)) {
            chart = ChartFactory.createStackedAreaChart(
                null, // title will be set later
                "Category", // category axis label
                "Value", // value axis label
                dataset,
                PlotOrientation.VERTICAL,
                false, // legend
                true, // tooltips
                false // urls
            );
        } else {
            chart = ChartFactory.createLineChart(
                null, // title will be set later
                "Category", // category axis label
                "Value", // value axis label
                dataset,
                PlotOrientation.VERTICAL,
                false, // legend
                true, // tooltips
                false // urls
            );
        }
        
        // 应用折线图特定配置
        applyLineConfiguration(chart, lineConfig, chartModel);
        
        return chart;
    }
    
    /**
     * 创建分类数据集
     */
    private CategoryDataset createCategoryDataset(List<ChartDataSet> dataSets) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        for (ChartDataSet dataSet : dataSets) {
            String seriesName = dataSet.getName();
            List<Number> values = dataSet.getValues();
            List<Object> categories = dataSet.getCategories();
            
            if (values == null || values.isEmpty()) {
                continue;
            }
            
            // 如果没有分类，使用索引
            if (categories == null || categories.isEmpty()) {
                for (int i = 0; i < values.size(); i++) {
                    Number value = values.get(i);
                    if (value != null) {
                        dataset.addValue(value, seriesName, "Point " + (i + 1));
                    }
                }
            } else {
                // 使用提供的分类
                int maxSize = Math.min(values.size(), categories.size());
                for (int i = 0; i < maxSize; i++) {
                    Number value = values.get(i);
                    Object category = categories.get(i);
                    if (value != null && category != null) {
                        dataset.addValue(value, seriesName, category.toString());
                    }
                }
            }
        }
        
        return dataset;
    }
    
    /**
     * 检查是否为堆积图表
     */
    private boolean isStackedChart(ChartLineConfigModel lineConfig) {
        if (lineConfig != null && lineConfig.getGrouping() != null) {
            return lineConfig.getGrouping() == ChartBarGrouping.STACKED || 
                   lineConfig.getGrouping() == ChartBarGrouping.PERCENT_STACKED;
        }
        return false;
    }
    
    /**
     * 应用折线图配置
     */
    private void applyLineConfiguration(JFreeChart chart, ChartLineConfigModel lineConfig, ChartModel chartModel) {
        if (lineConfig == null) {
            return;
        }
        
        CategoryPlot plot = chart.getCategoryPlot();
        CategoryItemRenderer renderer = plot.getRenderer();
        
        if (renderer instanceof LineAndShapeRenderer) {
            LineAndShapeRenderer lineRenderer = (LineAndShapeRenderer) renderer;
            
            // 应用标记点设置
            boolean showMarkers = Boolean.TRUE.equals(lineConfig.getMarker());
            lineRenderer.setDefaultShapesVisible(showMarkers);
            
            if (showMarkers) {
                // 设置标记点形状
                Shape markerShape = new Ellipse2D.Double(-3, -3, 6, 6);
                for (int i = 0; i < 10; i++) { // 设置前10个系列
                    lineRenderer.setSeriesShape(i, markerShape);
                }
            }
            
            // 应用平滑曲线设置
            // JFreeChart没有直接的平滑曲线支持，这里记录配置
            if (Boolean.TRUE.equals(lineConfig.getSmooth())) {
                LOG.debug("Smooth line configuration requested");
            }
            
            // 应用线条可见性
            lineRenderer.setDefaultLinesVisible(true);
        }
        
        // 应用颜色变化
        if (Boolean.TRUE.equals(lineConfig.getVaryColors())) {
            applyVaryColors(renderer, chartModel);
        }
        
        // 应用垂直线设置
        if (Boolean.TRUE.equals(lineConfig.getDropLines())) {
            LOG.debug("Drop lines configuration requested");
            // JFreeChart中需要自定义实现垂直线
        }
        
        // 应用高低线设置
        if (Boolean.TRUE.equals(lineConfig.getHiLowLines())) {
            LOG.debug("Hi-low lines configuration requested");
        }
        
        // 应用涨跌柱设置
        if (Boolean.TRUE.equals(lineConfig.getUpDownBars())) {
            LOG.debug("Up-down bars configuration requested");
        }
    }
    
    /**
     * 应用颜色变化
     */
    private void applyVaryColors(CategoryItemRenderer renderer, ChartModel chartModel) {
        String[] colorStrings = null;
        
        // 从图表模型的style.colors中获取颜色序列
        if (chartModel != null && chartModel.getStyle() != null) {
            java.util.List<String> colors = chartModel.getStyle().getColors();
            if (colors != null && !colors.isEmpty()) {
                colorStrings = colors.toArray(new String[colors.size()]);
                LOG.debug("Using custom colors from chart style: {}", (Object) colorStrings);
            }
        }
        
        // 如果没有自定义颜色，则使用默认的颜色序列
        if (colorStrings == null || colorStrings.length == 0) {
            colorStrings = new String[] {
                "#4472C4", "#ED7D31", "#A5A5A5", "#FFC000", 
                "#5B9BD5", "#70AD47", "#2F5597", "#9E480E",
                "#636363", "#997300"
            };
            LOG.debug("Using default colors: {}", (Object) colorStrings);
        }
        
        // 应用颜色到每个系列
        for (int i = 0; i < colorStrings.length; i++) {
            String colorStr = colorStrings[i];
            Color color = java.awt.Color.decode(colorStr);
            renderer.setSeriesPaint(i, color);
        }
    }
}