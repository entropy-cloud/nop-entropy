/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.excel.model;

import java.util.Objects;

/*
<fill>
<patternFill patternType="darkTrellis"><fgColor theme="4" tint="0.59996337778862885"/>
<bgColor theme="7" tint="0.79998168889431442"/></patternFill>
</fill>

patternType:
'darkDown', 'darkUp', 'lightDown', 'darkGrid', 'lightVertical',
               'solid', 'gray0625', 'darkHorizontal', 'lightGrid', 'lightTrellis',
               'mediumGray', 'gray125', 'darkGray', 'lightGray', 'lightUp',
               'lightHorizontal', 'darkTrellis', 'darkVertical'

 */
public class ExcelFill {
    private String patternType = "none";
    // 如果patternType=solid，则完全以fgColor为准
    private String fgColor;
    private String bgColor;

    public ExcelFill() {
    }

    public ExcelFill(String pattern) {
        this.patternType = pattern;
    }

    public int hashCode() {
        return patternType.hashCode() + (fgColor == null ? 0 : fgColor.hashCode()) + (bgColor == null ? 0 : bgColor.hashCode());
    }

    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof ExcelFill))
            return false;

        ExcelFill other = (ExcelFill) o;
        return Objects.equals(patternType, other.patternType)
                && Objects.equals(fgColor, other.fgColor)
                && Objects.equals(bgColor, other.bgColor);
    }

    public String getPatternType() {
        return patternType;
    }

    public void setPatternType(String patternType) {
        this.patternType = patternType;
    }

    public String getFgColor() {
        return fgColor;
    }

    public void setFgColor(String fgColor) {
        this.fgColor = fgColor;
    }

    public String getBgColor() {
        return bgColor;
    }

    public void setBgColor(String bgColor) {
        this.bgColor = bgColor;
    }
}
