package io.nop.pdf.toc;

import java.util.ArrayList;
import java.util.List;

/**
 * WI15：回填统计（字段表定版——v1/v2 并集，Java 端按单一 BackfillStats 归一；
 * 基准早期 return 形状不一致，R2-Major-3）。
 */
public class BackfillStats {
    private int outlineEntries;
    private int inserted;
    private int replaced;
    private int unmatched;
    private int skippedNoAnchor;
    private int tocEntries;
    private int byExact;
    private int byAdjacent;
    private int byPageTop;
    private int byScan;
    private int duplicateSkipped;
    private int depthFiltered;
    private int indexSkipped;
    private int exampleSkipped;
    private int bodyH1;
    private Integer pageOffset;
    private List<Integer> tocPages = new java.util.ArrayList<>();

    public int getOutlineEntries() {
        return outlineEntries;
    }

    public BackfillStats setOutlineEntries(int v) {
        this.outlineEntries = v;
        return this;
    }

    public int getInserted() {
        return inserted;
    }

    public BackfillStats setInserted(int v) {
        this.inserted = v;
        return this;
    }

    public int getReplaced() {
        return replaced;
    }

    public BackfillStats setReplaced(int v) {
        this.replaced = v;
        return this;
    }

    public int getUnmatched() {
        return unmatched;
    }

    public BackfillStats setUnmatched(int v) {
        this.unmatched = v;
        return this;
    }

    public int getSkippedNoAnchor() {
        return skippedNoAnchor;
    }

    public BackfillStats setSkippedNoAnchor(int v) {
        this.skippedNoAnchor = v;
        return this;
    }

    public int getTocEntries() {
        return tocEntries;
    }

    public BackfillStats setTocEntries(int v) {
        this.tocEntries = v;
        return this;
    }

    public int getByExact() {
        return byExact;
    }

    public BackfillStats setByExact(int v) {
        this.byExact = v;
        return this;
    }

    public int getByAdjacent() {
        return byAdjacent;
    }

    public BackfillStats setByAdjacent(int v) {
        this.byAdjacent = v;
        return this;
    }

    public int getByPageTop() {
        return byPageTop;
    }

    public BackfillStats setByPageTop(int v) {
        this.byPageTop = v;
        return this;
    }

    public int getByScan() {
        return byScan;
    }

    public BackfillStats setByScan(int v) {
        this.byScan = v;
        return this;
    }

    public int getDuplicateSkipped() {
        return duplicateSkipped;
    }

    public BackfillStats setDuplicateSkipped(int v) {
        this.duplicateSkipped = v;
        return this;
    }

    public int getDepthFiltered() {
        return depthFiltered;
    }

    public BackfillStats setDepthFiltered(int v) {
        this.depthFiltered = v;
        return this;
    }

    public int getIndexSkipped() {
        return indexSkipped;
    }

    public BackfillStats setIndexSkipped(int v) {
        this.indexSkipped = v;
        return this;
    }

    public int getExampleSkipped() {
        return exampleSkipped;
    }

    public BackfillStats setExampleSkipped(int v) {
        this.exampleSkipped = v;
        return this;
    }

    public int getBodyH1() {
        return bodyH1;
    }

    public BackfillStats setBodyH1(int v) {
        this.bodyH1 = v;
        return this;
    }

    public Integer getPageOffset() {
        return pageOffset;
    }

    public BackfillStats setPageOffset(Integer v) {
        this.pageOffset = v;
        return this;
    }

    public List<Integer> getTocPages() {
        return tocPages;
    }

    public BackfillStats setTocPages(List<Integer> v) {
        this.tocPages = v;
        return this;
    }
}
