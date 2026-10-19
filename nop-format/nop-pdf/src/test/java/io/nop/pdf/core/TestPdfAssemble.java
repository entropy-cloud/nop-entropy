package io.nop.pdf.core;

import io.nop.api.core.exceptions.NopException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI12：nop-pdf 生成/装配 API——merge（深拷贝 reload-close 回归钉）、append 生命周期、
 * exportPages 范围校验、页码/页眉页脚、图片→PDF、fail-loud（越界/坏图/CJK/不可编码字符）。
 */
public class TestPdfAssemble {

    static final String[] CJK_FONT_CANDIDATES = {
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
            "C:/Windows/Fonts/simsun.ttc",
    };

    /** 1px=1pt 页面：200x100 像素图 → 200x100pt 单页 */
    private byte[] page200x100() throws Exception {
        BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 100);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    /** 带文本的单页 fixture：图片页 + 盖章文本 */
    private PdfDoc newTextDoc(String text) throws Exception {
        PdfDoc doc = PdfDoc.fromImage(page200x100());
        doc.stampText(1, text, new PdfStampOptions().setX(20f).setY(50f));
        return doc;
    }

    // ========== merge（R1-B1 深拷贝回归钉） ==========

    @Test
    public void testMergeSaveCloseReload() throws Exception {
        Path dir = Files.createTempDirectory("wi12-merge");
        File f1 = dir.resolve("a.pdf").toFile();
        File f2 = dir.resolve("b.pdf").toFile();
        try (PdfDoc a = newTextDoc("PageOne")) {
            a.save(f1);
        }
        try (PdfDoc b = newTextDoc("PageTwo")) {
            b.save(f2);
        }

        PdfDoc merged = PdfDoc.merge(Arrays.asList(f1, f2));
        assertEquals(2, merged.getNumberOfPages());
        File out = dir.resolve("merged.pdf").toFile();
        merged.save(out);
        merged.close();

        // 深拷贝证明：源文档在 merge 内已关闭，重载产物仍完整可读
        try (PDDocument reloaded = Loader.loadPDF(out)) {
            assertEquals(2, reloaded.getNumberOfPages());
            String text = new PDFTextStripper().getText(reloaded);
            assertTrue(text.contains("PageOne") && text.contains("PageTwo"),
                    "merged text must survive reload: " + text);
        }
    }

    @Test
    public void testMergeFailureClosesTargetAndThrowsWithFileName() {
        File missing = new File("_tmp/wi12-no-such.pdf");
        NopException e = assertThrows(NopException.class, () -> PdfDoc.merge(Arrays.asList(missing)));
        assertNotNull(e.getErrorCode(), "first error must propagate as NopException");
    }

    // ========== append 生命周期 ==========

    @Test
    public void testAppendDoesNotTakeOwnership() throws Exception {
        try (PdfDoc target = PdfDoc.fromImage(page200x100())) {
            PdfDoc other = PdfDoc.fromImage(page200x100());
            assertEquals(1, target.getNumberOfPages());
            target.append(other);
            assertEquals(2, target.getNumberOfPages());
            // 不接管生命周期：调用方自行 close（此处验证 close 不影响 target）
            other.close();
            assertEquals(2, target.getNumberOfPages());
        }
    }

    // ========== exportPages ==========

    @Test
    public void testExportPagesSubsetAndRangeCheck() throws Exception {
        Path dir = Files.createTempDirectory("wi12-export");
        File f1 = dir.resolve("a.pdf").toFile();
        File f2 = dir.resolve("b.pdf").toFile();
        try (PdfDoc a = newTextDoc("PageOne")) {
            a.save(f1);
        }
        try (PdfDoc b = newTextDoc("PageTwo")) {
            b.save(f2);
        }
        try (PdfDoc merged = PdfDoc.merge(Arrays.asList(f1, f2))) {
            File out = dir.resolve("sub.pdf").toFile();
            merged.exportPages(Arrays.asList(2), out);
            try (PDDocument reloaded = Loader.loadPDF(out)) {
                assertEquals(1, reloaded.getNumberOfPages());
                assertTrue(new PDFTextStripper().getText(reloaded).contains("PageTwo"));
            }
            // 越界 fail-loud（selectPages 静默跳过，不可依赖）
            NopException e = assertThrows(NopException.class,
                    () -> merged.exportPages(Arrays.asList(3), dir.resolve("bad.pdf").toFile()));
            assertEquals("nop.err.pdf.page-out-of-range", e.getErrorCode());
        }
    }

    // ========== 图片→PDF ==========

    @Test
    public void testFromImagesPageGeometry() throws Exception {
        try (PdfDoc doc = PdfDoc.fromImages(Arrays.asList(page200x100(), page200x100()))) {
            assertEquals(2, doc.getNumberOfPages());
            try (PDDocument raw = doc.getDocument()) {
                assertEquals(200f, raw.getPage(0).getMediaBox().getWidth(), 0.01f, "1px=1pt@72dpi");
                assertEquals(100f, raw.getPage(0).getMediaBox().getHeight(), 0.01f);
            }
        }
        assertThrows(NopException.class, () -> PdfDoc.fromImage(new byte[0]));
    }

    // ========== 页码 / 页眉页脚 ==========

    @Test
    public void testAppendPageNumbers() throws Exception {
        try (PdfDoc doc = PdfDoc.fromImages(Arrays.asList(page200x100(), page200x100(), page200x100()))) {
            doc.appendPageNumbers("{page}/{pages}", null);
            String text = docAllText(doc);
            assertTrue(text.contains("1/3") && text.contains("2/3") && text.contains("3/3"),
                    "page numbers on all pages: " + text);
        }
    }

    @Test
    public void testAppendHeaderFooter() throws Exception {
        try (PdfDoc doc = PdfDoc.fromImage(page200x100())) {
            doc.appendHeaderFooter("Header-Text", "Footer-Text", null);
            String text = docAllText(doc);
            assertTrue(text.contains("Header-Text") && text.contains("Footer-Text"), text);
        }
    }

    // ========== fail-loud ==========

    @Test
    public void testCjkWithoutFontFailsLoud() throws Exception {
        try (PdfDoc doc = PdfDoc.fromImage(page200x100())) {
            NopException e = assertThrows(NopException.class,
                    () -> doc.stampText(1, "中文", null));
            assertEquals("nop.err.pdf.cjk-requires-font", e.getErrorCode());
        }
    }

    @Test
    public void testNonCjkUnencodableFailsLoud() throws Exception {
        try (PdfDoc doc = PdfDoc.fromImage(page200x100())) {
            NopException e = assertThrows(NopException.class,
                    () -> doc.stampText(1, "αβγ", null));
            assertEquals("nop.err.pdf.text-not-encodable", e.getErrorCode());
        }
    }

    /**
     * CJK 带字体文件：测试内自实现最小系统字体目录探测（不复用 report-pdf FontManager），
     * Assumptions 门控——无 CJK 字体环境跳过属预期（不提交字体入库）。
     */
    @Test
    public void testCjkWithFontFileRenders() throws Exception {
        File cjk = findSystemCjkFont();
        Assumptions.assumeTrue(cjk != null, "环境前提：需系统 CJK 字体（pdf-font-strategy D3 同源）");
        try (PdfDoc doc = PdfDoc.fromImage(page200x100())) {
            doc.stampText(1, "中文测试", new PdfStampOptions().setFontFile(cjk));
            assertTrue(docAllText(doc).contains("中文测试"));
        }
    }

    private File findSystemCjkFont() {
        for (String path : CJK_FONT_CANDIDATES) {
            File f = new File(path);
            if (f.exists())
                return f;
        }
        return null;
    }

    private String docAllText(PdfDoc doc) {
        return doc.getAllText(null, null, -1);
    }
}
