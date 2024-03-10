/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.excel.format.ExcelDateHelper;
import io.nop.excel.model._gen._ExcelStyle;
import io.nop.excel.model.color.ColorHelper;
import io.nop.excel.model.constants.ExcelHorizontalAlignment;
import io.nop.excel.model.constants.ExcelLineStyle;
import io.nop.excel.model.constants.IExcelEnumValue;
import io.nop.excel.util.UnitsHelper;

import static io.nop.excel.model.ExcelBorderStyle.isSameStyle;

public class ExcelStyle extends _ExcelStyle {
    private boolean dateFormat;

    public ExcelStyle() {

    }

    @JsonIgnore
    public boolean isDateFormat() {
        return dateFormat;
    }

    public void setNumberFormat(String format) {
        super.setNumberFormat(format);
        this.dateFormat = ExcelDateHelper.isADateFormat(format);
    }

    public String toString() {
        return getClass().getSimpleName() + "[id=" + getId() + ",loc=" + getLocation() + "]";
    }

    public boolean hasBorder() {
        if (getTopBorder() != null)
            return true;
        if (getBottomBorder() != null)
            return true;
        if (getLeftBorder() != null)
            return true;
        if (getRightBorder() != null)
            return true;
        if (getDiagonalLeftBorder() != null)
            return true;
        if (getDiagonalRightBorder() != null)
            return true;
        return false;
    }

    @JsonIgnore
    public ExcelBorder getBorder() {
        if (!hasBorder())
            return null;

        ExcelBorder ret = new ExcelBorder();
        ret.setLeftBorder(getLeftBorder());
        ret.setRightBorder(getRightBorder());
        ret.setTopBorder(getTopBorder());
        ret.setBottomBorder(getBottomBorder());
        ret.setDiagonalLeftBorder(getDiagonalLeftBorder());
        ret.setDiagonalRightBorder(getDiagonalRightBorder());
        return ret;
    }

    public ExcelStyle cloneInstance() {
        ExcelStyle style = new ExcelStyle();
        style.setLocation(getLocation());
        style.setVerticalAlign(getVerticalAlign());
        style.setFont(getFont());
        style.setShrinkToFit(isShrinkToFit());
        style.setRightBorder(getRightBorder());
        style.setLeftBorder(getLeftBorder());
        style.setTopBorder(getTopBorder());
        style.setBottomBorder(getBottomBorder());
        style.setDiagonalRightBorder(getDiagonalRightBorder());
        style.setDiagonalLeftBorder(getDiagonalLeftBorder());
        style.setFillFgColor(getFillFgColor());
        style.setFillBgColor(getFillBgColor());
        style.setFillPattern(getFillPattern());
        style.setNumberFormat(getNumberFormat());
        style.setHorizontalAlign(getHorizontalAlign());
        style.setName(getName());
        style.setRotate(getRotate());
        style.setWrapText(isWrapText());
        return style;
    }

    public boolean isAllBorderSameStyle() {
        return isSameStyle(getLeftBorder(), getRightBorder())
                && isSameStyle(getLeftBorder(), getTopBorder())
                && isSameStyle(getLeftBorder(), getBottomBorder());
    }

    public String toCssStyle(String cssPrefix) {
        StringBuilder sb = new StringBuilder();
        sb.append(".").append(cssPrefix).append(getId());
        sb.append("{\n");

        cssProp(sb, "vertical-align", this.getVerticalAlign());
        cssProp(sb, "text-align", this.getHorizontalAlign());

        ExcelHorizontalAlignment horAlign = getHorizontalAlign();

        if (getIndent() != null) {
            if (horAlign != null) {
                if (horAlign == ExcelHorizontalAlignment.LEFT) {
                    sb.append("padding-left:").append((getIndent() * UnitsHelper.DEFAULT_CHARACTER_WIDTH_IN_PT + 2) + "pt;\n");
                } else if (horAlign == ExcelHorizontalAlignment.RIGHT) {
                    sb.append("padding-right:").append((getIndent() * UnitsHelper.DEFAULT_CHARACTER_WIDTH_IN_PT + +2) + "pt;\n");
                }
            }
        }

        if (this.getFont() != null) {
            this.getFont().toCssStyle(sb);
        }

        if (isWrapText()) {
            sb.append("white-space:normal;\n");
        }

        if (getFillFgColor() != null) {
            sb.append("background-color:").append(ColorHelper.toCssColor(this.getFillFgColor())).append(";\r\n");
        }

        if (this.isAllBorderSameStyle()) {
            if (getLeftBorder() != null) {
                borderCss(sb, getLeftBorder(), "border");
            }
        } else {
            if (getTopBorder() != null) {
                borderCss(sb, getTopBorder(), "border-top");
            }
            if (getBottomBorder() != null) {
                borderCss(sb, getBottomBorder(), "border-bottom");
            }
            if (getLeftBorder() != null) {
                borderCss(sb, getLeftBorder(), "border-left");
            }
            if (getRightBorder() != null) {
                borderCss(sb, getRightBorder(), "border-right");
            }
        }
        sb.append("}\n");

        return sb.toString();
    }

    private void cssProp(StringBuilder sb, String name, IExcelEnumValue value) {
        if (name == null || value == null)
            return;
        sb.append(name).append(":").append(value.getCssText()).append(";\n");
    }

    private void borderCss(StringBuilder sb, ExcelBorderStyle style, String name) {
        if (style.getType() == ExcelLineStyle.NONE) {
            sb.append(name).append(":none;");
            return;
        }
        sb.append(name).append(':').append(style.getWeight()).append("px ")
                .append(m(style.getType(), "solid")).append(" ")
                .append(nvl(ColorHelper.toCssColor(style.getColor()), "black")).append(";\n");
    }

    private String m(IExcelEnumValue enumValue, String defaultValue) {
        if (enumValue == null)
            return defaultValue;
        return enumValue.getCssText();
    }

    private String nvl(String s, String defaultValue) {
        return s != null ? s : defaultValue;
    }

}
