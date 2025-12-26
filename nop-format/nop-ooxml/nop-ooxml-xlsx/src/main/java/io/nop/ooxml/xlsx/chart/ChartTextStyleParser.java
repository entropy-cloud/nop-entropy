package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.model.ChartTextStyleModel;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.constants.ExcelHorizontalAlignment;
import io.nop.excel.model.constants.ExcelVerticalAlignment;
import io.nop.excel.chart.constants.ChartTextDirection;

/**
 * ChartTextStyleParser - 文本样式解析器
 * 处理字体大小、粗细、颜色等属性
 */
public class ChartTextStyleParser {
    public static final ChartTextStyleParser INSTANCE = new ChartTextStyleParser();
    
    /**
     * 解析文本样式
     * @param txPrNode 文本属性节点
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
     * @param textStyle 文本样式模型
     * @param pPrNode 段落属性节点
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
     * @param textStyle 文本样式模型
     * @param txPrNode 文本属性节点
     * @param styleProvider 样式提供者
     */
    private void parseFontStyle(ChartTextStyleModel textStyle, XNode txPrNode, IChartStyleProvider styleProvider) {
        ExcelFont font = new ExcelFont();
        
        // 查找第一个段落中的字体属性
        XNode pNode = txPrNode.childByTag("a:p");
        if (pNode != null) {
            XNode rPrNode = pNode.childByTag("a:rPr");
            if (rPrNode != null) {
                parseRunProperties(font, rPrNode, styleProvider);
            }
        }
        
        textStyle.setFont(font);
    }
    
    /**
     * 解析运行属性（字体样式）
     * @param font 字体模型
     * @param rPrNode 运行属性节点
     * @param styleProvider 样式提供者
     */
    private void parseRunProperties(ExcelFont font, XNode rPrNode, IChartStyleProvider styleProvider) {
        // 解析字体大小
        Double sz = rPrNode.attrDouble("sz");
        if (sz != null) {
            double size = sz / 100.0; // 转换为磅值
            font.setFontSize(size);
        }
        
        // 解析字体名称
        String typeface = rPrNode.attrText("typeface");
        if (typeface != null) {
            font.setFontName(typeface);
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
            font.setUnderline(!u.equals("none"));
        }
        
        // 解析删除线
        String strike = rPrNode.attrText("strike");
        if (strike != null) {
            font.setStrikeout(ChartPropertyHelper.convertToBoolean(strike));
        }
    }
    
    /**
     * 解析字体颜色
     * @param font 字体模型
     * @param rPrNode 运行属性节点
     * @param styleProvider 样式提供者
     */
    private void parseFontColor(ExcelFont font, XNode rPrNode, IChartStyleProvider styleProvider) {
        XNode solidFillNode = rPrNode.childByTag("a:solidFill");
        if (solidFillNode != null) {
            // 解析RGB颜色
            XNode srgbClrNode = solidFillNode.childByTag("a:srgbClr");
            if (srgbClrNode != null) {
                String color = srgbClrNode.attrText("val");
                if (color != null) {
                    font.setFontColor(styleProvider.resolveColor(color));
                }
            }
            
            // 解析主题颜色
            XNode schemeClrNode = solidFillNode.childByTag("a:schemeClr");
            if (schemeClrNode != null) {
                String themeColor = schemeClrNode.attrText("val");
                if (themeColor != null) {
                    font.setFontColor(styleProvider.getThemeColor(themeColor));
                }
            }
        }
    }
    
    /**
     * 映射水平对齐方式
     * @param align 对齐字符串
     * @return 对应的水平对齐枚举
     */
    private ExcelHorizontalAlignment mapHorizontalAlignment(String align) {
        switch (align) {
            case "l": return ExcelHorizontalAlignment.LEFT;
            case "ctr": return ExcelHorizontalAlignment.CENTER;
            case "r": return ExcelHorizontalAlignment.RIGHT;
            case "just": return ExcelHorizontalAlignment.JUSTIFY;
            case "dist": return ExcelHorizontalAlignment.DISTRIBUTED;
            default: return null;
        }
    }
    
    /**
     * 映射垂直对齐方式
     * @param anchor 对齐字符串
     * @return 对应的垂直对齐枚举
     */
    private ExcelVerticalAlignment mapVerticalAlignment(String anchor) {
        switch (anchor) {
            case "t": return ExcelVerticalAlignment.TOP;
            case "ctr": return ExcelVerticalAlignment.CENTER;
            case "b": return ExcelVerticalAlignment.BOTTOM;
            case "just": return ExcelVerticalAlignment.JUSTIFY;
            case "dist": return ExcelVerticalAlignment.DISTRIBUTED;
            default: return null;
        }
    }
}