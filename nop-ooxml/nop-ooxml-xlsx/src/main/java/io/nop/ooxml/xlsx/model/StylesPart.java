/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.model;

import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.format.BuiltinFormats;
import io.nop.excel.model.ExcelBorder;
import io.nop.excel.model.ExcelBorderStyle;
import io.nop.excel.model.ExcelFill;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.constants.ExcelFontFamily;
import io.nop.ooxml.common.IOfficePackagePart;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StylesPart implements IOfficePackagePart {
    private final String path;
    private List<ExcelStyle> styles;

    public StylesPart(String path, List<ExcelStyle> styles) {
        this.path = path;
        this.styles = Guard.notEmpty(styles, "styles");
    }

    public StylesPart(List<ExcelStyle> styles) {
        this("xl/styles.xml", styles);
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public XNode loadXml() {
        return buildXml(null);
    }

    public List<ExcelStyle> getStyles() {
        return styles;
    }

    public XNode buildXml(IEvalContext context) {
        XNode node = XNode.make("styleSheet");
        node.setAttr("xmlns", "http://schemas.openxmlformats.org/spreadsheetml/2006/main");
        node.setAttr("xmlns:mc", "http://schemas.openxmlformats.org/markup-compatibility/2006");
        node.setAttr("mc:Ignorable", "x14ac x16r2 xr");
        node.setAttr("xmlns:x14ac", "http://schemas.microsoft.com/office/spreadsheetml/2009/9/ac");
        node.setAttr("xmlns:x16r2", "http://schemas.microsoft.com/office/spreadsheetml/2015/02/main");
        node.setAttr("xmlns:xr", "http://schemas.microsoft.com/office/spreadsheetml/2014/revision");
        node.setAttr("xmlns:xr9", "http://schemas.microsoft.com/office/spreadsheetml/2016/revision9");


        Map<String, Integer> numberFormats = new LinkedHashMap<>();

        List<ExcelFill> fills = new ArrayList<>();
        fills.add(new ExcelFill("none"));
        fills.add(new ExcelFill("gray125"));

        List<ExcelFont> fonts = new ArrayList<>();

        List<ExcelBorder> borders = new ArrayList<>();

        int customIndex = 170;

        XNode fontsN = XNode.make("fonts");

        XNode cellXfs = XNode.make("cellXfs");

        for (ExcelStyle style : styles) {
            XNode styleN = XNode.make("xf");
            cellXfs.appendChild(styleN);

            if (!StringHelper.isEmpty(style.getNumberFormat())) {
                int index = BuiltinFormats.getBuiltinFormat(style.getNumberFormat());
                if (index < 0) {
                    if (!numberFormats.containsKey(style.getNumberFormat())) {
                        int fmtIndex = customIndex++;
                        numberFormats.put(style.getNumberFormat(), fmtIndex);
                    }
                    index = numberFormats.get(style.getNumberFormat());
                }
                styleN.setAttr("numFmtId", index);
            }

            ExcelFont font = style.getFont();
            if (font != null) {
                int index = makeFont(fonts, font, fontsN);
                styleN.setAttr("fontId", index);
            }

            if (style.getFillPattern() != null) {
                ExcelFill fill = new ExcelFill();
                fill.setPatternType(style.getFillPattern());
                fill.setBgColor(style.getFillBgColor());
                fill.setFgColor(style.getFillFgColor());
                int index = fills.indexOf(fill);
                if (index < 0) {
                    index = fills.size();
                    fills.add(fill);
                }
                styleN.setAttr("fillId", index);
            }

            ExcelBorder border = style.getBorder();
            if (border != null) {
                borders.add(border);
                styleN.setAttr("borderId", borders.size() - 1);
            }

            styleN.setAttr("xfId", "0");

            if (style.getVerticalAlign() != null) {
                styleN.makeChild("alignment").setAttr("vertical", style.getVerticalAlign().getExcelText());
            }

            if (style.getHorizontalAlign() != null) {
                styleN.makeChild("alignment").setAttr("horizontal", style.getHorizontalAlign().getExcelText());
            }

            if (style.isWrapText()) {
                styleN.makeChild("alignment").setAttr("wrapText", "1");
            }

            if (style.getIndent() != null) {
                styleN.makeChild("alignment").setAttr("indent", style.getIndent());
            }
        }

        if (!fontsN.hasChild()) {
            addDefaultFont(fontsN);
        }

        addNumberFormats(node, numberFormats);
        fontsN.setAttr("count", fontsN.getChildCount());
        node.appendChild(fontsN);

        addFills(node, fills);
        addBorders(node, borders);

        addCellStyleXfs(node);

        if (!cellXfs.hasChild()) {
            addDefaultCellXf(cellXfs);
        }
        cellXfs.setAttr("count", cellXfs.getChildCount());
        node.appendChild(cellXfs);

        addCellStyles(node);

        addExt(node);
        return node;
    }

    /**
     * <font>
     * <sz val="11"/>
     * <color theme="1"/>
     * <name val="等线"/>
     * <family val="2"/>
     * <charset val="134"/>
     * <scheme val="minor"/>
     * </font>
     *
     * @param fontsN
     */
    private void addDefaultFont(XNode fontsN) {
        XNode font = fontsN.addChild("font");
        font.addChild("sz").setAttr("val", "11");
        font.addChild("color").setAttr("theme", "1");
        font.addChild("name").setAttr("val", "等线");
        font.addChild("family").setAttr("val", "2");
        font.addChild("charset").setAttr("val", "134");
        font.addChild("scheme").setAttr("val", "minor");
    }

    /**
     * <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0">
     * <alignment vertical="center"/>
     * </xf>
     *
     * @param cellXfs
     */
    private void addDefaultCellXf(XNode cellXfs) {
        XNode xf = cellXfs.addChild("xf");
        xf.setAttr("numFmtId", "0");
        xf.setAttr("fontId", "0");
        xf.setAttr("fillId", "0");
        xf.setAttr("borderId", "0");
        xf.setAttr("xfId", "0");
        xf.addChild("alignment").setAttr("vertical", "center");
    }

    private void addNumberFormats(XNode node, Map<String, Integer> numberFormats) {
        if (numberFormats.isEmpty())
            return;

        XNode fmts = node.addChild("numFmts");
        for (Map.Entry<String, Integer> entry : numberFormats.entrySet()) {
            XNode fmt = fmts.addChild("numFmt");
            fmt.setAttr("numFmtId", entry.getValue());
            fmt.setAttr("formatCode", entry.getKey());
        }
        fmts.setAttr("count", numberFormats.size());
    }

    private void addFills(XNode node, List<ExcelFill> fills) {
        XNode fillsN = node.addChild("fills");
        fillsN.setAttr("count", fills.size());

        for (ExcelFill fill : fills) {
            XNode fillN = fillsN.addChild("fill");
            XNode patternFillN = fillN.addChild("patternFill");
            patternFillN.setAttr("patternType", fill.getPatternType());

            if (fill.getFgColor() != null) {
                patternFillN.addChild("fgColor").setAttr("rgb", fill.getFgColor());
            }

            if (fill.getBgColor() != null) {
                patternFillN.addChild("bgColor").setAttr("rgb", fill.getBgColor());
            }
        }
    }

    private void addBorders(XNode node, List<ExcelBorder> borders) {
        XNode bordersN = node.addChild("borders");
        for (ExcelBorder border : borders) {
            bordersN.appendChild(toBorderNode(border));
        }

        if (!bordersN.hasChild()) {
            addDefaultBorder(bordersN);
        }
        bordersN.setAttr("count", borders.size());
    }

    /**
     * <border>
     * <left/>
     * <right/>
     * <top/>
     * <bottom/>
     * <diagonal/>
     * </border>
     *
     * @param bordersN
     */
    private void addDefaultBorder(XNode bordersN) {
        XNode border = bordersN.addChild("border");
        border.addChild("left");
        border.addChild("right");
        border.addChild("top");
        border.addChild("bottom");
        border.addChild("diagonal");
    }

    private void addCellStyleXfs(XNode node) {
        XNode xfs = node.addChild("cellStyleXfs");
        XNode xf = xfs.addChild("xf");
        xf.setAttr("numFmtId", "0");
        xf.setAttr("fontId", "0");
        xf.setAttr("fillId", "0");
        xf.setAttr("borderId", "0");
        XNode align = xf.addChild("alignment");
        align.setAttr("vertical", "center");

//        xf = xfs.addChild("xf");
//        xf.setAttr("numFmtId", "0");
//        xf.setAttr("fontId", "5");
//        xf.setAttr("fillId", "0");
//        xf.setAttr("borderId", "0");
//        align = xf.addChild("alignment");
//        align.setAttr("vertical", "center");

        xfs.setAttr("count", xfs.getChildCount());
    }

    private void addCellStyles(XNode node) {
        // <cellStyles count="2"><cellStyle name="常规" xfId="0" builtinId="0"/><cellStyle name="超链接" xfId="1" builtinId="8"/></cellStyles>
        XNode cellStyles = node.addChild("cellStyles");
        XNode cellStyle = cellStyles.addChild("cellStyle");
        cellStyle.setAttr("name", "Normal");
        cellStyle.setAttr("xfId", "0");
        cellStyle.setAttr("builtinId", "0");

        cellStyle = cellStyles.addChild("cellStyle");
        cellStyle.setAttr("name", "HyperLink");
        cellStyle.setAttr("xfId", "0");
        cellStyle.setAttr("builtinId", "8");

        cellStyles.setAttr("count", cellStyles.getChildCount());
    }

    private void addExt(XNode node) {
        node.addChild("dxfs").setAttr("count", "0");
        XNode tableStyles = node.addChild("tableStyles");
        tableStyles.setAttr("count", "0");
        tableStyles.setAttr("defaultTableStyle", "TableStyleMedium2");
        tableStyles.setAttr("defaultPivotStyle", "PivotStyleLight16");

        XNode extLst = node.addChild("extLst");
        XNode ext = extLst.addChild("ext");
        ext.setAttr("uri", "{EB79DEF2-80B8-43e5-95BD-54CBDDF9020C}");
        ext.setAttr("xmlns:x14", "http://schemas.microsoft.com/office/spreadsheetml/2009/9/main");
        ext.addChild("slicerStyles").setAttr("defaultSlicerStyle", "SlicerStyleLight1");
    }

    private XNode toBorderNode(ExcelBorder border) {
        XNode ret = XNode.make("border");
        if (border.getLeftBorder() != null) {
            ret.appendChild(toBorderStyle("left", border.getLeftBorder()));
        }
        if (border.getRightBorder() != null) {
            ret.appendChild(toBorderStyle("right", border.getRightBorder()));
        }

        if (border.getTopBorder() != null) {
            ret.appendChild(toBorderStyle("top", border.getTopBorder()));
        }

        if (border.getBottomBorder() != null) {
            ret.appendChild(toBorderStyle("bottom", border.getBottomBorder()));
        }
        return ret;
    }

    private XNode toBorderStyle(String name, ExcelBorderStyle borderStyle) {
        XNode node = XNode.make(name);
        if (borderStyle.getType() != null) {
            node.setAttr("style", borderStyle.getType().getExcelText());
        }
        if (borderStyle.getColor() != null) {
            node.addChild("color").setAttr("rgb", borderStyle.getColor());
        }
        return node;
    }

    private int makeFont(List<ExcelFont> fonts, ExcelFont font, XNode fontsN) {
        int index = fonts.indexOf(font);
        if (index < 0) {
            fonts.add(font);
            fontsN.appendChild(toFontNode(font));
            return fonts.size() - 1;
        }
        return index;
    }

    private XNode toFontNode(ExcelFont font) {
        XNode node = XNode.make("font");
        node.addChild("name").setAttr("val", font.getFontName());

        if (font.isBold()) {
            node.addChild("b");
        }
        if (font.isItalic()) {
            node.addChild("i");
        }
        if (font.getUnderlineStyle() != null) {
            node.addChild("u");
        }
        if (font.isStrikeout()) {
            node.addChild("strike");
        }

        if (font.getFontSize() != null) {
            node.addChild("sz").setAttr("val", font.getFontSizeString());
        }

        if (font.getCharSet() != null) {
            node.addChild("charset").setAttr("val", font.getCharSet());
        }

        if (font.getFontFamily() != null) {
            ExcelFontFamily family = ExcelFontFamily.fromText(font.getFontFamily());
            if (family != null) {
                node.addChild("family").setAttr("val", family.getValue());
            }
        }

        if (font.getVerticalAlign() != null) {
            node.addChild("vertAlign").setAttr("val", font.getVerticalAlign().name());
        }

        if (font.getFontColor() != null) {
            node.addChild("color").setAttr("rgb", font.getFontColor());
        }
        return node;
    }
}
