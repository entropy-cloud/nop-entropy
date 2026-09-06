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
