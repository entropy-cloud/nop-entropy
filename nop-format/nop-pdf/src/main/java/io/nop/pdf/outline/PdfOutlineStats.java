package io.nop.pdf.outline;

/**
 * WI14：outline 机械化统计（语义对齐 card-kb s0 statsOf）。
 */
public class PdfOutlineStats {
    private int entries;
    private int maxDepth;
    private int unresolvedPages;

    public int getEntries() {
        return entries;
    }

    public PdfOutlineStats setEntries(int entries) {
        this.entries = entries;
        return this;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public PdfOutlineStats setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
        return this;
    }

    public int getUnresolvedPages() {
        return unresolvedPages;
    }

    public PdfOutlineStats setUnresolvedPages(int unresolvedPages) {
        this.unresolvedPages = unresolvedPages;
        return this;
    }
}
