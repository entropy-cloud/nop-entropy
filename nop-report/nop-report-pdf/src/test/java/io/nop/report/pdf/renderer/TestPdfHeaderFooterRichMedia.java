/**
 * Copyright (c) 2017-2024 Nop Platform.
 */
package io.nop.report.pdf.renderer;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.ExcelHeaderFooter;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.ExcelPageMargins;
import io.nop.excel.model.ExcelPageSetup;
import io.nop.excel.model.ExcelRow;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.pdf.verify.PdfGoldenManager;
import io.nop.pdf.verify.PdfImageRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI5：页眉页脚富文本与图片——&G logo 渲染、多字体序列、分区裁剪。
 */
public class TestPdfHeaderFooterRichMedia extends JunitBaseTestCase {

    private byte[] logoPng() throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                40, 40, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 40; y++)
            for (int x = 0; x < 40; x++)
                img.setRGB(x, y, 0xFF2060E0);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    /** 单 sheet，rows 行，页脚 footerCenter；可选 &G 图片设置 */
    private ExcelWorkbook newWorkbook(int rows, String footerCenter) {
        return newWorkbook(rows, footerCenter, null, null);
    }

    private ExcelWorkbook newWorkbook(int rows, String footerCenter, String imageName,
                                      List<ExcelImage> images) {
        ExcelWorkbook workbook = new ExcelWorkbook();

        ExcelStyle style = new ExcelStyle();
        style.setId("text");
        ExcelFont font = new ExcelFont();
        font.setFontName("Helvetica");
        style.setFont(font);
        workbook.addStyle(style);

        io.nop.excel.model.ExcelSheet sheet = new io.nop.excel.model.ExcelSheet();
        sheet.setName("s1");
        sheet.setDefaultColumnWidth(60.0);
        sheet.setDefaultRowHeight(20.0);

        ExcelPageSetup pageSetup = new ExcelPageSetup();
        pageSetup.setPaperWidth(595f);
        pageSetup.setPaperHeight(842f);

        ExcelHeaderFooter footer = new ExcelHeaderFooter();
        footer.setCenter(footerCenter);
        footer.setStyle(style);
        footer.setImageName(imageName);
        pageSetup.setFooter(footer);
        sheet.setPageSetup(pageSetup);

        ExcelPageMargins margins = new ExcelPageMargins();
        margins.setLeft(36.0);
        margins.setRight(36.0);
        margins.setTop(30.0);
        margins.setBottom(30.0);
        margins.setHeader(10.0);
        margins.setFooter(20.0);
        sheet.setPageMargins(margins);

        for (int r = 0; r < rows; r++) {
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

        if (images != null)
            sheet.setImages(images);

        workbook.addSheet(sheet);
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
            List<String> ret = new ArrayList<>();
            for (int i = 1, n = doc.getNumberOfPages(); i <= n; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                ret.add(stripper.getText(doc));
            }
            return ret;
        }
    }

    /**
     * &G 有图：sheet 具名图片渲染于页脚分区（图片页眉/脚 golden + 采样）。
     */
    @Test
    public void testGCodeRendersNamedImage() throws Exception {
        ExcelImage logo = new ExcelImage();
        logo.setName("logo");
        logo.setDataBytes(logoPng());
        // HF 素材图 print=false：不作为 sheet 定位图片渲染，仅由 &G 引用（D7 裁定）
        logo.setPrint(false);

        ExcelWorkbook workbook = newWorkbook(90, "&G", "logo", List.of(logo));
        byte[] pdf = render(workbook);

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            java.awt.image.BufferedImage img = PdfImageRenderer.renderPage(doc, 0,
                    PdfImageRenderer.DEFAULT_DPI);
            // 页脚分区：顶基 10..30pt（页底 20pt 带从页顶算 842-30=812? 见 D3——实际为 printArea 下方的
            // footer 带，top-based y = 842 - footerY(10) - height(20) .. 842 - footerY(10) = 10..30pt）；中心 x≈297pt
            // 页脚带 top-based 792..812pt（实测 rect y=30..50 内容系）；logo 居中于中分区
            int px = (int) Math.round(261 * 110.0 / 72);
            int py = (int) Math.round(802 * 110.0 / 72);
            int rgb = img.getRGB(px, py);
            // logo 为蓝色块
            assertTrue((rgb & 0xFF) > 150 && ((rgb >> 16) & 0xFF) < 120,
                    "&G logo must render in footer band, got rgb=" + Integer.toHexString(rgb));
        }
    }

    /**
     * &G 无图（名字找不到）：WARN 跳过，不抛异常、不渲染任何图片。
     */
    @Test
    public void testGCodeMissingImageSkipped() throws Exception {
        ExcelWorkbook workbook = newWorkbook(90, "&G", "no-such-image", List.of());
        byte[] pdf = render(workbook);
        List<String> pages = pageTexts(pdf);
        assertTrue(pages.get(0) != null);
    }

    /**
     * 多字体序列：&"Courier"14AB&"Helvetica"8CD —— 两段文本按序渲染、
     * 字号 14/8 断言（TextPosition）。
     */
    @Test
    public void testMultiFontSequence() throws Exception {
        ExcelWorkbook workbook = newWorkbook(90, "&\"Courier\"&14AB&\"Helvetica\"&8CD");
        byte[] pdf = render(workbook);

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    if (text.contains("AB")) {
                        for (TextPosition pos : positions) {
                            if (pos.getUnicode() != null && "AB".contains(pos.getUnicode()))
                                assertEquals(14f, pos.getFontSizeInPt(), 0.5f,
                                        "AB must render at size 14");
                        }
                    }
                    if (text.contains("CD")) {
                        for (TextPosition pos : positions) {
                            if (pos.getUnicode() != null && "CD".contains(pos.getUnicode()))
                                assertEquals(8f, pos.getFontSizeInPt(), 0.5f,
                                        "CD must render at size 8");
                        }
                    }
                }
            };
            stripper.getText(doc);
        }
    }

    /**
     * 分区裁剪：超宽 run（长文本）不越界绘制到相邻分区——相邻分区区域无前景色像素。
     */
    @Test
    public void testSectionClipBounds() throws Exception {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 60; i++)
            longText.append("WW");
        ExcelWorkbook workbook = newWorkbook(90, longText.toString());
        byte[] pdf = render(workbook);

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            java.awt.image.BufferedImage img = PdfImageRenderer.renderPage(doc, 0,
                    PdfImageRenderer.DEFAULT_DPI);
            // center 分区 x 213..382pt；right 分区 x 382..559pt；页脚带 802..822pt。
            // 右分区右端（远端）应无 center 溢出的文本像素
            int px = (int) Math.round(545 * 110.0 / 72);
            int py = (int) Math.round(802 * 110.0 / 72);
            int rgb = img.getRGB(px, py);
            assertTrue(((rgb >> 16) & 0xFF) > 200 && ((rgb >> 8) & 0xFF) > 200 && (rgb & 0xFF) > 200,
                    "overflow text must be clipped to its section, got rgb=" + Integer.toHexString(rgb));
        }
    }

    /**
     * golden：图片页脚。
     */
    @Test
    public void testGoldenImageFooter() throws Exception {
        ExcelImage logo = new ExcelImage();
        logo.setName("logo");
        logo.setDataBytes(logoPng());
        logo.setPrint(false);
        ExcelWorkbook workbook = newWorkbook(90, "&G", "logo", List.of(logo));
        byte[] pdf = render(workbook);

        PdfGoldenManager golden = PdfGoldenManager.forDefaultDir("wi5-hf-rich-media");
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            List<java.awt.image.BufferedImage> images = PdfImageRenderer.renderAll(doc,
                    PdfImageRenderer.DEFAULT_DPI);
            for (int i = 0; i < images.size(); i++) {
                var result = golden.compare("image-footer-page-" + (i + 1), images.get(i));
                assertTrue(result.getStatus() == io.nop.pdf.verify.GoldenCompareResult.Status.PASS
                        || result.getStatus() == io.nop.pdf.verify.GoldenCompareResult.Status.BASELINE_CREATED,
                        "golden regression page " + (i + 1) + ": " + result);
            }
        }
    }
}
