package io.nop.pdf.toc;

import io.nop.pdf.outline.PdfOutlineEntry;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI15：结构源路由——确定性决策表全分支 + 守卫 corrections + crosscheck + 钩子校验。
 */
public class TestStructureSourceRouter {

    private PDDocument newDoc(int pages) {
        PDDocument doc = new PDDocument();
        for (int i = 0; i < pages; i++)
            doc.addPage(new PDPage());
        return doc;
    }

    private Map<Integer, String> tocAt(int page) {
        Map<Integer, String> m = new HashMap<>();
        StringBuilder sb = new StringBuilder();
        for (int c = 1; c <= 5; c++)
            sb.append("Chapter ").append(c).append(" .......... ").append(c + 2).append('\n');
        m.put(page, sb.toString());
        return m;
    }

    private List<PdfOutlineEntry> outline(int... pages) {
        List<PdfOutlineEntry> ret = new ArrayList<>();
        for (int i = 0; i < pages.length; i++)
            ret.add(new PdfOutlineEntry().setTitle("Chapter " + i).setDepth(1).setPage(pages[i]));
        return ret;
    }

    @Test
    public void testOutlineNonEmptyWins() throws Exception {
        try (PDDocument doc = newDoc(10)) {
            RoutingDecision d = StructureSourceRouter.route(doc, outline(3, 5), tocAt(2), null, "auto");
            assertEquals("outline", d.getSource());
            assertTrue(d.getCorrections().isEmpty());
        }
    }

    @Test
    public void testTocFeasibleRoutesTocRegex() throws Exception {
        try (PDDocument doc = newDoc(10)) {
            RoutingDecision d = StructureSourceRouter.route(doc, List.of(), tocAt(2), null, "auto");
            assertEquals("toc-regex", d.getSource());
            assertTrue(d.getEntries().isEmpty(), "s0 语义：抽取留给回填");
            assertTrue(d.getCorrections().isEmpty());
        }
    }

    @Test
    public void testTocInfeasibleNoHookFallsToChunk() throws Exception {
        try (PDDocument doc = newDoc(10)) {
            // 目录行在第 8 页 > ceil(10*0.2)=2 → 不可行；无钩子 → chunk
            Map<Integer, String> texts = tocAt(8);
            RoutingDecision d = StructureSourceRouter.route(doc, List.of(), texts, null, "auto");
            assertEquals("chunk", d.getSource());
            assertTrue(d.getCorrections().stream().anyMatch(c -> c.contains("infeasible")), d.getCorrections().toString());
        }
    }

    @Test
    public void testHookProducesValidatedEntries() throws Exception {
        try (PDDocument doc = newDoc(10)) {
            Map<Integer, String> texts = tocAt(8);
            ReconstructHook hook = (totalPages, front) -> {
                List<PdfOutlineEntry> ret = new ArrayList<>();
                // page 99 clamp 到 10；depth 9 clamp 到 6；短 title 丢弃
                ret.add(new PdfOutlineEntry().setTitle("Reconstructed Chapter").setDepth(9).setPage(99));
                ret.add(new PdfOutlineEntry().setTitle("ab").setDepth(1).setPage(3));
                return ret;
            };
            RoutingDecision d = StructureSourceRouter.route(doc, List.of(), texts, hook, "auto");
            assertEquals("ai-reconstruct", d.getSource());
            assertEquals(1, d.getEntries().size(), "短 title 丢弃");
            assertEquals(10, d.getEntries().get(0).getPage(), "page clamp 到 totalPages");
            assertEquals(6, d.getEntries().get(0).getDepth(), "depth clamp 1..6");
        }
    }

    @Test
    public void testHookEmptyIsHonestZero() throws Exception {
        try (PDDocument doc = newDoc(10)) {
            Map<Integer, String> texts = tocAt(8);
            ReconstructHook hook = (totalPages, front) -> List.of();
            RoutingDecision d = StructureSourceRouter.route(doc, List.of(), texts, hook, "auto");
            // 0 条目=诚实 0 条目（s0 :103-106 语义，非失败——保持 ai-reconstruct；
            // pin 路径 infeasible 的 correction 仍保留）
            assertEquals("ai-reconstruct", d.getSource());
            assertEquals(0, d.getEntries().size());
        }
    }

    @Test
    public void testExplicitPinUnusableFallsThrough() throws Exception {
        try (PDDocument doc = newDoc(10)) {
            // 显式 pin toc-regex 但目录行在第 8 页不可行 → correction + 顺延 chunk
            RoutingDecision d = StructureSourceRouter.route(doc, List.of(), tocAt(8), null, "toc-regex");
            assertEquals("chunk", d.getSource());
            assertTrue(d.getCorrections().stream().anyMatch(c -> c.contains("infeasible")));
        }
    }

    @Test
    public void testCrosscheckImbalanceWarning() throws Exception {
        try (PDDocument doc = newDoc(10)) {
            // outline 2 条 vs toc 行 5 条：ratio 0.4 < 0.5 → WARN
            RoutingDecision d = StructureSourceRouter.route(doc, outline(3, 4), tocAt(2), null, "auto");
            assertEquals("outline", d.getSource());
            assertTrue(d.getCrosscheckWarnings().stream().anyMatch(w -> w.contains("imbalance")),
                    d.getCrosscheckWarnings().toString());
        }
    }

    @Test
    public void testValidateClampsAndDedupes() throws Exception {
        List<PdfOutlineEntry> raw = new ArrayList<>();
        raw.add(new PdfOutlineEntry().setTitle("Same Title").setDepth(2).setPage(3));
        raw.add(new PdfOutlineEntry().setTitle("same title").setDepth(1).setPage(3));
        raw.add(new PdfOutlineEntry().setTitle("Valid Entry").setDepth(1).setPage(0));
        List<PdfOutlineEntry> out = StructureSourceRouter.validateReconstructedEntries(raw, 10);
        assertEquals(2, out.size(), "同 (title,page) 去重 + page 0 clamp 到 1");
        assertEquals(1, out.get(0).getPage());
    }
}
