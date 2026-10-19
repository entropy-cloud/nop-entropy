package io.nop.pdf.outline;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * WI14：PDF 原生 outline（书签树）→ 扁平结构化条目。语义逐条对齐 card-kb
 * pdfio.mjs extractOutlineEntries/destToPageIndex 与 s0-structure-source.mjs
 * fillPageEnds/dedupeEntries/statsOf（对版基准）。
 *
 * <p>规范处理顺序（顺序本身是对版契约，R1-M3）：抽取 → 去重 → page-end → stats。
 *
 * <p>防御：坏 outline（根缺失/异常）降级为空列表；遍历带 visited 环守卫
 * （COSDictionary 身份，对齐 pdfjs RefSet——PDFBox 裸走 /First//Next 链遇环不抛，
 * 无守卫会死循环到 OOM，R1-M1）。
 */
public final class PdfOutlineExtractor {

    private PdfOutlineExtractor() {
    }

    public static PdfOutline extract(PDDocument doc) {
        List<PdfOutlineEntry> flat = new ArrayList<>();
        Set<COSDictionary> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        PDDocumentCatalog catalog = doc.getDocumentCatalog();
        PDDocumentOutline outline;
        try {
            outline = catalog.getDocumentOutline();
        } catch (Exception e) {
            outline = null;
        }
        if (outline != null) {
            walkNode(outline, 1, flat, visited, catalog);
        }

        // 规范顺序：先去重，后 page-end，最后 stats（R1-M3）
        dedupe(flat);
        fillPageEnds(flat, doc.getNumberOfPages());
        return new PdfOutline(flat, statsOf(flat));
    }

    private static void walkNode(PDOutlineNode node, int depth, List<PdfOutlineEntry> flat,
                                 Set<COSDictionary> visited, PDDocumentCatalog catalog) {
        PDOutlineItem item = node.getFirstChild();
        while (item != null) {
            if (!visited.add(item.getCOSObject()))
                return; // 环：已处理条目，终止本链（对齐 pdfjs RefSet skip——按 COSDictionary 身份判重）

            String title = item.getTitle();
            title = title == null ? null : title.trim();

            Integer page = resolvePage(item, catalog);

            if (title == null || title.isEmpty()) {
                // 空 title：不产出条目，children 保持同 depth 下钻（pdfjs parity）
                if (item.hasChildren())
                    walkNode(item, depth, flat, visited, catalog);
            } else {
                PdfOutlineEntry entry = new PdfOutlineEntry()
                        .setTitle(title)
                        .setDepth(depth)
                        .setPage(page);
                flat.add(entry);
                if (item.hasChildren())
                    walkNode(item, depth + 1, flat, visited, catalog);
            }

            item = item.getNextSibling();
        }
    }

    /**
     * 页解析两形态（R1-B1，PDFBox 3.0.8 真实 API）：PDNamedDestination →
     * findNamedDestinationPage（同时查 /Names/Dests name tree 与 legacy /Dests）；
     * PDPageDestination → retrievePageNumber（页引用与显式页号通吃，-1 与异常一律归
     * null——PDDestination.create 对 [N] 单元素数组等怪形态抛 IOException，属可接受
     * 降级差异）。
     *
     * <p>/A 与 /Dest 优先级（closure audit 发现 1 修正为严格 pdfjs 序）：/A 存在时
     * /Dest 完全不参与——GoTo action 解析其 D，非 GoTo action 归 null；仅 /A 缺失时
     * 才读 /Dest（pdfjs parseDestDictionary 语义）。
     */
    private static Integer resolvePage(PDOutlineItem item, PDDocumentCatalog catalog) {
        PDDestination dest;
        PDAction action = item.getAction();
        if (action != null) {
            if (!(action instanceof PDActionGoTo goTo))
                return null; // 非 GoTo action：pdfjs 语义下 page=null
            try {
                dest = goTo.getDestination();
            } catch (IOException e) {
                return null;
            }
        } else {
            try {
                dest = item.getDestination();
            } catch (IOException e) {
                return null;
            }
        }
        if (dest == null)
            return null;
        try {
            if (dest instanceof PDNamedDestination named) {
                PDPageDestination resolved = catalog.findNamedDestinationPage(named);
                return pageNumber(resolved);
            }
            if (dest instanceof PDPageDestination pageDest)
                return pageNumber(pageDest);
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private static Integer pageNumber(PDPageDestination dest) {
        if (dest == null)
            return null;
        int pageIdx = dest.retrievePageNumber();
        return pageIdx < 0 ? null : pageIdx + 1;
    }

    /**
     * key=lowercase(title)+\u0000+page，保留首条（s0 dedupeEntries parity）
     */
    private static void dedupe(List<PdfOutlineEntry> entries) {
        Set<String> seen = new HashSet<>();
        List<PdfOutlineEntry> out = new ArrayList<>(entries.size());
        for (PdfOutlineEntry e : entries) {
            String key = e.getTitle().toLowerCase(Locale.ROOT) + '\u0000' + e.getPage();
            if (seen.add(key))
                out.add(e);
        }
        entries.clear();
        entries.addAll(out);
    }

    /**
     * 仅对 page!=null 条目：page-end = 其后第一条带页条目的 page（无则 total-pages），
     * cap 到 total-pages；page==null 条目无 page-end（s0 fillPageEnds parity）
     */
    private static void fillPageEnds(List<PdfOutlineEntry> entries, int totalPages) {
        for (int i = 0; i < entries.size(); i++) {
            PdfOutlineEntry e = entries.get(i);
            if (e.getPage() == null)
                continue;
            Integer next = null;
            for (int j = i + 1; j < entries.size(); j++) {
                if (entries.get(j).getPage() != null) {
                    next = entries.get(j).getPage();
                    break;
                }
            }
            int end = next != null ? next : totalPages;
            e.setPageEnd(Math.min(end, totalPages));
        }
    }

    private static PdfOutlineStats statsOf(List<PdfOutlineEntry> entries) {
        PdfOutlineStats stats = new PdfOutlineStats();
        stats.setEntries(entries.size());
        int maxDepth = 0;
        int unresolved = 0;
        for (PdfOutlineEntry e : entries) {
            maxDepth = Math.max(maxDepth, e.getDepth());
            if (e.getPage() == null)
                unresolved++;
        }
        stats.setMaxDepth(maxDepth);
        stats.setUnresolvedPages(unresolved);
        return stats;
    }

}
