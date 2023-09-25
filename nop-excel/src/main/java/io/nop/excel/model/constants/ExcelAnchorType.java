/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.excel.model.constants;

public enum ExcelAnchorType {
    /**
     * 表示图像或形状的编辑行为将基于两个单元格的范围进行调整和移动。这意味着当调整单元格大小或移动单元格时，图像或形状将相应地调整和移动。
     * Move and Resize With Anchor Cells (0)
     * <p>
     * Specifies that the current drawing shall move and resize to maintain its row and column anchors (i.e. the object
     * is anchored to the actual from and to row and column)
     * </p>
     */
    twoCell(0),

    /**
     * 表示图像或形状的编辑行为将基于一个单元格的范围进行调整和移动。这意味着当调整单元格大小时，图像或形状将自动调整大小，但不会随着单元格的移动而移动
     *
     * Don't Move but do Resize With Anchor Cells (1)
     * <p>
     * Specifies that the current drawing shall not move with its row and column, but should be resized. This option is
     * not normally used, but is included for completeness.
     * </p>
     */
    oneCell(1),

    /**
     * Move With Cells but Do Not Resize (2)
     * <p>
     * Specifies that the current drawing shall move with its row and column (i.e. the object is anchored to the actual
     * from row and column), but that the size shall remain absolute.
     * </p>
     * <p>
     * If additional rows/columns are added between the from and to locations of the drawing, the drawing shall move its
     * to anchors as needed to maintain this same absolute size.
     * </p>
     */
    absolute(2),

    /**
     * 表示图像或形状的编辑行为将保持绝对位置和大小不变。无论单元格如何调整或移动，图像或形状都将保持在原始的位置和大小
     *
     * Do Not Move or Resize With Underlying Rows/Columns (3)
     * <p>
     * Specifies that the current start and end positions shall be maintained with respect to the distances from the
     * absolute start point of the worksheet.
     * </p>
     * <p>
     * If additional rows/columns are added before the drawing, the drawing shall move its anchors as needed to maintain
     * this same absolute position.
     * </p>
     */
    anchor(3);

    public final short value;

    // disallow non-sequential enum instance creation
    ExcelAnchorType(int value) {
        this.value = (short) value;
    }

    public short getCode() {
        return value;
    }

    /**
     * return the AnchorType corresponding to the code
     *
     * @param value the anchor type code
     * @return the anchor type enum
     */
    public static ExcelAnchorType fromCode(int value) {
        return values()[value];
    }
}
