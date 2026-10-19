package io.nop.pdf.toc;

import io.nop.pdf.outline.PdfOutlineEntry;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WI15：结构源路由——确定性决策表（R1-M4 定版）：
 * (1) outline 非空 → outline；
 * (2) 显式指定 source 且可行 → 按指定（守卫修正记录；pin 不可行/钩子 null 时顺延后续分支并记 correction）；
 * (3) tocFirstPage（s0 TOC_LINE_RE 在前 12 页首命中）≤ ceil(nPages×20%) → toc-regex；
 * (4) 钩子非空 → front-pages 调钩子 + 确定性校验（失败记 correction 降 chunk；0 条目=诚实语义）；
 * (5) 否则 chunk。
 * crosscheck：outline 条目数 vs 目录行数（TOC_LINE_RE 口径）越出 [0.5, 2.0] 记 WARN（R1-m4）。
 */
public final class StructureSourceRouter {

    /**
     * s0-structure-source.mjs:23 TOC_LINE_RE 原文（路由证据口径——无附件形态、无 \r?；
     * 条目抽取用 TocLineDetector 的 TOC_ENTRY_RE）
     */
    static final Pattern TOC_LINE_RE = Pattern.compile(
            "^\\s*(.{3,140}?)[ .]{2,}([A-Za-z]?\\d{1,4}(?:-[A-Za-z]?\\d{1,4}[A-Za-z]?)?)\\s*$");

    static final int FRONT_SCAN_PAGES = 12;
    static final double TOC_WINDOW_RATIO = 0.2;
    static final int RECONSTRUCT_FIRST_PAGES = 10;
    static final int RECONSTRUCT_PAGE_CHARS = 1500;
    static final int CHUNK_LARGE_DOC_PAGES = 20;

    private StructureSourceRouter() {
    }

    public static RoutingDecision route(PDDocument doc, List<PdfOutlineEntry> outlineEntries,
                                 Map<Integer, String> pageTexts, ReconstructHook hook,
                                 String explicitSource) {
        int nPages = doc.getNumberOfPages();
        List<String> corrections = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 前 12 页目录行扫描（s0 :118-129）：路由证据 + crosscheck
        List<TocLine> tocLines = new ArrayList<>();
        Integer tocFirstPage = null;
        for (int p = 1; p <= Math.min(FRONT_SCAN_PAGES, nPages); p++) {
            String text = pageTexts.getOrDefault(p, "");
            if (text.isEmpty())
                continue;
            boolean found = false;
            for (String line : text.split("\r?\n")) {
                Matcher m = TOC_LINE_RE.matcher(line);
                if (m.matches()) {
                    tocLines.add(TocLineDetector.detect(line));
                    found = true;
                }
            }
            if (found && tocFirstPage == null)
                tocFirstPage = p;
        }
        tocLines.removeIf(java.util.Objects::isNull);

        int frontWindow = (int) Math.ceil(nPages * TOC_WINDOW_RATIO);
        boolean tocFeasible = tocFirstPage != null && tocFirstPage <= frontWindow;

        // crosscheck（s0 :196-213）：outline 条目数 vs toc 行数失衡告警（0 行也 WARN，F4）
        if (outlineEntries != null && !outlineEntries.isEmpty()) {
            double ratio = tocLines.isEmpty() ? Double.POSITIVE_INFINITY
                    : (double) outlineEntries.size() / tocLines.size();
            if (ratio < 0.5 || ratio > 2.0)
                warnings.add("outline/toc count imbalance: outline=" + outlineEntries.size()
                        + " tocLines=" + tocLines.size());
        }

        // (1) outline 非空 → outline
        if (outlineEntries != null && !outlineEntries.isEmpty())
            return new RoutingDecision("outline", outlineEntries, tocLines, corrections, warnings);

        String route = null;
        if (explicitSource != null && !"auto".equals(explicitSource)) {
            route = explicitSource;
            if ("toc-regex".equals(route) && !tocFeasible) {
                corrections.add("toc-regex infeasible (candidates first on page " + tocFirstPage
                        + ", window 1.." + frontWindow + ")");
                route = null;
            }
            if ("ai-reconstruct".equals(route) && hook == null) {
                corrections.add("ai-reconstruct requested but no hook provided");
                route = null;
            }
            if (route == null)
                corrections.add("explicit source '" + explicitSource + "' unusable, falling through decision table");
        }

        if (route == null) {
            if (tocFeasible) {
                route = "toc-regex";
            } else {
                corrections.add("toc-regex infeasible (candidates first on page " + tocFirstPage
                        + ", window 1.." + frontWindow + ")");
                if (hook != null) {
                    route = "ai-reconstruct";
                } else {
                    corrections.add("no reconstruct hook provided");
                    route = "chunk";
                }
            }
        }

        if ("ai-reconstruct".equals(route)) {
            List<PdfOutlineEntry> reconstructed = hook.reconstruct(nPages, frontPagesMap(pageTexts, nPages));
            List<PdfOutlineEntry> validated = validateReconstructedEntries(reconstructed, nPages);
            // 0 条目=诚实 0 条目（s0 :103-106 语义，非失败——closure audit F1 修正）
            return new RoutingDecision("ai-reconstruct", validated, tocLines, corrections, warnings);
        }

        if ("toc-regex".equals(route))
            return new RoutingDecision("toc-regex", new ArrayList<>(), tocLines, corrections, warnings);

        return new RoutingDecision(route, new ArrayList<>(), tocLines, corrections, warnings);
    }

    private static Map<Integer, String> frontPagesMap(Map<Integer, String> pageTexts, int nPages) {
        Map<Integer, String> ret = new LinkedHashMap<>();
        for (int p = 1; p <= Math.min(RECONSTRUCT_FIRST_PAGES, nPages); p++) {
            String text = pageTexts.getOrDefault(p, "");
            ret.put(p, text.length() > RECONSTRUCT_PAGE_CHARS ? text.substring(0, RECONSTRUCT_PAGE_CHARS) : text);
        }
        return ret;
    }

    /**
     * s0 validateReconstructedEntries（:69-82）直译：title≥3、depth clamp 1..6、
     * page clamp 1..nPages、按页排序、去重
     */
    static List<PdfOutlineEntry> validateReconstructedEntries(List<PdfOutlineEntry> raw, int nPages) {
        List<PdfOutlineEntry> clean = new ArrayList<>();
        for (PdfOutlineEntry e : raw == null ? List.<PdfOutlineEntry>of() : raw) {
            String title = e.getTitle() == null ? "" : e.getTitle().trim();
            if (title.length() < 3)
                continue;
            int depth = Math.min(Math.max(1, e.getDepth()), 6);
            Integer page = e.getPage();
            if (page == null)
                continue;
            int clamped = Math.min(Math.max(1, page), nPages);
            PdfOutlineEntry c = new PdfOutlineEntry().setTitle(title).setDepth(depth).setPage(clamped);
            clean.add(c);
        }
        clean.sort(Comparator.comparingInt(PdfOutlineEntry::getPage)
                .thenComparingInt(PdfOutlineEntry::getDepth));
        // 去重（同 dedupeEntries key：lowercase(title)+page）
        Set<String> seen = new HashSet<>();
        List<PdfOutlineEntry> out = new ArrayList<>();
        for (PdfOutlineEntry e : clean) {
            String key = e.getTitle().toLowerCase(java.util.Locale.ROOT) + "\u0000" + e.getPage();
            if (seen.add(key))
                out.add(e);
        }
        return out;
    }
}
