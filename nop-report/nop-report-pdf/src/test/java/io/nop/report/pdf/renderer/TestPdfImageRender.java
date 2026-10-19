/**
 * Copyright (c) 2017-2024 Nop Platform.
 */
package io.nop.report.pdf.renderer;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.bytes.ByteString;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelClientAnchor;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.ExcelPageMargins;
import io.nop.excel.model.ExcelPageSetup;
import io.nop.excel.model.ExcelRow;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.pdf.verify.GoldenCompareResult;
import io.nop.pdf.verify.PdfGoldenManager;
import io.nop.pdf.verify.PdfImageRenderer;
import io.nop.pdf.verify.RegionProbe;
import io.nop.report.pdf.renderer.PdfRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI6：图片嵌入保真——尺寸换算、rotateDegree 旋转、alpha 透明、缓存复用、缺图/损坏占位、
 * print=false、页面裁剪。锚点几何以 pt 直接构造（EMU→pt 换算在 DrawingParser 解析期，
 * 编程模板 dx/dy 即 pt）。
 */
public class TestPdfImageRender extends JunitBaseTestCase {
    /** 4 象限不对称图：TL红/TR绿/BL蓝/BR黄（旋转方向探针） */
    private byte[] quadrantPng() throws Exception {
        BufferedImage img = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        drawQuad(img, 0, 0, 0xFFE02020);
        drawQuad(img, 20, 0, 0xFF20E020);
        drawQuad(img, 0, 20, 0xFF2030E0);
        drawQuad(img, 20, 20, 0xFFE0E020);
        return toPng(img);
    }

    /** 上半透明/下半不透明红（alpha 探针） */
    private byte[] halfTransparentPng() throws Exception {
        BufferedImage img = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        for (int y = 20; y < 40; y++)
            for (int x = 0; x < 40; x++)
                img.setRGB(x, y, 0xFFE02020);
        return toPng(img);
    }

    private void drawQuad(BufferedImage img, int x0, int y0, int argb) {
        for (int y = y0; y < y0 + 20; y++)
            for (int x = x0; x < x0 + 20; x++)
                img.setRGB(x, y, argb);
    }

    private byte[] toPng(BufferedImage img) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    private SheetBuilder newBuilder() {
        return new SheetBuilder();
    }

    /** 4 列 x 12 行，行高 20pt 列宽 60pt；printArea 顶 40pt */
    class SheetBuilder {
        final ExcelWorkbook workbook = new ExcelWorkbook();
        final io.nop.excel.model.ExcelSheet sheet = new io.nop.excel.model.ExcelSheet();

        SheetBuilder() {
            ExcelStyle style = new ExcelStyle();
            style.setId("text");
            ExcelFont font = new ExcelFont();
            font.setFontName("Helvetica");
            style.setFont(font);
            workbook.addStyle(style);

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
                io.nop.excel.model.ExcelRow row = new ExcelRow();
                row.setHeight(20.0);
                for (int c = 0; c < 4; c++) {
                    ExcelCell cell = new ExcelCell();
                    row.getCells().add(cell);
                }
                sheet.getTable().getRows().add(row);
            }
        }

        SheetBuilder fillStyle() {
            ExcelStyle style = new ExcelStyle();
            style.setId("yellow");
            style.setFillFgColor("FFFF00");
            ExcelFont font = new ExcelFont();
            font.setFontName("Helvetica");
            style.setFont(font);
            workbook.addStyle(style);
            return this;
        }

        SheetBuilder fillCells(int rowFrom, int rowTo) {
            for (int r = rowFrom; r <= rowTo; r++) {
                io.nop.excel.model.ExcelRow row = sheet.getTable().getRows().get(r);
                for (int c = 0; c < 4; c++) {
                    ExcelCell cell = (ExcelCell) row.getCells().get(c);
                    cell.setStyleId("yellow");
                }
            }
            return this;
        }

        /** 2x2 单元格锚点（120x40pt），offset (col1,row1) */
        SheetBuilder image(byte[] png, int col1, int row1, int colSpan, int rowSpan, Double rotate) {
            ExcelImage image = new ExcelImage();
            image.setDataBytes(png);
            image.setPrint(true);
            if (rotate != null)
                image.setRotateDegree(rotate);
            ExcelClientAnchor anchor = new ExcelClientAnchor();
            anchor.setCol1(col1);
            anchor.setRow1(row1);
            anchor.setColDelta(colSpan);
            anchor.setRowDelta(rowSpan);
            image.setAnchor(anchor);
            image.calcSize(sheet);
            sheet.setImages(java.util.Collections.singletonList(image));
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

    private BufferedImage renderFirstPage(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return PdfImageRenderer.renderPage(doc, 0, PdfImageRenderer.DEFAULT_DPI);
        }
    }

    private static final double SCALE = 110.0 / 72;

    private Color probe(BufferedImage img, double ptX, double ptY) {
        int px = (int) Math.round(ptX * SCALE);
        int py = (int) Math.round(ptY * SCALE);
        return new Color(img.getRGB(px, py));
    }

    private boolean isWhitish(Color c) {
        return c.getRed() > 245 && c.getGreen() > 245 && c.getBlue() > 245;
    }

    private boolean isNear(Color c, int rgb, int tol) {
        return Math.abs(c.getRed() - ((rgb >> 16) & 0xFF)) <= tol
                && Math.abs(c.getGreen() - ((rgb >> 8) & 0xFF)) <= tol
                && Math.abs(c.getBlue() - (rgb & 0xFF)) <= tol;
    }

    /**
     * (a) 尺寸换算：2x2 单元格锚点图片渲染于锚点几何（列 0-2 行 0-2 → 打印区内 120x40pt），
     * 图片外区域保持空白。
     */
    @Test
    public void testImageAtAnchoredGeometry() throws Exception {
        SheetBuilder b = newBuilder().image(quadrantPng(), 0, 0, 2, 2, null);
        BufferedImage img = renderFirstPage(b.toPdf());
        // 打印区原点 (36, 40)pt；图片中心 = 36+60, 40+20
        Color center = probe(img, 36 + 60, 40 + 20);
        assertFalse(isWhitish(center), "image interior must be painted");
        // 图片外（右侧 2 列）：36+120+30
        Color outside = probe(img, 36 + 120 + 30, 40 + 20);
        assertTrue(isWhitish(outside), "area beyond anchored image must stay blank");
    }

    /**
     * 旋转方向探针：左红右蓝，rotateDegree=90（顺时针）后左侧红色出现在顶部。
     */
    @Test
    public void testRotate90Clockwise() throws Exception {
        SheetBuilder b = newBuilder().image(quadrantPng(), 0, 0, 2, 2, 90.0);
        BufferedImage img = renderFirstPage(b.toPdf());
        // 图片中心 (96, 60)pt；旋转 90 后占位 40 宽 x 120 高，取四象限内部点
        // （中心 (96,60)，x 76..116、y 0..120；象限中心 ±10pt）
        Color newTL = probe(img, 96 - 10, 60 - 30);
        Color newTR = probe(img, 96 + 10, 60 - 30);
        Color newBL = probe(img, 96 - 10, 60 + 30);
        Color newBR = probe(img, 96 + 10, 60 + 30);
        // 顺时针 90：新TL=旧BL(蓝)、新TR=旧TL(红)、新BL=旧BR(黄)、新BR=旧TR(绿)
        assertTrue(isNear(newTL, 0x2030E0, 60), "old bottom-left(blue) must become new top-left, got " + newTL);
        assertTrue(isNear(newTR, 0xE02020, 60), "old top-left(red) must become new top-right, got " + newTR);
        assertTrue(isNear(newBL, 0xE0E020, 60), "old bottom-right(yellow) must become new bottom-left, got " + newBL);
        assertTrue(isNear(newBR, 0x20E020, 60), "old top-right(green) must become new bottom-right, got " + newBR);
    }

    /**
     * alpha 透明：上透明下红的 PNG 叠在黄色填充单元格上，上半透出黄色、下半为红色。
     */
    @Test
    public void testAlphaTransparency() throws Exception {
        SheetBuilder b = newBuilder().fillStyle().fillCells(0, 3)
                .image(halfTransparentPng(), 0, 0, 2, 2, null);
        BufferedImage img = renderFirstPage(b.toPdf());
        Color top = probe(img, 36 + 60, 40 + 10);
        Color bottom = probe(img, 36 + 60, 40 + 30);
        assertTrue(isNear(top, 0xFFFF00, 40), "transparent half must show yellow background, got " + top);
        assertTrue(isNear(bottom, 0xE02020, 60), "opaque half must show image red, got " + bottom);
    }

    /**
     * 缺图（无数据）与损坏图：均绘制浅灰占位，不静默跳过、不抛异常。
     */
    @Test
    public void testMissingAndCorruptImagePlaceholders() throws Exception {
        // 缺图
        SheetBuilder missing = newBuilder().image(null, 0, 0, 2, 2, null);
        BufferedImage img1 = renderFirstPage(missing.toPdf());
        Color p1 = probe(img1, 36 + 60, 40 + 20);
        assertTrue(isNear(p1, 0xD3D3D3, 20), "missing image must draw gray placeholder, got " + p1);

        // 损坏图
        SheetBuilder corrupt = newBuilder().image("corrupt-bytes".getBytes(), 0, 0, 2, 2, null);
        BufferedImage img2 = renderFirstPage(corrupt.toPdf());
        Color p2 = probe(img2, 36 + 60, 40 + 20);
        assertTrue(isNear(p2, 0xD3D3D3, 20), "corrupt image must draw gray placeholder, got " + p2);
    }

    /**
     * print=false：套打图片导出 PDF 时不出现。
     */
    @Test
    public void testPrintFalseHidden() throws Exception {
        SheetBuilder b = newBuilder();
        ExcelImage image = new ExcelImage();
        image.setDataBytes(quadrantPng());
        image.setPrint(false);
        ExcelClientAnchor anchor = new ExcelClientAnchor();
        anchor.setCol1(0);
        anchor.setRow1(0);
        anchor.setColDelta(2);
        anchor.setRowDelta(2);
        image.setAnchor(anchor);
        image.calcSize(b.sheet);
        b.sheet.setImages(java.util.Collections.singletonList(image));

        BufferedImage img = renderFirstPage(b.toPdf());
        Color center = probe(img, 36 + 60, 40 + 20);
        assertTrue(isWhitish(center), "print=false image must be hidden in PDF export");
    }

    /**
     * 越界图片：锚点延伸出页面右缘——不抛异常，页面裁剪（D4）。
     */
    @Test
    public void testOutOfBoundsImageClipped() throws Exception {
        SheetBuilder b = newBuilder().image(quadrantPng(), 2, 0, 6, 2, null);
        BufferedImage img = renderFirstPage(b.toPdf());
        // 页宽 595pt：打印区内 36+120=156pt 起的图片被页缘裁剪
        Color inside = probe(img, 36 + 130, 40 + 20);
        assertFalse(isWhitish(inside), "image part inside page must render");
    }

    /**
     * 缓存复用：同一图片字节的 PDImageXObject per-document 复用同一实例。
     */
    @Test
    public void testImageCacheReuse() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PdfRenderer renderer = new PdfRenderer(doc);
            byte[] png = quadrantPng();
            ByteString data = ByteString.of(png);
            assertSame(renderer.getImage(data), renderer.getImage(data),
                    "same image bytes must reuse the cached PDImageXObject");
        }
    }

    /**
     * 多图渲染回归：12 张图片一页渲染不 OOM、不异常。
     */
    @Test
    public void testManyImagesRender() throws Exception {
        SheetBuilder b = newBuilder();
        java.util.List<ExcelImage> images = new java.util.ArrayList<>();
        for (int r = 0; r < 12; r++) {
            ExcelImage image = new ExcelImage();
            image.setDataBytes(quadrantPng());
            image.setPrint(true);
            image.setName("img-" + r);
            ExcelClientAnchor anchor = new ExcelClientAnchor();
            anchor.setCol1(r % 4);
            anchor.setRow1(r % 12);
            anchor.setColDelta(1);
            anchor.setRowDelta(1);
            image.setAnchor(anchor);
            image.calcSize(b.sheet);
            images.add(image);
        }
        b.sheet.setImages(images);
        BufferedImage img = renderFirstPage(b.toPdf());
        assertNotNull(img);
    }

    /**
     * golden：旋转 90、alpha 透明、锚点几何。
     */
    @Test
    public void testGoldenSnapshots() throws Exception {
        PdfGoldenManager golden = PdfGoldenManager.forDefaultDir("wi6-image-fidelity");
        assertGolden(golden, "rotate-90", newBuilder().image(quadrantPng(), 0, 0, 2, 2, 90.0).toPdf());
        assertGolden(golden, "alpha", newBuilder().fillStyle().fillCells(0, 3)
                .image(halfTransparentPng(), 0, 0, 2, 2, null).toPdf());
        assertGolden(golden, "geometry", newBuilder().image(quadrantPng(), 0, 0, 2, 2, null).toPdf());
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
