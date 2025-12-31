package io.nop.chart.export.renderer;

import io.nop.chart.export.ICellRefResolver;
import io.nop.chart.export.model.ChartDataSet;
import io.nop.core.type.utils.ConvertHelper;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartModel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYZDataset;
import org.jfree.data.xy.DefaultXYZDataset;

import java.util.List;

/**
 * Bubble chart renderer
 */
public class BubbleChartRenderer extends AbstractChartRenderer {
    
    @Override
    public ChartType getSupportedType() {
        return ChartType.BUBBLE;
    }
    
    @Override
    protected JFreeChart createChart(ChartModel chartModel, List<ChartDataSet> dataSets, ICellRefResolver resolver) {
        LOG.debug("Creating bubble chart with {} data sets", dataSets.size());
        
        // 创建数据集
        XYZDataset dataset = createXYZDataset(dataSets);
        
        // 创建气泡图
        JFreeChart chart = ChartFactory.createBubbleChart(
            null, // 标题将在样式应用时设置
            null, // X轴标签将在样式应用时设置
            null, // Y轴标签将在样式应用时设置
            dataset,
            org.jfree.chart.plot.PlotOrientation.VERTICAL,
            true, // 显示图例
            true, // 显示工具提示
            false // 不生成URL
        );
        
        // 应用气泡图特定配置
        applyBubbleConfig(chart, chartModel);
        
        return chart;
    }
    
    private XYZDataset createXYZDataset(List<ChartDataSet> dataSets) {
        DefaultXYZDataset dataset = new DefaultXYZDataset();
        
        for (int i = 0; i < dataSets.size(); i++) {
            ChartDataSet dataSet = dataSets.get(i);
            String seriesName = "Series " + (i + 1);
            
            List<Number> xValues = dataSet.getXValues();
            List<Number> yValues = dataSet.getValues();
            List<Number> zValues = dataSet.getBubbleSizes(); // 气泡大小
            
            if (zValues == null || zValues.isEmpty()) {
                // 如果没有气泡大小数据，使用默认大小
                zValues = java.util.Collections.nCopies(xValues.size(), 1.0);
            }
            
            int minSize = Math.min(Math.min(xValues.size(), yValues.size()), zValues.size());
            if (minSize > 0) {
                double[][] data = new double[3][minSize];
                
                for (int j = 0; j < minSize; j++) {
                    data[0][j] = ConvertHelper.convertTo(Double.class, xValues.get(j), 0.0); // X值
                    data[1][j] = ConvertHelper.convertTo(Double.class, yValues.get(j), 0.0); // Y值
                    data[2][j] = ConvertHelper.convertTo(Double.class, zValues.get(j), 1.0); // 气泡大小
                }
                
                dataset.addSeries(seriesName, data);
            }
        }
        
        return dataset;
    }
    
    private void applyBubbleConfig(JFreeChart chart, ChartModel chartModel) {
        // 应用气泡图特定配置
        if (chartModel.getPlotArea() != null && chartModel.getPlotArea().getBubbleConfig() != null) {
            // TODO: 应用气泡图特定配置，如气泡大小映射等
            LOG.debug("Applying bubble chart specific configuration");
        }
    }
}