package io.nop.chart.export.renderer;

import io.nop.chart.export.ICellRefResolver;
import io.nop.chart.export.model.ChartDataSet;
import io.nop.api.core.convert.ConvertHelper;
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
                    Number xNum = xValues.get(j);
                    Number yNum = yValues.get(j);
                    if (xNum != null && yNum != null) {
                        double x = xNum.doubleValue();
                        double y = yNum.doubleValue();
                        series.add(x, y);
                    }
                } catch (Exception e) {
                    LOG.debug("Failed to add data point at index {}", j, e);
                }
            }
            
            collection.addSeries(series);
        }
        
        return collection;
    }
    
    private void applyScatterConfig(JFreeChart chart, ChartModel chartModel) {
        // 应用散点图特定配置
        if (chartModel.getPlotArea() != null && chartModel.getPlotArea().getScatterConfig() != null) {
            org.jfree.chart.plot.XYPlot plot = chart.getXYPlot();
            org.jfree.chart.renderer.xy.XYItemRenderer renderer = plot.getRenderer();
            
            // 获取散点图配置
            io.nop.excel.chart.model.ChartScatterConfigModel scatterConfig = chartModel.getPlotArea().getScatterConfig();
            
            // 应用标记大小配置
            if (scatterConfig.getMarkerSize() != null) {
                Double markerSizeDouble = scatterConfig.getMarkerSize();
                double markerSize = markerSizeDouble != null ? markerSizeDouble : 5.0;
                // 设置标记点形状和大小
                java.awt.Shape markerShape = new java.awt.geom.Ellipse2D.Double(-markerSize/2, -markerSize/2, markerSize, markerSize);
                if (renderer instanceof org.jfree.chart.renderer.xy.XYLineAndShapeRenderer) {
                    ((org.jfree.chart.renderer.xy.XYLineAndShapeRenderer) renderer).setDefaultShape(markerShape);
                }
            }
            
            LOG.debug("Applied scatter chart specific configuration");
        }
    }
}