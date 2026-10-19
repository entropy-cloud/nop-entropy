package io.nop.report.pdf.renderer;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.ExcelPageMargins;
import io.nop.excel.model.ExcelPageSetup;
import io.nop.excel.model.ExcelRichText;
import io.nop.excel.model.ExcelRichTextPart;
import io.nop.excel.model.ExcelRow;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.pdf.verify.GoldenCompareResult;
import io.nop.pdf.verify.PdfGoldenManager;
import io.nop.pdf.verify.PdfImageRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI11：单元格富文本 run（逐 run 字体/字号/颜色）、竖排/旋转文本（ECMA-376
 * textRotation 语义）、numberFormat 直路回退（含 Excel 串行日期）。
 */
public class TestPdfRichTextRender extends JunitBaseTestCase {

    private io.nop.excel.model.ExcelSheet sheet;

    // ========== (a) 富文本 run ==========

    /**
     * 双 run 异字体/字号/颜色：TextPosition 证明逐 run 字体生效（接线验证），
     * 内容流 sc 证明逐 run 颜色生效。
     */
    @Test
    public void testRichRunsFontAndColor() throws Exception {
        ExcelWorkbook wb = newWorkbook();
        ExcelStyle style = new ExcelStyle();
        style.setId("rich");
        style.setFont(font("Helvetica", false, false, 10f, "0000FF"));
        wb.addStyle(style);

        ExcelCell cell = new ExcelCell();
        cell.setValue("Bold plain");
        cell.setStyleId("rich");
        ExcelRichText rich = new ExcelRichText();
        rich.setParts(new ArrayList<>());
        // run1：Helvetica bold 红色
        ExcelRichTextPart p1 = new ExcelRichTextPart();
        p1.setText("Bold ");
        p1.setFont(font("Helvetica", true, false, 14f, "FF0000"));
        rich.getParts().add(p1);
        // run2：无 font → 回退单元格样式字体/颜色
        ExcelRichTextPart p2 = new ExcelRichTextPart();
        p2.setText("plain");
        rich.getParts().add(p2);
        cell.setRichText(rich);

        ExcelRow row = sheet.getTable().getRows().get(0);
        row.internalAddCell(cell);
        byte[] pdf = render(wb);

        // 逐 run 字体（接线验证）：页面出现至少两种字体，其一为真实粗体变体
        Set<String> fontNames = new HashSet<>();
        Set<Float> sizes = new HashSet<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) throws java.io.IOException {
                    super.writeString(text, positions);
                    for (TextPosition pos : positions) {
                        if (pos.getFont() != null && pos.getFont().getName() != null)
                            fontNames.add(pos.getFont().getName());
                        sizes.add(pos.getFontSizeInPt());
                    }
                }
            };
            String text = stripper.getText(doc);
            assertTrue(text.contains("Bold") && text.contains("plain"),
                    "rich text must be extractable: " + text);
        }
        assertTrue(fontNames.stream().anyMatch(n -> n.contains("Helvetica-Bold")),
                "run1 must use real bold variant, fonts=" + fontNames);
        assertTrue(fontNames.size() >= 2, "two runs with different fonts must both render");
        assertTrue(sizes.stream().anyMatch(s -> Math.abs(s - 14f) < 0.6f),
                "run1 must render at size 14, sizes=" + sizes);

        // 逐 run 颜色：sc 操作符含红（run1）与蓝（run2 回退样式色）
        List<float[]> scColors = nonStrokingColors(pdf);
        assertTrue(containsColor(scColors, 1f, 0f, 0f), "run1 color red: " + scColors);
        assertTrue(containsColor(scColors, 0f, 0f, 1f), "run2 falls back to style color blue: " + scColors);
    }

    // ========== (b)(c) 旋转/竖排 ==========

    /**
     * rotate 90 → Tm (0,1,-1,0)（CCW 90）；rotate 135 → CW45 → Tm (≈.7071,-.7071,.7071,.7071)。
     */
    @Test
    public void testRotateMatrices() throws Exception {
        ExcelWorkbook wb = newWorkbook();
        addSimpleStyle(wb, "rot90", 90);
        addSimpleStyle(wb, "rot135", 135);
        textCell(wb, 0, 0, "UP", "rot90");
        textCell(wb, 0, 1, "DN", "rot135");
        byte[] pdf = render(wb);

        List<float[]> tms = textMatrices(pdf);
        boolean found90 = false, found135 = false;
        for (float[] tm : tms) {
            if (Math.abs(tm[0]) < 0.01f && Math.abs(tm[1] - 1f) < 0.01f
                    && Math.abs(tm[2] + 1f) < 0.01f && Math.abs(tm[3]) < 0.01f)
                found90 = true;
            if (Math.abs(tm[0] - 0.7071f) < 0.01f && Math.abs(tm[1] + 0.7071f) < 0.01f
                    && Math.abs(tm[2] - 0.7071f) < 0.01f && Math.abs(tm[3] - 0.7071f) < 0.01f)
                found135 = true;
        }
        assertTrue(found90, "rotate=90 must be CCW 90 (Tm 0,1,-1,0), tms=" + tms);
        assertTrue(found135, "rotate=135 must be CW45 (Tm .7071,-.7071,.7071,.7071)");
    }

    /**
     * rotate 255 竖排：逐字符独立绘制，自上而下提取有序。
     */
    @Test
    public void testVerticalText255() throws Exception {
        ExcelWorkbook wb = newWorkbook();
        addSimpleStyle(wb, "vert", 255);
        textCell(wb, 0, 0, "AB", "vert");
        byte[] pdf = render(wb);

        boolean found = false;
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc).replaceAll("\\s+", "");
            assertTrue(text.contains("A") && text.contains("B"), "vertical chars must extract: " + text);
            found = text.indexOf('A') < text.indexOf('B');
        }
        assertTrue(found, "vertical stack must extract top-down (A before B)");
    }

    // ========== (d)(e) numberFormat ==========

    @Test
    public void testNumberFormatDirectPath() throws Exception {
        ExcelWorkbook wb = newWorkbook();
        addNumFmtStyle(wb, "money", "#,##0.00");
        addNumFmtStyle(wb, "pct", "0.00%");
        addNumFmtStyle(wb, "date", "yyyy-mm-dd");
        addNumFmtStyle(wb, "ssn", "000-00-0000");
        numCell(wb, 0, 0, 1234.5, "money");
        numCell(wb, 0, 1, 0.25, "pct");
        numCell(wb, 1, 0, 45000.0, "date");
        numCell(wb, 1, 1, 123456789, "ssn");
        // (d) 回退：不可转换组合（String 值 + 数字格式）原样
        addNumFmtStyle(wb, "strnum", "#,##0.00");
        ExcelCell strCell = new ExcelCell();
        strCell.setValue("abc");
        strCell.setStyleId("strnum");
        sheet.getTable().getRows().get(2).internalAddCell(strCell);
        // (e) General 不变形
        // (e) 显式无格式样式（defaultStyle=第一个样式是平台既有语义，不能用无 styleId 表达"无格式"）
        addNumFmtStyle(wb, "raw", null);
        numCell(wb, 2, 1, 1234.5, "raw");
        byte[] pdf = render(wb);

        String text = TestPdfRenderDefects.normalize(TestPdfRenderDefects.allText(pdf));
        assertTrue(text.contains("1,234.50"), "thousands separator format: " + text);
        assertTrue(text.contains("25.00%"), "percent format: " + text);
        assertTrue(text.contains("2023-03-15"),
                "Excel serial 45000 must convert to serial date (not epoch 1970): " + text);
        assertTrue(text.contains("123-45-6789"), "SSN special format must keep working: " + text);
        assertTrue(text.contains("abc"), "unformattable combo must fall back to raw");
        assertTrue(text.contains("1234.5"), "no format → raw value unchanged");
        assertFalse(text.contains("1969") || text.contains("1970"), "no epoch garbage: " + text);
    }

    /**
     * Temporal 值（LocalDate）+ 日期格式。
     */
    @Test
    public void testLocalDateValue() throws Exception {
        ExcelWorkbook wb = newWorkbook();
        addNumFmtStyle(wb, "date", "yyyy-mm-dd");
        ExcelCell cell = new ExcelCell();
        cell.setValue(LocalDate.of(2024, 2, 29));
        cell.setStyleId("date");
        sheet.getTable().getRows().get(0).internalAddCell(cell);
        byte[] pdf = render(wb);
        String text = TestPdfRenderDefects.normalize(TestPdfRenderDefects.allText(pdf));
        assertTrue(text.contains("2024-02-29"), "LocalDate must format via date pattern: " + text);
    }

    // ========== (f) golden ==========

    @Test
    public void testGoldenRichTextPage() throws Exception {
        ExcelWorkbook wb = newWorkbook();
        ExcelStyle richStyle = new ExcelStyle();
        richStyle.setId("rich");
        richStyle.setFont(font("Helvetica", false, false, 10f, "0000FF"));
        wb.addStyle(richStyle);
        ExcelCell cell = new ExcelCell();
        cell.setValue("Bold plain");
        cell.setStyleId("rich");
        ExcelRichText rich = new ExcelRichText();
        rich.setParts(new ArrayList<>());
        ExcelRichTextPart p1 = new ExcelRichTextPart();
        p1.setText("Bold ");
        p1.setFont(font("Helvetica", true, false, 14f, "FF0000"));
        rich.getParts().add(p1);
        ExcelRichTextPart p2 = new ExcelRichTextPart();
        p2.setText("plain");
        rich.getParts().add(p2);
        cell.setRichText(rich);
        sheet.getTable().getRows().get(0).internalAddCell(cell);

        addSimpleStyle(wb, "rot90", 90);
        addSimpleStyle(wb, "vert", 255);
        textCell(wb, 0, 1, "UP", "rot90");
        textCell(wb, 1, 0, "AB", "vert");
        addNumFmtStyle(wb, "money", "#,##0.00");
        numCell(wb, 1, 1, 1234.5, "money");
        byte[] pdf = render(wb);

        PdfGoldenManager golden = PdfGoldenManager.forDefaultDir("wi11-rich-text");
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            List<BufferedImage> images = PdfImageRenderer.renderAll(doc, PdfImageRenderer.DEFAULT_DPI);
            for (int i = 0; i < images.size(); i++) {
                GoldenCompareResult result = golden.compare("rich-page-" + (i + 1), images.get(i));
                assertTrue(result.getStatus() == GoldenCompareResult.Status.PASS
                        || result.getStatus() == GoldenCompareResult.Status.BASELINE_CREATED,
                        "golden regression on page " + (i + 1) + ": " + result);
            }
        }
    }

    // ========== 夹具与内容流解析 ==========

    private List<float[]> nonStrokingColors(byte[] pdf) throws Exception {
        List<float[]> ret = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            for (var page : doc.getPages()) {
                PDFStreamParser parser = new PDFStreamParser(page);
                List<Object> operands = new ArrayList<>();
                for (Object token : parser.parse()) {
                    if (token instanceof Operator op) {
                        if (("sc".equals(op.getName()) || "rg".equals(op.getName()))
                                && operands.size() >= 3)
                            ret.add(new float[]{((COSNumber) operands.get(0)).floatValue(),
                                    ((COSNumber) operands.get(1)).floatValue(),
                                    ((COSNumber) operands.get(2)).floatValue()});
                        operands = new ArrayList<>();
                    } else {
                        operands.add(token);
                    }
                }
            }
        }
        return ret;
    }

    private boolean containsColor(List<float[]> colors, float r, float g, float b) {
        for (float[] c : colors) {
            if (Math.abs(c[0] - r) < 0.01f && Math.abs(c[1] - g) < 0.01f && Math.abs(c[2] - b) < 0.01f)
                return true;
        }
        return false;
    }

    /** 内容流全部 Tm 矩阵的 (a,b,c,d) 系数 */
    private List<float[]> textMatrices(byte[] pdf) throws Exception {
        List<float[]> ret = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            for (var page : doc.getPages()) {
                PDFStreamParser parser = new PDFStreamParser(page);
                List<Object> operands = new ArrayList<>();
                for (Object token : parser.parse()) {
                    if (token instanceof Operator op) {
                        if ("Tm".equals(op.getName()) && operands.size() >= 4)
                            ret.add(new float[]{((COSNumber) operands.get(0)).floatValue(),
                                    ((COSNumber) operands.get(1)).floatValue(),
                                    ((COSNumber) operands.get(2)).floatValue(),
                                    ((COSNumber) operands.get(3)).floatValue()});
                        operands = new ArrayList<>();
                    } else {
                        operands.add(token);
                    }
                }
            }
        }
        return ret;
    }

    private ExcelFont font(String name, boolean bold, boolean italic, float size, String color) {
        ExcelFont font = new ExcelFont();
        font.setFontName(name);
        font.setFontSize(size);
        font.setFontColor(color);
        font.setBold(bold);
        font.setItalic(italic);
        return font;
    }

    private void addSimpleStyle(ExcelWorkbook wb, String id, int rotate) {
        ExcelStyle style = new ExcelStyle();
        style.setId(id);
        style.setFont(font("Helvetica", false, false, 10f, "000000"));
        style.setRotate(rotate);
        wb.addStyle(style);
    }

    private void addNumFmtStyle(ExcelWorkbook wb, String id, String numberFormat) {
        ExcelStyle style = new ExcelStyle();
        style.setId(id);
        style.setFont(font("Helvetica", false, false, 10f, "000000"));
        if (numberFormat != null)
            style.setNumberFormat(numberFormat);
        wb.addStyle(style);
    }

    private void textCell(ExcelWorkbook wb, int row, int col, String text, String styleId) {
        ExcelCell cell = new ExcelCell();
        cell.setValue(text);
        cell.setStyleId(styleId);
        sheet.getTable().getRows().get(row).internalAddCell(cell);
    }

    private void numCell(ExcelWorkbook wb, int row, int col, Object value, String styleId) {
        ExcelCell cell = new ExcelCell();
        cell.setValue(value);
        if (styleId != null)
            cell.setStyleId(styleId);
        sheet.getTable().getRows().get(row).internalAddCell(cell);
    }

    private ExcelWorkbook newWorkbook() {
        ExcelWorkbook workbook = new ExcelWorkbook();

        sheet = new io.nop.excel.model.ExcelSheet();
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
            sheet.getTable().getRows().add(row);
        }
        workbook.addSheet(sheet);
        return workbook;
    }

    private byte[] render(ExcelWorkbook workbook) throws Exception {
        PdfReportRenderer renderer = new PdfReportRenderer(workbook, null);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        renderer.generateToStream(bos, null);
        assertNotNull(bos.toByteArray());
        return bos.toByteArray();
    }
}
