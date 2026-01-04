package io.nop.chart.export.renderer;

import io.nop.excel.resolver.ICellRefResolver;
import io.nop.excel.chart.util.ChartDataSet;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartModel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.SpiderWebPlot;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;

import java.util.List;

/**
 * Radar chart renderer (using SpiderWebPlot)
 */
public class RadarChartRenderer extends AbstractChartRenderer {
    
    @Override
    public ChartType getSupportedType() {
        return ChartType.RADAR;
    }
    
    @Override
    protected JFreeChart createChart(ChartModel chartModel, List<ChartDataSet> dataSets, ICellRefResolver resolver) {
        LOG.debug("Creating radar chart with {} data sets", dataSets.size());
        
        // 创建数据集
        CategoryDataset dataset = createCategoryDataset(dataSets);
        
        // 创建雷达图
        SpiderWebPlot plot = new SpiderWebPlot(dataset);
        JFreeChart chart = new JFreeChart(null, JFreeChart.DEFAULT_TITLE_FONT, plot, true);
        
        // 应用雷达图特定配置
        applyRadarConfig(chart, chartModel);
        
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
    
    private void applyRadarConfig(JFreeChart chart, ChartModel chartModel) {
        // 应用雷达图特定配置
        if (chartModel.getPlotArea() != null && chartModel.getPlotArea().getRadarConfig() != null) {
            SpiderWebPlot plot = (SpiderWebPlot) chart.getPlot();
            
            // 设置雷达图特有属性
            plot.setWebFilled(true);
            plot.setStartAngle(90); // 从顶部开始
            
            // TODO: 从配置中读取更多雷达图特定设置
            LOG.debug("Applying radar chart specific configuration");
        }
    }
}