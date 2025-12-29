package io.nop.ooxml.xlsx.chart;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartFillPatternType;
import io.nop.excel.chart.constants.ChartFillType;
import io.nop.excel.chart.constants.ChartGradientDirection;  // 新增导入
import io.nop.excel.chart.constants.ChartLineStyle;
import io.nop.excel.chart.model.ChartBorderModel;
import io.nop.excel.chart.model.ChartFillModel;
import io.nop.excel.chart.model.ChartFillPictureModel;
import io.nop.excel.chart.model.ChartGradientModel;
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

        ChartShapeStyleModel style = new ChartShapeStyleModel();

        // 解析填充 - 传入样式提供者用于完整的颜色修改处理
        parseFill(style, spPrNode, styleProvider);

        // 解析边框
        parseBorder(style, spPrNode.childByTag("a:ln"), styleProvider);

        // 解析阴影
        parseShadow(style, spPrNode.childByTag("a:effectLst"), styleProvider);

        return style;

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

        // 解析图片填充
        XNode blipFillNode = spPrNode.childByTag("a:blipFill");
        if (blipFillNode != null) {
            parsePictureFill(fill, blipFillNode, styleProvider);
            style.setFill(fill);
            return;
        }

        // 如果没有找到填充定义，设置默认填充
        fill.setType(ChartFillType.SOLID);
        fill.setForegroundColor("#FFFFFF"); // 默认白色
        style.setFill(fill);

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

        fill.setType(ChartFillType.SOLID);

        // 解析透明度
        parseOpacity(fill, solidFillNode);

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


    }

    /**
     * 解析渐变填充
     *
     * @param fill          填充模型
     * @param gradFillNode  渐变填充节点
     * @param styleProvider 样式提供者
     */
    private void parseGradientFill(ChartFillModel fill, XNode gradFillNode, IChartStyleProvider styleProvider) {

        fill.setType(ChartFillType.GRADIENT);

        ChartGradientModel gradient = new ChartGradientModel();
        gradient.setEnabled(true);

        // 解析渐变停止点
        XNode gsLstNode = gradFillNode.childByTag("a:gsLst");
        if (gsLstNode != null) {
            parseGradientStops(gradient, gsLstNode, styleProvider);
        }

        // 解析渐变方向
        parseGradientDirection(gradient, gradFillNode);

        // 解析透明度
        parseOpacity(fill, gradFillNode);

        fill.setGradient(gradient);

    }

    /**
     * 解析渐变方向 - 重构版本，使用新的枚举
     *
     * @param gradient     渐变模型
     * @param gradFillNode 渐变填充节点
     */
    private void parseGradientDirection(ChartGradientModel gradient, XNode gradFillNode) {
        // 检查线性渐变
        XNode linNode = gradFillNode.childByTag("a:lin");
        if (linNode != null) {
            parseLinearGradientDirection(gradient, linNode);
            return;
        }

        // 检查路径渐变
        XNode pathNode = gradFillNode.childByTag("a:path");
        if (pathNode != null) {
            parsePathGradientDirection(gradient, pathNode);
            return;
        }

        // 检查径向渐变（兼容旧版本）
        XNode radNode = gradFillNode.childByTag("a:rad");
        if (radNode != null) {
            // 径向渐变视为特殊类型的路径渐变
            gradient.setDirection(ChartGradientDirection.FROM_CENTER);
            return;
        }

        // 默认方向
        gradient.setDirection(ChartGradientDirection.HORIZONTAL);
    }

    /**
     * 解析线性渐变方向
     *
     * @param gradient 渐变模型
     * @param linNode  线性渐变节点
     */
    private void parseLinearGradientDirection(ChartGradientModel gradient, XNode linNode) {
        // 解析角度
        String angleStr = linNode.attrText("ang");
        if (angleStr != null) {
            Double degrees = ChartPropertyHelper.ooxmlAngleStringToDegrees(angleStr);
            if (degrees != null) {
                gradient.setAngle(degrees);

                // 根据角度确定方向
                ChartGradientDirection direction = determineDirectionFromAngle(degrees);
                gradient.setDirection(direction);
                return;
            }
        }

        // 如果没有角度或角度解析失败，使用默认方向
        gradient.setDirection(ChartGradientDirection.HORIZONTAL);
    }

    /**
     * 根据角度确定渐变方向
     *
     * @param degrees 角度值（度）
     * @return 对应的渐变方向
     */
    private ChartGradientDirection determineDirectionFromAngle(double degrees) {
        // 规范化角度到0-360范围
        double normalizedDegrees = ((degrees % 360) + 360) % 360;

        // 检查是否为常见角度
        if (Math.abs(normalizedDegrees) < 0.001 || Math.abs(normalizedDegrees - 360) < 0.001) {
            return ChartGradientDirection.HORIZONTAL;  // 0°
        }
        if (Math.abs(normalizedDegrees - 90) < 0.001) {
            return ChartGradientDirection.VERTICAL;  // 90°
        }
        if (Math.abs(normalizedDegrees - 135) < 0.001) {
            return ChartGradientDirection.DIAGONAL_UP;  // 135°
        }
        if (Math.abs(normalizedDegrees - 315) < 0.001 || Math.abs(normalizedDegrees + 45) < 0.001) {
            return ChartGradientDirection.DIAGONAL_DOWN;  // 315° 或 -45°
        }

        // 其他角度视为自定义
        return ChartGradientDirection.CUSTOM;
    }

    /**
     * 解析路径渐变方向
     *
     * @param gradient 渐变模型
     * @param pathNode 路径渐变节点
     */
    private void parsePathGradientDirection(ChartGradientModel gradient, XNode pathNode) {
        String pathType = pathNode.attrText("path");
        if (!StringHelper.isEmpty(pathType)) {
            if ("circle".equalsIgnoreCase(pathType)) {
                gradient.setDirection(ChartGradientDirection.FROM_CENTER);
            } else if ("rect".equalsIgnoreCase(pathType)) {
                gradient.setDirection(ChartGradientDirection.FROM_CORNER);
            } else {
                // 其他路径类型，使用自定义
                gradient.setDirection(ChartGradientDirection.CUSTOM);
                LOG.debug("Unknown path type: {}, using CUSTOM direction", pathType);
            }
        } else {
            // 没有路径类型，默认从中心
            gradient.setDirection(ChartGradientDirection.FROM_CENTER);
        }
    }

    /**
     * 解析渐变停止点
     *
     * @param gradient      渐变模型
     * @param gsLstNode     渐变停止点列表节点
     * @param styleProvider 样式提供者
     */
    private void parseGradientStops(ChartGradientModel gradient, XNode gsLstNode, IChartStyleProvider styleProvider) {

        java.util.List<XNode> gsNodes = gsLstNode.childrenByTag("a:gs");
        if (gsNodes.isEmpty()) {
            return;
        }

        // 简化处理：只取第一个和最后一个停止点作为开始和结束颜色
        XNode firstGs = gsNodes.get(0);
        XNode lastGs = gsNodes.get(gsNodes.size() - 1);

        // 解析开始颜色
        String startColor = parseGradientStopColor(firstGs, styleProvider);
        if (startColor != null) {
            gradient.setStartColor(startColor);
        }

        // 解析结束颜色
        String endColor = parseGradientStopColor(lastGs, styleProvider);
        if (endColor != null) {
            gradient.setEndColor(endColor);
        }

    }

    /**
     * 解析渐变停止点颜色
     *
     * @param gsNode        渐变停止点节点
     * @param styleProvider 样式提供者
     * @return 解析后的颜色值
     */
    private String parseGradientStopColor(XNode gsNode, IChartStyleProvider styleProvider) {
        // 处理 srgbClr
        String srgbColor = ChartPropertyHelper.getChildVal(gsNode, "a:srgbClr");
        if (!StringHelper.isEmpty(srgbColor)) {
            String baseColor = "#" + srgbColor;
            XNode srgbClrNode = gsNode.childByTag("a:srgbClr");
            return styleProvider.applyColorModifications(baseColor, srgbClrNode);
        }

        // 处理 schemeClr
        String schemeColor = ChartPropertyHelper.getChildVal(gsNode, "a:schemeClr");
        if (!StringHelper.isEmpty(schemeColor)) {
            String baseColor = styleProvider.getThemeColor(schemeColor);
            if (baseColor != null) {
                XNode schemeClrNode = gsNode.childByTag("a:schemeClr");
                return styleProvider.applyColorModifications(baseColor, schemeClrNode);
            }
        }

        return null;
    }

    /**
     * 解析图案填充
     *
     * @param fill          填充模型
     * @param pattFillNode  图案填充节点
     * @param styleProvider 样式提供者
     */
    private void parsePatternFill(ChartFillModel fill, XNode pattFillNode, IChartStyleProvider styleProvider) {

        fill.setType(ChartFillType.PATTERN);

        // 解析透明度
        parseOpacity(fill, pattFillNode);

        // 解析图案类型
        String prst = pattFillNode.attrText("prst");
        if (!StringHelper.isEmpty(prst)) {
            ChartFillPatternType patternType = mapOoxmlPatternType(prst);
            fill.setPattern(patternType);
        }

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
     * 映射 OOXML 图案类型到 ChartFillPatternType
     *
     * @param ooxmlPattern OOXML 图案类型
     * @return 对应的 ChartFillPatternType
     */
    private ChartFillPatternType mapOoxmlPatternType(String ooxmlPattern) {
        if (StringHelper.isEmpty(ooxmlPattern)) {
            return ChartFillPatternType.SOLID;
        }


        // 根据现有的 ChartFillPatternType 枚举值进行映射
        switch (ooxmlPattern.toLowerCase()) {
            case "solid":
                return ChartFillPatternType.SOLID;
            case "pct5":
                return ChartFillPatternType.PERCENT_5;
            case "pct10":
                return ChartFillPatternType.PERCENT_10;
            case "pct20":
                return ChartFillPatternType.PERCENT_20;
            case "pct25":
                return ChartFillPatternType.PERCENT_25;
            case "pct30":
                return ChartFillPatternType.PERCENT_30;
            case "pct40":
                return ChartFillPatternType.PERCENT_40;
            case "pct50":
                return ChartFillPatternType.PERCENT_50;
            case "pct60":
                return ChartFillPatternType.PERCENT_60;
            case "pct70":
                return ChartFillPatternType.PERCENT_70;
            case "pct75":
                return ChartFillPatternType.PERCENT_75;
            case "pct80":
                return ChartFillPatternType.PERCENT_80;
            case "pct90":
                return ChartFillPatternType.PERCENT_90;
            case "horz":
                return ChartFillPatternType.HORIZONTAL_STRIPE;
            case "vert":
                return ChartFillPatternType.VERTICAL_STRIPE;
            case "bdiag":
                return ChartFillPatternType.BACKWARD_DIAGONAL;
            case "fdiag":
                return ChartFillPatternType.FORWARD_DIAGONAL;
            case "cross":
                return ChartFillPatternType.CROSS;
            case "diagcross":
                return ChartFillPatternType.DIAGONAL_CROSS;
            case "darkhorz":
                return ChartFillPatternType.DARK_HORIZONTAL;
            case "darkvert":
                return ChartFillPatternType.DARK_VERTICAL;
            case "darkbdiag":
                return ChartFillPatternType.DARK_BACKWARD_DIAGONAL;
            case "darkfdiag":
                return ChartFillPatternType.DARK_FORWARD_DIAGONAL;
            case "darkcross":
                return ChartFillPatternType.DARK_CROSS;
            case "darkdiagcross":
                return ChartFillPatternType.DARK_DIAGONAL_CROSS;
            case "lgspot":
                return ChartFillPatternType.LARGE_SPOT;
            case "opendtl":
                return ChartFillPatternType.CHECKER_BOARD;
            case "none":
                return ChartFillPatternType.NONE;
            default:
                LOG.warn("Unknown OOXML pattern type: {}, using SOLID", ooxmlPattern);
                return ChartFillPatternType.SOLID;
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
            String baseColor = "#" + color;
            XNode srgbClrNode = colorNode.childByTag("a:srgbClr");
            String finalColor = styleProvider.applyColorModifications(baseColor, srgbClrNode);
            if (isForeground) {
                fill.setForegroundColor(finalColor);
            } else {
                fill.setBackgroundColor(finalColor);
            }
            return;
        }

        String themeColor = ChartPropertyHelper.getChildVal(colorNode, "a:schemeClr");
        if (themeColor != null) {
            String baseColor = styleProvider.getThemeColor(themeColor);
            if (baseColor != null) {
                XNode schemeClrNode = colorNode.childByTag("a:schemeClr");
                String finalColor = styleProvider.applyColorModifications(baseColor, schemeClrNode);
                if (isForeground) {
                    fill.setForegroundColor(finalColor);
                } else {
                    fill.setBackgroundColor(finalColor);
                }
            }
        }
    }

    /**
     * 解析图片填充
     *
     * @param fill          填充模型
     * @param blipFillNode  图片填充节点
     * @param styleProvider 样式提供者
     */
    private void parsePictureFill(ChartFillModel fill, XNode blipFillNode, IChartStyleProvider styleProvider) {

        fill.setType(ChartFillType.PICTURE);

        // 解析透明度
        parseOpacity(fill, blipFillNode);

        ChartFillPictureModel picture = new ChartFillPictureModel();

        // 解析图片引用
        XNode blipNode = blipFillNode.childByTag("a:blip");
        if (blipNode != null) {
            String embed = blipNode.attrText("r:embed");
            if (!StringHelper.isEmpty(embed)) {
                picture.setEmbedId(embed);
            }

            String link = blipNode.attrText("r:link");
            if (!StringHelper.isEmpty(link)) {
                picture.setImageUrl(link);
            }
        }

        // 解析拉伸模式
        XNode stretchNode = blipFillNode.childByTag("a:stretch");
        if (stretchNode != null) {
            picture.setStretch(true);

            // 解析填充矩形
            XNode fillRectNode = stretchNode.childByTag("a:fillRect");
            if (fillRectNode != null) {
                // 可以解析 l, t, r, b 属性来设置填充区域
                LOG.debug("Picture fill rect found but not fully implemented");
            }
        }

        // 解析平铺模式
        XNode tileNode = blipFillNode.childByTag("a:tile");
        if (tileNode != null) {
            picture.setTitle(true);

            // 解析平铺属性
            Double tx = tileNode.attrDouble("tx");
            Double ty = tileNode.attrDouble("ty");
            Double sx = tileNode.attrDouble("sx");
            Double sy = tileNode.attrDouble("sy");

            if (tx != null || ty != null || sx != null || sy != null) {
                LOG.debug("Picture tile properties found but not fully implemented");
            }
        }

        fill.setPicture(picture);

    }

    /**
     * 解析透明度/不透明度
     *
     * @param fill     填充模型
     * @param fillNode 填充节点
     */
    private void parseOpacity(ChartFillModel fill, XNode fillNode) {

        // 查找透明度相关的子节点
        for (XNode child : fillNode.getChildren()) {
            String tagName = child.getTagName();
            if (tagName != null && (tagName.endsWith("alpha") || tagName.endsWith("Alpha"))) {
                Double val = child.attrDouble("val");
                if (val != null) {
                    // OOXML 透明度值通常是 0-100000 的范围，转换为 0.0-1.0
                    double opacity = val / 100000.0;
                    fill.setOpacity(Math.max(0.0, Math.min(1.0, opacity)));
                    break;
                }
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


        ChartBorderModel border = new ChartBorderModel();

        // 解析边框颜色
        XNode solidFillNode = lnNode.childByTag("a:solidFill");
        if (solidFillNode != null) {
            parseBorderColor(border, solidFillNode, styleProvider);

            // 解析边框透明度
            parseBorderOpacity(border, solidFillNode);
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

        // 解析自定义虚线样式
        XNode custDashNode = lnNode.childByTag("a:custDash");
        if (custDashNode != null) {
            // 注意：当前ChartBorderModel可能不支持自定义虚线
            // 这里记录日志以便将来扩展
            LOG.debug("Custom dash pattern found but not fully supported");
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

        // 解析线条连接样式
        XNode joinNode = lnNode.childByTag("a:round");
        if (joinNode == null) joinNode = lnNode.childByTag("a:bevel");
        if (joinNode == null) joinNode = lnNode.childByTag("a:miter");
        if (joinNode != null) {
            LOG.debug("Line join style found: {}", joinNode.getTagName());
            border.setRound(true);
        }

        style.setBorder(border);

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
            String baseColor = "#" + color;
            XNode srgbClrNode = solidFillNode.childByTag("a:srgbClr");
            String finalColor = styleProvider.applyColorModifications(baseColor, srgbClrNode);
            border.setColor(finalColor);
            return;
        }

        String themeColor = ChartPropertyHelper.getChildVal(solidFillNode, "a:schemeClr");
        if (themeColor != null) {
            String baseColor = styleProvider.getThemeColor(themeColor);
            if (baseColor != null) {
                XNode schemeClrNode = solidFillNode.childByTag("a:schemeClr");
                String finalColor = styleProvider.applyColorModifications(baseColor, schemeClrNode);
                border.setColor(finalColor);
            }
        }
    }

    /**
     * 解析边框透明度
     *
     * @param border        边框模型
     * @param solidFillNode 纯色填充节点
     */
    private void parseBorderOpacity(ChartBorderModel border, XNode solidFillNode) {

        // 查找透明度相关的子节点
        for (XNode child : solidFillNode.getChildren()) {
            String tagName = child.getTagName();
            if (tagName != null && (tagName.endsWith("alpha") || tagName.endsWith("Alpha"))) {
                Double val = child.attrDouble("val");
                if (val != null) {
                    // OOXML 透明度值通常是 0-100000 的范围，转换为 0.0-1.0
                    double opacity = val / 100000.0;
                    // 注意：当前ChartBorderModel可能不支持opacity属性
                    // 这里记录日志以便将来扩展
                    LOG.debug("Border opacity: {}", opacity);
                    break;
                }
            }
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