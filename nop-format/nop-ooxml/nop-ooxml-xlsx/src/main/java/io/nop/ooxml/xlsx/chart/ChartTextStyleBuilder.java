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
            // 设置旋转角度（默认为0）
            bodyPrNode.setAttr("rot", "0");
            
            // 设置首末段落间距
            bodyPrNode.setAttr("spcFirstLastPara", "1");
            
            // 设置垂直溢出处理
            bodyPrNode.setAttr("vertOverflow", "ellipsis");
            
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
                        // LEFT_TO_RIGHT是默认值
                        bodyPrNode.setAttr("vert", "horz");
                        break;
                }
            } else {
                bodyPrNode.setAttr("vert", "horz");
            }
            
            // 设置文本换行
            boolean wrapText = textStyle.isWrapText();
            bodyPrNode.setAttr("wrap", wrapText ? "square" : "square");
            
            // 设置内边距（使用OOXML单位：EMU）
            bodyPrNode.setAttr("lIns", "38100");  // 左内边距
            bodyPrNode.setAttr("tIns", "19050");  // 上内边距
            bodyPrNode.setAttr("rIns", "38100");  // 右内边距
            bodyPrNode.setAttr("bIns", "19050");  // 下内边距
            
            // 设置垂直对齐
            ExcelVerticalAlignment verticalAlign = textStyle.getVerticalAlign();
            if (verticalAlign != null) {
                String anchor = mapVerticalAlignmentToOoxml(verticalAlign);
                if (anchor != null) {
                    bodyPrNode.setAttr("anchor", anchor);
                }
            } else {
                bodyPrNode.setAttr("anchor", "ctr");  // 默认居中
            }
            
            // 设置锚点居中
            bodyPrNode.setAttr("anchorCtr", "1");
            
            // 添加自动调整节点
            bodyPrNode.addChild("a:spAutoFit");
            
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
            
            // 构建默认运行属性（字体样式）- 使用defRPr而不是rPr
            if (textStyle.getFont() != null) {
                XNode defRPrNode = pPrNode.addChild("a:defRPr");
                buildRunProperties(defRPrNode, textStyle.getFont());
            }
            
            // 添加结束段落运行属性
            pNode.addChild("a:endParaRPr").setAttr("lang", "en-US");
            
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
            
            // 设置字体粗细
            boolean bold = font.isBold();
            rPrNode.setAttr("b", bold ? "1" : "0");
            
            // 设置斜体
            boolean italic = font.isItalic();
            rPrNode.setAttr("i", italic ? "1" : "0");
            
            // 设置下划线
            ExcelFontUnderline underlineStyle = font.getUnderlineStyle();
            if (underlineStyle != null) {
                String underline = mapUnderlineStyleToOoxml(underlineStyle);
                rPrNode.setAttr("u", underline);
            } else {
                rPrNode.setAttr("u", "none");
            }
            
            // 设置删除线
            boolean strikeout = font.isStrikeout();
            rPrNode.setAttr("strike", strikeout ? "sngStrike" : "noStrike");
            
            // 设置字距调整
            rPrNode.setAttr("kern", "1200");
            
            // 设置基线
            rPrNode.setAttr("baseline", "0");
            
            // 构建字体颜色
            buildFontColor(rPrNode, font.getFontColor());
            
            // 构建字体名称信息
            buildFontTypefaces(rPrNode, font.getFontName());
            
        } catch (Exception e) {
            LOG.warn("Failed to build run properties", e);
        }
    }
    
    /**
     * 构建字体名称信息
     * 生成 a:latin、a:ea、a:cs 元素
     */
    private void buildFontTypefaces(XNode rPrNode, String fontName) {
        try {
            // 如果有具体字体名称，使用它；否则使用主题字体引用
            String typeface = !StringHelper.isEmpty(fontName) ? fontName : "+mn-lt";
            
            // Latin字体（拉丁文字）
            XNode latinNode = rPrNode.addChild("a:latin");
            latinNode.setAttr("typeface", typeface.startsWith("+") ? typeface : fontName);
            
            // East Asian字体（东亚文字）
            XNode eaNode = rPrNode.addChild("a:ea");
            eaNode.setAttr("typeface", "+mn-ea");
            
            // Complex Script字体（复杂脚本）
            XNode csNode = rPrNode.addChild("a:cs");
            csNode.setAttr("typeface", "+mn-cs");
            
        } catch (Exception e) {
            LOG.warn("Failed to build font typefaces", e);
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