package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.IChartStyleSupportModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;

/**
 * IChartStyleProvider - 图表样式提供者接口
 * 提供theme支持和外部样式合并功能，支持OOXML颜色修改处理
 */
public interface IChartStyleProvider {
    
    /**
     * 获取主题颜色
     * @param themeColor 主题颜色引用（如"accent1", "accent2", "tx1", "bg1"）
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
     * 应用OOXML颜色修改从XML节点
     * 关键用于处理如下模式:
     * <a:schemeClr val="tx1"><a:lumMod val="65000"/><a:lumOff val="35000"/></a:schemeClr>
     * 
     * @param baseColor 基础颜色（RGB十六进制或主题颜色名称）
     * @param colorNode 包含修改元素的XML节点
     * @return 最终转换后的RGB十六进制格式颜色
     */
    String applyColorModifications(String baseColor, XNode colorNode);

    /**
     * 应用主题到模型
     * @param componentType 组件类型
     * @param model 支持样式的模型
     */
    void applyTheme(String componentType, IChartStyleSupportModel model);
    
    /**
     * 获取组件类型的默认样式
     * @param componentType 组件类型（如"title", "legend", "axis", "series", "grid"）
     * @return 默认样式模型
     */
    ChartShapeStyleModel getDefaultStyle(String componentType);
}