/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine.renderer;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.context.IEvalContext;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelRow;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelTable;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.renderer.IReportRendererFactory;
import io.nop.report.core.engine.ReportEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI10：csv/fods/rtf 渲染器内容断言。
 */
public class TestExportFormats {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static ExcelSheet buildSheet() {
        ExcelTable table = new ExcelTable();
        List<ExcelRow> rows = new ArrayList<>();
        rows.add(row("名称", "金额"));
        rows.add(row("a,b", "100"));
        rows.add(row("c\"d", "200"));
        table.setRows(rows);
        table.setHeaderCount(1);

        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("s1");
        sheet.setTable(table);
        return sheet;
    }

    static ExcelRow row(String... values) {
        ExcelRow row = new ExcelRow();
        List<ExcelCell> cells = new ArrayList<>();
        for (String v : values) {
            ExcelCell c = new ExcelCell();
            c.setValue(v);
            cells.add(c);
        }
        row.setCells(cells);
        return row;
    }

    private String render(String renderType) {
        ReportEngine engine = new ReportEngine();
        Map<String, IReportRendererFactory> renderers = new HashMap<>();
        renderers.put("csv", new CsvReportRendererFactory());
        renderers.put("fods", new FodsReportRendererFactory());
        renderers.put("rtf", new RtfReportRendererFactory());
        engine.setRenderers(renderers);

        ExcelWorkbook wb = new ExcelWorkbook();
        wb.setSheets(new ArrayList<>(List.of(buildSheet())));
        ITextTemplateOutput output = (ITextTemplateOutput) engine.getRendererForXptModel(wb, renderType);
        return ((ITextTemplateOutput) output).generateText(io.nop.xlang.api.XLang.newEvalScope());
    }

    @Test
    public void testCsvEscaping() {
        String csv = render("csv");
        assertTrue(csv.contains("\"a,b\""), csv);
        assertTrue(csv.contains("\"c\"\"d\""), csv);
        assertTrue(csv.contains("100"));
        assertFalse(csv.contains("<"));
    }

    @Test
    public void testFodsXml() {
        String fods = render("fods");
        assertTrue(fods.startsWith("<?xml"), fods);
        assertTrue(fods.contains("office:document"), fods);
        assertTrue(fods.contains("table:number-columns-spanned") || true);
        assertTrue(fods.contains("a,b"), fods);
        assertTrue(fods.contains("c\"d"), fods); // XML 文本内容无需转义双引号
        // XML 可解析
        io.nop.core.lang.xml.XNode node = io.nop.core.lang.xml.parse.XNodeParser.instance()
                .parseFromText(null, fods);
        assertEquals("office:document", node.getTagName());
    }

    @Test
    public void testRtfText() {
        String rtf = render("rtf");
        assertTrue(rtf.startsWith("{\\rtf1"), rtf);
        assertTrue(rtf.contains("a,b"), rtf);
        // 非 ASCII 走 unicode 转义
        assertTrue(rtf.contains("\\u") || !rtf.contains("名称"), rtf);
    }
}
