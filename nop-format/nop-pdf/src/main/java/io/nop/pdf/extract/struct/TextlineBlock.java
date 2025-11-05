package io.nop.pdf.extract.struct;


import io.nop.commons.text.MutableString;

public class TextlineBlock extends Block {

    /**
     * 文本内容
     */
    private String content;

    /**
     * 字号
     */
    private int fontSize;

    private MutableString normalizedInput;

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isEmpty() {
        return content == null || content.trim().isEmpty();
    }

    public MutableString getNormalizedInput() {
        if (content == null || content.length() <= 0)
            return null;

        if (normalizedInput == null)
            normalizedInput = new MutableString(content).trim();
        return normalizedInput;
    }

    public void setNormalizedInput(MutableString input) {
        this.normalizedInput = input;
    }
}
