package io.nop.chart.export.renderer;

import io.nop.excel.resolver.ChartDataResolver;
import io.nop.chart.export.ChartDataValidator;
import io.nop.excel.resolver.ICellRefResolver;
import io.nop.chart.export.IChartTypeRenderer;
import io.nop.excel.chart.util.ChartDataSet;
import io.nop.chart.export.style.ChartStyleApplier;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartModel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Abstract base class for chart renderers
 */
public abstract class AbstractChartRenderer implements IChartTypeRenderer {
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractChartRenderer.class);

    protected final ChartDataResolver dataResolver;
    protected final ChartDataValidator dataValidator;
    protected final ChartStyleApplier styleApplier;

    public AbstractChartRenderer() {
        this.dataResolver = ChartDataResolver.INSTANCE;
        this.dataValidator = new ChartDataValidator();
        this.styleApplier = new ChartStyleApplier();
    }

    @Override
    public JFreeChart render(ChartModel chartModel, ICellRefResolver resolver) {
        LOG.debug("Rendering chart: type={}", chartModel.getType());

        // 验证输入
        dataValidator.validateChartModel(chartModel);

        // 解析数据
        List<ChartDataSet> dataSets = dataResolver.resolveSeriesData(chartModel.getPlotArea(), resolver);

        // 验证数据
        for (ChartDataSet dataSet : dataSets) {
            dataValidator.handleMissingData(dataSet);
        }

        // 创建图表
        JFreeChart chart = createChart(chartModel, dataSets, resolver);

        // 应用样式
        applyChartStyles(chart, chartModel, resolver);

        return chart;
    }

    @Override
    public boolean supports(ChartType type) {
        return getSupportedType() == type;
    }

    /**
     * 创建具体类型的图表
     *
     * @param chartModel 图表模型
     * @param dataSets   数据集列表
     * @param resolver   数据解析器
     * @return JFreeChart对象
     */
    protected abstract JFreeChart createChart(ChartModel chartModel, List<ChartDataSet> dataSets, ICellRefResolver resolver);

    /**
     * 应用图表样式
     *
     * @param chart      JFreeChart对象
     * @param chartModel 图表模型
     */
    protected void applyChartStyles(JFreeChart chart, ChartModel chartModel, ICellRefResolver resolver) {
        // 使用统一的样式应用器
        styleApplier.applyAllStyles(chart, chartModel, resolver);
    }

    /**
     * 创建分类数据集
     *
     * @param dataSets 数据集列表
     * @return CategoryDataset对象
     */
    protected CategoryDataset createCategoryDataset(List<ChartDataSet> dataSets) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        for (ChartDataSet dataSet : dataSets) {
            String seriesName = dataSet.getName() != null ? dataSet.getName() : "Series";
            List<Number> values = dataSet.getValues();
            List<Object> categories = dataSet.getCategories();
            
            if (values == null || values.isEmpty()) {
                continue;
            }
            
            // 如果没有分类，使用索引
            if (categories == null || categories.isEmpty()) {
                for (int i = 0; i < values.size(); i++) {
                    Number value = values.get(i);
                    if (value != null) {
                        dataset.addValue(value, seriesName, "Category " + (i + 1));
                    }
                }
            } else {
                // 使用提供的分类
                int maxSize = Math.min(values.size(), categories.size());
                for (int i = 0; i < maxSize; i++) {
                    Number value = values.get(i);
                    Object category = categories.get(i);
                    if (value != null && category != null) {
                        dataset.addValue(value, seriesName, category.toString());
                    }
                }
            }
        }
        
        return dataset;
    }
}