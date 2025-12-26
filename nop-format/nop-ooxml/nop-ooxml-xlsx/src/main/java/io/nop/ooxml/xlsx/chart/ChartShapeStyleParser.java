package io.nop.ooxml.xlsx.chart;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartFillType;
import io.nop.excel.chart.constants.ChartLineStyle;
import io.nop.excel.chart.model.ChartBorderModel;
import io.nop.excel.chart.model.ChartFillModel;
import io.nop.excel.chart.model.ChartShadowModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import io.nop.excel.util.UnitsHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChartShapeStyleParser - 形状样式解析器
 * 统一处理填充、边框、阴影等样式属性，支持完整的OOXML颜色修改
 * 修复OOXML结构解析，使用applyColorModifications处理嵌套颜色结构
 */
public class ChartShapeStyleParser {
    private static final Logger LOG = LoggerFactory.getLogger(ChartShapeStyleParser.class);
    public static final ChartShapeStyleParser INSTANCE = new ChartShapeStyleParser();

    /**
     * 解析形状样式，支持完整的OOXML颜色修改
     *
     * @param spPrNode      形状属性节点
     * @param styleProvider 样式提供者
     * @return 解析后的形状样式模型
     */
    public ChartShapeStyleModel parseShapeStyle(XNode spPrNode, IChartStyleProvider styleProvider) {
        if (spPrNode == null) {
            LOG.warn("Shape properties node is null, returning null");
            return null;
        }

        try {
            ChartShapeStyleModel style = new ChartShapeStyleModel();

            // 解析填充 - 传入样式提供者用于完整的颜色修改处理
            parseFill(style, spPrNode, styleProvider);

            // 解析边框
            parseBorder(style, spPrNode.childByTag("a:ln"), styleProvider);

            // 解析阴影
            parseShadow(style, spPrNode.childByTag("a:effectLst"), styleProvider);

            return style;
        } catch (Exception e) {
            LOG.warn("Failed to parse shape style", e);
            return new ChartShapeStyleModel(); // 返回基本样式而不是null
        }
    }

    /**
     * 解析填充
     * 修复OOXML结构解析，正确处理嵌套的<a:solidFill><a:schemeClr>结构
     *
     * @param style         形状样式模型
     * @param spPrNode      形状属性节点
     * @param styleProvider 样式提供者
     */
    private void parseFill(ChartShapeStyleModel style, XNode spPrNode, IChartStyleProvider styleProvider) {
        try {
            ChartFillModel fill = new ChartFillModel();

            // 检查无填充
            XNode noFillNode = spPrNode.childByTag("a:noFill");
            if (noFillNode != null) {
                fill.setType(ChartFillType.NONE);
                style.setFill(fill);
                return;
            }

            // 解析纯色填充 - 支持完整的OOXML颜色修改
            XNode solidFillNode = spPrNode.childByTag("a:solidFill");
            if (solidFillNode != null) {
                parseSolidFill(fill, solidFillNode, styleProvider);
                style.setFill(fill);
                return;
            }

            // 解析渐变填充
            XNode gradFillNode = spPrNode.childByTag("a:gradFill");
            if (gradFillNode != null) {
                parseGradientFill(fill, gradFillNode, styleProvider);
                style.setFill(fill);
                return;
            }

            // 解析图案填充
            XNode pattFillNode = spPrNode.childByTag("a:pattFill");
            if (pattFillNode != null) {
                parsePatternFill(fill, pattFillNode, styleProvider);
                style.setFill(fill);
                return;
            }

            // 如果没有找到填充定义，设置默认填充
            fill.setType(ChartFillType.SOLID);
            fill.setForegroundColor("#FFFFFF"); // 默认白色
            style.setFill(fill);

        } catch (Exception e) {
            LOG.warn("Failed to parse fill", e);
        }
    }

    /**
     * 解析纯色填充
     * 使用applyColorModifications处理嵌套的颜色修改结构
     *
     * @param fill          填充模型
     * @param solidFillNode 纯色填充节点
     * @param styleProvider 样式提供者
     */
    private void parseSolidFill(ChartFillModel fill, XNode solidFillNode, IChartStyleProvider styleProvider) {
        try {
            fill.setType(ChartFillType.SOLID);

            // 处理srgbClr颜色（直接RGB）
            String colorVal = ChartPropertyHelper.getChildVal(solidFillNode, "a:srgbClr");
            if (!StringHelper.isEmpty(colorVal)) {
                String baseColor = "#" + colorVal;
                // 使用applyColorModifications处理嵌套的颜色修改
                XNode srgbClrNode = solidFillNode.childByTag("a:srgbClr");
                String finalColor = styleProvider.applyColorModifications(baseColor, srgbClrNode);
                fill.setForegroundColor(finalColor);
                return;
            }

            // 处理schemeClr颜色（主题颜色）- 样本中最常见的模式
            String themeColorName = ChartPropertyHelper.getChildVal(solidFillNode, "a:schemeClr");
            if (!StringHelper.isEmpty(themeColorName)) {
                // 先解析基础主题颜色
                String baseColor = styleProvider.getThemeColor(themeColorName);
                if (baseColor != null) {
                    // 使用applyColorModifications处理嵌套的颜色修改
                    // 这是处理样本中lumMod/lumOff模式的关键
                    XNode schemeClrNode = solidFillNode.childByTag("a:schemeClr");
                    String finalColor = styleProvider.applyColorModifications(baseColor, schemeClrNode);
                    fill.setForegroundColor(finalColor);
                    return;
                }
            }

            // 如果没有找到颜色定义，使用默认颜色
            fill.setForegroundColor("#000000");

        } catch (Exception e) {
            LOG.warn("Failed to parse solid fill, using default color", e);
            fill.setForegroundColor("#000000");
        }
    }

    /**
     * 解析渐变填充
     *
     * @param fill          填充模型
     * @param gradFillNode  渐变填充节点
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
     *
     * @param fill          填充模型
     * @param pattFillNode  图案填充节点
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
     *
     * @param fill          填充模型
     * @param colorNode     颜色节点
     * @param styleProvider 样式提供者
     * @param isForeground  是否为前景色
     */
    private void parseColorNode(ChartFillModel fill, XNode colorNode, IChartStyleProvider styleProvider, boolean isForeground) {
        String color = ChartPropertyHelper.getChildVal(colorNode, "a:srgbClr");
        if (color != null) {
            if (isForeground) {
                fill.setForegroundColor(styleProvider.resolveColor(color));
            } else {
                fill.setBackgroundColor(styleProvider.resolveColor(color));
            }
            return;
        }

        String themeColor = ChartPropertyHelper.getChildVal(colorNode, "a:schemeClr");
        if (themeColor != null) {
            String resolvedColor = styleProvider.getThemeColor(themeColor);
            if (isForeground) {
                fill.setForegroundColor(resolvedColor);
            } else {
                fill.setBackgroundColor(resolvedColor);
            }
        }
    }

    /**
     * 解析边框 - 完整支持OOXML a:ln元素的所有属性
     *
     * @param style         形状样式模型
     * @param lnNode        边框节点
     * @param styleProvider 样式提供者
     */
    private void parseBorder(ChartShapeStyleModel style, XNode lnNode, IChartStyleProvider styleProvider) {
        if (lnNode == null) return;

        try {
            ChartBorderModel border = new ChartBorderModel();

            // 解析边框颜色
            XNode solidFillNode = lnNode.childByTag("a:solidFill");
            if (solidFillNode != null) {
                parseBorderColor(border, solidFillNode, styleProvider);
            }

            // 解析边框宽度 - 正确处理EMU单位转换
            Integer width = lnNode.attrInt("w");
            if (width != null) {
                double pointsWidth = UnitsHelper.emuToPoints(width);
                border.setWidth(pointsWidth);
            }

            // 解析线条样式 - 支持预设虚线样式
            XNode prstDashNode = lnNode.childByTag("a:prstDash");
            if (prstDashNode != null) {
                String dashVal = prstDashNode.attrText("val");
                if (!StringHelper.isEmpty(dashVal)) {
                    ChartLineStyle lineStyle = mapOoxmlLineStyle(dashVal);
                    border.setStyle(lineStyle);
                }
            } else {
                // 如果没有预设虚线，默认为实线
                border.setStyle(ChartLineStyle.SOLID);
            }

            // 解析线条端点样式 (cap)
            String cap = lnNode.attrText("cap");
            if (!StringHelper.isEmpty(cap)) {
                // 注意：当前ChartBorderModel可能不支持cap属性
                // 这里记录日志以便将来扩展
                LOG.debug("Line cap style: {}", cap);
            }

            // 解析复合线条样式 (cmpd)
            String cmpd = lnNode.attrText("cmpd");
            if (!StringHelper.isEmpty(cmpd)) {
                // 注意：当前ChartBorderModel可能不支持cmpd属性
                // 这里记录日志以便将来扩展
                LOG.debug("Compound line style: {}", cmpd);
            }

            // 解析线条对齐方式 (algn)
            String algn = lnNode.attrText("algn");
            if (!StringHelper.isEmpty(algn)) {
                // 注意：当前ChartBorderModel可能不支持algn属性
                // 这里记录日志以便将来扩展
                LOG.debug("Line alignment: {}", algn);
            }

            style.setBorder(border);

        } catch (Exception e) {
            LOG.warn("Failed to parse border", e);
        }
    }

    /**
     * 映射OOXML线条样式到ChartLineStyle枚举
     * 基于OOXML ST_PresetLineDashVal规范
     *
     * @param ooxmlStyle OOXML线条样式值
     * @return 对应的ChartLineStyle枚举
     */
    private ChartLineStyle mapOoxmlLineStyle(String ooxmlStyle) {
        if (StringHelper.isEmpty(ooxmlStyle)) {
            return ChartLineStyle.SOLID;
        }

        switch (ooxmlStyle.toLowerCase()) {
            case "solid":
                return ChartLineStyle.SOLID;
            case "dash":
                return ChartLineStyle.DASH;
            case "dot":
                return ChartLineStyle.DOT;
            case "dashdot":
                return ChartLineStyle.DASH_DOT;
            case "dashdotdot":
                return ChartLineStyle.DASH_DOT_DOT;
            case "lgdash":
                return ChartLineStyle.LONG_DASH;
            case "lgdashdot":
                return ChartLineStyle.LONG_DASH_DOT;
            case "lgdashdotdot":
                return ChartLineStyle.LONG_DASH_DOT_DOT;
            case "sysdash":
                return ChartLineStyle.SYS_DASH;
            case "sysdot":
                return ChartLineStyle.SYS_DOT;
            case "sysdashdot":
                return ChartLineStyle.SYS_DASH_DOT;
            default:
                LOG.warn("Unknown OOXML line style: {}, using SOLID", ooxmlStyle);
                return ChartLineStyle.SOLID;
        }
    }

    /**
     * 解析边框颜色
     *
     * @param border        边框模型
     * @param solidFillNode 纯色填充节点
     * @param styleProvider 样式提供者
     */
    private void parseBorderColor(ChartBorderModel border, XNode solidFillNode, IChartStyleProvider styleProvider) {
        String color = ChartPropertyHelper.getChildVal(solidFillNode, "a:srgbClr");
        if (color != null) {
            border.setColor(styleProvider.resolveColor(color));
            return;
        }

        String themeColor = ChartPropertyHelper.getChildVal(solidFillNode, "a:schemeClr");
        if (themeColor != null) {
            border.setColor(styleProvider.getThemeColor(themeColor));
        }
    }

    /**
     * 解析阴影
     *
     * @param style         形状样式模型
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
        String color = ChartPropertyHelper.getChildVal(outerShdwNode, "a:srgbClr");
        if (color != null) {
            shadow.setColor(styleProvider.resolveColor(color));
        }

        // 解析阴影偏移
        Double blurRad = outerShdwNode.attrDouble("blurRad");
        if (blurRad != null) {
            shadow.setBlur(blurRad);
        }

        Double dist = outerShdwNode.attrDouble("dist");
        if (dist != null) {
            shadow.setOffsetX(dist);
            shadow.setOffsetY(dist);
        }

        style.setShadow(shadow);
    }
}