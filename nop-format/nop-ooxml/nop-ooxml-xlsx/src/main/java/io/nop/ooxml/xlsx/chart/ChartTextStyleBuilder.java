package io.nop.ooxml.xlsx.chart;

import io.nop.commons.util.StringHelper;
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
 * ChartTextStyleBuilder - 文本样式构建器
 * 负责将ChartTextStyleModel转换为OOXML文本样式XML
 * 支持字体、对齐、颜色等文本属性的完整生成
 */
public class ChartTextStyleBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ChartTextStyleBuilder.class);
    public static final ChartTextStyleBuilder INSTANCE = new ChartTextStyleBuilder();

    /**
     * 构建文本样式XML
     * 
     * @param textStyle 文本样式模型
     * @return 文本样式XML节点，如果样式为空则返回null
     */
    public XNode buildTextStyle(ChartTextStyleModel textStyle) {
        if (textStyle == null) {
            LOG.debug("Text style model is null, skipping text style generation");
            return null;
        }

        try {
            XNode txPrNode = XNode.make("c:txPr");
            
            // 构建body属性
            XNode bodyPrNode = txPrNode.addChild("a:bodyPr");
            buildBodyProperties(bodyPrNode, textStyle);
            
            // 构建列表样式（通常为空）
            txPrNode.addChild("a:lstStyle");
            
            // 构建段落
            XNode pNode = txPrNode.addChild("a:p");
            buildParagraph(pNode, textStyle);
            
            LOG.debug("Successfully built text style XML");
            return txPrNode;

        } catch (Exception e) {
            LOG.warn("Failed to build text style XML", e);
            return null;
        }
    }

    /**
     * 构建body属性
     */
    private void buildBodyProperties(XNode bodyPrNode, ChartTextStyleModel textStyle) {
        try {
            // 设置文本换行
            boolean wrapText = textStyle.isWrapText();
            if (wrapText) {
                bodyPrNode.setAttr("wrap",  "square" );
            }
            
            // 设置文本方向
            ChartTextDirection textDirection = textStyle.getTextDirection();
            if (textDirection != null) {
                switch (textDirection) {
                    case RIGHT_TO_LEFT:
                        bodyPrNode.setAttr("rtlCol", "1");
                        break;
                    case VERTICAL:
                        bodyPrNode.setAttr("vert", "wordArtVert");
                        break;
                    default:
                        // LEFT_TO_RIGHT是默认值，不需要设置
                        break;
                }
            }
            
            // 设置垂直对齐
            ExcelVerticalAlignment verticalAlign = textStyle.getVerticalAlign();
            if (verticalAlign != null) {
                String anchor = mapVerticalAlignmentToOoxml(verticalAlign);
                if (anchor != null) {
                    bodyPrNode.setAttr("anchor", anchor);
                }
            }
            
        } catch (Exception e) {
            LOG.warn("Failed to build body properties", e);
        }
    }

    /**
     * 构建段落
     */
    private void buildParagraph(XNode pNode, ChartTextStyleModel textStyle) {
        try {
            // 构建段落属性
            XNode pPrNode = pNode.addChild("a:pPr");
            buildParagraphProperties(pPrNode, textStyle);
            
            // 构建运行属性（字体样式）
            XNode rNode = pNode.addChild("a:r");
            XNode rPrNode = rNode.addChild("a:rPr");
            buildRunProperties(rPrNode, textStyle.getFont());
            
            // 添加空文本节点
            rNode.addChild("a:t");
            
        } catch (Exception e) {
            LOG.warn("Failed to build paragraph", e);
        }
    }

    /**
     * 构建段落属性
     */
    private void buildParagraphProperties(XNode pPrNode, ChartTextStyleModel textStyle) {
        try {
            // 设置水平对齐
            ExcelHorizontalAlignment horizontalAlign = textStyle.getHorizontalAlign();
            if (horizontalAlign != null) {
                String align = mapHorizontalAlignmentToOoxml(horizontalAlign);
                if (align != null) {
                    pPrNode.setAttr("algn", align);
                }
            }
            
            // 设置文本方向（RTL）
            ChartTextDirection textDirection = textStyle.getTextDirection();
            if (textDirection == ChartTextDirection.RIGHT_TO_LEFT) {
                pPrNode.setAttr("rtl", "1");
            }
            
        } catch (Exception e) {
            LOG.warn("Failed to build paragraph properties", e);
        }
    }

    /**
     * 构建运行属性（字体样式）
     */
    private void buildRunProperties(XNode rPrNode, ExcelFont font) {
        if (font == null) {
            LOG.debug("Font is null, using default font properties");
            return;
        }

        try {
            // 设置字体大小
            Float fontSize = font.getFontSize();
            if (fontSize != null && fontSize > 0) {
                // 转换为OOXML单位（百分之一磅）
                int ooxmlSize = Math.round(fontSize * 100);
                rPrNode.setAttr("sz", String.valueOf(ooxmlSize));
            }
            
            // 设置字体名称
            String fontName = font.getFontName();
            if (!StringHelper.isEmpty(fontName)) {
                rPrNode.setAttr("typeface", fontName);
            }
            
            // 设置字体粗细
            boolean bold = font.isBold();
            if (bold) {
                rPrNode.setAttr("b", "1");
            }
            
            // 设置斜体
            boolean italic = font.isItalic();
            if (italic) {
                rPrNode.setAttr("i", "1");
            }
            
            // 设置下划线
            ExcelFontUnderline underlineStyle = font.getUnderlineStyle();
            if (underlineStyle != null && underlineStyle != ExcelFontUnderline.NONE) {
                String underline = mapUnderlineStyleToOoxml(underlineStyle);
                rPrNode.setAttr("u", underline);
            }
            
            // 设置删除线
            boolean strikeout = font.isStrikeout();
            if (strikeout) {
                rPrNode.setAttr("strike", "1");
            }
            
            // 构建字体颜色
            buildFontColor(rPrNode, font.getFontColor());
            
        } catch (Exception e) {
            LOG.warn("Failed to build run properties", e);
        }
    }

    /**
     * 构建字体颜色
     */
    private void buildFontColor(XNode rPrNode, String fontColor) {
        if (StringHelper.isEmpty(fontColor)) {
            return;
        }

        try {
            XNode solidFillNode = rPrNode.addChild("a:solidFill");
            
            // 检查是否为RGB颜色（以#开头）
            if (fontColor.startsWith("#")) {
                String rgbValue = fontColor.substring(1).toUpperCase();
                XNode srgbClrNode = solidFillNode.addChild("a:srgbClr");
                srgbClrNode.setAttr("val", rgbValue);
            } else {
                // 假设是主题颜色
                XNode schemeClrNode = solidFillNode.addChild("a:schemeClr");
                schemeClrNode.setAttr("val", fontColor);
            }
            
        } catch (Exception e) {
            LOG.warn("Failed to build font color: {}", fontColor, e);
        }
    }

    /**
     * 映射水平对齐方式到OOXML
     */
    private String mapHorizontalAlignmentToOoxml(ExcelHorizontalAlignment alignment) {
        switch (alignment) {
            case LEFT:
                return "l";
            case CENTER:
                return "ctr";
            case RIGHT:
                return "r";
            case JUSTIFY:
                return "just";
            case DISTRIBUTED:
                return "dist";
            default:
                return null;
        }
    }

    /**
     * 映射垂直对齐方式到OOXML
     */
    private String mapVerticalAlignmentToOoxml(ExcelVerticalAlignment alignment) {
        switch (alignment) {
            case TOP:
                return "t";
            case CENTER:
                return "ctr";
            case BOTTOM:
                return "b";
            case JUSTIFY:
                return "just";
            case DISTRIBUTED:
                return "dist";
            default:
                return null;
        }
    }

    /**
     * 映射下划线样式到OOXML
     */
    private String mapUnderlineStyleToOoxml(ExcelFontUnderline underlineStyle) {
        switch (underlineStyle) {
            case NONE:
                return "none";
            case SINGLE:
                return "sng";
            case DOUBLE:
                return "dbl";
            case SINGLE_ACCOUNTING:
                return "sngAccounting";
            case DOUBLE_ACCOUNTING:
                return "dblAccounting";
            default:
                return "sng";
        }
    }

    /**
     * 构建简单文本样式（用于标题等简单文本）
     * 
     * @param text 文本内容
     * @param font 字体样式
     * @return 文本XML节点
     */
    public XNode buildSimpleText(String text, ExcelFont font) {
        if (StringHelper.isEmpty(text)) {
            return null;
        }

        try {
            XNode richNode = XNode.make("c:rich");
            
            // 构建body属性
            richNode.addChild("a:bodyPr");
            
            // 构建列表样式
            richNode.addChild("a:lstStyle");
            
            // 构建段落
            XNode pNode = richNode.addChild("a:p");
            XNode rNode = pNode.addChild("a:r");
            
            // 构建运行属性
            if (font != null) {
                XNode rPrNode = rNode.addChild("a:rPr");
                buildRunProperties(rPrNode, font);
            }
            
            // 设置文本内容
            XNode tNode = rNode.addChild("a:t");
            tNode.content(text);
            
            return richNode;
            
        } catch (Exception e) {
            LOG.warn("Failed to build simple text: {}", text, e);
            return null;
        }
    }
}