/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.chart;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.constants.ExcelFontUnderline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChartTextBuilder - 图表文本构建器
 * 负责根据给定的文字和字体自动选择合适的形式来生成OOXML文本节点
 * 支持生成富文本(c:rich)、简单文本值(c:v)、单元格引用(c:strRef)等
 */
public class ChartTextBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ChartTextBuilder.class);

    public static final ChartTextBuilder INSTANCE = new ChartTextBuilder();

    /**
     * 构建富文本节点 (c:rich)
     * 用于需要复杂格式的文本，如标题、图例等
     *
     * @param text 文本内容
     * @param font 字体样式，可以为null
     * @return 富文本XNode，如果text为null或空则返回null
     */
    public XNode buildRichText(String text, ExcelFont font) {
        if (StringHelper.isEmpty(text)) {
            return null;
        }

        XNode richNode = XNode.make("c:rich");

        // 构建文本体属性
        richNode.addChild("a:bodyPr");

        // 构建列表样式
        richNode.addChild("a:lstStyle");

        // 构建段落
        XNode pNode = richNode.addChild("a:p");
        XNode rNode = pNode.addChild("a:r");

        // 构建运行属性（字体样式）
        if (font != null) {
            XNode rPrNode = rNode.addChild("a:rPr");
            buildRunProperties(rPrNode, font);
        }

        // 设置文本内容
        XNode tNode = rNode.addChild("a:t");
        tNode.content(text);

        return richNode;
    }

    /**
     * 构建简单文本值节点 (c:v)
     * 用于简单的文本值，如数据标签的值
     *
     * @param value 文本值
     * @return 文本值XNode，如果value为null则返回null
     */
    public XNode buildTextValue(String value) {
        if (value == null) {
            return null;
        }


        XNode vNode = XNode.make("c:v");
        vNode.content(value);
        return vNode;

    }

    /**
     * 构建单元格引用节点 (c:strRef)
     * 用于引用Excel单元格中的文本
     *
     * @param cellRef 单元格引用，如"Sheet1!$A$1"
     * @return 单元格引用XNode，如果cellRef为null或空则返回null
     */
    public XNode buildCellReferenceText(String cellRef) {
        if (StringHelper.isEmpty(cellRef)) {
            return null;
        }


        XNode strRefNode = XNode.make("c:strRef");
        XNode fNode = strRefNode.addChild("c:f");
        fNode.content(cellRef);

        return strRefNode;

    }

    /**
     * 构建文本点节点 (c:pt)
     * 用于缓存数据中的文本点
     *
     * @param index 索引
     * @param value 文本值
     * @return 文本点XNode，如果value为null则返回null
     */
    public XNode buildTextPoint(int index, String value) {
        if (value == null) {
            return null;
        }


        XNode ptNode = XNode.make("c:pt");
        ptNode.setAttr("idx", String.valueOf(index));

        XNode vNode = ptNode.addChild("c:v");
        vNode.content(value);

        return ptNode;

    }

    /**
     * 自动选择合适的文本节点类型
     * 根据输入参数自动选择最合适的文本节点类型：
     * - 如果有cellRef，生成单元格引用节点
     * - 如果有font样式，生成富文本节点
     * - 否则生成简单文本值节点
     *
     * @param text    文本内容
     * @param cellRef 单元格引用，可以为null
     * @param font    字体样式，可以为null
     * @return 合适的文本XNode，如果text和cellRef都为空则返回null
     */
    public XNode buildText(String text, String cellRef, ExcelFont font) {
        // 优先使用单元格引用
        if (!StringHelper.isEmpty(cellRef)) {
            return buildCellReferenceText(cellRef);
        }

        // 如果没有文本内容，返回null
        if (StringHelper.isEmpty(text)) {
            return null;
        }

        // 如果有字体样式，使用富文本
        if (font != null) {
            return buildRichText(text, font);
        }

        // 否则使用简单文本值
        return buildTextValue(text);
    }

    /**
     * 构建运行属性（字体样式）
     */
    private void buildRunProperties(XNode rPrNode, ExcelFont font) {
        if (font == null) {
            return;
        }


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

    }

    /**
     * 构建字体名称信息
     * 生成 a:latin、a:ea、a:cs 元素
     */
    private void buildFontTypefaces(XNode rPrNode, String fontName) {

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

    }

    /**
     * 构建字体颜色
     */
    private void buildFontColor(XNode rPrNode, String fontColor) {
        if (StringHelper.isEmpty(fontColor)) {
            return;
        }


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

    }

    /**
     * 映射下划线样式到OOXML
     */
    private String mapUnderlineStyleToOoxml(io.nop.excel.model.constants.ExcelFontUnderline underlineStyle) {
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
}