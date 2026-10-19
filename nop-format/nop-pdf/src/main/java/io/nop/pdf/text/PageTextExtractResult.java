package io.nop.pdf.text;

import java.util.List;

/**
 * WI16：全文档抽取结果（页序）+ 机械化质量统计。
 */
public class PageTextExtractResult {
    private final List<PageTextResult> pages;
    private final int totalPages;
    private final int totalChars;
    private final int totalFffd;
    private final int totalUnmapped;
    private final int scannedPageCount;

    public PageTextExtractResult(List<PageTextResult> pages, int scannedPageCount) {
        this.pages = pages;
        int chars = 0;
        int fffd = 0;
        int unmapped = 0;
        int scanned = scannedPageCount;
        for (PageTextResult p : pages) {
            chars += p.getCharCount();
            fffd += p.getFffdCount();
            unmapped += p.getUnmappedCount();
        }
        this.totalPages = pages.size();
        this.totalChars = chars;
        this.totalFffd = fffd;
        this.totalUnmapped = unmapped;
        this.scannedPageCount = scanned;
    }

    public List<PageTextResult> getPages() {
        return pages;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public int getTotalChars() {
        return totalChars;
    }

    public int getTotalFffd() {
        return totalFffd;
    }

    public int getTotalUnmapped() {
        return totalUnmapped;
    }

    public int getScannedPageCount() {
        return scannedPageCount;
    }
}
