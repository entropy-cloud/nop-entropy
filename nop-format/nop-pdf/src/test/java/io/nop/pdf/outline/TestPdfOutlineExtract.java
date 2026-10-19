package io.nop.pdf.outline;

import io.nop.pdf.core.PdfDoc;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI14：PDF outline（书签树）抽取——语义对齐 card-kb pdfio.mjs/s0-structure-source：
 * 四种 dest 形态（页引用/命名目标/显式页号//A GoTo）、空 title 同 depth 下钻、
 * 环守卫、坏 outline 降级空列表、去重、page-end 推算、stats、YAML 契约。
 * 全部 fixture 程序合成（不引入 card-kb 工具或原始 PDF）。
 */
public class TestPdfOutlineExtract {

    private PDDocument newDoc(int pageCount) {
        PDDocument doc = new PDDocument();
        for (int i = 0; i < pageCount; i++)
            doc.addPage(new PDPage());
        return doc;
    }

    private PDOutlineItem item(String title, PDPage dest) {
        PDOutlineItem item = new PDOutlineItem();
        item.setTitle(title);
        if (dest != null)
            item.setDestination(dest);
        return item;
    }

    private PDOutlineItem itemWithNamedDest(String title, String name) {
        PDOutlineItem item = new PDOutlineItem();
        item.setTitle(title);
        item.setDestination(new PDNamedDestination(name));
        return item;
    }

    /** 显式页号形态 [N /Fit]（N 为 0-based 页索引） */
    private PDOutlineItem itemWithExplicitPage(String title, int pageIdx0Based) {
        COSArray arr = new COSArray();
        arr.add(org.apache.pdfbox.cos.COSInteger.get(pageIdx0Based));
        arr.add(COSName.getPDFName("Fit"));
        PDOutlineItem item = new PDOutlineItem();
        item.setTitle(title);
        item.setDestination(new PDPageFitDestination(arr));
        return item;
    }

    /** /A GoTo action（D 为页引用目标）——R1-M2 */
    private PDOutlineItem itemWithGoToAction(String title, PDPage dest) {
        PDOutlineItem item = new PDOutlineItem();
        item.setTitle(title);
        PDActionGoTo goTo = new PDActionGoTo();
        PDPageFitDestination d = new PDPageFitDestination();
        d.setPage(dest);
        goTo.setDestination(d);
        item.setAction(goTo);
        return item;
    }

    // ========== 基本遍历与 page-end ==========

    @Test
    public void testPageRefEntriesAndPageEnd() throws Exception {
        try (PDDocument doc = newDoc(3)) {
            PDDocumentOutline outline = new PDDocumentOutline();
            PDOutlineItem c1 = item("C1", doc.getPage(0));
            PDOutlineItem s1 = item("S1", doc.getPage(1));
            c1.addLast(s1);
            outline.addLast(c1);
            outline.addLast(item("C2", doc.getPage(2)));
            doc.getDocumentCatalog().setDocumentOutline(outline);

            PdfOutline result = new PdfDoc(doc).extractOutline();
            List<PdfOutlineEntry> entries = result.getEntries();
            assertEquals(3, entries.size());
            assertEquals("C1", entries.get(0).getTitle());
            assertEquals(1, entries.get(0).getDepth());
            assertEquals(1, entries.get(0).getPage());
            assertEquals(2, entries.get(0).getPageEnd(), "next page-carrying entry is S1@2");
            assertEquals("S1", entries.get(1).getTitle());
            assertEquals(2, entries.get(1).getDepth(), "child depth = parent + 1");
            assertEquals(2, entries.get(1).getPage());
            assertEquals(3, entries.get(1).getPageEnd());
            assertEquals("C2", entries.get(2).getTitle());
            assertEquals(3, entries.get(2).getPage());
            assertEquals(3, entries.get(2).getPageEnd(), "last entry caps to total-pages");

            assertEquals(3, result.getStats().getEntries());
            assertEquals(2, result.getStats().getMaxDepth());
            assertEquals(0, result.getStats().getUnresolvedPages());
        }
    }

    // ========== 命名目标（legacy /Dests 查找） ==========

    @Test
    public void testNamedDestinationAndUnresolved() throws Exception {
        try (PDDocument doc = newDoc(3)) {
            // legacy /Dests 字典注册命名目标（findNamedDestinationPage 双源之一）
            COSArray destArr = new COSArray();
            destArr.add(doc.getPage(1));
            destArr.add(COSName.getPDFName("Fit"));
            org.apache.pdfbox.cos.COSDictionary dests = new org.apache.pdfbox.cos.COSDictionary();
            dests.setItem("named-1", destArr);
            doc.getDocumentCatalog().getCOSObject().setItem(COSName.DESTS, dests);

            PDDocumentOutline outline = new PDDocumentOutline();
            outline.addLast(itemWithNamedDest("Named", "named-1"));
            outline.addLast(itemWithNamedDest("Missing", "no-such-entry"));
            doc.getDocumentCatalog().setDocumentOutline(outline);

            PdfOutline result = new PdfDoc(doc).extractOutline();
            List<PdfOutlineEntry> entries = result.getEntries();
            assertEquals(2, entries.size());
            assertEquals("Named", entries.get(0).getTitle());
            assertEquals(2, entries.get(0).getPage(), "named dest must resolve via legacy /Dests");
            assertEquals(3, entries.get(0).getPageEnd());
            assertEquals("Missing", entries.get(1).getTitle());
            assertNull(entries.get(1).getPage(), "unresolved named dest → page null");
            assertNull(entries.get(1).getPageEnd(), "page==null → no page-end");
            assertEquals(1, result.getStats().getUnresolvedPages());
        }
    }

    // ========== 显式页号 ==========

    @Test
    public void testExplicitPageNumberDestination() throws Exception {
        try (PDDocument doc = newDoc(6)) {
            PDDocumentOutline outline = new PDDocumentOutline();
            outline.addLast(itemWithExplicitPage("Jump", 4));
            doc.getDocumentCatalog().setDocumentOutline(outline);

            List<PdfOutlineEntry> entries = new PdfDoc(doc).extractOutline().getEntries();
            assertEquals(1, entries.size());
            assertEquals(5, entries.get(0).getPage(), "explicit 0-based page index 4 → 1-based 5");
            assertEquals(6, entries.get(0).getPageEnd(), "last entry caps to total-pages");
        }
    }

    // ========== /A GoTo action 兜底（R1-M2） ==========

    @Test
    public void testGoToActionFallback() throws Exception {
        try (PDDocument doc = newDoc(3)) {
            PDDocumentOutline outline = new PDDocumentOutline();
            outline.addLast(itemWithGoToAction("GoToEntry", doc.getPage(2)));
            doc.getDocumentCatalog().setDocumentOutline(outline);

            List<PdfOutlineEntry> entries = new PdfDoc(doc).extractOutline().getEntries();
            assertEquals(1, entries.size());
            assertEquals(3, entries.get(0).getPage(), "/A GoTo destination must resolve");
        }
    }

    /**
     * /A 与 /Dest 并存（畸形输入）：严格 pdfjs 序——/A 存在即 /Dest 不参与。
     * 非 GoTo /A + 有效 /Dest → page=null；GoTo /A + 不同 /Dest → 取 /A 的页。
     */
    @Test
    public void testActionOverridesDestWhenBothPresent() throws Exception {
        try (PDDocument doc = newDoc(3)) {
            PDDocumentOutline outline = new PDDocumentOutline();
            // 非 GoTo /A（URI 占位）+ 有效 /Dest → pdfjs 语义 page=null
            PDOutlineItem uriAction = item("UriAction", doc.getPage(1));
            org.apache.pdfbox.pdmodel.interactive.action.PDActionURI uri =
                    new org.apache.pdfbox.pdmodel.interactive.action.PDActionURI();
            uri.setURI("https://example.com");
            uriAction.setAction(uri);
            outline.addLast(uriAction);

            // GoTo /A 指向 page2 + /Dest 指向 page0 → 取 /A 的页
            PDOutlineItem both = item("GoToWins", doc.getPage(0));
            PDActionGoTo goTo = new PDActionGoTo();
            PDPageFitDestination d = new PDPageFitDestination();
            d.setPage(doc.getPage(2));
            goTo.setDestination(d);
            both.setAction(goTo);
            outline.addLast(both);

            doc.getDocumentCatalog().setDocumentOutline(outline);

            List<PdfOutlineEntry> entries = new PdfDoc(doc).extractOutline().getEntries();
            assertEquals(2, entries.size());
            assertEquals("UriAction", entries.get(0).getTitle());
            assertNull(entries.get(0).getPage(), "non-GoTo action must yield page=null even with /Dest");
            assertEquals("GoToWins", entries.get(1).getTitle());
            assertEquals(3, entries.get(1).getPage(), "GoTo /A must win over /Dest");
        }
    }

    /** /Outlines 为非 dict（畸形）：降级空列表 */
    @Test
    public void testNonDictOutlinesDegradesToEmpty() throws Exception {
        try (PDDocument doc = newDoc(1)) {
            doc.getDocumentCatalog().getCOSObject()
                    .setItem(COSName.OUTLINES, org.apache.pdfbox.cos.COSInteger.get(1));
            assertTrue(new PdfDoc(doc).extractOutline().getEntries().isEmpty(),
                    "non-dict /Outlines must degrade to empty list");
        }
    }

    // ========== 空 title 同 depth 下钻 ==========

    @Test
    public void testEmptyTitleChildrenSameDepth() throws Exception {
        try (PDDocument doc = newDoc(2)) {
            PDDocumentOutline outline = new PDDocumentOutline();
            PDOutlineItem empty = item("", doc.getPage(0));
            empty.addLast(item("Real", doc.getPage(1)));
            outline.addLast(empty);
            doc.getDocumentCatalog().setDocumentOutline(outline);

            List<PdfOutlineEntry> entries = new PdfDoc(doc).extractOutline().getEntries();
            assertEquals(1, entries.size(), "empty title produces no entry");
            assertEquals("Real", entries.get(0).getTitle());
            assertEquals(1, entries.get(0).getDepth(), "children of empty title stay at same depth");
        }
    }

    // ========== 环守卫（R1-M1） ==========

    @Test
    public void testSelfLoopTerminates() throws Exception {
        try (PDDocument doc = newDoc(2)) {
            PDDocumentOutline outline = new PDDocumentOutline();
            PDOutlineItem a = item("A", doc.getPage(0));
            PDOutlineItem b = item("B", doc.getPage(1));
            outline.addLast(a);
            a.insertSiblingAfter(b);
            // 人为构造环：B.Next → A
            b.getCOSObject().setItem(COSName.NEXT, a.getCOSObject());
            doc.getDocumentCatalog().setDocumentOutline(outline);

            List<PdfOutlineEntry> entries = new PdfDoc(doc).extractOutline().getEntries();
            assertEquals(2, entries.size(), "cycle must terminate without duplicates");
            assertEquals("A", entries.get(0).getTitle());
            assertEquals("B", entries.get(1).getTitle());
        }
    }

    // ========== 坏 outline 降级（R1-m4） ==========

    @Test
    public void testBadOutlinesDegradeToEmpty() throws Exception {
        // (a) 无 /Outlines
        try (PDDocument doc = newDoc(1)) {
            assertTrue(new PdfDoc(doc).extractOutline().getEntries().isEmpty());
        }
        // (b) 空 PDDocumentOutline（无 /First）
        try (PDDocument doc = newDoc(1)) {
            doc.getDocumentCatalog().setDocumentOutline(new PDDocumentOutline());
            assertTrue(new PdfDoc(doc).extractOutline().getEntries().isEmpty());
        }
    }

    // ========== 去重顺序（R1-M3：先去重后 page-end） ==========

    @Test
    public void testDedupeBeforePageEnd() throws Exception {
        try (PDDocument doc = newDoc(3)) {
            PDDocumentOutline outline = new PDDocumentOutline();
            outline.addLast(item("X", doc.getPage(0)));
            outline.addLast(item("X", doc.getPage(0)));
            outline.addLast(item("Y", doc.getPage(1)));
            doc.getDocumentCatalog().setDocumentOutline(outline);

            List<PdfOutlineEntry> entries = new PdfDoc(doc).extractOutline().getEntries();
            assertEquals(2, entries.size(), "(title,page) duplicates removed, first kept");
            assertEquals("X", entries.get(0).getTitle());
            // 顺序对版：X 的 page-end 取去重后下一条带页条目 Y.page=2（若反序会错取被去重的 B.page=1）
            assertEquals(2, entries.get(0).getPageEnd());
            assertEquals("Y", entries.get(1).getTitle());
            assertEquals(3, entries.get(1).getPageEnd());
        }
    }

    // ========== YAML 契约 ==========

    @Test
    public void testYamlContractShape() throws Exception {
        try (PDDocument doc = newDoc(3)) {
            COSArray destArr = new COSArray();
            destArr.add(doc.getPage(1));
            destArr.add(COSName.getPDFName("Fit"));
            org.apache.pdfbox.cos.COSDictionary dests = new org.apache.pdfbox.cos.COSDictionary();
            dests.setItem("named-1", destArr);
            doc.getDocumentCatalog().getCOSObject().setItem(COSName.DESTS, dests);

            PDDocumentOutline outline = new PDDocumentOutline();
            outline.addLast(itemWithNamedDest("Named", "named-1"));
            outline.addLast(itemWithNamedDest("Missing", "missing"));
            doc.getDocumentCatalog().setDocumentOutline(outline);

            String yaml = new PdfDoc(doc).extractOutlineYaml("t1", "src.pdf");

            Map<String, Object> root = new Yaml().load(yaml);
            assertEquals("t1", root.get("target"));
            assertEquals("src.pdf", root.get("source"));
            assertEquals("pdf-outline", root.get("outline-source"));
            assertEquals(3, root.get("total-pages"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entries = (List<Map<String, Object>>) root.get("entries");
            assertEquals(2, entries.size());
            Map<String, Object> named = entries.get(0);
            assertEquals("Named", named.get("title"));
            assertEquals(2, named.get("page"));
            assertEquals(3, named.get("page-end"));
            Map<String, Object> missing = entries.get(1);
            assertTrue(missing.containsKey("page"), "page==null 时键保留");
            assertNull(missing.get("page"));
            assertFalse(missing.containsKey("page-end"), "page==null 条目无 page-end 键");

            @SuppressWarnings("unchecked")
            Map<String, Object> stats = (Map<String, Object>) root.get("stats");
            assertEquals(2, stats.get("entries"));
            assertEquals(1, stats.get("max-depth"));
            assertEquals(1, stats.get("unresolved-pages"));
        }
    }

    // ========== 端到端：YAML 落盘回读 ==========

    @Test
    public void testYamlRoundtripThroughFile() throws Exception {
        try (PDDocument doc = newDoc(2)) {
            PDDocumentOutline outline = new PDDocumentOutline();
            outline.addLast(item("Chapter", doc.getPage(0)));
            doc.getDocumentCatalog().setDocumentOutline(outline);

            String yaml = new PdfDoc(doc).extractOutlineYaml("t", "s");
            java.nio.file.Path path = java.nio.file.Path.of("_tmp", "wi14-outline.yaml");
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.writeString(path, yaml);

            Map<String, Object> reloaded = new Yaml().load(java.nio.file.Files.readString(path));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entries = (List<Map<String, Object>>) reloaded.get("entries");
            assertEquals(1, entries.size());
            assertEquals("Chapter", entries.get(0).get("title"));
            assertEquals(1, entries.get(0).get("page"));
            assertEquals(2, entries.get(0).get("page-end"));
        }
    }
}
