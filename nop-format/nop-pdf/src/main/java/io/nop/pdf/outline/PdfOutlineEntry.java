package io.nop.pdf.outline;

/**
 * WI14：扁平化 outline 条目（语义对齐 card-kb pdfio.mjs extractOutlineEntries + s0
 * fillPageEnds）。page 为 1-based 物理页（null=解析失败/无目标）；page-end 仅在
 * page!=null 时有值（其后第一条带页条目的 page，无则 total-pages，cap 到 total-pages）。
 */
public class PdfOutlineEntry {
    private String title;
    private int depth;
    private Integer page;
    private Integer pageEnd;

    public String getTitle() {
        return title;
    }

    public PdfOutlineEntry setTitle(String title) {
        this.title = title;
        return this;
    }

    /** 1-based 层级 */
    public int getDepth() {
        return depth;
    }

    public PdfOutlineEntry setDepth(int depth) {
        this.depth = depth;
        return this;
    }

    public Integer getPage() {
        return page;
    }

    public PdfOutlineEntry setPage(Integer page) {
        this.page = page;
        return this;
    }

    public Integer getPageEnd() {
        return pageEnd;
    }

    public PdfOutlineEntry setPageEnd(Integer pageEnd) {
        this.pageEnd = pageEnd;
        return this;
    }
}
