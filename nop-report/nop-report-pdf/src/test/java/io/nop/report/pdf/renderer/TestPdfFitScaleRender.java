/**
 * Copyright (c) 2017-2024 Nop Platform.
 */
package io.nop.report.pdf.renderer;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.ExcelPageMargins;
import io.nop.excel.model.ExcelPageSetup;
import io.nop.excel.model.ExcelRow;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.pdf.verify.GoldenCompareResult;
import io.nop.pdf.verify.PdfGoldenManager;
import io.nop.pdf.verify.PdfImageRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI2：fit 缩放目标页数语义、scale 页容量修正、最小可读字号收敛、fit×重复块交互。
 * 均匀列宽构造（splitTable 贪心打包下目标页数才是确定性的）。
 * 字号显式指定以避开最小字号收敛对几何断言的干扰（收敛语义由 (c) 专项验证）。
 */
public class TestPdfFitScaleRender extends JunitBaseTestCase {
    private static final String TITLE = "FIT-TITLE";
    private static final double COL_WIDTH = 60.0;
    private static final double ROW_HEIGHT = 20.0;
    /** 12pt：缩放后 >=6pt 阈值的最小字号（scale 最高 0.5 时恰好不受收敛影响） */
    private static final float FONT_NO_CLAMP = 12f;

    private byte[] render(int cols, int rows, Float fontSize, Consumer<ExcelPageSetup> setup) throws Exception {
        return renderSheet(cols, rows, 0, fontSize, setup);
    }

    private byte[] renderSheet(int cols, int rows, int titleRows, Float fontSize,
                               Consumer<ExcelPageSetup> setup) throws Exception {
        ExcelWorkbook workbook = new ExcelWorkbook();
        workbook.addStyle(textStyle(fontSize));

        io.nop.excel.model.ExcelSheet sheet = new io.nop.excel.model.ExcelSheet();
        sheet.setName("s1");
        sheet.setDefaultColumnWidth(COL_WIDTH);
        sheet.setDefaultRowHeight(ROW_HEIGHT);

        ExcelPageSetup pageSetup = new ExcelPageSetup();
        pageSetup.setPaperWidth(595f);
        pageSetup.setPaperHeight(842f);
        if (titleRows > 0)
            pageSetup.setRepeatTitleRows("1:" + titleRows);
        if (setup != null)
            setup.accept(pageSetup);
        sheet.setPageSetup(pageSetup);

        ExcelPageMargins margins = new ExcelPageMargins();
        margins.setLeft(36.0);
        margins.setRight(36.0);
        margins.setTop(30.0);
        margins.setBottom(20.0);
        margins.setHeader(10.0);
        margins.setFooter(10.0);
        sheet.setPageMargins(margins);

        int totalRows = titleRows + rows;
        for (int r = 0; r < totalRows; r++) {
            ExcelRow row = new ExcelRow();
            row.setHeight(ROW_HEIGHT);
            for (int c = 0; c < cols; c++) {
                ExcelCell cell = new ExcelCell();
                cell.setValue(r < titleRows ? TITLE : "C" + c + "R" + r);
                cell.setStyleId("text");
                row.internalAddCell(cell);
            }
            sheet.getTable().getRows().add(row);
        }
        workbook.addSheet(sheet);

        PdfReportRenderer renderer = new PdfReportRenderer(workbook, null);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        renderer.generateToStream(bos, null);
        return bos.toByteArray();
    }

    private ExcelStyle textStyle(Float fontSize) {
        ExcelStyle style = new ExcelStyle();
        style.setId("text");
        ExcelFont font = new ExcelFont();
        font.setFontName("Helvetica");
        if (fontSize != null)
            font.setFontSize(fontSize);
        style.setFont(font);
        return style;
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
     * (a) fit 2×1：宽表缩放到 2 页宽，纵向 1 页；数据不丢。
     * 表宽 30 列×60pt=1800pt，打印区宽 523pt：scale≈0.581，每页容量≈900pt → 恰 2 页。
     */
    @Test
    public void testFitToTwoPagesWide() throws Exception {
        byte[] pdf = render(30, 28, FONT_NO_CLAMP, ps -> {
            ps.setFitToWidth(2);
            ps.setFitToHeight(0);
        });
        assertEquals(2, pageCount(pdf), "30 cols x 60pt should fit exactly 2 pages wide with fitToWidth=2");
        List<String> pages = pageTexts(pdf);
        assertTrue(pages.get(0).contains("C0R0"));
        assertTrue(pages.get(1).contains("C29R0"), "right-most column should be on page 2");
        String all = String.join("\n", pages);
        for (int r = 0; r < 28; r++)
            assertTrue(all.contains("C0R" + r), "missing C0R" + r);
    }

    /**
     * (b) scale 页容量修正：scale=50 时页容量翻倍，同表页数减少。
     * 90 行×20pt=1800pt 高，打印区高 772pt：scale=100 → 3 页；scale=50（容量 1544pt）→ 2 页。
     */
    @Test
    public void testScaleAffectsPageCapacity() throws Exception {
        int at100 = pageCount(render(4, 90, FONT_NO_CLAMP, ps -> ps.setScale(100)));
        int at50 = pageCount(render(4, 90, FONT_NO_CLAMP, ps -> ps.setScale(50)));
        assertTrue(at100 > at50, "scale=50 should hold more rows per page: at100=" + at100 + ", at50=" + at50);
        assertEquals(2, at50, "90 rows @20pt with capacity 1544pt should be 2 pages");
    }

    /**
     * (c) 最小可读字号收敛：小纸+fit 1×1 会把 7pt 字号缩到阈值以下 → scale 收敛 → 页数超过目标 1 页；
     * 同几何下 60pt 字号不受阈值约束 → 恰 1 页。
     */
    @Test
    public void testMinFontSizeClampRelaxesFitTarget() throws Exception {
        int small = pageCount(render(30, 28, 7f, ps -> {
            ps.setFitToWidth(1);
            ps.setFitToHeight(1);
        }));
        int large = pageCount(render(30, 28, 60f, ps -> {
            ps.setFitToWidth(1);
            ps.setFitToHeight(1);
        }));
        assertEquals(1, large, "60pt font should fit 1 page without clamp");
        assertTrue(small > 1, "7pt font below 6pt threshold must relax the fit target (more pages), got " + small);
    }

    /**
     * (d) fit×重复块交互：fitToWidth=1 缩放宽表，纵向分页后续页仍重复标题行。
     * 20 列宽 1200pt → scale≈0.436（15pt 字号缩放后 6.5pt 不触发收敛），
     * 容量高 772/0.436≈1770pt → 100 行（2000pt）分 2 个纵向带。
     */
    @Test
    public void testFitScaleKeepsRepeatedTitlesOnContinuationPages() throws Exception {
        byte[] pdf = renderSheet(20, 100, 1, 15f, ps -> ps.setFitToWidth(1));
        List<String> pages = pageTexts(pdf);
        assertTrue(pages.size() >= 2, "shrunken table should still paginate vertically, pages=" + pages.size());
        for (int i = 1; i < pages.size(); i++) {
            assertTrue(pages.get(i).contains(TITLE), "continuation page " + (i + 1) + " missing repeated title");
        }
    }

    /**
     * (e) golden 视觉验收：fit 与 scale 用例逐页快照（同机回归门）。
     */
    @Test
    public void testGoldenSnapshots() throws Exception {
        PdfGoldenManager golden = PdfGoldenManager.forDefaultDir("wi2-fit-scale");
        assertGolden(golden, "fit-2x1", render(30, 28, FONT_NO_CLAMP, ps -> {
            ps.setFitToWidth(2);
            ps.setFitToHeight(0);
        }));
        assertGolden(golden, "scale-50", render(4, 90, FONT_NO_CLAMP, ps -> ps.setScale(50)));
    }

    private void assertGolden(PdfGoldenManager golden, String name, byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            List<java.awt.image.BufferedImage> images = PdfImageRenderer.renderAll(doc, PdfImageRenderer.DEFAULT_DPI);
            for (int i = 0; i < images.size(); i++) {
                GoldenCompareResult result = golden.compare(name + "-page-" + (i + 1), images.get(i));
                assertTrue(result.getStatus() == GoldenCompareResult.Status.PASS
                        || result.getStatus() == GoldenCompareResult.Status.BASELINE_CREATED,
                        "golden regression on " + name + " page " + (i + 1) + ": " + result);
            }
        }
    }
}
