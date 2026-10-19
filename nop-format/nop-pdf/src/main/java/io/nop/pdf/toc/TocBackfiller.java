package io.nop.pdf.toc;

import io.nop.pdf.outline.PdfOutlineEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WI15：v1 toc 驱动回填（headingize :128-358 全机制清单直译——R1-M5/R2-Major-3）。
 * 输入=带锚 md（组件内部自行 isTocPage/抽取条目，非消费 TocLine 列表）。
 * 机制：isTocPage(≥3)/front-20% 门/目录带 maxGap=3/条目<3 放弃/NaN 守卫/
 * Figure-Table-样例过滤/tocDepth 深度过滤/pageOffset 扫描（off 2..40 四重采纳
 * 判据）/逻辑页单调扫描（scanFrom+extra≤12）/claimed-usedLines 去重/
 * SOURCE_HEADING_LIKE 守卫/页顶兜底/BODY_H1 pass。
 */
public final class TocBackfiller {

    private TocBackfiller() {
    }

    public static final class Result {
        private final String md;
        private final BackfillStats stats;

        public Result(String md, BackfillStats stats) {
            this.md = md;
            this.stats = stats;
        }

        public String getMd() {
            return md;
        }

        public BackfillStats getStats() {
            return stats;
        }
    }

    /** 页眉伪条目页码（"V17" 类字母前缀）——headingize :182 NaN 守卫 */
    static boolean isNumericPage(String ref) {
        try {
            Double.parseDouble(ref);
            return !Double.isNaN(Double.parseDouble(ref));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static Result backfill(String md, BackfillOptions opts) {
        boolean forceScan = opts != null && opts.isForceScan();
        int tocDepthOpt = opts == null ? 0 : opts.getTocDepth();
        BackfillStats stats = new BackfillStats();

        List<String> lines = new ArrayList<>(List.of(md.split("\r?\n", -1)));
        // 页块：{n, start, end}（含锚行）
        List<int[]> pages = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            java.util.regex.Matcher m = TocKeys.ANCHOR_RE.matcher(lines.get(i));
            if (m.find()) {
                int n = Integer.parseInt(m.group(1));
                pages.add(new int[]{n, i});
            }
        }
        if (pages.isEmpty())
            return new Result(md, stats);
        int[] pageEnds = new int[pages.size()];
        for (int i = 0; i < pages.size(); i++)
            pageEnds[i] = i + 1 < pages.size() ? pages.get(i + 1)[1] : lines.size();

        Map<Integer, Integer> startByPage = new HashMap<>();
        Map<Integer, Integer> endByPage = new HashMap<>();
        for (int i = 0; i < pages.size(); i++) {
            startByPage.put(pages.get(i)[0], pages.get(i)[1]);
            endByPage.put(pages.get(i)[0], pageEnds[i]);
        }

        // isTocPage：页内 TOC 条目命中 ≥3（cleanTitle 长度 ≥3）
        java.util.function.IntPredicate isTocPage = idx -> {
            int hits = 0;
            for (int i = pages.get(idx)[1] + 1; i < pageEnds[idx]; i++) {
                TocLine t = TocLineDetector.detect(lines.get(i));
                if (t != null && t.getTitle().length() >= 3)
                    hits++;
            }
            return hits >= 3;
        };

        // 目录区 = 首目录页起间隔 ≤3 的连续带；首目录页须在前 20% 内
        int firstTocIdx = -1;
        for (int i = 0; i < pages.size(); i++) {
            if (isTocPage.test(i)) {
                firstTocIdx = i;
                break;
            }
        }
        if (firstTocIdx < 0 || firstTocIdx > pages.size() * 0.2)
            return new Result(md, stats);
        int maxGap = 3;
        List<Integer> tocPageIdx = new ArrayList<>(List.of(firstTocIdx));
        for (int j = firstTocIdx + 1; j < pages.size(); j++) {
            if (pages.get(j)[0] - pages.get(tocPageIdx.get(tocPageIdx.size() - 1))[0] > maxGap)
                break;
            if (isTocPage.test(j))
                tocPageIdx.add(j);
        }
        int lastTocPage = pages.get(tocPageIdx.get(tocPageIdx.size() - 1))[0];

        // 条目抽取（全部过滤器直译）
        int indexSkipped = 0;
        int exampleSkipped = 0;
        List<PdfOutlineEntry> rawEntries = new ArrayList<>();
        List<String> rawLogical = new ArrayList<>();
        List<Integer> rawNumDepth = new ArrayList<>();
        List<Integer> rawIndent = new ArrayList<>();
        for (int idx : tocPageIdx) {
            for (int i = pages.get(idx)[1] + 1; i < pageEnds[idx]; i++) {
                TocLine t = TocLineDetector.detect(lines.get(i));
                if (t == null)
                    continue;
                String title = t.getTitle();
                if (title.length() < 3)
                    continue;
                String raw = rawTitleOf(lines.get(i));
                java.util.regex.Matcher num = java.util.regex.Pattern
                        .compile("^(\\d+(?:\\.\\d+)*)\\s").matcher(raw.trim());
                int numDepth = num.find() ? num.group(1).split("\\.").length : 0;
                int indent = raw.length() - raw.stripLeading().length();
                String ref = t.getPageLabel();
                boolean logical = ref.contains("-");
                // NaN 守卫已在 TocLineDetector 落地（伪页码条目整条丢弃）
                if (!logical && !isNumericPage(ref))
                    continue;
                // List of Figures/Tables 索引条目过滤（headingize :186-189）
                if (title.matches("(?i)^(figure|table|fig\\.?)\\s*\\d.*")) {
                    indexSkipped++;
                    continue;
                }
                // 示例行过滤（headingize :191-194）
                if (title.matches("(?i).*\\b[a-z0-9_]+\\.(csv|txt|json|xml|log|dat)\\b.*")
                        || title.matches("(?i)^file\\s*name\\s*:.*")
                        || title.matches(".*[a-z0-9]+(?:_[a-z0-9]+){2,}.*")) {
                    exampleSkipped++;
                    continue;
                }
                rawEntries.add(new PdfOutlineEntry().setTitle(title)
                        .setPage(logical ? null : Integer.parseInt(ref)));
                rawLogical.add(logical ? ref : null);
                rawNumDepth.add(numDepth);
                rawIndent.add(indent);
            }
        }

        // tocDepth 深度过滤（headingize :197-208）
        int tocDepth = tocDepthOpt;
        List<PdfOutlineEntry> entries = rawEntries;
        List<String> entLogical = rawLogical;
        int depthFiltered = 0;
        if (tocDepth > 0 && !rawEntries.isEmpty()) {
            int baseIndent = 0;
            boolean hasBase = false;
            for (int i = 0; i < rawEntries.size(); i++)
                if (rawNumDepth.get(i) == 1) {
                    baseIndent = hasBase ? Math.min(baseIndent, rawIndent.get(i)) : rawIndent.get(i);
                    hasBase = true;
                }
            List<PdfOutlineEntry> filtered = new ArrayList<>();
            List<String> filteredLogical = new ArrayList<>();
            for (int i = 0; i < rawEntries.size(); i++) {
                int depth = rawNumDepth.get(i) > 0 ? rawNumDepth.get(i)
                        : (rawIndent.get(i) > baseIndent + 1 ? 2 : 1);
                if (depth > tocDepth) {
                    depthFiltered++;
                    continue;
                }
                filtered.add(rawEntries.get(i));
                filteredLogical.add(rawLogical.get(i));
            }
            entries = filtered;
            entLogical = filteredLogical;
        }
        stats.setTocEntries(rawEntries.size()).setDepthFiltered(depthFiltered)
                .setIndexSkipped(indexSkipped).setExampleSkipped(exampleSkipped);
        List<Integer> tocPageNums = new ArrayList<>();
        for (int idx : tocPageIdx)
            tocPageNums.add(pages.get(idx)[0]);
        stats.setTocPages(tocPageNums);

        if (entries.size() < 3)
            return new Result(md, stats);

        // pageOffset 扫描（headingize :229-270）：无条件尝试、四重采纳判据
        if (!forceScan) {
            int base = scoreWithOffset(0, entries, entLogical, lastTocPage, startByPage, endByPage, lines);
            int bestOff = 0;
            int bestScore = base;
            for (int off = 2; off <= 40; off++) {
                int s = scoreWithOffset(off, entries, entLogical, lastTocPage, startByPage, endByPage, lines);
                if (s > bestScore) {
                    bestScore = s;
                    bestOff = off;
                }
            }
            if (bestOff != 0 && bestScore >= 10 && bestScore >= base * 1.2) {
                int second = 0;
                for (int off = 2; off <= 40; off++)
                    if (off != bestOff)
                        second = Math.max(second,
                                scoreWithOffset(off, entries, entLogical, lastTocPage, startByPage, endByPage, lines));
                int eligible = 0;
                for (int i = 0; i < entries.size(); i++)
                    if (entries.get(i).getPage() != null && entries.get(i).getPage() > lastTocPage)
                        eligible++;
                // JS 判据整数化：bestScore >= max(second*1.5, 10) 且 >= eligible*0.5
                if (bestScore * 2 >= second * 3 && bestScore >= 10 && bestScore * 2 >= eligible) {
                    for (int i = 0; i < entries.size(); i++)
                        if (entries.get(i).getPage() != null)
                            entries.get(i).setPage(entries.get(i).getPage() + bestOff);
                    stats.setPageOffset(bestOff);
                }
            }
        }

        // 主循环：claimed/usedLines 去重 + 扫描/相邻/兜底
        Set<Integer> usedLines = new HashSet<>();
        Map<String, Integer> claimed = new HashMap<>();
        int scanFrom = pageEnds[tocPageIdx.get(tocPageIdx.size() - 1)];
        record Pending(int lineIndex, String heading) {
        }
        List<Pending> pending = new ArrayList<>();

        for (int ei = 0; ei < entries.size(); ei++) {
            PdfOutlineEntry e = entries.get(ei);
            String logical = entLogical.get(ei);
            if (!forceScan && e.getPage() != null && e.getPage() <= lastTocPage)
                continue; // 指向目录区自身：跳过
            String key = TocKeys.keyify(e.getTitle());
            if (key.length() < 4) {
                stats.setUnmatched(stats.getUnmatched() + 1);
                continue;
            }
            int abs = -1;
            String how = null;
            if (logical != null || forceScan) {
                // 单调扫描：整行键相等优先；fallback=完整键前缀且 extra≤12
                String titleKey = TocKeys.keyify(e.getTitle());
                int fallback = -1;
                for (int i = scanFrom; i < lines.size(); i++) {
                    String t = lines.get(i).trim();
                    if (TocKeys.isSourceHeadingLike(t))
                        continue;
                    String k = TocKeys.keyify(t);
                    if (k.equals(titleKey)) {
                        abs = i;
                        how = "scan";
                        break;
                    }
                    if (fallback < 0 && k.startsWith(titleKey) && k.length() - titleKey.length() <= 12)
                        fallback = i;
                }
                if (abs < 0 && fallback >= 0) {
                    abs = fallback;
                    how = "scan";
                }
                if (abs >= 0)
                    scanFrom = abs + 1;
            } else {
                Integer targetStart = startByPage.get(e.getPage());
                if (targetStart == null) {
                    stats.setUnmatched(stats.getUnmatched() + 1);
                    continue;
                }
                int targetEnd = endByPage.get(e.getPage());
                int[] candStart = {targetStart,
                        startByPage.getOrDefault(e.getPage() + 1, -1),
                        startByPage.getOrDefault(e.getPage() - 1, -1)};
                int[] candEnd = {targetEnd,
                        endByPage.getOrDefault(e.getPage() + 1, -1),
                        endByPage.getOrDefault(e.getPage() - 1, -1)};
                for (int c = 0; c < 3 && abs < 0; c++) {
                    if (candStart[c] < 0)
                        continue;
                    List<String> slice = lines.subList(candStart[c] + 1, candEnd[c]);
                    int rel = TocKeys.findLine(slice, key);
                    if (rel >= 0) {
                        abs = candStart[c] + 1 + rel;
                        how = c == 0 ? "exact" : "adjacent";
                    }
                }
            }
            if (abs >= 0) {
                if (usedLines.contains(abs)) {
                    if (claimed.containsKey(key) && claimed.get(key) == abs) {
                        stats.setDuplicateSkipped(stats.getDuplicateSkipped() + 1);
                        continue;
                    }
                    stats.setUnmatched(stats.getUnmatched() + 1);
                    continue;
                }
                usedLines.add(abs);
                claimed.put(key, abs);
                int level = TocKeys.titleLevel(e.getTitle());
                pending.add(new Pending(abs, "#".repeat(level) + " " + lines.get(abs).trim()));
                stats.setInserted(stats.getInserted() + 1);
                if ("exact".equals(how))
                    stats.setByExact(stats.getByExact() + 1);
                else if ("adjacent".equals(how))
                    stats.setByAdjacent(stats.getByAdjacent() + 1);
                else
                    stats.setByScan(stats.getByScan() + 1);
            } else if (logical != null || forceScan) {
                // 扫描未命中：诚实 unmatched，绝不退回页码兜底
                stats.setUnmatched(stats.getUnmatched() + 1);
            } else {
                Integer targetStart = startByPage.get(e.getPage());
                int targetEnd = endByPage.get(e.getPage());
                int at = targetStart + 1;
                while (at < targetEnd && (lines.get(at).trim().isEmpty()
                        || TocKeys.isSourceHeadingLike(lines.get(at).trim())))
                    at++;
                if (at >= targetEnd)
                    at = Math.min(targetStart + 1, targetEnd - 1);
                if (TocKeys.isSourceHeadingLike(lines.get(at).trim())) {
                    stats.setUnmatched(stats.getUnmatched() + 1);
                    continue;
                }
                pending.add(new Pending(at, "#".repeat(TocKeys.titleLevel(e.getTitle())) + " " + e.getTitle()));
                stats.setInserted(stats.getInserted() + 1);
                stats.setByPageTop(stats.getByPageTop() + 1);
            }
        }

        pending.sort(Comparator.comparingInt(Pending::lineIndex).reversed());
        for (Pending it : pending)
            lines.add(it.lineIndex(), it.heading());

        // BODY_H1 pass（headingize :351-355）
        int bodyH1 = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (TocKeys.isBodyH1(lines.get(i).trim())) {
                lines.set(i, "# " + lines.get(i).trim());
                bodyH1++;
            }
        }
        stats.setBodyH1(bodyH1);

        return new Result(String.join("\n", lines), stats);
    }

    /** 从原始行还原 TOC match 的 group1（标题+前导空白），供缩进/编号分析 */
    private static String rawTitleOf(String line) {
        java.util.regex.Matcher m = TocLineDetector.TOC_ENTRY_RE.matcher(line);
        return m.matches() ? m.group(1) : line;
    }

    private static int scoreWithOffset(int off, List<PdfOutlineEntry> entries, List<String> entLogical,
                                       int lastTocPage, Map<Integer, Integer> startByPage,
                                       Map<Integer, Integer> endByPage, List<String> lines) {
        int n = 0;
        for (int i = 0; i < entries.size(); i++) {
            PdfOutlineEntry e = entries.get(i);
            if (e.getPage() == null || e.getPage() <= lastTocPage)
                continue;
            String tk = TocKeys.keyify(e.getTitle());
            if (tk.length() < 4)
                continue;
            Integer start = startByPage.get(e.getPage() + off);
            if (start == null)
                continue;
            Integer end = endByPage.get(e.getPage() + off);
            if (TocKeys.findLine(lines.subList(start + 1, end), tk) >= 0)
                n++;
        }
        return n;
    }
}
