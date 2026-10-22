/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.table.html;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.model.table.impl.BaseTable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestHtmlTableOutputRowRange {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private BaseTable buildTable() {
        BaseTable table = new BaseTable();
        for (int i = 0; i < 6; i++) {
            io.nop.core.model.table.impl.BaseRow row = new io.nop.core.model.table.impl.BaseRow();
            io.nop.core.model.table.impl.BaseCell cell = new io.nop.core.model.table.impl.BaseCell();
            cell.setValue("v" + i);
            row.addCell(cell);
            table.getRows().add(row);
        }
        table.setHeaderCount(1);
        return table;
    }

    @Test
    public void testFullTableByDefault() throws Exception {
        StringWriter out = new StringWriter();
        new HtmlTableOutput(buildTable(), "cls-").generateToWriter(out, null);
        String html = out.toString();
        assertTrue(html.contains("v0"));
        assertTrue(html.contains("v5"));
        assertTrue(html.contains("<tfoot>"));
    }

    @Test
    public void testRowRangeSlicesBody() throws Exception {
        BaseTable table = buildTable();
        StringWriter out = new StringWriter();
        HtmlTableOutput output = new HtmlTableOutput(table, "cls-");
        output.setRowRange(2, 4); // body rows v2,v3
        output.setWriteTfoot(false);
        output.generateToWriter(out, null);
        String html = out.toString();
        assertTrue(html.contains("v0")); // header row
        assertTrue(html.contains("v2"));
        assertTrue(html.contains("v3"));
        assertFalse(html.contains("v1")); // body row 1 excluded by range
        assertFalse(html.contains("v4"));
        assertFalse(html.contains("v5"));
        assertFalse(html.contains("<tfoot>"));
        assertTrue(html.contains("<thead>"));
    }
}
