/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.model;

import io.nop.excel.model.XptCellModel;
import io.nop.excel.model.constants.XptExpandType;
import io.nop.report.core.engine.IXptRuntime;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestExpandedCell {

    static IXptRuntime mockXptRuntime(ExpandedCell cell) {
        return (IXptRuntime) Proxy.newProxyInstance(IXptRuntime.class.getClassLoader(),
                new Class[]{IXptRuntime.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getCell":
                            return cell;
                        case "evaluateCell":
                            return null;
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        default:
                            return null;
                    }
                });
    }

    static ExpandedCell newCell(String name, XptExpandType expandType) {
        XptCellModel model = new XptCellModel();
        model.setName(name);
        model.setExpandType(expandType);
        ExpandedCell cell = new ExpandedCell();
        cell.setModel(model);
        return cell;
    }

    @Test
    public void testChildCellFromColDescendants() {
        ExpandedCell cell = newCell("A", null);
        ExpandedCell child = newCell("B", null);

        cell.addColChild(child);

        ExpandedCell result = cell.childCell("B", mockXptRuntime(child));
        // 列方向的子格也应该能通过childCell取到
        assertSame(child, result);
    }

    @Test
    public void testChildCellFromRowDescendants() {
        ExpandedCell cell = newCell("A", null);
        ExpandedCell child = newCell("B", null);

        cell.addRowChild(child);

        ExpandedCell result = cell.childCell("B", mockXptRuntime(child));
        assertSame(child, result);
    }

    @Test
    public void testGetExpandableRowParentSkipsNonExpandable() {
        // A可展开 <- B不可展开 <- C，从C向上查找应跳过B命中A
        ExpandedCell a = newCell("A", XptExpandType.r);
        ExpandedCell b = newCell("B", null);
        ExpandedCell c = newCell("C", null);

        b.setRowParent(a);
        c.setRowParent(b);

        assertSame(a, c.getExpandableRowParent());
        assertSame(a, b.getExpandableRowParent());
    }

    @Test
    public void testGetExpandableColParentSkipsNonExpandable() {
        ExpandedCell a = newCell("A", XptExpandType.c);
        ExpandedCell b = newCell("B", null);
        ExpandedCell c = newCell("C", null);

        b.setColParent(a);
        c.setColParent(b);

        assertSame(a, c.getExpandableColParent());
    }

    @Test
    public void testGetExpandableRowParentNoParent() {
        ExpandedCell c = newCell("C", null);
        assertNull(c.getExpandableRowParent());

        // 父格没有model时返回null
        ExpandedCell p = new ExpandedCell();
        c.setRowParent(p);
        assertNull(c.getExpandableRowParent());
    }
}
