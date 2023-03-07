/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.excel.model;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestExcelWorkbook {
    @BeforeAll
    public static void init(){
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy(){
        CoreInitialization.destroy();
    }

    @Test
    public void testMerge() {
        ExcelWorkbook wk = new ExcelWorkbook();
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("test");
        wk.addSheet(sheet);

        ExcelCell cell = new ExcelCell();
        cell.setMergeAcross(2);
        cell.setMergeDown(2);
        cell.setValue("test");
        sheet.getTable().setCell(0, 0, cell);

        XNode node = DslModelHelper.dslModelToXNode("/nop/schema/excel/workbook.xdef",wk);
        node.dump();

        wk = (ExcelWorkbook) new DslModelParser().parseFromNode(node);
        ExcelTable table = wk.getSheet("test").getTable();
        String html = table.toHtmlString();
        assertEquals("<table  class=\"xui-table\">\n" +
                "<colgroup></colgroup>\n" +
                "<thead>\n" +
                "</thead>\n" +
                "<tbody>\n" +
                "<tr  class=\"xui-row\"><td  class=\"xui-cell\" colspan=\"3\" rowspan=\"3\">test</td></tr>\n" +
                "<tr  class=\"xui-row\"></tr>\n" +
                "<tr  class=\"xui-row\"></tr>\n" +
                "</tbody>\n" +
                "<tfoot></tfoot>\n" +
                "</table>",html.trim());
    }
}
