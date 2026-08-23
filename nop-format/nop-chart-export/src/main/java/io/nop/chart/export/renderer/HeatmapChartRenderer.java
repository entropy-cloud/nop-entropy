package io.nop.chart.export.renderer;

import io.nop.excel.resolver.ICellRefResolver;
import io.nop.excel.chart.util.ChartDataSet;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartModel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.LookupPaintScale;
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
        applyHeatmapConfig(chart, chartModel, dataset);

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
            // 先统计非null数据点，避免null以0.0占位渲染出(0,0)幽灵数据
            int count = 0;
            for (int j = 0; j < minSize; j++) {
                if (xValues.get(j) != null && yValues.get(j) != null && zValues.get(j) != null)
                    count++;
            }

            if (count > 0) {
                double[][] data = new double[3][count];

                int k = 0;
                for (int j = 0; j < minSize; j++) {
                    Number xNum = xValues.get(j);
                    Number yNum = yValues.get(j);
                    Number zNum = zValues.get(j);
                    if (xNum != null && yNum != null && zNum != null) {
                        data[0][k] = xNum.doubleValue(); // X值
                        data[1][k] = yNum.doubleValue(); // Y值
                        data[2][k] = zNum.doubleValue(); // 热力值
                        k++;
                    }
                }

                dataset.addSeries(seriesName, data);
            }
        }
        
        return dataset;
    }
    
    private void applyHeatmapConfig(JFreeChart chart, ChartModel chartModel, XYZDataset dataset) {
        // 应用热力图特定配置
        if (chartModel.getPlotArea() != null && chartModel.getPlotArea().getHeatmapConfig() != null) {
            XYPlot plot = (XYPlot) chart.getPlot();
            XYBlockRenderer renderer = (XYBlockRenderer) plot.getRenderer();

            // 设置块大小
            renderer.setBlockWidth(1.0);
            renderer.setBlockHeight(1.0);

            // 设置颜色映射
            setupColorMapping(renderer, dataset);

            // TODO: 从配置中读取更多热力图特定设置
            LOG.debug("Applying heatmap chart specific configuration");
        }
    }

    private void setupColorMapping(XYBlockRenderer renderer, XYZDataset dataset) {
        // 颜色映射：蓝色(低值) -> 红色(高值)。必须设置 PaintScale，
        // 否则 XYBlockRenderer 所有块使用同一默认颜色，热度信息全部丢失
        Color[] colors = {
            Color.BLUE,
            Color.CYAN,
            Color.GREEN,
            Color.YELLOW,
            Color.ORANGE,
            Color.RED
        };

        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int s = 0; s < dataset.getSeriesCount(); s++) {
            for (int i = 0; i < dataset.getItemCount(s); i++) {
                double z = dataset.getZValue(s, i);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
        }
        if (minZ > maxZ) {
            minZ = 0;
            maxZ = 1;
        }
        if (minZ == maxZ) {
            maxZ = minZ + 1;
        }

        LookupPaintScale paintScale = new LookupPaintScale(minZ, maxZ, colors[0]);
        double step = (maxZ - minZ) / colors.length;
        for (int i = 0; i < colors.length; i++) {
            paintScale.add(minZ + i * step, colors[i]);
        }
        renderer.setPaintScale(paintScale);
    }
}