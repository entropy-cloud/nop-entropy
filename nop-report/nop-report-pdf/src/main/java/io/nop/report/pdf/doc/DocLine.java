package io.nop.report.pdf.doc;

import io.nop.report.pdf.font.FontManager;

import java.util.ArrayList;
import java.util.List;

/**
 * One laid-out line: positioned same-style segments plus the line box metrics.
 * All values are in pt; segment x is relative to the line content start.
 */
public final class DocLine {

    /** A run of consecutive glyphs sharing style and resolved font. */
    public static final class Segment {
        private final String text;
        private final float x;
        private final float width;
        private final DocTextStyle style;
        private final FontManager.FontResolution font;

        public Segment(String text, float x, float width, DocTextStyle style,
                       FontManager.FontResolution font) {
            this.text = text;
            this.x = x;
            this.width = width;
            this.style = style;
            this.font = font;
        }

        public String getText() {
            return text;
        }

        public float getX() {
            return x;
        }

        public float getWidth() {
            return width;
        }

        public DocTextStyle getStyle() {
            return style;
        }

        public FontManager.FontResolution getFont() {
            return font;
        }
    }

    private final List<Segment> segments = new ArrayList<>();
    private float width;
    private float height;
    private float baseline;
    private float xOffset;
    private boolean pageBreakAfter;

    public List<Segment> getSegments() {
        return segments;
    }

    /** Concatenated text of the line (tabs contribute no characters). */
    public String getText() {
        StringBuilder sb = new StringBuilder();
        for (Segment segment : segments)
            sb.append(segment.getText());
        return sb.toString();
    }

    /** Content width, trailing whitespace excluded. */
    public float getWidth() {
        return width;
    }

    void setWidth(float width) {
        this.width = width;
    }

    /** Line box height (largest font size on the line times the line spacing). */
    public float getHeight() {
        return height;
    }

    void setHeight(float height) {
        this.height = height;
    }

    /** Distance from the line box top down to the text baseline. */
    public float getBaseline() {
        return baseline;
    }

    void setBaseline(float baseline) {
        this.baseline = baseline;
    }

    /** Horizontal start of the line content relative to the block left edge (indent + alignment). */
    public float getXOffset() {
        return xOffset;
    }

    void setXOffset(float xOffset) {
        this.xOffset = xOffset;
    }

    /** True when an inline page break follows this line. */
    public boolean isPageBreakAfter() {
        return pageBreakAfter;
    }

    void setPageBreakAfter(boolean pageBreakAfter) {
        this.pageBreakAfter = pageBreakAfter;
    }
}
