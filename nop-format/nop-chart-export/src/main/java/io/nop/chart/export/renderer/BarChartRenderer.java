package io.nop.chart.export.renderer;

import io.nop.excel.resolver.ICellRefResolver;
import io.nop.excel.chart.util.ChartDataSet;
import io.nop.excel.chart.constants.ChartBarDirection;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartBarConfigModel;
import io.nop.excel.chart.model.ChartModel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
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
        ChartBarConfigModel barConfig = chartModel.getPlotArea().getBarConfig();
        
        // 确定方向
        PlotOrientation orientation = getPlotOrientation(barConfig);
        
        // 创建图表
        JFreeChart chart;
        if (barConfig.isStackedChart()) {
            chart = ChartFactory.createStackedBarChart(
                null, // title will be set later
                "Category", // category axis label
                "Value", // value axis label
                dataset,
                orientation,
                false, // legend
                false, // tooltips
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
                false, // tooltips
                false // urls
            );
        }
        
        // 应用柱状图特定配置
        applyBarConfiguration(chart, barConfig, chartModel);
        
        return chart;
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
     * 应用柱状图配置
     */
    private void applyBarConfiguration(JFreeChart chart, ChartBarConfigModel barConfig, ChartModel chartModel) {
        CategoryPlot plot = chart.getCategoryPlot();
        CategoryItemRenderer renderer = plot.getRenderer();
        
        // 确保使用BarRenderer
        if (!(renderer instanceof BarRenderer)) {
            BarRenderer barRenderer = new BarRenderer();
            plot.setRenderer(barRenderer);
            renderer = barRenderer;
        }
        
        BarRenderer barRenderer = (BarRenderer) renderer;
        
        // 应用间隙宽度
        if (barConfig.getGapWidth() != null) {
            double gapPercent = barConfig.getGapWidth() / 100.0;
            barRenderer.setItemMargin(gapPercent);
        }
        
        // 应用重叠设置
        if (barConfig.getOverlap() != null) {
            // JFreeChart中的重叠设置比较复杂，这里简化处理
            LOG.debug("Bar overlap configuration: {}", barConfig.getOverlap());
        }
        
        // 应用颜色变化 - 总是应用，除非明确设置为false
        boolean varyColors = true;
        if (barConfig.getVaryColors() != null) {
            varyColors = barConfig.getVaryColors();
        }
        
        if (varyColors) {
            // 为每个系列设置不同颜色
            applyVaryColors(renderer, chartModel);
        }
        
        // 应用3D效果
        if (Boolean.TRUE.equals(barConfig.getIs3D())) {
            // JFreeChart的3D效果需要使用不同的渲染器
            LOG.debug("3D bar chart effect requested");
        }
        
        // 设置图例可见（如果存在）
        if (chart.getLegend() != null) {
            chart.getLegend().setVisible(true);
        }
        
        LOG.debug("Applied bar chart configuration");
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