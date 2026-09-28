package io.nop.jq.jq.ast;

public final class StringLiteralNode implements JqAstNode {
    private final String value;
    public StringLiteralNode(String value) { this.value = value; }
    public String value() { return value; }
    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitString(this); }
    @Override public String toString() { return "\"" + value + "\""; }
}
