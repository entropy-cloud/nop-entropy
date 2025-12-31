package io.nop.chart.export.renderer;

import io.nop.api.core.exceptions.NopException;
import io.nop.chart.export.ICellRefResolver;
import io.nop.chart.export.model.ChartDataSet;
import io.nop.core.type.utils.ConvertHelper;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartDataPointModel;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.model.ChartPieConfigModel;
import io.nop.excel.chart.model.ChartSeriesModel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PiePlot;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.general.PieDataset;

import java.awt.*;
import java.util.List;

/**
 * Pie chart renderer
 */
public class PieChartRenderer extends AbstractChartRenderer {
    
    @Override
    public ChartType getSupportedType() {
        return ChartType.PIE;
    }
    
    @Override
    protected JFreeChart createChart(ChartModel chartModel, List<ChartDataSet> dataSets, ICellRefResolver resolver) {
        // 饼图通常只使用第一个数据系列
        PieDataset dataset = createPieDataset(dataSets);
        
        // 获取饼图配置
        ChartPieConfigModel pieConfig = chartModel.getPlotArea() != null ? 
            chartModel.getPlotArea().getPieConfig() : null;
        
        // 创建图表
        JFreeChart chart;
        if (Boolean.TRUE.equals(pieConfig != null ? pieConfig.getIs3D() : false)) {
            chart = ChartFactory.createPieChart3D(
                null, // title will be set later
                dataset,
                false, // legend
                true, // tooltips
                false // urls
            );
        } else {
            chart = ChartFactory.createPieChart(
                null, // title will be set later
                dataset,
                false, // legend
                true, // tooltips
                false // urls
            );
        }
        
        // 应用饼图特定配置
        applyPieConfiguration(chart, pieConfig, dataSets);
        
        return chart;
    }
    
    /**
     * 创建饼图数据集
     */
    protected PieDataset createPieDataset(List<ChartDataSet> dataSets) {
        DefaultPieDataset dataset = new DefaultPieDataset();
        
        if (dataSets.isEmpty()) {
            return dataset;
        }
        
        // 使用第一个数据系列
        ChartDataSet dataSet = dataSets.get(0);
        List<Number> values = dataSet.getValues();
        List<Object> categories = dataSet.getCategories();
        
        if (values == null || values.isEmpty()) {
            return dataset;
        }
        
        // 如果没有分类，使用索引
        if (categories == null || categories.isEmpty()) {
            for (int i = 0; i < values.size(); i++) {
                Number value = values.get(i);
                if (value != null && value.doubleValue() > 0) {
                    dataset.setValue("Slice " + (i + 1), value);
                }
            }
        } else {
            // 使用提供的分类
            int maxSize = Math.min(values.size(), categories.size());
            for (int i = 0; i < maxSize; i++) {
                Number value = values.get(i);
                Object category = categories.get(i);
                if (value != null && category != null && value.doubleValue() > 0) {
                    dataset.setValue(category.toString(), value);
                }
            }
        }
        
        return dataset;
    }
    
    /**
     * 应用饼图配置
     */
    protected void applyPieConfig(JFreeChart chart, ChartModel chartModel) {
        ChartPieConfigModel pieConfig = chartModel.getPlotArea() != null ? 
            chartModel.getPlotArea().getPieConfig() : null;
        
        // 从数据集获取信息
        List<ChartDataSet> dataSets = java.util.Collections.emptyList(); // 简化处理
        applyPieConfiguration(chart, pieConfig, dataSets);
    }
    
    /**
     * 应用饼图配置
     */
    private void applyPieConfiguration(JFreeChart chart, ChartPieConfigModel pieConfig, List<ChartDataSet> dataSets) {
        PiePlot plot = (PiePlot) chart.getPlot();
        
        if (pieConfig != null) {
            // 应用起始角度
            if (pieConfig.getStartAngle() != null) {
                Double startAngleDouble = ConvertHelper.convertTo(Double.class, pieConfig.getStartAngle(), 0.0);
                double startAngle = startAngleDouble != null ? startAngleDouble : 0.0;
                plot.setStartAngle(startAngle);
            }
            
            // 应用颜色变化
            if (Boolean.TRUE.equals(pieConfig.getVaryColors())) {
                applyVaryColors(plot);
            }
        }
        
        // 应用扇形分离效果
        applyExplosion(plot, dataSets);
        
        // 设置默认样式
        plot.setLabelGenerator(null); // 不显示标签，避免重叠
        plot.setCircular(true);
        plot.setLabelGap(0.02);
    }
    
    /**
     * 应用颜色变化
     */
    private void applyVaryColors(PiePlot plot) {
        // 设置默认颜色序列
        Color[] colors = {
            Color.BLUE, Color.RED, Color.GREEN, Color.ORANGE, 
            Color.MAGENTA, Color.CYAN, Color.PINK, Color.YELLOW,
            Color.LIGHT_GRAY, Color.DARK_GRAY
        };
        
        PieDataset dataset = plot.getDataset();
        if (dataset != null) {
            for (int i = 0; i < dataset.getItemCount() && i < colors.length; i++) {
                Comparable key = dataset.getKey(i);
                plot.setSectionPaint(key, colors[i % colors.length]);
            }
        }
    }
    
    /**
     * 应用扇形分离效果
     */
    private void applyExplosion(PiePlot plot, List<ChartDataSet> dataSets) {
        if (dataSets.isEmpty()) {
            return;
        }
        
        ChartDataSet dataSet = dataSets.get(0);
        ChartSeriesModel seriesModel = dataSet.getSeriesModel();
        
        if (seriesModel != null && seriesModel.getDataPoints() != null) {
            PieDataset dataset = plot.getDataset();
            
            for (ChartDataPointModel dataPoint : seriesModel.getDataPoints()) {
                if (dataPoint.getExplosion() != null) {
                    Double explosionDouble = ConvertHelper.convertTo(Double.class, dataPoint.getExplosion(), 0.0);
                    double explosion = explosionDouble != null ? explosionDouble : 0.0;
                    if (explosion > 0 && dataset != null) {
                        int index = dataPoint.getIndex();
                        if (index >= 0 && index < dataset.getItemCount()) {
                            Comparable key = dataset.getKey(index);
                            plot.setExplodePercent(key, explosion);
                        }
                    }
                }
            }
        }
    }
}