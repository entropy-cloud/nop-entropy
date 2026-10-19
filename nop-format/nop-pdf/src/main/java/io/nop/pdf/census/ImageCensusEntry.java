package io.nop.pdf.census;

/**
 * WI17：逐页图片清单条目（对齐 card-kb image-registry schema）。
 */
public class ImageCensusEntry {
    private final int seq;
    private final String file;
    private final int page;
    private final int width;
    private final int height;
    private final long sizeKb;
    private final int areaPx;
    private final boolean keep;
    private final boolean repeated;

    public ImageCensusEntry(int seq, String file, int page, int width, int height,
                            long sizeKb, int areaPx, boolean keep, boolean repeated) {
        this.seq = seq;
        this.file = file;
        this.page = page;
        this.width = width;
        this.height = height;
        this.sizeKb = sizeKb;
        this.areaPx = areaPx;
        this.keep = keep;
        this.repeated = repeated;
    }

    public int getSeq() { return seq; }
    public String getFile() { return file; }
    public int getPage() { return page; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public long getSizeKb() { return sizeKb; }
    public int getAreaPx() { return areaPx; }
    public boolean isKeep() { return keep; }
    public boolean isRepeated() { return repeated; }
}
