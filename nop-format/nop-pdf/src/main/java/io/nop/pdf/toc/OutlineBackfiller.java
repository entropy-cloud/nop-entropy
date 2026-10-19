package io.nop.pdf.toc;

import io.nop.pdf.outline.PdfOutlineEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WI15：v2 outline 驱动回填（headingizeFromOutline :70-126 直译）。
 * 整键相等（非前缀）；窗口=[上一物理页有锚时其锚行+1，否则目标页锚行+1， 下一锚行)；
 * 命中原位升级，未命中插在目标页锚后；层级=depth clamp 1..6；不消费 pageEnd；
 * 无锚→原样返回+空 stats。
 */
public final class OutlineBackfiller {

    private OutlineBackfiller() {
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

    public static Result backfill(String md, List<PdfOutlineEntry> entries) {
        BackfillStats stats = new BackfillStats().setOutlineEntries(entries.size());
        List<String> lines = new ArrayList<>(List.of(md.split("\r?\n", -1)));
        // 页块扫描：{n, start, end}
        List<int[]> pages = new ArrayList<>();
        List<Integer> pageNums = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            java.util.regex.Matcher m = TocKeys.ANCHOR_RE.matcher(lines.get(i));
            if (m.find()) {
                pages.add(new int[]{Integer.parseInt(m.group(1)), i});
                pageNums.add(Integer.parseInt(m.group(1)));
            }
        }
        if (pages.isEmpty())
            return new Result(md, stats); // 无锚：原样返回（headingize :89-90）
        int[] ends = new int[pages.size()];
        for (int i = 0; i < pages.size(); i++)
            ends[i] = i + 1 < pages.size() ? pages.get(i + 1)[1] : lines.size();
        Map<Integer, Integer> startByPage = new LinkedHashMap<>();
        Map<Integer, Integer> endByPage = new LinkedHashMap<>();
        for (int i = 0; i < pages.size(); i++) {
            startByPage.put(pages.get(i)[0], pages.get(i)[1]);
            endByPage.put(pages.get(i)[0], ends[i]);
        }

        record Pending(int lineIndex, String heading) {
        }
        List<Pending> replacements = new ArrayList<>();
        Map<Integer, List<String>> insertAt = new LinkedHashMap<>();
        int inserted = 0;
        int replaced = 0;
        int unmatched = 0;
        int skippedNoAnchor = 0;

        for (PdfOutlineEntry e : entries) {
            String title = TocKeys.cleanTitle(e.getTitle() == null ? "" : e.getTitle());
            if (title.length() < 3) {
                unmatched++;
                continue;
            }
            Integer page = e.getPage();
            Integer start = page == null ? null : startByPage.get(page);
            if (start == null) {
                skippedNoAnchor++;
                continue;
            }
            int level = Math.min(Math.max(1, e.getDepth()), 6);
            String heading = "#".repeat(level) + " " + title;
            String tKey = TocKeys.keyify(title);
            int hit = -1;
            // 窗口：目的地页前一物理页有锚时取其锚行+1（章节 opener 常在上一页底部），
            // 否则本页锚行+1；窗口末=本页块末尾（R2-Major-1 修正语义）
            Integer prevPage = page - 1;
            Integer prevStart = prevPage >= 1 ? startByPage.get(prevPage) : null;
            int winStart = prevStart != null ? prevStart + 1 : start + 1;
            int winEnd = endByPage.get(page);
            for (int i = winStart; i < winEnd; i++) {
                String l = lines.get(i);
                if (l == null || l.trim().isEmpty() || TocKeys.ANCHOR_RE.matcher(l).find())
                    continue;
                if (TocKeys.keyify(l).equals(tKey)) {
                    hit = i;
                    break;
                }
            }
            if (hit >= 0) {
                replacements.add(new Pending(hit, heading));
                replaced++;
            } else {
                int at = start + 1;
                insertAt.computeIfAbsent(at, k -> new ArrayList<>()).add(heading);
                inserted++;
            }
        }

        replacements.sort(Comparator.comparingInt(Pending::lineIndex).reversed());
        for (Pending r : replacements)
            lines.set(r.lineIndex(), r.heading());
        List<Integer> anchors = new ArrayList<>(insertAt.keySet());
        anchors.sort(Comparator.reverseOrder());
        for (int idx : anchors)
            lines.addAll(idx, insertAt.get(idx));

        stats.setInserted(inserted).setReplaced(replaced).setUnmatched(unmatched).setSkippedNoAnchor(skippedNoAnchor);
        return new Result(String.join("\n", lines), stats);
    }
}
