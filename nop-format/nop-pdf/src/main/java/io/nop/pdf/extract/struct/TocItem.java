package io.nop.pdf.extract.struct;


import io.nop.commons.text.MutableString;

/**
 * 目录项
 */
public class TocItem {

    private int mTocPageNo;

    /**
     * 标题
     */
    private String mTitle;

    /**
     * 页码(就是目录项结尾显示的那个数字)
     */
    private int mPageNo;

    private MutableString normalizedInput;

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        this.mTitle = title;
    }

    public int getTocPageNo() {
        return mTocPageNo;
    }

    public void setTocPageNo(int tocPageNo) {
        this.mTocPageNo = tocPageNo;
    }

    public int getPageNo() {
        return mPageNo;
    }

    public void setPageNo(int pageNo) {
        this.mPageNo = pageNo;
    }

    public MutableString getNormalizedInput() {
        if (mTitle == null)
            return null;
        if (normalizedInput == null)
            normalizedInput = new MutableString(mTitle).trim();
        return normalizedInput;
    }

    public void setNormalizedInput(MutableString input) {
        this.normalizedInput = input;
    }
}
