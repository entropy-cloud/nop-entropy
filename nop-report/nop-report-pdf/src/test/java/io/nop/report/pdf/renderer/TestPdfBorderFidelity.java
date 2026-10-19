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
import io.nop.office.model.constants.OfficeLineStyle;
import io.nop.excel.model.ExcelBorderStyle;
import io.nop.office.model.OfficeBorderStyle;
import io.nop.pdf.verify.GoldenCompareResult;
import io.nop.pdf.verify.PdfGoldenManager;
import io.nop.pdf.verify.PdfImageRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI8：边框线宽/线型保真——weight 驱动线宽（thin=1/medium=2/thick=3pt）、
 * DOUBLE 间距比例、虚线节距比例化、缩放线宽 CTM 等比。
 */
public class TestPdfBorderFidelity extends JunitBaseTestCase {

    /** 边框样式（weight 显式设置以模拟解析期结果） */
    private static ExcelBorderStyle border(OfficeLineStyle type, int weight) {
        ExcelBorderStyle border = new ExcelBorderStyle();
        border.setType(type);
        border.setWeight(weight);
        border.setColor("000000");
        return border;
    }

    /** 单页 2x2 单元格（各 60x20pt），每个单元格指定边框样式 */
    private ExcelWorkbook newWorkbook(ExcelBorderStyle border) {
        ExcelWorkbook workbook = new ExcelWorkbook();

        ExcelStyle style = new ExcelStyle();
        style.setId("border");
        style.setTopBorder(border);
        style.setBottomBorder(border);
        style.setLeftBorder(border);
        style.setRightBorder(border);
        workbook.addStyle(style);

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

        for (int r = 0; r < 4; r++) {
            ExcelRow row = new ExcelRow();
            row.setHeight(20.0);
            for (int c = 0; c < 4; c++) {
                ExcelCell cell = new ExcelCell();
                cell.setStyleId("border");
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

    /** 提取内容流中所有 setLineWidth（w 操作符）的值（pt，user space） */
    private List<Float> lineWidths(byte[] pdf) throws Exception {
        List<Float> ret = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            for (var page : doc.getPages()) {
                PDFStreamParser parser = new PDFStreamParser(page);
                var tokens = parser.parse();
                for (int i = 0; i < tokens.size(); i++) {
                    if (tokens.get(i) instanceof COSNumber && "w".equals(operatorAt(tokens, i + 1))) {
                        ret.add(((COSNumber) tokens.get(i)).floatValue());
                    }
                }
            }
        }
        return ret;
    }

    private String operatorAt(java.util.List<Object> tokens, int idx) {
        if (idx < tokens.size() && tokens.get(idx) instanceof org.apache.pdfbox.contentstream.operator.Operator op)
            return op.getName();
        return "";
    }

    /**
     * (a) 主断言：内容流 w 操作符精确值——thin=1/medium=2/thick=3pt。
     */
    @Test
    public void testWeightDrivenLineWidths() throws Exception {
        List<Float> thin = lineWidths(render(newWorkbook(border(OfficeLineStyle.SINGLE, 1))));
        List<Float> medium = lineWidths(render(newWorkbook(border(OfficeLineStyle.MEDIUM, 2))));
        List<Float> thick = lineWidths(render(newWorkbook(border(OfficeLineStyle.THICK, 3))));
        assertTrue(!thin.isEmpty(), "border strokes must exist");
        for (float w : thin)
            assertEquals(1f, w, 0.01f, "thin border must be 1pt");
        for (float w : medium)
            assertEquals(2f, w, 0.01f, "medium border must be 2pt");
        for (float w : thick)
            assertEquals(3f, w, 0.01f, "thick border must be 3pt");
    }

    /**
     * 程序化样式（未 setWeight）：回退类型规范权重（medium→2）。
     */
    @Test
    public void testWeightFallbackToTypeDefault() throws Exception {
        ExcelBorderStyle border = new ExcelBorderStyle();
        border.setType(OfficeLineStyle.MEDIUM);
        border.setColor("000000");
        List<Float> widths = lineWidths(render(newWorkbook(border)));
        assertTrue(!widths.isEmpty());
        for (float w : widths)
            assertEquals(2f, w, 0.01f, "type fallback must use OfficeLineStyle weight (medium=2)");
    }

    /**
     * (b) 像素层严格序：110dpi 渲染 thin < medium < thick 扫描线墨迹宽度。
     */
    @Test
    public void testPixelWidthOrdering() throws Exception {
        float thin = pixelBorderWidth(border(OfficeLineStyle.SINGLE, 1));
        float medium = pixelBorderWidth(border(OfficeLineStyle.MEDIUM, 2));
        float thick = pixelBorderWidth(border(OfficeLineStyle.THICK, 3));
        assertTrue(thin > 0 && thin < medium && medium < thick,
                "pixel widths must strictly increase: thin=" + thin + ", medium=" + medium + ", thick=" + thick);
    }

    private double borderExtent(ExcelBorderStyle border) throws Exception {
        byte[] pdf = render(newWorkbook(border));
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            BufferedImage img = io.nop.pdf.verify.PdfImageRenderer.renderPage(doc, 0,
                    PdfImageRenderer.DEFAULT_DPI);
            double s = 110.0 / 72;
            int px = (int) (66 * s);
            int minY = -1, maxY = -1;
            for (int py = (int) (45 * s); py < (int) (75 * s); py++) {
                int rgb = img.getRGB(px, py);
                if (((rgb >> 16) & 0xFF) < 128) {
                    if (minY < 0) minY = py;
                    maxY = py;
                }
            }
            return minY < 0 ? 0 : (maxY - minY) / s;
        }
    }

    private float pixelBorderWidth(ExcelBorderStyle border) throws Exception {
        byte[] pdf = render(newWorkbook(border));
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            BufferedImage img = io.nop.pdf.verify.PdfImageRenderer.renderPage(doc, 0,
                    PdfImageRenderer.DEFAULT_DPI);
            double s = 110.0 / 72;
            // 水平边框在行 0 底部 y=60pt：固定列（66pt，首单元格中心）垂直扫描墨迹行数=线厚
            int px = (int) (66 * s);
            int run = 0, maxRun = 0;
            for (int py = (int) (45 * s); py < (int) (75 * s); py++) {
                int rgb = img.getRGB(px, py);
                if (((rgb >> 16) & 0xFF) < 128) {
                    run++;
                    maxRun = Math.max(maxRun, run);
                } else {
                    run = 0;
                }
            }
            return maxRun;
        }
    }

    /**
     * (c) DOUBLE：线宽=权重（1），双线偏移=±lineWidth（比例化）——w 操作符=1 且双线间距像素带>thin。
     */
    @Test
    public void testDoubleBorderSpacingScales() throws Exception {
        List<Float> widths = lineWidths(render(newWorkbook(border(OfficeLineStyle.DOUBLE, 1))));
        assertTrue(!widths.isEmpty());
        for (float w : widths)
            assertEquals(1f, w, 0.01f, "double border lines drawn at weight width");
        // double 总外扩范围（两线外缘间距）> single 单线厚：扫描垂直带内墨迹总跨度
        double doubleExtent = borderExtent(border(OfficeLineStyle.DOUBLE, 1));
        double thinExtent = borderExtent(border(OfficeLineStyle.SINGLE, 1));
        assertTrue(doubleExtent > thinExtent,
                "double border total extent must exceed single: " + doubleExtent + " vs " + thinExtent);
    }

    /**
     * (d) DASHED 节距随线宽比例：medium dashed 的 dash 数组=3*2=6。
     */
    @Test
    public void testDashPatternScalesWithWidth() throws Exception {
        byte[] pdf = render(newWorkbook(border(OfficeLineStyle.DASHED, 2)));
        boolean found = false;
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            for (var page : doc.getPages()) {
                PDFStreamParser parser = new PDFStreamParser(page);
                var tokens = parser.parse();
                for (int i = 0; i < tokens.size(); i++) {
                    if (tokens.get(i) instanceof org.apache.pdfbox.contentstream.operator.Operator op
                            && "d".equals(op.getName()) && i >= 2
                            && tokens.get(i - 2) instanceof COSArray dash
                            && dash.size() >= 1 && dash.getObject(0) instanceof COSNumber n) {
                        assertEquals(6f, n.floatValue(), 0.01f,
                                "dashed medium dash length must be 3*2=6");
                        found = true;
                    }
                    // dash 数组是内容流中唯一的 COSArray 嵌套结构：[外层[内层...]]
                    if (tokens.get(i) instanceof COSArray outer && outer.size() >= 1
                            && outer.getObject(0) instanceof COSArray dash) {
                        if (dash.size() >= 1 && dash.getObject(0) instanceof COSNumber n) {
                            assertEquals(6f, n.floatValue(), 0.01f,
                                    "dashed medium dash length must be 3*2=6");
                            found = true;
                        }
                    }
                }
            }
        }
        assertTrue(found, "dash pattern must be present");
    }

    /**
     * (e) 缩放线宽：显式 setScale(50) 下 thin 线宽 w 操作符仍=1（user space 不变，
     * 设备呈现随 CTM 减半）——300dpi 渲染断言 scaled < unscaled。
     */
    @Test
    public void testScaledStrokeWidth() throws Exception {
        ExcelWorkbook scaled = newWorkbook(border(OfficeLineStyle.SINGLE, 1));
        scaled.getSheets().get(0).getPageSetup().setScale(50);
        List<Float> widths = lineWidths(render(scaled));
        assertTrue(!widths.isEmpty());
        for (float w : widths)
            assertEquals(1f, w, 0.01f, "user-space line width unchanged by scale (CTM handles device scaling)");

        // 像素层：300dpi 渲染对比 scaled vs unscaled 的 thin 墨迹宽度
        float unscaled = pixelWidthAt(render(newWorkbook(border(OfficeLineStyle.SINGLE, 1))), 300);
        float scaledW = pixelWidthAt(render(scaled), 300);
        assertTrue(scaledW > 0 && scaledW < unscaled,
                "scaled stroke must be thinner on device: scaled=" + scaledW + ", unscaled=" + unscaled);
    }

    private float pixelWidthAt(byte[] pdf, int dpi) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            BufferedImage img = io.nop.pdf.verify.PdfImageRenderer.renderPage(doc, 0, dpi);
            double s = dpi / 72.0;
            // 垂直扫描水平边框厚度（scaled 页边框位置随缩放移动——扫描整个上半页取最大游程）
            int px = (int) (66 * s);
            int run = 0, maxRun = 0;
            for (int py = 0; py < img.getHeight(); py++) {
                int rgb = img.getRGB(px, py);
                if (((rgb >> 16) & 0xFF) < 128) {
                    run++;
                    maxRun = Math.max(maxRun, run);
                } else {
                    run = 0;
                }
            }
            return maxRun;
        }
    }

    /**
     * golden：thin/medium/thick/DOUBLE 四张。
     */
    @Test
    public void testGoldenBorders() throws Exception {
        PdfGoldenManager golden = PdfGoldenManager.forDefaultDir("wi8-border-fidelity");
        assertGolden(golden, "thin", render(newWorkbook(border(OfficeLineStyle.SINGLE, 1))));
        assertGolden(golden, "medium", render(newWorkbook(border(OfficeLineStyle.MEDIUM, 2))));
        assertGolden(golden, "thick", render(newWorkbook(border(OfficeLineStyle.THICK, 3))));
        assertGolden(golden, "double", render(newWorkbook(border(OfficeLineStyle.DOUBLE, 1))));
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
