package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartTextDirection;
import io.nop.excel.chart.model.ChartTextStyleModel;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.constants.ExcelFontUnderline;
import io.nop.excel.model.constants.ExcelHorizontalAlignment;
import io.nop.excel.model.constants.ExcelVerticalAlignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChartTextStyleParser - 文本样式解析器
 * 处理字体大小、粗细、颜色等属性，支持完整的OOXML颜色修改
 */
public class ChartTextStyleParser {
    private static final Logger LOG = LoggerFactory.getLogger(ChartTextStyleParser.class);
    public static final ChartTextStyleParser INSTANCE = new ChartTextStyleParser();

    /**
     * 解析文本样式
     *
     * @param txPrNode      文本属性节点
     * @param styleProvider 样式提供者
     * @return 解析后的文本样式模型
     */
    public ChartTextStyleModel parseTextStyle(XNode txPrNode, IChartStyleProvider styleProvider) {
        if (txPrNode == null) return null;

        ChartTextStyleModel textStyle = new ChartTextStyleModel();

        // 解析段落样式
        parseParagraphStyle(textStyle, txPrNode.childByTag("a:pPr"), styleProvider);

        // 解析字体样式
        parseFontStyle(textStyle, txPrNode, styleProvider);

        return textStyle;
    }

    /**
     * 解析段落样式
     *
     * @param textStyle     文本样式模型
     * @param pPrNode       段落属性节点
     * @param styleProvider 样式提供者
     */
    private void parseParagraphStyle(ChartTextStyleModel textStyle, XNode pPrNode, IChartStyleProvider styleProvider) {
        if (pPrNode == null) return;

        // 解析水平对齐
        String align = pPrNode.attrText("algn");
        if (align != null) {
            ExcelHorizontalAlignment horizontalAlign = mapHorizontalAlignment(align);
            if (horizontalAlign != null) {
                textStyle.setHorizontalAlign(horizontalAlign);
            }
        }

        // 解析垂直对齐
        String anchor = pPrNode.attrText("anchor");
        if (anchor != null) {
            ExcelVerticalAlignment verticalAlign = mapVerticalAlignment(anchor);
            if (verticalAlign != null) {
                textStyle.setVerticalAlign(verticalAlign);
            }
        }

        // 解析文本方向
        String rtl = pPrNode.attrText("rtl");
        if (rtl != null) {
            ChartTextDirection direction = ChartPropertyHelper.convertToBoolean(rtl) ?
                    ChartTextDirection.RIGHT_TO_LEFT : ChartTextDirection.LEFT_TO_RIGHT;
            textStyle.setTextDirection(direction);
        }
    }

    /**
     * 解析字体样式
     *
     * @param textStyle     文本样式模型
     * @param txPrNode      文本属性节点
     * @param styleProvider 样式提供者
     */
    private void parseFontStyle(ChartTextStyleModel textStyle, XNode txPrNode, IChartStyleProvider styleProvider) {
        ExcelFont font = new ExcelFont();

        // 查找第一个段落中的字体属性
        XNode pNode = txPrNode.childByTag("a:p");
        if (pNode != null) {
            // 首先尝试解析段落属性中的默认运行属性 (a:defRPr)
            XNode pPrNode = pNode.childByTag("a:pPr");
            if (pPrNode != null) {
                XNode defRPrNode = pPrNode.childByTag("a:defRPr");
                if (defRPrNode != null) {
                    parseRunProperties(font, defRPrNode, styleProvider);
                }
            }

            // 然后尝试解析直接的运行属性 (a:rPr) - 这会覆盖默认属性
            XNode rPrNode = pNode.childByTag("a:rPr");
            if (rPrNode != null) {
                parseRunProperties(font, rPrNode, styleProvider);
            }
        }

        textStyle.setFont(font);
    }

    /**
     * 解析运行属性（字体样式）
     *
     * @param font          字体模型
     * @param rPrNode       运行属性节点
     * @param styleProvider 样式提供者
     */
    private void parseRunProperties(ExcelFont font, XNode rPrNode, IChartStyleProvider styleProvider) {
        // 解析字体大小
        Double sz = rPrNode.attrDouble("sz");
        if (sz != null) {
            double size = sz / 100.0; // 转换为磅值
            font.setFontSize((float) size);
        }

        // 解析字体名称 - 优先使用typeface属性
        String typeface = rPrNode.attrText("typeface");
        if (typeface != null) {
            font.setFontName(typeface);
        } else {
            // 如果没有直接的typeface属性，尝试从子元素中获取
            // 优先级：a:latin > a:ea > a:cs
            XNode latinNode = rPrNode.childByTag("a:latin");
            if (latinNode != null) {
                String latinTypeface = latinNode.attrText("typeface");
                if (latinTypeface != null && !latinTypeface.startsWith("+")) {
                    font.setFontName(latinTypeface);
                }
            } else {
                XNode eaNode = rPrNode.childByTag("a:ea");
                if (eaNode != null) {
                    String eaTypeface = eaNode.attrText("typeface");
                    if (eaTypeface != null && !eaTypeface.startsWith("+")) {
                        font.setFontName(eaTypeface);
                    }
                } else {
                    XNode csNode = rPrNode.childByTag("a:cs");
                    if (csNode != null) {
                        String csTypeface = csNode.attrText("typeface");
                        if (csTypeface != null && !csTypeface.startsWith("+")) {
                            font.setFontName(csTypeface);
                        }
                    }
                }
            }
        }

        // 解析字体颜色
        parseFontColor(font, rPrNode, styleProvider);

        // 解析字体粗细
        String b = rPrNode.attrText("b");
        if (b != null) {
            font.setBold(ChartPropertyHelper.convertToBoolean(b));
        }

        // 解析斜体
        String i = rPrNode.attrText("i");
        if (i != null) {
            font.setItalic(ChartPropertyHelper.convertToBoolean(i));
        }

        // 解析下划线
        String u = rPrNode.attrText("u");
        if (u != null) {
            ExcelFontUnderline underlineStyle = mapUnderlineStyle(u);
            font.setUnderlineStyle(underlineStyle);
        }

        // 解析删除线
        String strike = rPrNode.attrText("strike");
        if (strike != null) {
            boolean bStrike = strike.equals("noStrike");
            font.setStrikeout(bStrike);
        }
    }

    /**
     * 解析字体颜色
     * 使用applyColorModifications处理嵌套的颜色修改结构
     *
     * @param font          字体模型
     * @param rPrNode       运行属性节点
     * @param styleProvider 样式提供者
     */
    private void parseFontColor(ExcelFont font, XNode rPrNode, IChartStyleProvider styleProvider) {
        XNode solidFillNode = rPrNode.childByTag("a:solidFill");
        if (solidFillNode != null) {
            try {
                // 处理srgbClr颜色（直接RGB）
                String colorVal = ChartPropertyHelper.getChildVal(solidFillNode, "a:srgbClr");
                if (colorVal != null) {
                    String baseColor = "#" + colorVal;
                    // 使用applyColorModifications处理嵌套的颜色修改
                    XNode srgbClrNode = solidFillNode.childByTag("a:srgbClr");
                    String finalColor = styleProvider.applyColorModifications(baseColor, srgbClrNode);
                    font.setFontColor(finalColor);
                    return;
                }

                // 处理schemeClr颜色（主题颜色）- 样本中常见的模式
                String themeColorName = ChartPropertyHelper.getChildVal(solidFillNode, "a:schemeClr");
                if (themeColorName != null) {
                    // 先解析基础主题颜色
                    String baseColor = styleProvider.getThemeColor(themeColorName);
                    if (baseColor != null) {
                        // 使用applyColorModifications处理嵌套的颜色修改
                        // 这是处理样本中lumMod/lumOff模式的关键
                        XNode schemeClrNode = solidFillNode.childByTag("a:schemeClr");
                        String finalColor = styleProvider.applyColorModifications(baseColor, schemeClrNode);
                        font.setFontColor(finalColor);
                        return;
                    }
                }

                // 如果没有找到颜色定义，使用默认颜色
                font.setFontColor("#000000");

            } catch (Exception e) {
                // 使用LOG.warn处理解析错误，确保解析继续
                LOG.warn("Failed to parse font color, using default color", e);
                font.setFontColor("#000000");
            }
        }
    }

    /**
     * 映射水平对齐方式
     *
     * @param align 对齐字符串
     * @return 对应的水平对齐枚举
     */
    private ExcelHorizontalAlignment mapHorizontalAlignment(String align) {
        switch (align) {
            case "l":
                return ExcelHorizontalAlignment.LEFT;
            case "ctr":
                return ExcelHorizontalAlignment.CENTER;
            case "r":
                return ExcelHorizontalAlignment.RIGHT;
            case "just":
                return ExcelHorizontalAlignment.JUSTIFY;
            case "dist":
                return ExcelHorizontalAlignment.DISTRIBUTED;
            default:
                return null;
        }
    }

    /**
     * 映射垂直对齐方式
     *
     * @param anchor 对齐字符串
     * @return 对应的垂直对齐枚举
     */
    private ExcelVerticalAlignment mapVerticalAlignment(String anchor) {
        switch (anchor) {
            case "t":
                return ExcelVerticalAlignment.TOP;
            case "ctr":
                return ExcelVerticalAlignment.CENTER;
            case "b":
                return ExcelVerticalAlignment.BOTTOM;
            case "just":
                return ExcelVerticalAlignment.JUSTIFY;
            case "dist":
                return ExcelVerticalAlignment.DISTRIBUTED;
            default:
                return null;
        }
    }

    /**
     * 映射下划线样式
     *
     * @param underlineValue OOXML下划线值
     * @return 对应的下划线枚举
     */
    private ExcelFontUnderline mapUnderlineStyle(String underlineValue) {
        if (underlineValue == null) {
            return ExcelFontUnderline.NONE;
        }

        switch (underlineValue.toLowerCase()) {
            case "none":
                return ExcelFontUnderline.NONE;
            case "single":
                return ExcelFontUnderline.SINGLE;
            case "double":
                return ExcelFontUnderline.DOUBLE;
            case "singleaccounting":
            case "single-accounting":
                return ExcelFontUnderline.SINGLE_ACCOUNTING;
            case "doubleaccounting":
            case "double-accounting":
                return ExcelFontUnderline.DOUBLE_ACCOUNTING;
            default:
                // 对于未知值，默认使用单下划线
                return ExcelFontUnderline.SINGLE;
        }
    }
}