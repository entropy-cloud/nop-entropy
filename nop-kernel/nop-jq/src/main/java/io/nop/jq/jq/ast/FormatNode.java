package io.nop.jq.jq.ast;

public final class FormatNode implements JqAstNode {
    private final String format;
    private final JqAstNode object;

    public FormatNode(String format, JqAstNode object) {
        this.format = format;
        this.object = object;
    }

    public String format() { return format; }
    public JqAstNode object() { return object; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitFormat(this); }
    @Override public String toString() {
        return (object != null ? object + " | " : "") + "@" + format;
    }
}
