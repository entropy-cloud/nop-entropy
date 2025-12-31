package io.nop.excel.chart.model;

import io.nop.excel.chart.model._gen._ChartStyleModel;

public class ChartStyleModel extends _ChartStyleModel{
    public ChartStyleModel(){

    }
    
    /**
     * 根据索引获取颜色，如果索引超出范围则循环使用
     * @param index 颜色索引
     * @return 对应的颜色值，如果colors集合为空则返回null
     */
    public String getColorByIndex(int index) {
        java.util.List<String> colors = getColors();
        if (colors == null || colors.isEmpty()) {
            return null;
        }
        
        // 循环使用颜色集合
        int colorIndex = index % colors.size();
        return colors.get(colorIndex);
    }
    
    /**
     * 获取颜色序列数组
     * @return 颜色序列数组，如果colors集合为空则返回空数组
     */
    public String[] getColorSequence() {
        java.util.List<String> colors = getColors();
        if (colors == null || colors.isEmpty()) {
            return new String[0];
        }
        
        return colors.toArray(new String[colors.size()]);
    }
    
    /**
     * 检查是否有自定义颜色序列
     * @return 如果colors集合非空则返回true，否则返回false
     */
    public boolean hasCustomColors() {
        java.util.List<String> colors = getColors();
        return colors != null && !colors.isEmpty();
    }
}
