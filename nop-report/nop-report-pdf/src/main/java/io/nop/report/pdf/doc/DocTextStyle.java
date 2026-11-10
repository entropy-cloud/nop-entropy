package io.nop.report.pdf.doc;

import io.nop.office.model.OfficeFont;
import io.nop.office.model.WordRunStyle;
import io.nop.office.model.constants.OfficeFontUnderline;
import io.nop.office.model.constants.OfficeFontVerticalAlign;

/**
 * Fully resolved text attributes of one inline run (named style + inline overrides
 * already merged). Sizes are in pt.
 */
public final class DocTextStyle {
    /** Font size scale applied to superscript / subscript runs. */
    public static final float SCRIPT_SCALE = 0.65f;
    /** Baseline rise of a superscript run, as a fraction of the base font size. */
    public static final float SUPERSCRIPT_RISE = 0.33f;
    /** Baseline drop of a subscript run, as a fraction of the base font size. */
    public static final float SUBSCRIPT_DROP = 0.12f;

    private String fontName;
    private float fontSize = OfficeFont.DEFAULT_FONT_SIZE;
    private boolean bold;
    private boolean italic;
    private boolean underline;
    private boolean strikeout;
    private String color;
    private String highlightColor;
    private OfficeFontVerticalAlign verticalAlign;
    private String href;

    public DocTextStyle copy() {
        DocTextStyle ret = new DocTextStyle();
        ret.fontName = fontName;
        ret.fontSize = fontSize;
        ret.bold = bold;
        ret.italic = italic;
        ret.underline = underline;
        ret.strikeout = strikeout;
        ret.color = color;
        ret.highlightColor = highlightColor;
        ret.verticalAlign = verticalAlign;
        ret.href = href;
        return ret;
    }

    /**
     * Overlay a run style: non-null scalar font fields override, boolean flags are
     * OR-ed (a {@code false} primitive means "unset" in the model).
     */
    public DocTextStyle apply(WordRunStyle runStyle) {
        if (runStyle == null)
            return this;
        applyFont(runStyle.getFont());
        if (runStyle.getHighlightColor() != null && !runStyle.getHighlightColor().isEmpty())
            highlightColor = runStyle.getHighlightColor();
        return this;
    }

    public DocTextStyle applyFont(OfficeFont font) {
        if (font == null)
            return this;
        if (font.getFontName() != null && !font.getFontName().isEmpty())
            fontName = font.getFontName();
        if (font.getFontSize() != null && font.getFontSize() > 0)
            fontSize = font.getFontSize();
        if (font.getFontColor() != null && !font.getFontColor().isEmpty())
            color = font.getFontColor();
        if (font.isBold())
            bold = true;
        if (font.isItalic())
            italic = true;
        if (font.isStrikeout())
            strikeout = true;
        OfficeFontUnderline underlineStyle = font.getUnderlineStyle();
        if (underlineStyle != null)
            underline = underlineStyle != OfficeFontUnderline.NONE;
        if (font.getVerticalAlign() != null)
            verticalAlign = font.getVerticalAlign();
        return this;
    }

    /** Font size actually used for measuring and drawing (script runs are scaled down). */
    public float getEffectiveFontSize() {
        return verticalAlign == null ? fontSize : fontSize * SCRIPT_SCALE;
    }

    /** Baseline shift in pt, positive = up. */
    public float getBaselineRise() {
        if (verticalAlign == OfficeFontVerticalAlign.superscript)
            return fontSize * SUPERSCRIPT_RISE;
        if (verticalAlign == OfficeFontVerticalAlign.subscript)
            return -fontSize * SUBSCRIPT_DROP;
        return 0;
    }

    public String getFontName() {
        return fontName;
    }

    public void setFontName(String fontName) {
        this.fontName = fontName;
    }

    public float getFontSize() {
        return fontSize;
    }

    public void setFontSize(float fontSize) {
        this.fontSize = fontSize;
    }

    public boolean isBold() {
        return bold;
    }

    public void setBold(boolean bold) {
        this.bold = bold;
    }

    public boolean isItalic() {
        return italic;
    }

    public void setItalic(boolean italic) {
        this.italic = italic;
    }

    public boolean isUnderline() {
        return underline;
    }

    public void setUnderline(boolean underline) {
        this.underline = underline;
    }

    public boolean isStrikeout() {
        return strikeout;
    }

    public void setStrikeout(boolean strikeout) {
        this.strikeout = strikeout;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getHighlightColor() {
        return highlightColor;
    }

    public void setHighlightColor(String highlightColor) {
        this.highlightColor = highlightColor;
    }

    public OfficeFontVerticalAlign getVerticalAlign() {
        return verticalAlign;
    }

    public void setVerticalAlign(OfficeFontVerticalAlign verticalAlign) {
        this.verticalAlign = verticalAlign;
    }

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }
}
