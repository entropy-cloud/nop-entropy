/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.build;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.model.table.CellPosition;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelRow;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelTable;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.nop.report.core.XptErrors.ARG_CELL_POS;
import static io.nop.report.core.XptErrors.ARG_COL_PARENT;
import static io.nop.report.core.XptErrors.ARG_ROW_PARENT;
import static io.nop.report.core.XptErrors.ERR_XPT_COL_PARENT_CONTAINS_LOOP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestXptModelInitializer {

    @Test
    public void testColParentLoopErrorUsesColParentParam() {
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("S1");
        ExcelTable table = new ExcelTable();
        sheet.setTable(table);

        ExcelRow row = new ExcelRow();
        ExcelCell a1 = new ExcelCell();
        ExcelCell b1 = new ExcelCell();
        row.setCells(new ArrayList<>(List.of(a1, b1)));
        table.setRows(new ArrayList<>(List.of(row)));

        // A1与B1互为列父格，构成列方向循环
        a1.makeModel().setColParent(CellPosition.of(0, 1));
        b1.makeModel().setColParent(CellPosition.of(0, 0));

        XptModelInitializer initializer = new XptModelInitializer(XLang.newCompileTool().allowUnregisteredScopeVar(true));

        NopException ex = assertThrows(NopException.class, () -> initializer.buildSheetModel(sheet));
        assertEquals(ERR_XPT_COL_PARENT_CONTAINS_LOOP.getErrorCode(), ex.getErrorCode());

        // 异常参数必须是肇事的列父格配置，而不是行父格
        Object colParent = ex.getParam(ARG_COL_PARENT);
        assertNotNull(colParent);
        String colParentStr = colParent.toString();
        assertTrue(colParentStr.equals("B1") || colParentStr.equals("A1"),
                "unexpected colParent: " + colParent);
        assertNull(ex.getParam(ARG_ROW_PARENT));
        assertNotNull(ex.getParam(ARG_CELL_POS));
    }
}
