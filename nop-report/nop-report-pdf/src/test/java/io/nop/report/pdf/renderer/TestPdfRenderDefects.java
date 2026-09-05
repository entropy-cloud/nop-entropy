package io.nop.report.pdf.renderer;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.bytes.ByteString;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.store.InMemoryResourceStore;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelClientAnchor;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.ExcelPageMargins;
import io.nop.excel.model.ExcelPageSetup;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.ExcelWorkbook;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2260 Phase 1：PDF渲染缺陷的复现与断言基线（缺陷编号对应
 * ai-dev/analysis/2026-09/2026-09-05-nop-report-pdf-visual-audit.md）。
 *
 * F7为已修复缺陷的复验回归钉（出生即绿）；F5/F6/F9/F2/F3/F4为红测试，
 * 修复后翻转。
 */
public class TestPdfRenderDefects extends JunitBaseTestCase {

    static final String[] CJK_FONT_CANDIDATES = {
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
            "C:/Windows/Fonts/simsun.ttc",
    };

    private static volatile boolean fontInjected;

    /**
     * CJK字体注入：仓库未自带字体，命名路径/fonts/宋体.ttf与/fonts/default.ttf
     * 双路径注册，规避FontManager.init时序问题
     */
    static void injectCjkFontIfNeeded() {
        if (fontInjected)
            return;
        synchronized (TestPdfRenderDefects.class) {
            if (fontInjected)
                return;
            for (String path : CJK_FONT_CANDIDATES) {
                File f = new File(path);
                if (!f.exists())
                    continue;
                InMemoryResourceStore store = new InMemoryResourceStore();
                store.addResource(new FileResource("/fonts/default.ttf", f));
                store.addResource(new FileResource("/fonts/宋体.ttf", f));
                VirtualFileSystem.instance().updateInMemoryLayer(store);
                fontInjected = true;
                return;
            }
        }
    }

    // ========== F7：无样式单元格NPE（已修复，复验回归钉） ==========

    @Test
    public void testF7_noStyleCellRendersWithoutNpe() throws Exception {
        ExcelWorkbook wb = new ExcelWorkbook();
        ExcelSheet sheet = newSheet(wb, 300, 400);
        // 单元格无styleId、style无font：e2b02af86e修复前此处抛NPE
        setCell(sheet, 0, 0, "plain");
        setCell(sheet, 0, 1, 137);
        byte[] pdf = render(wb);
        String text = allText(pdf);
        assertTrue(text.contains("plain"), "text should render");
    }

    // ========== F9：Integer值渲染（回归钉：审计中的"整列缺失"经复现证伪为视觉误读） ==========

    @Test
    public void testF9_integerValueRendered() throws Exception {
        ExcelWorkbook wb = new ExcelWorkbook();
        ExcelSheet sheet = newSheet(wb, 400, 400);
        ExcelStyle style = addFontStyle(wb, "s1", "Helvetica");
        setCell(sheet, 0, 0, styleCell("C0", style));
        setCell(sheet, 0, 1, styleCell("C1", style));
        setCell(sheet, 0, 2, styleCell("C2", style));
        setCell(sheet, 1, 0, styleCell("R1", style));
        setCell(sheet, 1, 1, styleCell(137, style));
        setCell(sheet, 1, 2, styleCell(12.5, style));

        String text = allText(render(wb));
        assertTrue(text.contains("12.5"), "Double value should render");
        assertTrue(text.contains("137"), "Integer value should render, got: " + text);
    }

    // ========== F6：分页边界行文字重影（红） ==========

    @Test
    public void testF6_eachRowTextDrawnExactlyOnce() throws Exception {
        int rows = 60;
        ExcelWorkbook wb = new ExcelWorkbook();
        ExcelSheet sheet = newSheet(wb, 300, 300);
        ExcelStyle style = addFontStyle(wb, "s1", "Helvetica");
        for (int r = 0; r < rows; r++) {
            setCell(sheet, r, 0, styleCell("ROW-" + String.format("%02d", r), style));
        }

        byte[] pdf = render(wb);
        String text = normalize(allText(pdf));
        for (int r = 0; r < rows; r++) {
            String label = "ROW-" + String.format("%02d", r);
            int count = countOccurrences(text, label);
            assertEquals(1, count, "label " + label + " should be drawn exactly once");
        }
    }

    /**
     * F6审计场景复现：4列CJK长表（与审计synthetic-long-table完全一致的条件）。
     * 审计PNG显示分页边界附近行文字重影：同一行文本被绘制多次。
     * 用绘制操作计数（而非提取文本计数）——多列文本重叠时提取会交错，
     * 按showText操作计数才是重影检测的正确工具
     */
    @Test
    public void testF6_auditScenarioEachRowDrawnOnce() throws Exception {
        injectCjkFontIfNeeded();
        ExcelWorkbook wb = new ExcelWorkbook();
        ExcelSheet sheet = newSheet(wb, 595, 842);
        ExcelStyle style = addFontStyle(wb, "s1", "宋体");
        String[] headers = {"编号", "名称", "数量", "金额"};
        for (int c = 0; c < headers.length; c++) {
            setCell(sheet, 0, c, styleCell(headers[c], style));
        }
        for (int r = 1; r <= 200; r++) {
            Object[] row = {"P" + r, "测试项目名称第" + r + "号", r * 3, r * 12.5};
            for (int c = 0; c < row.length; c++) {
                setCell(sheet, r, c, styleCell(row[c], style));
            }
        }

        byte[] pdf = render(wb);
        assertTrue(pageCount(pdf) >= 2, "should paginate");
        // 审计显示边界附近（如P34-P48）存在重影
        for (int r = 30; r <= 48; r++) {
            String label = "P" + r;
            int count = countDrawOps(pdf, label);
            assertEquals(1, count, "audit-scenario row " + label + " should be drawn exactly once");
        }
    }

    // ========== F5：wrapText行距回归钉（审计中的"多行叠压"主因证伪为F10无裁剪溢出） ==========

    @Test
    public void testF5_wrapTextLinesHaveDistinctY() throws Exception {
        injectCjkFontIfNeeded();
        ExcelWorkbook wb = new ExcelWorkbook();
        ExcelSheet sheet = newSheet(wb, 300, 400);
        ExcelStyle style = addFontStyle(wb, "song", "宋体");
        style.setWrapText(true);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            sb.append("多行文本叠压测试段落");
        }
        setCell(sheet, 0, 0, styleCell(sb.toString(), style));
        // 空一行避免单格占满页面
        setCell(sheet, 3, 0, styleCell("END", style));

        // 逐字统计"叠"（每个奇数行含一个）的y坐标：行距为0时全部重合
        List<float[]> ys = charYPositions(render(wb), "叠");
        assertTrue(ys.size() >= 3, "wrapped text should produce >=3 叠 glyphs, got " + ys.size());
        Set<Float> distinct = new HashSet<>();
        for (float[] y : ys) {
            distinct.add(y[0]);
        }
        assertEquals(ys.size(), distinct.size(),
                "each wrapped line should have a distinct y position");
    }

    // ========== F2：print=false图片未排除（红） ==========

    @Disabled("plan 2260 Phase 5修复print过滤后启用；红证据见ai-dev/logs/2026/09-05.md")
    @Test
    public void testF2_printFalseImageExcludedFromPdf() throws Exception {
        ExcelWorkbook wb = new ExcelWorkbook();
        ExcelSheet sheet = newSheet(wb, 400, 400);
        ExcelStyle style = addFontStyle(wb, "s1", "Helvetica");
        setCell(sheet, 0, 0, styleCell("DATA", style));

        ExcelImage image = new ExcelImage();
        image.setImgType("png");
        image.setDataBytes(tinyPng());
        ExcelClientAnchor anchor = new ExcelClientAnchor();
        anchor.setRow1(1);
        anchor.setCol1(0);
        anchor.setRowDelta(2);
        anchor.setColDelta(1);
        image.setAnchor(anchor);
        // 套打背景图：屏幕可见、打印隐藏
        image.setPrint(false);
        sheet.addImage(image);

        byte[] pdf = render(wb);
        assertTrue(normalize(allText(pdf)).contains("DATA"), "data text should render");
        // 修复前：PDF包含应被排除的背景图
        assertEquals(0, countImageXObjects(pdf), "print=false image must be excluded from PDF");
    }

    // ========== F3：列拆分页无关键列重复（红） ==========

    @Disabled("plan 2260 Phase 4修复列拆分关键列重复后启用；红证据见ai-dev/logs/2026/09-05.md")
    @Test
    public void testF3_columnSplitRepeatsKeyColumn() throws Exception {
        ExcelWorkbook wb = new ExcelWorkbook();
        ExcelSheet sheet = newSheet(wb, 300, 400);
        ExcelStyle style = addFontStyle(wb, "s1", "Helvetica");
        // 12列窄页宽必然列拆分；第0列为关键列
        setCell(sheet, 0, 0, styleCell("KEY-1", style));
        for (int c = 1; c < 12; c++) {
            setCell(sheet, 0, c, styleCell("V" + c, style));
        }

        byte[] pdf = render(wb);
        int pages = pageCount(pdf);
        assertTrue(pages >= 2, "wide table should split into >=2 pages, got " + pages);
        // 修复前：关键列KEY-1只出现在第一个列拆分页上
        assertTrue(countPagesContaining(pdf, "KEY-1") >= 2,
                "key column should repeat on every column band page");
    }

    // ========== F4：续页无重复表头（红） ==========

    @Disabled("plan 2260 Phase 4修复续页表头重复后启用；红证据见ai-dev/logs/2026/09-05.md")
    @Test
    public void testF4_continuationPageRepeatsHeader() throws Exception {
        ExcelWorkbook wb = new ExcelWorkbook();
        ExcelSheet sheet = newSheet(wb, 300, 300);
        ExcelStyle style = addFontStyle(wb, "s1", "Helvetica");
        setCell(sheet, 0, 0, styleCell("HDR", style));
        for (int r = 1; r <= 60; r++) {
            setCell(sheet, r, 0, styleCell("ROW-" + String.format("%02d", r), style));
        }

        byte[] pdf = render(wb);
        int pages = pageCount(pdf);
        assertTrue(pages >= 2, "long table should split into >=2 pages, got " + pages);
        // 修复前：表头只出现在第1页
        assertTrue(normalize(pageText(pdf, 2)).contains("HDR"),
                "continuation page should repeat the header row");
    }

    // ========== 夹具与解析辅助 ==========

    static ExcelSheet newSheet(ExcelWorkbook wb, float paperWidth, float paperHeight) {
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("s1");
        ExcelPageSetup pageSetup = new ExcelPageSetup();
        pageSetup.setPaperWidth(paperWidth);
        pageSetup.setPaperHeight(paperHeight);
        sheet.setPageSetup(pageSetup);
        ExcelPageMargins margins = new ExcelPageMargins();
        margins.setLeft(30.0);
        margins.setRight(30.0);
        margins.setTop(30.0);
        margins.setBottom(30.0);
        sheet.setPageMargins(margins);
        wb.addSheet(sheet);
        return sheet;
    }

    static ExcelStyle addFontStyle(ExcelWorkbook wb, String styleId, String fontName) {
        ExcelStyle style = new ExcelStyle();
        style.setId(styleId);
        ExcelFont font = new ExcelFont();
        font.setFontName(fontName);
        font.setFontSize(10.0f);
        style.setFont(font);
        wb.addStyle(style);
        return style;
    }

    static ExcelCell styleCell(Object value, ExcelStyle style) {
        ExcelCell cell = new ExcelCell();
        cell.setValue(value);
        cell.setStyleId(style.getId());
        return cell;
    }

    static void setCell(ExcelSheet sheet, int row, int col, ExcelCell cell) {
        sheet.getTable().setCell(row, col, cell);
    }

    static void setCell(ExcelSheet sheet, int row, int col, Object value) {
        ExcelCell cell = new ExcelCell();
        cell.setValue(value);
        setCell(sheet, row, col, cell);
    }

    static byte[] render(ExcelWorkbook wb) throws Exception {
        PdfReportRenderer renderer = new PdfReportRenderer(wb, null);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        renderer.generateToStream(bos, null);
        return bos.toByteArray();
    }

    static String allText(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    static String pageText(byte[] pdf, int pageNo1Based) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(pageNo1Based);
            stripper.setEndPage(pageNo1Based);
            return stripper.getText(doc);
        }
    }

    static int pageCount(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        }
    }

    static int countPagesContaining(byte[] pdf, String needle) throws Exception {
        int n = pageCount(pdf);
        int count = 0;
        for (int p = 1; p <= n; p++) {
            if (normalize(pageText(pdf, p)).contains(needle))
                count++;
        }
        return count;
    }

    /**
     * 收集包含指定单字的所有字形y坐标：返回[y, x]对。
     * 注意：PDFTextStripper对CJK按单字分块，writeString的text参数是单字而非整行
     */
    static List<float[]> charYPositions(byte[] pdf, String singleChar) throws Exception {
        List<float[]> ret = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    if (text != null && text.contains(singleChar) && !positions.isEmpty()) {
                        TextPosition first = positions.get(0);
                        ret.add(new float[]{first.getYDirAdj(), first.getXDirAdj()});
                    }
                }
            };
            stripper.getText(doc);
        }
        return ret;
    }

    /**
     * 统计文本绘制操作（showText）中包含指定字符串的次数：
     * 每次drawText产生独立的BT/Tj/ET块，按操作计数不受多列文本提取交错影响
     */
    static int countDrawOps(byte[] pdf, String needle) throws Exception {
        int[] count = new int[1];
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    if (text != null) {
                        int idx = 0;
                        while ((idx = text.indexOf(needle, idx)) >= 0) {
                            count[0]++;
                            idx += needle.length();
                        }
                    }
                }
            };
            stripper.getText(doc);
        }
        return count[0];
    }

    static int countImageXObjects(byte[] pdf) throws Exception {
        int count = 0;
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            for (PDPage page : doc.getPages()) {
                PDResources res = page.getResources();
                if (res == null)
                    continue;
                for (org.apache.pdfbox.cos.COSName name : res.getXObjectNames()) {
                    if (res.getXObject(name) instanceof PDImageXObject)
                        count++;
                }
            }
        }
        return count;
    }

    static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }

    static int countOccurrences(String text, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    static byte[] tinyPng() throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }
}
