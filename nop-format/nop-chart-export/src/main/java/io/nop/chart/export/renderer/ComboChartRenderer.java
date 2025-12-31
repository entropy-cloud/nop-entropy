package io.nop.chart.export.renderer;

import io.nop.chart.export.ICellRefResolver;
import io.nop.chart.export.model.ChartDataSet;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartModel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;

import java.util.List;

/**
 * Combo chart renderer (combines multiple chart types)
 */
public class ComboChartRenderer extends AbstractChartRenderer {
    
    @Override
    public ChartType getSupportedType() {
        return ChartType.COMBO;
    }
    
    @Override
    protected JFreeChart createChart(ChartModel chartModel, List<ChartDataSet> dataSets, ICellRefResolver resolver) {
        LOG.debug("Creating combo chart with {} data sets", dataSets.size());
        
        // 创建主数据集（柱状图）
        CategoryDataset primaryDataset = createPrimaryDataset(dataSets);
        
        // 创建基础柱状图
        JFreeChart chart = ChartFactory.createBarChart(
            null, // 标题将在样式应用时设置
            null, // X轴标签将在样式应用时设置
            null, // Y轴标签将在样式应用时设置
            primaryDataset,
            org.jfree.chart.plot.PlotOrientation.VERTICAL,
            true, // 显示图例
            true, // 显示工具提示
            false // 不生成URL
        );
        
        // 添加第二个数据集（折线图）
        addSecondaryDataset(chart, dataSets);
        
        // 应用组合图特定配置
        applyComboConfig(chart, chartModel);
        
        return chart;
    }
    
    private CategoryDataset createPrimaryDataset(List<ChartDataSet> dataSets) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        // 使用前一半数据集作为柱状图数据
        int primaryCount = Math.max(1, dataSets.size() / 2);
        
        for (int i = 0; i < primaryCount && i < dataSets.size(); i++) {
            ChartDataSet dataSet = dataSets.get(i);
            String seriesName = "Bar Series " + (i + 1);
            
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
    
    private void addSecondaryDataset(JFreeChart chart, List<ChartDataSet> dataSets) {
        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        
        // 创建第二个数据集（折线图）
        DefaultCategoryDataset secondaryDataset = new DefaultCategoryDataset();
        
        // 使用后一半数据集作为折线图数据
        int primaryCount = Math.max(1, dataSets.size() / 2);
        
        for (int i = primaryCount; i < dataSets.size(); i++) {
            ChartDataSet dataSet = dataSets.get(i);
            String seriesName = "Line Series " + (i - primaryCount + 1);
            
            List<Object> categories = dataSet.getCategories();
            List<Number> values = dataSet.getValues();
            
            int minSize = Math.min(categories.size(), values.size());
            for (int j = 0; j < minSize; j++) {
                String category = categories.get(j) != null ? categories.get(j).toString() : "Category " + (j + 1);
                Number value = values.get(j);
                secondaryDataset.addValue(value, seriesName, category);
            }
        }
        
        // 添加第二个数据集和渲染器
        plot.setDataset(1, secondaryDataset);
        plot.setRenderer(1, new LineAndShapeRenderer());
        
        // 创建第二个Y轴
        NumberAxis secondAxis = new NumberAxis("Secondary Axis");
        plot.setRangeAxis(1, secondAxis);
        plot.mapDatasetToRangeAxis(1, 1);
        
        // 设置渲染顺序
        plot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);
    }
    
    private void applyComboConfig(JFreeChart chart, ChartModel chartModel) {
        // 应用组合图特定配置
        LOG.debug("Applying combo chart specific configuration");
        
        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        
        // 设置柱状图渲染器
        BarRenderer barRenderer = (BarRenderer) plot.getRenderer(0);
        if (barRenderer != null) {
            barRenderer.setDrawBarOutline(false);
        }
        
        // 设置折线图渲染器
        LineAndShapeRenderer lineRenderer = (LineAndShapeRenderer) plot.getRenderer(1);
        if (lineRenderer != null) {
            lineRenderer.setDefaultShapesVisible(true);
            lineRenderer.setDefaultShapesFilled(true);
        }
    }
}