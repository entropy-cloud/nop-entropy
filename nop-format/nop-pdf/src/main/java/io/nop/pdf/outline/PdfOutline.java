package io.nop.pdf.outline;

import java.util.List;

/**
 * WI14：outline 抽取结果载体（entries 已按规范顺序处理：抽取 → 去重 → page-end → stats）。
 */
public class PdfOutline {
    private final List<PdfOutlineEntry> entries;
    private final PdfOutlineStats stats;

    public PdfOutline(List<PdfOutlineEntry> entries, PdfOutlineStats stats) {
        this.entries = entries;
        this.stats = stats;
    }

    public List<PdfOutlineEntry> getEntries() {
        return entries;
    }

    public PdfOutlineStats getStats() {
        return stats;
    }
}
