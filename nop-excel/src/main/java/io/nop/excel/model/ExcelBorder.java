/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.excel.model;

public class ExcelBorder {
    /**
     * xml name: bottomBorder
     */
    private ExcelBorderStyle bottomBorder;

    /**
     * xml name: diagonalLeftBorder
     */
    private ExcelBorderStyle diagonalLeftBorder;

    /**
     * xml name: diagonalRightBorder
     */
    private ExcelBorderStyle diagonalRightBorder;

    private ExcelBorderStyle topBorder;
    private ExcelBorderStyle leftBorder;
    private ExcelBorderStyle rightBorder;

    public ExcelBorderStyle getBottomBorder() {
        return bottomBorder;
    }

    public void setBottomBorder(ExcelBorderStyle bottomBorder) {
        this.bottomBorder = bottomBorder;
    }

    public ExcelBorderStyle getDiagonalLeftBorder() {
        return diagonalLeftBorder;
    }

    public void setDiagonalLeftBorder(ExcelBorderStyle diagonalLeftBorder) {
        this.diagonalLeftBorder = diagonalLeftBorder;
    }

    public ExcelBorderStyle getDiagonalRightBorder() {
        return diagonalRightBorder;
    }

    public void setDiagonalRightBorder(ExcelBorderStyle diagonalRightBorder) {
        this.diagonalRightBorder = diagonalRightBorder;
    }

    public ExcelBorderStyle getTopBorder() {
        return topBorder;
    }

    public void setTopBorder(ExcelBorderStyle topBorder) {
        this.topBorder = topBorder;
    }

    public ExcelBorderStyle getLeftBorder() {
        return leftBorder;
    }

    public void setLeftBorder(ExcelBorderStyle leftBorder) {
        this.leftBorder = leftBorder;
    }

    public ExcelBorderStyle getRightBorder() {
        return rightBorder;
    }

    public void setRightBorder(ExcelBorderStyle rightBorder) {
        this.rightBorder = rightBorder;
    }
}
