package io.nop.chart.export.renderer;

import io.nop.chart.export.ICellRefResolver;
import io.nop.chart.export.model.ChartDataSet;
import io.nop.core.type.utils.ConvertHelper;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartModel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYBlockRenderer;
import org.jfree.data.xy.DefaultXYZDataset;
import org.jfree.data.xy.XYZDataset;

import java.awt.*;
import java.util.List;

/**
 * Heatmap chart renderer
 */
public class HeatmapChartRenderer extends AbstractChartRenderer {
    
    @Override
    public ChartType getSupportedType() {
        return ChartType.HEATMAP;
    }
    
    @Override
    protected JFreeChart createChart(ChartModel chartModel, List<ChartDataSet> dataSets, ICellRefResolver resolver) {
        LOG.debug("Creating heatmap chart with {} data sets", dataSets.size());
        
        // 创建数据集
        XYZDataset dataset = createXYZDataset(dataSets);
        
        // 创建热力图
        NumberAxis xAxis = new NumberAxis("X");
        NumberAxis yAxis = new NumberAxis("Y");
        XYBlockRenderer renderer = new XYBlockRenderer();
        
        XYPlot plot = new XYPlot(dataset, xAxis, yAxis, renderer);
        JFreeChart chart = new JFreeChart(null, JFreeChart.DEFAULT_TITLE_FONT, plot, true);
        
        // 应用热力图特定配置
        applyHeatmapConfig(chart, chartModel);
        
        return chart;
    }
    
    private XYZDataset createXYZDataset(List<ChartDataSet> dataSets) {
        DefaultXYZDataset dataset = new DefaultXYZDataset();
        
        for (int i = 0; i < dataSets.size(); i++) {
            ChartDataSet dataSet = dataSets.get(i);
            String seriesName = "Series " + (i + 1);
            
            List<Number> xValues = dataSet.getXValues();
            List<Number> yValues = dataSet.getValues();
            List<Number> zValues = dataSet.getHeatmapValues(); // 热力值
            
            if (zValues == null || zValues.isEmpty()) {
                // 如果没有热力值数据，使用Y值作为热力值
                zValues = yValues;
            }
            
            int minSize = Math.min(Math.min(xValues.size(), yValues.size()), zValues.size());
            if (minSize > 0) {
                double[][] data = new double[3][minSize];
                
                for (int j = 0; j < minSize; j++) {
                    data[0][j] = ConvertHelper.convertTo(Double.class, xValues.get(j), 0.0); // X值
                    data[1][j] = ConvertHelper.convertTo(Double.class, yValues.get(j), 0.0); // Y值
                    data[2][j] = ConvertHelper.convertTo(Double.class, zValues.get(j), 0.0); // 热力值
                }
                
                dataset.addSeries(seriesName, data);
            }
        }
        
        return dataset;
    }
    
    private void applyHeatmapConfig(JFreeChart chart, ChartModel chartModel) {
        // 应用热力图特定配置
        if (chartModel.getPlotArea() != null && chartModel.getPlotArea().getHeatmapConfig() != null) {
            XYPlot plot = (XYPlot) chart.getPlot();
            XYBlockRenderer renderer = (XYBlockRenderer) plot.getRenderer();
            
            // 设置块大小
            renderer.setBlockWidth(1.0);
            renderer.setBlockHeight(1.0);
            
            // 设置颜色映射
            setupColorMapping(renderer);
            
            // TODO: 从配置中读取更多热力图特定设置
            LOG.debug("Applying heatmap chart specific configuration");
        }
    }
    
    private void setupColorMapping(XYBlockRenderer renderer) {
        // 设置简单的颜色映射：蓝色(低值) -> 红色(高值)
        Color[] colors = {
            Color.BLUE,
            Color.CYAN,
            Color.GREEN,
            Color.YELLOW,
            Color.ORANGE,
            Color.RED
        };
        
        // TODO: 实现更复杂的颜色映射逻辑
        LOG.debug("Setting up color mapping for heatmap");
    }
}