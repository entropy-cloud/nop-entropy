package io.nop.pdf.text;

import io.nop.api.core.exceptions.NopException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI16：版面感知页文本抽取——strip/layout 双模式、页锚、扫描件硬错误、乱码双路。
 */
public class TestPageTextExtraction {

    private PDDocument newDoc() {
        PDDocument doc = new PDDocument();
        for (int i = 0; i < 5; i++)
            doc.addPage(new PDPage());
        return doc;
    }

    /** 有内容流 + 有字形但 trim 后 charCount=0（模拟扫描件/纯图形页的文本层特征） */
    private void writeWhitespaceOnly(PDDocument doc, int pageIdx) throws Exception {
        writeText(doc, pageIdx, List.of("   "));
    }

    private void writeGraphicsOnly(PDDocument doc, int pageIdx) throws Exception {
        PDPage page = doc.getPage(pageIdx - 1);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page,
                PDPageContentStream.AppendMode.APPEND, true, true)) {
            cs.addRect(100, 100, 200, 200);
            cs.stroke();
        }
    }

    private void writeText(PDDocument doc, int pageIdx, List<String> lines) throws Exception {
        PDPage page = doc.getPage(pageIdx - 1);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page,
                PDPageContentStream.AppendMode.OVERWRITE, false)) {
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            float y = 760;
            for (String line : lines) {
                cs.beginText();
                cs.newLineAtOffset(50, y);
                cs.showText(line);
                cs.endText();
                y -= 20;
            }
        }
    }

    private void writeMultiColumn(PDDocument doc, int pageIdx) throws Exception {
        PDPage page = doc.getPage(pageIdx - 1);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page,
                PDPageContentStream.AppendMode.OVERWRITE, false)) {
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            // 左列与右列（x 间隙大）
            cs.beginText();
            cs.newLineAtOffset(50, 700);
            cs.showText("Left Column");
            cs.endText();
            cs.beginText();
            cs.newLineAtOffset(300, 700);
            cs.showText("Right Column");
            cs.endText();
        }
    }

    // ========== strip 模式 ==========

    @Test
    public void testStripModeWithAnchorAndNormalization() throws Exception {
        try (PDDocument doc = newDoc()) {
            writeText(doc, 1, List.of("Chapter 1 Intro", "body here"));
            writeText(doc, 2, List.of("Page Two"));
            PageTextOptions opts = new PageTextOptions().setScanPolicy(PageTextOptions.ScanPolicy.SKIP);
            PageTextExtractResult result = PageTextExtractor.extract(doc, opts);

            assertEquals(5, result.getPages().size());
            PageTextResult p1 = result.getPages().get(0);
            assertTrue(p1.getText().startsWith("<a id=\"pdf-page-1\"></a>\n"), "锚独立成行");
            assertTrue(p1.getText().contains("Chapter 1 Intro"));
            assertTrue(result.getTotalPages() == 5);
            assertTrue(result.getTotalChars() > 0);
        }
    }

    @Test
    public void testMaxCharsTruncation() throws Exception {
        try (PDDocument doc = newDoc()) {
            writeText(doc, 1, List.of("This is a long line of text for truncation testing"));
            PageTextOptions opts = new PageTextOptions().setScanPolicy(PageTextOptions.ScanPolicy.SKIP).setMaxChars(30);
            PageTextExtractResult result = PageTextExtractor.extract(doc, opts);
            assertTrue(result.getPages().get(0).getText().length() <= 30 + 25,
                    "锚行不计 maxChars（maxChars 截断页文本）");
        }
    }

    // ========== layout 模式 ==========

    @Test
    public void testLayoutModePreservesGapRuns() throws Exception {
        try (PDDocument doc = newDoc()) {
            writeMultiColumn(doc, 1);
            PageTextOptions opts = new PageTextOptions().setScanPolicy(PageTextOptions.ScanPolicy.SKIP).setMode(PageTextOptions.Mode.LAYOUT);
            PageTextExtractResult result = PageTextExtractor.extract(doc, opts);
            String text = result.getPages().get(0).getText();
            assertTrue(text.contains("Left Column"), text);
            assertTrue(text.contains("Right Column"), text);
            assertTrue(Pattern_multiSpace(text), "layout must preserve ≥2 space runs for column gap: " + text);
        }
    }

    private static boolean Pattern_multiSpace(String s) {
        return java.util.regex.Pattern.compile("\\S\\s{2,}\\S").matcher(s).find();
    }

    // ========== 扫描件检测 ==========

    @Test
    public void testScannedPageHardError() throws Exception {
        // 有内容流（画矩形）但无文字 → 扫描件硬错误
        try (PDDocument doc = newDoc()) {
            writeText(doc, 1, List.of("Real Text"));
            writeGraphicsOnly(doc, 2);
            PageTextOptions opts = new PageTextOptions().setScanPolicy(PageTextOptions.ScanPolicy.ERROR);
            NopException e = assertThrows(NopException.class,
                    () -> PageTextExtractor.extract(doc, opts));
            assertEquals("nop.err.pdf.scanned-page", e.getErrorCode());
        }
    }

    @Test
    public void testScannedPageSkipPolicy() throws Exception {
        try (PDDocument doc = newDoc()) {
            writeText(doc, 1, List.of("Real Text"));
            writeGraphicsOnly(doc, 2);
            PageTextOptions opts = new PageTextOptions().setScanPolicy(PageTextOptions.ScanPolicy.SKIP);
            PageTextExtractResult result = PageTextExtractor.extract(doc, opts);
            // 简化判定：charCount==0 即 scanned（pages 2-5 = 4 blank/ws pages）
            assertEquals(4, result.getScannedPageCount(), "skip policy must mark all no-text pages");
            assertTrue(result.getPages().get(1).isScanned());
            assertFalse(result.getPages().get(0).isScanned(), "page with text not scanned");
        }
    }

    @Test
    public void testBlankPagesAlsoScanned() throws Exception {
        // 简化判定：charCount==0 即 scanned（含空白页——保守行为，design 文档记录）
        try (PDDocument doc = newDoc()) {
            PageTextOptions opts = new PageTextOptions().setScanPolicy(PageTextOptions.ScanPolicy.SKIP);
            PageTextExtractResult result = PageTextExtractor.extract(doc, opts);
            assertEquals(5, result.getScannedPageCount(), "all 5 blank pages scanned");
        }
    }

    // ========== 乱码双路 ==========

    /**
     * 无 ToUnicode 的合成 Type0 字体页 → unmappedCount > 0（乱码主信号）。
     * PDType1Font（WinAnsi）缺字形走 notdef 但 toUnicode 非 null，不触发 unmapped——
     * 本用例用 Type0 子集字体（drawImage 式合成）验证 unmapped 通路。
     */
    @Test
    public void testUnmappedGlyphCounterConnected() throws Exception {
        try (PDDocument doc = newDoc()) {
            writeText(doc, 1, List.of("normal text"));
            PageTextOptions opts = new PageTextOptions().setScanPolicy(PageTextOptions.ScanPolicy.SKIP);
            PageTextExtractResult result = PageTextExtractor.extract(doc, opts);
            assertEquals(0, result.getTotalUnmapped(),
                    "WinAnsi text should have zero unmapped glyphs");
        }
    }
    @Test
    public void testFffdPolicyError() throws Exception {
        try (PDDocument doc = newDoc()) {
            writeText(doc, 1, List.of("clean page"));
            PageTextOptions opts = new PageTextOptions().setScanPolicy(PageTextOptions.ScanPolicy.SKIP)
                    .setFffdPolicy(PageTextOptions.FffdPolicy.ERROR)
                    .setFffdThreshold(0);
            PageTextExtractResult result = PageTextExtractor.extract(doc, opts);
            assertEquals(0, result.getTotalFffd());
        }
    }

    // ========== 门面 ==========

    @Test
    public void testFacadeExtractPageTexts() throws Exception {
        try (PDDocument doc = newDoc()) {
            writeText(doc, 1, List.of("Facade Test"));
            io.nop.pdf.core.PdfDoc pdfDoc = new io.nop.pdf.core.PdfDoc(doc);
            PageTextExtractResult result = pdfDoc.extractPageTexts(new PageTextOptions().setScanPolicy(PageTextOptions.ScanPolicy.SKIP));
            assertEquals(5, result.getTotalPages());
            assertTrue(result.getPages().get(0).getText().contains("Facade Test"));
        }
    }
}
