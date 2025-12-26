package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import io.nop.excel.chart.model.ChartFillModel;
import io.nop.excel.chart.model.ChartBorderModel;
import io.nop.excel.chart.model.ChartShadowModel;

/**
 * ChartShapeStyleParser - 形状样式解析器
 * 统一处理填充、边框、阴影等样式属性，支持theme颜色解析
 */
public class ChartShapeStyleParser {
    public static final ChartShapeStyleParser INSTANCE = new ChartShapeStyleParser();
    
    /**
     * 解析形状样式，支持theme颜色解析
     * @param spPrNode 形状属性节点
     * @param styleProvider 样式提供者
     * @return 解析后的形状样式模型
     */
    public ChartShapeStyleModel parseShapeStyle(XNode spPrNode, IChartStyleProvider styleProvider) {
        if (spPrNode == null) return null;
        
        ChartShapeStyleModel style = new ChartShapeStyleModel();
        
        // 解析填充 - 传入样式提供者用于theme颜色解析
        parseFill(style, spPrNode.childByTag("a:fill"), styleProvider);
        
        // 解析边框
        parseBorder(style, spPrNode.childByTag("a:ln"), styleProvider);
        
        // 解析阴影
        parseShadow(style, spPrNode.childByTag("a:effectLst"), styleProvider);
        
        return style;
    }
    
    /**
     * 解析填充
     * @param style 形状样式模型
     * @param fillNode 填充节点
     * @param styleProvider 样式提供者
     */
    private void parseFill(ChartShapeStyleModel style, XNode fillNode, IChartStyleProvider styleProvider) {
        if (fillNode == null) return;
        
        ChartFillModel fill = new ChartFillModel();
        
        // 解析纯色填充 - 支持theme颜色解析
        XNode solidFillNode = fillNode.childByTag("a:solidFill");
        if (solidFillNode != null) {
            parseSolidFill(fill, solidFillNode, styleProvider);
        }
        
        // 解析渐变填充
        XNode gradFillNode = fillNode.childByTag("a:gradFill");
        if (gradFillNode != null) {
            parseGradientFill(fill, gradFillNode, styleProvider);
        }
        
        // 解析图案填充
        XNode pattFillNode = fillNode.childByTag("a:pattFill");
        if (pattFillNode != null) {
            parsePatternFill(fill, pattFillNode, styleProvider);
        }
        
        style.setFill(fill);
    }
    
    /**
     * 解析纯色填充
     * @param fill 填充模型
     * @param solidFillNode 纯色填充节点
     * @param styleProvider 样式提供者
     */
    private void parseSolidFill(ChartFillModel fill, XNode solidFillNode, IChartStyleProvider styleProvider) {
        XNode srgbClrNode = solidFillNode.childByTag("a:srgbClr");
        if (srgbClrNode != null) {
            String color = srgbClrNode.attrText("val");
            if (color != null) {
                fill.setForegroundColor(styleProvider.resolveColor(color));
            }
        }
        
        XNode schemeClrNode = solidFillNode.childByTag("a:schemeClr");
        if (schemeClrNode != null) {
            String themeColor = schemeClrNode.attrText("val");
            if (themeColor != null) {
                fill.setForegroundColor(styleProvider.getThemeColor(themeColor));
            }
        }
        
        // 解析透明度
        XNode alphaNode = solidFillNode.childByTag("a:alpha");
        if (alphaNode != null) {
            Double alphaVal = alphaNode.attrDouble("val");
            if (alphaVal != null) {
                double opacity = alphaVal / 100000.0; // 从0-100000转换为0-1
                fill.setOpacity(opacity);
            }
        }
    }
    
    /**
     * 解析渐变填充
     * @param fill 填充模型
     * @param gradFillNode 渐变填充节点
     * @param styleProvider 样式提供者
     */
    private void parseGradientFill(ChartFillModel fill, XNode gradFillNode, IChartStyleProvider styleProvider) {
        // 简化处理：只解析线性渐变
        XNode linNode = gradFillNode.childByTag("a:lin");
        if (linNode != null) {
            // 设置渐变类型为线性
            // 这里可以扩展支持更多渐变类型
        }
        
        // 解析渐变停止点
        XNode gsLstNode = gradFillNode.childByTag("a:gsLst");
        if (gsLstNode != null) {
            // 可以解析渐变停止点，这里简化处理
        }
    }
    
    /**
     * 解析图案填充
     * @param fill 填充模型
     * @param pattFillNode 图案填充节点
     * @param styleProvider 样式提供者
     */
    private void parsePatternFill(ChartFillModel fill, XNode pattFillNode, IChartStyleProvider styleProvider) {
        // 解析前景色
        XNode fgClrNode = pattFillNode.childByTag("a:fgClr");
        if (fgClrNode != null) {
            parseColorNode(fill, fgClrNode, styleProvider, true);
        }
        
        // 解析背景色
        XNode bgClrNode = pattFillNode.childByTag("a:bgClr");
        if (bgClrNode != null) {
            parseColorNode(fill, bgClrNode, styleProvider, false);
        }
    }
    
    /**
     * 解析颜色节点
     * @param fill 填充模型
     * @param colorNode 颜色节点
     * @param styleProvider 样式提供者
     * @param isForeground 是否为前景色
     */
    private void parseColorNode(ChartFillModel fill, XNode colorNode, IChartStyleProvider styleProvider, boolean isForeground) {
        XNode srgbClrNode = colorNode.childByTag("a:srgbClr");
        if (srgbClrNode != null) {
            String color = srgbClrNode.attrText("val");
            if (color != null) {
                if (isForeground) {
                    fill.setForegroundColor(styleProvider.resolveColor(color));
                } else {
                    fill.setBackgroundColor(styleProvider.resolveColor(color));
                }
            }
        }
        
        XNode schemeClrNode = colorNode.childByTag("a:schemeClr");
        if (schemeClrNode != null) {
            String themeColor = schemeClrNode.attrText("val");
            if (themeColor != null) {
                String color = styleProvider.getThemeColor(themeColor);
                if (isForeground) {
                    fill.setForegroundColor(color);
                } else {
                    fill.setBackgroundColor(color);
                }
            }
        }
    }
    
    /**
     * 解析边框
     * @param style 形状样式模型
     * @param lnNode 边框节点
     * @param styleProvider 样式提供者
     */
    private void parseBorder(ChartShapeStyleModel style, XNode lnNode, IChartStyleProvider styleProvider) {
        if (lnNode == null) return;
        
        ChartBorderModel border = new ChartBorderModel();
        
        // 解析边框颜色
        XNode solidFillNode = lnNode.childByTag("a:solidFill");
        if (solidFillNode != null) {
            parseBorderColor(border, solidFillNode, styleProvider);
        }
        
        // 解析边框宽度
        Double width = lnNode.attrDouble("w");
        if (width != null) {
            border.setWidth(width);
        }
        
        // 解析边框样式
        String cap = lnNode.attrText("cap");
        if (cap != null) {
            // 可以映射到ChartLineStyle枚举
        }
        
        style.setBorder(border);
    }
    
    /**
     * 解析边框颜色
     * @param border 边框模型
     * @param solidFillNode 纯色填充节点
     * @param styleProvider 样式提供者
     */
    private void parseBorderColor(ChartBorderModel border, XNode solidFillNode, IChartStyleProvider styleProvider) {
        XNode srgbClrNode = solidFillNode.childByTag("a:srgbClr");
        if (srgbClrNode != null) {
            String color = srgbClrNode.attrText("val");
            if (color != null) {
                border.setColor(styleProvider.resolveColor(color));
            }
        }
        
        XNode schemeClrNode = solidFillNode.childByTag("a:schemeClr");
        if (schemeClrNode != null) {
            String themeColor = schemeClrNode.attrText("val");
            if (themeColor != null) {
                border.setColor(styleProvider.getThemeColor(themeColor));
            }
        }
    }
    
    /**
     * 解析阴影
     * @param style 形状样式模型
     * @param effectLstNode 效果列表节点
     * @param styleProvider 样式提供者
     */
    private void parseShadow(ChartShapeStyleModel style, XNode effectLstNode, IChartStyleProvider styleProvider) {
        if (effectLstNode == null) return;
        
        XNode outerShdwNode = effectLstNode.childByTag("a:outerShdw");
        if (outerShdwNode == null) return;
        
        ChartShadowModel shadow = new ChartShadowModel();
        shadow.setEnabled(true);
        
        // 解析阴影颜色
        XNode srgbClrNode = outerShdwNode.childByTag("a:srgbClr");
        if (srgbClrNode != null) {
            String color = srgbClrNode.attrText("val");
            if (color != null) {
                shadow.setColor(styleProvider.resolveColor(color));
            }
        }
        
        // 解析阴影偏移
        Double blurRad = outerShdwNode.attrDouble("blurRad");
        if (blurRad != null) {
            shadow.setBlurRadius(blurRad);
        }
        
        Double dist = outerShdwNode.attrDouble("dist");
        if (dist != null) {
            shadow.setOffsetX(dist);
            shadow.setOffsetY(dist);
        }
        
        style.setShadow(shadow);
    }
}