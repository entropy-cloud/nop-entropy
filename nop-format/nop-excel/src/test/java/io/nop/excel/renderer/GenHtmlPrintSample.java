/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.renderer;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelPageSetup;
import io.nop.excel.model.ExcelRow;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelTable;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 生成分页打印验收样例 HTML（供 html-acceptance/accept.mjs 截图 + DOM 断言）。
 * 运行：./mvnw test -pl nop-format/nop-excel -Dtest=GenHtmlPrintSample
 */
public class GenHtmlPrintSample {

    @Test
    public void genSample() throws Exception {
        CoreInitialization.initialize();
        try {
            ExcelSheet sheet = buildSheet(120);
            ExcelWorkbook wb = new ExcelWorkbook();
            wb.setSheets(new ArrayList<>(List.of(sheet)));

            HtmlReportRendererFactory factory = new HtmlReportRendererFactory();
            ITextTemplateOutput output = factory.buildRenderer(wb, (ctx, consumer) -> consumer.accept(sheet, ctx));

            File dir = new File("src/test/html-acceptance");
            dir.mkdirs();
            File file = new File(dir, "sample.html");
            try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                output.generateToWriter(w, XLang.newEvalScope());
            }
            System.out.println("GEN_HTML_PRINT_SAMPLE=" + file.getAbsolutePath());
        } finally {
            CoreInitialization.destroy();
        }
    }

    private ExcelSheet buildSheet(int bodyRows) {
        ExcelTable table = new ExcelTable();
        List<ExcelRow> rows = new ArrayList<>();
        ExcelRow header = new ExcelRow();
        header.setHeight(24d);
        List<ExcelCell> headerCells = new ArrayList<>();
        String[] titles = {"编号", "名称", "金额"};
        for (String title : titles) {
            ExcelCell c = new ExcelCell();
            c.setValue(title);
            headerCells.add(c);
        }
        header.setCells(headerCells);
        rows.add(header);

        for (int i = 0; i < bodyRows; i++) {
            ExcelRow row = new ExcelRow();
            row.setHeight(20d);
            List<ExcelCell> cells = new ArrayList<>();
            for (String v : new String[]{String.valueOf(i + 1), "项目" + i, String.valueOf(100 + i)}) {
                ExcelCell c = new ExcelCell();
                c.setValue(v);
                cells.add(c);
            }
            row.setCells(cells);
            rows.add(row);
        }
        table.setRows(rows);
        table.setHeaderCount(1);

        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("long-report");
        sheet.setTable(table);
        sheet.setPageSetup(new ExcelPageSetup());
        sheet.setDefaultRowHeight(20d);
        return sheet;
    }
}
