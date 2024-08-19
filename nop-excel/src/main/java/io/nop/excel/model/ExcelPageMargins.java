/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.model;

import io.nop.excel.model._gen._ExcelPageMargins;
import io.nop.excel.util.UnitsHelper;

public class ExcelPageMargins extends _ExcelPageMargins {
    public ExcelPageMargins() {

    }

    public Double getLeftInches() {
        Double value = getLeft();
        if (value == null)
            return null;
        return UnitsHelper.pointsToInches(value);
    }

    public void setLeftInches(Double inches) {
        if (inches == null) {
            setLeft(null);
        } else {
            setLeft(UnitsHelper.inchesToPoints(inches));
        }
    }

    public Double getRightInches() {
        Double value = getRight();
        if (value == null)
            return null;
        return UnitsHelper.pointsToInches(value);
    }

    public void setRightInches(Double inches) {
        if (inches == null) {
            setRight(null);
        } else {
            setRight(UnitsHelper.inchesToPoints(inches));
        }
    }

    public Double getTopInches() {
        Double value = getTop();
        if (value == null)
            return null;
        return UnitsHelper.pointsToInches(value);
    }

    public void setTopInches(Double inches) {
        if (inches == null) {
            setTop(null);
        } else {
            setTop(UnitsHelper.inchesToPoints(inches));
        }
    }

    public Double getBottomInches() {
        Double value = getBottom();
        if (value == null)
            return null;
        return UnitsHelper.pointsToInches(value);
    }

    public void setBottomInches(Double inches) {
        if (inches == null) {
            setBottom(null);
        } else {
            setBottom(UnitsHelper.inchesToPoints(inches));
        }
    }

    public Double getHeaderInches() {
        Double value = getHeader();
        if (value == null)
            return null;
        return UnitsHelper.pointsToInches(value);
    }

    public void setHeaderInches(Double inches) {
        if (inches == null) {
            setHeader(null);
        } else {
            setHeader(UnitsHelper.inchesToPoints(inches));
        }
    }

    public Double getFooterInches() {
        Double value = getFooter();
        if (value == null)
            return null;
        return UnitsHelper.pointsToInches(value);
    }

    public void setFooterInches(Double inches) {
        if (inches == null) {
            setFooter(null);
        } else {
            setFooter(UnitsHelper.inchesToPoints(inches));
        }
    }
}
