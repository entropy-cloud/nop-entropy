package io.nop.chart.export.renderer;

import io.nop.chart.export.ICellRefResolver;
import io.nop.chart.export.model.ChartDataSet;
import io.nop.core.type.utils.ConvertHelper;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartModel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.util.List;

/**
 * Scatter chart renderer
 */
public class ScatterChartRenderer extends AbstractChartRenderer {
    
    @Override
    public ChartType getSupportedType() {
        return ChartType.SCATTER;
    }
    
    @Override
    protected JFreeChart createChart(ChartModel chartModel, List<ChartDataSet> dataSets, ICellRefResolver resolver) {
        LOG.debug("Creating scatter chart with {} data sets", dataSets.size());
        
        // 创建数据集
        XYDataset dataset = createXYDataset(dataSets);
        
        // 创建散点图
        JFreeChart chart = ChartFactory.createScatterPlot(
            null, // 标题将在样式应用时设置
            null, // X轴标签将在样式应用时设置
            null, // Y轴标签将在样式应用时设置
            dataset,
            org.jfree.chart.plot.PlotOrientation.VERTICAL,
            true, // 显示图例
            true, // 显示工具提示
            false // 不生成URL
        );
        
        // 应用散点图特定配置
        applyScatterConfig(chart, chartModel);
        
        return chart;
    }
    
    private XYDataset createXYDataset(List<ChartDataSet> dataSets) {
        XYSeriesCollection collection = new XYSeriesCollection();
        
        for (int i = 0; i < dataSets.size(); i++) {
            ChartDataSet dataSet = dataSets.get(i);
            XYSeries series = new XYSeries("Series " + (i + 1));
            
            // 添加数据点
            List<Number> xValues = dataSet.getXValues();
            List<Number> yValues = dataSet.getValues();
            
            int minSize = Math.min(xValues.size(), yValues.size());
            for (int j = 0; j < minSize; j++) {
                try {
                    double x = ConvertHelper.convertTo(Double.class, xValues.get(j), 0.0);
                    double y = ConvertHelper.convertTo(Double.class, yValues.get(j), 0.0);
                    series.add(x, y);
                } catch (Exception e) {
                    LOG.warn("Failed to convert data point at index {}: x={}, y={}", j, xValues.get(j), yValues.get(j));
                }
            }
            
            collection.addSeries(series);
        }
        
        return collection;
    }
    
    private void applyScatterConfig(JFreeChart chart, ChartModel chartModel) {
        // 应用散点图特定配置
        if (chartModel.getPlotArea() != null && chartModel.getPlotArea().getScatterConfig() != null) {
            // TODO: 应用散点图特定配置，如标记样式等
            LOG.debug("Applying scatter chart specific configuration");
        }
    }
}