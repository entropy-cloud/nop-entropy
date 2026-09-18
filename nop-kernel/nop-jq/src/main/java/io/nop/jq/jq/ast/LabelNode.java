package io.nop.jq.jq.ast;

public final class LabelNode implements JqAstNode {
    private final String name;
    public LabelNode(String name) { this.name = name; }
    public String name() { return name; }
    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitLabel(this); }
    @Override public String toString() { return "label $" + name; }
}
