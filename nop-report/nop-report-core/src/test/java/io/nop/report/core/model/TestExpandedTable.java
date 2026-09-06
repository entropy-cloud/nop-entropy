/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestExpandedTable {
    @Test
    public void testInsert() {
        ExpandedTable table = new ExpandedTable(3, 3);
        table.insertEmptyRow(1);
        ExpandedCell cell = table.getCell(2, 1);
        assertEquals(cell.getRight(), table.getCell(2, 2));
        assertEquals(cell.getDown(), table.getCell(3, 1));
        assertEquals(2, cell.getRight().getCol().getColIndex());
        assertNull(cell.getRight().getRight());
        assertNull(cell.getDown().getDown());
    }

    @Test
    public void testForEachCellOnEmptyRow() {
        // 0列表格中行内没有任何单元格，遍历应该是空操作而不是NPE
        ExpandedTable table = new ExpandedTable(2, 0);
        List<ExpandedCell> visited = new ArrayList<>();
        table.getRow(0).forEachCell(visited::add);
        assertTrue(visited.isEmpty());

        table.getRow(0).forEachRealCell(visited::add);
        assertTrue(visited.isEmpty());
    }

    @Test
    public void testForEachCellOnEmptyCol() {
        ExpandedTable table = new ExpandedTable(0, 2);
        List<ExpandedCell> visited = new ArrayList<>();
        table.getCol(0).forEachCell(visited::add);
        assertTrue(visited.isEmpty());
    }

    @Test
    public void testPropAccessOnRowWithoutModel() {
        // insertEmptyRow创建的行没有model，扩展属性访问不应NPE
        ExpandedTable table = new ExpandedTable(2, 1);
        ExpandedRow row = table.insertEmptyRow(1);
        assertNull(row.getModel());
        assertNull(row.prop_get("x"));
        assertFalse(row.prop_has("x"));
        row.prop_set("x", 1);
    }
}
