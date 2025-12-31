package io.nop.chart.export.renderer;

import io.nop.excel.chart.model.ChartTrendLineModel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.chart.renderer.category.StatisticalLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.statistics.Regression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;

/**
 * Trend line renderer for adding trend lines to charts
 */
public class TrendLineRenderer {
    private static final Logger LOG = LoggerFactory.getLogger(TrendLineRenderer.class);
    
    /**
     * 添加趋势线到图表
     * @param chart JFreeChart对象
     * @param trendLines 趋势线配置列表
     */
    public void addTrendLines(JFreeChart chart, List<ChartTrendLineModel> trendLines) {
        if (trendLines == null || trendLines.isEmpty()) {
            return;
        }
        
        LOG.debug("Adding {} trend lines to chart", trendLines.size());
        
        for (int i = 0; i < trendLines.size(); i++) {
            ChartTrendLineModel trendLine = trendLines.get(i);
            addTrendLine(chart, trendLine, i);
        }
    }
    
    /**
     * 添加单个趋势线
     * @param chart JFreeChart对象
     * @param trendLine 趋势线配置
     * @param index 趋势线索引
     */
    public void addTrendLine(JFreeChart chart, ChartTrendLineModel trendLine, int index) {
        if (trendLine == null) {
            return;
        }
        
        LOG.debug("Adding trend line: type={}, index={}", trendLine.getType(), index);
        
        if (chart.getPlot() instanceof CategoryPlot) {
            addCategoryTrendLine((CategoryPlot) chart.getPlot(), trendLine, index);
        } else if (chart.getPlot() instanceof XYPlot) {
            addXYTrendLine((XYPlot) chart.getPlot(), trendLine, index);
        }
    }
    
    private void addCategoryTrendLine(CategoryPlot plot, ChartTrendLineModel trendLine, int index) {
        CategoryItemRenderer renderer = plot.getRenderer();
        if (renderer == null) {
            return;
        }
        
        // 根据趋势线类型处理
        switch (getTrendLineType(trendLine)) {
            case LINEAR:
                addLinearTrendLine(plot, trendLine, index);
                break;
            case MOVING_AVERAGE:
                addMovingAverageTrendLine(plot, trendLine, index);
                break;
            case EXPONENTIAL:
                addExponentialTrendLine(plot, trendLine, index);
                break;
            default:
                LOG.warn("Unsupported trend line type for category plot: {}", trendLine.getType());
        }
    }
    
    private void addXYTrendLine(XYPlot plot, ChartTrendLineModel trendLine, int index) {
        XYItemRenderer renderer = plot.getRenderer();
        if (renderer == null) {
            return;
        }
        
        // 根据趋势线类型处理
        switch (getTrendLineType(trendLine)) {
            case LINEAR:
                addLinearXYTrendLine(plot, trendLine, index);
                break;
            case MOVING_AVERAGE:
                addMovingAverageXYTrendLine(plot, trendLine, index);
                break;
            case EXPONENTIAL:
                addExponentialXYTrendLine(plot, trendLine, index);
                break;
            default:
                LOG.warn("Unsupported trend line type for XY plot: {}", trendLine.getType());
        }
    }
    
    private void addLinearTrendLine(CategoryPlot plot, ChartTrendLineModel trendLine, int index) {
        // 线性趋势线实现
        // 这里需要根据数据计算线性回归
        LOG.debug("Adding linear trend line to category plot");
        
        // 应用趋势线样式
        applyTrendLineStyle(plot.getRenderer(), trendLine, index);
    }
    
    private void addMovingAverageTrendLine(CategoryPlot plot, ChartTrendLineModel trendLine, int index) {
        // 移动平均趋势线实现
        LOG.debug("Adding moving average trend line to category plot");
        
        // 应用趋势线样式
        applyTrendLineStyle(plot.getRenderer(), trendLine, index);
    }
    
    private void addExponentialTrendLine(CategoryPlot plot, ChartTrendLineModel trendLine, int index) {
        // 指数趋势线实现
        LOG.debug("Adding exponential trend line to category plot");
        
        // 应用趋势线样式
        applyTrendLineStyle(plot.getRenderer(), trendLine, index);
    }
    
    private void addLinearXYTrendLine(XYPlot plot, ChartTrendLineModel trendLine, int index) {
        // XY图的线性趋势线实现
        LOG.debug("Adding linear trend line to XY plot");
        
        // 使用JFreeChart的回归功能
        try {
            // 这里需要从数据集中获取数据点来计算回归线
            // 由于数据集结构复杂，这里只是框架实现
            
            // 应用趋势线样式
            applyXYTrendLineStyle(plot.getRenderer(), trendLine, index);
        } catch (Exception e) {
            LOG.warn("Failed to add linear trend line to XY plot", e);
        }
    }
    
    private void addMovingAverageXYTrendLine(XYPlot plot, ChartTrendLineModel trendLine, int index) {
        // XY图的移动平均趋势线实现
        LOG.debug("Adding moving average trend line to XY plot");
        
        // 应用趋势线样式
        applyXYTrendLineStyle(plot.getRenderer(), trendLine, index);
    }
    
    private void addExponentialXYTrendLine(XYPlot plot, ChartTrendLineModel trendLine, int index) {
        // XY图的指数趋势线实现
        LOG.debug("Adding exponential trend line to XY plot");
        
        // 应用趋势线样式
        applyXYTrendLineStyle(plot.getRenderer(), trendLine, index);
    }
    
    private void applyTrendLineStyle(CategoryItemRenderer renderer, ChartTrendLineModel trendLine, int index) {
        // 应用趋势线样式
        if (trendLine.getLineStyle() != null) {
            // 设置线条颜色
            if (trendLine.getLineStyle().getColor() != null) {
                // Color color = convertColor(trendLine.getLineStyle().getColor());
                // renderer.setSeriesPaint(index, color);
            }
            
            // 设置线条宽度
            Double width = trendLine.getLineStyle().getWidth();
            if (width != null && width > 0) {
                Stroke stroke = new BasicStroke(width.floatValue());
                // renderer.setSeriesStroke(index, stroke);
            }
        }
    }
    
    private void applyXYTrendLineStyle(XYItemRenderer renderer, ChartTrendLineModel trendLine, int index) {
        // 应用XY趋势线样式
        if (trendLine.getLineStyle() != null) {
            // 设置线条颜色
            if (trendLine.getLineStyle().getColor() != null) {
                // Color color = convertColor(trendLine.getLineStyle().getColor());
                // renderer.setSeriesPaint(index, color);
            }
            
            // 设置线条宽度
            Double width = trendLine.getLineStyle().getWidth();
            if (width != null && width > 0) {
                Stroke stroke = new BasicStroke(width.floatValue());
                // renderer.setSeriesStroke(index, stroke);
            }
        }
    }
    
    private TrendLineType getTrendLineType(ChartTrendLineModel trendLine) {
        // 根据趋势线模型确定类型
        if (trendLine.getType() != null) {
            String type = trendLine.getType().name().toLowerCase();
            switch (type) {
                case "linear":
                    return TrendLineType.LINEAR;
                case "moving_avg":
                    return TrendLineType.MOVING_AVERAGE;
                case "exponential":
                    return TrendLineType.EXPONENTIAL;
                case "polynomial":
                    return TrendLineType.POLYNOMIAL;
                case "logarithmic":
                    return TrendLineType.LOGARITHMIC;
                case "power":
                    return TrendLineType.POWER;
                default:
                    return TrendLineType.LINEAR;
            }
        }
        return TrendLineType.LINEAR;
    }
    
    /**
     * 趋势线类型枚举
     */
    public enum TrendLineType {
        LINEAR,
        MOVING_AVERAGE,
        EXPONENTIAL,
        POLYNOMIAL,
        LOGARITHMIC,
        POWER
    }
}