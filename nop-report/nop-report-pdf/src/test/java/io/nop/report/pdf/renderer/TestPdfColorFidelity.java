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
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI9：颜色保真——8 位 alpha 填充混色、rgba 边框、indexed/theme 回归、
 * alpha=255 不注入 ExtGState、gs 作用域收口。
 */
public class TestPdfColorFidelity extends JunitBaseTestCase {

    private ExcelWorkbook newWorkbook(String fillFgColor, String borderColor) {
        ExcelWorkbook workbook = new ExcelWorkbook();

        ExcelStyle fillStyle = new ExcelStyle();
        fillStyle.setId("fill");
        fillStyle.setFillFgColor(fillFgColor);
        ExcelFont font = new ExcelFont();
        font.setFontName("Helvetica");
        fillStyle.setFont(font);
        workbook.addStyle(fillStyle);

        ExcelStyle borderStyle = new ExcelStyle();
        borderStyle.setId("borders");
        io.nop.excel.model.ExcelBorderStyle top = new io.nop.excel.model.ExcelBorderStyle();
        top.setType(io.nop.office.model.constants.OfficeLineStyle.SINGLE);
        top.setWeight(2);
        top.setColor(borderColor);
        borderStyle.setTopBorder(top);
        borderStyle.setBottomBorder(top);
        borderStyle.setLeftBorder(top);
        borderStyle.setRightBorder(top);
        workbook.addStyle(borderStyle);

        io.nop.excel.model.ExcelSheet sheet = new io.nop.excel.model.ExcelSheet();
        sheet.setName("s1");
        sheet.setDefaultColumnWidth(60.0);
        sheet.setDefaultRowHeight(20.0);

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

        for (int r = 0; r < 12; r++) {
            ExcelRow row = new ExcelRow();
            row.setHeight(20.0);
            for (int c = 0; c < 4; c++) {
                ExcelCell cell = new ExcelCell();
                // 行 0-3：黄色底 + 半透明红填充（混色采样）；行 4+：仅边框样式
                cell.setStyleId(r < 4 ? "fill" : "borders");
                row.internalAddCell(cell);
            }
            sheet.getTable().getRows().add(row);
        }
        workbook.addSheet(sheet);
        return workbook;
    }

    private byte[] render(ExcelWorkbook workbook) throws Exception {
        PdfReportRenderer renderer = new PdfReportRenderer(workbook, null);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        renderer.generateToStream(bos, null);
        return bos.toByteArray();
    }

    private BufferedImage renderFirstPage(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return PdfImageRenderer.renderPage(doc, 0, PdfImageRenderer.DEFAULT_DPI);
        }
    }

    private Color probe(BufferedImage img, double ptX, double ptY) {
        return new Color(img.getRGB((int) Math.round(ptX * 110.0 / 72), (int) Math.round(ptY * 110.0 / 72)));
    }

    /**
     * 8 位 alpha 填充：#80FF0000（红 50% 透明）叠白页背景 → 粉 (255,127,127)。
     * 不透明红则绿/蓝通道为 0——区分 alpha 是否真实生效。
     */
    @Test
    public void testAlphaFillBlendsOverBackground() throws Exception {
        byte[] pdf = render(newWorkbook("#80FF0000", "000000"));
        BufferedImage img = renderFirstPage(pdf);
        // 行 0-3 单元格中心（60pt 列、20pt 行）：x=66、y=40+10=50pt
        Color blended = probe(img, 66, 50);
        // src=红(255,0,0) a=128/255, dst=白(255,255,255) → (255,127,127)
        assertEquals(255, blended.getRed(), 2, "red channel stays 255");
        assertEquals(127, blended.getGreen(), 3, "green = 0*0.5 + 255*0.5 ≈ 127");
        assertEquals(127, blended.getBlue(), 3, "blue = 0*0.5 + 255*0.5 ≈ 127");
    }

    /**
     * rgba 边框颜色（此前 toNormalizedRgb 会抛异常/parseColor 静默黑）：现正确渲染非黑色。
     */
    @Test
    public void testRgbaBorderRenders() throws Exception {
        byte[] pdf = render(newWorkbook("FFFF00", "rgba(0,0,255,1)"));
        BufferedImage img = renderFirstPage(pdf);
        // 行 4 单元格顶边框 y = 40 + 4*20 = 120pt；首单元格中心 x=66
        Color border = probe(img, 66, 120);
        assertTrue(border.getBlue() > 120 && border.getRed() < 100,
                "rgba blue border must render as blue, got " + border);
    }

    /**
     * 0x 前缀填充色（此前 parseColor 静默黑）：现正确渲染。
     */
    @Test
    public void testHexPrefixFillColor() throws Exception {
        byte[] pdf = render(newWorkbook("0xFF0000", "000000"));
        BufferedImage img = renderFirstPage(pdf);
        Color fill = probe(img, 66, 50);
        assertTrue(fill.getRed() > 200 && fill.getGreen() < 60 && fill.getBlue() < 60,
                "0x-prefixed red fill must render as red, got " + fill);
    }

    /**
     * alpha=255：内容流不注入 gs 操作符（ExtGState 仅在真透明时引入）。
     */
    @Test
    public void testOpaqueColorNoExtGState() throws Exception {
        byte[] pdf = render(newWorkbook("FFFF00", "000000"));
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            for (var page : doc.getPages()) {
                PDFStreamParser parser = new PDFStreamParser(page);
                var tokens = parser.parse();
                for (Object token : tokens) {
                    if (token instanceof Operator op && "gs".equals(op.getName())) {
                        throw new AssertionError("opaque colors must not inject ExtGState");
                    }
                }
            }
        }
    }

    /**
     * gs 作用域收口：alpha 填充后同页绘制的不透明文本/边框不受 alpha 影响
     * （alpha 填充下方边框线仍为纯色，不与黄色混色）。
     */
    @Test
    public void testAlphaScopeRestored() throws Exception {
        byte[] pdf = render(newWorkbook("#80FF0000", "000000"));
        BufferedImage img = renderFirstPage(pdf);
        // 行 4+ 的单元格边框（不透明黑色）在 alpha 填充之后绘制——
        // 若 gs 泄漏，黑色边框会与背景混色为灰
        Color border = probe(img, 66, 120);
        assertTrue(border.getRed() < 60 && border.getGreen() < 60 && border.getBlue() < 60,
                "opaque border after alpha fill must stay pure black, got " + border);
    }

    /**
     * WI9 审计 Blocker 回归：边框色 0x 前缀/裸 hex（旧 parseColor 静默黑）委托后正确渲染。
     */
    @Test
    public void testBorderColorHexPrefixForms() throws Exception {
        // 0x 前缀蓝色边框
        BufferedImage img1 = renderFirstPage(render(newWorkbook("FFFF00", "0x0000FF")));
        Color blue = probe(img1, 66, 120);
        assertTrue(blue.getBlue() > 120 && blue.getRed() < 100,
                "0x-prefixed blue border must render, got " + blue);

        // 裸 hex 蓝色边框
        BufferedImage img2 = renderFirstPage(render(newWorkbook("FFFF00", "0000FF")));
        Color blue2 = probe(img2, 66, 120);
        assertTrue(blue2.getBlue() > 120 && blue2.getRed() < 100,
                "bare-hex blue border must render, got " + blue2);
    }

    /**
     * golden：alpha 填充混色页。
     */
    @Test
    public void testGoldenAlphaBlend() throws Exception {
        byte[] pdf = render(newWorkbook("#80FF0000", "000000"));
        PdfGoldenManager golden = PdfGoldenManager.forDefaultDir("wi9-color-fidelity");
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            List<BufferedImage> images = PdfImageRenderer.renderAll(doc, PdfImageRenderer.DEFAULT_DPI);
            for (int i = 0; i < images.size(); i++) {
                GoldenCompareResult result = golden.compare("alpha-blend-page-" + (i + 1), images.get(i));
                assertTrue(result.getStatus() == GoldenCompareResult.Status.PASS
                        || result.getStatus() == GoldenCompareResult.Status.BASELINE_CREATED,
                        "golden regression page " + (i + 1) + ": " + result);
            }
        }
    }
}
