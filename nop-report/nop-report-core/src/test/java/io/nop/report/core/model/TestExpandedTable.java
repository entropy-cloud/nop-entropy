/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
