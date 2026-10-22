/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.model;

import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI5：折叠组标记从展开层级（rowParent）派生。
 */
public class TestExpandedRowExpandGroup {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testExpandGroupDerivedFromRowParent() {
        ExpandedTable table = new ExpandedTable(3, 1);
        ExpandedCell parent = table.getCell(0, 0);
        ExpandedCell child1 = table.getCell(1, 0);
        ExpandedCell child2 = table.getCell(2, 0);
        child1.setRowParent(parent);
        child2.setRowParent(parent);

        assertEquals("row-0", table.getRow(1).getExpandGroup());
        assertEquals("row-0", table.getRow(2).getExpandGroup());
        assertNull(table.getRow(0).getExpandGroup());
        assertTrue(table.getRow(0).isExpandGroupHeader());
        assertFalse(table.getRow(1).isExpandGroupHeader());
    }

    @Test
    public void testNoParentNoGroup() {
        ExpandedTable table = new ExpandedTable(2, 1);
        assertNull(table.getRow(0).getExpandGroup());
        assertFalse(table.getRow(0).isExpandGroupHeader());
    }
}
