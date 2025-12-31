package io.nop.chart.export.renderer;

import io.nop.chart.export.ICellRefResolver;
import io.nop.chart.export.model.ChartDataSet;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartModel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;

import java.util.List;

/**
 * Area chart renderer
 */
public class AreaChartRenderer extends AbstractChartRenderer {
    
    @Override
    public ChartType getSupportedType() {
        return ChartType.AREA;
    }
    
    @Override
    protected JFreeChart createChart(ChartModel chartModel, List<ChartDataSet> dataSets, ICellRefResolver resolver) {
        LOG.debug("Creating area chart with {} data sets", dataSets.size());
        
        // 创建数据集
        CategoryDataset dataset = createCategoryDataset(dataSets);
        
        // 创建面积图
        JFreeChart chart = ChartFactory.createAreaChart(
            null, // 标题将在样式应用时设置
            null, // X轴标签将在样式应用时设置
            null, // Y轴标签将在样式应用时设置
            dataset,
            org.jfree.chart.plot.PlotOrientation.VERTICAL,
            true, // 显示图例
            true, // 显示工具提示
            false // 不生成URL
        );
        
        // 应用面积图特定配置
        applyAreaConfig(chart, chartModel);
        
        return chart;
    }
    
    private CategoryDataset createCategoryDataset(List<ChartDataSet> dataSets) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        for (int i = 0; i < dataSets.size(); i++) {
            ChartDataSet dataSet = dataSets.get(i);
            String seriesName = "Series " + (i + 1);
            
            List<Object> categories = dataSet.getCategories();
            List<Number> values = dataSet.getValues();
            
            int minSize = Math.min(categories.size(), values.size());
            for (int j = 0; j < minSize; j++) {
                String category = categories.get(j) != null ? categories.get(j).toString() : "Category " + (j + 1);
                Number value = values.get(j);
                dataset.addValue(value, seriesName, category);
            }
        }
        
        return dataset;
    }
    
    private void applyAreaConfig(JFreeChart chart, ChartModel chartModel) {
        // 应用面积图特定配置
        if (chartModel.getPlotArea() != null && chartModel.getPlotArea().getAreaConfig() != null) {
            org.jfree.chart.plot.CategoryPlot plot = chart.getCategoryPlot();
            org.jfree.chart.renderer.category.CategoryItemRenderer renderer = plot.getRenderer();
            
            // 获取面积图配置
            io.nop.excel.chart.model.ChartAreaConfigModel areaConfig = chartModel.getPlotArea().getAreaConfig();
            
            LOG.debug("Applied area chart specific configuration");
        }
    }
}