/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine.expand;

import io.nop.excel.model.XptCellModel;
import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.model.ExpandedTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCellExpanderRemoveCell {

    @Test
    public void testRemoveRowCellClampedToTableBounds() {
        ExpandedTable table = new ExpandedTable(2, 1);
        ExpandedCell cell = table.getCell(0, 0);

        XptCellModel model = new XptCellModel();
        model.setName("A");
        // 展开范围超出表格实际行数
        model.setRowExpandOffset(0);
        model.setRowExpandSpan(5);
        cell.setModel(model);

        CellRowExpander.INSTANCE.removeCell(cell);

        assertTrue(table.getRow(0).isRemoved());
        assertTrue(table.getRow(1).isRemoved());
    }

    /**
     * P1-3.6-001（plan 2384）：offset≠0 时移除区域必须随 offset 右移/下移——
     * 修复前起点使用减号（rowIndex-offset），会错误删除 offset 区间之外的行/列
     * （包括模板行自身），与 extendCells/duplicateCell 的 +offset 语义矛盾。
     */
    @Test
    public void testRemoveRowCellHonorsNonZeroOffset() {
        ExpandedTable table = new ExpandedTable(3, 1);
        ExpandedCell cell = table.getCell(0, 0);

        XptCellModel model = new XptCellModel();
        model.setName("A");
        model.setRowExpandOffset(1);
        model.setRowExpandSpan(1);
        cell.setModel(model);

        CellRowExpander.INSTANCE.removeCell(cell);

        // 修复后：移除区域为 [rowIndex+offset, rowIndex+offset+span) = [1, 2)
        assertTrue(table.getRow(1).isRemoved(), "row at rowIndex+offset must be removed");
        assertTrue(!table.getRow(0).isRemoved(), "template row (rowIndex) must NOT be removed");
        assertTrue(!table.getRow(2).isRemoved(), "row beyond expansion span must NOT be removed");
    }

    @Test
    public void testRemoveColCellHonorsNonZeroOffset() {
        ExpandedTable table = new ExpandedTable(1, 3);
        ExpandedCell cell = table.getCell(0, 0);

        XptCellModel model = new XptCellModel();
        model.setName("A");
        model.setColExpandOffset(1);
        model.setColExpandSpan(1);
        cell.setModel(model);

        CellColExpander.INSTANCE.removeCell(cell);

        assertTrue(table.getCol(1).isRemoved(), "col at colIndex+offset must be removed");
        assertTrue(!table.getCol(0).isRemoved(), "template col (colIndex) must NOT be removed");
        assertTrue(!table.getCol(2).isRemoved(), "col beyond expansion span must NOT be removed");
    }

    @Test
    public void testRemoveColCellClampedToTableBounds() {
        ExpandedTable table = new ExpandedTable(1, 2);
        ExpandedCell cell = table.getCell(0, 0);

        XptCellModel model = new XptCellModel();
        model.setName("A");
        model.setColExpandOffset(0);
        model.setColExpandSpan(5);
        cell.setModel(model);

        CellColExpander.INSTANCE.removeCell(cell);

        assertTrue(table.getCol(0).isRemoved());
        assertTrue(table.getCol(1).isRemoved());
    }
}
