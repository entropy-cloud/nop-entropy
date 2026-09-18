package io.nop.jq.jq.ast;

public final class NullLiteralNode implements JqAstNode {
    public static final NullLiteralNode INSTANCE = new NullLiteralNode();
    private NullLiteralNode() {}
    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitNull(this); }
    @Override public String toString() { return "null"; }
}
