package io.nop.excel.chart.model;

import io.nop.excel.chart.model._gen._ChartShapeStyleModel;

public class ChartShapeStyleModel extends _ChartShapeStyleModel{
    public ChartShapeStyleModel(){

    }

    /**
     * 获取边框颜色的便利方法
     * @return 边框颜色，如果没有边框或颜色则返回null
     */
    public String getBorderColor() {
        return getBorder() != null ? getBorder().getColor() : null;
    }

    /**
     * 获取边框宽度的便利方法
     * @return 边框宽度（pt），如果没有边框或宽度则返回null
     */
    public Double getBorderWidth() {
        return getBorder() != null ? getBorder().getWidth() : null;
    }

    /**
     * 获取前景色的便利方法
     * @return 前景色，如果没有填充或前景色则返回null
     */
    public String getForegroundColor() {
        return getFill() != null ? getFill().getForegroundColor() : null;
    }

    /**
     * 获取背景色的便利方法
     * @return 背景色，如果没有填充或背景色则返回null
     */
    public String getBackgroundColor() {
        return getFill() != null ? getFill().getBackgroundColor() : null;
    }

    /**
     * 获取填充透明度的便利方法
     * @return 填充透明度，如果没有填充或透明度则返回null
     */
    public Double getFillOpacity() {
        return getFill() != null ? getFill().getOpacity() : null;
    }

    /**
     * 获取边框透明度的便利方法
     * @return 边框透明度，如果没有边框或透明度则返回null
     */
    public Double getBorderOpacity() {
        return getBorder() != null ? getBorder().getOpacity() : null;
    }

    /**
     * 检查是否有边框的便利方法
     * @return 如果有边框配置则返回true
     */
    public boolean hasBorder() {
        return getBorder() != null;
    }

    /**
     * 检查是否有填充的便利方法
     * @return 如果有填充配置则返回true
     */
    public boolean hasFill() {
        return getFill() != null;
    }

    /**
     * 检查是否有阴影的便利方法
     * @return 如果有阴影配置则返回true
     */
    public boolean hasShadow() {
        return getShadow() != null;
    }

    /**
     * 检查边框是否为无填充的便利方法
     * @return 如果边框设置为无填充则返回true
     */
    public boolean isBorderNoFill() {
        return getBorder() != null && Boolean.TRUE.equals(getBorder().getNoFill());
    }
}
