package io.nop.chart.export.renderer;

import io.nop.chart.export.ICellRefResolver;
import io.nop.chart.export.model.ChartDataSet;
import io.nop.chart.export.utils.JFreeChartStyleAdapter;
import io.nop.excel.chart.constants.ChartBarDirection;
import io.nop.excel.chart.constants.ChartBarGrouping;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartBarConfigModel;
import io.nop.excel.chart.model.ChartModel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.chart.renderer.category.StackedBarRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;

import java.awt.*;
import java.util.List;

/**
 * Bar chart renderer
 */
public class BarChartRenderer extends AbstractChartRenderer {
    
    @Override
    public ChartType getSupportedType() {
        return ChartType.BAR;
    }
    
    @Override
    protected JFreeChart createChart(ChartModel chartModel, List<ChartDataSet> dataSets, ICellRefResolver resolver) {
        // 创建数据集
        CategoryDataset dataset = createCategoryDataset(dataSets);
        
        // 获取柱状图配置
        ChartBarConfigModel barConfig = chartModel.getPlotArea() != null ? 
            chartModel.getPlotArea().getBarConfig() : null;
        
        // 确定方向
        PlotOrientation orientation = getPlotOrientation(barConfig);
        
        // 创建图表
        JFreeChart chart;
        if (isStackedChart(barConfig)) {
            chart = ChartFactory.createStackedBarChart(
                null, // title will be set later
                "Category", // category axis label
                "Value", // value axis label
                dataset,
                orientation,
                false, // legend
                true, // tooltips
                false // urls
            );
        } else {
            chart = ChartFactory.createBarChart(
                null, // title will be set later
                "Category", // category axis label
                "Value", // value axis label
                dataset,
                orientation,
                false, // legend
                true, // tooltips
                false // urls
            );
        }
        
        // 应用柱状图特定配置
        applyBarConfiguration(chart, barConfig);
        
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
                        dataset.addValue(value, seriesName, "Category " + (i + 1));
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
     * 获取绘图方向
     */
    private PlotOrientation getPlotOrientation(ChartBarConfigModel barConfig) {
        if (barConfig != null && barConfig.getDir() == ChartBarDirection.BAR) {
            return PlotOrientation.HORIZONTAL;
        }
        return PlotOrientation.VERTICAL;
    }
    
    /**
     * 检查是否为堆积图表
     */
    private boolean isStackedChart(ChartBarConfigModel barConfig) {
        if (barConfig != null && barConfig.getGrouping() != null) {
            return barConfig.getGrouping() == ChartBarGrouping.STACKED || 
                   barConfig.getGrouping() == ChartBarGrouping.PERCENT_STACKED;
        }
        return false;
    }
    
    /**
     * 应用柱状图配置
     */
    private void applyBarConfiguration(JFreeChart chart, ChartBarConfigModel barConfig) {
        if (barConfig == null) {
            return;
        }
        
        CategoryPlot plot = chart.getCategoryPlot();
        CategoryItemRenderer renderer = plot.getRenderer();
        
        // 应用间隙宽度
        if (barConfig.getGapWidth() != null && renderer instanceof BarRenderer) {
            BarRenderer barRenderer = (BarRenderer) renderer;
            double gapPercent = barConfig.getGapWidth() / 100.0;
            barRenderer.setItemMargin(gapPercent);
        }
        
        // 应用重叠设置
        if (barConfig.getOverlap() != null && renderer instanceof BarRenderer) {
            // JFreeChart中的重叠设置比较复杂，这里简化处理
            LOG.debug("Bar overlap configuration: {}", barConfig.getOverlap());
        }
        
        // 应用颜色变化
        if (Boolean.TRUE.equals(barConfig.getVaryColors())) {
            // 为每个系列设置不同颜色
            applyVaryColors(renderer);
        }
        
        // 应用3D效果
        if (Boolean.TRUE.equals(barConfig.getIs3D())) {
            // JFreeChart的3D效果需要使用不同的渲染器
            LOG.debug("3D bar chart effect requested");
        }
    }
    
    /**
     * 应用颜色变化
     */
    private void applyVaryColors(CategoryItemRenderer renderer) {
        // 设置默认颜色序列
        Color[] colors = {
            Color.BLUE, Color.RED, Color.GREEN, Color.ORANGE, 
            Color.MAGENTA, Color.CYAN, Color.PINK, Color.YELLOW
        };
        
        for (int i = 0; i < colors.length; i++) {
            renderer.setSeriesPaint(i, colors[i % colors.length]);
        }
    }
}