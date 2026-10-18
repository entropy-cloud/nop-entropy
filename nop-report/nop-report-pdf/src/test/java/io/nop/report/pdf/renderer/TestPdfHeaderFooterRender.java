/**
 * Copyright (c) 2017-2024 Nop Platform.
 */
package io.nop.report.pdf.renderer;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.ExcelHeaderFooter;
import io.nop.excel.model.ExcelPageMargins;
import io.nop.excel.model.ExcelPageSetup;
import io.nop.excel.model.ExcelRow;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.pdf.verify.GoldenCompareResult;
import io.nop.pdf.verify.PdfGoldenManager;
import io.nop.report.pdf.ReportPdfConfigs;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI4：页眉页脚 &-占位符求值、页码作用域、首页/奇偶变体、字体代码不字面打印。
 */
public class TestPdfHeaderFooterRender extends JunitBaseTestCase {

    private ExcelWorkbook newWorkbook(int sheets, int rowsPerSheet, String footerCenter) {
        int[] rowCounts = new int[sheets];
        java.util.Arrays.fill(rowCounts, rowsPerSheet);
        return newWorkbook(rowCounts, footerCenter);
    }

    private ExcelWorkbook newWorkbook(int[] rowCounts, String footerCenter) {
        ExcelWorkbook workbook = new ExcelWorkbook();

        ExcelStyle style = new ExcelStyle();
        style.setId("text");
        ExcelFont font = new ExcelFont();
        font.setFontName("Helvetica");
        style.setFont(font);
        workbook.addStyle(style);

        for (int s = 0; s < rowCounts.length; s++) {
            io.nop.excel.model.ExcelSheet sheet = new io.nop.excel.model.ExcelSheet();
            sheet.setName("Sheet" + (s + 1));
            sheet.setDefaultColumnWidth(60.0);
            sheet.setDefaultRowHeight(20.0);

            ExcelPageSetup pageSetup = new ExcelPageSetup();
            pageSetup.setPaperWidth(595f);
            pageSetup.setPaperHeight(842f);

            ExcelHeaderFooter footer = new ExcelHeaderFooter();
            footer.setCenter(footerCenter.replace("{S}", String.valueOf(s + 1)));
            footer.setStyle(style);
            pageSetup.setFooter(footer);
            sheet.setPageSetup(pageSetup);

            ExcelPageMargins margins = new ExcelPageMargins();
            margins.setLeft(36.0);
            margins.setRight(36.0);
            margins.setTop(30.0);
            margins.setBottom(20.0);
            margins.setHeader(10.0);
            margins.setFooter(20.0);
            sheet.setPageMargins(margins);

            for (int r = 0; r < rowCounts[s]; r++) {
                ExcelRow row = new ExcelRow();
                row.setHeight(20.0);
                for (int c = 0; c < 4; c++) {
                    ExcelCell cell = new ExcelCell();
                    cell.setValue("R" + r);
                    cell.setStyleId("text");
                    row.internalAddCell(cell);
                }
                sheet.getTable().getRows().add(row);
            }
            workbook.addSheet(sheet);
        }
        return workbook;
    }

    private byte[] render(ExcelWorkbook workbook) throws Exception {
        PdfReportRenderer renderer = new PdfReportRenderer(workbook, null);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        renderer.generateToStream(bos, null);
        return bos.toByteArray();
    }

    private List<String> pageTexts(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<String> ret = new java.util.ArrayList<>();
            for (int i = 1, n = doc.getNumberOfPages(); i <= n; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                ret.add(stripper.getText(doc));
            }
            return ret;
        }
    }

    /**
     * &P/&N 全局作用域：单 sheet 3 页，页脚依次为 1/3、2/3、3/3；& 代码不字面出现。
     */
    @Test
    public void testPlaceholderPageNumbersGlobal() throws Exception {
        byte[] pdf = render(newWorkbook(1, 90, "&P / &N"));
        List<String> pages = pageTexts(pdf);
        assertEquals(3, pages.size(), "90 rows @20pt should paginate to 3 pages");
        assertTrue(pages.get(0).contains("1 / 3"));
        assertTrue(pages.get(1).contains("2 / 3"));
        assertTrue(pages.get(2).contains("3 / 3"));
        for (String page : pages)
            assertFalse(page.contains("&"), "&-codes must never appear literally, page=" + page);
    }

    /**
     * scope=sheet：双 sheet（3+2 页），&P/&N 为各 sheet 内序号/页数。
     */
    @Test
    public void testSheetScopePageNumbers() throws Exception {
        var configProvider = AppConfig.getConfigProvider();
        configProvider.updateConfigValue(ReportPdfConfigs.CFG_PDF_PAGE_NUMBER_SCOPE, "sheet");
        try {
            // sheet1 90 行=3 页，sheet2 60 行=2 页
            byte[] pdf = render(newWorkbook(new int[]{90, 60}, "&P / &N"));
            List<String> pages = pageTexts(pdf);
            assertEquals(5, pages.size());
            assertTrue(pages.get(0).contains("1 / 3"));
            assertTrue(pages.get(2).contains("3 / 3"));
            assertTrue(pages.get(3).contains("1 / 2"), "sheet 2 must restart page numbering");
            assertTrue(pages.get(4).contains("2 / 2"));
        } finally {
            configProvider.updateConfigValue(ReportPdfConfigs.CFG_PDF_PAGE_NUMBER_SCOPE, "global");
        }
    }

    /**
     * &A 工作表名求值。
     */
    @Test
    public void testSheetNamePlaceholder() throws Exception {
        byte[] pdf = render(newWorkbook(1, 90, "&A"));
        List<String> pages = pageTexts(pdf);
        for (String page : pages)
            assertTrue(page.contains("Sheet1"), "&A must evaluate to the sheet name");
    }

    /**
     * &D/&T 日期时间：固定格式 regex 断言（避免跨午夜 flake）。
     */
    @Test
    public void testDateTimePlaceholders() throws Exception {
        byte[] pdf = render(newWorkbook(1, 90, "&D | &T"));
        List<String> pages = pageTexts(pdf);
        Pattern date = Pattern.compile("\\d{4}/\\d{1,2}/\\d{1,2}");
        Pattern time = Pattern.compile("\\d{2}:\\d{2}:\\d{2}");
        assertTrue(date.matcher(pages.get(0)).find(), "&D must render as yyyy/M/d");
        assertTrue(time.matcher(pages.get(0)).find(), "&T must render as HH:mm:ss");
    }

    /**
     * 首页不同：differentFirst + firstFooter 只出现在页 1，其余页用 odd footer。
     */
    @Test
    public void testDifferentFirstFooter() throws Exception {
        ExcelWorkbook workbook = newWorkbook(1, 90, "ODD");
        io.nop.excel.model.ExcelSheet sheet = workbook.getSheets().get(0);
        ExcelPageSetup ps = sheet.getPageSetup();
        ps.setDifferentFirst(true);
        ExcelHeaderFooter firstFooter = new ExcelHeaderFooter();
        firstFooter.setCenter("FIRST");
        firstFooter.setStyle(ps.getFooter().getStyle());
        ps.setFirstFooter(firstFooter);

        List<String> pages = pageTexts(render(workbook));
        assertTrue(pages.get(0).contains("FIRST"), "page 1 uses firstFooter");
        assertTrue(!pages.get(0).contains("ODD"));
        assertTrue(pages.get(1).contains("ODD"), "page 2 uses odd footer");
        assertTrue(!pages.get(1).contains("FIRST"));
    }

    /**
     * 奇偶不同：differentOddEven + evenFooter 出现在偶数页（1-based 第 2 页）。
     */
    @Test
    public void testDifferentOddEvenFooter() throws Exception {
        ExcelWorkbook workbook = newWorkbook(1, 90, "ODD");
        io.nop.excel.model.ExcelSheet sheet = workbook.getSheets().get(0);
        ExcelPageSetup ps = sheet.getPageSetup();
        ps.setDifferentOddEven(true);
        ExcelHeaderFooter evenFooter = new ExcelHeaderFooter();
        evenFooter.setCenter("EVEN");
        evenFooter.setStyle(ps.getFooter().getStyle());
        ps.setEvenFooter(evenFooter);

        List<String> pages = pageTexts(render(workbook));
        assertTrue(pages.get(0).contains("ODD"), "page 1 (odd) uses odd footer");
        assertTrue(pages.get(1).contains("EVEN"), "page 2 (even) uses even footer");
        assertTrue(pages.get(2).contains("ODD"), "page 3 (odd) uses odd footer");
    }

    /**
     * 字体/字号代码段：渲染后代码不字面出现、文本保留（字体切换由 golden 视觉覆盖）。
     */
    @Test
    public void testFontCodeSectionNotLiteral() throws Exception {
        byte[] pdf = render(newWorkbook(1, 90, "&\"Helvetica,Bold\"&14 FOOT&9ER"));
        List<String> pages = pageTexts(pdf);
        String all = String.join("\n", pages);
        assertFalse(all.contains("&\""), "font code must not print literally");
        assertTrue(all.contains("FOOT"), "segment text must render");
        assertTrue(all.contains("ER"), "post-size-code text must render");
    }

    /**
     * 无效 scope 配置显式报错（fail-loud，不静默回退）。
     */
    @Test
    public void testInvalidScopeFailsLoudly() {
        var configProvider = AppConfig.getConfigProvider();
        configProvider.updateConfigValue(ReportPdfConfigs.CFG_PDF_PAGE_NUMBER_SCOPE, "bogus");
        try {
            assertThrows(NopException.class, () -> render(newWorkbook(1, 90, "&P")));
        } finally {
            configProvider.updateConfigValue(ReportPdfConfigs.CFG_PDF_PAGE_NUMBER_SCOPE, "global");
        }
    }

    /**
     * golden：&P/&N 页脚 + 首页不同。
     */
    @Test
    public void testGoldenSnapshots() throws Exception {
        PdfGoldenManager golden = PdfGoldenManager.forDefaultDir("wi4-header-footer");
        assertGolden(golden, "page-numbers", render(newWorkbook(1, 90, "&P / &N")));

        ExcelWorkbook wb = newWorkbook(1, 90, "ODD");
        io.nop.excel.model.ExcelSheet sheet = wb.getSheets().get(0);
        ExcelPageSetup ps = sheet.getPageSetup();
        ps.setDifferentFirst(true);
        ExcelHeaderFooter firstFooter = new ExcelHeaderFooter();
        firstFooter.setCenter("FIRST");
        firstFooter.setStyle(ps.getFooter().getStyle());
        ps.setFirstFooter(firstFooter);
        assertGolden(golden, "first-different", render(wb));
    }

    private void assertGolden(PdfGoldenManager golden, String name, byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            List<java.awt.image.BufferedImage> images = io.nop.pdf.verify.PdfImageRenderer.renderAll(doc,
                    io.nop.pdf.verify.PdfImageRenderer.DEFAULT_DPI);
            for (int i = 0; i < images.size(); i++) {
                GoldenCompareResult result = golden.compare(name + "-page-" + (i + 1), images.get(i));
                assertTrue(result.getStatus() == GoldenCompareResult.Status.PASS
                        || result.getStatus() == GoldenCompareResult.Status.BASELINE_CREATED,
                        "golden regression on " + name + " page " + (i + 1) + ": " + result);
            }
        }
    }
}
