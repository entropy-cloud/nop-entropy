package io.nop.jq.ast;

public final class BooleanLiteralNode implements JqAstNode {
    private final boolean value;
    public BooleanLiteralNode(boolean value) { this.value = value; }
    public boolean value() { return value; }
    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitBoolean(this); }
    @Override public String toString() { return Boolean.toString(value); }
}
