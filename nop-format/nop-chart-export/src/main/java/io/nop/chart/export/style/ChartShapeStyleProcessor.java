package io.nop.chart.export.style;

import io.nop.chart.export.utils.JFreeChartStyleAdapter;
import io.nop.excel.chart.model.ChartBorderModel;
import io.nop.excel.chart.model.ChartFillModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;

/**
 * Chart shape style processor
 */
public class ChartShapeStyleProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(ChartShapeStyleProcessor.class);
    
    /**
     * 应用图表背景样式
     * @param chart JFreeChart对象
     * @param shapeStyle 形状样式
     */
    public void applyBackgroundStyle(JFreeChart chart, ChartShapeStyleModel shapeStyle) {
        LOG.debug("Applying chart background style");
        
        // 设置默认白色背景
        Paint backgroundColor = Color.WHITE;
        
        // 处理填充样式
        if (shapeStyle.getFill() != null) {
            ChartFillModel fill = shapeStyle.getFill();
            
            // 检查是否有渐变填充设置
            if (fill.getForegroundColor() != null && fill.getBackgroundColor() != null) {
                // 创建渐变填充
                Paint gradientPaint = JFreeChartStyleAdapter.createGradientPaint(
                    fill.getBackgroundColor(), 
                    fill.getForegroundColor(), 
                    0, 0, 100, 100
                );
                if (gradientPaint != null) {
                    backgroundColor = gradientPaint;
                }
            } else if (fill.getBackgroundColor() != null) {
                // 单色填充
                Color specifiedColor = JFreeChartStyleAdapter.convertColor(fill.getBackgroundColor());
                if (specifiedColor != null) {
                    backgroundColor = specifiedColor;
                }
            }
        }
        
        // 应用背景填充
        chart.setBackgroundPaint(backgroundColor);
        
        // 应用边框
        if (shapeStyle.getBorder() != null) {
            ChartBorderModel border = shapeStyle.getBorder();
            Color borderColor = JFreeChartStyleAdapter.convertColor(border.getColor());
            if (borderColor != null) {
                // 设置图表边框
                chart.setBorderPaint(borderColor);
                chart.setBorderStroke(JFreeChartStyleAdapter.createBorderStroke(border));
                chart.setBorderVisible(true);
                LOG.debug("Chart border color applied: {}", borderColor);
            }
        }
    }
    
    /**
     * 应用绘图区样式
     * @param plot 绘图区对象
     * @param shapeStyle 形状样式
     */
    public void applyPlotAreaStyle(Plot plot, ChartShapeStyleModel shapeStyle) {
        LOG.debug("Applying plot area style");
        
        // 处理填充样式
        if (shapeStyle.getFill() != null) {
            ChartFillModel fill = shapeStyle.getFill();
            Paint backgroundColor = Color.WHITE;
            
            // 检查是否有渐变填充设置
            if (fill.getForegroundColor() != null && fill.getBackgroundColor() != null) {
                // 创建渐变填充
                Paint gradientPaint = JFreeChartStyleAdapter.createGradientPaint(
                    fill.getBackgroundColor(), 
                    fill.getForegroundColor(), 
                    0, 0, 100, 100
                );
                if (gradientPaint != null) {
                    backgroundColor = gradientPaint;
                }
            } else if (fill.getBackgroundColor() != null) {
                // 单色填充
                Color specifiedColor = JFreeChartStyleAdapter.convertColor(fill.getBackgroundColor());
                if (specifiedColor != null) {
                    backgroundColor = specifiedColor;
                }
            }
            
            // 应用背景填充
            plot.setBackgroundPaint(backgroundColor);
        }
        
        // 应用边框
        if (shapeStyle.getBorder() != null) {
            ChartBorderModel border = shapeStyle.getBorder();
            Color borderColor = JFreeChartStyleAdapter.convertColor(border.getColor());
            Stroke borderStroke = JFreeChartStyleAdapter.createBorderStroke(border);
            
            if (borderColor != null) {
                plot.setOutlinePaint(borderColor);
                plot.setOutlineStroke(borderStroke);
                plot.setOutlineVisible(true);
            }
        } else {
            plot.setOutlineVisible(false);
        }
        
        // 应用网格线样式
        applyGridLineStyles(plot, shapeStyle);
    }
    
    /**
     * 应用网格线样式
     * @param plot 绘图区对象
     * @param shapeStyle 形状样式
     */
    private void applyGridLineStyles(Plot plot, ChartShapeStyleModel shapeStyle) {
        // 设置默认网格线样式
        Color gridColor = new Color(200, 200, 200, 150);
        Stroke gridStroke = new BasicStroke(0.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, 
                                            new float[]{5.0f, 5.0f}, 0);
        
        // 应用X轴网格线
        if (plot instanceof XYPlot) {
            XYPlot xyPlot = (XYPlot) plot;
            xyPlot.setDomainGridlinePaint(gridColor);
            xyPlot.setDomainGridlineStroke(gridStroke);
            xyPlot.setRangeGridlinePaint(gridColor);
            xyPlot.setRangeGridlineStroke(gridStroke);
            
            // 设置零基准线
            xyPlot.setDomainZeroBaselineVisible(true);
            xyPlot.setRangeZeroBaselineVisible(true);
            xyPlot.setDomainZeroBaselinePaint(Color.RED);
            xyPlot.setRangeZeroBaselinePaint(Color.RED);
            xyPlot.setDomainZeroBaselineStroke(new BasicStroke(1.0f));
            xyPlot.setRangeZeroBaselineStroke(new BasicStroke(1.0f));
        } else if (plot instanceof CategoryPlot) {
            CategoryPlot categoryPlot = (CategoryPlot) plot;
            categoryPlot.setDomainGridlinePaint(gridColor);
            categoryPlot.setDomainGridlineStroke(gridStroke);
            categoryPlot.setRangeGridlinePaint(gridColor);
            categoryPlot.setRangeGridlineStroke(gridStroke);
        }
    }
    
    /**
     * 应用系列样式
     * @param plot 绘图区对象
     * @param seriesIndex 系列索引
     * @param shapeStyle 形状样式
     */
    public void applySeriesStyle(Plot plot, int seriesIndex, ChartShapeStyleModel shapeStyle) {
        LOG.debug("Applying series style: index={}", seriesIndex);
        
        if (plot instanceof CategoryPlot) {
            applyCategorySeriesStyle((CategoryPlot) plot, seriesIndex, shapeStyle);
        } else if (plot instanceof XYPlot) {
            applyXYSeriesStyle((XYPlot) plot, seriesIndex, shapeStyle);
        }
    }
    
    private void applyCategorySeriesStyle(CategoryPlot plot, int seriesIndex, ChartShapeStyleModel shapeStyle) {
        CategoryItemRenderer renderer = plot.getRenderer();
        if (renderer == null) {
            return;
        }
        
        // 应用填充样式
        applySeriesFillStyle(renderer, seriesIndex, shapeStyle);
        
        // 应用边框样式
        applySeriesBorderStyle(renderer, seriesIndex, shapeStyle);
    }
    
    private void applyXYSeriesStyle(XYPlot plot, int seriesIndex, ChartShapeStyleModel shapeStyle) {
        XYItemRenderer renderer = plot.getRenderer();
        if (renderer == null) {
            return;
        }
        
        // 应用填充样式
        applySeriesFillStyle(renderer, seriesIndex, shapeStyle);
        
        // 应用边框样式
        applySeriesBorderStyle(renderer, seriesIndex, shapeStyle);
        
        // 应用形状样式（仅XY图表）
        applySeriesShapeStyle(renderer, seriesIndex, shapeStyle);
    }
    
    /**
     * 应用系列填充样式
     * @param renderer 渲染器
     * @param seriesIndex 系列索引
     * @param shapeStyle 形状样式
     */
    private void applySeriesFillStyle(Object renderer, int seriesIndex, ChartShapeStyleModel shapeStyle) {
        if (shapeStyle.getFill() == null) {
            return;
        }
        
        ChartFillModel fill = shapeStyle.getFill();
        Paint fillPaint = null;
        
        // 检查是否有渐变填充设置
        if (fill.getForegroundColor() != null && fill.getBackgroundColor() != null) {
            // 创建渐变填充
            fillPaint = JFreeChartStyleAdapter.createGradientPaint(
                fill.getBackgroundColor(), 
                fill.getForegroundColor(), 
                0, 0, 10, 10
            );
        } else if (fill.getBackgroundColor() != null) {
            // 单色填充
            fillPaint = JFreeChartStyleAdapter.convertColor(fill.getBackgroundColor());
        }
        
        if (fillPaint != null) {
            if (renderer instanceof CategoryItemRenderer) {
                ((CategoryItemRenderer) renderer).setSeriesPaint(seriesIndex, fillPaint);
            } else if (renderer instanceof XYItemRenderer) {
                ((XYItemRenderer) renderer).setSeriesPaint(seriesIndex, fillPaint);
            }
        }
    }
    
    /**
     * 应用系列边框样式
     * @param renderer 渲染器
     * @param seriesIndex 系列索引
     * @param shapeStyle 形状样式
     */
    private void applySeriesBorderStyle(Object renderer, int seriesIndex, ChartShapeStyleModel shapeStyle) {
        if (shapeStyle.getBorder() == null) {
            return;
        }
        
        ChartBorderModel border = shapeStyle.getBorder();
        Color borderColor = JFreeChartStyleAdapter.convertColor(border.getColor());
        Stroke borderStroke = JFreeChartStyleAdapter.createBorderStroke(border);
        
        if (borderColor != null && borderStroke != null) {
            if (renderer instanceof CategoryItemRenderer) {
                CategoryItemRenderer catRenderer = (CategoryItemRenderer) renderer;
                catRenderer.setSeriesOutlinePaint(seriesIndex, borderColor);
                catRenderer.setSeriesOutlineStroke(seriesIndex, borderStroke);
            } else if (renderer instanceof XYItemRenderer) {
                XYItemRenderer xyRenderer = (XYItemRenderer) renderer;
                xyRenderer.setSeriesOutlinePaint(seriesIndex, borderColor);
                xyRenderer.setSeriesOutlineStroke(seriesIndex, borderStroke);
            }
        }
    }
    
    /**
     * 应用系列形状样式
     * @param renderer 渲染器
     * @param seriesIndex 系列索引
     * @param shapeStyle 形状样式
     */
    private void applySeriesShapeStyle(XYItemRenderer renderer, int seriesIndex, ChartShapeStyleModel shapeStyle) {
        // 检查是否是支持形状的渲染器
        if (renderer instanceof XYLineAndShapeRenderer) {
            XYLineAndShapeRenderer lineRenderer = (XYLineAndShapeRenderer) renderer;
            
            // 应用形状
            Shape shape = JFreeChartStyleAdapter.getPredefinedShape("circle");
            if (shape != null) {
                lineRenderer.setSeriesShape(seriesIndex, shape);
            }
            
            // 设置形状可见
            lineRenderer.setSeriesShapesVisible(seriesIndex, true);
        }
    }
}