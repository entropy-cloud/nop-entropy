package io.nop.chart.export.renderer;

import io.nop.chart.export.ICellRefResolver;
import io.nop.chart.export.model.ChartDataSet;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartModel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.RingPlot;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.general.PieDataset;

import java.util.List;

/**
 * Doughnut chart renderer (extends pie chart)
 */
public class DoughnutChartRenderer extends PieChartRenderer {
    
    @Override
    public ChartType getSupportedType() {
        return ChartType.DOUGHNUT;
    }
    
    @Override
    protected JFreeChart createChart(ChartModel chartModel, List<ChartDataSet> dataSets, ICellRefResolver resolver) {
        LOG.debug("Creating doughnut chart with {} data sets", dataSets.size());
        
        // 创建数据集
        PieDataset dataset = super.createPieDataset(dataSets);
        
        // 创建环形图
        JFreeChart chart = ChartFactory.createRingChart(
            null, // 标题将在样式应用时设置
            dataset,
            true, // 显示图例
            true, // 显示工具提示
            false // 不生成URL
        );
        
        // 应用环形图特定配置
        applyDoughnutConfig(chart, chartModel);
        
        return chart;
    }
    
    private void applyDoughnutConfig(JFreeChart chart, ChartModel chartModel) {
        // 应用环形图特定配置
        if (chartModel.getPlotArea() != null && chartModel.getPlotArea().getDoughnutConfig() != null) {
            RingPlot plot = (RingPlot) chart.getPlot();
            
            // 设置内环大小（环形图的特征）
            plot.setSectionDepth(0.35); // 默认内环大小
            
            // TODO: 从配置中读取内环大小
            LOG.debug("Applying doughnut chart specific configuration");
        }
        
        // 应用饼图的通用配置
        super.applyPieConfig(chart, chartModel);
    }
}