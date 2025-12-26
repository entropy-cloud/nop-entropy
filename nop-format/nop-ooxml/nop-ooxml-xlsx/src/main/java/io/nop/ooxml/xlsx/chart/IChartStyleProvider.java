package io.nop.ooxml.xlsx.chart;

import io.nop.excel.chart.IChartStyleSupportModel;

/**
 * IChartStyleProvider - 图表样式提供者接口
 * 提供theme支持和外部样式合并功能
 */
public interface IChartStyleProvider {
    
    /**
     * 获取主题颜色
     * @param themeColor 主题颜色引用（如"accent1", "accent2"）
     * @return 实际颜色值
     */
    String getThemeColor(String themeColor);
      
    /**
     * 获取颜色定义
     * @param colorRef 颜色引用
     * @return 实际颜色值
     */
    String resolveColor(String colorRef);

    /**
     * 应用主题到模型
     * @param componentType 组件类型
     * @param model 支持样式的模型
     */
    void applyTheme(String componentType, IChartStyleSupportModel model);
}