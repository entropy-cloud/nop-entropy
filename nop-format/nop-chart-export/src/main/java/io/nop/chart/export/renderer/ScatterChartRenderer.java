package io.nop.chart.export.renderer;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.chart.export.utils.JFreeChartStyleAdapter;
import io.nop.excel.chart.constants.ChartMarkerType;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.resolver.ICellRefResolver;
import io.nop.excel.chart.util.ChartDataSet;

import java.util.ArrayList;
import java.util.List;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

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
        
        // 为散点图准备数据，确保xValues被正确设置
        for (ChartDataSet dataSet : dataSets) {
            if (dataSet.getXValues().isEmpty() && !dataSet.getCategories().isEmpty()) {
                // 如果xValues为空但categories不为空，将categories转换为xValues
                List<Number> xValues = new ArrayList<>();
                for (Object category : dataSet.getCategories()) {
                    if (category != null) {
                        try {
                            Number number = ConvertHelper.convertTo(Number.class, category, NopException::new);
                            xValues.add(number);
                        } catch (Exception e) {
                            LOG.warn("Failed to convert category to number: {}", category, e);
                            xValues.add(0); // 使用0作为默认值
                        }
                    } else {
                        xValues.add(null);
                    }
                }
                dataSet.setXValues(xValues);
                dataSet.setCategories(null); // 散点图不需要分类
            }
        }
        
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
            XYSeries series = new XYSeries(dataSet.getName() != null ? dataSet.getName() : "Series " + (i + 1));
            
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
        // 获取绘图区域和渲染器
        XYPlot plot = chart.getXYPlot();
        XYItemRenderer renderer = plot.getRenderer();
        
        // 确保使用合适的渲染器类型
        if (!(renderer instanceof XYLineAndShapeRenderer)) {
            // 如果不是XYLineAndShapeRenderer，替换为XYLineAndShapeRenderer以支持点和线
            XYLineAndShapeRenderer newRenderer = new XYLineAndShapeRenderer();
            plot.setRenderer(newRenderer);
            renderer = newRenderer;
        }
        
        XYLineAndShapeRenderer lineRenderer = (XYLineAndShapeRenderer) renderer;
        
        // 设置基本属性：显示点，不显示线
        lineRenderer.setDefaultShapesVisible(true);
        lineRenderer.setDefaultLinesVisible(false);
        
        // 设置默认标记大小
        double markerSize = 5.0;
        
        // 获取散点图配置
        if (chartModel.getPlotArea() != null && chartModel.getPlotArea().getScatterConfig() != null) {
            io.nop.excel.chart.model.ChartScatterConfigModel scatterConfig = chartModel.getPlotArea().getScatterConfig();
            
            // 应用标记大小配置
            if (scatterConfig.getMarkerSize() != null) {
                markerSize = scatterConfig.getMarkerSize();
            }
        }
        
        // 设置标记点形状和大小
        ChartMarkerType markerType = ChartMarkerType.CIRCLE; // 散点图默认使用圆形标记
        
        // 获取散点图配置中的标记类型
        if (chartModel.getPlotArea() != null && chartModel.getPlotArea().getScatterConfig() != null) {
            io.nop.excel.chart.model.ChartScatterConfigModel scatterConfig = chartModel.getPlotArea().getScatterConfig();
            if (scatterConfig.getMarkerType() != null) {
                markerType = scatterConfig.getMarkerType();
            }
        }
        
        // 使用JFreeChartStyleAdapter创建标记点形状
        java.awt.Shape markerShape = JFreeChartStyleAdapter.createMarkerShape(markerType, (float)markerSize, 0);
        lineRenderer.setDefaultShape(markerShape);
        
        LOG.debug("Applied scatter chart configuration");
    }
}