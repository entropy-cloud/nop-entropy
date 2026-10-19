package io.nop.pdf.census;

/**
 * WI17：文档机械化统计（供保真对账）。
 */
public class DocumentStats {
    private int pages;
    private int chars;
    private int fffdCount;
    private int headingCount;
    private int tableRows;
    private int tableCols;
    private int imageCount;

    public int getPages() { return pages; }
    public DocumentStats setPages(int v) { this.pages = v; return this; }
    public int getChars() { return chars; }
    public DocumentStats setChars(int v) { this.chars = v; return this; }
    public int getFffdCount() { return fffdCount; }
    public DocumentStats setFffdCount(int v) { this.fffdCount = v; return this; }
    public int getHeadingCount() { return headingCount; }
    public DocumentStats setHeadingCount(int v) { this.headingCount = v; return this; }
    public int getTableRows() { return tableRows; }
    public DocumentStats setTableRows(int v) { this.tableRows = v; return this; }
    public int getTableCols() { return tableCols; }
    public DocumentStats setTableCols(int v) { this.tableCols = v; return this; }
    public int getImageCount() { return imageCount; }
    public DocumentStats setImageCount(int v) { this.imageCount = v; return this; }
}
