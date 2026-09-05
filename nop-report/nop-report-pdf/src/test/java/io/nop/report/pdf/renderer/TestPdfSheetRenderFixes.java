/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.report.pdf.renderer;

import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.ExcelHeaderFooter;
import io.nop.excel.model.ExcelPageMargins;
import io.nop.excel.model.ExcelPageSetup;
import io.nop.excel.model.ExcelRow;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.excel.model.ExcelBorderStyle;
import io.nop.office.model.constants.OfficeLineStyle;
import io.nop.report.pdf.font.FontManager;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PDF渲染缺陷回归测试：字体注册、页眉页脚坐标、打印区平移、边框虚线状态、文档关闭。
 */
public class TestPdfSheetRenderFixes extends JunitBaseTestCase {

    // ========== 字体管理 ==========

    @Test
    public void testStandardFontRegistration() throws Exception {
        // 标准字体应从标准14字体表中直接命中，而不是回退到Helvetica
        try (PDDocument doc = new PDDocument()) {
            PDFont font = new FontManager().getFont("Times New Roman", false, false, doc);
            assertTrue(font instanceof PDType1Font, "should resolve to a standard-14 font");
            assertEquals("Times-Roman", ((PDType1Font) font).getBaseFont());
        }
    }

    @Test
    public void testGetDefaultFontInitializes() {
        // 全新FontManager实例上getDefaultFont()应完成初始化并返回可用字体
        assertNotNull(new FontManager().getDefaultFont());
    }

    // ========== 页面渲染 ==========

    /**
     * 页眉渲染在页面上方、页脚渲染在页面下方（PDF坐标系，原点在左下角）。
     */
    @Test
    public void testHeaderTopFooterBottom() throws Exception {
        byte[] pdf = renderPdf(true, false);
        Map<String, Float> textY = collectTextY(pdf);
        float pageHeight = pageHeight(pdf);
        assertTrue(textY.containsKey("HDR"), "header text should be rendered");
        assertTrue(textY.containsKey("FTR"), "footer text should be rendered");
        assertTrue(textY.get("HDR") > pageHeight * 0.75f,
                "header should be near page top, got y=" + textY.get("HDR"));
        assertTrue(textY.get("FTR") < pageHeight * 0.25f,
                "footer should be near page bottom, got y=" + textY.get("FTR"));
    }

    /**
     * 非对称页边距下，打印区平移量应等于 pageHeight - printArea.y。
     */
    @Test
    public void testPrintAreaTranslateWithAsymmetricMargins() throws Exception {
        byte[] pdf = renderPdf(false, false);
        Float cmY = firstTranslateY(pdf);
        assertNotNull(cmY, "content stream should contain the print-area translate");

        float pageHeight = pageHeight(pdf);
        // printArea.y = top(100) + header(30) = 130，正确平移y = 842 - 130 = 712
        float expected = pageHeight - 130f;
        assertEquals(expected, cmY, 0.5f, "print-area translate y should be pageHeight - printArea.y");
    }

    /**
     * DOUBLE边框绘制前应复位虚线状态。
     */
    @Test
    public void testDoubleBorderResetsDashPattern() throws Exception {
        byte[] pdf = renderPdf(false, true);
        List<int[]> dashOps = collectDashPatterns(pdf);
        assertTrue(dashOps.size() >= 2, "DOUBLE border must reset dash pattern before drawing");
        assertEquals(0, dashOps.get(dashOps.size() - 1)[0], "last dash pattern should be solid (empty)");
    }

    /**
     * 渲染完成后PDDocument应被关闭。
     */
    @Test
    public void testDocumentClosedAfterGenerate() throws Exception {
        ExcelWorkbook workbook = newWorkbook(false, false, false);
        PdfReportRenderer renderer = new PdfReportRenderer(workbook, null);
        renderer.generateToStream(new ByteArrayOutputStream(), null);

        Field field = PdfReportRenderer.class.getDeclaredField("renderer");
        field.setAccessible(true);
        PdfRenderer pdfRenderer = (PdfRenderer) field.get(renderer);
        assertTrue(pdfRenderer.getDocument().getDocument().isClosed(),
                "PDDocument should be closed after rendering");
    }

    // ========== 夹具与解析辅助 ==========

    private byte[] renderPdf(boolean headerFooter, boolean borders) throws Exception {
        ExcelWorkbook workbook = newWorkbook(headerFooter, borders, true);
        PdfReportRenderer renderer = new PdfReportRenderer(workbook, null);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        renderer.generateToStream(bos, null);
        return bos.toByteArray();
    }

    private ExcelWorkbook newWorkbook(boolean headerFooter, boolean borders, boolean margins) {
        ExcelWorkbook workbook = new ExcelWorkbook();

        ExcelStyle style = new ExcelStyle();
        style.setId("hf");
        ExcelFont font = new ExcelFont();
        font.setFontName("Helvetica");
        style.setFont(font);
        workbook.addStyle(style);

        io.nop.excel.model.ExcelSheet sheet = new io.nop.excel.model.ExcelSheet();
        sheet.setName("s1");

        ExcelPageSetup pageSetup = new ExcelPageSetup();
        pageSetup.setPaperWidth(595f);
        pageSetup.setPaperHeight(842f);
        sheet.setPageSetup(pageSetup);

        if (margins) {
            ExcelPageMargins pageMargins = new ExcelPageMargins();
            pageMargins.setLeft(57.0);
            pageMargins.setRight(57.0);
            pageMargins.setTop(100.0);
            pageMargins.setBottom(40.0);
            pageMargins.setHeader(30.0);
            pageMargins.setFooter(30.0);
            sheet.setPageMargins(pageMargins);
        }

        if (headerFooter) {
            ExcelHeaderFooter header = new ExcelHeaderFooter();
            header.setCenter("HDR");
            header.setStyle(style);
            pageSetup.setHeader(header);

            ExcelHeaderFooter footer = new ExcelHeaderFooter();
            footer.setCenter("FTR");
            footer.setStyle(style);
            pageSetup.setFooter(footer);
        }

        ExcelRow row = new ExcelRow();
        ExcelCell cell = new ExcelCell();
        if (borders) {
            ExcelStyle borderStyle = new ExcelStyle();
            borderStyle.setId("borders");
            ExcelBorderStyle dashed = new ExcelBorderStyle();
            dashed.setType(OfficeLineStyle.DASHED);
            ExcelBorderStyle dbl = new ExcelBorderStyle();
            dbl.setType(OfficeLineStyle.DOUBLE);
            borderStyle.setTopBorder(dashed);
            borderStyle.setBottomBorder(dbl);
            workbook.addStyle(borderStyle);
            cell.setStyleId("borders");
        }
        row.internalAddCell(cell);
        sheet.getTable().getRows().add(row);
        workbook.addSheet(sheet);
        return workbook;
    }

    private float pageHeight(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getPage(0).getMediaBox().getHeight();
        }
    }

    private Map<String, Float> collectTextY(byte[] pdf) throws Exception {
        Map<String, Float> textY = new HashMap<>();
        forEachOperator(pdf, (name, operands, state) -> {
            if (name.equals("Td") || name.equals("TD")) {
                if (operands.size() >= 2 && operands.get(1) instanceof COSNumber)
                    state.put("curY", ((COSNumber) operands.get(1)).floatValue());
            } else if (name.equals("Tm")) {
                if (operands.size() >= 6 && operands.get(5) instanceof COSNumber)
                    state.put("curY", ((COSNumber) operands.get(5)).floatValue());
            } else if (name.equals("Tj")) {
                if (!operands.isEmpty() && operands.get(operands.size() - 1) instanceof COSString) {
                    String text = ((COSString) operands.get(operands.size() - 1)).getString();
                    if (state.containsKey("curY"))
                        textY.put(text, (Float) state.get("curY"));
                }
            }
        });
        return textY;
    }

    private Float firstTranslateY(byte[] pdf) throws Exception {
        Float[] result = new Float[1];
        forEachOperator(pdf, (name, operands, state) -> {
            if (name.equals("cm") && result[0] == null && operands.size() >= 6
                    && operands.get(5) instanceof COSNumber) {
                result[0] = ((COSNumber) operands.get(5)).floatValue();
            }
        });
        return result[0];
    }

    private List<int[]> collectDashPatterns(byte[] pdf) throws Exception {
        List<int[]> patterns = new ArrayList<>();
        forEachOperator(pdf, (name, operands, state) -> {
            if (name.equals("d") && operands.size() >= 2 && operands.get(0) instanceof COSArray) {
                patterns.add(new int[]{((COSArray) operands.get(0)).size()});
            }
        });
        return patterns;
    }

    private interface OperatorVisitor {
        void visit(String name, List<Object> operands, Map<String, Object> state) throws Exception;
    }

    private void forEachOperator(byte[] pdf, OperatorVisitor visitor) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDPage page = doc.getPage(0);
            PDFStreamParser parser = new PDFStreamParser(page);
            List<Object> tokens = parser.parse();

            List<Object> operands = new ArrayList<>();
            Map<String, Object> state = new HashMap<>();
            for (Object token : tokens) {
                if (token instanceof org.apache.pdfbox.contentstream.operator.Operator) {
                    visitor.visit(((org.apache.pdfbox.contentstream.operator.Operator) token).getName(),
                            operands, state);
                    operands.clear();
                } else {
                    operands.add(token);
                }
            }
        }
    }
}
