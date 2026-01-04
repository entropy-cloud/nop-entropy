package io.nop.chart.export.renderer;

import io.nop.excel.resolver.ICellRefResolver;
import io.nop.chart.export.utils.JFreeChartStyleAdapter;
import io.nop.excel.chart.model.ChartAxisModel;
import org.jfree.chart.axis.Axis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.XYPlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * Chart axis renderer
 */
public class ChartAxisRenderer {
    private static final Logger LOG = LoggerFactory.getLogger(ChartAxisRenderer.class);
    
    /**
     * 渲染坐标轴
     * @param plot 图表绘图区
     * @param axis 坐标轴配置
     * @param resolver 数据解析器
     * @param isXAxis 是否为X轴
     */
    public void renderAxis(Plot plot, ChartAxisModel axis, ICellRefResolver resolver, boolean isXAxis) {
        if (axis == null) {
            return;
        }
        
        LOG.debug("Rendering chart axis: isXAxis={}, visible={}", isXAxis, axis.isVisible());
        
        Axis chartAxis = getAxis(plot, isXAxis);
        if (chartAxis == null) {
            return;
        }
        
        // 设置可见性
        chartAxis.setVisible(axis.isVisible());
        
        if (!axis.isVisible()) {
            return;
        }
        
        // 应用标题
        applyAxisTitle(chartAxis, axis, resolver);
        
        // 应用样式
        applyAxisStyle(chartAxis, axis);
        
        // 应用刻度和网格线
        applyAxisTicks(chartAxis, axis);
    }
    
    private Axis getAxis(Plot plot, boolean isXAxis) {
        if (plot instanceof CategoryPlot) {
            CategoryPlot categoryPlot = (CategoryPlot) plot;
            return isXAxis ? categoryPlot.getDomainAxis() : categoryPlot.getRangeAxis();
        } else if (plot instanceof XYPlot) {
            XYPlot xyPlot = (XYPlot) plot;
            return isXAxis ? xyPlot.getDomainAxis() : xyPlot.getRangeAxis();
        }
        return null;
    }
    
    private void applyAxisTitle(Axis axis, ChartAxisModel axisConfig, ICellRefResolver resolver) {
        if (axisConfig.getTitle() != null && axisConfig.getTitle().isVisible()) {
            String titleText = resolveAxisTitle(axisConfig.getTitle().getText(), resolver);
            axis.setLabel(titleText);
            
            // 应用标题样式
            if (axisConfig.getTitle().getTextStyle() != null) {
                Font font = JFreeChartStyleAdapter.convertFont(axisConfig.getTitle().getTextStyle());
                if (font != null) {
                    axis.setLabelFont(font);
                }
                
                // 从字体中获取颜色
                if (axisConfig.getTitle().getTextStyle().getFont() != null && axisConfig.getTitle().getTextStyle().getFont().getFontColor() != null) {
                    Color color = JFreeChartStyleAdapter.convertColor(axisConfig.getTitle().getTextStyle().getFont().getFontColor());
                    if (color != null) {
                        axis.setLabelPaint(color);
                    }
                }
            }
        } else {
            axis.setLabel(null);
        }
    }
    
    private String resolveAxisTitle(String titleText, ICellRefResolver resolver) {
        if (titleText == null) {
            return null;
        }
        
        // 如果是单元格引用，解析引用
        if (titleText.startsWith("=") && resolver != null) {
            try {
                Object value = resolver.getValue(titleText.substring(1));
                return value != null ? value.toString() : "";
            } catch (Exception e) {
                LOG.warn("Failed to resolve axis title reference: {}", titleText, e);
                return titleText; // 返回原始文本
            }
        }
        
        return titleText;
    }
    
    private void applyAxisStyle(Axis axis, ChartAxisModel axisConfig) {
        // 应用轴线样式
        if (axisConfig.getShapeStyle() != null && axisConfig.getShapeStyle().getBorder() != null) {
            Color lineColor = JFreeChartStyleAdapter.convertColor(axisConfig.getShapeStyle().getBorder().getColor());
            Stroke lineStroke = JFreeChartStyleAdapter.createBorderStroke(axisConfig.getShapeStyle().getBorder());
            
            if (lineColor != null) {
                axis.setAxisLinePaint(lineColor);
            }
            
            if (lineStroke != null) {
                axis.setAxisLineStroke(lineStroke);
            }
            
            // 设置轴线可见性
            axis.setAxisLineVisible(true);
        }
        
        // 应用刻度标签样式
        if (axisConfig.getTextStyle() != null) {
            Font font = JFreeChartStyleAdapter.convertFont(axisConfig.getTextStyle());
            if (font != null) {
                axis.setTickLabelFont(font);
            }
            
            // 从字体中获取颜色
            if (axisConfig.getTextStyle().getFont() != null && axisConfig.getTextStyle().getFont().getFontColor() != null) {
                Color color = JFreeChartStyleAdapter.convertColor(axisConfig.getTextStyle().getFont().getFontColor());
                if (color != null) {
                    axis.setTickLabelPaint(color);
                }
            }
        }
        
        // 设置坐标轴范围（如果需要，后续可以扩展支持）
        if (axis instanceof ValueAxis) {
            ValueAxis valueAxis = (ValueAxis) axis;
            // 暂时不设置具体范围，使用默认自动范围
            valueAxis.setAutoRange(true);
        }
    }
    
    private void applyAxisTicks(Axis axis, ChartAxisModel axisConfig) {
        // 设置刻度线可见性
        axis.setTickMarksVisible(true);
        
        // 应用刻度线样式
        if (axisConfig.getShapeStyle() != null && axisConfig.getShapeStyle().getBorder() != null) {
            Color tickColor = JFreeChartStyleAdapter.convertColor(axisConfig.getShapeStyle().getBorder().getColor());
            Stroke tickStroke = JFreeChartStyleAdapter.createBorderStroke(axisConfig.getShapeStyle().getBorder());
            
            if (tickColor != null) {
                axis.setTickMarkPaint(tickColor);
            }
            
            if (tickStroke != null) {
                axis.setTickMarkStroke(tickStroke);
            }
        }
        
        // 设置刻度线长度
        axis.setTickMarkInsideLength(4.0f);
        axis.setTickMarkOutsideLength(8.0f);
        
        // 设置次要刻度线
        if (axis instanceof ValueAxis) {
            ValueAxis valueAxis = (ValueAxis) axis;
            valueAxis.setMinorTickCount(4);
            valueAxis.setMinorTickMarksVisible(true);
        }
        
        // 设置自动刻度选择
        if (axis instanceof ValueAxis) {
            ValueAxis valueAxis = (ValueAxis) axis;
            valueAxis.setAutoTickUnitSelection(true);
        }
    }
}