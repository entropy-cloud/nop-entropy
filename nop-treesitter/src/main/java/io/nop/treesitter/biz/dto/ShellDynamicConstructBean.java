package io.nop.treesitter.biz.dto;

import io.nop.api.core.annotations.data.DataBean;

/**
 * One place where the script computes a value at run time, located by byte range.
 * Nothing is evaluated.
 */
@DataBean
public class ShellDynamicConstructBean {

    private String kind;
    private int startByte;
    private int endByte;
    private String text;

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public int getStartByte() {
        return startByte;
    }

    public void setStartByte(int startByte) {
        this.startByte = startByte;
    }

    public int getEndByte() {
        return endByte;
    }

    public void setEndByte(int endByte) {
        this.endByte = endByte;
    }

    /** The source text of the construct, verbatim. */
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
