package io.nop.chart.export.renderer;

import io.nop.excel.chart.constants.ChartRadarStyle;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.model.ChartRadarConfigModel;
import io.nop.excel.chart.model.ChartSeriesModel;
import io.nop.excel.chart.util.ChartDataSet;
import io.nop.excel.resolver.ICellRefResolver;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.SpiderWebPlot;
import org.jfree.data.category.CategoryDataset;

import java.awt.*;
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
        applyRadarConfig(plot, chartModel.getPlotArea().getRadarConfig());

        // 应用series级别的线条样式配置
        applySeriesStyles(plot, chartModel);

        return chart;
    }

    private void applyRadarConfig(SpiderWebPlot plot, ChartRadarConfigModel chartConfig) {
        if(chartConfig == null)
            return;

        // 应用雷达图特定配置
        if (chartConfig.getRadarStyle() == ChartRadarStyle.FILLED) {
            // 设置雷达图特有属性
            plot.setWebFilled(true);
        }

        if (chartConfig.getStartAngle() != null) {
            plot.setStartAngle(chartConfig.getStartAngle());
        }

        // TODO: 从配置中读取更多雷达图特定设置
        LOG.debug("Applying radar chart specific configuration");
    }

    private void applySeriesStyles(SpiderWebPlot plot, ChartModel chartModel) {
        if (chartModel.getPlotArea() == null || chartModel.getPlotArea().getSeriesList() == null) {
            return;
        }

        // 为每个series设置独立的线条样式
        List<ChartSeriesModel> seriesList = chartModel.getPlotArea().getSeriesList();
        
        // 定义默认颜色序列
        Color[] defaultColors = {
            Color.RED, Color.BLUE, Color.GREEN, Color.ORANGE, Color.MAGENTA,
            Color.CYAN, Color.PINK, Color.YELLOW, Color.GRAY, Color.DARK_GRAY
        };

        for (int seriesIndex = 0; seriesIndex < seriesList.size(); seriesIndex++) {
            ChartSeriesModel series = seriesList.get(seriesIndex);
            
            // 获取series级别的线条颜色
            Color seriesColor = getSeriesColor(series, seriesIndex, defaultColors);
            
            // 获取series级别的线条宽度
            float lineWidth = getSeriesLineWidth(series);
            
            // 设置series的线条样式
            plot.setSeriesPaint(seriesIndex, seriesColor);
            plot.setSeriesOutlinePaint(seriesIndex, seriesColor);
            plot.setSeriesOutlineStroke(seriesIndex, new BasicStroke(lineWidth));
            
            LOG.debug("Applied series {} style: color={}, lineWidth={}", 
                     seriesIndex, seriesColor, lineWidth);
        }
    }

    private Color getSeriesColor(ChartSeriesModel series, int seriesIndex, Color[] defaultColors) {
        // 优先使用series级别的shapeStyle配置
        if (series.getShapeStyle() != null && series.getShapeStyle().getFill() != null) {
            String fillColor = series.getShapeStyle().getFill().getForegroundColor();
            if (fillColor != null) {
                try {
                    return Color.decode(fillColor);
                } catch (NumberFormatException e) {
                    LOG.debug("Invalid color format: {}", fillColor);
                }
            }
        }
        
        // 使用默认颜色序列
        return defaultColors[seriesIndex % defaultColors.length];
    }

    private float getSeriesLineWidth(ChartSeriesModel series) {
        // 优先使用series级别的lineStyle配置
        if (series.getLineStyle() != null && series.getLineStyle().getWidth() != null) {
            return series.getLineStyle().getWidth().floatValue();
        }
        
        // 默认线条宽度
        return 2.0f;
    }
}