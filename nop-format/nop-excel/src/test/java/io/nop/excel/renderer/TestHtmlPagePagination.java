/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.renderer;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.context.IEvalContext;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelPageSetup;
import io.nop.excel.model.ExcelRow;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelTable;
import io.nop.excel.model.ExcelWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestHtmlPagePagination {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private ExcelCell cell(String text) {
        ExcelCell cell = new ExcelCell();
        cell.setValue(text);
        return cell;
    }

    private ExcelRow row(double height, String... texts) {
        ExcelRow row = new ExcelRow();
        row.setHeight(height);
        List<ExcelCell> cells = new ArrayList<>();
        for (String text : texts)
            cells.add(cell(text));
        row.setCells(cells);
        return row;
    }

    private ExcelSheet buildSheet(int bodyRows) {
        ExcelTable table = new ExcelTable();
        List<ExcelRow> rows = new ArrayList<>();
        rows.add(row(20d, "表头")); // header
        for (int i = 0; i < bodyRows; i++)
            rows.add(row(20d, "row" + i));
        table.setRows(rows);
        table.setHeaderCount(1);

        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("s1");
        sheet.setTable(table);
        ExcelPageSetup setup = new ExcelPageSetup();
        sheet.setPageSetup(setup);
        sheet.setDefaultRowHeight(20d);
        return sheet;
    }

    /** 性能基线：-Dnop.test.large-dataset=true 时启用（20k 行 html 分页渲染，预算 60s） */
    @org.junit.jupiter.api.Test
    public void testLargeHtmlPaginationBaseline() {
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("nop.test.large-dataset"));
        ExcelSheet sheet = buildSheet(20_000);
        ExcelWorkbook wb = new ExcelWorkbook();
        wb.setSheets(new ArrayList<>(List.of(sheet)));
        HtmlReportRendererFactory factory = new HtmlReportRendererFactory();
        ITextTemplateOutput output = factory.buildRenderer(wb, (ctx, consumer) -> consumer.accept(sheet, ctx));
        long begin = System.currentTimeMillis();
        String html = output.generateText(newContext());
        long elapsed = System.currentTimeMillis() - begin;
        assertTrue(html.contains("第 "), html);
        assertTrue(elapsed < 60_000, "baseline budget: " + elapsed + "ms");
        System.out.println("BASELINE html paginated render (20k rows): " + elapsed + "ms");
    }

    @Test
    public void testPackPages() {
        ExcelSheet sheet = buildSheet(60);
        List<int[]> pages = HtmlPagePagination.packPages(sheet);
        // A4 高 842pt，内容区 = 842 - 72*2(上下默认margin) - 36*2(header/footer) = 626pt；每页表头 20pt，可用 606pt → 30 行/页
        assertEquals(2, pages.size());
        assertEquals(1, pages.get(0)[0]);
        assertEquals(31, pages.get(0)[1]);
        assertEquals(31, pages.get(1)[0]);
        assertEquals(61, pages.get(1)[1]);
    }

    @Test
    public void testPackPagesMergeBreakAvoided() {
        ExcelSheet sheet = buildSheet(60);
        // 第 25 行（table index 25）mergeDown=10，跨界块整体移入下一页
        sheet.getTable().getRows().get(25).getCells().get(0).setMergeDown(10);

        List<int[]> pages = HtmlPagePagination.packPages(sheet);
        // 断点不得落在合并块内部：page0 的末行 end 必须满足块 [25,35) 不跨界
        for (int[] page : pages) {
            assertTrue(page[1] <= 25 || page[0] >= 35 || (page[0] <= 25 && page[1] >= 35),
                    "merge block must not be split: " + page[0] + "-" + page[1]);
        }
    }

    @Test
    public void testPageCssAndFooter() {
        ExcelSheet sheet = buildSheet(5);
        String css = HtmlPagePagination.buildPageCss(sheet);
        assertTrue(css.contains("@page{size:595pt 842pt"), css);
        assertTrue(css.contains("margin:"), css);

        assertEquals("第 2 / 3 页", HtmlPagePagination.renderFooter(null, 2, 3));
        assertEquals("P2/3", HtmlPagePagination.renderFooter("P&P/&N", 2, 3));
        // 页脚模板取 pageSetup footer center
        io.nop.excel.model.ExcelHeaderFooter footer = new io.nop.excel.model.ExcelHeaderFooter();
        footer.setCenter("page &P of &N");
        sheet.getPageSetup().setFooter(footer);
        assertEquals("page &P of &N", HtmlPagePagination.footerTemplate(sheet));
    }

    @Test
    public void testRenderPaginatedHtml() {
        ExcelSheet sheet = buildSheet(60);
        ExcelWorkbook wb = new ExcelWorkbook();
        wb.setSheets(new ArrayList<>(List.of(sheet)));

        HtmlReportRendererFactory factory = new HtmlReportRendererFactory();
        ITextTemplateOutput output = factory.buildRenderer(wb, (ctx, consumer) -> consumer.accept(sheet, ctx));
        String html = output.generateText(newContext());

        assertTrue(html.contains("@page{size:"), html);
        assertTrue(html.contains("xpt-page"), html);
        assertTrue(html.contains("第 1 / 2 页"), html);
        assertTrue(html.contains("第 2 / 2 页"), html);
        // 每页都复制表头行
        assertEquals(2, countOccurrences(html, "<thead>"));
        assertTrue(html.contains("row0"));
        assertTrue(html.contains("row59"));
    }

    @Test
    public void testRenderWithoutPageSetupUnchanged() {
        ExcelSheet sheet = buildSheet(5);
        sheet.setPageSetup(null);
        ExcelWorkbook wb = new ExcelWorkbook();
        wb.setSheets(new ArrayList<>(List.of(sheet)));

        HtmlReportRendererFactory factory = new HtmlReportRendererFactory();
        ITextTemplateOutput output = factory.buildRenderer(wb, (ctx, consumer) -> consumer.accept(sheet, ctx));
        String html = output.generateText(newContext());

        assertFalse(html.contains("xpt-page"), html);
        assertFalse(html.contains("@page"), html);
        assertTrue(html.contains("row4"));
    }

    private int countOccurrences(String html, String token) {
        int count = 0;
        int idx = 0;
        while ((idx = html.indexOf(token, idx)) >= 0) {
            count++;
            idx += token.length();
        }
        return count;
    }

    private IEvalContext newContext() {
        return io.nop.xlang.api.XLang.newEvalScope();
    }
}
