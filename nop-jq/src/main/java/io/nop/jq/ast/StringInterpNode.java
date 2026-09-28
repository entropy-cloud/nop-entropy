package io.nop.jq.ast;

import java.util.List;

public final class StringInterpNode implements JqAstNode {
    private final List<Object> parts;

    public StringInterpNode(List<Object> parts) { this.parts = parts; }
    public List<Object> parts() { return parts; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitStringInterp(this); }
    @Override public String toString() {
        StringBuilder sb = new StringBuilder("\"");
        for (Object part : parts) {
            if (part instanceof String s) sb.append(s);
            else if (part instanceof JqAstNode n) sb.append("\\(").append(n).append(")");
        }
        sb.append("\"");
        return sb.toString();
    }
}
