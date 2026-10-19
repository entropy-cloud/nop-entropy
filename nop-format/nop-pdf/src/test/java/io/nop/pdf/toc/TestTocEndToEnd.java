package io.nop.pdf.toc;

import io.nop.pdf.core.PdfDoc;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI15：端到端——合成 PDF（无 outline + 目录文本真实写入）经 PdfDoc 门面
 * routeStructureSource + backfillByToc 走通（R2-Minor-5 接线验证：
 * routeStructureSource 内部走 SortedPageTexts 提取，测试文本必须真实写入 PDF）。
 * 锚由测试以使用方身份手工合成（nop-pdf 不生成锚）。
 */
public class TestTocEndToEnd {

    private static void writeText(PDDocument doc, int pageIdx1Based, List<String> lines) throws Exception {
        PDPage page = doc.getPage(pageIdx1Based - 1);
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

    @Test
    public void testRouteThenBackfillThroughFacade() throws Exception {
        // 合成 PDF：12 页，目录在第 2 页（页数前 20% 内），文本真实写入 PDF
        PDDocument doc = new PDDocument();
        for (int i = 0; i < 12; i++)
            doc.addPage(new PDPage());
        PdfDoc pdfDoc = new PdfDoc(doc);

        List<String> tocLines = new ArrayList<>(List.of("Contents"));
        for (int c = 1; c <= 5; c++)
            tocLines.add("Chapter " + c + " Overview .......... " + (c + 3));
        writeText(doc, 2, tocLines);
        // 章节标题真实写入对应页（目录页码+3 → 物理页 5..9）
        for (int c = 1; c <= 5; c++)
            writeText(doc, c + 4, List.of("Chapter " + c + " Overview", "body"));

        // 路由：无 outline → toc 可行（首目录页 2 ≤ ceil(12×0.2)=3）→ toc-regex
        RoutingDecision decision = pdfDoc.routeStructureSource(null, "auto");
        assertEquals("toc-regex", decision.getSource(), "corrections=" + decision.getCorrections());
        assertEquals(5, decision.getTocLines().size(), "tocLines=" + decision.getTocLines());

        // 使用方合成带锚 md（锚=消费语义；页文本来自 SortedPageTexts 实际提取）
        Map<Integer, String> texts = SortedPageTexts.extract(doc);
        StringBuilder md = new StringBuilder();
        for (int p = 1; p <= 12; p++) {
            md.append("<a id=\"pdf-page-").append(p).append("\"></a>\n");
            md.append(texts.getOrDefault(p, "")).append('\n');
        }

        // 回填（v1 toc 路径）
        TocBackfiller.Result result = pdfDoc.backfillByToc(md.toString(), new BackfillOptions());
        String out = result.getMd();
        assertTrue(result.getStats().getInserted() >= 4, "inserted=" + result.getStats().getInserted());
        assertTrue(out.contains("# Chapter 1 Overview"), out);
        assertTrue(out.contains("# Chapter 5 Overview"), out);
    }

    @Test
    public void testChunkRouteWhenNoTocAndNoOutline() throws Exception {
        PDDocument doc = new PDDocument();
        for (int i = 0; i < 5; i++)
            doc.addPage(new PDPage());
        PdfDoc pdfDoc = new PdfDoc(doc);

        // 无 outline 且无目录行 → chunk
        RoutingDecision decision = pdfDoc.routeStructureSource(null, "auto");
        assertEquals("chunk", decision.getSource());

        // v2 回填经门面
        String md = "<a id=\"pdf-page-2\"></a>\nIntroduction\n";
        String out = pdfDoc.backfillByOutline(md,
                List.of(new io.nop.pdf.outline.PdfOutlineEntry()
                        .setTitle("Introduction").setDepth(1).setPage(2)));
        assertTrue(out.contains("# Introduction"), out);
    }
}
