package io.nop.pdf.toc;

/**
 * WI15：目录行条目（headingize TOC_ENTRY_RE 抽取产物）。
 */
public class TocLine {
    private final String title;
    private final String pageLabel;
    private final Integer pageNo;

    public TocLine(String title, String pageLabel, Integer pageNo) {
        this.title = title;
        this.pageLabel = pageLabel;
        this.pageNo = pageNo;
    }

    public String getTitle() {
        return title;
    }

    /** 目录页上的原始页码标签（物理页 "12" / 逻辑页 "3-1" / 中文汇编卷 "附件一-1"） */
    public String getPageLabel() {
        return pageLabel;
    }

    /** 物理页号（逻辑页形态无法确定物理页时为 null） */
    public Integer getPageNo() {
        return pageNo;
    }
}
