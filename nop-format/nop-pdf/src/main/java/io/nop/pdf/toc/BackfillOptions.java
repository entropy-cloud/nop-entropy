package io.nop.pdf.toc;

import io.nop.pdf.outline.PdfOutlineEntry;

import java.util.List;

/**
 * WI15：回填选项（headingize opts 直译——R2-Major-3 全量移植裁定）。
 */
public class BackfillOptions {
    /** outline 路径条目（非空时走 v2 headingizeFromOutline） */
    private List<PdfOutlineEntry> outline;
    /** >0 时按目录条目编号深度过滤（v1 tocDepth） */
    private int tocDepth;
    /** 全量单调标题扫描（页码算术对汇编卷无意义时） */
    private boolean forceScan;

    public List<PdfOutlineEntry> getOutline() {
        return outline;
    }

    public BackfillOptions setOutline(List<PdfOutlineEntry> outline) {
        this.outline = outline;
        return this;
    }

    public int getTocDepth() {
        return tocDepth;
    }

    public BackfillOptions setTocDepth(int tocDepth) {
        this.tocDepth = tocDepth;
        return this;
    }

    public boolean isForceScan() {
        return forceScan;
    }

    public BackfillOptions setForceScan(boolean forceScan) {
        this.forceScan = forceScan;
        return this;
    }
}
