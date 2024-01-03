/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ooxml.xlsx.parse;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.format.BuiltinFormats;
import io.nop.excel.model.ExcelBorder;
import io.nop.excel.model.ExcelBorderStyle;
import io.nop.excel.model.ExcelFill;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.color.ColorHelper;
import io.nop.excel.model.color.PredefinedColors;
import io.nop.excel.model.constants.ExcelFontFamily;
import io.nop.excel.model.constants.ExcelFontUnderline;
import io.nop.excel.model.constants.ExcelFontVerticalAlign;
import io.nop.excel.model.constants.ExcelHorizontalAlignment;
import io.nop.excel.model.constants.ExcelLineStyle;
import io.nop.excel.model.constants.ExcelVerticalAlignment;
import io.nop.ooxml.xlsx.model.ExcelOfficePackage;
import io.nop.ooxml.xlsx.model.ThemesPart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StylesPartParser {
    private final Map<String, String> numberFormats = new HashMap<>();
    private final List<ExcelFont> fonts = new ArrayList<>();
    private final List<ExcelBorder> borders = new ArrayList<>();
    private final List<ExcelFill> fills = new ArrayList<>();
    private final List<ExcelStyle> cellStyleXfs = new ArrayList<>();
    private final List<ExcelStyle> cellXfs = new ArrayList<>();
    private final ExcelOfficePackage pkg;
    private final ThemesPart themes;

    public StylesPartParser(ExcelOfficePackage pkg) {
        this.pkg = pkg;
        this.themes = pkg.getTheme1();
    }

    public ExcelOfficePackage getPkg() {
        return pkg;
    }

    public List<ExcelStyle> parseFromNode(XNode node) {
        parseNumberFormats(node);
        parseFonts(node);
        parseFills(node);
        parseBorders(node);
        parseCellStyleXfs(node);
        parseCellXfs(node);
        return cellXfs;
    }

    /**
     * <numFmts count="1">
     * <numFmt numFmtId="176" formatCode="0.00_ "/>
     * </numFmts>
     */
    private void parseNumberFormats(XNode node) {
        XNode numFmts = node.childByTag("numFmts");
        if (numFmts != null) {
            for (XNode numFmt : numFmts.getChildren()) {
                String numFmtId = numFmt.attrText("numFmtId");
                String formatCode = numFmt.attrText("formatCode");
                if (numFmtId != null && formatCode != null) {
                    numberFormats.put(numFmtId, formatCode);
                }
            }
        }
    }

    private void parseFonts(XNode node) {
        XNode fonts = node.childByTag("fonts");
        if (fonts != null) {
            for (XNode fontNode : fonts.getChildren()) {
                ExcelFont font = parseFont(fontNode);
                this.fonts.add(font);
            }
        }
    }

    private ExcelFont parseFont(XNode node) {
        ExcelFont ret = new ExcelFont();
        ret.setLocation(node.getLocation());
        if (node.hasChild("b")) {
            ret.setBold(true);
        }
        if (node.hasChild("u")) {
            ret.setUnderlineStyle(ExcelFontUnderline.SINGLE);
        }
        if (node.hasChild("strike")) {
            ret.setStrikeout(true);
        }
        Object val = node.childAttr("sz", "val");
        if (val != null) {
            ret.setFontSize(ConvertHelper.toFloat(val).shortValue());
        }

        Object charset = node.childAttr("charset", "val");
        if (charset != null) {
            ret.setCharSet(ConvertHelper.toInt(charset));
        }

        Object family = node.childAttr("family", "val");
        if (family != null) {
            ExcelFontFamily fontFamily = ExcelFontFamily.fromCode(ConvertHelper.toInt(family));
            if (fontFamily != null)
                ret.setFontFamily(fontFamily.getText());
        }

        String vertAlign = (String) node.childAttr("vertAlign", "val");
        if (vertAlign != null) {
            ret.setVerticalAlign(ExcelFontVerticalAlign.fromText(vertAlign));
        }

        ret.setItalic(node.hasChild("i"));
        ret.setFontName((String) node.childAttr("name", "val"));

        String rgb = parseColor(node.childByTag("color"));
        ret.setFontColor(rgb);
        return ret;
    }

    private void parseBorders(XNode node) {
        XNode bordersN = node.childByTag("borders");
        if (bordersN != null) {
            for (XNode borderN : bordersN.getChildren()) {
                ExcelBorder border = new ExcelBorder();
                border.setLeftBorder(parseBorderStyle(borderN.childByTag("left")));
                border.setRightBorder(parseBorderStyle(borderN.childByTag("right")));
                border.setTopBorder(parseBorderStyle(borderN.childByTag("top")));
                border.setBottomBorder(parseBorderStyle(borderN.childByTag("bottom")));

                ExcelBorderStyle diagonal = parseBorderStyle(borderN.childByTag("diagonal"));
                border.setDiagonalLeftBorder(diagonal);
                //border.setDiagonalRightBorder(diagonal);
                this.borders.add(border);
            }
        }
    }

    ExcelBorderStyle parseBorderStyle(XNode node) {
        if (node == null) return null;
        ExcelBorderStyle style = new ExcelBorderStyle();
        String styleName = node.attrText("style");
        style.setType(ExcelLineStyle.fromExcelText(styleName));

        String color = parseColor(node.childByTag("color"));
        style.setColor(color);
        style.setWeight(1);

        if (style.getType() != null) {
            style.setWeight(style.getType().getWeight());
        }
        return style;
    }

    private void parseFills(XNode node) {
        XNode fillsN = node.childByTag("fills");
        if (fillsN != null) {
            for (XNode fillN : fillsN.getChildren()) {
                XNode patternFill = fillN.childByTag("patternFill");
                if (patternFill == null) continue;
                String pattern = patternFill.attrText("patternType");
                if (pattern == null) continue;

                ExcelFill fill = new ExcelFill();
                fill.setPatternType(pattern);
                String fgColor = parseColor(patternFill.childByTag("fgColor"));
                String bgColor = parseColor(patternFill.childByTag("bgColor"));
                fill.setBgColor(bgColor);
                fill.setFgColor(fgColor);
                this.fills.add(fill);
            }
        }
    }

    private String parseColor(XNode node) {
        String color = _parseColor(node);
        if (color == null)
            return null;
        Double tint = node.attrDouble("tint", null);
        if (tint != null)
            color = ColorHelper.applyTint(color, tint);
        return color;
    }

    private String _parseColor(XNode node) {
        if (node == null) return null;
        int index = node.attrInt("indexed", -1);
        if (index >= 0) {
            PredefinedColors color = PredefinedColors.getByIndex(index);
            return color == null ? null : color.getArgb();
        }

        int theme = node.attrInt("theme", -1);
        if (theme >= 0 && this.themes != null) {
            return themes.getThemeColor(theme);
        }
        return node.attrText("rgb");
    }

    private void parseCellStyleXfs(XNode node) {
        XNode cellStyleXfs = node.childByTag("cellStyleXfs");
        if (cellStyleXfs != null) {
            for (XNode xf : cellStyleXfs.getChildren()) {
                ExcelStyle style = new ExcelStyle();
                initStyle(style, xf);

                this.cellStyleXfs.add(style);
            }
        }
    }

    private void parseCellXfs(XNode node) {
        XNode cellXfs = node.childByTag("cellXfs");
        if (cellXfs != null) {
            int index = 0;
            for (XNode xf : cellXfs.getChildren()) {
                int xfId = xf.attrInt("xfId", -1);
                ExcelStyle style = getCellStyleXf(xfId);
                if (style == null) {
                    style = new ExcelStyle();
                } else {
                    style = style.cloneInstance();
                }
                style.setId(String.valueOf(index));
                index++;

                initStyle(style, xf);
                this.cellXfs.add(style);
            }
        }
    }

    private void initStyle(ExcelStyle style, XNode xf) {
        style.setLocation(xf.getLocation());

        int numFmtId = xf.attrInt("numFmtId", -1);
        if (numFmtId >= 0) {
            String format = BuiltinFormats.getBuiltinFormat(numFmtId);
            if (format == null) {
                format = numberFormats.get(String.valueOf(numFmtId));
            }
            style.setNumberFormat(format);
        }

        int fontId = xf.attrInt("fontId", -1);
        ExcelFont font = getFont(fontId);
        if (font != null) style.setFont(font);

        String numberFmt = this.numberFormats.get(xf.attrText("numFmtId", ""));
        if (numberFmt != null) style.setNumberFormat(numberFmt);

        int fillId = xf.attrInt("fillId", -1);
        ExcelFill fill = getFill(fillId);
        if (fill != null) {
            style.setFillBgColor(fill.getBgColor());
            style.setFillFgColor(fill.getFgColor());
            style.setFillPattern(fill.getPatternType());
        }

        int borderId = xf.attrInt("borderId", -1);
        ExcelBorder border = getBorder(borderId);
        if (border != null) {
            if (border.getLeftBorder() != null) style.setLeftBorder(border.getLeftBorder());
            if (border.getRightBorder() != null) style.setRightBorder(border.getRightBorder());
            if (border.getTopBorder() != null) style.setTopBorder(border.getTopBorder());
            if (border.getBottomBorder() != null) style.setBottomBorder(border.getBottomBorder());
            if (border.getDiagonalLeftBorder() != null) style.setDiagonalLeftBorder(border.getDiagonalLeftBorder());
            if (border.getDiagonalRightBorder() != null) style.setDiagonalRightBorder(border.getDiagonalRightBorder());
        }

        XNode alignment = xf.childByTag("alignment");
        if (alignment != null) {
            String vertAlign = alignment.attrText("vertical");
            if (vertAlign != null) {
                ExcelVerticalAlignment verticalAlignment = ExcelVerticalAlignment.fromExcelText(vertAlign);
                if (verticalAlignment != null) style.setVerticalAlign(verticalAlignment);
            }

            String horAlign = alignment.attrText("horizontal");
            if (horAlign != null) {
                ExcelHorizontalAlignment horizontalAlignment = ExcelHorizontalAlignment.fromExcelText(horAlign);
                if (horizontalAlignment != null) style.setHorizontalAlign(horizontalAlignment);
            }

            boolean wrapText = alignment.attrBoolean("wrapText");
            style.setWrapText(wrapText);

            Integer indent = alignment.attrInt("indent");
            style.setIndent(indent);
        }
    }

    private ExcelFont getFont(int fontIndex) {
        return CollectionHelper.get(fonts, fontIndex);
    }

    private ExcelFill getFill(int fillId) {
        return CollectionHelper.get(fills, fillId);
    }

    private ExcelBorder getBorder(int borderId) {
        return CollectionHelper.get(borders, borderId);
    }

    private ExcelStyle getCellStyleXf(int xfId) {
        return CollectionHelper.get(cellStyleXfs, xfId);
    }
}