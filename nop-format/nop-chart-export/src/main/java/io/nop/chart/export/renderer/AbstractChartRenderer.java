package io.nop.chart.export.renderer;

import io.nop.chart.export.ChartDataResolver;
import io.nop.chart.export.ChartDataValidator;
import io.nop.chart.export.ICellRefResolver;
import io.nop.chart.export.IChartTypeRenderer;
import io.nop.chart.export.model.ChartDataSet;
import io.nop.chart.export.style.ChartStyleApplier;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartModel;
import org.jfree.chart.JFreeChart;
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
        this.dataResolver = new ChartDataResolver(null); // Will be set during render
        this.dataValidator = new ChartDataValidator();
        this.styleApplier = new ChartStyleApplier();
    }
    
    @Override
    public JFreeChart render(ChartModel chartModel, ICellRefResolver resolver) {
        LOG.debug("Rendering chart: type={}", chartModel.getType());
        
        // 验证输入
        dataValidator.validateChartModel(chartModel);
        
        // 解析数据
        ChartDataResolver resolver2 = new ChartDataResolver(resolver);
        List<ChartDataSet> dataSets = resolver2.resolveSeriesData(chartModel.getPlotArea(), resolver);
        
        // 验证数据
        for (ChartDataSet dataSet : dataSets) {
            dataValidator.handleMissingData(dataSet);
        }
        
        // 创建图表
        JFreeChart chart = createChart(chartModel, dataSets, resolver);
        
        // 应用样式
        applyChartStyles(chart, chartModel);
        
        return chart;
    }
    
    @Override
    public boolean supports(ChartType type) {
        return getSupportedType().equals(type);
    }
    
    /**
     * 创建具体类型的图表
     * @param chartModel 图表模型
     * @param dataSets 数据集列表
     * @param resolver 数据解析器
     * @return JFreeChart对象
     */
    protected abstract JFreeChart createChart(ChartModel chartModel, List<ChartDataSet> dataSets, ICellRefResolver resolver);
    
    /**
     * 应用图表样式
     * @param chart JFreeChart对象
     * @param chartModel 图表模型
     */
    protected void applyChartStyles(JFreeChart chart, ChartModel chartModel) {
        // 使用统一的样式应用器
        styleApplier.applyAllStyles(chart, chartModel, null);
    }
}