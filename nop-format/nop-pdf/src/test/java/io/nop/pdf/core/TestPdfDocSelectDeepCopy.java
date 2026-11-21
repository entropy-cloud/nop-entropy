package io.nop.pdf.core;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归钉（plan 2586）：selectPages/selectPageRange/selectPage 必须深拷贝——
 * 源文档关闭后派生文档仍可保存/盖章（此前 addPage 直接共享源 PDPage，PDFBox 3.x 关闭源后派生文档不可用）。
 */
public class TestPdfDocSelectDeepCopy {

    File newPdf(int pageCount, String name) throws Exception {
        File dir = new File("_tmp/test-pdf-select-deep-copy");
        dir.mkdirs();
        File file = new File(dir, name);
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pageCount; i++)
                doc.addPage(new PDPage());
            doc.save(file);
        }
        return file;
    }

    @Test
    public void testSelectPagesUsableAfterSourceClose() throws Exception {
        File sourceFile = newPdf(3, "source.pdf");
        File out = new File("_tmp/test-pdf-select-deep-copy", "selected.pdf");
        try (PdfDoc source = PdfDoc.loadFromFile(sourceFile)) {
            PdfDoc selected = source.selectPages(java.util.Arrays.asList(1, 3));
            try {
                source.close();
                // 源文档已关闭：派生文档必须仍可保存（深拷贝钉）
                selected.save(out);
                try (PdfDoc reloaded = PdfDoc.loadFromFile(out)) {
                    assertEquals(2, reloaded.getNumberOfPages());
                }
            } finally {
                io.nop.commons.util.IoHelper.safeClose(selected);
            }
        }
    }

    @Test
    public void testSelectPageStampDoesNotPolluteSource() throws Exception {
        File sourceFile = newPdf(2, "source2.pdf");
        try (PdfDoc source = PdfDoc.loadFromFile(sourceFile)) {
            PdfDoc page1 = source.selectPage(1);
            try {
                // 对派生文档盖章不得污染源文档
                page1.stampText(1, "X", null);
            } finally {
                io.nop.commons.util.IoHelper.safeClose(page1);
            }
            assertEquals(2, source.getNumberOfPages(), "source page count must be intact");
        }
    }

    @Test
    public void testSelectPageRangeDeepCopy() throws Exception {
        File sourceFile = newPdf(3, "source3.pdf");
        File out = new File("_tmp/test-pdf-select-deep-copy", "range.pdf");
        try (PdfDoc source = PdfDoc.loadFromFile(sourceFile)) {
            PdfDoc range = source.selectPageRange(2, 3);
            try {
                source.close();
                range.save(out);
                assertTrue(out.length() > 0);
            } finally {
                io.nop.commons.util.IoHelper.safeClose(range);
            }
        }
    }
}
