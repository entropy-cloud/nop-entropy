/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.excel.model;

import io.nop.excel.model._gen._ExcelBorderStyle;

import java.util.Objects;

public class ExcelBorderStyle extends _ExcelBorderStyle {
    public ExcelBorderStyle() {

    }

    public static boolean isSameStyle(ExcelBorderStyle styleA, ExcelBorderStyle styleB) {
        if (styleA == styleB)
            return true;

        if (styleA == null || styleB == null)
            return false;

        if (styleA.getWeight() != styleB.getWeight())
            return false;

        if (styleA.getType() != styleB.getType())
            return false;

        if (!Objects.equals(styleA.getColor(), styleB.getColor()))
            return false;

        return true;
    }
}
