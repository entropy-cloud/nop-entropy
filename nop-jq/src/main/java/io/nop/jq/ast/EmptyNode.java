package io.nop.jq.ast;

public final class EmptyNode implements JqAstNode {
    public static final EmptyNode INSTANCE = new EmptyNode();
    private EmptyNode() {}
    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitEmpty(this); }
    @Override public String toString() { return "empty"; }
}
