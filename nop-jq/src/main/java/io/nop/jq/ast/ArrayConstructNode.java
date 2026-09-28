package io.nop.jq.ast;

public final class ArrayConstructNode implements JqAstNode {
    private final JqAstNode element;
    public ArrayConstructNode(JqAstNode element) { this.element = element; }
    public JqAstNode element() { return element; }
    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitArrayConstruct(this); }
    @Override public String toString() { return "[" + element + "]"; }
}
