package io.nop.jq.ast;

public final class IteratorNode implements JqAstNode {
    private final JqAstNode object;

    public IteratorNode(JqAstNode object) { this.object = object; }
    public JqAstNode object() { return object; }
    public boolean hasExplicitObject() { return object != null; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitIterator(this); }
    @Override public String toString() {
        return (object != null ? object : "") + ".[]";
    }
}
