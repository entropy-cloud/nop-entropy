/**
 * Copyright (c) 2017-2024 Nop Platform.
 */
package io.nop.report.pdf.renderer;

import io.nop.api.core.exceptions.NopException;
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
import io.nop.report.pdf.ReportPdfErrors;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI1：模板级 Print_Titles（repeatTitleRows/repeatTitleCols）在 PDF 分页中的消费。
 * 模型声明优先于全局配置缺省；非前缀声明显式报错；视觉验收走 io.nop.pdf.verify 工具链。
 */
public class TestPdfPrintTitlesRender extends JunitBaseTestCase {
    private static final String TITLE_ROW_1 = "TITLE-ONE";
    private static final String TITLE_ROW_2 = "TITLE-TWO";
    private static final int DATA_ROWS = 40;
    private static final double ROW_HEIGHT = 20.0;

    private byte[] renderPdf(String repeatTitleRows, String repeatTitleCols) throws Exception {
        ExcelWorkbook workbook = newExcelWorkbook(repeatTitleRows, repeatTitleCols);
        PdfReportRenderer renderer = new PdfReportRenderer(workbook, null);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        renderer.generateToStream(bos, null);
        return bos.toByteArray();
    }

    private ExcelWorkbook newExcelWorkbook(String repeatTitleRows, String repeatTitleCols) {
        ExcelWorkbook workbook = new ExcelWorkbook();

        ExcelStyle style = new ExcelStyle();
        style.setId("text");
        ExcelFont font = new ExcelFont();
        font.setFontName("Helvetica");
        style.setFont(font);
        workbook.addStyle(style);

        io.nop.excel.model.ExcelSheet sheet = new io.nop.excel.model.ExcelSheet();
        sheet.setName("s1");

        ExcelPageSetup pageSetup = new ExcelPageSetup();
        pageSetup.setPaperWidth(595f);
        pageSetup.setPaperHeight(842f);
        pageSetup.setRepeatTitleRows(repeatTitleRows);
        pageSetup.setRepeatTitleCols(repeatTitleCols);
        sheet.setPageSetup(pageSetup);

        ExcelPageMargins margins = new ExcelPageMargins();
        margins.setLeft(57.0);
        margins.setRight(57.0);
        margins.setTop(50.0);
        margins.setBottom(40.0);
        margins.setHeader(20.0);
        margins.setFooter(20.0);
        sheet.setPageMargins(margins);

        // 2 个标题行 + 40 个数据行，每行 20pt：打印区高约 842-130=712pt，每页约 35 行 → 至少 2 页
        addTextRow(sheet, 0, TITLE_ROW_1);
        addTextRow(sheet, 1, TITLE_ROW_2);
        for (int i = 0; i < DATA_ROWS; i++) {
            addTextRow(sheet, 2 + i, "DATA-" + i);
        }

        workbook.addSheet(sheet);
        return workbook;
    }

    private void addTextRow(io.nop.excel.model.ExcelSheet sheet, int rowIndex, String text) {
        ExcelRow row = new ExcelRow();
        row.setHeight(ROW_HEIGHT);
        ExcelCell cell = new ExcelCell();
        cell.setValue(text);
        cell.setStyleId("text");
        row.internalAddCell(cell);
        sheet.getTable().getRows().add(row);
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
     * (a) 模板声明 2 行标题：每页（含续页）都含标题行文本，且数据完整不丢。
     * (b) 模型优先：全局配置缺省 repeat-header-rows=1 只会重复第 1 行；
     * 续页出现 TITLE-TWO 证明模板声明覆盖了全局缺省。
     */
    @Test
    public void testModelTitlesRepeatOnContinuationPages() throws Exception {
        byte[] pdf = renderPdf("1:2", null);
        List<String> pages = pageTexts(pdf);
        assertTrue(pages.size() >= 2, "40 data rows @20pt should paginate, got " + pages.size() + " pages");

        for (int i = 1; i < pages.size(); i++) {
            assertTrue(pages.get(i).contains(TITLE_ROW_1), "page " + (i + 1) + " missing TITLE-ONE");
            assertTrue(pages.get(i).contains(TITLE_ROW_2), "page " + (i + 1) + " missing TITLE-TWO");
        }
        // 数据不丢
        String all = String.join("\n", pages);
        for (int i = 0; i < DATA_ROWS; i++) {
            assertTrue(all.contains("DATA-" + i), "missing DATA-" + i);
        }
    }

    /**
     * (b) 无模型声明时保持现行为：全局配置缺省重复 1 行，续页只有第 1 标题行。
     */
    @Test
    public void testNoModelDeclarationFallsBackToConfig() throws Exception {
        byte[] pdf = renderPdf(null, null);
        List<String> pages = pageTexts(pdf);
        assertTrue(pages.size() >= 2);
        assertTrue(pages.get(1).contains(TITLE_ROW_1), "config default should repeat first row");
        assertFalse(pages.get(1).contains(TITLE_ROW_2),
                "config default (1 row) must NOT repeat the second row on continuation pages");
    }

    /**
     * (c) 非前缀声明（3:4）渲染端显式报错，与是否实际分页无关。
     */
    @Test
    public void testNonPrefixDeclarationFailsLoudly() {
        NopException err = assertThrows(NopException.class, () -> renderPdf("3:4", null));
        assertEquals(ReportPdfErrors.ERR_PDF_UNSUPPORTED_PRINT_TITLES.getErrorCode(), err.getErrorCode());
    }

    /**
     * (d) 视觉验收：PDF→PNG（110dpi）+ golden 快照（_tmp，同机回归门）+
     * 续页标题行区域结构断言（跨环境可移植门）。
     */
    @Test
    public void testVisualAcceptanceGoldenAndRegionProbe() throws Exception {
        byte[] pdf = renderPdf("1:2", null);
        List<BufferedImage> images;
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            images = PdfImageRenderer.renderAll(doc, PdfImageRenderer.DEFAULT_DPI);
        }
        assertTrue(images.size() >= 2);

        // golden：首跑建基线（BASELINE_CREATED），复跑像素一致（PASS）；任何差异即 FAIL；
        // 测试环境不开启 update 模式，UPDATED 属异常状态同样拒绝
        PdfGoldenManager golden = PdfGoldenManager.forDefaultDir("wi1-print-titles");
        for (int i = 0; i < images.size(); i++) {
            GoldenCompareResult result = golden.compare("page-" + (i + 1), images.get(i));
            assertTrue(result.getStatus() == GoldenCompareResult.Status.PASS
                            || result.getStatus() == GoldenCompareResult.Status.BASELINE_CREATED,
                    "golden regression on page " + (i + 1) + ": " + result);
        }

        // 续页标题行区域：打印区顶部 2 行高度内必须有文字像素（标题行被真实绘制，非空白）
        BufferedImage page2 = images.get(1);
        // printArea.y = top(50)+header(20) = 70pt；2 行 * 20pt = 40pt；110dpi: pt*110/72
        int yFrom = (int) Math.round(70 * 110.0 / 72);
        int yTo = (int) Math.round(110 * 110.0 / 72);
        int xFrom = (int) Math.round(57 * 110.0 / 72);
        int xTo = (int) Math.round((595 - 57) * 110.0 / 72);
        int textPixels = 0;
        for (int y = yFrom; y < yTo && y < page2.getHeight(); y++) {
            for (int x = xFrom; x < xTo && x < page2.getWidth(); x++) {
                int rgb = page2.getRGB(x, y);
                int max = Math.max((rgb >> 16) & 0xFF, Math.max((rgb >> 8) & 0xFF, rgb & 0xFF));
                if (max < 160)
                    textPixels++;
            }
        }
        assertTrue(textPixels > 50, "continuation page title band should contain text pixels, got " + textPixels);
    }
}
