package io.nop.report.pdf.doc;

import io.nop.report.pdf.font.FontManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Mixed-style inline line breaking. Unlike {@code TextWrapHelper} (single font, whole
 * string) this measures run by run and glyph by glyph so that runs with different
 * fonts and sizes share one line.
 *
 * <p>Rules: Latin text breaks at whitespace (a word longer than the line is hard
 * broken); CJK characters may break before and after each character; a tab advances
 * to the next {@link #TAB_WIDTH} stop measured from the line start; explicit breaks
 * always end the line. Pure measurement, no drawing.</p>
 */
public final class DocLineBreaker {
    /** Simplified tab model: fixed stops every 36pt (0.5 inch) from the line start. */
    public static final float TAB_WIDTH = 36f;
    /** Line box height as a multiple of the largest font size when the style has none. */
    public static final float DEFAULT_LINE_SPACING = 1.2f;
    /** Baseline position below the top of the em box, as a fraction of the font size. */
    public static final float ASCENT_RATIO = 0.85f;

    private static final float EPSILON = 0.01f;

    private final DocRenderSession session;

    public DocLineBreaker(DocRenderSession session) {
        this.session = session;
    }

    private static final class Glyph {
        final int codePoint;
        final float width;
        final DocTextStyle style;
        final FontManager.FontResolution font;
        final boolean spacer;

        Glyph(int codePoint, float width, DocTextStyle style, FontManager.FontResolution font, boolean spacer) {
            this.codePoint = codePoint;
            this.width = width;
            this.style = style;
            this.font = font;
            this.spacer = spacer;
        }
    }

    private final class State {
        final float firstWidth;
        final float otherWidth;
        final float defaultFontSize;
        final float lineSpacing;
        final List<DocLine> lines = new ArrayList<>();
        List<Glyph> current = new ArrayList<>();
        float currentWidth;
        int breakIndex;

        State(float firstWidth, float otherWidth, float defaultFontSize, float lineSpacing) {
            this.firstWidth = firstWidth;
            this.otherWidth = otherWidth;
            this.defaultFontSize = defaultFontSize;
            this.lineSpacing = lineSpacing;
        }

        float maxWidth() {
            return lines.isEmpty() ? firstWidth : otherWidth;
        }

        void add(Glyph glyph) {
            if (!current.isEmpty() && currentWidth + glyph.width > maxWidth() + EPSILON) {
                boolean space = !glyph.spacer && Character.isWhitespace(glyph.codePoint);
                if (space) {
                    // the overflowing space is consumed by the wrap
                    flush(false);
                    return;
                }
                if (glyph.spacer || isCjk(glyph.codePoint) || breakIndex == current.size() || breakIndex <= 0) {
                    flush(false);
                } else {
                    List<Glyph> carry = new ArrayList<>(current.subList(breakIndex, current.size()));
                    current = new ArrayList<>(current.subList(0, breakIndex));
                    flush(false);
                    current.addAll(carry);
                    for (Glyph g : carry)
                        currentWidth += g.width;
                }
            }
            current.add(glyph);
            currentWidth += glyph.width;
            if (glyph.spacer || Character.isWhitespace(glyph.codePoint) || isCjk(glyph.codePoint))
                breakIndex = current.size();
        }

        void addTab(DocTextStyle style) {
            float next = ((float) Math.floor((currentWidth + EPSILON) / TAB_WIDTH) + 1) * TAB_WIDTH;
            if (next > maxWidth() + EPSILON && !current.isEmpty()) {
                flush(false);
                next = TAB_WIDTH;
            }
            current.add(new Glyph(0, next - currentWidth, style, null, true));
            currentWidth = next;
            breakIndex = current.size();
        }

        void flush(boolean pageBreakAfter) {
            // trailing whitespace does not take part in the line width / alignment
            int end = current.size();
            while (end > 0) {
                Glyph g = current.get(end - 1);
                if (g.spacer || !Character.isWhitespace(g.codePoint))
                    break;
                end--;
            }

            DocLine line = new DocLine();
            float x = 0;
            float maxFontSize = 0;
            StringBuilder text = null;
            float segX = 0;
            float segWidth = 0;
            Glyph segHead = null;
            for (int i = 0; i < end; i++) {
                Glyph g = current.get(i);
                maxFontSize = Math.max(maxFontSize, g.style.getFontSize());
                boolean sameSegment = segHead != null && !g.spacer
                        && segHead.style == g.style && segHead.font.getFont() == g.font.getFont();
                if (!sameSegment && segHead != null) {
                    line.getSegments().add(new DocLine.Segment(text.toString(), segX, segWidth,
                            segHead.style, segHead.font));
                    segHead = null;
                }
                if (!g.spacer) {
                    if (segHead == null) {
                        segHead = g;
                        text = new StringBuilder();
                        segX = x;
                        segWidth = 0;
                    }
                    text.appendCodePoint(g.codePoint);
                    segWidth += g.width;
                }
                x += g.width;
            }
            if (segHead != null) {
                line.getSegments().add(new DocLine.Segment(text.toString(), segX, segWidth,
                        segHead.style, segHead.font));
            }
            if (maxFontSize <= 0) {
                // empty line: keep the size of what was trimmed, else the paragraph default
                for (Glyph g : current)
                    maxFontSize = Math.max(maxFontSize, g.style.getFontSize());
                if (maxFontSize <= 0)
                    maxFontSize = defaultFontSize;
            }
            float height = maxFontSize * lineSpacing;
            line.setWidth(x);
            line.setHeight(height);
            line.setBaseline((height - maxFontSize) / 2 + maxFontSize * ASCENT_RATIO);
            line.setPageBreakAfter(pageBreakAfter);
            lines.add(line);

            current = new ArrayList<>();
            currentWidth = 0;
            breakIndex = 0;
        }
    }

    /**
     * @param firstWidth      available width of the first line (pt)
     * @param otherWidth      available width of the following lines (pt)
     * @param defaultFontSize font size giving an empty line its height
     * @param lineSpacing     line box height as a multiple of the largest font size on the line
     * @return at least one line (an empty paragraph yields one empty line)
     */
    public List<DocLine> breakLines(List<DocInline> inlines, float firstWidth, float otherWidth,
                                    float defaultFontSize, float lineSpacing) {
        State state = new State(firstWidth, otherWidth, defaultFontSize,
                lineSpacing > 0 ? lineSpacing : DEFAULT_LINE_SPACING);

        for (DocInline inline : inlines) {
            switch (inline.getKind()) {
                case TAB:
                    state.addTab(inline.getStyle());
                    break;
                case LINE_BREAK:
                    state.flush(false);
                    break;
                case PAGE_BREAK:
                    state.flush(true);
                    break;
                default:
                    addText(state, inline);
            }
        }
        if (!state.current.isEmpty() || state.lines.isEmpty())
            state.flush(false);
        return state.lines;
    }

    private void addText(State state, DocInline inline) {
        String text = inline.getText();
        if (text == null || text.isEmpty())
            return;
        DocTextStyle style = inline.getStyle();

        StringBuilder visible = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= 0x20)
                visible.appendCodePoint(normalizeSpace(cp));
        }
        if (visible.length() == 0 && text.indexOf('\n') < 0 && text.indexOf('\t') < 0)
            return;

        FontManager.FontResolution font = session.fontForText(visible.toString(), style);
        float fontSize = style.getEffectiveFontSize();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\n') {
                state.flush(false);
            } else if (cp == '\t') {
                state.addTab(style);
            } else if (cp >= 0x20) {
                cp = normalizeSpace(cp);
                state.add(new Glyph(cp, session.charWidth(font.getFont(), cp, fontSize), style, font, false));
            }
        }
    }

    /** Non-breaking spaces have no glyph in most CJK fallback fonts: draw a plain space. */
    private static int normalizeSpace(int cp) {
        return cp == 0x00A0 || cp == 0x202F || cp == 0x2007 ? ' ' : cp;
    }

    static boolean isCjk(int cp) {
        if (cp < 0x2E80)
            return false;
        if (cp >= 0x3000 && cp <= 0x303F || cp >= 0xFF00 && cp <= 0xFFEF)
            return true;
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL;
    }
}
