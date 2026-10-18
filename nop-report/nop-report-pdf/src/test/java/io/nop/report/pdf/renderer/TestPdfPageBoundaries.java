/**
 * Copyright (c) 2017-2024 Nop Platform.
 */
package io.nop.report.pdf.renderer;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.ExcelPageBreaks;
import io.nop.excel.model.ExcelPageMargins;
import io.nop.excel.model.ExcelPageSetup;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.pdf.verify.GoldenCompareResult;
import io.nop.pdf.verify.PdfGoldenManager;
import io.nop.pdf.verify.PdfImageRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3：分页边界健壮性——手动分页符、超页行、合并单元格跨页、空白尾行裁剪、重复块边界。
 */
public class TestPdfPageBoundaries extends JunitBaseTestCase {
    private static final double ROW_HEIGHT = 20.0;

    /** 编程模板构造器：4 列、行高 20pt、每行 4 个无值无样式占位单元格 */
    class SheetBuilder {
        final ExcelWorkbook workbook = new ExcelWorkbook();
        final io.nop.excel.model.ExcelSheet sheet = new io.nop.excel.model.ExcelSheet();

        SheetBuilder(int rows) {
            ExcelStyle style = new ExcelStyle();
            style.setId("text");
            ExcelFont font = new ExcelFont();
            font.setFontName("Helvetica");
            style.setFont(font);
            workbook.addStyle(style);

            sheet.setName("s1");
            sheet.setDefaultColumnWidth(60.0);
            sheet.setDefaultRowHeight(ROW_HEIGHT);

            ExcelPageSetup pageSetup = new ExcelPageSetup();
            pageSetup.setPaperWidth(595f);
            pageSetup.setPaperHeight(842f);
            sheet.setPageSetup(pageSetup);

            ExcelPageMargins margins = new ExcelPageMargins();
            margins.setLeft(36.0);
            margins.setRight(36.0);
            margins.setTop(30.0);
            margins.setBottom(20.0);
            margins.setHeader(10.0);
            margins.setFooter(10.0);
            sheet.setPageMargins(margins);

            for (int r = 0; r < rows; r++) {
                io.nop.excel.model.ExcelRow row = new io.nop.excel.model.ExcelRow();
                row.setHeight(ROW_HEIGHT);
                for (int c = 0; c < 4; c++)
                    row.getCells().add(new ExcelCell());
                sheet.getTable().getRows().add(row);
            }
        }

        SheetBuilder setText(int rowIndex, String text) {
            ExcelCell cell = (ExcelCell) sheet.getTable().getRows().get(rowIndex).makeCell(0);
            cell.setValue(text);
            cell.setStyleId("text");
            return this;
        }

        SheetBuilder setText(int rowIndex, int colIndex, String text) {
            ExcelCell cell = (ExcelCell) sheet.getTable().getRows().get(rowIndex).makeCell(colIndex);
            cell.setValue(text);
            cell.setStyleId("text");
            return this;
        }

        SheetBuilder rowHeight(int rowIndex, double height) {
            sheet.getTable().getRows().get(rowIndex).setHeight(height);
            return this;
        }

        SheetBuilder breaks(List<Integer> rowBreaks, List<Integer> colBreaks) {
            ExcelPageBreaks b = new ExcelPageBreaks();
            if (rowBreaks != null)
                b.setRows(rowBreaks);
            if (colBreaks != null)
                b.setCols(colBreaks);
            sheet.setPageBreaks(b);
            return this;
        }

        SheetBuilder pageSetup(Consumer<ExcelPageSetup> setup) {
            setup.accept(sheet.getPageSetup());
            return this;
        }

        byte[] toPdf() throws Exception {
            workbook.addSheet(sheet);
            PdfReportRenderer renderer = new PdfReportRenderer(workbook, null);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            renderer.generateToStream(bos, null);
            return bos.toByteArray();
        }
    }

    private int pageCount(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        }
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
     * 手动行分页符：第 10 行（0-based）后 break → 页 1 止于 R10、页 2 始于 R11。
     */
    @Test
    public void testRowBreakSplitsAtExactRow() throws Exception {
        SheetBuilder b = new SheetBuilder(40);
        for (int r = 0; r < 40; r++)
            b.setText(r, "R" + r);
        b.breaks(Arrays.asList(10), null);
        byte[] pdf = b.toPdf();
        List<String> pages = pageTexts(pdf);
        assertTrue(pages.size() >= 2);
        assertTrue(pages.get(0).contains("R10"), "page 1 must end at row 10 (break-after semantics)");
        assertTrue(pages.get(1).contains("R11"), "page 2 must start at row 11");
        assertTrue(!pages.get(0).contains("R11"), "row 11 must not appear on page 1");
    }

    /**
     * 手动列分页符：第 1 列后 break → 列带拆分，跨带文本不串页。
     */
    @Test
    public void testColBreakSplitsColumns() throws Exception {
        SheetBuilder b = new SheetBuilder(30);
        for (int r = 0; r < 30; r++) {
            b.setText(r, 0, "R" + r + "#0");
            b.setText(r, 2, "R" + r + "#2");
        }
        b.breaks(null, Arrays.asList(1));
        byte[] pdf = b.toPdf();
        List<String> pages = pageTexts(pdf);
        assertTrue(pages.size() >= 2, "col break must create a second column band page");
        assertTrue(pages.get(0).contains("R5#0"), "page 1 band contains first column");
        assertTrue(!pages.get(0).contains("R5#2"), "columns after break must not appear on page 1");
        assertTrue(pages.get(1).contains("R5#2"), "page 2 band contains later columns");
    }

    /**
     * fit×breaks：fit scale 与 breaks 无关，手动 break 优先、页数可超 fit 目标（WI3 裁定）。
     */
    @Test
    public void testManualBreakOverridesFitTarget() throws Exception {
        SheetBuilder b = new SheetBuilder(40);
        for (int r = 0; r < 40; r++)
            b.setText(r, "R" + r);
        b.breaks(Arrays.asList(10, 20, 30), null);
        b.pageSetup(ps -> ps.setFitToWidth(2));
        byte[] pdf = b.toPdf();
        List<String> pages = pageTexts(pdf);
        assertTrue(pages.size() >= 4, "3 manual breaks must force >=4 pages despite fit, got " + pages.size());
        assertTrue(pages.get(1).contains("R11"));
        assertTrue(pages.get(3).contains("R31"));
    }

    /**
     * 超页行（900pt > 页内容区 772pt）：独立成页（MediaBox 裁切语义，见 plan 基线），
     * 前后行不丢。
     */
    @Test
    public void testOversizedRowGetsOwnPageWithoutLosingNeighbors() throws Exception {
        SheetBuilder b = new SheetBuilder(12);
        for (int r = 0; r < 12; r++)
            b.setText(r, "R" + r);
        b.rowHeight(5, 900.0);
        byte[] pdf = b.toPdf();
        List<String> pages = pageTexts(pdf);
        String all = String.join("\n", pages);
        for (int r = 0; r < 12; r++)
            assertTrue(all.contains("R" + r), "row " + r + " must not be lost");
        assertTrue(pages.size() >= 3, "900pt row forces its own page between content pages, pages=" + pages.size());
    }

    /**
     * 合并单元格跨行拆分边界：clip 语义不抛异常、锚点内容渲染、不丢其他内容。
     */
    @Test
    public void testMergedCellAcrossRowSplitBoundary() throws Exception {
        SheetBuilder b = new SheetBuilder(40);
        for (int r = 0; r < 40; r++)
            b.setText(r, "R" + r);
        ExcelCell merged = (ExcelCell) b.sheet.getTable().getRows().get(10).makeCell(1);
        merged.setValue("MERGED");
        merged.setStyleId("text");
        b.sheet.getTable().mergeCell(10, 1, 3, 0); // rows 10-12 merged in column 1
        byte[] pdf = b.toPdf();
        List<String> pages = pageTexts(pdf);
        String all = String.join("\n", pages);
        assertTrue(all.contains("MERGED"), "merged cell anchor content must render");
        for (int r = 0; r < 40; r++)
            assertTrue(all.contains("R" + r), "row " + r + " must not be lost");
    }

    /**
     * 空白尾行裁剪：纯空白行（无值无样式）不产生额外页。
     * 基线 38 行恰 2 页；+20 空白行裁剪后仍 2 页（不裁剪则为 3 页）。
     */
    @Test
    public void testTrailingBlankRowsTrimmed() throws Exception {
        SheetBuilder baseline = new SheetBuilder(38);
        for (int r = 0; r < 38; r++)
            baseline.setText(r, "R" + r);
        SheetBuilder withBlank = new SheetBuilder(58);
        for (int r = 0; r < 38; r++)
            withBlank.setText(r, "R" + r);
        // rows 38-57 保持无值无样式
        assertEquals(pageCount(baseline.toPdf()), pageCount(withBlank.toPdf()),
                "trailing value-and-style-less rows must be trimmed");
    }

    /**
     * style-only 尾行不裁剪：仅带样式的尾行（模板边框行）仍占页容量。
     * style-only 行较基线多出 2pt/行 × 50 行 = 100pt 高度，制造额外页以证明未被裁剪。
     */
    @Test
    public void testStyleOnlyTrailingRowsNotTrimmed() throws Exception {
        SheetBuilder baseline = new SheetBuilder(30);
        for (int r = 0; r < 30; r++)
            baseline.setText(r, "R" + r);
        SheetBuilder withStyled = new SheetBuilder(80); // rows 30-79: 无值但有样式（text 样式）
        for (int r = 0; r < 30; r++)
            withStyled.setText(r, "R" + r);
        for (int r = 30; r < 80; r++)
            ((ExcelCell) withStyled.sheet.getTable().getRows().get(r).makeCell(0)).setStyleId("text");
        int basePages = pageCount(baseline.toPdf());
        int styledPages = pageCount(withStyled.toPdf());
        assertTrue(styledPages > basePages,
                "style-only trailing rows must NOT be trimmed: base=" + basePages + ", styled=" + styledPages);
    }

    /**
     * 重复块边界 clamp：拆分点落在重复前缀内部时不丢内容。
     * 行 0/1 各 700pt（页 1 只装行 0），行 1 位于重复前缀（1:2）内部。
     */
    @Test
    public void testRepeatPrefixBoundaryClamp() throws Exception {
        SheetBuilder b = new SheetBuilder(30);
        for (int r = 0; r < 30; r++)
            b.setText(r, "R" + r);
        b.rowHeight(0, 700.0).rowHeight(1, 700.0);
        b.pageSetup(ps -> ps.setRepeatTitleRows("1:2"));
        byte[] pdf = b.toPdf();
        List<String> pages = pageTexts(pdf);
        String all = String.join("\n", pages);
        for (int r = 0; r < 30; r++)
            assertTrue(all.contains("R" + r), "row " + r + " must not be lost, split landed inside repeat prefix");
        assertTrue(pages.size() >= 3, "700pt rows force per-row pages, pages=" + pages.size());
    }

    /**
     * 边界用例 golden：手动分页符 + 超页行。
     */
    @Test
    public void testGoldenSnapshots() throws Exception {
        PdfGoldenManager golden = PdfGoldenManager.forDefaultDir("wi3-page-boundaries");
        SheetBuilder brk = new SheetBuilder(40);
        for (int r = 0; r < 40; r++)
            brk.setText(r, "R" + r);
        brk.breaks(Arrays.asList(10), null);
        assertGolden(golden, "row-break", brk.toPdf());

        SheetBuilder oversized = new SheetBuilder(12);
        for (int r = 0; r < 12; r++)
            oversized.setText(r, "R" + r);
        oversized.rowHeight(5, 900.0);
        assertGolden(golden, "oversized-row", oversized.toPdf());
    }

    private void assertGolden(PdfGoldenManager golden, String name, byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            List<BufferedImage> images = PdfImageRenderer.renderAll(doc, PdfImageRenderer.DEFAULT_DPI);
            for (int i = 0; i < images.size(); i++) {
                GoldenCompareResult result = golden.compare(name + "-page-" + (i + 1), images.get(i));
                assertTrue(result.getStatus() == GoldenCompareResult.Status.PASS
                        || result.getStatus() == GoldenCompareResult.Status.BASELINE_CREATED,
                        "golden regression on " + name + " page " + (i + 1) + ": " + result);
            }
        }
    }
}
