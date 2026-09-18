package io.nop.jq.jq.ast;

public final class VariableNode implements JqAstNode {
    private final String name;
    public VariableNode(String name) { this.name = name; }
    public String name() { return name; }
    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitVariable(this); }
    @Override public String toString() { return "$" + name; }
}
