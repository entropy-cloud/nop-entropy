/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine;

import io.nop.excel.model.XptCellModel;
import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.model.ExpandedTable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestExpandedSheetEvaluator {

    /**
     * rowTestExpr触发行删除时，合并范围可能越过表格边界，removeRow应钳制到实际行数避免NPE
     */
    @Test
    public void testRemoveRowClampedToTableBounds() throws Exception {
        ExpandedTable table = new ExpandedTable(2, 1);
        ExpandedCell cell = table.getCell(0, 0);

        XptCellModel model = new XptCellModel();
        model.setName("A1");
        cell.setModel(model);
        // mergeDown越过表格下边界
        cell.setMergeDown(5);

        Method removeRow = ExpandedSheetEvaluator.class.getDeclaredMethod("removeRow", ExpandedCell.class);
        removeRow.setAccessible(true);
        removeRow.invoke(ExpandedSheetEvaluator.INSTANCE, cell);

        assertTrue(table.getRow(0).isRemoved());
        assertTrue(table.getRow(1).isRemoved());
    }

    @Test
    public void testRemoveColClampedToTableBounds() throws Exception {
        ExpandedTable table = new ExpandedTable(1, 2);
        ExpandedCell cell = table.getCell(0, 0);

        XptCellModel model = new XptCellModel();
        model.setName("A1");
        cell.setModel(model);
        cell.setMergeAcross(5);

        Method removeCol = ExpandedSheetEvaluator.class.getDeclaredMethod("removeCol", ExpandedCell.class);
        removeCol.setAccessible(true);
        removeCol.invoke(ExpandedSheetEvaluator.INSTANCE, cell);

        assertTrue(table.getCol(0).isRemoved());
        assertTrue(table.getCol(1).isRemoved());
    }
}
