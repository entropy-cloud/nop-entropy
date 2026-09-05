/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.report.core.model;

import io.nop.core.initialize.CoreInitialization;
import io.nop.excel.model.XptCellModel;
import io.nop.excel.model.constants.XptExpandType;
import io.nop.report.core.engine.XptRuntime;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ExpandedCell 父子链相关缺陷回归测试。
 */
public class TestExpandedCellLinks {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private ExpandedCell cell(ExpandedTable table, int row, int col, String name, XptExpandType expandType) {
        ExpandedCell cell = table.getCell(row, col);
        XptCellModel model = new XptCellModel();
        model.setName(name);
        model.setExpandType(expandType);
        cell.setModel(model);
        return cell;
    }

    @Test
    public void testChildCellReturnsColDescendant() {
        ExpandedTable table = new ExpandedTable(2, 1);
        ExpandedCell parent = cell(table, 0, 0, "P", XptExpandType.c);
        ExpandedCell child = cell(table, 1, 0, "C", null);
        parent.addColChild(child);
        child.setValue("v");

        XptRuntime xptRt = new XptRuntime(XLang.newEvalScope());
        // 列子格也应被返回，而不是求值后丢弃
        assertSame(child, parent.childCell("C", xptRt));
        assertEquals("v", parent.childValue("C", xptRt));
    }

    @Test
    public void testExpandableRowParentSkipsNonExpandableMiddleCell() {
        ExpandedTable table = new ExpandedTable(3, 1);
        ExpandedCell a = cell(table, 0, 0, "A", XptExpandType.r);
        ExpandedCell b = cell(table, 1, 0, "B", null);
        ExpandedCell c = cell(table, 2, 0, "C", null);
        b.setRowParent(a);
        c.setRowParent(b);

        // B不可展开，应继续沿行父链找到A，而不是跳到列父链返回null
        assertSame(a, c.getExpandableRowParent());
        assertNotNull(c.getExpandableRowParent());
    }

    @Test
    public void testRowChildSetOnCellWithoutDescendants() {
        ExpandedCell lone = cell(new ExpandedTable(1, 1), 0, 0, "L", null);
        ExpandedCellSet set = new ExpandedCellSet(null, "L", Collections.singletonList(lone));

        // 单格无后代时不应抛NPE，应返回空集合
        ExpandedCellSet children = set.rowChildSet("X");
        assertNotNull(children);
        assertTrue(children.isEmpty());

        ExpandedCellSet colChildren = set.colChildSet("X");
        assertNotNull(colChildren);
        assertTrue(colChildren.isEmpty());
    }
}
