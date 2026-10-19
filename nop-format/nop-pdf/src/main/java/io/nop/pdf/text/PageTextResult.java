package io.nop.pdf.text;

/**
 * WI16：单页文本抽取结果。
 */
public class PageTextResult {
    private final int pageNo;
    private final String text;
    private final int charCount;
    private final int fffdCount;
    private final int unmappedCount;
    private boolean scanned;

    public PageTextResult(int pageNo, String text, int charCount, int fffdCount, int unmappedCount) {
        this.pageNo = pageNo;
        this.text = text;
        this.charCount = charCount;
        this.fffdCount = fffdCount;
        this.unmappedCount = unmappedCount;
    }

    public int getPageNo() {
        return pageNo;
    }

    /** 归一化后的页文本（strip 模式单空格；layout 模式保留重建 run；含尾 \\n） */
    public String getText() {
        return text;
    }

    /** trim 后字符数（扫描判定口径） */
    public int getCharCount() {
        return charCount;
    }

    public int getFffdCount() {
        return fffdCount;
    }

    /** toUnicode==null 的字形事件计数（PDFBox 原生乱码可观测点） */
    public int getUnmappedCount() {
        return unmappedCount;
    }

    public boolean isScanned() {
        return scanned;
    }

    public PageTextResult setScanned(boolean scanned) {
        this.scanned = scanned;
        return this;
    }
}
