/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.chart.util;

import io.nop.excel.chart.model.ChartBorderModel;
import io.nop.excel.chart.model.ChartFillModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import io.nop.excel.chart.constants.ChartFillType;
import io.nop.excel.chart.constants.ChartLineStyle;

/**
 * 图表样式帮助类
 * 提供创建和操作图表样式的便利方法
 */
public class ChartStyleHelper {

    /**
     * 创建一个简单的形状样式，包含填充色和边框
     * 
     * @param fillColor 填充颜色（如 "#FF0000"）
     * @param borderColor 边框颜色（如 "#000000"）
     * @param borderWidth 边框宽度（pt）
     * @return 创建的形状样式
     */
    public static ChartShapeStyleModel createSimpleStyle(String fillColor, String borderColor, Double borderWidth) {
        ChartShapeStyleModel style = new ChartShapeStyleModel();
        
        // 设置填充
        if (fillColor != null) {
            ChartFillModel fill = new ChartFillModel();
            fill.setType(ChartFillType.SOLID);
            fill.setForegroundColor(fillColor);
            style.setFill(fill);
        }
        
        // 设置边框
        if (borderColor != null || borderWidth != null) {
            ChartBorderModel border = new ChartBorderModel();
            if (borderColor != null) {
                border.setColor(borderColor);
            }
            if (borderWidth != null) {
                border.setWidth(borderWidth);
            }
            border.setStyle(ChartLineStyle.SOLID);
            style.setBorder(border);
        }
        
        return style;
    }

    /**
     * 创建一个只有填充色的样式
     * 
     * @param fillColor 填充颜色
     * @return 创建的形状样式
     */
    public static ChartShapeStyleModel createFillOnlyStyle(String fillColor) {
        return createSimpleStyle(fillColor, null, null);
    }

    /**
     * 创建一个只有边框的样式
     * 
     * @param borderColor 边框颜色
     * @param borderWidth 边框宽度
     * @return 创建的形状样式
     */
    public static ChartShapeStyleModel createBorderOnlyStyle(String borderColor, Double borderWidth) {
        return createSimpleStyle(null, borderColor, borderWidth);
    }

    /**
     * 检查样式是否有有效的填充颜色
     * 
     * @param style 要检查的样式
     * @return 如果有有效的填充颜色则返回true
     */
    public static boolean hasValidFillColor(ChartShapeStyleModel style) {
        return style != null && style.hasFill() && style.getForegroundColor() != null;
    }

    /**
     * 检查样式是否有有效的边框
     * 
     * @param style 要检查的样式
     * @return 如果有有效的边框则返回true
     */
    public static boolean hasValidBorder(ChartShapeStyleModel style) {
        return style != null && style.hasBorder() && 
               (style.getBorderColor() != null || style.getBorderWidth() != null);
    }

    /**
     * 获取样式的描述字符串，用于调试
     * 
     * @param style 要描述的样式
     * @return 样式描述字符串
     */
    public static String getStyleDescription(ChartShapeStyleModel style) {
        if (style == null) {
            return "No style";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Style[");
        
        if (style.hasFill()) {
            sb.append("fill=").append(style.getForegroundColor());
            if (style.getFillOpacity() != null) {
                sb.append("(").append(style.getFillOpacity()).append(")");
            }
        }
        
        if (style.hasBorder()) {
            if (sb.length() > 6) sb.append(", ");
            sb.append("border=").append(style.getBorderColor());
            if (style.getBorderWidth() != null) {
                sb.append("(").append(style.getBorderWidth()).append("pt)");
            }
        }
        
        if (style.hasShadow()) {
            if (sb.length() > 6) sb.append(", ");
            sb.append("shadow=true");
        }
        
        sb.append("]");
        return sb.toString();
    }

    /**
     * 创建一个带有smooth属性的系列模型
     * 
     * @param name 系列名称
     * @param dataCellRef 数据单元格引用
     * @param catCellRef 分类单元格引用
     * @param smooth 是否平滑
     * @return 创建的系列模型
     */
    public static io.nop.excel.chart.model.ChartSeriesModel createSmoothSeries(
            String name, String dataCellRef, String catCellRef, boolean smooth) {
        io.nop.excel.chart.model.ChartSeriesModel series = new io.nop.excel.chart.model.ChartSeriesModel();
        series.setName(name);
        series.setDataCellRef(dataCellRef);
        series.setCatCellRef(catCellRef);
        series.setSmooth(smooth);
        return series;
    }

    /**
     * 检查系列是否启用了平滑
     * 
     * @param series 要检查的系列
     * @return 如果启用了平滑则返回true
     */
    public static boolean isSmooth(io.nop.excel.chart.model.ChartSeriesModel series) {
        return series != null && series.getSmooth() != null && series.getSmooth();
    }

    /**
     * 创建一个无填充边框的样式
     * 
     * @param borderWidth 边框宽度（pt）
     * @param borderStyle 边框样式
     * @return 创建的形状样式
     */
    public static ChartShapeStyleModel createNoFillBorderStyle(Double borderWidth, ChartLineStyle borderStyle) {
        ChartShapeStyleModel style = new ChartShapeStyleModel();
        
        ChartBorderModel border = new ChartBorderModel();
        border.setNoFill(true);
        if (borderWidth != null) {
            border.setWidth(borderWidth);
        }
        if (borderStyle != null) {
            border.setStyle(borderStyle);
        } else {
            border.setStyle(ChartLineStyle.SOLID);
        }
        
        style.setBorder(border);
        return style;
    }

    /**
     * 创建一个无填充边框的样式（使用默认实线样式）
     * 
     * @param borderWidth 边框宽度（pt）
     * @return 创建的形状样式
     */
    public static ChartShapeStyleModel createNoFillBorderStyle(Double borderWidth) {
        return createNoFillBorderStyle(borderWidth, ChartLineStyle.SOLID);
    }

    /**
     * 检查边框是否为无填充
     * 
     * @param style 要检查的样式
     * @return 如果边框设置为无填充则返回true
     */
    public static boolean isBorderNoFill(ChartShapeStyleModel style) {
        return style != null && style.isBorderNoFill();
    }
}