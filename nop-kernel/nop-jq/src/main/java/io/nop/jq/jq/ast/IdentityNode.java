package io.nop.jq.jq.ast;

public final class IdentityNode implements JqAstNode {
    public static final IdentityNode INSTANCE = new IdentityNode();
    private IdentityNode() {}
    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitIdentity(this); }
    @Override public String toString() { return "."; }
}
