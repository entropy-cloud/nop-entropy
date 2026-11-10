package io.nop.report.pdf.doc;

/**
 * One inline atom fed to {@link DocLineBreaker}: styled text, a tab, or a forced break.
 */
public final class DocInline {
    public enum Kind {
        TEXT, TAB, LINE_BREAK, PAGE_BREAK
    }

    private final Kind kind;
    private final String text;
    private final DocTextStyle style;

    private DocInline(Kind kind, String text, DocTextStyle style) {
        this.kind = kind;
        this.text = text;
        this.style = style;
    }

    public static DocInline text(String text, DocTextStyle style) {
        return new DocInline(Kind.TEXT, text, style);
    }

    public static DocInline tab(DocTextStyle style) {
        return new DocInline(Kind.TAB, null, style);
    }

    public static DocInline lineBreak(DocTextStyle style) {
        return new DocInline(Kind.LINE_BREAK, null, style);
    }

    public static DocInline pageBreak(DocTextStyle style) {
        return new DocInline(Kind.PAGE_BREAK, null, style);
    }

    public Kind getKind() {
        return kind;
    }

    public String getText() {
        return text;
    }

    public DocTextStyle getStyle() {
        return style;
    }
}
