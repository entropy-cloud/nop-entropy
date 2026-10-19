package io.nop.report.pdf.renderer;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.store.InMemoryResourceStore;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelFont;
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
import io.nop.report.pdf.font.FontManager;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI10：字体渲染保真——伪粗体（FILL_STROKE 描边色随字体色 + dash 重置）、
 * 伪斜体（shear 0.25 行矩阵）、-Oblique 变体发现、ToUnicode 复制/搜索正确性。
 *
 * 伪变体场景用不存在的字体名（NoSuchSerif）走 defaultFontResource（捆绑 /fonts/default.ttf）
 * 回退分支，synthetic 标志必为 true；真实变体场景用 Helvetica 命中标准14变体表。
 */
public class TestPdfFontFidelity extends JunitBaseTestCase {

    private static final String FALLBACK_FONT = "NoSuchSerif";

    @BeforeAll
    static void resetFontManager() {
        FontManager.instance().resetForTesting();
    }

    @AfterAll
    static void restoreFontManager() {
        FontManager.instance().resetForTesting();
    }

    // ========== 解析标志判定 ==========

    @Test
    public void testResolveFontVariantHitNoSyntheticFlags() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            FontManager.FontResolution bold = FontManager.instance().resolveFont(
                    "Helvetica", true, false, doc);
            assertEquals("Helvetica-Bold", bold.getFont().getName());
            assertFalse(bold.isSyntheticBold(), "real bold variant must not be synthetic");

            FontManager.FontResolution boldItalic = FontManager.instance().resolveFont(
                    "Helvetica", true, true, doc);
            // PDFBox 标准14的 bold-italic 注册名是 -BoldOblique（"Helvetica-BoldItalic" 是本表别名键）
            assertEquals("Helvetica-BoldOblique", boldItalic.getFont().getName());
            assertFalse(boldItalic.isSyntheticBold());
            assertFalse(boldItalic.isSyntheticItalic());

            FontManager.FontResolution plain = FontManager.instance().resolveFont(
                    "Helvetica", false, false, doc);
            assertFalse(plain.isSyntheticBold());
            assertFalse(plain.isSyntheticItalic());
        }
    }

    @Test
    public void testResolveFontFallbackMarksSynthetic() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            FontManager.FontResolution res = FontManager.instance().resolveFont(
                    FALLBACK_FONT, true, true, doc);
            assertNotNull(res.getFont());
            assertTrue(res.isSyntheticBold(), "fallback font must mark bold request synthetic");
            assertTrue(res.isSyntheticItalic(), "fallback font must mark italic request synthetic");

            FontManager.FontResolution plain = FontManager.instance().resolveFont(
                    FALLBACK_FONT, false, false, doc);
            assertFalse(plain.isSyntheticBold());
            assertFalse(plain.isSyntheticItalic());
        }
    }

    /**
     * -Oblique 命名发现：italic 请求命中 /fonts/{name}-Oblique.ttf 时非合成；
     * 仅有 base 资源时 italic 合成；bold-only 请求不得把 regular oblique 当 bold 变体。
     */
    @Test
    public void testObliqueVariantDiscovery() throws Exception {
        File fontFile = copyBundledFont("Wi10Obl-Oblique.ttf");
        File baseFile = copyBundledFont("Wi10Base.ttf");
        File obliqueOnlyFile = copyBundledFont("Wi10BoldOnly-Oblique.ttf");
        InMemoryResourceStore store = new InMemoryResourceStore();
        store.addResource(new FileResource("/fonts/Wi10Obl-Oblique.ttf", fontFile));
        store.addResource(new FileResource("/fonts/Wi10Base.ttf", baseFile));
        store.addResource(new FileResource("/fonts/Wi10BoldOnly-Oblique.ttf", obliqueOnlyFile));
        VirtualFileSystem.instance().updateInMemoryLayer(store);

        try (PDDocument doc = new PDDocument()) {
            FontManager.FontResolution oblique = FontManager.instance().resolveFont(
                    "Wi10Obl", false, true, doc);
            assertFalse(oblique.isSyntheticItalic(),
                    "real -Oblique variant must satisfy italic request");

            FontManager.FontResolution base = FontManager.instance().resolveFont(
                    "Wi10Base", false, true, doc);
            assertTrue(base.isSyntheticItalic(),
                    "base-only resource must mark italic request synthetic");

            FontManager.FontResolution boldOnly = FontManager.instance().resolveFont(
                    "Wi10BoldOnly", true, false, doc);
            assertTrue(boldOnly.isSyntheticBold(),
                    "bold-only request must not take regular -Oblique as bold variant");
        }
    }

    // ========== 渲染层操作符断言 ==========

    /**
     * (a) 伪粗体：前格红色虚线边框残留 stroking 状态后，伪粗体文本必须重置
     * dash + 描边色随字体色（black），且以 Tr 2（FILL_STROKE）绘制。
     */
    @Test
    public void testSyntheticBoldFillStrokeWithColorGuard() throws Exception {
        ExcelWorkbook wb = newWorkbook();
        addStyle(wb, "dashed-red", font("Helvetica", false, false), "FF0000", OfficeLineStyle.DASHED);
        addStyle(wb, "synbold", font(FALLBACK_FONT, true, false), "000000", null);
        textCell(wb, 0, 0, "plain", "dashed-red");
        textCell(wb, 0, 1, "Bold", "synbold");
        byte[] pdf = render(wb);

        List<Op> ops = parseOps(pdf);
        // 定位首个合成粗体 Tr（值=2）；注意绘制后的 renderingMode 还原也发 "0 Tr"，不可混入
        int trIdx = -1;
        for (int i = 0; i < ops.size(); i++) {
            Op op = ops.get(i);
            if ("Tr".equals(op.name) && num(op.operands.get(0)) == 2) {
                trIdx = i;
                break;
            }
        }
        assertTrue(trIdx >= 0, "synthetic bold must emit Tr FILL_STROKE");

        // Tr 之前最后一个 RG（stroking color）必须是字体色 black 而非残留 red
        float[] stroking = lastStrokingRgbBefore(ops, trIdx);
        assertNotNull(stroking, "synthetic bold must set stroking color");
        assertEquals(0f, stroking[0], 0.01f, "stroke color must follow text color (black), not inherited red");
        assertEquals(0f, stroking[1], 0.01f);
        assertEquals(0f, stroking[2], 0.01f);

        // Tr 之前最后一个 d（dash）必须为空数组（重置边框虚线残留）
        COSArray dash = lastDashBefore(ops, trIdx);
        assertNotNull(dash, "synthetic bold must reset dash pattern");
        assertEquals(0, dash.size(), "dash pattern must be empty before synthetic bold stroke");

        assertTrue(TestPdfRenderDefects.normalize(TestPdfRenderDefects.allText(pdf)).contains("Bold"),
                "synthetic bold text must be extractable");

        // 前置条件自检：场景中确实存在红色描边（否则守卫断言无意义）
        assertTrue(hasStrokingRgb(ops, 1f, 0f, 0f), "scene must contain the red border stroke");
    }

    /**
     * (b) 真实变体：Helvetica bold 命中标准14变体，不得出现 Tr 合成操作符。
     */
    @Test
    public void testRealBoldVariantHasNoSyntheticOps() throws Exception {
        ExcelWorkbook wb = newWorkbook();
        addStyle(wb, "realbold", font("Helvetica", true, false), "000000", null);
        textCell(wb, 0, 0, "Bold", "realbold");
        byte[] pdf = render(wb);

        for (Op op : parseOps(pdf))
            assertNotTrFillStroke(op);
        assertTrue(TestPdfRenderDefects.normalize(TestPdfRenderDefects.allText(pdf)).contains("Bold"));
    }

    /**
     * (c) 伪斜体：shear 0.25 行矩阵（Tm c 系数）。
     */
    @Test
    public void testSyntheticItalicShearMatrix() throws Exception {
        ExcelWorkbook wb = newWorkbook();
        addStyle(wb, "synitalic", font(FALLBACK_FONT, false, true), "000000", null);
        textCell(wb, 0, 0, "Italic", "synitalic");
        byte[] pdf = render(wb);

        boolean found = false;
        for (Op op : parseOps(pdf)) {
            if ("Tm".equals(op.name) && op.operands.size() >= 3) {
                assertEquals(0.25f, ((COSNumber) op.operands.get(2)).floatValue(), 0.001f,
                        "synthetic italic must shear via Tm c=0.25");
                found = true;
            }
        }
        assertTrue(found, "synthetic italic must emit Tm shear matrix");
        assertTrue(TestPdfRenderDefects.normalize(TestPdfRenderDefects.allText(pdf)).contains("Italic"));
    }

    /**
     * 伪斜体剪切仅作用于请求维度的文本：常规文本走 Td 定位，不出现 0.25 剪切。
     */
    @Test
    public void testRegularTextHasNoShear() throws Exception {
        ExcelWorkbook wb = newWorkbook();
        addStyle(wb, "plain", font("Helvetica", false, false), "000000", null);
        textCell(wb, 0, 0, "Plain", "plain");
        byte[] pdf = render(wb);

        for (Op op : parseOps(pdf)) {
            if ("Tm".equals(op.name) && op.operands.size() >= 3)
                assertTrue(Math.abs(((COSNumber) op.operands.get(2)).floatValue() - 0.25f) > 0.001f,
                        "regular text must not be sheared");
        }
    }

    /**
     * wrapped 路径（wrapText）伪粗体同样走 FILL_STROKE。
     */
    @Test
    public void testSyntheticBoldWrappedPath() throws Exception {
        ExcelWorkbook wb = newWorkbook();
        ExcelStyle style = addStyle(wb, "wrapbold", font(FALLBACK_FONT, true, false), "000000", null);
        style.setWrapText(true);
        textCell(wb, 0, 0, "Wrapped Bold Text", "wrapbold");
        byte[] pdf = render(wb);

        boolean found = false;
        for (Op op : parseOps(pdf)) {
            if ("Tr".equals(op.name) && num(op.operands.get(0)) == 2)
                found = true;
        }
        assertTrue(found, "wrapped synthetic bold must emit Tr FILL_STROKE");
        assertTrue(TestPdfRenderDefects.normalize(TestPdfRenderDefects.allText(pdf)).contains("Wrapped"));
    }

    /**
     * (d) ToUnicode：经 defaultFontResource（捆绑 default.ttf）渲染的 CJK 文本
     * 必须可按原文提取（复制/搜索等价）。
     */
    @Test
    public void testToUnicodeCjkTextExtractable() throws Exception {
        ExcelWorkbook wb = newWorkbook();
        addStyle(wb, "cjk", font("宋体", false, false), "000000", null);
        textCell(wb, 0, 0, "字体保真测试ABC123", "cjk");
        byte[] pdf = render(wb);

        String text = TestPdfRenderDefects.normalize(TestPdfRenderDefects.allText(pdf));
        assertTrue(text.contains("测试") && text.contains("ABC123"),
                "CJK text must be extractable via ToUnicode: " + text);
    }

    /**
     * (e) golden：伪粗体/伪斜体/真实变体混排页快照。
     */
    @Test
    public void testGoldenSyntheticFontPage() throws Exception {
        ExcelWorkbook wb = newWorkbook();
        addStyle(wb, "dashed-red", font("Helvetica", false, false), "FF0000", OfficeLineStyle.DASHED);
        addStyle(wb, "synbold", font(FALLBACK_FONT, true, false), "000000", null);
        addStyle(wb, "synitalic", font(FALLBACK_FONT, false, true), "000000", null);
        addStyle(wb, "realbold", font("Helvetica", true, false), "000000", null);
        textCell(wb, 0, 0, "plain", "dashed-red");
        textCell(wb, 0, 1, "Bold", "synbold");
        textCell(wb, 1, 0, "Italic", "synitalic");
        textCell(wb, 1, 1, "RealBold", "realbold");
        byte[] pdf = render(wb);

        PdfGoldenManager golden = PdfGoldenManager.forDefaultDir("wi10-font-fidelity");
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            List<BufferedImage> images = PdfImageRenderer.renderAll(doc, PdfImageRenderer.DEFAULT_DPI);
            for (int i = 0; i < images.size(); i++) {
                GoldenCompareResult result = golden.compare("synthetic-font-page-" + (i + 1), images.get(i));
                assertTrue(result.getStatus() == GoldenCompareResult.Status.PASS
                        || result.getStatus() == GoldenCompareResult.Status.BASELINE_CREATED,
                        "golden regression on page " + (i + 1) + ": " + result);
            }
        }
    }

    // ========== 夹具与内容流解析 ==========

    private record Op(String name, List<Object> operands) {
    }

    private List<Op> parseOps(byte[] pdf) throws Exception {
        List<Op> ret = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            for (var page : doc.getPages()) {
                PDFStreamParser parser = new PDFStreamParser(page);
                List<Object> operands = new ArrayList<>();
                for (Object token : parser.parse()) {
                    if (token instanceof Operator op) {
                        ret.add(new Op(op.getName(), operands));
                        operands = new ArrayList<>();
                    } else {
                        operands.add(token);
                    }
                }
            }
        }
        return ret;
    }

    private int num(Object token) {
        return ((COSNumber) token).intValue();
    }

    /** PDFBox setStrokingColor 对 DeviceRGB 发 SC（或 RG）；DeviceGray 发 G/K（1 操作数） */
    private float[] lastStrokingRgbBefore(List<Op> ops, int idx) {
        for (int i = idx - 1; i >= 0; i--) {
            Op op = ops.get(i);
            if (("SC".equals(op.name) || "RG".equals(op.name)) && op.operands.size() >= 3)
                return new float[]{((COSNumber) op.operands.get(0)).floatValue(),
                        ((COSNumber) op.operands.get(1)).floatValue(),
                        ((COSNumber) op.operands.get(2)).floatValue()};
            if (("G".equals(op.name) || "K".equals(op.name)) && !op.operands.isEmpty()) {
                float v = ((COSNumber) op.operands.get(0)).floatValue();
                return new float[]{v, v, v};
            }
        }
        return null;
    }

    private boolean hasStrokingRgb(List<Op> ops, float r, float g, float b) {
        float[] found = null;
        for (Op op : ops) {
            float[] rgb = null;
            if (("SC".equals(op.name) || "RG".equals(op.name)) && op.operands.size() >= 3)
                rgb = new float[]{((COSNumber) op.operands.get(0)).floatValue(),
                        ((COSNumber) op.operands.get(1)).floatValue(),
                        ((COSNumber) op.operands.get(2)).floatValue()};
            else if ("G".equals(op.name) && !op.operands.isEmpty()) {
                float v = ((COSNumber) op.operands.get(0)).floatValue();
                rgb = new float[]{v, v, v};
            }
            if (rgb != null
                    && Math.abs(rgb[0] - r) < 0.01f && Math.abs(rgb[1] - g) < 0.01f
                    && Math.abs(rgb[2] - b) < 0.01f)
                found = rgb;
        }
        return found != null;
    }

    private COSArray lastDashBefore(List<Op> ops, int idx) {
        for (int i = idx - 1; i >= 0; i--) {
            Op op = ops.get(i);
            if ("d".equals(op.name) && !op.operands.isEmpty()
                    && op.operands.get(0) instanceof COSArray arr)
                return arr;
        }
        return null;
    }

    private void assertNotTrFillStroke(Op op) {
        if ("Tr".equals(op.name))
            org.junit.jupiter.api.Assertions.assertNotEquals(2, num(op.operands.get(0)),
                    "real variant must not use FILL_STROKE");
    }

    private ExcelFont font(String name, boolean bold, boolean italic) {
        ExcelFont font = new ExcelFont();
        font.setFontName(name);
        font.setFontSize(10.0f);
        font.setFontColor("000000");
        font.setBold(bold);
        font.setItalic(italic);
        return font;
    }

    private ExcelStyle addStyle(ExcelWorkbook wb, String id, ExcelFont font,
                                String borderColor, OfficeLineStyle borderType) {
        ExcelStyle style = new ExcelStyle();
        style.setId(id);
        style.setFont(font);
        if (borderType != null) {
            ExcelBorderStyle border = new ExcelBorderStyle();
            border.setType(borderType);
            border.setWeight(1);
            border.setColor(borderColor);
            style.setTopBorder(border);
            style.setBottomBorder(border);
            style.setLeftBorder(border);
            style.setRightBorder(border);
        }
        wb.addStyle(style);
        return style;
    }

    private io.nop.excel.model.ExcelSheet sheet;

    private void textCell(ExcelWorkbook wb, int row, int col, String text, String styleId) {
        ExcelCell cell = new ExcelCell();
        cell.setValue(text);
        cell.setStyleId(styleId);
        ExcelRow excelRow = sheet.getTable().getRows().get(row);
        excelRow.internalAddCell(cell);
    }

    /** 单页 2x2 网格（60x20pt 单元格），行由调用方填充单元格 */
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
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        renderer.generateToStream(bos, null);
        return bos.toByteArray();
    }

    /**
     * 把捆绑的 /fonts/default.ttf 复制为指定文件名的字体文件（父目录确保存在）
     */
    private File copyBundledFont(String fileName) throws Exception {
        Path dir = Path.of("_tmp", "wi10-fonts");
        Files.createDirectories(dir);
        Path target = dir.resolve(fileName);
        try (InputStream is = getClass().getResourceAsStream("/_vfs/fonts/default.ttf")) {
            assertNotNull(is, "bundled /fonts/default.ttf must exist in test resources");
            Files.copy(is, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return target.toFile();
    }
}
