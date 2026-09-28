package io.nop.jq.ast;

public final class IndexAccessNode implements JqAstNode {
    private final JqAstNode index;
    private final JqAstNode object;

    public IndexAccessNode(JqAstNode index, JqAstNode object) {
        this.index = index;
        this.object = object;
    }

    public JqAstNode index() { return index; }
    public JqAstNode object() { return object; }
    public boolean hasExplicitObject() { return object != null; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitIndexAccess(this); }
    @Override public String toString() {
        return (object != null ? object : "") + ".[" + index + "]";
    }
}
