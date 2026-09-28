package io.nop.jq.jq.ast;

public final class SliceNode implements JqAstNode {
    private final JqAstNode start;
    private final JqAstNode end;
    private final JqAstNode object;

    public SliceNode(JqAstNode start, JqAstNode end, JqAstNode object) {
        this.start = start;
        this.end = end;
        this.object = object;
    }

    public JqAstNode start() { return start; }
    public JqAstNode end() { return end; }
    public JqAstNode object() { return object; }
    public boolean hasExplicitObject() { return object != null; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitSlice(this); }
    @Override public String toString() {
        String s = start != null ? start.toString() : "";
        String e = end != null ? end.toString() : "";
        return (object != null ? object : "") + ".[" + s + ":" + e + "]";
    }
}
