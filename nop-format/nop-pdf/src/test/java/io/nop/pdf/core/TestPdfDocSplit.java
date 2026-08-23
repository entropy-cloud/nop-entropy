package io.nop.pdf.core;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 修复 splitIntoPages 差一错误：最后一页此前永远不会被拆出（循环条件 i &lt; n）
 */
public class TestPdfDocSplit {

    @Test
    public void testSplitIntoPagesIncludesLastPage() throws Exception {
        File dir = new File("_tmp/test-pdf-split");
        dir.mkdirs();
        for (File f : dir.listFiles())
            f.delete();

        File pdfFile = new File(dir, "source.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            doc.save(pdfFile);
        }

        PdfDoc pdfDoc = PdfDoc.loadFromFile(pdfFile);
        File outDir = new File(dir, "pages");
        pdfDoc.splitIntoPages(outDir);

        File[] pages = outDir.listFiles();
        // 修复前只生成 1 个文件（缺最后一页）
        assertTrue(pages != null && pages.length == 2, "expect 2 pages, got "
                + (pages == null ? 0 : pages.length));
    }
}
